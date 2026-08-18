package de.zfzfg.core.inventory.mvi;

import de.zfzfg.eventplugin.EventPlugin;
import de.zfzfg.eventplugin.model.EventConfig;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Alles, was dieses Plugin ueber Multiverse-Inventories wissen muss - an einer Stelle.
 *
 * <p>Bewusst ohne Compile-Abhaengigkeit: Multiverse-Inventories wird ueber den Plugin-Namen
 * gefunden und seine Gruppen werden lesend aus der YAML-Datei gezogen. Ein API-Hook wuerde
 * das Plugin an eine bestimmte Multiverse-Version binden, ohne dass wir dafuer mehr
 * bekaemen als die Gruppenliste, die ohnehin auf der Platte liegt.</p>
 *
 * <p>Der Zustand wird einmal beim Start und danach nur bei einem Reload berechnet: der
 * Dateizugriff hat auf dem Haupt-Thread eines laufenden Servers nichts verloren.</p>
 */
public final class MultiverseInventoriesBridge {

    /** Plugin-Name in der plugin.yml von Multiverse-Inventories. */
    private static final String MVI_PLUGIN = "Multiverse-Inventories";  // i18n-ignore: Plugin-Name im Sinne von plugin.yml, kein Anzeigetext

    /**
     * Verzoegerung, mit der nach einem Weltwechsel wiederhergestellt wird, wenn
     * Multiverse-Inventories mitspielt.
     *
     * <p>Drei Ticks sind kein Ratespiel mehr, sondern nur noch Puffer: der eigentliche
     * Schutz sind der MONITOR-Listener und die Nachkontrolle in
     * {@code InventorySessionManager.finish}. Ohne Multiverse-Inventories ist die
     * Verzoegerung 0.</p>
     */
    private static final int DEFAULT_DELAY_TICKS = 3;

    private final EventPlugin plugin;
    private final AtomicInteger recoveries = new AtomicInteger();

    private boolean installed;
    private String version = "";
    private MviConflictReport report = MviConflictReport.notInstalled();

    public MultiverseInventoriesBridge(EventPlugin plugin) {
        this.plugin = plugin;
        refresh();
    }

    /** Erkennung und Diagnose neu ausfuehren - beim Start und nach jedem Config-Reload. */
    @SuppressWarnings("deprecation")
    public void refresh() {
        Plugin mvi = Bukkit.getPluginManager().getPlugin(MVI_PLUGIN);
        this.installed = mvi != null;
        if (!installed) {
            this.version = "";
            this.report = MviConflictReport.notInstalled();
            return;
        }
        this.version = mvi.getDescription().getVersion();
        this.report = diagnose(mvi);
    }

    /** Ob Multiverse-Inventories geladen ist. */
    public boolean isInstalled() {
        return installed;
    }

    /** Version von Multiverse-Inventories, oder leer wenn es nicht laeuft. */
    public String version() {
        return version;
    }

    /** Das zuletzt berechnete Diagnoseergebnis. */
    public MviConflictReport report() {
        return report;
    }

    /**
     * Ob der Konfliktschutz laufen soll.
     *
     * <p>Nur sinnvoll, wenn beide Seiten aktiv sind: Multiverse-Inventories installiert und
     * das Plugin selbst fuer die Inventare zustaendig. Im Legacy-Betrieb tauscht
     * Multiverse-Inventories ja bewusst allein - dort waere jedes Nachfassen ein Angriff auf
     * die gewollte Konfiguration.</p>
     */
    public boolean conflictGuardActive() {
        return installed
                && plugin.getInventoryConfig().managedByPlugin()
                && plugin.getInventoryConfig().mviConflictGuard();
    }

    /**
     * Wie viele Ticks nach einem Rueckteleport gewartet wird, bevor wiederhergestellt wird.
     *
     * @return 0, wenn Multiverse-Inventories nicht laeuft - dann gibt es nichts abzuwarten
     */
    public long restoreDelayTicks() {
        if (!installed) {
            return 0L;
        }
        int configured = plugin.getInventoryConfig().mviRestoreDelayTicks();
        return configured < 0 ? DEFAULT_DELAY_TICKS : configured;
    }

    /** Zaehlt einen Fall, in dem ein Restore nachgezogen werden musste. */
    public void countRecovery() {
        recoveries.incrementAndGet();
    }

    /** Wie oft der Schutz seit dem Start eingegriffen hat. */
    public int recoveries() {
        return recoveries.get();
    }

    // ------------------------------------------------------------------ Diagnose

