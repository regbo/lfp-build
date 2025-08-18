package com.lfp.buildplugin

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.initialization.Settings
import org.gradle.api.tasks.Copy
import org.gradle.internal.extensions.core.extra
import java.io.File
import java.net.URI
import java.nio.file.Path
import java.util.regex.Pattern

/**
 * Gradle settings plugin that:
 * - Dynamically discovers and includes subprojects based on the filesystem structure
 * - Configures version catalogs from default resources
 * - Initializes basic source directory structure for new projects
 * - Adds useful metadata (path/name/package segments) to each project
 *
 * Applied to [Settings] rather than [Project], so it operates during the settings phase to control
 * project inclusion and basic setup.
 */
class BuildPlugin : Plugin<Settings> {

    /**
     * Entry point for plugin application. Configures version catalogs, sets up root project
     * metadata, and walks the file tree to find and include subprojects.
     */
    override fun apply(settings: Settings) {
        configureRepositories(settings)
        configureCaches(settings)
        configureVersionCatalogs(settings)

        // Configure root project metadata
        settings.gradle.beforeProject(
            Utils.action { project ->
                if (project.projectDir == settings.rootDir) {
                    beforeProjectEvaluated(project, emptyList(), Utils.split(project.name))
                }
            }
        )
        // Walk the root directory tree to discover and include module projects
        val buildScanner = BuildScanner(settings.rootDir.toPath())
        buildScanner.accept(
            Utils.action { buildFile -> includeProject(settings, buildFile.parent) }
        )
    }

    /**
     * Locates and applies default version catalogs packaged with the plugin.
     *
     * Searches the classpath for `default.libs.versions.toml` under the plugin's package path,
     * applies each one found as a Gradle version catalog, and wires up its auto-config options.
     */
    private fun configureVersionCatalogs(settings: Settings) {
        val pattern =
            "classpath*:${BuildPluginBuildConfig.plugin_package_name.replace('.', '/')}/default.libs.versions.toml"
        val resources = Utils.resources(pattern)
        if (resources.isEmpty()) {
            Utils.logger.warn("version catalogs not found")
            return
        }
        for (resource in resources) {
            val resourceVersionCatalog = VersionCatalog.from(settings, resource)
            resourceVersionCatalog.execute(settings)
        }
    }

    /**
     * Configures dependency resolution repositories for the given Gradle [Settings] instance.
     *
     * This method modifies the global dependency resolution management configuration.
     *
     * @param settings the Gradle [Settings] to configure.
     */
    @Suppress("UnstableApiUsage")
    private fun configureRepositories(settings: Settings) {
        settings.dependencyResolutionManagement {
            repositories {
                mavenCentral()
                google()
                maven { url = URI("https://jitpack.io") }
            }
        }
    }

    /**
     * Configures local build cache retention centrally.
     */
    private fun configureCaches(settings: Settings, days: Int = 7) {
        settings.buildCache {
            local {
                isEnabled = true
                directory = File(settings.rootDir, ".gradle/build-cache")
                removeUnusedEntriesAfterDays = days
            }
        }
    }

    /**
     * Includes the given directory as a Gradle subproject if valid, and registers configuration
     * logic for it.
     *
     * @param settings The Gradle settings instance
     * @param projectDir The path to the candidate project directory
     * @return true if included, false if skipped
     */
    private fun includeProject(settings: Settings, projectDir: Path): Boolean {
        val projectDirFile = projectDir.toFile().canonicalFile
        val projectDirRelativePath = projectDirFile.relativeTo(settings.rootDir.canonicalFile).path
        val projectPathSegments =
            projectDirRelativePath.split(Pattern.quote(File.separator).toRegex())
        if (projectPathSegments.isEmpty()) return false

        val projectNameSegments = projectNameSegments(projectPathSegments)
        if (projectNameSegments.isEmpty()) return false

        val projectName = projectNameSegments.joinToString("-")
        val projectPath = ":$projectName"

        Utils.logger.lifecycle(
            "including project $projectPath [${projectPathSegments.joinToString("/")}]"
        )

        settings.include(projectPath)
        val descriptor = settings.project(projectPath)
        descriptor.name = projectName
        descriptor.projectDir = projectDirFile

        settings.gradle.beforeProject(
            Utils.action { project ->
                if (project.projectDir == projectDirFile) {
                    beforeProjectEvaluated(project, projectPathSegments, projectNameSegments)
                }
            }
        )
        return true
    }

