# VSCode Window

VSCode-like window and panel management for IntelliJ IDEA.

## Features

- **Cmd+0**: Focus/toggle Project tool window
- **Cmd+1/2/3**: Focus editor group 1/2/3 (sorted by visual left-to-right position)
- **Cmd+B**: Toggle side panel (remembers last active tool window)
- **Cmd+J**: Toggle bottom panel (remembers last active tool window)
- **Cmd+\**: Split and move current file to a new right split
- **Cmd+Shift+\**: Equalize all editor split widths
- **Space**: Preview file in project tree
- **Option+Space**: Toggle folder in project tree

## Setup

### Disable default split shortcut

IntelliJ's built-in **Split Vertically** action (also bound to Cmd+\) duplicates the file into both panes and creates uneven nested splits. You should disable it to avoid conflicts:

1. Go to **Settings > Keymap**
2. Search for **Split Vertically**
3. Right-click the shortcut and select **Remove**

The plugin's **Split and Move Right** (Cmd+\) replaces this with better behavior: the file moves to the new split instead of being duplicated, and the original pane shows the most recently used file.

After splitting, use **Cmd+Shift+\** to equalize all pane widths.

## Install

```bash
./install-plugin.sh vscode-window
```

Restart IntelliJ IDEA to pick up changes.
