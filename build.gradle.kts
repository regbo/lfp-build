repositories {
    gradlePluginPortal()
    mavenCentral()
}

plugins {
    `kotlin-dsl`                       // Enables Kotlin DSL support for Gradle
    `java-gradle-plugin`              // Registers a Gradle plugin
    `maven-publish`                   // Enables publishing to Maven repositories
    id("com.github.gmazzo.buildconfig") version "5.6.2" // For generating BuildConfig-like constants
}

// === Dependencies for implementation and testing ===
dependencies {
    implementation(libs.apache.commons.lang3)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
}

// === Compute plugin ID from gradle.properties values ===
val pluginId = providers.provider {
    listOf("repository_group", "repository_owner", "repository_name")
        .map { providers.gradleProperty(it).getOrElse("") }
        .filter { it.isNotEmpty() }
        .joinToString(".")
}

// === Resolve plugin implementation class name and plugin name ===
val pluginImplementationClassName = providers.gradleProperty("plugin_implementation_class_name")
val pluginName = pluginImplementationClassName.map { it.substringAfterLast('.') }

// === Set group from the package of the plugin implementation class ===
group = pluginImplementationClassName.get().substringBeforeLast(".")


// === Gradle plugin declaration ===
gradlePlugin {
    plugins {
        register(pluginName.get()) {
            id = pluginId.get()
            implementationClass = pluginImplementationClassName.get()
        }
    }
}

// === Access to the shared version catalog ===
val versionCatalog = extensions.getByType<VersionCatalogsExtension>().named("libs")

// === Generate BuildConfig class with useful constants ===
buildConfig {
    // Package and class name for the generated BuildConfig
    packageName(group as String)
    className(pluginName.get() + "BuildConfig")

    // Inject all valid Gradle properties as constants
    properties.keys.forEach { key ->
        if (key.matches("^[a-zA-Z_\\$][a-zA-Z0-9_\\$]*$".toRegex())) {
            val value = property(key)
            when (value) {
                is Number -> buildConfigField(key, value)
                is String -> buildConfigField(key, value)
            }
        }
    }

    // Add version catalog library coordinates as a map field
    buildConfigField(
        "versionCatalogLibraries",
        versionCatalog.libraryAliases.associateWith { alias ->
            val dep = versionCatalog.findLibrary(alias).get().get()
            "${dep.module}:${dep.version ?: ""}"
        }
    )

    // Inject custom extras (collected earlier in settings.gradle.kts)
    listOf("versionCatalogEnforcedPlatformAliases", "versionCatalogTestImplementationAliases").forEach { name ->
        @Suppress("UNCHECKED_CAST")
        buildConfigField(name, project.extra[name] as Set<String>)
    }
}

// === Test configuration ===
tasks.test {
    useJUnitPlatform() // Enables JUnit 5
}
