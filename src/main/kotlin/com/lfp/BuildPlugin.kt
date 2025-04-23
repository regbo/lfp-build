package com.lfp

import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.initialization.Settings
import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.Logging
import org.gradle.internal.extensions.core.extra
import java.io.File
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.regex.Pattern
import kotlin.math.min

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
        addDependency(
            project,
            "org.springframework.boot:spring-boot-dependencies",
            BuildPluginProperties.spring_boot_dependencies_version,
            enforcedPlatform = true
        )
        addDependency(project, "org.apache.commons:commons-lang3", BuildPluginProperties.apache_commons_lang3_version)
        addDependency(project, "one.util:streamex", BuildPluginProperties.one_util_streamex_version)
        addDependency(project, "ch.qos.logback:logback-classic", BuildPluginProperties.qos_logback_classic_version)
        addDependency(project, "org.apache.logging.log4j:log4j-to-slf4j")
        addDependency(project, "org.slf4j:jul-to-slf4j")
        addDependency(project, "org.springframework.boot:spring-boot-starter-test", configurationName = "testImplementation")
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

    private fun addDependency(
        project: Project,
        module: String,
        minimumVersion: String? = null,
        configurationName: String = "implementation",
        enforcedPlatform: Boolean = false
    ) {
        val notation = minimumVersion?.let { "$module:$it" } ?: module
        val dependencyNotation: Any
        if (enforcedPlatform) {
            dependencyNotation = project.dependencies.enforcedPlatform(notation)
        } else {
            dependencyNotation = notation
        }
        project.dependencies.add(configurationName, dependencyNotation)
        if (minimumVersion != null) {
            project.dependencies.constraints {
                add(configurationName, notation) {
                    version {
                        require(minimumVersion)
                    }
                }
            }
        }
    }

}

