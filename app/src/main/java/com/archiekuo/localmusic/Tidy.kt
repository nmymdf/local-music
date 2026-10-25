package com.archiekuo.localmusic

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.sqrt

/** 一組要一起歸類的歌（同一位歌手、同一個資料夾，或 App 的一條建議） */
data class TidyGroup(
    val key: String,
    val title: String,
    val sub: String,
    val songs: List<Song>,
    val targetId: String? = null,
    val hide: Boolean = false,
)

private enum class TidyTab(val label: String) {
    SUGGEST("建議"),
    ARTIST("歌手"),
    FOLDER("資料夾"),
}

private fun folderTitle(folder: String): String =
    folder.trim('/').substringAfterLast('/').ifBlank { "（沒有資料夾）" }

private fun groupUp(
    un: List<Song>,
    keyOf: (Song) -> String,
    titleOf: (String) -> String,
    subOf: (String, Int) -> String,
    otherLabel: String,
    prefix: String,
): List<TidyGroup> {
    val m = un.groupBy(keyOf)
    val big = m.entries.filter { it.value.size >= 3 }.sortedByDescending { it.value.size }
    val small = m.values.filter { it.size < 3 }.flatten()
    val out = ArrayList<TidyGroup>()
    for (e in big) out.add(TidyGroup(prefix + e.key, titleOf(e.key), subOf(e.key, e.value.size), e.value))
    if (small.isNotEmpty()) out.add(TidyGroup(prefix + "~other", otherLabel, "${small.size} 首未分類", small))
    return out
}

/**
 * 替未分類的歌猜分類：
 * 1. 同資料夾、同歌手：那裡已分類的歌，六成以上（至少 3 首）在同一類 → 建議把其餘的也放進去
 * 2. 聽起來像：節拍和音量跟某一類已分類的歌相近
 */
