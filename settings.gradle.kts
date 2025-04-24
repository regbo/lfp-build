import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.dataformat.toml.TomlMapper
import java.io.*
import java.security.DigestOutputStream
import java.security.MessageDigest

// === Buildscript classpath setup for Jackson and TOML support ===
buildscript {
    val jacksonVersion = providers.gradleProperty("jackson_version").get()
    val buildDependencies = listOf(
        "com.fasterxml.jackson.core:jackson-databind:$jacksonVersion",
        "com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion",
        "com.fasterxml.jackson.dataformat:jackson-dataformat-toml:$jacksonVersion",
    )

    repositories {
        mavenCentral()
    }

    dependencies {
        buildDependencies.forEach { classpath(it) }
    }
}

// === Set the root project name from a Gradle property ===
rootProject.name = providers.gradleProperty("repository_name").get()

// === File reference for the original version catalog ===
val versionCatalogFile = file("libs.versions.toml")

// === Compute MD5 hash of the version catalog for caching ===
val versionCatalogHashHeader: String by lazy {
    val md = MessageDigest.getInstance("MD5")
    FileInputStream(versionCatalogFile).use { input ->
        DigestOutputStream(object : OutputStream() {
            override fun write(b: Int) {} // no-op sink
        }, md).use { digestOut ->
            input.copyTo(digestOut)
        }
    }
    "#${md.digest().joinToString("") { "%02x".format(it) }}"
}

// === Load TOML file using Jackson TomlMapper ===
val tomlMapper = TomlMapper()
val versionCatalogNode: JsonNode = tomlMapper.readTree(versionCatalogFile)

// === Collect aliases for special flags ===
val versionCatalogBuildOnlyAliases = mutableSetOf<String>()
val versionCatalogEnforcedPlatformAliases = mutableSetOf<String>()
val versionCatalogTestImplementationAliases = mutableSetOf<String>()

val libraries = versionCatalogNode.at("/libraries")
if (libraries.isObject) {
    for ((key, value) in libraries.fields()) {
        if (value.isObject) {
            val libraryObject = value as ObjectNode
            val alias = key.replace("-", ".")
            mapOf(
                "buildOnly" to versionCatalogBuildOnlyAliases,
                "enforcedPlatform" to versionCatalogEnforcedPlatformAliases,
                "testImplementation" to versionCatalogTestImplementationAliases
            ).forEach { (field, collector) ->
                val removed = libraryObject.remove(field)
                if (removed?.booleanValue() == true) {
                    collector.add(alias)
                }
            }
        }
    }
}

// === Generate and optionally reuse the cleaned version catalog ===
val generatedVersionCatalogFile = file("build/generated/libs.versions.toml")
val generatedVersionCatalogFileValid = generatedVersionCatalogFile.exists() &&
        generatedVersionCatalogFile.bufferedReader().use { it.readLine() } == versionCatalogHashHeader

if (!generatedVersionCatalogFileValid) {
    if (!generatedVersionCatalogFile.delete()) {
        generatedVersionCatalogFile.parentFile.mkdirs()
    }

    BufferedWriter(FileWriter(generatedVersionCatalogFile)).use { writer ->
        writer.write("$versionCatalogHashHeader\n")
        tomlMapper.writeValue(writer, versionCatalogNode)
    }
}

// === Inject the generated version catalog into Gradle resolution ===
dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files(generatedVersionCatalogFile))
        }
    }
}

// === Expose alias sets to all projects via project.extra ===
@Suppress("ObjectLiteralToLambda")
gradle.beforeProject(object : Action<Project> {
    override fun execute(project: Project) {
        project.extra.set(::versionCatalogBuildOnlyAliases.name, versionCatalogBuildOnlyAliases.toSet())
        project.extra.set(::versionCatalogEnforcedPlatformAliases.name, versionCatalogEnforcedPlatformAliases.toSet())
        project.extra.set(
            ::versionCatalogTestImplementationAliases.name,
            versionCatalogTestImplementationAliases.toSet()
        )
    }
})
