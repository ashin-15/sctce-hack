package org.sakshi.processing.analysis

/** Synthetic WhatsApp-style exports. Names, dates and words are invented. */
internal object SyntheticExports {
    const val OWNER: String = "synthetic-alex"
    const val OTHER: String = "synthetic-sam"

    /** Eight messages from two senders, one multi-line, one media placeholder, one system line. Day-first. */
    val EIGHT_MESSAGES: String = """
        24/09/2026, 21:03 - $OTHER: hello there
        24/09/2026, 21:05 - $OWNER: hi, please stop sending these
        25/09/2026, 08:15 - $OTHER: you are an idiot
        and this line continues
        on a third line
        25/09/2026, 08:16 - $OTHER: <Media omitted>
        25/09/2026, 08:17 - Messages and calls are end-to-end encrypted.
        25/09/2026, 08:20 - $OTHER: I will hurt you if you reply
        26/09/2026, 09:00 - $OTHER: good morning
        26/09/2026, 09:30 - $OWNER: no more messages
        26/09/2026, 10:00 - $OTHER: ok
    """.trimIndent() + "\n"

    /** Every date has both parts at most twelve, so the order cannot be read from the file. */
    val AMBIGUOUS: String = """
        01/02/2026, 10:00 - $OTHER: first
        02/03/2026, 11:00 - $OWNER: second
    """.trimIndent() + "\n"

    fun large(records: Int): String = buildString {
        for (i in 0 until records) {
            val day = 1 + i % 28
            val minute = i % 60
            val sender = if (i % 3 == 0) OWNER else OTHER
            val body = if (i % 50 == 0) "you are worthless $i" else "synthetic message $i"
            append("%02d/10/2026, 10:%02d - %s: %s\n".format(day, minute, sender, body))
        }
    }
}
