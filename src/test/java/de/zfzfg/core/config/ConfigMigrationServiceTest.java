package de.zfzfg.core.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.Collections;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Prueft die Migration bestehender Konfigurationsdateien auf den Stand dieser Version.
 *
 * <p>Diese Migration schreibt in die Dateien eines laufenden Servers. Ein Fehler darin faellt
 * nicht als Absturz auf, sondern still: eine vom Admin geaenderte Einstellung wird auf den
 * Standard zurueckgesetzt, ein bewusst geloeschtes Beispiel-Event kehrt zurueck, oder ein
 * Schalter wird umgeschrieben, den es schon gab. Deshalb eigene Tests, und deshalb liegt die
 * Umschreibelogik getrennt vom Dateizugriff - genau wie in {@link EquipmentSchemaMigration}.</p>
 */
class ConfigMigrationServiceTest {

    private static final int V1 = ConfigMigrationService.LEGACY_VERSION;
    private static final Set<String> ID_SECTIONS = ConfigMigrationService.ID_SECTIONS;

    private static YamlConfiguration yaml(String content) {
        YamlConfiguration config = new YamlConfiguration();
        try {
            config.load(new StringReader(content));
        } catch (Exception e) {
            throw new IllegalArgumentException("Test-YAML ist ungueltig", e);
        }
        return config;
    }

    // ============ Versionsstempel ============

    @Test
    void treatsAFileWithoutAStampAsLegacy() {
        assertEquals(V1, ConfigMigrationService.readVersion(yaml("settings:\n  language: de\n")));
    }

    @Test
    void aStampedFileIsNotRewrittenAgain() {
        // Ohne diese Sperre wuerde eine bereits migrierte Datei bei jedem Start erneut
        // angefasst - und jedes Mal eine weitere Sicherungskopie erzeugen.
        YamlConfiguration config = yaml(
                "config-version: " + ConfigMigrationService.CURRENT_VERSION + "\n"
                        + "settings:\n"
                        + "  world-loading: none\n"
                        + "  inventory-snapshots:\n"
                        + "    enabled: false\n");

        ConfigMigrationService.Result result = ConfigMigrationService.rewriteConfig(
                config, ConfigMigrationService.CURRENT_VERSION);

        assertFalse(result.isChanged());
        assertEquals("none", config.getString("settings.world-loading"));
    }

    @Test
    void stampsOnlyOnce() {
        YamlConfiguration config = yaml("settings:\n  language: de\n");

        assertTrue(ConfigMigrationService.stampVersion(config));
        assertEquals(ConfigMigrationService.CURRENT_VERSION,
                config.getInt(ConfigMigrationService.VERSION_KEY));
        assertFalse(ConfigMigrationService.stampVersion(config));
    }

    // ============ Inventar-Verwaltung ============

    @Test
    void mapsDisabledSnapshotsToTheLegacyProvider() {
        // enabled: false hiess "ein anderes Plugin verwaltet die Inventare" - das ist der
        // neue provider 'none'. Wuerde hier 'auto' entstehen, griffe das Plugin ploetzlich
        // in Inventare ein, die vorher Multiverse-Inventories gehoerten.
        YamlConfiguration config = yaml(
                "settings:\n"
                        + "  inventory-snapshots:\n"
                        + "    enabled: false\n"
                        + "    retain-days: 30\n");

        ConfigMigrationService.rewriteConfig(config, V1);

        assertEquals("none", config.getString("settings.inventory-management.provider"));
        assertTrue(config.getBoolean("settings.inventory-management.legacy-safety-backups"),
                "im Legacy-Betrieb muss das Sicherungsnetz an sein");
        assertFalse(config.isSet("settings.inventory-snapshots"));
    }

    @Test
    void mapsEnabledSnapshotsToTheOwnProvider() {
        YamlConfiguration config = yaml(
                "settings:\n"
                        + "  inventory-snapshots:\n"
                        + "    enabled: true\n");

        ConfigMigrationService.rewriteConfig(config, V1);

        assertEquals("auto", config.getString("settings.inventory-management.provider"));
    }

    @Test
    void collapsesTheInventoryRestoreAliasToAuto() {
        // 'inventoryrestore' und 'auto' verhielten sich immer gleich - es gab nie eine
        // Codestelle, die sie unterschieden haette. Im Panel steht deshalb nur noch ein
        // Eintrag, und der Altwert muss in der Datei nachziehen.
        YamlConfiguration config = yaml(
                "settings:\n"
                        + "  inventory-management:\n"
                        + "    provider: inventoryrestore\n");

        ConfigMigrationService.rewriteConfig(config, V1);

        assertEquals("auto", config.getString("settings.inventory-management.provider"));
    }

