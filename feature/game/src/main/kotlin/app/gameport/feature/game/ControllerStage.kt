package app.gameport.feature.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.gameport.core.designsystem.GlassButton
import app.gameport.core.designsystem.glass
import app.gameport.core.model.ControlRef
import app.gameport.core.model.ControllerLayout
import app.gameport.core.model.ControllerMapping
import app.gameport.core.model.Hand

/**
 * The controllers of this device (where the player's own buttons are) and of the game (the Steam Frame's) drawn side by side, the device's on the left unless
 * they are swapped. A touch on a button of this device selects it, and the controls of the game it can trigger then pulse: a touch on one adds it or takes it
 * away. The other way round works too, from a control of the game to the buttons of this device. Links that are not obvious are drawn between them, and a panel
 * under the drawing says what is selected and warns about what a choice breaks.
 */
@Composable
internal fun ControllerStage(
    mapping: ControllerMapping,
    deviceFamily: String?,
    focused: ControlRef?,
    onSelect: (ControlRef?) -> Unit,
    onToggleTarget: (source: ControlRef, target: ControlRef) -> Unit,
    deviceFirst: Boolean = true,
    modifier: Modifier = Modifier,
) {
    // A control of the drawing the game does not use: told as such, since it cannot be remapped.
    var unused by remember { mutableStateOf<ControlRef?>(null) }
    // A button of this device the player selected, to say which controls of the game it triggers.
    var selectedDevice by remember { mutableStateOf<ControlRef?>(null) }
    val chosenDevice = selectedDevice
    val deviceStyle = ControllerStyle.ofDeviceFamily(deviceFamily)
    val allocation = remember(mapping) { mapping.allocation() }
    val shared = remember(allocation) { ControllerLayout.shared(allocation) }
    // The control under the pointer is shown like the selected one, until the pointer leaves it.
    var hovered by remember { mutableStateOf<ControlRef?>(null) }
    // The button of the device under the pointer: it lights the controls of the game that go to it, so the link reads both ways.
    var hoveredTarget by remember { mutableStateOf<ControlRef?>(null) }
    val active: Set<ControlRef> = when {
        chosenDevice != null -> allocation.filterValues { chosenDevice in it }.keys
        focused != null -> setOf(focused)
        hovered != null -> setOf(hovered!!)
        hoveredTarget != null -> allocation.filterValues { hoveredTarget in it }.keys
        else -> emptySet()
    }
    // Only the links that are not obvious get a line and an outline; a control going to the control of the same name needs none.
    val links = remember(allocation) { ControllerArtLayout.notObvious(allocation) }
    fun deviceLights(hand: Hand): Map<String, ControllerLight> {
        val lights = ControllerArtLayout.deviceLights(hand, allocation, active, links)
        // A selected button that nothing goes to is still shown selected.
        return if (chosenDevice != null && chosenDevice.hand == hand && chosenDevice.group !in lights) {
            lights + (chosenDevice.group to ControllerLight(ControllerArtLayout.colorGroupOf(chosenDevice.group), emphasised = true, selected = true))
        } else lights
    }
    // The control being changed is marked on its own drawing.
    fun anchored(lights: Map<String, ControllerLight>, zones: List<ControlZone>, ref: ControlRef?, hand: Hand): Map<String, ControllerLight> {
        if (ref == null || ref.hand != hand) return lights
        val group = ControllerArtLayout.zoneOf(zones, ref)?.group ?: return lights
        val light = lights[group] ?: return lights
        return lights + (group to light.copy(anchor = true))
    }
    val gameLeft = anchored(ControllerArtLayout.gameLights(Hand.LEFT, mapping.detected, active, links), frameZonesOf(Hand.LEFT), focused, Hand.LEFT)
    val gameRight = anchored(ControllerArtLayout.gameLights(Hand.RIGHT, mapping.detected, active, links), frameZonesOf(Hand.RIGHT), focused, Hand.RIGHT)
    val deviceLeft = anchored(deviceLights(Hand.LEFT), ControllerArtLayout.zones(deviceStyle, Hand.LEFT), chosenDevice, Hand.LEFT)
    val deviceRight = anchored(deviceLights(Hand.RIGHT), ControllerArtLayout.zones(deviceStyle, Hand.RIGHT), chosenDevice, Hand.RIGHT)
    fun hoverGame(hand: Hand): (ControlZone?) -> Unit = { zone -> hovered = zone?.let { ControllerArtLayout.sourceFor(it, hand, mapping.detected) } }
    fun hoverDevice(hand: Hand): (ControlZone?) -> Unit = { zone -> hoveredTarget = zone?.let { ControlRef(hand, it.group).takeIf { ref -> ref in ControllerLayout.targets } } }
    fun done() { unused = null; selectedDevice = null; onSelect(null) }

    fun tapGame(hand: Hand): (ControlZone) -> Unit = { zone ->
        val source = ControllerArtLayout.sourceFor(zone, hand, mapping.detected)
        when {
            // A button of the device is selected: this control is added to what it triggers, or taken away.
            chosenDevice != null && source != null -> onToggleTarget(source, chosenDevice)
            chosenDevice != null -> unused = ControlRef(hand, zone.group)
            // A second touch on the selected control puts everything back on show.
            source != null && source == focused -> done()
            source != null -> { unused = null; selectedDevice = null; onSelect(source) }
            else -> { unused = ControlRef(hand, zone.group); onSelect(null) }
        }
    }
    fun tapDevice(hand: Hand): (ControlZone) -> Unit = { zone ->
        val target = ControlRef(hand, zone.group)
        if (target in ControllerLayout.targets) when {
            focused != null -> onToggleTarget(focused, target)
            chosenDevice == target -> done()
            else -> { unused = null; selectedDevice = target }
        }
    }
    // Every control drawn on the game's side is touchable, the unused ones too, so that a dark one is explained instead of looking forgotten.
    val touchLeft = remember { ControllerArtLayout.zones(ControllerStyle.FRAME, Hand.LEFT).filter { !it.decor }.map { it.group }.toSet() }
    val touchRight = remember { ControllerArtLayout.zones(ControllerStyle.FRAME, Hand.RIGHT).filter { !it.decor }.map { it.group }.toSet() }
    // The controls of the game that can be picked: the ones it uses.
    val usedLeft = remember(mapping.detected) { frameZonesOf(Hand.LEFT).filter { ControllerArtLayout.sourceFor(it, Hand.LEFT, mapping.detected) != null }.map { it.group }.toSet() }
    val usedRight = remember(mapping.detected) { frameZonesOf(Hand.RIGHT).filter { ControllerArtLayout.sourceFor(it, Hand.RIGHT, mapping.detected) != null }.map { it.group }.toSet() }
    // The buttons of this device a control can be sent to.
    val pickLeft = remember(deviceStyle) { ControllerArtLayout.targetZones(deviceStyle, Hand.LEFT).map { it.group }.toSet() }
    val pickRight = remember(deviceStyle) { ControllerArtLayout.targetZones(deviceStyle, Hand.RIGHT).map { it.group }.toSet() }

    // Where each drawing is, to draw the links across all of them.
    var box by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val planCache = remember { PlanCache() }
    val spots = remember { mutableStateMapOf<String, LayoutCoordinates>() }
    val frameZones = remember { mapOf(Hand.LEFT to frameZonesOf(Hand.LEFT), Hand.RIGHT to frameZonesOf(Hand.RIGHT)) }
    val deviceZones = remember(deviceStyle) { mapOf(Hand.LEFT to ControllerArtLayout.zones(deviceStyle, Hand.LEFT), Hand.RIGHT to ControllerArtLayout.zones(deviceStyle, Hand.RIGHT)) }
    val strokeWidth = with(LocalDensity.current) { 2.5.dp.toPx() }

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val wide = maxWidth >= 760.dp
        // Which side is being asked: the game's controls when a button of the device is selected, the device's buttons when a control of the game is.
        val pickGame = chosenDevice != null
        val pickDevice = focused != null
        val boxShape = RoundedCornerShape(18.dp)
        val game: @Composable (Modifier) -> Unit = { m ->
            Column(m.glass(boxShape).then(if (pickGame) Modifier.border(2.dp, Selected.copy(alpha = 0.85f), boxShape) else Modifier).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (pickGame) Text(stringResource(R.string.controllers_pick_game), style = MaterialTheme.typography.titleSmall, color = Selected, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)) {
                    ControllerView(ControllerStyle.FRAME, Hand.LEFT, stringResource(R.string.controllers_art_game_left), gameLeft, touchLeft, tapGame(Hand.LEFT), Modifier.weight(1f).widthIn(max = 190.dp), pickable = if (pickGame) usedLeft else emptySet(), onPlaced = { spots["game-left"] = it }, onHover = hoverGame(Hand.LEFT), pickMode = pickGame)
                    ControllerView(ControllerStyle.FRAME, Hand.RIGHT, stringResource(R.string.controllers_art_game_right), gameRight, touchRight, tapGame(Hand.RIGHT), Modifier.weight(1f).widthIn(max = 190.dp), pickable = if (pickGame) usedRight else emptySet(), onPlaced = { spots["game-right"] = it }, onHover = hoverGame(Hand.RIGHT), pickMode = pickGame)
                }
            }
        }
        val device: @Composable (Modifier) -> Unit = { m ->
            Column(m.glass(boxShape).then(if (pickDevice) Modifier.border(2.dp, Selected.copy(alpha = 0.85f), boxShape) else Modifier).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (pickDevice) Text(stringResource(R.string.controllers_pick_device), style = MaterialTheme.typography.titleSmall, color = Selected, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)) {
                    ControllerView(deviceStyle, Hand.LEFT, stringResource(R.string.controllers_art_device_left), deviceLeft, pickLeft, tapDevice(Hand.LEFT), Modifier.weight(1f).widthIn(max = 190.dp), pickable = if (pickDevice) pickLeft else emptySet(), onPlaced = { spots["device-left"] = it }, onHover = hoverDevice(Hand.LEFT), hoverable = pickLeft, pickMode = pickDevice)
                    ControllerView(deviceStyle, Hand.RIGHT, stringResource(R.string.controllers_art_device_right), deviceRight, pickRight, tapDevice(Hand.RIGHT), Modifier.weight(1f).widthIn(max = 190.dp), pickable = if (pickDevice) pickRight else emptySet(), onPlaced = { spots["device-right"] = it }, onHover = hoverDevice(Hand.RIGHT), hoverable = pickRight, pickMode = pickDevice)
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // While a choice is being made, how to leave it is in view at the top, with what is being changed.
            if (pickGame || pickDevice) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        targetLabel((chosenDevice ?: focused)!!),
                        style = MaterialTheme.typography.titleMedium,
                        color = Selected,
                        modifier = Modifier.weight(1f),
                    )
                    GlassButton(onClick = { done() }) {
                        Icon(Icons.Filled.Close, null, modifier = Modifier.padding(end = 8.dp))
                        Text(stringResource(R.string.controllers_cancel))
                    }
                }
            }
            // One frame around the two pairs of controllers: it delimits the area the links run in, and none of them leaves it.
            Box(Modifier.fillMaxWidth().onGloballyPositioned { box = it }.glass(boxShape).border(1.dp, Color.White.copy(alpha = 0.14f), boxShape)) {
                if (wide) {
                    Row(Modifier.padding(horizontal = 20.dp, vertical = 28.dp), horizontalArrangement = Arrangement.spacedBy(56.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (deviceFirst) { device(Modifier.weight(1f)); game(Modifier.weight(1f)) } else { game(Modifier.weight(1f)); device(Modifier.weight(1f)) }
                    }
                } else {
                    Column(Modifier.padding(horizontal = 20.dp, vertical = 28.dp), verticalArrangement = Arrangement.spacedBy(56.dp)) {
                        if (deviceFirst) { device(Modifier.fillMaxWidth()); game(Modifier.fillMaxWidth()) } else { game(Modifier.fillMaxWidth()); device(Modifier.fillMaxWidth()) }
                    }
                }
                // A line for every link that is not obvious, thin and pale; the links of the selected or hovered control or button stand out in blue. They never take a touch.
                Canvas(Modifier.matchParentSize().clipToBounds()) {
                    val origin = box?.takeIf { it.isAttached } ?: return@Canvas
                    val drawings = listOf("game-left" to frameZones.getValue(Hand.LEFT), "game-right" to frameZones.getValue(Hand.RIGHT), "device-left" to deviceZones.getValue(Hand.LEFT), "device-right" to deviceZones.getValue(Hand.RIGHT))
                    fun spot(ref: ControlRef, game: Boolean): Spot? {
                        val key = (if (game) "game-" else "device-") + (if (ref.hand == Hand.LEFT) "left" else "right")
                        val index = drawings.indexOfFirst { it.first == key }
                        return spotOf(origin, spots[key], drawings[index].second, ref.group, index)
                    }
                    fun ends(source: ControlRef, target: ControlRef): ArrowEnds? {
                        val a = spot(source, true) ?: return null
                        val b = spot(target, false) ?: return null
                        return ArrowEnds(Pt(a.x.round(), a.y.round()), a.rx, a.ry, a.id, Pt(b.x.round(), b.y.round()), b.rx, b.ry, b.id)
                    }
                    val clear = 2.dp.toPx()
                    val pairs = allocation.entries.filter { it.key in links }.flatMap { (source, targets) -> targets.map { source to it } }
                    val placed = pairs.mapNotNull { (source, target) -> ends(source, target)?.let { Triple(source, target, it) } }
                    // The controls a link should not run over: every control drawn, but the two it joins.
                    val obstacles = drawings.flatMapIndexed { index, (key, zones) ->
                        val drawing = spots[key]?.takeIf { it.isAttached } ?: return@flatMapIndexed emptyList<Obstacle>()
                        zones.filter { !it.decor }.mapNotNull { zone -> spotOf(origin, drawing, zones, zone.group, index)?.let { Obstacle(Pt(it.x.round(), it.y.round()), maxOf(it.rx, it.ry) + clear, it.id) } }
                    }
                    val inset = 6.dp.toPx()
                    val bounds = Bounds(inset, inset, size.width - inset, size.height - inset)
                    val key = Triple(placed.map { it.third }, obstacles, bounds)
                    if (planCache.key != key) { planCache.key = key; planCache.curves = ArrowCurves.plan(placed.map { it.third }, obstacles, 9.dp.toPx(), clear, bounds) }
                    // Which links stand out: those of the selected control or button, or of the one under the pointer.
                    fun standsOut(source: ControlRef, target: ControlRef): Boolean = when {
                        chosenDevice != null -> target == chosenDevice && source in active
                        focused != null -> source == focused
                        hovered != null -> source == hovered
                        hoveredTarget != null -> target == hoveredTarget && source in active
                        else -> false
                    }
                    val dim = active.isNotEmpty()
                    // The same line, the same thickness: blue when it is the one of the selected or hovered control or button, pale otherwise.
                    placed.forEachIndexed { i, (source, target, _) ->
                        if (!standsOut(source, target)) drawLink(planCache.curves[i], strokeWidth * 0.6f, dim)
                    }
                    placed.forEachIndexed { i, (source, target, _) ->
                        if (standsOut(source, target)) drawLink(planCache.curves[i], strokeWidth * 0.6f, false, Selected)
                    }
                    // A link that is not drawn when nothing is selected (an obvious one) is drawn the same way once it is.
                    for ((source, targets) in allocation) if (source !in links) for (target in targets) {
                        if (!standsOut(source, target)) continue
                        val e = ends(source, target) ?: continue
                        drawLink(ArrowCurves.curve(e, 0.4f, 0f, clear), strokeWidth * 0.6f, false, Selected)
                    }
                }
            }
            Column(Modifier.fillMaxWidth().glass(boxShape).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val notUsed = unused
                if (notUsed != null) {
                    Text(stringResource(R.string.controllers_unused, targetLabel(notUsed)), style = MaterialTheme.typography.titleMedium)
                } else if (chosenDevice != null) {
                    val sources = allocation.filterValues { chosenDevice in it }.keys.toList()
                    val names = sources.map { targetLabel(it) }
                    Text(
                        stringResource(R.string.controllers_art_focus, targetLabel(chosenDevice), if (names.isEmpty()) stringResource(R.string.controllers_nothing) else names.joinToString(", ")),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(stringResource(R.string.controllers_art_pick_device), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.controllers_legend), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    for (warning in warningsForDevice(chosenDevice, sources)) WarningLine(warning)
                    GlassButton(onClick = { done() }) {
                        Icon(Icons.Filled.Close, null, modifier = Modifier.padding(end = 8.dp))
                        Text(stringResource(R.string.controllers_show_all))
                    }
                } else if (focused != null) {
                    val targets = allocation[focused].orEmpty()
                    val names = targets.map { targetLabel(it) }
                    Text(
                        stringResource(R.string.controllers_art_focus, targetLabel(focused), if (names.isEmpty()) stringResource(R.string.controllers_nothing) else names.joinToString(", ")),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(stringResource(R.string.controllers_art_pick), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.controllers_legend), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    for (warning in warningsFor(focused, targets, allocation)) WarningLine(warning)
                    GlassButton(onClick = { done() }) {
                        Icon(Icons.Filled.Close, null, modifier = Modifier.padding(end = 8.dp))
                        Text(stringResource(R.string.controllers_show_all))
                    }
                } else {
                    Text(stringResource(R.string.controllers_art_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (shared.isNotEmpty()) {
                Column(Modifier.fillMaxWidth().glass(boxShape).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.controllers_art_shared), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    for ((target, sources) in shared) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(Modifier.size(12.dp).clip(CircleShape).background(Amber))
                            Text(stringResource(R.string.controllers_art_shared_line, targetLabel(target), sources.map { targetLabel(it) }.joinToString(", ")), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}

private fun frameZonesOf(hand: Hand): List<ControlZone> = ControllerArtLayout.zones(ControllerStyle.FRAME, hand).filter { !it.decor }

/** What goes wrong with the controls a button of this device triggers, said before the player is surprised by it. */
@Composable
private fun warningsForDevice(target: ControlRef, sources: List<ControlRef>): List<String> = buildList {
    if (sources.isEmpty()) add(stringResource(R.string.controllers_warn_device_nothing))
    if (sources.size >= 2) add(stringResource(R.string.controllers_warn_device_several, targetLabel(target), sources.map { targetLabel(it) }.joinToString(", ")))
    if (sources.any { (it.group == "thumbstick") != (target.group == "thumbstick") }) add(stringResource(R.string.controllers_warn_stick))
}

/** What goes wrong with where a control goes now, said before the player is surprised by it. */
private sealed interface Warn {
    data object Nowhere : Warn
    data class Together(val target: ControlRef, val others: List<ControlRef>) : Warn
    data object StickAndButton : Warn
}

@Composable
private fun warningsFor(source: ControlRef, targets: List<ControlRef>, allocation: Map<ControlRef, List<ControlRef>>): List<String> {
    val found = buildList<Warn> {
        if (targets.isEmpty()) add(Warn.Nowhere)
        for (target in targets) {
            val others = allocation.filter { (other, to) -> other != source && target in to }.keys.toList()
            if (others.isNotEmpty()) add(Warn.Together(target, others))
        }
        // A stick gives a direction and a button gives a press: one does not stand in for the other.
        if (targets.any { (it.group == "thumbstick") != (source.group == "thumbstick") }) add(Warn.StickAndButton)
    }
    return found.map { warn ->
        when (warn) {
            Warn.Nowhere -> stringResource(R.string.controllers_warn_nowhere)
            is Warn.Together -> stringResource(R.string.controllers_warn_together, targetLabel(warn.target), warn.others.map { targetLabel(it) }.joinToString(", "))
            Warn.StickAndButton -> stringResource(R.string.controllers_warn_stick)
        }
    }
}

@Composable
private fun WarningLine(text: String) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Filled.Warning, null, tint = Amber, modifier = Modifier.size(18.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = Amber)
    }
}

/** Where a control is drawn, in the coordinates of the stage: its middle, its half size and an id telling it from the others. */
private data class Spot(val x: Float, val y: Float, val rx: Float, val ry: Float, val id: Int)

private class PlanCache { var key: Triple<List<ArrowEnds>, List<Obstacle>, Bounds>? = null; var curves: List<ArrowCurve> = emptyList() }

private fun Float.round(): Float = kotlin.math.round(this)

/** The place of the zone of [group] on a drawing, in the coordinates of [origin], or null when the drawing is not placed yet or has no such zone. */
private fun spotOf(origin: LayoutCoordinates, drawing: LayoutCoordinates?, zones: List<ControlZone>, group: String, drawingIndex: Int): Spot? {
    if (drawing == null || !drawing.isAttached) return null
    val zone = zones.firstOrNull { !it.decor && group in it.groups } ?: return null
    val width = drawing.size.width.toFloat()
    val middle = origin.localPositionOf(drawing, Offset(zone.cx * width, zone.cy * width))
    return Spot(middle.x, middle.y, zone.w * width / 2f, zone.h * width / 2f, drawingIndex * 1000 + zones.indexOf(zone))
}

/** One link: a plain line, no point and no head. A light line on a thin dark edge, so it reads on the dark controllers as well as on the white ones. */
private fun DrawScope.drawLink(curve: ArrowCurve, width: Float, dim: Boolean, color: Color? = null) {
    val path = Path().apply {
        moveTo(curve.start.x, curve.start.y)
        cubicTo(curve.c1.x, curve.c1.y, curve.c2.x, curve.c2.y, curve.end.x, curve.end.y)
    }
    drawPath(path, Color(0xFF0B1020).copy(alpha = if (dim) 0.25f else 0.5f), style = Stroke(width * 2.6f, cap = StrokeCap.Round))
    drawPath(path, color ?: Color(0xFFD5DAE6).copy(alpha = if (dim) 0.55f else 1f), style = Stroke(width, cap = StrokeCap.Round))
}
