package app.gameport.core.model

/**
 * The regions Steam serves downloads from, as Steam numbers them (its "download region" setting). 0 is automatic: Steam picks from where
 * the connection is. Another region can reach faster servers on a network whose route to the nearest one is poor. The names are Steam's own.
 */
object SteamRegions {
    const val AUTOMATIC = 0

    /** Id and name, by name. Automatic is not in it: it is the absence of a choice. */
    val all: List<Pair<Int, String>> = listOf(
        19 to "Asia To Australia",
        52 to "Australia - NSW",
        25 to "Brazil - Sao Paulo",
        20 to "Canada - Toronto",
        33 to "China - Hong Kong",
        42 to "Czech Republic",
        41 to "Denmark",
        21 to "E. Canada to US",
        13 to "East Atlantic",
        18 to "East Pacific",
        14 to "France - Paris",
        5 to "Germany - Frankfurt",
        43 to "Greece",
        29 to "Iceland, Greenland, and Faroe Islands",
        36 to "India - Mumbai",
        44 to "Indonesia",
        30 to "Israel",
        37 to "Italy - Rome",
        32 to "Japan - Tokyo",
        23 to "NA to SA",
        15 to "Netherlands",
        22 to "New Zealand",
        45 to "Philippines",
        38 to "Poland - Warsaw",
        16 to "Romania",
        7 to "Russia - Moscow",
        27 to "Russia to Europe",
        35 to "Singapore",
        26 to "South Africa - Johannesburg",
        8 to "South Korea - Seoul",
        40 to "Spain & Portugal",
        9 to "Taiwan",
        34 to "Thailand",
        4 to "UK - London",
        50 to "US - Atlanta",
        1 to "US - Chicago",
        49 to "US - Denver",
        12 to "US - Miami",
        2 to "US - New York",
        11 to "US - Phoenix",
        10 to "US - San Jose",
        31 to "US - Seattle",
        6 to "W. Canada to US",
        24 to "West Africa to UK",
        3 to "West Atlantic",
        17 to "West Pacific",
    )

    fun nameOf(id: Int): String? = all.firstOrNull { it.first == id }?.second
}
