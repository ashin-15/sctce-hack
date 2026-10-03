package org.sakshi.acquisition.projection

import android.content.Context
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.os.Build
import android.app.NotificationManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Application-process owner for the ephemeral draft key, user-visible state and service commands. */
public class ProjectionCaptureRuntime(context: Context) : ProjectionCaptureController {
    private val appContext = context.applicationContext
    internal val draftStore = EncryptedProjectionDraftStore(appContext)
    private val handler = Handler(Looper.getMainLooper())
    private val mutableState = MutableStateFlow<ProjectionCaptureState>(ProjectionCaptureState.Idle)
    override val state: StateFlow<ProjectionCaptureState> = mutableState.asStateFlow()
    override val drafts: StateFlow<List<ProjectionDraft>> = draftStore.drafts
    private var expiryTask: Runnable? = null
    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF && draftStore.activeSessionId() != null) {
                if (mutableState.value is ProjectionCaptureState.Capturing) stop(appContext)
                draftStore.clear()
                expiryTask?.let(handler::removeCallbacks)
                mutableState.value = ProjectionCaptureState.Idle
            }
        }
    }

    init {
        ContextCompat.registerReceiver(
            appContext,
            screenOffReceiver,
            IntentFilter(Intent.ACTION_SCREEN_OFF),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    override fun start(context: Context, resultCode: Int, resultData: Intent, mode: ProjectionCaptureMode): String {
        check(NotificationManagerCompat.from(appContext).areNotificationsEnabled()) {
            "Enable Sakshi notifications to keep a visible capture stop control."
        }
        check(draftStore.drafts.value.isEmpty()) { "Review or discard the previous capture before starting again." }
        check(mutableState.value !is ProjectionCaptureState.Capturing) { "A capture session is already active." }
        val sessionId = UUID.randomUUID().toString()
        expiryTask?.let(handler::removeCallbacks)
        draftStore.beginSession(sessionId)
        mutableState.value = ProjectionCaptureState.Capturing(sessionId, mode)
        val intent = Intent(appContext, ProjectionCaptureService::class.java)
            .setAction(ProjectionCaptureService.ACTION_START)
            .putExtra(ProjectionCaptureService.EXTRA_SESSION_ID, sessionId)
            .putExtra(ProjectionCaptureService.EXTRA_RESULT_CODE, resultCode)
            .putExtra(ProjectionCaptureService.EXTRA_RESULT_DATA, resultData)
            .putExtra(ProjectionCaptureService.EXTRA_MODE, mode.name)
        try {
            ContextCompat.startForegroundService(appContext, intent)
        } catch (error: Exception) {
            draftStore.clear()
            mutableState.value = ProjectionCaptureState.Failed("Could not start screen capture. Review or discard any drafts and try again.")
            throw error
        }
        return sessionId
    }

    override fun stop(context: Context) {
        val intent = Intent(appContext, ProjectionCaptureService::class.java).setAction(ProjectionCaptureService.ACTION_STOP)
        runCatching { appContext.startService(intent) }
    }

    override suspend fun readDraft(draftId: String): ByteArray = draftStore.read(draftId)

    override fun discardDraft(draftId: String) {
        draftStore.discard(draftId)
        if (draftStore.drafts.value.isEmpty()) mutableState.value = ProjectionCaptureState.Idle
    }

    override fun discardAll() {
        stop(appContext)
        draftStore.clear()
        expiryTask?.let(handler::removeCallbacks)
        mutableState.value = ProjectionCaptureState.Idle
    }

    internal fun completed(sessionId: String, error: String? = null) {
        if (draftStore.activeSessionId() != sessionId) return
        if (error != null) {
            mutableState.value = ProjectionCaptureState.Failed("Capture stopped before it finished. Review any complete frames below.")
        } else {
            val count = draftStore.drafts.value.size
            mutableState.value = if (count == 0) ProjectionCaptureState.Idle else ProjectionCaptureState.Ready(sessionId, count)
        }
        if (draftStore.drafts.value.isNotEmpty()) {
            val expiry = Runnable {
                if (draftStore.activeSessionId() == sessionId) {
                    draftStore.clear()
                    mutableState.value = ProjectionCaptureState.Idle
                }
            }
            expiryTask?.let(handler::removeCallbacks)
            expiryTask = expiry
            handler.postDelayed(expiry, draftStore.limits.draftExpiryMs)
        } else draftStore.clear()
    }

    internal fun screenLocked(sessionId: String?) {
        if (sessionId == null || draftStore.activeSessionId() == sessionId) {
            draftStore.clear()
            mutableState.value = ProjectionCaptureState.Idle
        }
    }

    public fun consentIntent(): Intent {
        val manager = appContext.getSystemService(android.media.projection.MediaProjectionManager::class.java)
        return manager.createScreenCaptureIntent()
    }

    public fun notificationsEnabled(): Boolean {
        if (!NotificationManagerCompat.from(appContext).areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true
        val manager = appContext.getSystemService(NotificationManager::class.java)
        val channel = manager.getNotificationChannel(ProjectionCaptureService.CHANNEL_ID)
        return channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE
    }
}
