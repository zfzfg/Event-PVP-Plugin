package de.zfzfg.core.world.mv;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Prueft die Eingabevalidierung der Multiverse-Web-API.
 *
 * <p>Diese Werte kommen aus dem Browser und steuern Ordnerloeschungen bzw. -- im Legacy-Backend --
 * eine Konsolenkommandozeile. Die Validierung ist damit sicherheitsrelevant und nicht nur
 * Komfort, deshalb hat sie eigene Tests.</p>
 */
class MvWorldInputValidationTest {

    // ============ Weltnamen ============

    @Test
    void acceptsOrdinaryWorldNames() {
        assertEquals("pvp_arena_1", MvWorldService.requireValidWorldName("pvp_arena_1"));
        assertEquals("Event-Lobby", MvWorldService.requireValidWorldName("Event-Lobby"));
        assertEquals("world", MvWorldService.requireValidWorldName("  world  "), "trimmt Leerzeichen");
    }

    @Test
    void rejectsPathTraversal() {
        assertThrows(MvInputException.class, () -> MvWorldService.requireValidWorldName("../plugins"));
        assertThrows(MvInputException.class, () -> MvWorldService.requireValidWorldName("world/../.."));
        assertThrows(MvInputException.class, () -> MvWorldService.requireValidWorldName("foo\\bar"));
        assertThrows(MvInputException.class, () -> MvWorldService.requireValidWorldName("world.dat"));
    }

    @Test
    void rejectsReservedServerDirectories() {
        assertThrows(MvInputException.class, () -> MvWorldService.requireValidWorldName("plugins"));
        assertThrows(MvInputException.class, () -> MvWorldService.requireValidWorldName("PLUGINS"));
        assertThrows(MvInputException.class, () -> MvWorldService.requireValidWorldName("logs"));
        assertThrows(MvInputException.class, () -> MvWorldService.requireValidWorldName("crash-reports"));
    }

    @Test
    void rejectsEmptyAndOverlongNames() {
        assertThrows(MvInputException.class, () -> MvWorldService.requireValidWorldName(null));
        assertThrows(MvInputException.class, () -> MvWorldService.requireValidWorldName(""));
        assertThrows(MvInputException.class, () -> MvWorldService.requireValidWorldName("   "));
        assertThrows(MvInputException.class, () -> MvWorldService.requireValidWorldName("w".repeat(65)));
    }

    // ============ Erstellungsparameter ============

    private Map<String, Object> body(String world) {
        Map<String, Object> body = new HashMap<>();
        body.put("world", world);
        return body;
    }

    @Test
    void appliesDefaultsWhenOnlyTheNameIsGiven() {
        MvCreateSpec spec = MvCreateSpec.fromMap(body("arena"));

        assertEquals("arena", spec.getName());
        assertEquals("NORMAL", spec.getEnvironment());
        assertEquals("NORMAL", spec.getWorldType());
        assertEquals("", spec.getSeed());
        assertEquals("", spec.getGenerator());
        assertEquals("", spec.getBiome());
        assertTrue(spec.isGenerateStructures(), "Strukturen sind per Default an");
        assertTrue(spec.isAdjustSpawn(), "Spawn-Anpassung ist per Default an");
    }

    @Test
    void acceptsFullOptionSet() {
        Map<String, Object> body = body("arena");
        body.put("environment", "the_end");
        body.put("worldType", "flat");
        body.put("seed", "mySeed_42");
        body.put("generator", "VoidGen:flat");
        body.put("biome", "minecraft:plains");
        body.put("generatorSettings", "{\"layers\":[]}");
        body.put("generateStructures", false);
        body.put("adjustSpawn", false);

        MvCreateSpec spec = MvCreateSpec.fromMap(body);

        assertEquals("THE_END", spec.getEnvironment(), "Environment wird normalisiert");
        assertEquals("FLAT", spec.getWorldType());
        assertEquals("mySeed_42", spec.getSeed());
        assertEquals("VoidGen:flat", spec.getGenerator());
        assertEquals("minecraft:plains", spec.getBiome());
        assertEquals("{\"layers\":[]}", spec.getGeneratorSettings());
        assertFalse(spec.isGenerateStructures());
        assertFalse(spec.isAdjustSpawn());
    }

    @Test
    void rejectsUnknownEnvironmentAndWorldType() {
        Map<String, Object> badEnv = body("arena");
        badEnv.put("environment", "CUSTOM");
        assertThrows(MvInputException.class, () -> MvCreateSpec.fromMap(badEnv));

        Map<String, Object> badType = body("arena");
        badType.put("worldType", "SUPERFLAT");
        assertThrows(MvInputException.class, () -> MvCreateSpec.fromMap(badType));
    }

    /**
     * Der wichtigste Fall: Seed und Generator landen im Legacy-Backend hinter {@code -s} bzw.
     * {@code -g} in einer Kommandozeile. Ein Leerzeichen darin waere eine Flag-Injection.
     */
    @Test
    void rejectsCommandInjectionInSeedAndGenerator() {
        Map<String, Object> injectedSeed = body("arena");
        injectedSeed.put("seed", "1 -g EvilGen");
        assertThrows(MvInputException.class, () -> MvCreateSpec.fromMap(injectedSeed));

        Map<String, Object> injectedGenerator = body("arena");
        injectedGenerator.put("generator", "Gen -a false");
        assertThrows(MvInputException.class, () -> MvCreateSpec.fromMap(injectedGenerator));

        Map<String, Object> newlineSeed = body("arena");
        newlineSeed.put("seed", "1\nop someone");
        assertThrows(MvInputException.class, () -> MvCreateSpec.fromMap(newlineSeed));
    }

    @Test
    void rejectsOverlongGeneratorSettings() {
        Map<String, Object> body = body("arena");
        body.put("generatorSettings", "x".repeat(4097));
        assertThrows(MvInputException.class, () -> MvCreateSpec.fromMap(body));
    }

    @Test
    void rejectsInvalidWorldNameInCreateBody() {
        assertThrows(MvInputException.class, () -> MvCreateSpec.fromMap(body("../plugins")));
        assertThrows(MvInputException.class, () -> MvCreateSpec.fromMap(body("")));
    }
}
