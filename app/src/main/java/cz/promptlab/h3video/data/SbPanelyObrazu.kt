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
     */
    fun najdiBunky(argb: IntArray, w: Int, h: Int): List<Bunka>? {
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
