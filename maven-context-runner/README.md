# Maven Context Runner

An IntelliJ IDEA plugin that runs Maven goals for the module containing the current file.

## Features

When you're editing a file inside a Maven project, press:
- **Option+V** (Mac) / **Alt+V** (Windows/Linux): Run `mvn verify`
- **Option+T** (Mac) / **Alt+T** (Windows/Linux): Run `mvn test`
- **Option+F** (Mac) / **Alt+F** (Windows/Linux): Run `mvn spotless:apply`

The plugin automatically detects which Maven module contains your current file and runs the goal in that module's directory.

## Building

```bash
./gradlew buildPlugin
```

The plugin ZIP will be in `build/distributions/`.

## Installing

1. Build the plugin (see above)
2. In IntelliJ IDEA: **Settings → Plugins → ⚙️ → Install Plugin from Disk...**
3. Select the ZIP file from `build/distributions/`
4. Restart IntelliJ IDEA

## Development

To run a sandboxed IntelliJ IDEA instance with the plugin:

```bash
./gradlew runIde
```

## Customizing Keyboard Shortcuts

After installing, you can change the shortcuts in:
**Settings → Keymap → search for "Maven Context Runner"**

## Adding More Goals

Edit `RunMavenGoalAction.kt` and add new classes:

```kotlin
class MavenCleanAction : RunMavenGoalAction("clean", "Maven Clean")
class MavenInstallAction : RunMavenGoalAction("install", "Maven Install")
```

Then register them in `plugin.xml`:

```xml
<action id="MavenContextRunner.Clean"
        class="com.example.mavencontextrunner.MavenCleanAction"
        text="Maven Clean (Current Module)">
    <keyboard-shortcut keymap="$default" first-keystroke="alt C"/>
</action>
```

## Requirements

- IntelliJ IDEA 2024.1 or later (Ultimate or Community with Maven plugin)
- Maven projects must be imported/registered in IntelliJ
