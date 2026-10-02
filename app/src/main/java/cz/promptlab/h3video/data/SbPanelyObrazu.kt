package cz.promptlab.h3video.data

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Skutečné hranice panelů na storyboardu (5.38, uživatel 30. 9. 2026: „musí to
 * fungovat, i když jsou panely různě veliké… i když je lichý počet oken a jedno
 * je prázdné“). Dřív appka brala rovnoměrnou mřížku řádky × sloupce od vision
 * modelu — při různě velkých panelech řízla čtení replik přes panel.
 *
 * Postup (probráno s odborníky na rozvržení):
 *  1. Barva mezer z dlouhých jednolitých pruhů přes celý list (řádky/sloupce
 *     s malým rozptylem) — ne z obvodu, panely často sahají až k okraji.
 *  2. Maska „obsah“ = pixely vzdálené od barvy mezer (RGB), souvislé oblasti.
 *     Rámeček nebo fotka je jedna oblast; čísla v rohu leží uvnitř.
 *  3. Velké oblasti = kandidáti na panel (relativně k největší). Prázdné políčko
 *     (rámeček s jednolitým vnitřkem) se vynechá — lichý počet oken.
 *  4. Malé oblasti (písmena popisků) se připojí k panelu nad sebou; titulek
 *     přes víc panelů se zahodí.
 *  5. Pořadí čtení po řádcích, v řádku zleva doprava; vysoký panel přes dva
 *     řádky drží řádek pohromadě.
 *  6. Nejistý výsledek (< 2 panely, malé pokrytí) → null, appka jede postaru.
 *
 * Čistý Kotlin nad ARGB polem, ať jde ověřit testem bez Androidu.
 */
object SbPanelyObrazu {

    data class Obdelnik(val x0: Int, val y0: Int, val x1: Int, val y1: Int) {
        val sirka get() = x1 - x0
        val vyska get() = y1 - y0
        val plocha get() = sirka.toLong() * vyska
    }

    /** Proč naposledy nic nenašlo (jen pro testy a ladění). */
    @Volatile var duvod: String = ""
        private set

    /** Pracovní rozlišení: delší strana nejvýš tolik bodů. */
    private const val PRACOVNI = 800

    /** Políčko listu: panel, nebo prázdné políčko v rámečku (lichý počet oken). */
    data class Bunka(val obdelnik: Obdelnik, val prazdna: Boolean)

    /** Panely s obsahem v pořadí čtení, v souřadnicích vstupu; null = nejisté. */
    fun najdi(argb: IntArray, w: Int, h: Int): List<Obdelnik>? =
        najdiBunky(argb, w, h)?.filter { !it.prazdna }?.map { it.obdelnik }

    /**
     * Všechna políčka v pořadí čtení včetně prázdných v rámečku (5.38): model
     * může prázdné políčko započítat (10 oken, jedno bez děje) — pak se
     * porovnává počet všech políček. Null = nejisté.
     *
     * Dva nezávislé pohledy (5.69, sada 20 listů 2. 10. 2026, 10 z 21 nových listů špatně):
     *  - [podleOblasti] = původní hledání souvislých oblastí obsahu; bez titulku a patičky
     *    ([bezTitulku]) — pruh s nadpisem nad mřížkou se dřív počítal jako panel;
     *  - [podleMezer] = řezy po mezerách mezi panely; najde mřížku i na tmavém listu
     *    s tenkými mezerami a na zdobeném okraji, kde oblasti selžou.
     * Přednost má původní výsledek (staré listy dávají přesně to co dřív); mezery
     * ho nahradí, jen když oblasti nic nenašly, nebo když mezery rozdělí slité
     * oblasti na víc panelů ([zjemnuje]).
     */
    fun najdiBunky(argb: IntArray, w: Int, h: Int): List<Bunka>? {
        duvod = ""
        if (w < 50 || h < 50 || argb.size < w * h) run { duvod = "D1"; return null }
        val oblasti = podleOblasti(argb, w, h)?.let { bezTitulku(it) }
        val duvodOblasti = duvod
        val mezery = podleMezer(argb, w, h)
        val plne = oblasti?.filter { !it.prazdna }
        val vysledek = when {
            mezery == null -> oblasti
            oblasti == null || plne!!.size < 2 -> mezery.map { Bunka(it, false) }
            mezery.size > plne.size && zjemnuje(mezery, plne.map { it.obdelnik }, max(w, h)) -> mezery.map { Bunka(it, false) }
            else -> oblasti
        }
        duvod = if (vysledek === oblasti) duvodOblasti else "M podle mezer"
        return vysledek
    }

