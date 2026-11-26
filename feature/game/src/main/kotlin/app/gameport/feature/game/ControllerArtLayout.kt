package app.gameport.feature.game

import app.gameport.core.model.ControlRef
import app.gameport.core.model.ControllerLayout
import app.gameport.core.model.Hand

/** The controllers that can be drawn: the Steam Frame's (the game's side) and those of the device, by family. */
internal enum class ControllerStyle {
    FRAME,
    QUEST,
    PICO,
    GENERIC,
    ;

    companion object {
        /** The drawing of this device's controllers, from its VR family (`meta`, `pico`, `openxr`, null when it has no VR). */
        fun ofDeviceFamily(family: String?): ControllerStyle = when (family) {
            "meta" -> QUEST
            "pico" -> PICO
            else -> GENERIC
        }
    }
}

/** One colour per kind of control: what the game uses lights up in it, and its destination on the device lights up in the same one. */
internal enum class ControlColorGroup(val argb: Long) {
    TRIGGER(0xFFE59B86),
    GRIP(0xFFB9A9E3),
    STICK(0xFF8CCBC4),
    FACE(0xFFE4CB91),
    DPAD(0xFF94B6E6),
    SYSTEM(0xFFA3D0AE),
}

internal enum class ZoneShape { CIRCLE, PILL, SQUARE }

/**
 * A control on the drawing, in the coordinates of one controller: x from 0 to 1 across its width, y from 0 to [ControllerArtLayout.HEIGHT] in the same unit.
 * [groups] are the names a control of the game can have on it (a bumper is also called a shoulder). A [decor] zone is drawn but cannot be lit or touched.
 */
internal data class ControlZone(
    val group: String,
    val shape: ZoneShape,
    val cx: Float,
    val cy: Float,
    val w: Float,
    val h: Float,
    val decor: Boolean = false,
    val groups: Set<String> = setOf(group),
    /** An ARGB ring drawn around the button (the home button of a Pico controller). */
    val ring: Long? = null,
)

internal data class Point(val x: Float, val y: Float)

/** The body of a controller: a round head on a handle, a thick line from [handleFrom] to [handleTo]. */
internal data class ControllerBody(val headCenter: Point, val headRadius: Float, val handleFrom: Point, val handleTo: Point, val handleWidth: Float)

/** The colours of a controller's body, as ARGB. */
internal data class ControllerPalette(val top: Long, val bottom: Long, val edge: Long, val button: Long, val letter: Long)

/** How a control is lit: the colour of its group, whether it stands out, and whether another control of the game lands on the same button. */
internal data class ControllerLight(val color: ControlColorGroup, val emphasised: Boolean, val shareCount: Int = 0, val selected: Boolean = false, val linked: Boolean = false, val anchor: Boolean = false) {
    /** Whether another control of the game lands on the same button. */
    val shared: Boolean get() = shareCount >= 2
}

/** The geometry of the drawn controllers, apart from any drawing so it can be tested: where each control is, which one a touch hits, what is lit and in which colour. */
internal object ControllerArtLayout {
    /** The drawing is [HEIGHT] times as high as it is wide. */
    const val HEIGHT = 1.37f

    /** The share of the handle that is drawn. */
    private const val HANDLE_KEEP = 0.82f

    /** Room added around a control when a touch is matched to it, as a share of the width. */
    const val TOUCH_SLOP = 0.035f

    /** A link is obvious when a control goes to the control of the same name on the same hand: nothing to point out there. */
    fun isObvious(source: ControlRef, targets: List<ControlRef>): Boolean = targets == listOf(source)

    /** The controls that go somewhere not obvious: they get an arrow, and a thin outline at both ends. */
    fun notObvious(allocation: Map<ControlRef, List<ControlRef>>): Set<ControlRef> =
        allocation.filter { (source, targets) -> targets.isNotEmpty() && !isObvious(source, targets) }.keys

    private const val ORANGE = 0xFFFF8A3DL
    private const val BLUE = 0xFF3D8BFFL

