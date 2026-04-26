const express    = require('express');
const yaml       = require('js-yaml');
const rateLimit  = require('express-rate-limit');
const { WebSocketServer } = require('ws');
const http       = require('http');
const https      = require('https');
const Docker     = require('dockerode');
const net        = require('net');
const path       = require('path');
const fs         = require('fs').promises;
const mysql      = require('mysql2/promise');

const app    = express();
const server = http.createServer(app);
const wss    = new WebSocketServer({ server });
const docker = new Docker({ socketPath: '/var/run/docker.sock' });

app.use(express.json());
app.use(express.static(path.join(__dirname, 'public')));

// ── Rate-Limiting ──────────────────────────────────────────────────────────
// Globales Limit: max 120 Anfragen/Min pro IP (Dashboard-Nutzung)
app.use('/api/', rateLimit({
  windowMs: 60 * 1000,
  max: 120,
  standardHeaders: true,
  legacyHeaders: false,
  message: { error: 'Too many requests, please slow down.' }
}));
// Strengeres Limit für sensible Aktionen (Ban, Kick, Passwort-geschützte Aktionen)
const strictLimit = rateLimit({
  windowMs: 60 * 1000,
  max: 20,
  standardHeaders: true,
  legacyHeaders: false,
  message: { error: 'Too many requests on this endpoint.' }
});

// ── Konfiguration ─────────────────────────────────────────────────────────

const DASHBOARD_PASSWORD = process.env.DASHBOARD_PASSWORD || 'pinkhorizon2024';
const PORT = process.env.PORT || 3000;

const DB_HOST = process.env.DB_HOST || 'ph-mysql';
const DB_PORT = parseInt(process.env.DB_PORT || '3306');
const DB_USER = process.env.DB_USER || 'ph_user';
const DB_PASS = process.env.DB_PASS || 'ph-db-2024';

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
  },
  minigames: {
    label:     'Minigames',
    container: 'ph-minigames',
    color:     '#E91E63',
    rcon:      { host: process.env.MINIGAMES_RCON_HOST || 'minigames', port: 25575, password: process.env.RCON_PASSWORD || 'ph-admin-2024' }
  },
  smash: {
    label:     'Smash the Boss',
    container: 'ph-smash',
    color:     '#F44336',
    rcon:      { host: process.env.SMASH_RCON_HOST || 'smash', port: 25575, password: process.env.RCON_PASSWORD || 'ph-admin-2024' }
  },
  generators: {
    label:     'IdleForge',
    container: 'ph-generators',
    color:     '#FFD700',
    rcon:      { host: process.env.GENERATORS_RCON_HOST || 'generators', port: 25575, password: process.env.RCON_PASSWORD || 'ph-admin-2024' }
  }
};

const VALID_RANKS = ['spieler', 'siedler', 'krieger', 'legende', 'vip', 'supporter', 'moderator', 'dev', 'admin', 'owner'];

// ── Auth-Middleware ───────────────────────────────────────────────────────

function auth(req, res, next) {
  const token = req.headers['x-dashboard-token'];
  if (token !== DASHBOARD_PASSWORD) return res.status(401).json({ error: 'Unauthorized' });
  next();
}

// ── Hilfsfunktionen ───────────────────────────────────────────────────────

function stripColors(s) {
  return (s || '').replace(/§[0-9a-fk-orA-FK-OR]/g, '').trim();
}

function parseKeyValue(raw) {
  const result = {};
  for (const line of stripColors(raw).split('\n')) {
    const idx = line.indexOf(':');
    if (idx > 0) result[line.slice(0, idx).trim()] = line.slice(idx + 1).trim();
  }
  return result;
}

// ── Connection-Pools ──────────────────────────────────────────────────────

const poolCore = mysql.createPool({
  host: DB_HOST, port: DB_PORT, user: DB_USER, password: DB_PASS,
  database: 'pinkhorizon', waitForConnections: true,
  connectionLimit: 5, connectTimeout: 5000
});

const poolSv = mysql.createPool({
  host: DB_HOST, port: DB_PORT, user: DB_USER, password: DB_PASS,
  database: 'ph_survival', waitForConnections: true,
  connectionLimit: 5, connectTimeout: 5000
});

const poolMg = mysql.createPool({
  host: DB_HOST, port: DB_PORT, user: DB_USER, password: DB_PASS,
  database: 'ph_minigames', waitForConnections: true,
  connectionLimit: 5, connectTimeout: 5000
});

const poolSmash = mysql.createPool({
  host: DB_HOST, port: DB_PORT, user: DB_USER, password: DB_PASS,
  database: 'ph_smash', waitForConnections: true,
  connectionLimit: 5, connectTimeout: 5000
});

const poolGen = mysql.createPool({
  host: DB_HOST, port: DB_PORT, user: DB_USER, password: DB_PASS,
  database: 'ph_generators', waitForConnections: true,
  connectionLimit: 5, connectTimeout: 5000
});

async function checkDb(pool) {
  try { await pool.query('SELECT 1'); return true; }
  catch { return false; }
}

// ── Audit-Log ─────────────────────────────────────────────────────────────
const AUDIT_FILE = '/data/dashboard/audit.json';
const auditEntries = [];
const MAX_AUDIT = 500;
(async () => {
  try {
    await fs.mkdir('/data/dashboard', { recursive: true });
    const raw = await fs.readFile(AUDIT_FILE, 'utf8');
    const d = JSON.parse(raw);
    if (Array.isArray(d)) auditEntries.push(...d.slice(-MAX_AUDIT));
  } catch {}
})();
function addAudit(action, target, detail = '') {
  const e = { ts: new Date().toISOString(), action: String(action), target: String(target || ''), detail: String(detail || '').slice(0, 200) };
  auditEntries.push(e);
  while (auditEntries.length > MAX_AUDIT) auditEntries.shift();
  fs.writeFile(AUDIT_FILE, JSON.stringify(auditEntries), 'utf8').catch(() => {});
}

// ── Spieler-Metriken (alle 5 min) ─────────────────────────────────────────
const playerMetrics = []; // { ts, count } – In-Memory-Ring für 24 h

async function ensureMetricsTable() {
  try {
    await poolCore.query(`CREATE TABLE IF NOT EXISTS ph_metrics (
      id      BIGINT AUTO_INCREMENT PRIMARY KEY,
      ts      BIGINT NOT NULL,
      players SMALLINT NOT NULL DEFAULT 0,
      INDEX idx_ts (ts)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4`);
  } catch (e) { console.error('ensureMetricsTable:', e.message); }
}
ensureMetricsTable();

async function collectMetrics() {
  let total = 0;
  for (const [, cfg] of Object.entries(SERVERS)) {
    if (!cfg.rcon) continue;
    try {
      const st = await getContainerStatus(cfg.container);
      if (!st.running) continue;
      const raw   = await rconSend(cfg.rcon, 'list');
      const clean = stripColors(raw);
      const m = /(\d+)\s+of\s+a\s+max/i.exec(clean) || /There are (\d+)/i.exec(clean);
      if (m) total += parseInt(m[1]);
    } catch {}
  }
  const now = Date.now();
  playerMetrics.push({ ts: now, count: total });
  while (playerMetrics.length > 288) playerMetrics.shift(); // 24 h @ 5 min
  // Persistenz in DB (nur die letzten 7 Tage behalten)
  try {
    await poolCore.query('INSERT INTO ph_metrics (ts, players) VALUES (?, ?)', [now, total]);
    const cutoff = now - 7 * 24 * 60 * 60 * 1000;
    await poolCore.query('DELETE FROM ph_metrics WHERE ts < ?', [cutoff]);
  } catch {}
}
setInterval(collectMetrics, 5 * 60 * 1000);
setTimeout(collectMetrics, 10000);

// ── Server-Alerts ─────────────────────────────────────────────────────────
const alertSubscribers = new Set();
const prevSrvStatus = {};
async function checkAlerts() {
  for (const [key, cfg] of Object.entries(SERVERS)) {
    try {
      const { running } = await getContainerStatus(cfg.container);
      if (prevSrvStatus[key] !== undefined && prevSrvStatus[key] !== running) {
        const payload = JSON.stringify({ type: 'server_alert', server: key, label: cfg.label, running, ts: Date.now() });
        for (const ws of [...alertSubscribers]) {
          if (ws.readyState === 1) ws.send(payload);
          else alertSubscribers.delete(ws);
        }
      }
      prevSrvStatus[key] = running;
    } catch {}
  }
}
setInterval(checkAlerts, 20000);

// ── XRay-Alert-Scanner ────────────────────────────────────────────────────
const xrayAlertCache = new Map(); // uuid → { score, alertedAt }

function calcXrayScore(dRate, aRate) {
  let score = 0;
  if (dRate > 15) score += 50; else if (dRate > 8) score += 35; else if (dRate > 4) score += 20;
  if (aRate > 5)  score += 30; else if (aRate > 2) score += 20; else if (aRate > 1) score += 10;
  return Math.min(score, 100);
}

async function checkXrayAlerts() {
  try {
    const [rows] = await poolSv.query(
      `SELECT p.name, mb.player_uuid, mb.total_blocks,
         COALESCE(d.cnt,0) as diamonds, COALESCE(a.cnt,0) as debris
       FROM sv_mining_blocks mb
       JOIN pinkhorizon.players p ON mb.player_uuid = p.uuid
       LEFT JOIN (SELECT player_uuid, COUNT(*) as cnt FROM sv_mining_log WHERE ore_type='diamond' GROUP BY player_uuid) d ON d.player_uuid = mb.player_uuid
       LEFT JOIN (SELECT player_uuid, COUNT(*) as cnt FROM sv_mining_log WHERE ore_type='ancient_debris' GROUP BY player_uuid) a ON a.player_uuid = mb.player_uuid
       WHERE mb.total_blocks >= 300`
    );
    const now = Date.now();
    for (const row of rows) {
      const total  = Number(row.total_blocks);
      const dRate  = Number(row.diamonds) / total * 1000;
      const aRate  = Number(row.debris)   / total * 1000;
      const score  = calcXrayScore(dRate, aRate);
      if (score < 35) continue;

      const cached = xrayAlertCache.get(row.player_uuid);
      // Nur neu melden wenn noch nicht gemeldet oder Score gestiegen oder 1h vergangen
      if (cached && cached.score >= score && now - cached.alertedAt < 3600000) continue;

      xrayAlertCache.set(row.player_uuid, { score, alertedAt: now });
      const payload = JSON.stringify({
        type:        'xray_alert',
        player:      row.name,
        uuid:        row.player_uuid,
        score,
        dRate:       dRate.toFixed(1),
        aRate:       aRate.toFixed(1),
        totalBlocks: total,
        ts:          now
      });
      for (const ws of [...alertSubscribers]) {
        if (ws.readyState === 1) ws.send(payload);
        else alertSubscribers.delete(ws);
      }
    }
  } catch {}
}
setInterval(checkXrayAlerts, 5 * 60 * 1000);
setTimeout(checkXrayAlerts, 10000); // Einmal kurz nach Start

