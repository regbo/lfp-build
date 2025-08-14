@file:Suppress("ObjectLiteralToLambda", "JavaDefaultMethodsNotOverriddenByDelegation")

package com.lfp.buildplugin.shared

import com.fasterxml.jackson.dataformat.toml.TomlMapper
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module
import com.fasterxml.jackson.module.kotlin.KotlinModule
import groovy.lang.Closure
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.builder.ToStringBuilder
import org.apache.commons.lang3.builder.ToStringStyle
import org.gradle.api.Action
import org.gradle.api.file.FileCollection
import org.gradle.api.initialization.Settings
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.PathResource
import org.springframework.core.io.Resource
import org.springframework.core.io.support.PathMatchingResourcePatternResolver
import org.springframework.core.io.support.ResourcePatternResolver
import java.io.File
import java.io.StringReader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import java.util.function.Consumer
import java.util.regex.Pattern
import java.util.stream.Stream

/**
 * Utility helper object providing:
 *  - Logging
 *  - TOML parsing with Jackson
 *  - Resource resolution
 *  - String splitting with CSV, non-alphanumeric, and CamelCase rules
 *  - Reflection-based string rendering
 *  - Regex conversion
 *  - Gradle file collection creation
 *  - Java/Kotlin interop helpers for Gradle [Action] and Groovy [Closure]
 */
object Utils {

    /** Gradle logger scoped to [Settings]. */
    val logger: Logger by lazy {
        Logging.getLogger(Settings::class.java)
    }

    /** Preconfigured [TomlMapper] with Kotlin and JDK8 module support. */
    val tomlMapper: TomlMapper by lazy {
        TomlMapper().apply {
            registerModules(Jdk8Module(), KotlinModule.Builder().build())
        }
    }

    /** Spring resource resolver for classpath and file system patterns. */
    val resourcePatternResolver: ResourcePatternResolver by lazy {
        PathMatchingResourcePatternResolver()
    }
    

    /**
     * Splits the given string into segments with optional processing:
     *  - CSV parsing if commas are present
     *  - Splitting on non-alphanumeric characters
     *  - Splitting on CamelCase boundaries
     *  - Lowercasing segments
     *  - Trimming and filtering blank entries
     *
     * @param str Input string
     * @param nonAlphaNumeric If true, split on any non-alphanumeric character
     * @param camelCase If true, split on camel-case boundaries
     * @param lowercase If true, convert segments to lowercase
     * @return List of processed, trimmed segments
     */
    fun split(
        str: String?, nonAlphaNumeric: Boolean = false, camelCase: Boolean = false, lowercase: Boolean = false
    ): List<String> {
        if (str.isNullOrBlank()) return emptyList()

        var segments: List<String> = listOf(str).flatMap { segment ->
            if (segment.contains(",")) {
                CSVParser.parse(StringReader(segment), CSVFormat.DEFAULT).use { parser ->
                    parser.records.flatMap { it.toList() }
                }
            } else {
                listOf(segment)
            }
        }

        if (nonAlphaNumeric) {
            segments = segments.flatMap { it.split("[^a-zA-Z0-9]+".toRegex()) }
        }

        if (camelCase) {
            segments = segments.flatMap { StringUtils.splitByCharacterTypeCamelCase(it).toList() }
        }

        if (lowercase) {
            segments = segments.map { it.lowercase() }
        }

        return TrimmedList.from(segments)
    }

