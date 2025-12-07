package de.zfzfg.eventplugin.manager;

import de.zfzfg.eventplugin.EventPlugin;
import de.zfzfg.eventplugin.model.EventConfig;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class AutoEventManager {
    
    private final EventPlugin plugin;
    private BukkitTask autoEventTask;
    private final Random random;
    private int currentSequenceIndex = 0;
    
    public AutoEventManager(EventPlugin plugin) {
        this.plugin = plugin;
        this.random = new Random();
    }
    
    public void start() {
        if (autoEventTask != null) {
            autoEventTask.cancel();
        }
        scheduleNextEvent();
        plugin.getLogger().info("Auto-Event System gestartet!");
    }
    
    public void stop() {
        if (autoEventTask != null) {
            autoEventTask.cancel();
            autoEventTask = null;
        }
        plugin.getLogger().info("Auto-Event System gestoppt!");
    }
    
    private void scheduleNextEvent() {
        int minInterval = plugin.getConfigManager().getAutoEventIntervalMin();
        int maxInterval = plugin.getConfigManager().getAutoEventIntervalMax();
        int interval = minInterval + random.nextInt(maxInterval - minInterval + 1);
        
        plugin.getLogger().info("Nächstes Auto-Event in " + interval + " Sekunden");
        
        autoEventTask = new BukkitRunnable() {
            @Override
            public void run() {
                startNextEvent();
                scheduleNextEvent();
            }
        }.runTaskLater(plugin, de.zfzfg.core.util.Time.seconds(interval));
    }
    
    private void startNextEvent() {
        List<String> selectedEventIds = plugin.getConfigManager().getSelectedAutoEvents();
        boolean useRandomSelection = plugin.getConfigManager().isAutoEventRandomSelection();
        boolean checkPlayers = plugin.getConfigManager().shouldCheckOnlinePlayers();
        
        List<EventConfig> availableEvents = getAvailableEvents(selectedEventIds);
        
        if (availableEvents.isEmpty()) {
            plugin.getLogger().warning("Keine verfügbaren Events für Auto-Start!");
            return;
        }
        
        EventConfig selectedEvent = selectEvent(availableEvents, selectedEventIds, useRandomSelection);
        
        if (selectedEvent == null) {
            plugin.getLogger().warning("Kein Event konnte ausgewählt werden!");
            return;
        }
        
        if (checkPlayers) {
            int onlinePlayers = Bukkit.getOnlinePlayers().size();
            if (onlinePlayers < selectedEvent.getMinPlayers()) {
                plugin.getLogger().info("Nicht genug Spieler online für " + selectedEvent.getDisplayName() + 
                    " (" + onlinePlayers + "/" + selectedEvent.getMinPlayers() + ")");
                
                EventConfig fallback = findFallbackEvent(availableEvents, onlinePlayers);
                if (fallback != null) {
                    selectedEvent = fallback;
                    plugin.getLogger().info("Verwende Fallback-Event: " + selectedEvent.getDisplayName());
                } else {
                    return;
                }
            }
        }
        
        plugin.getLogger().info("Starte Auto-Event: " + selectedEvent.getDisplayName());
        if (plugin.getEventManager().createEvent(selectedEvent.getId())) {
            plugin.getEventManager().getSession(selectedEvent.getId())
                .ifPresent(session -> session.startJoinPhase());
        }
    }
    
    private List<EventConfig> getAvailableEvents(List<String> selectedEventIds) {
        List<EventConfig> available = new ArrayList<>();
        
        if (selectedEventIds.isEmpty()) {
            for (EventConfig config : plugin.getConfigManager().getAllEvents().values()) {
                if (!plugin.getEventManager().isEventActive(config.getId())) {
                    available.add(config);
                }
            }
        } else {
            for (String eventId : selectedEventIds) {
                EventConfig config = plugin.getConfigManager().getAllEvents().get(eventId);
                if (config != null && !plugin.getEventManager().isEventActive(config.getId())) {
                    available.add(config);
                }
            }
        }
        
        return available;
    }
    
    private EventConfig selectEvent(List<EventConfig> availableEvents, List<String> selectedEventIds, boolean randomMode) {
        if (availableEvents.isEmpty()) {
            return null;
        }
        
        if (randomMode) {
            return availableEvents.get(random.nextInt(availableEvents.size()));
        } else {
            if (!selectedEventIds.isEmpty()) {
                for (int i = 0; i < selectedEventIds.size(); i++) {
                    currentSequenceIndex = currentSequenceIndex % selectedEventIds.size();
                    String nextEventId = selectedEventIds.get(currentSequenceIndex);
                    currentSequenceIndex++;
                    
                    EventConfig config = plugin.getConfigManager().getAllEvents().get(nextEventId);
                    if (config != null && !plugin.getEventManager().isEventActive(nextEventId)) {
                        return config;
                    }
                }
            }
            return availableEvents.get(0);
        }
    }
    
    private EventConfig findFallbackEvent(List<EventConfig> events, int availablePlayers) {
        EventConfig best = null;
        int bestMinPlayers = 0;
        
        for (EventConfig event : events) {
            int minPlayers = event.getMinPlayers();
            if (minPlayers <= availablePlayers && minPlayers > bestMinPlayers) {
                bestMinPlayers = minPlayers;
                best = event;
            }
        }
        
        return best;
    }
}
