package com.lfp

import org.gradle.api.initialization.Settings
import org.gradle.api.logging.Logger


object Utils {
    private val SETTINGS_GET_LOGGER_METHOD = Settings::class.java.getMethod("getLogger")

    fun logger(settings: Settings): Logger {
        return SETTINGS_GET_LOGGER_METHOD.invoke(settings) as Logger
    }

    fun split(
        str: String?,
        nonAlphaNumeric: Boolean = false,
        camelCase: Boolean = false,
        lowercase: Boolean = false
    ): List<String> {
        var segments: List<String> = TrimmedList.from(listOf(str))
        if (nonAlphaNumeric) {
            segments = segments.flatMap { splitNonAlphaNumeric(it) }
        }
        if (camelCase) {
            segments = segments.flatMap { splitCamelCase(it) }
        }
        if (lowercase) {
            segments = segments.map { it.lowercase() }
        }
        return segments;
    }

    fun splitNonAlphaNumeric(str: String?): List<String> {
        if (str.isNullOrEmpty()) return emptyList()
        return TrimmedList.from(str.split("[^a-zA-Z0-9]+".toRegex()))
    }

    fun splitCamelCase(str: String?): List<String> {
        if (str.isNullOrEmpty()) return emptyList()

        val chars = str.toCharArray()
        val result = mutableListOf<String>()

        var pos = 1
        var tokenStart = 0
        var currentType = Character.getType(chars[0])

        while (pos < chars.size) {
            val type = Character.getType(chars[pos])
            if (type != currentType) {
                if (type.toByte() == Character.LOWERCASE_LETTER && currentType.toByte() == Character.UPPERCASE_LETTER) {
                    val newTokenStart = pos - 1
                    if (newTokenStart != tokenStart) {
                        result.add(String(chars, tokenStart, newTokenStart - tokenStart))
                        tokenStart = newTokenStart
                    }
                } else {
                    result.add(String(chars, tokenStart, pos - tokenStart))
                    tokenStart = pos
                }
                currentType = type
            }
            pos++
        }

        result.add(String(chars, tokenStart, chars.size - tokenStart))
        return TrimmedList.from(result)
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