package com.example.mutqan.domain.audio

import android.content.Context
import com.example.mutqan.quran.QuranRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Keeps whole surahs of a reciter on the device, so they play with no connection and no wait. A surah is kept as the
 * files it is played from: one per verse, or the one surah file for a reciter who has only that. The files sit in app
 * storage, which the player's cache never clears; they go only when the reader deletes them.
 */
class RecitationDownloads private constructor(context: Context) {

    /** How far a download has got, in files. */
    data class Progress(val done: Int, val total: Int) {
        val fraction: Float get() = if (total == 0) 0f else done.toFloat() / total
    }

    data class State(
        /** Surahs being downloaded, by [key]. */
        val downloading: Map<String, Progress> = emptyMap(),
        /** Surahs kept in full, by [key]. */
        val downloaded: Set<String> = emptySet(),
        /** Bytes each kept surah takes on the device, by [key]. */
        val sizes: Map<String, Long> = emptyMap(),
        /** Set when a download stopped short; cleared by [clearFailure]. */
        val failed: Boolean = false
    )

    private val app = context.applicationContext
    private val sources = RecitationSources(app)
    private val quran by lazy { QuranRepository.getInstance(app) }
    private val client by lazy { OkHttpClient() }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = ConcurrentHashMap<String, Job>()

    /** One limit on files in flight, however many surahs are being fetched. */
    private val gate = Semaphore(PARALLEL)

    private val _state = MutableStateFlow(State(downloaded = scanDownloaded()))
    val state: StateFlow<State> = _state

    init {
        scope.launch { refreshSizes() }
    }

    /** Measures what the kept surahs take, file by file. */
    private fun refreshSizes() {
        val sizes = HashMap<String, Long>()
        for (reciter in Reciter.entries) {
            root(reciter).listFiles { f -> f.name.endsWith(".mp3") }?.forEach { f ->
                val surah = f.name.take(3).toIntOrNull() ?: return@forEach
                sizes.merge(key(reciter, surah), f.length(), Long::plus)
            }
        }
        _state.update { it.copy(sizes = sizes.filterKeys { k -> k in it.downloaded }) }
    }

    private fun root(reciter: Reciter) = File(app.filesDir, "recitation/${reciter.id}")

    private fun marker(reciter: Reciter, surah: Int) = File(root(reciter), "%03d.done".format(surah))

    private fun fileName(reciter: Reciter, surah: Int, ayah: Int) =
        if (reciter.verseUrlBase != null) "%03d%03d.mp3".format(surah, ayah) else "%03d.mp3".format(surah)

    private fun scanDownloaded(): Set<String> {
        val out = HashSet<String>()
        for (reciter in Reciter.entries) {
            root(reciter).listFiles { f -> f.name.endsWith(".done") }?.forEach { f ->
                f.name.removeSuffix(".done").toIntOrNull()?.let { out += key(reciter, it) }
            }
        }
        return out
    }

    /** The file of [surah]:[ayah] kept on the device for [reciter], or null when there is none. */
    fun fileFor(reciter: Reciter, surah: Int, ayah: Int): File? =
        File(root(reciter), fileName(reciter, surah, ayah)).takeIf { it.isFile }

    /** What to fetch for a surah: each verse's file with where it goes, a surah file once. */
    private fun filesOf(reciter: Reciter, surah: Int): List<Pair<String, File>> {
        val index = quran.ayahIndex
        val out = LinkedHashMap<File, String>()
        for (id in 1..TOTAL_AYAHS) {
            if (id !in index || index.surahOf(id) != surah) continue
            val ayah = index.ayahOf(id)
            val clip = sources.clip(reciter, surah, ayah) ?: continue
            out.putIfAbsent(File(root(reciter), fileName(reciter, surah, ayah)), clip.url)
        }
        return out.map { (file, url) -> url to file }
    }

