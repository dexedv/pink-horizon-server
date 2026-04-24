/**
 * Pink Horizon – Discord Community Bot
 *
 * Features:
 *  - /setup  – erstellt alle Rollen, Kategorien & Kanäle
 *  - /status – zeigt aktuellen Serverstatus
 *  - /stats  – zeigt Smash-Statistiken eines Spielers
 *  - Automatisches Status-Embed (60s Update)
 *  - Sprachkanal-Statistiken (Mitglieder / Ingame)
 *  - @Spieler-Rolle bei Join + Willkommensnachricht
 */

'use strict';

require('dotenv').config();

const {
  Client,
  GatewayIntentBits,
  EmbedBuilder,
  PermissionFlagsBits,
  ChannelType,
  ActivityType,
  SlashCommandBuilder,
  REST,
  Routes,
} = require('discord.js');

const { pingJava } = require('minecraft-server-util');
const mysql = require('mysql2/promise');

// ─────────────────────────────────────────────────────────────────────────────
// Config
// ─────────────────────────────────────────────────────────────────────────────

const TOKEN      = process.env.DISCORD_TOKEN;
const CLIENT_ID  = process.env.DISCORD_CLIENT_ID;
const GUILD_ID   = process.env.DISCORD_GUILD_ID;
const MC_ADDRESS = process.env.MC_ADDRESS || 'play.pinkhorizon.fun';

// Minecraft servers to monitor (inside Docker: service name = hostname)
const SERVERS = [
  { label: '🎮 Smash the Boss', host: process.env.SMASH_HOST    || 'smash',    port: parseInt(process.env.SMASH_PORT    || '25570') },
  { label: '⛏️ Survival',       host: process.env.SURVIVAL_HOST || 'survival', port: parseInt(process.env.SURVIVAL_PORT || '25565') },
  { label: '🏠 Lobby',          host: process.env.LOBBY_HOST    || 'lobby',    port: parseInt(process.env.LOBBY_PORT    || '25565') },
];
const PROXY_HOST = process.env.PROXY_HOST || 'velocity';
const PROXY_PORT = parseInt(process.env.PROXY_PORT || '25565');

// ─────────────────────────────────────────────────────────────────────────────
// Database (optional – for /stats)
// ─────────────────────────────────────────────────────────────────────────────

let db = null;

async function getDb() {
  if (db) return db;
  if (!process.env.DB_HOST) return null;
  db = mysql.createPool({
    host:     process.env.DB_HOST,
    port:     parseInt(process.env.DB_PORT || '3306'),
    user:     process.env.DB_USER,
    password: process.env.DB_PASS,
    database: process.env.DB_NAME || 'pinkhorizon',
    waitForConnections: true,
    connectionLimit: 3,
  });
  return db;
}

// ─────────────────────────────────────────────────────────────────────────────
// Runtime state (channel/message IDs)
// ─────────────────────────────────────────────────────────────────────────────

const state = {
  statusChannelId:       null,
  statusMessageId:       null,
  memberCountChannelId:  null,
  ingameCountChannelId:  null,
};

// ─────────────────────────────────────────────────────────────────────────────
// Discord Client
// ─────────────────────────────────────────────────────────────────────────────

const client = new Client({
  intents: [
    GatewayIntentBits.Guilds,
    GatewayIntentBits.GuildMembers,
    GatewayIntentBits.GuildMessages,
  ],
});

// ─────────────────────────────────────────────────────────────────────────────
// Minecraft Ping
// ─────────────────────────────────────────────────────────────────────────────

async function ping(host, port) {
  try {
    const r = await pingJava(host, port, { timeout: 3000 });
    return { online: true, players: r.players?.online ?? 0, max: r.players?.max ?? 0 };
  } catch {
    return { online: false, players: 0, max: 0 };
  }
}

// ─────────────────────────────────────────────────────────────────────────────
// Status Embed
// ─────────────────────────────────────────────────────────────────────────────

