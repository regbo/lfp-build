package com.lfp.buildplugin.shared

import com.fasterxml.jackson.databind.JsonNode
import org.apache.commons.codec.binary.Hex
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.initialization.Settings
import org.gradle.kotlin.dsl.getByType
import org.springframework.core.io.Resource
import java.io.File
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.stream.IntStream

/**
 * Wrapper for processing and applying a version catalog resource with auto-config metadata.
 */
data class VersionCatalog(
    val outputDirectory: File,
    val resource: Resource
) {
    // Lazily parse and digest the catalog once for name, hash, content, and autoConfig metadata
    private val context: Context by lazy {
        resource.inputStream.use { input ->
            val md = MessageDigest.getInstance("MD5")
            val content = DigestInputStream(input, md).use { digestInput ->
                Utils.tomlMapper.readTree(digestInput)
            }
            val hash = Hex.encodeHexString(md.digest())
            val autoConfigOptions = LibraryAutoConfigOptions.read(content, remove = true)
            Context(hash, content, autoConfigOptions)
        }
    }

    // Write a cleaned version of the version catalog file to disk
    val file: File by lazy {
        val outFile = File(outputDirectory, "${name}/${resource.filename}")
        if (!outFile.exists()) {
            outFile.parentFile.mkdirs()
            Utils.tomlMapper.writeValue(outFile, context.content)
        }
        outFile
    }

    // Create a deterministic version catalog name based on filename and hash
    val name: String by lazy {
        val nameParts = Utils.split(resource.filename, nonAlphaNumeric = true, camelCase = true).toMutableList()
        if (nameParts.size > 1) {
            IntStream.range(1, nameParts.size).forEach { index ->
                nameParts[index] = nameParts[index].replaceFirstChar { it.uppercase() }
            }
        }
        nameParts.add(hash())
        nameParts.joinToString("")
    }

    fun hash(): String = context.hash

    fun autoConfigOptions(): Map<String, LibraryAutoConfigOptions> = context.autoConfigOptions

    /**
     * Applies this version catalog to the given [Settings] and wires up auto-config after projects are evaluated.
     */
    fun apply(settings: Settings) {
        settings.dependencyResolutionManagement.versionCatalogs.create(name) {
            from(Utils.fileCollection(settings, file))
        }
        settings.gradle.afterProject(Utils.action { project ->
            val libs = project.extensions.getByType<VersionCatalogsExtension>().named(name)
            apply(project, libs)
        })
    }

    /**
     * Applies auto-configuration to dependencies from this catalog inside the given [Project].
     */
    fun apply(project: Project, libs: org.gradle.api.artifacts.VersionCatalog) {
        libs.libraryAliases.forEach { alias ->
            val autoConfigOptions = autoConfigOptions()[alias] ?: LibraryAutoConfigOptions()
            val dep = libs.findLibrary(alias).get().get()
            autoConfigOptions.add(project, dep)
        }
    }

    companion object {
        private const val GENERATED_OUTPUT_PATH = "build/generated/version-catalog"

        fun from(settings: Settings, resource: Resource): VersionCatalog {
            return VersionCatalog(generatedOutputPath(settings.rootDir), resource)
        }

        private fun generatedOutputPath(rootDir: File): File {
            return File(rootDir, GENERATED_OUTPUT_PATH)
        }
    }
}

// Internal class representing the parsed catalog context
private data class Context(
    val hash: String,
    val content: JsonNode,
    val autoConfigOptions: Map<String, LibraryAutoConfigOptions>
)