app.get('/api/survival/xray/alerts', auth, async (req, res) => {
  try {
    const [rows] = await poolSv.query(
      `SELECT p.name, mb.player_uuid, mb.total_blocks,
         COALESCE(d.cnt,0) as diamonds, COALESCE(a.cnt,0) as debris
       FROM sv_mining_blocks mb
       JOIN pinkhorizon.players p ON mb.player_uuid = p.uuid
       LEFT JOIN (SELECT player_uuid, COUNT(*) as cnt FROM sv_mining_log WHERE ore_type='diamond' GROUP BY player_uuid) d ON d.player_uuid = mb.player_uuid
       LEFT JOIN (SELECT player_uuid, COUNT(*) as cnt FROM sv_mining_log WHERE ore_type='ancient_debris' GROUP BY player_uuid) a ON a.player_uuid = mb.player_uuid
       WHERE mb.total_blocks >= 300`
    );
    const alerts = [];
    for (const row of rows) {
      const total = Number(row.total_blocks);
      const dRate = Number(row.diamonds) / total * 1000;
      const aRate = Number(row.debris)   / total * 1000;
      const score = calcXrayScore(dRate, aRate);
      if (score >= 35) alerts.push({ player: row.name, uuid: row.player_uuid, score, dRate: dRate.toFixed(1), aRate: aRate.toFixed(1), totalBlocks: total });
    }
    alerts.sort((a, b) => b.score - a.score);
    res.json({ alerts });
  } catch (e) { res.status(500).json({ error: e.message, alerts: [] }); }
});
setTimeout(checkAlerts, 6000);

// ── RCON-Client ───────────────────────────────────────────────────────────

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

// ── REST-API: Login ───────────────────────────────────────────────────────

app.post('/api/login', (req, res) => {
  if (req.body.password === DASHBOARD_PASSWORD)
    return res.json({ ok: true, token: DASHBOARD_PASSWORD });
  res.status(401).json({ ok: false });
});

// ── REST-API: Server ──────────────────────────────────────────────────────

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

// ── REST-API: Netzwerk-Neustart ───────────────────────────────────────────
app.post('/api/network/restart', auth, async (req, res) => {
  const minutes = parseInt(req.body?.minutes) || 5;
  if (minutes < 1 || minutes > 60) return res.status(400).json({ error: 'Minuten müssen zwischen 1 und 60 liegen.' });
  const cmd = `networkrestart ${minutes}`;
  const results = {};
  for (const [key, cfg] of Object.entries(SERVERS)) {
    if (!cfg.rcon) { results[key] = 'kein RCON'; continue; }
    try { results[key] = await rconSend(cfg.rcon, cmd); }
    catch (e) { results[key] = `Fehler: ${e.message}`; }
  }
  res.json({ ok: true, minutes, results });
});

app.post('/api/network/cancel', auth, async (req, res) => {
  const cmd = 'networkrestart cancel';
  const results = {};
  for (const [key, cfg] of Object.entries(SERVERS)) {
    if (!cfg.rcon) { results[key] = 'kein RCON'; continue; }
    try { results[key] = await rconSend(cfg.rcon, cmd); }
    catch (e) { results[key] = `Fehler: ${e.message}`; }
  }
  addAudit('restart-cancel', 'network', '');
  res.json({ ok: true, results });
});

// ── REST-API: Spieler ─────────────────────────────────────────────────────

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
    case 'vote_give': {
      const v = parseInt(value);
      if (!Number.isInteger(v) || v <= 0) return res.status(400).json({ error: 'Ungültiger Betrag' });
      cmd = `voteadmin give ${name} ${v}`;
      break;
    }
    case 'vote_take': {
      const v = parseInt(value);
      if (!Number.isInteger(v) || v <= 0) return res.status(400).json({ error: 'Ungültiger Betrag' });
      cmd = `voteadmin take ${name} ${v}`;
      break;
    }
    case 'vote_set': {
      const v = parseInt(value);
      if (!Number.isInteger(v) || v < 0) return res.status(400).json({ error: 'Ungültiger Betrag' });
      cmd = `voteadmin set ${name} ${v}`;
      break;
    }
    default:
      return res.status(400).json({ error: 'Unbekannte Aktion' });
  }

  try {
    const out = await rconSend(cfg.rcon, cmd);
    addAudit(type === 'rank_set' ? 'rank_set' : type, name, type === 'rank_set' ? rank : String(value));
    res.json({ ok: true, output: stripColors(out) });
  } catch (e) {
    res.status(500).json({ ok: false, error: e.message });
  }
});

app.post('/api/players/kick', strictLimit, auth, async (req, res) => {
  const { name, reason } = req.body;
  if (!name || !/^[a-zA-Z0-9_]{1,16}$/.test(name)) return res.status(400).json({ error: 'Ungültiger Name' });
  const cfg = SERVERS.survival;
  if (!cfg.rcon) return res.status(400).json({ error: 'Kein RCON' });
  try {
    const out = await rconSend(cfg.rcon, reason ? `kick ${name} ${reason}` : `kick ${name}`);
    addAudit('kick', name, reason || '');
    res.json({ ok: true, output: stripColors(out) });
  } catch (e) { res.status(500).json({ ok: false, error: e.message }); }
});

app.post('/api/players/ban', strictLimit, auth, async (req, res) => {
  const { name, reason } = req.body;
  if (!name || !/^[a-zA-Z0-9_]{1,16}$/.test(name)) return res.status(400).json({ error: 'Ungültiger Name' });
  const cfg = SERVERS.survival;
  if (!cfg.rcon) return res.status(400).json({ error: 'Kein RCON' });
  try {
    const out = await rconSend(cfg.rcon, reason ? `ban ${name} ${reason}` : `ban ${name}`);
    addAudit('ban', name, reason || '');
    res.json({ ok: true, output: stripColors(out) });
  } catch (e) { res.status(500).json({ ok: false, error: e.message }); }
});

app.get('/api/players/banlist', auth, async (req, res) => {
  try {
    const data = await fs.readFile('/data/survival/banned-players.json', 'utf8');
    const raw  = JSON.parse(data);
    const bans = raw.map(b => ({
      name:    b.name   || b.uuid,
      reason:  b.reason || 'Kein Grund angegeben',
      source:  b.source || 'Unbekannt',
      expires: b.expires|| 'forever'
    }));
    res.json({ bans });
  } catch { res.json({ bans: [] }); }
});

app.post('/api/players/unban', strictLimit, auth, async (req, res) => {
  const { name } = req.body;
  if (!name || !/^[a-zA-Z0-9_]{1,16}$/.test(name)) return res.status(400).json({ error: 'Ungültiger Name' });
  const cfg = SERVERS.survival;
  if (!cfg?.rcon) return res.status(400).json({ error: 'Kein RCON' });
  try {
    const out = await rconSend(cfg.rcon, `pardon ${name}`);
    addAudit('unban', name, '');
    res.json({ ok: true, output: stripColors(out) });
  } catch (e) { res.status(500).json({ ok: false, error: e.message }); }
});

// ── REST-API: Datenbanken ─────────────────────────────────────────────────

app.get('/api/databases', auth, async (req, res) => {
  const checkTable = async (pool, query) => { try { await pool.execute(query); return true; } catch { return false; } };
  const [ctr, ph, sv, mg, sm, gn, vt, dc] = await Promise.allSettled([
    getContainerStatus('ph-mysql'),
    checkDb(poolCore),
    checkDb(poolSv),
    checkDb(poolMg),
    checkDb(poolSmash),
    checkDb(poolGen),
    checkTable(poolCore, 'SELECT 1 FROM vote_coins LIMIT 1'),
    checkTable(poolCore, 'SELECT 1 FROM discord_sync LIMIT 1')
  ]);
  res.json({
    container:     ctr.status === 'fulfilled' ? ctr.value : { running: false, status: 'error' },
    pinkhorizon:   ph.status  === 'fulfilled' ? ph.value  : false,
    ph_survival:   sv.status  === 'fulfilled' ? sv.value  : false,
    ph_minigames:  mg.status  === 'fulfilled' ? mg.value  : false,
    ph_smash:      sm.status  === 'fulfilled' ? sm.value  : false,
    ph_generators: gn.status  === 'fulfilled' ? gn.value  : false,
    ph_vote:       vt.status  === 'fulfilled' ? vt.value  : false,
    discord_sync:  dc.status  === 'fulfilled' ? dc.value  : false
  });
});

// ── REST-API: Wirtschaft ──────────────────────────────────────────────────

app.get('/api/economy/baltop', auth, async (req, res) => {
  try {
    const [rows] = await poolSv.execute(
      `SELECT p.name, e.coins FROM sv_economy e
       JOIN pinkhorizon.players p ON e.uuid = p.uuid
       ORDER BY e.coins DESC LIMIT 10`
    );
    const baltop = rows.map((r, i) => ({ rank: i + 1, name: r.name, coins: Number(r.coins) }));
    res.json({ baltop });
  } catch (e) { res.status(500).json({ error: e.message }); }
});

// ── REST-API: Bank ────────────────────────────────────────────────────────

app.get('/api/bank/overview', auth, async (req, res) => {
  try {
    const [[r]] = await poolSv.execute(
      'SELECT COUNT(*) AS accounts, COALESCE(SUM(balance),0) AS total, COALESCE(MAX(balance),0) AS top FROM sv_bank WHERE balance > 0'
    );
    res.json({ ok: true, accounts: Number(r.accounts), total: Number(r.total), top: Number(r.top) });
  } catch (e) { res.status(500).json({ error: e.message }); }
});

app.get('/api/bank/top', auth, async (req, res) => {
  try {
    const [rows] = await poolSv.execute(
      `SELECT p.name, b.balance, b.last_interest FROM sv_bank b
       JOIN pinkhorizon.players p ON b.uuid = p.uuid
       WHERE b.balance > 0 ORDER BY b.balance DESC LIMIT 20`
    );
    res.json({ ok: true, rows: rows.map(r => ({ ...r, balance: Number(r.balance) })) });
  } catch (e) { res.status(500).json({ error: e.message }); }
});

