package org.sakshi.export.report

import android.os.Build
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.security.KeyFactory
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.runner.RunWith
import org.sakshi.core.integrity.Sha256

@RunWith(AndroidJUnit4::class)
class KeystoreManifestSignerDeviceTest : DeviceTestBase() {
    private val manifest = "synthetic manifest bytes".toByteArray()

    @Test
    fun signatureVerifiesAgainstThePublishedPublicKey() {
        val signer = newSigner()
        assertFalse(signer.exists())
        val spki = signer.publicKeySpkiDer
        assertTrue(signer.exists())
        val signature = signer.sign(manifest)
        val publicKey = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(spki))
        val verifier = Signature.getInstance("SHA256withECDSA")
        verifier.initVerify(publicKey)
        verifier.update(manifest)
        assertTrue(verifier.verify(signature))
        verifier.initVerify(publicKey)
        verifier.update(manifest + 1)
        assertFalse(verifier.verify(signature))
    }

    @Test
    fun theKeyIsHardwareBackedNotExportableAndSignOnly() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        val alias = "synthetic-report-signer-info"
        val signer = newSigner(alias)
        signer.publicKeySpkiDer
        val key = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.getKey(alias, null) as PrivateKey
        assertNull(key.encoded)
        val info = KeyFactory.getInstance(key.algorithm, "AndroidKeyStore").getKeySpec(key, KeyInfo::class.java)
        assertEquals(KeyProperties.PURPOSE_SIGN, info.purposes)
        assertFalse(info.isUserAuthenticationRequired)
        assertTrue(info.securityLevel != KeyProperties.SECURITY_LEVEL_SOFTWARE)
        val level = when (info.securityLevel) {
            KeyProperties.SECURITY_LEVEL_STRONGBOX -> "strongbox"
            KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT -> "tee"
            else -> "other"
        }
        record("keystore_signer", *deviceState(context), "security_level" to level)
    }

    @Test
    fun keyIdIsStableAcrossInstancesAndDeleteRemovesTheKey() {
        val alias = "synthetic-report-signer-stable-${System.nanoTime()}"
        val first = newSigner(alias)
        val id = first.keyId()
        assertEquals(Sha256.hex(Sha256.digest(first.publicKeySpkiDer)), id)
        assertEquals(64, id.length)
        val second = newSigner(alias)
        assertTrue(second.exists())
        assertEquals(id, second.keyId())
        second.delete()
        assertFalse(first.exists())
    }
}
