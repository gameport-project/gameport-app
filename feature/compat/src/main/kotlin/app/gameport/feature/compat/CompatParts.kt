package app.gameport.feature.compat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.Icon
import app.gameport.core.designsystem.PageHeader
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.gameport.core.designsystem.CompatGlyph
import app.gameport.core.designsystem.GlassChip
import app.gameport.core.designsystem.Glyph
import app.gameport.core.model.CompatCounts
import app.gameport.core.model.CompatLevel
import app.gameport.core.model.CompatRules
import app.gameport.core.model.DeviceCounts

/** The colour of the mark in the header: the orange of the compatibility labels. */
private val HeaderOrange = Color(0xFFFFB74D)

/** The header of the page of the table: the wrench of the compatibility options in the colour of the labels. */
@Composable
internal fun CompatHeader(onBack: () -> Unit, subtitle: String) {
    PageHeader(onBack, stringResource(R.string.compat_title), subtitle) {
        Icon(Icons.Filled.Build, contentDescription = null, tint = HeaderOrange, modifier = Modifier.size(52.dp))
    }
}

/** The kinds of device in the order they are listed, after the one in hand. */
private val ORDER = listOf("quest", "pico", "phone", "tablet", "other")

/** The kinds of device that have answers for [counts], the one in hand [deviceKind] first, then the others in a fixed order. */
internal fun devicesOf(counts: CompatCounts, deviceKind: String, withOwn: Boolean = true): List<Pair<String, DeviceCounts>> {
    val all = counts.devices.toMutableMap()
    // The device in hand always has its line, even when nobody with the same kind of device answered.
    if (withOwn && deviceKind !in all) all[deviceKind] = DeviceCounts(works = 0, fails = 0)
    return all.entries.sortedBy { (kind, _) -> if (kind == deviceKind) -1 else ORDER.indexOf(kind).let { if (it < 0) ORDER.size else it } }.map { it.key to it.value }
}

internal fun mark(level: CompatLevel): Triple<Color, Glyph, Int> = when (level) {
    CompatLevel.WORKS -> Triple(Color(0xFF66BB6A), Glyph.CHECK, R.string.compat_level_works)
    CompatLevel.OFFLINE_ONLY -> Triple(Color(0xFFFFB74D), Glyph.NO_NETWORK, R.string.compat_level_offline_only)
    CompatLevel.MIXED -> Triple(Color(0xFFFFB74D), Glyph.EXCLAMATION, R.string.compat_level_mixed)
    CompatLevel.FAILS -> Triple(Color(0xFFE57373), Glyph.CROSS, R.string.compat_level_fails)
}

@Composable
internal fun deviceName(kind: String): String = stringResource(
    when (kind) {
        "quest" -> R.string.compat_device_quest
        "pico" -> R.string.compat_device_pico
        "phone" -> R.string.compat_device_phone
        "tablet" -> R.string.compat_device_tablet
        else -> R.string.compat_device_other
    },
)

/** The label of the game's page: the verdict for this kind of device, in colour, with its mark before the words. */
@Composable
internal fun VerdictChip(level: CompatLevel, device: String) {
    val (colour, glyph, _) = mark(level)
    val label = stringResource(
        when (level) {
            CompatLevel.WORKS -> R.string.compat_chip_works
            CompatLevel.OFFLINE_ONLY -> R.string.compat_chip_offline_only
            CompatLevel.MIXED -> R.string.compat_chip_mixed
            CompatLevel.FAILS -> R.string.compat_chip_fails
        },
        device,
    )
    val capitals = with(LocalDensity.current) { (MaterialTheme.typography.labelLarge.fontSize * CAPITAL_HEIGHT).toDp() }
    GlassChip(label, accent = colour, leading = { CompatGlyph(glyph, colour, capitals) })
}

/** The height of a capital letter, as a share of the size of the font. */
internal const val CAPITAL_HEIGHT = 0.72f

/** What the players of one kind of device said: the kind, its verdict when there are enough answers, and the numbers. */
@Composable
internal fun DeviceLine(counts: CompatCounts, kind: String, own: DeviceCounts, mine: Boolean, trailing: (@Composable () -> Unit)? = null) {
    val verdict = CompatRules.of(counts, kind)
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (mine) stringResource(R.string.compat_this_device, deviceName(kind)) else deviceName(kind),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f),
            )
            when {
                // The label of the card or the page already says it for the device in hand.
                verdict != null && !mine -> {
                    val (colour, glyph, label) = mark(verdict.level)
                    CompatGlyph(glyph, colour, height = 14.dp)
                    Text(stringResource(label), color = colour, style = MaterialTheme.typography.labelMedium)
                }
                verdict == null -> Text(stringResource(R.string.compat_few), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
            }
            trailing?.invoke()
        }
        // Only the answers that exist: "0 did not work" is not worth a word.
        val parts = listOfNotNull(
            own.works.takeIf { it > 0 }?.let { stringResource(R.string.compat_count_works, it) },
            own.offlineOnly.takeIf { it > 0 }?.let { stringResource(R.string.compat_count_offline_only, it) },
            own.fails.takeIf { it > 0 }?.let { stringResource(R.string.compat_count_fails, it) },
        )
        if (parts.isNotEmpty()) {
            Text(parts.joinToString(" · "), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        if (own.worksOffline > 0) {
            Text(
                pluralStringResource(R.plurals.compat_offline_tried, own.worksOffline, own.worksOffline),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
