# Usage Preview

Show usages of a symbol with a syntax-highlighted editor preview. A dual-pane popup displays the usage list on the left and a live editor preview on the right.

## Features

- **gd** (go to definition): If the caret is on a reference, jumps to the definition. If already on the definition, shows the usages popup.
- **gu** (go to usages): Always shows the usages popup regardless of whether the caret is on a definition or reference.
- Syntax-highlighted read-only editor preview that updates as you navigate the list
- Usage line is highlighted in the preview
- Enter navigates to the selected usage, Escape closes the popup

## IdeaVim Setup

Add the following to your `~/.ideavimrc`:

```vim
nmap gd :action UsagePreview.ShowUsagesWithPreview<CR>
nmap gu :action UsagePreview.ShowUsagesAlways<CR>
```

## Install

```bash
./install-plugin.sh usage-preview
```

Restart IntelliJ IDEA to pick up changes.
