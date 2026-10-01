package cz.promptlab.h3video.data

/**
 * Čtení storyboardu bez Androidu (5.67). Do 5.66 žil celý tok v MainViewModel a dal se ověřit jen
 * na emulátoru, jeden storyboard za několik minut; uživatel pak chyby našel až ve filmu. Teď ho
 * appka i test pouštějí stejně — test proti serveru nad sadou skutečných storyboardů.
 *
 * Postup: 1) celý list (struktura, časy, hlasy, vzhled, hudba, kontinuita), 2) každý řádek panelů
 * zvlášť ve zvětšeném výřezu (opis textu pod panely), 3) hlas a vzhled chybějících mluvčích,
 * 4) překlad nálad, zvuků a děje do angličtiny. [sestav] z toho udělá plán panelů.
 */
object SbCteniTok {

    /** Obrázek storyboardu; souřadnice vždy v rozměrech originálu. */
    interface Obraz {
        val sirka: Int
        val vyska: Int
        /** Soubor tak, jak ho uživatel vybral (posílá se na celé čtení). */
        fun original(): ByteArray
        /** Výřez [x0, x1) × [y0, y1) zvětšený [meritko]× jako PNG. */
        fun vyrezPng(x0: Int, y0: Int, x1: Int, y1: Int, meritko: Float): ByteArray
        /** Políčka z pixelů ([SbPanelyObrazu.najdiBunky]), null = nenalezeno. */
        fun bunky(): List<SbPanelyObrazu.Bunka>?
        /** Výška řádku textu v popiscích ([SbPanelyObrazu.vyskaPisma]), null = nejde změřit. */
        fun vyskaPisma(panely: List<SbPanelyObrazu.Obdelnik>): Float? = null
        /** Pás popisku každého panelu ([SbPanelyObrazu.pasPopisku]), null = nenalezen. */
        fun pasyPopisku(panely: List<SbPanelyObrazu.Obdelnik>): List<SbPanelyObrazu.Obdelnik?> = panely.map { null }
    }

    /** Vidoucí model na serveru. */
    interface Model {
        /** Nahraje obrázek, vrátí jméno na serveru. */
        suspend fun nahraj(png: ByteArray, nazev: String): String
        /** Jedno čtení obrázku [jmeno] s otázkou; [krok] = index kroku v průběhu akce. */
        suspend fun precti(jmeno: String, otazka: String, krok: Int): String
        /**
         * OCR (Tesseract, uzel H3TesseractOCR_v1) oblastí obrázku [jmeno] → text každé oblasti;
         * null = server OCR nemá nebo selhalo (appka pak jede bez něj, jako do 5.66).
         */
        suspend fun ocr(jmeno: String, oblasti: List<SbPanelyObrazu.Obdelnik>, krok: Int): List<String>? = null
    }

    /** Průběh pro UI: kolik řádků se bude číst a vložení kroku navíc (vrací jeho index). */
    interface Prubeh {
        fun radku(pocet: Int) {}
        fun krokNavic(): Int
    }

    data class Vysledek(
        /** Odpověď celého čtení (s doplněnými hlasy a vzhledem). */
        val text: String,
        val repliky: Map<Int, String>,
        val nalady: Map<Int, String>,
        val zvuky: Map<Int, String>,
        val deje: Map<Int, String>,
        val kamery: Map<Int, String>,
        val poRadcich: Boolean,
        /** Nálady a děj, jak je opsalo čtení řádků, ještě před překladem (kontrola a regresní sada). */
        val naladyOpis: Map<Int, String> = emptyMap(),
        val dejeOpis: Map<Int, String> = emptyMap(),
        /** Panely, kde se čtení replik neshodla ani po samostatném výřezu. */
        val nejiste: Set<Int> = emptySet(),
        /** Panely, kde se čtení replik liší v jednotlivých slovech → varianty ke kontrole. */
        val sporne: Map<Int, String> = emptyMap(),
        /** Slova replik opravená podle OCR (číslo panelu → „z → na“). */
        val ocrOpravy: Map<Int, String> = emptyMap(),
    )

