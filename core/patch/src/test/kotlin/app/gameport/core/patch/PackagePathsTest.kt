package app.gameport.core.patch

import app.gameport.core.patch.patches.PackagePaths
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Random
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PackagePathsTest {
    private val came = "com.studio.EscapeGame"
    private val renamed = "com.gameport.g1435790"

    /** The string literals of an IL2CPP game sit one after the other, with no separator between them: this is how they are laid out. */
    private fun literals(vararg parts: String) = ("\u0000\u0001" + parts.joinToString("") + "\u0002").toByteArray(Charsets.ISO_8859_1)

    private fun text(bytes: ByteArray) = String(bytes, Charsets.ISO_8859_1)

    @Test
    fun `the size of the file never changes and the name is written over the old one behind slashes`() {
        val content = literals("/storage/emulated/0/Android/obb/$came/assets", "Application.persistentDataPath")
        val size = content.size
        val outcome = PackagePaths.rewrite(content, listOf(came), renamed)
        assertEquals(1, outcome.rewritten)
        assertEquals(size, content.size)
        val pad = came.length - renamed.length
        assertTrue(text(content).contains("/storage/emulated/0/Android/obb/" + "/".repeat(pad) + "$renamed/assets"))
        assertTrue(text(content).contains("Application.persistentDataPath"))
    }

    @Test
    fun `every path of the game is changed, in each folder that holds a package's files`() {
        val content = literals("/sdcard/Android/obb/$came", "/storage/emulated/0/Android/obb/$came/assets", "/sdcard/Android/data/$came/files", "/data/data/$came/cache", "/data/user/0/$came/x")
        val outcome = PackagePaths.rewrite(content, listOf(came), renamed)
        assertEquals(5, outcome.rewritten)
        assertFalse(text(content).contains(came))
    }

    @Test
    fun `the same word anywhere else is the game's own and is left alone`() {
        val content = literals("com.studio.EscapeGame", "id=$came;", "package: $came", "store/$came/page")
        val before = content.copyOf()
        assertEquals(0, PackagePaths.rewrite(content, listOf(came), renamed).rewritten)
        assertArrayEquals(before, content)
    }

    @Test
    fun `a longer name that begins with the old one is not the old one`() {
        val content = literals("/sdcard/Android/obb/${came}2/assets", "/sdcard/Android/obb/$came.Sub/assets", "/sdcard/Android/obb/${came}_x")
        val before = content.copyOf()
        assertEquals(0, PackagePaths.rewrite(content, listOf(came), renamed).rewritten)
        assertArrayEquals(before, content)
    }

    @Test
    fun `a name at the very end of the file is found`() {
        val content = ("x/Android/obb/$came").toByteArray(Charsets.ISO_8859_1)
        assertEquals(1, PackagePaths.rewrite(content, listOf(came), renamed).rewritten)
        assertFalse(text(content).contains(came))
    }

    @Test
    fun `a new name longer than what stands there leaves the path as it was and says so`() {
        val short = "com.a.b"
        val content = literals("/sdcard/Android/obb/$short/assets")
        val before = content.copyOf()
        val outcome = PackagePaths.rewrite(content, listOf(short), renamed)
        assertEquals(0, outcome.rewritten)
        assertEquals(1, outcome.tooShort)
        assertArrayEquals(before, content)
    }

    @Test
    fun `doing it again changes nothing`() {
        val content = literals("/sdcard/Android/obb/$came/assets")
        PackagePaths.rewrite(content, listOf(came), renamed)
        val once = content.copyOf()
        assertEquals(0, PackagePaths.rewrite(content, listOf(came), renamed).rewritten)
        assertEquals(0, PackagePaths.rewrite(content, listOf(renamed), renamed).rewritten)
        assertArrayEquals(once, content)
    }

    @Test
    fun `putting the game back under its own name restores the file byte for byte`() {
        val content = literals("/sdcard/Android/obb/$came", "/storage/emulated/0/Android/obb/$came/assets", "Photon $came")
        val original = content.copyOf()
        PackagePaths.rewrite(content, listOf(came), renamed)
        assertFalse(content.contentEquals(original))
        // The names looked for are the one it came with and the one it carries now.
        val outcome = PackagePaths.rewrite(content, listOf(came, renamed), came)
        assertEquals(2, outcome.rewritten)
        assertArrayEquals(original, content)
    }

    @Test
    fun `moving from one name of GamePort to another keeps the slot and the size`() {
        val content = literals("/sdcard/Android/obb/$came/assets")
        val size = content.size
        PackagePaths.rewrite(content, listOf(came), renamed)
        val other = "com.gameport.g42"
        assertEquals(1, PackagePaths.rewrite(content, listOf(came, renamed), other).rewritten)
        assertEquals(size, content.size)
        assertTrue(text(content).contains("obb/" + "/".repeat(came.length - other.length) + other + "/assets"))
    }

    @Test
    fun `the names of a Mono game are found in UTF-16 text`() {
        val content = ("\u0003/sdcard/Android/obb/$came/assets\u0001").toByteArray(Charsets.UTF_16LE)
        val size = content.size
        val outcome = PackagePaths.rewrite(content, listOf(came), renamed, wide = true)
        assertEquals(1, outcome.rewritten)
        assertEquals(size, content.size)
        val decoded = String(content, Charsets.UTF_16LE)
        assertTrue(decoded.contains("obb/" + "/".repeat(came.length - renamed.length) + renamed + "/assets"))
        assertFalse(decoded.contains(came))
    }

    @Test
    fun `UTF-16 text is not looked for as narrow text, nor the other way round`() {
        val wideText = ("/sdcard/Android/obb/$came").toByteArray(Charsets.UTF_16LE)
        assertEquals(0, PackagePaths.rewrite(wideText.copyOf(), listOf(came), renamed, wide = false).rewritten)
        val narrowText = ("/sdcard/Android/obb/$came").toByteArray(Charsets.ISO_8859_1)
        assertEquals(0, PackagePaths.rewrite(narrowText.copyOf(), listOf(came), renamed, wide = true).rewritten)
    }

    @Test
    fun `an empty file, no names, or the target as the only name are handled`() {
        assertEquals(0, PackagePaths.rewrite(ByteArray(0), listOf(came), renamed).rewritten)
        assertEquals(0, PackagePaths.rewrite(literals("/sdcard/Android/obb/$came"), emptyList(), renamed).rewritten)
        assertEquals(0, PackagePaths.rewrite(literals("/sdcard/Android/obb/$came"), listOf(came), came).rewritten)
    }

    @Test
    fun `a prefix cut by the end of the file is not an error`() {
        assertEquals(0, PackagePaths.rewrite("Android/obb/".toByteArray(), listOf(came), renamed).rewritten)
        assertEquals(0, PackagePaths.rewrite("Android/ob".toByteArray(), listOf(came), renamed).rewritten)
    }

    /** A file of [size] bytes of noise with a path written at each of [offsets]. */
    private fun noisy(size: Int, offsets: List<Int>, wide: Boolean = false): ByteArray {
        val random = Random(7)
        val data = ByteArray(size).also { random.nextBytes(it) }
        // Noise that cannot begin a path: no 'A', no 'd'.
        for (i in data.indices) if (data[i] == 'A'.code.toByte() || data[i] == 'd'.code.toByte()) data[i] = 0x01
        for (offset in offsets) {
            val path = "/storage/emulated/0/Android/obb/$came/assets".toByteArray(if (wide) Charsets.UTF_16LE else Charsets.ISO_8859_1)
            path.copyInto(data, offset)
        }
        return data
    }

    private fun stream(data: ByteArray, chunk: Int, wide: Boolean = false): Pair<ByteArray, PackagePaths.Outcome> {
        val out = ByteArrayOutputStream()
        val outcome = PackagePaths.rewriteStream(ByteArrayInputStream(data), out, listOf(came), renamed, wide, chunk)
        return out.toByteArray() to outcome
    }

    @Test
    fun `a stream pass gives what a pass over the whole file gives, wherever the windows cut`() {
        // Paths at many places, none over another (a path takes 62 bytes), some across the edge of a window (the windows of 700 + 512 bytes end near 1212, 1912, ...).
        val offsets = listOf(0, 300, 640, 720, 1150, 1230, 1900, 1990, 2610, 3500, 4200, 4950)
        val data = noisy(5200, offsets)
        val whole = data.copyOf()
        val expected = PackagePaths.rewrite(whole, listOf(came), renamed)
        for (chunk in listOf(1, 64, 700, 1000, 5200, 10_000)) {
            val (written, outcome) = stream(data, chunk)
            assertEquals("chunk $chunk: the same paths", expected, outcome)
            assertArrayEquals("chunk $chunk: the same bytes", whole, written)
            assertEquals("chunk $chunk: the same size", data.size, written.size)
        }
        assertEquals(offsets.size, expected.rewritten)
    }

    @Test
    fun `a stream pass handles UTF-16 text as the whole pass does`() {
        val data = noisy(4000, listOf(10, 800, 1500, 2203, 3100), wide = true)
        val whole = data.copyOf()
        val expected = PackagePaths.rewrite(whole, listOf(came), renamed, wide = true)
        val (written, outcome) = stream(data, 640, wide = true)
        assertEquals(expected, outcome)
        assertArrayEquals(whole, written)
    }

    @Test
    fun `a stream pass over an empty input writes nothing`() {
        val (written, outcome) = stream(ByteArray(0), 100)
        assertEquals(0, written.size)
        assertEquals(PackagePaths.Outcome(0, 0), outcome)
    }

    @Test
    fun `a stream pass over a file with no path writes it as it was`() {
        val data = noisy(3000, emptyList())
        val (written, outcome) = stream(data, 500)
        assertArrayEquals(data, written)
        assertEquals(0, outcome.rewritten)
    }

    @Test
    fun `a stream pass that is run again changes nothing`() {
        val data = noisy(4000, listOf(100, 1000, 2000))
        val (first, _) = stream(data, 700)
        val out = ByteArrayOutputStream()
        val again = PackagePaths.rewriteStream(ByteArrayInputStream(first), out, listOf(came), renamed, false, 700)
        assertEquals(0, again.rewritten)
        assertArrayEquals(first, out.toByteArray())
    }

    @Test
    fun `a stream pass puts the game back under its own name byte for byte`() {
        val data = noisy(4000, listOf(100, 1000, 2000, 3900))
        val (renamedBytes, _) = stream(data, 700)
        val out = ByteArrayOutputStream()
        PackagePaths.rewriteStream(ByteArrayInputStream(renamedBytes), out, listOf(came, renamed), came, false, 700)
        assertArrayEquals(data, out.toByteArray())
    }
}
