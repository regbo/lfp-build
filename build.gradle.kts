import com.lfp.buildplugin.shared.LibraryAutoConfigOptions

// === Repository configuration for resolving plugins and dependencies ===
repositories {
    gradlePluginPortal() // Gradle Plugin Portal for community plugins
    mavenCentral()       // Maven Central for standard dependencies
}

// === Plugins required for building and publishing this Gradle plugin ===
plugins {
    `kotlin-dsl`                      // Enable Kotlin DSL for build scripts
    `maven-publish`                   // Enable publishing artifacts to Maven repositories
    alias(libs.plugins.buildconfig)   // BuildConfig generation via catalog plugin alias
}

// === Java and Kotlin toolchain configuration ===
val javaVersion = providers.gradleProperty("java_version").map { it.toInt() }

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(javaVersion.get()))
    }
}

kotlin {
    jvmToolchain(javaVersion.get())
    sourceSets {
        getByName("main") {
            // Include additional Kotlin sources from buildSrc
            kotlin.srcDir("buildSrc/src/main/kotlin")
        }
    }
}

// === Test dependencies ===
dependencies {
    testImplementation(platform("org.junit:junit-bom:5.9.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

// === Apply all dependencies from the version catalog automatically ===
val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
libs.libraryAliases.forEach { alias ->
    val dep = libs.findLibrary(alias).get().get()
    LibraryAutoConfigOptions().add(project, dep)
}

// === Plugin metadata construction ===
val pluginId = providers.provider {
    listOf("repository_group", "repository_owner", "repository_name")
        .map { providers.gradleProperty(it).getOrElse("") }
        .filter { it.isNotEmpty() }
        .joinToString(".")
}

val pluginImplementationClass = providers.gradleProperty("plugin_implementation_class")
val pluginName = pluginImplementationClass.map { it.substringAfterLast('.') }
group = pluginImplementationClass.get().substringBeforeLast(".")

// === Plugin registration ===
gradlePlugin {
    plugins {
        register(pluginName.get()) {
            id = pluginId.get()
            implementationClass = pluginImplementationClass.get()
        }
    }
}

// === BuildConfig generation ===
buildConfig {
    packageName(group as String)
    className(pluginName.get() + "BuildConfig")

    // Include gradle.properties entries as constants (only valid Java identifiers)
    properties.keys.forEach { key ->
        if (key.matches("^[a-zA-Z_\\$][a-zA-Z0-9_\\$]*$".toRegex())) {
            when (val value = property(key)) {
                is Number -> buildConfigField(key, value)
                is String -> buildConfigField(key, value)
            }
        }
    }

    // Include the plugin package name as a constant
    buildConfigField(
        "plugin_package_name",
        pluginImplementationClass.map { it.substringBeforeLast(".") }
    )
}

// === Test task configuration ===
tasks.test {
    useJUnitPlatform()
}
