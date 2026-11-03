package app.gameport.core.model

/** The name a person knows a device by (Quest 3, Pico 4), read from what the device calls itself. Null when it is not one this knows: nothing is guessed. */
object DeviceNames {
    /** The codenames (`Build.DEVICE`) of the Quests, which say more than the model does. */
    private val CODENAMES = mapOf("eureka" to "Quest 3", "panther" to "Quest 3S", "hollywood" to "Quest 2", "seacliff" to "Quest Pro")

    fun friendly(model: String?, codename: String?): String? {
        CODENAMES[codename.orEmpty().trim().lowercase()]?.let { return it }
        val name = model.orEmpty().trim().lowercase()
        return when {
            name.startsWith("quest 3s") -> "Quest 3S"
            name.startsWith("quest 3") -> "Quest 3"
            name.startsWith("quest 2") -> "Quest 2"
            name.startsWith("quest pro") -> "Quest Pro"
            name.startsWith("pico 4 ultra") -> "Pico 4 Ultra"
            name.startsWith("pico 4 pro") -> "Pico 4 Pro"
            name.startsWith("pico 4") -> "Pico 4"
            name.startsWith("pico neo 3") -> "Pico Neo 3"
            else -> null
        }
    }
}
