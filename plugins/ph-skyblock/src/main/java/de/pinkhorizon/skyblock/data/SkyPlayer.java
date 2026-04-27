package de.pinkhorizon.skyblock.data;

import java.util.UUID;

public class SkyPlayer {

    private final UUID uuid;
    private String name;
    private Integer islandId;   // null = kein Mitglied
    private boolean islandChat;
    private long lastSeen;

    public SkyPlayer(UUID uuid, String name, Integer islandId, boolean islandChat, long lastSeen) {
        this.uuid = uuid;
        this.name = name;
        this.islandId = islandId;
        this.islandChat = islandChat;
        this.lastSeen = lastSeen;
    }

    public UUID getUuid()        { return uuid; }
    public String getName()      { return name; }
    public Integer getIslandId() { return islandId; }
    public boolean isIslandChat(){ return islandChat; }
    public long getLastSeen()    { return lastSeen; }

    public void setName(String v)       { this.name = v; }
    public void setIslandId(Integer v)  { this.islandId = v; }
    public void setIslandChat(boolean v){ this.islandChat = v; }
    public void setLastSeen(long v)     { this.lastSeen = v; }
}
