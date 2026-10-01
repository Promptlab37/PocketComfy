package cz.promptlab.h3video.data

/**
 * Odhad času u akcí karty Film ze storyboardu, které se skládají z víc běhů
 * na serveru (uživatel 29. 9. 2026, po několikáté: „stále to neukazuje
 * reálný čas“).
 *
 * Proč to dřív nesedělo (ověřeno):
 *  - čtení = celý obrázek + každý řádek mřížky zvlášť, příprava promptů =
 *    úsek po úseku; odhad znal jen právě běžící běh a u dalšího začal znovu,
 *  - celé čtení a čtení řádku měly stejný klíč minulé délky (17 s × 8 s),
 *  - balík přepisovače hlásí „progress“ i při nahrávání modelu (max = 1000),
 *    appka to brala jako psaní,
 *  - studený model (po generování videa) trvá 5× déle: celé čtení 16–19 s
 *    teplé, 93–99 s studené (historie serveru).
 *
 * Tady se proto počítá celá akce: seznam kroků, každý s naměřenou délkou
 * zvlášť pro teplý a studený model. Návrh prošel kritikem.
 */
enum class TypKroku(
    /** Výchozí délky v s (medián z historie serveru 29. 9. 2026). */
    val teplyS: Double,
    val studenyS: Double,
    /** Hlásí server skutečné tokeny, podle kterých jde odhad zpřesnit? */
    val tokeny: Boolean,
) {
    CTENI_CELE(17.0, 96.0, false),
    CTENI_RADEK(8.5, 8.5, false),
    // Tokeny při psaní nehlásí: zachyceno 29. 9. 2026 — průběh jen u popisu
    // obrázku (max 1024) a jedna zpráva po nahrání modelu (1000), psaní mlčí.
    PREPIS_USEKU(57.0, 117.0, false),
    NAVRH(20.0, 20.0, false),
    /**
     * Odvázaný vylepšovač (llama.cpp). Uzel během psaní hlásí jen „obrázek
     * 1 z 1“, žádné tokeny — appka to brala jako psaní a čas skákal
     * (uživatel 29. 9. 2026). Změřeno: bez fotky 11 s, s fotkou 28 s,
     * s fotkou po videu 77 s.
     */
    ODVAZANY(12.0, 60.0, false),
    ODVAZANY_FOTO(28.0, 77.0, false),
    /** Vylepšovač MiniMax (Universal Writer): při psaní taky mlčí, ~50 s / 117 s studený. */
    VYLEPSENI_H3(57.0, 117.0, false),
    /**
     * Vylepšovač MiniMax PromptWriter 8B (All in One, 3 kroky, Long MM) — llama.cpp
     * ~35 s, studený až ~90 s (paměť „prepisovac-pomaly-fast-disk“). Dřív bez typu,
     * takže karta ukázala jen kolečko bez odhadu (uživatel 1. 10. 2026).
     */
    VYLEPSENI_MINIMAX(35.0, 90.0, false),
    /** Qwen 2.1 PE (`TextGenerate`, karta Obrázek): ~3,5 tokenu/s, změřeno 118–150 s. */
    QWEN_PE(120.0, 150.0, false),
    ;

    companion object {
        /**
         * Samostatný přepis, který průběh psaní nehlásí → odhad podle času.
         * null = přepisovač hlásí tokeny (8B PromptWriter), zůstává starý odhad.
         */
        fun proGraf(wf: org.json.JSONObject): TypKroku? {
            val tridy = wf.keys().asSequence().mapNotNull { wf.optJSONObject(it)?.optString("class_type") }.toSet()
            val sFotkou = "LoadImage" in tridy
            return when {
                "llama_cpp_instruct_adv" in tridy -> if (sFotkou) ODVAZANY_FOTO else ODVAZANY
                "MiniMaxH3UniversalWriter" in tridy -> VYLEPSENI_H3
                "MiniMaxH3PromptWriter8B" in tridy -> VYLEPSENI_MINIMAX
                "TextGenerate" in tridy || "TextGenerateLTX2Prompt" in tridy -> QWEN_PE
                else -> null
            }
        }
    }
}

data class KrokAkce(val typ: TypKroku, val studeny: Boolean = false)

/** Naučené délky kroku (s). */
data class Ocekavani(val teply: Double, val studeny: Double)

object OdhadAkce {

    /** Bar se zastaví tady — do konce ho dotáhne až výsledek. */
    const val STROP_PODILU = 0.97f

    data class Vysledek(
        /** Zbývající sekundy celé akce; null = krok přetáhl i studený odhad. */
        val zbyvaS: Long?,
        /** Hotová část 0–[STROP_PODILU], nikdy menší než minule. */
        val podil: Float,
    )

