package cz.promptlab.h3video.data

import kotlin.math.roundToInt

/**
 * Volba **Storyboard + scénář** (5.14): storyboard je obrázek bez textu a
 * scénář přijde zvlášť (typicky z ChatGPT, pokaždé trochu jinak zapsaný).
 *
 * Kdo o čem rozhoduje (kritici 30. 9. 2026 — scénárista, filmař, specialista
 * na generativní modely, vývojář, grafik):
 *  - **scénář**: repliky a kdo je říká, podání, emoce, zvuk, délky, vzhled
 *    postav, kontinuita, formát, a typ záběru, když ho uvádí;
 *  - **obrázek**: jinak typ záběru a kamera; kompozice (jde do H3 jako `<Picture 1>`).
 *
 * Texty na videu, závěrečná výzva, loga, adresy webů a poznámky pro střih do
 * H3 nejdou nikdy — H3 by je napsal do obrazu komolenými písmeny nebo přečetl
 * nahlas. Jdou do seznamu „Texty do střihu“ a do titulků .srt.
 *
 * Rozbor je deterministický (bez modelu), po řádcích. Když scénář okna
 * neoznačuje, rozdělí ho jazykový model ([SYSTEM_ROZDELENI]) a každá replika
 * se ověří proti replikám nalezeným ve scénáři ([zOdpovedi]).
 */

/** Jedna replika: kdo, jak (podání, česky ze scénáře) a doslovný text. */
data class SbReplika(val kdo: String, val podani: String, val text: String)

/** Jedno okno scénáře. */
data class SbOkno(
    val cislo: Int,
    val od: Double? = null,
    val doS: Double? = null,
    /** Typ záběru, když ho scénář uvádí (detail, polocelek…) — má přednost před obrázkem. */
    val typ: String = "",
    val obraz: String = "",
    val akce: String = "",
    val emoce: String = "",
    val kamera: String = "",
    val repliky: List<SbReplika> = emptyList(),
    val zvuk: String = "",
    val texty: List<String> = emptyList(),
    val vyzva: String = "",
    val poznamky: List<String> = emptyList(),
) {
    /** Záběr detailu telefonu / displeje — H3 na displeji píše nesmyslná písmena. */
    val obrazovka: Boolean get() = SbScenar.jeObrazovka("$obraz $akce")
}

/** Rozebraný scénář. */
data class SbScenarCteni(
    val nazev: String = "",
    val pomer: LongMmPomer? = null,
    val celkem: Double? = null,
    /** Postavy: jméno → popis ze scénáře („35 let, tmavé mikádo, krémový svetr“). */
    val postavy: Map<String, String> = emptyMap(),
    /** Pohlaví postav z hlavičky (true = žena), i podle slov před jménem („jeho syn Kuba“). */
    val zeny: Map<String, Boolean> = emptyMap(),
    val kontinuita: String = "",
    val okna: List<SbOkno> = emptyList(),
    /** Rozděleno jazykovým modelem (scénář okna neoznačoval). */
    val odhadem: Boolean = false,
)

/** Řádek seznamu „Texty do střihu“. Čas se počítá až z hotových délek panelů. */
data class SbTextStrihu(val cislo: Int, val text: String, val druh: Druh) {
    enum class Druh { TEXT, VYZVA, POZNAMKA }
}

object SbScenar {

    private const val SLOVA_OKNA = "okno|panel|záběr|zaber|scéna|scena|shot|scene|window|frame|snímek|snimek"

    /** Řádek se značkou okna: `[OKNO 1 | 0–2 s]`, `Záběr č. 3 (5–8 s):`, `OKNO 2/8`, `Okno 1 — Nález (0–4 s)`. */
    internal val ZNACKA = Regex(
        """(?imu)^[ \t]*\[?[ \t]*(?:$SLOVA_OKNA)[ \t]*(?:č\.?[ \t]*)?(\d{1,2})(?![\d:.,]\d)(?:[ \t]*/[ \t]*\d{1,2})?""",
    )
    /** Záloha: řádky číslované `1.` nebo `1)`. */
    internal val ZNACKA_CISLO = Regex("""(?m)^[ \t]*(\d{1,2})[.)](?!\d)[ \t]*""")
    /** Scénář slitý do jednoho řádku: značka velkým písmenem uprostřed textu. */
    private val ZNACKA_V_RADKU = Regex(
        """(?<![\p{L}\d])(?=\[?(?:OKNO|PANEL|ZÁBĚR|ZABER|SCÉNA|SCENA|SHOT|SCENE|WINDOW|Okno|Panel|Záběr|Scéna|Shot|Scene)\s+\d{1,2}(?![\d:.,]\d))""",
    )

    internal enum class Pole { OBRAZ, AKCE, AKCE_EMOCE, EMOCE, KAMERA, DIALOG, VO, ZVUK, HUDBA, TEXT, VYZVA, POZNAMKA, CELY_FILM }

    /** Štítky polí okna — delší dřív, ať „Akce a emoce“ nevyhraje jako „Akce“. */
    internal val STITKY: List<Pair<String, Pole>> = listOf(
        "akce a emoce" to Pole.AKCE_EMOCE, "action and emotion" to Pole.AKCE_EMOCE,
        "text na videu" to Pole.TEXT, "text na obrazovce" to Pole.TEXT, "text v obraze" to Pole.TEXT,
        // „Text ve videu:“ — svatební scénář 30. 9. 2026 (bez štítku zůstal v popisu jako „Text ve videu:.“).
        "text ve videu" to Pole.TEXT, "text do videa" to Pole.TEXT, "text do videu" to Pole.TEXT,
        "text v záběru" to Pole.TEXT, "text na displeji" to Pole.TEXT, "texty" to Pole.TEXT,
        "on-screen text" to Pole.TEXT, "onscreen text" to Pole.TEXT, "on screen text" to Pole.TEXT,
        "text on screen" to Pole.TEXT, "titulek" to Pole.TEXT, "titulky" to Pole.TEXT, "caption" to Pole.TEXT,
        "overlay" to Pole.TEXT, "super" to Pole.TEXT, "text" to Pole.TEXT, "nápis" to Pole.TEXT, "napis" to Pole.TEXT,
        "slogan" to Pole.TEXT, "claim" to Pole.TEXT, "headline" to Pole.TEXT, "grafika" to Pole.TEXT,
        "závěrečná výzva" to Pole.VYZVA, "zaverecna vyzva" to Pole.VYZVA, "výzva k akci" to Pole.VYZVA,
        "výzva" to Pole.VYZVA, "call to action" to Pole.VYZVA, "cta" to Pole.VYZVA, "závěrečný text" to Pole.VYZVA,
        "závěrečný titulek" to Pole.VYZVA, "end card" to Pole.VYZVA, "endcard" to Pole.VYZVA, "logo" to Pole.VYZVA,
        "packshot" to Pole.VYZVA,
        "obraz" to Pole.OBRAZ, "vizuál" to Pole.OBRAZ, "vizual" to Pole.OBRAZ, "visual" to Pole.OBRAZ,
        "image" to Pole.OBRAZ, "picture" to Pole.OBRAZ, "popis" to Pole.OBRAZ, "description" to Pole.OBRAZ,
        "akce" to Pole.AKCE, "action" to Pole.AKCE, "děj" to Pole.AKCE,
        "emoce" to Pole.EMOCE, "emotion" to Pole.EMOCE, "emotions" to Pole.EMOCE, "nálada" to Pole.EMOCE,
        "mood" to Pole.EMOCE,
        "kamera" to Pole.KAMERA, "camera" to Pole.KAMERA,
        "dialog" to Pole.DIALOG, "dialogue" to Pole.DIALOG, "dialogy" to Pole.DIALOG, "replika" to Pole.DIALOG,
        "repliky" to Pole.DIALOG,
        "voiceover" to Pole.VO, "voice-over" to Pole.VO, "voice over" to Pole.VO, "vo" to Pole.VO, "v.o." to Pole.VO,
        "hlas mimo obraz" to Pole.VO, "hlas" to Pole.VO, "vypravěč" to Pole.VO, "vypravec" to Pole.VO,
        "narrator" to Pole.VO, "narration" to Pole.VO, "mluvené slovo" to Pole.VO,
        "zvuk" to Pole.ZVUK, "zvuky" to Pole.ZVUK, "ruchy" to Pole.ZVUK, "sound" to Pole.ZVUK, "sfx" to Pole.ZVUK,
        "audio" to Pole.ZVUK,
        "hudba" to Pole.HUDBA, "music" to Pole.HUDBA,
        "poznámka pro střih" to Pole.POZNAMKA, "poznámka ke střihu" to Pole.POZNAMKA,
        "poznámka" to Pole.POZNAMKA, "poznamka" to Pole.POZNAMKA, "note" to Pole.POZNAMKA, "notes" to Pole.POZNAMKA,
        "střih" to Pole.POZNAMKA, "strih" to Pole.POZNAMKA, "postprodukce" to Pole.POZNAMKA, "edit" to Pole.POZNAMKA,
        "důležitá kontinuita" to Pole.CELY_FILM, "kontinuita" to Pole.CELY_FILM, "continuity" to Pole.CELY_FILM,
    ).sortedByDescending { it.first.length }

