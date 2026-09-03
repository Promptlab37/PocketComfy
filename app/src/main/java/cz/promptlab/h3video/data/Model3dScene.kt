package cz.promptlab.h3video.data

import android.graphics.Bitmap
import androidx.compose.runtime.Immutable
import java.io.File

/**
 * Jak se má hotová síť otexturovat.
 *
 * Rozdíl není v tom, jak model vypadá zblízka, ale v tom, co s ním jde dělat
 * dál: barvy ve vrcholech unese kdeco, ale v editoru se s nimi nedá pracovat.
 * PBR sada (base color, kov, drsnost, normály, stínění) je to, co čekají
 * herní enginy — jen se na ni čeká podstatně dýl.
 */
enum class Model3dKvalita(
    private val nazevCs: String,
    private val popisCs: String,
    /** Rozlišení remeshe (výchozí uzlu je 512). */
    val remesh: Int,
    /** Kolik iterací vyhlazení sítě (výchozí uzlu je 0). */
    val vyhlazeni: Int,
    /** Cílový počet ploch po decimaci (výchozí uzlu je 200 000). */
    val plochy: Int,
    /** Strana normálové mapy (výchozí uzlu je 1024). */
    val normaly: Int,
) {
    RYCHLA(
        "Rychlá",
        "Barvy zapečené do vrcholů sítě. Žádné rozbalování UV ani pečení map — " +
            "hotové řádově dřív, ale v editoru se s tím nedá pracovat.",
        remesh = 512, vyhlazeni = 0, plochy = 200_000, normaly = 1024,
    ),
    PBR(
        "Plné textury",
        "Rozbalí UV a upeče base color, kov, drsnost, normály i stínění. " +
            "Hodnoty jsou ty, které mají uzly jako výchozí.",
        remesh = 512, vyhlazeni = 0, plochy = 200_000, normaly = 1024,
    ),
    MAXIMALNI(
        "Maximální",
        "Jako plné textury, ale s hodnotami z ukázkové šablony ComfyUI: hustší " +
            "remesh, 700 tisíc ploch, vyhlazení a normály 2048. Nejdelší běh.",
        remesh = 768, vyhlazeni = 20, plochy = 700_000, normaly = 2048,
    );

    /** Peče se PBR sada, nebo se jen obarví vrcholy? */
    val jePbr: Boolean get() = this != RYCHLA

    val nazev: String get() = t(nazevCs)
    val popis: String get() = t(popisCs)
}

/**
 * Karta **3D model** — z jedné fotky vznikne síť s texturami (TRELLIS.2).
 *
 * Jede na nativní podpoře v ComfyUI (od 0.34), žádný custom node. Pozadí
 * z fotky odstraní BiRefNet, takže se nahrává obyčejná fotka z telefonu —
 * alfa kanál mít nemusí. Výstupem je soubor **.glb**.
 */
@Immutable
data class Model3dScene(
    val source: File? = null,
    val thumb: Bitmap? = null,
    val kvalita: Model3dKvalita = Model3dKvalita.PBR,
    /**
     * Rozlišení voxelové mřížky tvaru. Vyšší = víc detailu i VRAM.
     *
     * Výchozí je 1024, stejně jako má uzel. 1536 z ukázkové šablony ComfyUI
     * je paměťový vrchol celého řetězu — na 16GB kartě spadne převod tvaru
     * na síť (`VaeDecodeShapeTrellis`), jakmile část paměti drží něco jiného.
     */
    val detail: Int = 1024,
    /** Strana zapečené textury. */
    val textura: Int = 2048,
) {
    val uploadImages: List<File> get() = listOfNotNull(source)

    companion object {
        /**
         * Uzel bere 1024–2048 po 128, ale nabízí se jen to, co na 16GB kartě
         * doopravdy projde. 1536 spadlo 3. 9. 2026 přímo na převodu tvaru
         * na síť (`VaeDecodeShapeTrellis`) — a to je paměťový vrchol běhu,
         * takže vyšší hodnoty nemá smysl vůbec ukazovat.
         */
        val DETAILY = listOf(1024)

        /** Strany textury, které se na 16GB kartě upečou. */
        val TEXTURY = listOf(1024, 2048)
    }
}

/** Co kartě chybí, než se dá spustit. */
fun model3dProblem(s: Model3dScene): String? =
    if (s.source == null) t("Vyber fotku předmětu, ze které se má model udělat.") else null

/** Upozornění, která nebrání spuštění. */
fun model3dHints(s: Model3dScene): List<String> {
    val out = mutableListOf<String>()
    out += t(
        "Nejlíp to funguje na JEDEN předmět na klidném pozadí. Skupinu věcí nebo " +
            "celou scénu model rozumně nerozdělí."
    )
    if (s.kvalita.jePbr) {
        out += t(
            "Po čtyřech průchodech modelu přijde ještě remesh, decimace, rozbalení UV " +
                "a pečení map. Tahle část jede z velké části na procesoru, takže ji " +
                "rychlejší grafika nezkrátí — počítej v desítkách minut."
        )
    }
    if (s.kvalita == Model3dKvalita.MAXIMALNI) {
        out += t(
            "Maximální kvalita dělá 700 tisíc ploch místo 200 tisíc a normály 2048 " +
                "místo 1024. Rozdíl na výsledku bývá menší než rozdíl v čase."
        )
    }
    out += t(
        "Než to spustíš, zavři hry a další věci, co berou grafiku — model si " +
            "bere skoro celou paměť karty."
    )
    return out
}
