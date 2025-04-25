package com.lfp.buildplugin.shared

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.MinimalExternalModuleDependency

open class LibraryAutoConfigOptions : LibraryAutoConfig() {
    var enforcedPlatform = false


    fun add(project: Project, dependency: MinimalExternalModuleDependency): Boolean {
        val configurations = configurations(project)
        if (configurations.isEmpty()) return false
        val version = dependency.versionConstraint.requiredVersion
        val notation = "${dependency.module}:$version"
        val dependencyNotation: Any =
            if (enforcedPlatform) project.dependencies.enforcedPlatform(notation) else notation
        for (configuration in configurations) {
            project.dependencies.add(configuration.name, dependencyNotation)
        }
        return true
    }

    fun configurations(project: Project): Set<Configuration> {
        if (!enabled) return emptySet()
        val configurationNames = this.configurations ?: if (strictConfigurations) {
            emptyList()
        } else if (enforcedPlatform) {
            listOf("implementation")
        } else {
            listOf("api")
        }
        if (configurationNames.isEmpty()) return emptySet()
        val configurations = mutableSetOf<Configuration>()
        val checkedConfigurationNames = mutableSetOf<String>()
        for (i in configurationNames.indices) {
            var configurationName: String? = configurationNames[i]
            while (configurationName != null) {
                var match = false
                if (checkedConfigurationNames.add(configurationName)) {
                    val configurationNameRegex = Utils.toRegex(configurationName)
                    for (configuration in project.configurations) {
                        if (configurationNameRegex.matches(configuration.name)) {
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
        return configurations.toSet();
    }


    companion object {

        private const val PROPERTY_NAME = "autoConfigOptions"
        private val FALLBACK_CONFIGURATIONS =
            mapOf("api" to "implementation", "implementation" to "testImplementation")

        fun from(config: LibraryAutoConfig): LibraryAutoConfigOptions {
            if (config is LibraryAutoConfigOptions) return config
            return LibraryAutoConfigOptions().also { configOptions ->
                configOptions.enabled = config.enabled
                configOptions.strictConfigurations = config.strictConfigurations
                config.configurations?.let { configOptions.configurations = it }
            }
        }

        fun read(versionCatalogNode: JsonNode, remove: Boolean = false): Map<String, LibraryAutoConfigOptions> {
            val libraries = versionCatalogNode.at("/libraries")
            if (!libraries.isObject) return emptyMap()
            val result = mutableMapOf<String, LibraryAutoConfigOptions>()
            libraries.fields().forEach { (name, value) ->
                if (value.isObject) {
                    val autoConfigOptionsNode =
                        if (remove) (value as ObjectNode).remove(PROPERTY_NAME) else value.get(PROPERTY_NAME)
                    if (autoConfigOptionsNode?.isObject == true) {
                        val alias = name.replace('-', '.')
                        val autoConfigOptions =
                            Utils.tomlMapper.treeToValue(
                                autoConfigOptionsNode,
                                LibraryAutoConfigOptions::class.java
                            )
                        result[alias] = autoConfigOptions
                    }
                }
            }
            return result.toMap()
        }
    }
}