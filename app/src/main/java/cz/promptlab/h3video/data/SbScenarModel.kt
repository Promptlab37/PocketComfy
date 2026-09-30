package cz.promptlab.h3video.data

/**
 * Druhé čtení scénáře lokálním jazykovým modelem (5.27, odborníci 30. 9. 2026).
 *
 * Model scénář NEPŘEPISUJE — věty dostane očíslované (U1…Un) a ke každé jen
 * řekne, co je zač (replika, popis, text do střihu, postava…), ve kterém
 * okně je a kdo mluví. Text repliky se bere ze scénáře podle čísla, takže se
 * nemůže přeložit, zkrátit ani vymyslet. Výsledek se porovná s rozborem
 * aplikace ([SbScenar.rozeber]); když se liší, uživatel vybere.
 */
object SbScenarModel {

    data class Jednotka(val id: Int, val text: String)

    enum class Druh { TITLE, FORMAT, CAST, SETTING, CONTINUITY, WINDOW, WINDOWTITLE, PICTURE, ACTION, EMOTION, CAMERA, DIALOGUE, SOUND, MUSIC, ONSCREEN, CTA, NOTE }

    data class Stitek(val druh: Druh, val okno: Int, val kdo: String, val jak: String)

    /** Věty scénáře s čísly — řádek, a v něm věty (řádek „Zvuk: šustění. Syn tiše: „…““ má dvě). */
    fun jednotky(scenar: String): List<Jednotka> {
        var id = 0
        var text = SbScenar.uprav(scenar)
        // Scénář slitý do jednoho řádku: značky oken na vlastní řádky (jako rozbor appky).
        if (SbScenar.ZNACKA.findAll(text).count() < 2) {
            val rozdeleny = text.replace(ZNACKA_V_RADKU, "\n")
            if (SbScenar.ZNACKA.findAll(rozdeleny).count() >= 2) text = rozdeleny
        }
        // Okna jen číslovaná („1. Děda brousí…“): číslo zvlášť, aby obsah nešel za záhlaví.
        val cislovane = SbScenar.ZNACKA.findAll(text).count() < 2 && SbScenar.ZNACKA_CISLO.findAll(text).count() >= 2
        return text.lines().map { it.trim() }.filter { it.isNotBlank() }
            .flatMap { r ->
                val m = if (cislovane) SbScenar.ZNACKA_CISLO.find(r)?.takeIf { it.range.first == 0 } else null
                val zbytek = if (m == null) "" else r.substring(m.range.last + 1).trim()
                // „1. Příchod (0–3 s)“ je celé záhlaví (čas nebo krátký název) — nedělit.
                if (m == null || zbytek.isBlank() || SbScenar.cas(zbytek) != null || SbScenar.jeNadpis(zbytek)) listOf(r)
                else listOf(r.substring(0, m.range.last + 1).trim(), zbytek)
            }
            .flatMap { r -> oddelZahlavi(r) }
            .flatMap { r -> if (jeRadekRepliky(r)) listOf(r) else vetyMimoUvozovky(r).ifEmpty { listOf(r) } }
            .map { Jednotka(++id, it) }
    }

    /** Celý řádek je replika bez uvozovek („JOHN: Meet me at 5:30.“) — nedělit. */
    private fun jeRadekRepliky(r: String): Boolean {
        val m = SbScenar.RADEK_MLUVCI.find(r) ?: return false
        val jm = m.groupValues[1].trim()
        return jm.length >= 2 && jm == jm.uppercase() && jm.any { it.isLetter() } && !r.contains('„') && !r.contains('"') &&
            jm.lowercase() !in SbScenar.STITKY_JMENA
    }

    private val ZNACKA_V_RADKU = Regex(
        """(?<![\p{L}\d])(?=\[?(?:OKNO|PANEL|ZÁBĚR|ZABER|SCÉNA|SCENA|SHOT|SCENE|WINDOW|Okno|Panel|Záběr|Scéna|Shot|Scene)\s+\d{1,2}(?![\d:.,]\d))""",
    )

