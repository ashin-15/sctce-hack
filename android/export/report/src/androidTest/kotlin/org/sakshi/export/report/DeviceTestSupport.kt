package org.sakshi.export.report

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Bundle
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import org.junit.After
import org.sakshi.core.crypto.KeyWrapper
import org.sakshi.core.vault.KeystoreKeyWrapper
import org.sakshi.core.vault.Vault

/** Log tag whose output is the only thing read back from the device. Values are numbers, booleans and names. */
const val BENCH_TAG: String = "SakshiBench"

/** Writes the entries to logcat under [BENCH_TAG] and to the instrumentation status stream, so they reach the Gradle report. */
fun record(test: String, vararg entries: Pair<String, Any>) {
    val bundle = Bundle()
    bundle.putString("test", test)
    entries.forEach { bundle.putString(it.first, it.second.toString()) }
    Log.i(BENCH_TAG, entries.joinToString(" ", prefix = "$test ") { "${it.first}=${it.second}" })
    InstrumentationRegistry.getInstrumentation().sendStatus(0, bundle)
}

/** The device's own state: model, API level, battery and thermal status. No user data. */
fun deviceState(context: Context): Array<Pair<String, Any>> {
    val battery: Intent? = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
    val status = battery?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
    val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
    return arrayOf(
        "model" to Build.MODEL,
        "sdk" to Build.VERSION.SDK_INT,
        "battery_pct" to if (level >= 0 && scale > 0) level * 100 / scale else -1,
        "charging" to charging,
        "thermal_status" to context.getSystemService(PowerManager::class.java).currentThermalStatus,
    )
}

/** Owns what a device test creates and removes it afterwards: Keystore aliases, vaults and scratch files. */
abstract class DeviceTestBase {
    protected val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val wrappers = mutableListOf<KeystoreKeyWrapper>()
    private val signers = mutableListOf<KeystoreManifestSigner>()
    private val vaults = mutableListOf<Vault>()
    private val scratch = mutableListOf<File>()

    protected fun newWrapper(): KeystoreKeyWrapper =
        KeystoreKeyWrapper(alias = "synthetic-report-vault-${UUID.randomUUID()}", requireUserAuthentication = false)
            .also { wrappers += it }

    protected fun newSigner(alias: String = "synthetic-report-signer-${UUID.randomUUID()}"): KeystoreManifestSigner =
        KeystoreManifestSigner(alias).also { signers += it }

    protected fun openVault(wrapper: KeyWrapper): Vault = Vault.open(context, wrapper).also { vaults += it }

    protected fun scratchDirectory(): File {
        val directory = File(context.noBackupFilesDir, "synthetic-scratch-${UUID.randomUUID()}")
        check(directory.mkdirs()) { "Scratch directory was not created" }
        scratch += directory
        return directory
    }

    @After
    fun cleanUp() {
        vaults.forEach { runCatching { it.close() } }
        vaults.clear()
        wrappers.forEach { runCatching { it.delete() } }
        signers.forEach { runCatching { it.delete() } }
        wrappers.clear()
        signers.clear()
        File(context.noBackupFilesDir, "vault").deleteRecursively()
        File(context.cacheDir, "exports").deleteRecursively()
        scratch.forEach { it.deleteRecursively() }
        scratch.clear()
    }
}
