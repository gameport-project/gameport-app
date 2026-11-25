package app.gameport.core.patch

import com.reandroid.apk.ApkModule
import com.reandroid.arsc.chunk.xml.AndroidManifestBlock
import com.reandroid.json.JSONObject
import java.io.File

/** What a patch may know about the game and the account it is patched for. Nothing secret. */
data class PatchContext(
    val steamAppId: Int,
    val steamId: Long,
    val personaName: String,
    /** False for a flat game: the patches that only make sense in VR leave it alone. Null when unknown (treated as VR). */
    val isVr: Boolean? = null,
    /** The game's name on Steam, written into the game as its label ([app.gameport.core.patch.patches.AppLabelPatch]). */
    val gameName: String? = null,
    /** The version code of the game as installed on this device, when it is: a patched game never gets a lower one, which Android refuses. */
    val installedVersionCode: Long? = null,
    /**
     * The game has expansion files, whose names carry the version code (`main.<code>.<package>.obb`) and which some engines expect under the code the game was built with:
     * its version code is left as it is, never raised ([app.gameport.core.patch.patches.VersionCodePatch]).
     */
    val keepVersionCode: Boolean = false,
    /** The player did not turn off the VR entry of the game's own activity ([app.gameport.core.patch.patches.VrGameActivityPatch]). */
    val vrGameActivity: Boolean = true,
    /** The player did not turn off the stand-in for Meta's OVRPlugin ([app.gameport.core.patch.patches.OvrPluginStandInPatch]). */
    val ovrPluginStandIn: Boolean = true,
    /** The player turned on the declaration of the passthrough feature for this game ([app.gameport.core.patch.patches.PassthroughFeaturePatch]). */
    val allowPassthrough: Boolean = false,
    /** The patch version to record in the game ([PatchVersioning.current]). */
    val patchVersion: Int = PatchVersioning.GENERATION * 1_000,
    /** The game's achievements (JSON, see [app.gameport.core.model.ShimAchievements]) for the Steamworks shim; null to bake none. */
    val achievementDefinitions: String? = null,
    /** The achievements the account already unlocked, to seed the shim's own record with. */
    val achievementsEarned: String? = null,
    /**
     * The DLC the account (or its family) really has for this game, for the shim to answer with. Null when it is not known: the shim then
     * keeps saying that every DLC is there, as it did before.
     */
    val ownedDlc: List<Int>? = null,
    /** The DLC the library knows the account does NOT have (the shim says no to those, and keeps saying yes to a DLC nobody listed). */
    val missingDlc: List<Int> = emptyList(),
    /** The game is on the account through Family Sharing, not bought by it. */
    val familyShared: Boolean = false,
    /** The game has expansion files (`.obb`, or an `obb/` folder) that it reads from shared storage. */
    val hasExpansionFiles: Boolean = false,
    /** The headset family of this device (`meta`, `pico`, `openxr`), null when unknown or not a headset ([app.gameport.core.patch.patches.MetaLoaderStubPatch]). */
    val deviceFamily: String? = null,
    /**
     * On a Pico, disguise the game so the store's local recognition model does not take it for a title it sells: GamePort is added to the label
     * ([app.gameport.core.patch.patches.AppLabelPatch]) and its logo is inset into the icon ([app.gameport.core.patch.patches.PicoDisguisePatch]). False everywhere else.
     */
    val picoStoreDisguise: Boolean = false,
    /** The Android version of this device (`Build.VERSION.SDK_INT`), null when unknown ([app.gameport.core.patch.patches.MinSdkPatch]). */
    val deviceSdk: Int? = null,
    /**
     * The package name to install the game under instead of the one its APK declares, or null to keep the game's own name
     * ([app.gameport.core.patch.patches.PackageRenamePatch]).
     */
    val renamedPackage: String? = null,
    /**
     * Put the game back under the name its APK came with, which the manifest records: a repatch that only kept the name it finds would leave a game renamed
     * earlier where it is ([app.gameport.core.patch.patches.PackageRenamePatch.META_ORIGINAL]).
     */
    val restorePackageName: Boolean = false,
    /**
     * The player chose a frame rate for this game (anything but the game's own): on a Meta headset the game then declares the Quests it is made for, so the runtime offers it
     * their frame rates ([app.gameport.core.patch.patches.MetaSupportedDevicesPatch]).
     */
    val metaHighRefresh: Boolean = false,
    /** The GamePort that patches the game, by its package name: the game's hook talks to that one and to no other. */
    val owner: String = DEFAULT_OWNER,
) {
    companion object {
        /** The package name of GamePort as it is released. */
        const val DEFAULT_OWNER = "app.gameport"
    }
}

/**
 * The APK being patched: its manifest can be edited and files can be added.
 *
 * [workDir] is where a patch may keep a temporary file while it rewrites a file too large to hold in memory ([tempFile], [replaceFileWithFile]); the engine empties it when it is done.
 */
class PatchSession(private val apk: ApkModule, val existingHookDex: String? = null, private val workDir: File? = null) {
    private var manifestJson: JSONObject? = null

    /** The manifest as editable JSON; reading it marks the manifest as modified. */
    val manifest: JSONObject
        get() = manifestJson ?: apk.androidManifest.toJson().also { manifestJson = it }

    /**
     * The rewritten manifest, or null when no patch touched it. It is built into a fresh block:
     * loading JSON into the existing manifest would append to it and duplicate every element.
     */
    fun manifestBytes(): ByteArray? = manifestJson?.let { json ->
        AndroidManifestBlock().apply {
            fromJson(json)
            refresh()
        }.bytes
    }

