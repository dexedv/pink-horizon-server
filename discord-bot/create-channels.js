/**
 * Einmaliges Setup-Script: erstellt Rollen + Kanäle + Spielmodus-Panel
 * Ausführen mit: node create-channels.js
 */
'use strict';
require('dotenv').config();

const TOKEN    = process.env.DISCORD_TOKEN;
const GUILD_ID = process.env.DISCORD_GUILD_ID;

if (!TOKEN || !GUILD_ID) { console.error('❌ DISCORD_TOKEN oder DISCORD_GUILD_ID fehlt in .env'); process.exit(1); }

const BASE = 'https://discord.com/api/v10';
const headers = { 'Authorization': `Bot ${TOKEN}`, 'Content-Type': 'application/json' };

const sleep = ms => new Promise(r => setTimeout(r, ms));

async function api(method, path, body) {
  const res = await fetch(`${BASE}${path}`, { method, headers, body: body ? JSON.stringify(body) : undefined });
  if (res.status === 429) {
    const j = await res.json();
    const wait = (j.retry_after ?? 5) * 1000 + 500;
    console.log(`  ⏳ Rate limit – warte ${Math.round(wait/1000)}s...`);
    await sleep(wait);
    return api(method, path, body);
  }
  if (!res.ok) { const t = await res.text(); throw new Error(`${res.status} ${path}: ${t}`); }
  return res.status === 204 ? null : res.json();
}