    /** Původní hledání po souvislých oblastech obsahu (5.38), null = nejisté. */
    private fun podleOblasti(argb: IntArray, w: Int, h: Int): List<Bunka>? {
        if (w < 50 || h < 50 || argb.size < w * h) run { duvod = "D1"; return null }
        val krok = max(1, (max(w, h) + PRACOVNI - 1) / PRACOVNI)
        val sw = w / krok
        val sh = h / krok
        val r = IntArray(sw * sh); val g = IntArray(sw * sh); val b = IntArray(sw * sh)
        for (y in 0 until sh) for (x in 0 until sw) {
            val p = argb[(y * krok) * w + x * krok]
            val i = y * sw + x
            r[i] = (p shr 16) and 255; g[i] = (p shr 8) and 255; b[i] = p and 255
        }
        val mezera = barvaMezer(r, g, b, sw, sh) ?: run { duvod = "D2"; return null }
        val tol = 34
        val obsah = BooleanArray(sw * sh) { i ->
            abs(r[i] - mezera[0]) > tol || abs(g[i] - mezera[1]) > tol || abs(b[i] - mezera[2]) > tol
        }
        val oblasti = oblasti(obsah, sw, sh)
        if (oblasti.isEmpty()) run { duvod = "D3"; return null }
        val nejvetsi = oblasti.maxOf { it.plocha }
        // Kandidát na panel: velký vůči největší oblasti a ne jen tenká čára.
        val velke = oblasti.filter { it.plocha >= nejvetsi / 6 && min(it.sirka, it.vyska) >= min(sw, sh) / 25 }
            .toMutableList()
        // Oblast celá uvnitř jiné (číslo v rohu, obrázek v rámu) — patří k ní.
        velke.removeAll { a -> velke.any { b -> b !== a && b.obsahuje(a) } }
        // Prázdné políčko (rámeček bez obsahu) — vynechat.
        val prazdna = velke.filter { prazdne(it, obsah, sw) }
        velke.removeAll(prazdna)
        if (velke.size < 2) run { duvod = "D4 málo panelů"; return null }
        // Malé oblasti (písmena popisků) k panelu nad sebou.
        val panely = velke.map { it }.toMutableList()
        oblasti.filter { a -> velke.none { it == a } && a.plocha < nejvetsi / 6 }.forEach { a ->
            if (panely.any { it.obsahuje(a) }) return@forEach
            val nad = panely.withIndex().filter { (_, p) ->
                prekryvX(p, a) > 0.5 * a.sirka && a.y0 >= p.y1 - 2 && a.y0 - p.y1 < p.vyska / 2
            }
            // Titulek přes víc panelů nebo nic nad ním → zahodit.
            if (nad.isEmpty()) return@forEach
            val (i, p) = nad.minBy { it.value.y1 }.let { best -> nad.filter { it.value.y1 == best.value.y1 } }
                .minBy { abs((it.value.x0 + it.value.x1) / 2 - (a.x0 + a.x1) / 2) }
            if (panely.count { prekryvX(it, a) > 0.2 * a.sirka && abs(it.y1 - p.y1) < p.vyska / 4 } > 1 && a.sirka > p.sirka) return@forEach
            panely[i] = Obdelnik(min(p.x0, a.x0), p.y0, max(p.x1, a.x1), max(p.y1, a.y1))
        }
        // Sloučit, co se po připojení popisků překrývá.
        var zmena = true
        while (zmena) {
            zmena = false
            loop@ for (i in panely.indices) for (j in i + 1 until panely.size) {
                val a = panely[i]; val c = panely[j]
                if (a.x0 < c.x1 && c.x0 < a.x1 && a.y0 < c.y1 && c.y0 < a.y1) {
                    // Jen malý přesah (popisek sahající k sousedovi) nevadí.
                    val px = min(a.x1, c.x1) - max(a.x0, c.x0)
                    val py = min(a.y1, c.y1) - max(a.y0, c.y0)
                    if (px.toLong() * py > min(a.plocha, c.plocha) / 10) {
                        panely[i] = Obdelnik(min(a.x0, c.x0), min(a.y0, c.y0), max(a.x1, c.x1), max(a.y1, c.y1))
                        panely.removeAt(j); zmena = true; break@loop
                    }
                }
            }
        }
        if (panely.size < 2) run { duvod = "D5"; return null }
        // Pokrytí: panely musí tvořit většinu listu (jinak je to fotka, ne storyboard).
        val pokryti = (panely + prazdna).sumOf { it.plocha }.toDouble() / (sw.toLong() * sh)
        if (pokryti < 0.45) run { duvod = "D6 malé pokrytí"; return null }
        // Velikosti panelů rozumně srovnatelné (fotka s jednobarevným pozadím dá 1 velkou + drobky).
        val med = panely.map { it.plocha }.sorted()[panely.size / 2]
        if (panely.any { it.plocha < med / 5 }) run { duvod = "D7"; return null }
        val prazdne = prazdna.toSet()
        return poradi(panely + prazdna).map {
            Bunka(Obdelnik(it.x0 * krok, it.y0 * krok, min(w, it.x1 * krok), min(h, it.y1 * krok)), it in prazdne)
        }
    }

