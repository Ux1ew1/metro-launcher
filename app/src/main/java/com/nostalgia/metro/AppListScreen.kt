package com.nostalgia.metro

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private sealed interface ListRow {
    val key: String
}

private class HeaderRow(val letter: String) : ListRow {
    override val key = "h:$letter"
}

private class AppRow(val app: AppEntry) : ListRow {
    override val key = "a:${app.key}"
}

private val LATIN = ('a'..'z').map { it.toString() }
private val CYRILLIC = "абвгдежзийклмнопрстуфхцчшщэюя".map { it.toString() }

private fun groupOf(label: String): String {
    val c = label.trim().firstOrNull()?.lowercaseChar() ?: return "#"
    val g = if (c == 'ё') "е" else c.toString()
    return if (g in LATIN || g in CYRILLIC) g else "#"
}

private fun groupOrder(g: String): Int = when (g) {
    "#" -> 0
    in LATIN -> 1 + LATIN.indexOf(g)
    in CYRILLIC -> 100 + CYRILLIC.indexOf(g)
    else -> 999
}

private fun buildRows(apps: List<AppEntry>, query: String): List<ListRow> {
    val q = query.trim()
    if (q.isNotEmpty()) return apps.filter { it.label.contains(q, ignoreCase = true) }.map { AppRow(it) }
    val sorted = apps.sortedBy { groupOrder(groupOf(it.label)) } // сортировка стабильная — порядок Collator сохраняется
    val out = ArrayList<ListRow>()
    var last: String? = null
    for (a in sorted) {
        val g = groupOf(a.label)
        if (g != last) {
            out += HeaderRow(g)
            last = g
        }
        out += AppRow(a)
    }
    return out
}

@Composable
fun AppListScreen(ctl: LauncherController, onPin: (AppEntry) -> Unit) {
    val m = LocalMetro.current
    val apps = ctl.repo.apps
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var jumpOpen by remember { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val rows = remember(apps, query) { buildRows(apps, query) }
    val present = remember(rows) { rows.mapNotNullTo(HashSet()) { (it as? HeaderRow)?.letter } }
    // при открытом контекстном меню список слегка «отъезжает» назад, как в WP
    val listScale by animateFloatAsState(if (menuFor != null) 0.95f else 1f, label = "menuScale")
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    BackHandler(enabled = jumpOpen || searching || menuFor != null) {
        when {
            menuFor != null -> menuFor = null
            jumpOpen -> jumpOpen = false
            else -> {
                searching = false
                query = ""
            }
        }
    }

    LaunchedEffect(ctl.homeEvents) {
        jumpOpen = false
        searching = false
        query = ""
        menuFor = null
        listState.scrollToItem(0)
    }

    Box(Modifier.fillMaxSize().background(m.listBg)) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .graphicsLayer {
                    scaleX = listScale
                    scaleY = listScale
                },
            contentPadding = PaddingValues(top = top + 12.dp, bottom = bottom + 24.dp),
        ) {
            item(key = "top") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                        .turnstileItem(ctl.turnstile),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (searching) {
                        SearchBox(query, { query = it }, Modifier.weight(1f))
                        Spacer(Modifier.width(10.dp))
                        CircleButton(Icons.Filled.Close, "закрыть поиск", {
                            searching = false
                            query = ""
                        })
                    } else {
                        CircleButton(Icons.Filled.Search, "поиск", { searching = true })
                        Spacer(Modifier.weight(1f))
                        CircleButton(Icons.Filled.Settings, "настройки", { ctl.openSettings() })
                    }
                }
            }
            items(rows, key = { it.key }, contentType = { if (it is HeaderRow) 0 else 1 }) { row ->
                when (row) {
                    is HeaderRow -> LetterHeader(row.letter, Modifier.turnstileItem(ctl.turnstile)) {
                        jumpOpen = true
                    }
                    is AppRow -> AppRowItem(
                        ctl = ctl,
                        row = row,
                        menuOpen = menuFor == row.app.key,
                        onMenu = { menuFor = it },
                        onPin = onPin,
                    )
                }
            }
            if (rows.isEmpty() && apps.isNotEmpty()) {
                item(key = "empty") {
                    MText("ничего не найдено", 18.sp, m.sub, modifier = Modifier.padding(20.dp))
                }
            }
        }

        if (jumpOpen) {
            val letters = listOf("#") + LATIN + (if (present.any { it in CYRILLIC }) CYRILLIC else emptyList())
            JumpList(letters, present, onDismiss = { jumpOpen = false }) { g ->
                jumpOpen = false
                val idx = rows.indexOfFirst { it is HeaderRow && it.letter == g }
                if (idx >= 0) scope.launch { listState.scrollToItem(idx + 1) } // +1 за верхнюю строку
            }
        }
    }
}

