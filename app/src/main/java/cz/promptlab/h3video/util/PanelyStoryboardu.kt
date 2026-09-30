package cz.promptlab.h3video.util

import android.graphics.BitmapFactory
import cz.promptlab.h3video.data.SbPanelyObrazu
import java.io.File

/**
 * Skutečné panely ze souboru storyboardu (5.38) — Android obal nad
 * [SbPanelyObrazu]. Dekóduje zmenšeně (delší strana ~1000 px, hledání stejně pracuje s 800), souřadnice
 * vrací v rozměrech ORIGINÁLU. Výsledek se pamatuje podle souboru a data změny.
 */
object PanelyStoryboardu {

    private val pamet = HashMap<String, List<SbPanelyObrazu.Bunka>>()
    private val nic = emptyList<SbPanelyObrazu.Bunka>()

    /**
     * Obdélníky panelů, když jejich počet sedí na [pocet] přečtený modelem:
     * nejdřív panely s obsahem, pak i s prázdnými políčky v rámečku (model je
     * mohl započítat). Jinak null — appka jede postaru.
     */
    fun podlePoctu(soubor: File, pocet: Int): List<SbPanelyObrazu.Obdelnik>? {
        val vse = bunky(soubor) ?: return null
        val plne = vse.filter { !it.prazdna }
        return when (pocet) {
            plne.size -> plne.map { it.obdelnik }
            vse.size -> vse.map { it.obdelnik }
            else -> null
        }
    }

    // Jeden výpočet naráz: Kontrola ukazuje ~10 výřezů a každý by jinak
    // dekódoval stejný velký obrázek současně (OOM, odborníci 30. 9. 2026).
    @Synchronized
    fun bunky(soubor: File): List<SbPanelyObrazu.Bunka>? {
        val klic = soubor.absolutePath + "@" + soubor.lastModified()
        pamet[klic]?.let { return it.ifEmpty { null } }
        val vysledek = runCatching {
            val meze = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(soubor.absolutePath, meze)
            val w0 = meze.outWidth; val h0 = meze.outHeight
            if (w0 <= 0 || h0 <= 0) return@runCatching null
            var vzorek = 1
            while (maxOf(w0, h0) / (vzorek * 2) >= 1000) vzorek *= 2
            val bmp = BitmapFactory.decodeFile(soubor.absolutePath, BitmapFactory.Options().apply { inSampleSize = vzorek })
                ?: return@runCatching null
            val w = bmp.width; val h = bmp.height
            val px = IntArray(w * h)
            bmp.getPixels(px, 0, w, 0, 0, w, h)
            bmp.recycle()
            val sx = w0.toDouble() / w; val sy = h0.toDouble() / h
            SbPanelyObrazu.najdiBunky(px, w, h)?.map { b ->
                val it = b.obdelnik
                SbPanelyObrazu.Bunka(SbPanelyObrazu.Obdelnik(
                    (it.x0 * sx).toInt(), (it.y0 * sy).toInt(),
                    minOf(w0, (it.x1 * sx).toInt()), minOf(h0, (it.y1 * sy).toInt()),
                ), b.prazdna)
            }
        }.getOrNull()
        pamet[klic] = vysledek ?: nic
        return vysledek
    }
}