    /**
     * Bez titulku a patičky (5.69): nízký pruh celý nad všemi panely (nadpis „STORYBOARD – …“,
     * řádek Postavy) nebo celý pod nimi (Kontinuita) není panel. Nízký = pod polovinou
     * mediánu výšky; prázdná políčka se neposuzují. Sada 20: listy 02, 03, 13, 16, 17.
     */
    private fun bezTitulku(bunky: List<Bunka>): List<Bunka> {
        val plne = bunky.filter { !it.prazdna }
        if (plne.size < 3) return bunky
        val med = plne.map { it.obdelnik.vyska }.sorted()[plne.size / 2]
        val vysoke = plne.filter { it.obdelnik.vyska * 2 >= med }
        if (vysoke.size < 2) return bunky
        val tol = med / 20
        val horni = vysoke.minOf { it.obdelnik.y0 }
        val dolni = vysoke.maxOf { it.obdelnik.y1 }
        val pryc = plne.filter { b ->
            b.obdelnik.vyska * 2 < med && (b.obdelnik.y1 <= horni + tol || b.obdelnik.y0 >= dolni - tol)
        }.toSet()
        if (pryc.isEmpty()) return bunky
        return bunky.filter { it !in pryc }
    }

    /**
     * Mezery jen rozdělily slité oblasti (tmavý list s tenkou mezerou, nadpis přetékající přes
     * horní řádek): každý panel z mezer leží uvnitř některé oblasti (s tolerancí) a v každém
     * řádku jsou panely zhruba stejně široké. Nestejné kusy = řez skrz obrázek (svislý sloupek
     * na fotce), tomu se nevěří (Otevřené dveře).
     */
    private fun zjemnuje(mezery: List<Obdelnik>, oblasti: List<Obdelnik>, rozmer: Int): Boolean {
        val tol = rozmer / 50
        val uvnitr = mezery.all { m ->
            oblasti.any { o -> m.x0 >= o.x0 - tol && m.y0 >= o.y0 - tol && m.x1 <= o.x1 + tol && m.y1 <= o.y1 + tol }
        }
        return uvnitr && radky(mezery).all { r -> r.minOf { it.sirka } * 10 >= r.maxOf { it.sirka } * 6 }
    }