async function main() {
  console.log('🔍 Lade vorhandene Rollen und Kanäle...');
  const [allRoles, allChannels] = await Promise.all([
    api('GET', `/guilds/${GUILD_ID}/roles`),
    api('GET', `/guilds/${GUILD_ID}/channels`),
  ]);

  const role    = name => allRoles.find(r => r.name === name);
  const channel = (name, parentId) => allChannels.find(c => c.name === name && (!parentId || c.parent_id === parentId));
  const cat     = name => allChannels.find(c => c.name === name && c.type === 4);

  const adminRole  = role('Admin');
  const modRole    = role('Moderator');
  const suppRole   = role('Supporter');
  const verifRole  = role('Verifiziert');
  const everyone   = allRoles.find(r => r.name === '@everyone');

  console.log(`  Rollen: Admin=${!!adminRole} Mod=${!!modRole} Supp=${!!suppRole} Verif=${!!verifRole}`);

  // ── 1. Neue Rollen ─────────────────────────────────────────────────────────
  const newRoleDefs = [
    { name: 'Survival-Fan',  color: 0x2ECC71 },
    { name: 'Smash-Fan',     color: 0xFF5555 },
    { name: 'IdleForge-Fan', color: 0xFFAA00 },
  ];
  const roleMap = {};
  for (const def of newRoleDefs) {
    let r = role(def.name);
    if (!r) {
      console.log(`  ➕ Erstelle Rolle ${def.name}...`);
      r = await api('POST', `/guilds/${GUILD_ID}/roles`, { name: def.name, color: def.color, hoist: false, mentionable: false });
      await sleep(3000);
    } else {
      console.log(`  ✓ Rolle ${def.name} existiert bereits`);
    }
    roleMap[def.name] = r;
  }

  // ── 2. SPIELMODUS Kategorie ────────────────────────────────────────────────
  const spielmodusCatPerms = [
    { id: everyone.id, type: 0, deny: '1024' },
    ...(verifRole ? [{ id: verifRole.id, type: 0, allow: '1024' }] : []),
    ...(adminRole ? [{ id: adminRole.id, type: 0, allow: '1024' }] : []),
    ...(modRole   ? [{ id: modRole.id,   type: 0, allow: '1024' }] : []),
    ...(suppRole  ? [{ id: suppRole.id,  type: 0, allow: '1024' }] : []),
  ];

  let spielmodusCat = cat('🎮 SPIELMODUS');
  if (!spielmodusCat) {
    console.log('  ➕ Erstelle Kategorie 🎮 SPIELMODUS...');
    spielmodusCat = await api('POST', `/guilds/${GUILD_ID}/channels`, {
      name: '🎮 SPIELMODUS', type: 4, permission_overwrites: spielmodusCatPerms,
    });
    await sleep(3000);
  } else {
    console.log('  ✓ 🎮 SPIELMODUS existiert bereits');
  }

  let selfroleCh = channel('rollen-wählen', spielmodusCat.id);
  if (!selfroleCh) {
    console.log('  ➕ Erstelle #rollen-wählen...');
    selfroleCh = await api('POST', `/guilds/${GUILD_ID}/channels`, {
      name: 'rollen-wählen', type: 0, parent_id: spielmodusCat.id,
      permission_overwrites: [
        { id: everyone.id, type: 0, deny: '2048' },
        ...(adminRole ? [{ id: adminRole.id, type: 0, allow: '2048' }] : []),
        ...(modRole   ? [{ id: modRole.id,   type: 0, allow: '2048' }] : []),
      ],
    });
    await sleep(3000);
  } else {
    console.log('  ✓ #rollen-wählen existiert bereits');
  }

  // ── 3. IDLEFORGE Kategorie ─────────────────────────────────────────────────
  const idleforgeCatPerms = [
    { id: everyone.id, type: 0, deny: '1024' },
    ...(adminRole                ? [{ id: adminRole.id,                  type: 0, allow: '1024' }] : []),
    ...(modRole                  ? [{ id: modRole.id,                    type: 0, allow: '1024' }] : []),
    ...(suppRole                 ? [{ id: suppRole.id,                   type: 0, allow: '1024' }] : []),
    ...(roleMap['IdleForge-Fan'] ? [{ id: roleMap['IdleForge-Fan'].id,   type: 0, allow: '1024' }] : []),
  ];

  let idleforgeCat = cat('⚙️ IDLEFORGE');
  if (!idleforgeCat) {
    console.log('  ➕ Erstelle Kategorie ⚙️ IDLEFORGE...');
    idleforgeCat = await api('POST', `/guilds/${GUILD_ID}/channels`, {
      name: '⚙️ IDLEFORGE', type: 4, permission_overwrites: idleforgeCatPerms,
    });
    await sleep(3000);
  } else {
    console.log('  ✓ ⚙️ IDLEFORGE existiert bereits');
  }

  for (const chName of ['generators-allgemein', 'generators-tipps']) {
    if (!channel(chName, idleforgeCat.id)) {
      console.log(`  ➕ Erstelle #${chName}...`);
      await api('POST', `/guilds/${GUILD_ID}/channels`, { name: chName, type: 0, parent_id: idleforgeCat.id });
      await sleep(3000);
    } else {
      console.log(`  ✓ #${chName} existiert bereits`);
    }
  }

  // ── 4. Spielmodus-Panel posten ─────────────────────────────────────────────
  const msgs = await api('GET', `/channels/${selfroleCh.id}/messages?limit=5`);
  if (!msgs || msgs.length === 0) {
    console.log('  📩 Poste Spielmodus-Panel...');
    await api('POST', `/channels/${selfroleCh.id}/messages`, {
      embeds: [{
        title: '🎮 Wähle deinen Spielmodus',
        color: 0xAA00AA,
        description: [
          'Klicke auf einen Button um die Rolle für deinen Spielmodus zu erhalten.',
          'Du kannst mehrere Rollen gleichzeitig haben.',
          '',
          '**⛏️ Survival** – Erkunde die Welt, Claims & Economy',
          '**🎮 Smash the Boss** – Besiege Bosse, Upgrades & Prestige',
          '**⚙️ IdleForge** – Generatoren, passives Einkommen & Prestige',
          '',
          '*Klicke erneut auf eine Rolle um sie zu entfernen.*',
        ].join('\n'),
        footer: { text: 'Pink Horizon · play.pinkhorizon.fun' },
      }],
      components: [{
        type: 1,
        components: [
          { type: 2, style: 3, label: '⛏️ Survival',       custom_id: 'selfrole_survival'  },
          { type: 2, style: 4, label: '🎮 Smash the Boss', custom_id: 'selfrole_smash'     },
          { type: 2, style: 1, label: '⚙️ IdleForge',      custom_id: 'selfrole_idleforge' },
        ],
      }],
    });
  } else {
    console.log('  ✓ Panel existiert bereits');
  }

  console.log('\n✅ Fertig! Alles wurde erstellt.');
}

main().catch(e => { console.error('❌ Fehler:', e.message); process.exit(1); });