    /**
     * Stores metadata about the project in its extra properties and initializes its source
     * directory if needed.
     */
    private fun beforeProjectEvaluated(
        project: Project,
        projectPathSegments: List<String>,
        projectNameSegments: List<String>,
    ) {
        project.extra["projectPathSegments"] = projectPathSegments
        project.extra["projectNameSegments"] = projectNameSegments
        val packageDirSegments = packageDirSegments(project, projectNameSegments)
        project.extra["packageDirSegments"] = packageDirSegments
        configureProjectLogbackXml(project)
        if (project != project.rootProject) {
            configureProjectSrcDir(project, packageDirSegments)
        }
    }

    /**
     * Contributes a default logback.xml to processResources with low precedence.
     *
     * Precedence rules
     * - This spec sets DuplicatesStrategy.INCLUDE so later specs that add the same path overwrite
     *   it.
     * - If another plugin also adds logback.xml, the last one configured wins.
     *
     * Safety
     * - No effect if the module already has src/main/resources/logback.xml.
     */
    private fun configureProjectLogbackXml(project: Project) {
        val moduleHasOwn =
            project.layout.projectDirectory.file("src/main/resources/logback.xml").asFile.exists()
        if (moduleHasOwn) return

        // language=xml
        val logbackXmlContent =
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
                .trimIndent()

        val fromProvider = project.resources.text.fromString(logbackXmlContent)

        project.tasks.withType(Copy::class.java).configureEach {
            if (name == "processResources") {
                duplicatesStrategy = DuplicatesStrategy.INCLUDE
                from(fromProvider) {
                    rename { "logback.xml" }
                    into("")
                }
            }
        }
    }

    /**
     * Ensures the default `src/main/java` or `src/main/kotlin` package directory exists for a new
     * project if no source files are present yet.
     */
    private fun configureProjectSrcDir(project: Project, packageDirSegments: List<String>) {
        val srcMainDir = project.projectDir.resolve("src/main")
        val sourceFileExists =
            srcMainDir
                .takeIf { it.exists() && it.isDirectory }
                ?.walkTopDown()
                ?.any { it.isFile && (it.extension == "kt" || it.extension == "java") } ?: false

        if (sourceFileExists) return

        val packageDirPath = packageDirSegments.joinToString("/")
        val srcMainLanguageDir =
            if (project.buildFile.name.endsWith(".kts")) {
                srcMainDir.resolve("kotlin/$packageDirPath")
            } else {
                srcMainDir.resolve("java/$packageDirPath")
            }

        srcMainLanguageDir.mkdirs()
    }

    /**
     * Converts a list of project path segments into clean lowercase name segments, splitting on
     * non-alphanumeric and CamelCase boundaries, and removing leading "module" or "modules" if
     * present.
     */
    private fun projectNameSegments(projectPathSegments: List<String>): List<String> {
        var segments =
            projectPathSegments.flatMap {
                Utils.split(it, nonAlphaNumeric = true, camelCase = true, lowercase = true)
            }
        if (segments.size > 1 && segments[0].matches(Regex("^modules?$"))) {
            segments = segments.drop(1)
        }
        return segments
    }

    /**
     * Derives the Java/Kotlin package directory segments from the project's group and name. If
     * group is not set, falls back to the root project's group.
     */
    private fun packageDirSegments(project: Project, nameSegments: List<String>): List<String> {
        var groupSegments = Utils.split(project.group.toString(), nonAlphaNumeric = true)
        if (groupSegments.isEmpty() && project != project.rootProject) {
            groupSegments =
                Utils.split(project.rootProject.group.toString(), nonAlphaNumeric = true)
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