    /** Panely z pixelů, když jejich počet sedí na [pocet] přečtený modelem (jako [cz.promptlab.h3video.util.PanelyStoryboardu.podlePoctu]). */
    fun podlePoctu(bunky: List<SbPanelyObrazu.Bunka>?, pocet: Int): List<SbPanelyObrazu.Obdelnik>? {
        val vse = bunky ?: return null
        val plne = vse.filter { !it.prazdna }
        return when (pocet) {
            plne.size -> plne.map { it.obdelnik }
            vse.size -> vse.map { it.obdelnik }
            else -> null
        }
    }

    /**
     * Jak číst řádky. [panelPx] = cílová šířka panelu ve výřezu; [dvaPohledy] = druhé čtení
     * v měřítku [druhePomer] a hlasování (5.67).
     */
    data class Nastaveni(
        val panelPx: Float = 500f,
        // Od zapojení OCR zbytečné (sada 1. 10. 2026: jeden pohled 59/59 a méně falešných poplachů,
        // čtení o třetinu kratší a průběh bez skákání kroků).
        val dvaPohledy: Boolean = false,
        val druhePomer: Float = 0.85f,
        /** Cílová výška řádku textu ve výřezu, když jde písmo změřit (jinak [panelPx]). */
        val pismoPx: Float = 30f,
    )

    /** Jedno čtení řádku rozložené na pole (číslo panelu → text). */
    private class Opis(
        val repliky: Map<Int, String>,
        val nalady: Map<Int, String>,
        val zvuky: Map<Int, String>,
        val deje: Map<Int, String>,
    ) {
        companion object {
            fun z(t: String) = Opis(SbFilmPlan.prectiRepliky(t), SbFilmPlan.prectiNalady(t), SbFilmPlan.prectiZvuky(t), SbFilmPlan.prectiDeje(t))
        }
    }