    private val KVALIFIKATOR =
        """(?:[ \t]*\(([^)\n]{1,60})\))?(?:[ \t]*[–—-][ \t]*([^:\n„“"]{1,60}?))?(?:[ \t]*\(([^)\n]{1,60})\))?[ \t]*:(?!\d)"""

    /**
     * Štítek jen na začátku řádku nebo hned po konci věty — „je to super: …“
     * uprostřed věty štítek není (kritik 30. 9. 2026).
     */
    private val STITEK = Regex(
        "(?imu)(?:^[ \\t]*|(?<=[.!?…“”\"»])[ \\t]+)(" + STITKY.joinToString("|") { Regex.escape(it.first) } + ")" + KVALIFIKATOR,
    )

    /** „Pokyn pro převod storyboardu na video:“ a podobné — platí pro celý film. */
    private val POKYN = Regex("""(?imu)^[ \t]*(?:pokyny?|instrukce|instructions?|directions?|pro převod|režijní poznámka)\b[^:\n]{0,70}:""")

    /** Prázdné pole bez dvojtečky: `Dialog – žádný`, `Zvuk: none`. */
    internal val PRAZDNE_POLE = Regex(
        """(?imu)^[ \t]*(?:dialog|dialogue|dialogy|replika|zvuk|sound|text na videu|text|titulek|hudba|music|vo)[ \t]*[–—:-]?[ \t]*""" +
            """(?:žádný|žádná|žádné|none|nic|bez dialogu|bez zvuku|—|–|-|n/a)[ \t]*\.?[ \t]*$""",
    )

    /** Štítky hlavičky scénáře (před prvním oknem). */
    internal val HLAVICKA = Regex(
        """(?imu)(?:^[ \t]*|(?<=[.!?…])[ \t]+)(důležitá kontinuita|kontinuita|continuity|formát|format|postavy a rekvizity|""" +
            """postavy|rekvizity|characters|cast|props|obsazení|prostředí|setting|místo děje|""" +
            """styl|style|tón|tone|cíl|goal|délka|length|duration)[ \t]*:""",
    )

    /** Replika v uvozovkách: `„…“`, `"…"`, `“…”`, `«…»`. */
    private val UVOZOVKY = Regex("""[„“"«»”]([^„“"«»”\n]{1,300})[“”"«»]""")

    /**
     * Replika se jménem uprostřed textu: `Dcera: „…“`, `Syn tiše: „…“`,
     * `DCERA (překvapeně): „…“`. Skupiny: jméno, podání slovy, podání v závorce, text.
     */
    private val MLUVCI_REPLIKA = Regex(
        // Podání slovy („Syn tiše:“) nebo za čárkou („Keramička, spokojeně:“ — hrnek 30. 9. 2026).
        // Slovo podání smí mít i jedno písmeno („Jana s úsměvem:“ — korpus 30. 9. 2026).
        """(?<![\p{L}])([\p{L}][\p{L}]{1,20}(?:[ \t]\p{Lu}[\p{L}]{1,20})?)((?:[ \t]*,[ \t]*\p{Ll}[\p{L} ]{1,30}?)|(?:[ \t]+\p{Ll}[\p{L}]{0,15}){0,3}?)[ \t]*""" +
            """(?:\(([^)\n]{1,40})\))?[ \t]*:[ \t]*[„“"«]([^„“"«»”\n]{1,300})[“”"»]""",
    )

    /** Řádek repliky bez uvozovek: `DCERA: To jsi ty?`, `MAMINKA (dojatě): To jsem já…`. */
    internal val RADEK_MLUVCI = Regex(
        // Druhé slovo jména jen velkým písmenem — „Otec tiše:“ je jméno a podání.
        """^[ \t]*([\p{L}][\p{L}]{1,20}(?:[ \t]\p{Lu}[\p{L}]{1,20})?)((?:[ \t]*,[ \t]*\p{Ll}[\p{L} ]{1,30}?)|(?:[ \t]+\p{Ll}[\p{L}]{0,15}){0,3}?)[ \t]*""" +
            """(?:\(([^)\n]{1,40})\))?[ \t]*:[ \t]*(.+)$""",
    )

    /**
     * Replika celá v uvozovkách, i s uvozovkami uvnitř (`"Říkali mi "Květa"."`)
     * — jinak by se rozpadla na dvě.
     */
    private fun celaVUvozovkach(obsah: String): String? =
        Regex("""^[„“"«](.+)[“”"»][.!?…]?$""").find(obsah.trim())?.groupValues?.get(1)?.trim()
            // `„A“ „B“` jsou dvě repliky, ne jedna s uvozovkami uvnitř.
            ?.takeUnless { Regex("""[“”"»]\s*[;,]?\s*[„“"«]""").containsMatchIn(it) }

    /** Věty o střihu a postprodukci — do H3 nepatří. */
    private val STRIHOVA_VETA = Regex(
        """(?iu)(při střihu|ve střihu|v postprodukci|záznam obrazovky|screen recording|in the edit|in post)""",
    )

    /** Adresa webu — v obraze by ji H3 napsal komolenými písmeny. */
    private val WEB = Regex("""(?iu)(?:https?://)?(?:www\.)?[\p{L}\d-]+\.(?:cz|sk|com|eu|net|org|io|app|ai|de|at)(?![\p{L}\d])(?:/\S*)?""")
    /** „s prostorem pro logo“ — H3 by logo nakreslil. */
    private val MISTO_PRO_LOGO = Regex(
        """(?iu)[ \t]*(?:,[ \t]*)?(?:s[ \t]+|se[ \t]+)?(?:volným[ \t]+|volný[ \t]+)?(?:prostor(?:em)?|míst(?:o|em))[ \t]+(?:pro|na)[ \t]+(?:logo|text|titulek|titulky|claim|packshot|nápis)""",
    )
    /** Věta, která je jen logo / packshot („Logo.“, „Packshot s claimem.“). */
    private val JEN_LOGO = Regex("""(?iu)^(?:logo|packshot|claim|slogan|end ?card|titulek|nápis)(?![\p{L}])[^.!?]{0,40}[.!?]?$""")

    private val OBRAZOVKA = Regex(
        """(?iu)(detail (telefonu|mobilu|displeje|obrazovky)|na displeji|na obrazovce|na telefonu se|displej|screen of the phone|phone screen|""" +
            """close-up of the (phone|screen)|on the screen|the screen shows)""",
    )

    fun jeObrazovka(text: String): Boolean = OBRAZOVKA.containsMatchIn(text)

    /** Typy záběrů podle slov scénáře. */
    private val VELIKOSTI: List<Pair<Regex, String>> = listOf(
        "velký detail|extreme close-?up|makro|macro" to "extreme close-up",
        "polodetail|medium close-?up" to "medium close-up",
        "detail|close-?up|closeup" to "close-up",
        "polocelek|medium shot|střední záběr|americký záběr" to "medium",
        "velký celek|celek|wide shot|establishing shot|total" to "wide",
        "přes rameno|over-the-shoulder|over the shoulder" to "over-the-shoulder",
    ).map { (vzor, typ) -> Regex("""(?iu)(?<![\p{L}])(?:$vzor)(?![\p{L}])""") to typ }

    fun typZ(text: String): String = VELIKOSTI.firstOrNull { it.first.containsMatchIn(text) }?.second.orEmpty()

    /**
     * Sjednocení zápisu: tučné písmo a nadpisy z markdownu, odrážky, emoji,
     * pomlčka před mluvčím pryč. Obsah se nemění.
     */
    fun uprav(vstup: String): String = spojReplikyPresRadek(vstup.replace('\u00A0', ' ').replace("\r", ""))
        .replace(Regex("""[\u200B-\u200D\uFE0E\uFE0F]"""), "")
        .replace(Regex("""\p{So}"""), "")
        .replace("**", "").replace("__", "")
        .lines().joinToString("\n") { r ->
            r.replace(Regex("""^[ \t]*(?:#{1,6}[ \t]*|>[ \t]*)+"""), "").replace(Regex("""^[ \t]*[-•*–—][ \t]+"""), "")
        }

