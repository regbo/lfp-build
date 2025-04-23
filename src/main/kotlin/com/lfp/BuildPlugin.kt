package com.lfp

import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.initialization.Settings
import org.gradle.api.logging.Logging
import org.gradle.internal.extensions.core.extra
import java.io.File
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.regex.Pattern

class BuildPlugin : Plugin<Settings> {

    private val logger = Logging.getLogger(javaClass)


    override fun apply(settings: Settings) {
        Files.walkFileTree(settings.rootDir.toPath(), object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(dir: Path?, attrs: BasicFileAttributes): FileVisitResult {
                if (isModuleProjectDir(settings, dir) && include(settings, dir!!)) {
                    return FileVisitResult.SKIP_SUBTREE
                }
                return super.preVisitDirectory(dir, attrs)

            }
        })
    }

    private fun isModuleProjectDir(settings: Settings, dir: Path?): Boolean {
        if (dir != null && !Files.isHidden(dir) && settings.rootDir != dir) {
            val dirFileName = dir.fileName.toString();
            if (!dirFileName.startsWith(".") && !arrayOf("src", "build", "temp", "tmp").contains(dirFileName)) {
                for (suffix in arrayOf("", ".kts")) {
                    val buildFile = dir.resolve("build.gradle$suffix")
                    if (Files.isRegularFile(buildFile)) {
                        return true
                    }
                }
            }
        }
        return false
    }

    @Suppress("ObjectLiteralToLambda")
    private fun include(settings: Settings, projectDir: Path): Boolean {
        val projectDirFile = projectDir.toFile().canonicalFile
        val projectDirRelativePath = projectDirFile.relativeTo(settings.rootDir.canonicalFile).path
        val projectPathSegments = projectDirRelativePath.split(Pattern.quote(File.separator).toRegex())
        if (projectPathSegments.isEmpty()) return false
        val projectNameSegments = projectNameSegments(projectPathSegments)
        if (projectNameSegments.isEmpty()) return false
        val projectName = projectNameSegments.joinToString("-")
        val projectPath = ":$projectName"
        val logMessage = "including project $projectPath [${projectPathSegments.joinToString("/")}]"
        Utils.logger.lifecycle(logMessage)
        settings.include(projectPath)
        val projectDescriptor = settings.project(projectPath)
        projectDescriptor.name = projectName
        projectDescriptor.projectDir = projectDirFile
        settings.gradle.beforeProject(object : Action<Project> {
            override fun execute(project: Project) {
                if (project.projectDir == projectDirFile) {
                    configureProject(settings, project, projectPathSegments, projectNameSegments)
                }
            }
        })
        return true
    }

    private fun projectNameSegments(projectPathSegments: List<String>): List<String> {
        var projectNameSegments = projectPathSegments.flatMap {
            Utils.split(
                it, nonAlphaNumeric = true, camelCase = true, lowercase = true
            )
        };
        if (projectNameSegments.size > 1 && projectNameSegments[0].matches("^modules?$".toRegex())) {
            projectNameSegments = projectNameSegments.subList(1, projectNameSegments.size)
        }
        return projectNameSegments
    }


    private fun configureProject(
        settings: Settings, project: Project, projectPathSegments: List<String>, projectNameSegments: List<String>
    ) {
        project.extra["projectPathSegments"] = projectPathSegments
        project.extra["projectNameSegments"] = projectNameSegments
        project.extra["packageDirSegments"] = packageDirSegments(project, projectNameSegments)
        Library.fromProps().forEach { library ->
            Utils.logger.lifecycle("adding library - $library")
            val notation = library.version?.let { "${library.module}:${library.version}" } ?: library.module
            val dependencyNotation: Any
            if (library.enforcedPlatform) {
                dependencyNotation = project.dependencies.enforcedPlatform(notation)
            } else {
                dependencyNotation = notation
            }
            project.dependencies.add(library.configurationName, dependencyNotation)
            if (library.version != null) {
                project.dependencies.constraints {
                    add(library.configurationName, notation) {
                        version {
                            require(library.version)
                        }
                    }
                }
            }

        }
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

private data class Library(
    val configurationName: String,
    val alias: String,
    val module: String,
    val version: String? = null,
    val enforcedPlatform: Boolean
) {
    companion object {
        fun fromProps(): List<Library> {
            val enforcedModules = Utils.split(BuildPluginProperties.enforced_platform_modules).toSet()
            val testImplementationModules = Utils.split(BuildPluginProperties.test_implementation_modules).toSet()

            return BuildPluginProperties.versionCatalogLibraries.map { (alias, fullNotation) ->
                val parts = fullNotation.split(':').toList()
                val module = parts.subList(0, parts.size - 1).joinToString(":")
                val version = parts.lastOrNull()
                Library(
                    configurationName = if (module in testImplementationModules) "testImplementation" else "implementation",
                    alias = alias,
                    module = module,
                    version = if (version.isNullOrBlank() || parts.size < 3) null else version,
                    enforcedPlatform = module in enforcedModules
                )
            }
        }
    }
}
