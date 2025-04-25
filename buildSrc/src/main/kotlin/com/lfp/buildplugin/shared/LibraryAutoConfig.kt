package com.lfp.buildplugin.shared

import org.gradle.api.plugins.ExtensionContainer

/**
 * Base class for configuring how dependencies should be auto-wired to configurations.
 */
open class LibraryAutoConfig {
    var enabled = true
    var strictConfigurations = false
    var configurations: List<String>? = null

    /**
     * Creates and registers the extension inside a project's or settings' [ExtensionContainer].
     */
    fun createExtension(extensions: ExtensionContainer): LibraryAutoConfig {
        return extensions.create("libraryAutoConfig", LibraryAutoConfig::class.java)
    }

    override fun toString(): String {
        return Utils.toString(this)
    }
}
