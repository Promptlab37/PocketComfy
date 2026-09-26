package cz.promptlab.h3video.data

/**
 * Pojistka: nahota ani sex u nezletilých. Odblokované modely (přepisovače
 * i generátory) se na pravidlo v systémovém zadání spolehlivě nedrží — při
 * testu 26. 9. 2026 napsal odvázaný přepisovač na „žena je nahá, 15 let"
 * normální svlékací pokyn. Proto to hlídá appka sama, dřív než cokoli odejde
 * na server: zadání, které spojuje nahotu nebo sex s věkem pod 18 let nebo
 * se slovy pro dítě, se nepošle.
 *
 * „Dívka" / „girl" samo o sobě nezletilost neznamená (česky i o dospělé), proto
 * se na ně nereaguje — rozhoduje věk nebo výslovné slovo pro dítě.
 */
object NezletiliPojistka {

    /** Značka, kterou vrací přepisovač, když zadání odmítne (viz ImagePromptBuilder). */
    const val ZNACKA = "REFUSED_MINOR"

    // Pozor na „nahoru" / „nahoře": nahota se pozná jen z celých tvarů.
    // Hranice slova přes \p{L}, ne : na JVM je  jen ASCII a „nahá," by
    // nenašel; tohle funguje stejně v testech i na Androidu.
    private val NAHOTA = Regex(
        """(?<![\p{L}\p{N}])nah(á|é|ý|ou|ých|ým|ými|ota|otě|otu|oty)(?![\p{L}\p{N}])|(?<![\p{L}\p{N}])svlé[kc]|(?<![\p{L}\p{N}])svlek|sex|(?<![\p{L}\p{N}])prs(a|y|ou|ech|ům)?(?![\p{L}\p{N}])|""" +
            """bradav|vagín|vagin|kund|píč|(?<![\p{L}\p{N}])penis|(?<![\p{L}\p{N}])čurák|(?<![\p{L}\p{N}])kokot|varlat|(?<![\p{L}\p{N}])kozy(?![\p{L}\p{N}])|erot|porn|""" +
            """masturb|(?<![\p{L}\p{N}])orál|(?<![\p{L}\p{N}])nude|(?<![\p{L}\p{N}])naked|nsfw|topless|breast|nipple|pussy|vulva|(?<![\p{L}\p{N}])cock|""" +
            """(?<![\p{L}\p{N}])dick|genital|undress""",
        RegexOption.IGNORE_CASE,
    )

    private val VEK = Regex(
        """(?<![0-9])(1[0-7]|[1-9])\s*-?\s*(let|lety|letá|letý|leté|letou|roků|rok|roky|years?|yrs?|yo(?![\p{L}\p{N}])|y\.o\.)""",
        RegexOption.IGNORE_CASE,
    )

    private val DITE = Regex(
        """dítě|(?<![\p{L}\p{N}])dět(i|í|mi)(?![\p{L}\p{N}])|děck|holčičk|holčin|chlapeč|chlapc[ei]|klučin|nezletil|školačk|""" +
            """školák|žačk|puberť|mimin|batol|(?<![\p{L}\p{N}])child|(?<![\p{L}\p{N}])kids?(?![\p{L}\p{N}])|(?<![\p{L}\p{N}])minors?(?![\p{L}\p{N}])|underage|under-age|""" +
            """(?<![\p{L}\p{N}])teen|preteen|schoolgirl|schoolboy|(?<![\p{L}\p{N}])loli|(?<![\p{L}\p{N}])shota|little girl|little boy""",
        RegexOption.IGNORE_CASE,
    )

    /** true = zadání se nesmí poslat. */
    fun zakazano(text: String): Boolean =
        NAHOTA.containsMatchIn(text) && (VEK.containsMatchIn(text) || DITE.containsMatchIn(text))

    val HLASKA: String get() =
        t("Nahota ani sex u nezletilých nejsou dovolené. Zadání se neodeslalo.")
}
