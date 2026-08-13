package de.zfzfg.core.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Prueft die Zusammenfuehrung der drei historischen Set-Sektionen von {@code equipment.yml}.
 *
 * <p>Diese Migration schreibt in die Konfigurationsdatei eines laufenden Servers. Ein Fehler
 * darin faellt nicht als Absturz auf, sondern als "mein Kit ist weg" oder - schlimmer - als
 * ein bewusst abgeschaltetes Set, das ploetzlich wieder ausgeteilt wird. Deshalb eigene
 * Tests, und deshalb liegt die Umschreibelogik in {@code merge()} getrennt vom Dateizugriff.</p>
 */
class EquipmentSchemaMigrationTest {

    private static YamlConfiguration yaml(String content) {
        YamlConfiguration config = new YamlConfiguration();
        try {
            config.load(new StringReader(content));
        } catch (Exception e) {
            throw new IllegalArgumentException("Test-YAML ist ungueltig", e);
        }
        return config;
    }

    // ============ Nichts zu tun ============

    @Test
    void leavesAnAlreadyUnifiedFileUntouched() {
        YamlConfiguration config = yaml(
                "equipment:\n"
                        + "  starter:\n"
                        + "    pvpwager-equip-enable: true\n"
                        + "    display-name: Starter\n");

        EquipmentSchemaMigration.Result result = EquipmentSchemaMigration.merge(config);

        assertFalse(result.isChanged(), "ohne Alt-Sektion darf nichts geschrieben werden");
        assertEquals(0, result.getMigratedSets());
        assertEquals("Starter", config.getString("equipment.starter.display-name"));
    }

    @Test
    void doesNothingOnAnEmptyFile() {
        assertFalse(EquipmentSchemaMigration.merge(yaml("")).isChanged());
    }

    // ============ Uebernahme ============

    @Test
    void movesLegacySectionsIntoTheTargetSection() {
        YamlConfiguration config = yaml(
                "equipment-sets:\n"
                        + "  starter:\n"
                        + "    display-name: Starter\n"
                        + "equipment-groups:\n"
                        + "  old_group:\n"
                        + "    display-name: Alt\n");

        EquipmentSchemaMigration.Result result = EquipmentSchemaMigration.merge(config);

        assertTrue(result.isChanged());
        assertEquals(2, result.getMigratedSets());
        assertEquals("Starter", config.getString("equipment.starter.display-name"));
        assertEquals("Alt", config.getString("equipment.old_group.display-name"));

        // Die Alt-Sektionen muessen verschwinden, sonst laeuft die Migration bei jedem
        // Start erneut und erzeugt jedes Mal eine neue Sicherungskopie.
        assertFalse(config.isConfigurationSection("equipment-sets"));
        assertFalse(config.isConfigurationSection("equipment-groups"));
    }

    @Test
    void keepsNestedItemDataIntact() {
        YamlConfiguration config = yaml(
                "equipment-sets:\n"
                        + "  starter:\n"
                        + "    armor:\n"
                        + "      helmet: DIAMOND_HELMET\n"
                        + "      helmet-enchantments:\n"
                        + "        - \"PROTECTION:4\"\n"
                        + "    inventory:\n"
                        + "      - slot: 0\n"
                        + "        item: STONE_SWORD\n"
                        + "        amount: 1\n");

        EquipmentSchemaMigration.merge(config);

        assertEquals("DIAMOND_HELMET", config.getString("equipment.starter.armor.helmet"));
        assertEquals(java.util.Collections.singletonList("PROTECTION:4"),
                config.getStringList("equipment.starter.armor.helmet-enchantments"));

        // Die Inventarliste ist eine Liste von Maps - beim Umkopieren darf sie nicht zu
        // einer Sektion mit Zahlenschluesseln werden, sonst findet getMapList() sie nicht.
        java.util.List<java.util.Map<?, ?>> inventory = config.getMapList("equipment.starter.inventory");
        assertEquals(1, inventory.size());
        assertEquals("STONE_SWORD", inventory.get(0).get("item"));
    }

    // ============ Flag-Uebersetzung ============

    @Test
    void translatesTheSharedEnabledFlagToBothSystems() {
        YamlConfiguration config = yaml(
                "equipment-sets:\n"
                        + "  disabled_set:\n"
                        + "    enabled: false\n"
                        + "  enabled_set:\n"
                        + "    enabled: true\n");

        EquipmentSchemaMigration.merge(config);

        // Ein abgeschaltetes Set muss in BEIDEN neuen Schaltern abgeschaltet ankommen -
        // sonst taucht es nach dem Update wieder im Spiel auf.
        assertFalse(config.getBoolean("equipment.disabled_set.pvpwager-equip-enable"));
        assertFalse(config.getBoolean("equipment.disabled_set.event-equip-enable"));

        assertTrue(config.getBoolean("equipment.enabled_set.pvpwager-equip-enable"));
        assertTrue(config.getBoolean("equipment.enabled_set.event-equip-enable"));

        // Der alte gemeinsame Schalter darf nicht stehenbleiben.
        assertFalse(config.contains("equipment.disabled_set.enabled"));
    }

