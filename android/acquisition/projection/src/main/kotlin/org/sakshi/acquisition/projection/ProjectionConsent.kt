package org.sakshi.acquisition.projection

import android.content.Intent

/**
 * Lifecycle state of a MediaProjection session token.
 */
public enum class TokenLifecycleState {
    /** Token acquired from system consent activity, not yet attached to a VirtualDisplay. */
    PENDING,

    /** Active session currently attached to a live MediaProjection instance. */
    ACTIVE,

    /** Session stopped gracefully by the user or application. */
    STOPPED,

    /** Session revoked by the OS (e.g., status bar chip stopped, screen lock, or target app switch). */
    REVOKED,
}

/**
 * Thrown when attempting to reuse a single-use projection token.
 */
public class TokenAlreadyConsumedException(message: String) : IllegalStateException(message)

/**
 * Thrown when attempting operations on a revoked projection session.
 */
public class ProjectionRevokedException(message: String) : IllegalStateException(message)

/**
 * Thread-safe wrapper around the MediaProjection intent token and result code.
 *
 * Android 14+ (API 34+) enforces that screen capture intent tokens are single-use.
 * This class ensures that tokens are not accidentally reused or left unclosed.
 */
public class ProjectionToken(
    public val resultCode: Int,
    public val resultData: Intent,
) {
    private val lock: Any = Any()

    public var state: TokenLifecycleState = TokenLifecycleState.PENDING
        private set

    /**
     * True if this token can still be used to initialize a new MediaProjection instance.
     */
    public val isUsable: Boolean
        get() = synchronized(lock) { state == TokenLifecycleState.PENDING }

    /**
     * Marks the token as active upon successful MediaProjection initialization.
     * Throws [TokenAlreadyConsumedException] if called more than once.
     */
    public fun consume(): Unit = synchronized(lock) {
        if (state != TokenLifecycleState.PENDING) {
            throw TokenAlreadyConsumedException(
                "MediaProjection token has already been consumed or closed (state=$state). Android 14+ tokens are single-use."
            )
        }
        state = TokenLifecycleState.ACTIVE
    }

    /**
     * Marks the session as stopped cleanly.
     */
    public fun markStopped(): Unit = synchronized(lock) {
        if (state == TokenLifecycleState.ACTIVE || state == TokenLifecycleState.PENDING) {
            state = TokenLifecycleState.STOPPED
        }
    }

    /**
     * Marks the session as revoked by the Android OS.
     */
    public fun markRevoked(): Unit = synchronized(lock) {
        state = TokenLifecycleState.REVOKED
    }
}
