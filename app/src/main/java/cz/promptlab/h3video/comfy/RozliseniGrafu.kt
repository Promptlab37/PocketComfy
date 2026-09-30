package cz.promptlab.h3video.comfy

import org.json.JSONObject
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Na jaké rozlišení běh opravdu generuje — vyčteno z grafu, který jde na server
 * (5.45, uživatel: „proč nevidím, na jaké rozlišení generuji“). Funguje pro
 * všechny karty stejně: štítky balíku SatoDive („540P“ + poměr) přepočítá jeho
 * vzorcem (`_canvas_dimensions`, nodes.py), jinak vezme width/height z grafu.
 */
object RozliseniGrafu {

    /** Rozpočet plochy štítků balíku (`RESOLUTION_MEGAPIXELS`). */
    private val MPX = mapOf(
        "360P" to 0.2, "416P" to 0.3, "480P" to 0.4, "540P" to 0.5, "640P" to 0.7, "720P" to 0.9,
        "768P" to 1.0, "832P" to 1.2, "928P" to 1.5, "1024P" to 1.8, "1080P" to 2.0,
    )
    private val POMERY = mapOf(
        "1:1" to (1 to 1), "2:3" to (2 to 3), "3:2" to (3 to 2), "3:4" to (3 to 4), "4:3" to (4 to 3),
        "9:16" to (9 to 16), "16:9" to (16 to 9), "21:9" to (21 to 9),
    )

    private fun zarovnej(v: Double) = maxOf(32, (v / 32.0).roundToInt() * 32)

    /** Štítek balíku → px, přesně jako balík. */
    fun zeStitku(stitek: String, pomer: String, w: Int = 0, h: Int = 0): Pair<Int, Int>? {
        if (stitek == "custom" || pomer == "Custom") return if (w > 0 && h > 0) zarovnej(w.toDouble()) to zarovnej(h.toDouble()) else null
        val mp = MPX[stitek] ?: return null
        val (rw, rh) = POMERY[pomer] ?: (16 to 9)
        val k = sqrt(mp * 1024 * 1024 / (rw * rh))
        return zarovnej(rw * k) to zarovnej(rh * k)
    }

    /** Rozměry z grafu, nebo null, když v něm žádné nejsou. */
    fun zGrafu(wf: JSONObject): Pair<Int, Int>? {
        var nejlepsi: Pair<Int, Int>? = null
        for (id in wf.keys()) {
            val i = wf.optJSONObject(id)?.optJSONObject("inputs") ?: continue
            val stitek = i.opt("resolution") as? String
            val pomer = i.opt("aspect_ratio") as? String
            val w = i.opt("width") as? Int ?: 0
            val h = i.opt("height") as? Int ?: 0
            // Štítek balíku má přednost — width/height tam bývají jen výchozí hodnoty uzlu.
            if (stitek != null && pomer != null) {
                zeStitku(stitek, pomer, w, h)?.let { return it }
            }
            // Jinak největší plátno v grafu (u dvou průchodů je to výsledné).
            if (w > 0 && h > 0 && (nejlepsi == null || w * h > nejlepsi.first * nejlepsi.second)) nejlepsi = w to h
        }
        return nejlepsi
    }

    fun text(wf: JSONObject): String = zGrafu(wf)?.let { "${it.first}×${it.second}" }.orEmpty()
}
