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
import de.zfzfg.pvpwager.gui.GuiListener;
import de.zfzfg.pvpwager.gui.GuiManager;
import de.zfzfg.pvpwager.gui.livetrade.LiveTradeListener;
import de.zfzfg.pvpwager.gui.livetrade.LiveTradeManager;
import de.zfzfg.pvpwager.listeners.PvPListener;
import de.zfzfg.pvpwager.listeners.SpectatorRecoveryListener;
import de.zfzfg.pvpwager.managers.*;
import net.milkbowl.vault.economy.Economy;
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
    private DebugManager debugManager;
    private GuiManager guiManager;
    private LiveTradeManager liveTradeManager;
    private PvPWagerGuiCommand pvpWagerGuiCommand;
    // Keep reference to PvP WorldChangeListener for cleanup
    private de.zfzfg.pvpwager.listeners.WorldChangeListener pvpWorldChangeListener;
    // Web-Interface
    private WebConfigManager webConfigManager;
    private WebAuthManager webAuthManager;
    private WebServer webServer;
    private CommandCooldownManager commandCooldownManager;
    private UpdateChecker updateChecker;

    @Override
    public void onEnable() {
        long t0 = System.nanoTime();
        instance = this;

        // Zentralen Core-Config-Manager laden (vereinheitlichte Dateien)
        coreConfigManager = new CoreConfigManager(this);
        coreConfigManager.load();

        // Debug-Manager initialisieren (standardmäßig aus)
        debugManager = new DebugManager(this);

        // Zentrale Services
        configurationService = new ConfigurationService(this);
        taskManager = new TaskManager(this);

        // Lade Event-Konfigurationen
        configManager = new ConfigManager(this);
        configManager.loadConfigs();

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
        guiManager = new GuiManager(this);
        liveTradeManager = new LiveTradeManager(this);
        statsManager = new StatsManager();
        // Load persistent PvP stats
        try {
            java.util.Map<java.util.UUID, de.zfzfg.pvpwager.models.PlayerStats> loadedPvpStats =
                de.zfzfg.pvpwager.storage.PvpStatsStorage.load(this);
            statsManager.loadFrom(loadedPvpStats);
        } catch (Exception e) {
            getLogger().warning("Konnte PvP-Statistiken nicht laden: " + e.getMessage());
        }

        // Event stats manager + load
        eventStatsManager = new de.zfzfg.eventplugin.managers.EventStatsManager();
        try {
            java.util.Map<java.util.UUID, de.zfzfg.eventplugin.models.EventStats> loadedEventStats =
                de.zfzfg.eventplugin.storage.EventStatsStorage.load(this);
            eventStatsManager.loadFrom(loadedEventStats);
        } catch (Exception e) {
            getLogger().warning("Konnte Event-Statistiken nicht laden: " + e.getMessage());
        }

        // Vault Economy Hook
        if (getServer().getPluginManager().getPlugin("Vault") != null) {
            setupEconomy();
        } else {
            getLogger().warning("Vault nicht gefunden! Geld-Wetten sind deaktiviert.");
        }

        // Central command cooldown manager with auto-cleanup on quit (must be initialized before PvP commands)
        commandCooldownManager = new CommandCooldownManager();
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
        PvPACommand pvpaCommand = new PvPACommand(this);
        registerCommand("pvpa", pvpaCommand, pvpaCommand);
        PvPAnswerCommand pvpanswerCommand = new PvPAnswerCommand(this);
        registerCommand("pvpanswer", pvpanswerCommand, pvpanswerCommand);
        registerCommand("pvpyes", new PvPYesCommand(this));
        registerCommand("pvpno", new PvPNoCommand(this));
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
        PvPAcceptCommand acceptCommand = new PvPAcceptCommand(this);
        registerCommand("pvpaccept", acceptCommand, acceptCommand);

        PvPDenyCommand denyCommand = new PvPDenyCommand(this);
        registerCommand("pvpdeny", denyCommand, denyCommand);

        // GUI-Befehl für Wager-Antworten
        de.zfzfg.pvpwager.commands.PvPRespondCommand respondCommand = new de.zfzfg.pvpwager.commands.PvPRespondCommand(this);
        registerCommand("pvprespond", respondCommand);

        // Inventory restore command
        de.zfzfg.eventplugin.commands.InventoryRestoreCommand invRestore = new de.zfzfg.eventplugin.commands.InventoryRestoreCommand(this);
        registerCommand("inventoryrestore", invRestore, invRestore);

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
        // GUI-Listener für Wager-GUIs
        getServer().getPluginManager().registerEvents(new GuiListener(this), this);
        // Live Trade GUI Listener
        getServer().getPluginManager().registerEvents(new LiveTradeListener(this, liveTradeManager), this);

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
                getLogger().info("Web-Authentifizierung aktiviert. Nutze /eventpvp webtoken für Login.");
            }
        } else {
            getLogger().info("Web-Interface ist deaktiviert.");
        }

        // === Periodisches Statistik-Speichern (alle 5 Minuten) ===
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
            try {
                de.zfzfg.pvpwager.storage.PvpStatsStorage.save(this, statsManager.toMap());
                de.zfzfg.eventplugin.storage.EventStatsStorage.save(this, eventStatsManager.toMap());
                getLogger().info("Periodic stats save completed.");
            } catch (Exception e) {
                getLogger().log(java.util.logging.Level.WARNING, "Periodic stats save failed: " + e.getMessage(), e);
            }
        }, 6000L, 6000L); // 5 Minuten initial, dann alle 5 Minuten

        // === Update-Check ===
        if (configManager.isUpdateCheckEnabled() && configManager.shouldCheckOnStartup()) {
            String pluginVersion = getDescription().getVersion();
            updateChecker = new UpdateChecker(this, pluginVersion, configManager.getModrinthProjectId());
            updateChecker.checkForUpdates();
        }

        long enableMs = (System.nanoTime() - t0) / 1_000_000L;
        getLogger().info("Event-PVP-Plugin aktiviert in " + enableMs + " ms: Events & PvP-Wager kombiniert.");
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
        if (guiManager != null) {
            guiManager.cleanup();
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

        // Speichere Statistiken
        try {
            de.zfzfg.pvpwager.storage.PvpStatsStorage.save(this, statsManager.toMap());
        } catch (Exception e) {
            getLogger().warning("Konnte PvP-Statistiken nicht speichern: " + e.getMessage());
        }
        try {
            de.zfzfg.eventplugin.storage.EventStatsStorage.save(this, eventStatsManager.toMap());
        } catch (Exception e) {
            getLogger().warning("Konnte Event-Statistiken nicht speichern: " + e.getMessage());
        }
        // Prune old inventory backups (>30 Tage)
        try {
            de.zfzfg.eventplugin.storage.InventorySnapshotStorage.pruneOldEntries(this);
        } catch (Exception e) {
            getLogger().warning("Konnte alte Inventar-Backups nicht bereinigen: " + e.getMessage());
        }

        long disableMs = (System.nanoTime() - t0) / 1_000_000L;
        getLogger().info("Event-PVP-Plugin deaktiviert in " + disableMs + " ms.");
    }

    private boolean setupEconomy() {
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        economy = rsp.getProvider();
        getLogger().info("Vault Economy eingebunden: " + (economy != null));
        return economy != null;
    }

    private void registerCommand(String name, org.bukkit.command.CommandExecutor executor) {
        org.bukkit.command.PluginCommand cmd = getCommand(name);
        if (cmd == null) {
            getLogger().warning("Command '" + name + "' not found in plugin.yml! Skipping registration.");
            return;
        }
        cmd.setExecutor(executor);
    }

    private void registerCommand(String name, org.bukkit.command.CommandExecutor executor, org.bukkit.command.TabCompleter tabCompleter) {
        org.bukkit.command.PluginCommand cmd = getCommand(name);
        if (cmd == null) {
            getLogger().warning("Command '" + name + "' not found in plugin.yml! Skipping registration.");
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
    public DebugManager getDebugManager() { return debugManager; }
    public GuiManager getGuiManager() { return guiManager; }
    public LiveTradeManager getLiveTradeManager() { return liveTradeManager; }
    public PvPWagerGuiCommand getPvpWagerGuiCommand() { return pvpWagerGuiCommand; }
    public CommandCooldownManager getCommandCooldownManager() { return commandCooldownManager; }
    public WebAuthManager getWebAuthManager() { return webAuthManager; }
    public int getWebServerPort() { return webConfigManager != null ? webConfigManager.getPort() : 8085; }
    public String getWebPublicUrl() { 
        if (webConfigManager != null) {
            String url = webConfigManager.getPublicUrl();
            getLogger().info("EventPlugin.getWebPublicUrl() called, returning: " + url);
            return url;
        } else {
            getLogger().warning("webConfigManager is NULL in getWebPublicUrl()!");
            return "http://localhost:" + getWebServerPort();
        }
    }
    public UpdateChecker getUpdateChecker() { return updateChecker; }
}