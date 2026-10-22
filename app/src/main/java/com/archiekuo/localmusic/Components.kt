@file:OptIn(ExperimentalFoundationApi::class)

package com.archiekuo.localmusic

import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache
import android.util.Size
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

val NumStyle = TextStyle(fontFeatureSettings = "tnum")
val SerifBold = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold)

fun fmtTime(ms: Long): String {
    val s = (ms / 1000).toInt().coerceAtLeast(0)
    return String.format(Locale.US, "%d:%02d", s / 60, s % 60)
}

fun fmtCount(n: Int): String = String.format(Locale.US, "%,d", n)

// ---------------- 封面 ----------------

object CoverCache {
    private val cache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
    private val misses = HashSet<String>()

    fun get(ctx: Context, song: Song, px: Int): Bitmap? {
        val key = "${song.id}_$px"
        cache.get(key)?.let { return it }
        synchronized(misses) { if (key in misses) return null }
        return try {
            val b = ctx.contentResolver.loadThumbnail(song.uri, Size(px, px), null)
            cache.put(key, b)
            b
        } catch (e: Exception) {
            synchronized(misses) { misses.add(key) }
            null
        }
    }
}

private val Grads = listOf(
    listOf(Color(0xFF2F6F6A), Color(0xFF173C3A)),
    listOf(Color(0xFF3C6EA8), Color(0xFF16294A)),
    listOf(Color(0xFFB07A3C), Color(0xFF4A2E15)),
    listOf(Color(0xFF8E3F52), Color(0xFF3A1520)),
    listOf(Color(0xFF6B4F8E), Color(0xFF2B1F40)),
    listOf(Color(0xFF4F7A3A), Color(0xFF1C3016)),
)

@Composable
fun CoverArt(song: Song?, size: Dp, radius: Dp) {
    val ctx = LocalContext.current
    val density = LocalDensity.current
    val px = with(density) { size.roundToPx() }.coerceAtLeast(64)
    val bmp by produceState<ImageBitmap?>(null, song?.id, px) {
        value = if (song == null) {
            null
        } else {
            withContext(Dispatchers.IO) { CoverCache.get(ctx, song, px)?.asImageBitmap() }
        }
    }
    val idx = Math.floorMod(song?.id ?: 0L, Grads.size.toLong()).toInt()
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(radius))
            .background(Brush.linearGradient(Grads[idx])),
        contentAlignment = Alignment.Center,
    ) {
        val b = bmp
        if (b != null) {
            Image(
                bitmap = b,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                imageVector = IcNote,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(size / 2.2f),
            )
        }
    }
}

// ---------------- 小零件 ----------------

@Composable
fun Ico(v: ImageVector, tint: Color, size: Dp = 22.dp) {
    Icon(imageVector = v, contentDescription = null, tint = tint, modifier = Modifier.size(size))
}

@Composable
fun IconTap(
    v: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit,
    size: Dp = 22.dp,
    box: Dp = 44.dp,
) {
    Box(
        Modifier
            .size(box)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = v, contentDescription = label, tint = tint, modifier = Modifier.size(size))
    }
}

