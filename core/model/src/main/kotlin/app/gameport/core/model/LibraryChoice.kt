package app.gameport.core.model

/**
 * Which native libraries of a game go with the report a player chooses to send them with. The relay takes a zip of [MAX_ZIP_BYTES] at most, the report itself takes a small part of it,
 * and the libraries of an engine (several tens of megabytes) are not what is looked for: the smallest ones are taken first, as long as they fit in [BUDGET_BYTES], and the others are
 * only named in the report, so the file says what it does not hold.
 */
object LibraryChoice {
    /** What the relay accepts for a set of libraries (see its README). */
    const val MAX_ZIP_BYTES = 8_000_000

    /** What the libraries may take of that, as the size the APK keeps them in: the rest of the zip is the report, which stays far under a megabyte. */
    const val BUDGET_BYTES = 7_000_000L

    /** One library of the game's APK: its path inside it, its size, and the size the APK keeps it in (what it will take in the zip, about). */
    data class Library(val path: String, val size: Long, val stored: Long)

    /** The libraries that go, and the ones that do not. */
    data class Pick(val included: List<Library>, val left: List<Library>) {
        /** What the included libraries take, about, in the zip. */
        val bytes: Long get() = included.sumOf { it.stored }
    }

    /**
     * What GamePort puts in the game itself: its OpenXR layer, the loaders it brings, and its stand-in for Steam, which takes the name libsteamclient.so. They are ours and the
     * same in every game, so they are not what a report about a game is sent with (and they would take the room of the game's own libraries).
     */
    private val GAMEPORT_OWN = setOf("libXrApiLayer_gameport.so", "libopenxr_loader.so", "libopenxr_loader_meta.so", "libsteamclient.so")

    /** Words in the name of a library that ties it to the headset's platform or to Steam: what a failure to start is most often about. */
    private val PLATFORM_WORDS = listOf("pico", "pxr", "oculus", "ovr", "openxr", "steam", "tobapi")

    /** Whether a library is one of GamePort's own. */
    fun isGamePortOwn(path: String): Boolean = path.substringAfterLast('/') in GAMEPORT_OWN

    private fun isPlatform(path: String): Boolean = path.substringAfterLast('/').lowercase().let { name -> PLATFORM_WORDS.any { it in name } }

    /** The game's libraries of the ABI the device runs (not GamePort's own): the 64-bit ones when the game has them, else the 32-bit ones, else none. */
    fun ofDeviceAbi(entries: List<Library>): List<Library> {
        for (abi in ABIS) {
            val own = entries.filter { it.path.startsWith("lib/$abi/") && it.path.endsWith(".so") && !isGamePortOwn(it.path) }
            if (own.isNotEmpty()) return own
        }
        return emptyList()
    }

    /**
     * The libraries of the platform and of Steam first, the others after them, each group from the smallest, while they fit in [budget]: one that does not fit is left out and the
     * next is tried. The included ones are listed in the order of their paths.
     */
    fun pick(libraries: List<Library>, budget: Long = BUDGET_BYTES): Pick {
        val ordered = libraries.sortedWith(compareBy<Library>({ !isPlatform(it.path) }, { it.stored }, { it.path }))
        var used = 0L
        val included = mutableListOf<Library>()
        for (library in ordered) {
            if (used + library.stored > budget) continue
            used += library.stored
            included += library
        }
        val left = ordered.filter { it !in included }
        return Pick(included.sortedBy { it.path }, left.sortedBy { it.path })
    }

    /** The same choice without the biggest included library: what is tried again when the zip came out larger than the relay takes. */
    fun withoutBiggest(pick: Pick): Pick? {
        val biggest = pick.included.maxWithOrNull(compareBy({ it.stored }, { it.path })) ?: return null
        return Pick(pick.included - biggest, (pick.left + biggest).sortedBy { it.path })
    }

    private val ABIS = listOf("arm64-v8a", "armeabi-v7a")
}
