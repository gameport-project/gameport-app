package app.gameport.core.patch.patches

import app.gameport.core.patch.ApkPatch
import app.gameport.core.patch.PatchAssets
import app.gameport.core.patch.PatchContext
import app.gameport.core.patch.PatchSession
import app.gameport.core.patch.ResourceTableNames
import app.gameport.core.patch.unity.CrcCounter
import app.gameport.core.patch.unity.UnityFs
import app.gameport.core.patch.unity.UnityFsOutcome
import app.gameport.core.patch.unity.UnityIdentity
import app.gameport.core.patch.unity.UnityNames
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
    /** The biggest catalog of asset bundles looked into: a larger file is not text a catalog would be. */
    private const val MAX_CATALOG_BYTES = 16L * 1024 * 1024

    /** The string literals of an IL2CPP Unity game, and the folder of the assemblies of a Mono one. */
    private const val IL2CPP_METADATA = "assets/bin/Data/Managed/Metadata/global-metadata.dat"
    private const val MANAGED = "assets/bin/Data/Managed/"
    private const val CODE_BUFFER = 1 shl 16

    /** The company and product names a Unity game was built with, written down before GamePort changed them ([UnityIdentity]). */
    const val META_COMPANY = "app.gameport.original_company"
    const val META_PRODUCT = "app.gameport.original_product"

    /** Where a Unity game keeps its player settings: in its data archive (`globalgamemanagers` inside `data.unity3d`), or in a file of its own. */
    private const val UNITY_ARCHIVE = "assets/bin/Data/data.unity3d"
    private const val UNITY_SETTINGS_FILE = "assets/bin/Data/globalgamemanagers"
    private const val UNITY_SETTINGS_NODE = "globalgamemanagers"

    /** The biggest settings file looked into: it is held in memory (the one of a game is some hundreds of kilobytes). */
    private const val MAX_SETTINGS_BYTES = 32 * 1024 * 1024

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
        // A game that writes the way to its own folders, with its package name, in its asset catalog or in its compiled code finds nothing under another name.
        // This runs whether or not the name changes in this pass: a game renamed by an earlier GamePort already carries its new name, and still holds the old one in its paths.
        val stale = staleNames(original, recorded, target)
        if (stale.isNotEmpty()) patchObbPaths(session, stale, target)
        followUnityNames(session, manifest, came = recorded ?: original, current = original, target = target)
    }

    /**
     * Makes the company and product names a Unity game was built with follow the package it runs as, for the games that build the folder of their expansion files from them
     * ([UnityIdentity]): their asset bundles are otherwise looked for in a folder that is not the game's own. Whether the name changes in this pass or not: a game renamed by an earlier
     * GamePort carries its new package and still the old names. The names the game came with are written down in the manifest ([META_COMPANY], [META_PRODUCT]) the first time they
     * are changed, so that putting the game back under its own package restores them. A game that does not hold such a pair of names, whose archive is of a kind that is not handled,
     * or whose package does not fit in the old names, is left as it is: this only ever adds a fix, and never stops a game from being patched.
     */
    internal fun followUnityNames(session: PatchSession, manifest: JSONObject, came: String, current: String, target: String) {
        if (!session.hasFile(UNITY_ARCHIVE) && !session.hasFile(UNITY_SETTINGS_FILE)) return
        val recorded = recordedMeta(manifest, META_COMPANY)?.let { company -> recordedMeta(manifest, META_PRODUCT)?.let { product -> UnityNames(company, product) } }
        val accepted = setOf(came, current, target)
        var original: UnityNames? = null
        val edit = { bytes: ByteArray ->
            var changed = false
            for (found in UnityIdentity.locate(bytes) { UnityIdentity.derive(it) in accepted }) {
                val wanted = UnityIdentity.wanted(found.names, recorded, came, target) ?: continue
                if (recorded == null && original == null) original = found.names
                UnityIdentity.write(bytes, found, wanted)
                changed = true
            }
            changed
        }
        val written = try {
            if (session.hasFile(UNITY_ARCHIVE)) rewriteUnityArchive(session, edit) else rewriteUnitySettings(session, edit)
        } catch (e: Exception) {
            // Whatever goes wrong here (a disk that is full, an archive that is not what it seems), the game is patched without this fix, as it was before it existed.
            false
        }
        original?.takeIf { written }?.let {
            putMeta(manifest, META_COMPANY, it.company)
            putMeta(manifest, META_PRODUCT, it.product)
        }
    }

    /** The player settings of a game that keeps its data in an archive (`data.unity3d`, Unity 2023 and later on Android): the archive is written again from a file on the disk. */
    private fun rewriteUnityArchive(session: PatchSession, edit: (ByteArray) -> Boolean): Boolean {
        val file = session.tempFile("unity")
        val outcome = UnityFs.rewriteNode({ session.openFile(UNITY_ARCHIVE) ?: throw java.io.IOException("$UNITY_ARCHIVE is gone") }, UNITY_SETTINGS_NODE, MAX_SETTINGS_BYTES, file, edit)
        if (outcome is UnityFsOutcome.Rewritten) {
            session.replaceFileWithFile(UNITY_ARCHIVE, file, outcome.size, outcome.crc)
            return true
        }
        file.delete()
        return false
    }

    /** The player settings of a game that keeps them in a file of their own (`globalgamemanagers`, older Unity): the file is small, edited where it is. */
    private fun rewriteUnitySettings(session: PatchSession, edit: (ByteArray) -> Boolean): Boolean {
        if ((session.fileLength(UNITY_SETTINGS_FILE) ?: Long.MAX_VALUE) > MAX_SETTINGS_BYTES) return false
        val content = session.readFile(UNITY_SETTINGS_FILE) ?: return false
        if (!edit(content)) return false
        session.replaceFile(UNITY_SETTINGS_FILE, content)
        return true
    }

    /** The value of the `meta-data` the manifest holds under [name], or null. */
    internal fun recordedMeta(manifest: JSONObject, name: String): String? =
        manifest.elem<JSONArray>("nodes").elemEach<JSONObject> { named("manifest") }
            .flatMap { root -> root.elem<JSONArray>("nodes").elemEach<JSONObject> { named("application") } }
            .flatMap { application -> application.elem<JSONArray>("nodes").elemEach<JSONObject> { named("meta-data") } }
            .firstOrNull { it.nameAttribute() == name }
            ?.let { meta -> meta.elem<JSONArray>("attributes").elemEach<JSONObject>().firstOrNull { it.named("value") }?.elem<String>("data") }

    /** Writes a `meta-data` under [name] in the application element, in place of the one that is there. */
    internal fun putMeta(manifest: JSONObject, name: String, value: String) {
        manifest.takeNodesEach({ named("manifest") }) {
            takeNodesEach({ named("application") }) {
                takeNodes {
                    val nodes = this ?: JSONArray()
                    nodes.takeEach<JSONObject>({ named("meta-data") && nameAttribute() == name }) { null }
                    nodes.put(element("meta-data", ManifestAttr("name", ATTR_NAME, "STRING", name), ManifestAttr("value", ATTR_VALUE, "STRING", value)))
                    nodes
                }
            }
        }
    }

    /**
     * The names a path in the game's files may still carry when the game must run as [target]: the one it came with ([recorded], or [current] when it was never renamed) and
     * the one it carries now ([current]), without [target] itself. Empty when there is nothing to move: a game that keeps its own name. Putting a game back under its own
     * name gives the name of GamePort it carries; patching again a game renamed before gives the name it came with.
     */
    internal fun staleNames(current: String, recorded: String?, target: String): Set<String> =
        setOfNotNull(recorded ?: current, current).filterTo(linkedSetOf()) { it != target }

    /**
     * Some games (Budget Cuts Ultimate, with Unity's Addressables) look for their scenes at a path written down when they were built,
     * `/sdcard/Android/obb/<package the game came with>/…`, and not at the one of the package they run as: under another name the files are not found and the game
     * stays on its title screen. When the catalog of the asset bundles is a text file (a JSON file under `assets/aa/`), the package in that path is changed to the one the game runs as.
     * A catalog that is not text, or that holds no such path, is left alone: nothing is changed in a game that has none.
     */
    internal fun patchObbPaths(session: PatchSession, names: Set<String>, renamed: String) {
        for (path in session.paths("assets/aa/")) {
            if (!path.endsWith(".json", ignoreCase = true)) continue
            if ((session.fileLength(path) ?: Long.MAX_VALUE) > MAX_CATALOG_BYTES) continue
            val content = session.readFile(path) ?: continue
            rewriteObbPaths(content, names, renamed)?.let { session.replaceFile(path, it) }
        }
        // The compiled code holds such paths too: Escape Simulator loads its asset bundles from `/storage/emulated/0/Android/obb/com.PineStudio.EscapeSimulator/assets`, a string of its
        // IL2CPP metadata, and started without them (every text of its menu a bare key) under another package. The strings are written over where they stand ([PackagePaths]).
        for ((path, wide) in codeFiles(session)) rewriteCode(session, path, wide, names, renamed)
    }

    /** The files of compiled code that hold a game's strings: the metadata of an IL2CPP game (UTF-8), the assemblies of the game's own scripts in a Mono one (UTF-16). */
    private fun codeFiles(session: PatchSession): List<Pair<String, Boolean>> = buildList {
        if (session.hasFile(IL2CPP_METADATA)) add(IL2CPP_METADATA to false)
        session.paths(MANAGED).filter { path -> path.substringAfterLast('/').let { it.startsWith("Assembly-") && it.endsWith(".dll") } }.forEach { add(it to true) }
    }

    /**
     * Rewrites one file of compiled code into a temporary file, a window at a time, and puts it in the place of the game's when a path was changed: the file is never held whole. A file that
     * cannot be handled (whatever goes wrong) is left as it is, so the game is patched without this fix, as it was before it existed.
     */
    private fun rewriteCode(session: PatchSession, path: String, wide: Boolean, names: Set<String>, renamed: String) {
        val file = session.tempFile("code")
        try {
            val counter = CrcCounter(file.outputStream().buffered(CODE_BUFFER))
            val outcome = counter.use { out -> session.openFile(path)?.use { input -> PackagePaths.rewriteStream(input, out, names, renamed, wide) } }
            if (outcome != null && outcome.rewritten > 0) {
                session.replaceFileWithFile(path, file, counter.count, counter.crc.value)
                return
            }
        } catch (e: Exception) {
            // see above
        }
        file.delete()
    }

    /** [content] with `obb/<name>` made `obb/<renamed>` for each of [names] (any slash style), or null when it holds none. */
    internal fun rewriteObbPaths(content: ByteArray, names: Set<String>, renamed: String): ByteArray? {
        val text = String(content, Charsets.ISO_8859_1)
        val changed = names.filter { it != renamed }.fold(text) { current, name ->
            current.replace("obb/$name", "obb/$renamed").replace("obb\\\\$name", "obb\\\\$renamed")
        }
        return if (changed == text) null else changed.toByteArray(Charsets.ISO_8859_1)
    }

    internal fun rewriteObbPaths(content: ByteArray, original: String, renamed: String): ByteArray? = rewriteObbPaths(content, setOf(original), renamed)

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
        // The table is changed in its bytes, never decoded: see [ResourceTableNames].
        val table = session.readFile(RESOURCES) ?: return
        if (ResourceTableNames.rename(table, packageName, APP_PACKAGE_ID)) session.replaceFile(RESOURCES, table)
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
