package com.lfp.buildplugin.shared

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.MinimalExternalModuleDependency

/**
 * Extended version of [LibraryAutoConfig] with per-library behavior such as enforced platform support.
 */
open class LibraryAutoConfigOptions : LibraryAutoConfig() {
    var enforcedPlatform = false

    /**
     * Adds the given dependency to all resolved configurations.
     */
    fun add(project: Project, dependency: MinimalExternalModuleDependency): Boolean {
        val configurations = configurations(project)
        if (configurations.isEmpty()) return false

        val version = dependency.versionConstraint.requiredVersion
        val notation = "${dependency.module}:$version"
        val dependencyNotation: Any =
            if (enforcedPlatform) project.dependencies.enforcedPlatform(notation) else notation

        for (configuration in configurations) {
            project.dependencies.add(configuration.name, dependencyNotation)
            Utils.logger.lifecycle(
                "dependency added - configuration:${configuration.name} " +
                        "dependencyNotation:$dependencyNotation autoConfigOptions:$this"
            )
        }

        return true
    }

    /**
     * Resolves the appropriate configurations from the project according to config rules and fallbacks.
     */
    fun configurations(project: Project): Set<Configuration> {
        if (!enabled) return emptySet()

        val configurationNames = configurations ?: when {
            strictConfigurations -> emptyList()
            enforcedPlatform -> listOf("implementation")
            else -> listOf("api")
        }

        if (configurationNames.isEmpty()) return emptySet()

        val configurations = mutableSetOf<Configuration>()
        val checkedConfigurationNames = mutableSetOf<String>()

        for (i in configurationNames.indices) {
            var configurationName: String? = configurationNames[i]
            while (configurationName != null) {
                var match = false
                if (checkedConfigurationNames.add(configurationName)) {
                    val regex = Utils.toRegex(configurationName)
                    for (configuration in project.configurations) {
                        if (regex.matches(configuration.name)) {
                            configurations.add(configuration)
                            match = true
                        }
                    }
                }

                if (strictConfigurations || match) {
                    break
                } else {
                    configurationName = FALLBACK_CONFIGURATIONS[configurationName]
                }
            }
        }

        return configurations.toSet()
    }

    companion object {
        private const val PROPERTY_NAME = "autoConfigOptions"

        // Allows fallback to other configurations when not strict
        private val FALLBACK_CONFIGURATIONS = mapOf(
            "api" to "implementation",
            "implementation" to "testImplementation"
        )

        /**
         * Converts a [LibraryAutoConfig] into [LibraryAutoConfigOptions].
         */
        fun from(config: LibraryAutoConfig): LibraryAutoConfigOptions {
            if (config is LibraryAutoConfigOptions) return config
            return LibraryAutoConfigOptions().also { configOptions ->
                configOptions.enabled = config.enabled
                configOptions.strictConfigurations = config.strictConfigurations
                config.configurations?.let { configOptions.configurations = it }
            }
        }

        /**
         * Reads `autoConfigOptions` entries from the TOML structure of a version catalog.
         * Strips them out of the tree if `remove` is true.
         */
        fun read(versionCatalogNode: JsonNode, remove: Boolean = false): Map<String, LibraryAutoConfigOptions> {
            val libraries = versionCatalogNode.at("/libraries")
            if (!libraries.isObject) return emptyMap()

            val result = mutableMapOf<String, LibraryAutoConfigOptions>()

            libraries.fields().forEach { (name, value) ->
                if (value.isObject) {
                    val optionsNode = if (remove)
                        (value as ObjectNode).remove(PROPERTY_NAME)
                    else
                        value.get(PROPERTY_NAME)

                    if (optionsNode?.isObject == true) {
                        val alias = name.replace('-', '.')
                        val parsed = Utils.tomlMapper.treeToValue(
                            optionsNode,
                            LibraryAutoConfigOptions::class.java
                        )
                        result[alias] = parsed
                    }
                }
            }

            return result
        }
    }
}
