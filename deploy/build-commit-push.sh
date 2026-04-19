#!/bin/bash
# build-commit-push.sh
# Lokal ausführen (Windows/Git Bash):
#   bash deploy/build-commit-push.sh
#
# Baut alle Custom-Plugins, kopiert die JARs in servers/*/plugins/
# und committed + pusht alles in einem Schritt.

set -e
cd "$(dirname "$0")/.."

MAVEN="/c/Users/domin/Desktop/Entwicklingen/maven/apache-maven-3.9.6/bin/mvn"
JAVA_HOME="/c/Users/domin/AppData/Roaming/Badlion Client/Data/jdk-21.0.2"
export JAVA_HOME
export PATH="$JAVA_HOME/bin:$PATH"

echo "=== Baue Plugins ==="
$MAVEN package -q -f plugins/ph-core/pom.xml
$MAVEN package -q -f plugins/ph-lobby/pom.xml
$MAVEN package -q -f plugins/ph-survival/pom.xml

echo "=== Kopiere JARs ==="
CORE_JAR="plugins/ph-core/target/ph-core-1.0.0.jar"

cp "$CORE_JAR"                                         servers/lobby/plugins/ph-core-1.0.0.jar
cp "$CORE_JAR"                                         servers/survival/plugins/ph-core-1.0.0.jar
cp plugins/ph-lobby/target/ph-lobby-1.0.0.jar         servers/lobby/plugins/PH-Lobby.jar
cp plugins/ph-survival/target/ph-survival-1.0.0.jar   servers/survival/plugins/ph-survival-1.0.0.jar

echo "=== Commit & Push ==="
git add \
  servers/lobby/plugins/PH-Lobby.jar \
  servers/lobby/plugins/ph-core-1.0.0.jar \
  servers/survival/plugins/ph-survival-1.0.0.jar \
  servers/survival/plugins/ph-core-1.0.0.jar

# Nur committen wenn sich was geändert hat
if git diff --cached --quiet; then
  echo "Keine JAR-Änderungen – nichts zu committen."
else
  git commit -m "deploy: update plugin JARs"
  git push
  echo "=== Gepusht! Auf Server: ./deploy/update.sh ==="
fi
