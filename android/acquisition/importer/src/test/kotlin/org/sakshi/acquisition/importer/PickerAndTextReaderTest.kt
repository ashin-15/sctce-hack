package org.sakshi.acquisition.importer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull

class PickerAndTextReaderTest : ImporterTestBase() {
    @Test
    fun pickerBuildsItemsWithClaims() {
        val uri = serve("p1", ByteArray(2), Entry(mime = "image/jpeg", name = "synthetic.jpg", size = 2))
        val batch = PickerReader.fromPickedUris(listOf(uri, contentUri("p2")), ImportMechanism.PHOTO_PICKER, resolver)
        assertEquals(ImportMechanism.PHOTO_PICKER, batch.mechanism)
        assertNull(batch.referrerClaim)
        assertEquals(ItemKind.IMAGE, assertIs<PendingItem.Stream>(batch.items[0]).kind)
        assertEquals(ItemKind.OTHER, assertIs<PendingItem.Stream>(batch.items[1]).kind)
    }

    @Test
    fun pickerDropsRepeatedUrisRejectsFilesAndCaps() {
        val uri = contentUri("p3")
        val file = android.net.Uri.parse("file:///data/synthetic.bin")
        val batch = PickerReader.fromPickedUris(listOf(uri, uri, file), ImportMechanism.DOCUMENT_PICKER, resolver)
        assertEquals(2, batch.items.size)
        assertEquals(Rejection.UNSUPPORTED_SCHEME, assertIs<PendingItem.Rejected>(batch.items[1]).reason)

        val many = (1..12).map { contentUri("m$it") }
        val capped = PickerReader.fromPickedUris(many, ImportMechanism.DOCUMENT_PICKER, resolver)
        assertEquals(Rejection.TOO_MANY_ITEMS, assertIs<PendingItem.Rejected>(capped.items.last()).reason)
        assertEquals(11, capped.items.size)
    }

    @Test
    fun pickerRejectsNonPickerMechanism() {
        assertFailsWith<IllegalArgumentException> {
            PickerReader.fromPickedUris(emptyList(), ImportMechanism.PASTE, resolver)
        }
    }

    @Test
    fun pastedTextIsKeptExactlyOrRejected() {
        val text = "  synthetic pasted\n text  "
        assertEquals(text, assertIs<PendingItem.Text>(TextReader.fromPastedText(text).items.single()).text)
        assertEquals(ImportMechanism.PASTE, TextReader.fromPastedText(text).mechanism)
        assertEquals(Rejection.EMPTY_TEXT, assertIs<PendingItem.Rejected>(TextReader.fromPastedText("  ").items.single()).reason)
        val long = TextReader.fromPastedText("a".repeat(200_001))
        assertEquals(Rejection.TEXT_TOO_LONG, assertIs<PendingItem.Rejected>(long.items.single()).reason)
    }

    @Test
    fun limitsMapKindsToBytes() {
        val limits = ImportLimits()
        val mib = 1024L * 1024L
        assertEquals(20 * mib, limits.maxBytesFor(ItemKind.IMAGE))
        assertEquals(20 * mib, limits.maxBytesFor(ItemKind.AUDIO))
        assertEquals(100 * mib, limits.maxBytesFor(ItemKind.VIDEO))
        assertEquals(10 * mib, limits.maxBytesFor(ItemKind.PDF))
        assertEquals(5 * mib, limits.maxBytesFor(ItemKind.TEXT_FILE))
        assertEquals(50 * mib, limits.maxBytesFor(ItemKind.ARCHIVE))
        assertEquals(20 * mib, limits.maxBytesFor(ItemKind.OTHER))
        assertFailsWith<IllegalArgumentException> { ImportLimits(maxItems = 0) }
    }
}
