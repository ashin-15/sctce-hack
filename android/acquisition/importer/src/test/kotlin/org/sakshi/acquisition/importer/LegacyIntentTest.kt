package org.sakshi.acquisition.importer

import android.content.ClipData
import android.content.Intent
import android.os.Parcelable
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import org.junit.Test
import org.robolectric.annotation.Config

/** Runs the reader on API levels either side of the typed `Intent` extras API (33). */
@Config(sdk = [26, 29, 32, 33, 36])
class LegacyIntentTest : ImporterTestBase() {
    private fun read(intent: Intent): PendingBatch = assertNotNull(IntentReader.read(intent, resolver, null))

    @Test
    fun uriOnlyInExtraWithoutClipDataSend() {
        val uri = serve("l1", ByteArray(1), Entry(mime = "image/png"))
        val batch = read(Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uri as Parcelable))
        assertEquals(uri, assertIs<PendingItem.Stream>(batch.items.single()).uri)
    }

    @Test
    fun urisOnlyInExtraWithoutClipDataSendMultiple() {
        val uris = arrayListOf(serve("l2", ByteArray(1)), serve("l3", ByteArray(1)))
        val batch = read(Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris))
        assertEquals(uris, batch.items.map { assertIs<PendingItem.Stream>(it).uri })
    }

    @Test
    fun wrongTypedExtraIsRejected() {
        val single = read(Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, "content://synthetic.test/x"))
        assertEquals(Rejection.NOT_A_URI, assertIs<PendingItem.Rejected>(single.items.single()).reason)
        val list = read(
            Intent(Intent.ACTION_SEND_MULTIPLE).putStringArrayListExtra(Intent.EXTRA_STREAM, arrayListOf("a")),
        )
        assertEquals(Rejection.NOT_A_URI, assertIs<PendingItem.Rejected>(list.items.single()).reason)
    }

    @Test
    fun extraAndClipDataCollapse() {
        val uri = serve("l4", ByteArray(1))
        val intent = Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uri as Parcelable)
        intent.clipData = ClipData.newRawUri("synthetic", uri)
        assertEquals(1, read(intent).items.size)
    }

    @Test
    fun absentExtraGivesNoItems() {
        assertEquals(0, read(Intent(Intent.ACTION_SEND)).items.size)
    }
}
