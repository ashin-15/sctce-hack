package org.sakshi.export.bundle

import java.util.Base64
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.sakshi.core.integrity.CanonicalJson

/** Parsed `signer.json`. Every field is a claim to be checked, not a fact. */
internal class SignerFile(val algorithm: String, val spkiBase64: String, val keyId: String) {
    companion object {
        fun encode(spki: ByteArray, keyId: String): ByteArray = CanonicalJson.encode(
            jsonObject(
                "algorithm" to JsonPrimitive(BundleFormat.ALGORITHM),
                "public_key_spki_der_base64" to JsonPrimitive(Base64.getEncoder().encodeToString(spki)),
                "key_id" to JsonPrimitive(keyId),
            ),
        )

        fun parse(bytes: ByteArray): SignerFile {
            val o = JsonInput.parse(String(bytes, Charsets.UTF_8)) as? JsonObject
                ?: throw IllegalArgumentException("signer.json must be an object")
            return SignerFile(o.str("algorithm"), o.str("public_key_spki_der_base64"), o.str("key_id"))
        }
    }
}