    /**
     * Replika „…“ zalomená přes řádek (PDF, úzké okno) se spojí — jinak by ji
     * rozbor nenašel. Jen české uvozovky: „ otevírá, “ zavírá, jednoznačně.
     */
    private fun spojReplikyPresRadek(t: String): String {
        var s = t
        repeat(5) {
            val dalsi = s.replace(Regex("""(„[^“”„\n]{0,300})\n[ \t]*(?=[^\n]*“)"""), "$1 ")
            if (dalsi == s) return s
            s = dalsi
        }
        return s
    }

    // ------------------------------------------------------------------ časy

    internal val CAS = Regex(
        """(\d{1,2}(?::\d{2})?(?:[.,]\d+)?)[ \t]*(?:s|sek|sec)?[ \t]*[-–—][ \t]*(\d{1,2}(?::\d{2})?(?:[.,]\d+)?)[ \t]*(?:s(?![\p{L}])|sek(?![\p{L}])|sec(?![\p{L}])|sekund)?""",
    )

    /** `0–2 s`, `2,5–5 s`, `0:02-0:05`, `00:00–00:03` → (od, do) v sekundách. */
    fun cas(t: String): Pair<Double, Double>? {
        val m = CAS.find(t) ?: return null
        fun sek(x: String): Double {
            val y = x.replace(',', '.')
            return if (':' in y) y.substringBefore(':').toDouble() * 60 + y.substringAfter(':').toDouble() else y.toDouble()
        }
        val od = sek(m.groupValues[1])
        val doS = sek(m.groupValues[2])
        return if (doS > od) od to doS else null
    }

    // ------------------------------------------------------------------ rozbor

    /**
     * Rozebere scénář. Null, když v něm nejsou aspoň dvě okna — pak ho
     * rozdělí jazykový model.
     */
    fun rozeber(vstup: String): SbScenarCteni? {
        var text = uprav(vstup)
        var znacky = rostouci(ZNACKA.findAll(text).toList())
        if (znacky.size < 2) {
            // Slitý text: značky na vlastní řádky.
            val rozdeleny = ZNACKA_V_RADKU.replace(text, "\n")
            val z = rostouci(ZNACKA.findAll(rozdeleny).toList())
            if (z.size >= 2) { text = rozdeleny; znacky = z }
        }
        if (znacky.size < 2) {
            znacky = ZNACKA_CISLO.findAll(text).toList().fold(mutableListOf()) { acc, m ->
                val n = m.groupValues[1].toInt()
                if (n == (acc.lastOrNull()?.groupValues?.get(1)?.toInt() ?: 0) + 1) acc += m
                acc
            }
        }
        if (znacky.size < 2) return null
        val hlavicka = rozeberHlavicku(text.substring(0, znacky.first().range.first))
        val celyFilm = mutableListOf<String>()
        val okna = znacky.mapIndexed { i, m ->
            val konec = znacky.getOrNull(i + 1)?.range?.first ?: text.length
            rozeberOkno(m.groupValues[1].toInt(), text.substring(m.range.last + 1, konec), hlavicka.postavy.keys, celyFilm)
        }
        val kontinuita = (listOf(hlavicka.kontinuita) + celyFilm).filter { it.isNotBlank() }.joinToString(" ")
        return hlavicka.copy(kontinuita = kontinuita, okna = okna.map { sjednotMluvci(it, hlavicka.postavy) })
    }

    /** Jen rostoucí čísla — „jako v okně 3“ značka není. */
    private fun rostouci(z: List<MatchResult>): List<MatchResult> = z.fold(mutableListOf()) { acc, m ->
        val n = m.groupValues[1].toInt()
        if (acc.isEmpty() || n > acc.last().groupValues[1].toInt()) acc += m
        acc
    }

    /** Hlavička: název, formát, celková délka, postavy, kontinuita. */
    private fun rozeberHlavicku(text0: String): SbScenarCteni {
        // Nadpis bez dvojtečky na vlastním řádku („POSTAVY A REKVIZITY“ — Dar mudrců 30. 9. 2026).
        val text = text0.replace(
            Regex("""(?imu)^[ \t]*(postavy a rekvizity|postavy|rekvizity|characters|cast|props|obsazení)[ \t]*$"""), "$1:",
        )
        val stitky = HLAVICKA.findAll(text).toList()
        fun obsah(i: Int): String {
            val od = stitky[i].range.last + 1
            val konec = stitky.getOrNull(i + 1)?.range?.first ?: text.length
            return text.substring(od, konec).trim()
        }
        val pred = text.substring(0, stitky.firstOrNull()?.range?.first ?: text.length)
        var nazev = pred.lines().map { it.trim() }.firstOrNull { it.isNotBlank() }.orEmpty()
        // „STORYBOARD – FOTOŽIJE.CZ“ → „FOTOŽIJE.CZ“; „Scénář k obrázkovému storyboardu“ název není.
        val predpona = Regex("""(?iu)^(storyboard|scénář|scenar|script|screenplay|reklama)(?![\p{L}])\s*([–—:-]\s*)?""")
        predpona.find(nazev)?.let { m -> nazev = if (m.groupValues[2].isNotBlank()) nazev.substring(m.range.last + 1).trim() else "" }
        var pomer: LongMmPomer? = null
        var celkem: Double? = null
        val postavy = linkedMapOf<String, String>()
        val zeny = linkedMapOf<String, Boolean>()
        val kontinuita = mutableListOf<String>()
        // Další řádky pod názvem („Adaptace povídky O. Henryho pro realistické krátké video.“)
        // popisují celý film — dřív se tiše ztratily (kontrola pokrytí 30. 9. 2026).
        pred.lines().map { it.trim() }.filter { it.isNotBlank() }.drop(1)
            .filterNot { it.none { ch -> ch.isLowerCase() } }
            .forEach { kontinuita += it }
        stitky.forEachIndexed { i, m ->
            val v = obsah(i)
            when (m.groupValues[1].lowercase()) {
                "formát", "format", "délka", "length", "duration" -> {
                    pomer = pomer ?: pomerZ(v)
                    celkem = celkem ?: delkaZ(v)
                    // „Formát: …, 9:16, 15 s. Ve všech oknech je stejná fotografie.“ — druhá věta je kontinuita.
                    kontinuita += vety(v.replace(Regex("""\s+"""), " ")).filter {
                        pomerZ(it) == null && delkaZ(it) == null &&
                            !Regex("""(?iu)reklam|reels|spot|formát|format""").containsMatchIn(it)
                    }
                }
                "postavy", "characters", "cast", "obsazení", "postavy a rekvizity", "rekvizity", "props" -> postavyZ(v).forEach { p ->
                    postavy[p.jmeno] = p.popis
                    p.zena?.let { zeny[p.jmeno] = it }
                }
                "důležitá kontinuita", "kontinuita", "continuity" -> kontinuita += v.replace(Regex("""\s+"""), " ")
                "prostředí", "setting", "místo děje" -> kontinuita += "Prostředí: " + v.replace(Regex("""\s+"""), " ")
                // Styl, tón a cíl se dřív zahazovaly (kritik 30. 9. 2026) — platí pro celý film.
                "styl", "style" -> kontinuita += "Styl: " + v.replace(Regex("""\s+"""), " ")
                "tón", "tone" -> kontinuita += "Tón: " + v.replace(Regex("""\s+"""), " ")
                "cíl", "goal" -> kontinuita += "Cíl: " + v.replace(Regex("""\s+"""), " ")
            }
        }
        return SbScenarCteni(
            nazev = nazev.take(80), pomer = pomer, celkem = celkem, postavy = postavy, zeny = zeny,
            kontinuita = kontinuita.filter { it.isNotBlank() }.joinToString(" "),
        )
    }

    fun pomerZ(t: String): LongMmPomer? {
        val s = t.lowercase()
        return when {
            Regex("""9\s*[:x/]\s*16|na výšku|vertikál|vertical|portrait|reels|tiktok|shorts|stories""").containsMatchIn(s) -> LongMmPomer.NAVYSKU
            Regex("""16\s*[:x/]\s*9|na šířku|horizont|landscape|widescreen""").containsMatchIn(s) -> LongMmPomer.NASIRKU
            Regex("""1\s*[:x/]\s*1|čtverec|square""").containsMatchIn(s) -> LongMmPomer.CTVEREC
            else -> null
        }
    }

    private fun delkaZ(t: String): Double? =
        Regex("""(?iu)(\d{1,3}(?:[.,]\d+)?)\s*(?:s(?![\p{L}])|sek|sekund|seconds|secs?(?![\p{L}]))""").find(t)
            ?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull()

    /** Postava z hlavičky. */
    data class Postava(val jmeno: String, val popis: String, val zena: Boolean?)

