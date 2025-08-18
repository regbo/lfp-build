import java.util.stream.Stream

// === Repository configuration for resolving plugins and dependencies ===
repositories {
    gradlePluginPortal() // Gradle Plugin Portal for community plugins
    mavenCentral() // Maven Central for standard dependencies
}

// === Plugins required for building and publishing this Gradle plugin ===
plugins {
    `kotlin-dsl` // Enable Kotlin DSL for build scripts
    `maven-publish` // Enable publishing artifacts to Maven repositories
    alias(libs.plugins.buildconfig)
}

// === Java and Kotlin toolchain configuration ===
val javaVersion = providers.gradleProperty("java_version").map { it.toInt() }

java { toolchain { languageVersion.set(JavaLanguageVersion.of(javaVersion.get())) } }

kotlin { jvmToolchain(javaVersion.get()) }

fun tokenize(input: String): List<String> {
    return input
        .replace(Regex("([a-z0-9])([A-Z])"), "$1 $2")
        .split(Regex("[^a-zA-Z0-9]+"))
        .filter { it.isNotBlank() }
        .map { it.lowercase() }
}

// === Plugin metadata construction ===
val pluginId =
    providers.provider {
        listOf("repository_group", "repository_owner", "repository_name")
            .map { providers.gradleProperty(it).getOrElse("") }
            .filter { it.isNotEmpty() }
            .joinToString(".")
    }

val pluginImplementationClass = providers.gradleProperty("plugin_implementation_class")
val pluginPackageName = pluginImplementationClass.map { it.substringBeforeLast(".") }
val pluginName = pluginImplementationClass.map { it.substringAfterLast('.') }

group = pluginPackageName.get()

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            artifactId = tokenize(pluginName.get()).joinToString("-")
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

// === Test dependencies ===
dependencies {
    implementation(
        libs.versions.spring.boot
            .map { "org.springframework.boot:spring-boot-dependencies:${it}" }
            .map { platform(it) }
    )
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// === Apply all dependencies from the version catalog automatically ===
val libsVersionCatalog = extensions.getByType<VersionCatalogsExtension>().named("libs")

libsVersionCatalog.libraryAliases.forEach { alias ->
    dependencies { implementation(libsVersionCatalog.findLibrary(alias).orElseThrow()) }
}

// === BuildConfig generation ===
buildConfig {
    packageName(pluginPackageName.get())
    className(pluginName.get() + "BuildConfig")

    // Include gradle.properties entries as constants (only valid Java identifiers)
    properties.keys
        .map { Pair(it, it.replace(".", "_").trim()) }
        .filter { pair ->
            pair.component1() == pair.component2() || !properties.containsKey(pair.component2())
        }
        .filter { pair -> pair.component2().matches(Regex("^[a-zA-Z_\\$][a-zA-Z0-9_\\$]*$")) }
        .filter { pair ->
            val javaReservedWords =
                Stream.of(
                    "abstract",
                    "assert",
                    "boolean",
                    "break",
                    "byte",
                    "case",
                    "catch",
                    "char",
                    "class",
                    "const",
                    "continue",
                    "default",
                    "do",
                    "double",
                    "else",
                    "enum",
                    "extends",
                    "final",
                    "finally",
                    "float",
                    "for",
                    "goto",
                    "if",
                    "implements",
                    "import",
                    "instanceof",
                    "int",
                    "interface",
                    "long",
                    "native",
                    "new",
                    "package",
                    "private",
                    "protected",
                    "public",
                    "return",
                    "short",
                    "static",
                    "strictfp",
                    "super",
                    "switch",
                    "synchronized",
                    "this",
                    "throw",
                    "throws",
                    "transient",
                    "try",
                    "void",
                    "volatile",
                    "while",
                    "true",
                    "false",
                    "null",
                )
            javaReservedWords.noneMatch { it.equals(pair.component2(), ignoreCase = true) }
        }
        .forEach { pair ->
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
    buildConfigField("plugin_package_name", pluginPackageName)
    buildConfigField("plugin_name", pluginName)

    libsVersionCatalog.versionAliases.forEach { alias ->
        var aliasParts = tokenize(alias)
        if (!aliasParts.isEmpty()) {
            val versionPart = "version"
            if (!aliasParts.contains(versionPart)) {
                aliasParts = aliasParts + versionPart
            }
            val name = aliasParts.joinToString("_")
            if (!properties.containsKey(name)) {
                buildConfigField(
                    name,
                    libsVersionCatalog.findVersion(alias).orElseThrow().toString(),
                )
            }
        }
    }
}

// === Test task configuration ===
tasks.test { useJUnitPlatform() }
