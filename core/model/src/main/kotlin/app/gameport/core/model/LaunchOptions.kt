package app.gameport.core.model

/**
 * One way Steam says a game can be started: the arguments it is given, and what its developer calls that way, when they say.
 * [android] is true when Steam lists it for the Android start of the game and not for another system's.
 */
data class LaunchOption(val arguments: String, val description: String?, val vr: Boolean, val android: Boolean = false)

/**
 * The arguments a game is started with. A Unity game reads them from the `unity` extra of the intent that starts it, and some decide from them
 * whether to run in VR: they look for a sign of it, find none and stop the VR after their logo, which leaves a black screen. Steam lists the
 * arguments of a game with the kind of start each belongs to, so the ones meant for VR can be given without the player having to know them.
 */
object LaunchOptions {
    const val UNITY_EXTRA = "unity"

    private val VR_TYPES = setOf("vr", "openxr", "openvr")
    private val VR_WORD = Regex("""\bvr\b""", RegexOption.IGNORE_CASE)

    /** Words that turn "VR" into its opposite: "Play without VR", "Non-VR mode", "Flat screen", "No VR". */
    private val NOT_VR = Regex("""\b(no|non|not|without|sans)[\s\-_]*(vr|hmd|headset)\b|\b(flat|desktop|2d|monitor)\b|\bno[\-_]?vr\b|\bnohmd\b""", RegexOption.IGNORE_CASE)

    /**
     * True when an entry of Steam's list is meant for VR. Its type says so when it has one that Steam uses for VR (any other type, such as the
     * default one, says it is not). Without a type the developer's words decide, unless they negate it, and so do the arguments themselves.
     */
    fun isVr(type: String?, description: String?, arguments: String? = null): Boolean {
        val kind = type?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
        if (kind != null) return kind in VR_TYPES
        if (arguments?.let(NOT_VR::containsMatchIn) == true) return false
        val words = description ?: return false
        return VR_WORD.containsMatchIn(words) && !NOT_VR.containsMatchIn(words)
    }

    /** A bare switch for VR (`-vr`, `--vr`, `vrmode`, `-xr`): the whole of the arguments, and nothing else that depends on a system or a runtime. */
    private val VR_SWITCH = Regex("""-{0,2}(vr|xr|vr_?mode)""", RegexOption.IGNORE_CASE)

    /**
     * True when an option can be given without the player knowing it. Steam files under VR arguments that pick a runtime (`forceSteam`), a graphics
     * API (`-d3d11`), a window or capture (`-windowed`, `-mrc`): those belong to the Windows start of the game and could break the Android one, so
     * they are offered but never picked alone. What is picked is an option meant for VR whose arguments are only a switch for it, or that Steam
     * lists for the Android start.
     */
    fun isSuggested(option: LaunchOption): Boolean =
        option.vr && (option.android || option.arguments.split(' ').filter { it.isNotEmpty() }.let { tokens -> tokens.isNotEmpty() && tokens.all(VR_SWITCH::matches) })

    /** What a game is given when the player has not chosen: the arguments of the options that can be given without asking. */
    fun preselected(options: List<LaunchOption>): Set<String> = options.filter(::isSuggested).map { it.arguments }.toSet()

    /**
     * The arguments to start a game with, in the order Steam lists them. [picked] is the player's choice (null when there is none), which wins;
     * without one a game that runs in VR gets the options that can be given without asking, and a flat game gets nothing.
     */
    fun chosen(options: List<LaunchOption>, picked: Set<String>?, isVr: Boolean?): List<String> {
        val wanted = picked ?: if (isVr == false) emptySet() else preselected(options)
        return options.map { it.arguments }.filter { it in wanted }
    }

    /**
     * Splits what Steam gives an entry into the flags it holds: `-vr -dx11` is `-vr` and `-dx11`, while `-game x` stays whole, since `x` is the value
     * of `-game`. Arguments that start with no flag stay as they are.
     */
    fun flags(arguments: String): List<String> {
        val tokens = arguments.split(' ').filter { it.isNotEmpty() }
        val out = mutableListOf<String>()
        tokens.forEach { token ->
            if (token.startsWith("-") || out.isEmpty()) out += token else out[out.lastIndex] = out.last() + " " + token
        }
        return out
    }

    /** What the `unity` extra holds for [arguments], or null when there is nothing to pass. */
    fun unityExtra(arguments: List<String>): String? = arguments.joinToString(" ").takeIf { it.isNotBlank() }
}