app.get('/api/bank/player', auth, async (req, res) => {
  const { name } = req.query;
  if (!name) return res.status(400).json({ error: 'Kein Spielername' });
  try {
    const [rows] = await poolSv.execute(
      `SELECT b.balance, b.last_interest FROM sv_bank b
       JOIN pinkhorizon.players p ON b.uuid = p.uuid WHERE p.name = ?`, [name]
    );
    if (!rows.length) return res.json({ ok: true, balance: 0, lastInterest: null });
    res.json({ ok: true, balance: Number(rows[0].balance), lastInterest: rows[0].last_interest });
  } catch (e) { res.status(500).json({ error: e.message }); }
});

app.post('/api/bank/player', auth, async (req, res) => {
  const { name, balance } = req.body;
  if (!name) return res.status(400).json({ error: 'Kein Spielername' });
  const bal = parseInt(balance);
  if (!Number.isInteger(bal) || bal < 0) return res.status(400).json({ error: 'Ungültiger Betrag' });
  try {
    const [players] = await poolCore.execute('SELECT uuid FROM players WHERE name = ?', [name]);
    if (!players.length) return res.status(404).json({ error: 'Spieler nicht gefunden' });
    const uuid = players[0].uuid;
    await poolSv.execute(
      'INSERT INTO sv_bank (uuid, balance, last_interest) VALUES (?, ?, CURRENT_DATE) ON DUPLICATE KEY UPDATE balance = ?',
      [uuid, bal, bal]
    );
    res.json({ ok: true });
  } catch (e) { res.status(500).json({ ok: false, error: e.message }); }
});

// ── REST-API: Broadcast ───────────────────────────────────────────────────

app.post('/api/broadcast', auth, async (req, res) => {
  const { message } = req.body;
  if (!message?.trim()) return res.status(400).json({ error: 'Keine Nachricht' });
  const results = {};
  const tellraw = JSON.stringify([
    { text: '[Dashboard] ', color: 'light_purple', bold: true },
    { text: message.trim(), color: 'white' }
  ]);
  for (const [key, cfg] of Object.entries(SERVERS)) {
    if (!cfg.rcon) continue;
    try {
      const out = await rconSend(cfg.rcon, `tellraw @a ${tellraw}`);
      results[key] = !String(out).toLowerCase().includes('unknown command');
    } catch { results[key] = false; }
  }
  const anyOk = Object.values(results).some(v => v);
  res.json({ ok: anyOk, results });
});

// ── Container-Ressourcen ──────────────────────────────────────────────────

const STATS_CONTAINERS = {
  ...Object.fromEntries(Object.entries(SERVERS).map(([k, v]) => [k, v.container])),
  mysql:     'ph-mysql',
  dashboard: 'ph-dashboard'
};

const STATS_LABELS = {
  velocity: 'Velocity Proxy', lobby: 'Lobby', survival: 'Survival',
  minigames: 'Minigames', smash: 'Smash the Boss', generators: 'IdleForge',
  mysql: 'MySQL', dashboard: 'Dashboard'
};

async function fetchAllStats() {
  const result = {};
  await Promise.all(Object.entries(STATS_CONTAINERS).map(async ([key, containerName]) => {
    try {
      const s        = await docker.getContainer(containerName).stats({ stream: false });
      const cpuDelta = s.cpu_stats.cpu_usage.total_usage  - s.precpu_stats.cpu_usage.total_usage;
      const sysDelta = s.cpu_stats.system_cpu_usage       - s.precpu_stats.system_cpu_usage;
      const nCpus    = s.cpu_stats.online_cpus            || s.cpu_stats.cpu_usage.percpu_usage?.length || 1;
      const memUsed  = Math.max(0, (s.memory_stats.usage || 0) - (s.memory_stats.stats?.cache || 0));
      const memLimit = s.memory_stats.limit || 1;
      result[key] = {
        label:   STATS_LABELS[key] || key,
        cpu:     sysDelta > 0 ? parseFloat(((cpuDelta / sysDelta) * nCpus * 100).toFixed(1)) : 0,
        memUsed,
        memLimit,
        memPct:  parseFloat((memUsed / memLimit * 100).toFixed(1))
      };
    } catch { result[key] = null; }
  }));

  return result;
}

// Stats-WebSocket-Push
const statsSubscribers = new Set();
let statsInterval = null;

function startStatsInterval() {
  if (statsInterval) return;
  statsInterval = setInterval(async () => {
    if (statsSubscribers.size === 0) {
      clearInterval(statsInterval);
      statsInterval = null;
      return;
    }
    try {
      const data = await fetchAllStats();
      const msg  = JSON.stringify({ type: 'stats', data });
      for (const client of statsSubscribers) {
        if (client.readyState === 1) client.send(msg);
        else statsSubscribers.delete(client);
      }
    } catch {}
  }, 2000);
}

app.get('/api/containers/stats', auth, async (req, res) => {
  res.json(await fetchAllStats());
});

// ── REST-API: Backup ──────────────────────────────────────────────────────

const { execFile } = require('child_process');
const BACKUP_DIR = process.env.BACKUP_DIR || '/data/backups';

// Alle Backups auflisten
app.get('/api/backups', auth, async (req, res) => {
  try {
    await fs.mkdir(BACKUP_DIR, { recursive: true });
    const files = await fs.readdir(BACKUP_DIR);
    const tars  = files.filter(f => f.endsWith('.tar.gz')).sort().reverse();
    const list  = await Promise.all(tars.map(async f => {
      const stat = await fs.stat(path.join(BACKUP_DIR, f));
      return { name: f, size: stat.size, mtime: stat.mtimeMs };
    }));
    res.json({ backups: list });
  } catch (e) { res.json({ backups: [] }); }
});

const SERVER_WORLD_DIRS = {
  lobby:      ['world'],
  survival:   ['world', 'world_nether', 'world_the_end'],
  smash:      ['world'],
  skyblock:   ['world', 'skyblock_world'],
  generators: ['island-template'],
};

async function createBackup(serverName) {
  const dirs = SERVER_WORLD_DIRS[serverName];
  if (!dirs) throw new Error('Unbekannter Server');

  const ts  = new Date().toISOString().slice(0, 16).replace(/[T:]/g, '-');
  const arc = path.join(BACKUP_DIR, `${serverName}_${ts}.tar.gz`);
  const src = `/data/${serverName}`;

  await fs.mkdir(BACKUP_DIR, { recursive: true });

  // save-all via RCON
  try {
    const cfg = SERVERS[serverName];
    if (cfg?.rcon) { await rconSend(cfg.rcon, 'save-all'); await new Promise(r => setTimeout(r, 2000)); }
  } catch {}

  await new Promise((resolve, reject) => {
    execFile('tar', ['-czf', arc, '-C', src, ...dirs], { timeout: 300000 }, (err) => {
      if (err) reject(err); else resolve();
    });
  });

  // Rotation: maximal 7 Backups pro Server behalten
  const all = (await fs.readdir(BACKUP_DIR))
    .filter(f => f.startsWith(`${serverName}_`) && f.endsWith('.tar.gz')).sort().reverse();
  for (const old of all.slice(7)) await fs.unlink(path.join(BACKUP_DIR, old)).catch(() => {});

  const stat = await fs.stat(arc);
  return { name: path.basename(arc), size: stat.size };
}

// Alle Server sichern
app.post('/api/backups/run', auth, async (req, res) => {
  const results = [];
  for (const srv of Object.keys(SERVER_WORLD_DIRS)) {
    try {
      const r = await createBackup(srv);
      results.push({ server: srv, ok: true, ...r });
    } catch (e) {
      results.push({ server: srv, ok: false, error: e.message });
    }
  }
  const allOk = results.every(r => r.ok);
  res.json({ ok: allOk, results });
});

// Einzelnen Server sichern
app.post('/api/backup/:server', auth, async (req, res) => {
  const serverName = req.params.server;
  if (!SERVER_WORLD_DIRS[serverName])
    return res.status(400).json({ error: 'Unbekannter Server' });
  try {
    const r = await createBackup(serverName);
    res.json({ ok: true, message: `Backup erstellt: ${r.name}`, size: r.size });
  } catch (e) { res.status(500).json({ ok: false, error: e.message }); }
});

// ── REST-API: MOTD ────────────────────────────────────────────────────────

app.get('/api/servers/:name/motd', auth, async (req, res) => {
  const name = req.params.name;
  if (!MANAGED_SERVERS.includes(name)) return res.status(400).json({ error: 'Unbekannter Server' });
  try {
    const props = await fs.readFile(`/data/${name}/server.properties`, 'utf8');
    const m = /^motd=(.*)$/m.exec(props);
    res.json({ motd: m ? m[1] : '' });
  } catch { res.json({ motd: '' }); }
});

