import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import java.security.DigestOutputStream
import java.security.MessageDigest
import com.fasterxml.jackson.dataformat.toml.TomlMapper
import java.io.*

buildscript {
    val jacksonVersion = providers.gradleProperty("jackson_version").get()
    val buildDependencies = listOf(
        "com.fasterxml.jackson.core:jackson-databind:${jacksonVersion}",
        "com.fasterxml.jackson.module:jackson-module-kotlin:${jacksonVersion}",
        "com.fasterxml.jackson.dataformat:jackson-dataformat-toml:${jacksonVersion}",
    )
    repositories {
        mavenCentral()
    }
    dependencies {
        for (buildDependency in buildDependencies) {
            classpath(buildDependency)
        }
    }
}

rootProject.name = providers.gradleProperty("repository_name").get()

val versionCatalogFile = file("libs.versions.toml")
val versionCatalogHashHeader: String by lazy {
    val md = MessageDigest.getInstance("MD5")
    FileInputStream(versionCatalogFile).use { input ->
        DigestOutputStream(object : OutputStream() {
            override fun write(b: Int) {}
        }, md).use { digestOut ->
            input.copyTo(digestOut)
        }
    }
    val hash = md.digest().joinToString("") { "%02x".format(it) }
    "#${hash}"
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
val generatedVersionCatalogFileValid = generatedVersionCatalogFile.exists() &&
        generatedVersionCatalogFile.bufferedReader().use(BufferedReader::readLine) == versionCatalogHashHeader
if (!generatedVersionCatalogFileValid) {
    generatedVersionCatalogFile.parentFile.mkdirs()
    BufferedWriter(FileWriter(generatedVersionCatalogFile)).use { writer ->
        writer.write("$versionCatalogHashHeader\n")
        tomlMapper.writeValue(writer, versionCatalogNode)
    }
}
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



