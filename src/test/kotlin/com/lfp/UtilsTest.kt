package com.lfp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
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

    @Nested
    inner class SplitNonAlphaNumeric {

        @Test
        fun `splits by symbols and spaces`() {
            val result = Utils.splitNonAlphaNumeric("hello-world_test 123")
            assertEquals(listOf("hello", "world", "test", "123"), result)
        }

        @Test
        fun `trims and removes empty segments`() {
            val result = Utils.splitNonAlphaNumeric("  one  , , two,, ,three ")
            assertEquals(listOf("one", "two", "three"), result)
        }

        @Test
        fun `returns empty list for null or blank`() {
            assertEquals(emptyList<String>(), Utils.splitNonAlphaNumeric(null))
            assertEquals(emptyList<String>(), Utils.splitNonAlphaNumeric("   "))
        }
    }

    @Nested
    inner class SplitCamelCase {

        @Test
        fun `splits mixed camelCase and acronyms`() {
            val result = Utils.splitCamelCase("HTTPRequestHandler")
            assertEquals(listOf("HTTP", "Request", "Handler"), result)
        }

        @Test
        fun `splits PascalCase`() {
            val result = Utils.splitCamelCase("MyXMLParser")
            assertEquals(listOf("My", "XML", "Parser"), result)
        }

        @Test
        fun `splits single word`() {
            val result = Utils.splitCamelCase("Simple")
            assertEquals(listOf("Simple"), result)
        }

        @Test
        fun `returns empty list for null or blank`() {
            assertEquals(emptyList<String>(), Utils.splitCamelCase(null))
            assertEquals(emptyList<String>(), Utils.splitCamelCase("  "))
        }
    }

    @Nested
    inner class SplitCombined {

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
                Utils.split("Hello-JSONParser_2024", lowercase = true, nonAlphaNumeric = true, camelCase = true)
            assertEquals(listOf("hello", "jsonparser", "2024"), result)
        }

        @Test
        fun `split with null and empty string`() {
            assertEquals(emptyList<String>(), Utils.split(null))
            assertEquals(emptyList<String>(), Utils.split("  "))
        }
    }
}