    /** „ZÁBĚR 1 (0–3 s): Obraz: Jana vejde…“ → záhlaví a obsah zvlášť (jinak model obsah zahodí jako záhlaví). */
    private fun oddelZahlavi(r: String): List<String> {
        val m = SbScenar.ZNACKA.find(r)?.takeIf { it.range.first == 0 } ?: return listOf(r)
        val zbytek = r.substring(m.range.last + 1)
        // Konec záhlaví: „]“, nebo čas a za ním dvojtečka/závorka.
        val konec = zbytek.indexOf(']').takeIf { it >= 0 && !zbytek.substring(0, it).contains('„') }
            ?: SbScenar.CAS.find(zbytek)?.let { c ->
                val za = Regex("""^[ \t]*\)?[ \t]*[:.]?""").find(zbytek.substring(c.range.last + 1))?.value?.length ?: 0
                c.range.last + za
            }
            ?: return listOf(r)
        val hlav = r.substring(0, m.range.last + 1 + konec + 1).trim()
        val obsah = zbytek.substring(konec + 1).trim().trimStart(':', '.', '–', '—', '-').trim()
        // Krátký název okna za záhlavím („NÁPAD“) zůstává se záhlavím.
        return if (obsah.isBlank() || SbScenar.jeNadpis(obsah)) listOf(r) else listOf(hlav, obsah)
    }

    /**
     * Věty řádku. Uvnitř uvozovek se nedělí („Začneme detailem. Pak ukážeme…“ je
     * jedna replika); dělí se ZA uzavřenou replikou („„Pro tebe.“ Vnuk: „Děkuju!““
     * jsou dvě); pořadové číslo „1.“ a iniciála „O. Henry“ konec věty nejsou.
     */
    fun vetyMimoUvozovky(r: String): List<String> {
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        var uvnitr = false
        var i = 0
        while (i < r.length) {
            val ch = r[i]
            sb.append(ch)
            var zavrena = false
            when (ch) {
                '„', '«' -> uvnitr = true
                '“', '”', '»' -> if (uvnitr) { uvnitr = false; zavrena = true } else if (ch == '“') uvnitr = true
                '"' -> { if (uvnitr) zavrena = true; uvnitr = !uvnitr }
            }
            val inicialy = ch == '.' && i >= 1 && r[i - 1].isUpperCase() && (i < 2 || !r[i - 2].isLetter())
            val poradi = ch == '.' && sb.toString().trim().matches(Regex("""\d{1,2}\."""))
            val konecVety = !uvnitr && !inicialy && !poradi && (ch in ".!?…" || zavrena) &&
                i + 1 < r.length && r[i + 1].isWhitespace()
            if (konecVety) {
                var j = i + 1
                while (j < r.length && r[j].isWhitespace()) j++
                if (j < r.length && (r[j].isUpperCase() || r[j].isDigit() || r[j] in "„“\"")) {
                    out += sb.toString().trim(); sb.clear(); i = j; continue
                }
            }
            i++
        }
        if (sb.isNotBlank()) out += sb.toString().trim()
        return out
    }

