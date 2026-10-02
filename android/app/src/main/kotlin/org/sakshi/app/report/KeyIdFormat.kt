package org.sakshi.app.report

/** Shows the signing key id in groups so that two people can read it to each other and compare. */
object KeyIdFormat {
    const val GROUP_SIZE: Int = 4

    fun grouped(keyId: String): String = keyId.chunked(GROUP_SIZE).joinToString(" ")
}
