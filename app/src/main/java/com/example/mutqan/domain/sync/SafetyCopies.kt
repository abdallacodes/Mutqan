package com.example.mutqan.domain.sync

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Why a copy is being kept; it goes into the file name. */
enum class CopyReason(val tag: String) {
    DEVICE_REPLACED("device-replaced"),
    CLOUD_REPLACED("cloud-replaced"),
    DEVICE_DELETED("device-deleted"),
    CLOUD_DELETED("cloud-deleted"),
    BEFORE_MERGE("before-merge")
}

/** A copy of the user's data saved on this phone; [where] is how to find it. */
class SavedCopy(val name: String, val where: String)

/** The copy could not be written, or did not read back the same, so the change it was meant to protect must not happen. */
class SafetyCopyFailedException(cause: Throwable) : Exception("A copy of the data could not be saved", cause)

/**
 * Keeps a copy of data on this phone before anything replaces or deletes it. The file is a backup the Restore button
 * reads: in Downloads/Mutqan where Android allows it, otherwise in the app's own folder. It is read back and compared
 * before [save] returns, so a caller that gets a [SavedCopy] can go ahead.
 */
class SafetyCopies(private val context: Context) {

    /** Saves [text], a snapshot; null when it holds no entries, because there is nothing to keep. */
    suspend fun save(reason: CopyReason, text: String, now: Long = System.currentTimeMillis()): SavedCopy? =
        withContext(Dispatchers.IO) {
            if (SyncEngine.isEmpty(text)) return@withContext null
            try {
                val bytes = JSONObject(text).toString(2).toByteArray()
                val name = fileName(reason, now)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) writeToDownloads(name, bytes) else writeToAppFolder(name, bytes)
            } catch (e: Exception) {
                throw SafetyCopyFailedException(e)
            }
        }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun writeToDownloads(name: String, bytes: ByteArray): SavedCopy {
        val resolver = context.contentResolver
        val folder = "${Environment.DIRECTORY_DOWNLOADS}/$FOLDER"
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, "application/json")
            put(MediaStore.Downloads.RELATIVE_PATH, folder)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: throw IOException("The file could not be created")
        try {
            (resolver.openOutputStream(uri, "w") ?: throw IOException("The file could not be opened")).use { it.write(bytes) }
            val back = resolver.openInputStream(uri)?.use { it.readBytes() }
            if (back == null || !back.contentEquals(bytes)) throw IOException("The file did not read back the same")
            resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            throw e
        }
        // Android adds a number when a file of that name exists; say the name it really has.
        val shown = resolver.query(uri, arrayOf(MediaStore.Downloads.DISPLAY_NAME), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0) else null } ?: name
        return SavedCopy(shown, "$folder/$shown")
    }

    // Before Android 10 there is no way to write to Downloads without a storage permission the app does not ask for.
    private fun writeToAppFolder(name: String, bytes: ByteArray): SavedCopy {
        val dir = File(context.getExternalFilesDir(null) ?: context.filesDir, FOLDER).apply { mkdirs() }
        val file = File(dir, name)
        file.writeBytes(bytes)
        if (!file.readBytes().contentEquals(bytes)) {
            file.delete()
            throw IOException("The file did not read back the same")
        }
        return SavedCopy(name, file.absolutePath)
    }

    companion object {
        private const val FOLDER = "Mutqan"

        /** `mutqan-cloud-deleted-20261106-153045.json`: what happened to the data, and when. */
        fun fileName(reason: CopyReason, millis: Long): String =
            "mutqan-${reason.tag}-${SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date(millis))}.json"
    }
}
