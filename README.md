# LFP Build Plugin

The **LFP Build Plugin** is a lightweight Gradle plugin designed to streamline multi-module project setups. It automatically discovers subprojects, creates package-aligned source directory structures, and injects dependencies from a version catalog based on optional flags like `enforcedPlatform` and `testImplementation`.

## Features

- Automatically includes subprojects by scanning the root directory for `build.gradle` or `build.gradle.kts` files.
- Generates `src/main/java` or `src/main/kotlin` directories based on your project's group and name.
- Injects libraries from a version catalog into `api`, `implementation`, and `testImplementation` configurations.
- Supports `enforcedPlatform` and `testImplementation` flags for version catalog libraries.
- Infers plugin ID from Gradle properties: `repository_group`, `repository_owner`, and `repository_name`.

## Usage with JitPack

This plugin can be consumed directly using JitPack.

### Add JitPack to your pluginManagement block:

```kotlin
pluginManagement {
    repositories {
        maven { url = uri("https://jitpack.io") }
        gradlePluginPortal()
    }
}
```

### Apply the plugin in your build script:

```kotlin
plugins {
    id("com.github.regbo.lfp-build") version("<COMMIT or TAG or BRANCH>")
}
```

Replace `<COMMIT or TAG or BRANCH>` with the Git reference you want to use.

## Required Properties

Define the following properties in `gradle.properties` or pass them via `-P`:

```properties
repository_group=com.example
repository_owner=yourgithubuser
repository_name=your-project-name
plugin_implementation_class_name=com.example.BuildPlugin
```

## Version Catalog Integration

The plugin expects a version catalog file named `libs.versions.toml` at the root. It parses the file, removes `enforcedPlatform` and `testImplementation` flags, and rewrites the file with a content hash to avoid redundant writes. The parsed flags are stored in `project.extra` and used to configure dependencies appropriately.

### Defining Libraries in `libs.versions.toml`

You can define your libraries using the TOML format. Example:

```toml
[versions]
junit = "5.9.1"
logback = "1.4.11"
commons-lang3 = "3.12.0"

[libraries]
junit-bom = { module = "org.junit:junit-bom", version.ref = "junit", enforcedPlatform = true, testImplementation = true }
junit-jupiter = { module = "org.junit.jupiter:junit-jupiter", testImplementation = true }
logback-classic = { module = "ch.qos.logback:logback-classic", version.ref = "logback" }
apache-commons-lang3 = { module = "org.apache.commons:commons-lang3", version.ref = "commons-lang3" }
```

- `enforcedPlatform = true` will apply the dependency via `enforcedPlatform(...)`.
- `testImplementation = true` will scope the dependency only for test use.

All remaining libraries will be added to `api`, `implementation`, and `testImplementation` unless otherwise specified.
