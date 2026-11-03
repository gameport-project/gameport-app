package app.gameport.core.patch.patches

import app.gameport.core.patch.ApkPatch
import app.gameport.core.patch.PatchAssets
import app.gameport.core.patch.PatchContext
import app.gameport.core.patch.PatchSession

/**
 * Some games sign in one way on a Steam Frame (through Steam) and another on a Quest (through Meta), and tell the two apart by something a Quest never
 * says, such as the name of its graphics driver. This patch makes such a game take its Steam way, for the one place that chooses how to sign in. Nothing else
 * of what the game believes about the device changes.
 *
 * It is a change of the game's own code, made at a known place of a known build, so it is on for the games it knows and does nothing for any other. The bytes
 * there are checked first and a build that does not match is left as it is.
 */
object SteamFrameLoginPatch : ApkPatch {
    override val id = "steam_frame_login"
    override val recommended = true
    override val locked = false

    const val LIBRARY_PATH = "lib/arm64-v8a/libil2cpp.so"

    /** A place of a game's code, the bytes it holds in the build this was read from, and what takes their place. */
    internal class Site(val packageName: String, val offset: Int, val expected: ByteArray, val replacement: ByteArray)

    private fun words(vararg words: Long): ByteArray = ByteArray(words.size * 4) { index ->
        (words[index / 4] shr (8 * (index % 4))).toByte()
    }

    internal val SITES = listOf(
        // LobbyManager.GetLoginType, on Android: `mov x0, xzr`, `bl Utility.IsSteamFrame`, `tbnz w0, #0, <PC login>`. The call becomes `mov w0, #1`.
        Site(
            packageName = "com.FlatHeadStudio.DeadlyDelivery",
            offset = 0x25D30E0,
            expected = words(0xAA1F03E0, 0x94025C7E, 0x37000160),
            replacement = words(0xAA1F03E0, 0x52800020, 0x37000160),
        ),
    )

    override fun apply(session: PatchSession, context: PatchContext, assets: PatchAssets) {
        val packageName = session.packageName()
        if (SITES.none { it.packageName == packageName }) return
        val library = session.readFile(LIBRARY_PATH) ?: return
        if (patchBytes(packageName, library)) session.addFile(LIBRARY_PATH, library)
    }

    /** Changes [library] in place and says whether it did: only when the bytes at the known place are the ones expected, never twice. */
    internal fun patchBytes(packageName: String, library: ByteArray): Boolean {
        val site = SITES.firstOrNull { it.packageName == packageName } ?: return false
        if (site.offset < 0 || site.offset + site.expected.size > library.size) return false
        val here = library.copyOfRange(site.offset, site.offset + site.expected.size)
        if (!here.contentEquals(site.expected)) return false
        site.replacement.copyInto(library, site.offset)
        return true
    }
}
