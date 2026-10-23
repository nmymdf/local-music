package com.archiekuo.localmusic

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.nio.ByteOrder
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sqrt

/**
 * 估算節拍（BPM）。只在手機上解碼歌曲中段約 30 秒，不上網。
 * 做法：取音量包絡 → 找出突然變大的地方 → 用自相關找最常重複的間隔。
 * 這是估算，偶爾會抓成兩倍或一半，所以只拿來粗分快歌、慢歌。
 */
const val LOUD_NONE = 999

/** bpm：每分鐘拍數（0 = 抓不到）；loud：平均音量，單位 0.1 dBFS（LOUD_NONE = 量不到） */
data class Feat(val bpm: Int, val loud: Int)

object BpmAnalyzer {

    private const val ENV_RATE = 100          // 每秒 100 個包絡點
    private const val MAX_SECONDS = 30
    private const val MIN_BPM = 60
    private const val MAX_BPM = 170

    fun analyze(ctx: Context, uri: Uri, durationMs: Long): Feat {
        return try {
            val env = decodeEnvelope(ctx, uri, durationMs)
            if (env.size < ENV_RATE * 8) Feat(0, LOUD_NONE) else Feat(estimate(env), loudness(env))
        } catch (e: Throwable) {
            Feat(0, LOUD_NONE)
        }
    }

    /** 平均音量（RMS）換成 dBFS 的十倍，例如 -245 = -24.5 dB */
    private fun loudness(env: FloatArray): Int {
        var sq = 0.0
        for (v in env) sq += v.toDouble() * v
        val rms = sqrt(sq / env.size) / 32768.0
        if (rms <= 1e-6) return LOUD_NONE
        return Math.round(20.0 * Math.log10(rms) * 10.0).toInt()
    }

    private fun decodeEnvelope(ctx: Context, uri: Uri, durationMs: Long): FloatArray {
        val ex = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            ex.setDataSource(ctx, uri, null)
            var track = -1
            var format: MediaFormat? = null
            for (i in 0 until ex.trackCount) {
                val f = ex.getTrackFormat(i)
                if ((f.getString(MediaFormat.KEY_MIME) ?: "").startsWith("audio/")) {
                    track = i
                    format = f
                    break
                }
            }
            if (track < 0 || format == null) return FloatArray(0)
            ex.selectTrack(track)
            val startUs = if (durationMs > 90_000) durationMs * 1000L / 3 else 0L
            if (startUs > 0) ex.seekTo(startUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

            var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return FloatArray(0)
            val c = MediaCodec.createDecoderByType(mime)
            codec = c
            c.configure(format, null, null, 0)
            c.start()

            val info = MediaCodec.BufferInfo()
            val out = ArrayList<Float>(ENV_RATE * MAX_SECONDS)
            var sumSq = 0.0
            var cnt = 0
            var inputDone = false
            var outputDone = false
            var guard = 0
            val maxEnv = ENV_RATE * MAX_SECONDS

            while (!outputDone && out.size < maxEnv && guard < 20000) {
                guard++
                if (!inputDone) {
                    val ii = c.dequeueInputBuffer(5000)
                    if (ii >= 0) {
                        val buf = c.getInputBuffer(ii)
                        val size = if (buf != null) ex.readSampleData(buf, 0) else -1
                        if (size < 0) {
                            c.queueInputBuffer(ii, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            c.queueInputBuffer(ii, 0, size, ex.sampleTime, 0)
                            ex.advance()
                        }
                    }
                }
                val oi = c.dequeueOutputBuffer(info, 5000)
                if (oi >= 0) {
                    val buf = c.getOutputBuffer(oi)
                    if (buf != null && info.size > 0) {
                        buf.position(info.offset)
                        buf.limit(info.offset + info.size)
                        val sb = buf.order(ByteOrder.nativeOrder()).asShortBuffer()
                        val hop = (sampleRate / ENV_RATE).coerceAtLeast(1)
                        val ch = channels.coerceAtLeast(1)
                        val frames = sb.remaining() / ch
                        for (f in 0 until frames) {
                            var m = 0.0
                            for (k in 0 until ch) m += sb.get(f * ch + k).toDouble()
                            m /= ch
                            sumSq += m * m
                            cnt++
                            if (cnt >= hop) {
                                out.add(sqrt(sumSq / cnt).toFloat())
                                sumSq = 0.0
                                cnt = 0
                                if (out.size >= maxEnv) break
                            }
                        }
                    }
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    c.releaseOutputBuffer(oi, false)
                } else if (oi == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val nf = c.outputFormat
                    if (nf.containsKey(MediaFormat.KEY_SAMPLE_RATE)) sampleRate = nf.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    if (nf.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) channels = nf.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                }
            }
            return out.toFloatArray()
        } finally {
            try { codec?.stop() } catch (e: Throwable) { }
            try { codec?.release() } catch (e: Throwable) { }
            try { ex.release() } catch (e: Throwable) { }
        }
    }

    private fun estimate(env: FloatArray): Int {
        val n = env.size
        // 取對數讓大小聲差異不要太誇張，再取「突然變大」的部分
        val onset = FloatArray(n)
        var prev = ln(1.0 + env[0]).toFloat()
        for (i in 1 until n) {
            val cur = ln(1.0 + env[i]).toFloat()
            onset[i] = max(0f, cur - prev)
            prev = cur
        }
        var mean = 0f
        for (v in onset) mean += v
        mean /= n
        for (i in 0 until n) onset[i] -= mean

        val minLag = ENV_RATE * 60 / MAX_BPM
        val maxLag = ENV_RATE * 60 / MIN_BPM
        var bestLag = -1
        var best = 0.0
        for (lag in minLag..maxLag) {
            var r = 0.0
            for (i in lag until n) r += onset[i].toDouble() * onset[i - lag]
            r /= (n - lag)
            if (r > best) {
                best = r
                bestLag = lag
            }
        }
        if (bestLag <= 0 || best <= 0.0) return 0
        return Math.round(ENV_RATE * 60.0 / bestLag).toInt()
    }
}