app.post('/api/servers/:name/motd', auth, async (req, res) => {
  const name = req.params.name;
  if (!MANAGED_SERVERS.includes(name)) return res.status(400).json({ error: 'Unbekannter Server' });
  const { motd } = req.body;
  if (motd === undefined) return res.status(400).json({ error: 'Kein MOTD' });
  const safe = String(motd).replace(/[`$\\|]/g, '').slice(0, 256);
  try {
    const exec = await docker.getContainer(`ph-${name}`).exec({
      Cmd: ['bash', '-c', `sed -i "s/^motd=.*/motd=${safe.replace(/\//g, '\\/')}/" /data/server.properties && echo ok`],
      AttachStdout: true, AttachStderr: true
    });
    const stream = await exec.start({ hijack: true });
    await new Promise((resolve, reject) => {
      stream.on('end', resolve); stream.on('error', reject);
    });
    res.json({ ok: true });
  } catch (e) { res.status(500).json({ ok: false, error: e.message }); }
});

// ── REST-API: Statistiken ─────────────────────────────────────────────────

app.get('/api/stats/overview', auth, async (req, res) => {
  try {
    const [[eco]]        = await poolSv.execute(`SELECT COUNT(*) AS players, COALESCE(SUM(coins),0) AS total_coins FROM sv_economy`);
    const [topPlaytime]  = await poolSv.execute(`SELECT p.name, s.playtime FROM sv_stats s JOIN pinkhorizon.players p ON s.uuid=p.uuid ORDER BY s.playtime DESC LIMIT 10`);
    const [topKills]     = await poolSv.execute(`SELECT p.name, s.mob_kills FROM sv_stats s JOIN pinkhorizon.players p ON s.uuid=p.uuid ORDER BY s.mob_kills DESC LIMIT 10`);
    const [topDeaths]    = await poolSv.execute(`SELECT p.name, s.deaths FROM sv_stats s JOIN pinkhorizon.players p ON s.uuid=p.uuid ORDER BY s.deaths DESC LIMIT 10`);
    const [topBlocks]    = await poolSv.execute(`SELECT p.name, s.blocks_broken FROM sv_stats s JOIN pinkhorizon.players p ON s.uuid=p.uuid ORDER BY s.blocks_broken DESC LIMIT 10`);
    const [jobs]         = await poolSv.execute(`SELECT job_id, COUNT(*) AS players, MAX(level) AS max_level FROM sv_jobs WHERE active=TRUE GROUP BY job_id ORDER BY players DESC`);
    const [[quest]]      = await poolSv.execute(`SELECT COUNT(*) AS completed FROM sv_quests WHERE completed=TRUE`);
    res.json({ eco, topPlaytime, topKills, topDeaths, topBlocks, jobs, quest });
  } catch (e) { res.status(500).json({ error: e.message }); }
});

// ── REST-API: Auktionshaus ────────────────────────────────────────────────

app.get('/api/auction', auth, async (req, res) => {
  try {
    const [rows]             = await poolSv.execute(`SELECT id, seller_name, price, listed_at FROM sv_auction ORDER BY listed_at DESC LIMIT 100`);
    const [[{ total }]]      = await poolSv.execute(`SELECT COUNT(*) AS total FROM sv_auction`);
    const [[{ totalValue }]] = await poolSv.execute(`SELECT COALESCE(SUM(price),0) AS totalValue FROM sv_auction`);
    const now = Date.now();
    const EXPIRE = 7 * 24 * 60 * 60 * 1000;
    const listings = rows.map(l => ({
      id:        l.id,
      seller:    l.seller_name,
      price:     Number(l.price),
      listedAt:  l.listed_at,
      expiresIn: Math.max(0, EXPIRE - (now - Number(l.listed_at)))
    }));
    res.json({ listings, total: Number(total), totalValue: Number(totalValue) });
  } catch (e) { res.status(500).json({ error: e.message, listings: [], total: 0, totalValue: 0 }); }
});

app.delete('/api/auction/:id', auth, async (req, res) => {
  const { id } = req.params;
  if (!id) return res.status(400).json({ error: 'Keine ID' });
  try {
    const [result] = await poolSv.execute(`DELETE FROM sv_auction WHERE id=?`, [id]);
    if (result.affectedRows === 0) return res.status(404).json({ error: 'Listing nicht gefunden' });
    res.json({ ok: true });
  } catch (e) { res.status(500).json({ ok: false, error: e.message }); }
});

// ── REST-API: SQL-Konsole (nur SELECT) ───────────────────────────────────

app.post('/api/db/query', auth, async (req, res) => {
  const { query, database } = req.body;
  if (!query?.trim()) return res.status(400).json({ error: 'Keine Abfrage' });
  if (!/^\s*SELECT\s/i.test(query)) return res.status(400).json({ error: 'Nur SELECT-Abfragen erlaubt' });
  if (!/^(pinkhorizon|ph_survival|ph_smash|ph_generators|ph_minigames)$/.test(database)) return res.status(400).json({ error: 'Unbekannte Datenbank' });
  try {
    const pool = database === 'pinkhorizon' ? poolCore
      : database === 'ph_smash'      ? poolSmash
      : database === 'ph_generators' ? poolGen
      : database === 'ph_minigames'  ? poolMg
      : poolSv;
    const lq = /\blimit\b/i.test(query) ? query.trimEnd().replace(/;$/, '') : `${query.trimEnd().replace(/;$/, '')} LIMIT 200`;
    const [rows, fields] = await pool.execute(lq);
    res.json({ ok: true, columns: fields.map(f => f.name), rows, count: rows.length });
  } catch (e) { res.status(500).json({ ok: false, error: e.message }); }
});

// ── REST-API: Permissions / Ränge ─────────────────────────────────────────

/** Alle Spieler mit Rang aus der DB */
app.get('/api/permissions/players', auth, async (req, res) => {
  try {
    const [rows] = await poolCore.execute(
      "SELECT name, `rank` FROM players ORDER BY FIELD(`rank`,'owner','admin','dev','moderator','supporter','vip','legende','krieger','siedler','spieler'), name"
    );
    res.json({ players: rows });
  } catch (e) {
    res.status(500).json({ error: e.message, players: [] });
  }
});

/** LuckPerms-Gruppenrechte aus MySQL */
app.get('/api/permissions/groups', auth, async (req, res) => {
  try {
    const [groupRows] = await poolCore.execute(`SELECT name FROM luckperms_groups ORDER BY name`);
    const [permRows]  = await poolCore.execute(
      `SELECT name, permission, server, \`value\`
       FROM luckperms_group_permissions
       WHERE expiry = 0
       ORDER BY name, permission`
    );
    const groups = {};
    for (const g of groupRows) groups[g.name] = [];
    for (const row of permRows) {
      if (!groups[row.name]) groups[row.name] = [];
      groups[row.name].push({ permission: row.permission, server: row.server, value: row.value === 1 });
    }
    res.json({ groups });
  } catch (e) {
    res.status(500).json({ error: e.message, groups: {} });
  }
});

// ── REST-API: Rechteverwaltung ────────────────────────────────────────────

const MANAGED_SERVERS = ['lobby', 'survival', 'smash'];

app.get('/api/servers/:name/ops', auth, async (req, res) => {
  const name = req.params.name;
  if (!MANAGED_SERVERS.includes(name)) return res.status(400).json({ error: 'Unbekannter Server' });
  try {
    const raw = await fs.readFile(`/data/${name}/ops.json`, 'utf8');
    res.json({ ops: JSON.parse(raw) });
  } catch { res.json({ ops: [] }); }
});

app.get('/api/servers/:name/whitelist', auth, async (req, res) => {
  const name = req.params.name;
  if (!MANAGED_SERVERS.includes(name)) return res.status(400).json({ error: 'Unbekannter Server' });
  try {
    const [wlRaw, propsRaw] = await Promise.all([
      fs.readFile(`/data/${name}/whitelist.json`, 'utf8').catch(() => '[]'),
      fs.readFile(`/data/${name}/server.properties`, 'utf8').catch(() => '')
    ]);
    res.json({ whitelist: JSON.parse(wlRaw), enabled: /^white-list=true$/m.test(propsRaw) });
  } catch { res.json({ whitelist: [], enabled: false }); }
});

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
  } catch (e) { res.status(500).json({ ok: false, error: e.message }); }
});

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
    const cmd = (action === 'on' || action === 'off') ? `whitelist ${action}` : `whitelist ${action} ${player}`;
    const out = await rconSend(cfg.rcon, cmd);
    res.json({ ok: true, output: stripColors(out) });
  } catch (e) { res.status(500).json({ ok: false, error: e.message }); }
});

// ── WebSocket – Echtzeit-Logs ─────────────────────────────────────────────

const logSubscribers = {};
const activeStreams   = {};

async function startLogStream(containerName) {
  if (activeStreams[containerName]) return;
  try {
    const container = docker.getContainer(containerName);
    const stream    = await container.logs({ follow: true, stdout: true, stderr: true, tail: 150 });

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
      for (const ws of subs)
        if (ws.readyState === 1) ws.send(JSON.stringify({ type: 'log', server: containerName, text }));
    });

    stream.on('end', () => {
      delete activeStreams[containerName];
      setTimeout(() => {
        if (logSubscribers[containerName]?.size > 0) startLogStream(containerName);
      }, 3000);
    });

    activeStreams[containerName] = stream;
  } catch (e) { console.error('Log-Stream Fehler:', containerName, e.message); }
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
      if (msg.type === 'subscribe_stats') {
        statsSubscribers.add(ws);
        startStatsInterval();
        return;
      }
      if (msg.type === 'unsubscribe_stats') {
        statsSubscribers.delete(ws);
        return;
      }
      if (msg.type === 'subscribe_alerts') {
        alertSubscribers.add(ws);
        return;
      }
      if (msg.type === 'unsubscribe_alerts') {
        alertSubscribers.delete(ws);
        return;
      }
    } catch (e) { console.error('WS Message Fehler:', e.message); }
  });

  ws.on('close', () => {
    if (subscribedContainer) logSubscribers[subscribedContainer]?.delete(ws);
    statsSubscribers.delete(ws);
    alertSubscribers.delete(ws);
  });
});

// ── Survival – Ofen & Trichter Upgrades ──────────────────────────────────

app.get('/api/survival/upgrades', auth, async (req, res) => {
  const { player } = req.query;
  if (!player) return res.status(400).json({ error: 'Kein Spielername' });
  try {
    const [[uuidRow]] = await poolCore.execute('SELECT uuid FROM players WHERE name = ?', [player]);
    if (!uuidRow) return res.status(404).json({ error: 'Spieler nicht gefunden' });
    const uuid = uuidRow.uuid;

    // Kumulierte Speed-Kosten (Lv0-10): Upgrade-Preis TO level summiert
    const SPEED_CUM = [0, 0, 1000, 3500, 8500, 18500, 38500, 78500, 148500, 258500, 408500];
    // Kumulierte Fortune-Kosten (Lv0-10)
    const FORT_CUM  = [0, 1000, 4000, 10000, 22000, 44000, 82000, 142000, 232000, 352000, 502000];
    const speedPaid   = lvl => SPEED_CUM[Math.min(Math.max(lvl, 0), 10)];
    const fortunePaid = lvl => FORT_CUM[Math.min(Math.max(lvl, 0), 10)];

    const [furnaces] = await poolSv.execute(
      'SELECT level, fortune_level, world, x, y, z FROM sv_furnace_upgrades WHERE owner_uuid = ? AND world IS NOT NULL', [uuid]);
    const [hoppers]  = await poolSv.execute(
      'SELECT level, world, x, y, z FROM sv_hopper_upgrades WHERE owner_uuid = ? AND world IS NOT NULL', [uuid]);

    const fList = furnaces.map(r => ({
      level:        r.level,
      fortuneLevel: r.fortune_level || 0,
      world: r.world, x: r.x, y: r.y, z: r.z,
      paidSpeed:   speedPaid(r.level),
      paidFortune: fortunePaid(r.fortune_level || 0),
      paid:        speedPaid(r.level) + fortunePaid(r.fortune_level || 0)
    }));
    const hList = hoppers.map(r => ({
      level: r.level,
      world: r.world, x: r.x, y: r.y, z: r.z,
      paid: speedPaid(r.level)
    }));

    res.json({
      furnaces:  fList,
      hoppers:   hList,
      totalPaid: [...fList, ...hList].reduce((s, r) => s + r.paid, 0)
    });
  } catch (e) { res.status(500).json({ error: e.message }); }
});

