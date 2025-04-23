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
                if (dir == null || Files.isHidden(dir)) {
                    return FileVisitResult.SKIP_SUBTREE
                } else {
                    val dirFileName = dir.fileName.toString();
                    if (dirFileName.startsWith(".") || arrayOf("src", "build", "temp", "tmp").contains(dirFileName)) {
                        return FileVisitResult.SKIP_SUBTREE
                    } else {
                        for (suffix in arrayOf("", ".kts")) {
                            val buildFile = dir.resolve("build.gradle$suffix")
                            if (Files.isRegularFile(buildFile) && include(settings, dir)) {
                                return FileVisitResult.SKIP_SUBTREE
                            }
                        }
                    }
                }
                return super.preVisitDirectory(dir, attrs)

            }
        })
    }

    @Suppress("ObjectLiteralToLambda")
    private fun include(settings: Settings, projectDir: Path): Boolean {
        val projectDirFile = projectDir.toFile()
        val projectPathSegments = segments(projectDirFile.relativeTo(settings.rootDir).path)
        if (projectPathSegments.isEmpty()) return false
        val projectNameSegments = projectPathSegments.toMutableList();
        if (projectNameSegments[0].matches("^modules?$".toRegex())) {
            projectNameSegments.removeAt(0);
        }
        val projectName = projectNameSegments.joinToString("-")
        val projectPath = ":" + projectPathSegments.subList(0, projectPathSegments.size - 1)
            .joinToString(":") + ":" + projectName
        println("including project - projectDir:$projectDir projectPath:$projectPath")
        settings.include(projectPath)

        val projectDescriptor = settings.findProject(projectPath)!!
        projectDescriptor.name = projectName
        projectDescriptor.projectDir = projectDirFile
        settings.gradle.beforeProject(object : Action<Project> {
            override fun execute(project: Project) {
                if (project.projectDir == projectDirFile) {
                    project.extra["projectPathSegments"] = projectPathSegments
                    project.extra["projectNameSegments"] = projectNameSegments
                    project.extra["packageDirSegments"] = segments(project.group.toString()) + projectNameSegments
                }
            }
        })
        return true
    }

    private fun segments(path: String): List<String> {
        return path.split("[^a-zA-Z0-9]+".toRegex())
            .flatMap { it.split("(?<=[a-z])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])".toRegex()) }.filter { it.isNotEmpty() }
            .toList()
    }
}