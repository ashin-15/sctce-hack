package org.sakshi.acquisition.importer

import org.sakshi.core.vault.NotificationClaims

/**
 * Text the person chose to keep from an observed notification, with the claims that came with it. [text] is kept
 * exactly as received. Every field of [claims] is a claim by the publishing app or the collector, never a verified
 * fact; the source-claim time and the collector time stay separate and are never compared.
 *
 * This type is deliberately independent of the notification module so the importer pulls in no service code. The
 * app maps its hand-off onto it field by field.
 */
public data class NotificationExcerptImport(val text: String, val claims: NotificationClaims)
