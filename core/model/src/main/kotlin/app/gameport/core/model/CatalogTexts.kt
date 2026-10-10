package app.gameport.core.model

import java.util.Locale
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** The texts of the catalog in one language: the name of each tag and the short description of each app (see `text/` in the catalog repository). */
@Serializable
data class CatalogTexts(
    val v: Int = 1,
    val lang: String = "en",
    val tags: Map<String, String> = emptyMap(),
    val summaries: Map<String, String> = emptyMap(),
) {
    fun summary(appId: Int): String? = summaries[appId.toString()]

    fun tagName(tagId: Int): String? = tags[tagId.toString()]

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(text: String?): CatalogTexts? =
            runCatching { json.decodeFromString(serializer(), text.orEmpty()) }.getOrNull()?.takeIf { it.v == 1 && it.lang.isNotBlank() }
    }
}

/** The languages the catalog has texts in, by the codes of its files, and which of them a device language falls on. */
object CatalogLanguages {
    val codes = listOf(
        "ar", "bg", "zh-CN", "zh-TW", "cs", "da", "nl", "en", "fi", "fr", "de", "el", "hu", "id", "it", "ja", "ko", "no", "pl", "pt", "pt-BR", "ro", "ru",
        "es-419", "es", "sv", "th", "tr", "uk", "vi",
    )

    private val latinAmerica = setOf("419", "AR", "BO", "CL", "CO", "CR", "CU", "DO", "EC", "SV", "GT", "HN", "MX", "NI", "PA", "PY", "PE", "PR", "UY", "VE")
    private val traditionalChinese = setOf("TW", "HK", "MO")

    /** The code of the file to read for [locale]; English when the catalog has none in that language. */
    fun of(locale: Locale): String = when (locale.language) {
        "zh" -> if (locale.country in traditionalChinese || locale.script == "Hant") "zh-TW" else "zh-CN"
        "pt" -> if (locale.country == "BR") "pt-BR" else "pt"
        "es" -> if (locale.country in latinAmerica) "es-419" else "es"
        "nb", "nn", "no" -> "no"
        // Java still calls Indonesian "in".
        "in" -> "id"
        else -> locale.language.takeIf { it in codes } ?: "en"
    }
}
