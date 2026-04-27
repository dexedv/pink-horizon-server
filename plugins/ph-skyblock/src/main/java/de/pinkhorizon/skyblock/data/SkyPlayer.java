package de.pinkhorizon.skyblock.data;

import java.util.UUID;

/**
 * Repräsentiert einen Spieler im PH-SkyBlock-System.
 * Island-Management wird vollständig von BentoBox übernommen.
 * Dieses Objekt speichert nur noch unsere Custom-Daten (zuletzt gesehen, Name).
 */
public class SkyPlayer {

    private final UUID uuid;
    private String name;
    private long lastSeen;

    public SkyPlayer(UUID uuid, String name, long lastSeen) {
        this.uuid = uuid;
        this.name = name;
        this.lastSeen = lastSeen;
    }

    public UUID getUuid()        { return uuid; }
    public String getName()      { return name; }
    public long getLastSeen()    { return lastSeen; }

    public void setName(String v)     { this.name = v; }
    public void setLastSeen(long v)   { this.lastSeen = v; }
}
