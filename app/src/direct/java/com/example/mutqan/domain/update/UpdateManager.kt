package com.example.mutqan.domain.update

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Updates the app from its own GitHub releases, so a new version needs no trip to the browser: asks GitHub for the latest
 * release, downloads its APK into the cache, checks it, and hands it to Android's installer (which asks the user once and
 * refuses anything not signed with the same key as the installed app).
 */
class UpdateManager private constructor(context: Context) {

    /** [SERVER]: the connection works but GitHub does not give the answer or file (busy, or its rate limit is used up). */
    enum class Problem { NETWORK, SERVER, BAD_FILE, STORAGE }

    sealed interface State {
        data object Idle : State
        /** [quiet] checks (made by the app on its own) show nothing until they find an update. */
        data class Checking(val quiet: Boolean) : State
        data object UpToDate : State
        data class Available(val info: UpdateInfo) : State
        data class Downloading(val info: UpdateInfo, val fraction: Float) : State
        data class Ready(val info: UpdateInfo, val file: File) : State
        /** [info] is null when the failure was the check itself. */
        data class Failed(val problem: Problem, val info: UpdateInfo?) : State
    }

    private val app = context.applicationContext
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefs = app.getSharedPreferences("update", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state

    /** The version installed now. */
    val currentVersion: String =
        runCatching { app.packageManager.getPackageInfo(app.packageName, 0).versionName }.getOrNull().orEmpty()

    /** A build run from Android Studio is signed with another key than the releases, so it could not be updated this way. */
    private val isDebugBuild = app.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    private val busy get() = _state.value.let { it is State.Checking || it is State.Downloading }

    private val updateDir get() = File(app.cacheDir, "update")

    private val _autoCheck = MutableStateFlow(prefs.getBoolean(AUTO_CHECK, true))

    /** Whether the app looks for an update on its own (the Settings switch); off, it checks only when asked. */
    val autoCheck: StateFlow<Boolean> = _autoCheck

    fun setAutoCheck(on: Boolean) {
        prefs.edit().putBoolean(AUTO_CHECK, on).apply()
        _autoCheck.value = on
    }

    /** The app's own check: at most every [CHECK_EVERY_MS], only when switched on, never in a debug build, and silent unless there is an update. */
    fun checkIfDue() {
        if (isDebugBuild || busy || !_autoCheck.value) return
        val now = System.currentTimeMillis()
        if (now - prefs.getLong(LAST_CHECK, 0L) < CHECK_EVERY_MS) return
        check(quiet = true)
    }

    /** Asks GitHub for the latest release. [quiet]: a failure is not shown. */
    fun check(quiet: Boolean = false) {
        if (busy) return
        _state.value = State.Checking(quiet)
        scope.launch {
            try {
                val info = retrying { fetchLatest() }
                prefs.edit().putLong(LAST_CHECK, System.currentTimeMillis()).apply()
                if (info != null && UpdateRelease.isNewer(info.version, currentVersion)) {
                    _state.value = State.Available(info)
                } else {
                    updateDir.deleteRecursively() // what was downloaded earlier is installed now
                    _state.value = State.UpToDate
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "checking for an update failed", e)
                _state.value = if (quiet) State.Idle else State.Failed(problemOf(e), null)
            }
        }
    }

    /** Downloads the update offered (or retries one that failed). */
    fun download() {
        val info = when (val s = _state.value) {
            is State.Available -> s.info
            is State.Failed -> s.info
            else -> null
        }
        if (info == null) {
            if (_state.value is State.Failed) check()
            return
        }
        _state.value = State.Downloading(info, 0f)
        scope.launch {
            try {
                val file = retrying { fetchApk(info) { fraction -> _state.value = State.Downloading(info, fraction) } }
                _state.value = State.Ready(info, file)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "downloading the update failed", e)
                _state.value = State.Failed(problemOf(e), info)
            }
        }
    }

    /** Runs [block], and again after a pause when it failed in a way that may pass ([UpdateRelease.isTransient]): GitHub answers 504 now and then. */
    private suspend fun <T> retrying(block: () -> T): T {
        var attempt = 1
        while (true) {
            try {
                return block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (attempt >= ATTEMPTS || e is BadFileException || !UpdateRelease.isTransient(e)) throw e
                Log.w(TAG, "attempt $attempt failed, trying again", e)
                delay(RETRY_PAUSE_MS * attempt)
                attempt++
            }
        }
    }

