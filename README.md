# IntelliJ Plugins

A collection of custom IntelliJ IDEA plugins.

| Plugin | Description |
|---|---|
| [git-file-checkout](git-file-checkout/) | Check out a single file from any branch or commit without switching branches |
| [maven-context-runner](maven-context-runner/) | Run Maven goals on the module containing the current file via keyboard shortcuts |
| [kotlin-type-info](kotlin-type-info/) | Show fully resolved type info for Kotlin expressions with substituted generic type parameters |
| [vscode-window](vscode-window/) | VSCode-like window and panel management with keyboard shortcuts |

## Requirements

- IntelliJ IDEA 2024.1+ (2025.1+ for kotlin-type-info)
- Java 21 (for building)

## Building & Installing

Install a single plugin:

```bash
./install-plugin.sh git-file-checkout
```

Install all plugins:

```bash
./install-all-plugins.sh
```

Restart IntelliJ IDEA after installing to pick up changes.

## Manual Install

Each plugin can also be built independently:

```bash
cd <plugin-dir>
./gradlew buildPlugin
```

Then install the ZIP from `build/distributions/` via **Settings → Plugins → ⚙️ → Install Plugin from Disk...**.
