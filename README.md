# LFP Build Plugin

A Gradle settings plugin for multi module repos

* Discovers and includes subprojects by scanning the repo for `build.gradle` or `build.gradle.kts` while respecting `.gitignore`
* Sets dependency repositories at the settings level using `RepositoriesMode.PREFER_SETTINGS` and adds Maven Central
* Loads one or more version catalogs from the plugin classpath and can auto wire libraries to configurations using `autoConfigOptions`
* Seeds new modules with a package directory under `src/main/java` or `src/main/kotlin`
* Contributes a default `logback.xml` with low precedence during `processResources` so a module’s own file wins
* Exposes helpful metadata on each project via `project.extra`
* Minimums: Gradle 8.14 and Java 17

## Install

Using a published build from JitPack

```kotlin
// settings.gradle.kts
pluginManagement {
    repositories {
        maven { url = uri("https://jitpack.io") }
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    id("com.github.regbo.lfp-build") version "<git-hash or tag>"
}
```

Using a local checkout

```kotlin
// settings.gradle.kts
pluginManagement {
    includeBuild("../lfp-build")
}
plugins {
    id("com.github.regbo.lfp-build")
}
```

No extra configuration is required. Apply the plugin in `settings.gradle.kts` only.

## What it does at a glance

### Project discovery

* Walks the repository starting at the root
* Skips paths that are hidden or match common build folders such as `build`, `temp`, `tmp`
* Merges `.gitignore` rules from the root down using JGit and ignores matching files or directories
* Includes any directory that contains a valid Gradle build file

### Repositories

* Configures `dependencyResolutionManagement` in settings
* Uses `RepositoriesMode.PREFER_SETTINGS`
* Adds Maven Central

### Version catalogs and auto wiring

* Loads `gradle/libs.versions.toml` resources from the plugin classpath
* Supports property placeholders like `${spring-boot}` which are resolved from Gradle properties
* Writes a cleaned copy of each catalog under `build/generated/version-catalog/<hash>/libs.versions.toml`
* After each project is created, applies dependencies for every catalog alias using optional `autoConfigOptions`

`autoConfigOptions` fields

* `enabled` controls whether the alias is applied
* `platform` adds the alias using `dependencies.platform(...)`
* `configurations` optional list of configuration name patterns. Regular expressions are supported
* `strictConfigurations` when true, only the listed configurations are used. When false, a small default mapping is used as a fallback

  * Default fallback mapping

    * `api` goes to `implementation`
    * `implementation` goes to `testImplementation`

Example `libs.versions.toml`

```toml
[versions]
spring-boot = "3.5.4"

[libraries]
spring-core = { module = "org.springframework:spring-core" }

# Example of an alias that will be added as a platform first
spring-bom = { module = "org.springframework.boot:spring-boot-dependencies", version.ref = "spring-boot", autoConfigOptions = { platform = true, configurations = ["implementation|testImplementation"] } }

# Example of an alias without a version. Version will be controlled by the platform above
jackson-databind = { module = "com.fasterxml.jackson.core:jackson-databind", autoConfigOptions = { configurations = ["implementation"] } }
```

Notes

* If `platform = true` the alias must have a version
* If an alias has no version and `platform = false` it is added as a `group` and `name` map so a BOM can control the version
* Application order is platforms first then non platforms

### Seeding source layout

For each included subproject without existing sources

* Creates `src/main/java` or `src/main/kotlin` directories using a derived package path
* Derivation uses the project group if set and falls back to the root group. It also uses name segments from the project path
* Example

  * Root group `com.example`
  * Module path `:modules:my-service`
  * Created package path `com/example/my/service`

### Default logging config

* Adds a simple `logback.xml` to `processResources` with `DuplicatesStrategy.INCLUDE`
* If the module already has `src/main/resources/logback.xml` nothing is added
* If multiple tasks contribute `logback.xml` the last one configured wins

### Project metadata

Each project gets the following extras

* `project.extra["projectPathSegments"]` a list of tokens from the project path
* `project.extra["projectNameSegments"]` a list of tokens from the project name
* `project.extra["packageDirSegments"]` the derived package directory segments used for seeding sources

## Building this plugin

* Java 17 and Gradle 8.14
* `./gradlew build`
* The plugin coordinates are built from `gradle.properties`

  * `repository_group`, `repository_owner`, `repository_name` form the plugin id `com.github.<owner>.<name>`
  * `plugin_implementation_class` sets the implementation class and the default plugin name

## Tips

* To keep a directory out of discovery, add it to a `.gitignore` near that directory
* To override the default `logback.xml` add your own file under `src/main/resources`
* To disable a catalog entry set `autoConfigOptions.enabled = false`
* Use a `platform` alias first when you want its BOM to control versions for other aliases
