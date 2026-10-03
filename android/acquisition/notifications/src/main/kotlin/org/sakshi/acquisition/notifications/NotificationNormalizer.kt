package org.sakshi.acquisition.notifications

import org.sakshi.core.model.IdentityBasis

/**
 * Turns one [NotificationSnapshot] into zero or more [ObservedMessage] candidates: one per message of a
 * MessagingStyle notification, otherwise one from the title and text. Pure and stateless.
 *
 * It never invents a sender, a time or a body. Historic messages and messages with no sender (the current user) are
 * context and never become candidates. A count notice such as "3 new messages" is a summary and never a message.
 * Duplicate suppression is the job of [ObservationDiffer], not of this class.
 */
public object NotificationNormalizer {
    /** Counts and placeholders with no body. English only: other languages fall through to the generic path. */
    private val SUMMARY_NOTICE = Regex(
        """^\s*((\d+|new)\s+(new\s+)?(messages?|notifications?|chats?)|new\s+messages?)(\s+from\s+.+)?\s*$""",
        RegexOption.IGNORE_CASE,
    )

    private val MEDIA_LABELS = setOf("photo", "image", "video", "voice message", "audio", "gif", "sticker", "document")

    /** True when [text] is only a message count or placeholder such as "3 new messages". */
    public fun isSummaryCountNotice(text: String): Boolean = SUMMARY_NOTICE.matches(text)

    /** True when [text] is only a media label such as "Photo". It is a type hint, not content. */
    public fun isMediaLabelOnly(text: String): Boolean = text.trim().trimEnd('.').lowercase() in MEDIA_LABELS

    public fun normalize(snapshot: NotificationSnapshot): NormalizationResult {
        if (snapshot.origin == SnapshotOrigin.REMOVAL) return NormalizationResult(emptyList(), WithheldReason.REMOVAL_ONLY)
        if (snapshot.visibility == NotificationVisibility.SECRET) {
            return NormalizationResult(emptyList(), WithheldReason.SECRET_VISIBILITY)
        }
        return if (snapshot.messages.isNotEmpty()) fromMessages(snapshot) else fromPlainText(snapshot)
    }

    private fun fromMessages(snapshot: NotificationSnapshot): NormalizationResult {
        val incoming = snapshot.messages.filter { !it.fromCurrentUserHint && it.text.isNotBlank() }
        val candidates = incoming.map { message ->
            val status = when {
                snapshot.isGroupSummary -> ObservedTextStatus.SUMMARY_ONLY
                message.textTruncated -> ObservedTextStatus.TRUNCATED
                else -> ObservedTextStatus.COMPLETE
            }
            build(
                snapshot = snapshot,
                sender = message.senderLabel,
                senderSource = if (message.senderLabel == null) SenderLabelSource.NONE else SenderLabelSource.MESSAGE_SENDER,
                text = message.text,
                status = status,
                direction = if (message.senderLabel == null) ObservedDirection.UNKNOWN else ObservedDirection.INCOMING_HINT,
                claim = SourceClaimTime(message.timestampMs, SourceClaimTimeBasis.MESSAGE_TIMESTAMP),
            )
        }
        return NormalizationResult(candidates, if (candidates.isEmpty()) WithheldReason.NO_TEXT else null)
    }

    private fun fromPlainText(snapshot: NotificationSnapshot): NormalizationResult {
        val bigText = snapshot.bigText?.takeIf { it.isNotBlank() }
        val text = bigText ?: snapshot.text?.takeIf { it.isNotBlank() }
            ?: return NormalizationResult(emptyList(), WithheldReason.NO_TEXT)
        if (isSummaryCountNotice(text)) return NormalizationResult(emptyList(), WithheldReason.SUMMARY_COUNT_NOTICE)
        if (isMediaLabelOnly(text)) return NormalizationResult(emptyList(), WithheldReason.MEDIA_LABEL_ONLY)
        val truncatedField = if (bigText != null) SnapshotField.BIG_TEXT else SnapshotField.TEXT
        val title = snapshot.title?.takeIf { it.isNotBlank() }
        val status = when {
            snapshot.isGroupSummary -> ObservedTextStatus.SUMMARY_ONLY
            truncatedField in snapshot.truncatedFields -> ObservedTextStatus.TRUNCATED
            else -> ObservedTextStatus.COMPLETE
        }
        val claim = SourceClaimTime(
            snapshot.whenMs,
            if (snapshot.whenMs == null) SourceClaimTimeBasis.NONE else SourceClaimTimeBasis.NOTIFICATION_WHEN,
        )
        val message = build(
            snapshot = snapshot,
            sender = title,
            senderSource = if (title == null) SenderLabelSource.NONE else SenderLabelSource.NOTIFICATION_TITLE,
            text = text,
            status = status,
            direction = ObservedDirection.UNKNOWN,
            claim = claim,
        )
        return NormalizationResult(listOf(message), null)
    }

    private fun build(
        snapshot: NotificationSnapshot,
        sender: String?,
        senderSource: SenderLabelSource,
        text: String,
        status: ObservedTextStatus,
        direction: ObservedDirection,
        claim: SourceClaimTime,
    ): ObservedMessage = ObservedMessage(
        sourceAppClaim = snapshot.packageName,
        conversation = ConversationScopeClaim(
            packageName = snapshot.packageName,
            scopeId = scopeIdOf(snapshot),
            notificationKey = snapshot.notificationKey,
            groupKey = snapshot.groupKey,
            shortcutId = snapshot.shortcutId,
            conversationTitle = snapshot.conversationTitle,
            isGroup = snapshot.isGroupConversation,
        ),
        senderLabel = sender,
        senderLabelSource = senderSource,
        identityBasis = if (sender == null) IdentityBasis.UNKNOWN else IdentityBasis.APP_SCOPED_HINT,
        text = text,
        textStatus = status,
        direction = direction,
        sourceClaimTime = claim,
        notificationPostTimeMs = snapshot.postTimeMs,
        collector = CollectorTime(snapshot.observedWallMs, snapshot.elapsedRealtimeMs, snapshot.collectorSessionId),
        origin = snapshot.origin,
    )

    /** The shortcut when the app gave one, otherwise the single notification. Equal titles never merge two chats. */
    internal fun scopeIdOf(snapshot: NotificationSnapshot): String =
        if (snapshot.shortcutId != null) {
            "${snapshot.packageName}|shortcut|${snapshot.shortcutId}"
        } else {
            "${snapshot.packageName}|key|${snapshot.notificationKey}"
        }
}
