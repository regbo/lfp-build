package com.lfp.buildplugin.shared

import org.gradle.api.Project
import org.gradle.api.initialization.Settings
import org.gradle.api.plugins.ExtensionContainer

open class LibraryAutoConfig {
    var enabled = true
    var strictConfigurations = false
    var configurations: List<String>? = null

    fun createExtension(extensions: ExtensionContainer): LibraryAutoConfig {
        return extensions.create("libraryAutoConfig", LibraryAutoConfig::class.java)
    }

    override fun toString(): String {
        return Utils.toString(this)
    }


}