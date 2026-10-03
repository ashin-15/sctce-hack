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
import org.sakshi.processing.llm.engine.DeterministicFallbackEngine
import org.sakshi.processing.llm.engine.GenerationOutcome
import org.sakshi.processing.llm.engine.GenerationRequest
import org.sakshi.processing.llm.engine.LlamaCppEngine
import org.sakshi.processing.llm.engine.LlmEngine
import org.sakshi.processing.llm.engine.LlmStatus
import org.sakshi.processing.llm.engine.NativeLlmBridge
import org.sakshi.processing.llm.model.ModelManager
import org.sakshi.processing.llm.model.ModelPreset

data class InstalledModel(
    val filename: String,
    val displayName: String,
    val sizeBytes: Long,
    val formattedSize: String,
    val sha256Prefix: String,
    val isRunning: Boolean = false,
    val statusDescription: String = "Ready and running on device",
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
    private var activeEngine: LlmEngine? = null

    init {
        refreshInstalledModel()
    }

    override fun onCleared() {
        super.onCleared()
        activeEngine?.let { modelManager.unregisterActiveEngine(it) }
        activeEngine?.close()
        activeEngine = null
    }

    fun refreshInstalledModel() {
        val files = modelManager.listModelFiles()
        val primary = files.firstOrNull { it.name.endsWith(".gguf", ignoreCase = true) }
        if (primary != null) {
            val sizeMb = primary.length() / (1024.0 * 1024.0)
            val sizeFormatted = if (sizeMb >= 1000) {
                String.format(java.util.Locale.US, "%.2f GB", sizeMb / 1024.0)
            } else {
                String.format(java.util.Locale.US, "%.1f MB", sizeMb)
            }
            val hash = try {
                val hex = modelManager.computeSha256(primary)
                if (hex.length >= 12) hex.substring(0, 12) + "..." else hex
            } catch (_: Exception) {
                "unknown"
            }
            val matchedPreset = ModelManager.PRESETS.firstOrNull { it.filename.equals(primary.name, ignoreCase = true) }
            val displayName = matchedPreset?.displayName ?: primary.name

            if (activeEngine == null) {
                val engine = if (NativeLlmBridge.isAvailable) {
                    LlamaCppEngine(modelPath = primary.absolutePath)
                } else {
                    DeterministicFallbackEngine()
                }
                activeEngine = engine
                modelManager.registerActiveEngine(engine)
            }

            val statusDesc = if (activeEngine?.status == LlmStatus.Ready) {
                "Ready and running on device"
            } else {
                "Model loaded"
            }

            mutableUiState.value = mutableUiState.value.copy(
                installedModel = InstalledModel(
                    filename = primary.name,
                    displayName = displayName,
                    sizeBytes = primary.length(),
                    formattedSize = sizeFormatted,
                    sha256Prefix = hash,
                    isRunning = true,
                    statusDescription = statusDesc,
                ),
            )
        } else {
            activeEngine?.let { modelManager.unregisterActiveEngine(it) }
            activeEngine?.close()
            activeEngine = null
            mutableUiState.value = mutableUiState.value.copy(installedModel = null, testOutput = null)
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
                    notice = "Model file imported and running on device.",
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
                    notice = "Model file imported and running on device.",
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
        val model = mutableUiState.value.installedModel ?: return
        mutableUiState.value = mutableUiState.value.copy(isTestingInference = true, error = null)
        viewModelScope.launch {
            try {
                val result = withContext(ioDispatcher) {
                    val file = modelManager.getModelFile(model.filename)
                    val engine = activeEngine ?: (
                        if (NativeLlmBridge.isAvailable) {
                            LlamaCppEngine(modelPath = file.absolutePath)
                        } else {
                            DeterministicFallbackEngine()
                        }
                    ).also {
                        activeEngine = it
                        modelManager.registerActiveEngine(it)
                    }

                    val startTime = System.currentTimeMillis()
                    val request = GenerationRequest(
                        systemPrompt = "You are a local AI assistant analyzing harassment patterns.",
                        userPrompt = "Explain the predicted label [src-1] Evidence: \"threatening message\"; classifier labels: harassment",
                    )
                    val outcome = engine.generate(request)
                    val durationMs = System.currentTimeMillis() - startTime
                    when (outcome) {
                        is GenerationOutcome.Success -> "${outcome.text} (${durationMs}ms)"
                        is GenerationOutcome.Failed -> "Inference failed: ${outcome.reason} - ${outcome.message}"
                    }
                }
                mutableUiState.value = mutableUiState.value.copy(
                    isTestingInference = false,
                    testOutput = result,
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
