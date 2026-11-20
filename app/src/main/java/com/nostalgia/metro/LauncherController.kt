package com.nostalgia.metro

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Process
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.AndroidUiDispatcher
import androidx.core.content.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Состояние лаунчера. Живёт в Application, поэтому переживает пересоздание Activity. */
class LauncherController(private val app: Context) {
    // AndroidUiDispatcher.Main содержит frame clock — нужен для анимаций вне композиции
    val scope = CoroutineScope(SupervisorJob() + AndroidUiDispatcher.Main)
    private val prefs = app.getSharedPreferences("metro", Context.MODE_PRIVATE)
    val repo = AppRepository(app)

    var settings by mutableStateOf(readSettings())
        private set

    val tiles = mutableStateListOf<TileSpec>()
    private var tilesReady = false

    val turnstile = TurnstileState()
    val settingsTurnstile = TurnstileState()

    var showSettings by mutableStateOf(false)
        private set
    var editMode by mutableStateOf(false)
        private set
    var selectedTile by mutableStateOf<String?>(null)
    var homeEvents by mutableIntStateOf(0)
        private set
    var homeAnimated = false
        private set

    var resumed = false
    var pendingIn = false
    private var launching = false
    private var navJob: Job? = null

    init {
        prefs.getString("tiles", null)?.let {
            tiles.addAll(TileSpec.listFromJson(it))
            tilesReady = true
        }
        repo.onAppsChanged = ::onAppsChanged
        repo.start(scope)
    }

    // ---------- настройки ----------

    private fun readSettings(): LauncherSettings {
        val d = LauncherSettings()
        return LauncherSettings(
            accent = prefs.getInt("accent", d.accent),
            dark = prefs.getBoolean("dark", d.dark),
            moreTiles = prefs.getBoolean("moreTiles", d.moreTiles),
            monoIcons = prefs.getBoolean("monoIcons", d.monoIcons),
            wallpaper = prefs.getBoolean("wallpaper", d.wallpaper),
        )
    }

    fun updateSettings(change: (LauncherSettings) -> LauncherSettings) {
        val s = change(settings)
        settings = s
        prefs.edit {
            putInt("accent", s.accent)
            putBoolean("dark", s.dark)
            putBoolean("moreTiles", s.moreTiles)
            putBoolean("monoIcons", s.monoIcons)
            putBoolean("wallpaper", s.wallpaper)
        }
    }

    // ---------- плитки ----------

    private fun onAppsChanged(apps: List<AppEntry>) {
        if (apps.isEmpty()) return
        if (!tilesReady) {
            tiles.addAll(buildDefaultTiles(apps))
            tilesReady = true
            saveTiles()
        } else {
            val keys = apps.mapTo(HashSet()) { it.key }
            if (tiles.removeAll { it.kind == TileKind.APP && (it.appKey == null || it.appKey !in keys) }) saveTiles()
        }
    }

    fun saveTiles() {
        prefs.edit { putString("tiles", TileSpec.listToJson(tiles.toList())) }
    }

    fun enterEdit(id: String) {
        editMode = true
        selectedTile = id
    }

    fun exitEdit() {
        editMode = false
        selectedTile = null
        saveTiles()
    }

    fun moveTile(id: String, targetId: String) {
        val from = tiles.indexOfFirst { it.id == id }
        val to = tiles.indexOfFirst { it.id == targetId }
        if (from < 0 || to < 0 || from == to) return
        val t = tiles.removeAt(from)
        tiles.add(to, t)
    }

    fun moveTileToEnd(id: String) {
        val from = tiles.indexOfFirst { it.id == id }
        if (from < 0 || from == tiles.lastIndex) return
        tiles.add(tiles.removeAt(from))
    }

    fun resize(id: String) {
        val i = tiles.indexOfFirst { it.id == id }
        if (i >= 0) tiles[i] = tiles[i].resized()
        saveTiles()
    }

    fun unpin(id: String) {
        tiles.removeAll { it.id == id }
        selectedTile = null
        if (tiles.isEmpty()) editMode = false
        saveTiles()
    }

    /** @return false, если такая плитка уже есть */
    fun pin(entry: AppEntry): Boolean {
        if (tiles.any { it.appKey == entry.key }) return false
        tiles.add(TileSpec(TileSpec.newId(), TileKind.APP, TileSize.MEDIUM, entry.key))
        saveTiles()
        return true
    }

    fun resetTiles() {
        tiles.clear()
        tiles.addAll(buildDefaultTiles(repo.apps))
        saveTiles()
    }

    // ---------- запуск ----------

    private fun options(ctx: Context) =
        ActivityOptions.makeCustomAnimation(ctx, R.anim.app_open, R.anim.stay).toBundle()

