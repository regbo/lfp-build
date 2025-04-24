import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.dataformat.toml.TomlMapper
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module
import com.fasterxml.jackson.module.kotlin.KotlinModule
import java.io.BufferedWriter
import java.io.FileInputStream
import java.io.FileWriter
import java.io.OutputStream
import java.security.DigestOutputStream
import java.security.MessageDigest

// === Buildscript classpath setup for Jackson and TOML support ===
buildscript {
    val jacksonVersion = providers.gradleProperty("jackson_version").get()
    val buildDependencies = listOf(
        "com.fasterxml.jackson.core:jackson-databind:$jacksonVersion",
        "com.fasterxml.jackson.datatype:jackson-datatype-jdk8:$jacksonVersion",
        "com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion",
        "com.fasterxml.jackson.dataformat:jackson-dataformat-toml:$jacksonVersion",
    )
    extra["buildDependencies"] = buildDependencies
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
val versionCatalogFile = file("src/main/resources/com/lfp/buildplugin/libs.versions.toml")

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

val versionCatalogRemoveFieldNames = listOf("buildOnly", "enforcedPlatform", "testImplementation")
fun versionCatalogNormalize(node: JsonNode?) {
    if (node == null) return
    else if (node.isObject) {
        val objectNode = node as ObjectNode
        val fieldNames = objectNode.fieldNames()
        while (fieldNames.hasNext()) {
            val fieldName = fieldNames.next()
            if (fieldName in versionCatalogRemoveFieldNames) {
                fieldNames.remove()
            } else {
                versionCatalogNormalize(objectNode.get(fieldName))
            }
        }
    } else if (node.isArray) {
        (node as ArrayNode).forEach(::versionCatalogNormalize)
    }
}

// === Load TOML file using Jackson TomlMapper ===
val tomlMapper = TomlMapper()
tomlMapper.registerModules(Jdk8Module(), KotlinModule.Builder().build())
val versionCatalogNode: JsonNode = tomlMapper.readTree(versionCatalogFile)
versionCatalogNormalize(versionCatalogNode)


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
        project.extra["buildDependencies"] = settings.extra["buildDependencies"]
    }
})