    /**
     * @param kroky plán akce
     * @param casy naučené délky podle typu kroku
     * @param k index právě běžícího kroku
     * @param vKrokuS kolik sekund krok běží na serveru; null = ještě čeká ve frontě
     * @param ubehloS kolik sekund běží celá akce
     * @param tokenZbyvaS živý odhad z tokenů pro tenhle krok (jen [TypKroku.tokeny])
     * @param predchoziPodil minulá hodnota baru — nesmí couvnout
     */
    fun spocitej(
        kroky: List<KrokAkce>,
        casy: (TypKroku) -> Ocekavani,
        k: Int,
        vKrokuS: Double?,
        ubehloS: Double,
        tokenZbyvaS: Double? = null,
        predchoziPodil: Float = 0f,
    ): Vysledek {
        if (kroky.isEmpty() || k !in kroky.indices) return Vysledek(null, predchoziPodil)
        fun delka(krok: KrokAkce): Double = casy(krok.typ).let { if (krok.studeny) it.studeny else it.teply }

        val ted = kroky[k]
        val o = casy(ted.typ)
        val zbytek = kroky.drop(k + 1).sumOf { delka(it) }
        val tento: Double? = when {
            vKrokuS == null -> delka(ted)
            tokenZbyvaS != null -> tokenZbyvaS
            // Krok, který se protáhl, dál nepřidává čas: zbývá jen to, co je po něm.
            // Do 5.58 se teplý krok po 1,5× délky přepnul na studený odhad a čas
            // skočil z nuly zpět na minuty — úsek 2 Příšery se psal 87 s místo 57 s,
            // byl jen delší, ne studený (uživatel 1. 10. 2026). Studenost se určuje
            // jen předem (co běželo na serveru), ne uprostřed kroku.
            else -> maxOf(0.0, delka(ted) - vKrokuS)
        }
        if (tento == null) return Vysledek(null, predchoziPodil)
        val zbyva = tento + zbytek
        // Poslední krok přetáhl: žádné číslo, jen uběhlý čas; bar stojí.
        if (zbyva <= 0.0) return Vysledek(null, predchoziPodil)
        val podil = if (ubehloS + zbyva > 0) (ubehloS / (ubehloS + zbyva)).toFloat() else 0f
        return Vysledek(
            Math.round(zbyva),
            maxOf(predchoziPodil, podil.coerceIn(0f, STROP_PODILU)),
        )
    }

    /**
     * Nová naučená délka: průměr se starou, skok nejvýš 3× nahoru či dolů.
     * Vrací (studený?, hodnota) — teplý krok, který trval přes 2× teplé
     * délky, patří do studeného koše (studenost se odhadla špatně).
     */
    fun nauc(o: Ocekavani, studeny: Boolean, trvaniS: Double): Pair<Boolean, Double> {
        val doStudeneho = studeny || trvaniS > 2 * o.teply
        val stara = if (doStudeneho) o.studeny else o.teply
        val x = trvaniS.coerceIn(stara / 3, stara * 3)
        return doStudeneho to (stara + x) / 2
    }

    /**
     * Třídy uzlů textových úloh (čtení, přepis, návrh, překlad). Hlídá test:
     * každý textový graf appky musí mít třídy jen odsud.
     */
    val TEXTOVE_TRIDY = setOf(
        "LoadImage", "PreviewAny", "ImageScaleToMaxDimension",
        "MiniMaxH3ReferenceCaption", "MiniMaxH3UniversalWriter", "MiniMaxH3RewriterOptions",
        "MiniMaxH3PromptWriter8B",
        "llama_cpp_model_loader", "llama_cpp_instruct_adv", "llama_cpp_parameters",
    )

    /**
     * Bude model studený? Ano, když poslední hotová úloha na serveru nebyla
     * textová (video vytlačí jazykový model z paměti), když historie je
     * prázdná (restart ComfyUI) nebo nejde přečíst.
     *
     * @param historie odpověď `/history?max_items=1` (null = chyba)
     */
    fun jeStudeny(historie: org.json.JSONObject?): Boolean {
        if (historie == null || historie.length() == 0) return true
        var nejnovejsi: org.json.JSONObject? = null
        var cislo = Double.NEGATIVE_INFINITY
        for (id in historie.keys()) {
            val e = historie.optJSONObject(id) ?: continue
            val c = e.optJSONArray("prompt")?.optDouble(0) ?: continue
            if (c > cislo) { cislo = c; nejnovejsi = e }
        }
        val graf = nejnovejsi?.optJSONArray("prompt")?.optJSONObject(2) ?: return true
        val tridy = graf.keys().asSequence()
            .mapNotNull { graf.optJSONObject(it)?.optString("class_type") }.toSet()
        return tridy.isEmpty() || !TEXTOVE_TRIDY.containsAll(tridy)
    }
}
