package org.sakshi.app

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Bundle
import java.util.UUID

/**
 * The only exported entry for shared content. It has no screen and never touches the vault: it copies the share
 * into an explicit intent for [MainActivity] and finishes.
 */
class ShareTargetActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        forwardingIntent(this, intent)?.let {
            try {
                startActivity(it)
            } catch (_: ActivityNotFoundException) {
                // Nothing to show; the share is dropped.
            }
        }
        finish()
    }
}

/** Extra added by the share target: a random id so that [MainActivity] handles each forwarded share once. */
internal const val EXTRA_SHARE_ID = "org.sakshi.app.extra.SHARE_ID"

/**
 * Copies action, type, extras, clip data and the read grant flag of a share into an intent for [MainActivity].
 * Returns null for anything that is not `ACTION_SEND` or `ACTION_SEND_MULTIPLE`, or whose extras cannot be read.
 */
internal fun forwardingIntent(context: Context, source: Intent?): Intent? {
    val action = source?.action
    if (action != Intent.ACTION_SEND && action != Intent.ACTION_SEND_MULTIPLE) return null
    return try {
        Intent(action).setClass(context, MainActivity::class.java).apply {
            type = source.type
            source.extras?.let { putExtras(it) }
            clipData = source.clipData
            // A new task reuses the existing Sakshi task instead of stacking a second MainActivity on the sender's task.
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or (source.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION)
            putExtra(EXTRA_SHARE_ID, UUID.randomUUID().toString())
        }
    } catch (_: RuntimeException) {
        // A malformed parcel from the sending app.
        null
    }
}
