#!/bin/bash
# Pink Horizon - Alle Server starten
# Aufruf: bash start-all.sh

BASE="/opt/minecraft"

start_server() {
  local name=$1
  local dir=$2
  local jar=$3
  local ram=$4

  if screen -list | grep -q "$name"; then
    echo "[$name] laeuft bereits."
    return
  fi

  echo "[$name] Starte..."
  screen -dmS "ph-$name" bash -c "
    cd '$dir'
    java -Xms${ram} -Xmx${ram} \
      -XX:+UseG1GC \
      -XX:+ParallelRefProcEnabled \
      -XX:MaxGCPauseMillis=200 \
      -XX:+UnlockExperimentalVMOptions \
      -XX:+DisableExplicitGC \
      -jar $jar nogui
  "
  echo "[$name] gestartet in screen 'ph-$name'"
}

# Velocity Proxy
start_server "proxy"    "$BASE/proxy"              "velocity.jar" "512M"

sleep 2

# Sub-Server
start_server "lobby"    "$BASE/servers/lobby"      "server.jar"   "1G"
start_server "survival" "$BASE/servers/survival"   "server.jar"   "2G"
start_server "skyblock" "$BASE/servers/skyblock"   "server.jar"   "2G"
start_server "minigames" "$BASE/servers/minigames" "server.jar"   "2G"

echo ""
echo "Alle Server gestartet!"
echo "Screen-Sessions anzeigen: screen -ls"
echo "Zu Server verbinden: screen -r ph-<name>"
