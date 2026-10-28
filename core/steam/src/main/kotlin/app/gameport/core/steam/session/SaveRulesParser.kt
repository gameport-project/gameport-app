package app.gameport.core.steam.session

import app.gameport.core.steam.cache.CachedSaveRule
import `in`.dragonbra.javasteam.types.KeyValue

/**
 * Reads an app's `ufs` section (Steam Cloud auto-sync configuration) into rules the phone can use.
 *
 * Steam names the folders after Windows roots such as `WinAppDataLocalLow`. For Android builds it
 * publishes a root override that moves such a root to `AndroidExternalData` (the shared storage)
 * and rewrites the path, typically into `Android/data/<package>/files`. A root can also name the private
 * data of the game (`AndroidInternalData`). Rules without an Android mapping cannot be synced and are left out.
 *
 * Some studios keep the Android saves apart from the Windows ones instead: two rules with their own roots and no override
 * between them. The Windows rule of such a pair is kept as [CachedSaveRule.pcCloudPrefix] of the Android one, so the player
 * can choose to use the Windows saves.
 */
internal object SaveRulesParser {
    private const val SHARED_ROOT = "AndroidExternalData"
    private const val INTERNAL_ROOT = "AndroidInternalData"

    /** Marks a folder in the private data of the game (`<package>/files/...`): the hook, inside the game, is the one that can reach it. */
    const val INTERNAL_PREFIX = "@internal/"

    fun parse(ufs: KeyValue): List<CachedSaveRule> {
        val overrides = ufs["rootoverrides"].children.filter(::appliesToAndroid)
        val files = ufs["savefiles"].children
        return files.mapNotNull { file ->
            val platforms = file["platforms"].children.mapNotNull { it.value?.lowercase() }
            if (platforms.isNotEmpty() && "android" !in platforms) return@mapNotNull null

            val root = file["root"].value ?: return@mapNotNull null
            val path = file["path"].value.orEmpty().normalized()
            val override = overrides.firstOrNull { it["root"].value == root }
            var local = path
            var apart = false
            // The Android root the cloud files of a game written from `Android/data` are named after: Steam keeps them under it, whatever the Windows root of the rule.
            var sharedName: String? = null
            if (override != null) {
                val target = override["useinstead"].value
                if (target != SHARED_ROOT && target != INTERNAL_ROOT) return@mapNotNull null
                override["pathtransforms"].children.forEach { transform ->
                    val find = transform["find"].value.orEmpty().normalized()
                    if (find.isNotEmpty()) local = local.replace(find, transform["replace"].value.orEmpty().normalized())
                }
                val added = override["addpath"].value.orEmpty().normalized()
                if (added.isNotEmpty()) local = if (local.isEmpty()) added else "$added/$local"
                if (target == INTERNAL_ROOT) local = INTERNAL_PREFIX + local.trim('/')
                else if (!local.startsWith("Android/") && startsWithPackage(local)) {
                    // Written from `Android/data` (`<package>/files`), like a rule that names the Android root itself. Only a path that begins with a package name is the game's own
                    // folder: any other (a studio's folder name) is no place the game can write, and is left as it was read.
                    sharedName = local.trim('/')
                    local = "Android/data/$local"
                }
            } else if (root == INTERNAL_ROOT) {
                // The path is written from the folder that holds the private data of every app: `<package>/files/saves`.
                local = INTERNAL_PREFIX + local.trim('/')
                apart = true
            } else if (root == SHARED_ROOT) {
                // The developer named the Android root directly. Some write the path from the shared
                // storage (`Android/data/<package>/files`), others from `Android/data` (`<package>/files`).
                if (!local.startsWith("Android/")) local = "Android/data/$local"
                apart = true
            } else {
                return@mapNotNull null
            }

            val pattern = file["pattern"].value.orEmpty().ifEmpty { "*" }
            val recursive = file["recursive"].asInteger(0) != 0
            val rule = CachedSaveRule(
                localDir = local.trim('/'),
                pattern = pattern,
                recursive = recursive,
                cloudPrefix = "%$root%$path",
                pcCloudPrefix = if (apart) windowsTwin(files, overrides, pattern, recursive) else null,
            )
            // The name Steam describes comes first, and is the one saves are sent under. A game whose rule reaches Android by a path from `Android/data` may hold older saves in
            // the cloud under the Android root (what an earlier rule of the studio named, and what an earlier GamePort sent): that name comes second, only to read them.
            if (sharedName == null) listOf(rule) else listOf(rule, rule.copy(cloudPrefix = "%$SHARED_ROOT%$sharedName"))
        }.flatten()
    }

    /**
     * The Windows rule kept apart from an Android one: a Windows root, for Windows, with the same pattern, and that no override
     * brings to Android (otherwise it is already the Android rule). Null when the studio has none.
     */
    private fun windowsTwin(files: List<KeyValue>, overrides: List<KeyValue>, pattern: String, recursive: Boolean): String? {
        val candidates = files.filter { file ->
            val root = file["root"].value.orEmpty()
            val platforms = file["platforms"].children.mapNotNull { it.value?.lowercase() }
            root.startsWith("Win", ignoreCase = true) &&
                (platforms.isEmpty() || "windows" in platforms) &&
                overrides.none { it["root"].value == root } &&
                file["pattern"].value.orEmpty().ifEmpty { "*" }.equals(pattern, ignoreCase = true)
        }
        val twin = candidates.firstOrNull { (it["recursive"].asInteger(0) != 0) == recursive } ?: candidates.firstOrNull() ?: return null
        return "%${twin["root"].value}%${twin["path"].value.orEmpty().normalized()}"
    }

    /** An override applies to Android when it names Android, or names another system with `!=` ("every system but that one"). */
    private fun appliesToAndroid(override: KeyValue): Boolean {
        val os = override["os"].value ?: return false
        val equal = override["oscompare"].value?.trim() != "!="
        return os.equals("Android", ignoreCase = true) == equal
    }

    /** Whether the first folder of a path is a package name (`com.studio.game`): letters, digits and underscores, with dots. */
    private fun startsWithPackage(path: String): Boolean {
        val first = path.trim('/').substringBefore('/')
        return '.' in first && first.all { it.isLetterOrDigit() || it == '.' || it == '_' } && first.first().isLetter() && first.last() != '.'
    }

    private fun String.normalized() = replace('\\', '/').trim('/')
}
