package org.sakshi.app.importing

/**
 * Remembers which forwarded share was already handled, so that recreating the activity does not parse it again.
 * [lastId] is a random identifier made by the share target; it is not derived from the shared content.
 */
class ConsumedIntentTracker(var lastId: String? = null) {
    /**
     * An intent with an id is handled once. One without an id is handled unless the activity was recreated,
     * because Android hands a recreated activity the intent it was first started with.
     */
    fun shouldProcess(id: String?, recreated: Boolean): Boolean {
        val fresh = if (id == null) !recreated else id != lastId
        if (id != null) lastId = id
        return fresh
    }
}