    /** Pracovní rozlišení hledání mezer: tenké mezery tmavých listů (3 px na 1672) musí přežít zmenšení. */
    private const val MEZERY_PRACOVNI = 1000

    /** Tolerance barvy mezery po složkách RGB. */
    private const val MEZERY_TOL = 28

    /**
     * Panely podle mezer (5.69), řezy jako u stránky komiksu (XY-cut):
     *  1. Vodorovné mezery přes celou šířku listu: pás řádků jedné barvy, který aspoň z jedné
     *     strany ostře ukončí obsah (hrana panelu, rámeček). Mezera mezi řádky textu v popisku
     *     řezem není — text kolem ní je řídký.
     *  2. Vysoké pruhy s obrázkem jsou řádky panelů; nízké pruhy a pruhy z řídkého textu
     *     (popisek pod obrázkem, vyprávění pod řadou komiksu, patička) patří k řádku nad sebou,
     *     nízký řádek úplně nahoře (nadpis listu) se zahodí.
     *  3. V každém řádku svislé mezery přes celou výšku řádku (když je přetne patička, jen
     *     přes vysokou část) → panely; úzké zbytky u okraje (ozdobný rám) se zahodí.
     * Barva mezer se nebere z celého listu — každá mezera má svoji (tmavé i světlé listy,
     * zdobené okraje). Null = nejisté.
     */
    private fun podleMezer(argb: IntArray, w0: Int, h0: Int): List<Obdelnik>? {
        val krok = max(1, (max(w0, h0) + MEZERY_PRACOVNI - 1) / MEZERY_PRACOVNI)
        val w = w0 / krok
        val h = h0 / krok
        if (w < 50 || h < 50) return null
        val px = IntArray(w * h) { i -> argb[(i / w) * krok * w0 + (i % w) * krok] }
        val vodorovne = rezy(px, w, 0, h, 0, w, vodorovne = true)
        val pruhy = useky(vodorovne.pasy, h)
        if (pruhy.isEmpty()) return null
        val nejvyssi = pruhy.maxOf { it[1] - it[0] }
        // Pruh s obrázkem: většina řádků pestrá. Pruh s textem má i řádky s písmem skoro jednolité.
        fun obrazovy(p: IntArray): Boolean {
            val podily = (p[0] until p[1]).map { vodorovne.podil[it] }.sorted()
            return podily[podily.size / 2] < 0.6f
        }
        // Řádek panelů: [začátek, konec vysoké části, konec i s nízkými pruhy pod ní].
        val radky = mutableListOf<IntArray>()
        for (p in pruhy) {
            if ((p[1] - p[0]) * 10 >= nejvyssi * 4 && obrazovy(p)) radky += intArrayOf(p[0], p[1], p[1])
            else radky.lastOrNull()?.let { it[2] = p[1] }
        }
        if (radky.isEmpty()) return null
        val medR = radky.map { it[2] - it[0] }.sorted()[radky.size / 2]
        while (radky.size > 1 && (radky[0][2] - radky[0][0]) * 2 < medR) radky.removeAt(0)
        val bunky = mutableListOf<Obdelnik>()
        for ((y0, yVysoka, y1) in radky) {
            var sloupce = useky(rezy(px, w, y0, y1, 0, w, vodorovne = false).pasy, w)
            if (sloupce.size <= 1 && y1 > yVysoka) sloupce = useky(rezy(px, w, y0, yVysoka, 0, w, vodorovne = false).pasy, w)
            if (sloupce.isEmpty()) continue
            val nejsirsi = sloupce.maxOf { it[1] - it[0] }
            for (s in sloupce) if ((s[1] - s[0]) * 10 >= nejsirsi * 3) bunky += Obdelnik(s[0], y0, s[1], y1)
        }
        if (bunky.size < 2) return null
        val med = bunky.map { it.plocha }.sorted()[bunky.size / 2]
        if (bunky.any { it.plocha < med / 5 }) return null
        if (bunky.sumOf { it.plocha }.toDouble() / (w.toLong() * h) < 0.45) return null
        return poradi(bunky).map {
            Obdelnik(it.x0 * krok, it.y0 * krok, min(w0, it.x1 * krok), min(h0, it.y1 * krok))
        }
    }

