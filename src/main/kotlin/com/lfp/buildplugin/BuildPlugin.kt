package com.lfp.buildplugin

import com.lfp.buildplugin.shared.Utils
import com.lfp.buildplugin.shared.VersionCatalog
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.initialization.Settings
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.internal.extensions.core.extra
import java.io.File
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.regex.Pattern

/**
 * Gradle settings plugin that:
 *  - Dynamically discovers and includes subprojects based on the filesystem structure
 *  - Configures version catalogs from default resources
 *  - Initializes basic source directory structure for new projects
 *  - Adds useful metadata (path/name/package segments) to each project
 *
 * Applied to [Settings] rather than [Project], so it operates during the settings
 * phase to control project inclusion and basic setup.
 */
class BuildPlugin : Plugin<Settings> {

    /**
     * Entry point for plugin application.
     * Configures version catalogs, sets up root project metadata,
     * and walks the file tree to find and include subprojects.
     */
    override fun apply(settings: Settings) {
        configureVersionCatalogs(settings)

        // Configure root project metadata
        settings.gradle.beforeProject(Utils.action { project ->
            if (project.projectDir == settings.rootDir) {
                beforeProjectEvaluated(project, emptyList(), Utils.split(project.name))
            }
        })
        // Configure project after evaluated
        settings.gradle.afterProject(Utils.action { project ->
            afterProjectEvaluated(project)
        })

        // Walk the root directory tree to discover and include module projects
        Files.walkFileTree(settings.rootDir.toPath(), object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(dir: Path?, attrs: BasicFileAttributes): FileVisitResult {
                if (isModuleProjectDir(settings, dir) && include(settings, dir!!)) {
                    // Skip recursion into a module once included
                    return FileVisitResult.SKIP_SUBTREE
                }
                return super.preVisitDirectory(dir, attrs)
            }
        })
    }

    /**
     * Locates and applies default version catalogs packaged with the plugin.
     *
     * Searches the classpath for `default.libs.versions.toml` under the
     * plugin's package path, applies each one found as a Gradle version catalog,
     * and wires up its auto-config options.
     */
    private fun configureVersionCatalogs(settings: Settings) {
        val pattern =
            "classpath*:${BuildPluginBuildConfig.plugin_package_name.replace('.', '/')}/default.libs.versions.toml"
        val resources = Utils.resources(pattern)
        var found = false
        for (resource in resources) {
            val resourceVersionCatalog = VersionCatalog.from(settings, resource)
            resourceVersionCatalog.execute(settings)
            found = true
        }
        if (!found) {
            Utils.logger.warn("version catalogs not found")
        }
    }

    /**
     * Returns true if the given directory looks like a Gradle module project.
     *
     * A valid module:
     *  - Is not hidden
     *  - Is not the root directory
     *  - Is not in excluded directory names (`src`, `build`, `temp`, `tmp`)
     *  - Contains a `build.gradle` or `build.gradle.kts` file
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
     * Includes the given directory as a Gradle subproject if valid, and
     * registers configuration logic for it.
     *
     * @param settings The Gradle settings instance
     * @param projectDir The path to the candidate project directory
     * @return true if included, false if skipped
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

        settings.gradle.beforeProject(Utils.action { project ->
            if (project.projectDir == projectDirFile) {
                beforeProjectEvaluated(project, projectPathSegments, projectNameSegments)
            }
        })
        return true
    }

    /**
     * Stores metadata about the project in its extra properties and
     * initializes its source directory if needed.
     */
    private fun beforeProjectEvaluated(
        project: Project, projectPathSegments: List<String>, projectNameSegments: List<String>
    ) {
        project.extra["projectPathSegments"] = projectPathSegments
        project.extra["projectNameSegments"] = projectNameSegments
        val packageDirSegments = packageDirSegments(project, projectNameSegments)
        project.extra["packageDirSegments"] = packageDirSegments
        if (project != project.rootProject) {
            configureProjectSrcDir(project, packageDirSegments)
        }
    }

    /**
     * Executes post-evaluation configuration tasks for a project.
     *
     * This method is invoked after the project has been evaluated and performs
     * tasks that rely on the presence of the build output directory.
     *
     * @param project The Gradle [Project] to configure
     */
    private fun afterProjectEvaluated(project: Project) {
        configureProjectLogbackXml(project)
    }

    /**
     * Ensures a default `logback.xml` exists under the build output resources for the `main` source set.
     *
     * The method resolves `build/resources/main` from the `main` source set output. If the directory
     * exists and no `logback.xml` is present, it creates the parent directory if needed and writes a
     * standard console-only Logback configuration with a sensible pattern at INFO level.
     *
     * @param project The Gradle [Project] to configure
     */
    private fun configureProjectLogbackXml(project: Project) {
        val resourcesDir = project.extensions
            .findByType(SourceSetContainer::class.java)
            ?.findByName("main")
            ?.output
            ?.resourcesDir
        if (resourcesDir != null) {
            val logbackXml = File(resourcesDir, "logback.xml")
            if (!logbackXml.exists()) {
                logbackXml.parentFile.mkdirs()
                //language=xml
                val logbackXmlContent = """
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
            """.trimIndent()
                logbackXml.writeText(logbackXmlContent, charset = Charsets.UTF_8)
                Utils.logger.debug("logback configuration created - {}", logbackXml.absolutePath)
            }
        }
    }


    /**
     * Ensures the default `src/main/java` or `src/main/kotlin` package directory exists
     * for a new project if no source files are present yet.
     */
    private fun configureProjectSrcDir(project: Project, packageDirSegments: List<String>) {
        val srcMainDir = project.projectDir.resolve("src/main")
        val sourceFileExists = srcMainDir.takeIf { it.exists() && it.isDirectory }?.walkTopDown()
            ?.any { it.isFile && (it.extension == "kt" || it.extension == "java") } ?: false

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
     * Converts a list of project path segments into clean lowercase name segments,
     * splitting on non-alphanumeric and CamelCase boundaries, and removing
     * leading "module" or "modules" if present.
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
     * Derives the Java/Kotlin package directory segments from the project's group
     * and name. If group is not set, falls back to the root project's group.
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
