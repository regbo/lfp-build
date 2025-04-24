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
import org.gradle.api.initialization.Settings
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.springframework.core.io.DefaultResourceLoader
import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.Resource
import org.springframework.core.io.ResourceLoader
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

    val resourceLoader: ResourceLoader by lazy {
        DefaultResourceLoader()
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

    fun resources(path: String = ""): List<Resource> {
        val packageNameParts = this::class.java.packageName.split(".")
        val pathPrefixes = mutableListOf(packageNameParts.joinToString("/"))
        if (packageNameParts.size > 1) {
            pathPrefixes.add(packageNameParts.subList(0, packageNameParts.size - 1).joinToString("/"))
        }
        pathPrefixes.add("")
        val pathPrefixesSize = pathPrefixes.size
        for (i in 0 until pathPrefixesSize) {
            val index = pathPrefixes.size - 1 - i
            val pathPrefix = pathPrefixes[index]
            pathPrefixes.add(0, "src/main/resources${if (pathPrefix.isEmpty()) "" else "/$pathPrefix"}")
        }
        val locationPrefixes = listOf(ResourceUtils.FILE_URL_PREFIX, ResourceUtils.CLASSPATH_URL_PREFIX, "")
        for (pathPrefix in pathPrefixes) {
            for (locationPrefix in locationPrefixes) {
                val location = locationPrefix + (if (pathPrefix.isEmpty()) "" else "$pathPrefix/") + path
                try {
                    val resource = resourceLoader.getResource(location)
                    if (resource.exists()) {
                        if (resource.isFile) {
                            val resourceFile = resource.file
                            if (resourceFile.isDirectory) {
                                return resourceFile
                                    .walkTopDown()
                                    .filter { it.isFile }
                                    .map { FileSystemResource(it) }
                                    .toList()
                            }
                        }
                        return listOf(resource)
                    }
                } catch (_: Exception) {

                }
            }
        }
        return emptyList()
    }

    fun resourceFiles(settings: Settings, path: String = ""): List<File> {
        return resourceFiles(File(settings.rootDir, "build"), path)
    }

    fun resourceFiles(buildDir: File, path: String = "", forceCopy: Boolean = false): List<File> {
        val resources = resources(path)
        if (resources.isEmpty()) return emptyList()
        return resources.map { resource ->
            if (!forceCopy) {
                resource.file
            } else {
                val uid = "${path}|${resource.uri}"
                val hash = DigestUtils.md5DigestAsHex(uid.toByteArray())
                val resourceFile = File(buildDir, "resources/main/generated/$hash/${resource.filename}")
                if (!resourceFile.isFile) {
                    resourceFile.parentFile.mkdirs()
                    resource.inputStream.use { input ->
                        FileOutputStream(resourceFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                }
                resourceFile
            }
        }
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