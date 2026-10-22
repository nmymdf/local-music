package com.archiekuo.localmusic

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private class CatStyle(val color: Color, val icon: ImageVector?, val glyph: String?)

private val ExtraColors = listOf(
    Color(0xFF6FA8E6), Color(0xFFE58FA3), Color(0xFFA99BE0), Color(0xFFE0A458), Color(0xFF5BC8A8), Color(0xFFF0875A),
)

private fun styleFor(c: Category, index: Int): CatStyle = when (c.id) {
    "zh" -> CatStyle(Color(0xFF5BC8A8), null, "中")
    "en" -> CatStyle(Color(0xFF6FA8E6), null, "A")
    "lyric" -> CatStyle(Color(0xFFE58FA3), IcHeart, null)
    "quiet" -> CatStyle(Color(0xFFA99BE0), IcMoon, null)
    "buddhist" -> CatStyle(Color(0xFFE0A458), IcTarget, null)
    "run" -> CatStyle(Color(0xFFF0875A), IcBolt, null)
    else -> CatStyle(ExtraColors[index % ExtraColors.size], IcTag, null)
}

@Composable
private fun BadgeCircle(style: CatStyle, size: Int) {
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(style.color.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        if (style.glyph != null) {
            Text(style.glyph, color = style.color, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        } else if (style.icon != null) {
            Ico(style.icon, style.color, 20.dp)
        }
    }
}

@Composable
private fun CountText(n: Int) {
    val p = LocalPal.current
    Row(verticalAlignment = Alignment.Bottom) {
        Text(fmtCount(n), color = p.text, fontSize = 18.sp, fontWeight = FontWeight.Medium, style = NumStyle)
        Text(" 首", color = p.muted, fontSize = 13.sp)
    }
}

@Composable
private fun CatCard(name: String, count: Int, style: CatStyle, nameColor: Color, onClick: () -> Unit) {
    val p = LocalPal.current
    Column(
        Modifier
            .height(92.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(p.surface)
            .border(1.dp, p.border, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        BadgeCircle(style, 36)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Text(name, color = nameColor, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.weight(1f))
            CountText(count)
        }
    }
}

@Composable
fun CategoriesScreen(
    vm: LibraryViewModel,
    onOpen: (String) -> Unit,
    onManage: () -> Unit,
    onTidy: () -> Unit,
) {
    val p = LocalPal.current
    val uncat = vm.unclassified.size
    val freq = vm.cats.firstOrNull { it.isSystem }
    val normal = vm.cats.filter { !it.isSystem }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("分類", style = SerifBold, color = p.text, fontSize = 28.sp, modifier = Modifier.weight(1f))
            Box(
                Modifier.height(44.dp).clip(RoundedCornerShape(22.dp)).clickable(onClick = onManage).padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) { Text("管理", color = p.accent, fontSize = 15.sp) }
        }

        if (uncat > 0) {
            Row(
                Modifier
                    .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(p.selected)
                    .border(1.dp, p.accent.copy(alpha = 0.25f), RoundedCornerShape(18.dp))
                    .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text("還有 ${fmtCount(uncat)} 首沒分類", color = p.text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text("自動分類判斷不出來的歌，勾選後一次歸類", color = p.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp))
                }
                Box(
                    Modifier.height(44.dp).clip(RoundedCornerShape(22.dp)).background(p.accent).clickable(onClick = onTidy).padding(horizontal = 20.dp),
                    contentAlignment = Alignment.Center,
                ) { Text("整理", color = p.onAccent, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f),
        ) {
            if (freq != null) {
                item(key = "freq", span = { GridItemSpan(maxLineSpan) }) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(92.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(p.surface)
                            .border(1.dp, p.amber.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
                            .clickable { onOpen(freq.id) }
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Box(
                            Modifier.size(44.dp).clip(CircleShape).background(p.amber.copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center,
                        ) { Ico(IcStar, p.amber, 22.dp) }
                        Column(Modifier.weight(1f)) {
                            Text(freq.name + "的歌", color = p.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text("依播放次數，自動記住你最常聽的", color = p.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp))
                        }
                        CountText(vm.byCat[freq.id]?.size ?: 0)
                    }
                }
            }
            items(normal, key = { it.id }) { c ->
                CatCard(c.name, vm.byCat[c.id]?.size ?: 0, styleFor(c, normal.indexOf(c)), p.text) { onOpen(c.id) }
            }
            item(key = "uncat") {
                CatCard("未分類", uncat, CatStyle(p.muted, IcTag, null), p.sub) { onOpen("uncat") }
            }
            item(key = "add") {
                val dash = p.muted.copy(alpha = 0.5f)
                Column(
                    Modifier
                        .height(92.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .clickable(onClick = onManage)
                        .drawBehind {
                            drawRoundRect(
                                color = dash,
                                cornerRadius = CornerRadius(20.dp.toPx()),
                                style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))),
                            )
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Ico(IcPlus, p.muted, 24.dp)
                    Text("新增分類", color = p.muted, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
    }
}
