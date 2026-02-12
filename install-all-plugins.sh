#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")" && pwd)"

for dir in "$REPO_ROOT"/*/; do
  if [ -f "$dir/build.gradle.kts" ]; then
    PLUGIN_NAME="$(basename "$dir")"
    echo ""
    echo "========================================"
    echo "  Installing: $PLUGIN_NAME"
    echo "========================================"
    "$REPO_ROOT/install-plugin.sh" "$PLUGIN_NAME"
  fi
done

echo ""
echo "All plugins installed. Restart IntelliJ IDEA to pick up changes."
