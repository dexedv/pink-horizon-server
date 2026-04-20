#!/usr/bin/env node
/**
 * Pink Horizon – YAML → MySQL Migrationsskript
 *
 * Ausführen im Dashboard-Container (empfohlen):
 *   docker exec ph-dashboard node migrate.js
 *
 * Optionale Umgebungsvariablen:
 *   DATA_DIR  – Pfad zum PH-Survival Plugin-Ordner
 *               (Standard: /data/survival/plugins/PH-Survival)
 *   DRY_RUN=1 – Nur einlesen und anzeigen, nichts in die DB schreiben
 */

const mysql = require('mysql2/promise');
const yaml  = require('js-yaml');
const fs    = require('fs');
const path  = require('path');

// ── Konfiguration ──────────────────────────────────────────────────────────
const DATA_DIR       = process.env.DATA_DIR       || '/data/survival/plugins/PH-Survival';
const LOBBY_DATA_DIR = process.env.LOBBY_DATA_DIR || '/data/lobby/plugins/PH-Lobby';
const DB_HOST  = process.env.DB_HOST  || 'ph-mysql';
const DB_PORT  = parseInt(process.env.DB_PORT  || '3306');
const DB_USER  = process.env.DB_USER  || 'ph_user';
const DB_PASS  = process.env.DB_PASS  || 'ph-db-2024';
const DRY_RUN  = process.env.DRY_RUN  === '1';

const EXPIRE_MS = 7 * 24 * 60 * 60 * 1000; // 7 Tage (Auktionshaus)

let conn;       // ph_survival DB
let connCore;   // pinkhorizon DB (für Lobby)
let stats = {};

// ── Hilfsfunktionen ────────────────────────────────────────────────────────
function loadYaml(filename) {
  const file = path.join(DATA_DIR, filename);
  if (!fs.existsSync(file)) { log(`  ⚠  ${filename} nicht gefunden – überspringe`); return null; }
  try {
    return yaml.load(fs.readFileSync(file, 'utf8')) || {};
  } catch (e) {
    log(`  ✗  Fehler beim Lesen von ${filename}: ${e.message}`);
    return null;
  }
}

function log(msg) { console.log(msg); }

function track(table, n) {
  stats[table] = (stats[table] || 0) + n;
}

async function exec(sql, params) {
  if (DRY_RUN) return [{ affectedRows: 0, insertId: 0 }];
  return conn.execute(sql, params);
}

async function execCore(sql, params) {
  if (DRY_RUN) return [{ affectedRows: 0, insertId: 0 }];
  return connCore.execute(sql, params);
}

/** Parst "world:cx:cz" – world-Name darf Doppelpunkte enthalten */
function splitClaimKey(key) {
  const last   = key.lastIndexOf(':');
  const second = key.lastIndexOf(':', last - 1);
  return {
    world:   key.slice(0, second),
    chunkX:  parseInt(key.slice(second + 1, last)),
    chunkZ:  parseInt(key.slice(last + 1))
  };
}

// ── Migrationen ────────────────────────────────────────────────────────────

async function migrateHomes() {
  log('\n── Homes (homes.yml → sv_homes) ──');
  const data = loadYaml('homes.yml');
  if (!data?.homes) { log('  (leer)'); return; }
  let n = 0;
  for (const [uuid, homeMap] of Object.entries(data.homes)) {
    if (typeof homeMap !== 'object') continue;
    for (const [name, loc] of Object.entries(homeMap)) {
      if (!loc?.world) continue;
      try {
        await exec(
          `INSERT IGNORE INTO sv_homes (uuid, name, world, x, y, z, yaw, pitch)
           VALUES (?,?,?,?,?,?,?,?)`,
          [uuid, name, loc.world, loc.x ?? 0, loc.y ?? 64, loc.z ?? 0, loc.yaw ?? 0, loc.pitch ?? 0]
        );
        n++;
      } catch (e) { log(`  ✗ Home ${uuid}/${name}: ${e.message}`); }
    }
  }
  track('sv_homes', n);
  log(`  ✓ ${n} Homes migriert`);
}

