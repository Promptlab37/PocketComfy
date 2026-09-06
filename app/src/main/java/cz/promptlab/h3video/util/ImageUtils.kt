package cz.promptlab.h3video.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

object ImageUtils {

    /** Delší hrana odesílaného obrázku. H3 stejně nikdy neupscaluje nad 2048 px. */
    private const val MAX_EDGE = 2048

    /** Načte obrázek z galerie, narovná ho podle EXIF a zmenší na rozumnou velikost. */
    fun loadUpright(ctx: Context, uri: Uri, maxEdge: Int = MAX_EDGE): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > maxEdge * 2) sample *= 2

        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        var bmp = ctx.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        } ?: return null

        val orientation = runCatching {
            ctx.contentResolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        bmp = applyExif(bmp, orientation)

        val longest = max(bmp.width, bmp.height)
        if (longest > maxEdge) {
            val s = maxEdge.toFloat() / longest
            val scaled = Bitmap.createScaledBitmap(
                bmp, (bmp.width * s).roundToInt(), (bmp.height * s).roundToInt(), true
            )
            if (scaled != bmp) bmp.recycle()
            bmp = scaled
        }
        return bmp
    }

    private fun applyExif(src: Bitmap, orientation: Int): Bitmap {
        val m = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { m.postRotate(90f); m.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { m.postRotate(270f); m.postScale(-1f, 1f) }
            else -> return src
        }
        val out = Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
        if (out != src) src.recycle()
        return out
    }

    fun toJpeg(bmp: Bitmap, quality: Int = 92): ByteArray {
        val bos = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, quality, bos)
        return bos.toByteArray()
    }

    /** Delší hrana náhledu v UI. Víc nemá smysl, karta obrázku je menší než 200 dp. */
    private const val THUMB_EDGE = 480

    /**
     * Zmenšená kopie pro náhled v UI. Ukládá se v RGB_565, protože náhled nepotřebuje
     * průhlednost ani 8 bitů na kanál – zabere polovinu paměti proti ARGB_8888.
     */
    fun scaleTo(src: Bitmap, maxEdge: Int): Bitmap {
        val longest = max(src.width, src.height)
        val s = if (longest <= maxEdge) 1f else maxEdge.toFloat() / longest
        val w = (src.width * s).roundToInt().coerceAtLeast(1)
        val h = (src.height * s).roundToInt().coerceAtLeast(1)
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565)
        android.graphics.Canvas(out).drawBitmap(
            src,
            android.graphics.Rect(0, 0, src.width, src.height),
            android.graphics.Rect(0, 0, w, h),
            android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)
        )
        return out
    }

    /**
     * Vybraný obrázek narovná, zmenší a uloží jako JPEG do složky aplikace.
     * Díky tomu přežije restart i zabití procesu a při odesílání se už jen čte
     * hotový soubor — nezáleží na tom, jestli mezitím vypršelo oprávnění k URI.
     */
    fun importToApp(ctx: Context, uri: Uri, target: File): Bitmap? {
        val bmp = loadUpright(ctx, uri) ?: return null
        target.parentFile?.mkdirs()
        target.writeBytes(toJpeg(bmp))
        val thumb = scaleTo(bmp, THUMB_EDGE)
        if (thumb != bmp) bmp.recycle()
        return thumb
    }

    /**
     * Kam patří náhled 3D modelu.
     *
     * GLB sám o sobě obrázek nemá, takže se náhled sejme z prohlížeče při
     * prvním zobrazení a uloží sem. Do galerie telefonu nepatří — je to
     * pomůcka appky, ne výsledek.
     */
    fun nahled3d(ctx: Context, model: File): File =
        File(File(ctx.filesDir, "nahledy3d").apply { mkdirs() }, model.name + ".jpg")

    /** Rozměry souboru bez dekódování pixelů. Null, když to není obrázek. */
    fun rozmery(file: File): Pair<Int, Int>? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth > 0 && bounds.outHeight > 0) {
            bounds.outWidth to bounds.outHeight
        } else null
    }.getOrNull()

    /**
     * Rozměry videa tak, jak se doopravdy zobrazí.
     *
     * Telefon točí na výšku tak, že uloží obraz na šířku a přidá k němu
     * otočení o 90°. Kdo tu značku přehlédne, vezme z portrétového videa
     * rozměry na šířku — proto se strany při 90 a 270° prohazují.
     */
    fun rozmeryVidea(file: File): Pair<Int, Int>? = runCatching {
        val m = android.media.MediaMetadataRetriever()
        try {
            m.setDataSource(file.absolutePath)
            fun cislo(klic: Int) = m.extractMetadata(klic)?.toIntOrNull() ?: 0
            val w = cislo(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            val h = cislo(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            val otoceni = cislo(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
            if (w <= 0 || h <= 0) null
            else if (otoceni == 90 || otoceni == 270) h to w
            else w to h
        } finally {
            runCatching { m.release() }
        }
    }.getOrNull()

    /** Náhled uloženého referenčního souboru – dekóduje se rovnou zmenšený. */
    fun loadFileThumb(file: File): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= THUMB_EDGE) sample *= 2
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        BitmapFactory.decodeFile(file.absolutePath, opts)
    }.getOrNull()
}
