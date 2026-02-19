#!/usr/bin/env bash
set -euo pipefail

# Launch a tmux session with one window per plugin worktree.
#
# Window 0 "main": single pane in the main/ worktree (control window)
# Windows 1-7: one per plugin, each with 3 panes:
#   - Top (65%): claude (Claude Code agent)
#   - Bottom-left (50%): lazygit
#   - Bottom-right: empty terminal

REPO_ROOT="$(cd "$(dirname "$0")" && pwd)"
SESSION="plugins"

PLUGINS=(command-palette compile-errors git-file-checkout kotlin-type-info maven-context-runner usage-preview vscode-window)

# Kill existing session if present
if tmux has-session -t "$SESSION" 2>/dev/null; then
  read -p "Session '$SESSION' already exists. Kill and recreate? [y/N] " -n 1 -r
  echo
  if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo "Attaching to existing session..."
    exec tmux attach -t "$SESSION"
  fi
  tmux kill-session -t "$SESSION"
fi

# ── Window 0: main (control window) ───────────────────────────────────────────

tmux new-session -d -s "$SESSION" -n "main" -c "$REPO_ROOT/main"

# ── Windows 1-7: per-plugin worktrees ─────────────────────────────────────────

for plugin in "${PLUGINS[@]}"; do
  WORKTREE="$REPO_ROOT/$plugin"

  if [ ! -d "$WORKTREE" ]; then
    echo "Warning: Worktree $WORKTREE not found, skipping $plugin"
    continue
  fi

  # Create window
  tmux new-window -t "$SESSION" -n "$plugin" -c "$WORKTREE"

  # Split bottom pane (35% height)
  tmux split-window -t "$SESSION:$plugin" -v -l 35% -c "$WORKTREE"

  # Split bottom into left/right
  tmux split-window -t "$SESSION:$plugin.1" -h -l 50% -c "$WORKTREE"

  # Pane layout is now:
  #   pane 0: top (main area)
  #   pane 1: bottom-left
  #   pane 2: bottom-right

  # Start lazygit in bottom-left
  tmux send-keys -t "$SESSION:$plugin.1" "lazygit" C-m

  # Start claude in top pane
  tmux send-keys -t "$SESSION:$plugin.0" "claude" C-m
done

# Select the main window
tmux select-window -t "$SESSION:main"

echo "==> tmux session '$SESSION' created with $(( ${#PLUGINS[@]} + 1 )) windows"
echo "    Attaching..."

exec tmux attach -t "$SESSION"
