package app.gameport.core.steam.session

import app.gameport.core.steam.cache.CachedSaveRule
import `in`.dragonbra.javasteam.types.KeyValue

/**
 * Reads an app's `ufs` section (Steam Cloud auto-sync configuration) into rules the phone can use.
 *
 * Steam names the folders after Windows roots such as `WinAppDataLocalLow`. For Android builds it
 * publishes a root override that moves such a root to `AndroidExternalData` (the shared storage)
 * and rewrites the path, typically into `Android/data/<package>/files`. Rules without an
 * Android mapping cannot be synced and are left out.
 */
internal object SaveRulesParser {
    private const val SUPPORTED_ROOT = "AndroidExternalData"

    fun parse(ufs: KeyValue): List<CachedSaveRule> {
        val overrides = ufs["rootoverrides"].children.filter { it["os"].value.equals("Android", ignoreCase = true) }
        return ufs["savefiles"].children.mapNotNull { file ->
            val platforms = file["platforms"].children.mapNotNull { it.value?.lowercase() }
            if (platforms.isNotEmpty() && "android" !in platforms) return@mapNotNull null

            val root = file["root"].value ?: return@mapNotNull null
            val path = file["path"].value.orEmpty().normalized()
            val override = overrides.firstOrNull { it["root"].value == root }
            var local = path
            if (override != null) {
                if (override["useinstead"].value != SUPPORTED_ROOT) return@mapNotNull null
                override["pathtransforms"].children.forEach { transform ->
                    val find = transform["find"].value.orEmpty().normalized()
                    if (find.isNotEmpty()) local = local.replace(find, transform["replace"].value.orEmpty().normalized())
                }
                val added = override["addpath"].value.orEmpty().normalized()
                if (added.isNotEmpty()) local = if (local.isEmpty()) added else "$added/$local"
            } else if (root == SUPPORTED_ROOT) {
                // The developer named the Android root directly. Some write the path from the shared
                // storage (`Android/data/<package>/files`), others from `Android/data` (`<package>/files`).
                if (!local.startsWith("Android/")) local = "Android/data/$local"
            } else {
                return@mapNotNull null
            }

            CachedSaveRule(
                localDir = local.trim('/'),
                pattern = file["pattern"].value.orEmpty().ifEmpty { "*" },
                recursive = file["recursive"].asInteger(0) != 0,
                cloudPrefix = "%$root%$path",
            )
        }
    }

    private fun String.normalized() = replace('\\', '/').trim('/')
}