async function migrateWarps() {
  log('\n── Warps (warps.yml → sv_warps) ──');
  const data = loadYaml('warps.yml');
  if (!data?.warps) { log('  (leer)'); return; }
  let n = 0;
  for (const [name, loc] of Object.entries(data.warps)) {
    if (!loc?.world) continue;
    try {
      await exec(
        `INSERT INTO sv_warps (name, world, x, y, z, yaw, pitch)
         VALUES (?,?,?,?,?,?,?)
         ON DUPLICATE KEY UPDATE world=VALUES(world), x=VALUES(x), y=VALUES(y), z=VALUES(z), yaw=VALUES(yaw), pitch=VALUES(pitch)`,
        [name, loc.world, loc.x ?? 0, loc.y ?? 64, loc.z ?? 0, loc.yaw ?? 0, loc.pitch ?? 0]
      );
      n++;
    } catch (e) { log(`  ✗ Warp ${name}: ${e.message}`); }
  }
  track('sv_warps', n);
  log(`  ✓ ${n} Warps migriert`);
}

async function migrateClaims() {
  log('\n── Claims (claims.yml → sv_claims + sv_claim_trusts) ──');
  const data = loadYaml('claims.yml');
  if (!data) { log('  (leer)'); return; }

  const claimsMap = data.claims || {};
  const trustMap  = data.trust  || {};
  let nc = 0, nt = 0;

  for (const [key, ownerUuid] of Object.entries(claimsMap)) {
    if (!ownerUuid) continue;
    const { world, chunkX, chunkZ } = splitClaimKey(key);
    try {
      const [res] = await exec(
        `INSERT IGNORE INTO sv_claims (world, chunk_x, chunk_z, owner_uuid) VALUES (?,?,?,?)`,
        [world, chunkX, chunkZ, ownerUuid]
      );
      nc++;

      // Trusts für diesen Chunk
      const trustedList = trustMap[key];
      if (!Array.isArray(trustedList) || trustedList.length === 0) continue;

      for (const trustedUuid of trustedList) {
        try {
          await exec(
            `INSERT IGNORE INTO sv_claim_trusts (world, chunk_x, chunk_z, trusted_uuid) VALUES (?,?,?,?)`,
            [world, chunkX, chunkZ, trustedUuid]
          );
          nt++;
        } catch (e) { log(`  ✗ Trust ${key}/${trustedUuid}: ${e.message}`); }
      }
    } catch (e) { log(`  ✗ Claim ${key}: ${e.message}`); }
  }
  track('sv_claims', nc);
  track('sv_claim_trusts', nt);
  log(`  ✓ ${nc} Claims, ${nt} Trusts migriert`);
}

async function migrateJobs() {
  log('\n── Jobs (jobs.yml → sv_jobs) ──');
  const data = loadYaml('jobs.yml');
  if (!data?.players) { log('  (leer)'); return; }
  const JOB_NAMES = ['MINER','FARMER','WOODCUTTER','HUNTER','FISHERMAN'];
  let n = 0;
  for (const [uuid, pd] of Object.entries(data.players)) {
    if (typeof pd !== 'object') continue;
    const activeJob = pd.job && pd.job !== 'NONE' ? pd.job : null;
    // Aktiven Job
    if (activeJob) {
      const progress = pd[activeJob] || {};
      try {
        await exec(
          `INSERT INTO sv_jobs (uuid, job_id, level, xp, active)
           VALUES (?,?,?,?,TRUE)
           ON DUPLICATE KEY UPDATE level=VALUES(level), xp=VALUES(xp), active=TRUE`,
          [uuid, activeJob, progress.level ?? 1, progress.xp ?? 0]
        );
        n++;
      } catch (e) { log(`  ✗ Job ${uuid}/${activeJob}: ${e.message}`); }
    }
    // Alle anderen Jobs mit Fortschritt (inaktiv)
    for (const jobId of JOB_NAMES) {
      if (jobId === activeJob) continue;
      const progress = pd[jobId];
      if (!progress || (progress.level <= 1 && progress.xp === 0)) continue;
      try {
        await exec(
          `INSERT INTO sv_jobs (uuid, job_id, level, xp, active)
           VALUES (?,?,?,?,FALSE)
           ON DUPLICATE KEY UPDATE level=VALUES(level), xp=VALUES(xp)`,
          [uuid, jobId, progress.level ?? 1, progress.xp ?? 0]
        );
        n++;
      } catch (e) { log(`  ✗ Job ${uuid}/${jobId}: ${e.message}`); }
    }
  }
  track('sv_jobs', n);
  log(`  ✓ ${n} Job-Einträge migriert`);
}