    @Test
    void defaultsToEnabledWhenTheLegacyFlagIsMissing() {
        YamlConfiguration config = yaml(
                "equipment-sets:\n"
                        + "  starter:\n"
                        + "    display-name: Starter\n");

        EquipmentSchemaMigration.merge(config);

        // Ohne 'enabled' galt ein Set frueher als aktiv; das muss so bleiben.
        assertTrue(config.getBoolean("equipment.starter.pvpwager-equip-enable", true));
        assertTrue(config.getBoolean("equipment.starter.event-equip-enable", true));
    }

    @Test
    void alreadySpecificFlagsWin() {
        YamlConfiguration config = yaml(
                "equipment-sets:\n"
                        + "  starter:\n"
                        + "    enabled: true\n"
                        + "    event-equip-enable: false\n");

        EquipmentSchemaMigration.merge(config);

        assertTrue(config.getBoolean("equipment.starter.pvpwager-equip-enable"));
        assertFalse(config.getBoolean("equipment.starter.event-equip-enable"),
                "der spezifischere Schalter darf nicht vom gemeinsamen ueberschrieben werden");
    }

    // ============ Kollisionen ============

    @Test
    void keepsBothSetsWhenAnIdExistsTwice() {
        YamlConfiguration config = yaml(
                "equipment:\n"
                        + "  starter:\n"
                        + "    display-name: Neu\n"
                        + "equipment-sets:\n"
                        + "  starter:\n"
                        + "    display-name: Alt\n");

        EquipmentSchemaMigration.Result result = EquipmentSchemaMigration.merge(config);

        // Genau diese Kollision war die Ursache dafuer, dass PvP und Events verschiedene
        // Sets unter einer ID sehen konnten. Nichts darf stillschweigend verlorengehen.
        assertEquals("Neu", config.getString("equipment.starter.display-name"));
        assertEquals("Alt", config.getString("equipment.starter-legacy.display-name"));
        assertEquals(1, result.getWarnings().size(), "die Kollision muss gemeldet werden");
        assertTrue(result.getWarnings().get(0).contains("starter"));
    }

    @Test
    void numbersFurtherCollisions() {
        YamlConfiguration config = yaml(
                "equipment:\n"
                        + "  starter:\n"
                        + "    display-name: Neu\n"
                        + "  starter-legacy:\n"
                        + "    display-name: Schon belegt\n"
                        + "equipment-sets:\n"
                        + "  starter:\n"
                        + "    display-name: Alt\n");

        EquipmentSchemaMigration.merge(config);

        assertEquals("Schon belegt", config.getString("equipment.starter-legacy.display-name"));
        assertEquals("Alt", config.getString("equipment.starter-legacy2.display-name"));
    }

    // ============ Idempotenz ============

    @Test
    void isIdempotent() {
        String source =
                "equipment-sets:\n"
                        + "  starter:\n"
                        + "    enabled: false\n"
                        + "    display-name: Starter\n";

        YamlConfiguration config = yaml(source);
        assertTrue(EquipmentSchemaMigration.merge(config).isChanged());
        String afterFirst = config.saveToString();

        // Zweiter Lauf auf dem Ergebnis: darf die Datei nicht erneut anfassen, sonst legt
        // jeder Serverstart eine weitere Sicherungskopie an.
        YamlConfiguration again = yaml(afterFirst);
        assertFalse(EquipmentSchemaMigration.merge(again).isChanged());
        assertEquals(afterFirst, again.saveToString());
    }

    @Test
    void skipsEntriesThatAreNotSections() {
        // Eine von Hand verhunzte Datei darf die Migration nicht zum Absturz bringen.
        YamlConfiguration config = yaml(
                "equipment-sets:\n"
                        + "  broken: \"just a string\"\n"
                        + "  starter:\n"
                        + "    display-name: Starter\n");

        EquipmentSchemaMigration.Result result = EquipmentSchemaMigration.merge(config);

        assertEquals(1, result.getMigratedSets());
        assertEquals("Starter", config.getString("equipment.starter.display-name"));
    }

    @Test
    void targetSectionSurvivesWithAllItsSets() {
        YamlConfiguration config = yaml(
                "equipment:\n"
                        + "  a:\n"
                        + "    display-name: A\n"
                        + "  b:\n"
                        + "    display-name: B\n"
                        + "equipment-sets:\n"
                        + "  c:\n"
                        + "    display-name: C\n");

        EquipmentSchemaMigration.merge(config);

        ConfigurationSection target = config.getConfigurationSection("equipment");
        assertNotNull(target);
        assertEquals(java.util.Set.of("a", "b", "c"), target.getKeys(false));
    }
}
