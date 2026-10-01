package cz.promptlab.h3video.data

import java.util.Locale

/**
 * Jeden průchod přepisovače stačí (5.67, uživatel: „aby to po jednom průchodu udělal správně“).
 * Repliky a časy střihů appka zná přesně předem — přepisovač je jen opisuje a právě tam chybuje
 * (pozměněné slovo, replika v cizím záběru, vymyšlená replika, jiný čas). Tady se v kódu, za zlomek
 * sekundy, vynutí to, co je dané: počet a pořadí záběrů, čas každého střihu a doslovné repliky
 * v jejich záběru. Popis a herecké podání zůstávají od přepisovače.
 * Úpravy podle rady odborníků (1. 10. 2026): záběry navíc pryč a jako nález, replika navíc pryč i s větou,
 * v tichém záběru pryč věty o mluvení a rtech, chybějící replika na začátek záběru ve tvaru
 * „<Subject K> (S1) says: <d>…</d>“ (české jméno mimo <d> by H3 mohl vyslovit).
 */
object SbVynuceni {

    private val SHOT = Regex("""\[Shot\s*(\d+)]""")
    private val D = Regex("""<d>(.*?)</d>""", RegexOption.DOT_MATCHES_ALL)
    private val AT = Regex("""\bAt\s+\d{1,2}:\d{2}(?:\.\d+)?""")
    private val KONEC_DD = listOf("overall_soundscape", "non_diegetic_music")
    private val VETA = Regex("""(?<=[.!?])\s+(?=[A-Z<\[(])""")
    private val MLUVENI = Regex("""\b(says|said|speaks|speaking|spoken|asks|replies|answers|adds|line|lines|lips|whispers|shouts)\b""", RegexOption.IGNORE_CASE)
    private val TICHO = Regex("""\b(silent|silence|no dialogue)\b""", RegexOption.IGNORE_CASE)
    private const val SMAZAT = "\u0000SMAZAT\u0000"

    /** Co vynucení změnilo — záběry navíc se zahazují a hlásí jako nález. */
    data class Vysledek(val prompt: String, val zahozeneZabery: Int = 0)

    fun cas(s: Double): String {
        val ms = Math.round(s * 1000)
        return String.format(Locale.ROOT, "%02d:%02d.%03d", ms / 60000, (ms / 1000) % 60, ms % 1000)
    }

    fun vynut(prompt: String, u: SbUsek, jazyk: String?, idMluvcich: Map<String, String>): String =
        vynutS(prompt, u, jazyk, idMluvcich).prompt

    fun vynutS(prompt: String, u: SbUsek, jazyk: String?, idMluvcich: Map<String, String>): Vysledek {
        val iDd = prompt.indexOf("detailed_description:")
        if (iDd < 0 || u.panely.isEmpty()) return Vysledek(prompt)
        // (Sx) → <Subject K>, jak je přepisovač spojil („<Subject 1> (S1)“).
        val subjekt = Regex("""<Subject\s*(\d+)>\s*\((S\d+)\)""").findAll(prompt)
            .associate { it.groupValues[2] to "<Subject ${it.groupValues[1]}>" }
        val konec = KONEC_DD.map { prompt.indexOf("$it:", iDd) }.filter { it > 0 }.minOrNull() ?: prompt.length
        val dd = prompt.substring(iDd, konec)
        val znacky = SHOT.findAll(dd).toList()
        val uvod = if (znacky.isEmpty()) dd.trimEnd() + "\n" else dd.substring(0, znacky.first().range.first)
        var zabery = znacky.mapIndexed { i, m ->
            dd.substring(m.range.last + 1, znacky.getOrNull(i + 1)?.range?.first ?: dd.length).trim()
        }
        // Víc záběrů než panelů: přebytek pryč a nález — přilepený by dal střih bez značky a akci z cizího panelu.
        val zahozene = (zabery.size - u.panely.size).coerceAtLeast(0)
        if (zahozene > 0) zabery = zabery.take(u.panely.size)
        var t = 0.0
        val vystup = StringBuilder(uvod)
        u.panely.forEachIndexed { k, p ->
            var text = zabery.getOrNull(k)
                // Chybějící záběr: z popisu panelu (anglicky ze čtení storyboardu).
                ?: "The shot cuts to a ${p.typ.ifBlank { "medium" }} view. ${bezPoznamek(p.popis)}"
            // Čas střihu: první záběr žádný, další přesně podle plánu.
            text = if (k == 0) text.replaceFirst(AT, "").replace(Regex("""^\s*,\s*"""), "")
                .replaceFirst(Regex("""^the shot cuts to\s*""", RegexOption.IGNORE_CASE), "").replaceFirstChar { it.uppercaseChar() }
            else if (AT.containsMatchIn(text.take(120))) text.replaceFirst(AT, "At ${cas(t)}")
            else "At ${cas(t)}, " + text.replaceFirstChar { it.lowercaseChar() }
            text = repliky(text, SbFilmPrepis.repliky(p.repliky), jazyk, idMluvcich, subjekt)
            vystup.append("[Shot ${k + 1}] ").append(text.trim()).append("\n")
            t += p.sekundy
        }
        return Vysledek(prompt.substring(0, iDd) + vystup.toString().trimEnd() + "\n\n" + prompt.substring(konec).trimStart(), zahozene)
    }

