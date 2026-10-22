package com.archiekuo.localmusic

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val folder: String,
    val genre: String,
) {
    val uri: Uri
        get() = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)

    // 自動分類用：歌名、歌手、專輯、資料夾、曲風全部串在一起比對
    val haystack: String = "$title $artist $album $folder $genre".lowercase()

    val artistLabel: String
        get() = artist.ifBlank { "未知歌手" }
}

enum class RuleType(val label: String) {
    MANUAL("手動"),
    LANG_ZH("語言：中文"),
    LANG_EN("語言：英文"),
    KEYWORDS("資料夾／關鍵字"),
    FREQUENT("播放次數"),
}

data class Category(
    val id: String,
    val name: String,
    val rule: RuleType,
    val param: String = "",
) {
    val keywords: List<String>
        get() = param.split(',', '，', '、', '\n').map { it.trim().lowercase() }.filter { it.isNotEmpty() }

    val isSystem: Boolean
        get() = rule == RuleType.FREQUENT

    val minPlays: Int
        get() = param.trim().toIntOrNull()?.coerceAtLeast(1) ?: 10

    fun ruleText(): String = when (rule) {
        RuleType.FREQUENT -> "播放 ≥ $minPlays 次"
        RuleType.KEYWORDS -> if (keywords.isEmpty()) "資料夾／關鍵字" else "關鍵字：" + keywords.joinToString("、")
        else -> rule.label
    }
}

data class StoreData(
    val cats: List<Category>,
    val added: Map<Long, Set<String>>,
    val removed: Map<Long, Set<String>>,
    val plays: Map<Long, Int>,
    val onboarded: Boolean,
)

class Store(private val file: File) {

    companion object {
        fun defaultCats(): List<Category> = listOf(
            Category("frequent", "常聽", RuleType.FREQUENT, "10"),
            Category("zh", "中文", RuleType.LANG_ZH),
            Category("en", "英文", RuleType.LANG_EN),
            Category("lyric", "抒情", RuleType.MANUAL),
            Category("quiet", "安靜", RuleType.MANUAL),
            Category(
                "buddhist", "佛曲", RuleType.KEYWORDS,
                "佛,南無,南无,心經,心经,大悲咒,觀音,观音,阿彌陀,阿弥陀,念佛,梵唄,梵呗,法師,法师,佛曲,佛歌,佛號,佛号,誦經,诵经,晨課,晚課"
            ),
            Category("run", "跑步", RuleType.KEYWORDS, "跑步,慢跑,running,workout"),
        )
    }

    fun load(): StoreData {
        val fallback = StoreData(defaultCats(), emptyMap(), emptyMap(), emptyMap(), false)
        if (!file.exists()) return fallback
        return try {
            val o = JSONObject(file.readText())
            val cats = ArrayList<Category>()
            val ca = o.optJSONArray("cats") ?: JSONArray()
            for (i in 0 until ca.length()) {
                val c = ca.getJSONObject(i)
                val rule = try {
                    RuleType.valueOf(c.getString("rule"))
                } catch (e: Exception) {
                    RuleType.MANUAL
                }
                cats.add(Category(c.getString("id"), c.getString("name"), rule, c.optString("param", "")))
            }
            if (cats.none { it.rule == RuleType.FREQUENT }) cats.add(0, defaultCats()[0])
            StoreData(
                cats = cats,
                added = readSetMap(o.optJSONObject("added")),
                removed = readSetMap(o.optJSONObject("removed")),
                plays = readPlays(o.optJSONObject("plays")),
                onboarded = o.optBoolean("onboarded", false),
            )
        } catch (e: Exception) {
            fallback
        }
    }

    fun save(d: StoreData) {
        try {
            val o = JSONObject()
            o.put("v", 1)
            o.put("onboarded", d.onboarded)
            val ca = JSONArray()
            for (c in d.cats) {
                ca.put(
                    JSONObject()
                        .put("id", c.id)
                        .put("name", c.name)
                        .put("rule", c.rule.name)
                        .put("param", c.param)
                )
            }
            o.put("cats", ca)
            o.put("added", writeSetMap(d.added))
            o.put("removed", writeSetMap(d.removed))
            val po = JSONObject()
            for ((k, v) in d.plays) po.put(k.toString(), v)
            o.put("plays", po)
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(o.toString())
            if (!tmp.renameTo(file)) {
                file.writeText(o.toString())
                tmp.delete()
            }
        } catch (e: Exception) {
            // 存檔失敗就算了，下次再存
        }
    }

