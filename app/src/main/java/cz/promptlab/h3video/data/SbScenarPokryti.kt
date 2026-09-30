package cz.promptlab.h3video.data

/**
 * Kontrola „každá věta ze scénáře někam patří“ (5.27, odborníci 30. 9. 2026).
 *
 * Rozbor scénáře může selhat tiše — replika „Keramička, spokojeně: „…““ se
 * dřív ztratila a záběr byl tichý, postavy spadly do kontinuity. Tahle
 * kontrola nezávisí na tom, JAK je scénář zapsaný:
 *  - každá věta zdroje musí mít svá slova v rozboru SVÉHO okna (nebo v tom,
 *    co platí pro celý film) — slova odjinud se nepočítají;
 *  - text v uvozovkách se jménem mluvčího před sebou musí být replikou nebo
 *    textem na videu toho okna, ne jen poznámkou — jinak by záběr byl tichý.
 * Co zbude, zastaví přípravu a ukáže se — nikdy se tiše nezahodí.
 */
object SbScenarPokryti {

    /** Slova, která nesou jen formu (štítky, spojky) — do pokrytí se nepočítají. */
    private val FORMA: Set<String> = (
        SbScenar.STITKY.flatMap { it.first.split(Regex("""[\s-]+""")) } + listOf(
            "okno", "okna", "panel", "záběr", "zaber", "scéna", "scena", "shot", "scene", "window", "postavy",
            "rekvizity", "kontinuita", "důležitá", "formát", "format", "prostředí", "styl", "style", "tón", "tone",
            "cíl", "goal", "délka", "length", "duration", "obsazení", "místo", "děje", "setting", "cast", "props",
            "characters", "character", "continuity", "the", "and", "with", "pro", "při", "jako", "nebo", "než", "ale",
            "aby", "které", "který", "která", "jsou", "jeho", "její", "jejich", "sekund", "přibližně", "about",
            "seconds", "doplnit", "střihu", "videu", "videa", "obrazovce", "none", "žádný", "žádná", "žádné", "nic",
            "bez", "dialogu", "zvuku",
        )
        ).map { it.lowercase() }.filter { it.length >= 3 }.toSet()

    private fun slova(t: String): List<String> =
        Regex("""\p{L}{3,}""").findAll(t.lowercase()).map { it.value }.filter { it !in FORMA }.toList()

    private fun norm(t: String) = t.lowercase().replace(Regex("""[„“”"«».,!?…:;–—-]"""), " ").replace(Regex("""\s+"""), " ").trim()

    /** Slova toho, co platí pro celý film (název, postavy, kontinuita). */
    private fun pytelFilmu(c: SbScenarCteni): Set<String> =
        (listOf(c.nazev, c.kontinuita) + c.postavy.flatMap { (k, v) -> listOf(k, v) }).flatMap { slova(it) }.toSet()

    private fun pytelOkna(o: SbOkno): Set<String> {
        val casti = mutableListOf(o.obraz, o.akce, o.emoce, o.kamera, o.zvuk, o.vyzva, o.typ)
        casti += o.texty
        casti += o.poznamky
        o.repliky.forEach { r -> casti += r.kdo; casti += r.podani; casti += r.text }
        return casti.flatMap { slova(it) }.toSet()
    }

    /**
     * Věty zdroje, které rozbor nezařadil. Záhlaví oken, nadpisy velkými
     * písmeny, řádek s formátem a prázdná pole („Zvuk: žádný“) se nepočítají.
     */
    fun nepokryte(vstup: String, c: SbScenarCteni): List<String> {
        val radky = SbScenar.uprav(vstup).lines()
        // Řádky do bloků podle oken (záhlaví okna na začátku řádku); blok 0 = hlavička.
        var blok = 0
        val bloky = radky.map { r ->
            if (SbScenar.ZNACKA.find(r)?.let { m -> r.substring(0, m.range.first).isBlank() } == true) ++blok else blok
        }
        val poOknech = blok == c.okna.size && blok > 0
        val filmu = pytelFilmu(c)
        val vse = filmu + c.okna.flatMap { pytelOkna(it) }
        val out = mutableListOf<String>()
        val prvniNeprazdny = radky.indexOfFirst { it.isNotBlank() }
        radky.forEachIndexed { i, r0 ->
            var r = r0.trim()
            if (r.isEmpty()) return@forEachIndexed
            // Prázdné pole („Zvuk: žádný“, „Dialog – žádný“) rozbor zahodí záměrně.
            if (SbScenar.PRAZDNE_POLE.containsMatchIn(r) || (r.contains(':') && SbScenar.jeNic(r.substringAfter(':')))) return@forEachIndexed
            // Záhlaví okna: obsah za ním se kontroluje.
            SbScenar.ZNACKA.find(r)?.takeIf { it.range.first == 0 }?.let { m ->
                val zbytek = r.substring(m.range.last + 1)
                val za = zbytek.substringAfter(']', zbytek)
                r = if (SbScenar.jeNadpis(za)) "" else za
            }
            if (r.isBlank()) return@forEachIndexed
            if (r.none { it.isLowerCase() }) return@forEachIndexed
            if (i == prvniNeprazdny && !r.contains(':')) return@forEachIndexed
            if (Regex("""(?iu)^(formát|format|délka|length|duration)\s*:""").containsMatchIn(r)) return@forEachIndexed
            val okno = if (poOknech && bloky[i] > 0) c.okna[bloky[i] - 1] else null
            val pytel = if (okno != null) filmu + pytelOkna(okno) else vse
            SbScenarModel.vetyMimoUvozovky(r).forEach { v ->
                // Záhlaví okna „1. Příchod (0–3 s)“: čas + nejvýš 4 slova.
                if (SbScenar.cas(v) != null &&
                    v.replace(Regex("""[\d:.,–—\-()]+"""), " ").trim().split(Regex("""\s+""")).filter { it != "s" }.size <= 4
                ) return@forEach
                val s = slova(v)
                if (s.isNotEmpty() && s.count { it !in pytel } * 2 > s.size) { out += v.trim(); return@forEach }
                // Replika se jménem před uvozovkou musí být replikou nebo textem, ne jen poznámkou.
                if (okno != null) {
                    val vyslovene = (okno.repliky.map { it.text } + okno.texty + okno.vyzva).map { norm(it) }
                    Regex("""(?<![\p{L}\d])\p{Lu}[\p{L}\d]{1,20}[^„“"«\n:]{0,40}[:–—-]\s*[„“"«]([^„“"«»”\n]{2,300})[“”"»]""").findAll(v).forEach { m ->
                        val q = norm(m.groupValues[1])
                        if (vyslovene.none { it == q || (q.length > 3 && it.contains(q)) }) out += v.trim()
                    }
                }
            }
        }
        return out.distinct()
    }
}