    @Test
    void keepsTheLegacyProviderUntouched() {
        // Die Gegenprobe zur Alias-Regel: 'none' ist eine bewusste Entscheidung des Admins
        // und darf von keiner Migration eingesammelt werden.
        YamlConfiguration config = yaml(
                "settings:\n"
                        + "  inventory-management:\n"
                        + "    provider: none\n");

        ConfigMigrationService.rewriteConfig(config, V1);

        assertEquals("none", config.getString("settings.inventory-management.provider"));
    }

    @Test
    void treatsAMissingEnabledFlagAsEnabled() {
        YamlConfiguration config = yaml(
                "settings:\n"
                        + "  inventory-snapshots:\n"
                        + "    retain-days: 14\n");

        ConfigMigrationService.rewriteConfig(config, V1);

        assertEquals("auto", config.getString("settings.inventory-management.provider"));
    }

    @Test
    void doesNotTouchAnExistingProviderSetting() {
        YamlConfiguration config = yaml(
                "settings:\n"
                        + "  inventory-snapshots:\n"
                        + "    enabled: true\n"
                        + "  inventory-management:\n"
                        + "    provider: none\n");

        ConfigMigrationService.rewriteConfig(config, V1);

        assertEquals("none", config.getString("settings.inventory-management.provider"),
                "eine bereits getroffene Entscheidung darf die Migration nicht ueberschreiben");
    }

    @Test
    void reportsSettingsThatHaveNoCounterpartLeft() {
        YamlConfiguration config = yaml(
                "settings:\n"
                        + "  inventory-snapshots:\n"
                        + "    enabled: true\n"
                        + "    retain-days: 30\n"
                        + "    ids:\n"
                        + "      inventory-id-digits: 4\n");

        ConfigMigrationService.Result result = ConfigMigrationService.rewriteConfig(config, V1);

        // Diese Einstellungen verschwinden ersatzlos. Sie stillschweigend zu loeschen waere
        // schlimmer als sie zu nennen: der Admin haette sonst eine Aufbewahrungsdauer
        // eingestellt und wuesste nicht, dass sie niemand mehr liest.
        assertEquals(1, result.getWarnings().size());
        assertTrue(result.getWarnings().get(0).contains("retain-days"));
        assertTrue(result.getWarnings().get(0).contains("ids.inventory-id-digits"));
    }

    @Test
    void doesNothingWithoutTheLegacySection() {
        YamlConfiguration config = yaml("settings:\n  language: de\n");

        assertFalse(ConfigMigrationService.rewriteConfig(config, V1).isChanged());
    }

    // ============ Weltenverwaltung ============

    @Test
    void splitsWorldLoadingIntoTwoSwitches() {
        YamlConfiguration config = yaml("settings:\n  world-loading: arena\n");

        ConfigMigrationService.rewriteConfig(config, V1);

        assertFalse(config.getBoolean("settings.world-management.events"));
        assertTrue(config.getBoolean("settings.world-management.arenas"));
        assertFalse(config.isSet("settings.world-loading"));
    }

    @Test
    void mapsInoperativeCommandRestrictionValues() {
        YamlConfiguration config = yaml("settings:\n  command-restriction: join\n");

        ConfigMigrationService.rewriteConfig(config, V1);

        assertEquals("both", config.getString("settings.command-restriction"));
    }

    @Test
    void keepsAValidCommandRestriction() {
        YamlConfiguration config = yaml("settings:\n  command-restriction: lobby\n");

        ConfigMigrationService.rewriteConfig(config, V1);

        assertEquals("lobby", config.getString("settings.command-restriction"));
    }

    // ============ worlds.yml ============

    @Test
    void removesCommentLinesThatYamlReadAsKeys() {
        // In der Vorlage bis 1.0.9 fehlte diesen beiden Zeilen das '#'. Sie stehen deshalb
        // als Schluessel in der Datei jedes Servers, der die Vorlage uebernommen hat.
        YamlConfiguration worlds = yaml(
                "worlds:\n"
                        + "  PvPArena:\n"
                        + "    display-name: Arena\n"
                        + "    Optional: Quelle fuer Klon/Reset\n"
                        + "    clone-source-world: PvPArena_original\n"
                        + "    Falls spaeter fuer PvP genutzt werden soll:\n");

        ConfigMigrationService.Result result = ConfigMigrationService.rewriteWorlds(worlds, V1);

        assertTrue(result.isChanged());
        assertFalse(worlds.isSet("worlds.PvPArena.Optional"));
        assertFalse(worlds.isSet("worlds.PvPArena.Falls spaeter fuer PvP genutzt werden soll"));

        // Die echten Schluessel dazwischen muessen unangetastet bleiben.
        assertEquals("Arena", worlds.getString("worlds.PvPArena.display-name"));
        assertEquals("PvPArena_original", worlds.getString("worlds.PvPArena.clone-source-world"));
    }

