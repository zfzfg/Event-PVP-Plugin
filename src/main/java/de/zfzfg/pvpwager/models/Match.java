package de.zfzfg.pvpwager.models;

import de.zfzfg.pvpwager.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import java.util.*;

public class Match {
    private UUID matchId;
    // 5-stellige Kurz-ID zur Verknüpfung von Vor/Nach-Inventarsnapshots
    private final String eventMatchIdShort;
    private Player player1, player2;
    private MatchState state;
    private Arena arena;
    private EquipmentSet player1Equipment, player2Equipment;
    private Map<UUID, List<ItemStack>> wagerItems;
    private Map<UUID, Double> wagerMoney;
    private Set<UUID> spectators;
    private Map<UUID, Location> originalLocations;
    private long startTime;
    private final java.util.concurrent.atomic.AtomicBoolean drawVoteActive = new java.util.concurrent.atomic.AtomicBoolean(false);
    private final java.util.concurrent.atomic.AtomicReference<UUID> drawVoteInitiator = new java.util.concurrent.atomic.AtomicReference<>(null);
    
    // Bestätigungssystem
    private Integer player1Confirmed = null;
    private Integer player2Confirmed = null;
    private boolean wagerItemsReturned = false; // Verhindert doppelte Rückgabe
    
    // Countdown-Timer für Bestätigungen
    private Integer confirmationCountdown = null;
    private boolean confirmationActive = false;
    
    // No-Wager Mode
    private boolean noWagerMode;
    private Set<UUID> wagerSkipVotes;
    
    // Arena Bestätigung
    private boolean player1ArenaConfirmed = false;
    private boolean player2ArenaConfirmed = false;
    
    // Equipment Bestätigung
    private boolean player1EquipmentConfirmed = false;
    private boolean player2EquipmentConfirmed = false;
    
    // Welt-Lade-Status
    private boolean worldLoading = false;
    private long worldLoadingStartTime = 0;
    
    public Match(Player player1, Player player2) {
        this.matchId = UUID.randomUUID();
        this.eventMatchIdShort = generate5DigitId();
        this.player1 = player1;
        this.player2 = player2;
        this.state = MatchState.SETUP;
        this.wagerItems = new HashMap<>();
        this.wagerMoney = new HashMap<>();
        this.spectators = new HashSet<>();
        this.originalLocations = new HashMap<>();
        // drawVoteActive initialisiert oben als AtomicBoolean(false)
        this.noWagerMode = false;
        this.wagerSkipVotes = new HashSet<>();
        
        this.wagerItems.put(player1.getUniqueId(), new ArrayList<>());
        this.wagerItems.put(player2.getUniqueId(), new ArrayList<>());
        this.wagerMoney.put(player1.getUniqueId(), 0.0);
        this.wagerMoney.put(player2.getUniqueId(), 0.0);
    }

    private String generate5DigitId() {
        int n = 10000 + new java.util.Random().nextInt(90000);
        return String.valueOf(n);
    }
    
    // Bestätigungsmethoden für Wager
    public void confirmWager(Player player) {
        if (player.equals(player1)) {
            player1Confirmed = 5; // 5 Sekunden Countdown
        } else if (player.equals(player2)) {
            player2Confirmed = 5;
        }
    }
    
    public void cancelWagerConfirmation(Player player) {
        if (player.equals(player1)) {
            player1Confirmed = null;
        } else if (player.equals(player2)) {
            player2Confirmed = null;
        }
    }
    
    public boolean hasPlayerConfirmedWager(Player player) {
        if (player.equals(player1)) return player1Confirmed != null;
        if (player.equals(player2)) return player2Confirmed != null;
        return false;
    }
    
    public Integer getPlayerWagerConfirmation(Player player) {
        if (player.equals(player1)) return player1Confirmed;
        if (player.equals(player2)) return player2Confirmed;
        return null;
    }
    
    public void tickWagerConfirmation() {
        if (player1Confirmed != null) player1Confirmed--;
        if (player2Confirmed != null) player2Confirmed--;
    }
    
    public boolean bothPlayersConfirmedWager() {
        return player1Confirmed != null && player2Confirmed != null && 
               player1Confirmed <= 0 && player2Confirmed <= 0;
    }
    
    // Countdown-System für Bestätigungen
    public void startConfirmationCountdown() {
        this.confirmationCountdown = 5;
        this.confirmationActive = true;
    }
    
    public void stopConfirmationCountdown() {
        this.confirmationCountdown = null;
        this.confirmationActive = false;
    }
    
    public boolean isConfirmationActive() {
        return confirmationActive;
    }
    
    public Integer getConfirmationCountdown() {
        return confirmationCountdown;
    }
    
    public void tickConfirmationCountdown() {
        if (confirmationCountdown != null) {
            confirmationCountdown--;
            if (confirmationCountdown <= 0) {
                confirmationActive = false;
            }
        }
    }
    
    // Arena Bestätigung
    public void confirmArena(Player player) {
        if (player.equals(player1)) {
            player1ArenaConfirmed = true;
        } else if (player.equals(player2)) {
            player2ArenaConfirmed = true;
        }
    }
    
    public void cancelArenaConfirmation(Player player) {
        if (player.equals(player1)) {
            player1ArenaConfirmed = false;
        } else if (player.equals(player2)) {
            player2ArenaConfirmed = false;
        }
    }
    
    public boolean hasPlayerConfirmedArena(Player player) {
        if (player.equals(player1)) return player1ArenaConfirmed;
        if (player.equals(player2)) return player2ArenaConfirmed;
        return false;
    }
    
    public boolean bothPlayersConfirmedArena() {
        return player1ArenaConfirmed && player2ArenaConfirmed;
    }
    
