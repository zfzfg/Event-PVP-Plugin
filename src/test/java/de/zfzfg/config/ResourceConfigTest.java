package de.zfzfg.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ResourceConfigTest {

    @Test
    void yamlDateienSindGueltig() {
        List<String> paths = Arrays.asList(
                "src/main/resources/config.yml",
                "src/main/resources/equipment.yml",
                "src/main/resources/worlds.yml",
                "src/main/resources/web-config.yml",
                "src/main/resources/messages_de.yml",
                "src/main/resources/messages_en.yml"
        );

        for (String path : paths) {
            File file = new File(path);
            assertTrue(file.exists(), "Datei existiert nicht: " + path);
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            assertNotNull(config, "Config konnte nicht geladen werden: " + path);
            assertFalse(config.getKeys(false).isEmpty(), "Config ist leer: " + path);
        }
    }

    @Test
    void deUndEnHabenKernSchluessel() {
        YamlConfiguration de = YamlConfiguration.loadConfiguration(new File("src/main/resources/messages_de.yml"));
        YamlConfiguration en = YamlConfiguration.loadConfiguration(new File("src/main/resources/messages_en.yml"));

        Set<String> deKeys = de.getKeys(true);
        Set<String> enKeys = en.getKeys(true);

        assertFalse(deKeys.isEmpty(), "messages_de.yml ist leer");
        assertFalse(enKeys.isEmpty(), "messages_en.yml ist leer");
    }
}
