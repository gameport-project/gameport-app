package app.gameport.core.model

/** Settings shared by every game unless a game has its own. */
data class PlayerDefaults(
    val heightCm: Int = DEFAULT_HEIGHT_CM,
) {
    companion object {
        const val DEFAULT_HEIGHT_CM = 170
        val HEIGHT_RANGE_CM = 120..220
    }
}

/** What the player chose for one game. */
data class GameSettings(
    /** Play seated while the game believes the player stands. */
    val seated: Boolean = false,
    /** This game's own height; null means the game follows [PlayerDefaults]. */
    val heightCm: Int? = null,
) {
    val followsDefaults: Boolean get() = heightCm == null

    fun effectiveHeightCm(defaults: PlayerDefaults): Int = heightCm ?: defaults.heightCm

    /** Standing eye height: the eyes sit about 12 cm below the top of the head. */
    fun eyeHeightCm(defaults: PlayerDefaults): Int = effectiveHeightCm(defaults) - EYE_BELOW_TOP_OF_HEAD_CM

    private companion object {
        const val EYE_BELOW_TOP_OF_HEAD_CM = 12
    }
}
