package app.gameport.core.patch.patches

import app.gameport.core.patch.ApkPatch
import app.gameport.core.patch.PatchAssets
import app.gameport.core.patch.PatchContext
import app.gameport.core.patch.PatchSession
import app.gameport.core.patch.json.ATTR_NAME
import app.gameport.core.patch.json.ATTR_VALUE
import app.gameport.core.patch.json.ManifestAttr
import app.gameport.core.patch.json.element
import app.gameport.core.patch.json.nameAttribute
import app.gameport.core.patch.json.named
import app.gameport.core.patch.json.takeEach
import app.gameport.core.patch.json.takeNodes
import app.gameport.core.patch.json.takeNodesEach
import com.reandroid.json.JSONArray
import com.reandroid.json.JSONObject
import java.util.Base64

/**
 * Meta's plugins, in an Unreal game made for another headset, can look for the current activity in Unity's player class (`com.unity3d.player.UnityPlayer.currentActivity`),
 * which an Unreal game does not have: the call then dies on a missing class or a null. This gives the game a class of that name, with an empty field, in a dex of its own,
 * and marks the game so that its hook (see `followActivityForUnityPlayerStub`) fills the field with the game's activity, as Unity's player would.
 *
 * The idea is OVRPort's `patch_fix_unreal_crash` (GPLv3, see docs/THIRD_PARTY_NOTICES.md), which writes the field from the game's activity class by editing its code; here
 * the game's code is left alone. Only on a Pico, only for an Unreal game (it carries `libUnreal.so` and no `libunity.so`), so a game with a Unity player of its own is never given a second one.
 */
object UnrealUnityPlayerStubPatch : ApkPatch {
    override val id = "unreal_unityplayer_stub"
    override val recommended = true
    override val locked = false

    const val UNREAL_LIBRARY = "lib/arm64-v8a/libUnreal.so"
    const val UNITY_LIBRARY = "lib/arm64-v8a/libunity.so"

    /** Application metadata the hook reads: present when the game was given the stand-in. */
    const val META_KEY = "app.gameport.unreal_unityplayer_stub"

    /** The dex of the stand-in (core/patch/src/dexstub, built by scripts/build_unityplayer_stub.sh): 700 bytes. */
    internal val STUB: ByteArray = Base64.getDecoder().decode("ZGV4CjAzOQBVexjuF1qSzywv6m4m/nC70z636zbrzc68AgAAcAAAAHhWNBIAAAAAAAAAACgCAAAIAAAAcAAAAAQAAACQAAAAAQAAAKAAAAABAAAArAAAAAIAAAC0AAAAAQAAAMQAAADYAQAA5AAAAAABAAAIAQAAIAEAAEIBAABWAQAAaAEAAGsBAAB8AQAAAQAAAAIAAAADAAAABQAAAAUAAAADAAAAAAAAAAEAAAAGAAAAAQAAAAAAAAACAAAAAAAAAAEAAAARAAAAAgAAAAAAAAAEAAAAAAAAABsCAAAAAAAAAQABAAEAAAD8AAAABAAAAHAQAQAAAA4ACgAOAAY8aW5pdD4AFkxhbmRyb2lkL2FwcC9BY3Rpdml0eTsAIExjb20vdW5pdHkzZC9wbGF5ZXIvVW5pdHlQbGF5ZXI7ABJMamF2YS9sYW5nL09iamVjdDsAEFVuaXR5UGxheWVyLmphdmEAAVYAD2N1cnJlbnRBY3Rpdml0eQCcAX5+RDh7ImJhY2tlbmQiOiJkZXgiLCJjb21waWxhdGlvbi1tb2RlIjoiZGVidWciLCJoYXMtY2hlY2tzdW1zIjpmYWxzZSwibWluLWFwaSI6MjksInNoYS0xIjoiNzUwYTIxYjRmNDI4MWIxZjQ1M2I2NDllMGI4NGYxYmE5YzA0ZjRmYyIsInZlcnNpb24iOiI5LjAuMy1kZXYifQABAAEAAAkAgoAE5AEADAAAAAAAAAABAAAAAAAAAAEAAAAIAAAAcAAAAAIAAAAEAAAAkAAAAAMAAAABAAAAoAAAAAQAAAABAAAArAAAAAUAAAACAAAAtAAAAAYAAAABAAAAxAAAAAEgAAABAAAA5AAAAAMgAAABAAAA/AAAAAIgAAAIAAAAAAEAAAAgAAABAAAAGwIAAAAQAAABAAAAKAIAAA==")

    internal fun wanted(context: PatchContext, hasUnreal: Boolean, hasUnity: Boolean): Boolean =
        context.isVr != false && context.deviceFamily == PicoMetadataPatch.FAMILY && hasUnreal && !hasUnity

    override fun apply(session: PatchSession, context: PatchContext, assets: PatchAssets) {
        if (!wanted(context, session.hasFile(UNREAL_LIBRARY), session.hasFile(UNITY_LIBRARY))) return
        // A game patched again starts from the APK already patched: the stand-in is there when one of its dex is this very file.
        val present = (1..MAX_DEX).map { if (it == 1) "classes.dex" else "classes$it.dex" }
            .any { name -> session.fileLength(name) == STUB.size.toLong() && session.readFile(name)?.contentEquals(STUB) == true }
        if (!present) session.addFile(session.nextDexName(), STUB)
        mark(session.manifest)
    }

    private const val MAX_DEX = 40

    internal fun mark(manifest: JSONObject) {
        manifest.takeNodesEach({ named("manifest") }) {
            takeNodesEach({ named("application") }) {
                takeNodes {
                    val nodes = this ?: JSONArray()
                    nodes.takeEach<JSONObject>({ named("meta-data") && nameAttribute() == META_KEY }) { null }
                    nodes.put(element("meta-data", ManifestAttr("name", ATTR_NAME, "STRING", META_KEY), ManifestAttr("value", ATTR_VALUE, "STRING", "true")))
                    nodes
                }
            }
        }
    }
}