fun buildSuggestions(vm: LibraryViewModel): List<TidyGroup> {
    val un = vm.unclassified
    if (un.isEmpty()) return emptyList()
    val unIds = HashSet<Long>()
    for (s in un) unIds.add(s.id)
    val cands = vm.cats.filter {
        it.rule != RuleType.FREQUENT && it.rule != RuleType.LANG_ZH && it.rule != RuleType.LANG_EN
    }
    val member = HashMap<Long, MutableList<String>>()
    for (c in cands) {
        vm.byCat[c.id]?.forEach { member.getOrPut(it.id) { mutableListOf() }.add(c.id) }
    }
    val labeled = vm.songs.filter { it.id !in unIds && member.containsKey(it.id) }
    val claimed = HashSet<Long>()
    val out = ArrayList<TidyGroup>()

    fun byKey(prefix: String, label: String, keyOf: (Song) -> String, nameOf: (String) -> String, skip: (String) -> Boolean) {
        val labByKey = labeled.groupBy(keyOf)
        val unByKey = un.groupBy(keyOf)
        for ((k, ls) in labByKey) {
            if (skip(k)) continue
            val mine = (unByKey[k] ?: continue).filter { it.id !in claimed }
            if (mine.isEmpty()) continue
            val counts = HashMap<String, Int>()
            for (s in ls) for (cid in member[s.id] ?: emptyList<String>()) counts[cid] = (counts[cid] ?: 0) + 1
            val best = counts.entries.maxByOrNull { it.value } ?: continue
            if (best.value < 3 || best.value < ls.size * 0.6) continue
            val cat = cands.firstOrNull { it.id == best.key } ?: continue
            for (s in mine) claimed.add(s.id)
            out.add(TidyGroup(prefix + k, "$label「${nameOf(k)}」", "你標過 ${best.value} 首都在「${cat.name}」", mine, cat.id))
        }
    }
    byKey("sf:", "同資料夾", { it.folder }, { folderTitle(it) }, { it.isBlank() })
    byKey("sa:", "同歌手", { it.artist }, { it }, { it.isBlank() })

    // 可能不是歌：太短、檔名像語音或錄音
    val shorts = un.filter { it.id !in claimed && it.durationMs < 45_000 }
    if (shorts.isNotEmpty()) {
        for (s in shorts) claimed.add(s.id)
        out.add(TidyGroup("sx:short", "可能不是歌：太短", "短於 45 秒，像語音或提示音", shorts, null, true))
    }
    val voiceRe = Regex("^(voice|ptt|aud[-_]|audio[-_]|rec[-_ ]|record|錄音|录音|語音|语音|通話|通话)", RegexOption.IGNORE_CASE)
    val voices = un.filter { it.id !in claimed && voiceRe.containsMatchIn(it.title.trim()) }
    if (voices.isNotEmpty()) {
        for (s in voices) claimed.add(s.id)
        out.add(TidyGroup("sx:name", "可能不是歌：檔名像語音", "VOICE、PTT、錄音 開頭的檔名", voices, null, true))
    }

    // 聽起來像：節拍與音量
    fun feat(s: Song): Pair<Double, Double>? {
        val b = vm.bpm[s.id] ?: 0
        val l = vm.loud[s.id] ?: LOUD_NONE
        return if (b > 0 && l != LOUD_NONE) Pair(b.toDouble(), l / 10.0) else null
    }
    class Model(val cat: Category, val mb: Double, val sb: Double, val ml: Double, val sl: Double)
    val models = ArrayList<Model>()
    for (c in cands) {
        val fs = (vm.byCat[c.id] ?: emptyList()).filter { it.id !in unIds }.mapNotNull { feat(it) }
        if (fs.size < 8) continue
        val mb = fs.sumOf { it.first } / fs.size
        val ml = fs.sumOf { it.second } / fs.size
        val sb = sqrt(fs.sumOf { (it.first - mb) * (it.first - mb) } / fs.size).coerceAtLeast(8.0)
        val sl = sqrt(fs.sumOf { (it.second - ml) * (it.second - ml) } / fs.size).coerceAtLeast(3.0)
        models.add(Model(c, mb, sb, ml, sl))
    }
    if (models.isNotEmpty()) {
        val buckets = HashMap<String, ArrayList<Song>>()
        for (s in un) {
            if (s.id in claimed) continue
            val f = feat(s) ?: continue
            var bestM: Model? = null
            var bestD = 1.3
            for (m in models) {
                val db = (f.first - m.mb) / m.sb
                val dl = (f.second - m.ml) / m.sl
                val d = sqrt(db * db + dl * dl)
                if (d <= bestD) {
                    bestD = d
                    bestM = m
                }
            }
            if (bestM != null) buckets.getOrPut(bestM.cat.id) { ArrayList() }.add(s)
        }
        for (m in models) {
            val list = buckets[m.cat.id] ?: continue
            out.add(TidyGroup("ss:" + m.cat.id, "聽起來像「${m.cat.name}」", "節拍、音量和你標過的相近", list, m.cat.id))
        }
    }
    return out
}

