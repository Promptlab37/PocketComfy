package cz.promptlab.h3video.data

/**
 * Přísná kontrola hotového zadání úseku pro MiniMax H3 (5.67). Hlídá všechno, co dřív uživatel
 * našel až ve filmu: replika mimo svůj záběr nebo pozměněná, vymyšlená replika, česká věta mimo
 * `<d>` (H3 ji vysloví), chybějící pole, jiný počet záběrů než panelů, střihy mimo úsek,
 * `<Subject N>` bez definice. Vrací popisy chyb, prázdný seznam = v pořádku.
 */
object SbKontrolaPromptu {

    val POLE = listOf("subject_definitions", "summary", "retention_analysis", "detailed_description", "overall_soundscape", "non_diegetic_music")

    private val D = Regex("""<d>(.*?)</d>""", RegexOption.DOT_MATCHES_ALL)
    private val SHOT = Regex("""\[Shot\s*(\d+)]""")
    private val CAS = Regex("""\b(\d{1,2}):(\d{2}(?:\.\d+)?)""")

    fun zkontroluj(prompt: String, u: SbUsek): List<String> {
        val chyby = mutableListOf<String>()
        POLE.filter { !Regex("""(?m)^\s*$it\s*:""").containsMatchIn(prompt) }.forEach { chyby += t("chybí pole %s").format(it) }
        val iDd = prompt.indexOf("detailed_description:")
        if (iDd < 0) return chyby
        val konecDd = POLE.drop(4).map { prompt.indexOf("$it:", iDd) }.filter { it > 0 }.minOrNull() ?: prompt.length
        val dd = prompt.substring(iDd, konecDd)
        // Záběry: text od [Shot k] do dalšího.
        val znacky = SHOT.findAll(dd).toList()
        val zabery = znacky.mapIndexed { i, m -> dd.substring(m.range.first, znacky.getOrNull(i + 1)?.range?.first ?: dd.length) }
        if (zabery.size != u.panely.size) chyby += t("záběrů %d, panelů %d").format(zabery.size, u.panely.size)
        val cisla = znacky.map { it.groupValues[1].toInt() }
        if (cisla != (1..cisla.size).toList()) chyby += t("záběry nejsou očíslované popořadě")
        // Repliky: každá doslova v <d> ve svém záběru.
        u.panely.forEachIndexed { i, p ->
            val zaber = zabery.getOrNull(i) ?: return@forEachIndexed
            val dVZaberu = D.findAll(zaber).map { norm(bezJazyka(it.groupValues[1])) }.toList()
            SbFilmPrepis.repliky(p.repliky).forEach { (kdo, co) ->
                val x = norm(co)
                if (dVZaberu.none { it == x || it.contains(x) } && norm(dVZaberu.joinToString(" ")).contains(x).not())
                    chyby += t("záběr %d: chybí replika %s: „%s“").format(i + 1, kdo, co)
            }
        }
        // Vymyšlené repliky: každý <d> v záběrech musí být (část) repliky úseku.
        val vsechny = u.panely.flatMap { SbFilmPrepis.repliky(it.repliky).map { r -> norm(r.second) } }
        D.findAll(dd).map { bezJazyka(it.groupValues[1]) }.forEach { d ->
            val x = norm(d)
            if (x.isNotEmpty() && vsechny.none { it.contains(x) || x.contains(it) }) chyby += t("replika navíc: „%s“").format(d.trim())
        }
        // Česká věta v uvozovkách mimo <d> (H3 by ji vyslovil).
        val bezD = D.replace(prompt, " ")
        Regex("""[„"“]([^„“"”]{3,})[“"”]""").findAll(bezD).map { it.groupValues[1] }
            .filter { Regex("""[ěščřžýáíéůúťďňĚŠČŘŽÝÁÍÉŮÚŤĎŇ]""").containsMatchIn(it) }
            .forEach { chyby += t("česky mimo <d>: „%s“").format(it) }
        // Střihy: rostou a jsou uvnitř úseku.
        val casy = znacky.drop(1).mapIndexedNotNull { i, m ->
            CAS.find(zabery[i + 1].take(80))?.let { it.groupValues[1].toDouble() * 60 + it.groupValues[2].toDouble() }
        }
        if (casy.zipWithNext().any { (a, b) -> b <= a }) chyby += t("časy střihů nerostou")
        if (casy.any { it >= u.sekundy }) chyby += t("střih za koncem úseku")
        // <Subject N> jen definované.
        val iSd = prompt.indexOf("subject_definitions:")
        val iSum = prompt.indexOf("summary:")
        if (iSd >= 0 && iSum > iSd) {
            val def = Regex("""<Subject\s*(\d+)>""").findAll(prompt.substring(iSd, iSum)).map { it.groupValues[1] }.toSet()
            Regex("""<Subject\s*(\d+)>""").findAll(prompt.substring(iSum)).map { it.groupValues[1] }.toSet()
                .filter { it !in def }.forEach { chyby += t("<Subject %s> bez definice").format(it) }
        }
        return chyby
    }

    private fun bezJazyka(t: String) = t.replace(Regex("""^\s*\[[^\]]*]\s*"""), "")

    /** Porovnání replik: uvozovky, mezery, trojtečka, apostrof místo háčku; diakritika a slova přesně. */
    fun norm(t: String) = SbFilmPrepis.opravHacky(t).replace("…", "...")
        .replace(Regex("""[„“”"«»]"""), "").replace('‘', '\'').replace('’', '\'')
        .replace(Regex("""\s+"""), " ").trim().lowercase()
}