    const val SYSTEM =
        "You label the parts of a film screenplay written in any language and any layout. Every input line starts " +
            "with an ID like U12. Answer with exactly one output line per input line, in the same order, nothing else:\n" +
            "ID | KIND | SPEAKER | DELIVERY\n" +
            "KIND is one of: TITLE (title of the film), FORMAT (aspect ratio or length), CAST (describes a character or a " +
            "prop), SETTING (place and time of the whole film), CONTINUITY (a rule for the whole film), WINDOW (the heading that " +
            "starts a new window, shot, scene or panel: its number, maybe with a name and a time), WINDOWTITLE (a " +
            "separate line with only the name of a window, without a number), PICTURE (what we see), ACTION " +
            "(what happens), EMOTION (feelings or mood), CAMERA (camera or shot size), DIALOGUE (a line that a character " +
            "says aloud), SOUND (sound effects), MUSIC (music), ONSCREEN (text shown on screen or added in the edit: captions, titulky, titles, overlays, slogans), CTA " +
            "(closing call to action), NOTE (instruction for the editor or anything else).\n" +
            "SPEAKER and DELIVERY only for DIALOGUE: who says it, exactly as named in the screenplay, and " +
            "how (the delivery words as written, for example quietly); otherwise -. Never copy or translate the text " +
            "of the lines."

    fun zadani(j: List<Jednotka>): String = j.joinToString("\n") { "U${it.id}: ${it.text}" }

    /**
     * Zadání pro ChatGPT (tlačítko „Zadání pro ChatGPT“, 5.27): scénář pak přijde
     * v tvaru, který appka rozebere hned a jistě. Ostatní zápisy fungují taky.
     */
    val ZADANI_CHATGPT_CS = """
        Napiš scénář k mému obrázkovému storyboardu přesně v tomto tvaru a nic jiného nepiš:

        STORYBOARD – <název>
        Formát: <9:16 nebo 16:9>, přibližně <počet> sekund.
        Postavy:
        <Jméno>: <věk> let, <vzhled: vlasy, oblečení a jeho barvy>
        Rekvizity:
        <Název>: <jak vypadá>
        Kontinuita: <co musí zůstat v celém filmu stejné>

        [OKNO 1 | 0–4 s] <krátký název okna>
        Obraz: <co je vidět a co se děje, jedna až dvě věty>
        Emoce: <nálada>
        <Jméno> (<jak to říká>): „<replika přesně tak, jak se má říct>“
        Zvuk: <zvuky>
        Text na videu: „<text, jen když má být>“

        Pravidla:
        - Jedno okno = jeden panel storyboardu, ve stejném pořadí a se stejným číslem.
        - Repliky jen na vlastním řádku se jménem mluvčího a v uvozovkách „…“, nikdy v popisu obrazu.
        - Texty na obrazovku jen na řádku „Text na videu“, nikdy v popisu obrazu.
        - Časy na sebe navazují; okno trvá 2–8 s, okno s replikou tak dlouho, aby se stihla říct.
        - Když se vzhled postavy během filmu mění, napiš to u postavy, např. „od okna 5 krátké vlasy“.
        - Řádky, které okno nemá, vynech.
    """.trimIndent()

    val ZADANI_CHATGPT_EN = """
        Write a screenplay for my picture storyboard in exactly this layout and nothing else:

        STORYBOARD – <title>
        Format: <9:16 or 16:9>, about <number> seconds.
        Characters:
        <Name>: <age> years, <look: hair, clothes and their colours>
        Props:
        <Name>: <what it looks like>
        Continuity: <what must stay the same in the whole film>

        [WINDOW 1 | 0–4 s] <short window name>
        Visual: <what we see and what happens, one or two sentences>
        Emotion: <mood>
        <Name> (<how they say it>): "<the line exactly as it should be spoken>"
        Sound: <sounds>
        On-screen text: "<text, only when there is one>"

        Rules:
        - One window = one storyboard panel, in the same order and with the same number.
        - Spoken lines only on their own line with the speaker's name and in quotes, never in the visual description.
        - On-screen texts only on the "On-screen text" line, never in the visual description.
        - Times follow each other; a window lasts 2–8 s, a window with a line long enough to say it.
        - When a character's look changes during the film, say so with the character, e.g. "short hair from window 5".
        - Leave out lines a window doesn't have.
    """.trimIndent()