async function buildStatusEmbed() {
  const proxy   = await ping(PROXY_HOST, PROXY_PORT);
  const results = await Promise.all(SERVERS.map(s => ping(s.host, s.port).then(r => ({ ...s, ...r }))));

  const timeStr = new Date().toLocaleTimeString('de-DE', { timeZone: 'Europe/Berlin' });
  const anyOnline = results.some(r => r.online);
  const color = proxy.online ? 0x57F287 : (anyOnline ? 0xFEE75C : 0xED4245);

  const serverField = results
    .map(s => `**${s.label}**\n${s.online ? `🟢 Online · \`${s.players}\` Spieler` : '🔴 Offline'}`)
    .join('\n\n');

  return new EmbedBuilder()
    .setTitle('🌐 Pink Horizon · Serverstatus')
    .setColor(color)
    .setDescription(`**\`${MC_ADDRESS}\`**`)
    .addFields(
      {
        name: '📡 Netzwerk',
        value: proxy.online
          ? `🟢 Online · **${proxy.players}** Spieler verbunden`
          : '🔴 Offline',
        inline: false,
      },
      {
        name: '🖥️ Server',
        value: serverField || '–',
        inline: false,
      },
    )
    .setFooter({ text: `Zuletzt geprüft: ${timeStr} Uhr · Aktualisierung alle 60s` })
    .setTimestamp();
}

// ─────────────────────────────────────────────────────────────────────────────
// Monitor (runs every 60 seconds)
// ─────────────────────────────────────────────────────────────────────────────

// Voice channel names are rate-limited by Discord (2 changes / 10 min per channel).
// We track the last update time to avoid hitting the limit.
const lastVoiceUpdate = { members: 0, ingame: 0 };
const VOICE_COOLDOWN = 10 * 60 * 1000; // 10 minutes

async function runMonitor(guild) {
  try {
    // ── Status Embed ──────────────────────────────────────────────────────
    const statusCh = state.statusChannelId ? guild.channels.cache.get(state.statusChannelId) : null;
    if (statusCh) {
      const embed = await buildStatusEmbed();
      if (state.statusMessageId) {
        try {
          const msg = await statusCh.messages.fetch(state.statusMessageId);
          await msg.edit({ embeds: [embed] });
        } catch {
          const msg = await statusCh.send({ embeds: [embed] });
          state.statusMessageId = msg.id;
        }
      } else {
        const msg = await statusCh.send({ embeds: [embed] });
        state.statusMessageId = msg.id;
      }
    }

    // ── Voice Stat Channels ───────────────────────────────────────────────
    const now = Date.now();

    if (state.memberCountChannelId && now - lastVoiceUpdate.members > VOICE_COOLDOWN) {
      const ch = guild.channels.cache.get(state.memberCountChannelId);
      if (ch) {
        await ch.setName(`👥 Mitglieder: ${guild.memberCount}`).catch(() => {});
        lastVoiceUpdate.members = now;
      }
    }

    if (state.ingameCountChannelId && now - lastVoiceUpdate.ingame > VOICE_COOLDOWN) {
      const proxy = await ping(PROXY_HOST, PROXY_PORT);
      const ch = guild.channels.cache.get(state.ingameCountChannelId);
      if (ch) {
        await ch.setName(`🎮 Ingame: ${proxy.players}`).catch(() => {});
        lastVoiceUpdate.ingame = now;
      }
    }

    // ── Bot Presence ──────────────────────────────────────────────────────
    const proxy = await ping(PROXY_HOST, PROXY_PORT);
    client.user.setActivity(
      proxy.online ? `${proxy.players} Spieler · ${MC_ADDRESS}` : 'Server offline',
      { type: proxy.online ? ActivityType.Watching : ActivityType.Listening },
    );
  } catch (e) {
    console.error('[Monitor]', e.message);
  }
}

let monitorInterval = null;

function startMonitor(guild) {
  if (monitorInterval) clearInterval(monitorInterval);
  runMonitor(guild);
  monitorInterval = setInterval(() => runMonitor(guild), 60_000);
}

// ─────────────────────────────────────────────────────────────────────────────
// Setup: Roles
// ─────────────────────────────────────────────────────────────────────────────

