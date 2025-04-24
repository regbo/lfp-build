package com.lfp

import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
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
        settings.gradle.beforeProject(object : Action<Project> {
            override fun execute(project: Project) {
                if (project.projectDir == settings.rootDir) {
                    configureProject(project, emptyList(), Utils.split(project.name))
                }
            }
        })
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
        @Suppress("ObjectLiteralToLambda")
        settings.gradle.beforeProject(object : Action<Project> {
            override fun execute(project: Project) {
                if (project.projectDir == projectDirFile) {
                    configureProject(project, projectPathSegments, projectNameSegments)
                }
            }
        })
        return true
    }


    private fun configureProject(
        project: Project,
        projectPathSegments: List<String>,
        projectNameSegments: List<String>
    ) {
        project.extra["projectPathSegments"] = projectPathSegments
        project.extra["projectNameSegments"] = projectNameSegments
        val packageDirSegments = packageDirSegments(project, projectNameSegments)
        project.extra["packageDirSegments"] = packageDirSegments
        if (project != project.rootProject) {
            configureProjectSrcDir(project, packageDirSegments)
        }
        @Suppress("ObjectLiteralToLambda")
        project.afterEvaluate(object : Action<Project> {
            override fun execute(project: Project) {
                configureProjectAfterEvaluate(project)
            }
        })
    }


    private fun configureProjectSrcDir(project: Project, packageDirSegments: List<String>) {
        val srcMainDir = project.rootDir.resolve("src/main")
        val sourceFileExists = srcMainDir
            .takeIf { it.exists() && it.isDirectory }
            ?.walkTopDown()
            ?.any { it.isFile && (it.extension == "kt" || it.extension == "java") }
            ?: false
        if (sourceFileExists) return
        val packageDirPath = packageDirSegments.joinToString("/")
        val srcMainLanguageDir: File = if (project.buildFile.name.endsWith(".kts")) {
            srcMainDir.resolve("kotlin/$packageDirPath")
        } else {
            srcMainDir.resolve("java/$packageDirPath")
        }
        srcMainLanguageDir.mkdirs()
    }

    private fun configureProjectAfterEvaluate(
        project: Project
    ) {
        val apiConfiguration = project.configurations.findByName("api")
        val implementationConfiguration = project.configurations.findByName("implementation")
        val testImplementationConfiguration = project.configurations.findByName("testImplementation")
        Library.fromProps().forEach { library ->
            val configurations: List<Configuration?> = if (library.testImplementation) {
                listOf(testImplementationConfiguration)
            } else if (library.enforcedPlatform) {
                listOf(implementationConfiguration, testImplementationConfiguration)
            } else {
                listOf(apiConfiguration, implementationConfiguration, testImplementationConfiguration)
            }
            for (configuration in configurations) {
                if (configuration != null) {
                    project.logger.lifecycle("adding library to ${configuration.name} - $library")
                    val notation = library.version?.let { "${library.module}:${library.version}" } ?: library.module
                    val dependencyNotation: Any = if (library.enforcedPlatform) {
                        project.dependencies.enforcedPlatform(notation)
                    } else {
                        notation
                    }
                    project.dependencies.add(configuration.name, dependencyNotation)
                    break
                }
            }
        }
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
    val alias: String,
    val module: String,
    val version: String? = null,
    val enforcedPlatform: Boolean,
    val testImplementation: Boolean
) {
    companion object {
        fun fromProps(): List<Library> {
            return BuildPluginPropertiesBuildConfig.versionCatalogLibraries.map { (alias, fullNotation) ->
                val parts = fullNotation.split(':').toList()
                val module = parts.subList(0, parts.size - 1).joinToString(":")
                val version = parts.lastOrNull()?.takeIf { it.isNotBlank() && parts.size >= 2 }
                Library(
                    alias = alias,
                    module = module,
                    version = version,
                    enforcedPlatform = alias in BuildPluginPropertiesBuildConfig.versionCatalogEnforcedPlatformAliases,
                    testImplementation = alias in BuildPluginPropertiesBuildConfig.versionCatalogTestImplementationAliases,
                )
            }
        }
    }
}