    private fun readSetMap(o: JSONObject?): Map<Long, Set<String>> {
        val out = HashMap<Long, Set<String>>()
        if (o == null) return out
        val keys = o.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            val id = k.toLongOrNull() ?: continue
            val arr = o.optJSONArray(k) ?: continue
            val set = HashSet<String>()
            for (i in 0 until arr.length()) set.add(arr.getString(i))
            if (set.isNotEmpty()) out[id] = set
        }
        return out
    }

    private fun writeSetMap(m: Map<Long, Set<String>>): JSONObject {
        val o = JSONObject()
        for ((k, v) in m) {
            val arr = JSONArray()
            for (s in v) arr.put(s)
            o.put(k.toString(), arr)
        }
        return o
    }

    private fun readPlays(o: JSONObject?): Map<Long, Int> {
        val out = HashMap<Long, Int>()
        if (o == null) return out
        val keys = o.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            val id = k.toLongOrNull() ?: continue
            out[id] = o.optInt(k, 0)
        }
        return out
    }
}

object Classifier {
    private fun isHan(cp: Int) = cp in 0x4E00..0x9FFF || cp in 0x3400..0x4DBF
    private fun isKana(cp: Int) = cp in 0x3040..0x30FF
    private fun isHangul(cp: Int) = cp in 0xAC00..0xD7AF || cp in 0x1100..0x11FF

    fun isZh(s: Song): Boolean {
        var han = false
        for (ch in s.title + s.artist) {
            val cp = ch.code
            if (isKana(cp) || isHangul(cp)) return false
            if (isHan(cp)) han = true
        }
        return han
    }

    fun isEn(s: Song): Boolean {
        var latin = false
        for (ch in s.title + " " + s.artist) {
            val cp = ch.code
            if (isHan(cp) || isKana(cp) || isHangul(cp)) return false
            if (ch in 'a'..'z' || ch in 'A'..'Z') latin = true
        }
        return latin
    }

    fun match(c: Category, keywords: List<String>, s: Song, plays: Int): Boolean = when (c.rule) {
        RuleType.MANUAL -> false
        RuleType.LANG_ZH -> isZh(s)
        RuleType.LANG_EN -> isEn(s)
        RuleType.KEYWORDS -> keywords.any { s.haystack.contains(it) }
        RuleType.FREQUENT -> plays >= c.minPlays
    }
}

object Scanner {
    suspend fun scan(ctx: Context): List<Song> = withContext(Dispatchers.IO) {
        val out = ArrayList<Song>()
        val m = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val proj = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.RELATIVE_PATH,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.GENRE,
        )
        val sel = "${MediaStore.Audio.Media.DURATION} >= 20000 AND " +
            "${MediaStore.Audio.Media.IS_RINGTONE} = 0 AND " +
            "${MediaStore.Audio.Media.IS_ALARM} = 0 AND " +
            "${MediaStore.Audio.Media.IS_NOTIFICATION} = 0"
        ctx.contentResolver.query(m, proj, sel, null, null)?.use { c ->
            val iId = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val iTitle = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val iArtist = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val iAlbum = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val iDur = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val iPath = c.getColumnIndexOrThrow(MediaStore.Audio.Media.RELATIVE_PATH)
            val iName = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val iGenre = c.getColumnIndexOrThrow(MediaStore.Audio.Media.GENRE)
            while (c.moveToNext()) {
                val name = c.getString(iName) ?: ""
                var title = c.getString(iTitle) ?: ""
                if (title.isBlank()) title = name.substringBeforeLast('.')
                var artist = c.getString(iArtist) ?: ""
                if (artist == "<unknown>") artist = ""
                var album = c.getString(iAlbum) ?: ""
                if (album == "<unknown>") album = ""
                out.add(
                    Song(
                        id = c.getLong(iId),
                        title = title,
                        artist = artist,
                        album = album,
                        durationMs = c.getLong(iDur),
                        folder = c.getString(iPath) ?: "",
                        genre = c.getString(iGenre) ?: "",
                    )
                )
            }
        }
        out.sortedBy { it.title.lowercase() }
    }
}
