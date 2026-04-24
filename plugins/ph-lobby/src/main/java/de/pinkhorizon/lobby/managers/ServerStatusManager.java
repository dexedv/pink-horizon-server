package de.pinkhorizon.lobby.managers;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.pinkhorizon.lobby.PHLobby;
import org.bukkit.scheduler.BukkitTask;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class ServerStatusManager {

    public enum Status { ONLINE, RESTARTING, OFFLINE }

    public record ServerEntry(String id, String display, String host, int port) {}

    private record PingResult(boolean online, int players) {}

    /** Wie lange ein Server als "Neustart" gilt bevor er als Offline angezeigt wird. */
    private static final long RESTART_GRACE_MS = 120_000L;

    private final PHLobby plugin;
    private final List<ServerEntry>          servers      = new ArrayList<>();
    private final Map<String, Status>        statuses     = new LinkedHashMap<>();
    private final Map<String, Integer>       playerCounts = new HashMap<>();
    private final Map<String, Long>          lastOnline   = new HashMap<>();

    private BukkitTask pingTask;

    public ServerStatusManager(PHLobby plugin) {
        this.plugin = plugin;
        loadServers();
    }

    private void loadServers() {
        var section = plugin.getConfig().getConfigurationSection("servers");
        if (section == null) return;
        for (String id : section.getKeys(false)) {
            String display = plugin.getConfig().getString("servers." + id + ".display", id);
            String host    = plugin.getConfig().getString("servers." + id + ".host",    "localhost");
            int    port    = plugin.getConfig().getInt   ("servers." + id + ".port",    25565);
            servers.add(new ServerEntry(id, display, host, port));
            statuses.put(id, Status.OFFLINE);
            playerCounts.put(id, 0);
        }
    }

    public void start() {
        // 2 Sekunden Verzögerung, dann alle 5 Sekunden pingen
        pingTask = plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            Map<String, PingResult> results = new HashMap<>();
            for (ServerEntry s : servers) {
                results.put(s.id(), pingServer(s.host(), s.port()));
            }
            // Statusübergänge auf dem Haupt-Thread anwenden
            plugin.getServer().getScheduler().runTask(plugin, () -> applyResults(results));
        }, 40L, 100L);
    }

    private void applyResults(Map<String, PingResult> results) {
        long now = System.currentTimeMillis();
        for (ServerEntry s : servers) {
            PingResult r    = results.get(s.id());
            Status     prev = statuses.get(s.id());

            if (r.online()) {
                statuses.put(s.id(), Status.ONLINE);
                playerCounts.put(s.id(), r.players());
                lastOnline.put(s.id(), now);
            } else {
                playerCounts.put(s.id(), 0);
                if (prev == Status.ONLINE) {
                    // Gerade offline gegangen → Neustart annehmen
                    statuses.put(s.id(), Status.RESTARTING);
                    lastOnline.put(s.id(), now);
                } else if (prev == Status.RESTARTING) {
                    long elapsed = now - lastOnline.getOrDefault(s.id(), now);
                    if (elapsed > RESTART_GRACE_MS) {
                        statuses.put(s.id(), Status.OFFLINE);
                    }
                }
                // OFFLINE bleibt OFFLINE
            }
        }
        plugin.getScoreboardManager().refreshAll();
    }

    public void stop() {
        if (pingTask != null) pingTask.cancel();
    }

    public List<ServerEntry> getServers()   { return servers; }
    public Status  getStatus(String id)     { return statuses.getOrDefault(id, Status.OFFLINE); }
    public int     getPlayerCount(String id){ return playerCounts.getOrDefault(id, 0); }

    // ── Minecraft Server List Ping (SLP) ─────────────────────────────────

    private PingResult pingServer(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 1500);
            socket.setSoTimeout(1500);

            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            DataInputStream  in  = new DataInputStream(socket.getInputStream());

            // Handshake-Paket
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            DataOutputStream      pkt = new DataOutputStream(buf);
            writeVarInt(pkt, 0x00);       // Paket-ID: Handshake
            writeVarInt(pkt, 769);        // Protocol Version (1.21.4)
            writeString(pkt, host);       // Server-Adresse
            pkt.writeShort(port);         // Server-Port
            writeVarInt(pkt, 1);          // Next State: STATUS
            byte[] handshake = buf.toByteArray();
            writeVarInt(out, handshake.length);
            out.write(handshake);

            // Status-Request
            writeVarInt(out, 1);    // Paket-Länge
            writeVarInt(out, 0x00); // Paket-ID: Status Request
            out.flush();

            // Status-Response lesen
            readVarInt(in);              // Paket-Länge überspringen
            int packetId = readVarInt(in);
            if (packetId != 0x00) return new PingResult(false, 0);

            int    jsonLen   = readVarInt(in);
            byte[] jsonBytes = new byte[jsonLen];
            in.readFully(jsonBytes);
            String json = new String(jsonBytes, StandardCharsets.UTF_8);

            JsonObject obj     = JsonParser.parseString(json).getAsJsonObject();
            int        players = obj.getAsJsonObject("players").get("online").getAsInt();
            return new PingResult(true, players);

        } catch (Exception e) {
            return new PingResult(false, 0);
        }
    }

    private void writeVarInt(DataOutputStream out, int value) throws IOException {
        while (true) {
            if ((value & ~0x7F) == 0) { out.writeByte(value); return; }
            out.writeByte((value & 0x7F) | 0x80);
            value >>>= 7;
        }
    }

    private void writeString(DataOutputStream out, String s) throws IOException {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        writeVarInt(out, bytes.length);
        out.write(bytes);
    }

    private int readVarInt(DataInputStream in) throws IOException {
        int result = 0, shift = 0;
        byte b;
        do {
            b = in.readByte();
            result |= (b & 0x7F) << shift;
            shift  += 7;
        } while ((b & 0x80) != 0);
        return result;
    }
}
