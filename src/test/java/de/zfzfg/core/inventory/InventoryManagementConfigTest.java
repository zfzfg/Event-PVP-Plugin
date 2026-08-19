package de.zfzfg.core.inventory;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class InventoryManagementConfigTest {

    @Test
    @DisplayName("defaults with empty config")
    void testEmptyConfigDefaults() {
        YamlConfiguration config = new YamlConfiguration();
        InventoryManagementConfig imc = new InventoryManagementConfig(config);

        assertThat(imc.mode()).isEqualTo(InventoryManagementConfig.Mode.AUTO);
        assertThat(imc.modeWasInvalid()).isFalse();
        assertThat(imc.managedByPlugin()).isTrue();
        assertThat(imc.restoreOnMatchEnd()).isTrue();
        assertThat(imc.restoreOnEventEnd()).isTrue();
        assertThat(imc.restoreOnRespawn()).isTrue();
        assertThat(imc.restoreOnRejoin()).isTrue();
        assertThat(imc.failurePolicy()).isEqualTo(InventoryManagementConfig.FailurePolicy.ABORT);
        assertThat(imc.legacySafetyBackups()).isTrue();
        assertThat(imc.cleanupAfterMatch()).isFalse();
        assertThat(imc.guardEnabled()).isTrue();
        assertThat(imc.restoreOrphansOnStart()).isTrue();
        assertThat(imc.warnOnMultiverseInventories()).isTrue();
        assertThat(imc.mviConflictGuard()).isTrue();
        assertThat(imc.mviRestoreDelayTicks()).isEqualTo(3);
    }

    @ParameterizedTest
    @CsvSource({
            "auto, AUTO",
            "inventoryrestore, INVENTORYRESTORE",
            "none, NONE",
            "AUTO, AUTO",
            "None, NONE"
    })
    @DisplayName("mode parsing")
    void testModeParsing(String input, InventoryManagementConfig.Mode expected) {
        YamlConfiguration config = new YamlConfiguration();
        config.set("settings.inventory-management.provider", input);
        InventoryManagementConfig imc = new InventoryManagementConfig(config);

        assertThat(imc.mode()).isEqualTo(expected);
        assertThat(imc.modeWasInvalid()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"invalid_mode", "custom", "xyz", ""})
    @DisplayName("invalid mode falls back to AUTO with modeWasInvalid=true")
    void testInvalidModeFallback(String invalid) {
        YamlConfiguration config = new YamlConfiguration();
        config.set("settings.inventory-management.provider", invalid);
        InventoryManagementConfig imc = new InventoryManagementConfig(config);

        assertThat(imc.mode()).isEqualTo(InventoryManagementConfig.Mode.AUTO);
        assertThat(imc.modeWasInvalid()).isTrue();
    }

    @Test
    @DisplayName("mode NONE disables plugin-managed restores and guard")
    void testModeNoneDisablesRestores() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("settings.inventory-management.provider", "none");
        InventoryManagementConfig imc = new InventoryManagementConfig(config);

        assertThat(imc.mode()).isEqualTo(InventoryManagementConfig.Mode.NONE);
        assertThat(imc.managedByPlugin()).isFalse();
        assertThat(imc.restoreOnMatchEnd()).isFalse();
        assertThat(imc.restoreOnEventEnd()).isFalse();
        assertThat(imc.restoreOnRespawn()).isFalse();
        assertThat(imc.restoreOnRejoin()).isFalse();
        assertThat(imc.guardEnabled()).isFalse();
    }

    @Test
    @DisplayName("failure policy parsing")
    void testFailurePolicyParsing() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("settings.inventory-management.on-backup-failure", "warn");
        InventoryManagementConfig imc = new InventoryManagementConfig(config);
        assertThat(imc.failurePolicy()).isEqualTo(InventoryManagementConfig.FailurePolicy.WARN);

        config.set("settings.inventory-management.on-backup-failure", "abort");
        imc = new InventoryManagementConfig(config);
        assertThat(imc.failurePolicy()).isEqualTo(InventoryManagementConfig.FailurePolicy.ABORT);

        config.set("settings.inventory-management.on-backup-failure", "anything_else");
        imc = new InventoryManagementConfig(config);
        assertThat(imc.failurePolicy()).isEqualTo(InventoryManagementConfig.FailurePolicy.ABORT);
    }
}