@Composable
private fun SearchBox(query: String, onChange: (String) -> Unit, modifier: Modifier) {
    val m = LocalMetro.current
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    BasicTextField(
        value = query,
        onValueChange = onChange,
        singleLine = true,
        textStyle = TextStyle(color = Color.Black, fontSize = 19.sp, fontFamily = FontFamily.SansSerif),
        cursorBrush = SolidColor(m.accent),
        modifier = modifier
            .background(Color.White)
            .border(2.dp, m.accent)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .focusRequester(focus),
        decorationBox = { inner ->
            Box {
                if (query.isEmpty()) MText("поиск", 19.sp, Color.Gray)
                inner()
            }
        },
    )
}

@Composable
private fun LetterHeader(letter: String, modifier: Modifier, onClick: () -> Unit) {
    val m = LocalMetro.current
    Box(modifier.padding(start = 20.dp, top = 12.dp, bottom = 4.dp)) {
        Box(
            Modifier
                .size(50.dp)
                .tilt()
                .border(2.dp, m.accent)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                )
        ) {
            MText(letter, 28.sp, m.fg, modifier = Modifier.align(Alignment.BottomStart).padding(start = 6.dp))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppRowItem(
    ctl: LauncherController,
    row: AppRow,
    menuOpen: Boolean,
    onMenu: (String?) -> Unit,
    onPin: (AppEntry) -> Unit,
) {
    val m = LocalMetro.current
    val ctx = LocalContext.current
    val app = row.app
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .turnstileItem(ctl.turnstile, row.key)
                .tilt(maxAngle = 6f)
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onLongClick = { onMenu(app.key) },
                    onClick = { ctl.launchApp(ctx, app, row.key) },
                )
                .padding(horizontal = 20.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val mono = if (m.monoIcons) app.mono else null
            Box(Modifier.size(50.dp).background(m.tile), contentAlignment = Alignment.Center) {
                Image(mono ?: app.icon, app.label, Modifier.size(if (mono != null) 44.dp else 38.dp))
            }
            Spacer(Modifier.width(14.dp))
            MText(app.label, 20.sp, m.fg, FontWeight.Normal, maxLines = 1)
        }
        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { onMenu(null) },
            offset = DpOffset(20.dp, 0.dp),
            shape = RectangleShape,
            containerColor = m.menuBg,
        ) {
            MenuEntry("закрепить на начальном экране") {
                onMenu(null)
                onPin(app)
            }
            MenuEntry("о приложении") {
                onMenu(null)
                ctl.repo.openInfo(app)
            }
            MenuEntry("удалить") {
                onMenu(null)
                ctl.repo.uninstall(ctx, app)
            }
        }
    }
}

@Composable
private fun MenuEntry(text: String, onClick: () -> Unit) {
    val m = LocalMetro.current
    MText(
        text, 19.sp, m.menuFg, FontWeight.Normal,
        Modifier
            .widthIn(min = 260.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
    )
}

/** Сетка букв для быстрого перехода (jump list) с анимацией «наезда». */
@Composable
private fun JumpList(
    letters: List<String>,
    present: Set<String>,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    val m = LocalMetro.current
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, tween(280, easing = LinearOutSlowInEasing)) }
    val cols = if (letters.size > 30) 5 else 4
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = appear.value }
            .background(m.bg.copy(alpha = 0.94f))
            .pointerInput(Unit) { detectTapGestures { onDismiss() } }
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(cols),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = top + 16.dp, bottom = bottom + 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.graphicsLayer {
                val s = 1.3f - 0.3f * appear.value
                scaleX = s
                scaleY = s
            },
        ) {
            items(letters) { l ->
                val on = l in present
                Box(
                    Modifier
                        .aspectRatio(1f)
                        .then(if (on) Modifier.tilt() else Modifier)
                        .background(if (on) m.accent else m.disabled)
                        .then(
                            if (on) {
                                Modifier.clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) { onPick(l) }
                            } else {
                                Modifier
                            }
                        )
                ) {
                    MText(
                        l, 30.sp, if (on) Color.White else m.sub,
                        modifier = Modifier.align(Alignment.BottomStart).padding(start = 8.dp, bottom = 2.dp),
                    )
                }
            }
        }
    }
}
