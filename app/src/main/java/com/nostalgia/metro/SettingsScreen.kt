package com.nostalgia.metro

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SettingsScreen(ctl: LauncherController) {
    val m = LocalMetro.current
    val ctx = LocalContext.current
    val s = ctl.settings
    val ts = ctl.settingsTurnstile

    BackHandler { ctl.closeSettings() }

    fun open(intent: Intent) {
        try {
            ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(m.bg)
            // перехватываем касания, чтобы они не доходили до плиток под экраном
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
        ) {
            Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
            Spacer(Modifier.height(18.dp))
            MText("НАСТРОЙКИ", 15.sp, m.fg, FontWeight.Bold, Modifier.turnstileItem(ts))
            MText("начальный экран", 46.sp, m.fg, FontWeight.Light, Modifier.turnstileItem(ts), lineHeight = 52.sp)
            Spacer(Modifier.height(18.dp))

            Section(ts, "Тема") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Choice("тёмная", s.dark) { ctl.updateSettings { it.copy(dark = true) } }
                    Choice("светлая", !s.dark) { ctl.updateSettings { it.copy(dark = false) } }
                }
            }

            Section(ts, "Цвет элементов") {
                MText(
                    AccentColors.firstOrNull { it.intValue == s.accent }?.name ?: "",
                    18.sp, m.accent, FontWeight.Normal, Modifier.padding(bottom = 8.dp),
                )
                AccentColors.chunked(5).forEach { line ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        line.forEach { a ->
                            val sel = a.intValue == s.accent
                            Box(
                                Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .tilt()
                                    .background(a.color)
                                    .then(if (sel) Modifier.border(3.dp, m.fg) else Modifier)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                    ) { ctl.updateSettings { it.copy(accent = a.intValue) } }
                            )
                        }
                    }
                }
            }

            Section(ts, null) {
                ToggleRow("Показать больше плиток", s.moreTiles, "6 колонок вместо 4") { v ->
                    ctl.updateSettings { it.copy(moreTiles = v) }
                }
                ToggleRow(
                    "Монохромные значки", s.monoIcons,
                    "Android 13+, если приложение поддерживает тематические значки",
                ) { v -> ctl.updateSettings { it.copy(monoIcons = v) } }
                ToggleRow("Показывать обои за плитками", s.wallpaper, null) { v ->
                    ctl.updateSettings { it.copy(wallpaper = v) }
                }
            }

            Section(ts, null) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (s.wallpaper) {
                        MetroButton("выбрать обои") {
                            open(Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), "Обои"))
                        }
                    }
                    MetroButton("сделать лаунчером по умолчанию") { open(Intent(Settings.ACTION_HOME_SETTINGS)) }
                    MetroButton("сбросить начальный экран") { ctl.resetTiles() }
                }
            }

            MText(
                "Ностальгический лаунчер в стиле Metro. Неофициальный, не связан с Microsoft.",
                13.sp, m.sub, FontWeight.Normal, Modifier.padding(top = 24.dp, bottom = 24.dp).turnstileItem(ts),
            )
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
}

@Composable
private fun Section(ts: TurnstileState, title: String?, content: @Composable ColumnScope.() -> Unit) {
    val m = LocalMetro.current
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp)
            .turnstileItem(ts)
    ) {
        if (title != null) MText(title, 20.sp, m.sub, FontWeight.Normal, Modifier.padding(bottom = 8.dp))
        content()
    }
}

@Composable
private fun Choice(text: String, selected: Boolean, onClick: () -> Unit) {
    val m = LocalMetro.current
    Box(
        Modifier
            .tilt(maxAngle = 6f)
            .background(if (selected) m.accent else Color.Transparent)
            .border(2.dp, if (selected) m.accent else m.fg)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        MText(text, 18.sp, if (selected) Color.White else m.fg, FontWeight.Normal)
    }
}
