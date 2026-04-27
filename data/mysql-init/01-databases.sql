-- Pink Horizon MySQL Init-Script
-- Wird beim ersten Start des MySQL-Containers automatisch ausgeführt

-- Survival-Datenbank anlegen
CREATE DATABASE IF NOT EXISTS ph_survival
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

-- Smash-Datenbank anlegen
CREATE DATABASE IF NOT EXISTS ph_smash
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

-- Generators-Datenbank anlegen
CREATE DATABASE IF NOT EXISTS ph_generators
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

-- SkyBlock-Datenbank anlegen
CREATE DATABASE IF NOT EXISTS ph_skyblock
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

-- ph_user Zugriff auf alle Datenbanken gewähren
GRANT ALL PRIVILEGES ON pinkhorizon.* TO 'ph_user'@'%';
GRANT ALL PRIVILEGES ON ph_survival.* TO 'ph_user'@'%';
GRANT ALL PRIVILEGES ON ph_smash.* TO 'ph_user'@'%';
GRANT ALL PRIVILEGES ON ph_generators.* TO 'ph_user'@'%';
GRANT ALL PRIVILEGES ON ph_skyblock.* TO 'ph_user'@'%';
FLUSH PRIVILEGES;

-- ── ph_skyblock Tabellen ─────────────────────────────────────────────────────

USE ph_skyblock;

CREATE TABLE IF NOT EXISTS sb_islands (
    id              INT AUTO_INCREMENT PRIMARY KEY,
    uuid            VARCHAR(36)  NOT NULL UNIQUE COMMENT 'Insel-UUID (= Owner-UUID)',
    owner_uuid      VARCHAR(36)  NOT NULL,
    owner_name      VARCHAR(16)  NOT NULL,
    world           VARCHAR(64)  NOT NULL DEFAULT 'skyblock_world',
    center_x        INT          NOT NULL DEFAULT 0,
    center_y        INT          NOT NULL DEFAULT 64,
    center_z        INT          NOT NULL DEFAULT 0,
    home_x          DOUBLE       NOT NULL DEFAULT 0,
    home_y          DOUBLE       NOT NULL DEFAULT 65,
    home_z          DOUBLE       NOT NULL DEFAULT 0,
    home_yaw        FLOAT        NOT NULL DEFAULT 0,
    home_pitch      FLOAT        NOT NULL DEFAULT 0,
    level           INT          NOT NULL DEFAULT 1,
    score           BIGINT       NOT NULL DEFAULT 0,
    size            INT          NOT NULL DEFAULT 80,
    max_members     INT          NOT NULL DEFAULT 4,
    is_open         TINYINT      NOT NULL DEFAULT 0,
    warp_enabled    TINYINT      NOT NULL DEFAULT 0,
    warp_name       VARCHAR(32)  DEFAULT NULL,
    created_at      BIGINT       NOT NULL DEFAULT 0,
    last_active     BIGINT       NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS sb_island_members (
    island_id   INT          NOT NULL,
    uuid        VARCHAR(36)  NOT NULL,
    name        VARCHAR(16)  NOT NULL,
    role        ENUM('MEMBER','COOP','VISITOR') NOT NULL DEFAULT 'MEMBER',
    joined_at   BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (island_id, uuid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS sb_island_bans (
    island_id   INT          NOT NULL,
    uuid        VARCHAR(36)  NOT NULL,
    name        VARCHAR(16)  NOT NULL,
    banned_at   BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (island_id, uuid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS sb_players (
    uuid            VARCHAR(36)  PRIMARY KEY,
    name            VARCHAR(16)  NOT NULL,
    island_id       INT          DEFAULT NULL COMMENT 'NULL = kein Mitglied einer Insel',
    language        VARCHAR(5)   NOT NULL DEFAULT 'de',
    chat_island     TINYINT      NOT NULL DEFAULT 0 COMMENT '1 = Insel-Chat aktiv',
    last_seen       BIGINT       NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS sb_upgrades (
    island_id   INT          NOT NULL,
    upgrade     VARCHAR(64)  NOT NULL,
    level       INT          NOT NULL DEFAULT 1,
    PRIMARY KEY (island_id, upgrade)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS sb_missions (
    island_id   INT          NOT NULL,
    mission_id  VARCHAR(64)  NOT NULL,
    progress    BIGINT       NOT NULL DEFAULT 0,
    completed   TINYINT      NOT NULL DEFAULT 0,
    times       INT          NOT NULL DEFAULT 0 COMMENT 'Wie oft abgeschlossen',
    PRIMARY KEY (island_id, mission_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX IF NOT EXISTS idx_sb_islands_score   ON sb_islands(score DESC);
CREATE INDEX IF NOT EXISTS idx_sb_islands_level   ON sb_islands(level DESC);
CREATE INDEX IF NOT EXISTS idx_sb_players_island  ON sb_players(island_id);

-- ── ph_generators Tabellen ───────────────────────────────────────────────────

USE ph_generators;

CREATE TABLE IF NOT EXISTS gen_players (
    uuid              VARCHAR(36)  PRIMARY KEY,
    name              VARCHAR(16)  NOT NULL,
    money             BIGINT       DEFAULT 0,
    prestige          INT          DEFAULT 0,
    total_earned      BIGINT       DEFAULT 0,
    total_upgrades    INT          DEFAULT 0,
    afk_boxes_opened  INT          DEFAULT 0,
    booster_expiry    BIGINT       DEFAULT 0,
    booster_mult      DOUBLE       DEFAULT 1.0,
    border_size       INT          DEFAULT 40,
    last_seen         BIGINT       DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS gen_generators (
    id     INT AUTO_INCREMENT PRIMARY KEY,
    uuid   VARCHAR(36) NOT NULL,
    world  VARCHAR(64) NOT NULL,
    x      INT         NOT NULL,
    y      INT         NOT NULL,
    z      INT         NOT NULL,
    tier   VARCHAR(32) NOT NULL,
    level  INT         DEFAULT 1,
    UNIQUE KEY pos_key (world, x, y, z)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS gen_guilds (
    id           INT AUTO_INCREMENT PRIMARY KEY,
    name         VARCHAR(32) NOT NULL UNIQUE,
    leader_uuid  VARCHAR(36) NOT NULL,
    created_at   BIGINT      DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS gen_guild_members (
    guild_id   INT         NOT NULL,
    uuid       VARCHAR(36) NOT NULL,
    PRIMARY KEY (uuid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS gen_quests (
    uuid        VARCHAR(36) NOT NULL,
    quest_id    VARCHAR(64) NOT NULL,
    progress    BIGINT      DEFAULT 0,
    completed   TINYINT     DEFAULT 0,
    reset_date  VARCHAR(10) NOT NULL,
    PRIMARY KEY (uuid, quest_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS gen_achievements (
    uuid           VARCHAR(36) NOT NULL,
    achievement_id VARCHAR(64) NOT NULL,
    progress       BIGINT      DEFAULT 0,
    completed      TINYINT     DEFAULT 0,
    PRIMARY KEY (uuid, achievement_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS gen_afk (
    uuid          VARCHAR(36) PRIMARY KEY,
    afk_since     BIGINT DEFAULT 0,
    last_box_time BIGINT DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
