#!/bin/bash
# ============================================================
# Pink Horizon – World Backup Script
# Läuft per Cron alle 4 Stunden auf dem Server.
#
# Einrichtung (einmalig auf dem Server):
#   crontab -e
#   0 */4 * * * bash /opt/pinkhorizon/deploy/backup.sh >> /var/log/ph-backup.log 2>&1
# ============================================================

SCRIPT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
BACKUP_DIR="$SCRIPT_DIR/backups"
KEEP_DAYS=7
TIMESTAMP=$(date +%Y%m%d_%H%M%S)

GREEN='\033[0;32m'; YELLOW='\033[1;33m'; RED='\033[0;31m'; RESET='\033[0m'

log()  { echo -e "[$(date '+%d.%m.%Y %H:%M:%S')] $1"; }
ok()   { echo -e "${GREEN}[OK]${RESET}  $1"; }
warn() { echo -e "${YELLOW}[WARN]${RESET} $1"; }
err()  { echo -e "${RED}[ERR]${RESET}  $1"; }

mkdir -p "$BACKUP_DIR"
log "════════════════════════════════════════"
log "Pink Horizon Backup – $TIMESTAMP"
log "════════════════════════════════════════"

# ── Welt sichern ──────────────────────────────────────────────────────────────
backup_server() {
    local container=$1       # Docker-Container-Name (z.B. ph-survival)
    local label=$2           # Anzeigename
    local world_dirs=("${@:3}")  # Alle Welt-Verzeichnisse (relativ zu SCRIPT_DIR)

    log "Sichere $label…"

    # Save-All via RCON (ignoriert Fehler falls Server gerade offline)
    if docker ps --format '{{.Names}}' | grep -q "^${container}$"; then
        docker exec "$container" rcon-cli save-all 2>/dev/null && sleep 3 || true
    else
        warn "$container läuft nicht – sichere trotzdem Dateien."
    fi

    local backup_file="$BACKUP_DIR/${label}_${TIMESTAMP}.tar.gz"
    local dirs_to_backup=()
    for d in "${world_dirs[@]}"; do
        [ -d "$SCRIPT_DIR/$d" ] && dirs_to_backup+=("$d")
    done

    if [ ${#dirs_to_backup[@]} -eq 0 ]; then
        warn "Keine Weltordner gefunden für $label – übersprungen."
        return
    fi

    tar -czf "$backup_file" -C "$SCRIPT_DIR" "${dirs_to_backup[@]}" 2>/dev/null
    local size
    size=$(du -sh "$backup_file" 2>/dev/null | cut -f1)
    ok "$label → $(basename "$backup_file") ($size)"
}

backup_server "ph-survival" "survival" \
    "servers/survival/world" \
    "servers/survival/world_nether" \
    "servers/survival/world_the_end"

backup_server "ph-lobby" "lobby" \
    "servers/lobby/world"

backup_server "ph-smash" "smash" \
    "servers/smash/world"

backup_server "ph-skyblock" "skyblock" \
    "servers/skyblock/world" \
    "servers/skyblock/skyblock_world"

# ── Generators: island-template + alle island_<UUID> Welten ──────────────────
log "Sichere generators…"
if docker ps --format '{{.Names}}' | grep -q "^ph-generators$"; then
    docker exec ph-generators rcon-cli save-all 2>/dev/null && sleep 3 || true
else
    warn "ph-generators läuft nicht – sichere trotzdem Dateien."
fi

gen_dirs=()
[ -d "$SCRIPT_DIR/servers/generators/island-template" ] && \
    gen_dirs+=("servers/generators/island-template")
for d in "$SCRIPT_DIR/servers/generators/island_"*/; do
    [ -d "$d" ] && gen_dirs+=("servers/generators/$(basename "$d")")
done

if [ ${#gen_dirs[@]} -eq 0 ]; then
    warn "Keine Weltordner gefunden für generators – übersprungen."
else
    gen_backup="$BACKUP_DIR/generators_${TIMESTAMP}.tar.gz"
    tar -czf "$gen_backup" -C "$SCRIPT_DIR" "${gen_dirs[@]}" 2>/dev/null
    gen_size=$(du -sh "$gen_backup" 2>/dev/null | cut -f1)
    ok "generators → $(basename "$gen_backup") ($gen_size)  [${#gen_dirs[@]} Welten]"
fi

# ── Alte Backups löschen ──────────────────────────────────────────────────────
log "Lösche Backups älter als $KEEP_DAYS Tage…"
deleted=$(find "$BACKUP_DIR" -name "*.tar.gz" -mtime +"$KEEP_DAYS" -print -delete | wc -l)
ok "$deleted alte Backup(s) gelöscht."

# ── Zusammenfassung ───────────────────────────────────────────────────────────
total=$(find "$BACKUP_DIR" -name "*.tar.gz" | wc -l)
size=$(du -sh "$BACKUP_DIR" 2>/dev/null | cut -f1)
log "════════════════════════════════════════"
log "Fertig. $total Backup(s) gespeichert, Gesamt: $size"
log "Backup-Ordner: $BACKUP_DIR"
log "════════════════════════════════════════"
