# LFP Build Plugin

A Gradle **settings** plugin that:
- Discovers and includes subprojects by scanning the repo for `build.gradle(.kts)` files while respecting `.gitignore`
- Configures dependency repositories at the **settings** level using `RepositoriesMode.PREFER_SETTINGS`
- Loads one or more default version catalogs from the plugin classpath and **auto wires** libraries into appropriate configurations using `autoConfigOptions`
- Seeds new projects with a package directory under `src/main/java` or `src/main/kotlin`
- Contributes a low precedence `logback.xml` during `processResources` that you can override
- Exposes handy metadata on each project via `project.extra`

Minimums: Gradle 8.14, Java 17

## Quick start

### Using JitPack in `settings.gradle.kts`

```kotlin
pluginManagement {
    repositories {
        maven { url = uri("https://jitpack.io") }
        gradlePluginPortal()
        mavenCentral()
    }
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "com.github.regbo.lfp-build") {
                useModule("com.github.regbo:lfp-build:${'$'}{requested.version}")
            }
        }
    }
}

plugins {
    // use a tag, release, or commit hash
    id("com.github.regbo.lfp-build") version "<version>"
}
```

### Using a local checkout

```kotlin
pluginManagement {
    includeBuild("../lfp-build") // path to this repo
}
plugins {
    id("com.github.regbo.lfp-build")
}
```

## What the plugin does

### Repository management

At settings time the plugin configures
```kotlin
settings.dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        mavenCentral()
    }
}
```
Add any extra repositories you need in `settings.gradle(.kts)` under `dependencyResolutionManagement`. Project level repositories are ignored when `PREFER_SETTINGS` is used.

### Subproject discovery

The plugin walks the file tree from the repo root and includes any folder that contains `build.gradle` or `build.gradle.kts`. Discovery behavior:
- Respects `.gitignore` files at every directory level, including nested ones
- Skips hidden directories and common temp folders: `build`, `temp`, `tmp`
- Determines the Gradle project path from the relative directory path
- Derives a human readable project name from path segments and the `group`

Internally this is implemented with JGit (`IgnoreNode` and `FastIgnoreRule`).

### Source package seeding

If a project has no `src/main/java` or `src/main/kotlin` for the computed package, the plugin creates it. The package segments come from `group` and the project name segments. This is a no-op if the paths already exist.

### Default `logback.xml`

During `processResources` the plugin contributes a small `logback.xml` via an additional `from` spec and sets:
```kotlin
duplicatesStrategy = DuplicatesStrategy.INCLUDE
```
This gives the contributed file **lowest precedence**. If you place your own `src/main/resources/logback.xml`, or another task adds one later, that file takes priority and overwrites the contributed one.

### Version catalogs and auto wiring

The plugin scans its classpath for resources named:
```
com/lfp/buildplugin/default.libs.versions.toml
```
For each found file it:
1. Parses the TOML into a Gradle version catalog
2. Reads `autoConfigOptions` from each library entry
3. Removes `autoConfigOptions` from the catalog object so Gradle sees a normal catalog
4. Applies the catalog to every project and wires libraries according to the options

`autoConfigOptions` format is embedded per library alias in the catalog. Example:

```toml
[libraries]
# platform example
spring-boot-bom = { module = "org.springframework.boot:spring-boot-dependencies", version = "3.5.4", autoConfigOptions = { platform = true, configurations = ["api"] } }

# implementation example
jackson-databind = { module = "com.fasterxml.jackson.core:jackson-databind", version.ref = "jackson", autoConfigOptions = { configurations = ["implementation"] } }

# lombok example with multiple configurations
lombok = { module = "org.projectlombok:lombok", version = "1.18.34", autoConfigOptions = { configurations = ["annotationProcessor", "compileOnly", "testAnnotationProcessor", "testCompileOnly"] } }
```

Supported `autoConfigOptions` fields:
- `enabled` boolean, default true
- `platform` boolean, wraps the dependency with `dependencies.platform(...)`
- `configurations` list of configuration names or regex patterns
- `strictConfigurations` boolean, when true only `configurations` are used. When false the plugin applies simple fallbacks

Default configuration resolution when `configurations` is not set:
- If `platform = true` the defaults are `api` and `testImplementation`
- Otherwise the default is `api`

Fallbacks when `strictConfigurations = false`:
- `api` falls back to `implementation`
- `implementation` falls back to `testImplementation`

Version handling:
- If a library has no version in the catalog it will be added using only `group` and `name`. This is allowed only when `platform = false`
- Platform libraries must declare a version in the catalog. If missing the plugin fails fast

Libraries are applied in two passes with platform entries first so that alignment is in place before implementations.

### Project metadata

For convenience the plugin exposes these values on each project:
- `project.extra["projectPathSegments"]` list of path segments from the repo root
- `project.extra["projectNameSegments"]` list of normalized name segments
- `project.extra["packageDirSegments"]` list of directory segments used for the package under `src/main/...`

### BuildConfig for the plugin itself

This repo uses the `com.github.gmazzo.buildconfig` plugin to generate constants that the plugin uses at runtime:
- `plugin_package_name`
- `plugin_name`

You do not need to configure these in consumer projects.

## Tips

- To skip a directory from discovery add it to a `.gitignore` near that directory
- To override the default `logback.xml` just add your own file under `src/main/resources`
- To disable a catalog entry set `autoConfigOptions.enabled = false` for that library
- To model a BOM use `platform = true` and pick the configurations that should receive it

## Development

- Java 17
- Gradle 8.14
- Run `./gradlew build`
- Tests use JUnit 5
