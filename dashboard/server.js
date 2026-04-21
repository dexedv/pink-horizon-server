const express  = require('express');
const { WebSocketServer } = require('ws');
const http     = require('http');
const Docker   = require('dockerode');
const net      = require('net');
const path     = require('path');
const fs       = require('fs').promises;
const mysql    = require('mysql2/promise');

const app    = express();
const server = http.createServer(app);
const wss    = new WebSocketServer({ server });
const docker = new Docker({ socketPath: '/var/run/docker.sock' });

app.use(express.json());
app.use(express.static(path.join(__dirname, 'public')));

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
  }
};

const VALID_RANKS = ['spieler', 'vip', 'supporter', 'moderator', 'dev', 'admin', 'owner'];

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

async function checkDb(pool) {
  try { await pool.query('SELECT 1'); return true; }
  catch { return false; }
}

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

app.get('/api/players/banlist', auth, async (req, res) => {
  const cfg = SERVERS.survival;
  if (!cfg?.rcon) return res.json({ bans: [] });
  try {
    const raw   = await rconSend(cfg.rcon, 'banlist');
    const clean = stripColors(raw);
    const bans  = [];
    for (const line of clean.split('\n')) {
      const trimmed = line.trim();
      if (!trimmed) continue;
      if (/^there are/i.test(trimmed) || /^banned players:/i.test(trimmed)) continue;
      const m = /^(.+?):\s*(.*)$/.exec(trimmed);
      if (m) bans.push({ name: m[1].trim(), reason: m[2].trim() || 'Kein Grund angegeben' });
    }
    res.json({ bans });
  } catch { res.json({ bans: [] }); }
});

app.post('/api/players/unban', auth, async (req, res) => {
  const { name } = req.body;
  if (!name || !/^[a-zA-Z0-9_]{1,16}$/.test(name)) return res.status(400).json({ error: 'Ungültiger Name' });
  const cfg = SERVERS.survival;
  if (!cfg?.rcon) return res.status(400).json({ error: 'Kein RCON' });
  try {
    const out = await rconSend(cfg.rcon, `pardon ${name}`);
    res.json({ ok: true, output: stripColors(out) });
  } catch (e) { res.status(500).json({ ok: false, error: e.message }); }
});

// ── REST-API: Datenbanken ─────────────────────────────────────────────────

app.get('/api/databases', auth, async (req, res) => {
  const [ctr, ph, sv, mg] = await Promise.allSettled([
    getContainerStatus('ph-mysql'),
    checkDb(poolCore),
    checkDb(poolSv),
    checkDb(poolMg)
  ]);
  res.json({
    container:    ctr.status === 'fulfilled' ? ctr.value : { running: false, status: 'error' },
    pinkhorizon:  ph.status  === 'fulfilled' ? ph.value  : false,
    ph_survival:  sv.status  === 'fulfilled' ? sv.value  : false,
    ph_minigames: mg.status  === 'fulfilled' ? mg.value  : false
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
  for (const [key, cfg] of Object.entries(SERVERS)) {
    if (!cfg.rcon) continue;
    try { await rconSend(cfg.rcon, `broadcast ${message}`); results[key] = true; }
    catch { results[key] = false; }
  }
  res.json({ ok: true, results });
});

// ── REST-API: Container-Ressourcen ────────────────────────────────────────

app.get('/api/containers/stats', auth, async (req, res) => {
  const result = {};
  await Promise.all(Object.entries(SERVERS).map(async ([key, cfg]) => {
    try {
      const s = await docker.getContainer(cfg.container).stats({ stream: false });
      const cpuDelta = s.cpu_stats.cpu_usage.total_usage  - s.precpu_stats.cpu_usage.total_usage;
      const sysDelta = s.cpu_stats.system_cpu_usage       - s.precpu_stats.system_cpu_usage;
      const nCpus    = s.cpu_stats.online_cpus            || s.cpu_stats.cpu_usage.percpu_usage?.length || 1;
      result[key] = {
        cpu:      sysDelta > 0 ? parseFloat(((cpuDelta / sysDelta) * nCpus * 100).toFixed(1)) : 0,
        memUsed:  s.memory_stats.usage  || 0,
        memLimit: s.memory_stats.limit  || 1,
        memPct:   parseFloat(((s.memory_stats.usage || 0) / (s.memory_stats.limit || 1) * 100).toFixed(1))
      };
    } catch { result[key] = null; }
  }));
  res.json(result);
});

// ── REST-API: Backup ──────────────────────────────────────────────────────

app.post('/api/backup/:server', auth, async (req, res) => {
  const serverName = req.params.server;
  if (!['lobby', 'survival'].includes(serverName))
    return res.status(400).json({ error: 'Unbekannter Server' });
  const cfg = SERVERS[serverName];
  const ts  = new Date().toISOString().slice(0, 16).replace(/[T:]/g, '-');
  const arc = `backup-${ts}.tar.gz`;
  try {
    if (cfg.rcon) {
      try { await rconSend(cfg.rcon, 'save-all'); await new Promise(r => setTimeout(r, 2000)); } catch {}
    }
    const exec = await docker.getContainer(`ph-${serverName}`).exec({
      Cmd: ['bash', '-c', `cd /data && tar -czf ${arc} world world_nether world_the_end --ignore-failed-read 2>/dev/null; echo ok`],
      AttachStdout: true, AttachStderr: true
    });
    const stream = await exec.start({ hijack: true });
    await new Promise((resolve, reject) => {
      const t = setTimeout(() => reject(new Error('Backup-Timeout (5 min)')), 300000);
      stream.on('end', () => { clearTimeout(t); resolve(); });
      stream.on('error', e => { clearTimeout(t); reject(e); });
    });
    res.json({ ok: true, message: `Backup erstellt: ${arc}` });
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
  if (!/^(pinkhorizon|ph_survival|ph_minigames)$/.test(database)) return res.status(400).json({ error: 'Unbekannte Datenbank' });
  try {
    const pool = database === 'pinkhorizon' ? poolCore : database === 'ph_minigames' ? poolMg : poolSv;
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
      "SELECT name, `rank` FROM players ORDER BY FIELD(`rank`,'owner','admin','dev','moderator','supporter','vip','spieler'), name"
    );
    res.json({ players: rows });
  } catch (e) {
    res.status(500).json({ error: e.message, players: [] });
  }
});

// ── REST-API: Rechteverwaltung ────────────────────────────────────────────

const MANAGED_SERVERS = ['lobby', 'survival', 'minigames'];

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
    } catch (e) { console.error('WS Message Fehler:', e.message); }
  });

  ws.on('close', () => {
    if (subscribedContainer) logSubscribers[subscribedContainer]?.delete(ws);
  });
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

// ── Start ─────────────────────────────────────────────────────────────────

server.listen(PORT, () => console.log(`Pink Horizon Dashboard läuft auf Port ${PORT}`));
