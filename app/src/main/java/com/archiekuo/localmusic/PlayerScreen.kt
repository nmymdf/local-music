package com.archiekuo.localmusic

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player

@Composable
fun PlayerScreen(
    vm: LibraryViewModel,
    conn: PlayerConnection,
    version: String,
    onClose: () -> Unit,
) {
    val p = LocalPal.current
    val song = vm.songs.firstOrNull { it.id == conn.currentId }
    var adding by remember { mutableStateOf(false) }
    var drag by remember { mutableStateOf<Float?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .background(p.bg)
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconTap(IcChevDown, "收起", p.text, onClose, 26.dp)
            Text(
                "播放中",
                color = p.muted,
                fontSize = 13.sp,
                letterSpacing = 1.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            Box(Modifier.size(44.dp))
        }

        if (song == null) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("目前沒有在播放的歌", color = p.muted, fontSize = 14.sp)
            }
            CreditFooter(version)
            return@Column
        }

        val screenW = LocalConfiguration.current.screenWidthDp
        val art = minOf(290, screenW - 80).dp
        val memberOf = vm.cats.filter { c -> vm.byCat[c.id]?.any { it.id == song.id } == true }
        val plays = vm.playCount(song.id)
        val dur = conn.durationMs.coerceAtLeast(1L)
        val value = drag ?: (conn.positionMs.toFloat() / dur).coerceIn(0f, 1f)

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            Box(Modifier.fillMaxWidth().padding(top = 4.dp), contentAlignment = Alignment.Center) {
                CoverArt(song, art, 28.dp)
            }

            Column(Modifier.padding(start = 28.dp, end = 28.dp, top = 20.dp)) {
                Text(
                    song.title,
                    style = SerifBold,
                    color = p.text,
                    fontSize = 24.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(song.artistLabel, color = p.muted, fontSize = 15.sp, modifier = Modifier.padding(top = 4.dp))
                Row(
                    Modifier.padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Ico(IcStar, p.amber, 14.dp)
                    Text("已播放 $plays 次", color = p.amber, fontSize = 13.sp, style = NumStyle)
                }
            }

            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 28.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                memberOf.forEach { c ->
                    Box(
                        Modifier
                            .height(32.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(p.surface)
                            .border(1.dp, if (c.isSystem) p.amber.copy(alpha = 0.4f) else p.border, RoundedCornerShape(16.dp))
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(c.name, color = if (c.isSystem) p.amber else p.sub, fontSize = 13.sp)
                    }
                }
                Chip(if (adding) "完成" else "分類", adding, { adding = !adding }, icon = if (adding) null else IcPlus)
            }
            if (adding) {
                Row(
                    Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 28.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    vm.cats.filter { !it.isSystem }.forEach { c ->
                        val inCat = memberOf.any { it.id == c.id }
                        Chip(
                            c.name,
                            inCat,
                            {
                                if (inCat) vm.unassign(setOf(song.id), c.id) else vm.assign(setOf(song.id), c.id)
                            },
                        )
                    }
                }
            }

            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp)) {
                Slider(
                    value = value,
                    onValueChange = { drag = it },
                    onValueChangeFinished = {
                        val d = drag
                        if (d != null) conn.seekTo((d * dur).toLong())
                        drag = null
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = p.text,
                        activeTrackColor = p.accent,
                        inactiveTrackColor = p.border,
                    ),
                )
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                    Text(fmtTime((value * dur).toLong()), color = p.muted, fontSize = 12.sp, style = NumStyle, modifier = Modifier.weight(1f))
                    Text(fmtTime(conn.durationMs), color = p.muted, fontSize = 12.sp, style = NumStyle)
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconTap(IcShuffle, "隨機播放", if (conn.shuffle) p.accent else p.muted, { conn.toggleShuffle() })
                IconTap(IcPrev, "上一首", p.text, { conn.prev() }, 28.dp, 52.dp)
                Box(
                    Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(p.accent)
                        .clickable { conn.toggle() },
                    contentAlignment = Alignment.Center,
                ) {
                    Ico(if (conn.isPlaying) IcPause else IcPlay, p.onAccent, 32.dp)
                }
                IconTap(IcNext, "下一首", p.text, { conn.next() }, 28.dp, 52.dp)
                IconTap(
                    IcRepeat,
                    "重複播放",
                    if (conn.repeatMode != Player.REPEAT_MODE_OFF) p.accent else p.muted,
                    { conn.cycleRepeat() },
                )
            }
            if (conn.repeatMode == Player.REPEAT_MODE_ONE) {
                Text(
                    "單曲重複",
                    color = p.accent,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (conn.nextLabel.isNotBlank()) {
                Row(
                    Modifier
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .fillMaxWidth()
                        .height(60.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(p.surface)
                        .border(1.dp, p.border, RoundedCornerShape(16.dp))
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("接下來", color = p.muted, fontSize = 12.sp)
                        Text(
                            conn.nextLabel,
                            color = p.text,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 1.dp),
                        )
                    }
                    Ico(IcChevRight, p.muted, 20.dp)
                }
            }
        }

        CreditFooter(version)
    }
}
