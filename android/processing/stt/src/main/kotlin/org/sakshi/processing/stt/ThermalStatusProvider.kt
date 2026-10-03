package org.sakshi.processing.stt

import android.content.Context
import android.os.Build
import android.os.PowerManager

/** Reports the phone's thermal status as a `PowerManager.THERMAL_STATUS_*` value. */
public fun interface ThermalStatusProvider {
    public fun status(): Int

    public companion object {
        /** `PowerManager.THERMAL_STATUS_SEVERE`: work is refused at or above this status. */
        public const val SEVERE: Int = 3

        private const val NONE: Int = 0

        /** The system status on Android 10 and later; earlier releases have no such API and report none. */
        public fun system(context: Context): ThermalStatusProvider {
            val power = context.applicationContext.getSystemService(PowerManager::class.java)
            return ThermalStatusProvider {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && power != null) power.currentThermalStatus else NONE
            }
        }
    }
}
