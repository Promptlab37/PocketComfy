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
    /** Čím se fotka zvedne — pomalý SeedVR2, nebo rychlé DLSS 5. */
    val metoda: UpscaleMetoda = UpscaleMetoda.SEEDVR2,
    /** DLSS: kolikrát se strana zvětší (1x = jen doostření bez zvětšení). */
    val dlssNasobek: String = "1x",
    /** DLSS: povaha neuronového průchodu. */
    val dlssStyl: DlssStyl = DlssStyl.PRIROZENY,
    /** DLSS: síla průchodu; nad 1.0 už runtime nic dalšího nepřidá. */
    val dlssSila: Float = 1f,
    /** DLSS: rekonstruovat pleť (zapíná automatickou masku kůže). */
    val dlssPlet: Boolean = true,
) {
    val uploadImages: List<File> get() = listOfNotNull(source)

    /** Kolik dlaždic na stranu — hrubý odhad výsledné velikosti je N × 3200 px. */
    val tilesPerSide: Int get() = grid.substringBefore('x').toIntOrNull() ?: 2

    companion object {
        /** Mřížky, které umí uzel ImageTileSplit. */
        val GRIDS = listOf("2x2", "3x3", "4x4")

        /** Míry zvětšení, které umí DLSS (1,724× vynecháno — mate a nic navíc nedá). */
        val DLSS_NASOBKY = listOf("1x", "1.5x", "2x", "3x")
    }
}

/** Dvě cesty karty Zvětšit. Každá je na něco jiného, nejde o kvalitativní pořadí. */
enum class UpscaleMetoda(val stitek: String, val popis: String) {
    SEEDVR2(
        "SeedVR2 (gigapixel)",
        "Difuzní model dokreslí detaily, které v předloze nejsou. Minuty až desítky minut."
    ),
    DLSS(
        "DLSS 5 (rychlé)",
        "NVIDIA Neural Rendering rekonstruuje, co ve fotce je. Sekundy, ale nic si nevymýšlí."
    ),
}

/** Styl neuronového průchodu tak, jak ho čeká uzel `DLSS5Settings`. */
enum class DlssStyl(val uzel: String, val stitek: String) {
    VYCHOZI("Default", "Výchozí"),
    PRIROZENY("Natural", "Přirozený"),
    FILMOVY("Cinematic", "Filmový"),
}

/** Co kartě chybí, než se dá spustit. */
fun upscaleProblem(s: UpscaleScene): String? =
    if (s.source == null) t("Vyber fotku, kterou chceš zvětšit.") else null

/** Upozornění, která nebrání spuštění. */
fun upscaleHints(s: UpscaleScene): List<String> {
    val out = mutableListOf<String>()
    if (s.metoda == UpscaleMetoda.DLSS) {
        if (s.dlssNasobek == "3x") {
            out += t(
                "Zvětšení 3× je „Ultra Performance\" — DLSS má na rekonstrukci nejmíň " +
                    "podkladu a výsledek bývá měkčí než při 2×."
            )
        }
        return out
    }
    if (s.tilesPerSide >= 3) {
        out += "Mřížka ${s.grid} znamená ${s.tilesPerSide * s.tilesPerSide} dlaždic — " +
            "výsledek kolem ${s.tilesPerSide * 3} tisíc pixelů, ale poběží to " +
            "násobně déle než 2×2."
    }
    return out
}