    /** Odpověď → štítky podle čísla věty. Null, když chybí věta nebo je odpověď nečitelná. */
    fun stitky(odpoved: String, j: List<Jednotka>): Map<Int, Stitek>? {
        val out = mutableMapOf<Int, Stitek>()
        odpoved.replace(Regex("""\s*(?=\bU\d+\s*\|)"""), "\n").lines().forEach { r ->
            val c = r.split("|").map { it.trim() }
            if (c.size < 2) return@forEach
            val id = Regex("""^U(\d+)$""").find(c[0])?.groupValues?.get(1)?.toIntOrNull() ?: return@forEach
            val druh = runCatching { Druh.valueOf(c[1].uppercase()) }.getOrNull() ?: return@forEach
            // Mluvčí a podání: první dvě smysluplná pole za druhem (model občas vloží „-“ nebo číslo navíc).
            val pole = c.drop(2).filterNot { it.isBlank() || it == "-" || it.equals("none", true) || it.all { ch -> ch.isDigit() } }
            out[id] = Stitek(druh, 0, pole.getOrNull(0).orEmpty(), pole.getOrNull(1).orEmpty())
        }
        // Okno se počítá ze záhlaví oken v pořadí — model ho nemusí vyplňovat.
        var okno = 0
        j.forEach { u ->
            val s = out[u.id] ?: return@forEach
            // Záhlaví s číslem je začátek okna, i když ho model označí jen jako název okna
            // („Okno 1 — Nález (0–4 s)“ → WINDOWTITLE, svatba 30. 9. 2026).
            // Záhlaví okna má vždy číslo — „Jimův dárek: …“ označené jako okno (Dar mudrců) okno není.
            val maCislo = Regex("""\d""").containsMatchIn(u.text)
            val zacatek = (s.druh == Druh.WINDOW || s.druh == Druh.WINDOWTITLE) && maCislo
            if (zacatek) okno++
            val druh = when {
                zacatek -> Druh.WINDOW
                s.druh == Druh.WINDOW -> if (okno == 0) Druh.CAST else Druh.ACTION
                else -> s.druh
            }
            out[u.id] = s.copy(druh = druh, okno = okno)
        }
        // Každá věta musí mít štítek; okna nesmí jít pozpátku.
        if (j.any { it.id !in out }) return null
        var posledni = 0
        j.forEach { u -> val o = out.getValue(u.id).okno; if (o < posledni) return null; posledni = o }
        return out
    }

    private val STITEK_NA_ZACATKU = Regex("""^[\p{L}][\p{L} ]{1,40}?(?:\s*[–—-][^:]{1,40})?\s*:\s*""")
    private fun bezStitku(t: String) = t.replace(STITEK_NA_ZACATKU, "").trim()
    private fun bezUvozovek(t: String) = t.replace(Regex("""[„“”"«»]"""), "").trim()

