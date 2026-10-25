package com.archiekuo.localmusic

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

@Immutable
class Pal(
    val bg: Color,
    val surface: Color,
    val surface2: Color,
    val border: Color,
    val text: Color,
    val sub: Color,
    val muted: Color,
    val accent: Color,
    val onAccent: Color,
    val selected: Color,
    val amber: Color,
    val gold: Color,
    val red: Color,
    val navBg: Color,
)

val DarkPal = Pal(
    bg = Color(0xFF101418),
    surface = Color(0xFF181E24),
    surface2 = Color(0xFF1F2830),
    border = Color(0xFF26303A),
    text = Color(0xFFECE8DF),
    sub = Color(0xFFCFCBC2),
    muted = Color(0xFF8E99A4),
    accent = Color(0xFF5BC8A8),
    onAccent = Color(0xFF0B1512),
    selected = Color(0xFF15262A),
    amber = Color(0xFFE0A458),
    gold = Color(0xFFD4AF37),
    red = Color(0xFFF0707F),
    navBg = Color(0xFF121820),
)

val LightPal = Pal(
    bg = Color(0xFFF6F4EF),
    surface = Color(0xFFFFFFFF),
    surface2 = Color(0xFFEFEBE3),
    border = Color(0xFFE0DBD0),
    text = Color(0xFF1B1F23),
    sub = Color(0xFF454B52),
    muted = Color(0xFF6A737C),
    accent = Color(0xFF1F8F76),
    onAccent = Color(0xFFFFFFFF),
    selected = Color(0xFFE3F2EE),
    amber = Color(0xFFB5701A),
    gold = Color(0xFFA8820F),
    red = Color(0xFFC2394B),
    navBg = Color(0xFFFBFAF7),
)

val LocalPal = staticCompositionLocalOf { DarkPal }

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val pal = if (dark) DarkPal else LightPal
    CompositionLocalProvider(LocalPal provides pal) {
        MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
            content()
        }
    }
}

// ---- 圖示：用和示意圖一樣的線條路徑畫，不需要額外的圖示套件 ----

private fun circ(cx: Float, cy: Float, r: Float): String =
    "M${cx - r} ${cy}a$r $r 0 1 0 ${2 * r} 0a$r $r 0 1 0 ${-2 * r} 0"

private fun vec(name: String, vararg d: String, filled: Boolean = false): ImageVector {
    val b = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    )
    for (p in d) {
        val nodes = PathParser().parsePathString(p).toNodes()
        if (filled) {
            b.addPath(pathData = nodes, fill = SolidColor(Color.Black))
        } else {
            b.addPath(
                pathData = nodes,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
    }
    return b.build()
}

val IcNote = vec("note", "M9 18V6l10-2v12", circ(6.5f, 18f, 2.5f), circ(16.5f, 16f, 2.5f))
val IcSearch = vec("search", circ(11f, 11f, 6.5f), "M16 16l4.5 4.5")
val IcPlay = vec("play", "M8 5.5v13l11-6.5z", filled = true)
val IcPause = vec("pause", "M7 5h3.5v14H7z", "M13.5 5H17v14h-3.5z", filled = true)
val IcNext = vec("next", "M6 6l9 6-9 6z", "M17 6h2v12h-2z", filled = true)
val IcPrev = vec("prev", "M18 6l-9 6 9 6z", "M5 6h2v12H5z", filled = true)
val IcShuffle = vec("shuffle", "M16 3h5v5", "M4 20L21 3", "M21 16v5h-5", "M15 15l6 6", "M4 4l5 5")
val IcRepeat = vec("repeat", "M17 1l4 4-4 4", "M3 11V9a4 4 0 014-4h14", "M7 23l-4-4 4-4", "M21 13v2a4 4 0 01-4 4H3")
val IcGrid = vec("grid", "M4 4h7v7H4z", "M13 4h7v7h-7z", "M4 13h7v7H4z", "M13 13h7v7h-7z")
val IcSliders = vec("sliders", "M4 7h10", "M18 7h2", "M4 17h2", "M10 17h10", circ(16f, 7f, 2f), circ(8f, 17f, 2f))
val IcCheck = vec("check", "M5 12.5l4.5 4.5L19 7.5")
val IcChevDown = vec("chevDown", "M6 9l6 6 6-6")
val IcChevRight = vec("chevRight", "M9 6l6 6-6 6")
val IcBack = vec("back", "M15 5l-7 7 7 7")
val IcPlus = vec("plus", "M12 5v14", "M5 12h14")
val IcTrash = vec("trash", "M5 7h14", "M10 7V5h4v2", "M7 7l1 12h8l1-12")
val IcStar = vec("star", "M12 3.5l2.6 5.4 5.9.8-4.3 4.1 1 5.8L12 16.8l-5.2 2.8 1-5.8-4.3-4.1 5.9-.8z", filled = true)
val IcStarLine = vec("starLine", "M12 3.5l2.6 5.4 5.9.8-4.3 4.1 1 5.8L12 16.8l-5.2 2.8 1-5.8-4.3-4.1 5.9-.8z")
val IcHeart = vec("heart", "M12 20s-7-4.5-7-10a4 4 0 017-2.5A4 4 0 0119 10c0 5.5-7 10-7 10z")
val IcMoon = vec("moon", "M20 14.5A8 8 0 019.5 4 8 8 0 1020 14.5z")
val IcBolt = vec("bolt", "M13 3L5 13.5h6L10 21l8-10.5h-6z")
val IcTarget = vec("target", circ(12f, 12f, 8f), circ(12f, 12f, 3.5f))
val IcTag = vec("tag", "M4 12V5h7l9 9-7 7z", circ(8.5f, 9f, 1f))
val IcFolder = vec("folder", "M3 7a2 2 0 012-2h4l2 2h8a2 2 0 012 2v8a2 2 0 01-2 2H5a2 2 0 01-2-2z")
val IcBars = vec("bars", "M3 10h4v11H3z", "M10 3h4v18h-4z", "M17 7h4v14h-4z", filled = true)
