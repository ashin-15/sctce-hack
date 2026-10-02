package org.sakshi.app.report

import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import org.sakshi.app.ShareTargetActivity

/**
 * Builds the intent that hands the finished export file to another app. The file is served by a [FileProvider] that
 * is not exported and covers only the export folder of the cache. The intent carries no subject and no text, and the
 * file name is the snapshot id, so nothing from the case reaches the receiving app except the file's bytes.
 */
object ExportShare {
    const val MIME_TYPE: String = "application/zip"
    private const val AUTHORITY_SUFFIX = ".exports"

    fun authority(context: Context): String = context.packageName + AUTHORITY_SUFFIX

    fun uriFor(context: Context, file: File): Uri = FileProvider.getUriForFile(context, authority(context), file)

    /** The send intent itself: a read grant for one URI and nothing else. */
    fun sendIntent(context: Context, file: File): Intent {
        val uri = uriFor(context, file)
        return Intent(Intent.ACTION_SEND).apply {
            type = MIME_TYPE
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri("", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /** The system share sheet for the file. Sakshi's own import target is left out of the list. */
    fun chooser(context: Context, file: File, title: CharSequence): Intent =
        Intent.createChooser(sendIntent(context, file), title).apply {
            putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, arrayOf(ComponentName(context, ShareTargetActivity::class.java)))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
}
