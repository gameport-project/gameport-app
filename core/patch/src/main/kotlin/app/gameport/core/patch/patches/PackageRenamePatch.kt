package app.gameport.core.patch.patches

import app.gameport.core.patch.ApkPatch
import app.gameport.core.patch.PatchAssets
import app.gameport.core.patch.PatchContext
import app.gameport.core.patch.PatchSession
import app.gameport.core.patch.json.ATTR_NAME
import app.gameport.core.patch.json.ATTR_VALUE
import app.gameport.core.patch.json.ManifestAttr
import app.gameport.core.patch.json.elem
import app.gameport.core.patch.json.elemEach
import app.gameport.core.patch.json.element
import app.gameport.core.patch.json.nameAttribute
import app.gameport.core.patch.json.named
import app.gameport.core.patch.json.takeEach
import app.gameport.core.patch.json.takeNodes
import app.gameport.core.patch.json.takeNodesEach
import com.reandroid.json.JSONArray
import com.reandroid.json.JSONObject

/**
 * Installs the game under a package name of GamePort's own instead of the one its APK declares.
 *
 * Why: a headset can keep its own store's package names associated with the signature it signed them with, and refuse a game that carries another one
 * before it runs. A Pico does exactly that: its `com.bytedance.pico.matrix` asks Pico's servers about every third-party application it sees
 * (`syncInstalledEntitlementInfoListFromNetwork`, sending the package name and the signature of the installed APK), and force-stops the ones whose
 * signature is not the one registered for that name, showing "the entitlement check fails" with a button to the store. Nothing of the game is at
 * fault: a build made for another store and re-signed by GamePort answers that check for every game sold on the Pico Store. Under a name nobody
 * knows, there is nothing to compare, and the check has nothing to say.
 *
 * What changes in the manifest: the `package` attribute, the provider authorities and the permission names that carry the old name (both are unique
 * on the device, so a second copy under the old one would not even install), the references to those permissions, and the component names written
 * relatively (they resolve against the package, so they are written in full before it changes). Class names written in full are left alone: they name
 * classes that really exist in the game's dex, and renaming them would break the components that use them.
 *
 * The name the game came with is kept in the manifest ([META_ORIGINAL]) so GamePort still knows it: Steam names a game's own folders and its save
 * rules after it, and the game's data folder moves with the package name.
 */
object PackageRenamePatch : ApkPatch {
    override val id = "package_rename"
    override val recommended = true
    override val locked = false

    /** The name the game's APK came with, written into the manifest so it is not lost. */
    const val META_ORIGINAL = "app.gameport.original_package"

    /** The game's resource table, which names the package its resources belong to. */
    const val RESOURCES = "resources.arsc"

    /** The id of the resources a package's own APK declares: the one every application gets. */
    private const val APP_PACKAGE_ID = 0x7f

    /** Attributes whose value carries the package name, rewritten when they start with the old one. */
    private val PREFIXED_ATTRIBUTES = setOf("authorities", "permission", "readPermission", "writePermission", "targetPackage")

    /** Attributes that name a class; a relative one resolves against the package, so it is written in full before the package changes. */
    private val CLASS_ATTRIBUTES = setOf("name", "targetActivity", "backupAgent", "manageSpaceActivity")

    /** Elements whose `name` is a class of the game. */
    private val COMPONENTS = setOf("application", "activity", "activity-alias", "service", "receiver", "provider", "instrumentation")

    /** Elements whose `name` is a permission the package itself names. */
    private val PERMISSIONS = setOf("permission", "uses-permission", "uses-permission-sdk-23")

    /** Whether this patch has something to do for this game: a name was chosen and it is not the one the APK already has. */
    fun wanted(context: PatchContext, original: String): Boolean =
        context.renamedPackage != null && context.renamedPackage != original

    /**
     * The package name the game will be installed under, whether or not this patch runs: the name asked for, the one its manifest records when it is put back under
     * its own, or the one it carries. This must be the final name, since the hook's authority is built from it — a hook left on the name the game had before would
     * clash with the copy still installed under that name (`INSTALL_FAILED_CONFLICTING_PROVIDER`).
     */
    fun installName(context: PatchContext, session: PatchSession): String =
        targetName(context, session.packageName(), recordedOriginal(session.manifest))

    /** The name the game's APK came with: the one its manifest records since GamePort renamed it, or the one it carries now. */
    fun originalName(session: PatchSession): String = recordedOriginal(session.manifest) ?: session.packageName()

    override fun apply(session: PatchSession, context: PatchContext, assets: PatchAssets) {
        val original = session.packageName()
        val manifest = session.manifest
        val recorded = recordedOriginal(manifest)
        val target = targetName(context, original, recorded)
        // The name the game came with is what the manifest must keep: putting a game back under its own name must not overwrite it with the GamePort one it carried.
        if (target != original) patchManifest(manifest, original, target, recorded ?: original)
        patchResources(session, target)
    }

