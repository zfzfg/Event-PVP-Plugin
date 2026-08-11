package de.zfzfg.core.inventory;

import de.zfzfg.core.inventory.adapter.InventoryRestoreApiAdapter;
import de.zfzfg.core.inventory.adapter.NoOpInventoryBackupAdapter;
import de.zfzfg.eventplugin.EventPlugin;
import org.bukkit.Bukkit;

/**
 * Waehlt den Inventar-Provider anhand der Konfiguration und meldet beim Start, was tatsaechlich
 * aktiv ist.
 *
 * <p>Die Auswahl ist bewusst laut: welcher Provider laeuft, entscheidet darueber, ob Inventare
 * bei Matches und Events ueberhaupt gerettet werden. Ein stiller Rueckfall auf den internen
 * Speicher waere genau die Art von unsichtbarer Fehlkonfiguration, die das Plugin loswerden soll.</p>
 */
public final class InventoryBackupServiceFactory {

    private InventoryBackupServiceFactory() {
    }

    /** Ob das Plugin InventoryBackup laeuft und seine API bereitsteht. */
    public static boolean inventoryRestoreAvailable() {
        if (Bukkit.getPluginManager().getPlugin("InventoryBackup") == null) {
            return false;
        }
        if (!InventoryRestoreApiAdapter.classesPresent()) {
            return false;
        }
        return InventoryRestoreApiAdapter.runningApiVersion() > 0;
    }

    /**
     * Baut den Provider zur gewaehlten Betriebsart.
     *
     * @param config bereits geladene Einstellungen
     */
    public static InventoryBackupService create(EventPlugin plugin, InventoryManagementConfig config) {
        if (config.modeWasInvalid()) {
            plugin.getLogger().warning(plugin.getConsoleMsg("inventory-provider-invalid"));
        }

        // Der Adapter haengt NICHT an der Betriebsart: auch im Legacy-Betrieb wird
        // InventoryBackup gebraucht, dort fuer die Sicherungskopien, die bewusst nie
        // automatisch zurueckgespielt werden. Was mit den Backups geschieht, entscheidet
        // allein InventorySessionManager.
        if (inventoryRestoreAvailable()) {
            logApiVersion(plugin);
            if (config.mode() == InventoryManagementConfig.Mode.NONE) {
                plugin.getLogger().info(plugin.getConsoleMsg(config.legacySafetyBackups()
                        ? "inventory-provider-legacy-safety"
                        : "inventory-provider-legacy"));
            }
            return new InventoryRestoreApiAdapter(plugin);
        }

        if (config.mode() == InventoryManagementConfig.Mode.NONE) {
            // Im Legacy-Betrieb uebernimmt Multiverse-Inventories; ohne API entfaellt nur
            // die zusaetzliche Sicherungskopie. Kein Grund, Matches zu blockieren.
            plugin.getLogger().warning(plugin.getConsoleMsg("inventory-provider-legacy-degraded"));
            return new NoOpInventoryBackupAdapter();
        }

        // InventoryBackup steht in plugin.yml unter depend, der Server laesst dieses Plugin
        // also ohne es gar nicht erst starten. Hierher kommt man nur, wenn InventoryBackup
        // zwar geladen wurde, seine API aber nicht registriert ist - etwa weil es sich beim
        // Start selbst deaktiviert hat. Dann ist Nichtstun sicherer als ein halber Betrieb:
        // ohne Backup darf nichts geleert werden.
        plugin.getLogger().severe(plugin.getConsoleMsg("inventory-provider-missing"));
        return new NoOpInventoryBackupAdapter();
    }

    private static void logApiVersion(EventPlugin plugin) {
        int running = InventoryRestoreApiAdapter.runningApiVersion();
        int compiled = InventoryRestoreApiAdapter.compiledApiVersion();
        plugin.getLogger().info(plugin.getConsoleMsg("inventory-provider-inventoryrestore",
                "version", String.valueOf(running)));
        if (running < compiled) {
            plugin.getLogger().warning(plugin.getConsoleMsg("inventory-provider-old-api",
                    "running", String.valueOf(running), "expected", String.valueOf(compiled)));
        }
    }

}