    fun openTile(ctx: Context, spec: TileSpec, entry: AppEntry?) {
        when (spec.kind) {
            TileKind.APP -> if (entry != null) launchApp(ctx, entry, spec.id)
            TileKind.CLOCK -> launchIntent(ctx, Intent(AlarmClock.ACTION_SHOW_ALARMS), spec.id)
            TileKind.CALENDAR -> launchIntent(
                ctx,
                Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR),
                spec.id,
            )
        }
    }

    fun launchApp(ctx: Context, entry: AppEntry, key: Any) =
        launchWithTurnstile(ctx, key) { repo.launch(entry, options(ctx)) }

    private fun launchIntent(ctx: Context, intent: Intent, key: Any) = launchWithTurnstile(ctx, key) {
        try {
            ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), options(ctx))
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Сначала плитки «улетают» турникетом, потом открывается приложение. */
    private fun launchWithTurnstile(ctx: Context, key: Any, start: () -> Boolean) {
        if (launching) return
        launching = true
        scope.launch {
            try {
                turnstile.run(TurnMode.ForwardOut, key)
                if (start()) {
                    pendingIn = true
                    scope.launch {
                        // если приложение так и не перекрыло лаунчер — вернуть плитки
                        delay(2000)
                        if (resumed && pendingIn) {
                            pendingIn = false
                            turnstile.run(TurnMode.BackwardIn)
                        }
                    }
                } else {
                    Toast.makeText(ctx, "Не удалось открыть", Toast.LENGTH_SHORT).show()
                    turnstile.run(TurnMode.BackwardIn)
                }
            } finally {
                launching = false
            }
        }
    }

    // ---------- навигация ----------

    fun openSettings() {
        if (showSettings || navJob?.isActive == true) return
        navJob = scope.launch {
            turnstile.run(TurnMode.ForwardOut)
            showSettings = true
            settingsTurnstile.run(TurnMode.ForwardIn)
        }
    }

    fun closeSettings() {
        if (!showSettings || navJob?.isActive == true) return
        navJob = scope.launch {
            settingsTurnstile.run(TurnMode.BackwardOut)
            showSettings = false
            settingsTurnstile.show()
            turnstile.run(TurnMode.BackwardIn)
        }
    }

    // ---------- жизненный цикл ----------

    fun onResumed() {
        resumed = true
        if (pendingIn) {
            pendingIn = false
            scope.launch {
                repo.awaitLoaded(1200)
                turnstile.run(TurnMode.BackwardIn)
            }
        }
    }

    fun onPaused() {
        resumed = false
    }

    fun onStopped() {
        if (editMode) exitEdit()
        navJob?.cancel()
        showSettings = false
        pendingIn = true
        scope.launch {
            settingsTurnstile.show()
            turnstile.hide()
        }
    }

    fun onHome() {
        if (editMode) exitEdit()
        if (showSettings && resumed) closeSettings()
        homeAnimated = resumed
        homeEvents++
    }

    // ---------- плитки по умолчанию ----------

    private fun buildDefaultTiles(apps: List<AppEntry>): List<TileSpec> {
        val pm = app.packageManager
        val me = Process.myUserHandle()
        val mine = apps.filter { it.user == me }

        fun find(intent: Intent): AppEntry? {
            val pkg = try {
                pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
                    ?.activityInfo?.packageName?.takeIf { it != "android" }
                    ?: pm.queryIntentActivities(intent, 0).firstOrNull()?.activityInfo?.packageName
            } catch (e: Exception) {
                null
            }
            return pkg?.let { p -> mine.firstOrNull { it.packageName == p } }
        }

        fun selector(category: String) = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, category)

        val used = HashSet<String>()
        val out = ArrayList<TileSpec>()
        fun add(entry: AppEntry?, size: TileSize) {
            if (entry != null && used.add(entry.key)) {
                out += TileSpec(TileSpec.newId(), TileKind.APP, size, entry.key)
            }
        }

        add(find(Intent(Intent.ACTION_DIAL)), TileSize.MEDIUM)
        add(find(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:"))), TileSize.MEDIUM)
        out += TileSpec(TileSpec.newId(), TileKind.CLOCK, TileSize.WIDE)
        add(find(Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))), TileSize.SMALL)
        add(find(Intent(MediaStore.ACTION_IMAGE_CAPTURE)), TileSize.SMALL)
        add(find(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))), TileSize.SMALL)
        add(find(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0"))), TileSize.SMALL)
        out += TileSpec(TileSpec.newId(), TileKind.CALENDAR, TileSize.MEDIUM)
        add(find(selector(Intent.CATEGORY_APP_GALLERY)), TileSize.MEDIUM)
        add(find(Intent(Settings.ACTION_SETTINGS)), TileSize.SMALL)
        add(find(selector(Intent.CATEGORY_APP_MARKET)), TileSize.SMALL)
        add(find(selector(Intent.CATEGORY_APP_MUSIC)), TileSize.SMALL)
        add(find(selector(Intent.CATEGORY_APP_CONTACTS)), TileSize.SMALL)
        mine.filter { it.key !in used }.take(4).forEach { add(it, TileSize.MEDIUM) }
        return out
    }
}
