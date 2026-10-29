package app.gameport.core.patch

import com.reandroid.apk.ApkModule
import com.reandroid.archive.ArchiveFile
import com.reandroid.archive.io.ZipFileInput
import com.reandroid.arsc.chunk.xml.AndroidManifestBlock
import java.io.File

/**
 * What a game's APK says about itself, read from its manifest rather than through Android's own parser.
 *
 * The system's parser answers nothing at all for an APK that asks for a newer Android than the device runs, which is the same answer it
 * gives for a file it cannot make sense of: a perfectly good game then looks like a broken download. Reading the manifest directly tells
 * the two apart, and it is the manifest the patcher already works on, so nothing new is decoded.
 */
object ApkFacts {
    /** The Android version (API level) the game asks for, or null when its manifest does not say it or cannot be read. */
    fun minSdkOf(apk: File): Int? = read(apk) { it.minSdkVersion }

    /** The package name from the manifest: the one thing the system's parser will not give for an APK it refuses. */
    fun packageNameOf(apk: File): String? = read(apk) { it.packageName }

    /** The class the game is started through, from its manifest, or null when it cannot be read. */
    fun mainActivityOf(apk: File): String? = read(apk) { it.mainActivityClassName }

    /**
     * True when the game is started through a class that cannot work below Android 11, whatever its manifest asks for. Lowering what such
     * a game asks for only turns a refusal into a crash, so the offer to do it is withheld.
     */
    fun needsAndroid11Entry(apk: File): Boolean = needsAndroid11Entry(mainActivityOf(apk))

    /** The same of a class name already read, which is what the decision really rests on. */
    fun needsAndroid11Entry(mainActivity: String?): Boolean = mainActivity in NEEDS_ANDROID_11

    /**
     * The entry points known to need Android 11. Both are built on Google's GameActivity, whose text input reaches
     * `Window.setDecorFitsSystemWindows` through AndroidX without asking the version first; that method arrived in Android 11, and
     * AndroidX skips the check because the game declared it needed that version.
     *
     * Named exactly and never matched loosely: Unreal's own activities are called GameActivity too
     * (`com.epicgames.unreal.GameActivity`, `com.epicgames.ue4.GameActivity`), have nothing to do with Google's, and run on Android 10
     * perfectly well. A match on the word alone would wrongly condemn every Unreal game.
     */
    private val NEEDS_ANDROID_11 = setOf(
        "com.unity3d.player.UnityPlayerGameActivity",
        "com.google.androidgamesdk.GameActivity",
    )

    private fun <T> read(apk: File, of: (AndroidManifestBlock) -> T?): T? = runCatching {
        val archive = ArchiveFile(ZipFileInput(apk))
        val module = ApkModule("base", archive.createZipEntryMap()).apply { setCloseable(archive) }
        try {
            of(module.androidManifest)
        } finally {
            module.close()
        }
    }.getOrNull()
}
