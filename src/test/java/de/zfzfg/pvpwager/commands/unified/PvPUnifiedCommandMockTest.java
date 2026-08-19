package de.zfzfg.pvpwager.commands.unified;

import de.zfzfg.core.config.CoreConfigManager;
import de.zfzfg.core.util.CommandCooldownManager;
import de.zfzfg.eventplugin.EventPlugin;
import de.zfzfg.pvpwager.managers.MatchManager;
import de.zfzfg.pvpwager.managers.RequestManager;
import de.zfzfg.test.MockBukkitTestBase;
import org.bukkit.command.Command;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.List;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class PvPUnifiedCommandMockTest extends MockBukkitTestBase {

    private EventPlugin mockPlugin;
    private CoreConfigManager mockCoreConfig;
    private MatchManager mockMatchManager;
    private RequestManager mockRequestManager;
    private CommandCooldownManager cooldownManager;
    private PvPUnifiedCommand pvpCommand;

    @BeforeEach
    void setUp() {
        mockPlugin = mock(EventPlugin.class);
        mockCoreConfig = mock(CoreConfigManager.class);
        mockMatchManager = mock(MatchManager.class);
        mockRequestManager = mock(RequestManager.class);
        cooldownManager = new CommandCooldownManager();

        YamlConfiguration messages = new YamlConfiguration();
        messages.set("messages.pvp-help.header", "&7---");
        messages.set("messages.pvp-help.title", "&6PvP Help");
        messages.set("messages.system.unknown-subcommand", "&cUnknown: {command}");

        when(mockCoreConfig.getMessages()).thenReturn(messages);
        when(mockPlugin.getCoreConfigManager()).thenReturn(mockCoreConfig);
        when(mockPlugin.getMatchManager()).thenReturn(mockMatchManager);
        when(mockPlugin.getRequestManager()).thenReturn(mockRequestManager);
        when(mockPlugin.getCommandCooldownManager()).thenReturn(cooldownManager);
        when(mockPlugin.getLogger()).thenReturn(Logger.getLogger("PvPUnifiedCommandTest"));

        pvpCommand = new PvPUnifiedCommand(mockPlugin);
    }

    @Test
    @DisplayName("Executing /pvp without args shows help")
    void testCommandNoArgsShowsHelp() {
        PlayerMock player = createPlayer("TestPlayer");
        Command cmd = mock(Command.class);

        boolean result = pvpCommand.onCommand(player, cmd, "pvp", new String[0]);

        assertThat(result).isTrue();
        assertThat(player.nextMessage()).isNotNull();
    }

    @Test
    @DisplayName("Tab completion on arg 0 returns subcommands and player names")
    void testTabCompletionSubcommandsAndPlayers() {
        PlayerMock player = createPlayer("TestPlayer");
        PlayerMock opponent = createPlayer("OpponentPlayer");
        Command cmd = mock(Command.class);

        List<String> completions = pvpCommand.onTabComplete(player, cmd, "pvp", new String[]{""});

        assertThat(completions).isNotNull();
        assertThat(completions).contains("challenge", "accept", "deny", "spectate", "leave", "surrender", "draw");
        assertThat(completions).contains(opponent.getName(), player.getName());
    }

    @Test
    @DisplayName("Tab completion filters based on prefix")
    void testTabCompletionPrefixFilter() {
        PlayerMock player = createPlayer("TestPlayer");
        Command cmd = mock(Command.class);

        List<String> completions = pvpCommand.onTabComplete(player, cmd, "pvp", new String[]{"ch"});

        assertThat(completions).isNotNull();
        assertThat(completions).containsExactly("challenge");
    }
}