    /** Štítky → rozbor ve stejném tvaru jako [SbScenar.rozeber]. */
    fun cteni(j: List<Jednotka>, st: Map<Int, Stitek>): SbScenarCteni {
        var nazev = ""
        var pomer: LongMmPomer? = null
        var celkem: Double? = null
        val postavy = linkedMapOf<String, String>()
        val zeny = linkedMapOf<String, Boolean>()
        val kontinuita = mutableListOf<String>()
        data class O(val cislo: Int, var od: Double? = null, var doS: Double? = null, val obraz: MutableList<String> = mutableListOf(),
            val akce: MutableList<String> = mutableListOf(), val emoce: MutableList<String> = mutableListOf(),
            val kamera: MutableList<String> = mutableListOf(), val zvuk: MutableList<String> = mutableListOf(),
            val texty: MutableList<String> = mutableListOf(), val vyzva: MutableList<String> = mutableListOf(),
            val poznamky: MutableList<String> = mutableListOf(), val repliky: MutableList<SbReplika> = mutableListOf())
        val okna = linkedMapOf<Int, O>()
        val uvod = mutableListOf<String>()
        val uvodRepliky = mutableListOf<Pair<Jednotka, Stitek>>()
        fun okno(n: Int) = okna.getOrPut(n) { O(n) }
        j.forEach { u ->
            val s = st.getValue(u.id)
            val t = u.text
            if (s.okno == 0) when (s.druh) {
                Druh.TITLE -> if (nazev.isNotEmpty()) kontinuita += t else nazev = t.replace(Regex("""(?iu)^(storyboard|scénář|script)\s*[–—:-]\s*"""), "").trim()
                Druh.FORMAT -> { pomer = pomer ?: SbScenar.pomerZ(t); celkem = celkem ?: Regex("""(\d{1,3})\s*(?:s|sek)""").find(t)?.groupValues?.get(1)?.toDoubleOrNull() }
                Druh.CAST -> SbScenar.postavyZ(bezStitku(t)).forEach { p -> postavy[p.jmeno] = p.popis; p.zena?.let { zeny[p.jmeno] = it } }
                Druh.SETTING, Druh.CONTINUITY, Druh.PICTURE, Druh.ACTION -> kontinuita += t
                // Replika před prvním oknem (vypravěč na začátku) se řekne v okně 1.
                Druh.DIALOGUE -> uvodRepliky += u to s
                // Poznámky a texty před prvním oknem nepatří do H3, ale nesmí se ztratit.
                else -> uvod += t
            } else {
                val o = okno(s.okno)
                when (s.druh) {
                    Druh.WINDOW -> SbScenar.cas(t)?.let { o.od = it.first; o.doS = it.second }
                    Druh.WINDOWTITLE, Druh.TITLE -> o.poznamky += t
                    Druh.FORMAT -> {}
                    Druh.PICTURE -> o.obraz += bezUvozovek(bezStitku(t))
                    Druh.ACTION -> o.akce += bezUvozovek(bezStitku(t))
                    Druh.EMOTION -> o.emoce += bezStitku(t)
                    Druh.CAMERA -> o.kamera += bezStitku(t)
                    Druh.SOUND -> o.zvuk += bezStitku(t)
                    Druh.MUSIC, Druh.NOTE -> o.poznamky += t
                    Druh.ONSCREEN -> o.texty += bezUvozovek(bezStitku(t))
                    Druh.CTA -> o.vyzva += bezUvozovek(bezStitku(t))
                    Druh.CAST -> SbScenar.postavyZ(bezStitku(t)).forEach { p -> postavy[p.jmeno] = p.popis }
                    Druh.SETTING, Druh.CONTINUITY -> kontinuita += t
                    Druh.DIALOGUE -> {
                        // Text repliky ze scénáře, ne od modelu: v uvozovkách, jinak za poslední dvojtečkou.
                        val kusy = Regex("""[„“"«]([^„“"«»”\n]{1,300})[“”"»]""").findAll(t).map { it.groupValues[1].trim() }.toList()
                            // Bez uvozovek: text za PRVNÍ dvojtečkou mluvčího („5:30“ uvnitř repliky zůstane).
                            .ifEmpty { listOf(t.substringAfter(':').trim()) }.filter { it.isNotBlank() }
                        val kdo = SbScenar.jmeno(s.kdo.ifBlank { "Vypravěč" })
                        // Model občas do podání vloží i text („tiše: „Tati…““) — jen slova před dvojtečkou.
                        val jak = s.jak.substringBefore(':').substringBefore('„').substringBefore('"').trim().takeIf { it.length <= 40 }.orEmpty()
                        kusy.forEach { o.repliky += SbReplika(kdo, jak, it) }
                    }
                }
            }
        }
        if (uvodRepliky.isNotEmpty()) {
            val prvni = okna.values.minByOrNull { it.cislo }
            uvodRepliky.forEach { (u, st2) ->
                val kusy = Regex("""[„“"«]([^„“"«»”
]{1,300})[“”"»]""").findAll(u.text).map { it.groupValues[1].trim() }.toList()
                    .ifEmpty { listOf(u.text.substringAfter(':').trim()) }
                kusy.forEach { prvni?.repliky?.add(0, SbReplika(SbScenar.jmeno(st2.kdo.ifBlank { "Vypravěč" }), st2.jak, it)) }
            }
        }
        // Mluvčí pod jmény z postav (Dcery → Dcera).
        fun sjednot(k: String) = postavy.keys.firstOrNull { it.lowercase().take(4) == k.lowercase().take(4) } ?: k
        val vysledna = okna.values.sortedBy { it.cislo }.mapIndexed { i, o ->
            SbOkno(
                cislo = i + 1, od = o.od, doS = o.doS, typ = SbScenar.typZ(o.kamera.joinToString(" ")),
                obraz = o.obraz.joinToString(" "), akce = o.akce.joinToString(" "), emoce = o.emoce.joinToString(" "),
                kamera = o.kamera.joinToString(" "), repliky = o.repliky.map { it.copy(kdo = sjednot(it.kdo)) },
                zvuk = o.zvuk.joinToString(" "), texty = o.texty, vyzva = o.vyzva.joinToString(" "),
                poznamky = (if (i == 0) uvod else emptyList()) + o.poznamky,
            )
        }
        return SbScenarCteni(nazev = nazev, pomer = pomer, celkem = celkem, postavy = postavy, zeny = zeny,
            kontinuita = kontinuita.joinToString(" "), okna = vysledna, odhadem = true)
    }

