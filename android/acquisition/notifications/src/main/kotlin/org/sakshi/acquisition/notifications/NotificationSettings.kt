package org.sakshi.acquisition.notifications

import android.content.Context
import java.io.File
import java.io.IOException
import java.util.Properties
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The person's choices for the lane. Everything defaults to the safe side: off, not paused, no apps, no processing
 * while the device is locked, and active notifications seeded rather than reported when the listener connects.
 */
public data class NotificationSettingsState(
    val enabled: Boolean = false,
    val paused: Boolean = false,
    val allowlist: PackageAllowlist = PackageAllowlist.EMPTY,
    /** Separate opt-in to keep available previews of notifications that arrive while the device is locked. */
    val lockScreenPreviewsOptIn: Boolean = false,
    /** Report what is already shown when the listener connects. Off: it only seeds de-duplication state. */
    val includeActiveOnConnect: Boolean = false,
)

/**
 * Stores [NotificationSettingsState] in one small properties file. It holds configuration only: booleans and the
 * package names of apps the person chose. It never holds notification text. The file lives under the app's
 * no-backup directory so that the choices are not copied to cloud backups or device transfers. A missing or unreadable
 * file means the defaults, which observe nothing.
 */
public class NotificationSettings internal constructor(private val file: File) {
    private val lock = Any()
    private val flow = MutableStateFlow(load())

    public val state: StateFlow<NotificationSettingsState> = flow.asStateFlow()

    /** Applies [change] to the current state, saves it, and returns the new state. */
    public fun update(change: (NotificationSettingsState) -> NotificationSettingsState): NotificationSettingsState =
        synchronized(lock) {
            val next = change(flow.value)
            save(next)
            flow.value = next
            next
        }

    private fun load(): NotificationSettingsState {
        if (!file.isFile) return NotificationSettingsState()
        val properties = Properties()
        try {
            file.inputStream().use { properties.load(it) }
        } catch (ignored: IOException) {
            return NotificationSettingsState()
        }
        val names = properties.getProperty(KEY_ALLOWLIST).orEmpty().split(SEPARATOR).filter(PackageAllowlist::isValidName)
        return NotificationSettingsState(
            enabled = properties.getProperty(KEY_ENABLED).toBoolean(),
            paused = properties.getProperty(KEY_PAUSED).toBoolean(),
            allowlist = PackageAllowlist.of(names),
            lockScreenPreviewsOptIn = properties.getProperty(KEY_LOCK_OPT_IN).toBoolean(),
            includeActiveOnConnect = properties.getProperty(KEY_INCLUDE_ACTIVE).toBoolean(),
        )
    }

    private fun save(value: NotificationSettingsState) {
        val properties = Properties()
        properties.setProperty(KEY_ENABLED, value.enabled.toString())
        properties.setProperty(KEY_PAUSED, value.paused.toString())
        properties.setProperty(KEY_LOCK_OPT_IN, value.lockScreenPreviewsOptIn.toString())
        properties.setProperty(KEY_INCLUDE_ACTIVE, value.includeActiveOnConnect.toString())
        properties.setProperty(KEY_ALLOWLIST, value.allowlist.names.sorted().joinToString(SEPARATOR))
        file.parentFile?.mkdirs()
        val temporary = File(file.parentFile, file.name + ".tmp")
        temporary.outputStream().use { properties.store(it, null) }
        if (!temporary.renameTo(file)) {
            file.delete()
            check(temporary.renameTo(file)) { "Could not save notification settings" }
        }
    }

    public companion object {
        private const val FILE_NAME = "notification-lane-settings.properties"
        private const val SEPARATOR = ","
        private const val KEY_ENABLED = "enabled"
        private const val KEY_PAUSED = "paused"
        private const val KEY_LOCK_OPT_IN = "lock_screen_previews_opt_in"
        private const val KEY_INCLUDE_ACTIVE = "include_active_on_connect"
        private const val KEY_ALLOWLIST = "allowlist"

        /** Settings kept in the app's no-backup directory. */
        public fun forContext(context: Context): NotificationSettings =
            NotificationSettings(File(context.noBackupFilesDir, FILE_NAME))
    }
}
