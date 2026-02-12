# Git File Checkout

An IntelliJ IDEA plugin that checks out a single file from any branch or commit hash, without switching branches. The file contents are replaced in-place as an unstaged modification (undoable with Cmd+Z).

## Usage

1. Open a file in the editor
2. **Cmd+Shift+A** → type "Checkout File from Branch"
3. Select a branch from the list or type a commit hash
4. The file is replaced with the version from that ref

The action is also available under **Tools → Checkout File from Branch/Commit**.

## Building

```bash
# From the repo root:
../install-plugin.sh git-file-checkout

# Or standalone:
./gradlew buildPlugin
```

The plugin ZIP will be in `build/distributions/`.

## Installing Manually

1. Build the plugin (see above)
2. In IntelliJ IDEA: **Settings → Plugins → ⚙️ → Install Plugin from Disk...**
3. Select the ZIP file from `build/distributions/`
4. Restart IntelliJ IDEA

## Development

To run a sandboxed IntelliJ IDEA instance with the plugin:

```bash
./gradlew runIde
```

## Requirements

- IntelliJ IDEA 2024.1 or later (Ultimate or Community with Git plugin)
- The file must be inside a Git repository