async function migrateStats() {
  log('\n── Statistiken (stats.yml → sv_stats) ──');
  const data = loadYaml('stats.yml');
  if (!data) { log('  (leer)'); return; }
  let n = 0;
  for (const [uuid, s] of Object.entries(data)) {
    if (typeof s !== 'object') continue;
    try {
      await exec(
        `INSERT INTO sv_stats (uuid, deaths, mob_kills, player_kills, blocks_broken, playtime)
         VALUES (?,?,?,?,?,?)
         ON DUPLICATE KEY UPDATE
           deaths=VALUES(deaths), mob_kills=VALUES(mob_kills),
           player_kills=VALUES(player_kills), blocks_broken=VALUES(blocks_broken),
           playtime=VALUES(playtime)`,
        [uuid, s.deaths ?? 0, s.mob_kills ?? 0, s.player_kills ?? 0, s.blocks_broken ?? 0, s.playtime ?? 0]
      );
      n++;
    } catch (e) { log(`  ✗ Stats ${uuid}: ${e.message}`); }
  }
  track('sv_stats', n);
  log(`  ✓ ${n} Statistik-Einträge migriert`);
}

async function migrateAchievements() {
  log('\n── Achievements (achievements.yml → sv_achievements) ──');
  const data = loadYaml('achievements.yml');
  if (!data?.achievements) { log('  (leer)'); return; }
  let n = 0;
  for (const [uuid, ach] of Object.entries(data.achievements)) {
    if (typeof ach !== 'object') continue;
    for (const [name, unlocked] of Object.entries(ach)) {
      if (!unlocked) continue;
      try {
        await exec(
          `INSERT IGNORE INTO sv_achievements (uuid, achievement) VALUES (?,?)`,
          [uuid, name]
        );
        n++;
      } catch (e) { log(`  ✗ Achievement ${uuid}/${name}: ${e.message}`); }
    }
  }
  track('sv_achievements', n);
  log(`  ✓ ${n} Achievements migriert`);
}

async function migrateFriends() {
  log('\n── Freunde (friends.yml → sv_friends + sv_friend_requests) ──');
  const data = loadYaml('friends.yml');
  if (!data) { log('  (leer)'); return; }
  let nf = 0, nr = 0;

  const friendsMap   = data.friends  || {};
  const requestsMap  = data.requests || {};

  for (const [uuid, list] of Object.entries(friendsMap)) {
    if (!Array.isArray(list)) continue;
    for (const friend of list) {
      try {
        await exec(
          `INSERT IGNORE INTO sv_friends (uuid1, uuid2) VALUES (?,?)`,
          [uuid, friend]
        );
        nf++;
      } catch (e) { log(`  ✗ Friend ${uuid}→${friend}: ${e.message}`); }
    }
  }

  for (const [fromUuid, list] of Object.entries(requestsMap)) {
    if (!Array.isArray(list)) continue;
    for (const toUuid of list) {
      try {
        await exec(
          `INSERT IGNORE INTO sv_friend_requests (from_uuid, to_uuid) VALUES (?,?)`,
          [fromUuid, toUuid]
        );
        nr++;
      } catch (e) { log(`  ✗ Request ${fromUuid}→${toUuid}: ${e.message}`); }
    }
  }
  track('sv_friends', nf);
  track('sv_friend_requests', nr);
  log(`  ✓ ${nf} Freundschaften, ${nr} Anfragen migriert`);
}

async function migrateBank() {
  log('\n── Bank (bank.yml → sv_bank) ──');
  const data = loadYaml('bank.yml');
  if (!data?.bank) { log('  (leer)'); return; }
  let n = 0;
  for (const [uuid, b] of Object.entries(data.bank)) {
    if (typeof b !== 'object') continue;
    const lastInterest = b.lastInterest || new Date().toISOString().slice(0, 10);
    try {
      await exec(
        `INSERT INTO sv_bank (uuid, balance, last_interest)
         VALUES (?,?,?)
         ON DUPLICATE KEY UPDATE balance=VALUES(balance), last_interest=VALUES(last_interest)`,
        [uuid, b.balance ?? 0, lastInterest]
      );
      n++;
    } catch (e) { log(`  ✗ Bank ${uuid}: ${e.message}`); }
  }
  track('sv_bank', n);
  log(`  ✓ ${n} Bank-Konten migriert`);
}

