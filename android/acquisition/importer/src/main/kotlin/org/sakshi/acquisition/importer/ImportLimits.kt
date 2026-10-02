package org.sakshi.acquisition.importer

private const val MIB: Long = 1024L * 1024L

/** Upper bounds for one import. The byte limits are enforced while streaming, whatever size the source claims. */
public data class ImportLimits(
    val maxItems: Int = 10,
    val maxTextChars: Int = 200_000,
    val maxImageBytes: Long = 20 * MIB,
    val maxAudioBytes: Long = 20 * MIB,
    val maxVideoBytes: Long = 100 * MIB,
    val maxPdfBytes: Long = 10 * MIB,
    val maxTextFileBytes: Long = 5 * MIB,
    val maxArchiveBytes: Long = 50 * MIB,
    val maxOtherBytes: Long = 20 * MIB,
) {
    init {
        require(maxItems > 0 && maxTextChars > 0) { "Limits must be positive" }
    }

    /** Byte limit for a stream of [kind]. Text items are bounded by [maxTextChars] instead. */
    public fun maxBytesFor(kind: ItemKind): Long = when (kind) {
        ItemKind.TEXT -> maxTextChars.toLong() * MAX_UTF8_BYTES_PER_CHAR
        ItemKind.IMAGE -> maxImageBytes
        ItemKind.AUDIO -> maxAudioBytes
        ItemKind.VIDEO -> maxVideoBytes
        ItemKind.PDF -> maxPdfBytes
        ItemKind.TEXT_FILE -> maxTextFileBytes
        ItemKind.ARCHIVE -> maxArchiveBytes
        ItemKind.OTHER -> maxOtherBytes
    }

    private companion object {
        const val MAX_UTF8_BYTES_PER_CHAR: Long = 3
    }
}
