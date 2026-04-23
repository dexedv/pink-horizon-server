#!/bin/bash
# update.sh – Git pull + alle Container neu starten
# Auf dem Server ausführen: bash deploy/update.sh

set -e
cd "$(dirname "$0")/.."

echo ""
echo "╔══════════════════════════════════════╗"
echo "║   Pink Horizon – Deploy & Restart    ║"
echo "╚══════════════════════════════════════╝"
echo ""

# ── 1. Git Pull ───────────────────────────────────────────────────────────
echo "▶ [1/4] Git Pull..."
git pull
echo ""

# ── 2. Berechtigungen fixieren (Docker uid=1000) ─────────────────────────
echo "▶ [2/4] Berechtigungen setzen..."
chown -R 1000:1000 servers/lobby/ servers/survival/ servers/minigames/ 2>/dev/null || true
echo ""

# ── 3. Spielserver + Proxy neu starten (neue JARs aus Volume) ────────────
echo "▶ [3/4] Server-Container neu starten..."
docker compose restart lobby survival minigames smash velocity
echo ""

# ── 4. Dashboard neu bauen (damit server.js-Änderungen aktiv werden) ─────
echo "▶ [4/4] Dashboard neu bauen..."
docker compose up -d --build dashboard
echo ""

echo "╔══════════════════════════════════════╗"
echo "║   ✔  Deploy abgeschlossen!           ║"
echo "╚══════════════════════════════════════╝"
echo ""
docker compose ps --format "table {{.Name}}\t{{.Status}}"
echo ""
