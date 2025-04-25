# LFP Build Plugin

The LFP Build Plugin is a lightweight Gradle plugin designed to streamline multi-module project setups. It automatically discovers subprojects, creates package-aligned source directory structures, and injects dependencies from a version catalog using configuration options like `enforcedPlatform` or scoped `configurations`.

## Features

- Automatically includes subprojects by scanning the root directory for `build.gradle` or `build.gradle.kts` files.
- Generates `src/main/java` or `src/main/kotlin` directories based on your project's group and name.
- Injects libraries from a version catalog into Gradle configurations like `api`, `implementation`, `testImplementation`, `annotationProcessor`, etc.
- Uses `autoConfigOptions` metadata in `libs.versions.toml` to determine how each dependency should be applied.
- Infers plugin ID from Gradle properties: `repository_group`, `repository_owner`, and `repository_name`.
- Generates a `BuildConfig` class containing Gradle properties and metadata.

## Usage with JitPack

This plugin can be consumed directly using JitPack.

### Add JitPack to your `pluginManagement` block:

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

## Version Catalog Integration

The plugin expects a version catalog file named `libs.versions.toml` at the root. It parses the file and rewrites it with `autoConfigOptions` metadata removed, writing the cleaned version to a hashed output location. Parsed configuration instructions are then used to apply dependencies to the appropriate configurations.

### Defining Libraries in `libs.versions.toml`

Libraries can be annotated with `autoConfigOptions` to control how they're added to the project.

Example:

```toml
[versions]
spring-boot = "3.3.5"

[libraries]
# annotations
lombok = { module = "org.projectlombok:lombok", version = "1.18.32", autoConfigOptions = { configurations = ["annotationProcessor", "compileOnly", "testAnnotationProcessor", "testCompileOnly"] } }

# platform implementation
spring-boot-bom = { module = "org.springframework.boot:spring-boot-dependencies", version.ref = "spring-boot", autoConfigOptions = { enforcedPlatform = true } }

# implementation
apache-commons-lang3 = { module = "org.apache.commons:commons-lang3", version = "3.17.0" }
logback-classic = { module = "ch.qos.logback:logback-classic" }
apache-logging-log4j-to-slf4j = { module = "org.apache.logging.log4j:log4j-to-slf4j" }
slf4j-jul-to-slf4j = { module = "org.slf4j:jul-to-slf4j" }
one-util-streamex = { module = "one.util:streamex", version = "0.8.3" }

# platform testImplementation
junit-bom = { module = "org.junit:junit-bom", version = "5.9.1", autoConfigOptions = { enforcedPlatform = true } }

# testImplementation
junit-jupiter = { module = "org.junit.jupiter:junit-jupiter", autoConfigOptions = { configurations = ["testImplementation"] } }
```

### Configuration Behavior

- `enforcedPlatform = true` causes the dependency to be applied using `enforcedPlatform(...)`.
- `configurations = [...]` controls which configurations the dependency is added to. If not specified:
    - `api` is used by default
    - If `enforcedPlatform = true`, `implementation` is used
- If `strictConfigurations = true` is also present, no fallbacks will be used.

This allows fine-grained control over how each version catalog entry is applied to your project.
