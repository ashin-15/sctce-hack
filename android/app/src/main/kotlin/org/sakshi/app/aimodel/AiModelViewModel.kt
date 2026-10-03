package org.sakshi.app.aimodel

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.sakshi.processing.llm.engine.GenerationOutcome
import org.sakshi.processing.llm.engine.GenerationRequest
import org.sakshi.processing.llm.engine.NativeLlmBridge
import org.sakshi.processing.llm.model.LlmSessionManager
import org.sakshi.processing.llm.model.ModelManager
import org.sakshi.processing.llm.model.ModelPreset

data class InstalledModel(
    val filename: String,
    val displayName: String,
    val sizeBytes: Long,
    val formattedSize: String,
    val sha256Prefix: String,
    val isRunning: Boolean = false,
    val statusDescription: String = "Model installed; not loaded",
)

data class AiModelUiState(
    val installedModel: InstalledModel? = null,
    val presets: List<ModelPreset> = ModelManager.PRESETS,
    val detectedInDownloads: ModelPreset? = null,
    val isImporting: Boolean = false,
    val importedBytes: Long = 0L,
    val totalBytesToImport: Long = 0L,
    val isTestingInference: Boolean = false,
    val testOutput: String? = null,
    val notice: String? = null,
    val error: String? = null,
)

