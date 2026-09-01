package cz.promptlab.h3video.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.media.MediaDataSource
import android.media.MediaMetadataRetriever
import android.os.Build
import java.nio.ByteBuffer

/**
 * Živý náhled z uzlu `ModelPreviewOverrideKJ`.
 *
 * Uzel neposílá jeden obrázek: dekóduje víc snímků latentu naráz a pošle je jako
 * jeden soubor v base64 spolu s údajem `mime` (viz `preview_override_node.py`):
 *
 *  - `video/mp4`   – animace zabalená NVENC enkodérem, když ho karta má,
 *  - `image/webp`  – animovaný WebP, když NVENC není,
 *  - `image/jpeg`  – jeden snímek (úvodní náhled šumu).
 *
 * `BitmapFactory` umí jen to poslední: z animovaného WebP vytáhne první snímek
 * a na MP4 vrátí null – proto náhled během vzorkování mizel úplně.
 */
sealed interface Preview {
    /** Snímky, které si obrazovka přehrává sama (MP4 a jednotlivé obrázky). */
    data class Frames(val frames: List<Bitmap>, val fps: Int) : Preview

    /**
     * Animovaný WebP. Snímky z něj vytáhnout nejdou – `AnimatedImageDrawable` se
     * posouvá podle času a překreslovacího zpětného volání, ne podle počtu volání
     * `draw()`. Předává se proto celý a přehraje si ho `ImageView`, který ta
     * volání umí obsloužit.
     */
    data class Animated(val drawable: Drawable) : Preview
}

object PreviewDecoder {

    /** Kolik snímků má smysl z náhledu tahat – víc než tohle oko stejně nepozná. */
    private const val MAX_FRAMES = 24

    fun decode(raw: ByteArray, mime: String?, fps: Int): Preview? {
        val safeFps = fps.coerceIn(1, 30)

        if (mime.equals("video/mp4", ignoreCase = true)) {
            val frames = videoFrames(raw)
            if (frames.isNotEmpty()) return Preview.Frames(frames, safeFps)
            return null   // z videa se obrázek vytáhnout nedá, nemá cenu to zkoušet dál
        }

        if (mime.equals("image/webp", ignoreCase = true) &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
        ) {
            animated(raw)?.let { return it }
        }

        // Jednotlivý snímek – a zároveň záchrana pro starý Android: z animovaného
        // WebP takhle vypadne aspoň jeho první snímek, tedy stav před opravou.
        val single = BitmapFactory.decodeByteArray(raw, 0, raw.size) ?: return null
        return Preview.Frames(listOf(single), safeFps)
    }

    /** Snímky z MP4. `getFramesAtIndex` je až od Androidu 9, níž zbyde jeden snímek. */
    private fun videoFrames(raw: ByteArray): List<Bitmap> {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(ByteArraySource(raw))
            val count = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)
                ?.toIntOrNull() ?: 0
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && count > 1 ->
                    retriever.getFramesAtIndex(0, minOf(count, MAX_FRAMES))

                else -> listOfNotNull(retriever.getFrameAtTime(0))
            }
        } catch (_: Exception) {
            emptyList()
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun animated(raw: ByteArray): Preview.Animated? = try {
        val source = ImageDecoder.createSource(ByteBuffer.wrap(raw))
        when (val drawable = ImageDecoder.decodeDrawable(source)) {
            is AnimatedImageDrawable -> {
                drawable.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
                Preview.Animated(drawable)
            }
            else -> null
        }
    } catch (_: Exception) {
        null
    }

    /** MediaMetadataRetriever neumí číst z pole bajtů, jen ze zdroje. */
    private class ByteArraySource(private val data: ByteArray) : MediaDataSource() {
        override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
            if (position >= data.size) return -1
            val length = minOf(size.toLong(), data.size - position).toInt()
            System.arraycopy(data, position.toInt(), buffer, offset, length)
            return length
        }

        override fun getSize(): Long = data.size.toLong()
        override fun close() {}
    }
}
