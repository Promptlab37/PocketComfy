package cz.promptlab.h3video.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import cz.promptlab.h3video.data.SbCteniTok
import cz.promptlab.h3video.data.SbPanelyObrazu
import java.io.ByteArrayOutputStream
import java.io.File

/** Storyboard ze souboru pro [SbCteniTok] (5.67). Bitmapa se dekóduje až při prvním výřezu. */
class ObrazStoryboardu(private val soubor: File) : SbCteniTok.Obraz {

    private val meze = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        .also { BitmapFactory.decodeFile(soubor.absolutePath, it) }
    override val sirka: Int get() = meze.outWidth
    override val vyska: Int get() = meze.outHeight

    private val bmp: Bitmap by lazy {
        BitmapFactory.decodeFile(soubor.absolutePath) ?: throw IllegalStateException("storyboard nejde přečíst")
    }

    override fun original(): ByteArray = soubor.readBytes()

    override fun vyrezPng(x0: Int, y0: Int, x1: Int, y1: Int, meritko: Float): ByteArray {
        // Bitmapa může být menší než soubor (velký obrázek) — souřadnice se přepočítají.
        val sx = bmp.width.toDouble() / sirka
        val sy = bmp.height.toDouble() / vyska
        val bx0 = (x0 * sx).toInt().coerceIn(0, bmp.width - 1)
        val by0 = (y0 * sy).toInt().coerceIn(0, bmp.height - 1)
        val bx1 = (x1 * sx).toInt().coerceIn(bx0 + 1, bmp.width)
        val by1 = (y1 * sy).toInt().coerceIn(by0 + 1, bmp.height)
        val vyrez = Bitmap.createBitmap(bmp, bx0, by0, bx1 - bx0, by1 - by0)
        val m = (meritko / sx).toFloat()
        val pruh = if (m > 1f) Bitmap.createScaledBitmap(vyrez, (vyrez.width * m).toInt(), (vyrez.height * m).toInt(), true) else vyrez
        val out = ByteArrayOutputStream()
        pruh.compress(Bitmap.CompressFormat.PNG, 100, out)
        return out.toByteArray()
    }

    override fun bunky(): List<SbPanelyObrazu.Bunka>? = PanelyStoryboardu.bunky(soubor)

    private val pixely: IntArray by lazy { IntArray(bmp.width * bmp.height).also { bmp.getPixels(it, 0, bmp.width, 0, 0, bmp.width, bmp.height) } }

    override fun pasyPopisku(panely: List<SbPanelyObrazu.Obdelnik>): List<SbPanelyObrazu.Obdelnik?> = runCatching {
        val sx = bmp.width.toDouble() / sirka; val sy = bmp.height.toDouble() / vyska
        panely.map { o ->
            SbPanelyObrazu.pasPopisku(pixely, bmp.width, bmp.height,
                SbPanelyObrazu.Obdelnik((o.x0 * sx).toInt(), (o.y0 * sy).toInt(), (o.x1 * sx).toInt(), (o.y1 * sy).toInt()))
                ?.let { SbPanelyObrazu.Obdelnik(o.x0, (it.y0 / sy).toInt(), o.x1, (it.y1 / sy).toInt()) }
        }
    }.getOrElse { panely.map { null } }

    override fun vyskaPisma(panely: List<SbPanelyObrazu.Obdelnik>): Float? = runCatching {
        val w = bmp.width; val h = bmp.height
        val px = pixely
        val sx = w.toDouble() / sirka; val sy = h.toDouble() / vyska
        SbPanelyObrazu.vyskaPisma(px, w, h, panely.map {
            SbPanelyObrazu.Obdelnik((it.x0 * sx).toInt(), (it.y0 * sy).toInt(), (it.x1 * sx).toInt(), (it.y1 * sy).toInt())
        })?.let { (it / sy).toFloat() }
    }.getOrNull()
}
