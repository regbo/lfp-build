import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import java.io.FileInputStream
import java.io.OutputStream
import java.security.DigestOutputStream
import java.security.MessageDigest
import com.fasterxml.jackson.dataformat.toml.TomlMapper

buildscript {
    val jacksonVersion = providers.gradleProperty("jackson_version").get()
    val buildDependencies = listOf(
        "com.fasterxml.jackson.core:jackson-databind:${jacksonVersion}",
        "com.fasterxml.jackson.module:jackson-module-kotlin:${jacksonVersion}",
        "com.fasterxml.jackson.dataformat:jackson-dataformat-toml:${jacksonVersion}",
    )
    extra["buildDependencies"] = buildDependencies
    repositories {
        mavenCentral()
    }
    dependencies {
        for (buildDependency in buildDependencies) {
            classpath(buildDependency)
        }
    }
}
fun buildDependencies(): List<String> {
    @Suppress("UNCHECKED_CAST") return extra["buildDependencies"] as List<String>
}


rootProject.name = providers.gradleProperty("repository_name").get()

val versionCatalogFile = file("libs.versions.toml")
val versionCatalogHash: String by lazy {
    val md = MessageDigest.getInstance("MD5")
    FileInputStream(versionCatalogFile).use { input ->
        DigestOutputStream(OutputStream.nullOutputStream(), md).use { digestOut ->
            input.copyTo(digestOut)
        }
    }
    md.digest().joinToString("") { "%02x".format(it) }
}

val tomlMapper = TomlMapper()
val versionCatalogNode: JsonNode = tomlMapper.readTree(versionCatalogFile)
val versionCatalogEnforcedPlatformAliases = mutableSetOf<String>()
val versionCatalogTestImplementationAliases = mutableSetOf<String>()
val libraries: JsonNode = versionCatalogNode.at("/libraries")
if (libraries.isObject) {
    for (libraryEntry in libraries.fields()) {
        val libraryName = libraryEntry.key
        val alias = libraryName.replace("-", ".")
        val libraryNode = libraryEntry.value
        if (libraryNode.isObject) {
            val libraryObjectNode = libraryNode as ObjectNode
            val enforcedPlatform = libraryObjectNode.remove("enforcedPlatform")
            if (enforcedPlatform != null && enforcedPlatform.booleanValue()) {
                versionCatalogEnforcedPlatformAliases.add(alias)
            }
            val testImplementation = libraryObjectNode.remove("testImplementation")
            if (testImplementation != null && testImplementation.booleanValue()) {
                versionCatalogTestImplementationAliases.add(alias)
            }
        }
    }
}
val generatedVersionCatalogFile = file("build/generated/libs.versions.toml")
tomlMapper.writeValue(generatedVersionCatalogFile, versionCatalogNode)
dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files(generatedVersionCatalogFile))
        }
    }
}
gradle.beforeProject(object : Action<Project> {
    override fun execute(project: Project) {
        project.extra["versionCatalogEnforcedPlatformAliases"] = versionCatalogEnforcedPlatformAliases.toSet()
        project.extra["versionCatalogTestImplementationAliases"] = versionCatalogTestImplementationAliases.toSet()
    }
})



