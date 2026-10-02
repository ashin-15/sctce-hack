package org.sakshi.processing.text

import org.sakshi.core.model.CategoryLabel

/** Maps producer labels to event-schema labels (megaplan 11.3, M2 and M3). */
public object LabelMapping {
    public const val VERSION: Int = 1

    /**
     * Schema label for [label]. [ProducerLabel.CASTE_RELIGIOUS_SLUR] maps to `VERBAL_ABUSE`, a known loss
     * until schema v1.1 adds an identity-directed label; the producer label must always be kept alongside.
     */
    public fun toSchema(label: ProducerLabel): CategoryLabel = when (label) {
        ProducerLabel.INSULT -> CategoryLabel.VERBAL_ABUSE
        ProducerLabel.THREAT -> CategoryLabel.EXPLICIT_THREAT
        ProducerLabel.SEXUAL_HARASSMENT -> CategoryLabel.SEXUAL_PRESSURE
        ProducerLabel.CASTE_RELIGIOUS_SLUR -> CategoryLabel.VERBAL_ABUSE
        ProducerLabel.DOXXING -> CategoryLabel.PRIVACY_EXPOSURE_INDICATOR
        ProducerLabel.COERCIVE_CONTROL -> CategoryLabel.CONTROLLING_REQUEST
    }
}
