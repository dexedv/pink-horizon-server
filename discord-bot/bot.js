/**
 * Pink Horizon – Discord Community Bot
 *
 * Features:
 *  - /setup         – erstellt alle Rollen, Kategorien & Kanäle
 *  - /status        – zeigt aktuellen Serverstatus
 *  - /stats         – zeigt Smash-Statistiken eines Spielers
 *  - /ticket-panel  – postet das Ticket-Panel (Admin)
 *  - Ticket-System  – Erstellen, Claimen, Schließen + Transcript
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
  ActionRowBuilder,
  ButtonBuilder,
  ButtonStyle,
  StringSelectMenuBuilder,
  StringSelectMenuOptionBuilder,
  REST,
  Routes,
} = require('discord.js');

const { pingJava } = require('minecraft-server-util');
const Dockerode   = require('dockerode');
const mysql       = require('mysql2/promise');

const docker = new Dockerode({ socketPath: '/var/run/docker.sock' });

// ─────────────────────────────────────────────────────────────────────────────
// Config
// ─────────────────────────────────────────────────────────────────────────────

const TOKEN      = process.env.DISCORD_TOKEN;
const CLIENT_ID  = process.env.DISCORD_CLIENT_ID;
const GUILD_ID   = process.env.DISCORD_GUILD_ID;
const MC_ADDRESS = process.env.MC_ADDRESS || 'play.pinkhorizon.fun';

const SERVERS = [
  { label: '🎮 Smash the Boss', container: 'ph-smash'    },
  { label: '⛏️ Survival',       container: 'ph-survival' },
  { label: '🏠 Lobby',          container: 'ph-lobby'    },
];
const PROXY_HOST = process.env.PROXY_HOST || 'velocity';
const PROXY_PORT = parseInt(process.env.PROXY_PORT || '25565');

// ─────────────────────────────────────────────────────────────────────────────
// Database
// ─────────────────────────────────────────────────────────────────────────────

let db = null;
async function getDb() {
  if (db) return db;
  if (!process.env.DB_HOST) return null;
  db = mysql.createPool({
    host: process.env.DB_HOST, port: parseInt(process.env.DB_PORT || '3306'),
    user: process.env.DB_USER, password: process.env.DB_PASS,
    database: process.env.DB_NAME || 'pinkhorizon',
    waitForConnections: true, connectionLimit: 3,
  });
  return db;
}

// ─────────────────────────────────────────────────────────────────────────────
// Runtime state
// ─────────────────────────────────────────────────────────────────────────────

const state = {
  statusChannelId:      null,
  statusMessageId:      null,
  memberCountChannelId: null,
  ingameCountChannelId: null,
};

// ticket channel id → { userId, userName, category, number, claimedBy }
const tickets = new Map();

// ─────────────────────────────────────────────────────────────────────────────
// Discord Client
// ─────────────────────────────────────────────────────────────────────────────

const client = new Client({
  intents: [
    GatewayIntentBits.Guilds,
    GatewayIntentBits.GuildMembers,
    GatewayIntentBits.GuildMessages,
    GatewayIntentBits.MessageContent,
  ],
});

// ─────────────────────────────────────────────────────────────────────────────
// Docker + Minecraft Status
// ─────────────────────────────────────────────────────────────────────────────

async function isContainerRunning(name) {
  try {
    const info = await docker.getContainer(name).inspect();
    return info.State.Running === true;
  } catch {
    return false;
  }
}

async function ping(host, port) {
  try {
    const r = await pingJava(host, port, { timeout: 4000 });
    return { online: true, players: r.players?.online ?? 0, max: r.players?.max ?? 0 };
  } catch {
    return { online: false, players: 0, max: 0 };
  }
}

// ─────────────────────────────────────────────────────────────────────────────
// Status Embed
// ─────────────────────────────────────────────────────────────────────────────

async function buildStatusEmbed() {
  // Container-Status via Docker Socket (zuverlässig)
  const serverResults = await Promise.all(
    SERVERS.map(async s => ({ ...s, running: await isContainerRunning(s.container) }))
  );
  // Spielerzahl via Proxy-Ping
  const proxy = await ping(PROXY_HOST, PROXY_PORT);

  const timeStr   = new Date().toLocaleTimeString('de-DE', { timeZone: 'Europe/Berlin' });
  const anyOnline = serverResults.some(r => r.running);
  const color     = anyOnline ? 0x57F287 : 0xED4245;

  const serverField = serverResults
    .map(s => `**${s.label}**\n${s.running ? '🟢 Online' : '🔴 Offline'}`)
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
          : (anyOnline ? '🟡 Proxy startet...' : '🔴 Offline'),
        inline: false,
      },
      { name: '🖥️ Server', value: serverField || '–', inline: false },
    )
    .setFooter({ text: `Zuletzt geprüft: ${timeStr} Uhr · Aktualisierung alle 60s` })
    .setTimestamp();
}

// ─────────────────────────────────────────────────────────────────────────────
// Monitor
// ─────────────────────────────────────────────────────────────────────────────

const lastVoiceUpdate = { members: 0, ingame: 0 };
const VOICE_COOLDOWN  = 10 * 60 * 1000;
let monitorInterval   = null;

async function runMonitor(guild) {
  try {
    // Status embed
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

    // Voice stat channels (rate-limited: max 2 per 10 min)
    const now = Date.now();
    if (state.memberCountChannelId && now - lastVoiceUpdate.members > VOICE_COOLDOWN) {
      const ch = guild.channels.cache.get(state.memberCountChannelId);
      if (ch) { await ch.setName(`👥 Mitglieder: ${guild.memberCount}`).catch(() => {}); lastVoiceUpdate.members = now; }
    }
    if (state.ingameCountChannelId && now - lastVoiceUpdate.ingame > VOICE_COOLDOWN) {
      const proxyPing = await ping(PROXY_HOST, PROXY_PORT);
      const ch = guild.channels.cache.get(state.ingameCountChannelId);
      if (ch) { await ch.setName(`🎮 Ingame: ${proxyPing.players}`).catch(() => {}); lastVoiceUpdate.ingame = now; }
    }

    // Bot presence
    const proxy = await ping(PROXY_HOST, PROXY_PORT);
    client.user.setActivity(
      proxy.online ? `${proxy.players} Spieler · ${MC_ADDRESS}` : 'Server offline',
      { type: proxy.online ? ActivityType.Watching : ActivityType.Listening },
    );
  } catch (e) { console.error('[Monitor]', e.message); }
}

function startMonitor(guild) {
  if (monitorInterval) clearInterval(monitorInterval);
  runMonitor(guild);
  monitorInterval = setInterval(() => runMonitor(guild), 60_000);
}

// ─────────────────────────────────────────────────────────────────────────────
// Ticket System
// ─────────────────────────────────────────────────────────────────────────────

const TICKET_CATEGORIES = [
  { id: 'help',  label: '🔧 Allgemeine Hilfe', description: 'Allgemeine Fragen & Hilfe' },
  { id: 'bug',   label: '🐛 Bug Report',       description: 'Einen Fehler melden'       },
  { id: 'apply', label: '📝 Bewerbung',         description: 'Als Staff bewerben'        },
  { id: 'other', label: '❓ Sonstiges',          description: 'Andere Anliegen'           },
];

function ticketPanelEmbed() {
  return new EmbedBuilder()
    .setTitle('📩 Pink Horizon Support')
    .setColor(0x5865F2)
    .setDescription([
      'Brauchst du Hilfe oder hast du ein Anliegen?',
      '',
      'Klicke auf **Ticket öffnen**, wähle eine Kategorie und das Support-Team meldet sich bei dir.',
      '',
      '> **Reaktionszeit:** So schnell wie möglich',
      '> **Sprache:** Deutsch / Englisch',
    ].join('\n'))
    .setFooter({ text: 'Pink Horizon · play.pinkhorizon.fun' });
}

function ticketPanelRow() {
  return new ActionRowBuilder().addComponents(
    new ButtonBuilder()
      .setCustomId('ticket_open')
      .setLabel('📩 Ticket öffnen')
      .setStyle(ButtonStyle.Primary),
  );
}

function getNextTicketNumber(guild) {
  let max = 0;
  for (const ch of guild.channels.cache.values()) {
    const m = ch.name.match(/^ticket-(\d+)$/);
    if (m) max = Math.max(max, parseInt(m[1]));
  }
  return max + 1;
}

function isStaff(member) {
  return member.roles.cache.some(r => ['Admin', 'Moderator', 'Supporter'].includes(r.name));
}

async function openTicketCategory(interaction) {
  const select = new StringSelectMenuBuilder()
    .setCustomId('ticket_category')
    .setPlaceholder('Wähle eine Kategorie...')
    .addOptions(TICKET_CATEGORIES.map(c =>
      new StringSelectMenuOptionBuilder()
        .setLabel(c.label)
        .setDescription(c.description)
        .setValue(c.id),
    ));

  await interaction.reply({
    content: '**Bitte wähle eine Kategorie für dein Ticket:**',
    components: [new ActionRowBuilder().addComponents(select)],
    ephemeral: true,
  });
}

async function createTicket(interaction, categoryId) {
  const guild    = interaction.guild;
  const user     = interaction.user;
  const member   = interaction.member;
  const category = TICKET_CATEGORIES.find(c => c.id === categoryId);

  // Only one open ticket per user
  const existing = guild.channels.cache.find(ch =>
    ch.name.startsWith('ticket-') && tickets.get(ch.id)?.userId === user.id,
  );
  if (existing) {
    await interaction.update({ content: `❌ Du hast bereits ein offenes Ticket: ${existing}`, components: [], ephemeral: true });
    return;
  }

  await interaction.update({ content: '⏳ Ticket wird erstellt...', components: [], ephemeral: true });

  const num       = String(getNextTicketNumber(guild)).padStart(4, '0');
  const modRole   = guild.roles.cache.find(r => r.name === 'Moderator');
  const suppRole  = guild.roles.cache.find(r => r.name === 'Supporter');
  const adminRole = guild.roles.cache.find(r => r.name === 'Admin');
  const supportCat = guild.channels.cache.find(c => c.name === '🆘 SUPPORT' && c.type === ChannelType.GuildCategory);

  const perms = [
    { id: guild.roles.everyone.id, deny: [PermissionFlagsBits.ViewChannel] },
    { id: user.id, allow: [PermissionFlagsBits.ViewChannel, PermissionFlagsBits.SendMessages, PermissionFlagsBits.ReadMessageHistory, PermissionFlagsBits.AttachFiles] },
  ];
  if (adminRole) perms.push({ id: adminRole.id, allow: [PermissionFlagsBits.ViewChannel, PermissionFlagsBits.SendMessages, PermissionFlagsBits.ManageMessages, PermissionFlagsBits.ReadMessageHistory] });
  if (modRole)   perms.push({ id: modRole.id,   allow: [PermissionFlagsBits.ViewChannel, PermissionFlagsBits.SendMessages, PermissionFlagsBits.ManageMessages, PermissionFlagsBits.ReadMessageHistory] });
  if (suppRole)  perms.push({ id: suppRole.id,  allow: [PermissionFlagsBits.ViewChannel, PermissionFlagsBits.SendMessages, PermissionFlagsBits.ReadMessageHistory] });

  const channel = await guild.channels.create({
    name: `ticket-${num}`,
    type: ChannelType.GuildText,
    parent: supportCat?.id,
    topic: `${user.id} | ${category.label} | #${num}`,
    permissionOverwrites: perms,
  });

  tickets.set(channel.id, { userId: user.id, userName: user.tag, category: category.label, number: num, claimedBy: null });

  const embed = new EmbedBuilder()
    .setTitle(`📩 Ticket #${num} · ${category.label}`)
    .setColor(0x5865F2)
    .setDescription([
      `Willkommen ${user}! 👋`,
      '',
      'Das Support-Team meldet sich so schnell wie möglich bei dir.',
      '**Beschreibe dein Anliegen so genau wie möglich.**',
    ].join('\n'))
    .addFields(
      { name: '👤 Erstellt von', value: `${user}`,          inline: true },
      { name: '📂 Kategorie',   value: category.label,      inline: true },
      { name: '🔢 Ticket-Nr.',  value: `#${num}`,           inline: true },
      { name: '🛡️ Status',      value: '🟡 Offen · Warte auf Staff', inline: false },
    )
    .setTimestamp()
    .setFooter({ text: 'Pink Horizon Support · Ticket öffnen jederzeit möglich' });

  const pingText = [modRole, suppRole].filter(Boolean).map(r => `${r}`).join(' ');

  const row = new ActionRowBuilder().addComponents(
    new ButtonBuilder().setCustomId('ticket_claim').setLabel('✋ Übernehmen').setStyle(ButtonStyle.Success),
    new ButtonBuilder().setCustomId('ticket_close').setLabel('🔒 Schließen').setStyle(ButtonStyle.Danger),
  );

  await channel.send({ content: `${user} ${pingText}`, embeds: [embed], components: [row] });
  await interaction.editReply({ content: `✅ Dein Ticket wurde erstellt: ${channel}`, components: [] });
}

async function claimTicket(interaction) {
  const channel = interaction.channel;
  const data    = tickets.get(channel.id);
  if (!data) return interaction.reply({ content: '❌ Kein Ticket-Kanal.', ephemeral: true });
  if (!isStaff(interaction.member)) return interaction.reply({ content: '❌ Nur Staff kann ein Ticket übernehmen.', ephemeral: true });
  if (data.claimedBy) return interaction.reply({ content: `❌ Bereits übernommen von <@${data.claimedBy}>.`, ephemeral: true });

  data.claimedBy = interaction.user.id;

  // Update button row: disable claim, show who claimed
  const row = new ActionRowBuilder().addComponents(
    new ButtonBuilder().setCustomId('ticket_claim').setLabel(`✅ ${interaction.user.username}`).setStyle(ButtonStyle.Secondary).setDisabled(true),
    new ButtonBuilder().setCustomId('ticket_close').setLabel('🔒 Schließen').setStyle(ButtonStyle.Danger),
  );

  await interaction.update({ components: [row] });

  await channel.send({
    embeds: [
      new EmbedBuilder()
        .setColor(0x57F287)
        .setDescription(`✋ **${interaction.user}** hat dieses Ticket übernommen.`),
    ],
  });
}

async function closeTicket(interaction) {
  const channel = interaction.channel;
  const data    = tickets.get(channel.id);
  if (!data) return interaction.reply({ content: '❌ Kein Ticket-Kanal.', ephemeral: true });

  // Only staff or the ticket creator can close
  if (!isStaff(interaction.member) && interaction.user.id !== data.userId) {
    return interaction.reply({ content: '❌ Nur der Ersteller oder Staff kann das Ticket schließen.', ephemeral: true });
  }

  await interaction.reply({
    embeds: [new EmbedBuilder().setColor(0xED4245).setDescription('🔒 Ticket wird geschlossen und Transcript gespeichert...')],
  });

  // Build transcript
  const messages = await channel.messages.fetch({ limit: 100 }).catch(() => null);
  let transcriptLines = [];
  if (messages) {
    transcriptLines = [...messages.values()]
      .reverse()
      .map(m => `[${m.createdAt.toLocaleTimeString('de-DE')}] ${m.author.tag}: ${m.content || '[Anhang/Embed]'}`);
  }
  const transcript = transcriptLines.join('\n').slice(0, 4000) || '(leer)';

  // Post to admin-logs
  const logCh = interaction.guild.channels.cache.find(c => c.name === 'admin-logs' && c.type === ChannelType.GuildText);
  if (logCh) {
    await logCh.send({
      embeds: [
        new EmbedBuilder()
          .setTitle(`📋 Ticket #${data.number} geschlossen`)
          .setColor(0xED4245)
          .addFields(
            { name: '👤 Ersteller',         value: `<@${data.userId}> (${data.userName})`, inline: true },
            { name: '📂 Kategorie',          value: data.category,                          inline: true },
            { name: '🛡️ Geschlossen von',   value: `${interaction.user}`,                  inline: true },
            { name: data.claimedBy ? '✋ Bearbeitet von' : '⚠️ Bearbeitet von', value: data.claimedBy ? `<@${data.claimedBy}>` : 'Niemand', inline: true },
          )
          .setTimestamp()
          .setFooter({ text: `Ticket #${data.number} · Pink Horizon Support` }),
      ],
    });
    // Send transcript as code block if not empty
    if (transcriptLines.length > 0) {
      const chunks = splitIntoChunks(transcript, 1900);
      for (const chunk of chunks) {
        await logCh.send({ content: `\`\`\`\n${chunk}\n\`\`\`` });
      }
    }
  }

  tickets.delete(channel.id);
  await sleep(3000);
  await channel.delete(`Ticket #${data.number} geschlossen von ${interaction.user.tag}`).catch(() => {});
}

function splitIntoChunks(str, size) {
  const chunks = [];
  for (let i = 0; i < str.length; i += size) chunks.push(str.slice(i, i + size));
  return chunks;
}

// ─────────────────────────────────────────────────────────────────────────────
// Setup: Roles
// ─────────────────────────────────────────────────────────────────────────────

const ROLE_DEFS = [
  { name: 'Admin',          color: 0xFF3333, hoist: true,  mentionable: false },
  { name: 'Moderator',      color: 0xFF6600, hoist: true,  mentionable: false },
  { name: 'Supporter',      color: 0xFFAA00, hoist: true,  mentionable: true  },
  { name: 'Booster',        color: 0xFF73FA, hoist: false, mentionable: false },
  { name: 'Verifiziert',    color: 0x57F287, hoist: false, mentionable: false },
  { name: 'Server-Updates', color: 0x5865F2, hoist: false, mentionable: true  },
  { name: 'Neuigkeiten',    color: 0x2ECC71, hoist: false, mentionable: true  },
  { name: 'Spieler',        color: 0xAAAAAA, hoist: false, mentionable: false },
];

async function ensureRoles(guild) {
  const created = {};
  for (const def of ROLE_DEFS) {
    let role = guild.roles.cache.find(r => r.name === def.name);
    if (!role) { role = await guild.roles.create({ name: def.name, color: def.color, hoist: def.hoist, mentionable: def.mentionable }); await sleep(400); }
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
      name: '📌 INFORMATION', teamOnly: false,
      children: [
        { name: 'regeln',        readonly: true, public: true   }, // sichtbar ohne Verifizierung
        { name: 'ankündigungen', readonly: true                 },
        { name: 'changelog',     readonly: true                 },
        { name: 'server-status', readonly: true, tag: 'status'  },
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
        { name: 'ticket-erstellen', readonly: true, tag: 'tickets' },
        { name: 'hilfe'       },
        { name: 'bug-reports' },
      ],
    },
    {
      name: '🔧 TEAM', teamOnly: true,
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
  const everyone     = guild.roles.everyone;
  const adminRole    = roles['Admin'];
  const modRole      = roles['Moderator'];
  const suppRole     = roles['Supporter'];
  const verifRole    = roles['Verifiziert'];
  const created      = {};

  for (const catDef of buildChannelDefs()) {
    // Kategorie-Permissions:
    // teamOnly  → nur Admin/Mod sehen
    // sonst     → @everyone geblockt, Verifiziert + Staff sehen
    let catPerms;
    if (catDef.teamOnly) {
      catPerms = [
        { id: everyone.id,  deny:  [PermissionFlagsBits.ViewChannel] },
        { id: adminRole.id, allow: [PermissionFlagsBits.ViewChannel] },
        { id: modRole.id,   allow: [PermissionFlagsBits.ViewChannel] },
      ];
    } else {
      catPerms = [
        { id: everyone.id,  deny:  [PermissionFlagsBits.ViewChannel] },
        { id: verifRole.id, allow: [PermissionFlagsBits.ViewChannel] },
        { id: adminRole.id, allow: [PermissionFlagsBits.ViewChannel] },
        { id: modRole.id,   allow: [PermissionFlagsBits.ViewChannel] },
        { id: suppRole.id,  allow: [PermissionFlagsBits.ViewChannel] },
      ];
    }

    let category = guild.channels.cache.find(c => c.name === catDef.name && c.type === ChannelType.GuildCategory);
    if (!category) { category = await guild.channels.create({ name: catDef.name, type: ChannelType.GuildCategory, permissionOverwrites: catPerms }); await sleep(400); }

    for (const chDef of catDef.children) {
      if (!chDef.voice) {
        const exists = guild.channels.cache.find(c => c.name === chDef.name && c.parentId === category.id && c.type === ChannelType.GuildText);
        if (exists) { if (chDef.tag) created[chDef.tag] = exists.id; continue; }

        const perms = [];
        if (chDef.public) {
          // #regeln: für alle sichtbar (überschreibt Kategorie-Deny), aber kein Schreiben
          perms.push({ id: everyone.id,  allow: [PermissionFlagsBits.ViewChannel] });
          perms.push({ id: everyone.id,  deny:  [PermissionFlagsBits.SendMessages] });
          perms.push({ id: adminRole.id, allow: [PermissionFlagsBits.SendMessages] });
          perms.push({ id: modRole.id,   allow: [PermissionFlagsBits.SendMessages] });
        } else if (chDef.readonly) {
          perms.push({ id: everyone.id,  deny:  [PermissionFlagsBits.SendMessages] });
          perms.push({ id: adminRole.id, allow: [PermissionFlagsBits.SendMessages] });
          perms.push({ id: modRole.id,   allow: [PermissionFlagsBits.SendMessages] });
        }

        const ch = await guild.channels.create({ name: chDef.name, type: ChannelType.GuildText, parent: category.id, permissionOverwrites: perms });
        await sleep(400);
        if (chDef.tag) created[chDef.tag] = ch.id;
      } else {
        const perms = [{ id: everyone.id, deny: [PermissionFlagsBits.Connect] }];
        const ch = await guild.channels.create({ name: chDef.name, type: ChannelType.GuildVoice, parent: category.id, permissionOverwrites: perms });
        await sleep(400);
        if (chDef.tag) created[chDef.tag] = ch.id;
      }
    }
  }
  return created;
}

// ─────────────────────────────────────────────────────────────────────────────
// Setup: Default content
// ─────────────────────────────────────────────────────────────────────────────

async function postDefaultContent(guild, createdChannels) {
  // #regeln + Verifikations-Button
  const regelnCh = guild.channels.cache.find(c => c.name === 'regeln' && c.type === ChannelType.GuildText);
  if (regelnCh) {
    const msgs = await regelnCh.messages.fetch({ limit: 5 }).catch(() => null);
    if (msgs?.size === 0) {
      await regelnCh.send({ embeds: [new EmbedBuilder()
        .setTitle('📜 Serverregeln · Pink Horizon').setColor(0xFF3333)
        .setDescription([
          '**1.** Behandle alle Mitglieder respektvoll.',
          '**2.** Kein Spam, keine beleidigenden oder anstößigen Inhalte.',
          '**3.** Keine unaufgeforderte Werbung.',
          '**4.** Halte dich an die Discord-Nutzungsbedingungen.',
          '**5.** Cheaten, Exploiten oder Hacking auf dem Server ist verboten.',
          '**6.** Staff-Entscheidungen sind zu respektieren.',
          '**7.** Beleidigungen gegenüber dem Team werden nicht toleriert.',
          '',
          '*Bei Regelverstößen drohen Verwarnungen, Mutes oder Bans.*',
        ].join('\n'))
        .setFooter({ text: 'Pink Horizon · play.pinkhorizon.fun' })] });

      // Verifikations-Button direkt nach den Regeln
      await regelnCh.send({
        embeds: [new EmbedBuilder()
          .setColor(0x57F287)
          .setDescription('✅ **Mit dem Klick auf den Button bestätigst du, dass du die Regeln gelesen hast und ihnen zustimmst.**\nDanach erhältst du Zugriff auf alle Kanäle.')],
        components: [new ActionRowBuilder().addComponents(
          new ButtonBuilder()
            .setCustomId('verify')
            .setLabel('✅ Regeln akzeptieren & Verifizieren')
            .setStyle(ButtonStyle.Success),
        )],
      });
    }
  }

  // #smash-tipps
  const tippsCh = guild.channels.cache.find(c => c.name === 'smash-tipps' && c.type === ChannelType.GuildText);
  if (tippsCh) {
    const msgs = await tippsCh.messages.fetch({ limit: 5 }).catch(() => null);
    if (msgs?.size === 0) {
      await tippsCh.send({ embeds: [new EmbedBuilder()
        .setTitle('⚔️ Smash the Boss – Tipps').setColor(0xFF5555)
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
        .setFooter({ text: 'Pink Horizon · play.pinkhorizon.fun' })] });
    }
  }

  // #ticket-erstellen – Ticket-Panel
  const ticketChId = createdChannels?.tickets || state.ticketsChannelId;
  const ticketCh   = ticketChId ? guild.channels.cache.get(ticketChId) : guild.channels.cache.find(c => c.name === 'ticket-erstellen');
  if (ticketCh) {
    const msgs = await ticketCh.messages.fetch({ limit: 5 }).catch(() => null);
    if (msgs?.size === 0) {
      await ticketCh.send({ embeds: [ticketPanelEmbed()], components: [ticketPanelRow()] });
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
    const created = await ensureChannels(guild, roles);

    if (created.status)  state.statusChannelId      = created.status;
    if (created.members) state.memberCountChannelId  = created.members;
    if (created.ingame)  state.ingameCountChannelId  = created.ingame;

    await interaction.editReply('⚙️ Kanäle ✅ – Standard-Inhalte werden gepostet...');
    await postDefaultContent(guild, created);

    await interaction.editReply('⚙️ Inhalte ✅ – Server-Monitor wird gestartet...');
    await guild.members.fetch();
    startMonitor(guild);

    await interaction.editReply([
      '✅ **Setup abgeschlossen!**',
      '',
      `• ${ROLE_DEFS.length} Rollen erstellt`,
      '• Kategorien + Kanäle erstellt',
      '• Ticket-Panel in #ticket-erstellen gepostet',
      '• Server-Monitor aktiv (60s Intervall)',
      '',
      `Tritt dem Server bei: \`${MC_ADDRESS}\``,
    ].join('\n'));
  } catch (e) {
    console.error('[Setup] Fehler:', e);
    await interaction.editReply(`❌ Setup-Fehler: ${e.message}`);
  }
}

// ─────────────────────────────────────────────────────────────────────────────
// Slash Commands
// ─────────────────────────────────────────────────────────────────────────────

const COMMANDS = [
  new SlashCommandBuilder()
    .setName('setup')
    .setDescription('Erstellt alle Kanäle, Rollen und startet den Monitor (Admin)')
    .setDefaultMemberPermissions(PermissionFlagsBits.Administrator),

  new SlashCommandBuilder()
    .setName('ticket-panel')
    .setDescription('Postet das Ticket-Panel in diesen Kanal (Admin)')
    .setDefaultMemberPermissions(PermissionFlagsBits.Administrator),

  new SlashCommandBuilder()
    .setName('status')
    .setDescription('Zeigt den aktuellen Minecraft-Serverstatus'),

  new SlashCommandBuilder()
    .setName('stats')
    .setDescription('Zeigt Smash-the-Boss Statistiken eines Spielers')
    .addStringOption(o => o.setName('spieler').setDescription('Minecraft-Name').setRequired(true)),
].map(c => c.toJSON());

// ─────────────────────────────────────────────────────────────────────────────
// Events
// ─────────────────────────────────────────────────────────────────────────────

client.once('ready', async () => {
  console.log(`[Bot] Eingeloggt als ${client.user.tag}`);

  const rest = new REST({ version: '10' }).setToken(TOKEN);
  try {
    await rest.put(Routes.applicationGuildCommands(CLIENT_ID, GUILD_ID), { body: COMMANDS });
    console.log('[Bot] Slash-Commands registriert.');
  } catch (e) { console.error('[Bot] Command-Registrierung:', e.message); }

  client.user.setActivity('Pink Horizon wird geladen...', { type: ActivityType.Watching });

  const guild = client.guilds.cache.get(GUILD_ID);
  if (guild) {
    await guild.members.fetch().catch(() => {});

    // Auto-find existing channels
    const statusCh  = guild.channels.cache.find(c => c.name === 'server-status');
    const membersCh = guild.channels.cache.find(c => c.name.startsWith('👥 Mitglieder'));
    const ingameCh  = guild.channels.cache.find(c => c.name.startsWith('🎮 Ingame'));
    if (statusCh)  state.statusChannelId      = statusCh.id;
    if (membersCh) state.memberCountChannelId  = membersCh.id;
    if (ingameCh)  state.ingameCountChannelId  = ingameCh.id;

    startMonitor(guild);
  }
});

// ─────────────────────────────────────────────────────────────────────────────
// Verifikation
// ─────────────────────────────────────────────────────────────────────────────

async function verifyMember(interaction) {
  const member    = interaction.member;
  const guild     = interaction.guild;
  const verifRole = guild.roles.cache.find(r => r.name === 'Verifiziert');

  if (!verifRole) {
    return interaction.reply({ content: '❌ Verifizierungs-Rolle nicht gefunden. Bitte kontaktiere einen Admin.', ephemeral: true });
  }

  if (member.roles.cache.has(verifRole.id)) {
    return interaction.reply({ content: '✅ Du bist bereits verifiziert!', ephemeral: true });
  }

  await member.roles.add(verifRole).catch(() => {});

  // Spieler-Rolle ebenfalls vergeben
  const spielerRole = guild.roles.cache.find(r => r.name === 'Spieler');
  if (spielerRole) await member.roles.add(spielerRole).catch(() => {});

  // Willkommensnachricht
  const welcomeCh = guild.channels.cache.get('1497213734555226182');
  const regelnCh  = guild.channels.cache.find(c => c.name === 'regeln' && c.type === ChannelType.GuildText);
  if (welcomeCh) {
    const rulesHint = regelnCh ? `` : '';
    await welcomeCh.send({
      embeds: [new EmbedBuilder()
        .setColor(0x57F287)
        .setDescription(`🎉 **Willkommen auf Pink Horizon, ${member}!**\nDu hast die Regeln akzeptiert und hast jetzt Zugriff auf alle Kanäle.\n\n🎮 Tritt dem Server bei: \`${MC_ADDRESS}\``)
        .setTimestamp()],
    }).catch(() => {});
  }

  await interaction.reply({
    embeds: [new EmbedBuilder()
      .setColor(0x57F287)
      .setDescription('✅ **Verifiziert!** Du hast jetzt Zugriff auf alle Kanäle.\nViel Spaß auf Pink Horizon! 🎮')],
    ephemeral: true,
  });
}

// Beim Join: nur Hinweis im #regeln (kein Auto-Zugriff)
client.on('guildMemberAdd', async member => {
  const regelnCh = member.guild.channels.cache.find(c => c.name === 'regeln' && c.type === ChannelType.GuildText);
  if (regelnCh) {
    await regelnCh.send({
      embeds: [new EmbedBuilder()
        .setColor(0x5865F2)
        .setDescription(`👋 ${member} ist dem Server beigetreten! Bitte lies die Regeln und klicke auf **Verifizieren** um Zugriff zu erhalten.`)],
    }).catch(() => {});
  }
});

client.on('interactionCreate', async interaction => {
  if (interaction.guildId !== GUILD_ID) return;

  // ── Slash Commands ──────────────────────────────────────────────────────
  if (interaction.isChatInputCommand()) {
    await interaction.deferReply();
    const { commandName, guild } = interaction;

    if (commandName === 'setup') {
      await runSetup(guild, interaction);

    } else if (commandName === 'ticket-panel') {
      await interaction.channel.send({ embeds: [ticketPanelEmbed()], components: [ticketPanelRow()] });
      await interaction.editReply({ content: '✅ Ticket-Panel gepostet.', ephemeral: true });

    } else if (commandName === 'status') {
      await interaction.editReply({ embeds: [await buildStatusEmbed()] });

    } else if (commandName === 'stats') {
      const name = interaction.options.getString('spieler');
      const pool = await getDb();
      if (!pool) { await interaction.editReply('❌ Datenbank nicht konfiguriert.'); return; }
      try {
        const [rows] = await pool.query(
          `SELECT pd.boss_level, pd.kills, pd.total_damage,
                  COALESCE(sc.amount, 0) AS coins,
                  COALESCE(pr.prestige, 0) AS prestige
           FROM smash_players pd
           LEFT JOIN smash_coins    sc ON sc.uuid = pd.uuid
           LEFT JOIN smash_prestige pr ON pr.uuid = pd.uuid
           WHERE pd.name = ? LIMIT 1`, [name],
        );
        if (!rows.length) { await interaction.editReply(`❌ Spieler \`${name}\` nicht gefunden.`); return; }
        const r = rows[0];
        await interaction.editReply({ embeds: [new EmbedBuilder()
          .setTitle(`⚔️ Smash Stats · ${name}`).setColor(0xFF5555)
          .addFields(
            { name: '🎯 Boss Level',    value: String(r.boss_level),      inline: true },
            { name: '☠️ Boss Kills',    value: String(r.kills),           inline: true },
            { name: '✦ Prestige',       value: String(r.prestige),        inline: true },
            { name: '⚡ Gesamtschaden', value: formatDmg(r.total_damage), inline: true },
            { name: '💰 Münzen',        value: String(r.coins),           inline: true },
          )
          .setFooter({ text: 'Pink Horizon · Smash the Boss' })] });
      } catch (e) {
        console.error('[/stats]', e.message);
        await interaction.editReply('❌ Datenbankfehler: ' + e.message);
      }
    }

  // ── Button Interactions ──────────────────────────────────────────────────
  } else if (interaction.isButton()) {
    const id = interaction.customId;

    if (id === 'verify') {
      await verifyMember(interaction);

    } else if (id === 'ticket_open') {
      await openTicketCategory(interaction);

    } else if (id === 'ticket_claim') {
      await claimTicket(interaction);

    } else if (id === 'ticket_close') {
      await closeTicket(interaction);
    }

  // ── Select Menu Interactions ─────────────────────────────────────────────
  } else if (interaction.isStringSelectMenu()) {
    if (interaction.customId === 'ticket_category') {
      await createTicket(interaction, interaction.values[0]);
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

function sleep(ms) { return new Promise(r => setTimeout(r, ms)); }

// ─────────────────────────────────────────────────────────────────────────────
// Start
// ─────────────────────────────────────────────────────────────────────────────

if (!TOKEN || !CLIENT_ID || !GUILD_ID) {
  console.error('❌ Fehlende Env-Vars: DISCORD_TOKEN, DISCORD_CLIENT_ID, DISCORD_GUILD_ID');
  process.exit(1);
}

client.login(TOKEN).catch(e => { console.error('❌ Login fehlgeschlagen:', e.message); process.exit(1); });