    /**
     * Gleicht die Weltgruppen von Multiverse-Inventories gegen die verwalteten Welten ab.
     *
     * <p>Gefaehrlich ist eine Gruppe erst, wenn sie neben der Arena- oder Event-Welt noch
     * mindestens eine weitere Welt enthaelt: dann tauscht Multiverse-Inventories beim
     * Betreten das Inventar aus - genau in dem Moment, in dem dieses Plugin sein Backup
     * anlegt.</p>
     */
    private MviConflictReport diagnose(Plugin mvi) {
        Set<String> managed = managedWorlds();
        if (managed.isEmpty()) {
            return MviConflictReport.of(version, new ArrayList<MviConflictReport.Collision>());
        }

        File groupsFile = new File(mvi.getDataFolder(), "groups.yml");
        if (!groupsFile.isFile()) {
            // Ohne Gruppendatei tauscht Multiverse-Inventories nichts aus - das ist der
            // harmlose Fall, keine Unsicherheit.
            return MviConflictReport.of(version, new ArrayList<MviConflictReport.Collision>());
        }

        FileConfiguration groups;
        try {
            groups = YamlConfiguration.loadConfiguration(groupsFile);
        } catch (Exception e) {
            plugin.getLogger().warning(plugin.getConsoleMsg("inventory-mvinv-groups-unreadable",
                    "error", String.valueOf(e.getMessage())));
            return MviConflictReport.unreadable(version);
        }

        ConfigurationSection root = groups.getConfigurationSection("groups");
        if (root == null) {
            return MviConflictReport.of(version, new ArrayList<MviConflictReport.Collision>());
        }

        List<MviConflictReport.Collision> collisions = new ArrayList<>();
        for (String groupName : root.getKeys(false)) {
            ConfigurationSection group = root.getConfigurationSection(groupName);
            if (group == null) {
                continue;
            }
            List<String> worlds = readWorldList(group);
            if (worlds.size() < 2) {
                // Eine Gruppe mit hoechstens einer Welt hat keinen zweiten Zustand, zwischen
                // dem sie wechseln koennte.
                continue;
            }
            for (String world : worlds) {
                if (!managed.contains(world.toLowerCase(Locale.ROOT))) {
                    continue;
                }
                List<String> partners = new ArrayList<>(worlds);
                partners.remove(world);
                collisions.add(new MviConflictReport.Collision(world, groupName, partners));
            }
        }
        return MviConflictReport.of(version, collisions);
    }

    /**
     * Liest die Weltliste einer Gruppe.
     *
     * <p>Aeltere und neuere Fassungen von Multiverse-Inventories schreiben {@code worlds}
     * mal als YAML-Liste, mal als Abschnitt mit den Weltnamen als Schluessel. Beide Formen
     * werden akzeptiert, damit die Diagnose nicht an einer Versionsfrage scheitert.</p>
     */
    private List<String> readWorldList(ConfigurationSection group) {
        Set<String> worlds = new LinkedHashSet<>(group.getStringList("worlds"));
        if (worlds.isEmpty()) {
            ConfigurationSection section = group.getConfigurationSection("worlds");
            if (section != null) {
                worlds.addAll(section.getKeys(false));
            }
        }
        return new ArrayList<>(worlds);
    }

    /**
     * Alle Welten, in denen dieses Plugin Inventare anfasst - kleingeschrieben zum
     * Vergleichen, weil Multiverse-Inventories Weltnamen unabhaengig von der Schreibweise
     * fuehrt.
     */
    private Set<String> managedWorlds() {
        Set<String> managed = new LinkedHashSet<>();

        FileConfiguration worlds = plugin.getCoreConfigManager().getWorlds();
        if (worlds != null) {
            ConfigurationSection section = worlds.getConfigurationSection("worlds");
            if (section != null) {
                for (String world : section.getKeys(false)) {
                    managed.add(world.toLowerCase(Locale.ROOT));
                }
            }
        }

        if (plugin.getConfigManager() != null) {
            Map<String, EventConfig> events = plugin.getConfigManager().getAllEvents();
            if (events != null) {
                for (EventConfig event : events.values()) {
                    addIfPresent(managed, event.getEventWorld());
                    addIfPresent(managed, event.getLobbyWorld());
                }
            }
        }

        // Die Hauptwelt gehoert nicht dazu: dorthin kehren die Spieler zurueck, dort soll
        // Multiverse-Inventories ungestoert weiterarbeiten duerfen.
        if (plugin.getConfigManager() != null) {
            String main = plugin.getConfigManager().getMainWorld();
            if (main != null) {
                managed.remove(main.toLowerCase(Locale.ROOT));
            }
        }
        return managed;
    }

    private void addIfPresent(Set<String> target, String world) {
        if (world != null && !world.trim().isEmpty()) {
            target.add(world.trim().toLowerCase(Locale.ROOT));
        }
    }
}
