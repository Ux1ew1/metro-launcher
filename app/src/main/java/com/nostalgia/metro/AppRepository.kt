package com.nostalgia.metro

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.text.Collator

private const val ICON_PX = 128

/** Список установленных приложений (включая рабочий профиль) и их запуск. */
class AppRepository(private val context: Context) {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val userManager = context.getSystemService(UserManager::class.java)

    var apps by mutableStateOf<List<AppEntry>>(emptyList())
        private set

    var onAppsChanged: ((List<AppEntry>) -> Unit)? = null

    private var scope: CoroutineScope? = null
    private var refreshJob: Job? = null

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String?, user: UserHandle?) = refresh()
        override fun onPackageAdded(packageName: String?, user: UserHandle?) = refresh()
        override fun onPackageChanged(packageName: String?, user: UserHandle?) = refresh()
        override fun onPackagesAvailable(packageNames: Array<out String>?, user: UserHandle?, replacing: Boolean) = refresh()
        override fun onPackagesUnavailable(packageNames: Array<out String>?, user: UserHandle?, replacing: Boolean) = refresh()
    }

    fun start(scope: CoroutineScope) {
        this.scope = scope
        launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
        refresh()
    }

    fun refresh() {
        val s = scope ?: return
        refreshJob?.cancel()
        refreshJob = s.launch {
            val list = withContext(Dispatchers.IO) { loadAll() }
            apps = list
            onAppsChanged?.invoke(list)
        }
    }

    suspend fun awaitLoaded(timeoutMs: Long) {
        withTimeoutOrNull(timeoutMs) { snapshotFlow { apps.isNotEmpty() }.first { it } }
    }

    private fun loadAll(): List<AppEntry> {
        val me = Process.myUserHandle()
        val dpi = context.resources.displayMetrics.densityDpi
        val out = ArrayList<AppEntry>()
        for (user in userManager.userProfiles) {
            val serial = userManager.getSerialNumberForUser(user)
            val infos = try {
                launcherApps.getActivityList(null, user)
            } catch (e: Exception) {
                emptyList()
            }
            for (info in infos) {
                val cn = info.componentName
                if (cn.packageName == context.packageName) continue
                val raw: Drawable = try {
                    info.getIcon(dpi)
                } catch (e: Exception) {
                    null
                } ?: context.packageManager.defaultActivityIcon
                val shown = if (user != me) context.packageManager.getUserBadgedIcon(raw, user) else raw
                out += AppEntry(
                    key = "${cn.flattenToShortString()}|$serial",
                    label = info.label?.toString() ?: cn.packageName,
                    component = cn,
                    user = user,
                    icon = render(shown),
                    mono = renderMono(raw),
                )
            }
        }
        val collator = Collator.getInstance()
        return out.sortedWith { a, b -> collator.compare(a.label, b.label) }
    }

    private fun render(d: Drawable): ImageBitmap =
        d.toBitmap(ICON_PX, ICON_PX, Bitmap.Config.ARGB_8888).asImageBitmap()

    /** Белый силуэт из монохромного слоя адаптивной иконки — как глифы на плитках WP. */
    private fun renderMono(d: Drawable): ImageBitmap? {
        if (Build.VERSION.SDK_INT < 33) return null
        val adaptive = d as? AdaptiveIconDrawable ?: return null
        val mono = adaptive.monochrome ?: return null
        val bmp = Bitmap.createBitmap(ICON_PX, ICON_PX, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        // слои адаптивной иконки на 25% больше видимой области с каждой стороны
        val inset = ICON_PX / 4
        val dr = mono.mutate()
        dr.setBounds(-inset, -inset, ICON_PX + inset, ICON_PX + inset)
        dr.setTint(android.graphics.Color.WHITE)
        dr.draw(canvas)
        return bmp.asImageBitmap()
    }

    fun launch(app: AppEntry, opts: Bundle?): Boolean = try {
        launcherApps.startMainActivity(app.component, app.user, null, opts)
        true
    } catch (e: Exception) {
        false
    }

    fun openInfo(app: AppEntry) {
        try {
            launcherApps.startAppDetailsActivity(app.component, app.user, null, null)
        } catch (_: Exception) {
        }
    }

    fun uninstall(ctx: Context, app: AppEntry) {
        val intent = Intent(Intent.ACTION_DELETE, Uri.fromParts("package", app.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            ctx.startActivity(intent)
        } catch (_: Exception) {
        }
    }
}
