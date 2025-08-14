package com.lfp.buildplugin

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.io.File

abstract class GenerateLogback : DefaultTask() {
    @get:OutputDirectory
    abstract val outDir: DirectoryProperty

    @TaskAction
    fun run() {
        val dir = outDir.get().asFile
        dir.mkdirs()
        //language=xml
        val content =
            """
                <configuration>
                  <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
                    <encoder>
                      <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
                    </encoder>
                  </appender>
                  <root level="INFO">
                    <appender-ref ref="STDOUT"/>
                  </root>
                </configuration>
                """
        File(dir, "logback.xml").writeText(
            content.trimIndent(),
            Charsets.UTF_8
        )
    }
}