    private fun problemOf(e: Exception): Problem = when {
        e is BadFileException -> Problem.BAD_FILE
        e is HttpAnswerException -> Problem.SERVER
        e is IOException && (e.message?.contains("ENOSPC") == true || e.message?.contains("No space", ignoreCase = true) == true) -> Problem.STORAGE
        else -> Problem.NETWORK
    }

    /** Whether Android lets this app start an install; when not, [allowInstalls] opens the setting. */
    fun canInstall(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.O || app.packageManager.canRequestPackageInstalls()

    /** Opens the "install unknown apps" setting of this app. */
    fun allowInstalls(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${app.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    /** Opens the system installer for the downloaded update. False when there is none to install or the installer would not open. */
    fun install(context: Context): Boolean {
        val ready = _state.value as? State.Ready ?: return false
        val uri = FileProvider.getUriForFile(app, "${app.packageName}.updates", ready.file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, APK_TYPE)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }

    private fun fetchLatest(): UpdateInfo? {
        val request = Request.Builder()
            .url(LATEST_URL)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "Mutqan-app")
            .build()
        client.newCall(request).execute().use { response ->
            if (response.code == 404) return null // no release yet
            if (!response.isSuccessful) throw HttpAnswerException(response.code)
            return UpdateRelease.parseLatest(response.body?.string().orEmpty())
        }
    }

    /** Downloads [info]'s APK and checks it; the file is kept in the cache until the next update replaces it. */
    private fun fetchApk(info: UpdateInfo, onProgress: (Float) -> Unit): File {
        val dir = updateDir.apply { mkdirs() }
        val target = File(dir, "Mutqan-${info.version}.apk")
        dir.listFiles()?.filter { it != target }?.forEach { it.delete() }
        if (target.isFile && matches(target, info, sha = null)) return target
        // Still ends in .apk, so that checking it as a package does not depend on the name.
        val part = File(dir, "part-${target.name}")
        val digest = MessageDigest.getInstance("SHA-256")
        val request = Request.Builder().url(info.apkUrl).header("User-Agent", "Mutqan-app").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw HttpAnswerException(response.code)
            val body = response.body ?: throw IOException("empty answer")
            val total = body.contentLength().takeIf { it > 0 } ?: info.size
            var done = 0L
            var shown = -1
            body.byteStream().use { input ->
                part.outputStream().use { out ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        out.write(buffer, 0, n)
                        digest.update(buffer, 0, n)
                        done += n
                        val percent = if (total > 0) (done * 100 / total).toInt().coerceIn(0, 100) else 0
                        if (percent != shown) {
                            shown = percent
                            onProgress(percent / 100f)
                        }
                    }
                }
            }
        }
        val sha = digest.digest().joinToString("") { "%02x".format(it) }
        if (!matches(part, info, sha)) {
            part.delete()
            throw BadFileException()
        }
        if (!part.renameTo(target)) throw IOException("could not keep the download")
        return target
    }

    /** The file is the whole APK GitHub listed, of this app: its size, its SHA-256 ([sha] when known) and its package. */
    private fun matches(file: File, info: UpdateInfo, sha: String?): Boolean {
        if (info.size > 0 && file.length() != info.size) return false
        if (sha != null && info.sha256 != null && sha != info.sha256) return false
        val archive = app.packageManager.getPackageArchiveInfo(file.path, 0)
        return archive?.packageName == app.packageName
    }

    private class BadFileException : IOException("the downloaded file does not match the release")

    companion object {
        private const val LATEST_URL = "https://api.github.com/repos/abdallacodes/Mutqan/releases/latest"
        private const val APK_TYPE = "application/vnd.android.package-archive"
        private const val LAST_CHECK = "last_check"
        private const val AUTO_CHECK = "auto_check"
        private const val TAG = "UpdateManager"
        /** GitHub allows 60 unsigned requests an hour from one address; one check an hour from a phone is far below that. */
        private const val CHECK_EVERY_MS = 60L * 60 * 1000
        private const val ATTEMPTS = 3
        private const val RETRY_PAUSE_MS = 2_000L

        @Volatile
        private var instance: UpdateManager? = null

        fun get(context: Context): UpdateManager =
            instance ?: synchronized(this) { instance ?: UpdateManager(context).also { instance = it } }
    }
}
