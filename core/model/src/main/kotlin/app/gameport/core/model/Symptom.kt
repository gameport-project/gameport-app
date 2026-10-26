package app.gameport.core.model

/**
 * What the player saw go wrong with a game, ticked in a problem report. The [code] is what is written in the report (and, later, what a vote can carry): a fixed
 * list, so it can be counted and shown as it is, and cannot hold anything else.
 */
enum class Symptom(val code: String) {
    NO_IMAGE("no_image"),
    FLAT_WINDOW("flat_window"),
    CLOSES("closes"),
    STUCK_LOADING("stuck_loading"),
    NO_CONTROLS("no_controls"),
    NO_SOUND("no_sound"),
    NO_ONLINE("no_online"),
    SLOW("slow"),
    NO_PASSTHROUGH("no_passthrough"),
    VISUAL("visual"),
    OTHER("other"),
    ;

    companion object {
        /** The symptoms of [codes], the ones this version does not know left out (a newer report read by an older one). */
        fun parse(codes: Collection<String>): Set<Symptom> = codes.mapNotNull { code -> entries.firstOrNull { it.code == code } }.toCollection(linkedSetOf())

        /** The codes, in the order of the list, as a report writes them. */
        fun write(symptoms: Collection<Symptom>): String = entries.filter { it in symptoms }.joinToString(",") { it.code }
    }
}
