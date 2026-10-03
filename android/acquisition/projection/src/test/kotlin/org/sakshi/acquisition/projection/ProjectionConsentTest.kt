package org.sakshi.acquisition.projection

import android.app.Activity
import android.content.Intent
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ProjectionConsentTest {

    @Test
    fun `token transitions through lifecycle and enforces single use`() {
        val intent = Intent()
        val token = ProjectionToken(Activity.RESULT_OK, intent)

        assertEquals(TokenLifecycleState.PENDING, token.state)
        assertTrue(token.isUsable)

        // First consumption succeeds
        token.consume()
        assertEquals(TokenLifecycleState.ACTIVE, token.state)
        assertFalse(token.isUsable)

        // Second consumption fails (Android 14+ single-use policy enforcement)
        val ex = assertFailsWith<TokenAlreadyConsumedException> {
            token.consume()
        }
        assertTrue(ex.message!!.contains("single-use"))

        // Stop session cleanly
        token.markStopped()
        assertEquals(TokenLifecycleState.STOPPED, token.state)
    }

    @Test
    fun `token records OS revocation`() {
        val intent = Intent()
        val token = ProjectionToken(Activity.RESULT_OK, intent)
        token.consume()

        token.markRevoked()
        assertEquals(TokenLifecycleState.REVOKED, token.state)
        assertFalse(token.isUsable)
    }
}
