package org.sakshi.processing.analysis

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry

/** Log tag whose output is read back from the device. Values are numbers, booleans and class names only. */
const val BENCH_TAG: String = "SakshiBench"

/** Writes the entries to logcat and to the instrumentation status stream, so they reach the Gradle test report. */
fun record(test: String, vararg entries: Pair<String, Any>) {
    val bundle = Bundle()
    bundle.putString("test", test)
    val line = StringBuilder(test)
    for ((key, value) in entries) {
        when (value) {
            is Double -> bundle.putDouble(key, value)
            is Long -> bundle.putLong(key, value)
            is Int -> bundle.putInt(key, value)
            is Boolean -> bundle.putBoolean(key, value)
            else -> bundle.putString(key, value.toString())
        }
        line.append(' ').append(key).append('=').append(value)
    }
    Log.i(BENCH_TAG, line.toString())
    InstrumentationRegistry.getInstrumentation().sendStatus(0, bundle)
}

/** The device's own state: model, API level, SoC, battery and thermal status. No user data. */
fun deviceState(context: Context): Array<Pair<String, Any>> {
    val battery: Intent? = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
    val status = battery?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
    val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
    val soc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MODEL else "unavailable"
    val thermal = context.getSystemService(PowerManager::class.java).currentThermalStatus
    return arrayOf(
        "model" to Build.MODEL,
        "sdk" to Build.VERSION.SDK_INT,
        "soc" to soc,
        "battery_pct" to if (level >= 0 && scale > 0) level * 100 / scale else -1,
        "charging" to charging,
        "thermal_status" to thermal,
    )
}
