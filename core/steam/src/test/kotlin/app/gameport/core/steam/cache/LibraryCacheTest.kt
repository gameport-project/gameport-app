package app.gameport.core.steam.cache

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryCacheTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val wrong = CachedSaveRule("com.tvb.cubism/files", "*.save", true, "%WinAppDataLocalLow%tvb/cubism")
    private val right = CachedSaveRule("Android/data/com.tvb.cubism/files", "*.save", true, "%AndroidExternalData%com.tvb.cubism/files")
    private val windows = right.copy(cloudPrefix = "%WinAppDataLocalLow%tvb/cubism")

    @Test
    fun `rules read the same way are no change`() {
        assertEquals(false, SaveRulesChange.differ(listOf(right, windows), listOf(right, windows)))
    }

    @Test
    fun `the order of the rules is no change`() {
        assertEquals(false, SaveRulesChange.differ(listOf(right, windows), listOf(windows, right)))
    }

    @Test
    fun `rules read another way are a change`() {
        assertEquals(true, SaveRulesChange.differ(listOf(wrong), listOf(right, windows)))
    }

    @Test
    fun `a game with no rules before and some now is a change, a game never seen is not`() {
        assertEquals(true, SaveRulesChange.differ(emptyList(), listOf(right)))
        assertEquals(false, SaveRulesChange.differ(null, listOf(right)))
    }

    @Test
    fun `a game that keeps no rules is no change`() {
        assertEquals(false, SaveRulesChange.differ(emptyList(), emptyList()))
    }

    @Test
    fun `a cache of an earlier version is read as it was written, with nothing noted`() {
        val old = """{"version":11,"savedAt":5,"packageApps":{"1":[2]},"checkedApps":[2],"games":[]}"""

        val cache = decodeLibraryCache(json, old)!!

        assertEquals(11, cache.version)
        assertEquals(emptySet<Int>(), cache.rulesChanged)
    }

    @Test
    fun `the games noted survive a write and a read`() {
        val cache = CachedLibrary(CACHE_VERSION, 7, emptyMap(), emptySet(), emptyList(), rulesChanged = setOf(804530, 3530820))

        val back = decodeLibraryCache(json, json.encodeToString(CachedLibrary.serializer(), cache))!!

        assertEquals(setOf(804530, 3530820), back.rulesChanged)
        assertTrue(back.version == CACHE_VERSION)
    }

    @Test
    fun `text that is not a cache is read as nothing`() {
        assertNull(decodeLibraryCache(json, "not json"))
        assertNull(decodeLibraryCache(json, ""))
    }
}
