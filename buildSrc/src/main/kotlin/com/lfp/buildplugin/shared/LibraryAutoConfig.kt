package com.lfp.buildplugin.shared

open class LibraryAutoConfig {
    var enabled = true
    var strictConfigurations = false
    var configurations: List<String>? = null

    override fun toString(): String {
        return Utils.toString(this)
    }


    companion object {
        const val EXTENSION_NAME: String = "libraryAutoConfig"
    }


}