    private val NE_JMENA = setOf("let", "roků", "roky", "rok", "years", "year", "a", "and", "její", "jeho", "jejich")

    /**
     * `Dcera (35 let, …) a její maminka (72 let, …)`, `Dcera – 35 let (mikádo)`,
     * `Jana, 35 let, mikádo; Věra, 72 let`, `jeho syn Kuba (8 let)`.
     */
    fun postavyZ(t: String): List<Postava> {
        val out = mutableListOf<Postava>()
        val polozky = uprav(t).lines().flatMap { r ->
            // „Della: mladá žena. V oknech 1–4 …; od okna 5 …“ — středník je uvnitř popisu.
            if (Regex("""^\s*[\p{L}][\p{L} ]{1,40}:""").containsMatchIn(r)) listOf(r) else r.split(";")
        }
        polozky.map { it.trim().trimEnd('.') }.filter { it.isNotBlank() }.forEach { polozka ->
            val zavorky = Regex("""([\p{L}]{2,30})\s*\(([^)]{2,200})\)""").findAll(polozka)
                .filter { it.groupValues[1].lowercase() !in NE_JMENA }.toList()
            if (zavorky.isNotEmpty()) {
                zavorky.forEach { m ->
                    // Dvě slova před jménem prozradí pohlaví („jeho syn Kuba“).
                    val pred = polozka.substring(0, m.range.first).trim().split(Regex("""\s+""")).takeLast(2).joinToString(" ")
                    val jm = jmeno(m.groupValues[1])
                    out += Postava(jm, m.groupValues[2].trim(), zena(jm, m.groupValues[2], pred))
                }
                return@forEach
            }
            val m = Regex("""^([\p{L}]{2,30}(?:\s[\p{L}]{2,30})?)\s*:\s*(.{2,300})$""").find(polozka)
                ?: Regex("""^([\p{L}]{2,30}(?:\s\p{Lu}[\p{L}]{1,30})?)\s*[–—,-]\s*(.{2,300})$""").find(polozka)
            if (m == null) {
                // Věta bez oddělovače: „Keramička s kudrnatými vlasy v rezavé halence a tvůrce
                // videa v tmavé košili“ (hrnek 30. 9. 2026) — jméno je první slovo, popis celý kus.
                polozka.split(Regex("""\s*,\s*|\s+a\s+|\s+and\s+""")).map { it.trim() }.forEach { kus ->
                    val slova = kus.split(Regex("""\s+"""))
                    val prvni = slova.first().trim(',', '.')
                    if (slova.size < 2 || prvni.length < 2 || !prvni.all { it.isLetter() } || prvni.lowercase() in NE_JMENA) return@forEach
                    val jm = jmeno(prvni)
                    out += Postava(jm, kus, zena(jm, kus))
                }
                return@forEach
            }
            if (m.groupValues[1].lowercase() in NE_JMENA) return@forEach
            val jm = jmeno(m.groupValues[1])
            val popis = m.groupValues[2].trim().replace(Regex("""\s*\(([^)]*)\)"""), ", $1").trim(',', ' ')
            out += Postava(jm, popis, zena(jm, popis))
        }
        return out.distinctBy { it.jmeno }
    }

    /** `dcera` / `DCERA` → `Dcera`; smíšená velikost zůstane, jak je. */
    fun jmeno(t: String): String {
        val s = t.trim()
        if (s.isEmpty()) return s
        val jednotne = s == s.lowercase() || s == s.uppercase()
        val z = if (jednotne) s.lowercase() else s
        return z.replaceFirstChar { it.uppercase() }
    }

    /** Úvod hlavičky okna po čísle: ` | `, ` (`, ` – `, `: `. */
    private val UVOD_HLAVICKY = Regex("""^[ \t]*(?:[|(:,–—-][ \t]*)*""")

    /**
     * Hlavička okna (zbytek řádku se značkou): čas kdekoli v ní, typ záběru,
     * a kolik z řádku je obsah okna. `Okno 1 — Nález (0–4 s)` je celé hlavička,
     * `ZÁBĚR 1 (0–3 s): Obraz: …` má obsah za časem, `1. Dcera otevře album.` je celé obsah.
     */
    private fun hlavickaOkna(radek: String): Triple<Pair<Double, Double>?, String, String> {
        val zavorka = radek.indexOf(']')
        if (zavorka >= 0) {
            val h = radek.substring(0, zavorka)
            // `[OKNO 1 | 0–3 s] NÁPAD` — název okna za závorkou do popisu nepatří.
            val za = radek.substring(zavorka + 1)
            return Triple(cas(h), typZ(h), if (jeNadpis(za)) "" else za)
        }
        val stitek = Regex(
            "(?iu)(?<![\\p{L}])(" + STITKY.joinToString("|") { Regex.escape(it.first) } + ")" + KVALIFIKATOR,
        ).find(radek)
        val hlav = if (stitek != null) radek.substring(0, stitek.range.first) else radek
        val c = CAS.find(hlav)
        if (c != null) {
            val za = c.range.last + 1
            val konec = Regex("""^[ \t]*[)\]]?[ \t]*[:.,–—-]?""").find(hlav.substring(za))?.value?.length ?: 0
            val zbytek = hlav.substring(za + konec) + (if (stitek != null) radek.substring(stitek.range.first) else "")
            return Triple(cas(c.value), typZ(hlav.substring(0, za)), if (jeNadpis(zbytek)) "" else zbytek)
        }
        if (stitek != null) {
            // `3) Logo. Text: …` — věta před štítkem je obsah, ne název okna.
            val pred = hlav.substring(UVOD_HLAVICKY.find(hlav)?.value?.length ?: 0)
            return Triple(null, typZ(hlav), (if (jeNadpis(pred)) "" else "$pred ") + radek.substring(stitek.range.first))
        }
        val bez = radek.substring(UVOD_HLAVICKY.find(radek)?.value?.length ?: 0)
        return if (jeNadpis(bez)) Triple(null, typZ(bez), "") else Triple(null, "", bez)
    }

    /** Krátký název okna („Nález“, „INT. KUCHYNĚ – DEN“), ne věta děje. */
    internal fun jeNadpis(t: String): Boolean {
        val s = t.trim().trim(':', '–', '—', '-', ' ')
        if (s.isEmpty()) return true
        if (s.none { it.isLowerCase() }) return true
        return s.split(Regex("""\s+""")).size <= 4 && !Regex("""[.!?…]\s*$""").containsMatchIn(s) && !s.contains(':')
    }

