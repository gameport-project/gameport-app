package app.gameport.core.steam.session

/** The account and the access token that make steamcommunity.com see the signed-in account (see [SteamSession.webCookies]). Never written to a file or to the log. */
data class WebCookies(val steamId: Long, val accessToken: String) {
    /** The value of the `steamLoginSecure` cookie: the account, two bars (written %7C%7C in a cookie) and the token. */
    val steamLoginSecure: String get() = "$steamId%7C%7C$accessToken"
}
