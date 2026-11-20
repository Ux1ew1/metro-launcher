package com.nostalgia.metro

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun LauncherRoot(ctl: LauncherController) {
    val s = ctl.settings
    val metro = remember(s) { Metro.from(s) }
    val view = LocalView.current
    val activity = LocalContext.current as? Activity
    SideEffect {
        activity?.window?.let { w ->
            WindowCompat.getInsetsController(w, view).run {
                isAppearanceLightStatusBars = !s.dark && !s.wallpaper
                isAppearanceLightNavigationBars = !s.dark && !s.wallpaper
            }
        }
    }

    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(60_000L - value % 60_000L + 50L)
        }
    }

    val pager = rememberPagerState(pageCount = { 2 })
    val startScroll = rememberScrollState()
    val scope = rememberCoroutineScope()

    // «Назад» на начальном экране ничего не делает, как и положено лаунчеру
    BackHandler {
        when {
            ctl.editMode -> ctl.exitEdit()
            pager.currentPage != 0 -> scope.launch { pager.animateScrollToPage(0) }
        }
    }

    // кнопка «Домой»: вернуться на начальный экран
    LaunchedEffect(ctl.homeEvents) {
        if (ctl.homeEvents == 0) return@LaunchedEffect
        if (ctl.homeAnimated) {
            if (pager.currentPage != 0) pager.animateScrollToPage(0) else startScroll.animateScrollTo(0)
        } else {
            pager.scrollToPage(0)
        }
    }

    val scheme = if (s.dark) darkColorScheme(primary = metro.accent) else lightColorScheme(primary = metro.accent)
    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(LocalMetro provides metro, LocalNow provides now) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(if (s.wallpaper) Color.Transparent else metro.bg)
                    .onSizeChanged {
                        for (t in listOf(ctl.turnstile, ctl.settingsTurnstile)) {
                            t.screenWidth = it.width.toFloat()
                            t.screenHeight = it.height.toFloat()
                        }
                    }
            ) {
                HorizontalPager(
                    state = pager,
                    modifier = Modifier.fillMaxSize(),
                    beyondViewportPageCount = 1,
                    userScrollEnabled = !ctl.editMode,
                ) { page ->
                    if (page == 0) {
                        StartScreen(ctl, startScroll) { scope.launch { pager.animateScrollToPage(1) } }
                    } else {
                        AppListScreen(ctl) { app ->
                            ctl.pin(app)
                            scope.launch {
                                pager.animateScrollToPage(0)
                                startScroll.animateScrollTo(startScroll.maxValue)
                            }
                        }
                    }
                }
                if (ctl.showSettings) SettingsScreen(ctl)
            }
        }
    }
}
