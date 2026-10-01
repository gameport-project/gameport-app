package app.gameport.core.model

/** How download speed is shown. Network speeds are often quoted in bits, file sizes in bytes. */
enum class SpeedUnit {
    MEGABYTES_PER_SECOND,
    MEGABITS_PER_SECOND,
    ;

    /** [bytesPerSecond] expressed in this unit. */
    fun valueOf(bytesPerSecond: Long): Double = when (this) {
        MEGABYTES_PER_SECOND -> bytesPerSecond / BYTES_PER_MEGABYTE
        MEGABITS_PER_SECOND -> bytesPerSecond * BITS_PER_BYTE / BYTES_PER_MEGABYTE
    }

    private companion object {
        const val BYTES_PER_MEGABYTE = 1_000_000.0
        const val BITS_PER_BYTE = 8
    }
}

/**
 * The unit a person most likely reads a download speed in, judged from their language: French
 * speakers say "Mo/s" (megaoctets, a byte unit), while elsewhere connection speeds are quoted in
 * megabits. It is only the starting point; the setting can be changed.
 */
fun defaultSpeedUnit(languageTag: String): SpeedUnit =
    if (languageTag.substringBefore('-').equals("fr", ignoreCase = true)) SpeedUnit.MEGABYTES_PER_SECOND else SpeedUnit.MEGABITS_PER_SECOND
