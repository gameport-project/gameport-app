package app.gameport.feature.store

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gameport.core.designsystem.GameImage
import app.gameport.core.designsystem.GlassButton
import app.gameport.core.designsystem.GlassChip
import app.gameport.core.designsystem.KindBlue
import app.gameport.core.designsystem.PageHeader
import app.gameport.core.designsystem.glass
import app.gameport.core.model.AndroidApp
import app.gameport.feature.compat.VerdictChip
import app.gameport.core.model.PriceStory
import app.gameport.core.model.PriceVerdict
import app.gameport.core.model.StorePrice
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import app.gameport.core.designsystem.R as DesignR

private val CONTENT_WIDTH = 900.dp
private val COVER_WIDTH = 120.dp
private val HERO_HEIGHT = 190.dp

/**
 * How much of the cover lies over the picture. The buttons end where the cover does, so what is under the picture is a button plus the gap, and that gap
 * is the 16 dp the page leaves between its blocks: the buttons are as far under the picture as they are above the text.
 */
// A getter: the values it uses are declared further down the file.
private val COVER_OVER_PICTURE: Dp get() = COVER_WIDTH * 1.5f - (BUTTON_BLOCK_HEIGHT + 16.dp)

/**
 * The page of a game of the list of Android versions: its picture with the cover over it as on the page of an installed game, who made it, what people
 * think of it, what Steam sells it for, what it is, and its details with the languages as Steam shows them. Its own page, not the page of an installed
 * game: nothing here installs, plays or patches.
 */
@Composable
fun StoreGameScreen(onBack: () -> Unit, onGameClick: (Int) -> Unit, viewModel: StoreGameViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    Scaffold { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 32.dp)) {
            val content = state as? StoreGameUiState.Content
            PageHeader(onBack, content?.app?.name ?: stringResource(R.string.store_title), content?.app?.let { (it.developer + it.publisher).distinct().joinToString(" · ") }.orEmpty(), trailing = content?.app?.let { app -> { Rating(app) } }) {
                Icon(Icons.Filled.Storefront, contentDescription = null, tint = KindBlue, modifier = Modifier.size(52.dp))
            }
            when {
                content == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                content.app == null -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.storegame_not_listed), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    SteamButton(null) { openOnSteam(context, content.appId) }
                }
                else -> Details(content, content.app, onGameClick, context)
            }
        }
    }
}

/** Takes [amount] less room than it measures and is drawn that far higher: the cover lies over the picture without making the page taller. */
private fun Modifier.pullUp(amount: Dp): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val pulled = amount.roundToPx().coerceAtMost(placeable.height)
    layout(placeable.width, placeable.height - pulled) { placeable.place(0, -pulled) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Details(content: StoreGameUiState.Content, app: AndroidApp, onGameClick: (Int) -> Unit, context: Context) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.fillMaxWidth().widthIn(max = CONTENT_WIDTH)) {
            Box {
                Hero(app)
                // The labels lie over the picture, at the right of the cover.
                FlowRow(
                    Modifier.align(Alignment.BottomEnd).padding(start = COVER_WIDTH + 34.dp, end = 12.dp, bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    Chips(content, app)
                }
            }
            // The cover lies half over the picture; the text starts under it.
            Row(Modifier.fillMaxWidth().pullUp(COVER_OVER_PICTURE).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                val cover = RoundedCornerShape(14.dp)
                GameImage(
                    url = app.cover ?: app.header ?: app.banner ?: "",
                    fallbackUrl = app.header,
                    moreFallbacks = listOfNotNull(app.capsule),
                    contentDescription = null,
                    modifier = Modifier.width(COVER_WIDTH).aspectRatio(2f / 3f).glass(cover).clip(cover),
                )
                Column(Modifier.weight(1f).height(COVER_WIDTH * 1.5f), verticalArrangement = Arrangement.Bottom) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                        Buttons(content, app, onGameClick, context)
                    }
                }
            }
        }
        if (content.summary != null || content.tags.isNotEmpty()) {
            // The text comes straight, with no title over it.
            Column(Modifier.fillMaxWidth().widthIn(max = CONTENT_WIDTH).glass(RoundedCornerShape(16.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                content.summary?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
                if (content.tags.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        content.tags.forEach { StoreChip(it) }
                    }
                }
            }
        }
        Section(stringResource(R.string.storegame_details)) {
            // Two halves side by side where there is room: the facts, and the languages as a table. One under the other on a narrow screen.
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                if (maxWidth >= 640.dp) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Card(Modifier.weight(1f)) { Facts(app, context) }
                        Card(Modifier.weight(1f)) { LanguageTable(app) }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Card(Modifier.fillMaxWidth()) { Facts(app, context) }
                        Card(Modifier.fillMaxWidth()) { LanguageTable(app) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Buttons(content: StoreGameUiState.Content, app: AndroidApp, onGameClick: (Int) -> Unit, context: Context) {
    // The button with its note under it, then the figures of the history, each as high as the button and centred on it.
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            SteamButton(content.price) { openOnSteam(context, app.appid) }
            PriceNote(content.price, content.story?.verdict(content.price))
        }
        if (content.storyLoading) StatsSkeleton() else content.story?.let { PriceStats(it) }
    }
    if (content.owned) {
        GlassButton(onClick = { onGameClick(app.appid) }, modifier = Modifier.height(ROW_HEIGHT), contentPadding = BUTTON_PADDING) { Text(stringResource(R.string.storegame_open_library)) }
    }
}

/** The wide picture over the page, dark at its bottom so it joins the background. */
@Composable
private fun Hero(app: AndroidApp) {
    val shape = RoundedCornerShape(18.dp)
    Box(Modifier.fillMaxWidth().height(HERO_HEIGHT).clip(shape)) {
        GameImage(
            url = app.banner ?: app.header ?: app.cover ?: "",
            fallbackUrl = app.header,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
        )
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.45f to Color.Transparent, 0.7f to Color.Black.copy(alpha = 0.45f), 1f to Color.Black.copy(alpha = 0.82f))))
    }
}

