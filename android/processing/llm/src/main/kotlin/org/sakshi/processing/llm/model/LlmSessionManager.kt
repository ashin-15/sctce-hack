package org.sakshi.processing.llm.model

import java.io.File
import java.io.FileInputStream
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.sakshi.processing.llm.engine.InferenceLock
import org.sakshi.processing.llm.engine.LlamaCppEngine
import org.sakshi.processing.llm.engine.LlmEngine
import org.sakshi.processing.llm.engine.LlmStatus
import org.sakshi.processing.llm.engine.ModelInfo
import org.sakshi.processing.llm.engine.NativeLlmBridge
import org.sakshi.processing.llm.engine.NativeLoadResult

public enum class LlmUnavailableReason {
    MODEL_MISSING, RUNTIME_MISSING, MODEL_INVALID, LOAD_FAILED, MODEL_IDENTITY_MISMATCH, CONTEXT_INIT_FAILED,
}

public class LlmSessionUnavailable(
    public val reason: LlmUnavailableReason,
    public val metadata: NativeLoadResult? = null,
) : IllegalStateException(reason.name)

public data class LlmModelIdentity(
    public val presetId: String,
    public val weightSha256: String,
    public val provenanceVerified: Boolean,
    public val runtimeCommit: String,
    public val runtimeVersion: String,
)

/** Loads only the explicitly selected Qwen preset while holding the same process-wide lock as STT. */
public class LlmSessionManager(
    private val models: ModelManager,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    public suspend fun <T> withQwenSession(block: suspend (LlmEngine, LlmModelIdentity) -> T): T =
        InferenceLock.withLock {
            withContext(dispatcher) {
                if (!NativeLlmBridge.isAvailable) throw LlmSessionUnavailable(LlmUnavailableReason.RUNTIME_MISSING)
                val preset = ModelManager.QWEN_2_5_1_5B
                val file = models.getModelFile(preset.id)
                validateModelFile(file)
                val digest = runCatching { models.computeSha256(file) }
                    .getOrElse { throw LlmSessionUnavailable(LlmUnavailableReason.MODEL_INVALID) }
                val engine = LlamaCppEngine(
                    modelPath = file.canonicalPath,
                    modelInfo = ModelInfo(
                        modelId = preset.id,
                        parameterCount = 1_500_000_000L,
                        quantization = "Q4_K_M",
                        contextWindowTokens = 2048,
                        sha256Checksum = digest,
                    ),
                )
                if (engine.status != LlmStatus.Ready) {
                    val reason = when (engine.loadResult.statusCode) {
                        NativeLoadResult.STATUS_MODEL_IDENTITY_MISMATCH -> LlmUnavailableReason.MODEL_IDENTITY_MISMATCH
                        NativeLoadResult.STATUS_CONTEXT_INIT_FAILED -> LlmUnavailableReason.CONTEXT_INIT_FAILED
                        else -> LlmUnavailableReason.LOAD_FAILED
                    }
                    engine.close()
                    throw LlmSessionUnavailable(reason, engine.loadResult)
                }
                models.registerActiveEngine(engine)
                try {
                    block(
                        engine,
                        LlmModelIdentity(
                            presetId = preset.id,
                            weightSha256 = digest,
                            provenanceVerified = false,
                            runtimeCommit = NativeLlmBridge.RUNTIME_COMMIT,
                            runtimeVersion = NativeLlmBridge.RUNTIME_VERSION,
                        ),
                    )
                } finally {
                    models.unregisterActiveEngine(engine)
                    engine.close()
                }
            }
        }

    private fun validateModelFile(file: File) {
        val canonical = runCatching { file.canonicalFile }.getOrNull()
            ?: throw LlmSessionUnavailable(LlmUnavailableReason.MODEL_INVALID)
        val root = runCatching { models.modelsDir.canonicalFile }.getOrNull()
            ?: throw LlmSessionUnavailable(LlmUnavailableReason.MODEL_INVALID)
        val expected = File(root, ModelManager.QWEN_2_5_1_5B.filename).canonicalFile
        if (!file.exists()) throw LlmSessionUnavailable(LlmUnavailableReason.MODEL_MISSING)
        if (canonical != expected || !canonical.isFile || canonical.length() !in MIN_MODEL_BYTES..MAX_MODEL_BYTES) {
            throw LlmSessionUnavailable(LlmUnavailableReason.MODEL_INVALID)
        }
        val magic = ByteArray(4)
        val validGguf = runCatching {
            FileInputStream(canonical).use { it.read(magic) == magic.size } && magic.contentEquals(byteArrayOf(0x47, 0x47, 0x55, 0x46))
        }.getOrDefault(false)
        if (!validGguf) throw LlmSessionUnavailable(LlmUnavailableReason.MODEL_INVALID)
    }

    private companion object {
        const val MIN_MODEL_BYTES: Long = 500L * 1024L * 1024L
        const val MAX_MODEL_BYTES: Long = 3L * 1024L * 1024L * 1024L
    }
}