// ── Survival – XRay-Erkennung ────────────────────────────────────────────

app.get('/api/survival/xray', auth, async (req, res) => {
  const { player } = req.query;
  if (!player) return res.status(400).json({ error: 'Kein Spielername' });
  try {
    const [[uuidRow]] = await poolCore.execute('SELECT uuid FROM players WHERE name = ?', [player]);
    if (!uuidRow) return res.status(404).json({ error: 'Spieler nicht gefunden' });
    const uuid = uuidRow.uuid;

    const [[blocks]] = await poolSv.execute(
      'SELECT total_blocks, ores_found FROM sv_mining_blocks WHERE player_uuid = ?', [uuid]
    );
    const [oreCounts] = await poolSv.execute(
      'SELECT ore_type, COUNT(*) AS cnt FROM sv_mining_log WHERE player_uuid = ? GROUP BY ore_type', [uuid]
    );
    const [recentFinds] = await poolSv.execute(
      `SELECT ore_type, x, y, z, mined_at FROM sv_mining_log
       WHERE player_uuid = ? AND ore_type IN ('diamond','ancient_debris')
       ORDER BY mined_at DESC LIMIT 20`, [uuid]
    );
    const [diamondYs] = await poolSv.execute(
      `SELECT y FROM sv_mining_log WHERE player_uuid = ? AND ore_type = 'diamond'
       ORDER BY mined_at DESC LIMIT 100`, [uuid]
    );

    const totalBlocks = Number(blocks?.total_blocks || 0);
    const oreMap = {};
    for (const r of oreCounts) oreMap[r.ore_type] = Number(r.cnt);

    const diamonds = oreMap.diamond || 0;
    const debris   = oreMap.ancient_debris || 0;

    let score = 0;
    const reasons = [];

    if (totalBlocks >= 300) {
      const dRate = diamonds / totalBlocks * 1000;
      const aRate = debris   / totalBlocks * 1000;

      if      (dRate > 15) { score += 50; reasons.push(`Diamant-Rate sehr hoch: ${dRate.toFixed(1)}/1000 Blöcke`); }
      else if (dRate > 8)  { score += 35; reasons.push(`Diamant-Rate hoch: ${dRate.toFixed(1)}/1000 Blöcke`); }
      else if (dRate > 4)  { score += 20; reasons.push(`Diamant-Rate erhöht: ${dRate.toFixed(1)}/1000 Blöcke`); }

      if      (aRate > 5)  { score += 30; reasons.push(`Ancient-Debris-Rate sehr hoch: ${aRate.toFixed(1)}/1000 Blöcke`); }
      else if (aRate > 2)  { score += 20; reasons.push(`Ancient-Debris-Rate hoch: ${aRate.toFixed(1)}/1000 Blöcke`); }
      else if (aRate > 1)  { score += 10; reasons.push(`Ancient-Debris-Rate erhöht: ${aRate.toFixed(1)}/1000 Blöcke`); }

      // Y-Level-Konsistenz bei Diamantfunden
      if (diamondYs.length >= 5) {
        const ys   = diamondYs.map(r => r.y);
        const mean = ys.reduce((a, b) => a + b, 0) / ys.length;
        const std  = Math.sqrt(ys.reduce((a, b) => a + (b - mean) ** 2, 0) / ys.length);
        if      (std < 2 && ys.length >= 10) { score += 20; reasons.push(`Y-Level extrem konsistent (σ=${std.toFixed(1)})`); }
        else if (std < 4 && ys.length >=  5) { score += 10; reasons.push(`Y-Level konsistent (σ=${std.toFixed(1)})`); }
      }
    }

    score = Math.min(score, 100);
    const diamondRate = totalBlocks >= 300 ? (diamonds / totalBlocks * 1000).toFixed(2) : null;
    const debrisRate  = totalBlocks >= 300 ? (debris   / totalBlocks * 1000).toFixed(2) : null;

    res.json({
      ok: true,
      totalBlocks,
      ores: oreMap,
      score,
      reasons,
      diamondRate,
      debrisRate,
      recentFinds: recentFinds.map(r => ({ type: r.ore_type, x: r.x, y: r.y, z: r.z, at: Number(r.mined_at) })),
      insufficientData: totalBlocks < 300
    });
  } catch (e) { res.status(500).json({ ok: false, error: e.message }); }
});

// ── Survival – Claims & Inventar ─────────────────────────────────────────

app.get('/api/survival/claims', auth, async (req, res) => {
  const { player } = req.query;
  if (!player) return res.status(400).json({ error: 'Kein Spielername' });
  try {
    const [[uuidRow]] = await poolCore.execute(
      'SELECT uuid FROM players WHERE name = ?', [player]
    );
    if (!uuidRow) return res.status(404).json({ error: 'Spieler nicht gefunden' });
    const [claims] = await poolSv.execute(
      'SELECT world, chunk_x, chunk_z FROM sv_claims WHERE owner_uuid = ? ORDER BY world, chunk_x, chunk_z',
      [uuidRow.uuid]
    );
    const result = claims.map(c => ({
      world:   c.world,
      chunk_x: c.chunk_x,
      chunk_z: c.chunk_z,
      block_x: c.chunk_x * 16 + 8,
      block_z: c.chunk_z * 16 + 8
    }));
    res.json({ ok: true, claims: result, total: result.length });
  } catch (e) { res.status(500).json({ error: e.message }); }
});

app.get('/api/survival/inventory', auth, async (req, res) => {
  const { player } = req.query;
  if (!player) return res.status(400).json({ error: 'Kein Spielername' });
  try {
    const [[uuidRow]] = await poolCore.execute(
      'SELECT uuid FROM players WHERE name = ?', [player]
    );
    if (!uuidRow) return res.status(404).json({ error: 'Spieler nicht gefunden' });
    const [[snap]] = await poolSv.execute(
      'SELECT inventory_json, snapshot_time FROM sv_inventory_snapshot WHERE uuid = ?',
      [uuidRow.uuid]
    );
    if (!snap) return res.json({ ok: true, items: [], snapshotTime: null });
    let items = [];
    try { items = JSON.parse(snap.inventory_json); } catch {}
    res.json({ ok: true, items, snapshotTime: snap.snapshot_time });
  } catch (e) { res.status(500).json({ error: e.message }); }
});

// ── REST-API: Jobs ────────────────────────────────────────────────────────

app.get('/api/survival/jobs', auth, async (req, res) => {
  try {
    const [dist] = await poolSv.execute(
      `SELECT job_id, COUNT(*) AS players, MAX(level) AS max_level, ROUND(AVG(level),1) AS avg_level
       FROM sv_jobs WHERE active = 1 GROUP BY job_id ORDER BY players DESC`
    );
    const [top] = await poolSv.execute(
      `SELECT p.name, j.job_id, j.level, j.xp
       FROM sv_jobs j JOIN pinkhorizon.players p ON j.uuid = p.uuid
       WHERE j.active = 1 ORDER BY j.level DESC, j.xp DESC`
    );
    res.json({ ok: true, distribution: dist, players: top });
  } catch (e) { res.status(500).json({ error: e.message }); }
});

// ── Minigames API ─────────────────────────────────────────────────────────

app.get('/api/minigames/overview', auth, async (req, res) => {
  try {
    const [[{ total }]]  = await poolMg.execute('SELECT COUNT(*) AS total FROM mg_bedwars_stats');
    const [[{ games }]]  = await poolMg.execute('SELECT COALESCE(SUM(games_played),0) AS games FROM mg_bedwars_stats');
    const [[{ arenas }]] = await poolMg.execute('SELECT COUNT(*) AS arenas FROM mg_bedwars_arenas');
    res.json({ ok: true, players: total, totalGames: games, arenas });
  } catch (e) { res.status(500).json({ error: e.message }); }
});

app.get('/api/minigames/bedwars/stats', auth, async (req, res) => {
  try {
    const [rows] = await poolMg.execute(`
      SELECT p.name, s.wins, s.losses, s.kills, s.deaths, s.beds_broken, s.games_played
      FROM mg_bedwars_stats s
      LEFT JOIN pinkhorizon.players p ON s.uuid = p.uuid
      ORDER BY s.wins DESC
      LIMIT 20
    `);
    res.json({ ok: true, rows });
  } catch (e) { res.status(500).json({ error: e.message }); }
});

app.get('/api/minigames/bedwars/arenas', auth, async (req, res) => {
  try {
    const [arenas] = await poolMg.execute(
      'SELECT a.id, a.name, a.world, a.max_teams, a.team_size, ' +
      '(SELECT COUNT(*) FROM mg_bedwars_spawns WHERE arena_id=a.id) AS spawns_set, ' +
      '(SELECT COUNT(*) FROM mg_bedwars_beds WHERE arena_id=a.id) AS beds_set, ' +
      '(SELECT COUNT(*) FROM mg_bedwars_spawners WHERE arena_id=a.id) AS spawners ' +
      'FROM mg_bedwars_arenas a ORDER BY a.name'
    );
    res.json({ ok: true, arenas });
  } catch (e) { res.status(500).json({ error: e.message }); }
});

// ── REST-API: Audit-Log ───────────────────────────────────────────────────

app.get('/api/audit', auth, (req, res) => {
  res.json({ ok: true, entries: [...auditEntries].reverse().slice(0, 200) });
});

// ── REST-API: Smash the Boss ──────────────────────────────────────────────

app.get('/api/smash/online', auth, async (req, res) => {
  const cfg = SERVERS.smash;
  if (!cfg?.rcon) return res.json({ ok: true, players: [], count: 0 });
  try {
    const { running } = await getContainerStatus(cfg.container);
    if (!running) return res.json({ ok: true, players: [], count: 0, offline: true });
    const raw   = await rconSend(cfg.rcon, 'list');
    const clean = stripColors(raw);
    const match = /players online:\s*(.+)/i.exec(clean);
    const players = match
      ? match[1].split(',').map(s => s.trim()).filter(Boolean)
      : [];
    res.json({ ok: true, players, count: players.length });
  } catch {
    res.json({ ok: true, players: [], count: 0 });
  }
});

