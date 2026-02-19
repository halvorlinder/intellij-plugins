#!/usr/bin/env bash
set -euo pipefail

# Create a git worktree for a plugin AND add a tmux window for it.
#
# Usage: ./plugin-worktree.sh <plugin-name>
#
# Creates worktree at ~/Programming/IntelliJPlugins/<plugin-name>/ on branch
# feature/<plugin-name> (forked from main), then adds a tmux window.

PLUGIN="${1:?Usage: $0 <plugin-name>}"
REPO_ROOT="$(cd "$(dirname "$0")" && pwd)"
WORKTREE="$REPO_ROOT/$PLUGIN"
BRANCH="feature/$PLUGIN"

# Create worktree if it doesn't exist
if [ -d "$WORKTREE" ]; then
  echo "Worktree $WORKTREE already exists."
else
  echo "==> Creating worktree for $PLUGIN on branch $BRANCH..."

  if git show-ref --verify --quiet "refs/heads/$BRANCH"; then
    git worktree add "$WORKTREE" "$BRANCH"
  else
    git worktree add "$WORKTREE" -b "$BRANCH" main
  fi

  echo "    Created $WORKTREE on branch '$BRANCH'"
fi

# Add tmux window
"$REPO_ROOT/plugin-window.sh" "$PLUGIN"