async function migrateMail() {
  log('\n── Nachrichten (mail.yml → sv_mails) ──');
  const data = loadYaml('mail.yml');
  if (!data?.mails) { log('  (leer)'); return; }
  let n = 0;
  for (const [toUuid, mails] of Object.entries(data.mails)) {
    if (!Array.isArray(mails)) continue;
    for (const m of mails) {
      if (!m?.message) continue;
      try {
        await exec(
          `INSERT INTO sv_mails (to_uuid, sender_name, message, sent_at, is_read)
           VALUES (?,?,?,?,?)`,
          [toUuid, m.sender || 'Unbekannt', m.message, m.timestamp || new Date().toISOString().slice(0, 19).replace('T', ' '), m.read ? 1 : 0]
        );
        n++;
      } catch (e) { log(`  ✗ Mail für ${toUuid}: ${e.message}`); }
    }
  }
  track('sv_mails', n);
  log(`  ✓ ${n} Nachrichten migriert`);
}

async function migrateAuction() {
  log('\n── Auktionshaus (auction.yml → sv_auction) ──');
  const data = loadYaml('auction.yml');
  if (!data?.listings) { log('  (leer)'); return; }
  const now = Date.now();
  let n = 0, skipped = 0;
  for (const [id, l] of Object.entries(data.listings)) {
    if (!l?.seller || !l?.item) continue;
    if (now - Number(l.listedAt) > EXPIRE_MS) { skipped++; continue; }
    try {
      await exec(
        `INSERT IGNORE INTO sv_auction (id, seller_uuid, seller_name, item_data, price, listed_at)
         VALUES (?,?,?,?,?,?)`,
        [id, l.seller, l.sellerName || 'Unbekannt', l.item, l.price ?? 0, l.listedAt ?? now]
      );
      n++;
    } catch (e) { log(`  ✗ Listing ${id}: ${e.message}`); }
  }
  track('sv_auction', n);
  log(`  ✓ ${n} Angebote migriert (${skipped} abgelaufen übersprungen)`);
}

async function migrateUpgrades() {
  log('\n── Upgrades (upgrades.yml → sv_upgrades) ──');
  const data = loadYaml('upgrades.yml');
  if (!data?.upgrades) { log('  (leer)'); return; }
  let n = 0;
  for (const [uuid, u] of Object.entries(data.upgrades)) {
    if (typeof u !== 'object') continue;
    try {
      await exec(
        `INSERT INTO sv_upgrades (uuid, keep_inventory, fly_perm, ki_expiry, fly_expiry, extra_claims, claim_purchases)
         VALUES (?,?,?,?,?,?,?)
         ON DUPLICATE KEY UPDATE
           keep_inventory=VALUES(keep_inventory), fly_perm=VALUES(fly_perm),
           ki_expiry=VALUES(ki_expiry), fly_expiry=VALUES(fly_expiry),
           extra_claims=VALUES(extra_claims), claim_purchases=VALUES(claim_purchases)`,
        [
          uuid,
          u.keepInventory  ? 1 : 0,
          u.flyPerm        ? 1 : 0,
          u.kiExpiry       ?? 0,
          u.flyExpiry      ?? 0,
          u.extraClaims    ?? 0,
          u.claimPurchases ?? 0
        ]
      );
      n++;
    } catch (e) { log(`  ✗ Upgrade ${uuid}: ${e.message}`); }
  }
  track('sv_upgrades', n);
  log(`  ✓ ${n} Upgrade-Einträge migriert`);
}