    /** Výsledek [rezy]: pásy mezer (od, do) a podíl jednolitých bodů každé čáry řezu. */
    private class Rezy(val pasy: List<IntArray>, val podil: FloatArray)

    /**
     * Pásy mezer v oblasti [a0, a1) × [b0, b1) jako dvojice (od, do) relativně k začátku osy řezu:
     * u [vodorovne] řádky y ∈ [a0, a1) přes x ∈ [b0, b1), jinak sloupce x ∈ [b0, b1) přes y ∈ [a0, a1).
     * Pásy na okraji oblasti (okraj listu) se vrací vždy — jen ohraničují úseky.
     */
    private fun rezy(px: IntArray, w: Int, a0: Int, a1: Int, b0: Int, b1: Int, vodorovne: Boolean): Rezy {
        val n = if (vodorovne) a1 - a0 else b1 - b0
        val delka = if (vodorovne) b1 - b0 else a1 - a0
        if (n < 3 || delka < 3) return Rezy(emptyList(), FloatArray(max(0, n)))
        fun bod(i: Int, k: Int) = if (vodorovne) px[(a0 + i) * w + b0 + k] else px[(a0 + k) * w + b0 + i]
        // Pro každou čáru řezu: medián barvy po složkách a podíl bodů blízko něj.
        val podil = FloatArray(n)
        val barva = IntArray(n)
        val hr = IntArray(256); val hg = IntArray(256); val hb = IntArray(256)
        fun median(hist: IntArray): Int { var s = 0; for (v in 0..255) { s += hist[v]; if (s * 2 >= delka) return v }; return 255 }
        for (i in 0 until n) {
            for (k in 0 until delka) { val c = bod(i, k); hr[(c shr 16) and 255]++; hg[(c shr 8) and 255]++; hb[c and 255]++ }
            val mr = median(hr); val mg = median(hg); val mb = median(hb)
            var stejne = 0
            for (k in 0 until delka) {
                val c = bod(i, k)
                val r = (c shr 16) and 255; val g = (c shr 8) and 255; val b = c and 255
                hr[r]--; hg[g]--; hb[b]--
                if (abs(r - mr) <= MEZERY_TOL && abs(g - mg) <= MEZERY_TOL && abs(b - mb) <= MEZERY_TOL) stejne++
            }
            podil[i] = stejne.toFloat() / delka
            barva[i] = (mr shl 16) or (mg shl 8) or mb
        }
        fun blizko(c: Int, d: Int) = abs(((c shr 16) and 255) - ((d shr 16) and 255)) <= MEZERY_TOL &&
            abs(((c shr 8) and 255) - ((d shr 8) and 255)) <= MEZERY_TOL && abs((c and 255) - (d and 255)) <= MEZERY_TOL
        // Hustota čáry i vůči barvě mezery c: podíl bodů, které se od ní liší.
        fun hustota(i: Int, c: Int): Float {
            if (i < 0 || i >= n) return 0f
            var jine = 0
            for (k in 0 until delka) if (!blizko(bod(i, k), c)) jine++
            return jine.toFloat() / delka
        }
        // Pásy po sobě jdoucích čar s podílem aspoň [prah]; změna barvy pás dělí (tmavá mezera
        // se světlou linkou uprostřed jsou dva pásy — Soused z Marsu).
        fun pasy(prah: Float): List<IntArray> {
            val out = mutableListOf<IntArray>()
            var i = 0
            while (i < n) {
                if (podil[i] < prah) { i++; continue }
                var j = i + 1
                while (j < n && podil[j] >= prah && blizko(barva[j], barva[j - 1])) j++
                out += intArrayOf(i, j)
                i = j
            }
            return out
        }
        // Barva pásu = medián barev jeho čar po složkách.
        fun barvaPasu(i: Int, j: Int): Int {
            fun slozka(sh: Int) = (i until j).map { (barva[it] shr sh) and 255 }.sorted()[(j - i) / 2]
            return (slozka(16) shl 16) or (slozka(8) shl 8) or slozka(0)
        }
        val out = mutableListOf<IntArray>()
        for ((i, j) in pasy(0.9f)) {
            if (i == 0 || j == n) { out += intArrayOf(i, j); continue }
            val c = barvaPasu(i, j)
            // Ostrá hrana obsahu aspoň z jedné strany.
            if (max(max(hustota(i - 1, c), hustota(i - 2, c)), max(hustota(j, c), hustota(j + 1, c))) >= 0.5f) out += intArrayOf(i, j)
        }
        // Zdobený list (pergamen s přechodem, Čtyři přání) má vodorovnou mezeru méně jednolitou:
        // stačí 70 % čáry, když ji obsah ostře ukončí z obou stran.
        if (vodorovne) {
            for ((i, j) in pasy(0.7f)) {
                if (i == 0 || j == n || out.any { it[0] < j && i < it[1] }) continue
                val c = barvaPasu(i, j)
                if (min(max(hustota(i - 1, c), hustota(i - 2, c)), max(hustota(j, c), hustota(j + 1, c))) >= 0.7f) out += intArrayOf(i, j)
            }
            out.sortBy { it[0] }
        }
        return Rezy(out, podil)
    }

