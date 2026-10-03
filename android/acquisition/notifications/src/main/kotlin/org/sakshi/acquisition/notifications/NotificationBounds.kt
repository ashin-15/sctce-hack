package org.sakshi.acquisition.notifications

/**
 * Every numeric bound of the notification lane in one place. The values are proposed starting values from the
 * notification report (sections 5.2, 9.1 and 11) and megaplan 13.2. They are Sakshi limits, not Android limits, and
 * none of them is measured on a device.
 */
public object NotificationBounds {
    /** Capacity of the one channel between the callback and the consumer (megaplan 13.2: 64 snapshots). */
    public const val QUEUE_CAPACITY: Int = 64

    /** Newest current messages kept from one MessagingStyle notification (report section 11). */
    public const val MAX_MESSAGES: Int = 25

    /** Newest historic messages kept from one MessagingStyle notification (report section 11). */
    public const val MAX_HISTORIC_MESSAGES: Int = 25

    /** UTF-16 code units kept from one text field (report section 11). */
    public const val MAX_FIELD_CHARS: Int = 2_048

    /** UTF-16 code units kept from all text fields of one snapshot together (report section 11). */
    public const val MAX_TOTAL_TEXT_CHARS: Int = 8_192

    /** Records in the candidate inbox (megaplan 13.2). The same bound is used for the session inbox. */
    public const val INBOX_MAX_RECORDS: Int = 100

    /** Approximate bytes in the candidate inbox (megaplan 13.2: 2 MiB). */
    public const val INBOX_MAX_BYTES: Int = 2 * 1024 * 1024

    /** Notification scopes kept for de-duplication (report section 11). */
    public const val DEDUP_MAX_SCOPES: Int = 256

    /** Time in milliseconds after which an idle de-duplication scope is forgotten (report section 11: 30 minutes). */
    public const val DEDUP_TTL_MS: Long = 30L * 60L * 1_000L

    /** Coverage intervals kept in the ledger before the oldest are dropped and counted. */
    public const val LEDGER_MAX_INTERVALS: Int = 100

    /** The oldest Android API on which the lane runs (megaplan decision D-01). */
    public const val MIN_API: Int = 30
}
