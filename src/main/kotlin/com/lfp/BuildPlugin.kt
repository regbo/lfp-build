package com.lfp

import org.gradle.api.*
import org.gradle.api.initialization.Settings
import org.gradle.api.logging.LogLevel
import org.gradle.internal.extensions.core.extra
import java.io.File
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.util.regex.Pattern

/**
 * A Gradle plugin that dynamically discovers subprojects and configures them
 * based on their directory structure and naming.
 */
@Suppress("ObjectLiteralToLambda")
class BuildPlugin : Plugin<Settings> {

    /**
     * Entry point: Applies the plugin to the [Settings] object.
     */
    override fun apply(settings: Settings) {
        // Configure the root project
        settings.gradle.beforeProject(object : Action<Project> {
            override fun execute(project: Project) {
                if (project.projectDir == settings.rootDir) {
                    configureProject(project, emptyList(), Utils.split(project.name))
                }
            }
        })

        // Recursively walk the root directory to find valid subprojects
        Files.walkFileTree(settings.rootDir.toPath(), object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(dir: Path?, attrs: BasicFileAttributes): FileVisitResult {
                if (isModuleProjectDir(settings, dir) && include(settings, dir!!)) {
                    return FileVisitResult.SKIP_SUBTREE
                }
                return super.preVisitDirectory(dir, attrs)
            }
        })
    }

    /**
     * Returns true if the given directory appears to contain a Gradle module.
     */
    private fun isModuleProjectDir(settings: Settings, dir: Path?): Boolean {
        if (dir != null && !Files.isHidden(dir) && settings.rootDir != dir) {
            val dirName = dir.fileName.toString()
            if (!dirName.startsWith(".") && dirName !in listOf("src", "build", "temp", "tmp")) {
                for (suffix in listOf("", ".kts")) {
                    if (Files.isRegularFile(dir.resolve("build.gradle$suffix"))) {
                        return true
                    }
                }
            }
        }
        return false
    }

    /**
     * Includes a subproject and configures it if it is a valid module directory.
     */
    private fun include(settings: Settings, projectDir: Path): Boolean {
        val projectDirFile = projectDir.toFile().canonicalFile
        val projectDirRelativePath = projectDirFile.relativeTo(settings.rootDir.canonicalFile).path
        val projectPathSegments = projectDirRelativePath.split(Pattern.quote(File.separator).toRegex())
        if (projectPathSegments.isEmpty()) return false

        val projectNameSegments = projectNameSegments(projectPathSegments)
        if (projectNameSegments.isEmpty()) return false

        val projectName = projectNameSegments.joinToString("-")
        val projectPath = ":$projectName"

        Utils.logger.lifecycle("including project $projectPath [${projectPathSegments.joinToString("/")}]")

        settings.include(projectPath)
        val descriptor = settings.project(projectPath)
        descriptor.name = projectName
        descriptor.projectDir = projectDirFile

        settings.gradle.beforeProject(object : Action<Project> {
            override fun execute(project: Project) {
                if (project.projectDir == projectDirFile) {
                    configureProject(project, projectPathSegments, projectNameSegments)
                }
            }
        })

        return true
    }

    /**
     * Adds path/name/dir metadata to a project and initializes its source directory if needed.
     */
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
        LombokPlugin().apply(project)

        project.afterEvaluate(object : Action<Project> {
            override fun execute(project: Project) {
                configureProjectAfterEvaluate(project)
            }
        })
    }

    /**
     * Creates the default source directory structure if no source files exist.
     */
    private fun configureProjectSrcDir(project: Project, packageDirSegments: List<String>) {
        val srcMainDir = project.projectDir.resolve("src/main")
        val sourceFileExists = srcMainDir
            .takeIf { it.exists() && it.isDirectory }
            ?.walkTopDown()
            ?.any { it.isFile && (it.extension == "kt" || it.extension == "java") }
            ?: false

        if (sourceFileExists) return

        val packageDirPath = packageDirSegments.joinToString("/")
        val srcMainLanguageDir = if (project.buildFile.name.endsWith(".kts")) {
            srcMainDir.resolve("kotlin/$packageDirPath")
        } else {
            srcMainDir.resolve("java/$packageDirPath")
        }

        srcMainLanguageDir.mkdirs()
    }

    /**
     * Adds external libraries declared via BuildConfig to the appropriate configurations.
     */
    private fun configureProjectAfterEvaluate(project: Project) {
        val api = project.configurations.findByName("api")
        val impl = project.configurations.findByName("implementation")
        val testImpl = project.configurations.findByName("testImplementation")
        VersionCatalog.instance.libraries.map { it.value }.filter { !it.buildOnly }.forEach { libraryEntry ->
            val configurations = when {
                libraryEntry.testImplementation -> listOf(testImpl)
                libraryEntry.enforcedPlatform -> listOf(impl, testImpl)
                else -> listOf(api, impl, testImpl)
            }

            val added = configurations.any { config ->
                if (config != null) {
                    val dependencyNotation = libraryEntry.dependencyNotation(project)
                    project.logger.log(LogLevel.DEBUG, "added library to ${config.name} - $libraryEntry")
                    project.dependencies.add(config.name, dependencyNotation)
                    true
                } else false
            }

            if (!added) {
                project.logger.log(LogLevel.DEBUG, "skipping library - $libraryEntry")
            }
        }
    }

    /**
     * Derives a clean list of name segments from a project path.
     */
    private fun projectNameSegments(projectPathSegments: List<String>): List<String> {
        var segments = projectPathSegments.flatMap {
            Utils.split(it, nonAlphaNumeric = true, camelCase = true, lowercase = true)
        }
        if (segments.size > 1 && segments[0].matches(Regex("^modules?$"))) {
            segments = segments.drop(1)
        }
        return segments
    }

    /**
     * Computes the source package directory structure from the group and project name.
     */
    private fun packageDirSegments(project: Project, nameSegments: List<String>): List<String> {
        var groupSegments = Utils.split(project.group.toString(), nonAlphaNumeric = true)
        if (groupSegments.isEmpty() && project != project.rootProject) {
            groupSegments = Utils.split(project.rootProject.group.toString(), nonAlphaNumeric = true)
        }

        return if (groupSegments.isEmpty()) {
            nameSegments
        } else {
            val combined = groupSegments.toMutableList()
            for (i in nameSegments.indices) {
                if (combined.getOrNull(i) == groupSegments.lastOrNull()) continue
                combined.add(nameSegments[i])
            }
            combined.toList()
        }
    }
}