async function migrateQuests() {
  log('\n── Tages-Quests (quests.yml → sv_quests) ──');
  const data = loadYaml('quests.yml');
  if (!data?.quests) { log('  (leer)'); return; }
  const today = new Date().toISOString().slice(0, 10);
  let n = 0;
  for (const [uuid, q] of Object.entries(data.quests)) {
    if (typeof q !== 'object') continue;
    // Nur migrieren wenn es heutige Quests sind
    if (q.date !== today) continue;
    const types = Array.isArray(q.types) ? q.types : [];
    for (const type of types) {
      const progress  = q.progress?.[type]  ?? 0;
      const completed = q.completed?.[type] ?? false;
      try {
        await exec(
          `INSERT IGNORE INTO sv_quests (uuid, quest_date, quest_id, progress, completed)
           VALUES (?,?,?,?,?)`,
          [uuid, today, type, progress, completed ? 1 : 0]
        );
        n++;
      } catch (e) { log(`  ✗ Quest ${uuid}/${type}: ${e.message}`); }
    }
  }
  track('sv_quests', n);
  log(`  ✓ ${n} heutige Quest-Einträge migriert (ältere Quests werden übersprungen)`);
}

async function migrateNpcs() {
  log('\n── NPCs (npcs.yml → sv_npcs + sv_npc_commands) ──');
  const data = loadYaml('npcs.yml');
  if (!data?.npcs) { log('  (leer)'); return; }
  let nn = 0, nc = 0;
  for (const [key, npc] of Object.entries(data.npcs)) {
    if (typeof npc !== 'object') continue;
    const id         = parseInt(key);
    const name       = npc.name       ?? 'NPC';
    const world      = npc.world      ?? 'world';
    const x          = npc.x          ?? 0;
    const y          = npc.y          ?? 64;
    const z          = npc.z          ?? 0;
    const yaw        = npc.yaw        ?? 0;
    const profession = npc.profession ?? 'NONE';
    try {
      const [res] = await exec(
        `INSERT INTO sv_npcs (id, name, world, x, y, z, yaw, profession)
         VALUES (?,?,?,?,?,?,?,?)
         ON DUPLICATE KEY UPDATE name=VALUES(name), world=VALUES(world),
           x=VALUES(x), y=VALUES(y), z=VALUES(z), yaw=VALUES(yaw), profession=VALUES(profession)`,
        [id, name, world, x, y, z, yaw, profession]
      );
      nn++;
      // Befehle
      const commands = Array.isArray(npc.commands) ? npc.commands : [];
      if (commands.length > 0) {
        await exec(`DELETE FROM sv_npc_commands WHERE npc_id=?`, [id]);
        for (let i = 0; i < commands.length; i++) {
          await exec(
            `INSERT INTO sv_npc_commands (npc_id, idx, command) VALUES (?,?,?)`,
            [id, i, commands[i]]
          );
          nc++;
        }
      }
    } catch (e) { log(`  ✗ NPC ${key}: ${e.message}`); }
  }
  track('sv_npcs', nn);
  track('sv_npc_commands', nc);
  log(`  ✓ ${nn} NPCs, ${nc} Befehle migriert`);
}

async function migrateSurvivalHolograms() {
  log('\n── Survival-Holograms (holograms.yml → sv_holograms) ──');
  const data = loadYaml('holograms.yml');
  if (!data?.holograms) { log('  (leer)'); return; }
  let n = 0;
  for (const [name, h] of Object.entries(data.holograms)) {
    if (typeof h !== 'object') continue;
    const world = h.world ?? 'world';
    const x     = h.x     ?? 0;
    const y     = h.y     ?? 64;
    const z     = h.z     ?? 0;
    const scale = h.scale ?? 1.0;
    const lines = Array.isArray(h.lines) ? h.lines.join('\0') : '';
    try {
      await exec(
        `INSERT INTO sv_holograms (name, world, x, y, z, scale, \`lines\`)
         VALUES (?,?,?,?,?,?,?)
         ON DUPLICATE KEY UPDATE world=VALUES(world), x=VALUES(x), y=VALUES(y),
           z=VALUES(z), scale=VALUES(scale), \`lines\`=VALUES(\`lines\`)`,
        [name, world, x, y, z, scale, lines]
      );
      n++;
    } catch (e) { log(`  ✗ Hologram ${name}: ${e.message}`); }
  }
  track('sv_holograms', n);
  log(`  ✓ ${n} Survival-Holograms migriert`);
}