    suspend fun precti(obraz: Obraz, model: Model, prubeh: Prubeh, nastaveni: Nastaveni = Nastaveni()): Vysledek {
        val jmeno = model.nahraj(obraz.original(), "sbfilm_storyboard.png")
        var prvni = model.precti(jmeno, SbFilmPlan.OTAZKA_CTENI, 0)
        val cteni = SbFilmPlan.precti(prvni)
        val radku = cteni.radku ?: 0
        val sloupcu = cteni.sloupcu ?: 0
        val opravene = mutableMapOf<Int, String>()
        val nalady = mutableMapOf<Int, String>()
        val zvuky = mutableMapOf<Int, String>()
        val deje = mutableMapOf<Int, String>()
        val kamery = mutableMapOf<Int, String>()
        // Skutečné řádky panelů z obrázku (5.38): různě velké panely, prázdné políčko.
        // Použijí se, jen když počet panelů souhlasí se čtením modelu.
        val radkyObrazu = podlePoctu(obraz.bunky(), cteni.panely.size)
            ?.let { SbPanelyObrazu.radky(it) }
            ?.takeIf { it.size >= 2 }
        val poRadcich = radkyObrazu != null || (radku >= 2 && sloupcu >= 1 && radku * sloupcu >= cteni.panely.size)
        val skutecne = radkyObrazu?.size ?: if (poRadcich) radku else 0
        prubeh.radku(skutecne)
        val nejiste = mutableSetOf<Int>()
        val sporne = mutableMapOf<Int, String>()
        // Zvětšení podle velikosti písma (5.67): Pepa měl při 500 px na panel písmo 21 px a ztrácel háčky.
        val pismo = radkyObrazu?.let { obraz.vyskaPisma(it.flatten()) }
        fun meritko(pomer: Float, sirkaVyrezu: Int, panelu: Int): Float =
            if (pismo != null && pismo > 0f) (nastaveni.pismoPx * pomer / pismo).coerceIn(1f, 3f)
            else SbFilmPlan.zvetseniRadku(sirkaVyrezu, panelu, nastaveni.panelPx * pomer)
        if (poRadcich) {
            var prvniVRadku = 1
            for (r in 0 until skutecne) {
                val v = obraz.vyska / skutecne
                val presah = v / 20
                // Pruh podle skutečného řádku panelů (s popisky), jinak rovnoměrně.
                val radekObr = radkyObrazu?.get(r)
                val y0 = if (radekObr != null) (radekObr.minOf { it.y0 } - presah / 2).coerceAtLeast(0)
                else (r * v - presah).coerceAtLeast(0)
                val y1 = if (radekObr != null) (radekObr.maxOf { it.y1 } + presah / 2).coerceAtMost(obraz.vyska)
                else ((r + 1) * v + presah).coerceAtMost(obraz.vyska)
                val odPanelu = if (radekObr != null) prvniVRadku else r * sloupcu + 1
                val doPanelu = if (radekObr != null) prvniVRadku + radekObr.size - 1 else (r + 1) * sloupcu
                prvniVRadku += radekObr?.size ?: 0
                val vRadku = doPanelu - odPanelu + 1
                val otazka = SbFilmPlan.otazkaRadku(odPanelu, doPanelu)
                suspend fun cti(meritko: Float, nazev: String, krok: Int): Opis {
                    val jm = model.nahraj(obraz.vyrezPng(0, y0, obraz.sirka, y1, meritko), nazev)
                    return Opis.z(SbFilmPlan.prevedPrepis(model.precti(jm, otazka, krok)))
                }
                // Dvě čtení v různém měřítku (5.67): uzel čte greedy, stejný výřez dá vždy stejnou chybu;
                // jiné měřítko chybuje jinde. Malé písmo ztrácí háčky („příšný“), velké „opravuje“ tvary
                // slov („vyřešte“ → „vyřešíte“) — rozhoduje shoda.
                val a = cti(meritko(1f, obraz.sirka, vRadku), "sbfilm_radek${r + 1}.png", 1 + r)
                // Druhý pohled: jen pásy popisků bez obrázků (5.67), jinak menší měřítko celého řádku.
                val pasy = radekObr?.let { obraz.pasyPopisku(it) }?.takeIf { p -> p.all { it != null } }?.filterNotNull()
                val b = if (!nastaveni.dvaPohledy) null
                else if (pasy != null) {
                    val m = meritko(1f, obraz.sirka, vRadku)
                    val py0 = (pasy.minOf { it.y0 } - 4).coerceAtLeast(0)
                    val py1 = (pasy.maxOf { it.y1 } + 4).coerceAtMost(obraz.vyska)
                    val jm = model.nahraj(obraz.vyrezPng(0, py0, obraz.sirka, py1, m), "sbfilm_radek${r + 1}b.png")
                    Opis.z(SbFilmPlan.prevedPrepis(model.precti(jm, otazka, prubeh.krokNavic())))
                } else cti(meritko(nastaveni.druhePomer, obraz.sirka, vRadku), "sbfilm_radek${r + 1}b.png", prubeh.krokNavic())
                for (n in odPanelu..doPanelu) {
                    val celeRepl = cteni.panely.firstOrNull { it.cislo == n }?.repliky
                    val kandidati = listOfNotNull(a.repliky[n], b?.repliky?.get(n), celeRepl)
                    var volba = SbShoda.vyber(kandidati)
                    val vsechna = kandidati.toMutableList()
                    // Bez většiny: panel ještě sám, ve vlastním výřezu.
                    if (volba == null && radekObr != null) {
                        val o = radekObr[n - odPanelu]
                        val jm = model.nahraj(
                            obraz.vyrezPng(o.x0, o.y0, o.x1, o.y1, meritko(1f, o.sirka, 1)),
                            "sbfilm_panel$n.png",
                        )
                        val c = Opis.z(SbFilmPlan.prevedPrepis(model.precti(jm, SbFilmPlan.otazkaRadku(n, n), prubeh.krokNavic())))
                        c.repliky[n]?.let { vsechna += it }
                        volba = SbShoda.vyber(vsechna)
                        if (volba == null) nejiste += n
                    }
                    if (n !in nejiste) SbShoda.sporna(vsechna)?.let { sporne[n] = it }
                    // Panel, který čtení řádku vůbec nevrátilo, si nechá repliky z celého čtení.
                    (volba ?: a.repliky[n] ?: b?.repliky?.get(n))?.let { opravene[n] = it }
                    (SbShoda.vyber(listOfNotNull(a.nalady[n], b?.nalady?.get(n))) ?: a.nalady[n] ?: b?.nalady?.get(n))?.let { nalady[n] = it }
                    (SbShoda.vyber(listOfNotNull(a.zvuky[n], b?.zvuky?.get(n))) ?: a.zvuky[n] ?: b?.zvuky?.get(n))?.let { zvuky[n] = it }
                    (SbShoda.vyber(listOfNotNull(a.deje[n], b?.deje?.get(n))) ?: a.deje[n] ?: b?.deje?.get(n))?.let { deje[n] = it }
                }
            }
        }
        // OCR jako nezávislý hlas pro tvar slov (5.67): vidoucí model krátká a nespisovná slova „opravuje“
        // ve všech čteních stejně (celej → celý, překlep nabiječku → nabíječku), Tesseract opisuje znak po znaku.
        val ocrOpravy = mutableMapOf<Int, String>()
        if (radkyObrazu != null) {
            val panely = radkyObrazu.flatten()
            val pasy = obraz.pasyPopisku(panely)
            val oblasti = panely.mapIndexed { i, p -> pasy.getOrNull(i) ?: p }
            val texty = runCatching { model.ocr(jmeno, oblasti, prubeh.krokNavic()) }.getOrNull()
            if (texty != null && texty.size == panely.size) texty.forEachIndexed { i, ocrText ->
                val n = i + 1
                val repl = opravene[n] ?: return@forEachIndexed
                val (nove, opravy) = SbOcrShoda.opravRepliky(repl, ocrText)
                if (opravy.isNotEmpty()) {
                    opravene[n] = nove
                    ocrOpravy[n] = opravy.joinToString(", ") { "${it.z} → ${it.na}" }
                }
                // Sporné slovo, které OCR potvrdilo nebo opravilo, už uživatel kontrolovat nemusí.
                val text = SbFilmPrepis.repliky(opravene[n] ?: "").joinToString(" ") { it.second }
                if (text.isNotBlank() && SbOcrShoda.kostra(ocrText).contains(SbOcrShoda.kostra(text)) &&
                    normDiakritika(ocrText).contains(normDiakritika(text))) sporne.remove(n)
            }
        }
        // Hlas a vzhled mluvčích, které celé čtení vynechalo — jinak každý úsek jiný hlas (5.56, 5.60).
        val mluvci = SbFilmPrepis.slucCteni(cteni.panely, opravene)
            .flatMap { p -> SbFilmPrepis.repliky(p.repliky).map { it.first } }
            .distinctBy { it.lowercase() }
            .filter { m -> cteni.hlasy.keys.none { it.equals(m, ignoreCase = true) } || cteni.vzhled.keys.none { it.equals(m, ignoreCase = true) } }
        if (mluvci.isNotEmpty()) {
            val odp = model.precti(jmeno, SbFilmPlan.otazkaHlasu(mluvci), prubeh.krokNavic())
            val bezHlasu = mluvci.filter { m -> cteni.hlasy.keys.none { it.equals(m, ignoreCase = true) } }
            val bezVzhledu = mluvci.filter { m -> cteni.vzhled.keys.none { it.equals(m, ignoreCase = true) } }
            prvni = SbFilmPlan.doplnHlasy(prvni, SbFilmPlan.prectiHlasy(odp, bezHlasu))
            prvni = SbFilmPlan.doplnVzhled(prvni, SbFilmPlan.prectiVzhled(odp, bezVzhledu))
        }
        val naladyOpis = nalady.toMap()
        val dejeOpis = deje.toMap()
        // Zvuky, emoce a děj ze storyboardu do angličtiny — česky je přepisovač opsal doslova (5.57, 5.61, 5.67).
        if (zvuky.isNotEmpty() || nalady.isNotEmpty() || deje.isNotEmpty()) {
            val odp = model.precti(
                jmeno, SbFilmPlan.otazkaPrekladu(zvuky.toSortedMap(), nalady.toSortedMap(), deje.toSortedMap()), prubeh.krokNavic(),
            )
            zvuky.putAll(SbFilmPlan.prectiPreklad(odp, "SOUND", zvuky.keys.toSet()))
            nalady.putAll(SbFilmPlan.prectiPreklad(odp, "MOOD", nalady.keys.toSet()))
            // Nepřeložený děj se nepoužije — česky by ho přepisovač opsal.
            val dejeEn = SbFilmPlan.prectiPreklad(odp, "ACTION", deje.keys.toSet())
            deje.clear(); deje.putAll(dejeEn)
            kamery.putAll(SbFilmPlan.prectiPreklad(odp, "CAMERA", dejeEn.keys))
        }
        return Vysledek(prvni, opravene, nalady, zvuky, deje, kamery, poRadcich, naladyOpis, dejeOpis, nejiste, sporne, ocrOpravy)
    }

