package org.sakshi.app.onboarding

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/** Remembers that the user has read the limits screen. Holds no evidence or case data. */
interface OnboardingStore {
    fun isAcknowledged(): Boolean

    fun acknowledge()
}

class SharedPreferencesOnboardingStore(private val preferences: SharedPreferences) : OnboardingStore {
    override fun isAcknowledged(): Boolean = preferences.getBoolean(KEY_ACKNOWLEDGED, false)

    override fun acknowledge() {
        preferences.edit { putBoolean(KEY_ACKNOWLEDGED, true) }
    }

    companion object {
        private const val FILE = "onboarding"
        private const val KEY_ACKNOWLEDGED = "acknowledged"

        fun create(context: Context): SharedPreferencesOnboardingStore =
            SharedPreferencesOnboardingStore(context.getSharedPreferences(FILE, Context.MODE_PRIVATE))
    }
}
