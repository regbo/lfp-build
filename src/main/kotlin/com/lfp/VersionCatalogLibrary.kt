package com.lfp

import org.gradle.api.Project

/**
 * Represents a dependency declared in the version catalog and its properties.
 */
data class VersionCatalogLibrary(
    val alias: String,
    val module: String,
    val version: String? = null,
    val buildOnly: Boolean,
    val enforcedPlatform: Boolean,
    val testImplementation: Boolean
) {

    fun dependencyNotation(project: Project): Any {
        val notation = version?.let { "${module}:${it}" } ?: module
        return if (enforcedPlatform) project.dependencies.enforcedPlatform(notation) else notation
    }

    companion object {
        /**
         * Cached list of [VersionCatalogLibrary] instances created once from BuildConfig values.
         */
        val all: List<VersionCatalogLibrary> by lazy {
            BuildPluginBuildConfig.versionCatalogLibraries
                .map { (alias, notation) ->
                    val parts = notation.split(':')
                    val module = parts.dropLast(1).joinToString(":")
                    val version = parts.lastOrNull()?.takeIf { it.isNotBlank() && parts.size >= 2 }

                    VersionCatalogLibrary(
                        alias = alias,
                        module = module,
                        version = version,
                        buildOnly = alias in BuildPluginBuildConfig.versionCatalogBuildOnlyAliases,
                        enforcedPlatform = alias in BuildPluginBuildConfig.versionCatalogEnforcedPlatformAliases,
                        testImplementation = alias in BuildPluginBuildConfig.versionCatalogTestImplementationAliases,
                    )
                }
        }

        fun find(alias: String): VersionCatalogLibrary? {
            return all.find { it.alias == alias }
        }
    }
}