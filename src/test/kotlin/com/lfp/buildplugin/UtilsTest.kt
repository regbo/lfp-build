package com.lfp.buildplugin

import com.lfp.buildplugin.shared.Utils
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.core.io.support.PathMatchingResourcePatternResolver
import java.io.File
import java.util.regex.Pattern

class UtilsTest {

    @Test
    fun `split path`() {
        val file = File("test")
        val subFile = File(arrayOf("test", "sub", "dirName", "file-name").joinToString(File.separator))
        val result =
            subFile.canonicalFile.relativeTo(file.canonicalFile).path.split(Pattern.quote(File.separator).toRegex())
                .flatMap { segment ->
                    Utils.split(
                        segment,
                        lowercase = true,
                        camelCase = true,
                        nonAlphaNumeric = true
                    )
                }
        assertEquals(listOf("sub", "dir", "name", "file", "name"), result)
    }

    @Test
    fun `split comma`() {
        val result = Utils.split(
            "this   ,   is,a  ,test, wow"
        )
        assertEquals(listOf("this", "is", "a", "test", "wow"), result)
    }

    @Test
    fun `split comma quoted`() {
        val result = Utils.split(
            "\"this   \",\"   is\",\"a  \",\"test, wow\""
        )
        assertEquals(listOf("this", "is", "a", "test, wow"), result)
    }

    @Test
    fun `split only trims`() {
        val result = Utils.split("  Hello  ")
        assertEquals(listOf("Hello"), result)
    }

    @Test
    fun `split with lowercase`() {
        val result = Utils.split("  Hello World ", lowercase = true)
        assertEquals(listOf("hello world"), result)
    }

    @Test
    fun `split non alpha numeric with lowercase`() {
        val result = Utils.split("  Hello World ", lowercase = true, nonAlphaNumeric = true)
        assertEquals(listOf("hello", "world"), result)
    }

    @Test
    fun `split with non-alphanumeric`() {
        val result = Utils.split("Hello-World_2024", nonAlphaNumeric = true)
        assertEquals(listOf("Hello", "World", "2024"), result)
    }

    @Test
    fun `split with camelCase`() {
        val result = Utils.split("HTTPRequestParser", camelCase = true)
        assertEquals(listOf("HTTP", "Request", "Parser"), result)
    }

    @Test
    fun `split with lowercase, camelCase, and nonAlphaNumeric`() {
        val result =
            Utils.split("Hello-JSONParser_2024", nonAlphaNumeric = true, camelCase = true, lowercase = true)
        assertEquals(listOf("hello", "json", "parser", "2024"), result)
    }

    @Test
    fun `split with null and empty string`() {
        assertEquals(emptyList<String>(), Utils.split(null))
        assertEquals(emptyList<String>(), Utils.split("  "))
    }

}