    fun download(reciter: Reciter, surah: Int) {
        val key = key(reciter, surah)
        if (jobs.containsKey(key) || key in _state.value.downloaded) return
        // Shown at once, so the notification service that starts below finds a download to report.
        setProgress(key, Progress(0, 1))
        RecitationDownloadService.start(app)
        jobs[key] = scope.launch {
            try {
                root(reciter).mkdirs()
                val files = filesOf(reciter, surah)
                if (files.isEmpty()) throw IOException("nothing to download")
                val done = AtomicInteger(files.count { it.second.isFile })
                setProgress(key, Progress(done.get(), files.size))
                coroutineScope {
                    files.filterNot { it.second.isFile }.map { (url, file) ->
                        async {
                            gate.withPermit { fetch(url, file) }
                            setProgress(key, Progress(done.incrementAndGet(), files.size))
                        }
                    }.awaitAll()
                }
                marker(reciter, surah).writeText("")
                _state.update { it.copy(downloading = it.downloading - key, downloaded = it.downloaded + key) }
                refreshSizes()
            } catch (e: CancellationException) {
                _state.update { it.copy(downloading = it.downloading - key) }
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(downloading = it.downloading - key, failed = true) }
            } finally {
                jobs.remove(key)
            }
        }
    }

    /** Stops a download under way; what it has fetched is kept, and the next one carries on from there. */
    fun cancel(reciter: Reciter, surah: Int) {
        jobs[key(reciter, surah)]?.cancel()
    }

    fun delete(reciter: Reciter, surah: Int) {
        val key = key(reciter, surah)
        scope.launch {
            jobs.remove(key)?.cancelAndJoinQuietly()
            marker(reciter, surah).delete()
            for ((_, file) in filesOf(reciter, surah)) {
                file.delete()
                File(file.parentFile, file.name + ".part").delete()
            }
            _state.update { it.copy(downloading = it.downloading - key, downloaded = it.downloaded - key) }
            refreshSizes()
        }
    }

    /** Downloads each of [surahs] that is not kept yet. */
    fun downloadAll(reciter: Reciter, surahs: Collection<Int>) {
        surahs.forEach { download(reciter, it) }
    }

    fun cancelAll(reciter: Reciter, surahs: Collection<Int>) {
        surahs.forEach { cancel(reciter, it) }
    }

    /** Removes every downloaded surah of [reciter]. */
    fun deleteReciter(reciter: Reciter) {
        scope.launch {
            val prefix = "${reciter.id}:"
            jobs.filterKeys { it.startsWith(prefix) }.forEach { (key, job) ->
                jobs.remove(key)
                job.cancelAndJoinQuietly()
            }
            root(reciter).deleteRecursively()
            _state.update {
                it.copy(
                    downloading = it.downloading.filterKeys { k -> !k.startsWith(prefix) },
                    downloaded = it.downloaded.filterNot { k -> k.startsWith(prefix) }.toSet(),
                    sizes = it.sizes.filterKeys { k -> !k.startsWith(prefix) }
                )
            }
        }
    }

    /** Removes every downloaded recitation, of every reciter. */
    fun deleteAll() {
        scope.launch {
            jobs.values.forEach { it.cancelAndJoinQuietly() }
            File(app.filesDir, "recitation").deleteRecursively()
            _state.update { it.copy(downloading = emptyMap(), downloaded = emptySet(), sizes = emptyMap()) }
        }
    }

    fun clearFailure() {
        _state.update { it.copy(failed = false) }
    }

    private suspend fun Job.cancelAndJoinQuietly() {
        cancel()
        try {
            join()
        } catch (_: CancellationException) {
        }
    }

    private fun setProgress(key: String, progress: Progress) {
        _state.update { it.copy(downloading = it.downloading + (key to progress)) }
    }

    /** Fetches [url] into [to], whole or not at all. */
    private suspend fun fetch(url: String, to: File) {
        val part = File(to.parentFile, to.name + ".part")
        suspendCancellableCoroutine { cont ->
            val call = client.newCall(Request.Builder().url(url).build())
            cont.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    cont.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    try {
                        response.use {
                            if (!it.isSuccessful) throw IOException("HTTP ${it.code}")
                            val body = it.body ?: throw IOException("empty")
                            val expected = body.contentLength()
                            body.byteStream().use { input -> part.outputStream().use { out -> input.copyTo(out) } }
                            if (expected >= 0 && part.length() != expected) throw IOException("cut short")
                            if (!part.renameTo(to)) throw IOException("could not save")
                        }
                        cont.resume(Unit)
                    } catch (e: Exception) {
                        part.delete()
                        cont.resumeWithException(e)
                    }
                }
            })
        }
    }

    companion object {
        private const val TOTAL_AYAHS = 6236
        private const val PARALLEL = 3

        fun key(reciter: Reciter, surah: Int) = "${reciter.id}:$surah"

        @Volatile
        private var instance: RecitationDownloads? = null

        fun get(context: Context): RecitationDownloads =
            instance ?: synchronized(this) { instance ?: RecitationDownloads(context).also { instance = it } }
    }
}
