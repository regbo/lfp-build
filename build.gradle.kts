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
}

// === Declare implementation and test dependencies ===
dependencies {
    implementation(libs.apache.commons.lang3)
    implementation(libs.apache.commons.lang3)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
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

// === Compute the plugin ID from gradle.properties values ===
val pluginId = providers.provider {
    listOf("repository_group", "repository_owner", "repository_name")
        .map { providers.gradleProperty(it).getOrElse("") }
        .filter { it.isNotEmpty() }
        .joinToString(".")
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

// === Configure test task to use JUnit 5 (via Jupiter) ===
tasks.test {
    useJUnitPlatform()
}
