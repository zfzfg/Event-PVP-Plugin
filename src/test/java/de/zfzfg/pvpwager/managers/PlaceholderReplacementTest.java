package de.zfzfg.pvpwager.managers;

import de.zfzfg.core.config.CoreConfigManager;
import de.zfzfg.eventplugin.EventPlugin;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlaceholderReplacementTest {

    @Test
    void testCoreConfigManagerPlaceholderVariants() throws Exception {
        EventPlugin plugin = Mockito.mock(EventPlugin.class);
        CoreConfigManager coreConfig = new CoreConfigManager(plugin);

        YamlConfiguration messages = new YamlConfiguration();
        messages.set("messages.console.teleport-info", "Teleport in {seconds} seconds for {player}");
        
        Field messagesField = CoreConfigManager.class.getDeclaredField("messages");
        messagesField.setAccessible(true);
        messagesField.set(coreConfig, messages);

        // Test with plain names
        String r1 = coreConfig.getConsoleMsg("teleport-info", "seconds", "5", "player", "Alice");
        assertEquals("Teleport in 5 seconds for Alice", r1);

        // Test with braced names
        String r2 = coreConfig.getConsoleMsg("teleport-info", "{seconds}", "5", "{player}", "Alice");
        assertEquals("Teleport in 5 seconds for Alice", r2);

        // Test with percent names
        String r3 = coreConfig.getConsoleMsg("teleport-info", "%seconds%", "5", "%player%", "Alice");
        assertEquals("Teleport in 5 seconds for Alice", r3);
    }

    @Test
    void testEventConfigManagerPlaceholderVariants() throws Exception {
        EventPlugin plugin = Mockito.mock(EventPlugin.class);
        de.zfzfg.eventplugin.manager.ConfigManager eventConfig = new de.zfzfg.eventplugin.manager.ConfigManager(plugin);

        YamlConfiguration messages = new YamlConfiguration();
        messages.set("messages.start.join-phase-started", "Beitrittsphase für {event} gestartet ({time}s)");
        
        Field messagesField = de.zfzfg.eventplugin.manager.ConfigManager.class.getDeclaredField("messagesConfig");
        messagesField.setAccessible(true);
        messagesField.set(eventConfig, messages);

        // Test with plain names
        String r1 = eventConfig.getMessage("start.join-phase-started", "event", "PvP Arena", "time", "20");
        assertEquals("Beitrittsphase für PvP Arena gestartet (20s)", r1);

        // Test with braced names
        String r2 = eventConfig.getMessage("start.join-phase-started", "{event}", "PvP Arena", "{time}", "20");
        assertEquals("Beitrittsphase für PvP Arena gestartet (20s)", r2);

        // Test with percent names
        String r3 = eventConfig.getMessage("start.join-phase-started", "%event%", "PvP Arena", "%time%", "20");
        assertEquals("Beitrittsphase für PvP Arena gestartet (20s)", r3);
    }
}
