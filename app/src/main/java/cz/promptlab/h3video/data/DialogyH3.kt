package cz.promptlab.h3video.data

/**
 * Mluvené repliky v zadání pro vylepšovač MiniMax H3.
 *
 * Přepisovač má v šabloně „dialogy zachovej doslova v původním jazyce“, ale
 * 8B model to drží jen u replik v uvozovkách uvnitř věty. Dialog napsaný
 * jako scénář (`Novák: Kdo vás poslal?` — tak ho píše ChatGPT) 28. 9. 2026
 * celý zahodil („she speaks“) a H3 si pak vymyslel anglickou řeč.
 *
 * Appka proto repliky z textu vytáhne sama a přepisovači je vyjmenuje
 * s formátem z oficiální příručky: replika jen uvnitř `<d>[Jazyk] …</d>`,
 * mluvčí se stálým ID `(S1)`, `(S2)` podle pořadí, jazyk původní.
 *
 * **Jen u scénáře.** Repliky v uvozovkách uvnitř vět přepisovač zachová
 * sám (ověřeno); s pokynem je naopak vynechal — seznam bez jmen mluvčích
 * ho zmátl. Ověřeno živými běhy 28. 9. 2026: scénář s pokynem 3/3 běhy
 * česky, (S1)/(S2) u prvních replik; uvozovky bez pokynu česky správně.
 */
object DialogyH3 {

    data class Replika(val mluvci: String?, val text: String)

    /** Popisky scénáře, které nejsou postavy („Kamera: pomalý nájezd“). */
    private val NE_POSTAVY = setOf(
        "scéna", "scena", "dialog", "dialogy", "kamera", "styl", "hudba", "zvuk", "zvuky",
        "prostředí", "prostredi", "atmosféra", "atmosfera", "záběr", "zaber", "záběry",
        "poznámka", "poznamka", "délka", "delka", "formát", "format", "osvětlení",
        "osvetleni", "světlo", "svetlo", "nálada", "nalada", "popis", "akce", "postavy",
        "postava", "lokace", "místo", "misto", "čas", "cas", "střih", "strih", "efekt",
        "efekty", "titulky", "text", "prompt", "video", "obraz", "pozadí", "pozadi",
        "shot", "scene", "camera", "style", "music", "sound", "setting", "location",
        "lighting", "mood", "action", "note", "duration", "characters", "character",
        "voiceover", "narrator", "vypravěč",
    )

    private val RADEK_SCENARE =
        Regex("""^\s*[-*•]?\s*([\p{Lu}][\p{L}0-9 .'’-]{0,30}?)\s*(?:\([^)]{0,40}\))?\s*:\s*(.+?)\s*$""")
    private val UVOZOVKY = Regex("""[„“”"»«]([^„“”"»«\n]{2,300})[“”"«»]""")
    /** ě, ř, ů má jen čeština; slovenštinu prozradí ô, ä, ľ, ĺ, ŕ. */
    private val JEN_CESKE = Regex("[ěřůĚŘŮ]")
    private val SLOVENSKE = Regex("[ôäľĺŕÔÄĽĹŔ]")

    /** Repliky ve scénáři i v uvozovkách, v pořadí výskytu. */
    fun najdi(zadani: String): List<Replika> {
        val out = mutableListOf<Replika>()
        zadani.lines().forEach { radek ->
            val m = RADEK_SCENARE.matchEntire(radek)
            if (m != null) {
                val jmeno = m.groupValues[1].trim()
                val text = bezUvozovek(m.groupValues[2])
                if (jmeno.lowercase() !in NE_POSTAVY && jmeno.split(" ").size <= 3 &&
                    text.split(Regex("\\s+")).size >= 1 && text.isNotBlank()
                ) {
                    out += Replika(jmeno, text)
                    return@forEach
                }
            }
            UVOZOVKY.findAll(radek).forEach { q ->
                val text = q.groupValues[1].trim()
                // Jedno slovo v uvozovkách bývá název nebo nápis, ne replika.
                if (text.split(Regex("\\s+")).size >= 2) out += Replika(null, text)
            }
        }
        return out
    }

    private fun bezUvozovek(s: String): String =
        s.trim().trim('„', '“', '”', '"', '»', '«').trim()

    /** Jazykový tag pro `<d>[…]`: češtinu pozná, jinak ho nechá na modelu. */
    fun jazyk(repliky: List<Replika>): String? =
        if (repliky.none { SLOVENSKE.containsMatchIn(it.text) } &&
            repliky.any { JEN_CESKE.containsMatchIn(it.text) }
        ) "Czech" else null

    /**
     * Dovětek pro přepisovač. Prázdný, když zadání žádné repliky nemá —
     * pak se nic nemění.
     */
    fun hlidka(zadani: String): String {
        val repliky = najdi(zadani)
        // Bez scénáře (jen uvozovky ve větách) pokyn škodí — viz hlavička.
        if (repliky.none { it.mluvci != null }) return ""
        val jaz = jazyk(repliky)
        val tag = jaz ?: "Language"
        val mluvci = repliky.mapNotNull { it.mluvci }.distinct()
        val idPodleJmena = mluvci.withIndex().associate { (i, j) -> j to "S${i + 1}" }
        val sb = StringBuilder("\n\n[Spoken dialogue. Write every line below, in this order, ")
        sb.append(if (jaz != null) "word for word in $jaz" else "word for word in its original language")
        sb.append(", inside <d>[$tag] …</d>. Never translate, shorten, merge or drop a line. ")
        sb.append("Only the spoken words go inside <d>. Every speaker gets a stable speaker ID ")
        sb.append("written right after the speaker's description, before the words, every time ")
        sb.append("they speak. ")
        // Konkrétní vzor s první skutečnou replikou: obecný „(S1) says“ model
        // 28. 9. 2026 jednou dodržel a jednou ID vynechal.
        val prvni = repliky.first()
        val prvniId = prvni.mluvci?.let { idPodleJmena[it] } ?: "S1"
        sb.append("Example: The ${prvni.mluvci ?: "speaker"} ($prvniId) says: <d>[$tag] ${prvni.text}</d> ")
        sb.append("Speaker IDs: ")
        sb.append(idPodleJmena.entries.joinToString(", ") { "${it.key} = (${it.value})" })
        sb.append(". ")
        sb.append("Lines, already in the final form — copy each one into the shot where it is spoken:")
        repliky.forEachIndexed { i, r ->
            val id = r.mluvci?.let { idPodleJmena[it] }
            sb.append("\n${i + 1}. ")
            if (id != null) sb.append("${r.mluvci} ($id) says: ")
            sb.append("<d>[$tag] ${r.text}</d>")
        }
        // Závorka na vlastním řádku: přilepená za poslední repliku ji model
        // opsal do <d> („sama.]“, ověřeno 28. 9. 2026).
        sb.append("\nEnd of dialogue.\n]")
        return sb.toString()
    }

    /** Zadání pro vylepšovač MiniMax: text, dovětek proti nápisům a repliky. */
    fun proPrepisovac(zadani: String): String =
        "${zadani.trimEnd().trimEnd('.')}. Do not add any on-screen text or captions unless explicitly requested." +
            hlidka(zadani)
}
