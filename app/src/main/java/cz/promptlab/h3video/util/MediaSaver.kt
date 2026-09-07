package cz.promptlab.h3video.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File

object MediaSaver {

    private const val ALBUM = "H3 Video"

    /** Jednotná cesta pro jednotlivý i hromadný export. Volat mimo hlavní vlákno. */
    fun saveItem(ctx: Context, item: cz.promptlab.h3video.data.VideoItem): Boolean {
        val file = item.file(ctx)
        val displayName = "H3_${item.createdAt}.${file.extension}"
        return when {
            item.isModel3d -> save3dToDownloads(ctx, file, displayName)
            item.isImage -> saveImageToGallery(ctx, file, displayName)
            item.isAudio -> saveAudioToGallery(ctx, file, displayName)
            else -> saveToGallery(ctx, file, displayName)
        }
    }

    fun saveToGallery(ctx: Context, file: File, displayName: String): Boolean =
        saveMedia(ctx, file, displayName, MediaStore.Video.Media.EXTERNAL_CONTENT_URI, Environment.DIRECTORY_MOVIES)

    fun saveImageToGallery(ctx: Context, file: File, displayName: String): Boolean =
        saveMedia(ctx, file, displayName, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, Environment.DIRECTORY_PICTURES)

    fun saveAudioToGallery(ctx: Context, file: File, displayName: String): Boolean =
        saveMedia(ctx, file, displayName, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, Environment.DIRECTORY_MUSIC)

    fun save3dToDownloads(ctx: Context, file: File, displayName: String): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return saveMedia(ctx, file, displayName, MediaStore.Downloads.EXTERNAL_CONTENT_URI, Environment.DIRECTORY_DOWNLOADS)
        }
        return runCatching {
            require(file.isFile && file.length() > 0)
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), ALBUM)
                .apply { mkdirs() }
            file.copyTo(File(dir, displayName), overwrite = true)
            true
        }.getOrDefault(false)
    }

    /** Po neúspěšném kopírování se odstraní i rozpracovaný řádek MediaStore. */
    private fun saveMedia(ctx: Context, file: File, displayName: String, collection: Uri, directory: String): Boolean {
        if (!file.isFile || file.length() <= 0) return false
        val resolver = ctx.contentResolver
        var inserted: Uri? = null
        return try {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, mediaMimeType(file.name))
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "$directory/$ALBUM")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }
            val uri = resolver.insert(collection, values) ?: return false
            inserted = uri
            val output = resolver.openOutputStream(uri) ?: error("Nelze otevřít cílový soubor")
            output.use { out -> file.inputStream().use { it.copyTo(out) } }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val ready = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
                check(resolver.update(uri, ready, null, null) > 0)
            }
            true
        } catch (_: Exception) {
            inserted?.let { uri -> runCatching { resolver.delete(uri, null, null) } }
            false
        }
    }

    fun shareIntent(ctx: Context, file: File): Intent {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
        return Intent(Intent.ACTION_SEND).apply {
            type = mediaMimeType(file.name)
            clipData = android.content.ClipData.newRawUri("PocketComfy", uri)
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
