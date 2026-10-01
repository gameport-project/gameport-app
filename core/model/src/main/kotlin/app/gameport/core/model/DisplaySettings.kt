package app.gameport.core.model

/** How the library orders its "all games" row. */
enum class LibrarySort { NAME, RECENTLY_PLAYED, RECENTLY_INSTALLED }

/** The width of a cover, in dp. */
enum class CoverSize(val dp: Int) { MINI(110), SMALL(140), MEDIUM(190), LARGE(240) }

/** The gap between two covers, in dp. */
enum class CoverSpacing(val dp: Int) { COMPACT(4), TIGHT(10), NORMAL(20), AIRY(34) }

/** How much a highlighted cover moves: it zooms and a ring drifts out around it; people sensitive to motion can reduce or cut it. */
enum class HoverAnimation { FULL, REDUCED, NONE }

/** The colours offered for the buttons, switches and highlights; the player can also pick any other. */
object AccentPresets {
    const val DEFAULT: Long = 0xFFF5F5F7
    val colors: List<Long> = listOf(
        DEFAULT,
        0xFFE53935, // red
        0xFFFB8C00, // orange
        0xFFFDD835, // yellow
        0xFF43A047, // green
        0xFF00ACC1, // cyan
        0xFF1E88E5, // blue
        0xFF8E24AA, // purple
        0xFFD81B60, // pink
    )
}

/** The size of the name pill above a highlighted cover: [slotDp] is the room kept for it above the covers. */
enum class PillSize(val textSp: Int, val iconDp: Int, val slotDp: Int) {
    SMALL(11, 14, 28),
    MEDIUM(12, 18, 34),
    LARGE(15, 22, 42),
}

/** The two colours of the gradient behind every page, which games without artwork also show. */
object BackdropPresets {
    const val DEFAULT_START: Long = 0xFF222932
    const val DEFAULT_END: Long = 0xFF3C4C7A

    /** Start and end colours. */
    val pairs: List<Pair<Long, Long>> = listOf(
        DEFAULT_START to DEFAULT_END, // slate to blue
        0xFF0B0F1A to 0xFF1B2A4A, // night
        0xFF1A1033 to 0xFF4A2C7A, // violet
        0xFF0F2A24 to 0xFF1F5C4A, // forest
        0xFF2B1410 to 0xFF7A3B24, // ember
        0xFF101820 to 0xFF0E5A73, // ocean
        0xFF000000 to 0xFF1A1A1A, // black
    )
}

/** Which games the home's "continue" row lists on a headset: those of both tabs, or only those of the tab shown. */
enum class ContinueScope { ALL, TAB }

/** Everything the player can change about how the app looks and what the library shows. */
data class DisplaySettings(
    /** The selected game's artwork behind the library, and how visible it is (0 dark and blurred, 100 clear). */
    val backdrop: Boolean = true,
    val backdropStrength: Int = DEFAULT_BACKDROP_STRENGTH,
    /** ARGB of the two ends of the gradient behind the pages. */
    val gradientStart: Long = BackdropPresets.DEFAULT_START,
    val gradientEnd: Long = BackdropPresets.DEFAULT_END,
    val coverTitles: Boolean = false,
    val coverSize: CoverSize = CoverSize.SMALL,
    val coverSpacing: CoverSpacing = CoverSpacing.TIGHT,
    val hoverAnimation: HoverAnimation = HoverAnimation.FULL,
    /** ARGB of the accent colour. */
    val accent: Long = AccentPresets.DEFAULT,
    /** The pill above the highlighted cover: the game's name and a play icon. */
    val heroBanner: Boolean = true,
    val pillSize: PillSize = PillSize.MEDIUM,
    val continueScope: ContinueScope = ContinueScope.ALL,
    val showContinue: Boolean = true,
    val showFavorites: Boolean = true,
    val hideUninstalled: Boolean = false,
    val sort: LibrarySort = LibrarySort.NAME,
) {
    /** The look settings, back to the creator's defaults; the home's own settings are kept. */
    fun withDefaultAppearance(): DisplaySettings {
        val d = DisplaySettings()
        return copy(
            backdrop = d.backdrop, backdropStrength = d.backdropStrength, gradientStart = d.gradientStart, gradientEnd = d.gradientEnd,
            coverSize = d.coverSize, coverSpacing = d.coverSpacing, hoverAnimation = d.hoverAnimation, accent = d.accent,
        )
    }

    /** The home's settings, back to the creator's defaults; the look settings are kept. */
    fun withDefaultHome(): DisplaySettings {
        val d = DisplaySettings()
        return copy(
            heroBanner = d.heroBanner, pillSize = d.pillSize, coverTitles = d.coverTitles, continueScope = d.continueScope,
            showContinue = d.showContinue, showFavorites = d.showFavorites, hideUninstalled = d.hideUninstalled, sort = d.sort,
        )
    }

    companion object {
        /** The look before these settings existed. */
        const val DEFAULT_BACKDROP_STRENGTH = 50
    }
}
