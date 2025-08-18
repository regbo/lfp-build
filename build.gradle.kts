import java.util.stream.Stream

// === Repositories used to resolve plugins and dependencies ===
repositories {
    gradlePluginPortal()
    mavenCentral()
}

// === Build and publish plugins used for this Gradle plugin project ===
plugins {
    `kotlin-dsl`
    `maven-publish`
    alias(libs.plugins.buildconfig)
}

// === Java and Kotlin toolchains ===
val javaVersion = providers.gradleProperty("java_version").map { it.toInt() }

java { toolchain { languageVersion.set(JavaLanguageVersion.of(javaVersion.get())) } }

kotlin { jvmToolchain(javaVersion.get()) }

// === Plugin coordinates built from gradle.properties ===
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

// The group is the package of the implementation class so published artifacts use that coordinate
group = pluginPackageName.get()

/**
 * Utility to split a mixed case or delimited identifier into lowercase tokens.
 *
 * Examples: "MyPluginName" -> ["my", "plugin", "name"] "my-plugin_name" -> ["my", "plugin", "name"]
 */
fun tokenize(input: String): List<String> {
    return input
        .replace(Regex("([a-z0-9])([A-Z])"), "$1 $2")
        .split(Regex("[^a-zA-Z0-9]+"))
        .filter { it.isNotBlank() }
        .map { it.lowercase() }
}

// === Publishing configuration ===
publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            // Artifact id is a tokenized version of the implementation class name
            artifactId = tokenize(pluginName.get()).joinToString("-")
        }
    }
}

// === Gradle plugin registration ===
gradlePlugin {
    plugins {
        register(pluginName.get()) {
            id = pluginId.get()
            implementationClass = pluginImplementationClass.get()
        }
    }
}

// === Test dependencies only ===
dependencies {
    // Align dependency versions to Spring Boot BOM when a libs.versions.spring.boot entry is
    // present
    implementation(
        libs.versions.spring.boot
            .map { "org.springframework.boot:spring-boot-dependencies:$it" }
            .map { platform(it) }
    )

    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// === Apply all libraries from the version catalog as implementation dependencies ===
val libsVersionCatalog = extensions.getByType<VersionCatalogsExtension>().named("libs")

libsVersionCatalog.libraryAliases.forEach { alias ->
    dependencies { implementation(libsVersionCatalog.findLibrary(alias).orElseThrow()) }
}

// === Generate BuildConfig constants for convenient access in plugin code ===
buildConfig {
    packageName(pluginPackageName.get())
    className(pluginName.get() + "BuildConfig")

    // Add gradle.properties entries as constants when they are valid Java identifiers.
    // If a key has dots they are replaced with underscores. Conflicts are avoided by skipping
    // any transformed name that already exists as a key.
    properties.keys
        .map { key -> key to key.replace(".", "_").trim() }
        .filter { (orig, transformed) ->
            orig == transformed || !properties.containsKey(transformed)
        }
        .filter { (_, name) -> name.matches(Regex("^[a-zA-Z_\\$][a-zA-Z0-9_\\$]*$")) }
        .filter { (_, name) ->
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
            javaReservedWords.noneMatch { it.equals(name, ignoreCase = true) }
        }
        .forEach { (key, name) ->
            when (val value = properties[key]) {
                is Number -> buildConfigField(name, value)
                is String -> buildConfigField(name, value)
                is File -> buildConfigField(name, value)
                is java.nio.file.Path -> buildConfigField(name, value.toFile())
            }
        }

    // Always expose plugin identity
    buildConfigField("plugin_package_name", pluginPackageName)
    buildConfigField("plugin_name", pluginName)

    // Expose version catalog entries as constants when not already provided by gradle.properties
    libsVersionCatalog.versionAliases.forEach { alias ->
        var aliasParts = tokenize(alias)
        if (aliasParts.isNotEmpty()) {
            val versionPart = "version"
            if (!aliasParts.contains(versionPart)) aliasParts = aliasParts + versionPart
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

// === Test task ===
tasks.test { useJUnitPlatform() }
