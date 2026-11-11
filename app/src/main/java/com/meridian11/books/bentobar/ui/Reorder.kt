package com.meridian11.books.bentobar.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.isTertiaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.round
import com.meridian11.books.bentobar.data.BarConfig
import com.meridian11.books.bentobar.data.Part
import com.meridian11.books.bentobar.data.Position
import com.meridian11.books.bentobar.data.canGoBelow
import com.meridian11.books.bentobar.data.partOf
import com.meridian11.books.bentobar.data.parts
import com.meridian11.books.bentobar.data.placed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** One stretch of the bar's list a row can be dropped in: a part of an area. */
data class Zone(val area: Position, val part: Part)

/** Where a dragged row would land: a zone, and a place among that zone's other rows. */
data class DropSpot(val zone: Zone, val index: Int)

/**
 * The drop spot for a dragged row whose middle is at [y]. [zones] are the zones' vertical extents, top to
 * bottom (a zone starts at its heading: the list's head, an area's name, the line between the parts); [rows] the
 * middles of each zone's other rows (not the dragged one), in order. In a gap between two zones the [current] spot
 * stays, so the opening doesn't flicker.
 */
fun dropSpot(
    y: Float,
    zones: List<Pair<Zone, ClosedFloatingPointRange<Float>>>,
    rows: Map<Zone, List<Float>>,
    current: DropSpot,
): DropSpot {
    if (zones.isEmpty()) return current
    val zone = zones.firstOrNull { y in it.second }?.first ?: when {
        y < zones.first().second.start -> zones.first().first
        y > zones.last().second.endInclusive -> zones.last().first
        else -> return current
    }
    return DropSpot(zone, rows[zone].orEmpty().count { it < y })
}

/**
 * Drag to reorder within the bar's list and across the line between its parts. The gesture lives on the
 * container (a row that changes part is drawn anew, which would cancel a gesture on the row itself). While
 * dragging, the row's place shows an opening ([dragging] in [preview]), a copy of the row follows the pointer above
 * the list, and the layout changes once, on the drop, by [placed]: the one step a drop is, with its rules (a type
 * without a rule is not put under the line in the default mode: its opening stays where it was).
 */
@Stable
class Reorder {
    /** The row being dragged, or null. */
    var dragging by mutableStateOf<String?>(null)
        private set
    var spot by mutableStateOf<DropSpot?>(null)
        private set
    /** A drop on its way to the Store (whose flow delivers it a frame later), so the row doesn't jump back. */
    var pending by mutableStateOf<Pair<String, DropSpot>?>(null)
    /** The pointer, in the container's coordinates. */
    private var pointerY by mutableFloatStateOf(0f)
    private var pointerInWindow = Offset.Zero
    private var grabY = 0f
    private var settleTop by mutableStateOf<Float?>(null)
    var rowLeft = 0f
        private set
    var rowWidth = 0f
        private set
    var rowHeight = 0f
        private set

    /** The layout, as the Store has it (set by the composable). */
    var config: BarConfig = BarConfig()
    var container: LayoutCoordinates? = null
    /** Each row's bounds (the opening registers under the dragged id), its draggable area and its handle. */
    val rows = HashMap<String, LayoutCoordinates>()
    val grabAreas = HashMap<String, LayoutCoordinates>()
    val handles = HashMap<String, LayoutCoordinates>()
    /** Each zone's heading: a zone runs from its heading to the next one's, the last to the list's end. */
    val headings = HashMap<Zone, LayoutCoordinates>()
    /**
     * The areas stand side by side, each in its lane. A zone's place is told by height alone, so a row is carried up and
     * down its own lane; a drag across lanes needs the pointer's x here too.
     */
    var abreast = false
    /** Told when the dragged row is carried under a line it can't go under (its type has no rule): the page says why. */
    var onRefused: (String) -> Unit = {}

    val busy: Boolean get() = dragging != null || settleTop != null

    /** The layout to list: with the dragged row where it would land while dragging, else the Store's. */
    fun preview(config: BarConfig): BarConfig {
        val id = dragging
        val s = spot
        val p = pending
        return when {
            id != null && s != null -> config.placed(id, s.zone.area, s.zone.part, s.index)
            p != null -> config.placed(p.first, p.second.zone.area, p.second.zone.part, p.second.index)
            else -> config
        }
    }

    /** The floating copy's top, in the container's coordinates. */
    val ghostTop: Float
        get() {
            settleTop?.let { return it }
            val max = ((container?.size?.height ?: 0) - rowHeight).coerceAtLeast(0f)
            return (pointerY - grabY).coerceIn(0f, max)
        }

    private fun bounds(c: LayoutCoordinates?) =
        container?.takeIf { it.isAttached }?.let { root -> c?.takeIf { it.isAttached }?.let { root.localBoundingBoxOf(it) } }

    /** The row under [pos]: anywhere on it for a mouse or pen, only the handle for touch (so fingers still scroll). */
    fun hit(pos: Offset, touch: Boolean): String? =
        (if (touch) handles else grabAreas).entries.firstOrNull { (_, c) -> bounds(c)?.contains(pos) == true }?.key

    fun start(id: String, down: Offset) {
        val item = config.items.firstOrNull { it.id == id } ?: return
        val part = config.partOf(item) ?: return
        val b = bounds(rows[id]) ?: return
        rowLeft = b.left; rowWidth = b.width; rowHeight = b.height
        grabY = down.y - b.top
        pointerY = down.y
        container?.let { pointerInWindow = it.localToWindow(down) }
        pending = null
        spot = DropSpot(Zone(item.area, part), config.parts(item.area)[part].indexOfFirst { it.id == id })
        dragging = id
    }

