package de.zfzfg.core.world.mv;

import java.util.Map;

/**
 * Wunschzettel fuer eine neue Multiverse-Welt. Ausser {@code name} ist alles optional --
 * das Panel darf ein Preset auch ganz ohne Welterstellung anlegen.
 *
 * <p>Alle Freitextfelder landen im Legacy-Backend in einer Konsolen-Kommandozeile, deshalb
 * werden sie hier schon beim Parsen streng validiert statt erst beim Dispatch.</p>
 */
public class MvCreateSpec {

    /** Seeds duerfen Text sein ("mySeed"), aber keine Leerzeichen/Flags einschmuggeln. */
    private static final String SEED_PATTERN = "[A-Za-z0-9_\\-]{1,64}";  // i18n-ignore: Regex, kein Anzeigetext
    /** Generator akzeptiert die Multiverse-Form {@code Plugin} bzw. {@code Plugin:id}. */
    private static final String GENERATOR_PATTERN = "[A-Za-z0-9_\\-]{1,48}(:[A-Za-z0-9_\\-]{1,48})?";  // i18n-ignore: Regex, kein Anzeigetext
    private static final String BIOME_PATTERN = "[A-Za-z0-9_\\-:]{1,64}";  // i18n-ignore: Regex, kein Anzeigetext

    private String name;
    private String environment = "NORMAL";
    private String worldType = "NORMAL";
    private String seed = "";
    private String generator = "";
    private String generatorSettings = "";
    private String biome = "";
    private boolean generateStructures = true;
    private boolean adjustSpawn = true;

    /**
     * Baut die Spec aus dem JSON-Body von {@code POST /api/mvworlds/create}.
     *
     * @throws MvInputException bei ungueltigen Werten -- der Aufrufer macht daraus eine
     *                          {@code success:false}-Antwort mit uebersetzbarem Grund.
     */
    public static MvCreateSpec fromMap(Map<String, Object> body) {
        MvCreateSpec spec = new MvCreateSpec();
        spec.name = MvWorldService.requireValidWorldName(str(body, "world", str(body, "name", "")));

        String env = str(body, "environment", "NORMAL").toUpperCase(java.util.Locale.ROOT);
        if (!env.equals("NORMAL") && !env.equals("NETHER") && !env.equals("THE_END")) {
            throw new MvInputException("mv.error.invalidEnvironment", env);
        }
        spec.environment = env;

        String type = str(body, "worldType", "NORMAL").toUpperCase(java.util.Locale.ROOT);
        if (!type.equals("NORMAL") && !type.equals("FLAT")
                && !type.equals("LARGE_BIOMES") && !type.equals("AMPLIFIED")) {
            throw new MvInputException("mv.error.invalidWorldType", type);
        }
        spec.worldType = type;

        spec.seed = optional(body, "seed", SEED_PATTERN, "mv.error.invalidSeed");
        spec.generator = optional(body, "generator", GENERATOR_PATTERN, "mv.error.invalidGenerator");
        spec.biome = optional(body, "biome", BIOME_PATTERN, "mv.error.invalidBiome");

        // Generator-Settings sind freies JSON und gehen nur ueber die MV5-API (nie in eine
        // Kommandozeile), deshalb hier nur eine Laengenbegrenzung statt eines Zeichenfilters.
        String settings = str(body, "generatorSettings", "").trim();
        if (settings.length() > 4096) {
            throw new MvInputException("mv.error.generatorSettingsTooLong");
        }
        spec.generatorSettings = settings;

        spec.generateStructures = bool(body, "generateStructures", true);
        spec.adjustSpawn = bool(body, "adjustSpawn", true);
        return spec;
    }

    private static String optional(Map<String, Object> body, String key, String pattern, String errorKey) {
        String value = str(body, key, "").trim();
        if (value.isEmpty()) return "";
        if (!value.matches(pattern)) {
            throw new MvInputException(errorKey, value);
        }
        return value;
    }

    private static String str(Map<String, Object> body, String key, String fallback) {
        Object value = body == null ? null : body.get(key);
        return value == null ? fallback : String.valueOf(value);
    }

    private static boolean bool(Map<String, Object> body, String key, boolean fallback) {
        Object value = body == null ? null : body.get(key);
        if (value instanceof Boolean) return (Boolean) value;
        if (value == null) return fallback;
        return Boolean.parseBoolean(String.valueOf(value));
    }

    public String getName() {
        return name;
    }

    public String getEnvironment() {
        return environment;
    }

    public String getWorldType() {
        return worldType;
    }

    public String getSeed() {
        return seed;
    }

    public String getGenerator() {
        return generator;
    }

    public String getGeneratorSettings() {
        return generatorSettings;
    }

    public String getBiome() {
        return biome;
    }

    public boolean isGenerateStructures() {
        return generateStructures;
    }

    public boolean isAdjustSpawn() {
        return adjustSpawn;
    }
}
