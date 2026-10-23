package app.gameport.core.model

/** The guided tours there are, one per page; each is started from Settings > Help or from the help button of its page. */
enum class TourKind {
    HOME,
    STORE,
    STORE_GAME,
    COMPAT_TABLE,
    DOWNLOADS,
    GAME,
    GAME_COMPAT,
    GAME_CONTROLLERS,
    GAME_SAVES,
    SETTINGS,
    ;

    /**
     * The tour runs on a page of made-up values, not on the real one: a game's page shows only what applies to the game and a first launch has none, while the
     * tour must show every button and every notice.
     */
    val onDemoPage: Boolean get() = this != HOME && this != STORE && this != COMPAT_TABLE && this != SETTINGS

    companion object {
        /** The complete tour: every page, in the order a player meets them. */
        val COMPLETE: List<TourKind> = entries.toList()
    }
}
