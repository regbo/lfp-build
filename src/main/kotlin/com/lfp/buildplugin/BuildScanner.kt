package com.lfp.buildplugin

import org.eclipse.jgit.ignore.FastIgnoreRule
import org.eclipse.jgit.ignore.IgnoreNode
import org.gradle.api.Action
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.function.Consumer
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.useLines

/**
 * Scans a Gradle project tree for `build.gradle` and `build.gradle.kts` files while respecting
 * `.gitignore` rules, hidden directories, and common build/temp directories.
 *
 * This class implements [Consumer] of [Action]<[Path]> — the provided action will be executed for
 * each discovered build file that is not ignored.
 *
 * ## Key Features
 * - Walks the project tree starting from a given [rootDir].
 * - Skips directories that are:
 *     - Git-ignored (via `.gitignore` rules merged from the root down to the current directory)
 *     - Hidden
 *     - Named `build`, `temp`, or `tmp`
 * - Skips files that are Git-ignored.
 * - Detects Gradle build scripts in both Groovy (`build.gradle`) and Kotlin DSL
 *   (`build.gradle.kts`).
 * - Merges `.gitignore` rules found along the traversal path so that nested `.gitignore` files
 *   refine or override higher-level rules, matching Git's behavior.
 *
 * ## Gitignore Handling
 * - Uses JGit's [IgnoreNode] and [FastIgnoreRule] to parse `.gitignore` syntax.
 * - Converts directory-relative patterns into project-root-relative patterns, preserving anchored
 *   and unanchored semantics.
 * - Supports negated patterns (`!pattern`) just like Git.
 *
 * @property rootDir the root directory of the project to scan
 */
class BuildScanner(private val rootDir: Path) : Consumer<Action<Path>> {

    /**
     * Starts scanning the [rootDir] for build files.
     *
     * @param buildFileAction the action to execute for each discovered build file
     */
    override fun accept(buildFileAction: Action<Path>) {
        val fileVisitor =
            object : SimpleFileVisitor<Path>() {

                private var ignoreNode: IgnoreNode = IgnoreNode()

                override fun preVisitDirectory(
                    dir: Path,
                    attrs: BasicFileAttributes,
                ): FileVisitResult {
                    val gitIgnore = dir.resolve(".gitignore")
                    if (gitIgnore.isRegularFile()) {
                        ignoreNode = addGitignore(ignoreNode, gitIgnore)
                    }
                    if (isIgnoredDir(dir) || isGitIgnored(ignoreNode, dir)) {
                        return FileVisitResult.SKIP_SUBTREE
                    }
                    return super.preVisitDirectory(dir, attrs)
                }

                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    if (!isGitIgnored(ignoreNode, file) && isBuildFile(file)) {
                        buildFileAction.execute(file)
                    }
                    return super.visitFile(file, attrs)
                }
            }
        Files.walkFileTree(rootDir, fileVisitor)
    }

    /** Checks if the given file is a recognized Gradle build script. */
    private fun isBuildFile(file: Path): Boolean {
        if (file.isRegularFile()) {
            val fileName = file.fileName.toString()
            for (buildFileNameSuffix in listOf("", ".kts")) {
                val buildFileName = "build.gradle$buildFileNameSuffix"
                if (buildFileName == fileName) {
                    return true
                }
            }
        }
        return false
    }

    /**
     * Checks if a directory should be skipped based on being hidden, starting with `.`, or being in
     * the set of common build/temp directory names.
     */
    private fun isIgnoredDir(dir: Path): Boolean {
        if (dir.isDirectory()) {
            if (Files.isHidden(dir)) {
                return true
            }
            val dirName = dir.fileName.toString()
            if (dirName.startsWith(".") || dirName in listOf("build", "temp", "tmp")) {
                return true
            }
        }
        return false
    }

    /**
     * Checks if the given [candidate] path is ignored according to the current [IgnoreNode] rules.
     */
    @Throws(IOException::class)
    private fun isGitIgnored(ignoreNode: IgnoreNode, candidate: Path): Boolean {
        val rel = rootDir.relativize(candidate.toAbsolutePath().normalize())
        val relUnix = rel.toString().replace('\\', '/')
        val isDir = Files.isDirectory(candidate)
        return ignoreNode.isIgnored(relUnix, isDir) == IgnoreNode.MatchResult.IGNORED
    }

    /**
     * Parses and merges `.gitignore` rules from the given file into the provided [IgnoreNode].
     *
     * @param ignoreNode the current ignore rule set
     * @param gitignore the `.gitignore` file to parse
     * @return a new [IgnoreNode] containing the merged rules
     */
    @Throws(IOException::class)
    private fun addGitignore(ignoreNode: IgnoreNode, gitignore: Path): IgnoreNode {
        val baseDir = gitignore.parent
        var dirRel = rootDir.relativize(baseDir).toString().replace('\\', '/')
        if (dirRel.isNotEmpty() && !dirRel.endsWith("/")) dirRel += "/"
        var resultIgnoreNode = ignoreNode
        gitignore.useLines { lines ->
            lines.forEach { raw ->
                val line = raw.trim()
                if (line.isEmpty() || line.startsWith("#")) return@forEach
                resultIgnoreNode =
                    IgnoreNode(
                        resultIgnoreNode.rules + FastIgnoreRule(rewriteGitignoreRule(line, dirRel))
                    )
            }
        }
        return resultIgnoreNode
    }

    /**
     * Rewrites a `.gitignore` rule so that it is relative to the [rootDir]. This preserves anchored
     * (`/`) and unanchored patterns, as well as negation (`!`).
     */
    private fun rewriteGitignoreRule(rule: String, dirRel: String): String {
        if (dirRel.isEmpty()) return rule
        val neg = rule.startsWith("!")
        var body = if (neg) rule.substring(1) else rule
        body =
            if (body.startsWith("/")) {
                dirRel + body.substring(1)
            } else {
                if (body.startsWith("**")) {
                    dirRel + body
                } else {
                    "$dirRel**/$body"
                }
            }
        return if (neg) "!$body" else body
    }
}