async function migrateLobbyHolograms() {
  log('\n── Lobby-Holograms (holograms.yml → lb_holograms) ──');
  const file = path.join(LOBBY_DATA_DIR, 'holograms.yml');
  if (!fs.existsSync(file)) { log(`  ⚠  ${file} nicht gefunden – überspringe`); return; }
  let raw;
  try { raw = yaml.load(fs.readFileSync(file, 'utf8')) || {}; }
  catch (e) { log(`  ✗  Fehler beim Lesen: ${e.message}`); return; }
  if (!raw?.holograms) { log('  (leer)'); return; }
  let n = 0;
  for (const [name, h] of Object.entries(raw.holograms)) {
    if (typeof h !== 'object') continue;
    const world = h.world ?? 'world';
    const x     = h.x     ?? 0;
    const y     = h.y     ?? 64;
    const z     = h.z     ?? 0;
    const scale = h.scale ?? 3.0;
    const text  = h.text  ?? name;
    try {
      await execCore(
        `INSERT INTO lb_holograms (name, world, x, y, z, scale, text)
         VALUES (?,?,?,?,?,?,?)
         ON DUPLICATE KEY UPDATE world=VALUES(world), x=VALUES(x), y=VALUES(y),
           z=VALUES(z), scale=VALUES(scale), text=VALUES(text)`,
        [name, world, x, y, z, scale, text]
      );
      n++;
    } catch (e) { log(`  ✗ Lobby-Hologram ${name}: ${e.message}`); }
  }
  track('lb_holograms', n);
  log(`  ✓ ${n} Lobby-Holograms migriert`);
}

// ── Hauptprogramm ──────────────────────────────────────────────────────────
async function main() {
  log('╔══════════════════════════════════════════════╗');
  log('║  Pink Horizon – YAML → MySQL Migration       ║');
  log('╚══════════════════════════════════════════════╝');
  log(`\nSurvival-Ordner : ${DATA_DIR}`);
  log(`Lobby-Ordner    : ${LOBBY_DATA_DIR}`);
  log(`Datenbank       : ${DB_USER}@${DB_HOST}:${DB_PORT}/ph_survival + pinkhorizon`);
  log(`Modus           : ${DRY_RUN ? 'DRY-RUN (keine Änderungen)' : 'LIVE (schreibt in DB)'}\n`);

  if (!fs.existsSync(DATA_DIR)) {
    log(`FEHLER: Survival-Ordner nicht gefunden: ${DATA_DIR}`);
    log('Starte den Container mit korrektem Volume-Mount oder setze DATA_DIR.');
    process.exit(1);
  }

  if (!DRY_RUN) {
    try {
      conn = await mysql.createConnection({
        host: DB_HOST, port: DB_PORT, user: DB_USER, password: DB_PASS,
        database: 'ph_survival', connectTimeout: 5000
      });
      connCore = await mysql.createConnection({
        host: DB_HOST, port: DB_PORT, user: DB_USER, password: DB_PASS,
        database: 'pinkhorizon', connectTimeout: 5000
      });
      log('✓ MySQL-Verbindungen hergestellt\n');
    } catch (e) {
      log(`FEHLER: MySQL-Verbindung fehlgeschlagen: ${e.message}`);
      process.exit(1);
    }
  }

  await migrateHomes();
  await migrateWarps();
  await migrateClaims();
  await migrateJobs();
  await migrateStats();
  await migrateAchievements();
  await migrateFriends();
  await migrateBank();
  await migrateMail();
  await migrateAuction();
  await migrateUpgrades();
  await migrateQuests();
  await migrateNpcs();
  await migrateSurvivalHolograms();
  await migrateLobbyHolograms();

  if (conn) await conn.end();
  if (connCore) await connCore.end();

  log('\n══════════════════ Zusammenfassung ═══════════════');
  for (const [table, count] of Object.entries(stats)) {
    log(`  ${table.padEnd(22)} ${String(count).padStart(6)} Einträge`);
  }
  log('══════════════════════════════════════════════════');
  log(DRY_RUN ? '\n⚠  DRY-RUN: Keine Daten wurden geschrieben!' : '\n✓  Migration abgeschlossen!');
}

main().catch(e => { console.error('Unerwarteter Fehler:', e); process.exit(1); });
