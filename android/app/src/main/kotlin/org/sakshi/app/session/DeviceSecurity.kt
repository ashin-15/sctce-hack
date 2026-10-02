package org.sakshi.app.session

import android.app.KeyguardManager
import android.content.Context

interface DeviceSecurity {
    /** True when the phone has a PIN, pattern, password or similar secure lock screen. */
    fun isDeviceSecure(): Boolean
}

class AndroidDeviceSecurity(context: Context) : DeviceSecurity {
    private val keyguard = context.applicationContext.getSystemService(KeyguardManager::class.java)

    override fun isDeviceSecure(): Boolean = keyguard.isDeviceSecure
}