    /** Repliky záběru přesně podle storyboardu; chybějící se doplní, navíc se odstraní i s větou. */
    private fun repliky(
        zaber: String, ocek: List<Pair<String, String>>, jazyk: String?, id: Map<String, String>, subjekt: Map<String, String>,
    ): String {
        val jazykZnacka = jazyk ?: D.find(zaber)?.groupValues?.get(1)?.let { Regex("""^\s*\[([^\]]+)]""").find(it)?.groupValues?.get(1) }
        fun dTag(text: String) = "<d>" + (jazykZnacka?.let { "[$it] " } ?: "") + text + "</d>"
        // Přiřazení podle podobnosti textu, ne podle pořadí: u dvou mluvčích v jednom panelu by pořadí
        // mohlo dát repliku k cizímu (Sx). Nejpodobnější dvojice první.
        val nalezene = D.findAll(zaber).toList()
        val dvojice = nalezene.indices.flatMap { a -> ocek.indices.map { b -> Triple(a, b, podobnost(nalezene[a].groupValues[1], ocek[b].second)) } }
            .sortedByDescending { it.third }
        val prirazeni = HashMap<Int, Int>()
        val pouzite = HashSet<Int>()
        for ((a, b, _) in dvojice) if (a !in prirazeni && b !in pouzite) { prirazeni[a] = b; pouzite += b }
        var poradi = 0
        var out = D.replace(zaber) { _ ->
            val b = prirazeni[poradi++]
            if (b != null) dTag(ocek[b].second) else SMAZAT
        }
        var vety = out.split(VETA).filterNot { SMAZAT in it }
        // Záběr bez repliky: pryč věty o mluvení a rtech — H3 by hýbal rty v tichu.
        if (ocek.isEmpty()) vety = vety.filterNot { "<d>" !in it && MLUVENI.containsMatchIn(it) && !TICHO.containsMatchIn(it) }
        out = vety.joinToString(" ").replace(Regex("""\s{2,}"""), " ").trim()
        // Chybějící replika hned za první větu záběru (zadání chce repliku na začátku záběru).
        val chybi = ocek.filterIndexed { b, _ -> b !in pouzite }
        if (chybi.isNotEmpty()) {
            val vlozit = chybi.joinToString(" ") { (kdo, co) ->
                val sid = id[kdo]
                val kdoText = listOfNotNull(sid?.let { subjekt[it] }, sid?.let { "($it)" }).joinToString(" ").ifBlank { "The speaker" }
                "$kdoText says: ${dTag(co)}"
            }
            val v = out.split(VETA)
            out = (listOf(v.first()) + vlozit + v.drop(1)).joinToString(" ")
        }
        return out
    }

    /** Podíl společných slov (bez diakritiky a interpunkce), 0–1. */
    private fun podobnost(a: String, b: String): Double {
        fun slova(t: String) = java.text.Normalizer.normalize(t.replace(Regex("""^\s*\[[^\]]*]"""), "").lowercase(), java.text.Normalizer.Form.NFD)
            .replace(Regex("""\p{M}"""), "").split(Regex("""[^\p{L}\p{N}]+""")).filter { it.isNotEmpty() }.toSet()
        val x = slova(a); val y = slova(b)
        if (x.isEmpty() || y.isEmpty()) return 0.0
        return (x intersect y).size.toDouble() / (x union y).size
    }

    private fun bezPoznamek(popis: String) = popis.replace(Regex("""\s*(Mood|Sound):[^.]*\.?"""), "").trim()
}
