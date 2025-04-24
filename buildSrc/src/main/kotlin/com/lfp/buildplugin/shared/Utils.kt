@file:Suppress("ObjectLiteralToLambda")

package com.lfp.buildplugin.shared

import com.fasterxml.jackson.dataformat.toml.TomlMapper
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module
import com.fasterxml.jackson.module.kotlin.KotlinModule
import groovy.lang.Closure
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.apache.commons.lang3.StringUtils
import org.gradle.api.Action
import org.gradle.api.file.FileCollection
import org.gradle.api.initialization.Settings
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.springframework.core.io.DefaultResourceLoader
import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.Resource
import org.springframework.core.io.ResourceLoader
import org.springframework.core.io.support.PathMatchingResourcePatternResolver
import org.springframework.core.io.support.ResourcePatternResolver
import org.springframework.util.DigestUtils
import org.springframework.util.ResourceUtils
import java.io.File
import java.io.FileOutputStream
import java.io.StringReader
import java.util.function.Consumer

object Utils {

    val logger: Logger by lazy {
        Logging.getLogger(Settings::class.java)
    }


    val tomlMapper: TomlMapper by lazy {
        val tomlMapper = TomlMapper()
        tomlMapper.registerModules(Jdk8Module(), KotlinModule.Builder().build())
        tomlMapper
    }


    fun sayHi() {
        println("hi there")
    }

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
        return TrimmedList.from(segments);
    }


    fun toFile(settings: Settings, resource: Resource): File {
        return toFile(File(settings.rootDir, "build"), resource)
    }

    fun toFile(buildDir: File, resource: Resource, forceCopy: Boolean = false): File {
        if (!forceCopy && resource.isFile) {
            return resource.file
        } else {
            val hash = DigestUtils.md5DigestAsHex(resource.uri.toString().toByteArray())
            val resourceFile = File(buildDir, "generated/resources/$hash/${resource.filename}")
            if (!resourceFile.isFile) {
                resourceFile.parentFile.mkdirs()
                resource.inputStream.use { input ->
                    FileOutputStream(resourceFile).use { output ->
                        input.copyTo(output)
                    }
                }
            }
            return resourceFile
        }
    }

    fun fileCollection(settings: Settings, vararg files: File): FileCollection {
        @Suppress("UnstableApiUsage")
        return settings.layout.rootDirectory.files(files)
    }

    fun <T : Any> action(consumer: Consumer<T>): Action<T> {
        return object : Action<T> {
            override fun execute(t: T) {
                consumer.accept(t)
            }
        }
    }

    fun <T> closure(delegate: Any? = null, block: () -> T): Closure<T> {
        return object : Closure<T>(delegate) {
            override fun call(vararg args: Any?): T = block()
        }
    }
}


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