    /** Text bez mezer a interpunkce, s diakritikou (potvrzení repliky textem OCR). */
    private fun normDiakritika(t: String) = t.lowercase().replace(Regex("""[^\p{L}\p{N}]"""), "")

    data class Sestaveno(val cteni: SbCteni, val plan: SbPlan, val nalezy: List<SbNalez>)

    /** Výsledek čtení → plán panelů a nálezy kontroly. */
    fun sestav(v: Vysledek): Sestaveno {
        val cele = SbFilmPlan.precti(v.text)
        val cteni = cele.copy(panely = SbFilmPrepis.slucCteni(cele.panely, v.repliky).map { p ->
            // Děj vytištěný pod panelem má přednost před dějem odhadnutým z obrázku (5.67);
            // nálada z řádků do popisu záběru → herecké podání.
            val dej = v.deje[p.cislo]?.let { if (it.endsWith(".") || it.endsWith("!") || it.endsWith("?")) it else "$it." }
            p.copy(
                popis = SbFilmPlan.doplnZvuk(SbFilmPlan.doplnNaladu(dej ?: p.popis, v.nalady[p.cislo]), v.zvuky[p.cislo]),
                kamera = v.kamery[p.cislo] ?: p.kamera,
            )
        })
        val plan = SbFilmPlan.naplanuj(cteni)
        val nalezy = SbFilmKontrola.storyboard(cele.panely, v.repliky, cteni.radku, cteni.sloupcu, v.poRadcich, plan.panely) +
            v.nejiste.sorted().map { SbNalez(it, t("Repliku se nepodařilo přečíst jistě — zkontroluj ji proti storyboardu.")) } +
            v.sporne.toSortedMap().map { (n, slova) -> SbNalez(n, t("Nejisté slovo v replice: %s — zkontroluj proti storyboardu.").format(slova)) }
        // Opravy podle OCR přípravu nezastavují: jsou omezené přísnými pravidly (SbOcrShoda.prijmout)
        // a na sadě storyboardů neudělaly žádnou chybu; zastavuje jen sporné slovo pro člověka.
        return Sestaveno(cteni, plan, nalezy)
    }
}
