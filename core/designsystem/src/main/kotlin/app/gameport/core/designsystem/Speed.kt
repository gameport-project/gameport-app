package app.gameport.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.gameport.core.model.SpeedUnit
import java.util.Locale

/** A download rate as text, in the unit the user picked (for example "12.3 MB/s"). */
@Composable
fun speedText(bytesPerSecond: Long, unit: SpeedUnit): String {
    val number = String.format(Locale.getDefault(), "%.1f", unit.valueOf(bytesPerSecond))
    return stringResource(
        when (unit) {
            SpeedUnit.MEGABYTES_PER_SECOND -> R.string.speed_megabytes
            SpeedUnit.MEGABITS_PER_SECOND -> R.string.speed_megabits
        },
        number,
    )
}
