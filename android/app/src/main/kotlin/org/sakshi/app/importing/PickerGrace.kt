package org.sakshi.app.importing

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The system pickers cover the app, which would normally lock it as soon as it stops. While a picker that the
 * user opened is showing, a stop is tolerated for [window]; after that [onExpired] locks the session.
 * Call every method from the main thread.
 */
class PickerGrace(
    private val scope: CoroutineScope,
    private val onExpired: () -> Unit,
    private val window: Duration = 2.minutes,
) {
    private var open = false
    private var timer: Job? = null

    /** Call just before launching a picker. */
    fun begin() {
        open = true
    }

    /** Call when the picker returned, with or without a result. */
    fun end() {
        open = false
        timer?.cancel()
        timer = null
    }

    /** Call when the activity stops. Returns true if the stop is covered, in which case the session is not locked yet. */
    fun coverStop(): Boolean {
        if (!open) return false
        timer?.cancel()
        timer = scope.launch {
            delay(window)
            open = false
            timer = null
            onExpired()
        }
        return true
    }

    /** Call when the activity starts again. */
    fun onStarted() {
        timer?.cancel()
        timer = null
    }
}
