#!/bin/bash
# Pink Horizon - Einmalige Server-Installation (Ubuntu/Debian)
# Aufruf: sudo bash setup.sh

set -e

echo "=== Pink Horizon Setup ==="

# Java 21 installieren
echo "[1/5] Installiere Java 21..."
apt-get update -q
apt-get install -y openjdk-21-jre-headless screen

# User anlegen
echo "[2/5] Erstelle minecraft-User..."
id -u minecraft &>/dev/null || useradd -r -m -d /opt/minecraft minecraft

# Verzeichnisse anlegen
echo "[3/5] Erstelle Verzeichnisse..."
mkdir -p /opt/minecraft/{proxy,servers/{lobby,survival,skyblock,minigames}}
chown -R minecraft:minecraft /opt/minecraft

# Velocity herunterladen
echo "[4/5] Lade Velocity herunter..."
VELOCITY_VERSION="3.3.0-SNAPSHOT"
su - minecraft -c "wget -q -O /opt/minecraft/proxy/velocity.jar \
  https://api.papermc.io/v2/projects/velocity/versions/3.4.0-SNAPSHOT/builds/latest/downloads/velocity-3.4.0-SNAPSHOT-latest.jar || \
  echo 'Bitte Velocity manuell von https://papermc.io/downloads/velocity herunterladen'"

# Paper herunterladen
echo "[5/5] Lade Paper 1.21.4 herunter..."
PAPER_URL="https://api.papermc.io/v2/projects/paper/versions/1.21.4/builds/latest/downloads/paper-1.21.4-latest.jar"
for SERVER in lobby survival skyblock minigames; do
  su - minecraft -c "wget -q -O /opt/minecraft/servers/$SERVER/server.jar '$PAPER_URL' || \
    echo 'Bitte Paper fuer $SERVER manuell von https://papermc.io/downloads/paper herunterladen'"
  echo "eula=true" > /opt/minecraft/servers/$SERVER/eula.txt
done

# systemd-Services installieren
cp /opt/pink-horizon/deploy/systemd/*.service /etc/systemd/system/
systemctl daemon-reload

# ── Automatische Backups per Cron einrichten ──────────────────────────────────
echo "[6/6] Richte automatische World-Backups ein..."
BACKUP_SCRIPT="$(cd "$(dirname "$0")" && pwd)/backup.sh"
chmod +x "$BACKUP_SCRIPT"
mkdir -p /opt/minecraft/backups

# Cron-Job: alle 4 Stunden Backup
CRON_JOB="0 */4 * * * bash $BACKUP_SCRIPT >> /var/log/ph-backup.log 2>&1"
# Nur hinzufügen wenn noch nicht vorhanden
( crontab -l 2>/dev/null | grep -v "backup.sh"; echo "$CRON_JOB" ) | crontab -
echo "  Backup-Cron eingerichtet: alle 4 Stunden"
echo "  Backup-Ordner: /opt/minecraft/backups"
echo "  Log: /var/log/ph-backup.log"

echo ""
echo "=== Setup abgeschlossen! ==="
echo "Konfiguration kopieren: cp -r /opt/pink-horizon/proxy/* /opt/minecraft/proxy/"
echo "Plugins bauen und kopieren, dann starten mit: bash start-all.sh"
echo ""
echo "Backup manuell testen: bash $(dirname "$0")/backup.sh"
