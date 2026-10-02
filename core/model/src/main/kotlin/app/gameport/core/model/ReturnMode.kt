package app.gameport.core.model

/**
 * For which games GamePort opens again once the game is closed. A game started from the headset's library leaves GamePort running
 * in the background anyway, without its screen: that is what [APP], the default, leaves it as.
 */
enum class ReturnMode(val id: String) {
    NEVER("never"),

    /** Only a game started from GamePort. */
    APP("app"),

    /** Only a game started from the headset's library, or from anywhere but GamePort. */
    LIBRARY("library"),

    ALL("all"),
    ;

    companion object {
        fun ofId(id: String?): ReturnMode? = entries.firstOrNull { it.id == id }
    }
}
