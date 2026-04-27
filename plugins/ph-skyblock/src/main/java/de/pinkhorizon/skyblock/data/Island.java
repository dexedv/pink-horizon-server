package de.pinkhorizon.skyblock.data;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Island {

    private final int id;
    private final UUID islandUuid;
    private UUID ownerUuid;
    private String ownerName;
    private final String world;
    private final int centerX, centerY, centerZ;
    private double homeX, homeY, homeZ;
    private float homeYaw, homePitch;
    private long level;
    private long score;
    private int size;
    private int maxMembers;
    private boolean open;
    private boolean warpEnabled;
    private String warpName;
    private long createdAt;
    private long lastActive;

    private final List<IslandMember> members = new ArrayList<>();

    public Island(int id, UUID islandUuid, UUID ownerUuid, String ownerName,
                  String world, int centerX, int centerY, int centerZ,
                  double homeX, double homeY, double homeZ, float homeYaw, float homePitch,
                  long level, long score, int size, int maxMembers,
                  boolean open, boolean warpEnabled, String warpName,
                  long createdAt, long lastActive) {
        this.id = id;
        this.islandUuid = islandUuid;
        this.ownerUuid = ownerUuid;
        this.ownerName = ownerName;
        this.world = world;
        this.centerX = centerX;
        this.centerY = centerY;
        this.centerZ = centerZ;
        this.homeX = homeX;
        this.homeY = homeY;
        this.homeZ = homeZ;
        this.homeYaw = homeYaw;
        this.homePitch = homePitch;
        this.level = level;
        this.score = score;
        this.size = size;
        this.maxMembers = maxMembers;
        this.open = open;
        this.warpEnabled = warpEnabled;
        this.warpName = warpName;
        this.createdAt = createdAt;
        this.lastActive = lastActive;
    }

    public boolean isMember(UUID uuid) {
        if (uuid.equals(ownerUuid)) return true;
        return members.stream().anyMatch(m -> m.uuid().equals(uuid));
    }

    public boolean isCoOwner(UUID uuid) {
        return members.stream().anyMatch(m -> m.uuid().equals(uuid) && m.role() == MemberRole.COOP);
    }

    public boolean isBanned(UUID uuid) {
        return false; // managed separately
    }

    public IslandMember getMember(UUID uuid) {
        return members.stream().filter(m -> m.uuid().equals(uuid)).findFirst().orElse(null);
    }

    public boolean isWithinBorder(int x, int z) {
        int half = size / 2;
        return x >= centerX - half && x <= centerX + half
            && z >= centerZ - half && z <= centerZ + half;
    }

    // ── Getters / Setters ─────────────────────────────────────────────────────

    public int getId()            { return id; }
    public UUID getIslandUuid()   { return islandUuid; }
    public UUID getOwnerUuid()    { return ownerUuid; }
    public String getOwnerName()  { return ownerName; }
    public String getWorld()      { return world; }
    public int getCenterX()       { return centerX; }
    public int getCenterY()       { return centerY; }
    public int getCenterZ()       { return centerZ; }
    public double getHomeX()      { return homeX; }
    public double getHomeY()      { return homeY; }
    public double getHomeZ()      { return homeZ; }
    public float getHomeYaw()     { return homeYaw; }
    public float getHomePitch()   { return homePitch; }
    public long getLevel()        { return level; }
    public long getScore()        { return score; }
    public int getSize()          { return size; }
    public int getMaxMembers()    { return maxMembers; }
    public boolean isOpen()       { return open; }
    public boolean isWarpEnabled(){ return warpEnabled; }
    public String getWarpName()   { return warpName; }
    public long getCreatedAt()    { return createdAt; }
    public long getLastActive()   { return lastActive; }
    public List<IslandMember> getMembers() { return members; }

    public void setOwnerUuid(UUID v)     { this.ownerUuid = v; }
    public void setOwnerName(String v)   { this.ownerName = v; }
    public void setHomeX(double v)       { this.homeX = v; }
    public void setHomeY(double v)       { this.homeY = v; }
    public void setHomeZ(double v)       { this.homeZ = v; }
    public void setHomeYaw(float v)      { this.homeYaw = v; }
    public void setHomePitch(float v)    { this.homePitch = v; }
    public void setLevel(long v)         { this.level = v; }
    public void setScore(long v)         { this.score = v; }
    public void setSize(int v)           { this.size = v; }
    public void setMaxMembers(int v)     { this.maxMembers = v; }
    public void setOpen(boolean v)       { this.open = v; }
    public void setWarpEnabled(boolean v){ this.warpEnabled = v; }
    public void setWarpName(String v)    { this.warpName = v; }
    public void setLastActive(long v)    { this.lastActive = v; }

    public enum MemberRole { MEMBER, COOP, VISITOR }

    public record IslandMember(UUID uuid, String name, MemberRole role, long joinedAt) {}
}
