package app.gameport.core.sync

import app.gameport.core.model.SaveRule

/**
 * Converts between Steam's cloud names and paths on the headset, using a game's [SaveRule]s.
 * The tokens in a rule stand for the signed-in account and are replaced first.
 */
class CloudPaths(rules: List<SaveRule>, steamId64: Long, accountId: Long) {
    private val rules = rules.map { rule ->
        rule.copy(
            localDir = rule.localDir.substitute(steamId64, accountId),
            cloudPrefix = rule.cloudPrefix.substitute(steamId64, accountId),
        )
    }

    /** The rules with the account tokens filled in, as the in-game hook needs them to scan. */
    fun expandedRules(): List<SaveRule> = rules

    /** Path on the headset for a cloud file name, or null if no rule covers it. */
    fun toLocal(cloudName: String): String? {
        val rule = rules.filter { matches(it, cloudName) }.maxByOrNull { it.cloudPrefix.length } ?: return null
        val remainder = cloudName.removePrefix(rule.cloudPrefix).trimStart('/')
        return if (rule.localDir.isEmpty()) remainder else "${rule.localDir}/$remainder"
    }

    /** Cloud name for a local file, or null if it is not a save file under any rule. */
    fun toCloud(rel: String): String? {
        val rule = rules.filter { covers(it, rel) }.maxByOrNull { it.localDir.length } ?: return null
        val remainder = rel.removePrefix(rule.localDir).trimStart('/')
        return when {
            rule.cloudPrefix.isEmpty() -> remainder
            rule.cloudPrefix.endsWith("%") -> rule.cloudPrefix + remainder
            else -> "${rule.cloudPrefix}/$remainder"
        }
    }

    /**
     * A rule with no prefix stands for the files a game writes through Steam's cloud API: their
     * cloud names are the plain names the game chose, while Auto-Cloud names start with `%`.
     */
    private fun matches(rule: SaveRule, cloudName: String): Boolean = when {
        rule.cloudPrefix.isEmpty() -> !cloudName.startsWith("%")
        else -> cloudName == rule.cloudPrefix || cloudName.startsWith(rule.cloudPrefix.trimEnd('/') + "/") ||
            (rule.cloudPrefix.endsWith("%") && cloudName.startsWith(rule.cloudPrefix))
    }

    private fun covers(rule: SaveRule, rel: String): Boolean {
        val inside = if (rule.localDir.isEmpty()) rel else if (rel.startsWith(rule.localDir + "/")) rel.removePrefix(rule.localDir + "/") else return false
        if (!rule.recursive && '/' in inside) return false
        return Glob.matches(rule.pattern, inside.substringAfterLast('/'))
    }

    private fun String.substitute(steamId64: Long, accountId: Long) =
        replace("{64BitSteamID}", steamId64.toString()).replace("{Steam3AccountID}", accountId.toString())
}

/** Steam's simple file patterns: `*` and `?` wildcards, case-insensitive. */
object Glob {
    fun matches(pattern: String, name: String): Boolean {
        val regex = buildString {
            append("(?i)^")
            pattern.forEach { c ->
                when (c) {
                    '*' -> append(".*")
                    '?' -> append('.')
                    else -> append(Regex.escape(c.toString()))
                }
            }
            append('$')
        }
        return Regex(regex).matches(name)
    }
}
