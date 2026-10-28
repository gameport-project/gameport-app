package app.gameport.core.patch

import android.content.Context
import android.content.pm.ApplicationInfo
import java.util.zip.CRC32

/**
 * Which patch generation a game carries, and whether GamePort has a newer one.
 *
 * - A release build uses [GENERATION], plus [DEV_REVISION] when the patches' logic changed since the release. [GENERATION] is raised when a release is
 *   prepared, and only if the patches or the binaries they inject (shim, hook, OpenXR layer, OpenXR loader) changed since the
 *   previous release. A build of the CI that is not a release (an alpha) therefore patches again the games a release patched.
 * - A debug build adds a fingerprint of the injected binaries, so during development a rebuilt shim,
 *   hook or layer makes games patched earlier show as outdated without raising anything. A change to
 *   the patches' own logic, which no fingerprint can see, raises [DEV_REVISION] instead (it is reset
 *   to 0 when [GENERATION] is raised).
 */
object PatchVersioning {
    const val GENERATION = 9

    /** Raised during development when the patches' logic changes; back to 0 at each release. A release build counts it too, so an alpha carries it. */
    const val DEV_REVISION = 3

    private const val STEP = 1_000
    private val injected = listOf(
        "shim/arm64-v8a/libsteamclient.so",
        "hook/classes.dex",
        "xrlayer/arm64-v8a/libXrApiLayer_gameport.so",
        "xrloader/arm64-v8a/libopenxr_loader.so",
    )

    @Volatile private var cached: Int? = null

    /** The number written into a game patched by this build of GamePort. */
    fun current(context: Context): Int = cached ?: compute(context).also { cached = it }

    /** Whether a game recorded as patched by [installed] should be patched again. */
    fun isOutdated(installed: Int, context: Context): Boolean {
        val current = current(context)
        // Any difference counts: development fingerprints do not grow with time, and going back from an alpha to the release must patch the games again too.
        return installed != current
    }

    private fun compute(context: Context): Int {
        val base = GENERATION * STEP
        if (!isDebuggable(context)) return base + DEV_REVISION
        val crc = CRC32()
        crc.update(DEV_REVISION)
        val buffer = ByteArray(BUFFER)
        for (name in injected) {
            runCatching {
                context.assets.open(name).use { stream ->
                    while (true) {
                        val read = stream.read(buffer)
                        if (read < 0) break
                        crc.update(buffer, 0, read)
                    }
                }
            }
        }
        // 1..999: never equal to the release number of the same generation.
        return base + 1 + (crc.value % (STEP - 1)).toInt()
    }

    private fun isDebuggable(context: Context) = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    private const val BUFFER = 64 * 1024
}