    /**
     * Resolves arbitrary inputs (paths, files, patterns, resources) into readable [Resource] instances.
     * Supports:
     *  - Direct files and paths
     *  - Resource patterns (classpath, filesystem)
     *  - Directories (optional recursive walking)
     *
     * @param locations Locations to resolve
     * @param walkDirectories If true, recurse into directories
     */
    fun resources(vararg locations: Any?, walkDirectories: Boolean = true): List<Resource> {

        fun pathToResource(input: Path?): Resource? =
            input?.takeIf(Files::exists)?.let(::PathResource)

        fun stringToResources(input: String?): List<Resource> {
            val inputTrimmed = input?.trim()?.takeIf(String::isNotBlank) ?: return emptyList()
            val resources = mutableListOf<Resource?>()

            try {
                resources.add(pathToResource(Paths.get(inputTrimmed)))
            } catch (_: Exception) {
            }

            try {
                resources.addAll(resourcePatternResolver.getResources(inputTrimmed))
            } catch (_: Exception) {
            }

            return resources.filterNotNull().distinct()
        }

        fun anyToResources(input: Any?): List<Resource> = when (input) {
            null -> emptyList()
            is Resource -> listOf(input)
            is Path -> listOfNotNull(pathToResource(input))
            is File -> listOf(FileSystemResource(input))
            else -> stringToResources(input.toString())
        }

        var resources = locations.flatMap(::anyToResources)

        if (walkDirectories) {
            fun walkDirectoryResources(resource: Resource): List<Resource> {
                if (resource.isFile) {
                    val resourceFile = resource.file
                    if (resourceFile.isDirectory) {
                        return resourceFile
                            .walkTopDown()
                            .filter { it.isFile }
                            .flatMap { resources(it, walkDirectories = false) }
                            .toList()
                    }
                }
                return listOf(resource)
            }
            resources = resources.flatMap(::walkDirectoryResources)
        }

        return resources.filter { it.isReadable }
    }

    /**
     * Renders an object into a short string form using reflection-based
     * [ToStringBuilder] with [ToStringStyle.SHORT_PREFIX_STYLE].
     */
    fun toString(input: Any?): String {
        if (input == null) return Objects.toString(null)
        return ToStringBuilder.reflectionToString(input, ToStringStyle.SHORT_PREFIX_STYLE)
    }

    /**
     * Converts the input string to a [Regex].
     * If wrapped in `/.../`, uses it as raw regex; otherwise escapes it for exact matching.
     */
    fun toRegex(input: String): Regex {
        return if (input.length > 2 && Stream.of(0, input.length - 1).allMatch { input[it] == '/' }) {
            input.substring(1, input.length - 1).toRegex()
        } else {
            Regex("^" + Pattern.quote(input) + "$")
        }
    }

    /**
     * Creates a Gradle [FileCollection] from multiple files relative to the root directory.
     */
    fun fileCollection(settings: Settings, vararg files: File): FileCollection {
        @Suppress("UnstableApiUsage")
        return settings.layout.rootDirectory.files(files.map { it.path })
    }

    /**
     * Wraps a Java [Consumer] as a Gradle [Action] for DSL compatibility.
     */
    fun <T : Any> action(consumer: Consumer<T>): Action<T> {
        return object : Action<T> {
            override fun execute(t: T) {
                consumer.accept(t)
            }
        }
    }

    /**
     * Wraps a Kotlin lambda in a Groovy [Closure] for use with Groovy-based Gradle APIs.
     *
     * @param delegate Optional closure delegate
     * @param block    The Kotlin lambda to execute
     */
    fun <T> closure(delegate: Any? = null, block: () -> T): Closure<T> {
        return object : Closure<T>(delegate) {
            override fun call(vararg args: Any?): T = block()
        }
    }
}

/**
 * Immutable list wrapper that trims and removes null/blank strings.
 */
private class TrimmedList private constructor(
    private val delegate: List<String>
) : List<String> by delegate {

    override fun toString(): String = delegate.toString()

    companion object {
        /** Creates a [TrimmedList] from any list, removing null/blank elements and trimming whitespace. */
        fun from(list: List<String?>): TrimmedList {
            return if (list is TrimmedList) {
                list
            } else {
                val trimmed = list.mapNotNull { it?.trim()?.takeIf { trimmed -> trimmed.isNotEmpty() } }
                TrimmedList(trimmed)
            }
        }
    }
}
