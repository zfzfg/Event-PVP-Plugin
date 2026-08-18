package de.zfzfg.eventplugin;

import de.zfzfg.eventplugin.commands.EventCommand;
import de.zfzfg.eventplugin.listeners.EventListener;
import de.zfzfg.eventplugin.listeners.TeamPvPListener;
import de.zfzfg.eventplugin.listeners.UpdateNotifyListener;
import de.zfzfg.eventplugin.listeners.WorldChangeListener;
import de.zfzfg.eventplugin.listeners.VoidProtectionListener;
import de.zfzfg.eventplugin.manager.AutoEventManager;
import de.zfzfg.eventplugin.manager.ConfigManager;
import de.zfzfg.eventplugin.manager.EventManager;
import de.zfzfg.eventplugin.security.PlayerModeListener;
import de.zfzfg.eventplugin.util.UpdateChecker;
import de.zfzfg.pvpwager.commands.*;
import de.zfzfg.pvpwager.gui.livetrade.LiveTradeListener;
import de.zfzfg.pvpwager.gui.livetrade.LiveTradeManager;
import de.zfzfg.pvpwager.listeners.PvPListener;
import de.zfzfg.pvpwager.listeners.SpectatorRecoveryListener;
import de.zfzfg.pvpwager.managers.*;
import net.milkbowl.vault.economy.Economy;
import de.zfzfg.eventplugin.integration.CompositeExternalDisplayBridge;
import de.zfzfg.eventplugin.integration.ExternalDisplayBridge;
import de.zfzfg.eventplugin.integration.NoOpExternalDisplayBridge;
import de.zfzfg.eventplugin.integration.PluginDisplayBridge;
import de.zfzfg.eventplugin.integration.combat.CombatIntegrationBridge;
import de.zfzfg.eventplugin.integration.combat.NoOpCombatBridge;
import de.zfzfg.eventplugin.integration.combat.PvPManagerBridge;
import de.zfzfg.eventplugin.integration.papi.EventPvpExpansion;
import de.zfzfg.eventplugin.world.WorldStateManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import de.zfzfg.core.config.CoreConfigManager;
import de.zfzfg.core.service.ConfigurationService;
import de.zfzfg.core.tasks.TaskManager;
import de.zfzfg.core.monitoring.debug.DebugManager;
import de.zfzfg.core.util.CommandCooldownManager;
import de.zfzfg.core.web.WebServer;
import de.zfzfg.core.web.WebConfigManager;
import de.zfzfg.core.web.WebAuthManager;
import org.bukkit.scheduler.BukkitTask;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class EventPlugin extends JavaPlugin {

    private static EventPlugin instance;

    // Event-Modul
    private ConfigManager configManager;
    private EventManager eventManager;
    private AutoEventManager autoEventManager;

    // PvP-Wager-Modul
    private de.zfzfg.pvpwager.managers.ConfigManager pvpConfigManager;
    private MatchManager matchManager;
    private RequestManager requestManager;
    private ArenaManager arenaManager;
    private EquipmentManager equipmentManager;
    private CommandRequestManager commandRequestManager;
    private StatsManager statsManager;
    private de.zfzfg.eventplugin.managers.EventStatsManager eventStatsManager;
    private Economy economy;
    private WorldStateManager worldStateManager;
    private CoreConfigManager coreConfigManager;
    private ConfigurationService configurationService;
    private TaskManager taskManager;
    private de.zfzfg.core.world.mv.MvWorldService mvWorldService;
    private DebugManager debugManager;
    private LiveTradeManager liveTradeManager;
    private PvPWagerGuiCommand pvpWagerGuiCommand;
    // Keep reference to PvP WorldChangeListener for cleanup
    private de.zfzfg.pvpwager.listeners.WorldChangeListener pvpWorldChangeListener;
    // Web-Interface
    private WebConfigManager webConfigManager;
    private WebAuthManager webAuthManager;
    private WebServer webServer;
    // Inventar-Verwaltung (Ersatz fuer Multiverse-Inventories)
    private de.zfzfg.core.inventory.InventoryManagementConfig inventoryConfig;
    private de.zfzfg.core.inventory.InventoryBackupService inventoryBackupService;
    private de.zfzfg.core.inventory.guard.InventoryGuard inventoryGuard;
    private de.zfzfg.core.inventory.InventorySessionManager inventorySessions;
    // Erkennung und Konfliktschutz gegenueber einem parallel laufenden Multiverse-Inventories
    private de.zfzfg.core.inventory.mvi.MultiverseInventoriesBridge mviBridge;
    // Auffangspeicher fuer Gewinne, die nicht sofort ausgegeben werden konnten
    private de.zfzfg.core.reward.PendingPayoutStore pendingPayouts;
    // Positions-Sicherheitsnetz (Gegenstueck zum Inventar-Journal)
    private de.zfzfg.core.location.ReturnLocationStore returnLocations;
    private de.zfzfg.core.location.SafeLocationResolver safeLocations;
    private de.zfzfg.core.location.PluginWorlds pluginWorlds;
    private de.zfzfg.core.location.StrandedPlayerListener strandedPlayers;
    private CommandCooldownManager commandCooldownManager;
    private UpdateChecker updateChecker;

    private ExternalDisplayBridge externalDisplayBridge;
    private CombatIntegrationBridge combatBridge;
    private AtomicBoolean externalDisplayDirty = new AtomicBoolean(false);
    private int externalDisplayRefreshTaskId = -1;

    @Override
    @SuppressWarnings("deprecation")
    public void onEnable() {
        long t0 = System.nanoTime();
        instance = this;

        // Prüfe zwingend erforderliche Abhängigkeiten (Multiverse-Core, Vault, InventoryBackup)
        if (!checkRequiredDependencies()) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Zentralen Core-Config-Manager laden (vereinheitlichte Dateien)
        coreConfigManager = new CoreConfigManager(this);
        coreConfigManager.load();

        // Debug-Manager initialisieren (Stufe kommt aus settings.debug, Standard: aus)
        debugManager = new DebugManager(this);
        debugManager.loadFromConfig();

        // Positions-Sicherheitsnetz. Frueh, weil jeder Teleport in eine Event-, Lobby- oder
        // Arenawelt hier seine Rueckkehr-Position hinterlegt.
        returnLocations = new de.zfzfg.core.location.ReturnLocationStore(this);
        returnLocations.load();

        // Offene Gewinne aus einem vorherigen Lauf. Ebenfalls frueh, weil ein Spieler
        // schon waehrend des Startvorgangs beitreten kann.
        pendingPayouts = new de.zfzfg.core.reward.PendingPayoutStore(this);
        pendingPayouts.load();

        safeLocations = new de.zfzfg.core.location.SafeLocationResolver(this);
        pluginWorlds = new de.zfzfg.core.location.PluginWorlds(this);

        // Zentrale Services
        configurationService = new ConfigurationService(this);
        taskManager = new TaskManager(this);

        // Lade Event-Konfigurationen
        configManager = new ConfigManager(this);
        configManager.loadConfigs();

        // Inventar-Verwaltung: Provider waehlen und das Guard-Journal einlesen.
        // Muss vor den Modulen stehen, weil MatchManager und EventSession direkt darauf
        // zugreifen; der Wiederanlauf laeuft dagegen erst am Ende von onEnable, wenn die
        // Manager wissen, welche Matches und Events tatsaechlich noch laufen.
        initInventoryManagement();

        // Optional external display integration
        initExternalDisplaySupport();
        initCombatIntegration();

        // Initialisiere Event-Manager
        eventManager = new EventManager(this);
        autoEventManager = new AutoEventManager(this);

        // Registriere Event-Command und Listener
        registerCommand("event", new EventCommand(this));
        de.zfzfg.eventplugin.commands.EventPvpCommand eventPvpCommand = new de.zfzfg.eventplugin.commands.EventPvpCommand(this);
        registerCommand("eventpvp", eventPvpCommand, eventPvpCommand);
        getServer().getPluginManager().registerEvents(new EventListener(this), this);
        getServer().getPluginManager().registerEvents(new WorldChangeListener(this), this);
        getServer().getPluginManager().registerEvents(new TeamPvPListener(this), this);

        // Zusätzliche Sicherheits-/Modus-Listener
        getServer().getPluginManager().registerEvents(new PlayerModeListener(this), this);
        getServer().getPluginManager().registerEvents(new de.zfzfg.eventplugin.security.WorldProtectionListener(this), this);
        
        // Update-Notify Listener
        getServer().getPluginManager().registerEvents(new UpdateNotifyListener(this), this);

        // KRITISCH: Void-Schutz-Listener für sichere Respawns
        getServer().getPluginManager().registerEvents(new VoidProtectionListener(this), this);

        if (configManager.isAutoEventsEnabled()) {
            autoEventManager.start();
        }

        // === PvP-Wager Modul ===
        worldStateManager = new WorldStateManager(this);
        pvpConfigManager = new de.zfzfg.pvpwager.managers.ConfigManager(this);
        arenaManager = new ArenaManager(this);
        equipmentManager = new EquipmentManager(this);
        matchManager = new MatchManager(this);
        requestManager = new RequestManager(this);
        commandRequestManager = new CommandRequestManager(this);
        liveTradeManager = new LiveTradeManager(this);
        statsManager = new StatsManager();
        // Load persistent PvP stats
        try {
            java.util.Map<java.util.UUID, de.zfzfg.pvpwager.models.PlayerStats> loadedPvpStats =
                de.zfzfg.pvpwager.storage.PvpStatsStorage.load(this);
            statsManager.loadFrom(loadedPvpStats);
        } catch (Exception e) {
            getLogger().warning(getConsoleMsg("stats-load-error", "error", e.getMessage()));
        }

        // Event stats manager + load
        eventStatsManager = new de.zfzfg.eventplugin.managers.EventStatsManager();
        try {
            java.util.Map<java.util.UUID, de.zfzfg.eventplugin.models.EventStats> loadedEventStats =
                de.zfzfg.eventplugin.storage.EventStatsStorage.load(this);
            eventStatsManager.loadFrom(loadedEventStats);
        } catch (Exception e) {
            getLogger().warning(getConsoleMsg("stats-load-error", "error", e.getMessage()));
        }

        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new EventPvpExpansion(eventStatsManager, statsManager).register();
            getLogger().info(getConsoleMsg("papi-registered"));
        }

        // Vault Economy Hook
        if (getServer().getPluginManager().getPlugin("Vault") != null) {
            setupEconomy();
        } else {
            getLogger().warning(getConsoleMsg("vault-not-found"));
        }

        // Central command cooldown manager with auto-cleanup on quit (must be initialized before PvP commands)
        commandCooldownManager = new CommandCooldownManager();
        // Localized cooldown text, set once here so every consumer of the manager
        // gets it -- not only the sub-command that happens to be built first.
        commandCooldownManager.setMessageProvider(seconds -> {
            String msg = getCoreConfigManager().getMessages().getString("messages.system.cooldown-wait", null);
            if (msg == null) {
                msg = getCoreConfigManager().getMessages().getString("messages.general.cooldown", null);
            }
            if (msg == null) {
                return "&c[missing: messages.system.cooldown-wait]";
            }
            return de.zfzfg.eventplugin.util.ColorUtil.color(msg.replace("{seconds}", String.valueOf(seconds)));
        });
        getServer().getPluginManager().registerEvents(new org.bukkit.event.Listener() {
            @org.bukkit.event.EventHandler
            public void onQuit(org.bukkit.event.player.PlayerQuitEvent e) {
                commandCooldownManager.removePlayer(e.getPlayer().getUniqueId());
            }
        }, this);

        // Registriere PvP-Commands (vereinheitlichte Sub-Commands unter /pvp)
        de.zfzfg.pvpwager.commands.unified.PvPUnifiedCommand unifiedPvp = new de.zfzfg.pvpwager.commands.unified.PvPUnifiedCommand(this);
        registerCommand("pvp", unifiedPvp, unifiedPvp);
        // Also listen for quit to clear unified surrender confirmations
        getServer().getPluginManager().registerEvents(unifiedPvp, this);
        // Die folgenden Alias-Befehle sind bewusst als @Deprecated markiert: sie werden von
        // /pvp bzw. /pvpanswer abgeloest, bleiben aber registriert, damit bestehende
        // Tastenbelegungen und Makros der Spieler weiter funktionieren. Die Unterdrueckung
        // steht deshalb an der einzelnen Deklaration und nicht an der ganzen Methode - so
        // faellt eine kuenftige, echte Deprecation in onEnable() weiterhin auf.
        @SuppressWarnings("deprecation")
        PvPACommand pvpaCommand = new PvPACommand(this);
        registerCommand("pvpa", pvpaCommand, pvpaCommand);
        PvPAnswerCommand pvpanswerCommand = new PvPAnswerCommand(this);
        registerCommand("pvpanswer", pvpanswerCommand, pvpanswerCommand);
        @SuppressWarnings("deprecation")
        PvPYesCommand pvpYesCommand = new PvPYesCommand(this);
        registerCommand("pvpyes", pvpYesCommand);
        @SuppressWarnings("deprecation")
        PvPNoCommand pvpNoCommand = new PvPNoCommand(this);
        registerCommand("pvpno", pvpNoCommand);
        registerCommand("pvpadmin", new PvPAdminCommand(this));
        SurrenderCommand surrenderCommand = new SurrenderCommand(this);
        registerCommand("surrender", surrenderCommand);
        // Register as listener to clear per-command confirmations on quit
        getServer().getPluginManager().registerEvents(surrenderCommand, this);
        registerCommand("draw", new DrawCommand(this));
        registerCommand("pvpainfo", new PvPInfoCommand(this));
        PvPStatsCommand pvpStatsCommand = new PvPStatsCommand(this);
        registerCommand("pvpstats", pvpStatsCommand, pvpStatsCommand);

        // GUI-Befehl für Wager-Anfragen
        pvpWagerGuiCommand = new PvPWagerGuiCommand(this);
        registerCommand("pvpask", pvpWagerGuiCommand, pvpWagerGuiCommand);

        // Wager Accept/Deny Befehle
        @SuppressWarnings("deprecation")
        PvPAcceptCommand acceptCommand = new PvPAcceptCommand(this);
        registerCommand("pvpaccept", acceptCommand, acceptCommand);

        @SuppressWarnings("deprecation")
        PvPDenyCommand denyCommand = new PvPDenyCommand(this);
        registerCommand("pvpdeny", denyCommand, denyCommand);

        // GUI-Befehl für Wager-Antworten
        de.zfzfg.pvpwager.commands.PvPRespondCommand respondCommand = new de.zfzfg.pvpwager.commands.PvPRespondCommand(this);
        registerCommand("pvprespond", respondCommand);

        // Event-Stats-Befehl
        de.zfzfg.eventplugin.commands.EventStatsCommand eventStatsCommand = new de.zfzfg.eventplugin.commands.EventStatsCommand(this);
        registerCommand("eventstats", eventStatsCommand, eventStatsCommand);

        // Registriere PvP-Listener
        getServer().getPluginManager().registerEvents(new PvPListener(this), this);
        getServer().getPluginManager().registerEvents(new SpectatorRecoveryListener(this), this);
        pvpWorldChangeListener = new de.zfzfg.pvpwager.listeners.WorldChangeListener(this);
        getServer().getPluginManager().registerEvents(pvpWorldChangeListener, this);
        // Cleanup pending requests on player quit
        getServer().getPluginManager().registerEvents(new de.zfzfg.pvpwager.listeners.RequestCleanupListener(this), this);
        // Live Trade GUI Listener
        getServer().getPluginManager().registerEvents(new LiveTradeListener(liveTradeManager), this);

        // === Multiverse-Weltverwaltung (Backend-Wahl MV5-API vs. Konsolenbefehle) ===
        mvWorldService = new de.zfzfg.core.world.mv.MvWorldService(this);

        // === Web-Interface starten ===
        webConfigManager = new WebConfigManager(this);
        if (webConfigManager.isEnabled()) {
            // Auth-Manager initialisieren
            boolean authEnabled = webConfigManager.isAuthEnabled();
            webAuthManager = new WebAuthManager(this, "eventpvp.admin.web");
            webAuthManager.setTokenValidityMinutes(webConfigManager.getTokenValidityMinutes());
            webAuthManager.setSessionValidityMinutes(webConfigManager.getSessionValidityMinutes());
            
            webServer = new WebServer(this, webConfigManager, webAuthManager, webConfigManager.getPort(), authEnabled);
            webServer.start();
            
            if (authEnabled) {
                getLogger().info(getConsoleMsg("web-auth-enabled"));
            }
        } else {
            getLogger().info(getConsoleMsg("web-disabled"));
        }

        // === Periodisches Statistik-Speichern (alle 5 Minuten) ===
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
            try {
                de.zfzfg.pvpwager.storage.PvpStatsStorage.save(this, statsManager.toMap());
                de.zfzfg.eventplugin.storage.EventStatsStorage.save(this, eventStatsManager.toMap());
                getLogger().info(getConsoleMsg("stats-save-periodic"));
            } catch (Exception e) {
                getLogger().log(java.util.logging.Level.WARNING, getConsoleMsg("stats-save-error", "error", e.getMessage()), e);
            }
        }, 6000L, 6000L); // 5 Minuten initial, dann alle 5 Minuten

        // === Update-Check ===
        // Der Checker wird immer angelegt, damit /eventpvp version ihn benutzen
        // kann; nur der Abruf beim Start haengt an der Konfiguration.
        updateChecker = new UpdateChecker(this, getDescription().getVersion(),
                configManager.getModrinthProjectId());
        if (configManager.isUpdateCheckEnabled() && configManager.shouldCheckOnStartup()) {
            updateChecker.checkForUpdates();
        }

        // === Inventar-Wiederanlauf ===
        // Ganz am Ende: erst jetzt koennen Match- und Event-Manager beantworten, ob eine
        // im Journal offene Sitzung noch zu einem laufenden Spiel gehoert.
        if (inventoryGuard != null) {
            inventoryGuard.recoverOpenSessions();
            reportStaleState();
            // Danach taeglich nachsehen: ein Eintrag, der erst im Betrieb haengenbleibt,
            // soll nicht bis zum naechsten Neustart unsichtbar bleiben.
            taskManager.runRepeating(this::reportStaleState,
                    de.zfzfg.core.util.Time.hours(1), de.zfzfg.core.util.Time.hours(6));
        }

        long enableMs = (System.nanoTime() - t0) / 1_000_000L;
        getLogger().info(getConsoleMsg("plugin-enabled", "time", String.valueOf(enableMs)));
    }

    /**
     * Baut Provider, Guard-Journal und Sitzungsverwaltung fuer die Inventare auf.
     *
     * <p>Scheitert hier etwas, laeuft das Plugin bewusst weiter - aber mit einem
     * NoOp-Provider und einer deutlichen Meldung. Ein Serverstart, der an der
     * Inventarverwaltung haengenbleibt, hilft niemandem.</p>
     */
    private void initInventoryManagement() {
        try {
            inventoryConfig = de.zfzfg.core.inventory.InventoryManagementConfig.load(this);
            inventoryBackupService = de.zfzfg.core.inventory.InventoryBackupServiceFactory
                    .create(this, inventoryConfig);

            inventoryGuard = new de.zfzfg.core.inventory.guard.InventoryGuard(this);
            inventoryGuard.load();
            inventorySessions = new de.zfzfg.core.inventory.InventorySessionManager(this, inventoryGuard);

            getServer().getPluginManager().registerEvents(
                    new de.zfzfg.core.inventory.InventoryGuardListener(this), this);

            // Der Konfliktschutz wird immer registriert, auch ohne Multiverse-Inventories:
            // der Provider laesst sich im Web-Panel zur Laufzeit umstellen, und ein Listener
            // laesst sich nachtraeglich nicht wieder abmelden. Ob er etwas tut, entscheidet
            // er bei jedem Event selbst.
            mviBridge = new de.zfzfg.core.inventory.mvi.MultiverseInventoriesBridge(this);
            getServer().getPluginManager().registerEvents(
                    new de.zfzfg.core.inventory.mvi.MviConflictListener(this, mviBridge), this);
            logMviDiagnosis();

            // Positions-Netz: laeuft 20 Ticks nach dem Join, also nach dem Inventar-Netz.
            strandedPlayers = new de.zfzfg.core.location.StrandedPlayerListener(this);
            getServer().getPluginManager().registerEvents(strandedPlayers, this);
        } catch (Exception e) {
            getLogger().severe(getConsoleMsg("inventory-init-failed", "error", String.valueOf(e.getMessage())));
            inventoryConfig = new de.zfzfg.core.inventory.InventoryManagementConfig(
                    coreConfigManager.getConfig());
            inventoryBackupService = new de.zfzfg.core.inventory.adapter.NoOpInventoryBackupAdapter();
            inventoryGuard = new de.zfzfg.core.inventory.guard.InventoryGuard(this);
            inventoryGuard.load();
            inventorySessions = new de.zfzfg.core.inventory.InventorySessionManager(this, inventoryGuard);
            if (mviBridge == null) {
                // Ohne Bridge wuerde jede Abfrage der Verzoegerung ins Leere laufen. Sie hat
                // keine eigenen Voraussetzungen und kommt auch nach einem Fehlschlag hoch.
                mviBridge = new de.zfzfg.core.inventory.mvi.MultiverseInventoriesBridge(this);
            }
        }

        // Bewusst ausserhalb des try: offene Gewinne muessen auch dann ausgeliefert werden,
        // wenn die Inventarverwaltung nicht hochkommt. Sie liegen bereits in der Datei und
        // haetten sonst keinen Weg zurueck zum Spieler.
        getServer().getPluginManager().registerEvents(
                new de.zfzfg.core.reward.PendingPayoutListener(this), this);
    }

    /** Ab diesem Alter gilt eine offene Sitzung als haengengeblieben. */
    private static final long STALE_THRESHOLD_HOURS = 24L;

    /**
     * Meldet haengengebliebene Inventar-Sitzungen und Rueckkehr-Positionen.
     *
     * <p>Beides sind Zustaende, die im Normalbetrieb binnen Minuten wieder verschwinden.
     * Bleibt etwas ueber einen Tag stehen, hat ein Wiederherstellungspfad versagt - und das
     * darf nicht nur im Web-Panel stehen, wo niemand ohne Anlass nachsieht.</p>
     */
    private void reportStaleState() {
        long maxAge = de.zfzfg.core.util.Time.hoursToMillis(STALE_THRESHOLD_HOURS);

        int staleSessions = inventoryGuard == null ? 0 : inventoryGuard.countStale(maxAge);
        int staleReturns = returnLocations == null ? 0 : returnLocations.countOlderThan(maxAge);

        if (staleSessions > 0) {
            getLogger().warning(getConsoleMsg("guard-stale-sessions",
                    "count", String.valueOf(staleSessions),
                    "hours", String.valueOf(STALE_THRESHOLD_HOURS)));
        }
        if (staleReturns > 0) {
            getLogger().warning(getConsoleMsg("return-stale-entries",
                    "count", String.valueOf(staleReturns),
                    "hours", String.valueOf(STALE_THRESHOLD_HOURS)));
        }
    }

    /** Provider fuer Inventar-Backups. Nie null. */
    public de.zfzfg.core.inventory.InventoryBackupService getInventoryBackupService() {
        return inventoryBackupService;
    }

    /** Aktuelle Einstellungen der Inventar-Verwaltung. Nie null. */
    public de.zfzfg.core.inventory.InventoryManagementConfig getInventoryConfig() {
        return inventoryConfig;
    }

    /** Erkennung und Konfliktschutz gegenueber Multiverse-Inventories. Nie null nach dem Start. */
    public de.zfzfg.core.inventory.mvi.MultiverseInventoriesBridge getMviBridge() {
        return mviBridge;
    }

    /**
     * Ticks, die nach einem Rueckteleport gewartet wird, bevor wiederhergestellt wird.
     *
     * <p>Der eine Ort, an dem diese Zahl steht. Frueher lagen dafuer 10 und 12 verstreut in
     * MatchManager und EventSession - beides Schaetzwerte fuer denselben Zweck.</p>
     */
    public long getInventoryRestoreDelayTicks() {
        return mviBridge == null ? 0L : mviBridge.restoreDelayTicks();
    }

    /**
     * Meldet auf der Konsole, welche Weltgruppen von Multiverse-Inventories mit den eigenen
     * Welten kollidieren - mit Weltnamen, Gruppe und dem Befehl, der es aufloest.
     *
     * <p>Die alte Meldung sagte nur, dass beide Plugins laufen. Damit konnte ein Admin
     * nichts anfangen: welche Welt das Problem ist, stand nirgends.</p>
     */
    private void logMviDiagnosis() {
        if (mviBridge == null || !mviBridge.isInstalled() || !inventoryConfig.managedByPlugin()) {
            return;
        }
        if (!inventoryConfig.warnOnMultiverseInventories()) {
            return;
        }
        de.zfzfg.core.inventory.mvi.MviConflictReport report = mviBridge.report();
        if (report.configUnreadable()) {
            getLogger().warning(getConsoleMsg("inventory-mvinv-groups-unknown"));
            return;
        }
        if (!report.hasCollisions()) {
            return;
        }
        getLogger().warning(getConsoleMsg("inventory-mvinv-conflict-header",
                "count", String.valueOf(report.collisions().size())));
        for (de.zfzfg.core.inventory.mvi.MviConflictReport.Collision collision : report.collisions()) {
            getLogger().warning(getConsoleMsg("inventory-mvinv-conflict-entry",
                    "world", collision.world(),
                    "group", collision.group(),
                    "partners", String.join(", ", collision.partnerWorlds()),
                    "command", collision.fixCommand()));
        }
        getLogger().warning(getConsoleMsg(inventoryConfig.mviConflictGuard()
                ? "inventory-mvinv-guard-active"
                : "inventory-mvinv-guard-inactive"));
    }

    /** Journal der offenen Inventar-Sitzungen. */
    public de.zfzfg.core.inventory.guard.InventoryGuard getInventoryGuard() {
        return inventoryGuard;
    }

    /** Fassade fuer Sichern und Wiederherstellen rund um Matches und Events. */
    public de.zfzfg.core.inventory.InventorySessionManager getInventorySessions() {
        return inventorySessions;
    }

    /** Persistente Rueckkehr-Positionen. Das Gegenstueck zum Inventar-Journal. */
    public de.zfzfg.core.location.ReturnLocationStore getReturnLocations() {
        return returnLocations;
    }

    /**
     * Gewinne und Belohnungen, die nicht sofort ausgegeben werden konnten.
     *
     * <p>Faengt die Faelle ab, in denen der Erfolgs-Callback der Wiederherstellung nicht
     * laeuft - Spieler offline oder Wiederherstellung fehlgeschlagen. Ohne diesen Speicher
     * waeren die Items in beiden Faellen verloren.</p>
     */
    public de.zfzfg.core.reward.PendingPayoutStore getPendingPayouts() {
        return pendingPayouts;
    }

    /** Einheitliche Antwort auf "wohin gehoert dieser Spieler". */
    public de.zfzfg.core.location.SafeLocationResolver getSafeLocations() {
        return safeLocations;
    }

    /** Auskunft, ob eine Welt dem Plugin gehoert (Event, Lobby, Arena). */
    public de.zfzfg.core.location.PluginWorlds getPluginWorlds() {
        return pluginWorlds;
    }

    /** Rettung gestrandeter Spieler. Auch der Weg fuer /eventpvp rescue. */
    public de.zfzfg.core.location.StrandedPlayerListener getStrandedPlayers() {
        return strandedPlayers;
    }

    /**
     * Liest nur die Einstellungen der Inventar-Verwaltung neu ein.
     *
     * <p>{@link de.zfzfg.core.inventory.InventoryManagementConfig} haelt seine Werte in
     * {@code final}-Feldern - ohne diesen Neubau wirkt eine Aenderung an
     * {@code settings.inventory-management.*} erst nach einem Serverneustart. Der Provider und
     * damit der laufende Backup-Service bleiben unangetastet, weil ein reiner Config-Reload
     * keine offenen Sitzungen auf einen neu gebauten Adapter umhaengen soll.</p>
     */
    public void reloadInventoryConfig() {
        inventoryConfig = de.zfzfg.core.inventory.InventoryManagementConfig.load(this);
    }

    /**
     * Baut die Inventar-Verwaltung nach einem Config-Reload neu auf.
     *
     * <p>Das Journal bleibt dabei stehen: offene Sitzungen gehoeren zu laufenden Matches und
     * duerfen einen Providerwechsel ueberleben.</p>
     */
    public void reloadInventoryManagement() {
        reloadInventoryConfig();
        inventoryBackupService = de.zfzfg.core.inventory.InventoryBackupServiceFactory
                .create(this, inventoryConfig);
        if (mviBridge != null) {
            // Weltgruppen und Verzoegerung neu einlesen: ein Reload kann beides geaendert
            // haben, und die Diagnose darf nicht auf dem Stand vom Serverstart stehenbleiben.
            mviBridge.refresh();
        }
        logMviDiagnosis();
    }

    @Override
    public void onDisable() {
        long t0 = System.nanoTime();
        
        // Web-Server stoppen
        if (webServer != null) {
            webServer.stop();
        }
        
        // Stoppe laufende Events
        if (eventManager != null) {
            eventManager.stopAllEvents();
        }
        if (autoEventManager != null) {
            autoEventManager.stop();
        }

        // Stoppe laufende Matches (sofortige Rückgabe von Items/Geld)
        if (matchManager != null) {
            matchManager.stopAllMatches(true);
            matchManager.cancelAllTasks();
            matchManager.clearTransientState();
        }
        if (requestManager != null) {
            requestManager.cleanup();
        }
        if (commandRequestManager != null) {
            commandRequestManager.cleanup();
        }
        if (liveTradeManager != null) {
            liveTradeManager.shutdown();
        }
        if (worldStateManager != null) {
            worldStateManager.clearCache();
        }
        // Cleanup throttling map in PvP WorldChangeListener
        if (pvpWorldChangeListener != null) {
            try { pvpWorldChangeListener.cleanup(); } catch (Exception ignored) {}
        }

        if (externalDisplayRefreshTaskId != -1) {
            Bukkit.getScheduler().cancelTask(externalDisplayRefreshTaskId);
            externalDisplayRefreshTaskId = -1;
        }
        if (externalDisplayBridge != null) {
            externalDisplayBridge.shutdown();
        }

        // Speichere Statistiken
        try {
            de.zfzfg.pvpwager.storage.PvpStatsStorage.save(this, statsManager.toMap());
        } catch (Exception e) {
            getLogger().warning(getConsoleMsg("stats-save-error", "error", e.getMessage()));
        }
        try {
            de.zfzfg.eventplugin.storage.EventStatsStorage.save(this, eventStatsManager.toMap());
        } catch (Exception e) {
            getLogger().warning(getConsoleMsg("stats-save-error", "error", e.getMessage()));
        }
        // Das Aufraeumen alter Backups gehoert zu InventoryBackup; dessen eigenes Pruning
        // wird hier bewusst nicht angestossen.

        // Inventar-Journal sichern. Bewusst OHNE Wiederherstellungsversuch: im onDisable
        // laeuft der Scheduler nicht mehr, ein Future wuerde nie komplettieren. Die offenen
        // Sitzungen stehen auf der Platte und werden beim naechsten Start abgearbeitet.
        if (inventorySessions != null) {
            try {
                inventorySessions.shutdown();
            } catch (Exception e) {
                getLogger().warning(getConsoleMsg("guard-save-failed", "error", String.valueOf(e.getMessage())));
            }
        }

        // Rueckkehr-Positionen sichern. Ebenfalls ohne Aufraeumen: wer hier noch einen
        // Eintrag hat, ist genau der Spieler, der ihn beim naechsten Start braucht.
        if (returnLocations != null) {
            try {
                returnLocations.shutdown();
            } catch (Exception e) {
                getLogger().warning(getConsoleMsg("return-save-failed", "error", String.valueOf(e.getMessage())));
            }
        }

        long disableMs = (System.nanoTime() - t0) / 1_000_000L;
        getLogger().info(getConsoleMsg("plugin-disabled", "time", String.valueOf(disableMs)));
    }

    private void initCombatIntegration() {
        if (configManager.isPvpManagerEnabled() && Bukkit.getPluginManager().getPlugin("PvPManager") != null) {
            combatBridge = new PvPManagerBridge();
            getLogger().info(getConsoleMsg("pvpmanager-enabled"));
        } else {
            combatBridge = new NoOpCombatBridge();
        }
    }

    public String getConsoleMsg(String key, String... replacements) {
        if (coreConfigManager != null) {
            return coreConfigManager.getConsoleMsg(key, replacements);
        }
        return key;
    }

    private void initExternalDisplaySupport() {
        if (configManager.isAjLeaderboardsEnabled() || configManager.isDecentHologramsEnabled()) {
            List<ExternalDisplayBridge> activeBridges = new ArrayList<>();
            if (configManager.isAjLeaderboardsEnabled()) {
                org.bukkit.plugin.Plugin plugin = getServer().getPluginManager().getPlugin("AJLeaderboards");
                if (plugin != null && plugin.isEnabled()) {
                    activeBridges.add(new PluginDisplayBridge(this, "AJLeaderboards",
                            new String[]{"refreshLeaderboards", "refreshAll", "updateLeaderboards", "refresh", "reload"},
                            new String[]{"refreshLeaderboards", "refreshAll", "updateLeaderboards", "refresh", "reload"}));
                } else {
                    getLogger().info("AJLeaderboards integration disabled: plugin not found or not loaded.");  // i18n-ignore: technical integration trace
                }
            }
            if (configManager.isDecentHologramsEnabled()) {
                org.bukkit.plugin.Plugin plugin = getServer().getPluginManager().getPlugin("DecentHolograms");
                if (plugin != null && plugin.isEnabled()) {
                    activeBridges.add(new PluginDisplayBridge(this, "DecentHolograms",
                            new String[]{"refreshHolograms", "refreshAll", "updateHolograms", "refresh", "reload"},
                            new String[]{"refreshHolograms", "refreshAll", "updateHolograms", "refresh", "reload"}));
                } else {
                    getLogger().info("DecentHolograms integration disabled: plugin not found or not loaded.");  // i18n-ignore: technical integration trace
                }
            }

            if (!activeBridges.isEmpty()) {
                externalDisplayBridge = new CompositeExternalDisplayBridge(activeBridges);
                scheduleExternalDisplayRefreshTask();
                getLogger().info("External display integration enabled for " + activeBridges.size() + " plugin(s).");  // i18n-ignore: technical integration trace
            } else {
                externalDisplayBridge = new NoOpExternalDisplayBridge();
            }
        } else {
            externalDisplayBridge = new NoOpExternalDisplayBridge();
        }
    }

    private void scheduleExternalDisplayRefreshTask() {
        int interval = Math.max(1, configManager.getIntegrationRefreshIntervalTicks());
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (externalDisplayDirty.compareAndSet(true, false)) {
                try {
                    externalDisplayBridge.refreshPvpBoard();
                    externalDisplayBridge.refreshEventBoard();
                } catch (Exception e) {
                    getLogger().warning("External display refresh failed: " + e.getMessage());  // i18n-ignore: technical exception log
                }
            }
        }, interval, interval);
        externalDisplayRefreshTaskId = task.getTaskId();
    }

    public void markExternalDisplayDirty() {
        if (externalDisplayBridge != null && externalDisplayBridge.isActive()) {
            externalDisplayDirty.set(true);
        }
    }

    private boolean checkRequiredDependencies() {
        // Multiverse-Core (Zwingend erforderlich für Weltverwaltung, Klonen und Regeneration)
        org.bukkit.plugin.Plugin mv = getServer().getPluginManager().getPlugin("Multiverse-Core");
        if (mv == null || !mv.isEnabled()) {
            getLogger().severe(getConsoleMsg("dependency-multiverse-missing"));
            return false;
        }

        // Vault (Zwingend erforderlich für Wetteinsätze und Wirtschaft)
        org.bukkit.plugin.Plugin vault = getServer().getPluginManager().getPlugin("Vault");
        if (vault == null || !vault.isEnabled()) {
            getLogger().severe(getConsoleMsg("dependency-vault-missing"));
            return false;
        }

        // InventoryBackup (Zwingend erforderlich für Inventarsicherung)
        org.bukkit.plugin.Plugin invBackup = getServer().getPluginManager().getPlugin("InventoryBackup");
        if (invBackup == null || !invBackup.isEnabled()) {
            getLogger().severe(getConsoleMsg("dependency-inventorybackup-missing"));
            return false;
        }

        return true;
    }

    private boolean setupEconomy() {
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        economy = rsp.getProvider();
        getLogger().info(getConsoleMsg("vault-hooked", "status", String.valueOf(economy != null)));
        return economy != null;
    }

    private void registerCommand(String name, org.bukkit.command.CommandExecutor executor) {
        org.bukkit.command.PluginCommand cmd = getCommand(name);
        if (cmd == null) {
            getLogger().warning("Command '" + name + "' not found in plugin.yml! Skipping registration.");  // i18n-ignore: command registration check
            return;
        }
        cmd.setExecutor(executor);
    }

    private void registerCommand(String name, org.bukkit.command.CommandExecutor executor, org.bukkit.command.TabCompleter tabCompleter) {
        org.bukkit.command.PluginCommand cmd = getCommand(name);
        if (cmd == null) {
            getLogger().warning("Command '" + name + "' not found in plugin.yml! Skipping registration.");  // i18n-ignore: command registration check
            return;
        }
        cmd.setExecutor(executor);
        if (tabCompleter != null) {
            cmd.setTabCompleter(tabCompleter);
        }
    }

    public static EventPlugin getInstance() {
        return instance;
    }

    // Event-Getters
    public ConfigManager getConfigManager() { return configManager; }
    public EventManager getEventManager() { return eventManager; }
    public AutoEventManager getAutoEventManager() { return autoEventManager; }

    // PvP-Getters (für angepasste Klassen)
    public de.zfzfg.pvpwager.managers.ConfigManager getPvpConfigManager() { return pvpConfigManager; }
    public MatchManager getMatchManager() { return matchManager; }
    public RequestManager getRequestManager() { return requestManager; }
    public ArenaManager getArenaManager() { return arenaManager; }
    public EquipmentManager getEquipmentManager() { return equipmentManager; }
    public CommandRequestManager getCommandRequestManager() { return commandRequestManager; }
    public StatsManager getStatsManager() { return statsManager; }
    public de.zfzfg.eventplugin.managers.EventStatsManager getEventStatsManager() { return eventStatsManager; }
    public Economy getEconomy() { return economy; }
    public boolean hasEconomy() { return economy != null; }

    public WorldStateManager getWorldStateManager() { return worldStateManager; }
    public CoreConfigManager getCoreConfigManager() { return coreConfigManager; }
    public ConfigurationService getConfigurationService() { return configurationService; }
    public TaskManager getTaskManager() { return taskManager; }

    public de.zfzfg.core.world.mv.MvWorldService getMvWorldService() { return mvWorldService; }
    public DebugManager getDebugManager() { return debugManager; }
    public LiveTradeManager getLiveTradeManager() { return liveTradeManager; }
    public PvPWagerGuiCommand getPvpWagerGuiCommand() { return pvpWagerGuiCommand; }
    public CommandCooldownManager getCommandCooldownManager() { return commandCooldownManager; }
    public WebAuthManager getWebAuthManager() { return webAuthManager; }
    public int getWebServerPort() { return webConfigManager != null ? webConfigManager.getPort() : 8085; }
    public String getWebPublicUrl() { 
        if (webConfigManager != null) {
            String url = webConfigManager.getPublicUrl();
            debugManager.logFull("EventPlugin.getWebPublicUrl() called, returning: " + url);  // i18n-ignore: technical debug log
            return url;
        } else {
            getLogger().warning("webConfigManager is NULL in getWebPublicUrl()!");  // i18n-ignore: technical debug log
            return "http://localhost:" + getWebServerPort();
        }
    }
    public UpdateChecker getUpdateChecker() { return updateChecker; }
    public CombatIntegrationBridge getCombatBridge() { return combatBridge; }
}
