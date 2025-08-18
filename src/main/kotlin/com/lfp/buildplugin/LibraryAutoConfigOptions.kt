package com.lfp.buildplugin

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import org.apache.commons.lang3.builder.ToStringStyle
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.plugins.JavaPlugin
import java.util.*
import java.util.stream.Collectors
import java.util.stream.Stream

private const val AUTO_CONFIG_PROPERTY_NAME = "autoConfigOptions"

/** Default fallback configuration mapping used only when strict configuration matching is disabled. */
private val FALLBACK_CONFIGURATIONS =
    mapOf(
        JavaPlugin.API_CONFIGURATION_NAME to JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME,
        JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME to
                JavaPlugin.TEST_IMPLEMENTATION_CONFIGURATION_NAME,
    )

/**
 * Options that control how a single catalog library is applied to a project.
 *
 * Responsibilities:
 *  - Decide which Gradle configurations to target based on explicit names, strictness, and defaults
 *  - Add the library either as a normal dependency or as a platform using [Project.getDependencies]
 *  - Provide sensible fallbacks when strict matching is disabled
 */
class LibraryAutoConfigOptions : LibraryAutoConfig() {

    /** When true, the dependency is added via [Project.dependencies.platform]. */
    var platform = false

    /**
     * Adds [dependency] to one or more configurations determined by [configurations].
     *
     * Behavior:
     *  - If the dependency has no version and [platform] is true, an error is thrown because
     *    enforced platforms require a version
     *  - If the dependency has no version and [platform] is false, the dependency is added as
     *    a GAV map with only group and name so the version can be controlled by an external BOM
     *  - If the dependency has a version, either a normal GAV is added or a platform notation
     *    is created when [platform] is true
     *
     * Logging:
     *  A lifecycle log is emitted per configuration added with the configuration name, resolved
     *  dependency notation, and a short summary of the applied options
     *
     * @return true if at least one configuration received the dependency, false if none matched
     */
    fun add(project: Project, dependency: MinimalExternalModuleDependency): Boolean {
        val configurations = configurations(project)
        if (configurations.isEmpty()) return false

        val version = dependency.versionConstraint.requiredVersion
        val dependencyNotation: Any
        if (version.isEmpty()) {
            require(!platform) { "platform version required - $dependency" }
            dependencyNotation = mapOf("group" to dependency.group, "name" to dependency.name)
        } else {
            val notation =
                Stream.of<String>(dependency.group, dependency.name, version)
                    .collect(Collectors.joining(":"))
            dependencyNotation =
                if (platform) project.dependencies.platform(notation) else notation
        }

        for (configuration in configurations) {
            project.dependencies.add(configuration.name, dependencyNotation)
            Utils.logger.lifecycle(
                "dependency added - configurationName:{} dependencyNotation:{} autoConfigOptions:{}",
                configuration.name,
                dependencyNotation,
                Utils.toString(this, ToStringStyle.NO_CLASS_NAME_STYLE),
            )
        }
        return true
    }

    /**
     * Resolves the target configurations on [project].
     *
     * Source of truth in order:
     *  1. If [LibraryAutoConfig.enabled] is false, returns empty
     *  2. If explicit [LibraryAutoConfig.configurations] are present, they are used
     *     a. If [LibraryAutoConfig.strictConfigurations] is true, only exact or regex matches are used
     *     b. If strict is false and a name does not match, a fallback chain is applied using
     *        [FALLBACK_CONFIGURATIONS] until a match is found or the chain ends
     *  3. If no explicit configurations are provided and strict is false:
     *     a. When [platform] is true, defaults to implementation and testImplementation
     *     b. Otherwise defaults to api
     *
     * Each provided name can be either a literal or a regex wrapped in slashes. Names are matched
     * against [Configuration.getName]. The returned set is unmodifiable.
     */
    private fun configurations(project: Project): Set<Configuration> {
        if (!enabled) return emptySet()

        val configurationNames =
            configurations
                ?: when {
                    strictConfigurations -> emptyList()
                    platform ->
                        listOf(
                            JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME,
                            JavaPlugin.TEST_IMPLEMENTATION_CONFIGURATION_NAME,
                        )
                    else -> listOf(JavaPlugin.API_CONFIGURATION_NAME)
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

        /**
         * Reads library specific options from a version catalog JSON tree.
         *
         * Input expectations:
         *  - [versionCatalogNode] is a Jackson tree created from TOML, where libraries live under
         *    the object at path /libraries
         *  - Each library may contain an object property named [AUTO_CONFIG_PROPERTY_NAME] that
         *    maps to the fields of [LibraryAutoConfigOptions]
         *
         * Transformations:
         *  - Library aliases are normalized by replacing '-' with '.' to match Gradle catalog
         *    alias semantics used elsewhere in this code
         *
         * Side effects:
         *  - When [remove] is true, the [AUTO_CONFIG_PROPERTY_NAME] node is removed from the
         *    library object before parsing
         *
         * Non object entries and libraries without options are ignored.
         *
         * @return a map from normalized alias to parsed [LibraryAutoConfigOptions]
         */
        fun read(
            versionCatalogNode: JsonNode,
            remove: Boolean = false,
        ): Map<String, LibraryAutoConfigOptions> {
            val libraries = versionCatalogNode.at("/libraries")
            if (!libraries.isObject) return emptyMap()

            val result = mutableMapOf<String, LibraryAutoConfigOptions>()

            libraries.fields().forEach { (name, value) ->
                if (value.isObject) {
                    val optionsNode =
                        if (remove) (value as ObjectNode).remove(AUTO_CONFIG_PROPERTY_NAME)
                        else value.get(AUTO_CONFIG_PROPERTY_NAME)

                    if (optionsNode?.isObject == true) {
                        val alias = name.replace('-', '.')
                        val parsed =
                            Utils.tomlMapper.treeToValue(
                                optionsNode,
                                LibraryAutoConfigOptions::class.java,
                            )
                        result[alias] = parsed
                    }
                }
            }
            return result
        }
    }
}
