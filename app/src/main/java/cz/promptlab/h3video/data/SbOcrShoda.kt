package cz.promptlab.h3video.data

import java.text.Normalizer

/**
 * Oprava replik podle OCR (5.67). Vidoucí model „spisovně opravuje“ krátká a nespisovná slova
 * („celej“ → „celý“, překlep „nabiječku“ → „nabíječku“) a dělá to ve všech čteních stejně, takže
 * hlasování chybu nevidí. Tesseract (uzel H3TesseractOCR_v1) jazyk neodhaduje a opisuje znak po znaku:
 * na sadě storyboardů 41/43 replik přesně (měření 1. 10. 2026). Struktura (kdo mluví, která věta je
 * replika) zůstává od vidoucího modelu, OCR rozhoduje jen o tvaru jednotlivých slov.
 */
object SbOcrShoda {

    data class Oprava(val z: String, val na: String)

    /** Kostra slova: malá písmena bez diakritiky a interpunkce. */
    fun kostra(s: String): String = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("""\p{M}"""), "").replace(Regex("""[^\p{L}\p{N}]"""), "")

    private fun lev(a: String, b: String): Int {
        val d = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = d[0]; d[0] = i
            for (j in 1..b.length) {
                val t = d[j]
                d[j] = minOf(d[j] + 1, d[j - 1] + 1, prev + if (a[i - 1] == b[j - 1]) 0 else 1)
                prev = t
            }
        }
        return d[b.length]
    }

    /** Stejné slovo podle kostry; delší slova snesou jednu odchylku („neposlouchá“ / „neposluchá“). */
    private fun stejne(a: String, b: String): Boolean {
        val x = kostra(a); val y = kostra(b)
        if (x.isEmpty() || y.isEmpty()) return false
        return x == y || (minOf(x.length, y.length) >= 6 && lev(x, y) <= 1)
    }

    /**
     * Smí OCR slovo nahradit slovo vidoucího modelu? Jen u rozdílů, které model dělá „spisovným
     * opravováním“: diakritika (nabíječku/nabiječku), jedno vynechané či přidané písmeno
     * (neposlouchá/neposluchá), jiná koncovka (celý/celej). Jiné písmeno uvnitř slova je chyba
     * čtení OCR (odpověď/odnověď, byla/hvla — Wellness 1. 10. 2026) a slovo velkými písmeny je
     * nápis v obrázku nebo štítek (ELEKTRONIKA — Letiště).
     */
    private fun prijmout(v: String, o: String): Boolean {
        if (o.any { it.isLetter() } && o.filter { it.isLetter() }.all { it.isUpperCase() } && o.length > 1 &&
            !v.filter { it.isLetter() }.all { it.isUpperCase() }) return false
        val kv = kostra(v); val ko = kostra(o)
        if (kv.isEmpty() || ko.isEmpty()) return false
        if (kv == ko) return true
        val d = lev(kv, ko)
        if (d == 1 && kv.length != ko.length && minOf(kv.length, ko.length) >= 5) return true
        val prefix = kv.zip(ko).takeWhile { (a, b) -> a == b }.size
        return d <= 2 && prefix >= 3 && prefix >= minOf(kv.length, ko.length) - 2
    }

    /** I a l jsou v bezpatkovém písmu stejné (AI / Al) — takový rozdíl OCR nerozhodne. */
    private fun jenIl(a: String, b: String) =
        a.length == b.length && a.zip(b).all { (x, y) -> x == y || setOf(x, y) == setOf('I', 'l') }

    private val SLOVO = Regex("""[\p{L}\p{N}'’]+""")

    /**
     * Text repliky [text] opravený podle OCR textu panelu [ocr]. Slova se zarovnají (nejdelší společná
     * posloupnost podle kostry); spárované slovo s jiným tvarem dostane tvar z OCR. Jedno nespárované
     * slovo mezi dvěma spárovanými se nahradí jedním slovem OCR na stejném místě („ušetřili celý večer“
     * × „ušetřili celej večer“). Interpunkce a mezery repliky zůstávají.
     */
    fun oprav(text: String, ocr: String): Pair<String, List<Oprava>> {
        val v = SLOVO.findAll(text).toList()
        val o = SLOVO.findAll(ocr).map { it.value }.toList()
        if (v.isEmpty() || o.isEmpty()) return text to emptyList()
        // LCS podle stejne().
        val n = v.size; val m = o.size
        val dp = Array(n + 1) { IntArray(m + 1) }
        for (i in n - 1 downTo 0) for (j in m - 1 downTo 0)
            dp[i][j] = if (stejne(v[i].value, o[j])) dp[i + 1][j + 1] + 1 else maxOf(dp[i + 1][j], dp[i][j + 1])
        val par = arrayOfNulls<Int>(n)
        var i = 0; var j = 0
        while (i < n && j < m) {
            if (stejne(v[i].value, o[j]) && dp[i][j] == dp[i + 1][j + 1] + 1) { par[i] = j; i++; j++ }
            else if (dp[i + 1][j] >= dp[i][j + 1]) i++ else j++
        }
        // Málo shody = OCR četl jiný text (jiný panel, šum) — nic neměnit.
        val sparovano = par.count { it != null }
        if (sparovano * 2 < n) return text to emptyList()
        // Mezera přesně o jedno slovo mezi spárovanými sousedy.
        for (k in 1 until n - 1) {
            val a = par[k - 1]; val b = par[k + 1]
            if (par[k] == null && a != null && b != null && b - a == 2) par[k] = a + 1
        }
        val opravy = mutableListOf<Oprava>()
        val sb = StringBuilder()
        var pos = 0
        v.forEachIndexed { k, m0 ->
            sb.append(text, pos, m0.range.first)
            val ocrSlovo = par[k]?.let { o[it] }
            val nove = if (ocrSlovo != null && ocrSlovo != m0.value && !jenIl(m0.value, ocrSlovo) && prijmout(m0.value, ocrSlovo) &&
                // Velikost písmen drží replika (OCR čte i jmenovky a štítky velkými).
                !(ocrSlovo.equals(m0.value, ignoreCase = true))
            ) ocrSlovo.let { if (m0.value.first().isUpperCase()) it.replaceFirstChar { c -> c.uppercaseChar() } else it }
            else m0.value
            // „Ted'“ a „Teď“ je totéž písmeno jinak zapsané — žádná oprava.
            if (nove != m0.value && SbFilmPrepis.opravHacky(m0.value) != SbFilmPrepis.opravHacky(nove)) opravy += Oprava(m0.value, nove)
            sb.append(nove)
            pos = m0.range.last + 1
        }
        sb.append(text.substring(pos))
        return sb.toString() to opravy
    }

    /** Repliky panelu (`Kdo: "…"; …`) opravené podle OCR — jména mluvčích se nemění. */
    fun opravRepliky(repliky: String, ocr: String): Pair<String, List<Oprava>> {
        val r = SbFilmPrepis.repliky(repliky)
        if (r.isEmpty()) return repliky to emptyList()
        val vse = mutableListOf<Oprava>()
        val nove = r.joinToString("; ") { (kdo, co) ->
            val (t, op) = oprav(co, ocr)
            vse += op
            "$kdo: \"$t\""
        }
        return (if (vse.isEmpty()) repliky else nove) to vse
    }
}
