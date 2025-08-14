package com.lfp.buildplugin

import com.fasterxml.jackson.databind.JsonNode
import com.lfp.buildplugin.shared.Utils
import org.apache.commons.codec.binary.Hex
import org.gradle.api.Action
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
 * Represents a Gradle version catalog backed by a TOML resource, with optional
 * auto-configuration metadata for applying dependencies automatically.
 *
 * This class:
 *  - Reads and parses the given catalog file once, computing a deterministic hash
 *  - Strips out and stores any [LibraryAutoConfigOptions] from the catalog content
 *  - Writes a cleaned copy of the catalog to a generated build output directory
 *  - Registers the catalog with Gradle's dependency resolution management
 *  - Applies automatic dependency configuration to projects after evaluation
 *
 * @property outputDirectory The directory where the cleaned version catalog will be written
 * @property resource        The Spring [Resource] representing the original catalog file
 */
data class VersionCatalog(
    val outputDirectory: File,
    val resource: Resource
) : Action<Settings> {
    /**
     * Internal parsed representation of the catalog, computed lazily:
     *  - MD5 hash of file contents
     *  - Parsed TOML as [JsonNode]
     *  - Extracted auto-config metadata
     */
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

    /**
     * The cleaned catalog file written to [outputDirectory].
     * Created only once and reused afterwards.
     */
    val file: File by lazy {
        val outFile = File(outputDirectory, "${name}/${resource.filename}")
        if (!outFile.exists()) {
            outFile.parentFile.mkdirs()
            Utils.tomlMapper.writeValue(outFile, context.content)
        }
        outFile
    }

    /**
     * Deterministic catalog name based on the resource filename and content hash.
     * Camel-cases split filename parts and appends the hash.
     */
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

    /** Returns the MD5 hash of the catalog file contents. */
    fun hash(): String = context.hash

    /** Returns the extracted auto-config metadata keyed by library alias. */
    fun autoConfigOptions(): Map<String, LibraryAutoConfigOptions> = context.autoConfigOptions

    /**
     * Registers this version catalog with the given [Settings] and
     * wires up auto-configuration to run after all projects are evaluated.
     *
     * @param settings The Gradle [Settings] to apply the catalog to
     */
    override fun execute(settings: Settings) {
        settings.dependencyResolutionManagement.versionCatalogs.create(name) {
            from(Utils.fileCollection(settings, file))
        }
        settings.gradle.afterProject(Utils.action { project ->
            val libs = project.extensions.getByType<VersionCatalogsExtension>().named(name)
            apply(project, libs)
        })
    }

    /**
     * Applies auto-configuration to each library alias in the given catalog.
     *
     * @param project The Gradle [Project] to configure
     * @param libs    The [org.gradle.api.artifacts.VersionCatalog] instance
     */
    fun apply(project: Project, libs: org.gradle.api.artifacts.VersionCatalog) {
        libs.libraryAliases.map { alias ->
            Pair(alias, autoConfigOptions()[alias] ?: LibraryAutoConfigOptions())
        }.sortedBy { pair ->
            if (pair.component2().platform) 0 else 1
        }.forEach { pair ->
            val alias = pair.component1()
            val autoConfigOptions = pair.component2()
            val dep = libs.findLibrary(alias).get().get()
            autoConfigOptions.add(project, dep)
        }
    }

    companion object {
        private const val GENERATED_OUTPUT_PATH = "build/generated/version-catalog"

        /**
         * Creates a [VersionCatalog] instance from a [Settings] context and [Resource].
         */
        fun from(settings: Settings, resource: Resource): VersionCatalog {
            return VersionCatalog(generatedOutputPath(settings.rootDir), resource)
        }

        /**
         * Resolves the generated output path relative to the given root directory.
         */
        private fun generatedOutputPath(rootDir: File): File {
            return File(rootDir, GENERATED_OUTPUT_PATH)
        }
    }
}

/**
 * Internal parsed representation of the version catalog:
 * @property hash             MD5 hash of the original catalog content
 * @property content          Parsed TOML content as [JsonNode]
 * @property autoConfigOptions Extracted auto-config options keyed by library alias
 */
private data class Context(
    val hash: String,
    val content: JsonNode,
    val autoConfigOptions: Map<String, LibraryAutoConfigOptions>
)
