package org.sakshi.core.vault

import java.security.GeneralSecurityException

/** Why the master key could not be used. Messages never contain key material or aliases. */
public sealed class VaultKeyException(message: String, cause: Throwable?) : GeneralSecurityException(message, cause) {
    /** The key needs a recent user authentication (biometric or device credential). */
    public class NotAuthenticated(cause: Throwable?) : VaultKeyException("User authentication is required", cause)

    /** The key was permanently invalidated, for example by a change of lock screen or biometrics. */
    public class Invalidated(cause: Throwable?) : VaultKeyException("The master key is no longer valid", cause)

    /** The Keystore failed or the key is missing. */
    public class Unavailable(cause: Throwable?) : VaultKeyException("The master key is unavailable", cause)
}