app.get('/api/smash/overview', auth, async (req, res) => {
  try {
    const [[players]]   = await poolSmash.query('SELECT COUNT(*) AS cnt FROM smash_players');
    const [[kills]]     = await poolSmash.query('SELECT SUM(kills) AS total FROM smash_players');
    const [[topLevel]]  = await poolSmash.query('SELECT MAX(personal_level) AS max_lvl FROM smash_players');
    const [[coinSum]]   = await poolSmash.query('SELECT COALESCE(SUM(coins),0) AS total FROM smash_coins');
    res.json({
      ok: true,
      totalPlayers: players?.cnt      ?? 0,
      totalKills:   kills?.total      ?? 0,
      topBossLevel: topLevel?.max_lvl ?? 0,
      totalCoins:   Number(coinSum?.total ?? 0),
    });
  } catch (e) { res.status(500).json({ ok: false, error: e.message }); }
});

app.get('/api/smash/leaderboard', auth, async (req, res) => {
  try {
    const [rows] = await poolSmash.query(`
      SELECT p.uuid, COALESCE(p.name, p.uuid) AS name, p.kills, p.total_damage,
             p.personal_level, p.best_level, COALESCE(c.coins, 0) AS coins
      FROM smash_players p
      LEFT JOIN smash_coins c ON p.uuid = c.uuid
      ORDER BY p.kills DESC LIMIT 10`);
    res.json({ ok: true, rows });
  } catch (e) { res.status(500).json({ ok: false, error: e.message }); }
});

app.get('/api/smash/level-lb', auth, async (req, res) => {
  try {
    const [rows] = await poolSmash.query(`
      SELECT p.uuid, COALESCE(p.name, p.uuid) AS name, p.personal_level,
             p.kills, COALESCE(c.coins, 0) AS coins
      FROM smash_players p
      LEFT JOIN smash_coins c ON p.uuid = c.uuid
      ORDER BY p.personal_level DESC LIMIT 10`);
    res.json({ ok: true, rows });
  } catch (e) { res.status(500).json({ ok: false, error: e.message }); }
});

app.get('/api/smash/coins-lb', auth, async (req, res) => {
  try {
    const [rows] = await poolSmash.query(`
      SELECT c.uuid, COALESCE(p.name, c.uuid) AS name, c.coins,
             p.kills, p.personal_level
      FROM smash_coins c
      LEFT JOIN smash_players p ON c.uuid = p.uuid
      ORDER BY c.coins DESC LIMIT 10`);
    res.json({ ok: true, rows });
  } catch (e) { res.status(500).json({ ok: false, error: e.message }); }
});

app.get('/api/smash/prestige-lb', auth, async (req, res) => {
  try {
    const [rows] = await poolSmash.query(`
      SELECT pr.uuid, COALESCE(p.name, pr.uuid) AS name, pr.prestige,
             p.kills, COALESCE(c.coins, 0) AS coins
      FROM smash_prestige pr
      LEFT JOIN smash_players p  ON p.uuid  = pr.uuid
      LEFT JOIN smash_coins   c  ON c.uuid  = pr.uuid
      WHERE pr.prestige > 0
      ORDER BY pr.prestige DESC LIMIT 10`);
    res.json({ ok: true, rows });
  } catch (e) { res.status(500).json({ ok: false, error: e.message }); }
});

app.get('/api/smash/weekly-lb', auth, async (req, res) => {
  try {
    // Monday of current week (ISO)
    const now  = new Date();
    const day  = now.getDay(); // 0=Sun
    const diff = now.getDate() - day + (day === 0 ? -6 : 1);
    const mon  = new Date(now.setDate(diff));
    const weekStart = mon.toISOString().slice(0, 10);

    const [rows] = await poolSmash.query(`
      SELECT w.uuid, COALESCE(p.name, w.uuid) AS name, w.kills, w.best_level
      FROM smash_weekly w
      LEFT JOIN smash_players p ON p.uuid = w.uuid
      WHERE w.week_start = ?
      ORDER BY w.kills DESC LIMIT 10`, [weekStart]);
    res.json({ ok: true, rows, weekStart });
  } catch (e) { res.status(500).json({ ok: false, error: e.message }); }
});

// ── REST-API: IdleForge Generators ───────────────────────────────────────

app.get('/api/generators/online', auth, async (req, res) => {
  const cfg = SERVERS.generators;
  if (!cfg?.rcon) return res.json({ offline: true, players: [], count: 0 });
  try {
    const { running } = await getContainerStatus(cfg.container);
    if (!running) return res.json({ offline: true, players: [], count: 0 });
    const raw   = await rconSend(cfg.rcon, 'list');
    const clean = stripColors(raw);
    const match = /players online:\s*(.+)/i.exec(clean);
    const players = match && match[1].trim() !== ''
      ? match[1].split(',').map(s => s.trim()).filter(Boolean)
      : [];
    res.json({ offline: false, players, count: players.length });
  } catch { res.json({ offline: true, players: [], count: 0 }); }
});

app.get('/api/generators/overview', auth, async (req, res) => {
  try {
    const [[players]]    = await poolGen.query('SELECT COUNT(*) AS cnt FROM gen_players');
    const [[money]]      = await poolGen.query('SELECT COALESCE(SUM(money),0) AS total FROM gen_players');
    const [[gens]]       = await poolGen.query('SELECT COUNT(*) AS cnt FROM gen_generators');
    const [[topPrestige]]= await poolGen.query('SELECT COALESCE(MAX(prestige),0) AS max_p FROM gen_players');
    const [[guilds]]     = await poolGen.query('SELECT COUNT(*) AS cnt FROM gen_guilds');
    res.json({ ok: true,
      totalPlayers:  Number(players.cnt),
      totalMoney:    Number(money.total),
      totalGenerators: Number(gens.cnt),
      topPrestige:   Number(topPrestige.max_p),
      totalGuilds:   Number(guilds.cnt)
    });
  } catch (e) { res.status(500).json({ ok: false, error: e.message }); }
});

app.get('/api/generators/leaderboard', auth, async (req, res) => {
  const type = req.query.type || 'money';
  try {
    let rows;
    if (type === 'prestige') {
      [rows] = await poolGen.query(
        'SELECT name, prestige, money FROM gen_players ORDER BY prestige DESC, money DESC LIMIT 10');
    } else if (type === 'generators') {
      [rows] = await poolGen.query(
        `SELECT p.name, p.prestige, p.money, COUNT(g.id) AS gen_count,
                CAST(JSON_ARRAYAGG(
                  IF(g.id IS NULL, NULL, JSON_OBJECT('tier', g.tier, 'level', g.level))
                ) AS CHAR) AS generators
         FROM gen_players p LEFT JOIN gen_generators g ON p.uuid = g.uuid
         GROUP BY p.uuid ORDER BY gen_count DESC LIMIT 10`);
      rows = rows.map(r => {
        let gens = [];
        try { gens = (JSON.parse(r.generators) || []).filter(g => g && g.tier); } catch {}
        return { ...r, generators: gens };
      });
    } else {
      [rows] = await poolGen.query(
        'SELECT name, money, prestige FROM gen_players ORDER BY money DESC LIMIT 10');
    }
    res.json({ ok: true, rows: rows.map((r, i) => ({ rank: i + 1, ...r })) });
  } catch (e) { res.status(500).json({ ok: false, error: e.message }); }
});

app.get('/api/generators/players', auth, async (req, res) => {
  const page  = Math.max(0, parseInt(req.query.page  || 0));
  const limit = Math.min(100, Math.max(1, parseInt(req.query.limit || 50)));
  const sort  = ['money','prestige','total_earned','total_upgrades'].includes(req.query.sort) ? req.query.sort : 'money';
  try {
    const [rows] = await poolGen.query(
      `SELECT uuid, name, money, prestige, total_earned, total_upgrades, border_size,
              booster_expiry, booster_mult, last_seen
       FROM gen_players ORDER BY ${sort} DESC LIMIT ? OFFSET ?`,
      [limit, page * limit]
    );
    const [[{ total }]] = await poolGen.query('SELECT COUNT(*) AS total FROM gen_players');
    res.json({
      ok: true,
      rows: rows.map(r => ({ ...r, money: Number(r.money), total_earned: Number(r.total_earned) })),
      total: Number(total),
      page, limit
    });
  } catch (e) { res.status(500).json({ ok: false, error: e.message }); }
});

app.get('/api/generators/player', auth, async (req, res) => {
  const { name } = req.query;
  if (!name) return res.status(400).json({ error: 'Kein Name' });
  try {
    const [players] = await poolGen.query('SELECT * FROM gen_players WHERE name = ? LIMIT 1', [name]);
    if (!players.length) return res.status(404).json({ ok: false, error: 'Spieler nicht gefunden' });
    const p = players[0];
    const [generators] = await poolGen.query(
      'SELECT tier, level, world, x, y, z FROM gen_generators WHERE uuid = ? ORDER BY tier, level DESC', [p.uuid]);
    const guildId = await (async () => {
      const [r] = await poolGen.query('SELECT guild_id FROM gen_guild_members WHERE uuid = ? LIMIT 1', [p.uuid]);
      return r.length ? r[0].guild_id : null;
    })();
    let guild = null;
    if (guildId) {
      const [gr] = await poolGen.query('SELECT name FROM gen_guilds WHERE id = ? LIMIT 1', [guildId]);
      if (gr.length) guild = gr[0].name;
    }
    res.json({
      ok: true,
      player: { ...p, money: Number(p.money), total_earned: Number(p.total_earned) },
      generators,
      guild
    });
  } catch (e) { res.status(500).json({ ok: false, error: e.message }); }
});

app.get('/api/generators/guilds', auth, async (req, res) => {
  try {
    const [rows] = await poolGen.query(`
      SELECT g.id, g.name, g.leader_uuid,
             COUNT(m.uuid) AS member_count,
             COALESCE(SUM(p.money), 0) AS total_money,
             COALESCE(MAX(p.prestige), 0) AS max_prestige
      FROM gen_guilds g
      LEFT JOIN gen_guild_members m ON g.id = m.guild_id
      LEFT JOIN gen_players p ON m.uuid = p.uuid
      GROUP BY g.id ORDER BY total_money DESC LIMIT 20`);
    res.json({ ok: true, rows: rows.map(r => ({ ...r, total_money: Number(r.total_money) })) });
  } catch (e) { res.status(500).json({ ok: false, error: e.message }); }
});

