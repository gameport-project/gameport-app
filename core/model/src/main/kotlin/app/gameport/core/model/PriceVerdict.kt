package app.gameport.core.model

/** What the history of a price says of the price now, from the best to the one to wait out. */
enum class PriceVerdict { LOWEST, GREAT, GOOD, FAIR, WAIT, USUAL }
