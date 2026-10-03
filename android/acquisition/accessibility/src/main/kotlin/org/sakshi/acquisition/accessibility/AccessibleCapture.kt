package org.sakshi.acquisition.accessibility

import android.content.ComponentName
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.accessibilityservice.AccessibilityServiceInfo
import kotlinx.coroutines.flow.StateFlow

/** Explicit, bounded, memory-only capture independent of the encrypted vault. */
public class AccessibleCapture private constructor(context: Context) {
    internal val session: CaptureSession = CaptureSession()
    private val component = ComponentName(context, SakshiVisibleTextService::class.java)
    private val packageManager = context.packageManager
    private val accessibilityManager = context.getSystemService(AccessibilityManager::class.java)
    private val keyguardManager = context.getSystemService(KeyguardManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val expiry = Runnable { session.stop("Capture expired after five minutes. Review held snapshots.") }

    public val status: StateFlow<AccessibleCaptureStatus> get() = session.status
    public val candidates: StateFlow<List<AccessibleTextCandidate>> get() = session.candidates

    /** Call only after disclosure and explicit consent. This does not grant Android access. */
    public fun enable() {
        packageManager.setComponentEnabledSetting(component, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
    }

    public fun isAccessGranted(): Boolean {
        val manager = accessibilityManager ?: return false
        return manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK).any {
            ComponentName(it.resolveInfo.serviceInfo.packageName, it.resolveInfo.serviceInfo.name) == component
        }
    }

    public fun settingsIntent(): Intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)

    /** Session may continue while the vault locks on leaving Sakshi. Screen lock always clears it. */
    public fun startSession(packages: Set<String>): Boolean {
        if (!isAccessGranted()) return false
        val keyguard = keyguardManager ?: return false
        if (keyguard.isKeyguardLocked || keyguard.isDeviceLocked) { stopAndClear(); return false }
        val started = session.start(packages, SystemClock.elapsedRealtime())
        if (started) {
            handler.removeCallbacks(expiry)
            handler.postDelayed(expiry, CaptureSession.MAX_SESSION_MS)
        }
        return started
    }

    public fun stopSession() { handler.removeCallbacks(expiry); session.stop() }
    public fun stopAndClear() { handler.removeCallbacks(expiry); session.clear() }
    public fun dismiss(id: String) { session.dismiss(id) }

    public fun revoke() {
        stopAndClear()
        packageManager.setComponentEnabledSetting(component, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
    }

    public companion object {
        /** Generic visible-text probes only. None has a validated message parser or complete coverage guarantee. */
        public val SUPPORTED_PACKAGES: Set<String> = CaptureSession.KNOWN_CHAT_PACKAGES
        @Volatile private var instance: AccessibleCapture? = null
        public fun from(context: Context): AccessibleCapture = instance ?: synchronized(this) {
            instance ?: AccessibleCapture(context.applicationContext).also { instance = it }
        }
    }
}
