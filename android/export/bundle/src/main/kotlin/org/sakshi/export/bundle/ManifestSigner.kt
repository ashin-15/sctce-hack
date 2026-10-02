package org.sakshi.export.bundle

import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec

/** Signs the exact manifest bytes. On a device this is backed by the Android Keystore. */
public interface ManifestSigner {
    /** DER encoded SubjectPublicKeyInfo of the signing key. */
    public val publicKeySpkiDer: ByteArray

    /** Returns the raw DER encoded ECDSA (P-256, SHA-256) signature over [manifestBytes]. */
    public fun sign(manifestBytes: ByteArray): ByteArray
}

/** In-process P-256 signer for tests and tools. It offers no hardware protection. */
public class SoftwareP256Signer(private val keyPair: KeyPair) : ManifestSigner {
    init {
        require(keyPair.public is ECPublicKey && keyPair.private is ECPrivateKey) { "Key pair must be an EC key pair" }
    }

    override val publicKeySpkiDer: ByteArray
        get() = keyPair.public.encoded

    override fun sign(manifestBytes: ByteArray): ByteArray =
        Signature.getInstance(BundleFormat.JCA_SIGNATURE).run {
            initSign(keyPair.private)
            update(manifestBytes)
            sign()
        }

    public companion object {
        /** Generates a fresh P-256 key pair. */
        public fun generate(): SoftwareP256Signer {
            val generator = KeyPairGenerator.getInstance("EC")
            generator.initialize(ECGenParameterSpec("secp256r1"))
            return SoftwareP256Signer(generator.generateKeyPair())
        }
    }
}
