package com.archiekuo.localmusic

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

fun folderName(folder: String): String =
    folder.trim('/').substringAfterLast('/').ifBlank { "（沒有資料夾）" }

@Composable
private fun TagPill(text: String, strong: Boolean) {
    val p = LocalPal.current
    Box(
        Modifier
            .height(20.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (strong) p.selected else Color.Transparent)
            .border(1.dp, if (strong) p.accent.copy(alpha = 0.4f) else p.border, RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = if (strong) p.accent else p.muted, fontSize = 11.sp, maxLines = 1) }
}

/** 資料夾：整個資料夾（含子資料夾）歸進分類；點名稱看裡面的歌 */
@Composable
fun FolderScreen(vm: LibraryViewModel, conn: PlayerConnection, onClose: () -> Unit) {
    val p = LocalPal.current
    val ctx = LocalContext.current
    var open by remember { mutableStateOf<String?>(null) }
    var sel by remember { mutableStateOf(setOf<String>()) }
    var pick by remember { mutableStateOf<String?>(null) }

    BackHandler {
        if (open != null) open = null else onClose()
    }

    val folders = remember(vm.songs) {
        vm.songs.groupBy { it.folder }.entries.sortedBy { it.key.lowercase() }.map { it.key to it.value.size }
    }
    val total = folders.sumOf { it.second }
    val pickName = vm.cats.firstOrNull { it.id == pick }?.name

    Column(
        Modifier
            .fillMaxSize()
            .background(p.bg)
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        val o = open
        if (o != null) {
            FolderOpen(vm, conn, o) { open = null }
            return@Column
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.height(44.dp).clip(RoundedCornerShape(22.dp)).clickable(onClick = onClose).padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) { Text("返回", color = p.muted, fontSize = 15.sp) }
            Text(
                if (sel.isEmpty()) "資料夾" else "已選 ${sel.size} 個資料夾",
                style = SerifBold,
                color = p.text,
                fontSize = 18.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            Box(
                Modifier
                    .height(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .clickable { sel = if (sel.size == folders.size) emptySet() else folders.map { it.first }.toSet() }
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) { Text("全選", color = p.accent, fontSize = 15.sp) }
        }
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text("資料夾", color = p.text, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("${fmtCount(folders.size)} 個 · ${fmtCount(total)} 首", color = p.muted, fontSize = 13.sp, style = NumStyle)
        }
        Text(
            "勾選資料夾，再選分類；整個資料夾（含子資料夾）都歸進去，以後新增的歌也會自動歸入。點資料夾名稱可以看裡面的歌。",
            color = p.muted,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 6.dp),
        )

        if (folders.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("沒有資料夾", color = p.muted, fontSize = 14.sp)
            }
        } else {
            LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                items(folders, key = { it.first }) { (path, n) ->
                    val on = path in sel
                    val direct = (vm.folderCats[path] ?: emptySet()).mapNotNull { id -> vm.cats.firstOrNull { it.id == id } }
                    val inherited = vm.folderCats.entries
                        .filter { it.key != path && it.key.isNotEmpty() && path.startsWith(it.key) }
                        .flatMap { it.value }
                        .toSet()
                        .mapNotNull { id -> vm.cats.firstOrNull { it.id == id } }
                        .filter { c -> direct.none { it.id == c.id } }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(78.dp)
                            .background(if (on) p.selected else Color.Transparent)
                            .padding(start = 8.dp, end = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Box(
                            Modifier.size(44.dp).clickable { sel = if (on) sel - path else sel + path },
                            contentAlignment = Alignment.Center,
                        ) { CheckCircle(on) }
                        Row(
                            Modifier.weight(1f).fillMaxSize().clickable { open = path },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Box(
                                Modifier.size(44.dp).clip(RoundedCornerShape(11.dp)).background(p.surface2),
                                contentAlignment = Alignment.Center,
                            ) { Ico(IcFolder, p.amber, 22.dp) }
                            Column(Modifier.weight(1f)) {
                                Text(folderName(path), color = p.text, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(path.ifBlank { "（沒有資料夾）" }, color = p.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
                                Row(Modifier.padding(top = 5.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    if (direct.isEmpty() && inherited.isEmpty()) TagPill("沒有歸屬", false)
                                    direct.forEach { TagPill(it.name, true) }
                                    inherited.forEach { TagPill(it.name + "（上層）", false) }
                                }
                            }
                            Text(fmtCount(n), color = p.text, fontSize = 15.sp, style = NumStyle, textAlign = TextAlign.End, modifier = Modifier.width(44.dp))
                        }
                    }
                }
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .background(p.navBg)
                .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
        ) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("歸入", color = p.muted, fontSize = 13.sp)
                vm.cats.filter { !it.isSystem }.forEach { c ->
                    Chip(c.name, pick == c.id, { pick = if (pick == c.id) null else c.id })
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton(
                    if (pickName == null) "先選一個分類" else "歸入「$pickName」",
                    enabled = pick != null && sel.isNotEmpty(),
                    primary = true,
                    modifier = Modifier.weight(1.5f),
                ) {
                    val c = pick
                    if (c != null) {
                        vm.assignFolders(sel, c)
                        Toast.makeText(ctx, "已把 ${sel.size} 個資料夾歸入「$pickName」", Toast.LENGTH_SHORT).show()
                        sel = emptySet()
                    }
                }
                PillButton("取消歸屬", enabled = sel.isNotEmpty(), primary = false, modifier = Modifier.weight(1f)) {
                    vm.unassignFolders(sel, pick)
                    sel = emptySet()
                }
                PillButton("不是歌", enabled = sel.isNotEmpty(), primary = false, modifier = Modifier.weight(1f)) {
                    vm.hideFolders(sel)
                    Toast.makeText(ctx, "已隱藏 ${sel.size} 個資料夾（在 管理 → 已隱藏 可以恢復）", Toast.LENGTH_SHORT).show()
                    sel = emptySet()
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.FolderOpen(vm: LibraryViewModel, conn: PlayerConnection, folder: String, onBack: () -> Unit) {
    val p = LocalPal.current
    var adding by remember { mutableStateOf(false) }
    val list = vm.songs.filter { it.folder == folder }
    val direct = (vm.folderCats[folder] ?: emptySet()).mapNotNull { id -> vm.cats.firstOrNull { it.id == id } }

    Row(
        Modifier.fillMaxWidth().padding(start = 8.dp, end = 20.dp, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTap(IcBack, "返回", p.text, onBack, 24.dp)
        Column(Modifier.weight(1f)) {
            Text(folderName(folder), style = SerifBold, color = p.text, fontSize = 20.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(folder.ifBlank { "（沒有資料夾）" }, color = p.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
    Row(
        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("這個資料夾歸在", color = p.muted, fontSize = 13.sp)
        direct.forEach { c ->
            Row(
                Modifier
                    .height(32.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(p.selected)
                    .border(1.dp, p.accent.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                    .clickable { vm.unassignFolders(setOf(folder), c.id) }
                    .padding(start = 14.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(c.name, color = p.accent, fontSize = 13.sp)
                Text("✕", color = p.accent, fontSize = 12.sp)
            }
        }
        Chip(if (adding) "完成" else "分類", adding, { adding = !adding }, icon = if (adding) null else IcPlus)
    }
    if (adding) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            vm.cats.filter { !it.isSystem }.forEach { c ->
                val on = direct.any { it.id == c.id }
                Chip(c.name, on, { if (on) vm.unassignFolders(setOf(folder), c.id) else vm.assignFolders(setOf(folder), c.id) })
            }
        }
    }
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 6.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text("這個資料夾的歌", color = p.text, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Text("${fmtCount(list.size)} 首", color = p.muted, fontSize = 13.sp, style = NumStyle)
    }
    LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
        itemsIndexed(list, key = { _, s -> s.id }) { index, s ->
            SongRow(
                song = s,
                playing = conn.currentId == s.id,
                selecting = false,
                selected = false,
                onClick = { conn.playList(list, index) },
                onLongClick = { },
            )
        }
    }
}

/** 管理 → 已隱藏：被隱藏的資料夾和標成「不是歌」的檔案 */
@Composable
fun HiddenScreen(vm: LibraryViewModel, onClose: () -> Unit) {
    val p = LocalPal.current
    BackHandler { onClose() }
    var sel by remember { mutableStateOf(setOf<String>()) }

    val folders = vm.hiddenFolders.toList().sortedBy { it.lowercase() }
    val songs = vm.allSongs.filter { it.id in vm.hiddenSongs }

    Column(
        Modifier
            .fillMaxSize()
            .background(p.bg)
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconTap(IcBack, "返回", p.text, onClose, 24.dp)
            Text("已隱藏", style = SerifBold, color = p.text, fontSize = 20.sp, modifier = Modifier.weight(1f))
        }
        Text(
            "隱藏的資料夾和歌不會出現在曲庫、分類，也不會被分析。檔案沒有刪，隨時可以取消隱藏。",
            color = p.muted,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
        )
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            item(key = "h1") {
                Text("資料夾", color = p.muted, fontSize = 13.sp, modifier = Modifier.padding(start = 20.dp, top = 8.dp, bottom = 6.dp))
            }
            if (folders.isEmpty()) {
                item(key = "e1") { Text("沒有隱藏的資料夾", color = p.muted, fontSize = 13.sp, modifier = Modifier.padding(start = 20.dp, bottom = 8.dp)) }
            }
            items(folders, key = { "f:$it" }) { f ->
                val k = "f:$f"
                val n = vm.allSongs.count { inFolder(it.folder, f) }
                HiddenRow(k in sel, folderName(f), f.ifBlank { "（沒有資料夾）" }, n, true) { sel = if (k in sel) sel - k else sel + k }
            }
            item(key = "h2") {
                Text("單獨標成「不是歌」的檔案", color = p.muted, fontSize = 13.sp, modifier = Modifier.padding(start = 20.dp, top = 14.dp, bottom = 6.dp))
            }
            if (songs.isEmpty()) {
                item(key = "e2") { Text("沒有", color = p.muted, fontSize = 13.sp, modifier = Modifier.padding(start = 20.dp, bottom = 8.dp)) }
            }
            items(songs, key = { "s:${it.id}" }) { s ->
                val k = "s:${s.id}"
                HiddenRow(k in sel, s.title, "${fmtTime(s.durationMs)} · ${s.artistLabel}", 0, false) { sel = if (k in sel) sel - k else sel + k }
            }
        }
        Box(Modifier.fillMaxWidth().background(p.navBg).padding(horizontal = 16.dp, vertical = 10.dp)) {
            PillButton(
                "取消隱藏 ${sel.size} 項",
                enabled = sel.isNotEmpty(),
                primary = true,
                modifier = Modifier.fillMaxWidth(),
            ) {
                vm.unhideFolders(sel.filter { it.startsWith("f:") }.map { it.removePrefix("f:") }.toSet())
                vm.unhideSongs(sel.filter { it.startsWith("s:") }.mapNotNull { it.removePrefix("s:").toLongOrNull() }.toSet())
                sel = emptySet()
            }
        }
    }
}

@Composable
private fun HiddenRow(on: Boolean, title: String, sub: String, n: Int, folder: Boolean, onToggle: () -> Unit) {
    val p = LocalPal.current
    Row(
        Modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(if (on) p.selected else Color.Transparent)
            .clickable(onClick = onToggle)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CheckCircle(on)
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(11.dp)).background(p.surface2),
            contentAlignment = Alignment.Center,
        ) { Ico(if (folder) IcFolder else IcNote, if (folder) p.amber else p.muted, 22.dp) }
        Column(Modifier.weight(1f)) {
            Text(title, color = p.text, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sub, color = p.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
        }
        if (folder) Text(fmtCount(n), color = p.text, fontSize = 15.sp, style = NumStyle, textAlign = TextAlign.End, modifier = Modifier.width(44.dp))
    }
}
