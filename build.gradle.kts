import com.lfp.buildplugin.shared.LibraryAutoConfigOptions
import com.lfp.buildplugin.shared.Utils
import java.util.stream.Stream

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
    listOf("repository_group", "repository_owner", "repository_name").map { providers.gradleProperty(it).getOrElse("") }
        .filter { it.isNotEmpty() }.joinToString(".")
}

val pluginImplementationClass = providers.gradleProperty("plugin_implementation_class")
val pluginPackageName = pluginImplementationClass.map { it.substringBeforeLast(".") }
val pluginName = pluginImplementationClass.map { it.substringAfterLast('.') }
group = pluginPackageName.get()


publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            artifactId = Utils.split(pluginName.get(), nonAlphaNumeric = true, camelCase = true, lowercase = true)
                .joinToString("_")
        }
    }
}


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
    packageName(pluginPackageName.get())
    className(pluginName.get() + "BuildConfig")

    // Include gradle.properties entries as constants (only valid Java identifiers)
    properties.keys.map { Pair(it, it.replace(".", "_").trim()) }
        .filter { pair -> pair.component1() == pair.component2() || !properties.containsKey(pair.component2()) }
        .filter { pair -> pair.component2().matches(Regex("^[a-zA-Z_\\$][a-zA-Z0-9_\\$]*$")) }.filter { pair ->
            // @formatter:off
            val javaReservedWords = Stream.of(
                "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class",
                "const", "continue", "default", "do", "double", "else", "enum", "extends", "final",
                "finally", "float", "for", "goto", "if", "implements", "import", "instanceof", "int",
                "interface", "long", "native", "new", "package", "private", "protected", "public",
                "return", "short", "static", "strictfp", "super", "switch", "synchronized", "this",
                "throw", "throws", "transient", "try", "void", "volatile", "while", "true", "false", "null"
            )
            // @formatter:on
            javaReservedWords.noneMatch { it.equals(pair.component2(), ignoreCase = true) }
        }.forEach { pair ->
            val key = pair.component1()
            val name = pair.component2()
            when (val value = properties[key]) {
                is Number -> buildConfigField(name, value)
                is String -> buildConfigField(name, value)
                is File -> buildConfigField(name, value)
                is java.nio.file.Path -> buildConfigField(name, value.toFile())
            }
        }


    // Include the plugin name and package name as a constant
    buildConfigField(
        "plugin_package_name", pluginPackageName
    )
    buildConfigField(
        "plugin_name", pluginName
    )
}

// === Test task configuration ===
tasks.test {
    useJUnitPlatform()
}