    fun pointerAt(local: Offset) {
        val c = container?.takeIf { it.isAttached } ?: return
        pointerInWindow = c.localToWindow(local)
        sync()
    }

    /** Follows the pointer again after the content moved under it (auto-scroll, mouse wheel). */
    fun sync() {
        val id = dragging ?: return
        val c = container?.takeIf { it.isAttached } ?: return
        if (settleTop != null) return
        pointerY = c.windowToLocal(pointerInWindow).y
        val current = spot ?: return
        val preview = preview(config)
        val tops = headings.filterKeys { !abreast || it.area == current.zone.area }.mapNotNull { (zone, h) -> bounds(h)?.let { zone to it.top } }.sortedBy { it.second }
        val spans = tops.mapIndexed { i, (zone, top) -> zone to top..(tops.getOrNull(i + 1)?.second ?: c.size.height.toFloat()) }
        val middles = tops.associate { (zone, _) ->
            zone to preview.parts(zone.area)[zone.part].filter { it.id != id }.mapNotNull { bounds(rows[it.id])?.center?.y }
        }
        val next = dropSpot(ghostTop + rowHeight / 2, spans, middles, current)
        if (next == current) return
        spot = next
        if (next.zone.part == Part.LOWER && next.zone != current.zone && config.items.firstOrNull { it.id == id }?.let { !config.canGoBelow(it) } == true) onRefused(id)
    }

    /** Pixels to scroll this frame: faster the closer the pointer is to the visible edge. */
    fun edgeSpeed(edge: Float, max: Float): Float {
        if (settleTop != null) return 0f
        val c = container?.takeIf { it.isAttached } ?: return 0f
        val visible = c.boundsInWindow()
        val y = pointerInWindow.y
        return when {
            y < visible.top + edge -> -max * ((visible.top + edge - y) / edge).coerceAtMost(1f)
            y > visible.bottom - edge -> max * ((y - (visible.bottom - edge)) / edge).coerceAtMost(1f)
            else -> 0f
        }
    }

    /** Glides the copy into the opening, then saves the new place. */
    suspend fun drop() {
        val id = dragging ?: return
        val s = spot ?: return cancel()
        val target = bounds(rows[id])?.top
        if (target != null) {
            val from = ghostTop
            settleTop = from
            animate(from, target, animationSpec = Motion.spec(tween(140))) { v, _ -> settleTop = v }
        }
        pending = id to s
        // Nothing changed (the same place, or a place its type can't take): nothing is on its way.
        if (!Undo.change { it.placed(id, s.zone.area, s.zone.part, s.index) }) pending = null
        dragging = null
        spot = null
        settleTop = null
    }

    fun cancel() {
        dragging = null
        spot = null
        settleTop = null
    }
}

/**
 * The drag gesture, on the container. It watches the Initial pass so that, once a press has moved
 * the full touch slop, it can consume the movement before the row's click sees it (no click after a
 * drag). Compose's own mouse slop is about 1 dp, which would turn slightly shaky clicks into drags.
 */
fun Modifier.reorderable(r: Reorder, scope: CoroutineScope): Modifier = pointerInput(r) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        if (r.busy) return@awaitEachGesture
        // Right and middle presses are for menus; touchpads may report no button at all, so those count.
        val buttons = currentEvent.buttons
        if (down.type == PointerType.Mouse && (buttons.isSecondaryPressed || buttons.isTertiaryPressed)) return@awaitEachGesture
        val id = r.hit(down.position, touch = down.type == PointerType.Touch) ?: return@awaitEachGesture
        val slop = viewConfiguration.touchSlop
        var travel = Offset.Zero
        while (true) {
            val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
            if (!change.pressed || change.isConsumed) return@awaitEachGesture
            travel += change.position - change.previousPosition
            if (travel.getDistance() > slop) {
                change.consume()
                break
            }
        }
        r.start(id, down.position)
        try {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.type == PointerEventType.Scroll) continue // the wheel still scrolls; sync() follows
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                change.consume()
                if (!change.pressed) break
                r.pointerAt(change.position)
            }
        } finally {
            // Released, lost or interrupted: the row lands where the opening is.
            if (r.dragging != null) scope.launch { r.drop() }
        }
    }
}

/** Slides a row to its new place when the rows around it change, instead of jumping there. */
fun Modifier.animatePlacement(): Modifier = composed {
    val scope = rememberCoroutineScope()
    val holder = remember { PlacementHolder() }
    onPlaced { c ->
        val t = c.positionInParent().round()
        holder.target = t
        if (holder.anim == null) holder.anim = Animatable(t, IntOffset.VectorConverter)
    }.then(Modifier.layoutOffset(holder, scope))
}

private class PlacementHolder {
    var target = IntOffset.Zero
    var anim: Animatable<IntOffset, AnimationVector2D>? = null
}

// absoluteOffset: positionInParent is physical, and the delta below is too. `offset` would mirror it in right-to-left, and an
// item that moves right there would start on the far side and slide back.
private fun Modifier.layoutOffset(holder: PlacementHolder, scope: CoroutineScope): Modifier =
    absoluteOffset {
        val a = holder.anim ?: return@absoluteOffset IntOffset.Zero
        if (a.targetValue != holder.target) {
            val t = holder.target
            scope.launch { a.animateTo(t, Motion.spec(spring(stiffness = Spring.StiffnessMediumLow, visibilityThreshold = IntOffset.VisibilityThreshold))) }
        }
        a.value - holder.target
    }
