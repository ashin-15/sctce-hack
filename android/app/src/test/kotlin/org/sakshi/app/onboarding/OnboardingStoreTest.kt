package org.sakshi.app.onboarding

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OnboardingStoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun defaultsToNotAcknowledged() {
        assertFalse(SharedPreferencesOnboardingStore.create(context).isAcknowledged())
    }

    @Test
    fun persistsAcknowledgement() {
        SharedPreferencesOnboardingStore.create(context).acknowledge()
        assertTrue(SharedPreferencesOnboardingStore.create(context).isAcknowledged())
    }
}