    @Test
    void leavesACleanWorldsFileAlone() {
        YamlConfiguration worlds = yaml(
                "worlds:\n"
                        + "  PvPArena:\n"
                        + "    display-name: Arena\n");

        assertFalse(ConfigMigrationService.rewriteWorlds(worlds, V1).isChanged());
    }

    // ============ web-config.yml ============

    @Test
    void removesSupersededTextureKeys() {
        YamlConfiguration web = yaml(
                "items:\n"
                        + "  enable-textures: true\n"
                        + "  local-texture-path: /tmp/x\n"
                        + "  block-texture-source: http://example.invalid\n");

        ConfigMigrationService.Result result = ConfigMigrationService.rewriteWebConfig(web, V1);

        assertTrue(result.isChanged());
        assertFalse(web.isSet("items.local-texture-path"));
        assertFalse(web.isSet("items.block-texture-source"));
        assertTrue(web.getBoolean("items.enable-textures"));
    }

    // ============ Auffuellen fehlender Schluessel ============

    @Test
    void addsMissingKeysFromTheTemplate() {
        YamlConfiguration user = yaml("settings:\n  language: de\n");
        YamlConfiguration defaults = yaml(
                "settings:\n"
                        + "  language: en\n"
                        + "  debug: 'off'\n"
                        + "  inventory-management:\n"
                        + "    provider: auto\n"
                        + "    guard:\n"
                        + "      enabled: true\n");

        ConfigMigrationService.Result result =
                ConfigMigrationService.mergeMissing(user, defaults, ID_SECTIONS);

        assertTrue(result.isChanged());
        assertEquals("off", user.getString("settings.debug"));
        assertEquals("auto", user.getString("settings.inventory-management.provider"));
        assertTrue(user.getBoolean("settings.inventory-management.guard.enabled"),
                "auch verschachtelte Sektionen muessen vollstaendig entstehen");
    }

    @Test
    void neverOverwritesAValueTheAdminChose() {
        YamlConfiguration user = yaml(
                "settings:\n"
                        + "  language: de\n"
                        + "  lobby-countdown: 3\n");
        YamlConfiguration defaults = yaml(
                "settings:\n"
                        + "  language: en\n"
                        + "  lobby-countdown: 10\n");

        ConfigMigrationService.mergeMissing(user, defaults, ID_SECTIONS);

        assertEquals("de", user.getString("settings.language"));
        assertEquals(3, user.getInt("settings.lobby-countdown"));
    }

    @Test
    void keepsAnEmptyListTheAdminSet() {
        // Eine bewusst geleerte Liste ist eine Entscheidung, kein fehlender Schluessel.
        YamlConfiguration user = yaml("settings:\n  auto-events:\n    selected-events: []\n");
        YamlConfiguration defaults = yaml(
                "settings:\n"
                        + "  auto-events:\n"
                        + "    selected-events:\n"
                        + "      - pvparena\n");

        ConfigMigrationService.mergeMissing(user, defaults, ID_SECTIONS);

        assertTrue(user.getStringList("settings.auto-events.selected-events").isEmpty());
    }

    @Test
    void doesNotResurrectDeletedExampleEntries() {
        // Der Kern der ID-Sektionen: wer sein Beispiel-Event geloescht hat, will es nicht
        // bei jedem Serverstart wiedersehen.
        YamlConfiguration user = yaml(
                "events:\n"
                        + "  mein_event:\n"
                        + "    enabled: true\n");
        YamlConfiguration defaults = yaml(
                "events:\n"
                        + "  mein_event:\n"
                        + "    enabled: false\n"
                        + "    min-players: 2\n"
                        + "  pvparena:\n"
                        + "    enabled: true\n");

        ConfigMigrationService.mergeMissing(user, defaults, ID_SECTIONS);

        assertFalse(user.isSet("events.pvparena"), "geloeschte Beispiele duerfen nicht zurueckkehren");
        // Innerhalb eines vorhandenen Events wird dagegen sehr wohl ergaenzt, sonst kaeme
        // eine neue Event-Option nie bei bestehenden Eintraegen an.
        assertEquals(2, user.getInt("events.mein_event.min-players"));
        assertTrue(user.getBoolean("events.mein_event.enabled"));
    }

