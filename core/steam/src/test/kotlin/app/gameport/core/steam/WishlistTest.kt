package app.gameport.core.steam

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WishlistTest {
    @Test
    fun `a plain success is added`() {
        assertEquals(WishlistOutcome.Added, wishlistOutcome(200, """{"success":true,"wishlistCount":12}"""))
        assertEquals(WishlistOutcome.Added, wishlistOutcome(200, """{"success":1}"""))
    }

    @Test
    fun `a plain success of a removal is removed, a refusal is still a failure`() {
        assertEquals(WishlistOutcome.Removed, wishlistOutcome(200, """{"success":true,"wishlistCount":11}""", WishlistOutcome.Removed))
        assertEquals(WishlistOutcome.Failed, wishlistOutcome(200, """{"success":false}""", WishlistOutcome.Removed))
    }

    @Test
    fun `a refusal, a page to sign in or a bad status is a failure`() {
        assertEquals(WishlistOutcome.Failed, wishlistOutcome(200, """{"success":false}"""))
        assertEquals(WishlistOutcome.Failed, wishlistOutcome(200, "<html>Sign in</html>"))
        assertEquals(WishlistOutcome.Failed, wishlistOutcome(403, """{"success":true}"""))
        assertEquals(WishlistOutcome.Failed, wishlistOutcome(200, ""))
    }

    @Test
    fun `the apps of a wishlist are read from its items`() {
        assertEquals(setOf(10, 20), wishlistApps("""{"response":{"items":[{"appid":10,"priority":1},{"appid":20,"priority":2}]}}"""))
    }

    @Test
    fun `an empty wishlist is an empty set, not an unknown answer`() {
        assertEquals(emptySet<Int>(), wishlistApps("""{"response":{}}"""))
    }

    @Test
    fun `an answer that is not a wishlist is unknown`() {
        assertNull(wishlistApps("<html></html>"))
        assertNull(wishlistApps("""{"success":true}"""))
    }
}
