package org.sakshi.export.report

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import org.sakshi.core.integrity.Sha256
import org.sakshi.export.bundle.ManifestSigner

/**
 * [ManifestSigner] backed by a non-exportable EC P-256 key in the Android Keystore, created on first use.
 * StrongBox is tried first and the default (TEE) key is the fallback. The key can only sign and needs no
 * per-use user authentication, so it is usable while the app is unlocked (megaplan 21.6).
 *
 * Unverified on the JVM: the Keystore does not exist under Robolectric, so this class is covered by the device
 * test only.
 */
public class KeystoreManifestSigner(private val alias: String = DEFAULT_ALIAS) : ManifestSigner {

    override val publicKeySpkiDer: ByteArray
        get() = synchronized(LOCK) {
            ensureKey()
            requireNotNull(keyStore().getCertificate(alias)) { "Signing key has no certificate" }.publicKey.encoded
        }

    override fun sign(manifestBytes: ByteArray): ByteArray = synchronized(LOCK) {
        ensureKey()
        val key = keyStore().getKey(alias, null) as PrivateKey
        Signature.getInstance(SIGNATURE).run {
            initSign(key)
            update(manifestBytes)
            sign()
        }
    }

    /** True when the Keystore holds the signing key. */
    public fun exists(): Boolean = synchronized(LOCK) { keyStore().containsAlias(alias) }

    /** Deletes the signing key. Earlier bundles still verify; no new bundle can be signed with this identity. */
    public fun delete() {
        synchronized(LOCK) { keyStore().deleteEntry(alias) }
    }

    /** Hex SHA-256 of the DER SubjectPublicKeyInfo, as the bundle format defines the key id. */
    public fun keyId(): String = Sha256.hex(Sha256.digest(publicKeySpkiDer))

    private fun ensureKey() {
        if (keyStore().containsAlias(alias)) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                generate(strongBox = true)
                return
            } catch (_: StrongBoxUnavailableException) {
                // No StrongBox on this device: use the default hardware-backed key below.
            }
        }
        generate(strongBox = false)
    }

    private fun generate(strongBox: Boolean) {
        val builder = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN)
            .setAlgorithmParameterSpec(ECGenParameterSpec(CURVE))
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setUserAuthenticationRequired(false)
        if (strongBox && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) builder.setIsStrongBoxBacked(true)
        val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, PROVIDER)
        generator.initialize(builder.build())
        generator.generateKeyPair()
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }

    public companion object {
        public const val DEFAULT_ALIAS: String = "sakshi.export.v1"

        private const val PROVIDER = "AndroidKeyStore"
        private const val CURVE = "secp256r1"
        private const val SIGNATURE = "SHA256withECDSA"
        private val LOCK = Any()
    }
}
