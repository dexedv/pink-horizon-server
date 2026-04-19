#!/bin/bash
# Pink Horizon - Plugins bauen und auf Server kopieren
# Voraussetzung: Maven und Java 21 installiert

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
PLUGINS_DIR="$PROJECT_DIR/plugins"
MC_BASE="/opt/minecraft"

echo "=== Baue Plugins ==="
cd "$PLUGINS_DIR"
mvn clean package -q

echo "=== Kopiere Plugins ==="

# Core kommt auf alle Server
for SERVER in lobby survival skyblock minigames; do
  mkdir -p "$MC_BASE/servers/$SERVER/plugins"
  cp "$PLUGINS_DIR/ph-core/target/ph-core-1.0.0.jar" "$MC_BASE/servers/$SERVER/plugins/"
  echo "[Core] -> $SERVER"
done

# Server-spezifische Plugins
cp "$PLUGINS_DIR/ph-lobby/target/ph-lobby-1.0.0.jar"         "$MC_BASE/servers/lobby/plugins/"
cp "$PLUGINS_DIR/ph-survival/target/ph-survival-1.0.0.jar"   "$MC_BASE/servers/survival/plugins/"
cp "$PLUGINS_DIR/ph-skyblock/target/ph-skyblock-1.0.0.jar"   "$MC_BASE/servers/skyblock/plugins/"
cp "$PLUGINS_DIR/ph-minigames/target/ph-minigames-1.0.0.jar" "$MC_BASE/servers/minigames/plugins/"

echo ""
echo "=== Fertig! Starte Server neu mit: bash stop-all.sh && bash start-all.sh ==="
