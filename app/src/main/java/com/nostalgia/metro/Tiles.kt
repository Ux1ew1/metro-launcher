package com.nostalgia.metro

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Fmt {
    private val loc: Locale get() = Locale.getDefault()
    fun time(ctx: Context, t: Long): String =
        SimpleDateFormat(if (DateFormat.is24HourFormat(ctx)) "H:mm" else "h:mm", loc).format(Date(t))

    fun weekday(t: Long): String = SimpleDateFormat("EEEE", loc).format(Date(t)).lowercase(loc)
    fun dayMonth(t: Long): String = SimpleDateFormat("d MMMM", loc).format(Date(t)).lowercase(loc)
    fun day(t: Long): String = SimpleDateFormat("d", loc).format(Date(t))
    fun monthYear(t: Long): String = SimpleDateFormat("LLLL yyyy", loc).format(Date(t)).lowercase(loc)
}

@Composable
fun TileFace(spec: TileSpec, app: AppEntry?, flipEnabled: Boolean) {
    when (spec.kind) {
        TileKind.APP -> AppTileFace(spec, app)
        TileKind.CLOCK -> ClockTile(spec, flipEnabled)
        TileKind.CALENDAR -> CalendarTile(spec, flipEnabled)
    }
}

@Composable
private fun AppTileFace(spec: TileSpec, app: AppEntry?) {
    val m = LocalMetro.current
    Box(Modifier.fillMaxSize().background(m.tile)) {
        val mono = if (m.monoIcons) app?.mono else null
        val bmp = mono ?: app?.icon
        val small = spec.size == TileSize.SMALL
        var frac = when (spec.size) {
            TileSize.SMALL -> 0.6f
            TileSize.MEDIUM -> 0.42f
            TileSize.WIDE -> 0.46f
            TileSize.LARGE -> 0.3f
        }
        // глиф монохромной иконки занимает лишь центр картинки — компенсируем
        if (mono != null) frac = (frac * 1.35f).coerceAtMost(0.9f)
        if (bmp != null) {
            Image(
                bitmap = bmp,
                contentDescription = app?.label,
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = if (small) 0.dp else (-6).dp)
                    .fillMaxHeight(frac)
                    .aspectRatio(1f),
            )
        }
        if (!small && app != null) {
            MText(
                app.label, 13.sp, Color.White, FontWeight.Normal,
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 8.dp, end = 8.dp, bottom = 6.dp),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun TileLabel(text: String, modifier: Modifier) {
    MText(text, 13.sp, Color.White, FontWeight.Normal, modifier, maxLines = 1)
}

@Composable
private fun ClockTile(spec: TileSpec, flip: Boolean) {
    val m = LocalMetro.current
    val ctx = LocalContext.current
    val now = LocalNow.current
    val big = when (spec.size) {
        TileSize.LARGE -> 80.sp
        TileSize.WIDE -> 56.sp
        else -> 38.sp
    }
    val medium = spec.size == TileSize.MEDIUM
    FlipBox(
        Modifier.fillMaxSize(), flip,
        front = {
            Box(Modifier.fillMaxSize().background(m.tile).padding(10.dp)) {
                Column(Modifier.align(Alignment.BottomStart)) {
                    MText(Fmt.time(ctx, now), big, Color.White, FontWeight.Light, lineHeight = big)
                    if (!medium) MText("${Fmt.weekday(now)}, ${Fmt.dayMonth(now)}", 15.sp, Color.White, maxLines = 1)
                }
            }
        },
        back = {
            Box(Modifier.fillMaxSize().background(m.tile).padding(10.dp)) {
                Column(Modifier.align(Alignment.TopStart)) {
                    MText(Fmt.weekday(now), if (medium) 20.sp else 32.sp, Color.White, maxLines = 1)
                    MText(Fmt.dayMonth(now), if (medium) 16.sp else 22.sp, Color.White, maxLines = 1)
                }
                TileLabel("часы", Modifier.align(Alignment.BottomStart))
            }
        },
    )
}

@Composable
private fun CalendarTile(spec: TileSpec, flip: Boolean) {
    val m = LocalMetro.current
    val now = LocalNow.current
    if (spec.size == TileSize.SMALL) {
        Box(Modifier.fillMaxSize().background(m.tile), contentAlignment = Alignment.Center) {
            MText(Fmt.day(now), 30.sp, Color.White)
        }
        return
    }
    val wide = spec.size == TileSize.WIDE
    FlipBox(
        Modifier.fillMaxSize(), flip,
        front = {
            Box(Modifier.fillMaxSize().background(m.tile).padding(10.dp)) {
                Column(Modifier.align(Alignment.TopStart)) {
                    MText(Fmt.day(now), 58.sp, Color.White, lineHeight = 60.sp)
                    MText(Fmt.weekday(now), 17.sp, Color.White, maxLines = 1)
                }
                TileLabel("календарь", Modifier.align(Alignment.BottomStart))
            }
        },
        back = {
            Box(Modifier.fillMaxSize().background(m.tile).padding(10.dp)) {
                Column(Modifier.align(Alignment.TopStart)) {
                    MText("сегодня", 17.sp, Color.White)
                    MText(Fmt.dayMonth(now), if (wide) 30.sp else 22.sp, Color.White, maxLines = 2)
                    if (wide) MText(Fmt.monthYear(now), 15.sp, Color.White)
                }
                TileLabel("календарь", Modifier.align(Alignment.BottomStart))
            }
        },
    )
}
