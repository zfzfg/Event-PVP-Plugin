package de.zfzfg.core.config;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.bukkit.configuration.file.YamlConfiguration;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;
class ConfigWriteCoordinatorTest {
    @TempDir Path directory;
    @Test void staleRevisionCannotOverwritePatch() throws Exception {
        Path file = directory.resolve("config.yml");
        ConfigWriteCoordinator.patch(file, c -> c.set("value", 1));
        String revision = ConfigWriteCoordinator.revision(file);
        ConfigWriteCoordinator.patch(file, c -> c.set("language", "de"));
        YamlConfiguration proposed = new YamlConfiguration(); proposed.set("value", 2);
        assertThrows(ConfigWriteCoordinator.Conflict.class, () -> ConfigWriteCoordinator.replace(file, proposed, revision));
        assertEquals("de", YamlConfiguration.loadConfiguration(file.toFile()).getString("language"));
    }
    @Test void patchesReadLatestFile() {
        Path file = directory.resolve("config.yml");
        ConfigWriteCoordinator.patch(file, c -> c.set("world", "new"));
        ConfigWriteCoordinator.patch(file, c -> c.set("debug", "off"));
        assertEquals("new", YamlConfiguration.loadConfiguration(file.toFile()).getString("world"));
    }
    @Test void failedReplaceDoesNotTruncateExistingDirectory() throws Exception {
        Path file = directory.resolve("config.yml"); Files.createDirectory(file); Files.writeString(file.resolve("sentinel"), "keep");
        assertThrows(IllegalStateException.class, () -> ConfigWriteCoordinator.replace(file, new YamlConfiguration(), null));
        assertEquals("keep", Files.readString(file.resolve("sentinel")));
    }
}
