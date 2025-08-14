package com.lfp.buildplugin.shared

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import org.apache.commons.lang3.builder.ToStringStyle
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import java.util.*
import java.util.stream.Collectors
import java.util.stream.Stream

/**
 * Extended [LibraryAutoConfig] that adds per-library configuration behavior,
 * such as enforced platform support and the ability to apply dependencies
 * automatically to appropriate Gradle configurations.
 */
open class LibraryAutoConfigOptions : LibraryAutoConfig() {
    /** If true, dependencies will be added as enforced platforms. */
    var platform = false

    /**
     * Adds the given dependency to the resolved configurations for this project,
     * based on the configured rules and fallbacks.
     *
     * @param project    The target Gradle [Project]
     * @param dependency The dependency to add
     * @return true if added to at least one configuration, false if no configurations matched
     */
    fun add(project: Project, dependency: MinimalExternalModuleDependency): Boolean {
        val configurations = configurations(project)
        if (configurations.isEmpty()) return false


        val version = dependency.versionConstraint.requiredVersion
        val dependencyNotation: Any
        if (version.isEmpty()) {
            require(!platform, { "platform version required - ${dependency}" })
            dependencyNotation = mapOf("group" to dependency.group, "name" to dependency.name)
        } else {
            val notation =
                Stream.of<String>(dependency.group, dependency.name, version).collect(Collectors.joining(":"))
            if (platform) {
                dependencyNotation = project.dependencies.platform(notation)
            } else {
                dependencyNotation = notation
            }
        }
        for (configuration in configurations) {
            project.dependencies.apply {
                add(configuration.name, dependencyNotation)
            }
            Utils.logger.lifecycle(
                "dependency added - configurationName:{} dependencyNotation:{} autoConfigOptions:{}",
                configuration.name,
                dependencyNotation,
                Utils.toString(this, ToStringStyle.NO_CLASS_NAME_STYLE)
            )
        }
        return true
    }

    /**
     * Resolves configurations from the project according to configured names and fallbacks.
     * Honors `enabled`, `strictConfigurations`, and `platform` settings.
     *
     * @param project The Gradle [Project] to resolve configurations from
     * @return Set of matching configurations
     */
    fun configurations(project: Project): Set<Configuration> {
        if (!enabled) return emptySet()

        val configurationNames = configurations ?: when {
            strictConfigurations -> emptyList()
            platform -> listOf("api", "testImplementation")
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
        return Collections.unmodifiableSet(configurations)
    }

    companion object {
        private const val PROPERTY_NAME = "autoConfigOptions"

        /** Default fallback configuration mapping (used if not strict). */
        private val FALLBACK_CONFIGURATIONS = mapOf(
            "api" to "implementation",
            "implementation" to "testImplementation"
        )


        /**
         * Reads `autoConfigOptions` entries from a TOML-based version catalog [JsonNode].
         * Optionally removes the property from the catalog if [remove] is true.
         *
         * @param versionCatalogNode Root node of the parsed TOML catalog
         * @param remove If true, removes the `autoConfigOptions` property from the node
         * @return Map of library alias → [LibraryAutoConfigOptions]
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
                        result.put(alias, parsed)
                    }
                }
            }

            return result
        }
    }

}