    /** Úseky [od, do) mezi pásy mezer na ose délky [n]. */
    private fun useky(pasy: List<IntArray>, n: Int): List<IntArray> {
        val out = mutableListOf<IntArray>()
        var s = 0
        for (p in pasy) {
            if (p[0] > s) out += intArrayOf(s, p[0])
            s = max(s, p[1])
        }
        if (s < n) out += intArrayOf(s, n)
        return out
    }

    /** Řádky panelů (pro čtení replik po řádcích); v každém zleva doprava. */
    /**
     * Výška řádku tištěného textu v popiscích panelů (px originálu), null = nejde změřit
     * (světlé písmo na tmavém, málo textu). Řádek textu = souvislý pruh řádků pixelů se světlým
     * pozadím a tmavým inkoustem; háčky a čárky oddělené mezerou se nepočítají (min. 6 px).
     * Podle ní se volí zvětšení výřezu ke čtení (5.67): model čte dobře při ~30 px, při ~20 px
     * ztrácí háčky, při ~40 px „opravuje“ tvary slov (sada storyboardů 1. 10. 2026).
     */
    fun vyskaPisma(argb: IntArray, w: Int, h: Int, bunky: List<Obdelnik>): Float? {
        val behy = mutableListOf<Int>()
        for (b in bunky) {
            val x0 = (b.x0 + b.sirka * 0.03).toInt().coerceIn(0, w - 1)
            val x1 = (b.x1 - b.sirka * 0.03).toInt().coerceIn(x0 + 1, w)
            val ys = b.y0.coerceAtLeast(0) until b.y1.coerceAtMost(h)
            if (ys.isEmpty()) continue
            val svetly = BooleanArray(ys.count())
            val ink = BooleanArray(ys.count())
            for ((i, y) in ys.withIndex()) {
                var svetle = 0
                var inkoust = 0
                for (x in x0 until x1) {
                    val c = argb[y * w + x]
                    val l = ((c shr 16 and 0xFF) * 299 + (c shr 8 and 0xFF) * 587 + (c and 0xFF) * 114) / 1000
                    if (l > 170) svetle++
                    if (l < 110) inkoust++
                }
                // Světlé pozadí = většina pixelů světlá (průměr nestačí: tučné písmo ho stáhne pod práh).
                svetly[i] = svetle * 2 > (x1 - x0)
                ink[i] = inkoust > 0
            }
            // Pás popisku = nejdelší souvislý světlý úsek políčka (obrázek nad ním světlý není;
            // jinak by se počítaly i světlé kousky obrázku).
            var nejOd = 0; var nejDo = -1; var od = -1
            for (i in 0..svetly.size) {
                val sv = i < svetly.size && svetly[i]
                if (sv && od < 0) od = i
                if (!sv && od >= 0) { if (i - od > nejDo - nejOd + 1) { nejOd = od; nejDo = i - 1 }; od = -1 }
            }
            var beh = 0
            for (i in nejOd..nejDo) {
                if (ink[i]) beh++ else { if (beh in 6..80) behy += beh; beh = 0 }
            }
            if (beh in 6..80) behy += beh
        }
        if (behy.size < 4) return null
        return behy.sorted()[behy.size / 2].toFloat()
    }

