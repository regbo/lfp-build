package com.lfp.buildplugin.shared

import org.gradle.api.plugins.ExtensionContainer

/**
 * Base configuration for automatically wiring dependencies to Gradle configurations.
 *
 * Properties:
 *  - [enabled]               If false, disables automatic configuration for this library
 *  - [strictConfigurations]  If true, only explicitly listed configurations are considered
 *  - [configurations]         Optional list of configuration name patterns (regex supported)
 */
open class LibraryAutoConfig {
    /** Whether auto-config is enabled for this library. */
    var enabled = true

    /** If true, only [configurations] will be used with no fallbacks. */
    var strictConfigurations = false

    /** Optional list of configuration name patterns (e.g., "api", "implementation"). */
    var configurations: List<String>? = null

    /**
     * Creates and registers this configuration as an extension named `"libraryAutoConfig"`
     * in the given [ExtensionContainer].
     *
     * @param extensions The container to register with
     * @return This [LibraryAutoConfig] instance for chaining
     */
    fun createExtension(extensions: ExtensionContainer): LibraryAutoConfig {
        return extensions.create("libraryAutoConfig", LibraryAutoConfig::class.java)
    }

    /**
     * Returns a short string representation of this config, using reflection-based formatting.
     */
    override fun toString(): String {
        return Utils.toString(this)
    }
}
