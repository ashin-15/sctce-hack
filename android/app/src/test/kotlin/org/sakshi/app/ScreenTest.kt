package org.sakshi.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import org.sakshi.app.session.SessionState

class ScreenTest {
    @Test
    fun onboardingComesBeforeAnythingElse() {
        assertEquals(Screen.Onboarding, screenFor(acknowledged = false, SessionState.Locked))
        assertEquals(Screen.Onboarding, screenFor(acknowledged = false, SessionState.NoDeviceLock))
    }

    @Test
    fun acknowledgedUsersSeeTheLockScreenForEveryLockedState() {
        listOf(SessionState.Locked, SessionState.Unlocking, SessionState.NoDeviceLock, SessionState.KeyInvalidated).forEach {
            assertEquals(Screen.Lock(it), screenFor(acknowledged = true, it))
        }
        assertIs<Screen.Lock>(screenFor(acknowledged = true, SessionState.Locked))
    }
}