    /**
     * Pás popisku políčka (nejdelší souvislý světlý úsek) v souřadnicích vstupu, null = není.
     * Výřez jen s popiskem čte repliky věrněji než s obrázkem panelu — okolní scéna svádí model
     * k „opravám“ textu (rešerše 1. 10. 2026); druhé čtení řádku je proto bez obrázků.
     */
    fun pasPopisku(argb: IntArray, w: Int, h: Int, b: Obdelnik): Obdelnik? {
        val x0 = (b.x0 + b.sirka * 0.03).toInt().coerceIn(0, w - 1)
        val x1 = (b.x1 - b.sirka * 0.03).toInt().coerceIn(x0 + 1, w)
        val y0 = b.y0.coerceAtLeast(0)
        val y1 = b.y1.coerceAtMost(h)
        if (y1 - y0 < 10) return null
        // Světlé úseky; úseky oddělené jen pár řádky (tenká čára, tučný řádek) patří k jednomu popisku —
        // jinak pás popisku Pepy 12 skončil před replikami (OCR 1. 10. 2026).
        val useky = mutableListOf<IntArray>()
        var od = -1
        for (y in y0..y1) {
            val svetly = y < y1 && run {
                var svetle = 0
                for (x in x0 until x1) {
                    val c = argb[y * w + x]
                    if (((c shr 16 and 0xFF) * 299 + (c shr 8 and 0xFF) * 587 + (c and 0xFF) * 114) / 1000 > 170) svetle++
                }
                svetle * 2 > (x1 - x0)
            }
            if (svetly && od < 0) od = y
            if (!svetly && od >= 0) { useky += intArrayOf(od, y); od = -1 }
        }
        val mezera = maxOf(4, b.vyska / 50)
        val slite = mutableListOf<IntArray>()
        for (u in useky) {
            val posl = slite.lastOrNull()
            if (posl != null && u[0] - posl[1] <= mezera) posl[1] = u[1] else slite += u.copyOf()
        }
        val nej = slite.maxByOrNull { it[1] - it[0] }
        val nejOd = nej?.get(0) ?: -1
        val nejDo = nej?.get(1) ?: -1
        // Pás musí být aspoň osmina políčka a ne celé políčko (to je světlý obrázek, ne popisek).
        if (nejOd < 0 || nejDo - nejOd < b.vyska / 8 || nejDo - nejOd > b.vyska * 0.9) return null
        return Obdelnik(b.x0, nejOd, b.x1, nejDo)
    }

    fun radky(panely: List<Obdelnik>): List<List<Obdelnik>> {
        val out = mutableListOf<MutableList<Obdelnik>>()
        var y0 = 0; var y1 = -1
        for (p in panely.sortedBy { it.y0 }) {
            val stred = (p.y0 + p.y1) / 2
            if (out.isNotEmpty() && stred in y0..y1) {
                out.last() += p; y1 = max(y1, p.y1)
            } else {
                out += mutableListOf(p); y0 = p.y0; y1 = p.y1
            }
        }
        return out.map { r -> r.sortedWith(compareBy({ it.x0 }, { it.y0 })) }
    }

    private fun poradi(panely: List<Obdelnik>) = radky(panely).flatten()

    private fun Obdelnik.obsahuje(o: Obdelnik) = o.x0 >= x0 && o.y0 >= y0 && o.x1 <= x1 && o.y1 <= y1 && o != this