/** What people think, on the picture: the share of positive reviews in the colour of the verdict and their count, in a clear pill like the labels. */
@Composable
internal fun Rating(app: AndroidApp, modifier: Modifier = Modifier, small: Boolean = false, showCount: Boolean = true, onLight: Boolean = false) {
    val percent = app.reviewPercent ?: return
    val colour = when {
        percent >= 80 -> StoreGreen
        percent >= 60 -> StoreOrange
        else -> StoreRed
    }
    val countColour = if (onLight) CardInkPale else Color.White.copy(alpha = 0.8f)
    val shape = RoundedCornerShape(50)
    Row(
        modifier.clip(shape).background(if (onLight) colour.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.25f)).border(1.dp, colour.copy(alpha = 0.65f), shape)
            .padding(horizontal = if (small) 9.dp else 12.dp, vertical = if (small) 3.dp else 5.dp),
        horizontalArrangement = Arrangement.spacedBy(if (small) 5.dp else 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.ThumbUp, contentDescription = null, tint = colour, modifier = Modifier.size(if (small) 13.dp else 16.dp))
        Text("$percent %", style = if (small) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge, color = colour, fontWeight = FontWeight.Bold)
        if (showCount) app.reviewCount?.let { Text(stringResource(R.string.storegame_reviews_count, it), style = MaterialTheme.typography.labelSmall, color = countColour) }
        if (!small) app.metacritic?.let { Text(stringResource(R.string.storegame_metacritic, it), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f)) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Chips(content: StoreGameUiState.Content, app: AndroidApp) {
    StoreChip(
        stringResource(
            when (app.vrMode) {
                "only" -> R.string.storegame_vr_only
                "supported" -> R.string.storegame_vr_supported
                else -> R.string.storegame_flat
            },
        ),
        accent = if (app.vr) KindBlue else null,
    )
    if (!app.installable) StoreChip(stringResource(R.string.storegame_future), accent = StoreOrange)
    when (app.frame) {
        "verified" -> StoreChip(stringResource(R.string.storegame_frame_verified), accent = StoreGreen)
        "playable" -> StoreChip(stringResource(R.string.storegame_frame_playable), accent = StoreOrange)
        "unsupported" -> StoreChip(stringResource(R.string.storegame_frame_unsupported), accent = StoreRed)
    }
    when (app.deck) {
        "verified" -> StoreChip(stringResource(R.string.storegame_deck_verified), accent = StoreGreen)
        "playable" -> StoreChip(stringResource(R.string.storegame_deck_playable), accent = StoreOrange)
        "unsupported" -> StoreChip(stringResource(R.string.storegame_deck_unsupported), accent = StoreRed)
    }
    content.players?.let { VerdictChip(it.level, it.device, small = true) }
    if (content.owned) StoreChip(stringResource(R.string.store_owned), accent = KindBlue, leading = { Icon(Icons.Filled.VideogameAsset, contentDescription = null, tint = KindBlue, modifier = Modifier.size(12.dp)) })
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().widthIn(max = CONTENT_WIDTH), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        content()
    }
}