    fun palette(style: ControllerStyle): ControllerPalette = when (style) {
        ControllerStyle.FRAME -> ControllerPalette(0xFF343A47, 0xFF0F1218, 0xFF5B6475, 0xFF07090C, 0xFFB7C0CF)
        ControllerStyle.QUEST -> ControllerPalette(0xFF3A3A42, 0xFF121216, 0xFF62626E, 0xFF08080A, 0xFFB9B9C4)
        ControllerStyle.PICO -> ControllerPalette(0xFFF6F6F3, 0xFFC4C8CE, 0xFF8E939C, 0xFF14161A, 0xFFE8EAEE)
        ControllerStyle.GENERIC -> ControllerPalette(0xFF414B63, 0xFF171C2A, 0xFF6E7A99, 0xFF0A0D14, 0xFFC1C9DD)
    }

    fun colorGroupOf(group: String): ControlColorGroup = when {
        group == "trigger" || group == "shoulder" || group == "bumper" -> ControlColorGroup.TRIGGER
        group == "squeeze" -> ControlColorGroup.GRIP
        group == "thumbstick" -> ControlColorGroup.STICK
        group in FACE_GROUPS -> ControlColorGroup.FACE
        group.startsWith("dpad_") -> ControlColorGroup.DPAD
        else -> ControlColorGroup.SYSTEM
    }

    private val FACE_GROUPS = setOf("a", "b", "x", "y")

    /** The body of a controller; the right one is the left one seen in a mirror. */
    fun body(style: ControllerStyle, hand: Hand): ControllerBody {
        val head = Point(0.5f, 0.36f)
        val (from, to, width) = when (style) {
            // Handle leaning towards the middle, as on the photo of the Steam Frame's controllers.
            ControllerStyle.FRAME -> Triple(Point(0.50f, 0.56f), Point(0.66f, 1.40f), 0.22f)
            // Handle leaning outwards.
            ControllerStyle.QUEST -> Triple(Point(0.50f, 0.56f), Point(0.28f, 1.38f), 0.20f)
            ControllerStyle.PICO -> Triple(Point(0.50f, 0.56f), Point(0.40f, 1.42f), 0.17f)
            ControllerStyle.GENERIC -> Triple(Point(0.50f, 0.56f), Point(0.50f, 1.40f), 0.18f)
        }
        // The handle is kept at HANDLE_KEEP of its first length: the long one served no purpose.
        val shortened = Point(from.x + (to.x - from.x) * HANDLE_KEEP, from.y + (to.y - from.y) * HANDLE_KEEP)
        val body = ControllerBody(head, 0.34f, from, shortened, width)
        return if (hand == Hand.LEFT) body else body.copy(headCenter = body.headCenter.mirrored(), handleFrom = from.mirrored(), handleTo = shortened.mirrored())
    }

    private fun Point.mirrored() = Point(1f - x, y)

    /** All the controls drawn on one controller, front ones first, then the markers on the handle and the rim. */
    fun zones(style: ControllerStyle, hand: Hand): List<ControlZone> {
        val left = leftZones(style)
        return if (hand == Hand.LEFT) left else left.mapNotNull { rightOf(style, it) }
    }

    private fun circle(group: String, x: Float, y: Float, d: Float, decor: Boolean = false, ring: Long? = null) =
        ControlZone(group, ZoneShape.CIRCLE, x, y, d, d, decor, ring = ring)