@Composable
fun TidyScreen(vm: LibraryViewModel, onClose: () -> Unit) {
    val p = LocalPal.current
    BackHandler { onClose() }

    var tab by remember { mutableStateOf(TidyTab.SUGGEST) }
    var sel by remember { mutableStateOf(setOf<String>()) }
    var pick by remember { mutableStateOf<String?>(null) }
    var dismissed by remember { mutableStateOf(setOf<String>()) }

    val un = vm.unclassified
    val suggestions = remember(un, vm.bpm, vm.loud, vm.cats) { buildSuggestions(vm) }
    val artist = remember(un) {
        groupUp(un, { it.artistLabel }, { it }, { _, n -> "$n 首未分類" }, "其他（每位 2 首以下）", "a:")
    }
    val folder = remember(un) {
        groupUp(
            un,
            { it.folder.ifBlank { "（沒有資料夾）" } },
            { folderTitle(it) },
            { k, n -> "$n 首未分類 · $k" },
            "其他（每個資料夾 2 首以下）",
            "f:",
        )
    }
    val groups = when (tab) {
        TidyTab.SUGGEST -> suggestions.filter { it.key !in dismissed }
        TidyTab.ARTIST -> artist
        TidyTab.FOLDER -> folder
    }
    val chosen = groups.filter { it.key in sel }
    val chosenSongs = chosen.sumOf { it.songs.size }
    val pickName = vm.cats.firstOrNull { it.id == pick }?.name

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
            Box(
                Modifier.height(44.dp).clip(RoundedCornerShape(22.dp)).clickable(onClick = onClose).padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) { Text("返回", color = p.muted, fontSize = 15.sp) }
            Text(
                "整理未分類",
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
                    .clickable { sel = if (chosen.size == groups.size) emptySet() else groups.map { it.key }.toSet() }
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) { Text(if (groups.isNotEmpty() && chosen.size == groups.size) "取消全選" else "全選", color = p.accent, fontSize = 15.sp) }
        }
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text("未分類", color = p.text, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("${fmtCount(un.size)} 首", color = p.muted, fontSize = 13.sp, style = NumStyle)
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TidyTab.values().forEach { t ->
                val on = t == tab
                Box(
                    Modifier
                        .weight(1f)
                        .height(40.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (on) p.accent else p.surface)
                        .border(1.dp, if (on) p.accent else p.border, RoundedCornerShape(20.dp))
                        .clickable {
                            tab = t
                            sel = emptySet()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(t.label, color = if (on) p.onAccent else p.sub, fontSize = 14.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
        Text(
            when (tab) {
                TidyTab.SUGGEST -> "App 依資料夾、歌手、節拍和音量猜的，只是建議。勾選才會歸類。"
                TidyTab.ARTIST -> "勾選歌手，再選分類；那位歌手的未分類歌會一次歸進去。"
                TidyTab.FOLDER -> "勾選資料夾，再選分類；那個資料夾的未分類歌會一次歸進去。"
            },
            color = p.muted,
            fontSize = 12.sp,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 6.dp),
        )

        if (groups.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    when {
                        un.isEmpty() -> "沒有要整理的歌了"
                        tab == TidyTab.SUGGEST -> "目前沒有建議。\n多標幾首歌、等節拍和音量分析完，這裡會比較準。"
                        else -> "沒有可以整理的項目"
                    },
                    color = p.muted,
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp),
                )
            }
        } else {
            LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                items(groups, key = { it.key }) { g ->
                    val on = g.key in sel
                    val target = if (g.hide) "不是歌" else vm.cats.firstOrNull { it.id == g.targetId }?.name
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(if (tab == TidyTab.SUGGEST) 72.dp else 64.dp)
                            .background(if (on) p.selected else androidx.compose.ui.graphics.Color.Transparent)
                            .clickable { sel = if (on) sel - g.key else sel + g.key }
                            .padding(horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CheckCircle(on)
                        Column(Modifier.weight(1f)) {
                            Text(g.title, color = p.text, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(g.sub, color = p.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
                        }
                        if (target != null) Text("→ $target", color = p.accent, fontSize = 14.sp, maxLines = 1)
                        Text(
                            fmtCount(g.songs.size),
                            color = p.text,
                            fontSize = 15.sp,
                            style = NumStyle,
                            textAlign = TextAlign.End,
                            modifier = Modifier.width(44.dp),
                        )
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
            if (tab == TidyTab.SUGGEST) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillButton(
                        "加入建議的分類 · ${chosen.size} 組 · ${fmtCount(chosenSongs)} 首",
                        enabled = chosen.isNotEmpty(),
                        primary = true,
                        modifier = Modifier.weight(1.6f),
                    ) {
                        for (g in chosen) {
                            val t = g.targetId
                            if (g.hide) vm.hideSongs(g.songs.map { it.id }.toSet())
                            else if (t != null) vm.assign(g.songs.map { it.id }.toSet(), t)
                        }
                        sel = emptySet()
                    }
                    PillButton("略過勾選的", enabled = chosen.isNotEmpty(), primary = false, modifier = Modifier.weight(1f)) {
                        dismissed = dismissed + chosen.map { it.key }
                        sel = emptySet()
                    }
                }
                Text(
                    "歸錯了，可以到該分類的歌曲裡「移出分類」",
                    color = p.muted,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            } else {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("加入到", color = p.muted, fontSize = 13.sp)
                    vm.cats.filter { !it.isSystem }.forEach { c ->
                        Chip(c.name, pick == c.id, { pick = if (pick == c.id) null else c.id })
                    }
                }
                PillButton(
                    if (pickName == null) "先選一個分類" else "加入「$pickName」· ${chosen.size} 組 · ${fmtCount(chosenSongs)} 首",
                    enabled = pick != null && chosen.isNotEmpty(),
                    primary = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                ) {
                    val t = pick
                    if (t != null) {
                        vm.assign(chosen.flatMap { g -> g.songs.map { it.id } }.toSet(), t)
                        sel = emptySet()
                    }
                }
            }
        }
    }
}
