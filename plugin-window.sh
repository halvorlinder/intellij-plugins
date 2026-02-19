#!/usr/bin/env bash
set -euo pipefail

# Add a single plugin window to the existing 'plugins' tmux session.
#
# Usage: ./plugin-window.sh <plugin-name>
#
# The worktree must already exist at ~/Programming/IntelliJPlugins/<plugin-name>/

PLUGIN="${1:?Usage: $0 <plugin-name>}"
REPO_ROOT="$(cd "$(dirname "$0")" && pwd)"
SESSION="plugins"
WORKTREE="$REPO_ROOT/$PLUGIN"

if ! tmux has-session -t "$SESSION" 2>/dev/null; then
  echo "Error: tmux session '$SESSION' does not exist."
  echo "Run ./plugins-tmux.sh first, or create it manually."
  exit 1
fi

if [ ! -d "$WORKTREE" ]; then
  echo "Error: Worktree $WORKTREE not found."
  echo "Run ./plugin-worktree.sh $PLUGIN to create it."
  exit 1
fi

# Check if window already exists
if tmux list-windows -t "$SESSION" -F '#{window_name}' | grep -qx "$PLUGIN"; then
  echo "Window '$PLUGIN' already exists in session '$SESSION'."
  tmux select-window -t "$SESSION:$PLUGIN"
  exit 0
fi

# Create window with 3-pane layout
tmux new-window -t "$SESSION" -n "$PLUGIN" -c "$WORKTREE"
tmux split-window -t "$SESSION:$PLUGIN" -v -l 35% -c "$WORKTREE"
tmux split-window -t "$SESSION:$PLUGIN.1" -h -l 50% -c "$WORKTREE"

# Start lazygit in bottom-left, claude in top
tmux send-keys -t "$SESSION:$PLUGIN.1" "lazygit" C-m
tmux send-keys -t "$SESSION:$PLUGIN.0" "claude" C-m

echo "==> Added window '$PLUGIN' to session '$SESSION'"
