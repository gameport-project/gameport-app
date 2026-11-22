package app.gameport.core.model

import kotlin.math.sqrt

/**
 * What the last run of a game measured, as the hook of GamePort counted it: the size of the image the game draws for one eye, the frame rate the runtime printed, the memory, how long the start
 * took, and how long the run had lasted when the hook last handed anything over. It stays on the device: it is shown on the game's page and nothing in it says who played, or what.
 * A number the run could not measure is 0 (or -1 for a memory and a start time, where 0 is a value).
 */
data class RunStats(
    /** The image the runtime recommends for an eye, before the game or the player's scale touch it. */
    val recommendedWidth: Int,
    val recommendedHeight: Int,
    /** The image the game really uses for an eye. */
    val imageWidth: Int,
    val imageHeight: Int,
    val fpsMedian: Int,
    /** The frame rate the slowest tenth of the seconds stayed under. */
    val fpsLowTenth: Int,
    /** How many seconds of the runtime's frame rate line were seen. */
    val fpsSamples: Int,
    /** The frame rate the screen asked for. */
    val fpsTarget: Int,
    /** The longest run of seconds in a row under half of the frames asked for. */
    val slowestStretch: Int,
    /** The most the game held at once, in megabytes. */
    val peakMemoryMb: Int,
    /** The least memory the device had free during the run, in megabytes. */
    val minFreeMb: Int,
    /** Seconds from the first line of the log to the first frame handed to the runtime. */
    val startupSeconds: Int,
    /** How long the run had lasted, in seconds, when the hook last handed something over. */
    val handedOverSeconds: Int,
) {
    /** The image to show for an eye: the game's own when a frame was checked, the recommended one otherwise, null when the layer said nothing. */
    val shownImage: Pair<Int, Int>?
        get() = when {
            imageWidth > 0 && imageHeight > 0 -> imageWidth to imageHeight
            recommendedWidth > 0 && recommendedHeight > 0 -> recommendedWidth to recommendedHeight
            else -> null
        }

    /** Whether the run printed enough of the frame rate to say anything of it. */
    val hasFrameRate: Boolean get() = fpsSamples >= MIN_FPS_SAMPLES && fpsTarget > 0

    /** The numbers, as the hook hands them over and in this order (the format of [parse] and [write], with its version in front). */
    fun write(): String = (listOf(VERSION) + values()).joinToString(" ")

    private fun values() = listOf(
        recommendedWidth, recommendedHeight, imageWidth, imageHeight, fpsMedian, fpsLowTenth, fpsSamples, fpsTarget, slowestStretch,
        peakMemoryMb, minFreeMb, startupSeconds, handedOverSeconds,
    )

    companion object {
        /** Written in front of the numbers, so a later change of what is kept does not read an old record as a new one. */
        private const val VERSION = 1

        /** How many numbers the hook hands over. */
        const val FIELDS = 13

        /** Under this many seconds of the frame rate line, a median says too little. */
        const val MIN_FPS_SAMPLES = 5

        /** What the hook handed over, or null when it is not [FIELDS] numbers. */
        fun fromHandOver(values: IntArray?): RunStats? = values?.takeIf { it.size == FIELDS }?.let { of(it.toList()) }

        /** What [write] made, or null when the text is not of this version or does not hold [FIELDS] numbers. */
        fun parse(text: String?): RunStats? {
            val parts = text?.trim()?.split(' ')?.map { it.toIntOrNull() ?: return null } ?: return null
            return if (parts.size == FIELDS + 1 && parts[0] == VERSION) of(parts.drop(1)) else null
        }

        private fun of(v: List<Int>) = RunStats(v[0], v[1], v[2], v[3], v[4], v[5], v[6], v[7], v[8], v[9], v[10], v[11], v[12])
    }
}

/** What the measures of a run say of the way it went, and when to advise the player to lower the resolution. */
object RunRules {
    /** A run shorter than this says too little to be judged: a loading looks the same as a game that cannot keep up. */
    const val MIN_RUN_SECONDS = 20

