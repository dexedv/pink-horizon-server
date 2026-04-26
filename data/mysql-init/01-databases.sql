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