// ── REST-API: Einkommen-Verlauf ───────────────────────────────────────────

app.get('/api/generators/income-history', auth, async (req, res) => {
  const { uuid, hours = 24 } = req.query;
  if (!uuid) return res.status(400).json({ error: 'uuid required' });
  try {
    const nowHour = Math.floor(Date.now() / 3_600_000);
    const fromHour = nowHour - Math.min(168, Math.max(1, parseInt(hours)));
    const [rows] = await poolGen.query(
      'SELECT hour, earned FROM gen_income_log WHERE uuid=? AND hour>=? ORDER BY hour ASC',
      [uuid, fromHour]
    );
    // Fill missing hours with 0
    const map = new Map(rows.map(r => [Number(r.hour), Number(r.earned)]));
    const filled = [];
    for (let h = fromHour; h <= nowHour; h++) {
      filled.push({ hour: h, earned: map.get(h) || 0 });
    }
    res.json({ ok: true, data: filled });
  } catch (e) { res.status(500).json({ ok: false, error: e.message }); }
});

app.get('/api/generators/server-income', auth, async (req, res) => {
  try {
    const nowHour = Math.floor(Date.now() / 3_600_000);
    const [rows] = await poolGen.query(
      'SELECT hour, SUM(earned) AS total FROM gen_income_log WHERE hour>=? GROUP BY hour ORDER BY hour ASC',
      [nowHour - 24]
    );
    res.json({ ok: true, data: rows.map(r => ({ hour: Number(r.hour), total: Number(r.total) })) });
  } catch (e) { res.status(500).json({ ok: false, error: e.message }); }
});

app.get('/api/generators/seasons', auth, async (req, res) => {
  try {
    const [[{ maxSeason }]] = await poolGen.query('SELECT COALESCE(MAX(season_no),0) AS maxSeason FROM gen_seasons');
    const season = parseInt(req.query.season || maxSeason);
    const [rows] = await poolGen.query(
      'SELECT rank_pos, name, money, prestige FROM gen_seasons WHERE season_no=? ORDER BY rank_pos ASC',
      [season]
    );
    res.json({ ok: true, season, maxSeason: Number(maxSeason),
      rows: rows.map(r => ({ ...r, money: Number(r.money) })) });
  } catch (e) { res.status(500).json({ ok: false, error: e.message }); }
});

// ── REST-API: Economy-Übersicht ───────────────────────────────────────────

app.get('/api/economy/overview', auth, async (req, res) => {
  try {
    const [[eco]]  = await poolSv.execute('SELECT COUNT(*) AS players, COALESCE(SUM(coins),0) AS total FROM sv_economy');
    const [[bank]] = await poolSv.execute('SELECT COALESCE(SUM(balance),0) AS total FROM sv_bank WHERE balance > 0');
    const [jobs]   = await poolSv.execute('SELECT job_id, COUNT(*) AS players FROM sv_jobs WHERE active=1 GROUP BY job_id ORDER BY players DESC');
    const wallet   = Math.max(0, Number(eco.total) - Number(bank.total));
    res.json({ ok: true, totalCoins: Number(eco.total), bankCoins: Number(bank.total), walletCoins: wallet, players: Number(eco.players), jobs });
  } catch (e) { res.status(500).json({ error: e.message }); }
});

// ── REST-API: Alle Claims (für Karte) ────────────────────────────────────

app.get('/api/survival/allclaims', auth, async (req, res) => {
  try {
    const [rows] = await poolSv.execute(
      'SELECT c.chunk_x, c.chunk_z, c.world, p.name AS owner FROM sv_claims c JOIN pinkhorizon.players p ON c.owner_uuid=p.uuid'
    );
    res.json({ ok: true, claims: rows });
  } catch (e) { res.status(500).json({ error: e.message }); }
});

// ── REST-API: Freunde-Statistiken ─────────────────────────────────────────

app.get('/api/survival/friends', auth, async (req, res) => {
  try {
    const [[{ cnt }]] = await poolSv.execute('SELECT COUNT(*) AS cnt FROM sv_friends');
    const [top] = await poolSv.execute(
      'SELECT p.name, COUNT(*) AS friends FROM sv_friends f JOIN pinkhorizon.players p ON f.player_uuid=p.uuid GROUP BY f.player_uuid ORDER BY friends DESC LIMIT 10'
    );
    res.json({ ok: true, totalFriendships: Math.floor(Number(cnt) / 2), top });
  } catch (e) { res.status(500).json({ error: e.message }); }
});

// ── REST-API: Spieler-Metriken ────────────────────────────────────────────

app.get('/api/metrics/players', auth, async (req, res) => {
  const hours = parseInt(req.query.hours || '24');
  if (hours <= 24) {
    // Aus In-Memory-Buffer bedienen
    return res.json({ ok: true, data: playerMetrics });
  }
  // Aus DB: bis zu 7 Tage
  try {
    const cutoff = Date.now() - Math.min(hours, 168) * 60 * 60 * 1000;
    const [rows] = await poolCore.query(
      'SELECT ts, players AS count FROM ph_metrics WHERE ts >= ? ORDER BY ts ASC',
      [cutoff]
    );
    res.json({ ok: true, data: rows });
  } catch (e) { res.status(500).json({ error: e.message }); }
});

// ── REST-API: Weltgröße ───────────────────────────────────────────────────

app.get('/api/system/worldsize', auth, async (req, res) => {
  const result = {};
  for (const srv of ['lobby', 'survival']) {
    try {
      const exec = await docker.getContainer(`ph-${srv}`).exec({
        Cmd: ['bash', '-c', 'du -shc /data/world /data/world_nether /data/world_the_end 2>/dev/null | tail -1 | cut -f1'],
        AttachStdout: true, AttachStderr: true
      });
      const stream = await exec.start({ hijack: true });
      let out = '';
      await new Promise(r => { stream.on('data', c => { out += c.slice(8).toString(); }); stream.on('end', r); });
      result[srv] = out.trim() || '?';
    } catch { result[srv] = null; }
  }
  res.json({ ok: true, sizes: result });
});

// ── REST-API: IP-Banliste ─────────────────────────────────────────────────

app.get('/api/players/banlist-ip', auth, async (req, res) => {
  try {
    const raw  = await fs.readFile('/data/survival/banned-ips.json', 'utf8');
    const data = JSON.parse(raw);
    res.json({ bans: data.map(b => ({ ip: b.ip, reason: b.reason || 'Kein Grund', source: b.source || 'Unbekannt', expires: b.expires || 'forever' })) });
  } catch { res.json({ bans: [] }); }
});

app.delete('/api/players/banlist-ip', strictLimit, auth, async (req, res) => {
  const { ip } = req.body;
  if (!ip) return res.status(400).json({ error: 'Keine IP' });
  const cfg = SERVERS.survival;
  if (!cfg?.rcon) return res.status(400).json({ error: 'Kein RCON' });
  try {
    const out = await rconSend(cfg.rcon, `pardon-ip ${ip}`);
    addAudit('unban-ip', ip, '');
    res.json({ ok: true, output: stripColors(out) });
  } catch (e) { res.status(500).json({ ok: false, error: e.message }); }
});

// ── REST-API: Discord-Webhook ─────────────────────────────────────────────

