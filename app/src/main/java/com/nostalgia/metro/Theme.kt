package com.nostalgia.metro

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class AccentColor(val name: String, val argb: Long) {
    val color: Color get() = Color(argb)
    val intValue: Int get() = argb.toInt()
}

/** Палитра акцентных цветов Windows Phone 8. */
val AccentColors = listOf(
    AccentColor("лайм", 0xFFA4C400),
    AccentColor("зелёный", 0xFF60A917),
    AccentColor("изумрудный", 0xFF008A00),
    AccentColor("бирюзовый", 0xFF00ABA9),
    AccentColor("голубой", 0xFF1BA1E2),
    AccentColor("кобальт", 0xFF0050EF),
    AccentColor("индиго", 0xFF6A00FF),
    AccentColor("фиолетовый", 0xFFAA00FF),
    AccentColor("розовый", 0xFFF472D0),
    AccentColor("пурпурный", 0xFFD80073),
    AccentColor("малиновый", 0xFFA20025),
    AccentColor("красный", 0xFFE51400),
    AccentColor("оранжевый", 0xFFFA6800),
    AccentColor("янтарный", 0xFFF0A30A),
    AccentColor("жёлтый", 0xFFE3C800),
    AccentColor("коричневый", 0xFF825A2C),
    AccentColor("оливковый", 0xFF6D8764),
    AccentColor("стальной", 0xFF647687),
    AccentColor("лиловый", 0xFF76608A),
    AccentColor("серо-коричневый", 0xFF87794E),
)

data class Metro(
    val bg: Color,
    val fg: Color,
    val sub: Color,
    val accent: Color,
    val tile: Color,
    val listBg: Color,
    val menuBg: Color,
    val menuFg: Color,
    val disabled: Color,
    val dark: Boolean,
    val monoIcons: Boolean,
    val wallpaper: Boolean,
) {
    companion object {
        fun from(s: LauncherSettings): Metro {
            val accent = Color(s.accent)
            val bg = if (s.dark) Color.Black else Color.White
            return Metro(
                bg = bg,
                fg = if (s.dark) Color.White else Color.Black,
                sub = if (s.dark) Color(0xFF9A9A9A) else Color(0xFF666666),
                accent = accent,
                tile = if (s.wallpaper) accent.copy(alpha = 0.82f) else accent,
                listBg = if (s.wallpaper) bg.copy(alpha = 0.86f) else bg,
                menuBg = if (s.dark) Color(0xFFE8E8E8) else Color(0xFF1F1F1F),
                menuFg = if (s.dark) Color.Black else Color.White,
                disabled = if (s.dark) Color(0xFF222222) else Color(0xFFDDDDDD),
                dark = s.dark,
                monoIcons = s.monoIcons,
                wallpaper = s.wallpaper,
            )
        }
    }
}

val LocalMetro = staticCompositionLocalOf { Metro.from(LauncherSettings()) }

/** Текущее время, обновляется раз в минуту. */
val LocalNow = compositionLocalOf { System.currentTimeMillis() }

@Composable
fun MText(
    text: String,
    size: TextUnit,
    color: Color = LocalMetro.current.fg,
    weight: FontWeight = FontWeight.Light,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    lineHeight: TextUnit = TextUnit.Unspecified,
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        fontSize = size,
        fontWeight = weight,
        fontFamily = FontFamily.SansSerif,
        maxLines = maxLines,
        softWrap = maxLines != 1,
        overflow = TextOverflow.Ellipsis,
        lineHeight = lineHeight,
    )
}

/** Круглая кнопка-глиф в стиле панели приложений WP. */
@Composable
fun CircleButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 46.dp,
) {
    val m = LocalMetro.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Box(
        modifier
            .size(size)
            .tilt()
            .clip(CircleShape)
            .background(if (pressed) m.fg else Color.Transparent)
            .border(2.dp, m.fg, CircleShape)
            .clickable(interactionSource = source, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, tint = if (pressed) m.bg else m.fg, modifier = Modifier.size(size * 0.5f))
    }
}

/** Прямоугольная кнопка: при нажатии заливается акцентным цветом. */
@Composable
fun MetroButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val m = LocalMetro.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Box(
        modifier
            .tilt(maxAngle = 6f)
            .background(if (pressed) m.accent else Color.Transparent)
            .border(2.dp, if (pressed) m.accent else m.fg)
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
    ) {
        MText(text, 18.sp, if (pressed) Color.White else m.fg, FontWeight.Normal)
    }
}

/** Переключатель WP7/8: рамка, заливка акцентом и прямоугольный «бегунок». */
@Composable
fun MetroToggle(checked: Boolean, modifier: Modifier = Modifier) {
    val m = LocalMetro.current
    val t by animateFloatAsState(if (checked) 1f else 0f, tween(160), label = "toggle")
    Canvas(modifier.size(64.dp, 28.dp)) {
        val stroke = 2.dp.toPx()
        drawRect(
            color = m.fg,
            topLeft = Offset(stroke / 2, stroke / 2),
            size = Size(size.width - stroke, size.height - stroke),
            style = Stroke(stroke),
        )
        val pad = 5.dp.toPx()
        val thumbW = 13.dp.toPx()
        val travel = size.width - pad * 2 - thumbW
        val thumbX = pad + travel * t
        if (thumbX > pad) {
            drawRect(
                color = m.accent,
                topLeft = Offset(pad, pad),
                size = Size(thumbX - pad, size.height - pad * 2),
            )
        }
        drawRect(color = m.fg, topLeft = Offset(thumbX, 0f), size = Size(thumbW, size.height))
    }
}

@Composable
fun ToggleRow(title: String, checked: Boolean, hint: String? = null, onChange: (Boolean) -> Unit) {
    val m = LocalMetro.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onChange(!checked) }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            MText(title, 20.sp, m.fg, FontWeight.Normal)
            MText(if (checked) "Включено" else "Выключено", 16.sp, m.sub)
            if (hint != null) MText(hint, 13.sp, m.sub)
        }
        MetroToggle(checked)
    }
}