@Composable
private fun Card(modifier: Modifier, content: @Composable () -> Unit) {
    Column(modifier.glass(RoundedCornerShape(16.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
}

/** The facts of the game, one a line, leaving out what the catalog does not say. */
@Composable
private fun Facts(app: AndroidApp, context: Context) {
    val unknown = stringResource(R.string.storegame_before_tracking)
    Fact(R.string.storegame_release, app.release?.let(::dateOf))
    Fact(R.string.storegame_android_since, if (app.installable) app.arrival?.let { it.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)) } ?: unknown else null)
    Fact(R.string.storegame_size, app.installBytes.takeIf { it > 0 }?.let { Formatter.formatFileSize(context, it) })
    Fact(R.string.storegame_download, app.downloadBytes.takeIf { it > 0 }?.let { Formatter.formatFileSize(context, it) })
    Fact(R.string.storegame_platforms, app.platforms.takeIf { it.isNotEmpty() }?.joinToString(", ") { platformName(it) })
    Fact(
        R.string.storegame_modes,
        app.modes.mapNotNull {
            when (it) {
                "single" -> stringResource(R.string.storegame_mode_single)
                "multi" -> stringResource(R.string.storegame_mode_multi)
                "coop" -> stringResource(R.string.storegame_mode_coop)
                else -> null
            }
        }.takeIf { it.isNotEmpty() }?.joinToString(", "),
    )
    Fact(
        R.string.storegame_controller,
        when (app.controller) {
            "full" -> stringResource(R.string.storegame_controller_full)
            "partial" -> stringResource(R.string.storegame_controller_partial)
            else -> null
        },
    )
}

/**
 * What the history of the price tells, on the line of the Steam button: the lowest price seen and the last sale, each a small pale label over its figure in white,
 * between thin rules, centred on the height of the button. The only colour is a small dot before the sale: green while it is on, amber once it is over. Nothing
 * is shown while the history is only the first reading.
 */
@Composable
private fun PriceStats(story: PriceStory) {
    if (story.lines < 2) return
    val date = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    val sale = story.lastSale
    Rule()
    // Each figure has its own very fine line: the share off for the price, the price for the share.
    Stat(
        R.string.storegame_price_lowest,
        story.amount(story.lowestCents),
        story.lowestDiscount?.let { stringResource(R.string.storegame_price_lowest_fine, it, story.lowestFrom.format(date)) } ?: story.lowestFrom.format(date),
        null,
    )
    Rule()
    Stat(
        R.string.storegame_price_last_sale,
        if (sale == null) stringResource(R.string.storegame_price_no_sale) else stringResource(R.string.storegame_price_sale_value, sale.discountPercent),
        when {
            sale == null -> null
            sale.ongoing && sale.to != null -> stringResource(R.string.storegame_price_sale_fine_until, story.amount(sale.priceCents), sale.to!!.format(date))
            sale.ongoing -> stringResource(R.string.storegame_price_sale_fine_on, story.amount(sale.priceCents))
            else -> stringResource(R.string.storegame_price_sale_fine_past, story.amount(sale.priceCents), sale.from.format(date))
        },
        when {
            sale == null -> null
            sale.ongoing -> StoreGreen
            else -> StoreOrange
        },
    )
}

/** Where the figures of the history will be, while they are read: two pale blocks as high as the figures, between the same rules. */
@Composable
private fun StatsSkeleton() {
    Rule()
    SkeletonBlock(width = 110.dp)
    Rule()
    SkeletonBlock(width = 130.dp)
}

@Composable
private fun SkeletonBlock(width: Dp) {
    Box(Modifier.height(ROW_HEIGHT), contentAlignment = Alignment.Center) {
        Box(Modifier.width(width).height(30.dp).background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(8.dp)))
    }
}

@Composable
private fun Rule() {
    Box(Modifier.height(ROW_HEIGHT), contentAlignment = Alignment.Center) { Box(Modifier.width(1.dp).height(28.dp).background(Color.White.copy(alpha = 0.16f))) }
}

