const express  = require('express');
const { WebSocketServer } = require('ws');
const http     = require('http');
const Docker   = require('dockerode');
const net      = require('net');
const path     = require('path');
const fs       = require('fs').promises;

const app    = express();
const server = http.createServer(app);
const wss    = new WebSocketServer({ server });
const docker = new Docker({ socketPath: '/var/run/docker.sock' });

app.use(express.json());
app.use(express.static(path.join(__dirname, 'public')));

// ── Konfiguration ─────────────────────────────────────────────────────────

const DASHBOARD_PASSWORD = process.env.DASHBOARD_PASSWORD || 'pinkhorizon2024';
const PORT = process.env.PORT || 3000;

const SERVERS = {
  velocity: {
    label:     'Velocity Proxy',
    container: 'ph-velocity',
    color:     '#9B59B6',
    rcon:      null
  },
  lobby: {
    label:     'Lobby',
    container: 'ph-lobby',
    color:     '#3498DB',
    rcon:      { host: process.env.LOBBY_RCON_HOST || 'lobby', port: 25575, password: process.env.RCON_PASSWORD || 'ph-admin-2024' }
  },
  survival: {
    label:     'Survival',
    container: 'ph-survival',
    color:     '#2ECC71',
    rcon:      { host: process.env.SURVIVAL_RCON_HOST || 'survival', port: 25575, password: process.env.RCON_PASSWORD || 'ph-admin-2024' }
  }
};

// Verfügbare Survival-Ränge (für Validierung)
const VALID_RANKS = ['spieler', 'vip', 'mvp', 'mod', 'admin', 'owner'];

// ── Auth-Middleware ───────────────────────────────────────────────────────

function auth(req, res, next) {
  const token = req.headers['x-dashboard-token'];
  if (token !== DASHBOARD_PASSWORD) return res.status(401).json({ error: 'Unauthorized' });
  next();
}

// ── Hilfsfunktionen ───────────────────────────────────────────────────────

/** Entfernt Minecraft-Farbcodes (§x) aus Text. */
function stripColors(s) {
  return (s || '').replace(/§[0-9a-fk-orA-FK-OR]/g, '').trim();
}

/** Parst KEY:VALUE-Zeilen aus der /phinfo-Antwort. */
function parseKeyValue(raw) {
  const result = {};
  for (const line of stripColors(raw).split('\n')) {
    const idx = line.indexOf(':');
    if (idx > 0) {
      result[line.slice(0, idx).trim()] = line.slice(idx + 1).trim();
    }
  }
  return result;
}

// ── RCON-Client ───────────────────────────────────────────────────────────

/**
 * Sendet einen RCON-Befehl und sammelt ALLE Antwort-Pakete
 * (Paper sendet sendMessage()-Zeilen teils als mehrere Pakete).
 * Löst auf sobald 300 ms lang kein neues Paket kommt.
 */
function rconSend(rcon, command) {
  return new Promise((resolve, reject) => {
    const socket  = new net.Socket();
    const reqId   = Math.floor(Math.random() * 10000) + 1;
    let   authed  = false;
    let   buf     = Buffer.alloc(0);
    let   bodies  = [];
    let   timer   = null;

    const finish = () => { socket.destroy(); resolve(bodies.join('\n') || '✓'); };

    const buildPacket = (id, type, body) => {
      const bodyBuf = Buffer.from(body + '\0\0', 'utf8');
      const len     = 4 + 4 + bodyBuf.length;
      const pkt     = Buffer.alloc(4 + len);
      pkt.writeInt32LE(len, 0);
      pkt.writeInt32LE(id, 4);
      pkt.writeInt32LE(type, 8);
      bodyBuf.copy(pkt, 12);
      return pkt;
    };

    socket.setTimeout(6000);
    socket.connect(rcon.port, rcon.host, () => {
      socket.write(buildPacket(reqId, 3, rcon.password));
    });

    socket.on('data', chunk => {
      buf = Buffer.concat([buf, chunk]);
      while (buf.length >= 4) {
        const len = buf.readInt32LE(0);
        if (buf.length < 4 + len) break;
        const id   = buf.readInt32LE(4);
        const body = buf.slice(12, 4 + len - 2).toString('utf8');
        buf = buf.slice(4 + len);

        if (!authed) {
          if (id === -1) { socket.destroy(); return reject(new Error('RCON: Falsches Passwort')); }
          authed = true;
          socket.write(buildPacket(reqId + 1, 2, command));
        } else {
          if (body) bodies.push(body);
          clearTimeout(timer);
          timer = setTimeout(finish, 300);
        }
      }
    });

    socket.on('timeout', () => { clearTimeout(timer); socket.destroy(); reject(new Error('RCON Timeout')); });
    socket.on('error',   err  => { clearTimeout(timer); socket.destroy(); reject(err); });
    socket.on('close',   ()   => { clearTimeout(timer); finish(); });
  });
}

