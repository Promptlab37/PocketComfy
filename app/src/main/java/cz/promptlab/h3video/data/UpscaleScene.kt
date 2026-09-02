package cz.promptlab.h3video.data

import android.graphics.Bitmap
import androidx.compose.runtime.Immutable
import java.io.File

/**
 * Karta **Zvětšit** — uživatelovo vyladěné SeedVR2 „gigapixel" workflow.
 *
 * Předloha je 1:1 kopie jeho `Comfyui WF_seedvr2-unlimited-resolution-
 * gigapixel-upscale_PROMPTLAB.json` (dlaždice → SeedVR2 na 3200 px každou →
 * slepení s prolnutím). Appka dosazuje jen tři věci: obrázek, mřížku dlaždic
 * a seed. Všechno ostatní — modely, překryvy, prolnutí — zůstává z jeho
 * exportu a nesmí se měnit.
 *
 * Vstupní fotka se ukládá BEZ zmenšení a překódování (na rozdíl od referencí
 * u videa) — zmenšovat vstup upscaleru by byl protimluv.
 */
@Immutable
data class UpscaleScene(
    val source: File? = null,
    val thumb: Bitmap? = null,
    /** Mřížka dlaždic; každá dlaždice se zvětší na 3200 px. */
    val grid: String = "2x2",
) {
    val uploadImages: List<File> get() = listOfNotNull(source)

    /** Kolik dlaždic na stranu — hrubý odhad výsledné velikosti je N × 3200 px. */
    val tilesPerSide: Int get() = grid.substringBefore('x').toIntOrNull() ?: 2

    companion object {
        /** Mřížky, které umí uzel ImageTileSplit. */
        val GRIDS = listOf("2x2", "3x3", "4x4")
    }
}

/** Co kartě chybí, než se dá spustit. */
fun upscaleProblem(s: UpscaleScene): String? =
    if (s.source == null) t("Vyber fotku, kterou chceš zvětšit.") else null

/** Upozornění, která nebrání spuštění. */
fun upscaleHints(s: UpscaleScene): List<String> {
    val out = mutableListOf<String>()
    if (s.tilesPerSide >= 3) {
        out += "Mřížka ${s.grid} znamená ${s.tilesPerSide * s.tilesPerSide} dlaždic — " +
            "výsledek kolem ${s.tilesPerSide * 3} tisíc pixelů, ale poběží to " +
            "násobně déle než 2×2."
    }
    return out
}
