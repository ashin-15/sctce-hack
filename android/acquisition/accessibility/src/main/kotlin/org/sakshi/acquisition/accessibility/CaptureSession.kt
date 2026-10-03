package org.sakshi.acquisition.accessibility

import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A visible text snapshot, not a parsed message or authenticated original. */
public data class AccessibleTextCandidate(
    val id: String,
    val text: String,
    val packageName: String,
    val observedWallMs: Long,
    val collectorElapsedRealtimeMs: Long,
    val collectorSessionId: String,
    val sender: String? = null,
    val direction: String = "UNKNOWN",
    val messageBoundaries: String = "UNKNOWN",
)

public data class AccessibleCaptureStatus(
    val connected: Boolean = false,
    val active: Boolean = false,
    val expiresAtElapsedMs: Long? = null,
    val allowlist: Set<String> = emptySet(),
    val notice: String = "Capture is off. Coverage is unknown.",
)

/** Pure session policy. Text and deduplication exist only in memory; every session begins empty. */
internal class CaptureSession {
    private val statusFlow = MutableStateFlow(AccessibleCaptureStatus())
    private val candidateFlow = MutableStateFlow<List<AccessibleTextCandidate>>(emptyList())
    val status: StateFlow<AccessibleCaptureStatus> = statusFlow.asStateFlow()
    val candidates: StateFlow<List<AccessibleTextCandidate>> = candidateFlow.asStateFlow()
    private var sessionId = ""
    private val seen = LinkedHashSet<Pair<String, String>>()

    @Synchronized fun connection(connected: Boolean) {
        if (!connected) clear()
        statusFlow.value = statusFlow.value.copy(connected = connected)
    }

    @Synchronized fun start(packages: Set<String>, elapsedMs: Long): Boolean {
        if (!statusFlow.value.connected || packages.isEmpty() || !KNOWN_CHAT_PACKAGES.containsAll(packages)) return false
        clear()
        sessionId = UUID.randomUUID().toString()
        statusFlow.value = statusFlow.value.copy(
            active = true, allowlist = packages.toSet(), expiresAtElapsedMs = elapsedMs + MAX_SESSION_MS,
            notice = "Temporary visible text capture is active. Sender, direction, and message boundaries are unknown.",
        )
        return true
    }

    @Synchronized fun admits(packageName: String, elapsedMs: Long): Boolean {
        val state = statusFlow.value
        if (state.active && elapsedMs >= (state.expiresAtElapsedMs ?: 0L)) stop("Capture expired. Review the held snapshots.")
        return statusFlow.value.active && packageName in statusFlow.value.allowlist
    }

    @Synchronized fun observe(packageName: String, lines: List<String>, wallMs: Long, elapsedMs: Long) {
        if (!admits(packageName, elapsedMs)) return
        if (lines.any(::hasRestrictedMarker)) {
            clear()
            notice("Capture stopped: possible View Once or disappearing-content screen. Nothing from this screen was kept.")
            return
        }
        val text = lines.filter(String::isNotBlank).joinToString("\n").take(MAX_TEXT_CHARS)
        if (text.isBlank()) {
            notice("No readable visible text was exposed. Coverage is unknown.")
            return
        }
        if (!seen.add(packageName to text)) return
        if (candidateFlow.value.size >= MAX_CANDIDATES) {
            stop("Capture stopped at the memory limit. Review or discard snapshots.")
            return
        }
        candidateFlow.value = candidateFlow.value + AccessibleTextCandidate(
            UUID.randomUUID().toString(), text, packageName, wallMs, elapsedMs, sessionId,
        )
    }

    @Synchronized fun notice(message: String) { statusFlow.value = statusFlow.value.copy(notice = message) }

    @Synchronized fun stop(message: String = "Capture stopped. Review the held snapshots.") {
        statusFlow.value = statusFlow.value.copy(active = false, allowlist = emptySet(), expiresAtElapsedMs = null, notice = message)
    }

    @Synchronized fun clear() {
        stop("Capture is off. Held snapshots were cleared.")
        seen.clear()
        candidateFlow.value = emptyList()
        sessionId = ""
    }

    @Synchronized fun dismiss(id: String) { candidateFlow.value = candidateFlow.value.filterNot { it.id == id } }

    companion object {
        const val MAX_SESSION_MS: Long = 5 * 60 * 1000L
        const val MAX_TEXT_CHARS: Int = 8_192
        const val MAX_CANDIDATES: Int = 40
        val KNOWN_CHAT_PACKAGES: Set<String> = setOf(
            "com.whatsapp", "com.whatsapp.w4b", "com.instagram.android", "org.telegram.messenger", "org.thoughtcrime.securesms",
        ) + if (BuildConfig.DEBUG) setOf("org.sakshi.acquisition.accessibility.test") else emptySet()
        // Conservative heuristic only; no API exposes a reliable cross-app ephemeral-content flag.
        private val restrictedMarkers = listOf(
            "view once", "view-once", "disappearing", "self-destruct", "self destruct", "secret chat",
            "ephemeral", "vanish mode", "एक बार देखें", "ഒരിക്കൽ മാത്രം",
        )
        fun isRestricted(line: String): Boolean = restrictedMarkers.any { line.contains(it, ignoreCase = true) }
        private fun hasRestrictedMarker(line: String): Boolean = isRestricted(line)
    }
}
