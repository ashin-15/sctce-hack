package org.sakshi.export.bundle

import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

internal class LayoutScan(val files: Set<String>, val issues: List<String>)

/** Walks a bundle directory without following links and with a bound on the number of entries. */
internal object LayoutScanner {
    private const val EXTRA_ENTRIES: Int = 16
    private const val MAX_DEPTH: Int = 3

    fun scan(root: Path, maxFiles: Int): LayoutScan {
        val files = linkedSetOf<String>()
        val issues = mutableListOf<String>()
        var entries = 0

        fun relative(path: Path): String = root.relativize(path).joinToString("/") { it.toString() }

        val visitor = object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                if (dir == root) return FileVisitResult.CONTINUE
                val name = relative(dir)
                if (name in BundleFormat.DIRECTORIES) return FileVisitResult.CONTINUE
                issues += "unexpected directory ${safe(name)}"
                return FileVisitResult.SKIP_SUBTREE
            }

            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                if (++entries > maxFiles + EXTRA_ENTRIES) {
                    issues += "more than $maxFiles files"
                    return FileVisitResult.TERMINATE
                }
                val name = relative(file)
                when {
                    attrs.isSymbolicLink -> issues += "symbolic link ${safe(name)}"
                    attrs.isRegularFile -> files += name
                    else -> issues += "not a regular file ${safe(name)}"
                }
                return FileVisitResult.CONTINUE
            }

            override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult {
                issues += "unreadable entry ${safe(relative(file))}"
                return FileVisitResult.CONTINUE
            }
        }
        Files.walkFileTree(root, emptySet(), MAX_DEPTH, visitor)
        return LayoutScan(files, issues)
    }
}
