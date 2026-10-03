package org.sakshi.acquisition.accessibility

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

/** Synthetic fixture packaged only in the instrumentation APK. It never opens or reads real chats. */
class SyntheticVisibleTextActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AccessibleCapture.from(this).enable()
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 48, 24, 24)
        }
        val mode = intent.getStringExtra("mode") ?: "ordinary"
        layout.addView(TextView(this).apply {
            text = if (mode == "restricted") "View Once - SYNTHETIC_RESTRICTED" else "SYNTHETIC_VISIBLE_TEXT"
            textSize = 20f
            if (mode == "sensitive" && Build.VERSION.SDK_INT >= 34) {
                setAccessibilityDataSensitive(View.ACCESSIBILITY_DATA_SENSITIVE_YES)
            }
        })
        layout.addView(EditText(this).apply {
            setText("SYNTHETIC_EDITABLE_SECRET")
            isFocusable = false
        })
        layout.addView(EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setText("SYNTHETIC_PASSWORD_SECRET")
            isFocusable = false
        })
        setContentView(layout)
    }
}