const ROLE_DEFS = [
  { name: 'Admin',          color: 0xFF3333, hoist: true,  mentionable: false },
  { name: 'Moderator',      color: 0xFF6600, hoist: true,  mentionable: false },
  { name: 'Supporter',      color: 0xFFAA00, hoist: true,  mentionable: true  },
  { name: 'Booster',        color: 0xFF73FA, hoist: false, mentionable: false },
  { name: 'Server-Updates', color: 0x5865F2, hoist: false, mentionable: true  },
  { name: 'Neuigkeiten',    color: 0x57F287, hoist: false, mentionable: true  },
  { name: 'Spieler',        color: 0xAAAAAA, hoist: false, mentionable: false },
];

async function ensureRoles(guild) {
  const created = {};
  for (const def of ROLE_DEFS) {
    let role = guild.roles.cache.find(r => r.name === def.name);
    if (!role) {
      role = await guild.roles.create({ name: def.name, color: def.color, hoist: def.hoist, mentionable: def.mentionable });
      await sleep(400);
    }
    created[def.name] = role;
  }
  return created;
}

// ─────────────────────────────────────────────────────────────────────────────
// Setup: Channels
// ─────────────────────────────────────────────────────────────────────────────

function buildChannelDefs() {
  return [
    {
      name: '📌 INFORMATION',
      teamOnly: false,
      children: [
        { name: 'regeln',        readonly: true  },
        { name: 'ankündigungen', readonly: true  },
        { name: 'changelog',     readonly: true  },
        { name: 'server-status', readonly: true,  tag: 'status' },
      ],
    },
    {
      name: '🌐 COMMUNITY',
      children: [
        { name: 'allgemein'  },
        { name: 'off-topic'  },
        { name: 'media'      },
        { name: 'vorschläge' },
      ],
    },
    {
      name: '🎮 SMASH THE BOSS',
      children: [
        { name: 'smash-allgemein' },
        { name: 'smash-tipps'     },
        { name: 'smash-top',      readonly: true },
      ],
    },
    {
      name: '⛏️ SURVIVAL',
      children: [
        { name: 'survival-allgemein' },
        { name: 'survival-handel'    },
      ],
    },
    {
      name: '🆘 SUPPORT',
      children: [
        { name: 'hilfe'       },
        { name: 'bug-reports' },
      ],
    },
    {
      name: '🔧 TEAM',
      teamOnly: true,
      children: [
        { name: 'team-allgemein' },
        { name: 'admin-logs'     },
      ],
    },
    {
      name: '📊 STATISTIKEN',
      children: [
        { name: '👥 Mitglieder: ...', voice: true, tag: 'members' },
        { name: '🎮 Ingame: ...',     voice: true, tag: 'ingame'  },
      ],
    },
  ];
}

async function ensureChannels(guild, roles) {
  const everyone  = guild.roles.everyone;
  const adminRole = roles['Admin'];
  const modRole   = roles['Moderator'];

  for (const catDef of buildChannelDefs()) {
    // ── Category ──────────────────────────────────────────────────────────
    const catPerms = catDef.teamOnly
      ? [
          { id: everyone.id,   deny:  [PermissionFlagsBits.ViewChannel] },
          { id: adminRole.id,  allow: [PermissionFlagsBits.ViewChannel] },
          { id: modRole.id,    allow: [PermissionFlagsBits.ViewChannel] },
        ]
      : [];

    let category = guild.channels.cache.find(
      c => c.name === catDef.name && c.type === ChannelType.GuildCategory,
    );
    if (!category) {
      category = await guild.channels.create({
        name: catDef.name,
        type: ChannelType.GuildCategory,
        permissionOverwrites: catPerms,
      });
      await sleep(400);
    }

    // ── Children ──────────────────────────────────────────────────────────
    for (const chDef of catDef.children) {
      if (!chDef.voice) {
        // Text channel – skip if already exists
        const exists = guild.channels.cache.find(
          c => c.name === chDef.name && c.parentId === category.id && c.type === ChannelType.GuildText,
        );
        if (exists) {
          if (chDef.tag === 'status') state.statusChannelId = exists.id;
          continue;
        }

        const perms = [];
        if (chDef.readonly) {
          perms.push({ id: everyone.id,  deny:  [PermissionFlagsBits.SendMessages] });
          perms.push({ id: adminRole.id, allow: [PermissionFlagsBits.SendMessages] });
          perms.push({ id: modRole.id,   allow: [PermissionFlagsBits.SendMessages] });
        }

        const ch = await guild.channels.create({
          name: chDef.name,
          type: ChannelType.GuildText,
          parent: category.id,
          permissionOverwrites: perms,
        });
        await sleep(400);

        if (chDef.tag === 'status') state.statusChannelId = ch.id;
      } else {
        // Voice stat channel – always recreate if tag exists
        const perms = [{ id: everyone.id, deny: [PermissionFlagsBits.Connect] }];
        const ch = await guild.channels.create({
          name: chDef.name,
          type: ChannelType.GuildVoice,
          parent: category.id,
          permissionOverwrites: perms,
        });
        await sleep(400);

        if (chDef.tag === 'members') state.memberCountChannelId = ch.id;
        if (chDef.tag === 'ingame')  state.ingameCountChannelId = ch.id;
      }
    }
  }
}

