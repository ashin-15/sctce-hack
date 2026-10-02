package org.sakshi.app

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class ShareTargetTest {
    private val uri = Uri.parse("content://synthetic.authority/item")

    private fun start(intent: Intent): Pair<ShareTargetActivity, Intent?> {
        val controller = Robolectric.buildActivity(ShareTargetActivity::class.java, intent).create()
        return controller.get() to shadowOf(controller.get()).nextStartedActivity
    }

    @Test
    fun aSendIsForwardedToMainActivityWithEverythingNeededForTheGrant() {
        val source = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_TEXT, "synthetic text")
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri("synthetic", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
        val (activity, forwarded) = start(source)

        assertNotNull(forwarded)
        assertEquals(Intent.ACTION_SEND, forwarded.action)
        assertEquals("image/png", forwarded.type)
        assertEquals("synthetic text", forwarded.getStringExtra(Intent.EXTRA_TEXT))
        assertEquals(uri, forwarded.extras?.getParcelable(Intent.EXTRA_STREAM, Uri::class.java))
        assertEquals(uri, forwarded.clipData?.getItemAt(0)?.uri)
        assertTrue(forwarded.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertEquals(0, forwarded.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        assertEquals(MainActivity::class.java.name, forwarded.component?.className)
        assertNotNull(forwarded.getStringExtra(EXTRA_SHARE_ID))
        assertTrue(activity.isFinishing)
    }

    @Test
    fun everyForwardedShareGetsItsOwnId() {
        val source = Intent(Intent.ACTION_SEND_MULTIPLE).apply { type = "image/*" }
        val first = start(source).second?.getStringExtra(EXTRA_SHARE_ID)
        val second = start(source).second?.getStringExtra(EXTRA_SHARE_ID)
        assertNotNull(first)
        assertNotEquals(first, second)
    }

    @Test
    fun anIdSuppliedByTheSenderIsReplaced() {
        val source = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(EXTRA_SHARE_ID, "synthetic-sender-id")
        }
        assertNotEquals("synthetic-sender-id", start(source).second?.getStringExtra(EXTRA_SHARE_ID))
    }

    @Test
    fun aSendMultipleKeepsTheStreamList() {
        val source = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "image/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, arrayListOf(uri))
        }
        val forwarded = assertNotNull(start(source).second)
        assertEquals(Intent.ACTION_SEND_MULTIPLE, forwarded.action)
        val streams: List<Uri>? = forwarded.extras?.getParcelableArrayList(Intent.EXTRA_STREAM, Uri::class.java)
        assertEquals(listOf(uri), streams)
    }

    @Test
    fun anUnrelatedActionIsIgnoredAndTheActivityFinishes() {
        val (activity, forwarded) = start(Intent(Intent.ACTION_VIEW, uri))
        assertNull(forwarded)
        assertTrue(activity.isFinishing)
    }

    @Test
    fun anIntentWithoutAnActionIsIgnored() {
        val (activity, forwarded) = start(Intent())
        assertNull(forwarded)
        assertTrue(activity.isFinishing)
    }

    @Test
    fun forwardingIntentRefusesANullSourceAndNonShareActions() {
        assertNull(forwardingIntent(ApplicationProvider.getApplicationContext(), null))
        assertNull(forwardingIntent(ApplicationProvider.getApplicationContext(), Intent(Intent.ACTION_MAIN)))
    }
}
