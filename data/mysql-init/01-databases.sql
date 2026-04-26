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

-- ph_user Zugriff auf alle Datenbanken gewähren
GRANT ALL PRIVILEGES ON pinkhorizon.* TO 'ph_user'@'%';
GRANT ALL PRIVILEGES ON ph_survival.* TO 'ph_user'@'%';
GRANT ALL PRIVILEGES ON ph_smash.* TO 'ph_user'@'%';
GRANT ALL PRIVILEGES ON ph_generators.* TO 'ph_user'@'%';
FLUSH PRIVILEGES;

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
