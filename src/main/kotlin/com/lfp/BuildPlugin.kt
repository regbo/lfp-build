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
        val projectPathSegments = projectDirFile.relativeTo(settings.rootDir).path.split(Pattern.quote(File.separator))
        if (projectPathSegments.isEmpty()) return false
        val projectNameSegments = projectNameSegments(projectPathSegments)
        val projectName = projectNameSegments.joinToString("-")
        val projectPath = ":$projectName"
        Utils.logger.lifecycle("including project $projectPath [${projectPathSegments.joinToString { "/" }}]")
        settings.include(projectPath)
        val projectDescriptor = settings.project(projectPath)
        projectDescriptor.name = projectName
        projectDescriptor.projectDir = projectDirFile
        settings.gradle.beforeProject(object : Action<Project> {
            override fun execute(project: Project) {
                if (project.projectDir == projectDirFile) {
                    project.extra["projectPathSegments"] = projectPathSegments
                    project.extra["projectNameSegments"] = projectNameSegments
                    project.extra["packageDirSegments"] = packageDirSegments(project, projectNameSegments)
                }
            }
        })
        return true
    }

    private fun projectNameSegments(projectPathSegments: List<String>): List<String> {
        var projectNameSegments =
            projectPathSegments.flatMap { Utils.split(it, lowercase = true, nonAlphaNumeric = true, camelCase = true) };
        if (projectNameSegments[0].matches("^modules?$".toRegex())) {
            projectNameSegments = projectNameSegments.subList(1, projectNameSegments.size)
        }
        return projectNameSegments
    }

    private fun packageDirSegments(project: Project, projectNameSegments: List<String>): List<String> {
        var groupSegments = Utils.split(project.group.toString(), nonAlphaNumeric = true)
        if (groupSegments.isEmpty() && project != project.rootProject) {
            groupSegments = Utils.split(project.rootProject.group.toString(), nonAlphaNumeric = true)
        }
        val packageDirSegments: List<String>
        if (groupSegments.isEmpty()) {
            packageDirSegments = projectNameSegments
        } else {
            packageDirSegments = groupSegments.toMutableList()
            for (i in 0 until projectNameSegments.size) {
                if (packageDirSegments[i] == groupSegments[groupSegments.size - 1]) continue
                packageDirSegments.add(projectNameSegments[i])
            }
        }
        return packageDirSegments.toList();
    }
}

