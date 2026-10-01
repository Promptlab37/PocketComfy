package cz.promptlab.h3video.data

/**
 * Hlasování mezi čteními téhož panelu (5.67). Vidoucí model čte greedy: stejný výřez dá vždy stejnou
 * chybu, jiný výřez nebo měřítko chybuje jinde (pokus 1. 10. 2026 — „vyřešte“ / „vyřešíte“,
 * „přísný“ / „příšný“). Přijme se čtení, na kterém se shodnou aspoň dvě.
 */
object SbShoda {

    /**
     * Kandidát, na kterém se shoduje většina (aspoň dva), jinak null. Jediný kandidát vyhrává sám.
     * Repliky (`Kdo: „…“; …`) se porovnávají po mluvčích a textu, ostatní jako text.
     */
    fun vyber(kandidati: List<String>): String? {
        if (kandidati.isEmpty()) return null
        if (kandidati.size == 1) return kandidati[0]
        val klice = kandidati.map { klic(it) }
        for (i in kandidati.indices) {
            if (klice.count { it == klice[i] } >= 2) return kandidati[i]
        }
        return null
    }

    /**
     * Slova, ve kterých se neprázdná čtení repliky liší („Rozsvíť / Rozsviť“), null = shoda.
     * Porovnává se jen při stejném počtu slov; interpunkce a uvozovky se nepočítají.
     * Model hovorové tvary rád „spisovně opraví“ (celej → celý) — takové slovo musí vidět člověk.
     */
    fun sporna(kandidati: List<String>): String? {
        val slova = kandidati.filter { it.isNotBlank() }.map { k ->
            val repl = SbFilmPrepis.repliky(k)
            (if (repl.isNotEmpty()) repl.joinToString(" ") { it.second } else k).let { text(it) }
                .split(' ').map { it.trim('.', ',', '!', '?', ';', ':', '…', '-', '–', '—') }.filter { it.isNotEmpty() }
        }.filter { it.isNotEmpty() }
        if (slova.size < 2) return null
        val delka = slova.groupingBy { it.size }.eachCount().maxByOrNull { it.value }!!.key
        val stejne = slova.filter { it.size == delka }
        if (stejne.size < 2) return null
        val rozdily = (0 until delka).mapNotNull { i ->
            val varianty = stejne.map { it[i] }.distinct()
            if (varianty.size > 1 && varianty.map { it.lowercase() }.distinct().size > 1) varianty.joinToString(" / ") else null
        }
        return rozdily.takeIf { it.isNotEmpty() }?.joinToString("; ")
    }

    /** Porovnávací tvar: uvozovky, mezery, trojtečka a apostrof místo háčku sjednocené. */
    fun klic(t: String): String {
        val repl = SbFilmPrepis.repliky(t)
        return if (repl.isNotEmpty()) repl.joinToString(" ; ") { (kdo, co) -> kdo.trim().lowercase() + ": " + text(co) }
        else text(t).trim('(', ')', ' ').trimEnd('.').lowercase()
    }

    private fun text(t: String) = SbFilmPrepis.opravHacky(t).replace("…", "...")
        .replace(Regex("""[„“”"«»]"""), "").replace('‘', '\'').replace('’', '\'')
        .replace(Regex("""\s+"""), " ").trim()
}
