-- Pink Horizon MySQL Init-Script
-- Wird beim ersten Start des MySQL-Containers automatisch ausgeführt

-- Netzwerk-Datenbank (LuckPerms, geteilte Daten)
CREATE DATABASE IF NOT EXISTS pinkhorizon
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

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
    last_active     BIGINT       NOT NULL DEFAULT 0,
    INDEX idx_score (score DESC),
    INDEX idx_level (level DESC)
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
    island_chat     TINYINT      NOT NULL DEFAULT 0 COMMENT '1 = Insel-Chat aktiv',
    last_seen       BIGINT       NOT NULL DEFAULT 0,
    INDEX idx_island (island_id)
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

-- Indexes sind bereits inline in den CREATE TABLE Definitionen enthalten

-- ── ph_skyblock: SkyBlock Feature-Tabellen (ph-skyblock Plugin) ─────────────

-- Coins / Wirtschaft
CREATE TABLE IF NOT EXISTS sky_coin_accounts (
    uuid     VARCHAR(36) PRIMARY KEY,
    balance  BIGINT      DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Island-DNA
CREATE TABLE IF NOT EXISTS sky_island_dna (
    island_uuid       VARCHAR(36)  PRIMARY KEY,
    genes             VARCHAR(512) NOT NULL,
    combinations_used INT          DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS sky_dna_fragments (
    uuid        VARCHAR(36) NOT NULL,
    fragment_id VARCHAR(64) NOT NULL,
    amount      INT         DEFAULT 0,
    PRIMARY KEY (uuid, fragment_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Rituale
CREATE TABLE IF NOT EXISTS sky_rituals (
    island_uuid VARCHAR(36) NOT NULL,
    ritual_id   VARCHAR(64) NOT NULL,
    last_used   BIGINT      NOT NULL,
    PRIMARY KEY (island_uuid, ritual_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Sternschnuppen
CREATE TABLE IF NOT EXISTS sky_stars (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    island_uuid VARCHAR(36)  NOT NULL,
    tier        VARCHAR(32)  NOT NULL,
    dropped_at  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    collected   TINYINT      DEFAULT 0,
    INDEX idx_island (island_uuid),
    INDEX idx_tier   (tier)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Blueprints
CREATE TABLE IF NOT EXISTS sky_blueprints (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    owner_uuid   VARCHAR(36)  NOT NULL,
    name         VARCHAR(64)  NOT NULL,
    description  VARCHAR(256),
    blocks_json  MEDIUMTEXT   NOT NULL,
    width        INT          DEFAULT 64,
    height       INT          DEFAULT 64,
    depth        INT          DEFAULT 64,
    shared       TINYINT      DEFAULT 0,
    approved     TINYINT      DEFAULT 0,
    created_at   TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY owner_name (owner_uuid, name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Kontrakte / Auftrags-Brett
CREATE TABLE IF NOT EXISTS sky_contracts (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    creator      VARCHAR(36)  NOT NULL,
    type         VARCHAR(32)  NOT NULL,
    requirement  VARCHAR(256) NOT NULL,
    reward_coins BIGINT       DEFAULT 0,
    goal         BIGINT       DEFAULT 1,
    progress     BIGINT       DEFAULT 0,
    deadline     BIGINT       NOT NULL,
    completed    TINYINT      DEFAULT 0,
    INDEX idx_type    (type),
    INDEX idx_creator (creator)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS sky_contract_participants (
    contract_id  BIGINT      NOT NULL,
    uuid         VARCHAR(36) NOT NULL,
    contribution BIGINT      DEFAULT 0,
    PRIMARY KEY (contract_id, uuid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Story / Nyx
CREATE TABLE IF NOT EXISTS sky_story_progress (
    player_uuid   VARCHAR(36) PRIMARY KEY,
    chapter       INT         DEFAULT 0,
    nyx_fragments INT         DEFAULT 0,
    updated_at    TIMESTAMP   DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS sky_nyx_event (
    id            INT       PRIMARY KEY DEFAULT 1,
    progress      INT       DEFAULT 0,
    active        TINYINT   DEFAULT 0,
    completed_at  TIMESTAMP NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT IGNORE INTO sky_nyx_event (id) VALUES (1);

-- Island Chronicles
CREATE TABLE IF NOT EXISTS sky_chronicles (
    island_uuid VARCHAR(36)  NOT NULL,
    entry_id    INT          NOT NULL AUTO_INCREMENT,
    type        VARCHAR(32)  NOT NULL DEFAULT 'auto',
    message     VARCHAR(512) NOT NULL,
    created_at  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (entry_id),
    INDEX idx_island (island_uuid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ── ph_skyblock: Companions (ph-companions Plugin) ────────────────────────────

CREATE TABLE IF NOT EXISTS sky_companions (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    player_uuid    VARCHAR(36) NOT NULL,
    companion_type VARCHAR(32) NOT NULL,
    level          INT         DEFAULT 1,
    xp             BIGINT      DEFAULT 0,
    hunger         INT         DEFAULT 72000,
    active         TINYINT     DEFAULT 0,
    UNIQUE KEY one_type (player_uuid, companion_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ── ph_skyblock: Runen (ph-runes Plugin) ──────────────────────────────────────

CREATE TABLE IF NOT EXISTS sky_player_runes (
    player_uuid VARCHAR(36) NOT NULL,
    rune_type   VARCHAR(64) NOT NULL,
    amount      INT         DEFAULT 1,
    PRIMARY KEY (player_uuid, rune_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ── ph_skyblock: Dungeons (ph-dungeons Plugin) ────────────────────────────────

CREATE TABLE IF NOT EXISTS sky_dungeon_runs (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    player_uuid      VARCHAR(36) NOT NULL,
    dungeon_id       VARCHAR(64) NOT NULL,
    tier             INT         NOT NULL,
    duration_seconds INT,
    rank             CHAR(1),
    completed_at     TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_player (player_uuid),
    INDEX idx_dungeon (dungeon_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ── ph_skyblock: Battle Pass (ph-battlepass Plugin) ───────────────────────────

CREATE TABLE IF NOT EXISTS sky_battlepass (
    player_uuid VARCHAR(36) NOT NULL,
    season      INT         NOT NULL,
    bp_xp       BIGINT      DEFAULT 0,
    level       INT         DEFAULT 0,
    premium     TINYINT     DEFAULT 0,
    PRIMARY KEY (player_uuid, season)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS sky_bp_challenges (
    player_uuid   VARCHAR(36) NOT NULL,
    season        INT         NOT NULL,
    challenge_key VARCHAR(64) NOT NULL,
    progress      INT         DEFAULT 0,
    completed     TINYINT     DEFAULT 0,
    reset_date    DATE,
    PRIMARY KEY (player_uuid, season, challenge_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS sky_bp_claimed (
    player_uuid     VARCHAR(36) NOT NULL,
    season          INT         NOT NULL,
    level           INT         NOT NULL,
    premium_claimed TINYINT     DEFAULT 0,
    PRIMARY KEY (player_uuid, season, level)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ── ph_skyblock: Skills (ph-skills Plugin) ────────────────────────────────────

CREATE TABLE IF NOT EXISTS sky_skills (
    uuid     VARCHAR(36) NOT NULL,
    skill_id VARCHAR(32) NOT NULL,
    xp       BIGINT      DEFAULT 0,
    level    INT         DEFAULT 0,
    PRIMARY KEY (uuid, skill_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ── ph_skyblock: Research (ph-research Plugin) ────────────────────────────────

CREATE TABLE IF NOT EXISTS sky_research (
    uuid         VARCHAR(36) NOT NULL,
    research_id  VARCHAR(64) NOT NULL,
    started_at   TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP   NULL,
    PRIMARY KEY (uuid, research_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ── ph_skyblock: Maschinen (ph-machines Plugin) ───────────────────────────────

CREATE TABLE IF NOT EXISTS sky_machines (
    id           INT AUTO_INCREMENT PRIMARY KEY,
    island_uuid  VARCHAR(36) NOT NULL,
    world        VARCHAR(64) NOT NULL,
    x            INT         NOT NULL,
    y            INT         NOT NULL,
    z            INT         NOT NULL,
    type         VARCHAR(64) NOT NULL,
    config       TEXT,
    energy_stored INT        DEFAULT 0,
    active       TINYINT     DEFAULT 1,
    UNIQUE KEY pos (world, x, y, z),
    INDEX idx_island (island_uuid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ── ph_skyblock: Auktionshaus (ph-auction Plugin) ─────────────────────────────

CREATE TABLE IF NOT EXISTS sky_auctions (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    seller          VARCHAR(36)  NOT NULL,
    seller_name     VARCHAR(16),
    item_nbt        MEDIUMTEXT   NOT NULL,
    item_name       VARCHAR(128),
    start_price     BIGINT       DEFAULT 0,
    bin_price       BIGINT,
    highest_bid     BIGINT       DEFAULT 0,
    highest_bidder  VARCHAR(36),
    ends_at         TIMESTAMP    NOT NULL,
    sold            TINYINT      DEFAULT 0,
    INDEX idx_seller  (seller),
    INDEX idx_ends_at (ends_at),
    INDEX idx_sold    (sold)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

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
