package com.archiekuo.localmusic

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PillButton(
    text: String,
    enabled: Boolean,
    primary: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val p = LocalPal.current
    val bg = if (primary) (if (enabled) p.accent else p.accent.copy(alpha = 0.3f)) else Color.Transparent
    val fg = if (primary) p.onAccent else if (enabled) p.text else p.muted
    Box(
        modifier
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(bg)
            .border(1.dp, if (primary) bg else p.border, RoundedCornerShape(24.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = fg, fontSize = 15.sp, fontWeight = if (primary) FontWeight.Bold else FontWeight.Normal, maxLines = 1)
    }
}

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit) {
    val p = LocalPal.current
    Row(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(p.surface)
            .border(1.dp, p.border, RoundedCornerShape(22.dp))
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Ico(IcSearch, p.muted, 20.dp)
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) Text("搜尋歌名、歌手", color = p.muted, fontSize = 15.sp)
            BasicTextField(
                value = query,
                onValueChange = onChange,
                singleLine = true,
                textStyle = TextStyle(color = p.text, fontSize = 15.sp),
                cursorBrush = SolidColor(p.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
fun HomeScreen(
    vm: LibraryViewModel,
    conn: PlayerConnection,
    filter: String,
    onFilter: (String) -> Unit,
    selecting: Boolean,
    onSelecting: (Boolean) -> Unit,
    onDelete: (List<Song>) -> Unit,
    onFolders: () -> Unit,
) {
    val p = LocalPal.current
    val ctx = LocalContext.current
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(setOf<Long>()) }
    var pickCat by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(selecting) {
        if (!selecting) {
            selected = emptySet()
            confirmDelete = false
        }
    }

    val base: List<Song> = when (filter) {
        "all" -> vm.songs
        "uncat" -> vm.unclassified
        else -> vm.byCat[filter] ?: emptyList()
    }
    val list = remember(base, query) {
        if (query.isBlank()) base
        else base.filter { it.title.contains(query, ignoreCase = true) || it.artist.contains(query, ignoreCase = true) }
    }
    val title = when (filter) {
        "all" -> "全部歌曲"
        "uncat" -> "未分類"
        else -> vm.cats.firstOrNull { it.id == filter }?.name ?: "全部歌曲"
    }
    val pickName = vm.cats.firstOrNull { it.id == pickCat }?.name

    Column(Modifier.fillMaxSize()) {
        if (selecting) {
            Row(
                Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.height(44.dp).clip(RoundedCornerShape(22.dp)).clickable { onSelecting(false) }.padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) { Text("取消", color = p.muted, fontSize = 15.sp) }
                Text(
                    "已選 ${selected.size} 首",
                    style = SerifBold,
                    color = p.text,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    Modifier.height(44.dp).clip(RoundedCornerShape(22.dp)).clickable {
                        val all = list.map { it.id }.toSet()
                        selected = if (selected.containsAll(all)) emptySet() else all
                    }.padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) { Text("全選", color = p.accent, fontSize = 15.sp) }
            }
        } else {
            Text(
                "手機音樂",
                style = SerifBold,
                color = p.text,
                fontSize = 28.sp,
                modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 8.dp),
            )
            SearchField(query) { query = it }
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Chip("全部", filter == "all", { onFilter("all") })
                vm.cats.filter { it.isSystem }.forEach { c ->
                    Chip(c.name, filter == c.id, { onFilter(c.id) }, tint = p.amber, icon = IcStar)
                }
                Chip("資料夾", false, onFolders, icon = IcFolder)
                vm.cats.filter { !it.isSystem }.forEach { c ->
                    Chip(c.name, filter == c.id, { onFilter(c.id) })
                }
                Chip("未分類", filter == "uncat", { onFilter("uncat") })
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = if (selecting) 10.dp else 2.dp, bottom = 6.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(title, color = p.text, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("${fmtCount(list.size)} 首", color = p.muted, fontSize = 13.sp, style = NumStyle)
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (list.isEmpty()) {
                val c = vm.cats.firstOrNull { it.id == filter }
                val msg = when {
                    query.isNotBlank() -> "找不到符合的歌"
                    c != null && c.isSystem -> "播放次數達到 ${c.minPlays} 次的歌，會自動出現在這裡"
                    filter == "uncat" -> "每一首都已經分類了"
                    vm.songs.isEmpty() -> "手機裡還沒有找到音樂"
                    else -> "這個分類裡還沒有歌"
                }
                Text(
                    msg,
                    color = p.muted,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                )
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    itemsIndexed(list, key = { _, s -> s.id }) { index, s ->
                        SongRow(
                            song = s,
                            playing = conn.currentId == s.id,
                            selecting = selecting,
                            selected = s.id in selected,
                            onClick = {
                                if (selecting) {
                                    selected = if (s.id in selected) selected - s.id else selected + s.id
                                } else {
                                    conn.playList(list, index)
                                }
                            },
                            onLongClick = {
                                if (!selecting) {
                                    onSelecting(true)
                                    selected = setOf(s.id)
                                } else {
                                    selected = if (s.id in selected) selected - s.id else selected + s.id
                                }
                            },
                        )
                    }
                }
            }
        }

        if (selecting) {
            Column(Modifier.fillMaxWidth().background(p.navBg)) {
                if (confirmDelete) {
                    ConfirmBar(
                        message = "刪除 ${selected.size} 首？會從手機刪掉檔案，無法復原",
                        okText = "確定刪除",
                        onCancel = { confirmDelete = false },
                        onOk = {
                            val songs = vm.songs.filter { it.id in selected }
                            confirmDelete = false
                            onDelete(songs)
                            onSelecting(false)
                        },
                    )
                }
                Row(
                    Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("加入到", color = p.muted, fontSize = 13.sp)
                    vm.cats.filter { !it.isSystem }.forEach { c ->
                        Chip(c.name, pickCat == c.id, { pickCat = c.id })
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PillButton(
                        text = if (pickName != null) "加入「$pickName」" else "先選一個分類",
                        enabled = pickCat != null && selected.isNotEmpty(),
                        primary = true,
                        modifier = Modifier.weight(1.3f),
                    ) {
                        val id = pickCat
                        if (id != null) {
                            vm.assign(selected, id)
                            Toast.makeText(ctx, "已加入 ${selected.size} 首到「$pickName」", Toast.LENGTH_SHORT).show()
                            onSelecting(false)
                        }
                    }
                    PillButton(
                        text = "移出分類",
                        enabled = pickCat != null && selected.isNotEmpty(),
                        primary = false,
                        modifier = Modifier.weight(1f),
                    ) {
                        val id = pickCat
                        if (id != null) {
                            vm.unassign(selected, id)
                            Toast.makeText(ctx, "已從「$pickName」移出 ${selected.size} 首", Toast.LENGTH_SHORT).show()
                            onSelecting(false)
                        }
                    }
                    PillButton(
                        text = "不是歌",
                        enabled = selected.isNotEmpty(),
                        primary = false,
                        modifier = Modifier.weight(1f),
                    ) {
                        vm.hideSongs(selected)
                        Toast.makeText(ctx, "已隱藏 ${selected.size} 首（在 管理 → 已隱藏 可以恢復）", Toast.LENGTH_SHORT).show()
                        onSelecting(false)
                    }
                    Box(
                        Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .border(1.dp, if (selected.isNotEmpty()) p.red.copy(alpha = 0.6f) else p.border, CircleShape)
                            .clickable(enabled = selected.isNotEmpty()) { confirmDelete = true },
                        contentAlignment = Alignment.Center,
                    ) {
                        Ico(IcTrash, if (selected.isNotEmpty()) p.red else p.muted, 20.dp)
                    }
                }
            }
        }
    }
}