// ── Docker-Hilfsfunktionen ────────────────────────────────────────────────

async function getContainerStatus(containerName) {
  try {
    const c    = docker.getContainer(containerName);
    const info = await c.inspect();
    return {
      running: info.State.Running,
      status:  info.State.Status,
      started: info.State.StartedAt
    };
  } catch {
    return { running: false, status: 'nicht gefunden', started: null };
  }
}

// ── REST-API: Server ──────────────────────────────────────────────────────

app.post('/api/login', (req, res) => {
  if (req.body.password === DASHBOARD_PASSWORD)
    return res.json({ ok: true, token: DASHBOARD_PASSWORD });
  res.status(401).json({ ok: false });
});

app.get('/api/servers', auth, async (req, res) => {
  const result = {};
  for (const [key, cfg] of Object.entries(SERVERS)) {
    result[key] = { ...cfg, ...(await getContainerStatus(cfg.container)) };
  }
  res.json(result);
});

app.post('/api/servers/:name/command', auth, async (req, res) => {
  const cfg = SERVERS[req.params.name];
  if (!cfg)      return res.status(404).json({ error: 'Unbekannter Server' });
  if (!cfg.rcon) return res.status(400).json({ error: 'Kein RCON für diesen Server' });
  const { command } = req.body;
  if (!command)  return res.status(400).json({ error: 'Kein Befehl' });
  try {
    const out = await rconSend(cfg.rcon, command);
    res.json({ ok: true, output: out });
  } catch (e) {
    res.status(500).json({ ok: false, error: e.message });
  }
});

app.post('/api/servers/:name/restart', auth, async (req, res) => {
  const cfg = SERVERS[req.params.name];
  if (!cfg) return res.status(404).json({ error: 'Unbekannter Server' });
  try { await docker.getContainer(cfg.container).restart(); res.json({ ok: true }); }
  catch (e) { res.status(500).json({ ok: false, error: e.message }); }
});

app.post('/api/servers/:name/stop', auth, async (req, res) => {
  const cfg = SERVERS[req.params.name];
  if (!cfg) return res.status(404).json({ error: 'Unbekannter Server' });
  try { await docker.getContainer(cfg.container).stop(); res.json({ ok: true }); }
  catch (e) { res.status(500).json({ ok: false, error: e.message }); }
});

app.post('/api/servers/:name/start', auth, async (req, res) => {
  const cfg = SERVERS[req.params.name];
  if (!cfg) return res.status(404).json({ error: 'Unbekannter Server' });
  try { await docker.getContainer(cfg.container).start(); res.json({ ok: true }); }
  catch (e) { res.status(500).json({ ok: false, error: e.message }); }
});

// ── REST-API: Spielerverwaltung ───────────────────────────────────────────

