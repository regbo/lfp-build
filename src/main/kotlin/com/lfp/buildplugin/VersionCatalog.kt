package com.lfp.buildplugin

import com.fasterxml.jackson.dataformat.toml.TomlMapper
import org.gradle.api.Project

data class VersionCatalog internal constructor(
    val versions: Map<String, String> = emptyMap(),
    val plugins: Map<String, PluginEntry> = emptyMap(),
    val libraries: Map<String, LibraryEntry> = emptyMap()
) {
    fun findLibrary(alias: String): LibraryEntry? {
        return instance.libraries.entries.find { it.key == alias }?.value
    }

    companion object {

        val instance: VersionCatalog by lazy {
            val thisClass = this::class.java;
            val resourcePath = thisClass.packageName.replace('.', '/') + "/libs.versions.toml"
            thisClass.classLoader.getResourceAsStream(resourcePath).use { input ->
                Utils.tomlMapper.readValue(input, VersionCatalog::class.java)
            }
        }


    }
}

interface BuildOnlyAware {
    val buildOnly: Boolean
}

data class PluginEntry(
    val id: String,
    val version: String,
    val apply: Boolean = false,
    override val buildOnly: Boolean = false
) : BuildOnlyAware

data class LibraryEntry(
    val module: String,
    val version: Any? = null,
    val enforcedPlatform: Boolean = false,
    val testImplementation: Boolean = false,
    override val buildOnly: Boolean = false,
) : BuildOnlyAware {

    fun version(): String? {
        if (version is String) return version
        return versionRef()?.let { VersionCatalog.instance.versions[it] }
    }

    fun versionRef(): String? {
        if (version is Map<*, *>) {
            val ref = version["ref"]
            if (ref is String) return ref
        }
        return null
    }

    fun dependencyNotation(project: Project): Any {
        val notation = version()?.let { "${module}:${it}" } ?: module
        return if (enforcedPlatform) project.dependencies.enforcedPlatform(notation) else notation
    }
}