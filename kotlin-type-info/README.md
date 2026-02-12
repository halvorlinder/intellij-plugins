# Kotlin Type Info

An IntelliJ IDEA plugin that shows fully resolved type information for Kotlin expressions. Unlike the built-in Type Info action, this inlines actual resolved types instead of showing generic signatures.

For example, hovering over `map` in `listOf("a").map { it.length }` shows:

```
fun Iterable<String>.map(transform: (String) -> Int): List<Int>
```

instead of the generic `fun <T, R> Iterable<T>.map(transform: (T) -> R): List<R>`.

## Usage

1. Place the cursor on a Kotlin expression
2. Trigger the action **Show Resolved Type Info** (via **Tools** menu or a keybinding)

Recommended IdeaVim binding:

```vim
nmap gH :action KotlinTypeInfo.ShowResolvedTypeInfo<CR>
```

The hint popup uses semantic syntax highlighting with your editor's color scheme and font.

## Building

```bash
# From the repo root:
./install-plugin.sh kotlin-type-info

# Or standalone:
./gradlew buildPlugin
```

The plugin ZIP will be in `build/distributions/`.

## Requirements

- IntelliJ IDEA 2025.1 or later (requires K2 Kotlin plugin mode)
- The file must be a Kotlin file (`.kt`)
