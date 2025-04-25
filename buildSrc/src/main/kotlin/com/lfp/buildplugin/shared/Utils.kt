@file:Suppress("ObjectLiteralToLambda")

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

object Utils {

    // Lazy logger instance scoped to Settings class for Gradle logging
    val logger: Logger by lazy {
        Logging.getLogger(Settings::class.java)
    }

    // Configured TomlMapper with Kotlin and JDK8 support
    val tomlMapper: TomlMapper by lazy {
        val tomlMapper = TomlMapper()
        tomlMapper.registerModules(Jdk8Module(), KotlinModule.Builder().build())
        tomlMapper
    }

    // Spring resource resolver for classpath and file patterns
    val resourcePatternResolver: ResourcePatternResolver by lazy {
        PathMatchingResourcePatternResolver()
    }


    /**
     * Splits a string using optional options:
     * - CSV parsing (if commas present)
     * - Non-alphanumeric splitting
     * - CamelCase splitting
     * - Lowercasing
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
     * Resolves any input into a list of readable [Resource]s.
     * Supports files, paths, resource patterns, and directories (optionally walking them).
     */
    fun resources(vararg locations: Any?, walkDirectories: Boolean = true): List<Resource> {

        // Converts a Path to a Resource if it exists
        fun pathToResource(input: Path?): Resource? = input?.takeIf(Files::exists)?.let(::PathResource)

        // Converts a string to a list of resources via file path or pattern
        fun stringToResources(input: String?): List<Resource> {
            val inputTrimmed = input?.trim()?.takeIf(String::isNotBlank) ?: return emptyList()
            val resources = mutableListOf<Resource?>()

            try {
                resources.add(pathToResource(Paths.get(inputTrimmed)))
            } catch (_: Exception) {}

            try {
                resources.addAll(resourcePatternResolver.getResources(inputTrimmed))
            } catch (_: Exception) {}

            return resources.filterNotNull().distinct().toList()
        }

        // Normalizes any input to a list of resources
        fun anyToResources(input: Any?): List<Resource> = when (input) {
            null -> emptyList()
            is Resource -> listOf(input)
            is Path -> listOfNotNull(pathToResource(input))
            is File -> listOf(FileSystemResource(input))
            else -> stringToResources(input.toString())
        }

        // Flatten all input locations into a list of readable resources
        var resources = locations.flatMap(::anyToResources)

        // Optionally walk into directories
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
     * Returns a simple string representation of an object using Apache Commons `ToStringBuilder`.
     */
    fun toString(input: Any?): String {
        if (input == null) return Objects.toString(null)
        return ToStringBuilder.reflectionToString(input, ToStringStyle.SHORT_PREFIX_STYLE)
    }

    /**
     * Converts a string to a [Regex]. If the input is wrapped in `/.../`, returns raw regex;
     * otherwise, quotes it as an exact match.
     */
    fun toRegex(input: String): Regex {
        return if (input.length > 2 && Stream.of(0, input.length - 1).allMatch { input[it] == '/' }) {
            input.substring(1, input.length - 1).toRegex()
        } else {
            Regex("^" + Pattern.quote(input) + "$")
        }
    }

    /**
     * Creates a [FileCollection] from multiple files relative to the root directory.
     */
    fun fileCollection(settings: Settings, vararg files: File): FileCollection {
        @Suppress("UnstableApiUsage")
        return settings.layout.rootDirectory.files(files.map { it.path })
    }

    /**
     * Wraps a Java [Consumer] as a Gradle [Action].
     */
    fun <T : Any> action(consumer: Consumer<T>): Action<T> {
        return object : Action<T> {
            override fun execute(t: T) {
                consumer.accept(t)
            }
        }
    }

    /**
     * Wraps a Kotlin block as a Groovy [Closure] for compatibility with Groovy-based APIs.
     */
    fun <T> closure(delegate: Any? = null, block: () -> T): Closure<T> {
        return object : Closure<T>(delegate) {
            override fun call(vararg args: Any?): T = block()
        }
    }
}

/**
 * A List<String> wrapper that trims and filters out blank/null values.
 */
private class TrimmedList private constructor(
    private val delegate: List<String>
) : List<String> by delegate {

    override fun toString(): String = delegate.toString()

    companion object {
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
