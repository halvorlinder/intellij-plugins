#!/usr/bin/env bash
set -euo pipefail

PLUGIN_DIR="${1:?Usage: $0 <plugin-directory-name>}"
REPO_ROOT="$(cd "$(dirname "$0")" && pwd)"
PLUGIN_PATH="$REPO_ROOT/$PLUGIN_DIR"

if [ ! -f "$PLUGIN_PATH/build.gradle.kts" ]; then
  echo "Error: $PLUGIN_PATH/build.gradle.kts not found"
  exit 1
fi

export JAVA_HOME=$(/usr/libexec/java_home -v 21)

INTELLIJ_PLUGINS="$HOME/Library/Application Support/JetBrains/IntelliJIdea2025.2/plugins"

echo "==> Building $PLUGIN_DIR..."
cd "$PLUGIN_PATH"
./gradlew buildPlugin

DIST_ZIP=$(ls -t "$PLUGIN_PATH"/build/distributions/*.zip | head -1)
if [ -z "$DIST_ZIP" ]; then
  echo "Error: No distribution zip found in build/distributions/"
  exit 1
fi

echo "==> Removing old installation of $PLUGIN_DIR..."
rm -rf "$INTELLIJ_PLUGINS/$PLUGIN_DIR"

echo "==> Installing $PLUGIN_DIR..."
unzip -qo "$DIST_ZIP" -d "$INTELLIJ_PLUGINS"

echo "==> Done. Restart IntelliJ IDEA to pick up changes."
