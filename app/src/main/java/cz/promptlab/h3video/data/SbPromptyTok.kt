package cz.promptlab.h3video.data

/**
 * Zadání úseků filmu ze storyboardu bez Androidu (5.67) — appka i test proti serveru pouštějí
 * stejný kód: hlídka pro přepisovač, přepis, úklid odpovědi a kontrola replik.
 */
object SbPromptyTok {

    /** Přepisovač s referencemi (storyboard + fotky postav). */
    fun interface Pisar {
        suspend fun napis(sekundy: Double, zadani: String, hlidka: String, jenTvar: Set<Int>, krok: Int): String
    }

    data class Vysledek(val zadani: List<String>, val nalezy: List<SbNalez>)

    suspend fun napis(s: SbFilmScene, pisar: Pisar, stopy: List<SbDialogy.Stopa>, krokOd: Int): Vysledek {
        val useky = s.useky
        val sp = SbFilmPrepis
        val nalezy = mutableListOf<SbNalez>()
        val zadani = useky.mapIndexed { k, u ->
            val stopyUseku = stopy.filter { it.usek == k }
            val hlidka = sp.hlidka(
                s.uploadImages.size, u, k, useky.size, s.seStoryboardem,
                sp.idMluvcich(s.panely), sp.jazykFilmu(s), s.hlasy,
                predchozi = useky.getOrNull(k - 1)?.panely?.lastOrNull(),
                vzhled = s.vzhled, kontinuita = s.kontinuita,
                vzhledSeMeni = s.zdroj == SbZdroj.SCENAR && SbScenar.vzhledSeMeni(s.vzhled),
                zeScenare = s.zdroj == SbZdroj.SCENAR,
                // Fotky se jmény postav (5.17); bez fotek prázdné → prompt beze změny.
                jmenaFotek = if (s.seStoryboardem && s.postavy.isNotEmpty() && s.postavy.all { s.jmenoFotky(it.soubor) != null })
                    s.postavy.map { s.jmenoFotky(it.soubor)!! } else emptyList(),
                jenTvarFotek = s.postavy.map { it.soubor.absolutePath in s.jenTvar },
                popisyFilmu = s.panely.map { it.popis },
            ).let { sp.hlidkaSNahravkami(it, stopyUseku.map { st -> st.mluvci }.distinct()) }
            // Storyboard je první obrázek, fotky postav za ním.
            val jenTvar = s.postavy.withIndex().filter { it.value.soubor.absolutePath in s.jenTvar }
                .map { it.index + (if (s.seStoryboardem) 1 else 0) }.toSet()
            suspend fun napis(): String = dokonci(pisar.napis(u.sekundy, sp.zadani(s, k, useky.size), hlidka, jenTvar, krokOd + k), s, u)
            // Jeden průchod (5.67): přepisovač čte greedy, nový pokus by vrátil totéž a stál 50–90 s
            // (rada odborníků). Co přísná kontrola po vynucení ještě najde, ukáže se jako nález —
            // nic se nevynechá potichu.
            val prompt = napis()
            SbKontrolaPromptu.zkontroluj(prompt, u).forEach { nalezy += SbNalez(0, t("Úsek %d: %s").format(k + 1, it)) }
            // Nahrané repliky: <Audio N>, audio reuse, partially_copy a hlas z nahrávky (5.64).
            SbDialogy.doplnPrompt(prompt, stopyUseku, sp.idMluvcich(s.panely))
        }
        return Vysledek(zadani, nalezy)
    }

    /** Úklid odpovědi přepisovače pro úsek [u]. */
    fun dokonci(text: String, s: SbFilmScene, u: SbUsek): String {
        val sp = SbFilmPrepis
        // Vymyšlené <Audio>/<Video> a cizí záběry se odstraní hned —
        // opakovaný přepis je nespolehlivě opravoval a trvá dvakrát.
        // Holé „Subject N“ → <Subject N>, pozadí studiové fotky pryč (5.64).
        // Holá replika v záběru do <d> dřív, než úklid smaže „citace“ replik (5.66: replika byla
        // v <d> jen ve shrnutí a úklid ji ze záběru 2 smazal).
        val zabalene = sp.doplnD(sp.opravZnackyAPozadi(text), u, sp.jazykFilmu(s), sp.idMluvcich(s.panely))
        val cisty = sp.opravObrazky(sp.ocistiPrepis(zabalene, u.panely.size), s.uploadImages.size)
        // Se scénářem i vymyšlené značky (<Product>) → <Subject K> (5.22).
        // Se scénářem i replika vždy na začátek záběru (5.32).
        // Replika bez <d> se zabalí (5.60), pak uvozovky mimo <d> pryč — H3 by je vyslovil (5.57).
        val upraveny = if (s.zdroj == SbZdroj.SCENAR) sp.replikaNaZacatek(SbScenar.opravZnacky(cisty)) else cisty
        val hotovy = sp.doplnIdMluvciho(sp.bezDuplicitD(sp.bezUvozovekMimoD(sp.doplnD(upraveny, u, sp.jazykFilmu(s), sp.idMluvcich(s.panely)))), sp.idMluvcich(s.panely))
        // Repliky, časy střihů a počet záběrů přesně podle plánu (5.67) — jeden průchod přepisovače stačí.
        // Jen když kontrola najde chybu: bezchybný výstup jde beze změny, znak po znaku — zamčené nastavení
        // „Storyboard + scénář“ (5.32) i schválené výstupy zůstávají přesně, jak je uživatel schválil.
        if (SbKontrolaPromptu.zkontroluj(hotovy, u).isEmpty()) return hotovy
        return SbVynuceni.vynut(hotovy, u, sp.jazykFilmu(s), sp.idMluvcich(s.panely))
    }
}
