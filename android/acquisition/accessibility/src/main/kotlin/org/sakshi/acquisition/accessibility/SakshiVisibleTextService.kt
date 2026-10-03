package org.sakshi.acquisition.accessibility

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.content.ContextCompat

/** Reads only framework-exposed visible text. Never performs actions, screenshots, gestures or input. */
public class SakshiVisibleTextService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var capture: AccessibleCapture
    private var registered = false
    private var pendingPackage: String? = null
    private var readScheduled = false
    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                handler.removeCallbacks(readVisible)
                readScheduled = false
                capture.stopAndClear()
            }
        }
    }
    private val readVisible = Runnable { readScheduled = false; readCurrentWindow() }

    override fun onServiceConnected() {
        capture = AccessibleCapture.from(this)
        capture.session.connection(true)
        if (!registered) {
            ContextCompat.registerReceiver(this, screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF), ContextCompat.RECEIVER_NOT_EXPORTED)
            registered = true
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!::capture.isInitialized || event == null) return
        if (isLocked()) { capture.stopAndClear(); return }
        val packageName = event.packageName?.toString() ?: return
        if (!capture.session.admits(packageName, SystemClock.elapsedRealtime())) return
        if (event.eventType !in setOf(
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED, AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
                AccessibilityEvent.TYPE_VIEW_SCROLLED,
            )) return
        pendingPackage = packageName
        if (!readScheduled) {
            readScheduled = true
            handler.postDelayed(readVisible, 750L)
        }
    }

    private fun readCurrentWindow() {
        if (isLocked()) { capture.stopAndClear(); return }
        val packageName = pendingPackage ?: return
        if (!capture.session.admits(packageName, SystemClock.elapsedRealtime())) return
        val root = rootInActiveWindow
        if (root == null) {
            capture.session.notice("The app exposed no accessible window. Coverage is unknown.")
            return
        }
        if (root.packageName?.toString() != packageName) return
        val lines = mutableListOf<String>()
        val queue = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
        queue.add(root to 0)
        var nodes = 0
        var chars = 0
        var truncated = false
        while (queue.isNotEmpty() && nodes < MAX_NODES) {
            val (node, depth) = queue.removeFirst()
            nodes++
            if (!node.isVisibleToUser || node.packageName?.toString()?.let { it != packageName } == true) continue
            if (Build.VERSION.SDK_INT >= 34 && node.isAccessibilityDataSensitive) {
                capture.stopAndClear()
                capture.session.notice("Capture stopped: the app marked visible content sensitive.")
                return
            }
            if (node.isPassword || node.isEditable) continue
            if ((node.text?.length ?: 0) > MAX_NODE_CHARS || (node.contentDescription?.length ?: 0) > MAX_NODE_CHARS) {
                truncated = true
                break
            }
            val value = node.text?.take(MAX_NODE_CHARS)?.toString()?.trim().orEmpty()
            // Content descriptions are only checked for restrictive modes, not collected as message text.
            val description = node.contentDescription?.take(MAX_NODE_CHARS)?.toString().orEmpty()
            if (CaptureSession.isRestricted(value) || CaptureSession.isRestricted(description)) {
                capture.stopAndClear()
                capture.session.notice("Capture stopped: possible View Once or disappearing-content screen.")
                return
            }
            if (value.isNotBlank()) {
                if (chars + value.length > CaptureSession.MAX_TEXT_CHARS) { truncated = true; break }
                lines += value
                chars += value.length + 1
            }
            if (depth < MAX_DEPTH) {
                val childLimit = minOf(node.childCount, MAX_NODES - nodes - queue.size)
                if (childLimit < node.childCount) truncated = true
                for (index in 0 until childLimit) {
                    node.getChild(index)?.let { queue.add(it to depth + 1) }
                }
            } else if (node.childCount > 0) truncated = true
        }
        if (queue.isNotEmpty()) truncated = true
        if (truncated) {
            capture.session.notice("Visible window exceeded capture limits. No snapshot retained; coverage is unknown.")
            return
        }
        capture.session.observe(packageName, lines, System.currentTimeMillis(), SystemClock.elapsedRealtime())
    }

    private fun isLocked(): Boolean = getSystemService(KeyguardManager::class.java)?.let {
        it.isKeyguardLocked || it.isDeviceLocked
    } ?: true

    override fun onInterrupt() {
        if (::capture.isInitialized) capture.stopAndClear()
    }

    override fun onDestroy() {
        handler.removeCallbacks(readVisible)
        if (registered) unregisterReceiver(screenOff)
        if (::capture.isInitialized) capture.session.connection(false)
        super.onDestroy()
    }

    private companion object {
        const val MAX_NODES = 300
        const val MAX_DEPTH = 20
        const val MAX_NODE_CHARS = 2_048
    }
}
