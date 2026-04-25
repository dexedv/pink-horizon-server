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

# ── 2. Duplikate + remapped-Cache bereinigen ─────────────────────────────
echo "▶ [2/4] Plugin-Cleanup..."
rm -rf servers/survival/plugins/.paper-remapped/
rm -rf servers/lobby/plugins/.paper-remapped/
rm -rf servers/smash/plugins/.paper-remapped/
chown -R 1000:1000 servers/lobby/ servers/survival/ servers/smash/ 2>/dev/null || true
echo ""

# ── 3. Spielserver + Proxy neu starten (neue JARs aus Volume) ────────────
echo "▶ [3/5] Server-Container neu starten..."
docker compose restart lobby survival smash velocity
echo ""

# ── 4. Dashboard neu bauen (damit server.js-Änderungen aktiv werden) ─────
echo "▶ [4/5] Dashboard neu bauen..."
docker compose up -d --build dashboard
echo ""

# ── 5. Discord-Bot neu bauen ──────────────────────────────────────────────
echo "▶ [5/5] Discord-Bot neu bauen..."
docker compose up -d --build discord-bot
echo ""

echo "╔══════════════════════════════════════╗"
echo "║   ✔  Deploy abgeschlossen!           ║"
echo "╚══════════════════════════════════════╝"
echo ""
docker compose ps --format "table {{.Name}}\t{{.Status}}"
echo ""
