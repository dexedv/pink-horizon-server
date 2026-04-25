#!/bin/bash
# update.sh – Git pull + alle Container neu starten
# Auf dem Server ausführen: bash deploy/update.sh

set -e
cd "$(dirname "$0")/.."

# ── Farben & Styles ───────────────────────────────────────────────────────────
RED='\033[0;31m';  GREEN='\033[0;32m'; YELLOW='\033[1;33m'
CYAN='\033[0;36m'; BLUE='\033[0;34m';  MAGENTA='\033[0;35m'
BOLD='\033[1m';    DIM='\033[2m';      RESET='\033[0m'

# ── Hilfsfunktionen ───────────────────────────────────────────────────────────
step()    { echo -e "\n${MAGENTA}${BOLD}  ● ${RESET}${BOLD}$1${RESET}  ${DIM}$2${RESET}"; }
ok()      { echo -e "    ${GREEN}✔${RESET}  $1"; }
info()    { echo -e "    ${CYAN}→${RESET}  $1"; }
warn()    { echo -e "    ${YELLOW}⚠${RESET}  $1"; }

divider() {
  echo -e "${DIM}  ────────────────────────────────────────────────────${RESET}"
}

# ── Banner ────────────────────────────────────────────────────────────────────
clear
echo ""
echo -e "${MAGENTA}${BOLD}"
echo "  ██████╗ ██╗███╗   ██╗██╗  ██╗    ██╗  ██╗ ██████╗ ██████╗ "
echo "  ██╔══██╗██║████╗  ██║██║ ██╔╝    ██║  ██║██╔═══██╗██╔══██╗"
echo "  ██████╔╝██║██╔██╗ ██║█████╔╝     ███████║██║   ██║██████╔╝"
echo "  ██╔═══╝ ██║██║╚██╗██║██╔═██╗     ██╔══██║██║   ██║██╔══██╗"
echo "  ██║     ██║██║ ╚████║██║  ██╗    ██║  ██║╚██████╔╝██║  ██║"
echo "  ╚═╝     ╚═╝╚═╝  ╚═══╝╚═╝  ╚═╝   ╚═╝  ╚═╝ ╚═════╝ ╚═╝  ╚═╝"
echo -e "${RESET}"
echo -e "  ${DIM}Deploy & Restart  •  $(date '+%d.%m.%Y %H:%M:%S')${RESET}"
echo ""
divider
echo ""

START_TIME=$(date +%s)

# ── 1. Git Pull ───────────────────────────────────────────────────────────────
step "Git Pull" "[1/5]"
OUTPUT=$(git pull 2>&1)
if echo "$OUTPUT" | grep -q "Already up to date"; then
  ok "Bereits aktuell – keine Änderungen"
else
  echo "$OUTPUT" | grep -E "^\s*(Updating|[0-9]+ file)" | while read line; do
    info "$line"
  done
  CHANGED=$(echo "$OUTPUT" | grep -oP '\d+ file' || echo "Dateien")
  ok "Aktualisiert: $CHANGED geändert"
fi

# ── 2. Berechtigungen ────────────────────────────────────────────────────────
step "Berechtigungen setzen" "[2/5]"
chown -R 1000:1000 servers/lobby/ servers/survival/ servers/smash/ 2>/dev/null || true
ok "Berechtigungen gesetzt (uid 1000)"

# ── 3. Spielserver neu starten ────────────────────────────────────────────────
step "Spielserver neu starten" "[3/5]"
info "Stoppe: lobby, survival, smash, velocity..."
docker compose restart lobby survival smash velocity
ok "Alle Spielserver neu gestartet"

# ── 4. Dashboard ──────────────────────────────────────────────────────────────
step "Dashboard neu bauen" "[4/5]"
docker compose up -d --build dashboard 2>&1 | grep -E "Building|built|Started|Running" | while read line; do
  info "$line"
done
ok "Dashboard aktualisiert"

# ── 5. Discord-Bot ────────────────────────────────────────────────────────────
step "Discord-Bot neu bauen" "[5/5]"
docker compose up -d --build discord-bot 2>&1 | grep -E "Building|built|Started|Running" | while read line; do
  info "$line"
done
ok "Discord-Bot aktualisiert"

# ── Zusammenfassung ───────────────────────────────────────────────────────────
END_TIME=$(date +%s)
ELAPSED=$((END_TIME - START_TIME))

echo ""
divider
echo ""
echo -e "  ${GREEN}${BOLD}✔  Deploy abgeschlossen${RESET}  ${DIM}(${ELAPSED}s)${RESET}"
echo ""

docker compose ps --format "table {{.Name}}\t{{.Status}}\t{{.Ports}}" 2>/dev/null | while IFS= read -r line; do
  if echo "$line" | grep -q "running\|Up"; then
    echo -e "  ${GREEN}▸${RESET}  $line"
  elif echo "$line" | grep -q "NAME\|CONTAINER"; then
    echo -e "  ${DIM}  $line${RESET}"
  else
    echo -e "  ${RED}▸${RESET}  $line"
  fi
done

echo ""
divider
echo ""
