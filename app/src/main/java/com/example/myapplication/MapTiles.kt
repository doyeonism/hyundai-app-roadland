package com.example.myapplication

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.PriorityBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * 배경 지도 타일(웹 메르카토르 z/x/y 래스터). Pleos 이미지에는 Google Play 서비스가 없어 Google Maps SDK를 못 쓰므로,
 * 타일을 직접 받아 [HexMapView]가 육각 타일 밑에 깐다. 육각 격자도 같은 메르카토르 평면이라 좌표가 그대로 맞는다.
 * 주소는 빌드 때 정한다(gradle -ProadlandTiles=..., {s}가 있으면 a~d 서브도메인). 기본은 OSM 표준 타일.
 *
 * 끌어서 옮길 때 빈 칸이 보이지 않게, 화면에 보이는 타일([get])을 먼저 받고 화면 바깥 둘레([prefetch])는 남는 틈에 미리 받는다.
 * 둘 다 가장 최근에 요청한 것부터 받는다(빨리 끌고 지나간 자리는 뒤로 밀린다).
 */
class MapTiles(context: Context, private val onLoaded: () -> Unit) {
    // 투명도가 없는 지도라 RGB_565로 풀어 타일 하나 128KB. 32MB면 약 250장(화면 몇 개 분량)을 들고 있는다.
    private val memory = object : LruCache<String, Bitmap>(32 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    // 타일 주소가 바뀌면 예전 그림을 쓰지 않게 주소별로 폴더를 나눈다.
    private val dir = File(context.cacheDir, "tiles/${TEMPLATE.hashCode().toUInt()}").apply { mkdirs() }
    private val queued = HashMap<String, Job>()
    private val failed = HashMap<String, Long>()
    private val main = Handler(Looper.getMainLooper())
    // 현재 화면의 타일은 주변 타일을 미리 읽더라도 LRU에서 밀려나지 않게 보관한다.
    private var visibleKeys: Set<String> = emptySet()
    private val visible = HashMap<String, Bitmap>()

    fun setViewport(z: Int, x0: Int, y0: Int, cols: Int, rows: Int) {
        val n = 1 shl z
        val keys = buildSet {
            for (y in y0..y0 + rows) for (x in x0..x0 + cols) {
                if (y in 0 until n) add(key(z, Math.floorMod(x, n), y))
            }
        }
        visibleKeys = keys
        visible.keys.retainAll(keys)
        for (k in keys) if (k !in visible) memory.get(k)?.let { visible[k] = it }
    }

    /** 화면에 보이는 타일. 있으면 바로 주고, 없으면 먼저 받도록 줄 세운 뒤 null. 받으면 [onLoaded]를 부른다. */
    fun get(z: Int, x: Int, y: Int): Bitmap? {
        val k = key(z, x, y)
        return visible[k] ?: memory.get(k)?.also { if (k in visibleKeys) visible[k] = it }
            ?: run { load(z, x, y, urgent = true); null }
    }

    /** 곧 보일 수 있는 타일. 메모리에 없으면 남는 틈에 받아 둔다. */
    fun prefetch(z: Int, x: Int, y: Int) {
        // 캐시가 찼을 때 주변 타일끼리 서로 밀어내며 다시 받는 루프를 방지한다.
        if (memory.size() >= memory.maxSize() * 3 / 4) return
        if (memory.get(key(z, x, y)) == null && queued.size < MAX_QUEUED) load(z, x, y, urgent = false)
    }

    /** 받아 오지 않고 메모리에 있는 것만 본다(빈 칸을 더 넓은 줌 타일로 메울 때). */
    fun peek(z: Int, x: Int, y: Int): Bitmap? = memory.get(key(z, x, y))

    private fun load(z: Int, x: Int, y: Int, urgent: Boolean) {
        val key = key(z, x, y)
        val now = System.currentTimeMillis()
        if ((failed[key] ?: 0) > now) return
        val waiting = queued[key]
        if (waiting != null) {
            // 미리 받기로 줄 서 있던 타일이 화면에 들어왔으면 앞으로 당긴다.
            if (urgent && !waiting.urgent && io.queue.remove(waiting)) queued -= key else return
        }
        val job = Job(urgent, seq.incrementAndGet()) {
            val bmp = runCatching { read(z, x, y) }.getOrNull()
            main.post {
                queued -= key
                if (bmp != null) {
                    memory.put(key, bmp)
                    if (key in visibleKeys) {
                        visible[key] = bmp
                        onLoaded()
                    }
                } else failed[key] = System.currentTimeMillis() + RETRY_MS
            }
        }
        queued[key] = job
        io.execute(job)
    }

    private fun read(z: Int, x: Int, y: Int): Bitmap? {
        val file = File(dir, "${z}_${x}_$y.png")
        if (file.exists() && System.currentTimeMillis() - file.lastModified() < MAX_AGE_MS) {
            BitmapFactory.decodeFile(file.path, DECODE)?.let { return it }
        }
        val url = TEMPLATE.replace("{s}", "abcd"[(x + y) and 3].toString())
            .replace("{z}", "$z").replace("{x}", "$x").replace("{y}", "$y")
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 5000
            conn.readTimeout = 8000
            conn.setRequestProperty("User-Agent", "Roadland/${BuildConfig.VERSION_NAME} (Android)")
            if (conn.responseCode != 200) return null
            val bytes = conn.inputStream.use { it.readBytes() }
            file.writeBytes(bytes)
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, DECODE)
        } finally {
            conn.disconnect()
        }
    }

    /** 보이는 타일이 먼저, 같은 쪽끼리는 나중에 요청한 것이 먼저. */
    private class Job(val urgent: Boolean, val seq: Long, private val work: () -> Unit) : Runnable, Comparable<Job> {
        override fun run() = work()
        override fun compareTo(other: Job) =
            if (urgent != other.urgent) (if (urgent) -1 else 1) else other.seq.compareTo(seq)
    }

    companion object {
        val TEMPLATE: String = BuildConfig.ROADLAND_TILES
        const val ATTRIBUTION = "© OpenStreetMap contributors"
        const val MAX_ZOOM = 19
        private const val RETRY_MS = 15_000L
        private const val MAX_AGE_MS = 7 * 24 * 3_600_000L
        /** 미리 받기를 더 줄 세우지 않는 대기 수. 보이는 타일 요청은 이와 상관없이 받는다. */
        private const val MAX_QUEUED = 48
        private val DECODE = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.RGB_565 }
        private val seq = AtomicLong()
        private val io = ThreadPoolExecutor(4, 4, 30, TimeUnit.SECONDS, PriorityBlockingQueue<Runnable>())

        private fun key(z: Int, x: Int, y: Int) = "$z/$x/$y"
    }
}
