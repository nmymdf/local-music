package com.archiekuo.localmusic

import android.app.Application
import android.content.Context
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

class LibraryViewModel(app: Application) : AndroidViewModel(app) {

    private val store = Store(File(app.filesDir, "data.json"))

    var songs by mutableStateOf<List<Song>>(emptyList())
        private set
    var cats by mutableStateOf<List<Category>>(Store.defaultCats())
        private set
    var added by mutableStateOf<Map<Long, Set<String>>>(emptyMap())
        private set
    var removed by mutableStateOf<Map<Long, Set<String>>>(emptyMap())
        private set
    var plays by mutableStateOf<Map<Long, Int>>(emptyMap())
        private set
    var onboarded by mutableStateOf(false)
        private set
    var scanned by mutableStateOf(false)
        private set
    var scanning by mutableStateOf(false)
        private set

    private var refreshing = false

    init {
        val d = store.load()
        cats = d.cats
        added = d.added
        removed = d.removed
        plays = d.plays
        onboarded = d.onboarded
    }

    /** 每個分類裡有哪些歌（自動規則 + 手動加入 − 手動移出） */
    val byCat: Map<String, List<Song>> by derivedStateOf { computeByCat() }

    /** 沒有被任何分類（常聽除外）收進去的歌 */
    val unclassified: List<Song> by derivedStateOf {
        val covered = HashSet<Long>()
        for (c in cats) {
            if (c.rule != RuleType.FREQUENT) byCat[c.id]?.forEach { covered.add(it.id) }
        }
        songs.filter { it.id !in covered }
    }

    private fun computeByCat(): Map<String, List<Song>> {
        val kw = HashMap<String, List<String>>()
        for (c in cats) kw[c.id] = c.keywords
        val res = LinkedHashMap<String, ArrayList<Song>>()
        for (c in cats) res[c.id] = ArrayList()
        for (s in songs) {
            val p = plays[s.id] ?: 0
            val rem = removed[s.id]
            val add = added[s.id]
            for (c in cats) {
                val auto = Classifier.match(c, kw[c.id] ?: emptyList(), s, p)
                val inCat = (auto && rem?.contains(c.id) != true) || add?.contains(c.id) == true
                if (inCat) res[c.id]?.add(s)
            }
        }
        // 常聽：播放次數多的排前面
        for (c in cats) {
            if (c.rule == RuleType.FREQUENT) {
                res[c.id]?.sortByDescending { plays[it.id] ?: 0 }
            }
        }
        return res
    }

    fun playCount(id: Long): Int = plays[id] ?: 0

    fun refresh(ctx: Context) {
        if (refreshing) return
        refreshing = true
        viewModelScope.launch {
            if (!scanned) scanning = true
            val list = try {
                Scanner.scan(ctx)
            } catch (e: Exception) {
                emptyList()
            }
            songs = list
            scanned = true
            scanning = false
            refreshing = false
        }
    }

    fun markOnboarded() {
        onboarded = true
        persist()
    }

    fun addPlay(id: Long) {
        plays = plays + (id to ((plays[id] ?: 0) + 1))
        persist()
    }

    fun assign(ids: Set<Long>, catId: String) {
        val a = added.toMutableMap()
        val r = removed.toMutableMap()
        for (id in ids) {
            a[id] = (a[id] ?: emptySet()) + catId
            val rs = (r[id] ?: emptySet()) - catId
            if (rs.isEmpty()) r.remove(id) else r[id] = rs
        }
        added = a
        removed = r
        persist()
    }

    fun unassign(ids: Set<Long>, catId: String) {
        val a = added.toMutableMap()
        val r = removed.toMutableMap()
        for (id in ids) {
            val s = (a[id] ?: emptySet()) - catId
            if (s.isEmpty()) a.remove(id) else a[id] = s
            r[id] = (r[id] ?: emptySet()) + catId
        }
        added = a
        removed = r
        persist()
    }

    private fun checkName(name: String, selfId: String?): String? {
        val n = name.trim()
        if (n.isEmpty()) return "名稱不能空白"
        if (n == "全部" || n == "未分類") return "這個名稱系統已經在用"
        if (cats.any { it.id != selfId && it.name.equals(n, ignoreCase = true) }) {
            return "名稱已經有了，這一列不會存"
        }
        return null
    }

    fun addCategory(name: String): String? {
        val err = checkName(name, null)
        if (err != null) return err
        cats = cats + Category("c" + System.currentTimeMillis(), name.trim(), RuleType.MANUAL)
        persist()
        return null
    }

    fun renameCategory(id: String, name: String): String? {
        val err = checkName(name, id)
        if (err != null) return err
        cats = cats.map { if (it.id == id) it.copy(name = name.trim()) else it }
        persist()
        return null
    }

    fun setRule(id: String, rule: RuleType, param: String) {
        cats = cats.map { if (it.id == id) it.copy(rule = rule, param = param.trim()) else it }
        persist()
    }

    fun deleteCategories(ids: Set<String>) {
        cats = cats.filter { it.id !in ids || it.isSystem }
        added = added.mapValues { (_, v) -> v - ids }.filterValues { it.isNotEmpty() }
        removed = removed.mapValues { (_, v) -> v - ids }.filterValues { it.isNotEmpty() }
        persist()
    }

    /** 檔案已經被系統刪掉之後，把它們從清單和記憶裡拿掉 */
    fun removeSongs(ids: Set<Long>) {
        songs = songs.filter { it.id !in ids }
        added = added - ids
        removed = removed - ids
        plays = plays - ids
        persist()
    }

    private fun persist() {
        val d = StoreData(cats, added, removed, plays, onboarded)
        viewModelScope.launch(Dispatchers.IO) { store.save(d) }
    }
}