/** Online-Spieler eines Servers via RCON `list`. */
app.get('/api/players/online', auth, async (req, res) => {
  const serverKey = req.query.server || 'survival';
  const cfg = SERVERS[serverKey];
  if (!cfg || !cfg.rcon) return res.status(400).json({ players: [] });
  try {
    const raw   = await rconSend(cfg.rcon, 'list');
    const clean = stripColors(raw);
    const match = /players online:\s*(.+)/i.exec(clean);
    const players = match
      ? match[1].split(',').map(s => s.trim()).filter(Boolean)
      : [];
    res.json({ players });
  } catch {
    res.json({ players: [] });
  }
});

/** Vollständige Spielerdaten via /phinfo (Survival-Plugin). */
app.post('/api/players/info', auth, async (req, res) => {
  const { name } = req.body;
  if (!name) return res.status(400).json({ error: 'Kein Spielername' });

  const cfg = SERVERS.survival;
  if (!cfg.rcon) return res.status(400).json({ error: 'Kein RCON' });

  try {
    const raw  = await rconSend(cfg.rcon, `phinfo ${name}`);
    const text = stripColors(raw);

    if (text === 'NOT_FOUND')      return res.status(404).json({ error: 'Spieler nicht gefunden' });
    if (text === 'NOT_AUTHORIZED') return res.status(403).json({ error: 'Keine Berechtigung' });

    const kv = parseKeyValue(raw);
    if (!kv.NAME) return res.status(404).json({ error: 'Spieler nicht gefunden' });

    const playtime = parseInt(kv.PLAYTIME) || 0;
    res.json({
      name:         kv.NAME,
      coins:        parseInt(kv.COINS)         || 0,
      rank:         kv.RANK                    || 'spieler',
      playtimeMin:  playtime,
      playtimeH:    Math.floor(playtime / 60),
      playtimeM:    playtime % 60,
      deaths:       parseInt(kv.DEATHS)        || 0,
      mobKills:     parseInt(kv.MOB_KILLS)     || 0,
      playerKills:  parseInt(kv.PLAYER_KILLS)  || 0,
      blocksBroken: parseInt(kv.BLOCKS_BROKEN) || 0,
      job:          kv.JOB                     || 'NONE',
      jobDisplay:   kv.JOB_DISPLAY             || '-',
      jobLevel:     parseInt(kv.JOB_LEVEL)     || 0,
      jobXp:        parseInt(kv.JOB_XP)        || 0
    });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

/** Spielerdaten ändern (Coins, Rang). */
app.post('/api/players/action', auth, async (req, res) => {
  const { name, type, value, rank } = req.body;
  if (!name) return res.status(400).json({ error: 'Kein Spielername' });

  const cfg = SERVERS.survival;
  if (!cfg.rcon) return res.status(400).json({ error: 'Kein RCON' });

  let cmd;
  switch (type) {
    case 'eco_give': {
      const v = parseInt(value);
      if (!Number.isInteger(v) || v <= 0) return res.status(400).json({ error: 'Ungültiger Betrag' });
      cmd = `eco give ${name} ${v}`;
      break;
    }
    case 'eco_take': {
      const v = parseInt(value);
      if (!Number.isInteger(v) || v <= 0) return res.status(400).json({ error: 'Ungültiger Betrag' });
      cmd = `eco take ${name} ${v}`;
      break;
    }
    case 'eco_set': {
      const v = parseInt(value);
      if (!Number.isInteger(v) || v < 0) return res.status(400).json({ error: 'Ungültiger Betrag' });
      cmd = `eco set ${name} ${v}`;
      break;
    }
    case 'rank_set': {
      if (!VALID_RANKS.includes(rank)) return res.status(400).json({ error: 'Ungültiger Rang' });
      cmd = `srank set ${name} ${rank}`;
      break;
    }
    default:
      return res.status(400).json({ error: 'Unbekannte Aktion' });
  }

  try {
    const out = await rconSend(cfg.rcon, cmd);
    res.json({ ok: true, output: stripColors(out) });
  } catch (e) {
    res.status(500).json({ ok: false, error: e.message });
  }
});

// ── REST-API: Wirtschaft + Spieleraktionen ────────────────────────────────

/** Baltop via RCON. */
app.get('/api/economy/baltop', auth, async (req, res) => {
  const cfg = SERVERS.survival;
  if (!cfg.rcon) return res.status(400).json({ error: 'Kein RCON' });
  try {
    const raw   = await rconSend(cfg.rcon, 'baltop');
    const clean = stripColors(raw);
    const entries = [];
    for (const line of clean.split('\n')) {
      const m = /(\d+)\.\s+(.+?)\s+[–\-]\s+([\d.,]+)\s+Coins?/i.exec(line);
      if (m) entries.push({ rank: parseInt(m[1]), name: m[2].trim(), coins: parseInt(m[3].replace(/[.,]/g,'')) });
    }
    res.json({ baltop: entries });
  } catch (e) { res.status(500).json({ error: e.message }); }
});

/** Spieler kicken. */
app.post('/api/players/kick', auth, async (req, res) => {
  const { name, reason } = req.body;
  if (!name || !/^[a-zA-Z0-9_]{1,16}$/.test(name)) return res.status(400).json({ error: 'Ungültiger Name' });
  const cfg = SERVERS.survival;
  if (!cfg.rcon) return res.status(400).json({ error: 'Kein RCON' });
  try {
    const out = await rconSend(cfg.rcon, reason ? `kick ${name} ${reason}` : `kick ${name}`);
    res.json({ ok: true, output: stripColors(out) });
  } catch (e) { res.status(500).json({ ok: false, error: e.message }); }
});

/** Spieler bannen. */
app.post('/api/players/ban', auth, async (req, res) => {
  const { name, reason } = req.body;
  if (!name || !/^[a-zA-Z0-9_]{1,16}$/.test(name)) return res.status(400).json({ error: 'Ungültiger Name' });
  const cfg = SERVERS.survival;
  if (!cfg.rcon) return res.status(400).json({ error: 'Kein RCON' });
  try {
    const out = await rconSend(cfg.rcon, reason ? `ban ${name} ${reason}` : `ban ${name}`);
    res.json({ ok: true, output: stripColors(out) });
  } catch (e) { res.status(500).json({ ok: false, error: e.message }); }
});

// ── REST-API: Rechteverwaltung ────────────────────────────────────────────

const MANAGED_SERVERS = ['lobby', 'survival'];

/** Liest ops.json aus dem gemounteten Server-Verzeichnis. */
app.get('/api/servers/:name/ops', auth, async (req, res) => {
  const name = req.params.name;
  if (!MANAGED_SERVERS.includes(name)) return res.status(400).json({ error: 'Unbekannter Server' });
  try {
    const raw = await fs.readFile(`/data/${name}/ops.json`, 'utf8');
    res.json({ ops: JSON.parse(raw) });
  } catch {
    res.json({ ops: [] });
  }
});

/** Liest whitelist.json und Whitelist-Status aus server.properties. */
app.get('/api/servers/:name/whitelist', auth, async (req, res) => {
  const name = req.params.name;
  if (!MANAGED_SERVERS.includes(name)) return res.status(400).json({ error: 'Unbekannter Server' });
  try {
    const [wlRaw, propsRaw] = await Promise.all([
      fs.readFile(`/data/${name}/whitelist.json`, 'utf8').catch(() => '[]'),
      fs.readFile(`/data/${name}/server.properties`, 'utf8').catch(() => '')
    ]);
    const whitelist = JSON.parse(wlRaw);
    const enabled   = /^white-list=true$/m.test(propsRaw);
    res.json({ whitelist, enabled });
  } catch {
    res.json({ whitelist: [], enabled: false });
  }
});

/** OP vergeben oder entziehen via RCON. */
app.post('/api/servers/:name/op', auth, async (req, res) => {
  const name = req.params.name;
  const cfg  = SERVERS[name];
  if (!cfg || !cfg.rcon) return res.status(400).json({ error: 'Kein RCON für diesen Server' });
  const { player, action } = req.body;
  if (!['op', 'deop'].includes(action)) return res.status(400).json({ error: 'Ungültige Aktion' });
  if (!player || !/^[a-zA-Z0-9_]{1,16}$/.test(player))
    return res.status(400).json({ error: 'Ungültiger Spielername' });
  try {
    const out = await rconSend(cfg.rcon, `${action} ${player}`);
    res.json({ ok: true, output: stripColors(out) });
  } catch (e) {
    res.status(500).json({ ok: false, error: e.message });
  }
});

/** Whitelist add/remove/on/off via RCON. */
app.post('/api/servers/:name/whitelist', auth, async (req, res) => {
  const name = req.params.name;
  const cfg  = SERVERS[name];
  if (!cfg || !cfg.rcon) return res.status(400).json({ error: 'Kein RCON für diesen Server' });
  const { action, player } = req.body;
  if (!['add', 'remove', 'on', 'off'].includes(action))
    return res.status(400).json({ error: 'Ungültige Aktion' });
  if ((action === 'add' || action === 'remove') && !/^[a-zA-Z0-9_]{1,16}$/.test(player))
    return res.status(400).json({ error: 'Ungültiger Spielername' });
  try {
    const cmd = (action === 'on' || action === 'off')
      ? `whitelist ${action}`
      : `whitelist ${action} ${player}`;
    const out = await rconSend(cfg.rcon, cmd);
    res.json({ ok: true, output: stripColors(out) });
  } catch (e) {
    res.status(500).json({ ok: false, error: e.message });
  }
});

// ── WebSocket – Echtzeit-Logs ─────────────────────────────────────────────

const logSubscribers = {};
const activeStreams   = {};

async function startLogStream(containerName) {
  if (activeStreams[containerName]) return;
  try {
    const container = docker.getContainer(containerName);
    const stream    = await container.logs({
      follow: true, stdout: true, stderr: true, tail: 150
    });

    const stripHeader = (raw) => {
      const results = [];
      let i = 0;
      while (i + 8 <= raw.length) {
        const size = raw.readUInt32BE(i + 4);
        if (i + 8 + size > raw.length) break;
        results.push(raw.slice(i + 8, i + 8 + size).toString('utf8'));
        i += 8 + size;
      }
      return results.length ? results.join('') : raw.toString('utf8');
    };

    stream.on('data', chunk => {
      const text = stripHeader(chunk);
      const subs = logSubscribers[containerName];
      if (!subs) return;
      for (const ws of subs) {
        if (ws.readyState === 1)
          ws.send(JSON.stringify({ type: 'log', server: containerName, text }));
      }
    });

    stream.on('end', () => {
      delete activeStreams[containerName];
      setTimeout(() => {
        if (logSubscribers[containerName]?.size > 0) startLogStream(containerName);
      }, 3000);
    });

    activeStreams[containerName] = stream;
  } catch (e) {
    console.error('Log-Stream Fehler:', containerName, e.message);
  }
}

wss.on('connection', (ws) => {
  let subscribedContainer = null;

  ws.on('message', async raw => {
    try {
      const msg = JSON.parse(raw.toString());

      if (msg.type === 'auth') {
        if (msg.token !== DASHBOARD_PASSWORD) { ws.close(1008, 'Unauthorized'); return; }
        ws.send(JSON.stringify({ type: 'auth_ok' }));
        return;
      }

      if (msg.type === 'subscribe') {
        if (subscribedContainer) logSubscribers[subscribedContainer]?.delete(ws);
        subscribedContainer = msg.container;
        if (!logSubscribers[subscribedContainer]) logSubscribers[subscribedContainer] = new Set();
        logSubscribers[subscribedContainer].add(ws);
        startLogStream(subscribedContainer);
        return;
      }
    } catch (e) {
      console.error('WS Message Fehler:', e.message);
    }
  });

  ws.on('close', () => {
    if (subscribedContainer) logSubscribers[subscribedContainer]?.delete(ws);
  });
});

// ── Start ─────────────────────────────────────────────────────────────────

server.listen(PORT, () => {
  console.log(`Pink Horizon Dashboard läuft auf Port ${PORT}`);
});
