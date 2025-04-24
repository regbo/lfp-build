package com.lfp

import groovy.lang.Closure
import org.apache.commons.lang3.StringUtils
import org.gradle.api.initialization.Settings
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging


object Utils {

    val logger: Logger by lazy {
        Logging.getLogger(Settings::class.java)
    }


    fun split(
        str: String?,
        nonAlphaNumeric: Boolean = false,
        camelCase: Boolean = false,
        lowercase: Boolean = false
    ): List<String> {
        if (str.isNullOrBlank()) return emptyList()
        var segments: List<String> = listOf(str)
        if (nonAlphaNumeric) {
            segments = segments.flatMap { it.split("[^a-zA-Z0-9]+".toRegex()) }
        } else {
            segments = segments.flatMap { it.split("\\s*,\\s*".toRegex()) }
        }
        if (camelCase) {
            segments = segments.flatMap { StringUtils.splitByCharacterTypeCamelCase(it).toList() }
        }
        if (lowercase) {
            segments = segments.map { it.lowercase() }
        }
        return TrimmedList.from(segments);
    }

    fun <T> closure(delegate: Any? = null, block: () -> T): Closure<T> {
        return object : Closure<T>(delegate) {
            override fun call(vararg args: Any?): T = block()
        }
    }
}


private class TrimmedList private constructor(
    private val delegate: List<String>
) : List<String> by delegate {

    companion object {
        fun from(list: List<String?>): TrimmedList {
            return if (list is TrimmedList) {
                list
            } else {
                val trimmed = list.mapNotNull { it?.trim()?.takeIf { trimmed -> trimmed.isNotEmpty() } }
                TrimmedList(trimmed)
            }
        }
    }
}