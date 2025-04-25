import com.lfp.buildplugin.shared.LibraryAutoConfigOptions
import com.lfp.buildplugin.shared.Utils
import com.lfp.buildplugin.shared.VersionCatalog


// === Repositories used for resolving plugins and dependencies ===

repositories {
    gradlePluginPortal()
    mavenCentral()
}

// === Plugins used in the plugin project ===
plugins {
    `kotlin-dsl`                       // Enables Kotlin DSL support in the build script
    `java-gradle-plugin`              // Allows declaring and publishing a Gradle plugin
    `maven-publish`                   // Adds support for publishing to Maven repositories
    alias(libs.plugins.buildconfig)
}

// === Configure Java toolchain based on a Gradle property (java_version) ===
val javaVersion = providers.gradleProperty("java_version").map { it.toInt() }


java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(javaVersion.get()))
    }
}

kotlin {
    jvmToolchain(javaVersion.get()) // Applies the same JVM target to Kotlin compilation
}



kotlin {
    sourceSets {
        getByName("main") {
            kotlin.srcDir("buildSrc/src/main/kotlin")
        }
    }
}


// === Declare implementation and test dependencies ===
dependencies {
    testImplementation(platform("org.junit:junit-bom:5.9.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
libs.libraryAliases.forEach { alias ->
    val dep = libs.findLibrary(alias).get().get()
    LibraryAutoConfigOptions().add(project, dep)
}


// === Compute the plugin ID from gradle.properties values ===
val pluginId = providers.provider {
    listOf("repository_group", "repository_owner", "repository_name").map { providers.gradleProperty(it).getOrElse("") }
        .filter { it.isNotEmpty() }.joinToString(".")
}

// === Resolve the plugin implementation class and extract the plugin name ===
val pluginImplementationClassName = providers.gradleProperty("plugin_implementation_class_name")
val pluginName = pluginImplementationClassName.map { it.substringAfterLast('.') }

// === Set the group to the package of the plugin implementation class ===
group = pluginImplementationClassName.get().substringBeforeLast(".")

// === Register the plugin with the given ID and implementation class ===
gradlePlugin {
    plugins {
        register(pluginName.get()) {
            id = pluginId.get()
            implementationClass = pluginImplementationClassName.get()
        }
    }
}

// === Generate a BuildConfig class with constants from properties and the version catalog ===
buildConfig {
    packageName(group as String)
    className(pluginName.get() + "BuildConfig")

    // Include all valid Gradle properties (as Strings or Numbers) as constants
    properties.keys.forEach { key ->
        if (key.matches("^[a-zA-Z_\\$][a-zA-Z0-9_\\$]*$".toRegex())) {
            when (val value = property(key)) {
                is Number -> buildConfigField(key, value)
                is String -> buildConfigField(key, value)
            }
        }
    }
    buildConfigField(
        "plugin_package_name", providers
            .gradleProperty("plugin_implementation_class_name")
            .map { it.substringBeforeLast(".") })
}


// === Configure test task to use JUnit 5 (via Jupiter) ===
tasks.test {
    useJUnitPlatform()
}
