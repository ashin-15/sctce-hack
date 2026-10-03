package org.sakshi.acquisition.notifications

import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import androidx.annotation.ChecksSdkIntAtLeast
import java.time.Clock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow

/** Whether the lane can run at all on this device. */
public enum class NotificationAvailability {
    AVAILABLE,

    /** Android is older than API 30, so the platform message parser and the lane are not available. */
    UNAVAILABLE_ANDROID_VERSION,
}

/**
 * The entry point of the optional notification observation lane. Off by default, session-only (memory) retention, no
 * disk writes of notification text, no network. Every entry point checks `Build.VERSION.SDK_INT >= 30`; below that the
 * lane reports [NotificationAvailability.UNAVAILABLE_ANDROID_VERSION] and does nothing.
 *
 * This class never asks for notification access. The app shows its disclosure, the person chooses, and then the app
 * starts [accessSettingsIntent]. Access granted and listener connected are tracked separately in [coverage].
 */
public class NotificationObservation internal constructor(
    context: Context,
    private val settings: NotificationSettings,
    private val environment: IntakeEnvironment,
    clock: Clock,
    dispatcher: CoroutineDispatcher,
) {
    private val tracker = CoverageTracker(clock)
    private val connectionLock = Any()
    private var listenerConnected = false
    private var activeSnapshotProvider: (() -> List<NotificationSource>?)? = null
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val differ = ObservationDiffer(activePolicy = {
        if (settings.state.value.includeActiveOnConnect) ActiveSnapshotPolicy.REPORT_AS_ACTIVE_SNAPSHOT else ActiveSnapshotPolicy.SEED_ONLY
    })

    /** The session-only candidates. Memory only; cleared on stop, lock, disable and revoke. */
    public val inbox: CandidateInbox = CandidateInbox(onDropped = tracker::onCandidatesDropped)

    internal val intake: NotificationIntake =
        NotificationIntake({ settings.state.value }, environment, tracker, differ, inbox, scope)

    /** The coverage state. There is no state that means everything was seen. */
    public val coverage: StateFlow<CoverageState> get() = tracker.state

    /** The coverage state with the interval ledger and content-free counters. */
    public val coverageDetail: StateFlow<CoverageDetail> get() = tracker.flow

    /** The person's choices. Read-only here; change them with the methods of this class. */
    public val settingsState: StateFlow<NotificationSettingsState> get() = settings.state

    private val component = ComponentName(context, SakshiNotificationListener::class.java)
    private val packageManager: PackageManager = context.packageManager
    private val notificationManager: NotificationManager? = context.getSystemService(NotificationManager::class.java)

    init {
        refresh()
        if (environment.isAvailable() && settings.state.value.enabled) {
            // The process started with the lane already on. Anything between the last run and now is unknown.
            tracker.onProcessRestart()
        }
    }

    public fun availability(): NotificationAvailability =
        if (environment.isAvailable()) NotificationAvailability.AVAILABLE else NotificationAvailability.UNAVAILABLE_ANDROID_VERSION

    /** True when the system lists this app's listener as allowed. Granted is not the same as connected. */
    public fun isAccessGranted(): Boolean {
        if (Build.VERSION.SDK_INT < NotificationBounds.MIN_API || !environment.isAvailable()) return false
        val manager = notificationManager ?: return false
        return try {
            manager.isNotificationListenerAccessGranted(component)
        } catch (ignored: SecurityException) {
            false
        }
    }

    /**
     * The system screen where the person grants notification access. This module never requests access itself and has
     * no fallback to any other screen: the caller handles a device that has no such screen.
     */
    public fun accessSettingsIntent(): Intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)

    /**
     * Switches the lane on after the person's affirmative choice: enables the listener component and starts a session.
     * Returns the availability; below API 30 nothing is changed.
     */
    public fun enable(): NotificationAvailability {
        val availability = availability()
        if (availability != NotificationAvailability.AVAILABLE) return availability
        settings.update { it.copy(enabled = true, paused = false) }
        setComponent(PackageManager.COMPONENT_ENABLED_STATE_ENABLED)
        beginSession()
        return availability
    }

    /** Switches the lane off, disables the listener component and clears everything held in memory. */
    public fun disable() {
        settings.update { it.copy(enabled = false, paused = false) }
        if (environment.isAvailable()) setComponent(PackageManager.COMPONENT_ENABLED_STATE_DISABLED)
        intake.stopAndClear()
        refresh()
    }

    /** Stops observing until [resume]. Notifications that arrive meanwhile are not replayed. */
    public fun pause() {
        if (!environment.isAvailable()) return
        settings.update { it.copy(paused = true) }
        intake.discardQueued()
        refresh()
    }

    public fun resume() {
        if (!environment.isAvailable()) return
        settings.update { it.copy(paused = false) }
        refresh()
    }

    /** Replaces the positive allowlist. The default is empty, which observes nothing. */
    public fun setAllowlist(allowlist: PackageAllowlist) {
        settings.update { it.copy(allowlist = allowlist) }
    }

    /** Separate opt-in, default off: keep available previews of notifications that arrive while the device is locked. */
    public fun setLockScreenPreviewsOptIn(optIn: Boolean) {
        settings.update { it.copy(lockScreenPreviewsOptIn = optIn) }
    }

    /** Whether notifications already shown when the listener connects are reported (default) or only used to seed de-duplication. */
    public fun setIncludeActiveOnConnect(include: Boolean) {
        settings.update { it.copy(includeActiveOnConnect = include) }
    }

    public fun setBackgroundObservationOptIn(optIn: Boolean) {
        settings.update { it.copy(backgroundObservationOptIn = optIn) }
    }

    public fun setCueAlertsOptIn(optIn: Boolean) {
        settings.update { it.copy(cueAlertsOptIn = optIn) }
    }

    /**
     * Ends the session: queued work is discarded and the inbox, de-duplication state and coverage counters are cleared.
     * Call it when the app locks. [beginSession] starts the next session.
     */
    public fun stopAndClear() {
        intake.stopAndClear()
        refresh()
    }

    /** Starts a new session after [stopAndClear], for example when the app unlocks. */
    public fun beginSession() {
        if (!environment.isAvailable()) return
        synchronized(connectionLock) {
            intake.beginSession()
            refresh()
            if (listenerConnected) {
                intake.onConnected()
                activeSnapshotProvider?.let { intake.onActiveSnapshot(it) }
            }
        }
    }

    /** Recheck the user's system grant on returning from settings; revoke invalidates held content. */
    public fun refreshAccess() {
        if (!isAccessGranted()) intake.stopAndClear()
        refresh()
    }

    /** The provider returns null when Android could not read a real active snapshot. */
    internal fun onListenerConnected(snapshotProvider: (() -> List<NotificationSource>?)? = null) {
        synchronized(connectionLock) {
            listenerConnected = true
            activeSnapshotProvider = snapshotProvider
            intake.onConnected()
            refresh()
            snapshotProvider?.let { intake.onActiveSnapshot(it) }
        }
    }

    internal fun onListenerDisconnected() {
        synchronized(connectionLock) {
            listenerConnected = false
            activeSnapshotProvider = null
            intake.onDisconnected()
            refresh()
        }
        if (environment.isAvailable() && settings.state.value.enabled && !isAccessGranted()) {
            // Access was revoked in system settings: capture stops and nothing is kept.
            intake.stopAndClear()
            refresh()
        }
    }

    /** Re-reads the platform and settings facts that coverage depends on. */
    private fun refresh() {
        val state = settings.state.value
        tracker.setAvailable(environment.isAvailable())
        tracker.setEnabled(state.enabled)
        tracker.setAccessGranted(isAccessGranted())
        tracker.setPaused(state.paused)
    }

    private fun setComponent(newState: Int) {
        packageManager.setComponentEnabledSetting(component, newState, PackageManager.DONT_KILL_APP)
    }

    public companion object {
        @Volatile
        private var instance: NotificationObservation? = null

        /** The one instance of the process. The system creates the listener service itself, so it must find it here. */
        public fun from(context: Context): NotificationObservation =
            instance ?: synchronized(this) {
                instance ?: create(context.applicationContext).also { instance = it }
            }

        private fun create(context: Context): NotificationObservation =
            NotificationObservation(
                context = context,
                settings = NotificationSettings.forContext(context),
                environment = PlatformEnvironment(context),
                clock = Clock.systemUTC(),
                dispatcher = Dispatchers.Default,
            )
    }
}

/** The real answers from the platform. */
internal class PlatformEnvironment(private val context: Context) : IntakeEnvironment {
    @ChecksSdkIntAtLeast(api = NotificationBounds.MIN_API)
    override fun isAvailable(): Boolean = Build.VERSION.SDK_INT >= NotificationBounds.MIN_API

    override fun isDeviceLocked(): Boolean {
        val keyguard = context.getSystemService(KeyguardManager::class.java) ?: return true
        return try {
            keyguard.isDeviceLocked
        } catch (ignored: RuntimeException) {
            true
        }
    }

    override fun wallMs(): Long = System.currentTimeMillis()

    override fun elapsedRealtimeMs(): Long = SystemClock.elapsedRealtime()
}