// ─────────────────────────────────────────────────────────────────────────────
// Setup: Default content
// ─────────────────────────────────────────────────────────────────────────────

const RULES_TEXT = [
  '**1.** Behandle alle Mitglieder respektvoll.',
  '**2.** Kein Spam, keine beleidigenden oder anstößigen Inhalte.',
  '**3.** Keine unaufgeforderte Werbung.',
  '**4.** Halte dich an die Discord-Nutzungsbedingungen.',
  '**5.** Cheaten, Exploiten oder Hacking auf dem Server ist verboten.',
  '**6.** Staff-Entscheidungen sind zu respektieren.',
  '**7.** Beleidigungen gegenüber dem Team werden nicht toleriert.',
  '',
  '*Bei Regelverstößen drohen Verwarnungen, Mutes oder Bans.*',
].join('\n');

async function postDefaultContent(guild) {
  // #regeln
  const regelnCh = guild.channels.cache.find(c => c.name === 'regeln' && c.type === ChannelType.GuildText);
  if (regelnCh) {
    const msgs = await regelnCh.messages.fetch({ limit: 5 }).catch(() => null);
    if (msgs && msgs.size === 0) {
      await regelnCh.send({
        embeds: [
          new EmbedBuilder()
            .setTitle('📜 Serverregeln · Pink Horizon')
            .setColor(0xFF3333)
            .setDescription(RULES_TEXT)
            .setFooter({ text: 'Pink Horizon · play.pinkhorizon.fun' }),
        ],
      });
    }
  }

  // #smash-tipps
  const tippsCh = guild.channels.cache.find(c => c.name === 'smash-tipps' && c.type === ChannelType.GuildText);
  if (tippsCh) {
    const msgs = await tippsCh.messages.fetch({ limit: 5 }).catch(() => null);
    if (msgs && msgs.size === 0) {
      await tippsCh.send({
        embeds: [
          new EmbedBuilder()
            .setTitle('⚔️ Smash the Boss – Tipps')
            .setColor(0xFF5555)
            .setDescription([
              '**Befehle:**',
              '`/stb join` – Betritt deine persönliche Arena',
              '`/stb leave` – Verlasse die Arena',
              '`/stb shop` – Kaufe Upgrades',
              '`/stb afkfarm` – Starte die AFK-Farm',
              '`/stb prestige` – Prestige (ab Boss 100)',
              '',
              '**Tipps:**',
              '• Töte Bosse für AFK-Zeit und Belohnungen',
              '• Nutze Kombos für Schadensbonus',
              '• Upgrades und Talente verbessern deinen Schaden dauerhaft',
              '• Beim Prestige wird alles zurückgesetzt – dafür +50% Schaden',
            ].join('\n'))
            .setFooter({ text: 'Pink Horizon · play.pinkhorizon.fun' }),
        ],
      });
    }
  }
}