@Composable
fun Chip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    tint: Color? = null,
    icon: ImageVector? = null,
) {
    val p = LocalPal.current
    val bg = when {
        selected -> tint ?: p.accent
        else -> p.surface
    }
    val fg = when {
        selected -> p.onAccent
        tint != null -> tint
        else -> p.sub
    }
    val bd = when {
        selected -> bg
        tint != null -> tint.copy(alpha = 0.35f)
        else -> p.border
    }
    Row(
        Modifier
            .height(44.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(bg)
            .border(1.dp, bd, RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) Ico(icon, fg, 16.dp)
        Text(
            text,
            color = fg,
            fontSize = 15.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
        )
    }
}

@Composable
fun CheckCircle(checked: Boolean) {
    val p = LocalPal.current
    Box(
        Modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(if (checked) p.accent else Color.Transparent)
            .border(2.dp, if (checked) p.accent else p.muted.copy(alpha = 0.6f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) Ico(IcCheck, p.onAccent, 15.dp)
    }
}

@Composable
fun CheckBox(checked: Boolean, enabled: Boolean = true, onToggle: () -> Unit) {
    val p = LocalPal.current
    Box(
        Modifier
            .size(44.dp)
            .clickable(enabled = enabled, onClick = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(20.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(if (checked) p.accent else Color.Transparent)
                .border(
                    2.dp,
                    if (!enabled) p.border else if (checked) p.accent else p.muted.copy(alpha = 0.6f),
                    RoundedCornerShape(6.dp)
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) Ico(IcCheck, p.onAccent, 14.dp)
        }
    }
}

@Composable
fun CreditFooter(version: String) {
    val p = LocalPal.current
    Box(Modifier.fillMaxWidth().height(24.dp), contentAlignment = Alignment.Center) {
        Text(
            "作者：ArchieKUO　v$version",
            color = p.gold,
            fontSize = 11.sp,
            fontStyle = FontStyle.Italic,
            letterSpacing = 0.4.sp,
            textAlign = TextAlign.Center,
        )
    }
}

/** 列上的小確認列：不跳大視窗 */
@Composable
fun ConfirmBar(message: String, okText: String, onCancel: () -> Unit, onOk: () -> Unit) {
    val p = LocalPal.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(p.surface2)
            .border(1.dp, p.red.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
            .padding(start = 14.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(message, color = p.text, fontSize = 13.sp, modifier = Modifier.weight(1f).padding(vertical = 8.dp))
        Box(
            Modifier.height(44.dp).clip(RoundedCornerShape(22.dp)).clickable(onClick = onCancel).padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) { Text("取消", color = p.sub, fontSize = 14.sp) }
        Box(
            Modifier.height(44.dp).clip(RoundedCornerShape(22.dp)).clickable(onClick = onOk).padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) { Text(okText, color = p.red, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
    }
}

// ---------------- 歌曲列 ----------------

@Composable
fun SongRow(
    song: Song,
    playing: Boolean,
    selecting: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val p = LocalPal.current
    Row(
        Modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(if (selected || (playing && !selecting)) p.selected else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (selecting) CheckCircle(selected)
        CoverArt(song, if (selecting) 44.dp else 48.dp, 12.dp)
        Column(Modifier.weight(1f)) {
            Text(
                song.title,
                color = if (playing) p.accent else p.text,
                fontSize = 15.sp,
                fontWeight = if (playing) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                song.artistLabel,
                color = p.muted,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        if (playing && !selecting) Ico(IcBars, p.accent, 16.dp)
        Text(
            fmtTime(song.durationMs),
            color = p.muted,
            fontSize = 12.sp,
            style = NumStyle,
            textAlign = TextAlign.End,
            modifier = Modifier.width(40.dp),
        )
    }
}

// ---------------- 迷你播放器、底部分頁 ----------------

@Composable
fun MiniPlayer(conn: PlayerConnection, song: Song, onOpen: () -> Unit) {
    val p = LocalPal.current
    val frac = if (conn.durationMs > 0) (conn.positionMs.toFloat() / conn.durationMs).coerceIn(0f, 1f) else 0f
    Box(
        Modifier
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .fillMaxWidth()
            .height(60.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(p.surface2)
            .border(1.dp, p.border, RoundedCornerShape(16.dp)),
    ) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .clickable(onClick = onOpen)
                    .padding(start = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CoverArt(song, 40.dp, 10.dp)
                Column {
                    Text(song.title, color = p.text, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(song.artistLabel, color = p.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            IconTap(if (conn.isPlaying) IcPause else IcPlay, if (conn.isPlaying) "暫停" else "播放", p.text, { conn.toggle() }, 24.dp)
            IconTap(IcNext, "下一首", p.text, { conn.next() }, 22.dp)
            Spacer(Modifier.width(4.dp))
        }
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(frac)
                .height(2.dp)
                .background(p.accent),
        )
    }
}

@Composable
fun BottomNav(tab: Int, onTab: (Int) -> Unit) {
    val p = LocalPal.current
    Row(
        Modifier
            .fillMaxWidth()
            .background(p.navBg)
            .height(60.dp),
    ) {
        NavItem("歌曲", IcNote, tab == 0, Modifier.weight(1f)) { onTab(0) }
        NavItem("分類", IcGrid, tab == 1, Modifier.weight(1f)) { onTab(1) }
        NavItem("管理", IcSliders, tab == 2, Modifier.weight(1f)) { onTab(2) }
    }
}

@Composable
private fun NavItem(label: String, icon: ImageVector, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val p = LocalPal.current
    val c = if (active) p.accent else p.muted
    Column(
        modifier
            .fillMaxSize()
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Ico(icon, c, 22.dp)
        Text(label, color = c, fontSize = 12.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.padding(top = 3.dp))
    }
}