    /** The size of a file of the APK in bytes, or null when there is none. */
    fun fileLength(path: String): Long? = apk.zipEntryMap.getInputSource(path)?.length

    /** The paths of the files of the APK that begin with [prefix]. */
    fun paths(prefix: String): List<String> = apk.zipEntryMap.toArray().map { it.name }.filter { it.startsWith(prefix) }

    /** Whether the APK holds a file at [path]. */
    fun hasFile(path: String): Boolean = apk.zipEntryMap.getInputSource(path) != null

    /** A file of the game as it is in the APK, or null when there is none. */
    fun readFile(path: String): ByteArray? =
        apk.zipEntryMap.getInputSource(path)?.let { source -> source.openStream().use { it.readBytes() } }

    /** A file of the game as a stream, read from its start each time it is opened (nothing is held in memory), or null when there is none. The caller closes it. */
    fun openFile(path: String): java.io.InputStream? = apk.zipEntryMap.getInputSource(path)?.openStream()

    /** A file of the work folder, deleted when the patching is done ([cleanUp]). */
    fun tempFile(prefix: String): File {
        val directory = (workDir ?: File(System.getProperty("java.io.tmpdir"))).also { it.mkdirs() }
        return File.createTempFile(prefix, ".tmp", directory).also { temporaries += it }
    }

    /** A file that is written into the APK from a file on the disk, stored as it is: its size and its CRC-32, known by whoever wrote it. */
    class StreamedFile(val file: File, val size: Long, val crc: Long)

    private val temporaries = mutableListOf<File>()
    private val _streamed = LinkedHashMap<String, StreamedFile>()

    /** Files to put in the APK from the disk, by path. */
    val streamed: Map<String, StreamedFile> get() = _streamed

    /** Puts [file] (of [size] bytes, whose CRC-32 is [crc]) in the place of the game's file at [path], without reading it into memory. */
    fun replaceFileWithFile(path: String, file: File, size: Long, crc: Long) {
        _additions.remove(path)
        _streamed[path] = StreamedFile(file, size, crc)
    }

    /** Deletes the temporary files. The engine calls it once the new APK is written. */
    fun cleanUp() {
        temporaries.forEach { it.delete() }
        temporaries.clear()
    }

    /** The game's package name, read from its manifest. */
    fun packageName(): String = apk.androidManifest.packageName

    /**
     * The paths of the image files of the game's icon, one per density, from both its icon and its round-icon resources. Empty when the game declares no icon
     * or its resource table cannot be read. The caller reads and rewrites each one with [readFile]/[addFile].
     */
    fun iconResPaths(): List<String> {
        val manifest = apk.androidManifest
        val ids = listOfNotNull(
            manifest.iconResourceId.takeIf { it != 0 },
            manifest.roundIconResourceId.takeIf { it != 0 },
        )
        return ids.flatMap { id ->
            runCatching { apk.listResFiles(id, null).map { it.filePath } }.getOrDefault(emptyList())
        }.distinct()
    }

    /** Path of the next free `classesN.dex`, so an added dex never replaces one of the game's. */
    fun nextDexName(): String {
        val highest = apk.zipEntryMap.toArray().mapNotNull { DEX.matchEntire(it.name)?.groupValues?.get(1) }
            .maxOfOrNull { if (it.isEmpty()) 1 else it.toInt() } ?: 0
        val taken = _additions.keys.mapNotNull { DEX.matchEntire(it)?.groupValues?.get(1) }
            .maxOfOrNull { if (it.isEmpty()) 1 else it.toInt() } ?: 0
        return "classes${maxOf(highest, taken) + 1}.dex"
    }

    private val _removals = LinkedHashSet<String>()

    /** Files to take out of the APK, by path. */
    val removals: Set<String> get() = _removals

    fun removeFile(path: String) {
        _removals += path
    }

    private val _additions = LinkedHashMap<String, ByteArray>()

    /** Files to add to the APK, stored uncompressed, by path. */
    val additions: Map<String, ByteArray> get() = _additions

    fun addFile(path: String, bytes: ByteArray) {
        _streamed.remove(path)
        _additions[path] = bytes
    }

    /**
     * A file of the game to put in place of the one it has, stored uncompressed. The resource table has to be: Android refuses an APK whose
     * `resources.arsc` is compressed.
     */
    fun replaceFile(path: String, bytes: ByteArray) = addFile(path, bytes)

}

private val DEX = Regex("classes(\\d*)\\.dex")

/** One independent modification of an APK. */
interface ApkPatch {
    val id: String

    /** Applied by default, without the user asking. */
    val recommended: Boolean

    /** The user cannot switch it off: the game would not work, or its saves would be at risk. */
    val locked: Boolean

    fun apply(session: PatchSession, context: PatchContext, assets: PatchAssets)
}

/** Where a binary bundled with GamePort comes from (an app asset in production). */
fun interface BinarySource {
    fun open(): java.io.InputStream
}

/** The binaries a patch may inject. */
class PatchAssets(
    val shim: BinarySource,
    val hookDex: BinarySource,
    val xrLayer: BinarySource? = null,
    val xrLoader: BinarySource? = null,
    /** The stand-in for Meta's OVRPlugin ([app.gameport.core.patch.patches.OvrPluginStandInPatch]). */
    val ovrPlugin: BinarySource? = null,
    /** GamePort's logo, inset into the game's icon on a Pico ([app.gameport.core.patch.patches.PicoDisguisePatch]). */
    val gamePortIcon: BinarySource? = null,
)
