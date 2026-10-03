package org.sakshi.app.aimodel

import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.sakshi.processing.llm.model.ModelManager

@OptIn(ExperimentalCoroutinesApi::class)
class AiModelViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var tempDir: File
    private lateinit var modelManager: ModelManager

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        tempDir = File.createTempFile("models_test", "").also {
            it.delete()
            it.mkdirs()
        }
        modelManager = ModelManager(tempDir)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
        tempDir.deleteRecursively()
    }

    @Test
    fun initialUiStateHasNoInstalledModel() {
        val viewModel = AiModelViewModel(modelManager, testDispatcher)
        val state = viewModel.uiState.value
        assertNull(state.installedModel)
        assertEquals(ModelManager.PRESETS.size, state.presets.size)
    }

    @Test
    fun detectsInstalledPresetModel() {
        val modelFile = File(tempDir, "qwen2.5-1.5b-instruct-q4_k_m.gguf")
        modelFile.writeText("synthetic model data")

        val viewModel = AiModelViewModel(modelManager, testDispatcher)
        val state = viewModel.uiState.value
        val installed = state.installedModel

        assertNotNull(installed)
        assertEquals("qwen2.5-1.5b-instruct-q4_k_m.gguf", installed.filename)
        assertEquals("Qwen2.5 1.5B Instruct (Q4_K_M)", installed.displayName)
        assertTrue(installed.isRunning)
        assertTrue(installed.statusDescription.contains("Ready"))
    }

    @Test
    fun deletesModelAndResetsState() {
        val modelFile = File(tempDir, "qwen2.5-1.5b-instruct-q4_k_m.gguf")
        modelFile.writeText("synthetic model data")

        val viewModel = AiModelViewModel(modelManager, testDispatcher)
        assertNotNull(viewModel.uiState.value.installedModel)

        viewModel.deleteModel("qwen2.5-1.5b-instruct-q4_k_m.gguf")
        assertNull(viewModel.uiState.value.installedModel)
        assertEquals("Model file deleted.", viewModel.uiState.value.notice)
    }

    @Test
    fun testInferenceExecutesAndProducesOutput() = runTest(testDispatcher) {
        val modelFile = File(tempDir, "qwen2.5-1.5b-instruct-q4_k_m.gguf")
        modelFile.writeText("synthetic model data")

        val viewModel = AiModelViewModel(modelManager, testDispatcher)
        viewModel.testInference()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        val output = state.testOutput
        assertNotNull(output)
        assertTrue(output.contains("harassment"))
    }

    @Test
    fun clearNoticeRemovesNoticeAndError() {
        val viewModel = AiModelViewModel(modelManager, testDispatcher)
        viewModel.deleteModel("non_existent.gguf")
        assertNotNull(viewModel.uiState.value.notice)

        viewModel.clearNotice()
        assertNull(viewModel.uiState.value.notice)
        assertNull(viewModel.uiState.value.error)
    }
}
