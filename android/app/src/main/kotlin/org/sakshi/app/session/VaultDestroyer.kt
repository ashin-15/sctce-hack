package org.sakshi.app.session

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.sakshi.core.vault.KeystoreKeyWrapper
import org.sakshi.core.vault.Vault
import org.sakshi.core.vault.VaultDestroyResult

/** Deletes everything Sakshi saved, while no vault is open. Safe to call again after an incomplete result. */
fun interface VaultDestroyer {
    suspend fun destroy(): VaultDestroyResult
}

/** Destroys the on-device vault and the Keystore key behind it, off the main thread. */
class KeystoreVaultDestroyer(context: Context) : VaultDestroyer {
    private val appContext = context.applicationContext

    override suspend fun destroy(): VaultDestroyResult = withContext(Dispatchers.IO) {
        Vault.destroy(appContext, KeystoreKeyWrapper(requireUserAuthentication = true))
    }
}

/**
 * Remembers that a deletion was started and has not been finished, so that an app killed half way offers to finish it
 * on the next launch instead of trying to open data whose key is already gone. Holds no evidence or case data.
 */
interface DeletionMarker {
    fun isPending(): Boolean

    fun setPending()

    fun clear()
}

class SharedPreferencesDeletionMarker(private val preferences: SharedPreferences) : DeletionMarker {
    override fun isPending(): Boolean = preferences.getBoolean(KEY_PENDING, false)

    override fun setPending() {
        preferences.edit(commit = true) { putBoolean(KEY_PENDING, true) }
    }

    override fun clear() {
        preferences.edit(commit = true) { remove(KEY_PENDING) }
    }

    companion object {
        private const val FILE = "deletion"
        private const val KEY_PENDING = "pending"

        fun create(context: Context): SharedPreferencesDeletionMarker =
            SharedPreferencesDeletionMarker(context.getSharedPreferences(FILE, Context.MODE_PRIVATE))
    }
}
