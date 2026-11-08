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
import java.util.concurrent.Executors

/**
 * 배경 지도 타일(웹 메르카토르 z/x/y 래스터). Pleos 이미지에는 Google Play 서비스가 없어 Google Maps SDK를 못 쓰므로,
 * 타일을 직접 받아 [HexMapView]가 육각 타일 밑에 깐다. 육각 격자도 같은 메르카토르 평면이라 좌표가 그대로 맞는다.
 * 주소는 빌드 때 정한다(gradle -ProadlandTiles=..., {s}가 있으면 a~d 서브도메인). 기본은 OSM 표준 타일.
 */
class MapTiles(context: Context, private val onLoaded: () -> Unit) {
    private val memory = object : LruCache<String, Bitmap>(16 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    // 타일 주소가 바뀌면 예전 그림을 쓰지 않게 주소별로 폴더를 나눈다.
    private val dir = File(context.cacheDir, "tiles/${TEMPLATE.hashCode().toUInt()}").apply { mkdirs() }
    private val loading = HashSet<String>()
    private val failed = HashMap<String, Long>()
    private val main = Handler(Looper.getMainLooper())

    /** 있으면 바로 주고, 없으면 받아 오기 시작한 뒤 null. 받으면 [onLoaded]를 부른다. */
    fun get(z: Int, x: Int, y: Int): Bitmap? {
        val key = "$z/$x/$y"
        memory.get(key)?.let { return it }
        val now = System.currentTimeMillis()
        if (key in loading || (failed[key] ?: 0) > now) return null
        loading += key
        io.execute {
            val bmp = runCatching { read(z, x, y) }.getOrNull()
            main.post {
                loading -= key
                if (bmp != null) { memory.put(key, bmp); onLoaded() } else failed[key] = now + RETRY_MS
            }
        }
        return null
    }

    private fun read(z: Int, x: Int, y: Int): Bitmap? {
        val file = File(dir, "${z}_${x}_$y.png")
        if (file.exists() && System.currentTimeMillis() - file.lastModified() < MAX_AGE_MS) {
            BitmapFactory.decodeFile(file.path)?.let { return it }
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
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        val TEMPLATE: String = BuildConfig.ROADLAND_TILES
        const val ATTRIBUTION = "© OpenStreetMap contributors"
        const val MAX_ZOOM = 19
        private const val RETRY_MS = 15_000L
        private const val MAX_AGE_MS = 7 * 24 * 3_600_000L
        private val io = Executors.newFixedThreadPool(4)
    }
}
