package org.sakshi.tools.policy

import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

/** One text file of the repository: its path relative to the `android/` root (always with `/`) and its content. */
internal class RepoFile(val path: String, val text: String) {
    /** Gradle module directory, for example `core/vault` or `app`: the path up to the first `src` segment. */
    val module: String = path.substringBefore("/src/", missingDelimiterValue = "")

    val extension: String = path.substringAfterLast('.', missingDelimiterValue = "")

    val isMainSource: Boolean = "/src/main/" in "/$path" && extension in MAIN_EXTENSIONS

    val isBuildFile: Boolean = path.endsWith(".gradle.kts") || path == "gradle/libs.versions.toml"

    private companion object {
        val MAIN_EXTENSIONS = setOf("kt", "java", "xml")
    }
}

/**
 * Shared file walking for every policy test. The policy module itself is excluded from all scans in this one place
 * ([EXCLUDED_DIRECTORIES]) because its tests must be able to name the forbidden tokens they look for.
 */
internal object Repo {
    private val SKIPPED_DIRECTORY_NAMES = setOf("build", ".gradle", ".git", ".idea", ".kotlin", "node_modules")
    private val EXCLUDED_DIRECTORIES = setOf("tools/policy")
    private val TEXT_EXTENSIONS = setOf("kt", "java", "xml", "kts", "md", "toml", "properties", "pro")

    /** The `android/` directory: the nearest ancestor of the working directory that holds `settings.gradle.kts`. */
    val root: Path by lazy { locateRoot(Path.of("").toAbsolutePath()) }

    /** Every text file under [root] that any policy check may need, read once. */
    val files: List<RepoFile> by lazy { walk(root) }

    val mainSources: List<RepoFile> get() = files.filter { it.isMainSource }

    val buildFiles: List<RepoFile> get() = files.filter { it.isBuildFile }

    fun locateRoot(start: Path): Path {
        var directory: Path? = start
        while (directory != null) {
            if (Files.isRegularFile(directory.resolve("settings.gradle.kts"))) return directory
            directory = directory.parent
        }
        error("Policy tests could not find settings.gradle.kts in $start or any parent directory")
    }

    fun walk(root: Path): List<RepoFile> {
        val found = mutableListOf<RepoFile>()
        Files.walkFileTree(
            root,
            object : SimpleFileVisitor<Path>() {
                override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                    val relative = relativePath(root, dir)
                    val skipped = dir.fileName?.toString() in SKIPPED_DIRECTORY_NAMES || relative in EXCLUDED_DIRECTORIES
                    return if (dir != root && skipped) FileVisitResult.SKIP_SUBTREE else FileVisitResult.CONTINUE
                }

                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    if (file.fileName.toString().substringAfterLast('.', "") in TEXT_EXTENSIONS) {
                        found += RepoFile(relativePath(root, file), Files.readString(file))
                    }
                    return FileVisitResult.CONTINUE
                }
            },
        )
        return found.sortedBy { it.path }
    }

    private fun relativePath(root: Path, path: Path): String = root.relativize(path).joinToString("/")
}
