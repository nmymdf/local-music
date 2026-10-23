package com.archiekuo.localmusic

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PermissionScreen(version: String, onAllow: () -> Unit) {
    val p = LocalPal.current
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                Modifier.size(76.dp).clip(CircleShape).background(p.selected).border(3.dp, p.accent, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Ico(IcNote, p.accent, 34.dp) }
            Text("手機音樂", style = SerifBold, color = p.text, fontSize = 28.sp, modifier = Modifier.padding(top = 18.dp))
            Text(
                "要先允許讀取手機裡的音樂，才能找到你的歌。\n只會讀音樂，不會看照片或其他檔案，也不需要網路。",
                color = p.muted,
                fontSize = 14.sp,
                lineHeight = 22.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            PillButton("允許讀取音樂", enabled = true, primary = true, modifier = Modifier.fillMaxWidth(), onClick = onAllow)
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .clip(RoundedCornerShape(23.dp))
                    .clickable { openAppSettings(ctx) },
                contentAlignment = Alignment.Center,
            ) { Text("已經拒絕過？到系統設定開啟", color = p.accent, fontSize = 14.sp) }
        }
    }
}

private fun openAppSettings(ctx: Context) {
    try {
        val i = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null))
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
    } catch (e: Exception) {
        // 打不開就算了
    }
}

@Composable
fun ScanningScreen(version: String) {
    val p = LocalPal.current
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            CircularProgressIndicator(color = p.accent, trackColor = p.border, modifier = Modifier.size(56.dp))
            Text("正在掃描手機裡的歌…", color = p.text, fontSize = 16.sp, modifier = Modifier.padding(top = 20.dp))
            Text("歌很多的話要等一下", color = p.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
fun ScanDoneScreen(
    vm: LibraryViewModel,
    version: String,
    onEnter: () -> Unit,
    onTidy: () -> Unit,
) {
    val p = LocalPal.current
    val total = vm.songs.size
    val rows = vm.cats.filter { !it.isSystem }.map { Triple(it.name, it.ruleText(), vm.byCat[it.id]?.size ?: 0) }
    val uncat = vm.unclassified.size
    val max = maxOf(1, rows.maxOfOrNull { it.third } ?: 0, uncat)

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier.padding(top = 36.dp).size(76.dp).clip(CircleShape).background(p.selected).border(3.dp, p.accent, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Ico(IcCheck, p.accent, 34.dp) }
            Text("掃描完成", style = SerifBold, color = p.text, fontSize = 26.sp, modifier = Modifier.padding(top = 18.dp))
            Text("在你的手機裡找到", color = p.muted, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
            Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(fmtCount(total), style = SerifBold, color = p.text, fontSize = 48.sp)
                Text("首歌曲", color = p.sub, fontSize = 16.sp, modifier = Modifier.padding(bottom = 8.dp))
            }

            Text(
                "自動分類結果",
                color = p.text,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 6.dp),
            )
            rows.forEach { (name, source, n) ->
                ResultRow(name, source, n, max, false)
            }
            ResultRow("未分類", "等你自己整理", uncat, max, true)
        }
        Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            PillButton("進入曲庫", enabled = true, primary = true, modifier = Modifier.fillMaxWidth().height(52.dp), onClick = onEnter)
            if (uncat > 0) {
                Box(
                    Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(23.dp)).clickable(onClick = onTidy),
                    contentAlignment = Alignment.Center,
                ) { Text("先整理未分類的 ${fmtCount(uncat)} 首", color = p.accent, fontSize = 14.sp) }
            }
        }
    }
}

@Composable
private fun ResultRow(name: String, source: String, n: Int, max: Int, muted: Boolean) {
    val p = LocalPal.current
    Row(
        Modifier.fillMaxWidth().height(58.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(Modifier.width(110.dp)) {
            Text(name, color = if (muted) p.sub else p.text, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(source, color = p.muted, fontSize = 11.sp, maxLines = 1, modifier = Modifier.padding(top = 2.dp))
        }
        Box(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(p.border)) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth((n.toFloat() / max).coerceIn(0f, 1f))
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (muted) p.muted else p.accent),
            )
        }
        Text(fmtCount(n), color = p.text, fontSize = 15.sp, style = NumStyle, textAlign = TextAlign.End, modifier = Modifier.width(48.dp))
    }
}
