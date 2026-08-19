package de.zfzfg.core.web;

import de.zfzfg.core.inventory.guard.InventoryGuard;
import de.zfzfg.eventplugin.EventPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class WebApiHandlerTest {

    private EventPlugin plugin;
    private WebConfigManager webConfigManager;
    private InventoryGuard inventoryGuard;
    private WebApiHandler apiHandler;

    @BeforeEach
    void setUp() {
        plugin = mock(EventPlugin.class);
        webConfigManager = mock(WebConfigManager.class);
        inventoryGuard = mock(InventoryGuard.class);

        when(plugin.getLogger()).thenReturn(Logger.getLogger("WebApiHandlerTest"));
        when(plugin.getInventoryGuard()).thenReturn(inventoryGuard);

        apiHandler = new WebApiHandler(plugin, webConfigManager);
    }

    @Test
    @DisplayName("getConfig returns config map from config manager")
    void testGetConfig() {
        Map<String, Object> dummyConfig = Map.of("language", "de", "debug", false);
        when(webConfigManager.getConfigAsMap()).thenReturn(dummyConfig);

        Map<String, Object> response = apiHandler.getConfig();

        assertThat(response.get("success")).isEqualTo(true);
        assertThat(response.get("data")).isEqualTo(dummyConfig);
    }

    @Test
    @DisplayName("getWorlds returns worlds map from config manager")
    void testGetWorlds() {
        Map<String, Object> dummyWorlds = Map.of("worlds", Map.of("arena_1", Map.of("enabled", true)));
        when(webConfigManager.getWorldsAsMap()).thenReturn(dummyWorlds);

        Map<String, Object> response = apiHandler.getWorlds();

        assertThat(response.get("success")).isEqualTo(true);
        assertThat(response.get("data")).isEqualTo(dummyWorlds);
    }

    @Test
    @DisplayName("saveConfig returns failure if request body contains no data")
    void testSaveConfigNoData() {
        Map<String, Object> emptyBody = new HashMap<>();

        Map<String, Object> response = apiHandler.saveConfig(emptyBody);

        assertThat(response.get("success")).isEqualTo(false);
        assertThat(response.get("message")).isEqualTo("No data received");
    }

    @Test
    @DisplayName("saveWorlds returns failure if request body contains no data")
    void testSaveWorldsNoData() {
        Map<String, Object> emptyBody = new HashMap<>();

        Map<String, Object> response = apiHandler.saveWorlds(emptyBody);

        assertThat(response.get("success")).isEqualTo(false);
        assertThat(response.get("message")).isEqualTo("No data received");
    }

    @Test
    @DisplayName("setInventoryProvider rejects switch to none when open sessions exist")
    void testSetInventoryProviderRejectsNoneWithOpenSessions() {
        when(inventoryGuard.openCount()).thenReturn(2);

        Map<String, Object> request = Map.of("provider", "none");
        Map<String, Object> response = apiHandler.setInventoryProvider(request);

        assertThat(response.get("success")).isEqualTo(false);
        assertThat(response.get("messageKey")).isEqualTo("inventory.error.openSessions");
        assertThat(response.get("detail")).isEqualTo("2");
    }

    @Test
    @DisplayName("setInventoryProvider rejects unknown provider")
    void testSetInventoryProviderRejectsUnknown() {
        Map<String, Object> request = Map.of("provider", "invalid_provider");
        Map<String, Object> response = apiHandler.setInventoryProvider(request);

        assertThat(response.get("success")).isEqualTo(false);
        assertThat(response.get("messageKey")).isEqualTo("inventory.error.unknownProvider");
    }
}