// ─────────────────────────────────────────────────────────────────────────────
// Full Setup Flow
// ─────────────────────────────────────────────────────────────────────────────

async function runSetup(guild, interaction) {
  try {
    await interaction.editReply('⚙️ **Setup gestartet** – Rollen werden erstellt...');
    const roles = await ensureRoles(guild);

    await interaction.editReply('⚙️ Rollen ✅ – Kanäle werden erstellt...');
    await ensureChannels(guild, roles);

    await interaction.editReply('⚙️ Kanäle ✅ – Standard-Inhalte werden gepostet...');
    await postDefaultContent(guild);

    await interaction.editReply('⚙️ Inhalte ✅ – Server-Monitor wird gestartet...');
    await guild.members.fetch();
    startMonitor(guild);

    await interaction.editReply([
      '✅ **Setup abgeschlossen!**',
      '',
      `• Rollen erstellt: ${ROLE_DEFS.length}`,
      '• Kategorien + Kanäle erstellt',
      '• Server-Monitor aktiv (60s Intervall)',
      '• Willkommens-System aktiv',
      '',
      `Tritt dem Server bei: \`${MC_ADDRESS}\``,
    ].join('\n'));
  } catch (e) {
    console.error('[Setup] Fehler:', e);
    await interaction.editReply(`❌ Setup-Fehler: ${e.message}`);
  }
}

// ─────────────────────────────────────────────────────────────────────────────
// Slash Commands definition
// ─────────────────────────────────────────────────────────────────────────────

const COMMANDS = [
  new SlashCommandBuilder()
    .setName('setup')
    .setDescription('Erstellt alle Kanäle, Rollen und startet den Monitor (nur Admin)')
    .setDefaultMemberPermissions(PermissionFlagsBits.Administrator),

  new SlashCommandBuilder()
    .setName('status')
    .setDescription('Zeigt den aktuellen Minecraft-Serverstatus'),

  new SlashCommandBuilder()
    .setName('stats')
    .setDescription('Zeigt Smash-the-Boss Statistiken eines Spielers')
    .addStringOption(o =>
      o.setName('spieler')
       .setDescription('Minecraft-Name des Spielers')
       .setRequired(true)
    ),
].map(c => c.toJSON());

// ─────────────────────────────────────────────────────────────────────────────
// Events
// ─────────────────────────────────────────────────────────────────────────────

client.once('ready', async () => {
  console.log(`[Bot] Eingeloggt als ${client.user.tag}`);

  // Register slash commands
  const rest = new REST({ version: '10' }).setToken(TOKEN);
  try {
    await rest.put(Routes.applicationGuildCommands(CLIENT_ID, GUILD_ID), { body: COMMANDS });
    console.log('[Bot] Slash-Commands registriert.');
  } catch (e) {
    console.error('[Bot] Command-Registrierung fehlgeschlagen:', e.message);
  }

  client.user.setActivity('Pink Horizon wird geladen...', { type: ActivityType.Watching });

  // Auto-start monitor
  const guild = client.guilds.cache.get(GUILD_ID);
  if (guild) {
    await guild.members.fetch().catch(() => {});

    // Try to find existing status channel
    const statusCh = guild.channels.cache.find(c => c.name === 'server-status');
    if (statusCh) state.statusChannelId = statusCh.id;
    const membersCh = guild.channels.cache.find(c => c.name.startsWith('👥 Mitglieder'));
    if (membersCh) state.memberCountChannelId = membersCh.id;
    const ingameCh = guild.channels.cache.find(c => c.name.startsWith('🎮 Ingame'));
    if (ingameCh) state.ingameCountChannelId = ingameCh.id;

    startMonitor(guild);
  }
});

