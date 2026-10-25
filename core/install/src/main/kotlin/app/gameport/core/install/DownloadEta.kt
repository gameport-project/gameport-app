package app.gameport.core.install

/**
 * The time left of a download, worked out from how its progress moved over the last minutes: what is left of the whole, divided by the share done each
 * second. It uses the progress that is shown, not the bytes that cross the network, so a download that checks or rewrites files is told as it really goes.
 * Nothing is said for the first moments, nor while the progress does not move.
 */
internal class DownloadEta(private val now: () -> Long = System::currentTimeMillis) {
    private class Sample(val at: Long, val progress: Float)

    private val samples = ArrayDeque<Sample>()

    /** Starts again, for a figure that restarts (the end of a check of files). */
    fun reset() = samples.clear()

    /** Notes the progress now, and returns the seconds left, or null when they cannot be told. */
    fun update(progress: Float): Long? {
        val at = now()
        // A figure that goes back is not a speed: the window starts again from it.
        if (samples.isNotEmpty() && progress < samples.last().progress) samples.clear()
        samples.addLast(Sample(at, progress))
        while (samples.size > 1 && at - samples.first().at > WINDOW_MS) samples.removeFirst()
        val first = samples.first()
        val elapsed = at - first.at
        if (elapsed < MIN_WINDOW_MS) return null
        val moved = progress - first.progress
        if (moved <= 0f) return null
        val perSecond = moved / (elapsed / 1000.0)
        val left = ((1.0 - progress) / perSecond).toLong()
        return if (left in 0..MAX_SECONDS) left else null
    }

    private companion object {
        /** The rate is the one of the last two minutes: steady enough not to jump, recent enough to follow a change of speed. */
        const val WINDOW_MS = 120_000L
        const val MIN_WINDOW_MS = 20_000L
        const val MAX_SECONDS = 48L * 3600
    }
}
