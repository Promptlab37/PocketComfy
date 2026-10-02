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
        /** Nadpisy panelů anglicky (5.69, „1. 0–2 s | Úvodní záběr“) — nápověda pro přepisovač, ne děj. */
        val nadpisy: Map<Int, String> = emptyMap(),
        /** Nadpisy, jak je opsalo čtení řádků (česky). */
        val nadpisyOpis: Map<Int, String> = emptyMap(),
        /** Panely, jejichž repliku říká víc lidí najednou („(společně)“, 5.69). */
        val sbor: Set<Int> = emptySet(),
        /** Jméno mluvčího není nikde vytištěné a postava je jediná → všude [SbFilmPlan.VYCHOZI_MLUVCI] (5.69). */
        val jedenMluvci: Boolean = false,
        /** Vytištěná velikost záběru anglicky („Kamera: Detail.“ → close-up, 5.69) — přebije odhad celého čtení. */
        val zabery: Map<Int, String> = emptyMap(),
        /** Panely, kde celé čtení repliku vymyslelo nebo zdvojilo (5.69) — z plánu pryč. */
        val bezReplik: Set<Int> = emptySet(),
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
        val nadpisy: Map<Int, String>,
        val sbor: Set<Int>,
        val zabery: Map<Int, String>,
    ) {
        companion object {
            fun z(t: String) = Opis(
                SbFilmPlan.prectiRepliky(t), SbFilmPlan.prectiNalady(t), SbFilmPlan.prectiZvuky(t), SbFilmPlan.prectiDeje(t),
                SbFilmPlan.prectiNadpisy(t), SbFilmPlan.prectiSbor(t), SbFilmPlan.prectiZabery(t),
            )
        }
    }

    /**
     * Jméno mluvčího není vytištěné nikde na listu a postava je jediná (5.69, BYTY): čtení řádků
     * má repliky, ale žádnou se jménem. Celé čtení si pak jméno vymyslí — a u každého panelu
     * může jiné („Muž“, „Učitel“), takže by jedna postava mluvila několika hlasy. Jediná postava =
     * celé čtení dalo nejvýš jedno jméno, nebo jediný hlas či jediný vzhled. Víc postav bez jmen
     * (komiks Praotec: Čech, Lech, ženy) si jména z celého čtení nechá — jinak by mluvili jedním hlasem.
     */
    internal fun jedenMluvciBezJmena(cteni: SbCteni, radky: Map<Int, String>): Boolean {
        val vRadcich = radky.values.filter { it.isNotBlank() }
        if (vRadcich.isEmpty() || vRadcich.any { SbFilmPrepis.repliky(it).isNotEmpty() }) return false
        val jmena = cteni.panely.flatMap { SbFilmPrepis.repliky(it.repliky).map { r -> r.first.lowercase() } }.distinct()
        return jmena.size <= 1 || cteni.hlasy.size == 1 || cteni.vzhled.size == 1
    }

    /**
     * Zvukový efekt nakreslený v obraze („HAPČÍ!“ — Drak na pohovoru, 5.69), který celé čtení přisoudilo
     * postavě: jedno nebo dvě slova celá verzálkami s vykřičníkem, bez jména a bez uvozovek v popisku
     * (čtení řádku ho nemá). Patří do zvuku záběru — jako replika by ho postava řekla nahlas.
     */
    internal fun zvukovyEfekt(text: String): Boolean {
        val t = text.trim()
        val pismena = t.filter { it.isLetter() }
        return t.endsWith("!") && pismena.length >= 3 && pismena.all { it.isUpperCase() } &&
            t.split(Regex("""\s+""")).count { s -> s.any { it.isLetter() } } <= 2
    }

    /** Repliky z celého čtení a z řádků ([SbFilmPrepis.slucCteni]); u [jedenMluvci] všechny jedním mluvčím. */
    private fun sloucene(cteni: SbCteni, radky: Map<Int, String>, jedenMluvci: Boolean, bezReplik: Set<Int> = emptySet()): List<SbPrecteny> =
        SbFilmPrepis.slucCteni(cteni.panely.map { if (it.cislo in bezReplik) it.copy(repliky = "") else it }, radky).map { p ->
            if (!jedenMluvci) p
            else p.copy(repliky = SbFilmPrepis.repliky(p.repliky).joinToString("; ") { (_, co) -> "${SbFilmPlan.VYCHOZI_MLUVCI}: „$co“" })
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
        val nadpisy = mutableMapOf<Int, String>()
        val sbor = mutableSetOf<Int>()
        val zabery = mutableMapOf<Int, String>()
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
                    (SbShoda.vyber(listOfNotNull(a.nadpisy[n], b?.nadpisy?.get(n))) ?: a.nadpisy[n] ?: b?.nadpisy?.get(n))?.let { nadpisy[n] = it }
                    if (n in a.sbor || b?.sbor?.contains(n) == true) sbor += n
                    (a.zabery[n] ?: b?.zabery?.get(n))?.let { zabery[n] = it }
                }
            }
        }
        // OCR jako nezávislý hlas pro tvar slov (5.67): vidoucí model krátká a nespisovná slova „opravuje“
        // ve všech čteních stejně (celej → celý, překlep nabiječku → nabíječku), Tesseract opisuje znak po znaku.
        val ocrOpravy = mutableMapOf<Int, String>()
        // OCR pásu popisku každého panelu (pro děj a pro ověření bublin níž).
        val ocrPopisku = mutableMapOf<Int, String>()
        if (radkyObrazu != null) {
            val panely = radkyObrazu.flatten()
            val pasy = obraz.pasyPopisku(panely)
            val oblasti = panely.mapIndexed { i, p -> pasy.getOrNull(i) ?: p }
            val texty = runCatching { model.ocr(jmeno, oblasti, prubeh.krokNavic()) }.getOrNull()
            if (texty != null && texty.size == panely.size) texty.forEachIndexed { i, ocrText ->
                val n = i + 1
                ocrPopisku[n] = ocrText
                // Děj pod panelem (5.69): jen diakritika podle OCR — děj se pak překládá, jiné rozdíly
                // (koncovky, písmena) OCR nerozhoduje.
                deje[n]?.let { d -> if (SbOcrShoda.podilVOcr(d, ocrText) >= 0.5) deje[n] = SbOcrShoda.porovnej(d, ocrText, jenDiakritika = true).text }
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
        // Repliky, které má jen celé čtení — v popisku pod panelem nejsou (bubliny v obraze, 5.69).
        // Celé čtení je nejméně přesné: zkopíruje repliku do tichého panelu (Čtyři přání 6 ← 7), přečte
        // „chrnění“ místo „chrlení“ a nakreslené „HAPČÍ!“ dá postavě. Každá taková replika se ověří.
        val bezReplik = mutableSetOf<Int>()
        val jenZCelku = cteni.panely.filter { p -> SbFilmPrepis.repliky(p.repliky).isNotEmpty() && opravene[p.cislo].isNullOrBlank() }
        // 1) Zvukový efekt v obraze → zvuk záběru (přeloží se s ostatními zvuky).
        val overit = mutableMapOf<Int, List<Pair<String, String>>>()
        for (p in jenZCelku) {
            val (efekty, repl) = SbFilmPrepis.repliky(p.repliky).partition { zvukovyEfekt(it.second) }
            efekty.forEach { (_, co) -> zvuky[p.cislo] = listOfNotNull(zvuky[p.cislo], co).joinToString(" ") }
            if (repl.isEmpty()) bezReplik += p.cislo else overit[p.cislo] = repl
            if (efekty.isNotEmpty() && repl.isNotEmpty()) opravene[p.cislo] = repl.joinToString("; ") { (k, c) -> "$k: „$c“" }
        }
        // 2) OCR celého panelu, jen kde je potřeba: bublina je v obraze, ne v pásu popisku; a děj, který
        //    OCR pásu nepokrylo (pás popisku je u BYTY tabule, ne text pod obrázkem).
        //    Replika v OCR panelu je → tvar slov podle OCR a rozdíly ke kontrole; není v něm, ale je
        //    v jiném panelu → zdvojená, pryč. Děj: jen diakritika („šípce“ → „šipce“) — děj se pak
        //    překládá, jiné rozdíly (koncovky, písmena) OCR nerozhoduje.
        val dejBezOcr = deje.filter { (n, d) -> ocrPopisku[n]?.let { SbOcrShoda.podilVOcr(d, it) >= 0.5 } != true }.keys
        if (radkyObrazu != null && (overit.isNotEmpty() || dejBezOcr.isNotEmpty())) {
            val panely = radkyObrazu.flatten()
            val cisla = (overit.keys + dejBezOcr).filter { it in 1..panely.size }.sorted()
            val texty = if (cisla.isEmpty()) null
            else runCatching { model.ocr(jmeno, cisla.map { panely[it - 1] }, prubeh.krokNavic()) }.getOrNull()
            if (texty != null && texty.size == cisla.size) {
                val ocrPanelu = cisla.zip(texty).toMap()
                for (n in dejBezOcr) {
                    val d = deje[n] ?: continue
                    val t = ocrPanelu[n] ?: continue
                    if (SbOcrShoda.podilVOcr(d, t) >= 0.5) deje[n] = SbOcrShoda.porovnej(d, t, jenDiakritika = true).text
                }
                fun jinde(n: Int, co: String): Boolean =
                    opravene.any { (m, r) -> m != n && SbFilmPrepis.repliky(r).any { normRepl(it.second) == normRepl(co) } } ||
                        ocrPanelu.any { (m, t) -> m != n && m in overit && SbOcrShoda.podilVOcr(co, t) >= 0.75 }
                for (n in cisla.filter { it in overit }) {
                    val ocrText = ocrPanelu.getValue(n)
                    val potvrzene = mutableListOf<Pair<String, String>>()
                    var nejiste = false
                    val opravy = mutableListOf<SbOcrShoda.Oprava>()
                    val sporna = mutableListOf<SbOcrShoda.Oprava>()
                    for ((kdo, co) in overit.getValue(n)) {
                        when {
                            SbOcrShoda.podilVOcr(co, ocrText) >= 0.5 -> {
                                val por = SbOcrShoda.porovnej(co, ocrText)
                                potvrzene += kdo to por.text
                                opravy += por.opravy; sporna += por.neprijate
                            }
                            jinde(n, co) -> Unit
                            else -> { potvrzene += kdo to co; nejiste = true }
                        }
                    }
                    if (opravy.isNotEmpty()) ocrOpravy[n] = opravy.joinToString(", ") { "${it.z} → ${it.na}" }
                    if (sporna.isNotEmpty()) sporne[n] = sporna.joinToString("; ") { "${it.z} / ${it.na}" }
                    when {
                        potvrzene.isEmpty() -> bezReplik += n
                        // Nepotvrzenou repliku nechá prázdné čtení řádku — kontrola ji ukáže jako nejistou.
                        nejiste && potvrzene.size == overit.getValue(n).size -> Unit
                        else -> opravene[n] = potvrzene.joinToString("; ") { (k, c) -> "$k: „$c“" }
                    }
                }
            }
        }
        // Bez vytištěného jména a s jedinou postavou mluví všude jeden mluvčí (5.69); hlas a vzhled,
        // které k vymyšlenému jménu dalo celé čtení, patří jemu.
        val jedenMluvci = jedenMluvciBezJmena(cteni, opravene)
        if (jedenMluvci) {
            val vymyslena = cteni.panely.flatMap { SbFilmPrepis.repliky(it.repliky).map { r -> r.first } }.toSet()
            prvni = SbFilmPlan.prejmenujMluvciho(prvni, vymyslena, SbFilmPlan.VYCHOZI_MLUVCI)
        }
        // Hlas a vzhled mluvčích, které celé čtení vynechalo — jinak každý úsek jiný hlas (5.56, 5.60).
        val cteniH = if (jedenMluvci) SbFilmPlan.precti(prvni) else cteni
        val mluvci = sloucene(cteniH, opravene, jedenMluvci, bezReplik)
            .flatMap { p -> SbFilmPrepis.repliky(p.repliky).map { it.first } }
            .distinctBy { it.lowercase() }
            .filter { m -> cteniH.hlasy.keys.none { it.equals(m, ignoreCase = true) } || cteniH.vzhled.keys.none { it.equals(m, ignoreCase = true) } }
        if (mluvci.isNotEmpty()) {
            val odp = model.precti(jmeno, SbFilmPlan.otazkaHlasu(mluvci), prubeh.krokNavic())
            val bezHlasu = mluvci.filter { m -> cteniH.hlasy.keys.none { it.equals(m, ignoreCase = true) } }
            val bezVzhledu = mluvci.filter { m -> cteniH.vzhled.keys.none { it.equals(m, ignoreCase = true) } }
            prvni = SbFilmPlan.doplnHlasy(prvni, SbFilmPlan.prectiHlasy(odp, bezHlasu))
            prvni = SbFilmPlan.doplnVzhled(prvni, SbFilmPlan.prectiVzhled(odp, bezVzhledu))
        }
        val naladyOpis = nalady.toMap()
        val dejeOpis = deje.toMap()
        val nadpisyOpis = nadpisy.toMap()
        // Zvuky, emoce, děj a nadpisy ze storyboardu do angličtiny — česky je přepisovač opsal doslova (5.57, 5.61, 5.67).
        if (zvuky.isNotEmpty() || nalady.isNotEmpty() || deje.isNotEmpty() || nadpisy.isNotEmpty()) {
            val odp = model.precti(
                jmeno, SbFilmPlan.otazkaPrekladu(zvuky.toSortedMap(), nalady.toSortedMap(), deje.toSortedMap(), nadpisy.toSortedMap()),
                prubeh.krokNavic(),
            )
            zvuky.putAll(SbFilmPlan.prectiPreklad(odp, "SOUND", zvuky.keys.toSet()))
            nalady.putAll(SbFilmPlan.prectiPreklad(odp, "MOOD", nalady.keys.toSet()))
            // Nepřeložený děj se nepoužije — česky by ho přepisovač opsal.
            val dejeEn = SbFilmPlan.prectiPreklad(odp, "ACTION", deje.keys.toSet())
            deje.clear(); deje.putAll(dejeEn)
            kamery.putAll(SbFilmPlan.prectiPreklad(odp, "CAMERA", dejeEn.keys))
            // Nepřeložený nadpis taky ne (česky by ho přepisovač opsal).
            val nadpisyEn = SbFilmPlan.prectiPreklad(odp, "TITLE", nadpisy.keys.toSet())
            nadpisy.clear(); nadpisy.putAll(nadpisyEn)
        }
        return Vysledek(
            prvni, opravene, nalady, zvuky, deje, kamery, poRadcich, naladyOpis, dejeOpis, nejiste, sporne, ocrOpravy,
            nadpisy, nadpisyOpis, sbor, jedenMluvci, zabery, bezReplik,
        )
    }

    private fun normRepl(t: String) = t.lowercase().replace(Regex("""[^\p{L}\p{N}]"""), "")

    /** Text bez mezer a interpunkce, s diakritikou (potvrzení repliky textem OCR). */
    private fun normDiakritika(t: String) = t.lowercase().replace(Regex("""[^\p{L}\p{N}]"""), "")

    data class Sestaveno(val cteni: SbCteni, val plan: SbPlan, val nalezy: List<SbNalez>)

    /** Výsledek čtení → plán panelů a nálezy kontroly. */
    fun sestav(v: Vysledek): Sestaveno {
        val cele = SbFilmPlan.precti(v.text)
        val cteni = cele.copy(panely = sloucene(cele, v.repliky, v.jedenMluvci, v.bezReplik).map { p ->
            // Děj vytištěný pod panelem má přednost před dějem odhadnutým z obrázku (5.67);
            // nálada z řádků do popisu záběru → herecké podání. Nadpis panelu (5.69) jen jako
            // nápověda „Title:“ za dějem — Sound: musí zůstat poslední (hlídka ho odtud bere).
            val dej = v.deje[p.cislo]?.let { if (it.endsWith(".") || it.endsWith("!") || it.endsWith("?")) it else "$it." }
            p.copy(
                popis = SbFilmPlan.doplnZvuk(
                    SbFilmPlan.doplnNaladu(SbFilmPlan.doplnNadpis(dej ?: p.popis, v.nadpisy[p.cislo]), v.nalady[p.cislo]),
                    v.zvuky[p.cislo],
                ),
                kamera = v.kamery[p.cislo] ?: p.kamera,
                // Vytištěná velikost záběru (5.69) má přednost před odhadem celého čtení.
                typ = v.zabery[p.cislo] ?: p.typ,
            )
        })
        val plan0 = SbFilmPlan.naplanuj(cteni)
        // Replika „(společně)“ (5.69): mluvčí panelu ji řekne s ostatními najednou. Replika zůstává
        // doslova v jednom <d>, podání jde před ni ([SbFilmPrepis.hlidka]) — kontrola zadání ani
        // vynucení replik se nemění.
        val plan = plan0.copy(panely = plan0.panely.map { p ->
            val r = SbFilmPrepis.repliky(p.repliky)
            if (p.cislo !in v.sbor || r.isEmpty() || p.podani.isNotBlank()) p
            else p.copy(podani = r.joinToString(";") { SbFilmPlan.PODANI_SBOR })
        })
        val nalezy = SbFilmKontrola.storyboard(cele.panely, v.repliky, cteni.radku, cteni.sloupcu, v.poRadcich, plan.panely) +
            v.nejiste.sorted().map { SbNalez(it, t("Repliku se nepodařilo přečíst jistě — zkontroluj ji proti storyboardu.")) } +
            v.sporne.toSortedMap().map { (n, slova) -> SbNalez(n, t("Nejisté slovo v replice: %s — zkontroluj proti storyboardu.").format(slova)) }
        // Opravy podle OCR přípravu nezastavují: jsou omezené přísnými pravidly (SbOcrShoda.prijmout)
        // a na sadě storyboardů neudělaly žádnou chybu; zastavuje jen sporné slovo pro člověka.
        return Sestaveno(cteni, plan, nalezy)
    }
}
