package de.zfzfg.core.world;

import de.zfzfg.eventplugin.EventPlugin;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Zentrale Multiverse-Hilfsklasse, die von Event- und PvP-Modulen verwendet wird.
 * Vereinigt Lade-, Klon-, Lösch- und Regenerationsfunktionen.
 */
public class MultiverseHelper {

    private static final java.util.Set<String> REGENERATING_WORLDS = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final EventPlugin plugin;

    public MultiverseHelper(EventPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isMultiverseAvailable() {
        Plugin mv = Bukkit.getPluginManager().getPlugin("Multiverse-Core");  // i18n-ignore: nur Konsole (mv-Befehl bzw. Log-Diagnose), erreicht keinen Spieler
        return mv != null && mv.isEnabled();
    }

    /**
     * Entlaedt eine Welt, nachdem alle Spieler herausgeholt wurden.
     *
     * <p>Bleibt jemand zurueck, wird <b>nicht</b> entladen: eine geladene Welt zu viel ist
     * harmlos, ein Spieler in einer entladenen Welt ist es nicht.</p>
     */
    public void unloadWorld(String worldName) {
        if (!isMultiverseAvailable()) return;
        World world = Bukkit.getWorld(worldName);
        if (world != null) {
            int stuck = teleportPlayersOutWithSavedLocations(world);
            if (stuck > 0) {
                plugin.getLogger().warning(plugin.getConsoleMsg("world-unload-aborted",
                        "world", worldName, "count", String.valueOf(stuck)));
                return;
            }
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "mv unload " + worldName);  // i18n-ignore: nur Konsole (mv-Befehl bzw. Log-Diagnose), erreicht keinen Spieler
        }
    }