    private fun rozeberOkno(cislo: Int, telo: String, znamiMluvci: Set<String>, celyFilm: MutableList<String>): SbOkno {
        val prvniRadek = telo.substringBefore('\n')
        val (cas, typHlavicky, obsahRadku) = hlavickaOkna(prvniRadek)
        val zbytek = (obsahRadku + "\n" + telo.substringAfter('\n', "")
            .replace(Regex("""^\s*[\p{Lu}\d][\p{Lu}\d \t–—-]{1,40}\n"""), ""))
            .replace(PRAZDNE_POLE, "")
            .replace(POKYN, "Kontinuita:")

        val stitky = STITEK.findAll(zbytek).toList()
        val obraz = StringBuilder()
        val akce = StringBuilder()
        val emoce = StringBuilder()
        val kamera = StringBuilder()
        val zvuk = StringBuilder()
        val texty = mutableListOf<String>()
        var vyzva = ""
        val poznamky = mutableListOf<String>()
        val repliky = mutableListOf<SbReplika>()
        fun pridej(sb: StringBuilder, v: String) {
            if (v.isNotBlank()) { if (sb.isNotEmpty()) sb.append(' '); sb.append(v.trim()) }
        }

        fun jeMluvci(jmenoRaw: String, predUvozovkou: Boolean, naZacatku: Boolean): Boolean {
            val j = jmenoRaw.trim()
            val l = j.lowercase()
            if (l in STITKY_JMENA || STITKY.any { it.first == l }) return false
            if (znamiMluvci.any { stejnyMluvci(it, j) }) return true
            if (j.length >= 2 && j == j.uppercase() && j.any { it.isLetter() }) return true
            if (predUvozovkou && (j.first().isUpperCase() || naZacatku)) return true
            return false
        }

        /** Repliky se jménem v libovolném poli; vrátí zbytek textu bez nich. */
        fun vytahni(v: String): String {
            val zbyle = v.lines().mapNotNull { radek ->
                // Celý řádek je replika: `DCERA: To jsi ty?`, `Maminka (dojatě): „…“`.
                val r = RADEK_MLUVCI.find(radek)
                if (r != null) {
                    val obsah = r.groupValues[4].trim()
                    val vUvozovkach = celaVUvozovkach(obsah)
                    if (jeMluvci(r.groupValues[1], vUvozovkach != null, true)) {
                        repliky += SbReplika(
                            jmeno(r.groupValues[1]),
                            podani(listOf(r.groupValues[2], r.groupValues[3]).joinToString(", "), false),
                            vUvozovkach ?: obsah.trim(),
                        )
                        return@mapNotNull null
                    }
                }
                // Uprostřed textu: `Zvuk: šustění. Syn tiše: „Tati, podívej.“`
                var zbytekRadku = radek
                MLUVCI_REPLIKA.findAll(radek).toList().forEach { m ->
                    val zacatek = m.range.first == 0 || radek.substring(0, m.range.first).trimEnd().let { it.isEmpty() || it.last() in ".!?…:" }
                    if (!jeMluvci(m.groupValues[1], true, zacatek)) return@forEach
                    repliky += SbReplika(
                        jmeno(m.groupValues[1]),
                        podani(listOf(m.groupValues[2], m.groupValues[3]).joinToString(", "), false),
                        m.groupValues[4].trim(),
                    )
                    zbytekRadku = zbytekRadku.replace(m.value, " ")
                }
                zbytekRadku
            }
            return zbyle.joinToString("\n").replace(Regex("""[ \t]{2,}"""), " ").trim()
        }

        fun jednoradkove(t: String) = t.trim().replace(Regex("""\s*\n\s*"""), " ")

        pridej(obraz, jednoradkove(vytahni(zbytek.substring(0, stitky.firstOrNull()?.range?.first ?: zbytek.length))))
        stitky.forEachIndexed { i, m ->
            val od = m.range.last + 1
            val konec = stitky.getOrNull(i + 1)?.range?.first ?: zbytek.length
            val surove = zbytek.substring(od, konec)
            val pole = STITKY.first { it.first.equals(m.groupValues[1], ignoreCase = true) }.second
            val kvalifikator = listOf(m.groupValues[2], m.groupValues[3], m.groupValues[4]).filter { it.isNotBlank() }
                .joinToString(", ")
            when (pole) {
                Pole.DIALOG, Pole.VO -> {
                    val (r, zbyle) = replikyPole(surove, kvalifikator, pole == Pole.VO, znamiMluvci)
                    repliky += r
                    pridej(akce, zbyle)
                }
                Pole.TEXT -> bezUvozovek(jednoradkove(surove)).takeIf { it.isNotBlank() && !jeNic(it) }?.let { texty += it }
                Pole.VYZVA -> bezUvozovek(jednoradkove(surove)).takeUnless { jeNic(it) }?.let {
                    vyzva = listOf(vyzva, it).filter { x -> x.isNotBlank() }.joinToString(" ")
                }
                else -> {
                    val v = jednoradkove(vytahni(surove))
                    when (pole) {
                        Pole.OBRAZ -> pridej(obraz, v)
                        Pole.AKCE, Pole.AKCE_EMOCE -> pridej(akce, v)
                        Pole.EMOCE -> pridej(emoce, v)
                        Pole.KAMERA -> pridej(kamera, v)
                        Pole.ZVUK -> {
                            // „Hudba se na okamžik ztiší“ je pokyn pro mix — hudba přijde až ze střihu.
                            val (hudba, ostatni) = vety(v).partition { Regex("""(?iu)hudb|music""").containsMatchIn(it) }
                            pridej(zvuk, ostatni.joinToString(" "))
                            poznamky += hudba
                        }
                        Pole.HUDBA -> if (v.isNotBlank()) poznamky += "Hudba: $v"
                        Pole.POZNAMKA -> if (v.isNotBlank() && !jeNic(v)) poznamky += v
                        Pole.CELY_FILM -> if (v.isNotBlank()) celyFilm += v
                        else -> {}
                    }
                }
            }
        }

        /**
         * Popis pro H3: bez vět o střihu, bez adres webů a log, bez textu v
         * uvozovkách (ten by H3 vykreslil nebo řekl) — vše do poznámek.
         */
        fun cistyPopis(t0: String): String {
            // Text v uvozovkách za „Závěrečný text…“ / „Text…“ jde do střihu jako výzva
            // nebo text na videu, ne jen do poznámek (hrnek 30. 9. 2026: „Závěrečný text
            // doplnit až ve střihu: „Od nápadu…““).
            var t = t0
            UVOZOVKY.findAll(t0).toList().forEach { m ->
                val pred = t0.substring(maxOf(0, m.range.first - 60), m.range.first)
                val obsah = m.groupValues[1].trim()
                when {
                    Regex("""(?iu)(závěrečn\p{L}*|výzv\p{L}*|claim|slogan|cta)[^.!?]*$""").containsMatchIn(pred) -> {
                        vyzva = listOf(vyzva, obsah).filter { it.isNotBlank() }.joinToString(" ")
                        t = t.replace(m.value, "")
                    }
                    Regex("""(?iu)(?<![\p{L}])(text\p{L}*|titul\p{L}*)[^.!?]*$""").containsMatchIn(pred) -> {
                        texty += obsah
                        t = t.replace(m.value, "")
                    }
                }
            }
            val vety = vety(t).mapNotNull { v ->
                when {
                    STRIHOVA_VETA.containsMatchIn(v) || JEN_LOGO.matches(v.trim()) -> { poznamky += v; null }
                    else -> v
                }
            }
            var s = vety.joinToString(" ")
            MISTO_PRO_LOGO.find(s)?.let { m ->
                poznamky += m.value.trim().trim(',', ' ').replaceFirstChar { it.uppercase() }
                s = s.replace(m.value, " s volným místem v horní části obrazu")
            }
            UVOZOVKY.findAll(s).toList().forEach { m -> poznamky += m.groupValues[1].trim(); s = s.replace(m.value, "") }
            s = WEB.replace(s, "web")
            // Osiřelý štítek („Text ve videu:.“) — text v uvozovkách šel do poznámek, štítek do H3 nepatří.
            s = vety(s).filterNot { Regex("""^[\p{L} ,]{2,50}:\s*[.!]?$""").matches(it.trim()) }.joinToString(" ")
            return s.replace(Regex("""\s+([,.])"""), "$1").replace(Regex("""\s{2,}"""), " ").trim()
        }

        val obrazText = cistyPopis(obraz.toString())
        return SbOkno(
            cislo = cislo, od = cas?.first, doS = cas?.second,
            // Typ záběru z hlavičky, jinak z věty, která jím začíná („Detail rukou: …“, „Detail telefonu.“).
            typ = typHlavicky.ifBlank {
                vety(obrazText).firstNotNullOfOrNull { v ->
                    VELIKOSTI.firstOrNull { (r, _) -> r.find(v)?.range?.first == 0 }?.second
                }.orEmpty()
            },
            obraz = obrazText, akce = cistyPopis(akce.toString()),
            emoce = bezUvozovek(emoce.toString()), kamera = kamera.toString().trim(),
            repliky = repliky, zvuk = cistyPopis(zvuk.toString()).takeUnless { jeNic(it) }.orEmpty(),
            texty = texty, vyzva = vyzva, poznamky = poznamky.map { it.trim() }.filter { it.isNotBlank() }.distinct(),
        )
    }

    internal val STITKY_JMENA = STITKY.map { it.first }.toSet() +
        setOf("okno", "panel", "záběr", "shot", "scéna", "scene", "detail", "zvuk", "formát", "postavy", "poznámka")

    internal fun jeNic(t: String) = t.trim().trimEnd('.').lowercase() in setOf("", "none", "žádný", "žádná", "žádné", "nic", "-", "–", "n/a")

    /** Uvozovky pryč — text v uvozovkách H3 vykreslí jako nápis nebo řekne. */
    private fun bezUvozovek(t: String) = t.replace(Regex("""[„“”"«»]"""), "").replace(Regex("""\s{2,}"""), " ").trim()

    internal fun vety(t: String): List<String> =
        t.split(Regex("""(?<=[.!?…])\s+(?=[\p{Lu}\d„“"])""")).map { it.trim() }.filter { it.isNotBlank() }

    /** Stejný mluvčí podle kmene: Dcera / dcery / DCERA, Maminka / maminky. */
    private fun stejnyMluvci(a: String, b: String): Boolean {
        val x = a.lowercase().trim()
        val y = b.lowercase().trim()
        return x == y || (minOf(x.length, y.length) >= 4 && x.take(4) == y.take(4))
    }

