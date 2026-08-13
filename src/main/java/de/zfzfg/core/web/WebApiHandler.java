package de.zfzfg.core.web;

import org.bukkit.plugin.java.JavaPlugin;
import de.zfzfg.eventplugin.EventPlugin;

import java.util.*;
import java.util.logging.Level;

/**
 * Handhabt die REST API Endpoints für das Web-Interface
 */
public class WebApiHandler {

    private final JavaPlugin plugin;
    private final WebConfigManager configManager;

    /** Wiederherstellungen pro Minute ueber das gesamte Panel. */
    private static final int RESTORE_LIMIT_PER_MINUTE = 10;

    /**
     * Bremse fuer {@code /api/inventories/restore}.
     *
     * <p>Bewusst global und nicht pro Zielspieler: die Gefahr ist ein uebernommenes
     * Web-Login, das im Sekundentakt Backups auf wechselnde Spieler zurueckspielt und so
     * beliebig viele Items erzeugt. Zehn Wiederherstellungen je Minute liegen weit ueber
     * dem, was ein Administrator von Hand macht, und weit unter dem, womit sich die
     * Serverwirtschaft zerlegen laesst.</p>
     */
    private final SlidingWindowLimiter restoreRateLimiter =
            new SlidingWindowLimiter(RESTORE_LIMIT_PER_MINUTE, 60_000L);

    public WebApiHandler(JavaPlugin plugin, WebConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    // ============ Config API ============

    /**
     * GET /api/config/get - Gibt die config.yml zurück
     */
    public Map<String, Object> getConfig() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("data", configManager.getConfigAsMap());
        return response;
    }

