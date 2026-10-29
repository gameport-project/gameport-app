package app.gameport.core.model

/**
 * A channel of a game on Steam (a branch) that holds an Android build. The normal channel is named `public`; a studio can also publish a beta, a test of a new feature or a
 * build for one platform in others. GamePort installs the normal one: the others are shown to tell whether a more recent Android version exists somewhere.
 */
data class UpdateChannel(
    val name: String,
    val description: String?,
    /** Steam's date for the channel, in seconds since 1970; 0 when it gives none. The date is the channel's, not the Android build's alone. */
    val updatedSeconds: Long,
    /** The size of the Android build of this channel; 0 when Steam gives none. */
    val androidBytes: Long,
    /** The Android build is the very one of the normal channel (the same manifests), whatever the date of the channel says. */
    val sameAsNormal: Boolean,
    /** Steam asks a password for this channel. */
    val passwordRequired: Boolean,
) {
    val isNormal: Boolean get() = name == UpdateChannels.NORMAL
}

object UpdateChannels {
    const val NORMAL = "public"

    /** The normal channel first, then the others from the most recently updated to the least. */
    fun ordered(channels: List<UpdateChannel>): List<UpdateChannel> =
        channels.sortedWith(compareByDescending<UpdateChannel> { it.isNormal }.thenByDescending { it.updatedSeconds }.thenBy { it.name })

    /** Whether there is anything to show beyond the normal channel. */
    fun hasOthers(channels: List<UpdateChannel>): Boolean = channels.any { !it.isNormal }

    /** Whether [channel] carries an Android build that is not the normal channel's and was published after it: a version that may be more recent. */
    fun mayBeNewer(channel: UpdateChannel, normal: UpdateChannel?): Boolean =
        !channel.isNormal && !channel.sameAsNormal && normal != null && channel.updatedSeconds > normal.updatedSeconds
}
