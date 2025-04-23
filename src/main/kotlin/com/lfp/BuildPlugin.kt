package com.lfp

import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.initialization.Settings
import org.gradle.internal.extensions.core.extra
import java.io.File
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.util.regex.Pattern

class BuildPlugin : Plugin<Settings> {
    override fun apply(settings: Settings) {
        val rootDir = settings.rootDir;
        Files.walkFileTree(settings.rootDir.toPath(), object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(dir: Path?, attrs: BasicFileAttributes): FileVisitResult {
                if (dir == null || Files.isHidden(dir) || dir.fileName.toString()
                        .startsWith(".")
                ) return FileVisitResult.SKIP_SUBTREE
                else if (dir != rootDir) {
                    for (suffix in arrayOf("", ".kts")) {
                        val buildFile = dir.resolve("build.gradle$suffix")
                        if (Files.isRegularFile(buildFile) && include(settings, dir)) {
                            return FileVisitResult.SKIP_SUBTREE
                        }
                    }
                }
                return super.preVisitDirectory(dir, attrs)

            }
        })
    }

    @Suppress("ObjectLiteralToLambda")
    private fun include(settings: Settings, projectDir: Path): Boolean {
        val projectPathSegments = projectPathSegments(settings, projectDir)
        if (projectPathSegments.isEmpty()) return false
        val projectPath = ":" + projectPathSegments.joinToString(":")
        println("including project - projectDir:$projectDir projectPath:$projectPath")
        settings.include(projectPath)
        val projectDirFile = projectDir.toFile()
        val projectDescriptor = settings.findProject(projectPath)!!
        projectDescriptor.projectDir = projectDirFile
        settings.gradle.beforeProject(object : Action<Project> {
            override fun execute(project: Project) {
                if (project.projectDir == projectDirFile) {
                    project.extra["projectPathSegments"] = projectPathSegments
                }
            }
        })
        return true
    }

    private fun projectPathSegments(settings: Settings, projectDir: Path): List<String> {
        val projectPathSegments =
            projectDir.toFile().relativeTo(settings.rootDir).path
                .split(Pattern.quote(File.separator))
                .asSequence()
                .flatMap { it.split("[^a-zA-Z0-9]+".toRegex()) }
                .flatMap { it.split("(?<=[a-z])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])".toRegex()) }
                .filter { it.isNotEmpty() }
                .toMutableList()
        if (projectPathSegments.size > 1 && projectPathSegments[0].matches("^modules?$".toRegex())) {
            projectPathSegments.removeAt(0)
        }
        return projectPathSegments.toList()
    }
}