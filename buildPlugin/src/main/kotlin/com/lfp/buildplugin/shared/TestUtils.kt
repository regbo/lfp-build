@file:Suppress("ObjectLiteralToLambda")

package com.lfp.buildplugin.shared

import com.fasterxml.jackson.dataformat.toml.TomlMapper
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.apache.commons.lang3.builder.ToStringBuilder
import org.apache.commons.lang3.builder.ToStringStyle
import org.gradle.api.initialization.Settings
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import java.util.*
import java.util.regex.Pattern
import java.util.stream.Stream

object TestUtils {
    val logger: Logger by lazy {
        Logging.getLogger(Settings::class.java)
    }
    val tomlMapper: TomlMapper by lazy {
        val tomlMapper = TomlMapper()
        tomlMapper.registerModules(Jdk8Module(), KotlinModule.Builder().build())
        tomlMapper
    }


    fun sayHello() {
        println("Hello")
    }

    fun toString(input: Any?): String {
        if (input == null) return Objects.toString(null)
        return ToStringBuilder.reflectionToString(input, ToStringStyle.SHORT_PREFIX_STYLE)
    }

    fun toRegex(input: String): Regex {
        return if (input.length > 2 && Stream.of(0, input.length - 1).allMatch { input[it] == '/' }) {
            input.substring(1, input.length - 1).toRegex()
        } else {
            Regex("^" + Pattern.quote(input) + "$")
        }
    }

}