    /** This many seconds in a row under half of the frames asked for is a game that cannot keep up, not a loading. */
    const val SLOW_STRETCH_SECONDS = 8

    /** The hook says nothing for this long while the run goes on: the game stopped answering. */
    const val SILENT_SECONDS = 15

    /** The image holds this many times more pixels than the usual one of the device: the resolution is high for it. */
    const val HIGH_PIXEL_RATIO = 1.5

    /** A resolution the player already put at or under this is not advised to go lower. */
    const val ALREADY_LOWERED_PERCENT = 80

    /**
     * The image a device usually gives a game for one eye, as the runtime recommends it by default, or null when it was not measured: the advice is never given for a device that is not here.
     * The Quest 3 is half of its 4128 by 2208 screen mode; the Pico 4 Ultra is what its runtime recommended in the logs of the layer.
     */
    fun usualImage(model: String?): Pair<Int, Int>? = when (model?.trim()?.replace('_', ' ')?.lowercase()) {
        "quest 3" -> 2064 to 2208
        "a9210", "a92y0" -> 1920 to 1920
        else -> null
    }

    /**
     * How many times more pixels the image of the run holds than the usual one of the device, or null when either is unknown. It is the image the game really drew (the player's scale and an outside
     * tool's "HD+" mode are in it), the recommended one when no frame was checked.
     */
    fun pixelRatio(stats: RunStats, model: String?): Double? {
        val usual = usualImage(model) ?: return null
        val image = stats.shownImage ?: return null
        return (image.first.toLong() * image.second).toDouble() / (usual.first.toLong() * usual.second)
    }

    /** The size of the image against the usual one, as the resolution slider counts it (a scale of the width and of the height, not of the pixels), or null when unknown. */
    fun scalePercent(stats: RunStats, model: String?): Int? = pixelRatio(stats, model)?.let { (sqrt(it) * 100).toInt() }

    /** Whether the resolution is high for the device: [HIGH_PIXEL_RATIO] or more times the usual pixels. */
    fun highResolution(stats: RunStats, model: String?): Boolean = (pixelRatio(stats, model) ?: 0.0) >= HIGH_PIXEL_RATIO

    /** Whether the game handed over fewer than half of the frames asked for for [SLOW_STRETCH_SECONDS] in a row, or for most of the seconds that were seen. */
    fun slow(stats: RunStats): Boolean =
        stats.slowestStretch >= SLOW_STRETCH_SECONDS || (stats.hasFrameRate && stats.fpsMedian * 2 < stats.fpsTarget)

    /** Whether the hook went quiet while the run went on ([runSeconds] is how long it lasted): the game stopped answering. */
    fun stoppedAnswering(stats: RunStats, runSeconds: Int): Boolean =
        runSeconds >= MIN_RUN_SECONDS && stats.handedOverSeconds > 0 && runSeconds - stats.handedOverSeconds >= SILENT_SECONDS

    /** Whether the run could not keep up: slow while it printed, or silent after a slow start. A silent run that was never slow is not one (killed, or put down). */
    fun couldNotKeepUp(stats: RunStats, runSeconds: Int): Boolean =
        runSeconds >= MIN_RUN_SECONDS && stats.fpsSamples > 0 && (slow(stats) || (stoppedAnswering(stats, runSeconds) && stats.fpsMedian * 2 < stats.fpsTarget))

    /**
     * Whether to tell the player that a lower resolution would help. Every one of these must hold: the run lasted long enough to be judged, it could not keep up, the resolution is high for the
     * device, nothing else explains it (no crash), and the player has not already lowered it ([chosenPercent] is the resolution of the Performance page, 0 for the game's own).
     */
    fun adviseLowerResolution(stats: RunStats, runSeconds: Int, model: String?, chosenPercent: Int, crashed: Boolean): Boolean =
        !crashed && couldNotKeepUp(stats, runSeconds) && highResolution(stats, model) && (chosenPercent == 0 || chosenPercent > ALREADY_LOWERED_PERCENT)
}
