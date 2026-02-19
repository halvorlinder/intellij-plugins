#!/usr/bin/env bash
set -euo pipefail

# One-time migration: convert this repo from a regular git repo to a bare repo
# with per-plugin worktrees.
#
# Before running:
#   - Make sure `main` is clean and in sync with origin
#   - Run from the repo root: ~/Programming/IntelliJPlugins/

REPO_ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$REPO_ROOT"

PLUGINS=(command-palette compile-errors git-file-checkout kotlin-type-info maven-context-runner usage-preview vscode-window)

# ── Safety checks ──────────────────────────────────────────────────────────────

if [ -d ".bare" ]; then
  echo "Error: .bare/ already exists — looks like migration was already done."
  exit 1
fi

if [ ! -d ".git" ]; then
  echo "Error: No .git directory found. Run this from the repo root."
  exit 1
fi

if [ -f ".git" ]; then
  echo "Error: .git is already a file (not a directory). Migration may have been partially done."
  exit 1
fi

# Check for uncommitted changes
if ! git diff --quiet || ! git diff --cached --quiet; then
  echo "Error: You have uncommitted changes. Commit or stash them first."
  exit 1
fi

# Check for untracked files (excluding this script and other top-level scripts)
UNTRACKED=$(git ls-files --others --exclude-standard | grep -v -E '^(setup-bare-worktrees|plugins-tmux|plugin-window|plugin-worktree)\.sh$' || true)
if [ -n "$UNTRACKED" ]; then
  echo "Warning: Untracked files detected:"
  echo "$UNTRACKED"
  echo ""
  read -p "Continue anyway? [y/N] " -n 1 -r
  echo
  if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    exit 1
  fi
fi

echo "==> Converting to bare repo with worktrees..."

# ── Step 1: Move .git → .bare ─────────────────────────────────────────────────

mv .git .bare
echo "gitdir: .bare" > .git

# Configure the bare repo
git config core.bare false
git config core.worktree "$REPO_ROOT"

echo "    Moved .git/ → .bare/ and created .git pointer file"

# ── Step 2: Create main worktree ───────────────────────────────────────────────

# The current directory IS the working tree right now. We need to move all repo
# files into a main/ worktree. First, record what files exist.

REPO_FILES=$(git ls-files)
REPO_DIRS=$(echo "$REPO_FILES" | sed 's|/.*||' | sort -u)

# Create the main worktree using git worktree add
# We need to first move the current files out of the way, then create the worktree

# Create a temporary directory to hold current files
TMPDIR_MOVE=$(mktemp -d)

# Move all tracked directories and files to temp (but not .bare, .git, or our scripts)
for item in $REPO_DIRS; do
  if [ -e "$item" ] && [ "$item" != ".bare" ]; then
    mv "$item" "$TMPDIR_MOVE/"
  fi
done

# Also move top-level tracked files
for f in CLAUDE.md README.md .gitignore .claude; do
  if [ -e "$f" ]; then
    mv "$f" "$TMPDIR_MOVE/"
  fi
done

# Now create the main worktree
git worktree add main main 2>/dev/null || git worktree add main HEAD

# The worktree command checked out all files into main/. Remove the duplicates
# we moved to temp.
rm -rf "$TMPDIR_MOVE"

echo "    Created main/ worktree on branch 'main'"

# ── Step 3: Create per-plugin worktrees ────────────────────────────────────────

for plugin in "${PLUGINS[@]}"; do
  BRANCH="feature/$plugin"
  # Create branch from main if it doesn't exist
  if git show-ref --verify --quiet "refs/heads/$BRANCH"; then
    git worktree add "$plugin" "$BRANCH"
  else
    git worktree add "$plugin" -b "$BRANCH" main
  fi
  echo "    Created $plugin/ worktree on branch '$BRANCH'"
done

# ── Step 4: Clean up any leftover top-level files ─────────────────────────────

# Remove any remaining repo files from top-level that are now in worktrees
for item in $(ls -A); do
  case "$item" in
    .bare|.git|main|.DS_Store)
      continue
      ;;
    setup-bare-worktrees.sh|plugins-tmux.sh|plugin-window.sh|plugin-worktree.sh)
      continue
      ;;
  esac
  # Check if it's a worktree directory
  IS_WORKTREE=false
  for plugin in "${PLUGINS[@]}"; do
    if [ "$item" = "$plugin" ]; then
      IS_WORKTREE=true
      break
    fi
  done
  if [ "$IS_WORKTREE" = true ]; then
    continue
  fi
  # Remove leftover files/dirs that aren't worktrees or infrastructure
  rm -rf "$item"
done

echo ""
echo "==> Migration complete!"
echo ""
echo "Worktrees:"
git worktree list
echo ""
echo "Next steps:"
echo "  1. Run ./plugins-tmux.sh to launch the tmux workspace"
echo "  2. Each worktree is a full repo checkout — cd into any to work"
echo "  3. The main/ worktree is on branch 'main'"
echo "  4. Plugin worktrees are on 'feature/<plugin-name>' branches"
