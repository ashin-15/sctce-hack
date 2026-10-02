package org.sakshi.core.crypto

import java.io.IOException

/** Thrown when a blob is malformed, has been modified, or does not match the supplied key or identifier. */
public class BlobIntegrityException(message: String, cause: Throwable? = null) : IOException(message, cause)

/** Thrown when the plaintext exceeds the permitted size or the maximum number of chunks. */
public class BlobTooLargeException(message: String) : IOException(message)
