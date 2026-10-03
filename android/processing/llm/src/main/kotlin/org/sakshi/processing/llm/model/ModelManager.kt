package org.sakshi.processing.llm.model

import android.content.Context
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import org.sakshi.core.integrity.Sha256
import org.sakshi.processing.llm.engine.LlmEngine

public data class ModelPreset(
    val id: String,
    val displayName: String,
    val filename: String,
    val sizeDescription: String,
    val downloadUrl: String,
    val description: String,
    val isRecommended: Boolean = false,
)

public class ModelManager public constructor(
    public val modelsDir: File,
) {
    public constructor(context: Context) : this(File(context.filesDir, "models"))

    public companion object {
        public const val TRIM_MEMORY_RUNNING_CRITICAL: Int = 15

        public val QWEN_2_5_1_5B: ModelPreset = ModelPreset(
            id = "qwen2.5-1.5b-instruct-q4_k_m",
            displayName = "Qwen2.5 1.5B Instruct (Q4_K_M)",
            filename = "qwen2.5-1.5b-instruct-q4_k_m.gguf",
            sizeDescription = "1.12 GB",
            downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf",
            description = "Recommended for multilingual text, Hindi, Malayalam and English.",
            isRecommended = true,
        )

        public val SMOLLM2_1_7B: ModelPreset = ModelPreset(
            id = "smollm2-1.7b-instruct-q4_k_m",
            displayName = "SmolLM2 1.7B Instruct (Q4_K_M)",
            filename = "smollm2-1.7b-instruct-q4_k_m.gguf",
            sizeDescription = "1.06 GB",
            downloadUrl = "https://huggingface.co/HuggingFaceTB/SmolLM2-1.7B-Instruct-GGUF/resolve/main/smollm2-1.7b-instruct-q4_k_m.gguf",
            description = "Alternative lightweight model for English text.",
            isRecommended = false,
        )

        public val PRESETS: List<ModelPreset> = listOf(QWEN_2_5_1_5B, SMOLLM2_1_7B)
    }

    private val activeEngines: MutableSet<LlmEngine> = mutableSetOf()
    private val evictionCallbacks: MutableList<() -> Unit> = mutableListOf()

    init {
        if (!modelsDir.exists()) {
            modelsDir.mkdirs()
        }
    }

    public fun getModelFile(modelId: String): File {
        return File(modelsDir, modelId)
    }

    public fun hasModel(modelId: String): Boolean {
        val file = getModelFile(modelId)
        return file.exists() && file.isFile && file.length() > 0L
    }

    public fun listModelFiles(): List<File> {
        return modelsDir.listFiles()?.filter { it.isFile } ?: emptyList()
    }

    public fun deleteModel(modelId: String): Boolean {
        val file = getModelFile(modelId)
        return if (file.exists()) file.delete() else false
    }

    public fun importModelStream(
        sourceStream: InputStream,
        targetFileName: String,
        onProgress: ((bytesRead: Long) -> Unit)? = null,
    ): File {
        val tempFile = File(modelsDir, "$targetFileName.tmp")
        val targetFile = File(modelsDir, targetFileName)
        if (tempFile.exists()) tempFile.delete()
        sourceStream.use { input ->
            FileOutputStream(tempFile).use { output ->
                val buffer = ByteArray(65536)
                var read: Int
                var total = 0L
                while (input.read(buffer).also { read = it } != -1) {
                    output.write(buffer, 0, read)
                    total += read
                    onProgress?.invoke(total)
                }
                output.flush()
            }
        }
        if (targetFile.exists()) targetFile.delete()
        if (!tempFile.renameTo(targetFile)) {
            tempFile.copyTo(targetFile, overwrite = true)
            tempFile.delete()
        }
        return targetFile
    }

    public fun verifyChecksum(file: File, expectedSha256: String): Boolean {
        if (!file.exists() || !file.isFile) return false
        val computed = computeSha256(file)
        return computed.equals(expectedSha256.trim(), ignoreCase = true)
    }

    public fun computeSha256(file: File): String {
        require(file.exists() && file.isFile) { "File does not exist: ${file.absolutePath}" }
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { stream ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (stream.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return Sha256.hex(digest.digest())
    }

    public fun registerActiveEngine(engine: LlmEngine): Unit {
        synchronized(activeEngines) {
            activeEngines.add(engine)
        }
    }

    public fun unregisterActiveEngine(engine: LlmEngine): Unit {
        synchronized(activeEngines) {
            activeEngines.remove(engine)
        }
    }

    public fun addEvictionCallback(callback: () -> Unit): Unit {
        synchronized(evictionCallbacks) {
            evictionCallbacks.add(callback)
        }
    }

    public fun removeEvictionCallback(callback: () -> Unit): Unit {
        synchronized(evictionCallbacks) {
            evictionCallbacks.remove(callback)
        }
    }

    public fun evictActiveContext(): Unit {
        val enginesToClose = synchronized(activeEngines) {
            val list = activeEngines.toList()
            activeEngines.clear()
            list
        }
        for (engine in enginesToClose) {
            try {
                engine.close()
            } catch (_: Exception) {
                // Ignore exception on eviction close
            }
        }

        val callbacksToRun = synchronized(evictionCallbacks) {
            evictionCallbacks.toList()
        }
        for (callback in callbacksToRun) {
            try {
                callback.invoke()
            } catch (_: Exception) {
                // Ignore exception in callback
            }
        }
    }

    public fun onTrimMemory(level: Int): Boolean {
        if (level >= TRIM_MEMORY_RUNNING_CRITICAL) {
            evictActiveContext()
            return true
        }
        return false
    }
}
