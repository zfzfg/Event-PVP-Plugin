package de.zfzfg.core.web;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Prueft die Dateinamensfreigabe fuer {@code /item-assets/override/&lt;MATERIAL&gt;.png}.
 *
 * <p>Diese Dateien liegen als einzige statische Auslieferung nicht im JAR, sondern im
 * Plugin-Datenordner - sie entstehen erst zur Laufzeit aus dem Resourcepack des Servers.
 * Der Name kommt dabei ungeprueft aus der URL. Kaeme ein Pfadanteil durch, waeren beliebige
 * Dateien des Servers ueber das Web-Panel lesbar; die Pruefung ist damit sicherheitsrelevant
 * und nicht nur Formsache. Aus demselben Grund hat schon die Weltnamenspruefung
 * (MvWorldInputValidationTest) eigene Tests.</p>
 */
class TextureOverridePathTest {

    @Test
    void acceptsGeneratedMaterialFileNames() {
        assertTrue(ResourcePackTextureService.isValidOverrideFileName("DIAMOND_SWORD.png"));
        assertTrue(ResourcePackTextureService.isValidOverrideFileName("TNT.png"));
        assertTrue(ResourcePackTextureService.isValidOverrideFileName("ZOMBIE_SPAWN_EGG.png"));
        assertTrue(ResourcePackTextureService.isValidOverrideFileName("MUSIC_DISC_13.png"),
                "Ziffern kommen in Materialnamen vor");
    }

    @Test
    void rejectsPathTraversal() {
        assertFalse(ResourcePackTextureService.isValidOverrideFileName("../config.yml"));
        assertFalse(ResourcePackTextureService.isValidOverrideFileName("../../server.properties"));
        assertFalse(ResourcePackTextureService.isValidOverrideFileName("..%2Fconfig.yml"));
        assertFalse(ResourcePackTextureService.isValidOverrideFileName("sub/DIAMOND_SWORD.png"));
        assertFalse(ResourcePackTextureService.isValidOverrideFileName("sub\\DIAMOND_SWORD.png"));
        assertFalse(ResourcePackTextureService.isValidOverrideFileName("/etc/passwd"));
        assertFalse(ResourcePackTextureService.isValidOverrideFileName("C:\\Windows\\win.ini"));
    }

    @Test
    void rejectsOtherFileTypes() {
        // Der Ordner enthaelt ausschliesslich PNG. Alles andere waere entweder gar nicht da
        // oder etwas, das nicht ausgeliefert werden soll.
        assertFalse(ResourcePackTextureService.isValidOverrideFileName("DIAMOND_SWORD.yml"));
        assertFalse(ResourcePackTextureService.isValidOverrideFileName("DIAMOND_SWORD"));
        assertFalse(ResourcePackTextureService.isValidOverrideFileName(".pack-sha1"));
        assertFalse(ResourcePackTextureService.isValidOverrideFileName("DIAMOND_SWORD.png.yml"));
    }

    @Test
    void rejectsLowercaseAndMixedCase() {
        // Die Dateien entstehen aus Material.name() und sind damit immer gross geschrieben.
        // Ein kleingeschriebener Name traefe auf einem Dateisystem ohne
        // Gross-/Kleinschreibungsunterschied trotzdem - hier soll er gar nicht erst durch.
        assertFalse(ResourcePackTextureService.isValidOverrideFileName("diamond_sword.png"));
        assertFalse(ResourcePackTextureService.isValidOverrideFileName("Diamond_Sword.png"));
    }

    @Test
    void rejectsEmptyAndOverlongNames() {
        assertFalse(ResourcePackTextureService.isValidOverrideFileName(null));
        assertFalse(ResourcePackTextureService.isValidOverrideFileName(""));
        assertFalse(ResourcePackTextureService.isValidOverrideFileName(".png"));
        assertFalse(ResourcePackTextureService.isValidOverrideFileName("A".repeat(65) + ".png"));
        assertTrue(ResourcePackTextureService.isValidOverrideFileName("A".repeat(64) + ".png"));
    }

    @Test
    void rejectsNullBytesAndControlCharacters() {
        assertFalse(ResourcePackTextureService.isValidOverrideFileName("DIAMOND_SWORD.png\0"));
        assertFalse(ResourcePackTextureService.isValidOverrideFileName("DIAMOND\nSWORD.png"));
        assertFalse(ResourcePackTextureService.isValidOverrideFileName("DIAMOND SWORD.png"));
    }
}
