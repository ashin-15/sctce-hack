package org.sakshi.core.vault

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.InputStream
import java.io.RandomAccessFile
import java.util.UUID
import org.junit.After
import org.sakshi.core.crypto.KeyWrapper

/** Log tag whose output is the only thing read back from the device. Values are numbers, booleans and class names. */
const val BENCH_TAG: String = "SakshiBench"

/** Synthetic plaintext that must never appear in any stored file. */
const val DEVICE_MARKER: String = "SYNTHETIC-PLAINTEXT-MARKER-7f3a"

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
    val thermal = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        context.getSystemService(PowerManager::class.java).currentThermalStatus
    } else {
        -1
    }
    return arrayOf(
        "model" to Build.MODEL,
        "sdk" to Build.VERSION.SDK_INT,
        "soc" to soc,
        "battery_pct" to if (level >= 0 && scale > 0) level * 100 / scale else -1,
        "charging" to charging,
        "thermal_status" to thermal,
    )
}

fun containsBytes(haystack: ByteArray, needle: ByteArray): Boolean {
    if (needle.isEmpty() || haystack.size < needle.size) return false
    val first = needle[0]
    var start = 0
    val last = haystack.size - needle.size
    while (start <= last) {
        if (haystack[start] == first) {
            var matched = 1
            while (matched < needle.size && haystack[start + matched] == needle[matched]) matched++
            if (matched == needle.size) return true
        }
        start++
    }
    return false
}

/** Flips every bit of the byte at [position] in [file]. */
fun flipByte(file: File, position: Long) {
    RandomAccessFile(file, "rw").use { raf ->
        raf.seek(position)
        val original = raf.read()
        check(original >= 0) { "Position is beyond the end of the file" }
        raf.seek(position)
        raf.write(original xor 0xFF)
    }
}

/** Deterministic synthetic bytes produced on the fly; only one 64 KiB block exists in memory. */
class SyntheticStream(private val length: Long, seed: Long = 1L) : InputStream() {
    private val block = ByteArray(BLOCK).also { bytes ->
        var state = seed or 1L
        for (i in bytes.indices) {
            state = state xor (state shl 13)
            state = state xor (state ushr 7)
            state = state xor (state shl 17)
            bytes[i] = state.toByte()
        }
    }
    private var position = 0L

    override fun read(): Int {
        if (position >= length) return -1
        return block[(position++ % BLOCK).toInt()].toInt() and 0xFF
    }

    override fun read(buffer: ByteArray, offset: Int, count: Int): Int {
        if (position >= length) return -1
        val total = minOf(count.toLong(), length - position).toInt()
        var copied = 0
        while (copied < total) {
            val inBlock = (position % BLOCK).toInt()
            val n = minOf(total - copied, BLOCK - inBlock)
            System.arraycopy(block, inBlock, buffer, offset + copied, n)
            position += n
            copied += n
        }
        return copied
    }

    private companion object {
        const val BLOCK = 65536
    }
}

fun importRequest(caseId: String, maxBytes: Long): ImportRequest = ImportRequest(
    caseId = caseId,
    acquisitionKind = AcquisitionKind.SHARED_STREAM,
    accessClass = AccessClass.USER_MEDIATED,
    importerMechanism = "synthetic-device-test",
    declaredMime = "application/octet-stream",
    claimedOrigin = "synthetic-origin",
    displayNameClaim = "synthetic-name.bin",
    uriAuthorityClaim = "synthetic.authority",
    maxPlaintextBytes = maxBytes,
)

/**
 * Owns everything a device test creates and removes it afterwards: Keystore aliases, open vaults and the
 * vault and scratch directories. Wrappers never require user authentication, so tests run unattended.
 */
abstract class DeviceTestBase {
    protected val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val wrappers = mutableListOf<KeystoreKeyWrapper>()
    private val vaults = mutableListOf<Vault>()
    private val scratchDirectories = mutableListOf<File>()

    protected val vaultDirectory: File get() = File(context.noBackupFilesDir, "vault")

    protected fun newWrapper(requireUserAuthentication: Boolean = false): KeystoreKeyWrapper {
        val wrapper = KeystoreKeyWrapper(
            alias = "synthetic-vault-test-${UUID.randomUUID()}",
            requireUserAuthentication = requireUserAuthentication,
        )
        wrappers += wrapper
        return wrapper
    }

    protected fun openVault(wrapper: KeyWrapper): Vault = Vault.open(context, wrapper).also { vaults += it }

    protected fun closeVault(vault: Vault) {
        vaults.remove(vault)
        vault.close()
    }

    protected fun scratchDirectory(): File {
        val directory = File(context.noBackupFilesDir, "synthetic-scratch-${UUID.randomUUID()}")
        check(directory.mkdirs()) { "Scratch directory was not created" }
        scratchDirectories += directory
        return directory
    }

    /** Every file of the vault directory, including database side files. */
    protected fun vaultFiles(): List<File> = vaultDirectory.walkTopDown().filter { it.isFile }.toList()

    @After
    fun cleanUp() {
        vaults.forEach { runCatching { it.close() } }
        vaults.clear()
        wrappers.forEach { runCatching { it.delete() } }
        wrappers.clear()
        vaultDirectory.deleteRecursively()
        scratchDirectories.forEach { it.deleteRecursively() }
        scratchDirectories.clear()
    }
}
