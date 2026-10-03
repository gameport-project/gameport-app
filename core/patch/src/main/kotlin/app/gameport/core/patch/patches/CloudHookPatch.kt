package app.gameport.core.patch.patches

import app.gameport.core.patch.ApkPatch
import app.gameport.core.patch.PatchAssets
import app.gameport.core.patch.PatchContext
import app.gameport.core.patch.PatchSession
import app.gameport.core.patch.json.ATTR_AUTHORITIES
import app.gameport.core.patch.json.ATTR_EXPORTED
import app.gameport.core.patch.json.ATTR_INIT_ORDER
import app.gameport.core.patch.json.ATTR_NAME
import app.gameport.core.patch.json.ManifestAttr
import app.gameport.core.patch.json.element
import app.gameport.core.patch.json.named
import app.gameport.core.patch.json.takeNodes
import app.gameport.core.patch.json.takeNodesEach
import app.gameport.core.patch.json.elem
import app.gameport.core.patch.json.elemEach
import app.gameport.core.patch.json.nameAttribute
import app.gameport.core.patch.json.takeEach
import com.reandroid.json.JSONArray
import com.reandroid.json.JSONObject

/**
 * Adds GamePort's hook to the game: a small dex with a content provider. Android creates the
 * provider before any of the game's own code runs, however the game was started, and the hook uses
 * that moment to hand over to GamePort for the save sync.
 */
object CloudHookPatch : ApkPatch {
    override val id = "cloud_hook"
    override val recommended = true
    override val locked = true

    private const val HOOK_PROVIDER = "app.gameport.hook.GamePortHookProvider"
    private const val GAMEPORT_PACKAGE = "app.gameport"
    private const val GAMEPORT_AUTHORITY = "app.gameport.cloud"

    private fun JSONObject?.isGamePortQueries(): Boolean =
        named("queries") && elem<JSONArray>("nodes").elemEach<JSONObject> { named("package") && nameAttribute() == GAMEPORT_PACKAGE }.isNotEmpty()

    /** Takes the hook's manifest entries out again; the caller removes the dex. */
    fun removeFrom(session: PatchSession) {
        session.manifest.takeNodesEach({ named("manifest") }) {
            takeNodes {
                this.takeEach<JSONObject>({ isGamePortQueries() }) { null }
                this
            }
            takeNodesEach({ named("application") }) {
                takeNodes {
                    this.takeEach<JSONObject>({ named("provider") && nameAttribute() == HOOK_PROVIDER }) { null }
                    this
                }
            }
        }
    }

    override fun apply(session: PatchSession, context: PatchContext, assets: PatchAssets) {
        session.addFile(session.existingHookDex ?: session.nextDexName(), assets.hookDex.open().use { it.readBytes() })

        val hookAuthority = "${session.packageName()}.gameporthook"
        session.manifest.takeNodesEach({ named("manifest") }) {
            takeNodes {
                // Patching a game again must not stack a second copy on the first.
                this.takeEach<JSONObject>({ isGamePortQueries() }) { null }
                // Since Android 11 an app only sees the packages and providers it declares.
                this?.put(
                    element(
                        "queries",
                        children = listOf(
                            element("package", ManifestAttr("name", ATTR_NAME, "STRING", GAMEPORT_PACKAGE)),
                            element("provider", ManifestAttr("authorities", ATTR_AUTHORITIES, "STRING", GAMEPORT_AUTHORITY)),
                        ),
                    ),
                )
            }
            takeNodesEach({ named("application") }) {
                takeNodes {
                    this.takeEach<JSONObject>({ named("provider") && nameAttribute() == HOOK_PROVIDER }) { null }
                    this?.put(
                        element(
                            "provider",
                            ManifestAttr("name", ATTR_NAME, "STRING", HOOK_PROVIDER),
                            ManifestAttr("authorities", ATTR_AUTHORITIES, "STRING", hookAuthority),
                            // GamePort calls it to send the saves of a game that is not running; the hook accepts no other caller.
                            ManifestAttr("exported", ATTR_EXPORTED, "BOOLEAN", true),
                            // Runs before the game's other providers and its Application.
                            ManifestAttr("initOrder", ATTR_INIT_ORDER, "DEC", Int.MAX_VALUE),
                        ),
                    )
                }
            }
        }
    }
}
