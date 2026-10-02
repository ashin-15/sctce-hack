package org.sakshi.app.importing

import android.content.ContentResolver
import android.content.Intent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.sakshi.acquisition.importer.IntentReader

/**
 * Parses a forwarded share off the main thread and hands the result to the [ImportCoordinator].
 * Reading the claims queries the sending app's provider, which may be slow. No bytes are read here.
 */
class ShareIntake(
    private val coordinator: ImportCoordinator,
    private val resolver: ContentResolver,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
) {
    fun accept(intent: Intent) {
        scope.launch {
            // The forwarding activity is this app, so the platform referrer would say nothing about the sender.
            val batch = withContext(io) { IntentReader.read(intent, resolver, referrer = null) }
            if (batch != null) coordinator.offer(batch)
        }
    }
}