@Composable
private fun Stat(label: Int, value: String, fine: String?, dot: Color?) {
    // At least as high as the button and no more fixed: three lines with their own line heights must never be cut.
    Column(Modifier.heightIn(min = ROW_HEIGHT), verticalArrangement = Arrangement.spacedBy(0.dp, Alignment.CenterVertically)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (dot != null) Box(Modifier.size(6.dp).background(dot, CircleShape))
            Text(stringResource(label), style = MaterialTheme.typography.labelSmall, fontSize = 10.sp, lineHeight = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        Text(value, style = MaterialTheme.typography.labelLarge, lineHeight = 18.sp, color = Color.White, fontWeight = FontWeight.Medium, maxLines = 1)
        // A thin label, not a faded line: a hairline outline round a few small words.
        if (fine != null) {
            val thin = RoundedCornerShape(50)
            Text(
                fine,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 9.sp,
                lineHeight = 11.sp,
                color = Color.White.copy(alpha = 0.9f),
                maxLines = 1,
                modifier = Modifier.padding(top = 3.dp).border(0.75.dp, Color.White.copy(alpha = 0.25f), thin).padding(horizontal = 7.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun Fact(label: Int, value: String?) {
    if (value == null) return
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(label), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(170.dp), softWrap = false)
        Text(value, modifier = Modifier.weight(1f))
    }
}

/** The languages as Steam shows them: one a row, then a mark under the text (the interface), the voices and the subtitles the game has. */
@Composable
private fun LanguageTable(app: AndroidApp) {
    val codes = (app.languages + app.audio + app.subtitles).distinct()
    if (codes.isEmpty()) return
    val column = 64.dp
    Row(verticalAlignment = Alignment.CenterVertically) {
        // The first column has no title: the languages say what it is.
        Spacer(Modifier.weight(1f))
        listOf(R.string.store_language_text, R.string.store_language_voice, R.string.store_language_subtitles).forEach {
            Text(stringResource(it), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(column), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
    codes.forEach { code ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(languageName(code), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            listOf(app.languages, app.audio, app.subtitles).forEach { has ->
                Box(Modifier.width(column), contentAlignment = Alignment.Center) {
                    if (code in has) Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

/** The blue of Steam's store buttons. */
private val SteamBlue = Color(0xFF1A9FFF)

private val BUTTON_PADDING = PaddingValues(horizontal = 14.dp, vertical = 8.dp)

/**
 * The way to Steam, as a blue capsule: the Steam mark, what the game costs, and a small arrow out that says a page opens. Without a price, the mark and the
 * arrow. The words stay for the screen readers.
 */
@Composable
private fun SteamButton(price: StorePrice?, onClick: () -> Unit) {
    val words = stringResource(R.string.storegame_open_steam)
    Row(
        Modifier.height(ROW_HEIGHT).clip(RoundedCornerShape(50)).background(SteamBlue).clickable(onClick = onClick).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(DesignR.drawable.ic_steam), contentDescription = words, tint = Color.White, modifier = Modifier.size(22.dp))
        when (price) {
            StorePrice.Free -> Text(stringResource(R.string.storegame_free), color = Color.White, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
            is StorePrice.Paid -> Text(price.finalText(), color = Color.White, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
            null -> Unit
        }
        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(15.dp))
    }
}

/** The height of the Steam button and of the figures beside it, which are centred on it. */
private val ROW_HEIGHT = 44.dp

/** The button and the note under it: all of it lies under the picture, with the same gap above as there is below. */
private val BUTTON_BLOCK_HEIGHT = ROW_HEIGHT + 5.dp + 18.dp

/**
 * Under the button, small: the share off in soft green, then whether the price is worth it, a small dot of its colour before the words. Nothing when there is neither.
 */
@Composable
private fun PriceNote(price: StorePrice?, verdict: PriceVerdict?) {
    val paid = price as? StorePrice.Paid
    val sale = paid?.discountPercent?.takeIf { it > 0 }
    if (sale == null && verdict == null) return
    Row(Modifier.padding(start = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (sale != null) Text("\u2212$sale %", style = MaterialTheme.typography.labelMedium, color = Color(0xFF7BD389), fontWeight = FontWeight.SemiBold)
        // Without a verdict (no history), the share off alone says little: it is given with the price it comes from.
        if (sale != null && verdict == null) Text(stringResource(R.string.storegame_price_instead_of, paid!!.initialText()), style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.7f), maxLines = 1)
        if (verdict != null) {
            val (words, colour) = when (verdict) {
                PriceVerdict.LOWEST -> R.string.storegame_verdict_lowest to StoreGreen
                PriceVerdict.GREAT -> R.string.storegame_verdict_great to StoreGreen
                PriceVerdict.GOOD -> R.string.storegame_verdict_good to Color(0xFF9CCC65)
                PriceVerdict.FAIR -> R.string.storegame_verdict_fair to StoreOrange
                PriceVerdict.WAIT -> R.string.storegame_verdict_wait to StoreRed
                PriceVerdict.USUAL -> R.string.storegame_verdict_usual to Color(0xFFB0BEC5)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).background(colour, CircleShape))
                Text(stringResource(words), style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.85f), maxLines = 1)
            }
        }
    }
}

private fun dateOf(iso: String): String? = runCatching { LocalDate.parse(iso).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)) }.getOrNull()

private fun platformName(code: String) = when (code) {
    "windows" -> "Windows"
    "macos" -> "macOS"
    "linux" -> "Linux"
    "android" -> "Android"
    else -> code
}

/** Opens the page of the game on Steam, where it is bought. Nothing happens on a device that has no browser. */
private fun openOnSteam(context: Context, appId: Int) = openLink(context, "https://store.steampowered.com/app/$appId")

private fun openLink(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
