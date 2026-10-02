package org.sakshi.acquisition.importer

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Parcelable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class IntentReaderTest : ImporterTestBase() {
    private fun read(intent: Intent): PendingBatch =
        assertNotNull(IntentReader.read(intent, resolver, "synthetic.referrer"))

    private fun send(vararg extras: Pair<String, Parcelable>, type: String? = null): Intent =
        Intent(Intent.ACTION_SEND).apply {
            type?.let { this.type = it }
            extras.forEach { (key, value) -> putExtra(key, value) }
        }

    @Test
    fun sendWithStreamReadsClaims() {
        val uri = serve("a", ByteArray(3), Entry(mime = "image/png", name = "synthetic-a.png", size = 3))
        val batch = read(send(Intent.EXTRA_STREAM to uri))

        assertEquals(ImportMechanism.SHARE_SEND, batch.mechanism)
        assertEquals("synthetic.referrer", batch.referrerClaim)
        val item = assertIs<PendingItem.Stream>(batch.items.single())
        assertEquals(uri, item.uri)
        assertEquals("image/png", item.declaredMime)
        assertEquals("synthetic-a.png", item.displayNameClaim)
        assertEquals(3L, item.sizeClaim)
        assertEquals(ItemKind.IMAGE, item.kind)
        assertEquals(AUTHORITY, item.uriAuthorityClaim)
    }

    @Test
    fun sendWithTextOnly() {
        val batch = read(Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_TEXT, "synthetic text https://example.invalid/x"))
        val item = assertIs<PendingItem.Text>(batch.items.single())
        assertEquals("synthetic text https://example.invalid/x", item.text)
        assertEquals(ItemKind.TEXT, item.kind)
    }

    @Test
    fun sendWithStreamAndTextKeepsBoth() {
        val uri = serve("b", ByteArray(1))
        val intent = send(Intent.EXTRA_STREAM to uri).putExtra(Intent.EXTRA_TEXT, "synthetic caption")
        val batch = read(intent)
        assertEquals(2, batch.items.size)
        assertIs<PendingItem.Stream>(batch.items[0])
        assertEquals("synthetic caption", assertIs<PendingItem.Text>(batch.items[1]).text)
        assertEquals(listOf(0, 1), batch.items.map { it.index })
    }

    @Test
    fun sendMultipleWithList() {
        val uris = arrayListOf(serve("c1", ByteArray(1)), serve("c2", ByteArray(1)), serve("c3", ByteArray(1)))
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        val batch = read(intent)
        assertEquals(ImportMechanism.SHARE_SEND_MULTIPLE, batch.mechanism)
        assertEquals(uris, batch.items.map { assertIs<PendingItem.Stream>(it).uri })
    }

    @Test
    fun clipDataOnly() {
        val first = serve("d1", ByteArray(1))
        val second = serve("d2", ByteArray(1))
        val clip = ClipData.newRawUri("synthetic", first).apply { addItem(ClipData.Item(second)) }
        val batch = read(Intent(Intent.ACTION_SEND_MULTIPLE).apply { clipData = clip })
        assertEquals(listOf(first, second), batch.items.map { assertIs<PendingItem.Stream>(it).uri })
    }

    @Test
    fun sameUriInExtrasAndClipDataCollapses() {
        val uri = serve("e", ByteArray(1))
        val intent = send(Intent.EXTRA_STREAM to uri).apply { clipData = ClipData.newRawUri("synthetic", uri) }
        assertEquals(1, read(intent).items.size)
    }

    @Test
    fun sameTextInExtraAndClipDataCollapses() {
        val intent = Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_TEXT, "synthetic same")
        intent.clipData = ClipData.newPlainText("synthetic", "synthetic same")
        assertEquals(1, read(intent).items.size)
    }

    @Test
    fun fileSchemeIsRejected() {
        val batch = read(send(Intent.EXTRA_STREAM to Uri.parse("file:///data/synthetic.bin")))
        assertEquals(Rejection.UNSUPPORTED_SCHEME, assertIs<PendingItem.Rejected>(batch.items.single()).reason)
    }

    @Test
    fun otherSchemesAreRejected() {
        for (text in listOf("http://example.invalid/x", "android.resource://pkg/1", "CONTENT://synthetic.test/x")) {
            val batch = read(send(Intent.EXTRA_STREAM to Uri.parse(text)))
            assertEquals(Rejection.UNSUPPORTED_SCHEME, assertIs<PendingItem.Rejected>(batch.items.single()).reason, text)
        }
    }

    @Test
    fun stringWhereUriIsExpectedIsRejectedWithoutCrash() {
        val batch = read(Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, "content://synthetic.test/x"))
        assertEquals(Rejection.NOT_A_URI, assertIs<PendingItem.Rejected>(batch.items.single()).reason)
    }

    @Test
    fun wrongTypedListsAreRejectedWithoutCrash() {
        val strings = Intent(Intent.ACTION_SEND_MULTIPLE).putStringArrayListExtra(Intent.EXTRA_STREAM, arrayListOf("a", "b"))
        val rejected = read(strings).items.map { assertIs<PendingItem.Rejected>(it).reason }
        assertEquals(listOf(Rejection.NOT_A_URI, Rejection.NOT_A_URI), rejected)

        val mixed = arrayListOf<Parcelable>(contentUri("f"), Bundle())
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, mixed)
        val items = read(intent).items
        assertEquals(Rejection.NOT_A_URI, items.filterIsInstance<PendingItem.Rejected>().single().reason)
    }

    @Test
    fun wrongTypedTextIsRejectedWithoutCrash() {
        val intent = Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_TEXT, 42)
        assertEquals(Rejection.EMPTY_TEXT, assertIs<PendingItem.Rejected>(read(intent).items.single()).reason)
    }

    @Test
    fun unknownActionGivesNull() {
        assertNull(IntentReader.read(Intent(Intent.ACTION_VIEW).putExtra(Intent.EXTRA_TEXT, "x"), resolver, null))
        assertNull(IntentReader.read(Intent(), resolver, null))
    }

    @Test
    fun itemCapKeepsFirstAndAddsOneRejection() {
        val uris = ArrayList((1..13).map { serve("cap$it", ByteArray(1)) })
        val batch = read(Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris))
        assertEquals(11, batch.items.size)
        assertEquals(uris.take(10), batch.items.take(10).map { assertIs<PendingItem.Stream>(it).uri })
        val last = assertIs<PendingItem.Rejected>(batch.items.last())
        assertEquals(Rejection.TOO_MANY_ITEMS, last.reason)
        assertEquals(10, last.index)
    }

    @Test
    fun emptyAndTooLongTextAreRejected() {
        val empty = read(Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_TEXT, " \n\t "))
        assertEquals(Rejection.EMPTY_TEXT, assertIs<PendingItem.Rejected>(empty.items.single()).reason)
        val long = read(Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_TEXT, "a".repeat(200_001)))
        assertEquals(Rejection.TEXT_TOO_LONG, assertIs<PendingItem.Rejected>(long.items.single()).reason)
        val exact = read(Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_TEXT, "a".repeat(200_000)))
        assertIs<PendingItem.Text>(exact.items.single())
    }

    @Test
    fun displayNameClaimIsCappedAt255() {
        val uri = serve("g", ByteArray(1), Entry(name = "n".repeat(1000)))
        val item = assertIs<PendingItem.Stream>(read(send(Intent.EXTRA_STREAM to uri)).items.single())
        assertEquals(255, item.displayNameClaim?.length)
    }

    @Test
    fun cappingNeverSplitsASurrogatePair() {
        val name = "n".repeat(254) + "😀"
        val uri = serve("g2", ByteArray(1), Entry(name = name))
        val item = assertIs<PendingItem.Stream>(read(send(Intent.EXTRA_STREAM to uri)).items.single())
        assertEquals(254, item.displayNameClaim?.length)
    }

    @Test
    fun kindMapping() {
        val table = mapOf(
            "image/jpeg" to ItemKind.IMAGE,
            "audio/ogg" to ItemKind.AUDIO,
            "video/mp4" to ItemKind.VIDEO,
            "application/pdf" to ItemKind.PDF,
            "application/zip" to ItemKind.ARCHIVE,
            "application/x-7z-compressed" to ItemKind.ARCHIVE,
            "text/plain" to ItemKind.TEXT_FILE,
            "text/csv; charset=utf-8" to ItemKind.TEXT_FILE,
            "IMAGE/PNG" to ItemKind.IMAGE,
            "application/octet-stream" to ItemKind.OTHER,
            "application/vnd.android.package-archive" to ItemKind.OTHER,
        )
        table.entries.forEachIndexed { i, (mime, kind) ->
            val uri = serve("k$i", ByteArray(1), Entry(mime = mime))
            val item = assertIs<PendingItem.Stream>(read(send(Intent.EXTRA_STREAM to uri)).items.single())
            assertEquals(kind, item.kind, mime)
        }
    }

    @Test
    fun missingProviderTypeFallsBackToIntentType() {
        val uri = contentUri("unregistered")
        val item = assertIs<PendingItem.Stream>(read(send(Intent.EXTRA_STREAM to uri, type = "image/webp")).items.single())
        assertEquals("image/webp", item.declaredMime)
        assertEquals(ItemKind.IMAGE, item.kind)
        assertNull(item.displayNameClaim)
        assertNull(item.sizeClaim)
    }

    @Test
    fun wildcardIntentTypeIsNotAClaim() {
        val item = assertIs<PendingItem.Stream>(read(send(Intent.EXTRA_STREAM to contentUri("w"), type = "*/*")).items.single())
        assertNull(item.declaredMime)
        assertEquals(ItemKind.OTHER, item.kind)
    }

    @Test
    fun throwingProviderGivesNullClaims() {
        val uri = serve("h", ByteArray(1), Entry(mime = "image/png", name = "x", size = 1, throwOnMetadata = true))
        val item = assertIs<PendingItem.Stream>(read(send(Intent.EXTRA_STREAM to uri, type = "audio/ogg")).items.single())
        assertNull(item.displayNameClaim)
        assertNull(item.sizeClaim)
        assertEquals("audio/ogg", item.declaredMime)
    }

    @Test
    fun referrerClaimIsCapped() {
        val batch = IntentReader.read(Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_TEXT, "x"), resolver, "r".repeat(999))
        assertEquals(255, batch?.referrerClaim?.length)
    }
}
