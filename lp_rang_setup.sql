-- ============================================================
-- PH Pink Horizon – LuckPerms Rang-Setup
-- Datenbank: pinkhorizon | Container: ph-mysql
-- Server OFFLINE oder danach: /lp networksync
-- ============================================================

-- Gruppen erstellen (falls noch nicht vorhanden)
INSERT IGNORE INTO luckperms_groups (name) VALUES
  ('owner'), ('admin'), ('dev'), ('moderator'), ('supporter'),
  ('vip'), ('nexus'), ('catalyst'), ('rune'),
  ('legende'), ('krieger'), ('siedler'), ('default');

-- Alte Prefix-, Gewicht- und Vererbungs-Nodes entfernen
DELETE FROM luckperms_group_permissions
WHERE name IN ('owner','admin','dev','moderator','supporter',
               'vip','nexus','catalyst','rune',
               'legende','krieger','siedler','default')
  AND (permission LIKE 'prefix.%'
    OR permission LIKE 'weight.%'
    OR permission LIKE 'group.%'
    OR permission = '*');

-- ═══════════════ TEAM-RÄNGE ════════════════

-- OWNER  (dunkelrot fett | erbt catalyst + nexus | alle Rechte)
INSERT INTO luckperms_group_permissions
  (name, permission, value, server, world, expiry, contexts) VALUES
  ('owner', 'prefix.1000.&4&l[Owner] &r', 1, 'global', '', 0, '{}'),
  ('owner', 'weight.1000',                1, 'global', '', 0, '{}'),
  ('owner', '*',                          1, 'global', '', 0, '{}'),
  ('owner', 'group.catalyst',             1, 'global', '', 0, '{}'),
  ('owner', 'group.nexus',                1, 'global', '', 0, '{}');

-- ADMIN  (rot fett)
INSERT INTO luckperms_group_permissions
  (name, permission, value, server, world, expiry, contexts) VALUES
  ('admin', 'prefix.900.&c&l[Admin] &r', 1, 'global', '', 0, '{}'),
  ('admin', 'weight.900',                1, 'global', '', 0, '{}');

-- DEV  (aqua fett)
INSERT INTO luckperms_group_permissions
  (name, permission, value, server, world, expiry, contexts) VALUES
  ('dev', 'prefix.850.&b&l[DEV] &r', 1, 'global', '', 0, '{}'),
  ('dev', 'weight.850',               1, 'global', '', 0, '{}');

-- MODERATOR  (blau fett)
INSERT INTO luckperms_group_permissions
  (name, permission, value, server, world, expiry, contexts) VALUES
  ('moderator', 'prefix.800.&9&l[Mod] &r', 1, 'global', '', 0, '{}'),
  ('moderator', 'weight.800',               1, 'global', '', 0, '{}');

-- SUPPORTER  (dunkelaqua fett)
INSERT INTO luckperms_group_permissions
  (name, permission, value, server, world, expiry, contexts) VALUES
  ('supporter', 'prefix.700.&3&l[Support] &r', 1, 'global', '', 0, '{}'),
  ('supporter', 'weight.700',                   1, 'global', '', 0, '{}');

-- ═══════════════ DONOR-RÄNGE ═══════════════

-- NEXUS  (gold fett)
INSERT INTO luckperms_group_permissions
  (name, permission, value, server, world, expiry, contexts) VALUES
  ('nexus', 'prefix.600.&6&l[Nexus] &r', 1, 'global', '', 0, '{}'),
  ('nexus', 'weight.600',                1, 'global', '', 0, '{}');

-- CATALYST  (hellviolett fett)
INSERT INTO luckperms_group_permissions
  (name, permission, value, server, world, expiry, contexts) VALUES
  ('catalyst', 'prefix.500.&d&l[Catalyst] &r', 1, 'global', '', 0, '{}'),
  ('catalyst', 'weight.500',                    1, 'global', '', 0, '{}');

-- RUNE  (dunkelviolett fett)
INSERT INTO luckperms_group_permissions
  (name, permission, value, server, world, expiry, contexts) VALUES
  ('rune', 'prefix.400.&5&l[Rune] &r', 1, 'global', '', 0, '{}'),
  ('rune', 'weight.400',               1, 'global', '', 0, '{}');

-- VIP  (gold)
INSERT INTO luckperms_group_permissions
  (name, permission, value, server, world, expiry, contexts) VALUES
  ('vip', 'prefix.300.&6[VIP] &r', 1, 'global', '', 0, '{}'),
  ('vip', 'weight.300',             1, 'global', '', 0, '{}');

-- ══════════════ SPIELER-RÄNGE ══════════════

-- LEGENDE  (hellviolett)
INSERT INTO luckperms_group_permissions
  (name, permission, value, server, world, expiry, contexts) VALUES
  ('legende', 'prefix.200.&d[✦ Legende] ', 1, 'global', '', 0, '{}'),
  ('legende', 'weight.200',                1, 'global', '', 0, '{}');

-- KRIEGER  (gold)
INSERT INTO luckperms_group_permissions
  (name, permission, value, server, world, expiry, contexts) VALUES
  ('krieger', 'prefix.150.&6[Krieger] ', 1, 'global', '', 0, '{}'),
  ('krieger', 'weight.150',              1, 'global', '', 0, '{}');

-- SIEDLER  (grün)
INSERT INTO luckperms_group_permissions
  (name, permission, value, server, world, expiry, contexts) VALUES
  ('siedler', 'prefix.100.&a[Siedler] ', 1, 'global', '', 0, '{}'),
  ('siedler', 'weight.100',              1, 'global', '', 0, '{}');

-- DEFAULT  (kein Prefix, niedrigstes Gewicht)
INSERT INTO luckperms_group_permissions
  (name, permission, value, server, world, expiry, contexts) VALUES
  ('default', 'weight.1', 1, 'global', '', 0, '{}');
