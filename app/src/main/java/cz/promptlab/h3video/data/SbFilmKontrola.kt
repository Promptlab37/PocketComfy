package cz.promptlab.h3video.data

/**
 * Kontrola přečteného plánu před psaním promptů (5.16).
 *
 * „Připravit film“ po čtení rovnou píše prompty. U storyboardu s texty ale
 * repliky čte model z obrázku a špatně přečtená replika by ve videu zazněla
 * nahlas. Tahle kontrola běží v telefonu (žádný další model) a zastaví
 * přípravu jen tehdy, když čtení vypadá podezřele — jinak se jede dál
 * (kritici 30. 9. 2026). Do promptů nic nepřidává.
 */
data class SbNalez(
    /** Číslo panelu, 0 = týká se celého plánu. */
    val cislo: Int,
    val text: String,
)

object SbFilmKontrola {

    /** Znaky, které v replice dávají smysl; cokoli jiného = model četl nesmysl. */
    private val PODIVNE = Regex("""[^\p{L}\p{N}\s.,!?…:;'"„“”‚‘’«»()\-–—/%&+*]""")

    /**
     * Mám storyboard: celé čtení vs. čtení po řádcích, mřížka, podivné znaky,
     * délka repliky. [radky] = repliky z čtení po řádcích (číslo → text, "" =
     * panel bez repliky); prázdná mapa, když se po řádcích nečetlo.
     */
    fun storyboard(
        cele: List<SbPrecteny>,
        radky: Map<Int, String>,
        radku: Int?,
        sloupcu: Int?,
        poRadcich: Boolean,
        panely: List<SbPanel>,
    ): List<SbNalez> {
        val out = mutableListOf<SbNalez>()
        if (radku != null && sloupcu != null && radku > 0 && sloupcu > 0) {
            val n = cele.size
            if (n > radku * sloupcu || n <= (radku - 1) * sloupcu) {
                out += SbNalez(0, t("Přečteno %d panelů, mřížka je %d × %d.").format(n, radku, sloupcu))
            }
        }
        val sousedni = { c: Int -> listOf(c - 1, c + 1).mapNotNull { radky[it] }.flatMap { SbFilmPrepis.repliky(it) }.map { norm(it.second) } }
        cele.forEach { p ->
            val zCelku = SbFilmPrepis.repliky(p.repliky)
            if (zCelku.isEmpty() || !poRadcich) return@forEach
            val r = radky[p.cislo]
            when {
                // Řádek panel vůbec nevypsal — replika z celku nemá oporu.
                r == null -> out += SbNalez(p.cislo, t("Replika neověřena"))
                // Řádek říká „bez repliky“ a replika není ani u souseda (to by byla zdvojená).
                r.isBlank() && zCelku.any { norm(it.second) !in sousedni(p.cislo) } ->
                    out += SbNalez(p.cislo, t("Replika nejistá"))
            }
        }
        panely.forEach { p ->
            val repl = SbFilmPrepis.repliky(p.repliky)
            if (repl.any { PODIVNE.containsMatchIn(it.second) || it.second.contains('�') })
                out += SbNalez(p.cislo, t("Replika má podivné znaky"))
            val rec = SbFilmPlan.delkaReci(p.repliky)
            if (rec != null && rec > SbFilmPlan.MAX_PANEL_S)
                out += SbNalez(p.cislo, t("Replika je na panel moc dlouhá"))
        }
        return out.distinct()
    }

    /** Storyboard + scénář: jiný počet panelů v obrázku než oken ve scénáři. */
    fun scenar(panelyObrazku: Int, oken: Int): List<SbNalez> =
        if (panelyObrazku > 0 && oken > 0 && panelyObrazku != oken)
            listOf(SbNalez(0, t("Scénář má %d oken, storyboard %d panelů.").format(oken, panelyObrazku)))
        else emptyList()

    private fun norm(t: String) = t.lowercase().replace(Regex("""[„“”"«»‚‘’'.,!?…]"""), "").replace(Regex("""\s+"""), " ").trim()
}
