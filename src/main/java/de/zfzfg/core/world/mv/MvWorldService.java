package de.zfzfg.core.world.mv;

import de.zfzfg.eventplugin.EventPlugin;
import org.bukkit.Bukkit;
import org.bukkit.World;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Zentraler Einstiegspunkt fuer alle Weltoperationen, die aus dem Webinterface angestossen werden.
 *
 * <p>Drei Aufgaben:</p>
 * <ol>
 *   <li><strong>Backend-Wahl</strong> (verzoegert, siehe {@link #backend()}). Ist die
 *       Multiverse-Core-5-API vorhanden, wird {@link Mv5WorldBackend} benutzt, sonst
 *       {@link LegacyCommandWorldBackend}. Der {@code Class.forName}-Test steht bewusst hier
 *       und nicht im Backend selbst -- so wird {@code Mv5WorldBackend} auf MV4-Servern nie
 *       geladen und kann keinen {@code NoClassDefFoundError} ausloesen.</li>
 *   <li><strong>Jobs.</strong> Die HTTP-Handler laufen auf dem Executor des eingebetteten
 *       {@code HttpServer}, Weltoperationen muessen aber auf den Server-Main-Thread. Ein Job
 *       nimmt den Auftrag entgegen, gibt sofort eine ID zurueck und wird per
 *       {@code TaskManager} auf dem Main-Thread ausgefuehrt; das Panel pollt den Status.</li>
 *   <li><strong>Schutzgelaender.</strong> Namensvalidierung, Sperre fuer Haupt-/Default-Welt und
 *       das optionale Backup vor dem Loeschen.</li>
 * </ol>
 */
public class MvWorldService {

    /**
     * Signal eines Backends: "diese Welt verwaltet Multiverse nicht, raeum du den Ordner weg".
     * Betrifft Welten, die per Vanilla oder anderem Plugin entstanden sind, und den kompletten
     * Legacy-Pfad (dort ist ein Loeschen per Konsole wegen {@code mv confirm} nicht sicher).
     */
    static final String NOT_MANAGED_MARKER = "__MV_NOT_MANAGED__";  // i18n-ignore: interner Marker zwischen Backend und Service, nie sichtbar

    /**
     * Zulaessige Weltnamen. Bewusst enger als das, was Bukkit erlaubt: hier werden Ordner
     * geloescht, also darf kein {@code .}, {@code /} oder {@code \} durchkommen.
     */
    private static final String WORLD_NAME_PATTERN = "[A-Za-z0-9_\\-]{1,64}";  // i18n-ignore: Regex, kein Anzeigetext

    /** Ordner im Server-Root, die niemals als "Welt" behandelt werden duerfen. */
    private static final Set<String> FORBIDDEN_NAMES = Set.of(
            "plugins", "logs", "cache", "crash-reports", "libraries", "versions", "config");

    private static final long JOB_TTL_MILLIS = 5 * 60 * 1000L;

    private final EventPlugin plugin;
    private final Map<String, MvJob> jobs = new ConcurrentHashMap<>();
    private volatile MvWorldBackend backend;
    private volatile boolean stopping;
    private final WorldOperationGuard reservations = new WorldOperationGuard();
    private final java.util.concurrent.ThreadPoolExecutor io = new java.util.concurrent.ThreadPoolExecutor(
            2, 2, 0, java.util.concurrent.TimeUnit.SECONDS, new java.util.concurrent.ArrayBlockingQueue<>(16),
            task -> { Thread thread = new Thread(task, "EventPVP-WorldIO"); thread.setDaemon(true); return thread; });

    private void dispatch(MvJob job, Runnable work) {
        if (stopping) { fail(job, "Plugin stopping"); return; }
        try {
            plugin.getTaskManager().run(() -> {
                if (stopping) { fail(job, "Plugin stopping"); return; }
                try { work.run(); } catch (Exception ex) { fail(job, ex.toString()); }
            });
        } catch (Exception ex) { fail(job, ex.toString()); }
    }
    private void async(MvJob job, Runnable work) {
        if (stopping) { fail(job, "Plugin stopping"); return; }
        try { io.execute(() -> {
            if (stopping) { fail(job, "Plugin stopping"); return; }
            try { work.run(); } catch (Exception ex) { fail(job, ex.toString()); }
        }); } catch (java.util.concurrent.RejectedExecutionException ex) { fail(job, "World I/O overloaded"); }
    }
    private boolean reserve(MvJob job) throws java.io.IOException {
        File folder = resolveWorldFolder(job.worldName);
        if (folder == null) folder = new File(Bukkit.getWorldContainer(), job.worldName);
        if (!reservations.acquire(job.id, folder.getCanonicalFile().toPath())) {
            fail(job, "Conflicting world operation"); return false;
        }
        return true;
    }
    private void fail(MvJob job, String detail) {
        job.detail = detail; job.messageKey = MvResult.GENERIC_ERROR; job.status = "FAILED";
        complete(job);
        plugin.getLogger().warning("[Multiverse] Job " + job.id + " failed: " + detail);
    }
    private void complete(MvJob job) {
        job.completedAt = System.currentTimeMillis(); reservations.release(job.id);
    }
    public void shutdown() {
        stopping = true; io.shutdownNow();
        org.bukkit.configuration.file.YamlConfiguration report = new org.bukkit.configuration.file.YamlConfiguration();
        java.util.List<Map<String, Object>> unfinished = new ArrayList<>();
        for (MvJob job : jobs.values()) if ("RUNNING".equals(job.status)) {
            unfinished.add(job.toMap()); fail(job, "Interrupted by plugin shutdown; inspect world files before retry");
        }
        if (!unfinished.isEmpty()) {
            report.set("interrupted", unfinished);
            try { de.zfzfg.core.util.AtomicFiles.save(report, new File(plugin.getDataFolder(), "interrupted-world-jobs.yml").toPath()); }
            catch (java.io.IOException ex) { plugin.getLogger().log(Level.SEVERE, "Could not record interrupted world jobs", ex); }
        }
    }
    public Map<String, Object> status() {
        return Map.of("running", jobs.values().stream().filter(j -> "RUNNING".equals(j.status)).count(),
                "queuedIO", io.getQueue().size(), "activeIO", io.getActiveCount(), "stopping", stopping);
    }

    public MvWorldService(EventPlugin plugin) {
        this.plugin = plugin;
        // Einen Tick nach dem Enable aufwaermen: dann sind alle Plugins geladen, die Wahl ist
        // stabil und die gewaehlte Variante steht als Diagnosezeile im Startlog.
        plugin.getTaskManager().runLater(this::backend, 1L);
    }

    /**
     * Backend beim ersten Zugriff bestimmen statt im Konstruktor.
     *
     * <p>Multiverse ist nur ein {@code softdepend}: laedt es aus irgendeinem Grund erst nach
     * diesem Plugin, waere eine Entscheidung zur Konstruktionszeit dauerhaft falsch. Beim
     * ersten echten Zugriff -- fruehestens der erste Web-Request -- steht der Server dagegen.</p>
     */
    private MvWorldBackend backend() {
        MvWorldBackend resolved = backend;
        if (resolved != null) return resolved;

        synchronized (this) {
            if (backend == null) {
                MvWorldBackend selected = selectBackend(plugin);
                plugin.getLogger().info("[Multiverse] Using world backend: " + selected.getBackendId());  // i18n-ignore: console-only startup diagnosis
                backend = selected;
            }
            return backend;
        }
    }

    /**
     * Waehlt das Backend.
     *
     * <p>{@link Mv5WorldBackend} wird bewusst <em>reflektiv</em> erzeugt und nirgends im Bytecode
     * dieser Klasse namentlich erwaehnt. Sonst koennte schon die Verifikation von
     * {@code selectBackend} das Laden der Klasse -- und damit der MV5-Typen in ihren
     * Methodensignaturen -- anstossen, bevor der try-Block ueberhaupt laeuft. So bleibt jeder
     * Link-Fehler garantiert innerhalb des {@code catch (Throwable)}.</p>
     */
    private static MvWorldBackend selectBackend(EventPlugin plugin) {
        try {
            Class.forName("org.mvplugins.multiverse.core.MultiverseCoreApi");  // i18n-ignore: Klassenname
            MvWorldBackend mv5 = (MvWorldBackend) Class
                    .forName("de.zfzfg.core.world.mv.Mv5WorldBackend")  // i18n-ignore: Klassenname
                    .getDeclaredConstructor()
                    .newInstance();
            if (mv5.isAvailable()) {
                return mv5;
            }
        } catch (Throwable ignored) {
            // MV4, aelter oder gar kein Multiverse -> Kommando-Backend.
        }
        return new LegacyCommandWorldBackend(plugin);
    }

    public String getBackendId() {
        return backend().getBackendId();
    }

    public boolean isAvailable() {
        return backend().isAvailable();
    }

    public boolean supportsAdvancedCreateOptions() {
        return backend().supportsAdvancedCreateOptions();
    }

    /** Muss auf dem Main-Thread laufen (Bukkit-Zugriff). Fuer die Web-API via {@link #listWorldsSync()}. */
    public List<MvWorldInfo> listWorlds() {
        return backend().listWorlds();
    }

    /**
     * Weltliste vom HTTP-Thread aus: hopst auf den Main-Thread und wartet auf das Ergebnis.
     *
     * <p>Scheitert die Abfrage, wird bewusst <strong>geworfen</strong> statt eine leere Liste
     * zurueckzugeben. Eine leere Liste ist von "der Server hat wirklich keine Welten" nicht zu
     * unterscheiden -- das Panel haette daraufhin jede Welt als Platzhalter dargestellt und
     * "Welt erstellen" angeboten, obwohl die Welten laengst existieren.</p>
     *
     * <p>Die Wartezeit ist grosszuegig, weil genau die Momente kritisch sind, in denen der
     * Main-Thread beschaeftigt ist: direkt nach einem Unload speichert der Server die Welt und
     * entlaedt ihre Chunks.</p>
     *
     * @throws IllegalStateException wenn die Liste nicht ermittelt werden konnte
     */
    public List<MvWorldInfo> listWorldsSync() {
        if (Bukkit.isPrimaryThread()) {
            return listWorlds();
        }
        java.util.concurrent.CompletableFuture<List<MvWorldInfo>> future = new java.util.concurrent.CompletableFuture<>();
        plugin.getTaskManager().run(() -> {
            try {
                future.complete(listWorlds());
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });
        try {
            return future.get(30, java.util.concurrent.TimeUnit.SECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            plugin.getLogger().warning("[Multiverse] World list timed out - the server main thread did not respond within 30s");  // i18n-ignore: console-only diagnosis
            throw new IllegalStateException("world list timed out", e);  // i18n-ignore: console-only diagnosis
        } catch (Exception e) {
            Throwable cause = e instanceof java.util.concurrent.ExecutionException && e.getCause() != null ? e.getCause() : e;
            plugin.getLogger().log(Level.WARNING, "[Multiverse] Could not list worlds", cause);  // i18n-ignore: console-only diagnosis
            throw new IllegalStateException("world list failed", cause);  // i18n-ignore: console-only diagnosis
        }
    }

    // ============ Jobs ============

    /** Status eines laufenden oder abgeschlossenen Auftrags. */
    public static class MvJob {
        public final String id = UUID.randomUUID().toString().replace("-", "");
        public final String action;
        public final String worldName;
        public volatile String status = "RUNNING";  // i18n-ignore: Protokoll-Token der JSON-API, das Panel formuliert selbst
        /** Bundle-Key des Fehlergrunds ({@code mv.error.*}); leer, solange nichts schiefging. */
        public volatile String messageKey = "";
        /** Untranslatierbarer Zusatz: Multiverse-Fehlertext oder Exception-Message. */
        public volatile String detail = "";
        public final long createdAt = System.currentTimeMillis();
        public volatile long completedAt;
        public volatile String stagingPath = "";

        MvJob(String action, String worldName) {
            this.action = action;
            this.worldName = worldName;
        }

        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", id);
            map.put("action", action);
            map.put("worldName", worldName);
            map.put("status", status);
            map.put("messageKey", messageKey);
            map.put("detail", detail);
            map.put("stagingPath", stagingPath);
            map.put("createdAt", createdAt);
            map.put("completedAt", completedAt);
            return map;
        }
    }

    public MvJob getJob(String id) {
        purgeExpiredJobs();
        return id == null ? null : jobs.get(id);
    }

    private void purgeExpiredJobs() {
        long cutoff = System.currentTimeMillis() - JOB_TTL_MILLIS;
        jobs.values().removeIf(job -> job.completedAt > 0 && job.completedAt < cutoff);
    }

    private MvJob submit(String action, String worldName, java.util.function.Supplier<MvResult> work) {
        purgeExpiredJobs();
        MvJob job = new MvJob(action, worldName);
        jobs.put(job.id, job);

        dispatch(job, () -> {
            try { if (reserve(job)) finish(job, work); }
            catch (java.io.IOException ex) { fail(job, ex.toString()); }
        });
        return job;
    }

    private void finish(MvJob job, java.util.function.Supplier<MvResult> work) {
        if (stopping) { fail(job, "Plugin stopping"); return; }
        try {
            MvResult result = work.get();
            if (stopping) { fail(job, "Shutdown overlapped operation; inspect world state"); return; }
            job.messageKey = result.getMessageKey();
            job.detail = result.getDetail();
            job.status = result.isSuccess() ? "SUCCESS" : "FAILED";  // i18n-ignore: Protokoll-Token der JSON-API
        } catch (MvInputException e) {
            job.messageKey = e.getMessageKey();
            job.detail = e.getDetail();
            job.status = "FAILED";  // i18n-ignore: Protokoll-Token der JSON-API
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING,
                    "[Multiverse] Job " + job.action + " failed for world " + job.worldName, t);  // i18n-ignore: console-only diagnosis
            job.messageKey = MvResult.GENERIC_ERROR;
            job.detail = String.valueOf(t.getMessage());
            job.status = "FAILED";  // i18n-ignore: Protokoll-Token der JSON-API
        }
        complete(job);
        if ("FAILED".equals(job.status)) {  // i18n-ignore: Protokoll-Token der JSON-API
            // Auch in die Konsole: bisher wusste nur das Panel von einem fehlgeschlagenen Job,
            // im Server-Log fehlte z.B. ein gescheitertes "create" komplett.
            String reason = job.messageKey + (job.detail.isEmpty() ? "" : " (" + job.detail + ")");
            plugin.getLogger().warning("[Multiverse] Job " + job.action + " for '" + job.worldName + "' failed: " + reason);  // i18n-ignore: console-only diagnosis
        }
    }

    // ============ Operationen ============

    public MvJob createWorld(MvCreateSpec spec) {
        return submit("create", spec.getName(), () -> backend().create(spec));
    }

    public MvJob loadWorld(String worldName) {
        String name = requireValidWorldName(worldName);
        return submit("load", name, () -> backend().load(name));
    }

    public MvJob unloadWorld(String worldName) {
        String name = requireValidWorldName(worldName);
        return submit("unload", name, () -> {
            if (isProtected(name)) return MvResult.fail("mv.error.protectedWorld", name);
            evacuateWorld(name);
            return MvResult.ok();
        });
    }

    /**
     * Loescht eine Welt endgueltig.
     *
     * <p>Der Ablauf ist dreistufig, und die Reihenfolge ist wichtig:</p>
     * <ol>
     *   <li><em>Main-Thread:</em> Spieler herausteleportieren und die Welt entladen. Erst danach
     *       sind die Region-Dateien geschrieben und in sich stimmig.</li>
     *   <li><em>Async:</em> optionales Backup. Eine grosse Welt zu zippen dauert und hat auf dem
     *       Main-Thread nichts verloren -- und ein Backup einer noch geladenen Welt waere ein
     *       Abzug mitten im Schreibvorgang, also im Zweifel unbrauchbar.</li>
     *   <li><em>Main-Thread:</em> die eigentliche Loeschung.</li>
     * </ol>
     */
    public MvJob deleteWorld(String worldName, boolean backup) {
        String name = requireValidWorldName(worldName);
        purgeExpiredJobs(); MvJob job = new MvJob("delete", name); jobs.put(job.id, job);
        dispatch(job, () -> {
            if (isProtected(name)) { fail(job, "Protected world"); return; }
            final File folder;
            try {
                if (!reserve(job)) return;
                folder = resolveWorldFolder(name);
                evacuateWorld(name);
                validateDeleteTarget(name, folder);
            } catch (Exception ex) { fail(job, ex.toString()); return; }
            async(job, () -> {
                try {
                    if (folder != null) SafeFileTree.validate(folder.toPath());
                    if (backup) new de.zfzfg.core.world.MultiverseHelper(plugin).createBackup(name, folder);
                } catch (Exception ex) { fail(job, ex.toString()); return; }
                dispatch(job, () -> {
                    try { validateDeleteTarget(name, folder); }
                    catch (Exception ex) { fail(job, ex.toString()); return; }
                    MvResult result = backend().delete(name);
                    if (!result.isSuccess() && !NOT_MANAGED_MARKER.equals(result.getMessageKey())) { finish(job, () -> result); return; }
                    async(job, () -> finish(job, () -> {
                        try { if (folder != null) SafeFileTree.delete(folder.toPath()); return MvResult.ok(); }
                        catch (java.io.IOException ex) { return MvResult.fail("mv.error.deleteFolderFailed", ex.toString()); }
                    }));
                });
            });
        });
        return job;
    }

    private void validateDeleteTarget(String name, File folder) throws java.io.IOException {
        if (isProtected(name) || Bukkit.getWorld(name) != null) throw new java.io.IOException("World protected or still loaded");
        if (folder == null) return;
        File canonical = folder.getCanonicalFile(), container = Bukkit.getWorldContainer().getCanonicalFile();
        if (!isStrictlyInside(canonical, container)) throw new java.io.IOException("World folder outside container");
        for (World world : Bukkit.getWorlds()) {
            var loaded = world.getWorldFolder().getCanonicalFile().toPath();
            if (loaded.startsWith(canonical.toPath())) throw new java.io.IOException("Deletion contains a loaded world");
        }
    }

    /**
     * Loescht eine Welt sofort auf dem aufrufenden Thread -- der muss der Main-Thread sein.
     *
     * <p>Fuer {@link de.zfzfg.core.world.MultiverseHelper#deleteWorld}, das seinen
     * Callback-Vertrag behaelt und deshalb keinen Job benutzen kann.</p>
     */
    /**
     * Regeneriert eine geladene Welt ueber das gewaehlte Backend.
     *
     * <p>Auf MV5 geht das an {@code regenWorld} vorbei an {@code mv confirm}. Auf MV4
     * bleibt das Kommando-Paar im Legacy-Backend. Muss auf dem Main-Thread laufen.</p>
     */
    public MvResult regenerateWorldNow(String worldName) {
        if (worldName == null || worldName.isBlank()) {
            return MvResult.fail("mv.error.invalidName");
        }
        String name = requireValidWorldName(worldName);
        if (stopping || isProtected(name)) return MvResult.fail("mv.error.protectedWorld", name);
        MvJob job = new MvJob("regen", name);
        try {
            if (!reserve(job)) return MvResult.fail(MvResult.GENERIC_ERROR, job.detail);
            return backend().regen(name);
        } catch (java.io.IOException ex) { return MvResult.fail(MvResult.GENERIC_ERROR, ex.toString()); }
        finally { reservations.release(job.id); }
    }

    /** Nur fuer Tests: setzt das Backend, ohne Class.forName und ohne Konsolenbefehle. */
    void useBackendForTests(MvWorldBackend injected) {
        this.backend = injected;
    }

    public MvResult deleteWorldNow(String worldName) {
        String name = requireValidWorldName(worldName);
        if (stopping) return MvResult.fail(MvResult.GENERIC_ERROR, "Plugin stopping");
        MvJob job = new MvJob("delete", name);
        try {
            if (!reserve(job)) return MvResult.fail(MvResult.GENERIC_ERROR, job.detail);
            return deleteWorldNow(name, resolveWorldFolder(name));
        } catch (java.io.IOException ex) { return MvResult.fail(MvResult.GENERIC_ERROR, ex.toString()); }
        finally { reservations.release(job.id); }
    }

    /** @param resolvedFolder der vor dem Entladen aufgeloeste Weltordner, darf null sein */
    private MvResult deleteWorldNow(String worldName, java.io.File resolvedFolder) {
        String name = requireValidWorldName(worldName);
        if (isProtected(name)) {
            return MvResult.fail("mv.error.protectedWorld", name);
        }
        evacuateWorld(name);
        if (resolvedFolder != null) {
            try { SafeFileTree.validate(resolvedFolder.toPath()); }
            catch (java.io.IOException ex) { return MvResult.fail("mv.error.deleteFolderFailed", ex.toString()); }
        }
        MvResult result = backend().delete(name);
        if (!result.isSuccess() && NOT_MANAGED_MARKER.equals(result.getMessageKey())) {
            return deleteWorldFolder(name, resolvedFolder);
        }
        // Multiverse loescht den Ordner selbst; bleibt doch etwas liegen (z.B. weil die Welt
        // nur teilweise registriert war), wird hier nachgeraeumt.
        if (result.isSuccess()
                && ((resolvedFolder != null && resolvedFolder.isDirectory()) || worldFolderExists(name))) {
            return deleteWorldFolder(name, resolvedFolder);
        }
        return result;
    }

    // ============ Ordner-Aufloesung ============

    /**
     * Ermittelt den tatsaechlichen Weltordner. Muss auf dem Main-Thread laufen.
     *
     * <p>Der naive Griff zu {@code container/<name>} war der Kern zweier Bugs: auf modernen
     * Servern liegt eine Welt haeufig als Dimension <em>innerhalb</em> der Hauptwelt
     * ({@code world/dimensions/minecraft/<name>}) -- Backup fand "keinen Ordner", der Scan
     * hielt existierende Welten fuer Platzhalter. Aufloesungskette:</p>
     * <ol>
     *   <li>Welt geladen: {@code Bukkit.getWorld(name).getWorldFolder()} -- autoritativ.</li>
     *   <li>Multiverse fragen ({@code getOfflineWorldFolder()}, ab MV 5.7).</li>
     *   <li>Klassisch {@code container/<name>}.</li>
     *   <li>Dimensions-Scan: {@code container/<hauptwelt>/dimensions/<ns>/<name>}.</li>
     * </ol>
     *
     * @return der Ordner oder {@code null}, wenn nichts gefunden wurde (echter Platzhalter)
     */
    public java.io.File resolveWorldFolder(String worldName) {
        World loaded = Bukkit.getWorld(worldName);
        if (loaded != null) {
            try {
                java.io.File folder = loaded.getWorldFolder();
                if (folder != null) return folder;
            } catch (Throwable ignored) {
            }
        }

        try {
            java.io.File fromBackend = backend().resolveWorldFolder(worldName);
            if (fromBackend != null && fromBackend.isDirectory()) return fromBackend;
        } catch (Throwable ignored) {
        }

        java.io.File classic = new java.io.File(Bukkit.getWorldContainer(), worldName);
        if (classic.isDirectory()) return classic;

        return findDimensionFolder(worldName);
    }

    /** Sucht {@code container/<welt-mit-level.dat>/dimensions/<namespace>/<name>}. */
    private java.io.File findDimensionFolder(String worldName) {
        java.io.File[] roots = Bukkit.getWorldContainer().listFiles();
        if (roots == null) return null;
        for (java.io.File root : roots) {
            if (!root.isDirectory() || !new java.io.File(root, "level.dat").isFile()) continue;
            java.io.File[] namespaces = new java.io.File(root, "dimensions").listFiles();
            if (namespaces == null) continue;
            for (java.io.File namespace : namespaces) {
                java.io.File candidate = new java.io.File(namespace, worldName);
                if (candidate.isDirectory()) return candidate;
            }
        }
        return null;
    }

    /** Spieler aus der Welt holen und sie entladen, damit der Ordner freigegeben wird. */
    private void evacuateWorld(String worldName) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) return;
        World fallback = Bukkit.getWorld(plugin.getConfigManager().getMainWorld());
        if (fallback == null || fallback.equals(world)) {
            fallback = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
        }
        if (fallback != null && !fallback.equals(world)) {
            for (org.bukkit.entity.Player player : new ArrayList<>(world.getPlayers())) {
                try {
                    if (!player.teleport(fallback.getSpawnLocation())) throw new IllegalStateException("Evacuation rejected");
                } catch (Exception ex) {
                    throw new IllegalStateException("Could not evacuate " + player.getUniqueId(), ex);
                }
            }
        }
        if (!world.getPlayers().isEmpty() || !Bukkit.unloadWorld(world, true) || Bukkit.getWorld(worldName) != null)
            throw new IllegalStateException("World remains loaded: " + worldName);
    }

    /**
     * Loescht den Weltordner von der Platte.
     *
     * <p>Da der aufgeloeste Ordner inzwischen auch <em>innerhalb</em> der Hauptwelt liegen kann
     * ({@code world/dimensions/minecraft/<name>}), reicht die Namensvalidierung allein nicht
     * mehr -- vor dem Loeschen wird der kanonische Pfad geprueft: er muss echt unterhalb des
     * Server-Containers liegen und darf weder der Container selbst noch der Wurzelordner
     * irgendeiner geladenen Welt sein. Den Dimensions-Unterordner der Hauptwelt zu loeschen ist
     * gewollt; die Hauptwelt selbst zu treffen waere fatal.</p>
     */
    private MvResult deleteWorldFolder(String worldName, java.io.File resolvedFolder) {
        File folder = resolvedFolder != null ? resolvedFolder
                : new File(Bukkit.getWorldContainer(), worldName);
        if (!folder.isDirectory()) {
            return MvResult.ok();
        }
        if (Bukkit.getWorld(worldName) != null) {
            return MvResult.fail("mv.error.stillLoaded");
        }

        try {
            java.io.File canonical = folder.getCanonicalFile();
            java.io.File container = Bukkit.getWorldContainer().getCanonicalFile();

            if (canonical.equals(container) || !isStrictlyInside(canonical, container)) {
                plugin.getLogger().severe("[Multiverse] Refusing to delete folder outside the world container: " + canonical);  // i18n-ignore: console-only diagnosis
                return MvResult.fail("mv.error.deleteFolderFailed", canonical.getName());
            }
            for (World loadedWorld : Bukkit.getWorlds()) {
                java.io.File loadedRoot = loadedWorld.getWorldFolder().getCanonicalFile();
                if (canonical.equals(loadedRoot)) {
                    // Der Ordner IST eine geladene Welt (z.B. die Hauptwelt, in deren
                    // dimensions/ die Zielwelt lag) -- niemals anfassen.
                    plugin.getLogger().severe("[Multiverse] Refusing to delete the folder of loaded world '" + loadedWorld.getName() + "'");  // i18n-ignore: console-only diagnosis
                    return MvResult.fail("mv.error.protectedWorld", loadedWorld.getName());
                }
            }
        } catch (java.io.IOException e) {
            return MvResult.fail("mv.error.deleteFolderFailed", String.valueOf(e.getMessage()));
        }

        try {
            deleteRecursively(folder);
        } catch (Exception e) {
            return MvResult.fail("mv.error.deleteFolderFailed", String.valueOf(e.getMessage()));
        }
        return folder.exists()
                ? MvResult.fail("mv.error.folderLocked")
                : MvResult.ok();
    }

    /** Ob {@code child} echt unterhalb von {@code parent} liegt (beide kanonisch). */
    private static boolean isStrictlyInside(java.io.File child, java.io.File parent) {
        java.io.File current = child.getParentFile();
        while (current != null) {
            if (current.equals(parent)) return true;
            current = current.getParentFile();
        }
        return false;
    }

    private void deleteRecursively(File file) throws java.io.IOException {
        SafeFileTree.delete(file.toPath());
    }

    // ============ Backups ============

    /** Muster der Backup-Dateinamen: {@code <welt>_<yyyyMMdd_HHmmss>.zip}. */
    private static final java.util.regex.Pattern BACKUP_FILE_PATTERN =
            java.util.regex.Pattern.compile("^(.+)_(\\d{8}_\\d{6})\\.zip$");  // i18n-ignore: Regex, kein Anzeigetext

    private File getBackupsDir() {
        return new File(plugin.getDataFolder(), "backups");
    }

    /**
     * Listet die Zips in {@code plugins/<plugin>/backups/}. Thread-sicher (nur Dateisystem),
     * kann direkt vom HTTP-Thread aufgerufen werden.
     */
    public List<Map<String, Object>> listBackups() {
        List<Map<String, Object>> result = new ArrayList<>();
        File[] files = getBackupsDir().listFiles();
        if (files == null) return result;

        for (File file : files) {
            if (!file.isFile() || !file.getName().toLowerCase(Locale.ROOT).endsWith(".zip")) continue;
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("file", file.getName());
            entry.put("sizeBytes", file.length());
            java.util.regex.Matcher matcher = BACKUP_FILE_PATTERN.matcher(file.getName());
            if (matcher.matches()) {
                entry.put("worldName", matcher.group(1));
                entry.put("timestamp", matcher.group(2));
            } else {
                // Fremdes Zip im Backup-Ordner: anzeigen, aber ohne geratene Metadaten.
                entry.put("worldName", null);
                entry.put("timestamp", null);
            }
            result.add(entry);
        }
        result.sort((a, b) -> String.valueOf(b.get("file")).compareToIgnoreCase(String.valueOf(a.get("file"))));
        return result;
    }

    /**
     * Prueft einen aus dem Web kommenden Backup-Dateinamen und gibt die Datei zurueck.
     *
     * <p>Gleiche Strenge wie bei Weltnamen, aus demselben Grund: der Name steuert
     * Dateisystem-Zugriffe. Nur ein Basename ohne Separatoren, und die kanonische Datei muss
     * wirklich im Backup-Ordner liegen.</p>
     */
    public File requireValidBackupFile(String fileName) {
        String name = fileName == null ? "" : fileName.trim();
        if (name.isEmpty() || name.contains("/") || name.contains("\\") || name.contains("..")
                || !name.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            throw new MvInputException("mv.error.backupFileInvalid", name);
        }
        File file = new File(getBackupsDir(), name);
        try {
            if (!file.getCanonicalFile().getParentFile().equals(getBackupsDir().getCanonicalFile())) {
                throw new MvInputException("mv.error.backupFileInvalid", name);
            }
        } catch (java.io.IOException e) {
            throw new MvInputException("mv.error.backupFileInvalid", name);
        }
        if (!file.isFile()) {
            throw new MvInputException("mv.error.backupFileInvalid", name);
        }
        return file;
    }

    /** Loescht ein Backup-Zip. Betrifft nur die Datei, nie eine Welt. */
    public MvResult deleteBackup(String fileName) {
        File file = requireValidBackupFile(fileName);
        try {
            java.nio.file.Files.delete(file.toPath());
            return MvResult.ok();
        } catch (java.io.IOException e) {
            return MvResult.fail("mv.error.deleteFolderFailed", String.valueOf(e.getMessage()));
        }
    }

    /**
     * Stellt eine Welt aus einem Backup-Zip wieder her.
     *
     * <p>Ablauf: Ziel pruefen (Main-Thread) -> entpacken nach {@code container/<target>}
     * (async, mit Zip-Slip-Schutz) -> bei Multiverse importieren und laden (Main-Thread).
     * Ein existierendes Ziel wird abgelehnt -- Wiederherstellen ueberschreibt nie.</p>
     */
    public MvJob restoreBackup(String fileName, String targetName) {
        File archive = requireValidBackupFile(fileName);
        String target = requireValidWorldName(targetName);
        MvJob job = new MvJob("restore", target); jobs.put(job.id, job);
        dispatch(job, () -> {
            try { if (!reserve(job)) return; } catch (java.io.IOException ex) { fail(job, ex.toString()); return; }
            if (Bukkit.getWorld(target) != null || resolveWorldFolder(target) != null) { fail(job, "Restore target exists"); return; }
            File targetFolder = new File(Bukkit.getWorldContainer(), target);
            long maxBytes = plugin.getConfig().getLong("settings.world-io.max-restore-bytes", 21_474_836_480L);
            int maxEntries = plugin.getConfig().getInt("settings.world-io.max-restore-entries", 1_000_000);
            async(job, () -> {
                java.nio.file.Path staging = null;
                try {
                    staging = java.nio.file.Files.createTempDirectory(targetFolder.toPath().toAbsolutePath().getParent(), ".eventpvp-restore-");
                    job.stagingPath = staging.toString();
                    extractBackup(archive, staging.toFile(), maxBytes, maxEntries);
                    if (stopping || Thread.currentThread().isInterrupted()) throw new java.io.IOException("Restore interrupted");
                    final java.nio.file.Path prepared = staging;
                    dispatch(job, () -> {
                        try {
                            if (Bukkit.getWorld(target) != null || resolveWorldFolder(target) != null)
                                throw new java.io.IOException("Restore target appeared while extracting");
                            java.nio.file.Files.move(prepared, targetFolder.toPath()); // deliberately no REPLACE_EXISTING
                            finish(job, () -> backend().importWorld(target));
                        } catch (Exception ex) {
                            try { SafeFileTree.delete(prepared); } catch (Exception cleanup) { ex.addSuppressed(cleanup); }
                            fail(job, ex.toString());
                        }
                    });
                } catch (Exception ex) {
                    if (staging != null) try { SafeFileTree.delete(staging); } catch (Exception cleanup) { ex.addSuppressed(cleanup); }
                    fail(job, ex.toString());
                }
            });
        });
        return job;
    }

    /**
     * Entpackt ein Backup-Zip in den Zielordner.
     *
     * <p>Zip-Slip-Schutz: der kanonisierte Pfad jedes Eintrags muss unterhalb des Zielordners
     * bleiben -- ein Archiv mit {@code ../}-Eintraegen koennte sonst beliebige Serverdateien
     * ueberschreiben. {@code session.lock} wird uebersprungen (gehoert keinem Backup an).</p>
     *
     * @return ob das Archiv ein {@code level.dat} enthielt
     */
    static boolean extractBackup(File archive, File targetFolder) throws java.io.IOException {
        return extractBackup(archive, targetFolder, 21_474_836_480L, 1_000_000);
    }

    static boolean extractBackup(File archive, File targetFolder, long maxBytes, int maxEntries) throws java.io.IOException {
        if (maxBytes <= 0 || maxEntries <= 0) throw new java.io.IOException("Invalid archive limits");
        java.nio.file.Path targetRoot = targetFolder.toPath().toAbsolutePath().normalize();
        SafeFileTree.validate(targetRoot);
        long totalBytes = 0;
        int entryCount = 0;
        boolean hasLevelDat = false;
        int written = 0;

        try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(archive)) {
            java.util.Enumeration<? extends java.util.zip.ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                java.util.zip.ZipEntry entry = entries.nextElement();
                if (++entryCount > maxEntries || Thread.currentThread().isInterrupted()) throw new java.io.IOException("Archive limit or interruption");
                java.nio.file.Path destination = targetRoot.resolve(entry.getName()).normalize();
                if (!destination.startsWith(targetRoot)) {
                    throw new java.io.IOException("Blocked zip-slip entry: " + entry.getName());  // i18n-ignore: console-only diagnosis, panel shows a localized reason
                }
                if (entry.isDirectory()) {
                    java.nio.file.Files.createDirectories(destination);
                    continue;
                }
                String baseName = destination.getFileName().toString();
                if (baseName.equalsIgnoreCase("session.lock")) continue;
                if (baseName.equalsIgnoreCase("level.dat")
                        && destination.getParent() != null && destination.getParent().equals(targetRoot)) {
                    hasLevelDat = true;
                }
                java.nio.file.Files.createDirectories(destination.getParent());
                try (java.io.InputStream in = zip.getInputStream(entry)) {
                    try (var out = java.nio.file.Files.newOutputStream(destination, java.nio.file.StandardOpenOption.CREATE_NEW)) {
                        byte[] buffer = new byte[8192]; int read;
                        while ((read = in.read(buffer)) != -1) {
                            totalBytes += read;
                            if (totalBytes > maxBytes || Thread.currentThread().isInterrupted()) throw new java.io.IOException("Archive limit or interruption");
                            out.write(buffer, 0, read);
                        }
                    }
                }
                written++;
            }
        }

        if (written == 0) {
            throw new java.io.IOException("Archive contains no files");  // i18n-ignore: console-only diagnosis, panel shows a localized reason
        }
        return hasLevelDat;
    }

    // ============ Validierung ============

    /**
     * Prueft einen aus dem Web kommenden Weltnamen und gibt ihn getrimmt zurueck.
     *
     * @throws IllegalArgumentException wenn der Name leer, zu lang, gefaehrlich oder gesperrt ist
     */
    public static String requireValidWorldName(String worldName) {
        String name = worldName == null ? "" : worldName.trim();
        if (name.isEmpty()) {
            throw new MvInputException("mv.error.emptyName");
        }
        if (!name.matches(WORLD_NAME_PATTERN)) {
            throw new MvInputException("mv.error.invalidName", name);
        }
        if (FORBIDDEN_NAMES.contains(name.toLowerCase(Locale.ROOT))) {
            throw new MvInputException("mv.error.reservedName", name);
        }
        return name;
    }

    /** Haupt-/Default-Welt: darf weder entladen noch geloescht werden. */
    private boolean isProtected(String worldName) {
        if (!Bukkit.getWorlds().isEmpty()
                && Bukkit.getWorlds().get(0).getName().equalsIgnoreCase(worldName)) {
            return true;
        }
        try {
            String mainWorld = plugin.getConfigManager().getMainWorld();
            return mainWorld != null && mainWorld.equalsIgnoreCase(worldName);
        } catch (Exception e) {
            return false;
        }
    }

    // ============ Statische Helfer fuer die Backends ============

    static boolean worldFolderExists(String worldName) {
        return looksLikeWorldFolder(new File(Bukkit.getWorldContainer(), worldName));
    }

    /**
     * Ob ein Ordner Weltdaten enthaelt. {@code level.dat} reicht nicht als Pflichtkriterium:
     * Dimensionsordner ({@code world/dimensions/...}) haben keins, wohl aber {@code region/}.
     */
    static boolean looksLikeWorldFolder(File folder) {
        if (folder == null || !folder.isDirectory()) return false;
        return new File(folder, "level.dat").isFile() || new File(folder, "region").isDirectory();
    }

    /**
     * Findet Weltordner auf der Platte, die in {@code seen} noch nicht vorkommen -- also Welten,
     * die Multiverse (bzw. Bukkit) nicht als geladen meldet. {@code seen} wird dabei ergaenzt.
     */
    static List<MvWorldInfo> scanServerWorlds(Set<String> seen) {
        List<MvWorldInfo> result = new ArrayList<>();
        File[] candidates = Bukkit.getWorldContainer().listFiles();
        if (candidates == null) return result;

        for (File candidate : candidates) {
            if (!candidate.isDirectory()) continue;
            String name = candidate.getName();
            if (!new File(candidate, "level.dat").isFile()) continue;
            if (FORBIDDEN_NAMES.contains(name.toLowerCase(Locale.ROOT))) continue;
            if (!seen.add(name.toLowerCase(Locale.ROOT))) continue;

            result.add(new MvWorldInfo(name, guessEnvironment(name), null, false, false, true));
        }
        return result;
    }

    private static String guessEnvironment(String worldName) {
        String lower = worldName.toLowerCase(Locale.ROOT);
        if (lower.contains("nether")) return "NETHER";
        if (lower.contains("end")) return "THE_END";
        return "NORMAL";
    }
}
