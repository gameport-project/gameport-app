package app.gameport.core.model

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CatalogTextsTest {
    @Test
    fun `a file of texts is read as the catalog repository writes it`() {
        val texts = CatalogTexts.parse("""{"v":1,"lang":"fr","tags":{"19":"Action"},"summaries":{"5":"Un jeu"},"more":1}""")!!
        assertEquals("Action", texts.tagName(19))
        assertEquals("Un jeu", texts.summary(5))
        assertNull(texts.summary(6))
        assertNull(texts.tagName(20))
    }

    @Test
    fun `a file that is not texts is refused`() {
        assertNull(CatalogTexts.parse(null))
        assertNull(CatalogTexts.parse("not json"))
        assertNull(CatalogTexts.parse("""{"v":2,"lang":"fr"}"""))
    }

    @Test
    fun `the language of the device falls on a file of the catalog`() {
        val cases = mapOf(
            Locale.FRANCE to "fr", Locale.US to "en", Locale.GERMANY to "de", Locale.JAPAN to "ja", Locale.KOREA to "ko",
            Locale("pt", "BR") to "pt-BR", Locale("pt", "PT") to "pt", Locale("es", "ES") to "es", Locale("es", "MX") to "es-419", Locale("es", "419") to "es-419",
            Locale.SIMPLIFIED_CHINESE to "zh-CN", Locale.TRADITIONAL_CHINESE to "zh-TW", Locale("zh", "HK") to "zh-TW",
            Locale("nb", "NO") to "no", Locale("in", "ID") to "id", Locale("sw", "KE") to "en",
        )
        cases.forEach { (locale, code) -> assertEquals(locale.toString(), code, CatalogLanguages.of(locale)) }
    }

    @Test
    fun `every code the device can fall on is a language of the catalog`() {
        val all = Locale.getAvailableLocales().map { CatalogLanguages.of(it) }.toSet()
        assertEquals(emptySet<String>(), all - CatalogLanguages.codes.toSet())
    }
}