    /**
     * Pole Dialog / VO: každý řádek zvlášť (dva řádky bez uvozovek se jinak
     * slily do jedné repliky — kritik 30. 9. 2026). Vrací repliky a zbytek
     * textu, který replikou není („a usměje se“).
     */
    private fun replikyPole(
        v: String, kvalifikator: String, voiceover: Boolean, znami: Set<String>,
    ): Pair<List<SbReplika>, String> {
        val (kdoKv, jakKv) = kvalifikator.split(",", limit = 2).let { it[0].trim() to it.getOrElse(1) { "" }.trim() }
        val out = mutableListOf<SbReplika>()
        val zbytek = StringBuilder()
        fun vychozi(): String = when {
            kdoKv.isNotBlank() -> jmeno(kdoKv)
            voiceover -> "Vypravěč"
            znami.size == 1 -> znami.first()
            else -> "Vypravěč"
        }
        v.lines().map { it.trim() }.filter { it.isNotBlank() && !jeNic(it) }.forEach { radek ->
            val r = RADEK_MLUVCI.find(radek)
            val jm = r?.groupValues?.get(1)?.trim()
            if (r != null && jm != null && jm.lowercase() !in STITKY_JMENA) {
                val obsah = r.groupValues[4].trim()
                val uvoz = celaVUvozovkach(obsah)?.let { listOf(it) }
                    ?: UVOZOVKY.findAll(obsah).map { it.groupValues[1].trim() }.toList()
                // „Dcera říká: „…““ — slovo po jménu je sloveso, ne podání.
                val slovo = r.groupValues[2].trim()
                val podaniSlovy = if (Regex("""(?iu)^(říká|řekne|odpoví|odpovídá|zašeptá|zavolá|says|asks|replies|whispers)$""").matches(slovo)) "" else slovo
                val podani = podani(listOf(podaniSlovy, r.groupValues[3], jakKv.takeIf { kdoKv.isBlank() }.orEmpty()).joinToString(", "), voiceover)
                if (uvoz.isEmpty()) out += SbReplika(jmeno(jm), podani, obsah)
                else uvoz.forEach { out += SbReplika(jmeno(jm), podani, it) }
                return@forEach
            }
            val uvoz = UVOZOVKY.findAll(radek).toList()
            if (uvoz.isEmpty()) {
                // Celý řádek je replika bez jména (`Dialog – dcera: To jsi ty?`).
                out += SbReplika(vychozi(), podani(jakKv, voiceover), radek)
                return@forEach
            }
            // „Maminka odpoví „To jsem já.“ a usměje se.“ — mluvčí je jméno před uvozovkou.
            uvoz.forEach { m ->
                val pred = radek.substring(0, m.range.first)
                val slova = pred.trim().split(Regex("""\s+""")).filter { it.isNotBlank() }
                val kdo = znami.firstOrNull { z -> slova.any { stejnyMluvci(z, it.trim(',', ':')) } }
                    ?: slova.firstOrNull()?.takeIf { slova.size <= 3 && it.first().isUpperCase() && it.lowercase() !in STITKY_JMENA }
                        ?.trim(',', ':')?.let { jmeno(it) }
                    ?: vychozi()
                out += SbReplika(kdo, podani(jakKv, voiceover), m.groupValues[1].trim())
            }
            val bez = uvoz.fold(radek) { acc, m -> acc.replace(m.value, " ") }
            // Zbytek po odebrání mluvčího a slovesa řeči, když má smysl jako děj.
            val dej = bez.replace(Regex("""(?iu)^\s*[\p{L}]+\s+(říká|řekne|odpoví|odpovídá|zašeptá|zavolá)\s*:?"""), "")
                .replace(Regex("""\s{2,}"""), " ").trim().trimStart(',', ':', ' ')
            if (dej.split(" ").size >= 2) zbytek.append(dej.replaceFirstChar { it.uppercase() }).append(' ')
        }
        return out to zbytek.toString().trim()
    }

    private fun podani(t: String, voiceover: Boolean): String {
        val s = t.split(",").map { it.trim() }.filter { it.isNotBlank() }.joinToString(", ")
        val vo = voiceover || Regex("""(?iu)(?<![\p{L}])(v\.?\s?o\.?|mimo obraz|off-?screen|o\.?s\.?)(?![\p{L}])""").containsMatchIn(s)
        val bez = s.replace(Regex("""(?iu)(?<![\p{L}])(v\.\s?o\.|vo|o\.s\.|os)(?![\p{L}.])"""), "")
            .split(",").map { it.trim() }.filter { it.isNotBlank() }.joinToString(", ")
        return listOfNotNull(bez.takeIf { it.isNotBlank() }, "off-screen voice".takeIf { vo && "mimo obraz" !in bez.lowercase() })
            .joinToString(", ")
    }

    /** Mluvčí „Maminky“ / „MAMINKA“ → jméno postavy z hlavičky („Maminka“), podle kmene. */
    private fun sjednotMluvci(o: SbOkno, postavy: Map<String, String>): SbOkno {
        if (postavy.isEmpty()) return o
        fun najdi(kdo: String): String = postavy.keys.firstOrNull { stejnyMluvci(it, kdo) } ?: kdo
        return o.copy(repliky = o.repliky.map { it.copy(kdo = najdi(it.kdo)) })
    }

    // ---------------------------------------------------------------- plán

    /** Popis záběru pro přepisovač: obraz, akce, nálada. */
    fun popis(o: SbOkno): String {
        val casti = mutableListOf<String>()
        if (o.obraz.isNotBlank()) casti += o.obraz.trim().let { if (it.last() in ".!?…") it else "$it." }
        if (o.akce.isNotBlank()) casti += o.akce.trim().let { if (it.last() in ".!?…") it else "$it." }
        var p = casti.joinToString(" ")
        if (o.emoce.isNotBlank()) p = SbFilmPlan.doplnNaladu(p, o.emoce)
        return p
    }

    /** Repliky v kanonickém tvaru `Dcera: „…“; …` — ten čtou [SbFilmPrepis.repliky] i odhad délky řeči. */
    fun replikyText(o: SbOkno): String = o.repliky.joinToString("; ") { "${it.kdo}: „${it.text}“" }

    /**
     * Čtení pro plánovač. Typ záběru ze scénáře, jinak z obrázku (panel se
     * stejným pořadím), kamera obdobně, vše ostatní ze scénáře. Celková délka
     * se nepředává — „přibližně 26 s“ je odhad; platí časy oken, když na sebe
     * navazují.
     */
    fun cteni(s: SbScenarCteni, obrazek: SbCteni?): SbCteni {
        val panelyObr = obrazek?.panely?.sortedBy { it.cislo }.orEmpty()
        val panely = s.okna.mapIndexed { i, o ->
            val z = panelyObr.getOrNull(i)
            SbPrecteny(
                cislo = o.cislo, od = o.od, doS = o.doS,
                typ = o.typ.ifBlank { z?.typ.orEmpty() },
                kamera = o.kamera.ifBlank { z?.kamera.orEmpty() },
                popis = popis(o).ifBlank { z?.popis.orEmpty() },
                repliky = replikyText(o),
            )
        }
        return SbCteni(
            nazev = s.nazev.ifBlank { obrazek?.nazev.orEmpty() }.ifBlank { null },
            celkemVepsano = null, zaberuVepsano = null, panely = panely,
            radku = obrazek?.radku, sloupcu = obrazek?.sloupcu,
            hlasy = hlasyScenareAObrazku(s, obrazek), vzhled = vzhledScenareAObrazku(s, obrazek),
            hudbaStyl = obrazek?.hudbaStyl,
        )
    }

    /** Doplní do naplánovaných panelů podání a zvuk (plánovač je nezná). */
    fun doplnPanely(panely: List<SbPanel>, s: SbScenarCteni): List<SbPanel> = panely.map { p ->
        val o = s.okna.firstOrNull { it.cislo == p.cislo } ?: return@map p
        p.copy(
            podani = if (o.repliky.any { it.podani.isNotBlank() }) o.repliky.joinToString(";") { it.podani } else "",
            zvuk = o.zvuk,
            obrazovka = o.obrazovka,
        )
    }

    /** Texty na videu, výzva a poznámky pro střih, v pořadí oken. */
    fun strih(s: SbScenarCteni): List<SbTextStrihu> = s.okna.flatMap { o ->
        o.texty.map { SbTextStrihu(o.cislo, it, SbTextStrihu.Druh.TEXT) } +
            listOfNotNull(o.vyzva.takeIf { it.isNotBlank() }?.let { SbTextStrihu(o.cislo, it, SbTextStrihu.Druh.VYZVA) }) +
            o.poznamky.map { SbTextStrihu(o.cislo, it, SbTextStrihu.Druh.POZNAMKA) }
    }

    // ---------------------------------------------------------------- hlasy

