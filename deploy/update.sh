#!/bin/bash
# update.sh – Zieht nur Plugin-Updates vom Repo, Welten/Daten bleiben sicher

set -e
cd "$(dirname "$0")/.."

echo "=== Pink Horizon Update ==="

# Aktuelle Änderungen holen
git pull

# Container mit neuen JARs neustarten
# Welten, ranks.yml, claims.yml etc. sind in .gitignore → werden NIE berührt
docker compose restart lobby survival skyblock minigames

echo "=== Update abgeschlossen ==="
