package com.example.mutqan.quran

import android.content.Context
import android.graphics.Typeface
import android.util.Log
import android.util.LruCache
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipInputStream
import kotlin.coroutines.coroutineContext

/** A juz-sized zip of page fonts published as a GitHub release asset. */
data class FontPack(
    val file: String,
    val firstPage: Int,
    val lastPage: Int,
    val bytes: Long,
    val sha256: String
) {
    val pageCount: Int get() = lastPage - firstPage + 1
}

/**
 * Supplies the per-page QPC V2 fonts the Mushaf is drawn with.
 *
 * Pages 1–2 ship inside the APK (assets/mushaf_fonts); every other page font lives in a
 * ~4 MB juz pack that is downloaded on first use and unpacked to filesDir/mushaf_fonts.
 * The pack list and checksums come from assets/mushaf_font_manifest.json, which is the
 * manifest published next to the packs (see scripts/build_font_packs.py).
 */
class MushafFontManager private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val fontsDir: File = File(appContext.filesDir, "mushaf_fonts").also { it.mkdirs() }
    private val typefaces = LruCache<Int, Typeface>(TYPEFACE_CACHE_SIZE)
    private val packLocks = ConcurrentHashMap<String, Mutex>()
    private val httpClient = OkHttpClient()

    val packs: List<FontPack> by lazy {
        val json = appContext.assets.open(MANIFEST_ASSET).bufferedReader().use { JSONObject(it.readText()) }
        val array = json.getJSONArray("packs")
        List(array.length()) { i ->
            array.getJSONObject(i).let {
                FontPack(
                    file      = it.getString("file"),
                    firstPage = it.getInt("first_page"),
                    lastPage  = it.getInt("last_page"),
                    bytes     = it.getLong("bytes"),
                    sha256    = it.getString("sha256")
                )
            }
        }
    }

    fun packFor(page: Int): FontPack? = packs.firstOrNull { page in it.firstPage..it.lastPage }

    fun isAvailable(page: Int): Boolean =
        page in BUNDLED_PAGES || fontFile(page).let { it.exists() && it.length() > 0 }

    fun isPackComplete(pack: FontPack): Boolean = (pack.firstPage..pack.lastPage).all(::isAvailable)

    fun availablePageCount(): Int = (1..TOTAL_PAGES).count(::isAvailable)

    /** The page's font, or null if it is not on the device yet (see [ensure]). */
    fun typeface(page: Int): Typeface? {
        typefaces.get(page)?.let { return it }
        val loaded = try {
            when {
                page in BUNDLED_PAGES -> Typeface.createFromAsset(appContext.assets, "mushaf_fonts/p$page.ttf")
                fontFile(page).exists() -> Typeface.createFromFile(fontFile(page))
                else -> null
            }
        } catch (e: RuntimeException) {
            Log.e(TAG, "Unreadable font for page $page", e)
            fontFile(page).delete()
            null
        }
        return loaded?.also { typefaces.put(page, it) }
    }

    /** Makes sure [page]'s font is on the device, downloading its pack if needed. */
    suspend fun ensure(page: Int): Boolean {
        if (isAvailable(page)) return true
        val pack = packFor(page) ?: return false
        return downloadPack(pack)
    }

    /** Downloads and unpacks [pack]; returns true if all its fonts are on the device afterwards. */
    suspend fun downloadPack(pack: FontPack): Boolean = withContext(Dispatchers.IO) {
        packLocks.getOrPut(pack.file) { Mutex() }.withLock {
            if (isPackComplete(pack)) return@withLock true
            val zip = File(appContext.cacheDir, pack.file)
            try {
                download(pack, zip)
                unpack(pack, zip)
                isPackComplete(pack)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Pack ${pack.file} failed", e)
                false
            } finally {
                zip.delete()
            }
        }
    }

    private suspend fun download(pack: FontPack, dest: File) {
        val digest = MessageDigest.getInstance("SHA-256")
        val request = Request.Builder().url(BASE_URL + pack.file).build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code} for ${pack.file}")
            val body = response.body ?: throw IOException("Empty body for ${pack.file}")
            body.byteStream().use { input ->
                dest.outputStream().use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        coroutineContext.ensureActive()
                        val n = input.read(buffer)
                        if (n < 0) break
                        digest.update(buffer, 0, n)
                        output.write(buffer, 0, n)
                    }
                }
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        if (!actual.equals(pack.sha256, ignoreCase = true)) {
            throw IOException("Checksum mismatch for ${pack.file}")
        }
    }

    private fun unpack(pack: FontPack, zip: File) {
        ZipInputStream(zip.inputStream().buffered()).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                val page = FONT_ENTRY.matchEntire(entry.name)?.groupValues?.get(1)?.toIntOrNull()
                if (page == null || page !in pack.firstPage..pack.lastPage) continue
                val temp = File(fontsDir, "p$page.ttf.tmp")
                temp.outputStream().use { zis.copyTo(it) }
                if (!temp.renameTo(fontFile(page))) {
                    temp.copyTo(fontFile(page), overwrite = true)
                    temp.delete()
                }
            }
        }
    }

    private fun fontFile(page: Int) = File(fontsDir, "p$page.ttf")

    companion object {
        private const val TAG = "MushafFontManager"
        private const val TOTAL_PAGES = 604
        private const val MANIFEST_ASSET = "mushaf_font_manifest.json"
        private const val BASE_URL = "https://github.com/abdallacodes/Mutqan/releases/download/fonts-qpc-v2/"
        private const val TYPEFACE_CACHE_SIZE = 8
        private const val BUFFER_SIZE = 16 * 1024
        private val BUNDLED_PAGES = 1..2
        private val FONT_ENTRY = Regex("""p(\d+)\.ttf""")

        @Volatile private var instance: MushafFontManager? = null

        fun getInstance(context: Context): MushafFontManager =
            instance ?: synchronized(this) {
                instance ?: MushafFontManager(context).also { instance = it }
            }
    }
}
