package org.sakshi.core.vault

import android.os.Build
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertContains
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KeystoreKeyWrapperDeviceTest : DeviceTestBase() {
    private fun secret(): ByteArray = ByteArray(32).also(SecureRandom()::nextBytes)

    @Test
    fun wrapAndUnwrapRoundTripAndDetectTampering() {
        val wrapper = newWrapper()
        val secret = secret()

        val first = wrapper.wrap(secret)
        val second = wrapper.wrap(secret)

        assertContentEquals(secret, wrapper.unwrap(first))
        assertContentEquals(secret, wrapper.unwrap(second))
        assertFalse(first.contentEquals(second), "Two wraps of the same secret must differ")
        val tampered = first.copyOf().also { it[it.size / 2] = (it[it.size / 2].toInt() xor 0x01).toByte() }
        assertFailsWith<GeneralSecurityException> { wrapper.unwrap(tampered) }
        record("keystore_round_trip", "wrapped_size" to first.size)
    }

    @Test
    fun existsReflectsLifecycleOfTheKey() {
        val wrapper = newWrapper()

        assertFalse(wrapper.exists(), "No key before first use")
        wrapper.wrap(secret())
        assertTrue(wrapper.exists(), "Key exists after first use")
        wrapper.delete()
        assertFalse(wrapper.exists(), "Key is gone after delete")
    }

    @Test
    fun keyIsHardwareBackedAndNotExportable() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        val alias = "synthetic-vault-test-keyinfo-${UUID.randomUUID()}"
        val wrapper = KeystoreKeyWrapper(alias = alias, requireUserAuthentication = false)
        try {
            wrapper.wrap(secret())

            val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            val key = store.getKey(alias, null) as SecretKey
            assertTrue(key.encoded == null, "The key material must not be exportable")
            val info = SecretKeyFactory.getInstance(key.algorithm, "AndroidKeyStore")
                .getKeySpec(key, KeyInfo::class.java) as KeyInfo
            val level = when (info.securityLevel) {
                KeyProperties.SECURITY_LEVEL_STRONGBOX -> "StrongBox"
                KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT -> "TEE"
                KeyProperties.SECURITY_LEVEL_SOFTWARE -> "Software"
                else -> "Unknown-${info.securityLevel}"
            }
            record("keystore_security_level", "level" to level, "key_size_bits" to info.keySize)
            assertTrue(level == "StrongBox" || level == "TEE", "Key must not be software-only, got $level")
        } finally {
            wrapper.delete()
        }
    }

    @Test
    fun wrapperWithDifferentAliasCannotUnwrap() {
        val owner = newWrapper()
        val stranger = newWrapper()

        val wrapped = owner.wrap(secret())

        assertFailsWith<GeneralSecurityException> { stranger.unwrap(wrapped) }
    }

    @Test
    fun authenticationBoundKeyEitherWorksOrReportsNotAuthenticated() {
        val wrapper = newWrapper(requireUserAuthentication = true)
        val secret = secret()

        val outcome = try {
            val wrapped = wrapper.wrap(secret)
            assertContentEquals(secret, wrapper.unwrap(wrapped))
            "succeeded"
        } catch (e: VaultKeyException.NotAuthenticated) {
            assertContains(e.message.orEmpty(), "authentication")
            "not_authenticated"
        }

        record("keystore_auth_bound", "outcome" to outcome)
    }
}
