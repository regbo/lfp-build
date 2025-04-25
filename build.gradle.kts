import com.lfp.buildplugin.shared.LibraryAutoConfigOptions

// === Repositories used for resolving plugins and dependencies ===
repositories {
    gradlePluginPortal()
    mavenCentral()
}

// === Plugins used for building and publishing this plugin ===
plugins {
    `kotlin-dsl`                      // Enables Kotlin DSL in build scripts
    `java-gradle-plugin`             // Allows defining and publishing Gradle plugins
    `maven-publish`                  // Enables publishing artifacts to Maven repositories
    alias(libs.plugins.buildconfig)  // Adds BuildConfig generation support via plugin alias
}

// === Configure Java and Kotlin toolchains using a Gradle property ===
val javaVersion = providers.gradleProperty("java_version").map { it.toInt() }

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(javaVersion.get()))
    }
}

kotlin {
    jvmToolchain(javaVersion.get())  // Applies the same JVM version to Kotlin compilation
    sourceSets {
        getByName("main") {
            kotlin.srcDir("buildSrc/src/main/kotlin") // Adds an extra Kotlin source directory
        }
    }
}

// === Declare test dependencies ===
dependencies {
    testImplementation(platform("org.junit:junit-bom:5.9.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

// === Automatically apply all libraries from the version catalog via custom config ===
val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
libs.libraryAliases.forEach { alias ->
    val dep = libs.findLibrary(alias).get().get()
    LibraryAutoConfigOptions().add(project, dep)
}

// === Construct plugin ID from gradle.properties values ===
val pluginId = providers.provider {
    listOf("repository_group", "repository_owner", "repository_name")
        .map { providers.gradleProperty(it).getOrElse("") }
        .filter { it.isNotEmpty() }
        .joinToString(".")
}

// === Extract plugin name and group from the implementation class name ===
val pluginImplementationClassName = providers.gradleProperty("plugin_implementation_class_name")
val pluginName = pluginImplementationClassName.map { it.substringAfterLast('.') }
group = pluginImplementationClassName.get().substringBeforeLast(".")

// === Register the plugin with its ID and implementation class ===
gradlePlugin {
    plugins {
        register(pluginName.get()) {
            id = pluginId.get()
            implementationClass = pluginImplementationClassName.get()
        }
    }
}

// === Generate a BuildConfig class using all valid Gradle properties ===
buildConfig {
    packageName(group as String)
    className(pluginName.get() + "BuildConfig")

    // Add all valid gradle.properties entries as constants
    properties.keys.forEach { key ->
        if (key.matches("^[a-zA-Z_\\$][a-zA-Z0-9_\\$]*$".toRegex())) {
            when (val value = property(key)) {
                is Number -> buildConfigField(key, value)
                is String -> buildConfigField(key, value)
            }
        }
    }

    // Also include the plugin package name
    buildConfigField(
        "plugin_package_name",
        pluginImplementationClassName.map { it.substringBeforeLast(".") }
    )
}

// === Configure the test task to use JUnit 5 ===
tasks.test {
    useJUnitPlatform()
}
