package org.sakshi.acquisition.projection

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.graphics.drawable.Icon
import android.content.Context
import android.content.Intent
import androidx.core.content.IntentCompat
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.graphics.Rect
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.WindowManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/** User-started, non-exported foreground service owning exactly one projection and virtual display. */
public class ProjectionCaptureService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var captureJob: Job? = null
    private var frameSource: MediaProjectionFrameSource? = null
    private var activeSessionId: String? = null
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> finishCapture()
            ACTION_START -> startCapture(intent)
            else -> stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    private fun startCapture(intent: Intent) {
        if (captureJob?.isActive == true) {
            return
        }
        val sessionId = intent.getStringExtra(EXTRA_SESSION_ID) ?: return fail("Capture request is missing its session ID")
        activeSessionId = sessionId
        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
        val resultData = IntentCompat.getParcelableExtra(intent, EXTRA_RESULT_DATA, Intent::class.java)
            ?: return fail("Android consent data is unavailable")
        val mode = runCatching {
            ProjectionCaptureMode.valueOf(intent.getStringExtra(EXTRA_MODE).orEmpty())
        }.getOrElse { return fail("Capture mode is invalid") }

        try {
            ensureNotificationChannel()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification(), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
            } else startForeground(NOTIFICATION_ID, notification())
            activeSessionId = sessionId
            val manager = getSystemService(MediaProjectionManager::class.java)
            val projection: MediaProjection = checkNotNull(manager.getMediaProjection(resultCode, resultData)) {
                "Android did not provide a screen capture session."
            }
            val metrics = displayMetrics()
            val token = ProjectionToken(resultCode, resultData)
            frameSource = MediaProjectionFrameSource(projection, ProjectionConfig(metrics.widthPixels, metrics.heightPixels, metrics.densityDpi), token) {
                finishCapture()
            }
            captureJob = scope.launch {
                val source = checkNotNull(frameSource)
                val session = ProjectionSession(source)
                var failure: String? = null
                try {
                    withTimeout(runtime().draftStore.limits.sessionDurationMs) {
                        when (mode) {
                            ProjectionCaptureMode.SNAPSHOT -> {
                                kotlinx.coroutines.delay(SNAPSHOT_SETTLE_MS)
                                withContext(Dispatchers.IO) { source.captureNextFrame() }?.let {
                                    runtime().draftStore.stage(it)
                                } ?: throw IllegalStateException("No visible frame arrived from Android.")
                            }
                            ProjectionCaptureMode.BURST -> withContext(Dispatchers.IO) {
                                kotlinx.coroutines.delay(SNAPSHOT_SETTLE_MS)
                                session.captureBurst(
                                    maxFrames = runtime().draftStore.limits.maxFrames,
                                    intervalMs = runtime().draftStore.limits.sampleIntervalMs,
                                    maxAttempts = runtime().draftStore.limits.maxFrames * 2,
                                ) { frame ->
                                    runtime().draftStore.stage(frame)
                                    true
                                }
                            }
                        }
                    }
                } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
                    // The configured session deadline is a normal bounded stop, with any complete drafts retained.
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    failure = error.message ?: "Capture stopped because Android could not provide a frame."
                } finally {
                    session.close()
                    frameSource = null
                    runtime().completed(sessionId, failure)
                    releaseForeground()
                    activeSessionId = null
                    stopSelf()
                }
            }
        } catch (error: Exception) {
            runtime().completed(sessionId, "Android could not start the screen capture.")
            finishCapture()
        }
    }

    private fun finishCapture() {
        captureJob?.cancel()
        captureJob = null
        frameSource?.close()
        frameSource = null
        activeSessionId?.let { session ->
            runtime().completed(session)
        }
        activeSessionId = null
        releaseForeground()
        stopSelf()
    }

    private fun fail(message: String) {
        activeSessionId?.let { runtime().completed(it, message) }
        stopSelf()
    }

    private fun displayMetrics(): DisplayMetrics {
        val metrics = resources.displayMetrics
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val windowManager = getSystemService(WindowManager::class.java)
            val bounds: Rect = windowManager.maximumWindowMetrics.bounds
            return DisplayMetrics().apply {
                widthPixels = bounds.width()
                heightPixels = bounds.height()
                densityDpi = resources.configuration.densityDpi
            }
        }
        return metrics
    }

    private fun notification(): Notification {
        val stopIntent = Intent(this, ProjectionCaptureService::class.java).setAction(ACTION_STOP)
        val stop = PendingIntent.getService(this, STOP_REQUEST, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle(getString(R.string.projection_notification_title))
            .setContentText(getString(R.string.projection_notification_body))
            .setCategory(Notification.CATEGORY_SERVICE)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(Icon.createWithResource(this, android.R.drawable.ic_media_pause), getString(R.string.projection_stop), stop).build())
            .build()
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, getString(R.string.projection_notification_channel), NotificationManager.IMPORTANCE_LOW))
        }
    }

    private fun releaseForeground() {
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun runtime(): ProjectionCaptureRuntime =
        (application as ProjectionRuntimeOwner).projectionCaptureRuntime

    override fun onDestroy() {
        frameSource?.close()
        frameSource = null
        scope.cancel()
        super.onDestroy()
    }

    internal companion object {
        internal const val CHANNEL_ID = "projection_capture"
        internal const val NOTIFICATION_ID = 4206
        internal const val STOP_REQUEST = 4207
        internal const val SNAPSHOT_SETTLE_MS = 5_000L
        internal const val ACTION_START = "org.sakshi.projection.START"
        internal const val ACTION_STOP = "org.sakshi.projection.STOP"
        internal const val EXTRA_SESSION_ID = "projection_session_id"
        internal const val EXTRA_RESULT_CODE = "projection_result_code"
        internal const val EXTRA_RESULT_DATA = "projection_result_data"
        internal const val EXTRA_MODE = "projection_capture_mode"
    }
}

/** Implemented by the application object so the Android service can use the process-only key owner. */
public interface ProjectionRuntimeOwner {
    public val projectionCaptureRuntime: ProjectionCaptureRuntime
}
