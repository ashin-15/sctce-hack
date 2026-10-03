package org.sakshi.acquisition.projection

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.flow.StateFlow

public enum class ProjectionCaptureMode { SNAPSHOT, BURST }

public data class ProjectionCaptureLimits(
    public val sessionDurationMs: Long = 120_000,
    public val maxFrames: Int = 20,
    public val sampleIntervalMs: Long = 1_000,
    public val maxPixels: Long = 4_000_000,
    public val maxFrameBytes: Int = 16 * 1024 * 1024,
    public val maxSessionBytes: Long = 128L * 1024 * 1024,
    public val draftExpiryMs: Long = 5 * 60 * 1000,
)

public sealed interface ProjectionCaptureState {
    public data object Idle : ProjectionCaptureState
    public data class Capturing(public val sessionId: String, public val mode: ProjectionCaptureMode) : ProjectionCaptureState
    public data class Ready(public val sessionId: String, public val frameCount: Int) : ProjectionCaptureState
    public data class Failed(public val message: String) : ProjectionCaptureState
}

public data class ProjectionDraft(
    public val id: String,
    public val sessionId: String,
    public val frameIndex: Int,
    public val observedAtMs: Long,
    public val width: Int,
    public val height: Int,
    public val sha256: String,
    public val byteSize: Int,
    public val blankOrUnavailable: Boolean,
    public val elapsedRealtimeMs: Long,
)

public interface ProjectionCaptureController {
    public val state: StateFlow<ProjectionCaptureState>
    public val drafts: StateFlow<List<ProjectionDraft>>
    public fun start(context: Context, resultCode: Int, resultData: Intent, mode: ProjectionCaptureMode): String
    public fun stop(context: Context)
    public suspend fun readDraft(draftId: String): ByteArray
    public fun discardDraft(draftId: String)
    public fun discardAll()
}
