package org.sakshi.acquisition.importer

import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat

/**
 * Reads `Uri` extras on every API level through [IntentCompat]. A value of another type reads as null, so
 * callers use [isPresent] to tell "absent" from "present but not a Uri".
 */
internal object ParcelableExtras {
    fun uri(intent: Intent, name: String): Uri? = IntentCompat.getParcelableExtra(intent, name, Uri::class.java)

    /** Elements are returned as `Any?` because a forged list may hold anything. */
    fun uriList(intent: Intent, name: String): List<Any?>? =
        IntentCompat.getParcelableArrayListExtra(intent, name, Uri::class.java)

    fun isPresent(intent: Intent, name: String): Boolean = intent.extras?.containsKey(name) == true
}