app.post('/api/network/discord', auth, async (req, res) => {
  const { webhookUrl, message } = req.body;
  if (!webhookUrl || !message?.trim()) return res.status(400).json({ error: 'Webhook-URL und Nachricht angeben' });
  if (!/^https:\/\/discord(app)?\.com\/api\/webhooks\//.test(webhookUrl)) return res.status(400).json({ error: 'Ungültige Discord-Webhook-URL' });
  const body = JSON.stringify({ content: message, username: 'Pink Horizon' });
  try {
    const url = new URL(webhookUrl);
    await new Promise((resolve, reject) => {
      const req2 = https.request({
        hostname: url.hostname, path: url.pathname + url.search, method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Content-Length': Buffer.byteLength(body) }
      }, resp => { resp.resume(); resp.statusCode < 300 ? resolve() : reject(new Error(`Discord HTTP ${resp.statusCode}`)); });
      req2.on('error', reject); req2.write(body); req2.end();
    });
    addAudit('discord-webhook', 'discord', message.slice(0, 80));
    res.json({ ok: true });
  } catch (e) { res.status(500).json({ ok: false, error: e.message }); }
});

// ── REST-API: Quick-Aktionen ──────────────────────────────────────────────

const QUICK_ACTIONS = {
  time_day:      'time set day',
  time_night:    'time set night',
  weather_clear: 'weather clear',
  weather_rain:  'weather rain',
  itemclear:     'itemclear',
  save_all:      'save-all',
};

app.post('/api/servers/:name/quickaction', auth, async (req, res) => {
  const cfg = SERVERS[req.params.name];
  if (!cfg?.rcon) return res.status(400).json({ error: 'Kein RCON' });
  const cmd = QUICK_ACTIONS[req.body?.action];
  if (!cmd) return res.status(400).json({ error: 'Unbekannte Aktion' });
  try {
    const out = await rconSend(cfg.rcon, cmd);
    addAudit('quickaction', req.params.name, req.body.action);
    res.json({ ok: true, output: stripColors(out) });
  } catch (e) { res.status(500).json({ ok: false, error: e.message }); }
});

// ── REST-API: Spieler-Timeline ────────────────────────────────────────────

app.get('/api/players/timeline', auth, async (req, res) => {
  const { name } = req.query;
  if (!name) return res.status(400).json({ error: 'Kein Name' });
  try {
    const [rows] = await poolCore.execute('SELECT first_join, last_join AS last_seen FROM players WHERE name=? LIMIT 1', [name]);
    if (!rows.length) return res.status(404).json({ error: 'Nicht gefunden' });
    res.json({ ok: true, ...rows[0] });
  } catch (e) { res.status(500).json({ error: e.message }); }
});

// ── REST-API: VoteCoins ───────────────────────────────────────────────────

app.get('/api/vote/leaderboard', auth, async (req, res) => {
  try {
    const [rows] = await poolCore.execute(
      'SELECT name, coins, total_votes, last_vote FROM vote_coins ORDER BY coins DESC LIMIT 20'
    );
    res.json({ ok: true, rows: rows.map(r => ({ ...r, coins: Number(r.coins), total_votes: Number(r.total_votes) })) });
  } catch (e) { res.status(500).json({ ok: false, error: e.message }); }
});

app.get('/api/vote/player', auth, async (req, res) => {
  const { name } = req.query;
  if (!name) return res.status(400).json({ error: 'Kein Name' });
  try {
    const [rows] = await poolCore.execute(
      'SELECT name, coins, total_votes, last_vote FROM vote_coins WHERE name = ? LIMIT 1', [name]
    );
    if (!rows.length) return res.json({ ok: true, found: false });
    const r = rows[0];
    res.json({ ok: true, found: true, name: r.name, coins: Number(r.coins), totalVotes: Number(r.total_votes), lastVote: r.last_vote });
  } catch (e) { res.status(500).json({ ok: false, error: e.message }); }
});

app.get('/api/vote/stats', auth, async (req, res) => {
  try {
    const [[r]] = await poolCore.execute(
      'SELECT COUNT(*) AS players, COALESCE(SUM(coins),0) AS total_coins, COALESCE(SUM(total_votes),0) AS total_votes FROM vote_coins'
    );
    res.json({ ok: true, players: Number(r.players), totalCoins: Number(r.total_coins), totalVotes: Number(r.total_votes) });
  } catch (e) { res.status(500).json({ ok: false, error: e.message }); }
});

// ── REST-API: Discord Sync ────────────────────────────────────────────────

app.get('/api/discord/verified', auth, async (req, res) => {
  try {
    const [rows] = await poolCore.execute(
      `SELECT mc_name, discord_id, verified_at FROM discord_sync
       WHERE verified_at IS NOT NULL ORDER BY verified_at DESC LIMIT 50`
    );
    res.json({ ok: true, rows });
  } catch (e) { res.status(500).json({ ok: false, error: e.message }); }
});

app.get('/api/discord/player', auth, async (req, res) => {
  const { name } = req.query;
  if (!name) return res.status(400).json({ error: 'Kein Name' });
  try {
    const [rows] = await poolCore.execute(
      'SELECT mc_name, discord_id, verified_at FROM discord_sync WHERE mc_name = ? LIMIT 1', [name]
    );
    if (!rows.length) return res.json({ ok: true, found: false });
    res.json({ ok: true, found: true, ...rows[0], verified: !!rows[0].verified_at });
  } catch (e) { res.status(500).json({ ok: false, error: e.message }); }
});

app.delete('/api/discord/player', strictLimit, auth, async (req, res) => {
  const { name } = req.body;
  if (!name) return res.status(400).json({ error: 'Kein Name' });
  try {
    await poolCore.execute('DELETE FROM discord_sync WHERE mc_name = ?', [name]);
    addAudit('discord-unlink', name, '');
    res.json({ ok: true });
  } catch (e) { res.status(500).json({ ok: false, error: e.message }); }
});

// ── REST-API: Network Events ──────────────────────────────────────────────

app.get('/api/network/events', auth, async (req, res) => {
  try {
    const [rows] = await poolCore.execute(
      'SELECT server_name, event_type, seconds_until, created_at FROM network_events ORDER BY created_at DESC LIMIT 50'
    );
    res.json({ ok: true, rows });
  } catch (e) { res.json({ ok: true, rows: [] }); }
});

// ── DB Editor – Tabellen auflisten ────────────────────────────────────────

app.get('/api/db/tables', auth, async (req, res) => {
  const db = req.query.database;
  const allowed = ['pinkhorizon', 'ph_survival', 'ph_smash', 'ph_vote', 'ph_generators', 'ph_minigames'];
  if (!allowed.includes(db)) return res.status(400).json({ error: 'Invalid database' });
  try {
    // ph_vote ist ein Alias auf pinkhorizon (vote_coins-Tabellen liegen dort)
    const pool = db === 'ph_survival'   ? poolSv
      : db === 'ph_smash'      ? poolSmash
      : db === 'ph_generators' ? poolGen
      : db === 'ph_minigames'  ? poolMg
      : poolCore;
    const schemaDb = db === 'ph_vote' ? 'pinkhorizon' : db;
    const [rows] = await pool.execute(`
      SELECT table_name AS name, table_rows AS approx_rows,
             ROUND(data_length/1024,1) AS size_kb
      FROM information_schema.tables
      WHERE table_schema = ? ORDER BY table_name`, [schemaDb]);
    res.json({ tables: rows });
  } catch(e) { res.status(500).json({ error: e.message }); }
});

// ── DB Editor – Tabellen-Inhalt mit Paginierung ───────────────────────────

app.get('/api/db/table', auth, async (req, res) => {
  const { database: db, table, page = 0, limit = 50, search = '', sort = '', dir = 'ASC' } = req.query;
  const allowed = ['pinkhorizon', 'ph_survival', 'ph_smash', 'ph_vote', 'ph_generators', 'ph_minigames'];
  if (!allowed.includes(db)) return res.status(400).json({ error: 'Invalid database' });
  if (!/^[a-zA-Z0-9_]+$/.test(table)) return res.status(400).json({ error: 'Invalid table' });
  const safeDir = dir === 'DESC' ? 'DESC' : 'ASC';
  try {
    const pool = db === 'ph_survival'   ? poolSv
      : db === 'ph_smash'      ? poolSmash
      : db === 'ph_generators' ? poolGen
      : db === 'ph_minigames'  ? poolMg
      : poolCore;
    // Spalten ermitteln
    const [cols] = await pool.execute(`SHOW COLUMNS FROM \`${table}\``);
    const columns = cols.map(c => ({ name: c.Field, type: c.Type, key: c.Key, nullable: c.Null === 'YES' }));
    const pk = cols.find(c => c.Key === 'PRI')?.Field || columns[0]?.name;
    // Abfrage aufbauen
    let where = '';
    const params = [];
    if (search) {
      // LIKE nur auf String/Text-Spalten anwenden (nicht auf INT, BIGINT, etc.)
      const strCols = columns.filter(c => /char|text|varchar|enum|set/i.test(c.type)).slice(0, 5);
      if (strCols.length) {
        where = 'WHERE ' + strCols.map(c => `\`${c.name}\` LIKE ?`).join(' OR ');
        strCols.forEach(() => params.push(`%${search}%`));
      }
    }
    const orderBy = sort && columns.find(c => c.name === sort) ? `ORDER BY \`${sort}\` ${safeDir}` : '';
    const lim    = Math.max(1, Math.min(500, parseInt(limit) || 50));
    const offset = Math.max(0, parseInt(page) || 0) * lim;
    // LIMIT/OFFSET direkt interpolieren (sichere Integers) – vermeidet Prepared-Statement-Probleme
    const rowSql   = `SELECT * FROM \`${table}\` ${where} ${orderBy} LIMIT ${lim} OFFSET ${offset}`;
    const totalSql = `SELECT COUNT(*) as total FROM \`${table}\` ${where}`;
    const [rows]       = params.length ? await pool.execute(rowSql, params) : await pool.query(rowSql);
    const [[{total}]]  = params.length ? await pool.execute(totalSql, params) : await pool.query(totalSql);
    const safeVal = v => {
      if (v === null || v === undefined) return null;
      if (typeof v === 'bigint') return v.toString();
      if (Buffer.isBuffer(v)) return v.toString('hex');
      if (v instanceof Date) return isNaN(v.getTime()) ? null : v.toISOString();
      return v;
    };
    const safeRows = rows.map(row => Object.fromEntries(Object.entries(row).map(([k, v]) => [k, safeVal(v)])));
    const payload = JSON.stringify({ columns, rows: safeRows, total: Number(total), pk });
    res.setHeader('Content-Type', 'application/json');
    res.end(payload);
  } catch(e) { console.error('[DB/table]', e.message); res.status(500).json({ error: e.message }); }
});

// ── DB Editor – Zeile aktualisieren ──────────────────────────────────────

app.put('/api/db/row', auth, async (req, res) => {
  const { database: db, table, pk, pkValue, updates } = req.body;
  const allowed = ['pinkhorizon', 'ph_survival', 'ph_smash', 'ph_vote', 'ph_generators', 'ph_minigames'];
  if (!allowed.includes(db)) return res.status(400).json({ error: 'Invalid database' });
  if (!/^[a-zA-Z0-9_]+$/.test(table) || !/^[a-zA-Z0-9_]+$/.test(pk)) return res.status(400).json({ error: 'Invalid params' });
  try {
    const pool = db === 'ph_survival'   ? poolSv
      : db === 'ph_smash'      ? poolSmash
      : db === 'ph_generators' ? poolGen
      : db === 'ph_minigames'  ? poolMg
      : poolCore;
    const setClauses = Object.keys(updates).map(k => `\`${k}\` = ?`).join(', ');
    const values = [...Object.values(updates), pkValue];
    await pool.execute(`UPDATE \`${table}\` SET ${setClauses} WHERE \`${pk}\` = ?`, values);
    addAudit('db_update', table, `${pk}=${pkValue} SET ${JSON.stringify(updates)}`);
    res.json({ ok: true });
  } catch(e) { res.status(500).json({ error: e.message }); }
});

// ── DB Editor – Zeile löschen ─────────────────────────────────────────────

app.delete('/api/db/row', auth, async (req, res) => {
  const { database: db, table, pk, pkValue } = req.body;
  const allowed = ['pinkhorizon', 'ph_survival', 'ph_smash', 'ph_vote', 'ph_generators', 'ph_minigames'];
  if (!allowed.includes(db)) return res.status(400).json({ error: 'Invalid database' });
  if (!/^[a-zA-Z0-9_]+$/.test(table) || !/^[a-zA-Z0-9_]+$/.test(pk)) return res.status(400).json({ error: 'Invalid params' });
  try {
    const pool = db === 'ph_survival'   ? poolSv
      : db === 'ph_smash'      ? poolSmash
      : db === 'ph_generators' ? poolGen
      : db === 'ph_minigames'  ? poolMg
      : poolCore;
    await pool.execute(`DELETE FROM \`${table}\` WHERE \`${pk}\` = ?`, [pkValue]);
    addAudit('db_delete', table, `${pk}=${pkValue}`);
    res.json({ ok: true });
  } catch(e) { res.status(500).json({ error: e.message }); }
});

// ── Start ─────────────────────────────────────────────────────────────────

server.listen(PORT, () => console.log(`Pink Horizon Dashboard läuft auf Port ${PORT}`));
