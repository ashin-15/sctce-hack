package org.sakshi.app.aimodel

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.sakshi.processing.llm.model.ModelManager
import org.sakshi.processing.llm.model.ModelPreset

data class InstalledModel(
    val filename: String,
    val sizeBytes: Long,
    val formattedSize: String,
    val sha256Prefix: String,
)

data class AiModelUiState(
    val installedModel: InstalledModel? = null,
    val presets: List<ModelPreset> = ModelManager.PRESETS,
    val isImporting: Boolean = false,
    val importedBytes: Long = 0L,
    val notice: String? = null,
    val error: String? = null,
)

class AiModelViewModel(
    private val modelManager: ModelManager,
) : ViewModel() {

    private val mutableUiState = MutableStateFlow(AiModelUiState())
    val uiState: StateFlow<AiModelUiState> = mutableUiState.asStateFlow()

    init {
        refreshInstalledModel()
    }

    fun refreshInstalledModel() {
        val files = modelManager.listModelFiles()
        val primary = files.firstOrNull { it.name.endsWith(".gguf") }
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
            mutableUiState.value = mutableUiState.value.copy(
                installedModel = InstalledModel(
                    filename = primary.name,
                    sizeBytes = primary.length(),
                    formattedSize = sizeFormatted,
                    sha256Prefix = hash,
                ),
            )
        } else {
            mutableUiState.value = mutableUiState.value.copy(installedModel = null)
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

    fun importFromUri(uri: Uri, context: Context) {
        mutableUiState.value = mutableUiState.value.copy(isImporting = true, importedBytes = 0L, error = null)
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val contentResolver = context.contentResolver
                    val fileName = resolveFileName(uri, contentResolver) ?: "local_model.gguf"
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
                mutableUiState.value = mutableUiState.value.copy(isImporting = false, notice = "Model file imported.")
            } catch (e: Exception) {
                mutableUiState.value = mutableUiState.value.copy(isImporting = false, error = "Failed to import model: ${e.message}")
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

    companion object {
        fun factory(modelManager: ModelManager): ViewModelProvider.Factory = viewModelFactory {
            initializer { AiModelViewModel(modelManager) }
        }
    }
}