    private fun norm(t: String) = t.lowercase().replace(Regex("""[„“”"«».,!?…:;–—-]"""), " ").replace(Regex("""\s+"""), " ").trim()

    /**
     * Rozdíly mezi rozborem aplikace a čtením modelem, které mají vliv na film:
     * počet oken, repliky (okno, mluvčí, text) a texty na videu. Prázdné = shoda.
     */
    fun rozdily(a: SbScenarCteni, b: SbScenarCteni): List<String> {
        val out = mutableListOf<String>()
        if (a.okna.size != b.okna.size) {
            out += t("Počet oken: rozbor %d, model %d.").format(a.okna.size, b.okna.size)
            return out
        }
        a.okna.zip(b.okna).forEach { (x, y) ->
            val rx = x.repliky.map { it.kdo.lowercase().take(4) to norm(it.text) }
            val ry = y.repliky.map { it.kdo.lowercase().take(4) to norm(it.text) }
            if (rx != ry) out += t("Okno %d, repliky: rozbor %s, model %s.").format(
                x.cislo, x.repliky.joinToString("; ") { "${it.kdo}: „${it.text}“" }.ifBlank { "—" },
                y.repliky.joinToString("; ") { "${it.kdo}: „${it.text}“" }.ifBlank { "—" },
            )
            val tx = (x.texty + x.vyzva).map { norm(it) }.filter { it.isNotBlank() }.toSet()
            val ty = (y.texty + y.vyzva).map { norm(it) }.filter { it.isNotBlank() }.toSet()
            if (tx != ty) out += t("Okno %d, texty na videu se liší.").format(x.cislo)
        }
        return out
    }

    /**
     * Které čtení použít (5.35). Rozbor appky má přednost, ALE když model našel
     * víc replik, platí model — Otevřené dveře 30. 9. 2026: rozbor 0 replik,
     * model 15, uživatel pokračoval a úseky 2–4 šly do videa bez replik, H3 si
     * slova vymyslel. Film s chybějícími replikami se nesmí dát natočit.
     */
    fun vyber(rozbor: SbScenarCteni?, model: SbScenarCteni?): SbScenarCteni? {
        if (rozbor == null) return model
        if (model == null) return rozbor
        fun repliky(c: SbScenarCteni) = c.okna.sumOf { it.repliky.size }
        return if (repliky(model) > repliky(rozbor)) model else rozbor
    }
}
