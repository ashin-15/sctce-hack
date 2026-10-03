package org.sakshi.acquisition.notifications

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.TextView

/** Isolated test APK only: requests the normal posting permission for synthetic notifications. */
class SyntheticNotificationPermissionActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(TextView(this).apply {
            text = "Synthetic notification test only. This fixture posts harmless test text."
            textSize = 20f
            setPadding(24, 48, 24, 24)
        })
        NotificationObservation.from(this).enable()
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }
}