class AiModelViewModel(
    private val modelManager: ModelManager,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    private val mutableUiState = MutableStateFlow(AiModelUiState())
    val uiState: StateFlow<AiModelUiState> = mutableUiState.asStateFlow()
    init {
        refreshInstalledModel()
    }

    override fun onCleared() {
        super.onCleared()
    }

    fun refreshInstalledModel() {
        viewModelScope.launch {
            val preset = ModelManager.QWEN_2_5_1_5B
            val primary = modelManager.getModelFile(preset.id)
            if (withContext(ioDispatcher) { primary.isFile && primary.length() > 0L }) {
                val details = withContext(ioDispatcher) {
                    val size = primary.length()
                    val hash = runCatching { modelManager.computeSha256(primary).take(12) }.getOrDefault("unknown")
                    val sizeMb = size / (1024.0 * 1024.0)
                    val formatted = if (sizeMb >= 1000) {
                        String.format(java.util.Locale.US, "%.2f GB", sizeMb / 1024.0)
                    } else {
                        String.format(java.util.Locale.US, "%.1f MB", sizeMb)
                    }
                    Triple(size, hash, formatted)
                }
                val status = if (NativeLlmBridge.isAvailable) {
                    "Model file found; upstream provenance unverified; runtime available; not loaded"
                } else {
                    "Model file found; upstream provenance unverified; native runtime missing"
                }
                mutableUiState.value = mutableUiState.value.copy(
                    installedModel = InstalledModel(
                        filename = primary.name,
                        displayName = preset.displayName,
                        sizeBytes = details.first,
                        formattedSize = details.third,
                        sha256Prefix = details.second,
                        isRunning = false,
                        statusDescription = status,
                    ),
                )
            } else {
                mutableUiState.value = mutableUiState.value.copy(installedModel = null, testOutput = null)
            }
        }
        scanDownloadsFolder()
    }

    fun scanDownloadsFolder() {
        try {
            val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val match = ModelManager.PRESETS.firstOrNull { preset ->
                val candidate = File(downloadDir, preset.filename)
                candidate.exists() && candidate.canRead() && candidate.length() > 0L
            }
            mutableUiState.value = mutableUiState.value.copy(detectedInDownloads = match)
        } catch (_: Exception) {
            mutableUiState.value = mutableUiState.value.copy(detectedInDownloads = null)
        }
    }

    fun downloadPreset(preset: ModelPreset, context: Context) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(preset.downloadUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            mutableUiState.value = mutableUiState.value.copy(error = "Could not open browser to download model.")
        }
    }

    fun importDetectedDownload(preset: ModelPreset) {
        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val file = File(downloadDir, preset.filename)
        if (!file.exists() || !file.canRead()) {
            mutableUiState.value = mutableUiState.value.copy(error = "Cannot read downloaded file directly. Please use the file picker.")
            return
        }
        mutableUiState.value = mutableUiState.value.copy(
            isImporting = true,
            importedBytes = 0L,
            totalBytesToImport = file.length(),
            error = null,
        )
        viewModelScope.launch {
            try {
                withContext(ioDispatcher) {
                    modelManager.importModelFile(
                        sourceFile = file,
                        targetFileName = preset.filename,
                        onProgress = { bytes ->
                            mutableUiState.value = mutableUiState.value.copy(importedBytes = bytes)
                        },
                    )
                }
                refreshInstalledModel()
                mutableUiState.value = mutableUiState.value.copy(
                    isImporting = false,
                    notice = "Model file imported. It has not been loaded.",
                )
            } catch (e: Exception) {
                mutableUiState.value = mutableUiState.value.copy(
                    isImporting = false,
                    error = "Failed to import model: ${e.message}",
                )
            }
        }
    }

    fun importFromUri(uri: Uri, context: Context) {
        mutableUiState.value = mutableUiState.value.copy(
            isImporting = true,
            importedBytes = 0L,
            totalBytesToImport = 0L,
            error = null,
        )
        viewModelScope.launch {
            try {
                withContext(ioDispatcher) {
                    val contentResolver = context.contentResolver
                    val rawName = resolveFileName(uri, contentResolver) ?: "local_model.gguf"
                    val fileName = if (rawName.endsWith(".gguf", ignoreCase = true)) rawName else "$rawName.gguf"
                    val fileSize = resolveFileSize(uri, contentResolver)
                    withContext(Dispatchers.Main) {
                        mutableUiState.value = mutableUiState.value.copy(totalBytesToImport = fileSize)
                    }
                    val inputStream = contentResolver.openInputStream(uri)
                        ?: throw IllegalStateException("Could not open input stream for selected file")

                    modelManager.importModelStream(
                        sourceStream = inputStream,
                        targetFileName = fileName,
                        onProgress = { bytes ->
                            mutableUiState.value = mutableUiState.value.copy(importedBytes = bytes)
                        },
                    )
                }
                refreshInstalledModel()
                mutableUiState.value = mutableUiState.value.copy(
                    isImporting = false,
                    notice = "Model file imported. It has not been loaded.",
                )
            } catch (e: Exception) {
                mutableUiState.value = mutableUiState.value.copy(
                    isImporting = false,
                    error = "Failed to import model: ${e.message}",
                )
            }
        }
    }

    fun testInference() {
        if (mutableUiState.value.installedModel == null) return
        mutableUiState.value = mutableUiState.value.copy(isTestingInference = true, error = null)
        viewModelScope.launch {
            try {
                val result = withContext(ioDispatcher) {
                    val startTime = System.currentTimeMillis()
                    val outcome = LlmSessionManager(modelManager, ioDispatcher).withQwenSession { engine, _ ->
                        engine.generate(
                            GenerationRequest(
                                systemPrompt = "Reply briefly and literally.",
                                userPrompt = "Return the word READY.",
                                maxTokens = 8,
                            ),
                        )
                    }
                    val durationMs = System.currentTimeMillis() - startTime
                    when (outcome) {
                        is GenerationOutcome.Success -> "Local Qwen inference completed (${durationMs} ms)."
                        is GenerationOutcome.Failed -> "Inference failed: ${outcome.reason} - ${outcome.message}"
                    }
                }
                mutableUiState.value = mutableUiState.value.copy(
                    isTestingInference = false,
                    testOutput = result,
                    installedModel = mutableUiState.value.installedModel?.copy(
                        isRunning = false,
                        statusDescription = if (result.startsWith("Local Qwen inference completed")) {
                            "Last on-device inference succeeded; model unloaded"
                        } else {
                            "On-device inference failed; model unloaded"
                        },
                    ),
                )
            } catch (e: Exception) {
                mutableUiState.value = mutableUiState.value.copy(
                    isTestingInference = false,
                    error = "Test inference failed: ${e.message}",
                )
            }
        }
    }

    fun deleteModel(filename: String) {
        modelManager.deleteModel(filename)
        refreshInstalledModel()
        mutableUiState.value = mutableUiState.value.copy(notice = "Model file deleted.")
    }

    fun clearNotice() {
        mutableUiState.value = mutableUiState.value.copy(notice = null, error = null)
    }

    private fun resolveFileName(uri: Uri, contentResolver: android.content.ContentResolver): String? {
        if (uri.scheme == "content") {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    return cursor.getString(nameIndex)
                }
            }
        }
        return uri.lastPathSegment
    }

    private fun resolveFileSize(uri: Uri, contentResolver: android.content.ContentResolver): Long {
        if (uri.scheme == "content") {
            try {
                contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                    val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                    if (sizeIndex != -1 && cursor.moveToFirst() && !cursor.isNull(sizeIndex)) {
                        return cursor.getLong(sizeIndex)
                    }
                }
            } catch (_: Exception) {
            }
        }
        return 0L
    }

    companion object {
        fun factory(
            modelManager: ModelManager,
            ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { AiModelViewModel(modelManager, ioDispatcher) }
        }
    }
}
