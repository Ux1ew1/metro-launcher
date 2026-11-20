package com.nostalgia.metro

import android.content.ComponentName
import android.os.UserHandle
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntOffset
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Размер плитки в ячейках сетки (ширина x высота). */
enum class TileSize(val w: Int, val h: Int) {
    SMALL(1, 1),
    MEDIUM(2, 2),
    WIDE(4, 2),
    LARGE(4, 4),
}

enum class TileKind(val sizes: List<TileSize>) {
    APP(listOf(TileSize.SMALL, TileSize.MEDIUM, TileSize.WIDE)),
    CLOCK(listOf(TileSize.MEDIUM, TileSize.WIDE, TileSize.LARGE)),
    CALENDAR(listOf(TileSize.SMALL, TileSize.MEDIUM, TileSize.WIDE)),
}

data class TileSpec(
    val id: String,
    val kind: TileKind,
    val size: TileSize,
    val appKey: String? = null,
) {
    fun resized(): TileSpec {
        val s = kind.sizes
        return copy(size = s[(s.indexOf(size) + 1) % s.size])
    }

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("kind", kind.name)
        .put("size", size.name)
        .apply { if (appKey != null) put("app", appKey) }

    companion object {
        fun newId(): String = UUID.randomUUID().toString()

        private fun fromJson(o: JSONObject): TileSpec? = runCatching {
            TileSpec(
                id = o.getString("id"),
                kind = TileKind.valueOf(o.getString("kind")),
                size = TileSize.valueOf(o.getString("size")),
                appKey = if (o.has("app")) o.getString("app") else null,
            )
        }.getOrNull()

        fun listToJson(list: List<TileSpec>): String =
            JSONArray().apply { list.forEach { put(it.toJson()) } }.toString()

        fun listFromJson(s: String): List<TileSpec> = runCatching {
            val a = JSONArray(s)
            (0 until a.length()).mapNotNull { fromJson(a.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }
}

data class AppEntry(
    val key: String,
    val label: String,
    val component: ComponentName,
    val user: UserHandle,
    val icon: ImageBitmap,
    /** Монохромный слой адаптивной иконки (Android 13+), если приложение его предоставляет. */
    val mono: ImageBitmap?,
) {
    val packageName: String get() = component.packageName
}

data class LauncherSettings(
    val accent: Int = 0xFF0050EF.toInt(),
    val dark: Boolean = true,
    val moreTiles: Boolean = false,
    val monoIcons: Boolean = true,
    val wallpaper: Boolean = false,
)

/** Результат раскладки: колонка/строка каждой плитки и общее число строк. */
class Packing(val cells: Map<String, IntOffset>, val rows: Int)

/** Укладывает плитки по порядку в первую подходящую свободную позицию (как на начальном экране WP). */
fun packTiles(tiles: List<TileSpec>, cols: Int): Packing {
    val grid = ArrayList<BooleanArray>()
    fun line(r: Int): BooleanArray {
        while (grid.size <= r) grid.add(BooleanArray(cols))
        return grid[r]
    }

    fun fits(r: Int, c: Int, w: Int, h: Int): Boolean {
        for (y in r until r + h) {
            val l = line(y)
            for (x in c until c + w) if (l[x]) return false
        }
        return true
    }

    val out = HashMap<String, IntOffset>()
    var rows = 0
    for (t in tiles) {
        val w = minOf(t.size.w, cols)
        val h = t.size.h
        var r = 0
        search@ while (true) {
            for (c in 0..cols - w) {
                if (fits(r, c, w, h)) {
                    for (y in r until r + h) {
                        val l = line(y)
                        for (x in c until c + w) l[x] = true
                    }
                    out[t.id] = IntOffset(c, r)
                    rows = maxOf(rows, r + h)
                    break@search
                }
            }
            r++
        }
    }
    return Packing(out, rows)
}
