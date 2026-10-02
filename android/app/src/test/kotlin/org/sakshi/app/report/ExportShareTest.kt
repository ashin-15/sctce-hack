package org.sakshi.app.report

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.sakshi.app.R
import org.sakshi.app.ShareTargetActivity
import org.xmlpull.v1.XmlPullParser

@RunWith(RobolectricTestRunner::class)
class ExportShareTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val snapshotId = "synthetic-snapshot-1"
    private lateinit var file: File

    @BeforeTest
    fun createFile() {
        forgetProviderRoots()
        file = File(context.cacheDir, "exports/$snapshotId.zip")
        file.parentFile!!.mkdirs()
        file.writeBytes(ByteArray(10) { it.toByte() })
    }

    /**
     * FileProvider keeps the folders it resolved in a static map. Robolectric gives every test its own cache folder but
     * reuses the loaded classes, so the map must be emptied or it points at the folder of an earlier test.
     */
    private fun forgetProviderRoots() {
        val field = FileProvider::class.java.getDeclaredField("sCache")
        field.isAccessible = true
        (field.get(null) as MutableMap<*, *>).clear()
    }

    @AfterTest
    fun deleteFile() {
        File(context.cacheDir, "exports").deleteRecursively()
    }

    @Test
    fun theSendIntentCarriesOnlyAReadGrantForTheZip() {
        val intent = ExportShare.sendIntent(context, file)
        assertEquals(Intent.ACTION_SEND, intent.action)
        assertEquals("application/zip", intent.type)
        val uri = intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        assertNotNull(uri)
        assertEquals("content", uri.scheme)
        assertEquals("${context.packageName}.exports", uri.authority)
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertEquals(0, intent.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        assertFalse(intent.hasExtra(Intent.EXTRA_SUBJECT))
        assertFalse(intent.hasExtra(Intent.EXTRA_TEXT))
        assertFalse(intent.hasExtra(Intent.EXTRA_TITLE))
        assertEquals(setOf(Intent.EXTRA_STREAM), intent.extras!!.keySet())
    }

    @Test
    fun theFileIsNamedByTheSnapshotIdAlone() {
        val uri = ExportShare.uriFor(context, file)
        assertEquals("$snapshotId.zip", uri.lastPathSegment)
        context.contentResolver.query(uri, null, null, null, null)!!.use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("$snapshotId.zip", cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)))
            assertEquals(10L, cursor.getLong(cursor.getColumnIndexOrThrow(OpenableColumns.SIZE)))
        }
    }

    @Test
    fun theShareSheetWrapsTheSendIntentWithAGrantAndHidesSakshiItself() {
        val chooser = ExportShare.chooser(context, file, "Share export file")
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        assertEquals("Share export file", chooser.getCharSequenceExtra(Intent.EXTRA_TITLE))
        assertTrue(chooser.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        val inner = chooser.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
        assertNotNull(inner)
        assertEquals(Intent.ACTION_SEND, inner.action)
        assertNull(chooser.getStringExtra(Intent.EXTRA_SUBJECT))
        assertNull(chooser.getCharSequenceExtra(Intent.EXTRA_TEXT))
        val excluded = chooser.getParcelableArrayExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, ComponentName::class.java)
        assertEquals(listOf(ComponentName(context, ShareTargetActivity::class.java)), excluded!!.toList())
    }

    @Test
    fun aFileOutsideTheExportFolderCannotBeShared() {
        val outside = File(context.cacheDir, "synthetic-outside.zip").apply { writeBytes(ByteArray(1)) }
        val refused = runCatching { ExportShare.uriFor(context, outside) }.exceptionOrNull()
        assertTrue(refused is IllegalArgumentException)
        val other = File(context.filesDir, "synthetic-private.zip").apply { writeBytes(ByteArray(1)) }
        assertTrue(runCatching { ExportShare.uriFor(context, other) }.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun theProviderPathsExposeOnlyTheExportFolderOfTheCache() {
        val parser = context.resources.getXml(R.xml.export_paths)
        val elements = mutableListOf<Triple<String, String?, String?>>()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                elements += Triple(parser.name, parser.getAttributeValue(null, "name"), parser.getAttributeValue(null, "path"))
            }
            event = parser.next()
        }
        assertEquals(listOf(Triple("paths", null, null), Triple("cache-path", "exports", "exports/")), elements)
    }

    @Test
    fun theProviderIsNotExportedAndOnlyAllowedComponentsAre() {
        val flags = PackageManager.GET_PROVIDERS or PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES or PackageManager.GET_RECEIVERS
        val info = context.packageManager.getPackageInfo(context.packageName, flags)
        val provider = info.providers!!.single { it.authority == "${context.packageName}.exports" }
        assertEquals("androidx.core.content.FileProvider", provider.name)
        assertFalse(provider.exported)
        assertTrue(provider.grantUriPermissions)
        val exported = (info.providers.orEmpty().toList() + info.activities.orEmpty() + info.services.orEmpty() + info.receivers.orEmpty())
            .filter { it.exported }.map { it.name }.toSet()
        val allowed = setOf("org.sakshi.app.MainActivity", "org.sakshi.app.ShareTargetActivity", "androidx.profileinstaller.ProfileInstallReceiver")
        assertTrue(allowed.containsAll(exported), "Unexpected exported components: ${exported - allowed}")
    }
}