    @Test
    void appliesTheSameRuleToEquipmentAndWorlds() {
        YamlConfiguration user = yaml("equipment:\n  mein_kit:\n    display-name: Kit\n");
        YamlConfiguration defaults = yaml(
                "equipment:\n"
                        + "  mein_kit:\n"
                        + "    display-name: Anders\n"
                        + "    pvpwager-equip-enable: true\n"
                        + "  standard:\n"
                        + "    display-name: Standard\n");

        ConfigMigrationService.mergeMissing(user, defaults, ID_SECTIONS);

        assertFalse(user.isSet("equipment.standard"));
        assertEquals("Kit", user.getString("equipment.mein_kit.display-name"));
        assertTrue(user.getBoolean("equipment.mein_kit.pvpwager-equip-enable"));
    }

    @Test
    void mergesEveryKeyWhenNoSectionIsProtected() {
        // Sprachdateien laufen ohne ID-Sektionen: dort bestimmt der Admin keine Schluessel.
        YamlConfiguration user = yaml("messages:\n  console:\n    lang-loaded: Geladen\n");
        YamlConfiguration defaults = yaml(
                "messages:\n"
                        + "  console:\n"
                        + "    lang-loaded: Loaded\n"
                        + "    migration-header: Migrated\n");

        ConfigMigrationService.mergeMissing(user, defaults, Collections.emptySet());

        assertEquals("Geladen", user.getString("messages.console.lang-loaded"));
        assertEquals("Migrated", user.getString("messages.console.migration-header"));
    }

    @Test
    void survivesAMissingTemplate() {
        YamlConfiguration user = yaml("settings:\n  language: de\n");

        assertFalse(ConfigMigrationService.mergeMissing(user, null, ID_SECTIONS).isChanged());
    }

    @Test
    void recognisesManagedIdsOnlyOneLevelBelowTheRoot() {
        assertTrue(ConfigMigrationService.isManagedId("events.pvparena", ID_SECTIONS));
        assertFalse(ConfigMigrationService.isManagedId("events", ID_SECTIONS));
        assertFalse(ConfigMigrationService.isManagedId("events.pvparena.min-players", ID_SECTIONS));
        assertFalse(ConfigMigrationService.isManagedId("settings.language", ID_SECTIONS));
    }

    // ============ Idempotenz ============

    @Test
    void isIdempotent() {
        String source =
                "settings:\n"
                        + "  world-loading: both\n"
                        + "  inventory-snapshots:\n"
                        + "    enabled: false\n"
                        + "    retain-days: 30\n";
        String template =
                "config-version: " + ConfigMigrationService.CURRENT_VERSION + "\n"
                        + "settings:\n"
                        + "  language: en\n"
                        + "  inventory-management:\n"
                        + "    provider: auto\n"
                        + "    on-backup-failure: abort\n";

        YamlConfiguration config = yaml(source);
        ConfigMigrationService.Result first = ConfigMigrationService.rewriteConfig(config, V1);
        first.absorb(ConfigMigrationService.mergeMissing(config, yaml(template), ID_SECTIONS));
        ConfigMigrationService.stampVersion(config);
        assertTrue(first.isChanged());

        String afterFirst = config.saveToString();

        // Zweiter Lauf auf dem Ergebnis: darf die Datei nicht erneut anfassen, sonst legt
        // jeder Serverstart eine weitere Sicherungskopie an.
        YamlConfiguration again = yaml(afterFirst);
        int version = ConfigMigrationService.readVersion(again);
        ConfigMigrationService.Result second = ConfigMigrationService.rewriteConfig(again, version);
        second.absorb(ConfigMigrationService.mergeMissing(again, yaml(template), ID_SECTIONS));

        assertFalse(second.isChanged());
        assertFalse(ConfigMigrationService.stampVersion(again));
        assertEquals(afterFirst, again.saveToString());

        // Die Entscheidung aus der Alt-Config muss den zweiten Lauf ueberleben.
        assertEquals("none", again.getString("settings.inventory-management.provider"));
    }

    @Test
    void survivesAHandMangledFile() {
        // Eine von Hand verhunzte Datei darf die Migration nicht zum Absturz bringen.
        YamlConfiguration config = yaml("settings: \"just a string\"\n");

        assertDoesNotThrow(() -> ConfigMigrationService.rewriteConfig(config, V1));
        assertDoesNotThrow(() -> ConfigMigrationService.rewriteWorlds(
                yaml("worlds:\n  broken: \"a string\"\n"), V1));
    }
}
