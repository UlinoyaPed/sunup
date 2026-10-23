package org.unifiedpush.distributor.sunup.arrival

/** Counts transport arrivals, never unread messages. State comes from the active notification. */
internal object ArrivalPolicy {
    const val ALERT_INTERVAL_MS = 30_000L

    fun next(
        previousCount: Int?,
        lastAlert: Long?,
        now: Long
    ): State {
        val alert = lastAlert == null || now < lastAlert || now - lastAlert >= ALERT_INTERVAL_MS
        return State(
            count = ((previousCount ?: 0).coerceAtLeast(0).coerceAtMost(Int.MAX_VALUE - 1)) + 1,
            alert = alert,
            lastAlert = if (alert) now else lastAlert
        )
    }

    data class State(
        val count: Int,
        val alert: Boolean,
        val lastAlert: Long
    )
}
