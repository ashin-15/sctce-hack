package org.sakshi.app.session

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.sakshi.core.vault.KeystoreKeyWrapper
import org.sakshi.core.vault.Vault

/** Opens the vault after the user has authenticated. */
interface VaultOpener {
    suspend fun open(): Vault
}

/** Opens the on-device vault with the Keystore key that requires user authentication. */
class KeystoreVaultOpener(context: Context) : VaultOpener {
    private val appContext = context.applicationContext

    override suspend fun open(): Vault = withContext(Dispatchers.IO) {
        val vault = Vault.open(appContext, KeystoreKeyWrapper(requireUserAuthentication = true))
        try {
            vault.startUp()
        } catch (e: Exception) {
            vault.close()
            throw e
        }
        vault
    }
}
