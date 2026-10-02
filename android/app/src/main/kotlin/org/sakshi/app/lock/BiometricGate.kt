package org.sakshi.app.lock

import android.os.Build
import androidx.biometric.BiometricManager.Authenticators
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import org.sakshi.app.R

/**
 * Asks the user to authenticate with a biometric or the screen lock. Create it in `onCreate`, before the
 * activity starts, because [BiometricPrompt] registers lifecycle observers.
 */
class BiometricGate(
    private val activity: FragmentActivity,
    private val onAuthenticated: () -> Unit,
    private val onNotCompleted: () -> Unit,
) {
    private val prompt = BiometricPrompt(
        activity,
        ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onAuthenticated()

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onNotCompleted()
        },
    )

    fun authenticate() {
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(activity.getString(R.string.prompt_title))
            .setSubtitle(activity.getString(R.string.prompt_subtitle))
            .setAllowedAuthenticators(allowedAuthenticators())
            .build()
        prompt.authenticate(info)
    }

    internal companion object {
        /**
         * Biometric 1.1.0 rejects BIOMETRIC_STRONG with DEVICE_CREDENTIAL on API 28 and 29, where the library
         * accepts BIOMETRIC_WEAK with DEVICE_CREDENTIAL instead.
         */
        fun allowedAuthenticators(sdk: Int = Build.VERSION.SDK_INT): Int =
            if (sdk >= Build.VERSION_CODES.R) {
                Authenticators.BIOMETRIC_STRONG or Authenticators.DEVICE_CREDENTIAL
            } else {
                Authenticators.BIOMETRIC_WEAK or Authenticators.DEVICE_CREDENTIAL
            }
    }
}
