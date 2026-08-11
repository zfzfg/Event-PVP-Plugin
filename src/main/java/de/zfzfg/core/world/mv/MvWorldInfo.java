package de.zfzfg.core.world.mv;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Zustand einer Welt aus Sicht des Servers: bekannt Multiverse sie, ist sie geladen,
 * liegt ueberhaupt ein Weltordner auf der Platte?
 *
 * <p>Ein World-Preset in worlds.yml, fuer das {@code existsOnDisk == false} gilt, ist ein
 * reiner Platzhalter -- genau die Unterscheidung, die das Panel anzeigen soll.</p>
 */
public class MvWorldInfo {

    private final String name;
    private final String environment;
    private final String worldType;
    private final boolean loaded;
    private final boolean knownToMultiverse;
    private final boolean existsOnDisk;

    public MvWorldInfo(String name, String environment, String worldType,
                       boolean loaded, boolean knownToMultiverse, boolean existsOnDisk) {
        this.name = name;
        this.environment = environment;
        this.worldType = worldType;
        this.loaded = loaded;
        this.knownToMultiverse = knownToMultiverse;
        this.existsOnDisk = existsOnDisk;
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

    public boolean isLoaded() {
        return loaded;
    }

    public boolean isKnownToMultiverse() {
        return knownToMultiverse;
    }

    public boolean isExistsOnDisk() {
        return existsOnDisk;
    }

    /**
     * Serialisierungsform fuer die Web-API. Bewusst handgeschrieben statt per Gson-Reflection,
     * damit die JSON-Feldnamen unabhaengig von den Java-Feldnamen stabil bleiben.
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", name);
        map.put("environment", environment);
        map.put("worldType", worldType);
        map.put("loaded", loaded);
        map.put("knownToMultiverse", knownToMultiverse);
        map.put("existsOnDisk", existsOnDisk);
        return map;
    }
}