    public void loadWorld(String worldName, LoadCallback callback) {
        if (!isMultiverseAvailable()) {
            if (callback != null) callback.onResult(false, "Multiverse-Core not installed");  // i18n-ignore: console-only diagnosis (mv command / log), never reaches player
            return;
        }
        World world = Bukkit.getWorld(worldName);
        if (world != null) {
            if (callback != null) callback.onResult(true, "World already loaded");  // i18n-ignore: console-only diagnosis (mv command / log), never reaches player
            return;
        }

        java.io.File worldFolder = new java.io.File(Bukkit.getWorldContainer(), worldName);
        String env = guessEnv(worldName);

        String cloneSource = resolveCloneSourceForWorld(worldName);
        if (!worldFolder.exists() && cloneSource != null && !cloneSource.trim().isEmpty()) {
            plugin.getLogger().info("Clone-First: " + cloneSource.trim() + " -> " + worldName);  // i18n-ignore: technical multiverse clone trace
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "mv clone " + cloneSource.trim() + " " + worldName);  // i18n-ignore: console-only diagnosis (mv command / log), never reaches player
            plugin.getTaskManager().runLater(() -> {
                plugin.getLogger().info("Loading cloned world: " + worldName);  // i18n-ignore: technical multiverse clone trace
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "mv load " + worldName);  // i18n-ignore: console-only diagnosis (mv command / log), never reaches player
                boolean ok = (Bukkit.getWorld(worldName) != null);
                if (callback != null) callback.onResult(ok, ok ? "Clone+Load successful" : "Clone+Load failed");  // i18n-ignore: console-only diagnosis (mv command / log), never reaches player
            }, 40L);
            return;
        }

        // 1) Versuche: mv load (falls bereits bekannt aber nicht geladen)
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "mv load " + worldName);  // i18n-ignore: console-only diagnosis (mv command / log), never reaches player
        plugin.getTaskManager().runLater(() -> {
            if (Bukkit.getWorld(worldName) != null) {
                if (callback != null) callback.onResult(true, "mv load successful");  // i18n-ignore: console-only diagnosis (mv command / log), never reaches player
                return;
            }

            // 2) Wenn Ordner existiert, versuche mv import
            if (worldFolder.exists()) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "mv import " + worldName + " " + env);  // i18n-ignore: console-only diagnosis (mv command / log), never reaches player
                plugin.getTaskManager().runLater(() -> {
                    if (Bukkit.getWorld(worldName) != null) {
                        if (callback != null) callback.onResult(true, "mv import successful");  // i18n-ignore: console-only diagnosis (mv command / log), never reaches player
                        return;
                    }

                    // 3) Fallback: mv create (neue Welt anlegen)
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "mv create " + worldName + " " + env);  // i18n-ignore: console-only diagnosis (mv command / log), never reaches player
                    plugin.getTaskManager().runLater(() -> {
                        boolean ok = (Bukkit.getWorld(worldName) != null);
                        if (callback != null) callback.onResult(ok, ok ? "mv create successful" : "World could not be created/loaded");  // i18n-ignore: console-only diagnosis (mv command / log), never reaches player
                    }, 40L);
                }, 40L);
            } else {
                // Ordner existiert nicht -> direkt mv create
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "mv create " + worldName + " " + env);  // i18n-ignore: console-only diagnosis (mv command / log), never reaches player
                plugin.getTaskManager().runLater(() -> {
                    boolean ok = (Bukkit.getWorld(worldName) != null);
                    if (callback != null) callback.onResult(ok, ok ? "mv create successful" : "World could not be created/loaded");  // i18n-ignore: console-only diagnosis (mv command / log), never reaches player
                }, 40L);
            }
        }, 40L);
    }

    public void regenerateWorld(String worldName) {
        regenerateWorld(worldName, null);
    }

    public void regenerateWorld(String worldName, Runnable callback) {
        if (!isMultiverseAvailable()) {
            if (callback != null) callback.run();
            return;
        }
        if (worldName == null || worldName.trim().isEmpty()) {
            if (callback != null) callback.run();
            return;
        }
        String worldKey = worldName.toLowerCase().trim();
        if (!REGENERATING_WORLDS.add(worldKey)) {
            plugin.getLogger().info("Regeneration for world '" + worldName + "' already in progress - skipping duplicate call.");  // i18n-ignore: technical multiverse deduplication trace
            if (callback != null) callback.run();
            return;
        }
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            REGENERATING_WORLDS.remove(worldKey);
            if (callback != null) callback.run();
            return;
        }
        teleportPlayersOutWithSavedLocations(world);

        plugin.getTaskManager().runLater(() -> {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "mv regen " + worldName);  // i18n-ignore: nur Konsole (mv-Befehl bzw. Log-Diagnose), erreicht keinen Spieler
            plugin.getTaskManager().runLater(() -> {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "mv confirm");  // i18n-ignore: nur Konsole (mv-Befehl bzw. Log-Diagnose), erreicht keinen Spieler
                REGENERATING_WORLDS.remove(worldKey);
                if (callback != null) callback.run();
            }, 40L);
        }, 40L);
    }

    /**
     * Loescht eine Welt endgueltig.
     *
     * <p>Laeuft ueber {@link de.zfzfg.core.world.mv.MvWorldService}. Der fruehere Weg
     * ("mv delete", 40 Ticks warten, "mv confirm") ist auf Multiverse-Core-5-Servern wirkungslos:
     * dort verlangt die Bestaetigung standardmaessig ein Einmalpasswort
     * ({@code /mv confirm <otp>}), das von aussen nicht bekannt ist. Der Service benutzt
     * stattdessen die MV5-API, die gar keine Bestaetigung kennt, und faellt auf MV4 auf das
     * Kommando bzw. das Entfernen des Weltordners zurueck.</p>
     *
     * <p>Der Callback wird -- wie zuvor -- verzoegert aufgerufen, damit Aufrufer, die danach
     * dieselbe Welt neu anlegen, nicht in einen halb geloeschten Ordner laufen.</p>
     */
    public void deleteWorld(String worldName, Runnable callback) {
        World world = Bukkit.getWorld(worldName);
        if (world != null) {
            teleportPlayersOutWithSavedLocations(world);
        }
        try {
            de.zfzfg.core.world.mv.MvResult result = plugin.getMvWorldService().deleteWorldNow(worldName);
            if (!result.isSuccess()) {
                String reason = result.getMessageKey() + " " + result.getDetail();
                plugin.getLogger().warning("Deleting world '" + worldName + "' failed: " + reason);  // i18n-ignore: console-only diagnosis
            }
        } catch (Exception e) {
            plugin.getLogger().log(java.util.logging.Level.WARNING,
                    "Deleting world '" + worldName + "' failed", e);  // i18n-ignore: console-only diagnosis
        }
        plugin.getTaskManager().runLater(() -> {
            if (callback != null) callback.run();
        }, 40L);
    }

    public void cloneWorld(String sourceWorld, String targetWorld, Runnable callback) {
        if (!isMultiverseAvailable()) {
            if (callback != null) callback.run();
            return;
        }
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "mv clone " + sourceWorld + " " + targetWorld);  // i18n-ignore: nur Konsole (mv-Befehl bzw. Log-Diagnose), erreicht keinen Spieler
        plugin.getTaskManager().runLater(() -> {
            if (callback != null) callback.run();
        }, 40L);
    }

    public interface LoadCallback {
        void onResult(boolean success, String message);
    }

    /**
     * Holt alle Spieler aus einer Welt, die gleich entladen wird.
     *
     * <p>Nutzt die gemeinsame Kette aus {@link de.zfzfg.core.location.SafeLocationResolver},
     * damit hier dieselben Prioritaeten gelten wie beim Void-Schutz, beim Respawn und beim
     * Gestrandeten-Netz.</p>
     *
     * @return wie viele Spieler <b>nicht</b> herausgeholt werden konnten
     */
    private int teleportPlayersOutWithSavedLocations(World world) {
        de.zfzfg.core.location.SafeLocationResolver resolver = plugin.getSafeLocations();
        int stuck = 0;

        for (Player p : new java.util.ArrayList<>(world.getPlayers())) {
            try {
                org.bukkit.Location target = resolver.resolve(p);
                if (target == null || !p.teleport(target)) {
                    stuck++;
                    plugin.getLogger().warning(plugin.getConsoleMsg("world-unload-player-stuck",
                            "player", p.getName(), "world", world.getName()));
                    continue;
                }
                plugin.getEventManager().clearSavedLocation(p.getUniqueId());
            } catch (Exception e) {
                // Frueher verschluckt: ein fehlgeschlagener Teleport liess den Spieler in
                // einer Welt zurueck, die im naechsten Moment entladen wurde.
                stuck++;
                plugin.getLogger().warning(plugin.getConsoleMsg("world-unload-player-error",
                        "player", p.getName(), "world", world.getName(),
                        "error", String.valueOf(e.getMessage())));
            }
        }
        return stuck;
    }

    private String guessEnv(String worldName) {
        String lower = worldName.toLowerCase();
        if (lower.contains("nether")) return "NETHER";
        if (lower.contains("end")) return "THE_END";
        return "NORMAL";
    }

    private String resolveCloneSourceForWorld(String worldName) {
        try {
            java.util.Map<String, de.zfzfg.pvpwager.models.Arena> arenas = plugin.getArenaManager().getArenas();
            for (de.zfzfg.pvpwager.models.Arena a : arenas.values()) {
                if (worldName.equalsIgnoreCase(a.getArenaWorld())) {
                    String src = a.getCloneSourceWorld();
                    if (src != null && !src.trim().isEmpty()) return src.trim();
                }
            }
        } catch (Exception ignored) {}
        try {
            java.util.Map<String, de.zfzfg.eventplugin.model.EventConfig> events = plugin.getConfigManager().getAllEvents();
            for (de.zfzfg.eventplugin.model.EventConfig e : events.values()) {
                if (worldName.equalsIgnoreCase(e.getEventWorld())) {
                    String src = e.getCloneSourceEventWorld();
                    if (src != null && !src.trim().isEmpty()) return src.trim();
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    public void ensureWorldReady(String worldName, String cloneSource, boolean regenerate, boolean backupEnabled, boolean backupAsync) {
        org.bukkit.World world = org.bukkit.Bukkit.getWorld(worldName);
        boolean exists = new java.io.File(org.bukkit.Bukkit.getWorldContainer(), worldName).exists();
        if (!exists && cloneSource != null && !cloneSource.trim().isEmpty()) {
            cloneWorld(cloneSource.trim(), worldName, () -> {
                org.bukkit.Bukkit.dispatchCommand(org.bukkit.Bukkit.getConsoleSender(), "mv load " + worldName);  // i18n-ignore: nur Konsole (mv-Befehl bzw. Log-Diagnose), erreicht keinen Spieler
            });
            return;
        }
        if (world == null && exists) {
            org.bukkit.Bukkit.dispatchCommand(org.bukkit.Bukkit.getConsoleSender(), "mv load " + worldName);  // i18n-ignore: nur Konsole (mv-Befehl bzw. Log-Diagnose), erreicht keinen Spieler
        }
        if (regenerate) {
            if (backupEnabled) {
                if (backupAsync) {
                    plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> backupWorld(worldName));
                } else {
                    backupWorld(worldName);
                }
            }
            regenerateWorld(worldName);
        }
    }

    /**
     * Sichert eine Welt und meldet Fehler nur ins Log.
     *
     * <p>Fuer Aufrufer, bei denen das Backup eine Beigabe ist (Event-Regeneration): dort soll ein
     * fehlgeschlagenes Backup den Ablauf nicht anhalten. Wer sich auf das Backup <em>verlaesst</em>
     * -- etwa vor dem Loeschen einer Welt -- muss {@link #createBackup(String)} benutzen, sonst
     * laeuft er in ein stilles "kein Backup, aber trotzdem geloescht".</p>
     */
    public void backupWorld(String worldName) {
        try {
            createBackup(worldName);
        } catch (Exception e) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE,
                    plugin.getConsoleMsg("backup-failed", "error", String.valueOf(e.getMessage())), e);
        }
    }

    /**
     * Sichert eine Welt nach {@code plugins/<plugin>/backups/} und gibt die erzeugte Datei zurueck.
     *
     * <p>Wirft, statt still weiterzulaufen: kein Weltordner, nicht anlegbares Backup-Verzeichnis,
     * nicht lesbare Weltdateien oder ein leeres Archiv sind allesamt Gruende, den Aufrufer
     * abbrechen zu lassen. Ein halbes Backup ist kein Backup.</p>
     *
     * <p>Die Welt sollte vorher entladen sein -- sonst ist der Abzug ein Schnappschuss mitten im
     * Schreibvorgang.</p>
     *
     * @return die geschriebene Zip-Datei
     * @throws java.io.IOException wenn kein vollstaendiges Archiv entstanden ist
     */
    public java.io.File createBackup(String worldName) throws java.io.IOException {
        return createBackup(worldName, null);
    }

    /**
     * Wie {@link #createBackup(String)}, aber mit bereits aufgeloestem Weltordner.
     *
     * <p>Noetig, weil {@code container/<name>} auf modernen Servern oft der falsche Ort ist:
     * die Welt liegt dann als Dimension in der Hauptwelt
     * ({@code world/dimensions/minecraft/<name>}). Der Aufrufer loest den Ordner ueber
     * {@link de.zfzfg.core.world.mv.MvWorldService#resolveWorldFolder(String)} auf --
     * idealerweise solange die Welt noch geladen ist, denn dann kennt Bukkit ihn sicher.</p>
     *
     * @param resolvedFolder der Weltordner; {@code null} = klassisch {@code container/<name>}
     */
    public java.io.File createBackup(String worldName, java.io.File resolvedFolder) throws java.io.IOException {
        java.io.File container = org.bukkit.Bukkit.getWorldContainer();
        java.io.File worldFolder = resolvedFolder != null ? resolvedFolder
                : new java.io.File(container, worldName);
        if (!worldFolder.isDirectory()) {
            throw new java.io.IOException("World folder not found: " + worldFolder.getAbsolutePath());  // i18n-ignore: console-only diagnosis, panel shows a localized reason
        }

        java.io.File backupsDir = new java.io.File(plugin.getDataFolder(), "backups");
        if (!backupsDir.isDirectory() && !backupsDir.mkdirs()) {
            throw new java.io.IOException("Could not create backup directory: " + backupsDir.getAbsolutePath());  // i18n-ignore: console-only diagnosis, panel shows a localized reason
        }

        String timestamp = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss").format(new java.util.Date());
        java.io.File zipFile = new java.io.File(backupsDir, worldName + "_" + timestamp + ".zip");

        try {
            zipFolder(worldFolder.toPath(), zipFile.toPath());
        } catch (java.io.IOException e) {
            // Ein Torso-Archiv darf nicht liegenbleiben -- es saehe aus wie ein gueltiges Backup.
            java.nio.file.Files.deleteIfExists(zipFile.toPath());
            throw e;
        }

        if (!zipFile.isFile() || zipFile.length() == 0L) {
            java.nio.file.Files.deleteIfExists(zipFile.toPath());
            throw new java.io.IOException("Backup archive stayed empty: " + zipFile.getName());  // i18n-ignore: console-only diagnosis, panel shows a localized reason
        }

        plugin.getLogger().info(plugin.getConsoleMsg("backup-created", "file", zipFile.getName()));
        return zipFile;
    }

    /**
     * Zippt einen Ordner.
     *
     * <p>Frueher wurde eine {@code IOException} pro Datei verschluckt, das Archiv galt trotzdem
     * als geschrieben. Ergebnis war ein Backup, dem genau die Dateien fehlten, die gesperrt oder
     * kaputt waren. Jetzt bricht der erste Lesefehler den ganzen Vorgang ab.</p>
     */
    private void zipFolder(java.nio.file.Path sourceFolderPath, java.nio.file.Path zipPath) throws java.io.IOException {
        try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(
                new java.io.FileOutputStream(zipPath.toFile()));
             java.util.stream.Stream<java.nio.file.Path> files = java.nio.file.Files.walk(sourceFolderPath)) {

            java.util.Iterator<java.nio.file.Path> it = files
                    .filter(path -> !java.nio.file.Files.isDirectory(path))
                    .iterator();

            int written = 0;
            while (it.hasNext()) {
                java.nio.file.Path path = it.next();
                String entryName = sourceFolderPath.relativize(path).toString().replace('\\', '/');
                // session.lock haelt Minecraft offen; sie gehoert nicht ins Backup und waere auf
                // Windows der haeufigste Grund fuer einen Lesefehler.
                if (entryName.equalsIgnoreCase("session.lock")) continue;
                zos.putNextEntry(new java.util.zip.ZipEntry(entryName));
                java.nio.file.Files.copy(path, zos);
                zos.closeEntry();
                written++;
            }

            if (written == 0) {
                throw new java.io.IOException("No files found in " + sourceFolderPath);  // i18n-ignore: console-only diagnosis, panel shows a localized reason
            }
        }
    }
}
