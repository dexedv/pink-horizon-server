#!/bin/bash
# Pink Horizon - Alle Server stoppen

for name in proxy lobby survival skyblock minigames generators; do
  if screen -list | grep -q "ph-$name"; then
    echo "Stoppe ph-$name..."
    screen -S "ph-$name" -p 0 -X stuff "stop\n"
    sleep 3
    screen -S "ph-$name" -X quit 2>/dev/null
  else
    echo "ph-$name laeuft nicht."
  fi
done

echo "Alle Server gestoppt."
