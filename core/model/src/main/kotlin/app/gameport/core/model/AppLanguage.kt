package app.gameport.core.model

/**
 * The language of GamePort's texts. [AUTO] follows the device; the others are the languages the app is
 * translated into, each named in its own language so it can be found whatever language is shown now.
 */
enum class AppLanguage(val tag: String?, val nativeName: String?) {
    AUTO(null, null),
    FRENCH("fr", "Français"),
    ENGLISH("en", "English"),
    ;

    companion object {
        fun ofTag(tag: String?): AppLanguage = entries.firstOrNull { it.tag == tag } ?: AUTO
    }
}