    /**
     * POST /api/config/save - Speichert die config.yml
     */
    public Map<String, Object> saveConfig(Map<String, Object> requestBody) {
        Map<String, Object> response = new LinkedHashMap<>();
        try {
            plugin.getLogger().info("[Web-API] Saving config.yml...");  // i18n-ignore: web API internal log
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) requestBody.get("data");
            if (data != null) {
                configManager.saveConfigFromMap(data);
                plugin.getLogger().info("[Web-API] config.yml saved");  // i18n-ignore: web API internal log
                // Die Inventar-Einstellungen liegen in final-Feldern eines Caches - ohne diesen
                // Neubau wirkt z.B. "cleanup-backups-after-match" erst nach einem Neustart.
                EventPlugin ep = eventPlugin();
                if (ep != null) {
                    ep.reloadInventoryConfig();
                }
                response.put("success", true);
                response.put("message", "Config saved");  // i18n-ignore: JSON-Feld, das das Panel nicht anzeigt (nur /api/reload wird gerendert)
            } else {
                plugin.getLogger().warning("[Web-API] No data received for config.yml");  // i18n-ignore: web API internal log
                response.put("success", false);
                response.put("message", "No data received");  // i18n-ignore: JSON-Feld, das das Panel nicht anzeigt (nur /api/reload wird gerendert)
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "[Web-API] Error saving config.yml", e);  // i18n-ignore: web API internal log
            response.put("success", false);
            response.put("message", "Error: " + e.getMessage());  // i18n-ignore: JSON-Feld, das das Panel nicht anzeigt (nur /api/reload wird gerendert)
        }
        return response;
    }

    // ============ Worlds API ============

    /**
     * GET /api/worlds/get - Gibt die worlds.yml zurück
     */
    public Map<String, Object> getWorlds() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("data", configManager.getWorldsAsMap());
        return response;
    }

    /**
     * POST /api/worlds/save - Speichert die worlds.yml
     */
    public Map<String, Object> saveWorlds(Map<String, Object> requestBody) {
        Map<String, Object> response = new LinkedHashMap<>();
        try {
            plugin.getLogger().info("[Web-API] Saving worlds.yml...");  // i18n-ignore: web API internal log
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) requestBody.get("data");
            if (data != null) {
                configManager.saveWorldsFromMap(data);
                plugin.getLogger().info("[Web-API] worlds.yml saved");  // i18n-ignore: web API internal log
                response.put("success", true);
                response.put("message", "Worlds saved");  // i18n-ignore: JSON-Feld, das das Panel nicht anzeigt (nur /api/reload wird gerendert)
            } else {
                plugin.getLogger().warning("[Web-API] No data received for worlds.yml");  // i18n-ignore: web API internal log
                response.put("success", false);
                response.put("message", "No data received");  // i18n-ignore: JSON-Feld, das das Panel nicht anzeigt (nur /api/reload wird gerendert)
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "[Web-API] Error saving worlds.yml", e);  // i18n-ignore: web API internal log
            response.put("success", false);
            response.put("message", "Error: " + e.getMessage());  // i18n-ignore: JSON-Feld, das das Panel nicht anzeigt (nur /api/reload wird gerendert)
        }
        return response;
    }

    // ============ Inventar-Verwaltung API ============

    /**
     * GET /api/inventories/status - welche Betriebsart laeuft und was sie kann.
     *
     * <p>Speist die Auswahl im Panel: welcher Modus gesetzt ist, ob InventoryRestore
     * ueberhaupt installiert ist und ob Multiverse-Inventories parallel laeuft. Ohne diese
     * Angaben koennte das Panel den Legacy-Modus nicht ehrlich beschriften.</p>
     */
    public Map<String, Object> getInventoryStatus() {
        Map<String, Object> response = new LinkedHashMap<>();
        EventPlugin ep = eventPlugin();
        if (ep == null) {
            return failure(response, "inventory.error.unavailable", "");
        }

        de.zfzfg.core.inventory.InventoryManagementConfig config = ep.getInventoryConfig();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("provider", config.mode().id());
        data.put("activeProvider", ep.getInventoryBackupService().providerName());
        data.put("managed", config.managedByPlugin());
        data.put("inventoryRestoreInstalled",
                de.zfzfg.core.inventory.InventoryBackupServiceFactory.inventoryRestoreAvailable());
        de.zfzfg.core.inventory.mvi.MultiverseInventoriesBridge bridge = ep.getMviBridge();
        data.put("multiverseInventoriesInstalled", bridge != null && bridge.isInstalled());
        data.put("multiverseInventoriesVersion", bridge == null ? "" : bridge.version());
        data.put("mviGuardActive", bridge != null && bridge.conflictGuardActive());
        data.put("mviRecoveries", bridge == null ? 0 : bridge.recoveries());
        data.put("mviGroupsUnreadable", bridge != null && bridge.report().configUnreadable());
        data.put("mviConflicts", mviConflicts(bridge));
        data.put("safetyBackups", ep.getInventorySessions() != null
                && ep.getInventorySessions().isSafetyOnly());
        data.put("openSessions", ep.getInventoryGuard() == null ? 0 : ep.getInventoryGuard().openCount());
        data.put("autoRestoreMatchEnd", config.restoreOnMatchEnd());
        data.put("autoRestoreEventEnd", config.restoreOnEventEnd());
        data.put("autoRestoreRespawn", config.restoreOnRespawn());
        data.put("autoRestoreRejoin", config.restoreOnRejoin());
        // @loose-end(unused-api): supportsPreview wird geliefert, aber vom Panel nicht gelesen - die GUI-Vorschau ueber die API ist nicht gebaut
        data.put("supportsPreview", "inventoryrestore".equals(ep.getInventoryBackupService().providerName()));

        response.put("success", true);
        response.put("data", data);
        return response;
    }

    /**
     * Die Weltgruppen-Kollisionen als Liste fuer das Panel.
     *
     * <p>Jeder Eintrag traegt den fertigen {@code /mvinv}-Befehl mit: das Panel soll nicht
     * selbst Befehle zusammenbauen muessen, und ein Admin soll sie kopieren koennen, statt
     * die Syntax nachzuschlagen.</p>
     */
    private List<Map<String, Object>> mviConflicts(
            de.zfzfg.core.inventory.mvi.MultiverseInventoriesBridge bridge) {
        List<Map<String, Object>> conflicts = new ArrayList<>();
        if (bridge == null || !bridge.isInstalled()) {
            return conflicts;
        }
        for (de.zfzfg.core.inventory.mvi.MviConflictReport.Collision collision
                : bridge.report().collisions()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("world", collision.world());
            entry.put("group", collision.group());
            entry.put("partnerWorlds", collision.partnerWorlds());
            entry.put("fixCommand", collision.fixCommand());
            conflicts.add(entry);
        }
        return conflicts;
    }

    /**
     * POST /api/inventories/provider - schaltet die Betriebsart um.
     *
     * <p>Payload: {@code {"provider":"auto|none"}}. Der Altwert {@code inventoryrestore}
     * wird noch angenommen und auf {@code auto} normalisiert. Der Wechsel greift sofort;
     * das Guard-Journal bleibt bestehen, damit offene Sitzungen laufender Matches den
     * Wechsel ueberleben.</p>
     *
     * <p>Der Weg nach {@code none} ist gesperrt, solange Sitzungen offen sind: dort holt
     * niemand mehr die Inventare zurueck.</p>
     */
    public Map<String, Object> setInventoryProvider(Map<String, Object> requestBody) {
        Map<String, Object> response = new LinkedHashMap<>();
        EventPlugin ep = eventPlugin();
        if (ep == null) {
            return failure(response, "inventory.error.unavailable", "");
        }
        String raw = requestBody.get("provider") == null ? "" : String.valueOf(requestBody.get("provider"));
        de.zfzfg.core.inventory.InventoryManagementConfig.Mode mode =
                de.zfzfg.core.inventory.InventoryManagementConfig.Mode.from(raw);
        if (mode == null) {
            return failure(response, "inventory.error.unknownProvider", raw);
        }

        // Der Altwert 'inventoryrestore' war nie etwas anderes als 'auto'. Er wird noch
        // angenommen, damit alte Lesezeichen und Skripte nicht brechen, aber nicht mehr
        // geschrieben - in der config.yml soll genau ein Wert fuer diese Betriebsart stehen.
        if (mode == de.zfzfg.core.inventory.InventoryManagementConfig.Mode.INVENTORYRESTORE) {
            mode = de.zfzfg.core.inventory.InventoryManagementConfig.Mode.AUTO;
        }

        // Der Wechsel in den Legacy-Betrieb waehrend laufender Sitzungen ist echter
        // Item-Verlust: danach liefert InventorySessionManager.finish() nur noch
        // UNAVAILABLE, und die Inventare der laufenden Matches kommen nie zurueck. Die
        // Sperre gehoert hierher und nicht nur ins Panel - die API ist auch direkt
        // erreichbar.
        if (mode == de.zfzfg.core.inventory.InventoryManagementConfig.Mode.NONE
                && ep.getInventoryGuard() != null && ep.getInventoryGuard().openCount() > 0) {
            return failure(response, "inventory.error.openSessions",
                    String.valueOf(ep.getInventoryGuard().openCount()));
        }

        try {
            plugin.getLogger().info("[Web-API] Inventory provider -> " + mode.id());  // i18n-ignore: web API internal log
            plugin.getConfig().set("settings.inventory-management.provider", mode.id());
            plugin.saveConfig();
            plugin.reloadConfig();
            ep.getCoreConfigManager().reloadAll();
            ep.reloadInventoryManagement();
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "[Web-API] Could not switch inventory provider", e);  // i18n-ignore: web API internal log
            return failure(response, "inventory.error.switchFailed", String.valueOf(e.getMessage()));
        }
        return getInventoryStatus();
    }

    /**
     * GET /api/inventories/list?player=&lt;name|uuid&gt;[&amp;type=] - Backups eines Spielers.
     */
    public Map<String, Object> listInventories(Map<String, String> query) {
        Map<String, Object> response = new LinkedHashMap<>();
        EventPlugin ep = eventPlugin();
        if (ep == null) {
            return failure(response, "inventory.error.unavailable", "");
        }
        UUID ownerId = resolvePlayer(query.get("player"));
        if (ownerId == null) {
            return failure(response, "inventory.error.unknownPlayer", String.valueOf(query.get("player")));
        }
        String type = query.get("type");
        if (type != null && type.isEmpty()) {
            type = null;
        }

        try {
            List<de.zfzfg.core.inventory.BackupRef> refs =
                    await(ep.getInventoryBackupService().list(ownerId, type));
            List<Map<String, Object>> entries = new ArrayList<>();
            for (de.zfzfg.core.inventory.BackupRef ref : refs) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("id", ref.backupId());
                entry.put("type", ref.type());
                entry.put("createdAt", ref.createdAt());
                entry.put("metadata", ref.metadata());
                entries.add(entry);
            }
            org.bukkit.OfflinePlayer offline = org.bukkit.Bukkit.getOfflinePlayer(ownerId);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("player", ownerId.toString());
            data.put("playerName", offline.getName() == null ? ownerId.toString() : offline.getName());
            data.put("online", offline.isOnline());
            data.put("backups", entries);
            response.put("success", true);
            response.put("data", data);
        } catch (Exception e) {
            return failure(response, "inventory.error.listFailed", String.valueOf(e.getMessage()));
        }
        return response;
    }

    /**
     * GET /api/inventories/get?player=&lt;uuid&gt;&amp;id=&lt;backupId&gt; - Inhalt eines Backups.
     *
     * <p>Liefert Slot, Material, Anzahl, Anzeigename, Lore und Verzauberungen - genug fuer die
     * Gitterdarstellung im Panel, aber bewusst keine rohen NBT-Daten.</p>
     */
    public Map<String, Object> getInventory(Map<String, String> query) {
        Map<String, Object> response = new LinkedHashMap<>();
        EventPlugin ep = eventPlugin();
        if (ep == null) {
            return failure(response, "inventory.error.unavailable", "");
        }
        UUID ownerId = resolvePlayer(query.get("player"));
        String backupId = query.get("id");
        if (ownerId == null || backupId == null || backupId.isEmpty()) {
            return failure(response, "inventory.error.unknownBackup", String.valueOf(backupId));
        }

        try {
            Optional<de.zfzfg.core.inventory.BackupRef> ref =
                    await(ep.getInventoryBackupService().resolve(ownerId, backupId));
            if (ref.isEmpty()) {
                return failure(response, "inventory.error.unknownBackup", backupId);
            }
            Optional<de.zfzfg.core.inventory.CapturedInventory> snapshot =
                    await(ep.getInventoryBackupService().load(ref.get()));
            if (snapshot.isEmpty()) {
                return failure(response, "inventory.error.loadFailed", backupId);
            }

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("id", ref.get().backupId());
            data.put("type", ref.get().type());
            data.put("createdAt", ref.get().createdAt());
            data.put("metadata", ref.get().metadata());
            data.put("level", snapshot.get().level());
            data.put("exp", snapshot.get().exp());
            data.put("contents", describeItems(snapshot.get().contents()));
            data.put("armor", describeItems(snapshot.get().armor()));
            data.put("offhand", describeItem(snapshot.get().offhand(), 0));
            response.put("success", true);
            response.put("data", data);
        } catch (Exception e) {
            return failure(response, "inventory.error.loadFailed", String.valueOf(e.getMessage()));
        }
        return response;
    }

    /**
     * POST /api/inventories/restore - spielt ein Backup zurueck.
     *
     * <p>Payload: {@code {"player":"<uuid|name>","backupId":"<id>","clearBefore":true}}.
     * Funktioniert auch fuer Offline-Spieler - dann wird die Wiederherstellung persistent
     * fuer den naechsten Join eingereiht.</p>
     */
    public Map<String, Object> restoreInventory(Map<String, Object> requestBody) {
        Map<String, Object> response = new LinkedHashMap<>();
        EventPlugin ep = eventPlugin();
        if (ep == null) {
            return failure(response, "inventory.error.unavailable", "");
        }
        UUID ownerId = resolvePlayer(str(requestBody.get("player")));
        String backupId = str(requestBody.get("backupId"));
        if (ownerId == null || backupId == null || backupId.isEmpty()) {
            return failure(response, "inventory.error.unknownBackup", String.valueOf(backupId));
        }
        boolean clearBefore = requestBody.get("clearBefore") == null
                || Boolean.parseBoolean(String.valueOf(requestBody.get("clearBefore")));

        // Ein Restore legt Items im Spiel an. Ein uebernommenes Web-Login waere ohne Bremse
        // eine Item-Fabrik - deshalb eine harte Obergrenze pro Zeitfenster, unabhaengig davon,
        // wie viele verschiedene Spieler das Ziel sind.
        if (!restoreRateLimiter.tryAcquire()) {
            return failure(response, "inventory.error.rateLimited", String.valueOf(RESTORE_LIMIT_PER_MINUTE));
        }

        // Ein laufendes Match oder Event nicht ueberschreiben: der Spieler traegt dort das Kit,
        // und ein eingespieltes Survival-Inventar waere sowohl fuer ihn als auch fuer das
        // Match-Ergebnis ein Problem.
        if (ep.getInventoryGuard() != null && ep.getInventoryGuard().hasOpenSession(ownerId)) {
            return failure(response, "inventory.error.sessionActive", ownerId.toString());
        }

        try {
            Optional<de.zfzfg.core.inventory.BackupRef> ref =
                    await(ep.getInventoryBackupService().resolve(ownerId, backupId));
            if (ref.isEmpty()) {
                return failure(response, "inventory.error.unknownBackup", backupId);
            }
            de.zfzfg.core.inventory.RestoreMode mode = de.zfzfg.core.inventory.RestoreMode.builder()
                    .clearBefore(clearBefore)
                    .build();
            de.zfzfg.core.inventory.RestoreOutcome outcome =
                    await(ep.getInventoryBackupService().restore(ownerId, ref.get(), mode));

            // Nachvollziehbarkeit: ein Restore ueber das Panel ist effektiv ein Item-Vorgang
            // mit Wirtschaftsfolgen und gehoert deshalb ins Serverlog.
            plugin.getLogger().warning("[Web-API] Restored backup '" + backupId + "' for " + ownerId  // i18n-ignore: web API internal log
                    + " -> " + outcome.name());

            if (!outcome.isSuccess()) {
                return failure(response, "inventory.error.restoreFailed", outcome.name());
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("outcome", outcome.name());
            data.put("queued", outcome == de.zfzfg.core.inventory.RestoreOutcome.QUEUED_FOR_JOIN);
            response.put("success", true);
            response.put("data", data);
        } catch (Exception e) {
            return failure(response, "inventory.error.restoreFailed", String.valueOf(e.getMessage()));
        }
        return response;
    }

    /** POST /api/inventories/delete - loescht ein einzelnes Backup. */
    public Map<String, Object> deleteInventory(Map<String, Object> requestBody) {
        Map<String, Object> response = new LinkedHashMap<>();
        EventPlugin ep = eventPlugin();
        if (ep == null) {
            return failure(response, "inventory.error.unavailable", "");
        }
        UUID ownerId = resolvePlayer(str(requestBody.get("player")));
        String backupId = str(requestBody.get("backupId"));
        if (ownerId == null || backupId == null || backupId.isEmpty()) {
            return failure(response, "inventory.error.unknownBackup", String.valueOf(backupId));
        }
        try {
            Optional<de.zfzfg.core.inventory.BackupRef> ref =
                    await(ep.getInventoryBackupService().resolve(ownerId, backupId));
            if (ref.isEmpty()) {
                return failure(response, "inventory.error.unknownBackup", backupId);
            }
            plugin.getLogger().warning("[Web-API] Deleting backup '" + backupId + "' of " + ownerId);  // i18n-ignore: web API internal log
            boolean deleted = await(ep.getInventoryBackupService().delete(ref.get()));
            response.put("success", deleted);
            if (!deleted) {
                return failure(response, "inventory.error.deleteFailed", backupId);
            }
        } catch (Exception e) {
            return failure(response, "inventory.error.deleteFailed", String.valueOf(e.getMessage()));
        }
        return response;
    }

    /**
     * GET /api/inventories/guard - offene Sitzungen des Guard-Journals.
     *
     * <p>Das Ops-Fenster fuer haengende Inventare: hier sieht ein Admin, wessen Sitzung nach
     * einem Absturz offen blieb, statt es aus Spielerbeschwerden erschliessen zu muessen.</p>
     */
    public Map<String, Object> getInventoryGuard() {
        Map<String, Object> response = new LinkedHashMap<>();
        EventPlugin ep = eventPlugin();
        if (ep == null || ep.getInventoryGuard() == null) {
            return failure(response, "inventory.error.unavailable", "");
        }
        List<Map<String, Object>> sessions = new ArrayList<>();
        for (de.zfzfg.core.inventory.guard.GuardEntry entry : ep.getInventoryGuard().openSessions()) {
            Map<String, Object> map = entry.toMap();
            org.bukkit.OfflinePlayer offline = org.bukkit.Bukkit.getOfflinePlayer(entry.playerId());
            map.put("playerName", offline.getName() == null ? entry.playerId().toString() : offline.getName());
            map.put("online", offline.isOnline());
            sessions.add(map);
        }
        // Rueckkehr-Positionen gehoeren daneben: eine offene Sitzung ohne Position und eine
        // Position ohne Sitzung sind zwei verschiedene Stoerungen, und ein Admin muss beide
        // im selben Fenster sehen koennen.
        List<Map<String, Object>> returnLocations = new ArrayList<>();
        if (ep.getReturnLocations() != null) {
            for (de.zfzfg.core.location.StoredReturn entry : ep.getReturnLocations().all()) {
                Map<String, Object> map = entry.toMap();
                org.bukkit.OfflinePlayer offline = org.bukkit.Bukkit.getOfflinePlayer(entry.playerId());
                map.put("playerName", offline.getName() == null ? entry.playerId().toString() : offline.getName());
                map.put("online", offline.isOnline());
                returnLocations.add(map);
            }
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("sessions", sessions);
        data.put("returnLocations", returnLocations);
        response.put("success", true);
        response.put("data", data);
        return response;
    }

    // ---- Helfer der Inventar-Endpunkte ----

    private EventPlugin eventPlugin() {
        return plugin instanceof EventPlugin ? (EventPlugin) plugin : null;
    }

    private static String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    /**
     * Wartet auf ein Future der Inventar-API.
     *
     * <p>Zulaessig, weil HTTP-Handler auf eigenen Threads laufen - die Futures werden vom
     * Haupt-Thread komplettiert, ein Blockieren dort waere ein Deadlock. Das Zeitlimit
     * verhindert, dass ein haengender Request den HTTP-Thread dauerhaft bindet.</p>
     */
    private static <T> T await(java.util.concurrent.CompletableFuture<T> future) throws Exception {
        return future.get(10, java.util.concurrent.TimeUnit.SECONDS);
    }

    /** Loest Name oder UUID zu einer Spieler-UUID auf. */
    private UUID resolvePlayer(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ignored) {
            // Kein UUID-Format - als Name behandeln
        }
        org.bukkit.entity.Player online = org.bukkit.Bukkit.getPlayerExact(raw);
        if (online != null) {
            return online.getUniqueId();
        }
        for (org.bukkit.OfflinePlayer offline : org.bukkit.Bukkit.getOfflinePlayers()) {
            if (raw.equalsIgnoreCase(offline.getName())) {
                return offline.getUniqueId();
            }
        }
        return null;
    }

    private List<Map<String, Object>> describeItems(org.bukkit.inventory.ItemStack[] items) {
        List<Map<String, Object>> list = new ArrayList<>();
        if (items == null) {
            return list;
        }
        for (int i = 0; i < items.length; i++) {
            Map<String, Object> described = describeItem(items[i], i);
            if (described != null) {
                list.add(described);
            }
        }
        return list;
    }

    private Map<String, Object> describeItem(org.bukkit.inventory.ItemStack item, int slot) {
        if (item == null || item.getType() == org.bukkit.Material.AIR) {
            return null;
        }
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("slot", slot);
        map.put("material", item.getType().name());
        map.put("amount", item.getAmount());
        if (item.hasItemMeta()) {
            org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                if (meta.hasDisplayName()) {
                    map.put("displayName", de.zfzfg.core.util.Text.plain(de.zfzfg.core.util.Text.toLegacy(meta.displayName())));
                }
                if (meta.hasLore()) {
                    List<net.kyori.adventure.text.Component> loreComponents = meta.lore();
                    if (loreComponents != null) {
                        map.put("lore", loreComponents.stream()
                            .map(c -> de.zfzfg.core.util.Text.plain(de.zfzfg.core.util.Text.toLegacy(c)))
                            .toList());
                    }
                }
                if (!meta.getEnchants().isEmpty()) {
                    Map<String, Integer> enchants = new LinkedHashMap<>();
                    meta.getEnchants().forEach((ench, lvl) -> enchants.put(ench.getKey().getKey(), lvl));
                    map.put("enchantments", enchants);
                }
            }
        }
        return map;
    }

    // ============ Multiverse-Welten API ============

    /**
     * GET /api/mvworlds/list - Welten, die auf dem Server tatsaechlich existieren.
     *
     * <p>Speist das World-ID-Dropdown und die Weltenuebersicht im Panel. Zu jeder Welt kommt
     * mit, wo sie in den Konfigurationen bereits verwendet wird ({@code usedBy}), damit der
     * Nutzer eine Doppelbelegung sieht, bevor er ein Preset anlegt.</p>
     */
    public Map<String, Object> getMvWorlds() {
        Map<String, Object> response = new LinkedHashMap<>();
        Map<String, Object> data = new LinkedHashMap<>();

        de.zfzfg.core.world.mv.MvWorldService service = getMvService();
        if (service == null) {
            data.put("available", false);
            data.put("backend", "NONE");
            data.put("supportsAdvancedOptions", false);
            data.put("worlds", new ArrayList<>());
            response.put("success", true);
            response.put("data", data);
            return response;
        }

        List<de.zfzfg.core.world.mv.MvWorldInfo> serverWorlds;
        try {
            serverWorlds = service.listWorldsSync();
        } catch (Exception e) {
            // Bewusst ein Fehler und keine leere Liste: das Panel wuerde sonst jede konfigurierte
            // Welt als Platzhalter anzeigen und "Welt erstellen" anbieten, obwohl sie existiert.
            plugin.getLogger().log(Level.WARNING, "[Web-API] Could not list server worlds", e);  // i18n-ignore: web API internal log
            return failure(response, "mv.error.listFailed", String.valueOf(e.getMessage()));
        }

        Map<String, List<Map<String, Object>>> usage = buildWorldUsageIndex();
        List<Map<String, Object>> worlds = new ArrayList<>();
        for (de.zfzfg.core.world.mv.MvWorldInfo info : serverWorlds) {
            Map<String, Object> entry = info.toMap();
            entry.put("usedBy", usage.getOrDefault(info.getName().toLowerCase(Locale.ROOT), new ArrayList<>()));
            worlds.add(entry);
        }

        data.put("available", service.isAvailable());
        data.put("backend", service.getBackendId());
        data.put("supportsAdvancedOptions", service.supportsAdvancedCreateOptions());
        data.put("worlds", worlds);

        response.put("success", true);
        response.put("data", data);
        return response;
    }

    /**
     * POST /api/mvworlds/create - legt eine Welt ueber Multiverse an.
     *
     * <p>Antwortet sofort mit einer Job-ID; die Chunk-Generierung kann deutlich laenger dauern
     * als ein HTTP-Request leben sollte. Das Panel pollt {@code /api/mvworlds/job}.</p>
     */
    public Map<String, Object> createMvWorld(Map<String, Object> requestBody) {
        Map<String, Object> response = new LinkedHashMap<>();
        try {
            de.zfzfg.core.world.mv.MvWorldService service = requireMvService();
            de.zfzfg.core.world.mv.MvCreateSpec spec =
                    de.zfzfg.core.world.mv.MvCreateSpec.fromMap(requestBody);
            plugin.getLogger().info("[Web-API] Creating world '" + spec.getName() + "'");  // i18n-ignore: web API internal log
            response.put("success", true);
            response.put("jobId", service.createWorld(spec).id);
        } catch (de.zfzfg.core.world.mv.MvInputException e) {
            failure(response, e.getMessageKey(), e.getDetail());
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "[Web-API] Error creating world", e);  // i18n-ignore: web API internal log
            failure(response, de.zfzfg.core.world.mv.MvResult.GENERIC_ERROR, String.valueOf(e.getMessage()));
        }
        return response;
    }

    /**
     * POST /api/mvworlds/action - {@code load}, {@code unload} oder {@code delete}.
     *
     * <p>{@code delete} ist unwiderruflich; das Panel verlangt dafuer eine Tippbestaetigung und
     * schickt standardmaessig {@code backup:true} mit.</p>
     */
    public Map<String, Object> mvWorldAction(Map<String, Object> requestBody) {
        Map<String, Object> response = new LinkedHashMap<>();
        try {
            de.zfzfg.core.world.mv.MvWorldService service = requireMvService();
            String action = String.valueOf(requestBody.get("action")).toLowerCase(Locale.ROOT);
            String world = requestBody.get("world") == null ? "" : String.valueOf(requestBody.get("world"));
            Object backupRaw = requestBody.get("backup");
            boolean backup = backupRaw == null || Boolean.parseBoolean(String.valueOf(backupRaw));

            de.zfzfg.core.world.mv.MvWorldService.MvJob job;
            switch (action) {
                case "load":
                    job = service.loadWorld(world);
                    break;
                case "unload":
                    job = service.unloadWorld(world);
                    break;
                case "delete":
                    plugin.getLogger().warning("[Web-API] Deleting world '" + world + "' (backup=" + backup + ")");  // i18n-ignore: web API internal log
                    job = service.deleteWorld(world, backup);
                    break;
                default:
                    return failure(response, "mv.error.unknownAction", action);
            }
            response.put("success", true);
            response.put("jobId", job.id);
        } catch (de.zfzfg.core.world.mv.MvInputException e) {
            failure(response, e.getMessageKey(), e.getDetail());
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "[Web-API] World action failed", e);  // i18n-ignore: web API internal log
            failure(response, de.zfzfg.core.world.mv.MvResult.GENERIC_ERROR, String.valueOf(e.getMessage()));
        }
        return response;
    }

    /**
     * GET /api/mvworlds/backups - listet die Welt-Backups in plugins/&lt;plugin&gt;/backups/.
     */
    public Map<String, Object> getMvBackups() {
        Map<String, Object> response = new LinkedHashMap<>();
        try {
            de.zfzfg.core.world.mv.MvWorldService service = requireMvService();
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("backups", service.listBackups());
            response.put("success", true);
            response.put("data", data);
        } catch (de.zfzfg.core.world.mv.MvInputException e) {
            failure(response, e.getMessageKey(), e.getDetail());
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "[Web-API] Could not list backups", e);  // i18n-ignore: web API internal log
            failure(response, de.zfzfg.core.world.mv.MvResult.GENERIC_ERROR, String.valueOf(e.getMessage()));
        }
        return response;
    }

    /**
     * POST /api/mvworlds/backup-action - {@code restore} (Job) oder {@code delete} (direkt).
     *
     * <p>Beides betrifft ausschliesslich Zips im Backup-Ordner; {@code restore} lehnt ein
     * existierendes Ziel ab und ueberschreibt nie.</p>
     */
    public Map<String, Object> mvBackupAction(Map<String, Object> requestBody) {
        Map<String, Object> response = new LinkedHashMap<>();
        try {
            de.zfzfg.core.world.mv.MvWorldService service = requireMvService();
            String action = String.valueOf(requestBody.get("action")).toLowerCase(Locale.ROOT);
            String file = requestBody.get("file") == null ? "" : String.valueOf(requestBody.get("file"));

            switch (action) {
                case "delete":
                    plugin.getLogger().info("[Web-API] Deleting backup '" + file + "'");  // i18n-ignore: web API internal log
                    de.zfzfg.core.world.mv.MvResult deleted = service.deleteBackup(file);
                    if (deleted.isSuccess()) {
                        response.put("success", true);
                    } else {
                        failure(response, deleted.getMessageKey(), deleted.getDetail());
                    }
                    return response;
                case "restore":
                    String target = requestBody.get("target") == null ? "" : String.valueOf(requestBody.get("target"));
                    plugin.getLogger().info("[Web-API] Restoring backup '" + file + "' as world '" + target + "'");  // i18n-ignore: web API internal log
                    response.put("success", true);
                    response.put("jobId", service.restoreBackup(file, target).id);
                    return response;
                default:
                    return failure(response, "mv.error.unknownAction", action);
            }
        } catch (de.zfzfg.core.world.mv.MvInputException e) {
            failure(response, e.getMessageKey(), e.getDetail());
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "[Web-API] Backup action failed", e);  // i18n-ignore: web API internal log
            failure(response, de.zfzfg.core.world.mv.MvResult.GENERIC_ERROR, String.valueOf(e.getMessage()));
        }
        return response;
    }

    /**
     * GET /api/mvworlds/job?id=... - Fortschritt eines Auftrags.
     */
    public Map<String, Object> getMvJob(Map<String, String> query) {
        Map<String, Object> response = new LinkedHashMap<>();
        de.zfzfg.core.world.mv.MvWorldService service = getMvService();
        de.zfzfg.core.world.mv.MvWorldService.MvJob job =
                service == null ? null : service.getJob(query.get("id"));
        if (job == null) {
            return failure(response, "mv.error.unknownJob", "");
        }
        response.put("success", true);
        response.put("data", job.toMap());
        return response;
    }

    private de.zfzfg.core.world.mv.MvWorldService getMvService() {
        if (plugin instanceof EventPlugin) {
            return ((EventPlugin) plugin).getMvWorldService();
        }
        return null;
    }

    private de.zfzfg.core.world.mv.MvWorldService requireMvService() {
        de.zfzfg.core.world.mv.MvWorldService service = getMvService();
        if (service == null) {
            throw new de.zfzfg.core.world.mv.MvInputException("mv.error.unavailable");
        }
        return service;
    }

    /**
     * Fehlerantwort der Multiverse-Endpunkte.
     *
     * <p>{@code messageKey} zeigt in die Web-Bundles, damit das Panel den Grund in der Sprache
     * des Admins anzeigt; {@code detail} ist der untranslatierbare Zusatz (Multiverse-Text,
     * Exception, abgelehnter Wert) und wird dahinter in Klammern gehaengt.</p>
     */
    private Map<String, Object> failure(Map<String, Object> response, String messageKey, String detail) {
        response.put("success", false);
        response.put("messageKey", messageKey);
        response.put("detail", detail == null ? "" : detail);
        return response;
    }

    /**
     * Sammelt, welche Welt in welchem Preset bzw. Event referenziert wird.
     *
     * <p>Quelle sind bewusst die YAML-Maps des {@link WebConfigManager} und nicht die
     * Laufzeit-Manager: das Panel soll den Stand anzeigen, den es selbst bearbeitet.
     * Schluessel ist der kleingeschriebene Weltname.</p>
     */
    private Map<String, List<Map<String, Object>>> buildWorldUsageIndex() {
        Map<String, List<Map<String, Object>>> index = new LinkedHashMap<>();

        // worlds.yml: der Key eines Presets IST der Weltname.
        try {
            Object worldsSection = configManager.getWorldsAsMap().get("worlds");
            if (worldsSection instanceof Map) {
                for (Object key : ((Map<?, ?>) worldsSection).keySet()) {
                    addUsage(index, String.valueOf(key), "world", String.valueOf(key), "world-id");
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "[Web-API] Could not index worlds.yml usage", e);  // i18n-ignore: web API internal log
        }

        // config.yml: events.<id>.worlds.{lobby-world, event-world, clone-source-event-world}
        try {
            Object eventsSection = configManager.getConfigAsMap().get("events");
            if (eventsSection instanceof Map) {
                for (Map.Entry<?, ?> event : ((Map<?, ?>) eventsSection).entrySet()) {
                    if (!(event.getValue() instanceof Map)) continue;
                    Object worlds = ((Map<?, ?>) event.getValue()).get("worlds");
                    if (!(worlds instanceof Map)) continue;
                    Map<?, ?> worldsMap = (Map<?, ?>) worlds;
                    for (String field : new String[]{"lobby-world", "event-world", "clone-source-event-world"}) {
                        Object value = worldsMap.get(field);
                        if (value != null && !String.valueOf(value).trim().isEmpty()) {
                            addUsage(index, String.valueOf(value), "event", String.valueOf(event.getKey()), field);
                        }
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "[Web-API] Could not index config.yml usage", e);  // i18n-ignore: web API internal log
        }

        // worlds.yml: clone-source-world zeigt ebenfalls auf eine echte Welt.
        try {
            Object worldsSection = configManager.getWorldsAsMap().get("worlds");
            if (worldsSection instanceof Map) {
                for (Map.Entry<?, ?> entry : ((Map<?, ?>) worldsSection).entrySet()) {
                    if (!(entry.getValue() instanceof Map)) continue;
                    Object source = ((Map<?, ?>) entry.getValue()).get("clone-source-world");
                    if (source != null && !String.valueOf(source).trim().isEmpty()) {
                        addUsage(index, String.valueOf(source), "world",
                                String.valueOf(entry.getKey()), "clone-source-world");
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "[Web-API] Could not index clone sources", e);  // i18n-ignore: web API internal log
        }

        return index;
    }

    private void addUsage(Map<String, List<Map<String, Object>>> index,
                          String worldName, String type, String id, String field) {
        String key = worldName.trim().toLowerCase(Locale.ROOT);
        if (key.isEmpty()) return;
        Map<String, Object> usage = new LinkedHashMap<>();
        usage.put("type", type);
        usage.put("id", id);
        usage.put("field", field);
        index.computeIfAbsent(key, k -> new ArrayList<>()).add(usage);
    }

    // ============ Equipment API ============

    /**
     * GET /api/equipment/get - Gibt die equipment.yml zurück
     */
    public Map<String, Object> getEquipment() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("data", configManager.getEquipmentAsMap());
        return response;
    }

    /**
     * POST /api/equipment/save - Speichert die equipment.yml
     */
    public Map<String, Object> saveEquipment(Map<String, Object> requestBody) {
        Map<String, Object> response = new LinkedHashMap<>();
        try {
            plugin.getLogger().info("[Web-API] Saving equipment.yml...");  // i18n-ignore: web API internal log
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) requestBody.get("data");
            if (data != null) {
                configManager.saveEquipmentFromMap(data);
                plugin.getLogger().info("[Web-API] equipment.yml saved");  // i18n-ignore: web API internal log
                response.put("success", true);
                response.put("message", "Equipment saved");  // i18n-ignore: JSON-Feld, das das Panel nicht anzeigt (nur /api/reload wird gerendert)
            } else {
                plugin.getLogger().warning("[Web-API] No data received for equipment.yml");  // i18n-ignore: web API internal log
                response.put("success", false);
                response.put("message", "No data received");  // i18n-ignore: JSON-Feld, das das Panel nicht anzeigt (nur /api/reload wird gerendert)
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "[Web-API] Error saving equipment.yml", e);  // i18n-ignore: web API internal log
            response.put("success", false);
            response.put("message", "Error: " + e.getMessage());  // i18n-ignore: JSON-Feld, das das Panel nicht anzeigt (nur /api/reload wird gerendert)
        }
        return response;
    }

    // ============ Item-Katalog API ============

    /**
     * GET /api/materials - Items und Verzauberungen des laufenden Servers.
     *
     * <p>Ersetzt die frueher im Panel fest einprogrammierten Item-Listen. Weil sich der
     * Katalog zur Laufzeit nicht aendert, uebernimmt {@link MaterialCatalog} das Caching -
     * dieser Endpunkt reicht nur durch.</p>
     */
    public Map<String, Object> getMaterials() {
        Map<String, Object> response = new LinkedHashMap<>();
        try {
            response.put("success", true);
            response.put("data", MaterialCatalog.get());
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "[Web-API] Error building material catalog", e);  // i18n-ignore: web API internal log
            response.put("success", false);
            response.put("messageKey", "items.error.catalogFailed");
            response.put("detail", e.getMessage());
        }
        return response;
    }

    // ============ Web-Config API ============

    /**
     * GET /api/webconfig/get - Gibt die web-config.yml zurück
     */
    public Map<String, Object> getWebConfig() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("data", configManager.getWebConfigAsMap());
        return response;
    }

    /**
     * POST /api/webconfig/save - Speichert die web-config.yml
     */
    public Map<String, Object> saveWebConfig(Map<String, Object> requestBody) {
        Map<String, Object> response = new LinkedHashMap<>();
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) requestBody.get("data");
            if (data != null) {
                configManager.saveWebConfigFromMap(data);
                response.put("success", true);
                response.put("message", "Web config saved");  // i18n-ignore: JSON-Feld, das das Panel nicht anzeigt (nur /api/reload wird gerendert)
            } else {
                response.put("success", false);
                response.put("message", "No data received");  // i18n-ignore: JSON-Feld, das das Panel nicht anzeigt (nur /api/reload wird gerendert)
            }
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error: " + e.getMessage());  // i18n-ignore: JSON-Feld, das das Panel nicht anzeigt (nur /api/reload wird gerendert)
        }
        return response;
    }

    // ============ Reload & Status API ============

    /**
     * POST /api/reload - Lädt alle Konfigurationen neu
     */
    public Map<String, Object> reload() {
        Map<String, Object> response = new LinkedHashMap<>();
        try {
            plugin.getLogger().info("[Web-API] Reload requested...");  // i18n-ignore: web API internal log
            
            // Versuche den vollständigen Reload über ConfigurationService
            if (plugin instanceof EventPlugin) {
                EventPlugin eventPlugin = (EventPlugin) plugin;
                if (eventPlugin.getConfigurationService() != null) {
                    eventPlugin.getConfigurationService().reloadAll();
                    plugin.getLogger().info("[Web-API] ConfigurationService.reloadAll() successful");  // i18n-ignore: web API internal log
                } else {
                    // Fallback
                    configManager.reload();
                    plugin.getLogger().info("[Web-API] Fallback: WebConfigManager.reload()");  // i18n-ignore: web API internal log
                }
            } else {
                configManager.reload();
                plugin.getLogger().info("[Web-API] WebConfigManager.reload()");  // i18n-ignore: web API internal log
            }
            
            response.put("success", true);
            // Als einzige Antwort dieser Klasse landet "message" von /api/reload
            // sichtbar im Panel: app.js setzt sie in den uebersetzten Toast
            // server.reloadSuccess / server.reloadError ein. Deshalb hier kein
            // deutscher Satz mehr -- der Rahmen ist bereits lokalisiert, und der
            // Fehlerfall reicht nur den (ohnehin unuebersetzbaren) Exception-Text
            // durch.
            response.put("message", "OK");  // i18n-ignore: neutrales Protokoll-Token, das Panel formuliert den Satz
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "[Web-API] Reload error: " + e.getMessage(), e);  // i18n-ignore: web API internal log
            response.put("success", false);
            response.put("message", e.getMessage() != null ? e.getMessage() : "");  // i18n-ignore: Exception-Text, vom Panel lokalisiert gerahmt
        }
        return response;
    }

    /**
     * GET /api/status - Gibt den Plugin-Status zurück
     */
    public Map<String, Object> getStatus() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("pluginName", plugin.getDescription().getName());
        status.put("pluginVersion", plugin.getDescription().getVersion());
        status.put("serverVersion", plugin.getServer().getVersion());
        status.put("onlinePlayers", plugin.getServer().getOnlinePlayers().size());
        status.put("maxPlayers", plugin.getServer().getMaxPlayers());
        status.put("tps", getTps());
        status.put("uptime", getUptime());
        
        // Aktuelle Sprache hinzufügen
        String currentLang = "en";
        if (plugin instanceof EventPlugin) {
            EventPlugin eventPlugin = (EventPlugin) plugin;
            if (eventPlugin.getCoreConfigManager() != null) {
                currentLang = eventPlugin.getCoreConfigManager().getLanguage();
            }
        }
        status.put("language", currentLang);
        
        response.put("data", status);
        return response;
    }
    
    /**
     * GET /api/language/get - Gibt die aktuelle Sprache zurück
     */
    public Map<String, Object> getLanguage() {
        Map<String, Object> response = new LinkedHashMap<>();
        String currentLang = "en";
        if (plugin instanceof EventPlugin) {
            EventPlugin eventPlugin = (EventPlugin) plugin;
            if (eventPlugin.getCoreConfigManager() != null) {
                currentLang = eventPlugin.getCoreConfigManager().getLanguage();
                plugin.getLogger().info("[Web-API] getLanguage() returning: " + currentLang);  // i18n-ignore: web API internal log
            } else {
                plugin.getLogger().warning("[Web-API] CoreConfigManager is null!");  // i18n-ignore: web API internal log
            }
        } else {
            plugin.getLogger().warning("[Web-API] Plugin is not EventPlugin!");  // i18n-ignore: web API internal log
        }
        response.put("success", true);
        response.put("language", currentLang);
        return response;
    }
    
    /**
     * POST /api/language/save - Speichert die Sprache in der config.yml
     */
    public Map<String, Object> saveLanguage(Map<String, Object> requestBody) {
        Map<String, Object> response = new LinkedHashMap<>();
        try {
            String language = (String) requestBody.get("language");
            if (language == null || language.isEmpty()) {
                response.put("success", false);
                response.put("message", "No language specified");  // i18n-ignore: JSON-Feld, das das Panel nicht anzeigt (nur /api/reload wird gerendert)
                return response;
            }
            
            // Sprache in config.yml speichern
            if (plugin instanceof EventPlugin) {
                EventPlugin eventPlugin = (EventPlugin) plugin;
                if (eventPlugin.getCoreConfigManager() != null) {
                    eventPlugin.getCoreConfigManager().setLanguage(language);
                    plugin.getLogger().info("[Web-API] Language changed to: " + language);  // i18n-ignore: web API internal log
                    response.put("success", true);
                    response.put("message", "Language saved");  // i18n-ignore: JSON-Feld, das das Panel nicht anzeigt (nur /api/reload wird gerendert)
                    response.put("language", language);
                } else {
                    response.put("success", false);
                    response.put("message", "CoreConfigManager not available");  // i18n-ignore: JSON-Feld, das das Panel nicht anzeigt (nur /api/reload wird gerendert)
                }
            } else {
                response.put("success", false);
                response.put("message", "Plugin type not supported");  // i18n-ignore: JSON-Feld, das das Panel nicht anzeigt (nur /api/reload wird gerendert)
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "[Web-API] Error saving language", e);  // i18n-ignore: web API internal log
            response.put("success", false);
            response.put("message", "Error: " + e.getMessage());  // i18n-ignore: JSON-Feld, das das Panel nicht anzeigt (nur /api/reload wird gerendert)
        }
        return response;
    }

    /**
     * Berechnet die Server-TPS (Ticks per Second)
     */
    private double getTps() {
        try {
            double[] tps = org.bukkit.Bukkit.getServer().getTPS();
            if (tps != null && tps.length > 0) {
                return Math.round(Math.min(20.0, tps[0]) * 100.0) / 100.0;
            }
        } catch (Throwable ignored) {
        }
        return 20.0;
    }

    /**
     * Berechnet die Server-Uptime
     */
    private String getUptime() {
        try {
            long uptimeMillis = System.currentTimeMillis() - 
                java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime();
            long seconds = uptimeMillis / 1000;
            long minutes = seconds / 60;
            long hours = minutes / 60;
            long days = hours / 24;
            
            if (days > 0) {
                return String.format("%dd %dh %dm", days, hours % 24, minutes % 60);  // i18n-ignore: JSON-Feld, das das Panel nicht anzeigt (nur /api/reload wird gerendert)
            } else if (hours > 0) {
                return String.format("%dh %dm", hours, minutes % 60);  // i18n-ignore: JSON-Feld, das das Panel nicht anzeigt (nur /api/reload wird gerendert)
            } else {
                return String.format("%dm %ds", minutes, seconds % 60);  // i18n-ignore: JSON-Feld, das das Panel nicht anzeigt (nur /api/reload wird gerendert)
            }
        } catch (Exception e) {
            return "Unbekannt";
        }
    }
}