    private val ZENSKE = setOf(
        "žena", "dcera", "máma", "mama", "maminka", "matka", "babička", "babicka", "sestra", "teta", "dívka",
        "divka", "holka", "holčička", "paní", "pani", "slečna", "vnučka", "manželka", "přítelkyně", "kamarádka",
        "nevěsta", "její", "keramička", "tvůrkyně", "fotografka", "zákaznice", "prodavačka", "kuchařka", "lékařka",
        "učitelka", "majitelka", "maminka", "woman", "girl", "mother", "mom", "mum", "daughter", "grandma", "grandmother", "sister",
        "aunt", "lady", "wife", "bride", "she", "her",
    )
    private val MUZSKE = setOf(
        "muž", "muz", "otec", "táta", "tata", "tatínek", "děda", "deda", "dědeček", "syn", "bratr", "strýc", "kluk",
        "chlapec", "pán", "pan", "vnuk", "manžel", "přítel", "kamarád", "ženich", "jeho", "tvůrce", "fotograf",
        "kameraman", "režisér", "zákazník", "prodavač", "kuchař", "lékař", "učitel", "majitel", "soudce", "správce",
        "man", "boy", "father", "dad", "son", "grandpa", "grandfather", "brother", "uncle", "husband", "groom", "he", "his",
    )
    /** Mužská jména na -a / -e (Honza, Kuba…). */
    private val MUZSKA_NA_A = setOf(
        "honza", "kuba", "jirka", "standa", "franta", "vojta", "míra", "mira", "pepa", "ota", "láďa", "jarda", "ruda",
        "tonda", "venca", "zbyňa", "béďa", "ferda", "luboš", "joshua", "luca", "andrea", "nikola", "saša", "sasha",
    )

    /**
     * Žena (true), muž (false), nepoznané (null). Slova před jménem a v popisu
     * mají přednost („jeho syn Kuba“ je chlapec).
     */
    fun zena(jmeno: String, popis: String, pred: String = ""): Boolean? {
        fun rod(t: String): Boolean? {
            val slova = t.lowercase().split(Regex("""[^\p{L}]+""")).toSet()
            val z = slova.any { it in ZENSKE }
            val m = slova.any { it in MUZSKE }
            return if (z && !m) true else if (m && !z) false else null
        }
        rod(jmeno)?.let { return it }
        rod(pred.split(Regex("""\s+""")).lastOrNull().orEmpty())?.let { return it }
        rod(popis)?.let { return it }
        val j = jmeno.lowercase()
        return when {
            j in MUZSKA_NA_A -> if (j in setOf("andrea", "nikola", "saša", "sasha")) null else false
            j.endsWith("ová") -> true
            // -a (Jana, keramička) žena; -e je nejisté (tvůrce, soudce × Marie) — hlas pak z obrázku.
            Regex("""(a|ice|yně)$""").containsMatchIn(j) -> true
            // Marie, Lucie, Julie jsou ženy; jinak je -e nejisté (tvůrce, soudce).
            j.endsWith("ie") -> true
            j.endsWith("e") -> null
            j.isNotEmpty() && j.last().isLetter() -> false
            else -> null
        }
    }

