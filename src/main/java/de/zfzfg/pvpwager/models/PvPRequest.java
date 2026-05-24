package de.zfzfg.pvpwager.models;

import java.util.UUID;

public class PvPRequest {
    private UUID senderId;
    private UUID targetId;
    private long timestamp;
    
    public PvPRequest(UUID senderId, UUID targetId) {
        this.senderId = senderId;
        this.targetId = targetId;
        this.timestamp = System.currentTimeMillis();
    }
    
    // Getters and setters
    public UUID getSenderId() { return senderId; }
    public UUID getTargetId() { return targetId; }
    public long getTimestamp() { return timestamp; }
}