    private fun leftZones(style: ControllerStyle): List<ControlZone> {
        val front = when (style) {
            ControllerStyle.FRAME -> listOf(
                circle("thumbstick", 0.64f, 0.42f, 0.17f),
                ControlZone("dpad_up", ZoneShape.SQUARE, 0.34f, 0.31f, 0.085f, 0.085f),
                ControlZone("dpad_down", ZoneShape.SQUARE, 0.34f, 0.49f, 0.085f, 0.085f),
                ControlZone("dpad_left", ZoneShape.SQUARE, 0.25f, 0.40f, 0.085f, 0.085f),
                ControlZone("dpad_right", ZoneShape.SQUARE, 0.43f, 0.40f, 0.085f, 0.085f),
                ControlZone("dpad_hub", ZoneShape.SQUARE, 0.34f, 0.40f, 0.085f, 0.085f, decor = true),
                ControlZone("view", ZoneShape.PILL, 0.50f, 0.19f, 0.13f, 0.05f),
                circle("system", 0.44f, 0.58f, 0.08f),
            )
            ControllerStyle.QUEST -> listOf(
                circle("thumbstick", 0.46f, 0.24f, 0.15f),
                circle("menu", 0.27f, 0.41f, 0.09f),
                circle("x", 0.43f, 0.46f, 0.095f),
                circle("y", 0.62f, 0.38f, 0.095f),
                circle("capture", 0.68f, 0.18f, 0.07f, decor = true),
            )
            ControllerStyle.PICO -> listOf(
                circle("thumbstick", 0.44f, 0.18f, 0.15f),
                circle("menu", 0.25f, 0.28f, 0.085f),
                circle("y", 0.64f, 0.30f, 0.095f),
                circle("x", 0.57f, 0.45f, 0.095f),
                circle("system", 0.40f, 0.55f, 0.095f, decor = true, ring = ORANGE),
            )
            ControllerStyle.GENERIC -> listOf(
                circle("thumbstick", 0.50f, 0.22f, 0.16f),
                circle("menu", 0.30f, 0.30f, 0.08f),
                circle("x", 0.40f, 0.46f, 0.095f),
                circle("y", 0.60f, 0.46f, 0.095f),
            )
        }
        val body = body(style, Hand.LEFT)
        val axis = Point(body.handleTo.x - body.handleFrom.x, body.handleTo.y - body.handleFrom.y)
        val length = kotlin.math.sqrt(axis.x * axis.x + axis.y * axis.y)
        // The perpendicular that points to the right of a handle running downwards: the inner side of a left controller.
        val inner = Point(axis.y / length, -axis.x / length)
        fun onHandle(t: Float, side: Float) = Point(
            body.handleFrom.x + axis.x * t + inner.x * side * body.handleWidth * 0.42f,
            body.handleFrom.y + axis.y * t + inner.y * side * body.handleWidth * 0.42f,
        )
        val grip = onHandle(0.34f, 1f)
        // The Steam Frame has two triggers on top (the bumper and the trigger) and the grip on the handle; a Touch or a Pico controller has its trigger
        // on top and its grip on the handle.
        val markers = when (style) {
            // The bumper sits behind, smaller, and the trigger in front of it, bigger: drawn in that order.
            ControllerStyle.FRAME -> listOf(
                ControlZone("shoulder", ZoneShape.PILL, 0.50f, 0.025f, 0.19f, 0.05f, groups = setOf("shoulder", "bumper")),
                ControlZone("trigger", ZoneShape.PILL, 0.50f, 0.105f, 0.30f, 0.06f),
                ControlZone("squeeze", ZoneShape.PILL, grip.x, grip.y, 0.13f, 0.075f),
            )
            else -> listOf(
                ControlZone("trigger", ZoneShape.PILL, 0.50f, 0.05f, 0.20f, 0.05f),
                ControlZone("squeeze", ZoneShape.PILL, grip.x, grip.y, 0.13f, 0.075f),
            )
        }
        return front + markers
    }

    /** The same control on the right controller: mirrored, and named as the right controller names it. */
    private fun rightOf(style: ControllerStyle, zone: ControlZone): ControlZone? {
        val renamed: String? = when (style) {
            ControllerStyle.FRAME -> when (zone.group) {
                "dpad_up" -> "y"
                "dpad_down" -> "a"
                // Mirrored, the arm that was on the left is now on the right.
                "dpad_left" -> "b"
                "dpad_right" -> "x"
                "dpad_hub" -> null
                "view" -> "menu"
                else -> zone.group
            }
            ControllerStyle.QUEST -> when (zone.group) {
                "x" -> "a"
                "y" -> "b"
                "menu" -> "system"
                "capture" -> "capture"
                else -> zone.group
            }
            ControllerStyle.PICO -> when (zone.group) {
                "x" -> "a"
                "y" -> "b"
                "menu" -> "capture"
                else -> zone.group
            }
            ControllerStyle.GENERIC -> when (zone.group) {
                "x" -> "a"
                "y" -> "b"
                "menu" -> null
                else -> zone.group
            }
        }
        val name = renamed ?: return null
        val face = name in FACE_GROUPS
        val decor = zone.decor || name == "capture" || (style == ControllerStyle.QUEST && name == "system") || (style == ControllerStyle.PICO && name == "system")
        return zone.copy(
            group = name,
            groups = if (zone.groups.size > 1) zone.groups else setOf(name),
            cx = 1f - zone.cx,
            shape = if (face) ZoneShape.CIRCLE else zone.shape,
            decor = decor,
            ring = if (zone.ring != null) BLUE else null,
        )
    }