    /**
     * The package name the game must carry after this pass: the one asked for, the one its manifest records when it is put back under its own (the player turned the
     * choice off), or the one it already has.
     */
    internal fun targetName(context: PatchContext, current: String, recorded: String?): String = when {
        wanted(context, current) -> context.renamedPackage!!
        context.restorePackageName -> recorded?.takeIf { it != current } ?: current
        else -> current
    }

    /** The name the game's APK came with, as its manifest records it since GamePort renamed it, or null when it was never renamed. */
    internal fun recordedOriginal(manifest: JSONObject): String? =
        manifest.elem<JSONArray>("nodes").elemEach<JSONObject> { named("manifest") }
            .flatMap { root -> root.elem<JSONArray>("nodes").elemEach<JSONObject> { named("application") } }
            .flatMap { application -> application.elem<JSONArray>("nodes").elemEach<JSONObject> { named("meta-data") } }
            .firstOrNull { it.nameAttribute() == META_ORIGINAL }
            ?.let { meta -> meta.elem<JSONArray>("attributes").elemEach<JSONObject>().firstOrNull { it.named("value") }?.elem<String>("data") }

    /**
     * The resource table names the package its resources belong to, and a game that looks one of its own resources up by name with the package name it carries
     * now — Unity does, for the strings of its own player — finds nothing while the two disagree, and dies on `Resources$NotFoundException` (resource id 0).
     *
     * The table follows the manifest: whether this pass renames the game or an earlier one renamed the manifest alone, patching the game again puts the two
     * back together. Only the resources the package's own APK declares are touched (id `0x7f`): another package in the same table (a library the game embeds)
     * keeps its name.
     */
    internal fun patchResources(session: PatchSession, packageName: String) {
        val table = session.resources ?: return
        var renamed = 0
        table.forEach { block ->
            if (block.id == APP_PACKAGE_ID && block.name != packageName) {
                block.setName(packageName)
                renamed++
            }
        }
        if (renamed > 0) session.replaceFile(RESOURCES, table.bytes)
    }

    /**
     * Rewrites the manifest for [original] → [renamed]. [originalName] is the name the game came with, kept in the manifest ([META_ORIGINAL]): it is the one recorded
     * before, or the name carried now when the game is renamed for the first time.
     */
    internal fun patchManifest(manifest: JSONObject, original: String, renamed: String, originalName: String = original) {
        manifest.takeNodesEach({ named("manifest") }) {
            this?.let { rewriteElement(it, original, renamed) }
            takeNodesEach({ named("application") }) {
                takeNodes {
                    val nodes = this ?: JSONArray()
                    nodes.takeEach<JSONObject>({ named("meta-data") && nameAttribute() == META_ORIGINAL }) { null }
                    nodes.put(element("meta-data", ManifestAttr("name", ATTR_NAME, "STRING", META_ORIGINAL), ManifestAttr("value", ATTR_VALUE, "STRING", originalName)))
                    nodes
                }
            }
        }
    }

    /** Rewrites the names one element carries, then the ones every element under it carries. */
    private fun rewriteElement(element: JSONObject, original: String, renamed: String) {
        val tag = element.elem<String>("name")
        element.elem<JSONArray>("attributes")?.elemEach<JSONObject>()?.forEach { attribute ->
            val name = attribute.elem<String>("name") ?: return@forEach
            val value = attribute.elem<String>("data") as? String ?: return@forEach
            val replacement = when {
                tag == "manifest" && name == "package" -> renamed
                name == "name" && tag in PERMISSIONS -> prefixed(value, original, renamed)
                // Several authorities may be listed in one value, separated by semicolons: each one carries the old name.
                name in PREFIXED_ATTRIBUTES -> value.split(';').joinToString(";") { prefixed(it, original, renamed) }
                // A relative class name belongs to the game's own package: it must keep pointing at the same class.
                name in CLASS_ATTRIBUTES && (name != "name" || tag in COMPONENTS) -> qualified(value, original)
                else -> null
            }
            if (replacement != null && replacement != value) attribute.put("data", replacement)
        }
        element.elem<JSONArray>("nodes")?.elemEach<JSONObject>()?.forEach { rewriteElement(it, original, renamed) }
    }

    /**
     * The full name of a class written relatively: `.Name`, or `Name` with no dot at all, which Android also resolves against the package. Null for a name already in full.
     */
    private fun qualified(value: String, original: String): String? = when {
        value.startsWith(".") -> original + value
        value.isNotEmpty() && '.' !in value -> "$original.$value"
        else -> null
    }

    /** [value] with the old package name in front of it replaced by the new one, or unchanged when it names something else. */
    private fun prefixed(value: String, original: String, renamed: String): String =
        if (value == original || value.startsWith("$original.")) renamed + value.removePrefix(original) else value
}