    // Equipment Bestätigung
    public void confirmEquipment(Player player) {
        if (player.equals(player1)) {
            player1EquipmentConfirmed = true;
        } else if (player.equals(player2)) {
            player2EquipmentConfirmed = true;
        }
    }
    
    public void cancelEquipmentConfirmation(Player player) {
        if (player.equals(player1)) {
            player1EquipmentConfirmed = false;
        } else if (player.equals(player2)) {
            player2EquipmentConfirmed = false;
        }
    }
    
    public boolean hasPlayerConfirmedEquipment(Player player) {
        if (player.equals(player1)) return player1EquipmentConfirmed;
        if (player.equals(player2)) return player2EquipmentConfirmed;
        return false;
    }
    
    public boolean bothPlayersConfirmedEquipment() {
        return player1EquipmentConfirmed && player2EquipmentConfirmed;
    }
    
    public void markWagerItemsReturned() {
        this.wagerItemsReturned = true;
    }
    
    public boolean areWagerItemsReturned() {
        return wagerItemsReturned;
    }
    
    // Getters and setters
    public UUID getMatchId() { return matchId; }
    public String getEventMatchIdShort() { return eventMatchIdShort; }
    public Player getPlayer1() { return player1; }
    public Player getPlayer2() { return player2; }
    public MatchState getState() { return state; }
    public void setState(MatchState state) { this.state = state; }
    public Arena getArena() { return arena; }
    public void setArena(Arena arena) { 
        this.arena = arena;
        // Reset Arena-Bestätigungen bei Arena-Wechsel
        this.player1ArenaConfirmed = false;
        this.player2ArenaConfirmed = false;
    }
    public EquipmentSet getPlayer1Equipment() { return player1Equipment; }
    public void setPlayer1Equipment(EquipmentSet player1Equipment) { 
        this.player1Equipment = player1Equipment;
        this.player1EquipmentConfirmed = false;
    }
    public EquipmentSet getPlayer2Equipment() { return player2Equipment; }
    public void setPlayer2Equipment(EquipmentSet player2Equipment) { 
        this.player2Equipment = player2Equipment;
        this.player2EquipmentConfirmed = false;
    }
    public Map<UUID, List<ItemStack>> getWagerItems() { return wagerItems; }
    public List<ItemStack> getWagerItems(Player player) { return wagerItems.get(player.getUniqueId()); }
    public Map<UUID, Double> getWagerMoney() { return wagerMoney; }
    public double getWagerMoney(Player player) { return wagerMoney.get(player.getUniqueId()); }
    public Set<UUID> getSpectators() { return spectators; }
    public Map<UUID, Location> getOriginalLocations() { return originalLocations; }
    public Location getOriginalLocation(Player player) { return originalLocations.get(player.getUniqueId()); }
    public long getStartTime() { return startTime; }
    public void setStartTime(long startTime) { this.startTime = startTime; }
    public boolean isDrawVoteActive() { return drawVoteActive.get(); }
    public void setDrawVoteActive(boolean drawVoteActive) { this.drawVoteActive.set(drawVoteActive); }
    public UUID getDrawVoteInitiator() { return drawVoteInitiator.get(); }
    public void setDrawVoteInitiator(UUID drawVoteInitiator) { this.drawVoteInitiator.set(drawVoteInitiator); }

    // Atomic helpers for draw vote lifecycle
    public boolean tryActivateDrawVote(UUID initiatorId) {
        if (this.drawVoteActive.compareAndSet(false, true)) {
            this.drawVoteInitiator.set(initiatorId);
            return true;
        }
        return false;
    }

    public boolean cancelDrawVoteIfInitiator(UUID initiatorId) {
        UUID current = this.drawVoteInitiator.get();
        if (this.drawVoteActive.get() && java.util.Objects.equals(current, initiatorId)) {
            this.drawVoteActive.set(false);
            this.drawVoteInitiator.set(null);
            return true;
        }
        return false;
    }

    public void deactivateDrawVote() {
        this.drawVoteActive.set(false);
        this.drawVoteInitiator.set(null);
    }
    
    // No-Wager Mode
    public boolean isNoWagerMode() { return noWagerMode; }
    public void setNoWagerMode(boolean noWagerMode) { this.noWagerMode = noWagerMode; }
    public Set<UUID> getWagerSkipVotes() { return wagerSkipVotes; }
    
    public boolean hasPlayerVotedToSkip(Player player) {
        return wagerSkipVotes.contains(player.getUniqueId());
    }
    
    public void addSkipVote(Player player) {
        wagerSkipVotes.add(player.getUniqueId());
    }
    
    public void removeSkipVote(Player player) {
        wagerSkipVotes.remove(player.getUniqueId());
    }
    
    public boolean bothPlayersVotedToSkip() {
        return wagerSkipVotes.contains(player1.getUniqueId()) && 
               wagerSkipVotes.contains(player2.getUniqueId());
    }
    
    public Player getOpponent(Player player) {
        if (player.equals(player1)) return player2;
        if (player.equals(player2)) return player1;
        return null;
    }
    
    // Welt-Lade-Status
    public boolean isWorldLoading() { return worldLoading; }
    public void setWorldLoading(boolean worldLoading) { 
        this.worldLoading = worldLoading; 
        if (worldLoading) {
            this.worldLoadingStartTime = System.currentTimeMillis();
        }
    }
    public long getWorldLoadingStartTime() { return worldLoadingStartTime; }
    
    public void broadcast(String message) {
        String coloredMessage = MessageUtil.color(message);
        player1.sendMessage(coloredMessage);
        player2.sendMessage(coloredMessage);
        for (UUID spectatorId : spectators) {
            Player spectator = Bukkit.getPlayer(spectatorId);
            if (spectator != null && spectator.isOnline()) {
                spectator.sendMessage(coloredMessage);
            }
        }
    }
}