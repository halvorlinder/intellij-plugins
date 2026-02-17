# CLAUDE.md — IntelliJ Plugins Monorepo

## Overview

This repo contains custom IntelliJ IDEA plugins. Each subdirectory with a `build.gradle.kts` is an independent plugin with its own Gradle wrapper.

## Workflow

When the user describes a problem or desired behavior:

1. **Decide** which existing plugin to modify — or create a new plugin if none fits
2. **Implement** the changes (Kotlin, IntelliJ Platform SDK)
3. **Build & install** using `./install-plugin.sh <plugin-dir>`
4. **Commit** the changes

## Existing Plugins

| Directory | Plugin | Description |
|---|---|---|
| `git-file-checkout` | Git File Checkout | Check out a single file from any branch/commit without switching branches. Replaces the file in-place as an unstaged change (Cmd+Z to undo). |
| `maven-context-runner` | Maven Context Runner | Run Maven goals (verify, test, clean, spotless:apply) on the module containing the current file. All commands except Spotless Apply run spotless:apply first. Shortcuts: Option+V, Option+T, Option+C, Option+F. |
| `kotlin-type-info` | Kotlin Type Info | Show fully resolved type info for Kotlin expressions (substitutes generic type parameters). Bind via IdeaVim: `nmap gH :action KotlinTypeInfo.ShowResolvedTypeInfo<CR>`. |
| `vscode-window` | VSCode Window | VSCode-like window management: Cmd+0 focus project tree, Cmd+1/2/3 focus editor groups by position, Cmd+B/J toggle side/bottom panels with memory, Cmd+\ split and move file right, Cmd+Shift+\ equalize split widths, Space/Option+Space to preview files and toggle folders in project tree. |
| `usage-preview` | Usage Preview | Show usages with syntax-highlighted editor preview. Dual-pane popup: usage list (left) + live editor preview (right). IdeaVim: `gd` go-to-definition/show usages, `gu` always show usages. |
| `command-palette` | Command Palette | VSCode-like command palette: opens with empty search, shows recent commands first, multi-token filtering. Shortcut: Cmd+Shift+P. |

## Build Stack

- **Gradle** 8.5 (each plugin has its own wrapper)
- **Kotlin** 1.9.22 (2.1.0 for `kotlin-type-info`), JVM toolchain 17
- **IntelliJ Platform Gradle Plugin** 2.2.1
- **Target platform**: IntelliJ IDEA Ultimate 2024.1+ (`sinceBuild = "241"`, no upper bound). `kotlin-type-info` targets 2025.1+ (`sinceBuild = "251"`) and requires K2 mode.
- **Java 21** required to run Gradle: `export JAVA_HOME=$(/usr/libexec/java_home -v 21)`

## Build & Install Commands

```bash
# Build and install a single plugin
./install-plugin.sh <plugin-dir>

# Build and install all plugins
./install-all-plugins.sh

# Build only (no install)
cd <plugin-dir> && ./gradlew buildPlugin
```

The install scripts copy the built plugin to:
`~/Library/Application Support/JetBrains/IntelliJIdea2025.2/plugins/`

## Creating a New Plugin

1. Copy the structure from an existing plugin (build.gradle.kts, gradle.properties, settings.gradle.kts, gradle wrapper, src/)
2. Update `settings.gradle.kts` with the new project name
3. Update `gradle.properties` with new pluginGroup, pluginName, pluginVersion
4. Adjust `build.gradle.kts` dependencies (bundledPlugin entries) as needed
5. Create source files under `src/main/kotlin/` and `src/main/resources/META-INF/plugin.xml`
6. Test with `./install-plugin.sh <new-plugin-dir>`
7. Update this table in CLAUDE.md

## Key Conventions

- Plugin source: `src/main/kotlin/com/example/<pluginpackage>/`
- Plugin descriptor: `src/main/resources/META-INF/plugin.xml`
- Actions are registered in `plugin.xml` under `<actions>`
- Use `WriteCommandAction` for file modifications, `runBackgroundableTask` for long operations
- SSH host alias for GitHub: `github.com-halvorlinder`

## Git

- Remote: `git@github.com-halvorlinder:halvorlinder/intellij-plugins.git`
- Always commit after successful build+install verification
