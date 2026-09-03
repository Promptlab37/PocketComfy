package cz.promptlab.h3video.data

import android.graphics.Bitmap
import androidx.compose.runtime.Immutable
import cz.promptlab.h3video.comfy.LongVideoBuilder
import java.io.File

/** Odkud dlouhé video začíná. */
enum class LongStart(private val nazevCs: String, private val popisCs: String) {
    EXISTING_VIDEO(
        "Navázat na video",
        "Vezme hotové video a plynule na jeho konec naváže další úseky"
    ),
    GENERATED(
        "Začít od nuly",
        "První záběr vznikne z popisu a další úseky na něj navážou"
    );

    val nazev: String get() = t(nazevCs)
    val popis: String get() = t(popisCs)
}

/**
 * Jeden úsek dlouhého videa. Vlastní zadání, délka i LoRA — přesně to,
 * kvůli čemu má smysl skládat video po úsecích a ne ho generovat najednou.
 */
@Immutable
data class LongUsek(
    val key: Int,
    val prompt: String = "",
    val seconds: Float = 7f,
    /** Nepovinná LoRA jen pro tenhle úsek (prázdné = jede se na společné). */
    val lora: String = "",
    val loraSila: Float = 1f,
)

/**
 * Karta **Dlouhé video**: až šest navazujících úseků v jednom běhu.
 *
 * Proti kartě All in One → *Prodloužit*, která umí jedno navázání na běh,
 * a proti *Časové ose*, kde je každý segment samostatná úloha, tady jde
 * všechno naráz a úseky si předávají kontext přímo v latentu — mezi nimi
 * proto není vidět sešívání.
 */
@Immutable
data class LongScene(
    val zacatek: LongStart = LongStart.EXISTING_VIDEO,
    /** Navázání: video z telefonu, na které se navazuje. */
    val sourceVideo: File? = null,
    val sourceThumb: Bitmap? = null,
    /** Začátek od nuly: popis a délka prvního záběru. */
    val startPrompt: String = "",
    val startSeconds: Float = 7f,
    /**
     * Rychlý začátek: první záběr se spočítá na pětině plochy, latent se
     * neuronově zvětší na cílové rozlišení a dojede se krátký doostřovací
     * průchod. Ušetří většinu času prvního záběru.
     *
     * Platí JEN pro vygenerovaný začátek. Na navazující úseky se to použít
     * nedá — ty jedou přes maskovaný kontext, který je postavený na jednom
     * rozlišení, a Aitrepreneur to ve V3 taky nekombinuje.
     */
    val rychlyZacatek: Boolean = false,
    /** Reference, které drží podobu napříč všemi úseky. */
    val refs: List<AioSlot> = emptyList(),
    /** LoRA společná všem úsekům. */
    val spolecnaLora: String = "",
    val spolecnaLoraSila: Float = 1f,
    val useky: List<LongUsek> = listOf(LongUsek(key = 1)),
) {
    /** Úseky, které mají zadání — prázdné se do grafu neposílají. */
    val aktivniUseky: List<LongUsek> get() = useky.filter { it.prompt.isNotBlank() }

    val canAddUsek: Boolean get() = useky.size < LongVideoBuilder.MAX_USEKU

    val refsWithImage: List<AioSlot> get() = refs.filter { it.image != null }

    val uploadImages: List<File> get() = refsWithImage.mapNotNull { it.image }

    val uploadVideo: File?
        get() = if (zacatek == LongStart.EXISTING_VIDEO) sourceVideo else null

    /**
     * Odhad výsledné délky. Každý úsek si ubere chráněný začátek, kterým
     * navazuje na předchozí — ten se v hotovém videu neobjeví dvakrát.
     */
    val odhadSekund: Float
        get() {
            val kontext = LongVideoBuilder.CONTEXT_FRAMES / LongVideoBuilder.FPS.toFloat()
            val zaklad = if (zacatek == LongStart.GENERATED) startSeconds else 0f
            return zaklad + aktivniUseky.sumOf { (it.seconds - kontext).toDouble() }.toFloat()
        }
}

/** Co kartě chybí, než se dá spustit. */
fun longProblem(s: LongScene): String? = when {
    s.zacatek == LongStart.EXISTING_VIDEO && s.sourceVideo == null ->
        t("Vyber video, na které se má navázat.")
    s.zacatek == LongStart.GENERATED && s.startPrompt.isBlank() ->
        t("Napiš, co má být v prvním záběru.")
    s.aktivniUseky.isEmpty() -> t("Vyplň zadání aspoň u jednoho úseku.")
    else -> null
}

/** Upozornění, která nebrání spuštění. */
fun longHints(s: LongScene): List<String> {
    val out = mutableListOf<String>()
    val n = s.aktivniUseky.size
    if (n >= 4) {
        out += t(
            "Úseků je %d — každý je vlastní vzorkování, takže běh potrvá zhruba %d× dýl než jedno video."
        ).format(n, n)
    }
    if (s.useky.size > n) {
        out += t("Úseky bez zadání se přeskočí — do videa se nedostanou.")
    }
    if (s.zacatek == LongStart.EXISTING_VIDEO && s.refsWithImage.isEmpty()) {
        out += t(
            "Bez referencí drží podobu jen kontext z videa. U delších řetězů se postava " +
                "postupně rozjíždí — pomůže přidat její fotku."
        )
    }
    return out
}
