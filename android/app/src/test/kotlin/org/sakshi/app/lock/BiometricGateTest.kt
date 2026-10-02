package org.sakshi.app.lock

import androidx.biometric.BiometricManager.Authenticators
import kotlin.test.Test
import kotlin.test.assertEquals

class BiometricGateTest {
    @Test
    fun strongCredentialCombinationOnlyWhereTheLibrarySupportsIt() {
        val strong = Authenticators.BIOMETRIC_STRONG or Authenticators.DEVICE_CREDENTIAL
        val weak = Authenticators.BIOMETRIC_WEAK or Authenticators.DEVICE_CREDENTIAL
        assertEquals(weak, BiometricGate.allowedAuthenticators(26))
        assertEquals(weak, BiometricGate.allowedAuthenticators(29))
        assertEquals(strong, BiometricGate.allowedAuthenticators(30))
        assertEquals(strong, BiometricGate.allowedAuthenticators(36))
    }
}
