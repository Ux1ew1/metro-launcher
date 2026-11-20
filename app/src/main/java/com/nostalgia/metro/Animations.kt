package com.nostalgia.metro

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.MutatorMutex
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.random.Random

/*
 * Анимация «турникет» (turnstile) из Windows Phone: все элементы страницы
 * поворачиваются вокруг ЛЕВОГО КРАЯ ЭКРАНА с задержкой по вертикали («перо»),
 * а нажатая плитка улетает последней.
 */

enum class TurnMode { None, ForwardOut, BackwardIn, ForwardIn, BackwardOut }

object Turnstile {
    const val STAGGER_MS = 190f   // разброс задержки сверху вниз
    const val COLUMN_MS = 45f     // небольшая задержка слева направо
    const val TAP_EXTRA_MS = 80f  // нажатая плитка уходит последней
    const val ITEM_MS = 250f      // длительность поворота одного элемента
    val TOTAL_MS = (STAGGER_MS + COLUMN_MS + TAP_EXTRA_MS + ITEM_MS).toInt()

    // Знак угла задаёт направление поворота. Если на вашем устройстве плитки
    // «уходят» не в ту сторону — поменяйте знаки здесь.
    const val OUT_ANGLE = 70f  // «вперёд-уход» / «назад-приход»
    const val IN_ANGLE = -55f  // «вперёд-приход» / «назад-уход» (страница настроек)
}

@Stable
class TurnstileState {
    var mode by mutableStateOf(TurnMode.None)
        private set
    var progress by mutableFloatStateOf(0f)
        private set
    var tappedKey: Any? = null
        private set
    var screenWidth = 1080f
    var screenHeight = 1920f
    private val mutex = MutatorMutex()

    suspend fun run(newMode: TurnMode, tapped: Any? = null) = mutex.mutate {
        tappedKey = tapped
        mode = newMode
        progress = 0f
        animate(0f, 1f, animationSpec = tween(Turnstile.TOTAL_MS, easing = LinearEasing)) { v, _ ->
            progress = v
        }
        if (newMode == TurnMode.ForwardIn || newMode == TurnMode.BackwardIn) {
            mode = TurnMode.None
            tappedKey = null
        }
        Unit
    }

    suspend fun hide() = mutex.mutate { hideImmediately() }

    suspend fun show() = mutex.mutate {
        mode = TurnMode.None
        progress = 0f
        tappedKey = null
    }

    fun hideImmediately() {
        mode = TurnMode.ForwardOut
        progress = 1f
        tappedKey = null
    }
}

private class PosHolder {
    var x = 0f
    var y = 0f
}

fun Modifier.turnstileItem(state: TurnstileState, key: Any? = null): Modifier = composed {
    val pos = remember { PosHolder() }
    this
        .onGloballyPositioned {
            val p = it.positionInRoot()
            pos.x = p.x
            pos.y = p.y
        }
        .graphicsLayer {
            val mode = state.mode
            if (mode == TurnMode.None) {
                rotationY = 0f
                alpha = 1f
                return@graphicsLayer
            }
            val h = state.screenHeight.coerceAtLeast(1f)
            val w = state.screenWidth.coerceAtLeast(1f)
            val tapped = key != null && key == state.tappedKey
            val delayMs = if (tapped && mode == TurnMode.ForwardOut) {
                Turnstile.STAGGER_MS + Turnstile.COLUMN_MS + Turnstile.TAP_EXTRA_MS
            } else {
                (pos.y / h).coerceIn(0f, 1f) * Turnstile.STAGGER_MS +
                    (pos.x / w).coerceIn(0f, 1f) * Turnstile.COLUMN_MS
            }
            val t = ((state.progress * Turnstile.TOTAL_MS - delayMs) / Turnstile.ITEM_MS).coerceIn(0f, 1f)
            val easeIn = t * t
            val easeOut = 1f - (1f - t) * (1f - t) * (1f - t)
            when (mode) {
                TurnMode.ForwardOut -> {
                    rotationY = Turnstile.OUT_ANGLE * easeIn
                    alpha = if (t >= 1f) 0f else 1f - easeIn * easeIn
                }
                TurnMode.BackwardIn -> {
                    rotationY = Turnstile.OUT_ANGLE * (1f - easeOut)
                    alpha = (t * 3f).coerceAtMost(1f)
                }
                TurnMode.ForwardIn -> {
                    rotationY = Turnstile.IN_ANGLE * (1f - easeOut)
                    alpha = (t * 3f).coerceAtMost(1f)
                }
                TurnMode.BackwardOut -> {
                    rotationY = Turnstile.IN_ANGLE * easeIn
                    alpha = if (t >= 1f) 0f else 1f - easeIn * easeIn
                }
                TurnMode.None -> Unit
            }
            // ось вращения — левый край экрана
            transformOrigin = TransformOrigin(if (size.width > 0f) -pos.x / size.width else 0f, 0.5f)
            cameraDistance = 14f * density
        }
}

/**
 * Tilt-эффект WP: при нажатии плитка «продавливается» в сторону пальца,
 * при нажатии в центр — просто немного утапливается.
 */
fun Modifier.tilt(enabled: Boolean = true, maxAngle: Float = 10f): Modifier = composed {
    val rx = remember { Animatable(0f) }
    val ry = remember { Animatable(0f) }
    val sc = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    this
        .pointerInput(enabled) {
            if (!enabled) return@pointerInput
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val w = size.width.toFloat().coerceAtLeast(1f)
                val h = size.height.toFloat().coerceAtLeast(1f)
                val nx = (down.position.x / w - 0.5f) * 2f
                val ny = (down.position.y / h - 0.5f) * 2f
                val edge = max(abs(nx), abs(ny)).coerceIn(0f, 1f)
                // большие плитки наклоняются слабее
                val k = (200.dp.toPx() / max(w, h)).coerceIn(0.3f, 1f)
                scope.launch { ry.animateTo(nx * maxAngle * k, tween(90)) }
                scope.launch { rx.animateTo(-ny * maxAngle * k, tween(90)) }
                scope.launch { sc.animateTo(1f - 0.01f - 0.045f * (1f - edge), tween(90)) }
                waitForUpOrCancellation()
                val back = spring<Float>(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow)
                scope.launch { rx.animateTo(0f, back) }
                scope.launch { ry.animateTo(0f, back) }
                scope.launch { sc.animateTo(1f, back) }
            }
        }
        .graphicsLayer {
            rotationX = rx.value
            rotationY = ry.value
            scaleX = sc.value
            scaleY = sc.value
            cameraDistance = 14f * density
        }
}

/** Живая плитка: периодически переворачивается вокруг горизонтальной оси. */
@Composable
fun FlipBox(
    modifier: Modifier,
    enabled: Boolean,
    front: @Composable BoxScope.() -> Unit,
    back: @Composable BoxScope.() -> Unit,
) {
    val rot = remember { Animatable(0f) }
    LaunchedEffect(enabled) {
        if (!enabled) {
            rot.snapTo(0f)
            return@LaunchedEffect
        }
        while (true) {
            delay(Random.nextLong(4500, 10000))
            val target = if (rot.value < 90f) 180f else 0f
            rot.animateTo(target, tween(750, easing = FastOutSlowInEasing))
        }
    }
    Box(
        modifier.graphicsLayer {
            rotationX = rot.value
            cameraDistance = 14f * density
        }
    ) {
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = if (rot.value <= 90f) 1f else 0f }, content = front)
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    rotationX = 180f
                    alpha = if (rot.value > 90f) 1f else 0f
                },
            content = back,
        )
    }
}