    /** The control a touch at ([x], [y]) hits among [zones], or null: the nearest one, with a little room around each. */
    fun zoneAt(zones: List<ControlZone>, x: Float, y: Float): ControlZone? =
        zones.filter { !it.decor }
            .map { it to reach(it, x, y) }
            .filter { it.second <= 1f }
            .minByOrNull { it.second }
            ?.first

    /** How far a point is from the middle of a zone, 1 being its (widened) edge. */
    private fun reach(zone: ControlZone, x: Float, y: Float): Float {
        val dx = (x - zone.cx) / (zone.w / 2 + TOUCH_SLOP)
        val dy = (y - zone.cy) / (zone.h / 2 + TOUCH_SLOP)
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    /** The zone of this controller that a control of the game is, or null when the drawing has none for it. */
    fun zoneOf(zones: List<ControlZone>, ref: ControlRef): ControlZone? = zones.firstOrNull { !it.decor && ref.group in it.groups }

    /** The control of the game that [zone] stands for among [detected], or null when the game does not use it (it is not touchable then). */
    fun sourceFor(zone: ControlZone, hand: Hand, detected: List<ControlRef>): ControlRef? =
        detected.firstOrNull { it.hand == hand && it.group in zone.groups && it.group != "thumbrest" }

    /** The controls the game uses on one Steam Frame controller, by the name of their zone: each in its colour, standing out when nothing else is selected or it is. */
    fun gameLights(hand: Hand, detected: List<ControlRef>, focused: ControlRef?, linked: Set<ControlRef> = emptySet()): Map<String, ControllerLight> =
        gameLights(hand, detected, setOfNotNull(focused), linked)

    /** The same for several controls standing out at once (the ones going to a hovered button of the device). */
    fun gameLights(hand: Hand, detected: List<ControlRef>, active: Set<ControlRef>, linked: Set<ControlRef>): Map<String, ControllerLight> {
        val zones = zones(ControllerStyle.FRAME, hand)
        val lights = LinkedHashMap<String, ControllerLight>()
        for (ref in detected) {
            if (ref.hand != hand) continue
            val zone = zoneOf(zones, ref) ?: continue
            val chosen = active.any { it.hand == hand && it.group in zone.groups }
            lights[zone.group] = ControllerLight(colorGroupOf(zone.group), emphasised = active.isEmpty() || chosen, selected = chosen, linked = ref in linked)
        }
        return lights
    }

    /**
     * What lights up on one controller of the device, by the name of the control: every destination of the game's controls, in the colour of the control
     * that lands there (the selected one's when it is among them), standing out when it is where the selected control goes. A button that receives
     * more than one control carries their number in [ControllerLight.shareCount].
     */
    fun deviceLights(hand: Hand, allocation: Map<ControlRef, List<ControlRef>>, focused: ControlRef?, linked: Set<ControlRef> = emptySet()): Map<String, ControllerLight> =
        deviceLights(hand, allocation, setOfNotNull(focused), linked)

    fun deviceLights(hand: Hand, allocation: Map<ControlRef, List<ControlRef>>, active: Set<ControlRef>, linked: Set<ControlRef>): Map<String, ControllerLight> {
        val sourcesByTarget = LinkedHashMap<String, MutableList<ControlRef>>()
        for ((source, targets) in allocation) for (target in targets) if (target.hand == hand) sourcesByTarget.getOrPut(target.group) { mutableListOf() } += source
        return sourcesByTarget.mapValues { (_, sources) ->
            val lead = sources.firstOrNull { it in active } ?: sources.first()
            ControllerLight(colorGroupOf(lead.group), emphasised = active.isEmpty() || sources.any { it in active }, shareCount = sources.size, selected = sources.any { it in active }, linked = sources.any { it in linked })
        }
    }

    /** The zones of a device controller that can receive a control of the game (the others are drawn, never lit). */
    fun targetZones(style: ControllerStyle, hand: Hand): List<ControlZone> =
        zones(style, hand).filter { !it.decor && ControlRef(hand, it.group) in ControllerLayout.targets }
}
