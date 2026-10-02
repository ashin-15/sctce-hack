package org.sakshi.export.bundle

import java.security.AlgorithmParameters
import java.security.GeneralSecurityException
import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import org.sakshi.core.integrity.Sha256

internal class SignatureOutcome(val check: Check, val keyId: String?)

internal object SignatureVerifier {
    private const val NAME: String = "signature"
    private const val SCOPE: String = "This shows which key signed, not who holds it."

    private val P256: ECParameterSpec = AlgorithmParameters.getInstance("EC").run {
        init(ECGenParameterSpec("secp256r1"))
        getParameterSpec(ECParameterSpec::class.java)
    }

    fun verify(manifestBytes: ByteArray, signature: ByteArray?, signer: SignerFile): SignatureOutcome {
        if (signer.algorithm != BundleFormat.ALGORITHM) return fail("algorithm is not ${BundleFormat.ALGORITHM}", null)
        val spki = try {
            Base64.getDecoder().decode(signer.spkiBase64)
        } catch (e: IllegalArgumentException) {
            return fail("public key is not valid base64", null)
        }
        val computedId = Sha256.hex(Sha256.digest(spki))
        if (signer.keyId != computedId) return fail("key_id does not equal the SHA-256 of the public key; computed $computedId", computedId)
        val key = try {
            KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(spki))
        } catch (e: GeneralSecurityException) {
            return fail("public key cannot be decoded", computedId)
        }
        if (!isP256(key, spki)) return fail("public key is not a P-256 key in canonical encoding", computedId)
        if (signature == null || signature.isEmpty()) return fail("manifest.sig is missing or empty", computedId)
        val valid = try {
            Signature.getInstance(BundleFormat.JCA_SIGNATURE).run {
                initVerify(key)
                update(manifestBytes)
                verify(signature)
            }
        } catch (e: GeneralSecurityException) {
            false
        }
        if (!valid) return fail("signature does not verify over manifest.json with the key in signer.json", computedId)
        return SignatureOutcome(Check(NAME, CheckStatus.PASSED, "Signature verifies; key id $computedId. $SCOPE"), computedId)
    }

    private fun isP256(key: PublicKey, spki: ByteArray): Boolean {
        val ec = key as? ECPublicKey ?: return false
        val p = ec.params
        return p.curve == P256.curve && p.generator == P256.generator && p.order == P256.order &&
            p.cofactor == P256.cofactor && key.encoded.contentEquals(spki)
    }

    private fun fail(reason: String, keyId: String?): SignatureOutcome =
        SignatureOutcome(Check(NAME, CheckStatus.FAILED, "$reason. $SCOPE"), keyId)
}