    private fun prekryvX(a: Obdelnik, b: Obdelnik) = max(0, min(a.x1, b.x1) - max(a.x0, b.x0))

    /**
     * Barva mezer: medián řádků a sloupců, které jsou po celé délce skoro
     * jednolité (malý rozptyl). Bez takových pruhů to není mřížka panelů.
     */
    private fun barvaMezer(r: IntArray, g: IntArray, b: IntArray, w: Int, h: Int): IntArray? {
        val vzorky = mutableListOf<IntArray>()
        fun pruh(n: Int, px: (Int) -> Int) {
            var sr = 0.0; var sg = 0.0; var sb = 0.0
            for (k in 0 until n) { val i = px(k); sr += r[i]; sg += g[i]; sb += b[i] }
            val mr = sr / n; val mg = sg / n; val mb = sb / n
            var odch = 0.0
            for (k in 0 until n) { val i = px(k); odch += (r[i] - mr) * (r[i] - mr) + (g[i] - mg) * (g[i] - mg) + (b[i] - mb) * (b[i] - mb) }
            if (sqrt(odch / n / 3) < 10) vzorky += intArrayOf(mr.toInt(), mg.toInt(), mb.toInt())
        }
        for (y in 0 until h) pruh(w) { y * w + it }
        for (x in 0 until w) pruh(h) { it * w + x }
        if (vzorky.size < 2) run { duvod = "D8"; return null }
        fun med(k: Int) = vzorky.map { it[k] }.sorted()[vzorky.size / 2]
        return intArrayOf(med(0), med(1), med(2))
    }

    /** Souvislé oblasti obsahu (4-okolí), jako obdélníky. */
    private fun oblasti(obsah: BooleanArray, w: Int, h: Int): List<Obdelnik> {
        val videno = BooleanArray(w * h)
        val out = mutableListOf<Obdelnik>()
        val fronta = IntArray(w * h)
        for (start in 0 until w * h) {
            if (!obsah[start] || videno[start]) continue
            var hlava = 0; var konec = 0
            fronta[konec++] = start; videno[start] = true
            var x0 = w; var y0 = h; var x1 = 0; var y1 = 0; var pocet = 0
            while (hlava < konec) {
                val i = fronta[hlava++]
                val x = i % w; val y = i / w
                pocet++
                if (x < x0) x0 = x; if (x > x1) x1 = x; if (y < y0) y0 = y; if (y > y1) y1 = y
                if (x > 0 && obsah[i - 1] && !videno[i - 1]) { videno[i - 1] = true; fronta[konec++] = i - 1 }
                if (x < w - 1 && obsah[i + 1] && !videno[i + 1]) { videno[i + 1] = true; fronta[konec++] = i + 1 }
                if (y > 0 && obsah[i - w] && !videno[i - w]) { videno[i - w] = true; fronta[konec++] = i - w }
                if (y < h - 1 && obsah[i + w] && !videno[i + w]) { videno[i + w] = true; fronta[konec++] = i + w }
            }
            if (pocet >= 4) out += Obdelnik(x0, y0, x1 + 1, y1 + 1)
        }
        return out
    }

    /**
     * Políčko bez obsahu: vnitřek (bez okraje s rámečkem) má barvu pozadí listu.
     * Jednobarevný panel jiné barvy (zatmívačka, obloha) prázdný NENÍ.
     */
    private fun prazdne(o: Obdelnik, obsah: BooleanArray, w: Int): Boolean {
        val okraj = max(3, min(o.sirka, o.vyska) / 12)
        val xa = o.x0 + okraj; val xb = o.x1 - okraj; val ya = o.y0 + okraj; val yb = o.y1 - okraj
        if (xb - xa < 4 || yb - ya < 4) return false
        var plne = 0; var n = 0
        var y = ya
        while (y < yb) {
            var x = xa
            while (x < xb) { if (obsah[y * w + x]) plne++; n++; x += 2 }
            y += 2
        }
        return plne < n * 0.03
    }
}
