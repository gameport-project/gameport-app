package app.gameport.core.patch

import com.reandroid.apk.ApkModule
import com.reandroid.arsc.chunk.xml.AndroidManifestBlock
import com.reandroid.json.JSONObject

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
    /** The patch version to record in the game ([PatchVersioning.current]). */
    val patchVersion: Int = PatchVersioning.GENERATION * 1_000,
)

/** The APK being patched: its manifest can be edited and files can be added. */
class PatchSession(private val apk: ApkModule, val existingHookDex: String? = null) {
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

    /** The game's package name, read from its manifest. */
    fun packageName(): String = apk.androidManifest.packageName

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
        _additions[path] = bytes
    }
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
class PatchAssets(val shim: BinarySource, val hookDex: BinarySource, val xrLayer: BinarySource? = null)