// Auto-assign @Spieler role + welcome message
client.on('guildMemberAdd', async member => {
  const spielerRole = member.guild.roles.cache.find(r => r.name === 'Spieler');
  if (spielerRole) await member.roles.add(spielerRole).catch(() => {});

  const allgemeinCh = member.guild.channels.cache.find(c => c.name === 'allgemein' && c.type === ChannelType.GuildText);
  const regelnCh    = member.guild.channels.cache.find(c => c.name === 'regeln'    && c.type === ChannelType.GuildText);

  if (allgemeinCh) {
    const rulesHint = regelnCh ? ` Bitte lies dir die <#${regelnCh.id}> durch.` : '';
    await allgemeinCh.send(
      `🎉 Willkommen auf dem **Pink Horizon** Discord, ${member}!${rulesHint}\nTritt dem Server bei: \`${MC_ADDRESS}\``,
    ).catch(() => {});
  }
});

// Slash command handler
client.on('interactionCreate', async interaction => {
  if (!interaction.isChatInputCommand()) return;
  if (interaction.guildId !== GUILD_ID) return;

  await interaction.deferReply();

  const { commandName, guild } = interaction;

  // ── /setup ──────────────────────────────────────────────────────────────
  if (commandName === 'setup') {
    await runSetup(guild, interaction);

  // ── /status ─────────────────────────────────────────────────────────────
  } else if (commandName === 'status') {
    const embed = await buildStatusEmbed();
    await interaction.editReply({ embeds: [embed] });

  // ── /stats ──────────────────────────────────────────────────────────────
  } else if (commandName === 'stats') {
    const name = interaction.options.getString('spieler');
    const pool = await getDb();

    if (!pool) {
      await interaction.editReply('❌ Datenbank nicht konfiguriert.');
      return;
    }

    try {
      const [rows] = await pool.query(
        `SELECT
           pd.boss_level,
           pd.kills,
           pd.total_damage,
           COALESCE(sc.amount, 0)   AS coins,
           COALESCE(pr.prestige, 0) AS prestige
         FROM smash_players pd
         LEFT JOIN smash_coins     sc ON sc.uuid = pd.uuid
         LEFT JOIN smash_prestige  pr ON pr.uuid = pd.uuid
         WHERE pd.name = ?
         LIMIT 1`,
        [name],
      );

      if (!rows.length) {
        await interaction.editReply(`❌ Spieler \`${name}\` nicht gefunden.`);
        return;
      }

      const r = rows[0];
      const embed = new EmbedBuilder()
        .setTitle(`⚔️ Smash Stats · ${name}`)
        .setColor(0xFF5555)
        .addFields(
          { name: '🎯 Boss Level',      value: String(r.boss_level),       inline: true },
          { name: '☠️ Boss Kills',      value: String(r.kills),            inline: true },
          { name: '✦ Prestige',         value: String(r.prestige),         inline: true },
          { name: '⚡ Gesamtschaden',   value: formatDmg(r.total_damage),  inline: true },
          { name: '💰 Münzen',          value: String(r.coins),            inline: true },
        )
        .setFooter({ text: 'Pink Horizon · Smash the Boss · play.pinkhorizon.fun' });

      await interaction.editReply({ embeds: [embed] });
    } catch (e) {
      console.error('[/stats]', e.message);
      await interaction.editReply('❌ Datenbankfehler: ' + e.message);
    }
  }
});

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

function formatDmg(dmg) {
  if (dmg >= 1_000_000_000) return (dmg / 1_000_000_000).toFixed(2) + 'B';
  if (dmg >= 1_000_000)     return (dmg / 1_000_000).toFixed(2) + 'M';
  if (dmg >= 1_000)         return (dmg / 1_000).toFixed(1) + 'K';
  return String(dmg);
}

function sleep(ms) {
  return new Promise(resolve => setTimeout(resolve, ms));
}

// ─────────────────────────────────────────────────────────────────────────────
// Start
// ─────────────────────────────────────────────────────────────────────────────

if (!TOKEN || !CLIENT_ID || !GUILD_ID) {
  console.error('❌ Fehlende Umgebungsvariablen: DISCORD_TOKEN, DISCORD_CLIENT_ID, DISCORD_GUILD_ID');
  process.exit(1);
}

client.login(TOKEN).catch(e => {
  console.error('❌ Login fehlgeschlagen:', e.message);
  process.exit(1);
});
