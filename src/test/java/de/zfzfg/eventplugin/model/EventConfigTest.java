package de.zfzfg.eventplugin.model;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EventConfigTest {

    @Test
    @DisplayName("defaults with minimal configuration section")
    void testMinimalConfigDefaults() {
        YamlConfiguration yaml = new YamlConfiguration();
        EventConfig config = new EventConfig("test_event", yaml);

        assertThat(config.getId()).isEqualTo("test_event");
        assertThat(config.getCommand()).isEqualTo("test_event");
        assertThat(config.getDisplayName()).isEqualTo("test_event");
        assertThat(config.getMinPlayers()).isEqualTo(2);
        assertThat(config.getMaxPlayers()).isEqualTo(20);
        assertThat(config.getCountdownTime()).isEqualTo(60);
        assertThat(config.getGameMode()).isEqualTo(EventConfig.GameMode.SOLO);
        assertThat(config.getSpawnType()).isEqualTo(EventConfig.SpawnType.SINGLE_POINT);
        assertThat(config.isPvpEnabled()).isTrue();
        assertThat(config.isHungerEnabled()).isTrue();
    }

    @Test
    @DisplayName("parse full event configuration")
    void testFullConfigParsing() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("command", "tournament");
        yaml.set("display-name", "Epic Tournament");
        yaml.set("description", "A battle between teams");
        yaml.set("min-players", 4);
        yaml.set("max-players", 16);
        yaml.set("countdown-time", 30);

        yaml.set("worlds.lobby-world", "lobby");
        yaml.set("worlds.event-world", "event_arena");
        yaml.set("worlds.use-lobby", true);
        yaml.set("worlds.regenerate-event-world", true);
        yaml.set("worlds.build-allowed", false);

        yaml.set("spawn-settings.spawn-type", "RANDOM_RADIUS");
        yaml.set("spawn-settings.random-radius.center-x", 100.0);
        yaml.set("spawn-settings.random-radius.center-z", -50.0);
        yaml.set("spawn-settings.random-radius.radius", 75.0);
        yaml.set("spawn-settings.random-radius.min-distance", 15.0);

        yaml.set("mechanics.game-mode", "TEAM_2");
        yaml.set("mechanics.team-settings.friendly-fire", false);
        yaml.set("mechanics.team-settings.auto-balance", true);
        yaml.set("mechanics.win-condition.type", "LAST_MAN_STANDING");
        yaml.set("mechanics.death-handling.eliminate-on-death", true);
        yaml.set("mechanics.death-handling.spectator-mode", true);
        yaml.set("mechanics.pvp-enabled", true);
        yaml.set("mechanics.hunger-enabled", false);

        yaml.set("equipment-group", "iron_kit");
        yaml.set("give-equipment-in-lobby", true);

        yaml.set("messages.join", "Welcome to the event!");

        EventConfig config = new EventConfig("tournament", yaml);

        assertThat(config.getId()).isEqualTo("tournament");
        assertThat(config.getCommand()).isEqualTo("tournament");
        assertThat(config.getDisplayName()).isEqualTo("Epic Tournament");
        assertThat(config.getDescription()).isEqualTo("A battle between teams");
        assertThat(config.getMinPlayers()).isEqualTo(4);
        assertThat(config.getMaxPlayers()).isEqualTo(16);
        assertThat(config.getCountdownTime()).isEqualTo(30);

        assertThat(config.getLobbyWorld()).isEqualTo("lobby");
        assertThat(config.getEventWorld()).isEqualTo("event_arena");
        assertThat(config.isUseLobby()).isTrue();
        assertThat(config.shouldRegenerateEventWorld()).isTrue();
        assertThat(config.isBuildAllowed()).isFalse();

        assertThat(config.getSpawnType()).isEqualTo(EventConfig.SpawnType.RANDOM_RADIUS);
        assertThat(config.getSpawnConfig().getRandomRadius().getCenterX()).isEqualTo(100.0);
        assertThat(config.getSpawnConfig().getRandomRadius().getCenterZ()).isEqualTo(-50.0);
        assertThat(config.getSpawnConfig().getRandomRadius().getRadius()).isEqualTo(75.0);
        assertThat(config.getSpawnConfig().getRandomRadius().getMinDistance()).isEqualTo(15.0);

        assertThat(config.getGameMode()).isEqualTo(EventConfig.GameMode.TEAM_2);
        assertThat(config.getTeamSettings()).isNotNull();
        assertThat(config.getTeamSettings().isFriendlyFire()).isFalse();
        assertThat(config.getTeamSettings().isAutoBalance()).isTrue();

        assertThat(config.getWinCondition().getType()).isEqualTo("LAST_MAN_STANDING");
        assertThat(config.getDeathHandling().shouldEliminateOnDeath()).isTrue();
        assertThat(config.getDeathHandling().isSpectatorMode()).isTrue();
        assertThat(config.isPvpEnabled()).isTrue();
        assertThat(config.isHungerEnabled()).isFalse();

        assertThat(config.getEquipmentGroup()).isEqualTo("iron_kit");
        assertThat(config.shouldGiveEquipmentInLobby()).isTrue();
        assertThat(config.getMessage("join")).isEqualTo("Welcome to the event!");
    }
}
