package app.gameport.core.model

/**
 * Whether a game's request for access to the files of the device (`READ_EXTERNAL_STORAGE`) is something the player can answer. A question that cannot be answered is not asked:
 * the page would send the player to a setting that does not exist, and ask again at every Play.
 */
enum class StorageAccess {
    /** The game does not ask for it, or has been given it. */
    NOT_NEEDED,

    /** The game asks for it, does not have it, and the system lets it be given: Play waits until it is. */
    GRANTABLE,

    /** The game asks for it, does not have it, and the system does not offer it (or what the game was built for is not known): nothing to ask. */
    UNFIXABLE,
    ;

    companion object {
        /** From Android 13 a game built for it can no longer be given this permission: the system page lists the media permissions instead. */
        const val FIRST_SDK_WITHOUT_IT = 33

        /**
         * [lacksIt] is true when the game asks for the permission and has not been given it. [targetSdk] is the version of Android the game was built for, null when it is not
         * known: then nothing is claimed, since a block that may never be lifted is worse than no block.
         */
        fun of(lacksIt: Boolean, targetSdk: Int?, deviceSdk: Int, settingsOpened: Int = 0): StorageAccess = when {
            !lacksIt -> NOT_NEEDED
            targetSdk == null -> UNFIXABLE
            deviceSdk >= FIRST_SDK_WITHOUT_IT && targetSdk >= FIRST_SDK_WITHOUT_IT -> UNFIXABLE
            // The player went to the page of the permissions again and again and it is still not given: this device does not offer it, and Play must never stay shut for good.
            settingsOpened >= MAX_SETTINGS_VISITS -> UNFIXABLE
            else -> GRANTABLE
        }

        /** How many times the page of the permissions may be opened without the permission being given before it is taken for one this device does not offer. */
        const val MAX_SETTINGS_VISITS = 3
    }
}
