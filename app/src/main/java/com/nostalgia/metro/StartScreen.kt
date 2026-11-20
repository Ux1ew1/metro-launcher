package com.nostalgia.metro

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import androidx.compose.ui.unit.toOffset
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import kotlin.math.floor
import kotlin.math.roundToInt

private class DragState {
    var id by mutableStateOf<String?>(null)
    var pos by mutableStateOf(Offset.Zero) // левый верхний угол плитки в координатах сетки
    var grab = Offset.Zero                 // точка касания внутри плитки
    var lastTarget: String? = null
}

private class Origin {
    var y = 0f
}

private const val END_TARGET = "__end__"

@Composable
fun StartScreen(ctl: LauncherController, scroll: ScrollState, onOpenAppList: () -> Unit) {
    val density = LocalDensity.current
    val apps = ctl.repo.apps
    val appMap = remember(apps) { apps.associateBy { it.key } }
    val loaded = apps.isNotEmpty()
    val tiles = ctl.tiles.toList().filter { it.kind != TileKind.APP || !loaded || it.appKey?.let { k -> appMap.containsKey(k) } == true }
    val cols = if (ctl.settings.moreTiles) 6 else 4
    val placement = remember(tiles, cols) { packTiles(tiles, cols) }
    val drag = remember { DragState() }
    val gridOrigin = remember { Origin() }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val side = 12.dp
        val gap = 6.dp
        val topPad = 20.dp
        val cell = (maxWidth - side * 2 - gap * (cols - 1)) / cols
        val step = cell + gap
        val sidePx = with(density) { side.toPx() }
        val topPx = with(density) { topPad.toPx() }
        val stepPx = with(density) { step.toPx() }
        val screenH = constraints.maxHeight.toFloat()

        fun tileW(spec: TileSpec): Dp {
            val w = minOf(spec.size.w, cols)
            return cell * w + gap * (w - 1)
        }

        fun tileH(spec: TileSpec): Dp = cell * spec.size.h + gap * (spec.size.h - 1)

        // перестановка плиток под пальцем при перетаскивании
        val reorder: () -> Unit = reorder@{
            val id = drag.id ?: return@reorder
            val finger = drag.pos + drag.grab
            val col = floor((finger.x - sidePx) / stepPx).toInt()
            val row = floor((finger.y - topPx) / stepPx).toInt()
            if (row >= placement.rows) {
                if (drag.lastTarget != END_TARGET) {
                    drag.lastTarget = END_TARGET
                    ctl.moveTileToEnd(id)
                }
                return@reorder
            }
            if (col < 0 || col >= cols || row < 0) {
                drag.lastTarget = null
                return@reorder
            }
            val hit = tiles.firstOrNull { t ->
                if (t.id == id) return@firstOrNull false
                val p = placement.cells[t.id] ?: return@firstOrNull false
                col >= p.x && col < p.x + minOf(t.size.w, cols) && row >= p.y && row < p.y + t.size.h
            }?.id
            if (hit == null) {
                drag.lastTarget = null
            } else if (hit != drag.lastTarget) {
                drag.lastTarget = hit
                ctl.moveTile(id, hit)
            }
        }
        val currentReorder by rememberUpdatedState(reorder)

        // автопрокрутка, когда плитку тащат к краю экрана
        LaunchedEffect(drag.id) {
            if (drag.id == null) return@LaunchedEffect
            val edge = with(density) { 96.dp.toPx() }
            while (drag.id != null) {
                withFrameNanos { }
                val fingerY = gridOrigin.y + drag.pos.y + drag.grab.y
                val d = when {
                    fingerY < edge -> -(edge - fingerY) * 0.2f
                    fingerY > screenH - edge -> (fingerY - (screenH - edge)) * 0.2f
                    else -> 0f
                }
                if (d != 0f) {
                    val used = scroll.scrollBy(d)
                    if (used != 0f) {
                        drag.pos = drag.pos + Offset(0f, used)
                        currentReorder()
                    }
                }
            }
        }

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scroll, enabled = drag.id == null)
        ) {
            Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(topPad + step * placement.rows + 12.dp)
                    .onGloballyPositioned { gridOrigin.y = it.positionInRoot().y }
                    .then(
                        if (ctl.editMode) {
                            Modifier.pointerInput(Unit) { detectTapGestures { ctl.exitEdit() } }
                        } else {
                            Modifier
                        }
                    )
            ) {
                for (spec in tiles) {
                    key(spec.id) {
                        val c = placement.cells[spec.id] ?: IntOffset.Zero
                        val target = IntOffset(
                            (sidePx + c.x * stepPx).roundToInt(),
                            (topPx + c.y * stepPx).roundToInt(),
                        )
                        TileSlot(
                            ctl = ctl,
                            spec = spec,
                            app = spec.appKey?.let { appMap[it] },
                            target = target,
                            width = tileW(spec),
                            height = tileH(spec),
                            drag = drag,
                            onDragMove = { currentReorder() },
                        )
                    }
                }

                // кнопки «открепить» и «размер» на выбранной плитке
                val sel = ctl.selectedTile
                val selSpec = tiles.firstOrNull { it.id == sel }
                val selCell = sel?.let { placement.cells[it] }
                if (ctl.editMode && sel != null && selSpec != null && selCell != null && drag.id == null) {
                    val x = side + step * selCell.x
                    val y = topPad + step * selCell.y
                    val w = tileW(selSpec)
                    val h = tileH(selSpec)
                    val next = selSpec.resized().size
                    val grows = next.w * next.h > selSpec.size.w * selSpec.size.h
                    EditButton(
                        Icons.Filled.Close, "открепить",
                        Modifier.offset(x + w - 26.dp, y - 10.dp).zIndex(5f),
                    ) { ctl.unpin(sel) }
                    EditButton(
                        Icons.AutoMirrored.Filled.ArrowForward, "изменить размер",
                        Modifier.offset(x + w - 26.dp, y + h - 26.dp).zIndex(5f),
                        rotation = if (grows) -45f else 135f,
                    ) { ctl.resize(sel) }
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = side, end = side, top = 8.dp, bottom = 28.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                CircleButton(
                    Icons.AutoMirrored.Filled.ArrowForward, "все приложения", onOpenAppList,
                    Modifier.turnstileItem(ctl.turnstile),
                )
            }
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TileSlot(
    ctl: LauncherController,
    spec: TileSpec,
    app: AppEntry?,
    target: IntOffset,
    width: Dp,
    height: Dp,
    drag: DragState,
    onDragMove: () -> Unit,
) {
    val ctx = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val isDragging = drag.id == spec.id
    val anim = remember { Animatable(target, IntOffset.VectorConverter) }
    LaunchedEffect(target, isDragging) {
        if (!isDragging) anim.animateTo(target, spring(dampingRatio = 0.82f, stiffness = 420f))
    }
    val edit = ctl.editMode
    val selected = edit && ctl.selectedTile == spec.id
    val editScale by animateFloatAsState(
        when {
            !edit -> 1f
            selected -> 1.05f
            else -> 0.9f
        },
        spring(stiffness = 500f), label = "editScale",
    )
    val editAlpha by animateFloatAsState(if (!edit || selected) 1f else 0.7f, tween(200), label = "editAlpha")
    val moveState = rememberUpdatedState(onDragMove)

    val gestures = if (!edit) {
        Modifier
            .tilt()
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onLongClick = { ctl.enterEdit(spec.id) },
                onClick = { ctl.openTile(ctx, spec, app) },
            )
    } else {
        fun finish() {
            scope.launch {
                anim.snapTo(drag.pos.round())
                drag.id = null
                ctl.saveTiles()
            }
        }
        Modifier
            .pointerInput(spec.id, "tap") { detectTapGestures { ctl.selectedTile = spec.id } }
            .pointerInput(spec.id, "drag") {
                detectDragGestures(
                    onDragStart = { o ->
                        ctl.selectedTile = spec.id
                        drag.pos = anim.value.toOffset()
                        drag.grab = o
                        drag.lastTarget = null
                        drag.id = spec.id
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    onDragEnd = { finish() },
                    onDragCancel = { finish() },
                    onDrag = { change, amount ->
                        change.consume()
                        drag.pos += amount
                        moveState.value()
                    },
                )
            }
    }

    Box(
        Modifier
            .offset { if (isDragging) drag.pos.round() else anim.value }
            .zIndex(if (selected || isDragging) 3f else 0f)
            .size(width, height)
            .turnstileItem(ctl.turnstile, spec.id)
            .graphicsLayer {
                scaleX = editScale
                scaleY = editScale
                alpha = editAlpha
            }
            .then(gestures)
    ) {
        TileFace(spec, app, flipEnabled = !edit)
    }
}

@Composable
private fun EditButton(
    icon: ImageVector,
    description: String,
    modifier: Modifier,
    rotation: Float = 0f,
    onClick: () -> Unit,
) {
    val m = LocalMetro.current
    Box(
        modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(m.fg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, tint = m.bg, modifier = Modifier.size(20.dp).rotate(rotation))
    }
}
