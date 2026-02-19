# IntelliJ Plugins

A collection of custom IntelliJ IDEA plugins.

| Plugin | Description |
|---|---|
| [command-palette](command-palette/) | VSCode-like command palette with empty search, recent commands, and multi-token filtering |
| [compile-errors](compile-errors/) | Show Maven compilation errors in a navigable dual-pane popup with editor preview |
| [git-file-checkout](git-file-checkout/) | Check out a single file from any branch or commit without switching branches |
| [kotlin-type-info](kotlin-type-info/) | Show fully resolved type info for Kotlin expressions with substituted generic type parameters |
| [maven-context-runner](maven-context-runner/) | Run Maven goals on the module containing the current file via keyboard shortcuts |
| [usage-preview](usage-preview/) | Show usages with a syntax-highlighted editor preview popup and tree view |
| [vscode-window](vscode-window/) | VSCode-like window and panel management with keyboard shortcuts |

[shared-ui](shared-ui/) is a shared library used by `compile-errors` and `usage-preview` for the dual-pane popup UI.

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