    /** Věk z popisu („35 let“, „72 years“). */
    fun vek(popis: String): Int? =
        Regex("""(?iu)(\d{1,3})\s*(?:let|roků|roky|rok|years|year|y\.?\s?o\.?|-year)""").find(popis)
            ?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in 1..110 }

    private val RODICE = setOf("otec", "táta", "tata", "tatínek", "matka", "máma", "mama", "maminka", "father", "dad", "mother", "mom", "mum")
    private val PRARODICE = setOf("děda", "deda", "dědeček", "babička", "babicka", "grandpa", "grandfather", "grandma", "grandmother")
    private val DETI = setOf("syn", "dcera", "son", "daughter")

    /**
     * Hlas každého mluvčího — stejná slova do všech úseků (bez nich si
     * přepisovač vymýšlí hlas v každém úseku jinak). Věk z hlavičky; když
     * chybí, podle role (otec vedle syna je starší).
     */
    fun hlasy(s: SbScenarCteni): Map<String, String> {
        val mluvci = (s.postavy.keys + s.okna.flatMap { o -> o.repliky.map { it.kdo } }).distinct()
        val role = mluvci.map { it.lowercase() }.toSet()
        return mluvci.mapNotNull { jm ->
            val popis = s.postavy[jm].orEmpty()
            val zena = s.zeny[jm] ?: zena(jm, popis) ?: return@mapNotNull null
            val l = jm.lowercase()
            val vek = vek(popis) ?: when {
                l in PRARODICE -> 75
                l in RODICE && role.any { it in DETI } -> 68
                l in DETI && role.any { it in RODICE || it in PRARODICE } -> 35
                else -> null
            }
            if (vek == null) {
                if (jm == "Vypravěč") return@mapNotNull null
                return@mapNotNull jm to (if (zena) "a woman" else "a man") + " with a warm, natural, medium-pitched voice"
            }
            jm to hlas(zena, vek)
        }.toMap()
    }

    /**
     * Scénář bez seznamu postav: vzhled přečtený z obrázku („Young Man“,
     * „Elderly Man“) pod jmény mluvčích ze scénáře (Syn, Otec) — jinak by
     * přepisovač dostal čtyři postavy místo dvou. Přiřazuje se jen jednoznačně
     * (pohlaví + mladší/starší podle role); co nesedí, zůstane pod svým jménem.
     */
    fun vzhledProMluvci(vzhled: Map<String, String>, s: SbScenarCteni, napovedy: Map<String, String> = emptyMap()): Map<String, String> {
        val mluvci = s.okna.flatMap { o -> o.repliky.map { it.kdo } }.distinct().filter { it != "Vypravěč" }
        if (vzhled.isEmpty() || mluvci.isEmpty()) return vzhled
        val role = mluvci.map { it.lowercase() }.toSet()
        val maDite = role.any { it in DETI }
        val maRodice = role.any { it in RODICE || it in PRARODICE }
        fun stary(t: String) = Regex("""(?iu)elderly|old|older|grand|senior|aged|white hair|grey hair|gray hair""").containsMatchIn(t)
        fun zenaEn(t: String): Boolean? = zena("", t)?.takeIf {
            Regex("""(?iu)(?<![\p{L}])(woman|girl|lady|female|mother|daughter|grandma|bride|man|boy|male|father|son|grandpa|groom)(?![\p{L}])""").containsMatchIn(t)
        }
        val out = linkedMapOf<String, String>()
        val pouzite = mutableSetOf<String>()
        mluvci.forEach { m ->
            val l = m.lowercase()
            val starsi: Boolean? = when {
                l in PRARODICE -> true
                l in RODICE && maDite -> true
                l in DETI && maRodice -> false
                else -> null
            }
            val rod = s.zeny[m] ?: zena(m, "")
            val kandidati = vzhled.keys.filter { it !in pouzite }.filter { k ->
                val t = "$k ${vzhled[k]} ${napovedy[k].orEmpty()}"
                (rod == null || zenaEn(t) == null || zenaEn(t) == rod) &&
                    (starsi == null || stary(t) == starsi)
            }
            if (kandidati.size == 1 && (starsi != null || rod != null || vzhled.size == 1)) {
                out[m] = vzhled.getValue(kandidati.single())
                pouzite += kandidati.single()
            }
        }
        vzhled.filterKeys { it !in pouzite }.forEach { (k, v) -> out[k] = v }
        return out
    }

    /**
     * Vzhled: ze scénáře; mluvčím, které scénář nepopisuje, z obrázku (Young Man →
     * Syn). Bez seznamu postav ve scénáři i zbylé postavy z obrázku.
     */
    fun vzhledScenareAObrazku(s: SbScenarCteni, obrazek: SbCteni?): Map<String, String> {
        val zObrazku = vzhledProMluvci(obrazek?.vzhled.orEmpty(), s, obrazek?.hlasy.orEmpty())
        if (s.postavy.isEmpty()) return zObrazku
        val mluvci = s.okna.flatMap { o -> o.repliky.map { it.kdo } }.toSet()
        return s.postavy + zObrazku.filterKeys { k -> k in mluvci && s.postavy.keys.none { stejnyMluvci(it, k) } }
    }

    /** Hlasy: podle věku a rodu ze scénáře; komu chybí, z obrázku (hrnek: „tvůrce“ dostal ženský hlas). */
    fun hlasyScenareAObrazku(s: SbScenarCteni, obrazek: SbCteni?): Map<String, String> {
        val vse = hlasy(s)
        // Hlas s věkem ze scénáře má přednost; bez věku („a woman with a warm… voice“) je jen
        // odhad z rodu — obrázek věk odhadne líp; obecný hlas zůstane jen jako záloha.
        val sVekem = vse.filterValues { Regex(""" in (her|his) | of about |teenage""").containsMatchIn(it) }
        val zObrazku = vzhledProMluvci(obrazek?.hlasy.orEmpty(), s)
        val mluvci = s.okna.flatMap { o -> o.repliky.map { it.kdo } }.toSet()
        val out = LinkedHashMap(sVekem)
        zObrazku.filterKeys { k -> k in mluvci && out.keys.none { stejnyMluvci(it, k) } }.forEach { (k, v) -> out[k] = v }
        vse.filterKeys { k -> out.keys.none { stejnyMluvci(it, k) } }.forEach { (k, v) -> out[k] = v }
        return out
    }

    /**
     * Značky, které přepisovač vymyslel (`<Product>`), přepíše na další
     * `<Subject K>` — příručka zná jen Picture/Subject/Audio/Video (hrnek 30. 9. 2026).
     */
    fun opravZnacky(text: String): String {
        val povolene = Regex("""^(Picture|Subject|Audio|Video) \d+$|^/?d$""")
        val cizi = Regex("""<(/?[A-Za-z][A-Za-z ]{0,20}?)>""").findAll(text).map { it.groupValues[1] }
            .filterNot { povolene.matches(it) }.distinct().toList()
        if (cizi.isEmpty()) return text
        var dalsi = (Regex("""<Subject (\d+)>""").findAll(text).maxOfOrNull { it.groupValues[1].toInt() } ?: 0) + 1
        return cizi.fold(text) { t, z -> t.replace("<$z>", "<Subject ${dalsi++}>") }
    }

    /** Popis vzhledu mění postavu podle oken („V oknech 1–4 … od okna 5 …“). */
    fun vzhledSeMeni(vzhled: Map<String, String>): Boolean =
        vzhled.values.any { Regex("""(?iu)(?<![\p{L}])(okn\p{L}*|panel\p{L}*|záběr\p{L}*|window\p{L}*|shot\p{L}*)\s*\d""").containsMatchIn(it) }

    fun hlas(zena: Boolean, vek: Int): String {
        val kdo = if (zena) "a woman" else "a man"
        val jeji = if (zena) "her" else "his"
        return when {
            vek < 13 -> (if (zena) "a girl" else "a boy") + " of about $vek with a light, high child's voice"
            vek < 20 -> (if (zena) "a teenage girl" else "a teenage boy") + " with a light, youthful voice"
            else -> {
                val dekada = vek / 10 * 10
                val kde = when (vek % 10) { in 0..3 -> "early"; in 4..6 -> "mid-"; else -> "late" }
                val vekText = if (kde == "mid-") "mid-${dekada}s" else "$kde ${dekada}s"
                val barva = when {
                    vek >= 65 -> "soft, gentle, slightly husky"
                    vek >= 45 -> "warm, mature"
                    else -> "warm, natural, medium-pitched"
                }
                "$kdo in $jeji $vekText with a $barva voice"
            }
        }
    }

    // ------------------------------------------------------- záloha modelem

    /** Když scénář okna neoznačuje: jazykový model ho rozdělí do pevného tvaru. */
    const val SYSTEM_ROZDELENI =
        "You split a film screenplay into its shots. Answer in plain lines only, no other text, one line per " +
            "shot in story order: SHOT <number> | <start-end seconds as written, for example 0-2 s, or none> | " +
            "<what we see and what happens, copied from the screenplay in its original language> | <emotion, or " +
            "none> | <every spoken line of that shot as Speaker (delivery): \"line\", copied word for word in its " +
            "original language, separated by ;, or none> | <sound effects, or none> | <text shown on screen, or " +
            "none>. Every spoken line of the screenplay appears in exactly one shot. Never translate, shorten or " +
            "invent anything."

    private fun normalizuj(t: String) = t.lowercase()
        .replace(Regex("""[„“”"«»‚‘’'.,!?…:;–—-]"""), " ").replace(Regex("""\s+"""), " ").trim()

    /**
     * Odpověď modelu → okna. Repliky ve scénáři se najdou stejným rozborem
     * jako v oknech (celý scénář jako jedno okno); každá replika modelu musí
     * být CELÁ jedna z nich a každá z nich musí v odpovědi být. Jinak null —
     * raději chyba než zkrácená, přeložená nebo vymyšlená replika.
     */
    fun zOdpovedi(odpoved: String, puvodni: String): SbScenarCteni? {
        val hlav = rozeberHlavicku(uprav(puvodni))
        val veScenari = rozeberOkno(1, "\n" + uprav(puvodni), hlav.postavy.keys, mutableListOf())
            .repliky.map { normalizuj(it.text) }.filter { it.isNotBlank() }
        val radky = odpoved.replace(Regex("""(?iu)\s*(?=\bSHOT\s*\d+\s*\|)"""), "\n").lines()
            .map { it.trim() }.filter { it.uppercase().startsWith("SHOT") }
        if (radky.size < 2) return null
        val okna = radky.mapIndexed { i, r ->
            val c = r.split("|").map { it.trim() }
            fun pole(k: Int) = c.getOrNull(k).orEmpty().takeUnless { jeNic(it) }.orEmpty()
            val cas = cas(pole(1))
            val repl = pole(4).takeIf { it.isNotBlank() }?.let { v ->
                v.split(";").mapNotNull { kus ->
                    val m = Regex("""^\s*([^:(„"“]{1,30}?)\s*(?:\(([^)]{1,40})\))?\s*:\s*[„“"«]?(.+?)[“”"»]?\s*$""").find(kus)
                        ?: return@mapNotNull null
                    SbReplika(jmeno(m.groupValues[1]), podani(m.groupValues[2], false), m.groupValues[3].trim())
                }
            }.orEmpty()
            if (repl.any { normalizuj(it.text) !in veScenari }) return null
            val (obraz, poznamky) = vety(pole(2)).partition { !STRIHOVA_VETA.containsMatchIn(it) }
            SbOkno(
                cislo = i + 1, od = cas?.first, doS = cas?.second, typ = typZ(pole(2).take(20)),
                obraz = bezUvozovek(WEB.replace(obraz.joinToString(" "), "web")), emoce = pole(3), repliky = repl,
                zvuk = pole(5), texty = listOfNotNull(bezUvozovek(pole(6)).takeIf { it.isNotBlank() }), poznamky = poznamky,
            )
        }
        val vsechny = okna.flatMap { o -> o.repliky.map { normalizuj(it.text) } }
        if (veScenari.any { it !in vsechny }) return null
        return hlav.copy(okna = okna.map { sjednotMluvci(it, hlav.postavy) }, odhadem = true)
    }

    // ---------------------------------------------------------------- titulky

    /**
     * Titulky .srt s texty na videu a výzvou. Časy z HOTOVÝCH délek panelů
     * (plánovač může panel prodloužit o délku řeči a konec o rezervu), ne ze
     * scénáře. Poznámky pro střih do titulků nejdou. Text smazaného panelu
     * připadne předchozímu panelu.
     */
    fun srt(panely: List<SbPanel>, strih: List<SbTextStrihu>): String {
        val casy = casyPanelu(panely)
        var n = 0
        return strih.filter { it.druh != SbTextStrihu.Druh.POZNAMKA }.mapNotNull { r ->
            val (od, doS) = casPro(casy, r.cislo) ?: return@mapNotNull null
            n++
            "$n\n${casSrt(od)} --> ${casSrt(doS)}\n${r.text}\n"
        }.joinToString("\n")
    }

    /** Čas panelu; smazaný panel → nejbližší předchozí, jinak první. */
    fun casPro(casy: Map<Int, Pair<Double, Double>>, cislo: Int): Pair<Double, Double>? =
        casy[cislo] ?: casy.filterKeys { it < cislo }.maxByOrNull { it.key }?.value ?: casy.minByOrNull { it.key }?.value

    fun casSrt(s: Double): String {
        val ms = (s * 1000).roundToInt()
        return "%02d:%02d:%02d,%03d".format(ms / 3600000, (ms / 60000) % 60, (ms / 1000) % 60, ms % 1000)
    }

    /** Začátek a konec panelu v hotovém filmu (pro seznam Texty do střihu). */
    fun casyPanelu(panely: List<SbPanel>): Map<Int, Pair<Double, Double>> {
        val out = mutableMapOf<Int, Pair<Double, Double>>()
        var t = 0.0
        // Panel delší než úsek se rozpůlí do dvou kusů se stejným číslem.
        SbFilmPlan.rozdel(panely).flatMap { it.panely }.forEach { p ->
            out[p.cislo] = (out[p.cislo]?.first ?: t) to t + p.sekundy
            t += p.sekundy
        }
        return out
    }
}
