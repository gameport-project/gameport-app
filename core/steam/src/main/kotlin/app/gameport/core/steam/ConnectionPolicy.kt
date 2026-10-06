package app.gameport.core.steam

/** How long to wait before each attempt to reconnect after Steam dropped the connection. */
internal val RECONNECT_PAUSES_MS = listOf(3_000L, 10_000L, 30_000L, 60_000L, 120_000L)

/** How long a GamePort leaves the session to the one that took it (same account elsewhere), unless the player comes to it. */
internal const val REPLACED_PAUSE_MS = 5 * 60_000L

/** The pauses before reconnecting: one long one when the session was taken by another, so the two do not take it back from each other. */
internal fun reconnectPauses(replaced: Boolean): List<Long> = if (replaced) listOf(REPLACED_PAUSE_MS) else RECONNECT_PAUSES_MS

/** True while the session taken by another is still left to it ([replacedAt] is 0 when it never was). */
internal fun recentlyReplaced(now: Long, replacedAt: Long): Boolean = replacedAt != 0L && now - replacedAt < REPLACED_PAUSE_MS
