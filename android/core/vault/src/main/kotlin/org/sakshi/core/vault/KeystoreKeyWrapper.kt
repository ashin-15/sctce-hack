package org.sakshi.core.vault

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import android.security.keystore.UserNotAuthenticatedException
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.KeyStoreException
import java.security.ProviderException
import java.security.UnrecoverableKeyException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.sakshi.core.crypto.KeyWrapper

/**
 * [KeyWrapper] backed by a non-exportable AES-256-GCM key in the Android Keystore, created on first use.
 * StrongBox is tried first and the default (TEE) key is the fallback.
 *
 * Format: version byte 0x01 || 12-byte IV chosen by the Keystore || ciphertext and tag. The version byte is
 * authenticated as additional data.
 *
 * Unverified: the Keystore does not exist under Robolectric, so this class has no JVM test. It stays unverified
 * until an instrumented test has run it on a device.
 */
public class KeystoreKeyWrapper(
    private val alias: String = DEFAULT_ALIAS,
    private val requireUserAuthentication: Boolean,
    private val authenticationValiditySeconds: Int = DEFAULT_VALIDITY_SECONDS,
) : KeyWrapper {

    override fun wrap(secret: ByteArray): ByteArray = translating {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        cipher.updateAAD(byteArrayOf(VERSION))
        byteArrayOf(VERSION) + cipher.iv + cipher.doFinal(secret)
    }

    override fun unwrap(wrapped: ByteArray): ByteArray = translating {
        if (wrapped.size < 1 + IV_SIZE + TAG_SIZE || wrapped[0] != VERSION) {
            throw GeneralSecurityException("Unsupported wrapped value")
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_SIZE * 8, wrapped, 1, IV_SIZE))
        cipher.updateAAD(byteArrayOf(VERSION))
        cipher.doFinal(wrapped, 1 + IV_SIZE, wrapped.size - 1 - IV_SIZE)
    }

    /** True when the Keystore holds the master key. */
    public fun exists(): Boolean = translating { keyStore().containsAlias(alias) }

    /** Deletes the master key. Everything wrapped by it becomes permanently unreadable. */
    public fun delete() {
        translating { keyStore().deleteEntry(alias) }
    }

    private fun key(): SecretKey {
        val store = keyStore()
        return store.getKey(alias, null) as SecretKey? ?: generate()
    }

    private fun generate(): SecretKey {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                return generate(strongBox = true)
            } catch (_: StrongBoxUnavailableException) {
                // No StrongBox on this device: use the default hardware-backed key below.
            }
        }
        return generate(strongBox = false)
    }

    private fun generate(strongBox: Boolean): SecretKey {
        val builder = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(KEY_BITS)
            .setRandomizedEncryptionRequired(true)
        if (requireUserAuthentication) {
            builder.setUserAuthenticationRequired(true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                builder.setUserAuthenticationParameters(
                    authenticationValiditySeconds,
                    KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL,
                )
            } else {
                setLegacyValidity(builder)
            }
        }
        if (strongBox && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) builder.setIsStrongBoxBacked(true)
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        generator.init(builder.build())
        return generator.generateKey()
    }

    /**
     * API 26-29 only. The method is deprecated from API 30, where [setUserAuthenticationParameters][
     * KeyGenParameterSpec.Builder.setUserAuthenticationParameters] replaces it, so it is called by reflection to
     * keep the build free of deprecation warnings.
     */
    private fun setLegacyValidity(builder: KeyGenParameterSpec.Builder) {
        try {
            KeyGenParameterSpec.Builder::class.java
                .getMethod(LEGACY_VALIDITY_METHOD, Int::class.javaPrimitiveType)
                .invoke(builder, authenticationValiditySeconds)
        } catch (e: ReflectiveOperationException) {
            throw VaultKeyException.Unavailable(e)
        }
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }

    private inline fun <T> translating(block: () -> T): T = try {
        block()
    } catch (e: UserNotAuthenticatedException) {
        throw VaultKeyException.NotAuthenticated(e)
    } catch (e: KeyPermanentlyInvalidatedException) {
        throw VaultKeyException.Invalidated(e)
    } catch (e: VaultKeyException) {
        throw e
    } catch (e: ProviderException) {
        throw VaultKeyException.Unavailable(e)
    } catch (e: KeyStoreException) {
        throw VaultKeyException.Unavailable(e)
    } catch (e: UnrecoverableKeyException) {
        throw VaultKeyException.Unavailable(e)
    }

    public companion object {
        public const val DEFAULT_ALIAS: String = "sakshi.master.v1"
        public const val DEFAULT_VALIDITY_SECONDS: Int = 300

        private const val LEGACY_VALIDITY_METHOD = "setUserAuthenticationValidityDurationSeconds"
        private const val PROVIDER = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val VERSION: Byte = 1
        private const val IV_SIZE = 12
        private const val TAG_SIZE = 16
        private const val KEY_BITS = 256
    }
}
