package org.sakshi.export.report

import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.util.stream.Collectors
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Zips a bundle directory with relative, forward-slash entry names in sorted order and a fixed timestamp. */
internal object ZipPacker {
    private const val FIXED_TIME_MS: Long = 315_532_800_000L

    /** Entry names relative to [directory], sorted. Only regular files; links and directories are skipped. */
    fun entries(directory: Path): List<String> = Files.walk(directory).use { stream ->
        stream.filter { Files.isRegularFile(it, LinkOption.NOFOLLOW_LINKS) }
            .map { directory.relativize(it).joinToString("/") { part -> part.toString() } }
            .sorted()
            .collect(Collectors.toList())
    }

    fun pack(directory: Path, zip: File) {
        val names = entries(directory)
        require(names.none { name -> name.split('/').any { it.isEmpty() || it == ".." || it == "." } }) {
            "Unsafe entry name"
        }
        ZipOutputStream(zip.outputStream().buffered()).use { out ->
            for (name in names) {
                val entry = ZipEntry(name)
                entry.time = FIXED_TIME_MS
                out.putNextEntry(entry)
                Files.copy(directory.resolve(name), out)
                out.closeEntry()
            }
        }
    }
}
