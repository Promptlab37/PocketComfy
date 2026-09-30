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
    /** Rekvizity a produkt z hlavičky — nikdy nemluví (MÍLA: „jogurt, co má čas“ je nápis). */
    val rekvizity: Set<String> = emptySet(),
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
    internal val ZNACKA_CISLO = Regex("""(?m)^[ \t]*(?:\[|#)?[ \t]*(\d{1,2})(?:[ \t]*\]|[.)](?!\d)|[ \t]*$|[ \t]*(?=\|)|[ \t]*(?=\(\d)|[ \t]+(?=[–—][ \t]))[ \t]*""")
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
        "text on screen" to Pole.TEXT, "on screen" to Pole.TEXT, "na displeji" to Pole.TEXT, "na obrazovce" to Pole.TEXT, "nápis na zdi" to Pole.TEXT,
        "na zdi" to Pole.TEXT, "cedule" to Pole.TEXT, "sms" to Pole.TEXT, "titulek" to Pole.TEXT, "titulky" to Pole.TEXT, "caption" to Pole.TEXT,
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
            """postavy|rekvizity|rekvizita|produkt|product|characters|cast|props|obsazení|hrají|účinkují|prostředí|prostor|lokace|setting|location|místo děje|""" +
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
        """(?<![\p{L}\d.])((?:\p{Lu}\p{L}{1,5}\.[ \t]+)?[\p{L}][\p{L}\d]{1,20}(?:[ \t]\d{1,3}(?=[ \t]*[,(:]))?(?:(?:[ \t]\p{Lu}[\p{L}]{1,20}){1,2}|[ \t]\p{Ll}[\p{L}]{1,20}(?=[ \t]*,))?)((?:[ \t]*,[ \t]*\p{Ll}[\p{L} ]{1,30}?)|(?:[ \t]+\p{Ll}[\p{L}]{0,15}){0,3}?)[ \t]*""" +
            """(?:\(([^)\n]{1,40})\))?[ \t]*:[ \t]*[„“"«]([^„“"«»”\n]{1,300})[“”"»]""",
    )

    /** Řádek repliky bez uvozovek: `DCERA: To jsi ty?`, `MAMINKA (dojatě): To jsem já…`. */
    internal val RADEK_MLUVCI = Regex(
        // Druhé slovo jména jen velkým písmenem — „Otec tiše:“ je jméno a podání.
        """^[ \t]*((?:\p{Lu}\p{L}{1,5}\.[ \t]+)?[\p{L}][\p{L}\d]{1,20}(?:[ \t]\d{1,3}(?=[ \t]*[,(:]))?(?:(?:[ \t]\p{Lu}[\p{L}]{1,20}){1,2}|[ \t]\p{Ll}[\p{L}]{1,20}(?=[ \t]*,))?)((?:[ \t]*,[ \t]*\p{Ll}[\p{L} ]{1,30}?)|(?:[ \t]+\p{Ll}[\p{L}]{0,15}){0,3}?)[ \t]*""" +
            """(?:\(([^)\n]{1,40})\))?[ \t]*:[ \t]*(.+)$""",
    )

    /**
     * Replika celá v uvozovkách, i s uvozovkami uvnitř (`"Říkali mi "Květa"."`)
     * — jinak by se rozpadla na dvě.
     */
    private fun celaVUvozovkach(obsah: String): String? =
        (Regex("""^[„“"«](.+)[“”"»][.!?…]?$""").find(obsah.trim()) ?: Regex("""^'(.+)'[.!?…]?$""").find(obsah.trim()))
            ?.groupValues?.get(1)?.trim()
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
    fun uprav(vstup: String): String = spojReplikyPresRadek(zapisH3(predupravy(vstup.replace('\u00A0', ' ').replace("\r", ""))))
        .replace(Regex("""[\u200B-\u200D\uFE0E\uFE0F]"""), "")
        .replace(Regex("""\p{So}"""), "")
        .replace("**", "").replace("__", "")
        .lines().joinToString("\n") { r ->
            r.replace(Regex("""^[ \t]*(?:#{1,6}[ \t]*|>[ \t]*)+"""), "").replace(Regex("""^[ \t]*[-•*–—][ \t]+"""), "")
        }

    /**
     * Zápisy, které rozbor jinak nezná (korpus scénářů 30. 9. 2026 — ChatGPT, Claude,
     * Gemini i lidé na telefonu): markdown tabulka, *kurzíva*, „S1 (Martin)“,
     * „Postava: X / Replika (jak): …“, „Tomáš [podrážděně] — „…““, „Rozálie – plačtivě: …“.
     */
    internal fun predupravy(t0: String): String {
        var t = tabulkaNaText(tabulatoryNaTabulku(bezPovidani(t0
            // Emoji před jménem („🗣️ Děda Josef – trpělivě:“) — dřív se mazaly až po převodech.
            .replace(Regex("""[\u200B-\u200D\uFE0E\uFE0F]"""), "").replace(Regex("""\p{So}"""), ""))))
            .replace(Regex("""‚([^‘\n]{1,300})‘"""), "„$1“").replace(Regex("""»([^«\n]{1,300})«"""), "„$1“")
            .replace(Regex("""«([^»\n]{1,300})»"""), "„$1“")
            .replace("**", "").replace("__", "")
            .lines().joinToString("\n") { it.replace(Regex("""^[ \t]*[-•*][ \t]+(?=\S)"""), "") }
        // Jednoduchá kurzíva kolem jména nebo podání: *(nervózně)*, *Visual:*, _tiše_.
        t = t.replace(Regex("""(?<![*\p{L}\d])\*(?!\*)([^*\n]{1,80}?)\*(?![*\p{L}\d])"""), "$1")
            .replace(Regex("""(?<![_\p{L}\d])_(?!_)([^_\n]{1,80}?)_(?![_\p{L}\d])"""), "$1")
        // „00:00–00:03 — Záběr 1“ → „Záběr 1 (00:00–00:03)“: čas před značkou patří k oknu (kritik 30. 9. 2026).
        t = Regex("""(?imu)^[ \t]*(\d{1,2}(?::\d{2})?(?:[.,]\d+)?[ \t]*s?[ \t]*[–—-][ \t]*\d{1,2}(?::\d{2})?(?:[.,]\d+)?[ \t]*s?)[ \t]*[–—|:-]?[ \t]*((?:$SLOVA_OKNA)[ \t]*(?:č\.?[ \t]*)?\d{1,2}[^
]*)$""")
            .replace(t) { m -> m.groupValues[2].trimEnd() + " (" + m.groupValues[1].trim() + ")" }
        // Vypravěč pod jakýmkoli označením → „Vypravěč“ (Voice-over se dřív četl jako mluvčí „Voice“).
        t = Regex("""(?imu)^([ \t]*)(voice[- ]?over|v\.o\.|vo|narrator|narration|hlas mimo obraz|hlas vypravěče|vypravěčka|vypravěč|mluvený komentář|komentář|komentar)[ \t]*(?:\(([^)\n]{1,40})\))?[ \t]*:""")
            .replace(t) { m ->
                val jak = m.groupValues[3].trim().takeUnless { Regex("""(?i)^(v\.?o\.?|o\.?s\.?|voice[- ]?over|off)$""").matches(it) }.orEmpty()
                m.groupValues[1] + "Vypravěč" + (if (jak.isNotBlank()) " ($jak)" else "") + ":"
            }
        // „S1 (Martin)“ → Martin (jméno v závorce za kódem mluvčího).
        t = t.replace(Regex("""(?<![\p{L}\d])S\d{1,2}[ \t]*\((\p{Lu}\p{Ll}+(?:[ \t]\p{Lu}\p{Ll}+)?)\)"""), "$1")
        // „Postava: Zdeněk“ + „Replika (zoufale): …“ → „Zdeněk (zoufale): …“.
        t = Regex("""(?imu)^[ \t]*(?:postava|mluvčí|mluvci|speaker|character)[ \t]*:[ \t]*([^\n:]{1,40}?)[ \t]*\n[ \t]*(?:replika|dialog|dialogue|line|text repliky)[ \t]*(?:\(([^)\n]{1,40})\))?[ \t]*:[ \t]*([^\n]+)""")
            .replace(t) { m -> m.groupValues[1].trim() + (m.groupValues[2].takeIf { it.isNotBlank() }?.let { " ($it)" } ?: "") + ": " + m.groupValues[3].trim() }
        // „Tomáš [podrážděně] — „…““ / „Tomáš — „…““ (bez dvojtečky).
        t = Regex("""(?m)^([ \t]*\p{Lu}[\p{L}\d.]{1,20}(?:[ \t]\p{Lu}[\p{L}]{1,20}){0,2})[ \t]*(?:\[([^\]\n]{1,40})\][ \t]*)?[–—-][ \t]*(?=[„“"«»])""")
            .replace(t) { m ->
                val jm = m.groupValues[1].trim()
                if (jm.lowercase() in STITKY_JMENA || jm.lowercase() in NE_MLUVCI) m.value
                else jm + (m.groupValues[2].takeIf { it.isNotBlank() }?.let { " ($it)" } ?: "") + ": "
            }
        t = Regex("""(?m)^([ \t]*\p{Lu}[\p{L}\d.]{1,20}(?:[ \t]\p{Lu}[\p{L}]{1,20}){0,2})[ \t]*\[([^\]\n]{1,40})\][ \t]*:""")
            .replace(t) { m -> m.groupValues[1] + " (" + m.groupValues[2] + "):" }
        // „Rozálie – plačtivě: …“ → „Rozálie (plačtivě): …“.
        t = Regex("""(?m)^([ \t]*\p{Lu}[\p{L}\d.]{1,20}(?:[ \t]\p{Lu}[\p{L}]{1,20}){0,2})[ \t]*[–—-][ \t]*(\p{Ll}[\p{L}]{0,20}(?:[ \t]\p{Ll}[\p{L}]{1,20}){0,2})[ \t]*:""")
            .replace(t) { m ->
                val jm = m.groupValues[1].trim()
                if (jm.lowercase() in STITKY_JMENA || jm.lowercase() in NE_MLUVCI) m.value
                else m.groupValues[1] + " (" + m.groupValues[2] + "):"
            }
        return t
    }

    /** Čárky mimo závorky a uvozovky rozdělí řádek na kusy. */
    private fun mimoZavorky(t: String): List<String> {
        val out = mutableListOf<String>()
        var hloubka = 0
        var uvoz = false
        val sb = StringBuilder()
        t.forEach { ch ->
            when {
                ch == '(' -> { hloubka++; sb.append(ch) }
                ch == ')' -> { hloubka = maxOf(0, hloubka - 1); sb.append(ch) }
                ch in "„“”\"" -> { uvoz = ch == '„' || (ch == '"' && !uvoz); sb.append(ch) }
                (ch == ',' || ch == ';') && hloubka == 0 && !uvoz -> { out += sb.toString().trim(); sb.clear() }
                else -> sb.append(ch)
            }
        }
        if (sb.isNotBlank()) out += sb.toString().trim()
        return out.filter { it.isNotBlank() }
    }

    /** Tabulka z tabulkového procesoru (sloupce oddělené tabulátorem) → markdown tabulka. */
    private fun tabulatoryNaTabulku(t: String): String {
        val radky = t.lines()
        val iHlava = radky.indexOfFirst { r ->
            r.count { it == '\t' } >= 2 && Regex("""(?iu)^[ \t]*(okno|záběr|zaber|scéna|scena|panel|shot|#|č\.)[ \t]*\t""").containsMatchIn(r)
        }
        if (iHlava < 0) return t
        val sloupcu = radky[iHlava].split('\t').size
        // Buňka zalomená do dalšího řádku (Word): řádek, který nezačíná číslem okna, pokračuje předchozí buňkou.
        val bunky = mutableListOf<MutableList<String>>()
        val out = mutableListOf<String>()
        radky.forEachIndexed { i, r ->
            when {
                i < iHlava -> out += r
                i == iHlava -> bunky += r.split('\t').map { it.trim() }.toMutableList()
                r.isBlank() -> {}
                Regex("""^[ \t]*\d{1,2}[ \t]*\t""").containsMatchIn(r) || bunky.size <= 1 ->
                    bunky += r.split('\t').map { it.trim() }.toMutableList()
                else -> {
                    val dalsi = r.split('\t').map { it.trim() }
                    val posledni = bunky.last()
                    posledni[posledni.size - 1] = posledni.last() + "<br>" + dalsi.first()
                    posledni += dalsi.drop(1)
                }
            }
        }
        out += bunky.map { b -> "| " + b.take(sloupcu).joinToString(" | ") { it.ifBlank { "—" } } + " |" }
        return out.joinToString("\n")
    }

    /** Úvodní a závěrečné věty asistenta („Zde je návrh…“, „Chcete ještě…?“). */
    private val POVIDANI = Regex(
        """(?iu)^[ \t]*(zde je|tady je|tady máš|tady máte|níže je|níže najdeš|níže najdete|samozřejmě|jasně[,!]|rád[a]? pomůžu|doufám|pokud chce|chcete|chceš|mám (?:doplnit|připravit|upravit|napsat)|můžu (?:doplnit|připravit|upravit|napsat)|mohu (?:doplnit|připravit|upravit|napsat)|dejte vědět|dej vědět|celé video vychází|here is|here's|sure[,!]|hope this|let me know|would you like|pro krátké video)""",
    )

    /**
     * Pryč s tím, co do scénáře nepatří: čísla stránek a záhlaví stránek z PDF/Wordu,
     * citace [1], sekce se zdroji, vodorovné čáry, povídání asistenta na začátku a na konci.
     * „Poznámky k produkci“ na konci se přesunou nahoru jako kontinuita celého filmu.
     */
    private fun bezPovidani(t0: String): String {
        var radky = t0.lines()
        // Zdroje / Sources až do konce.
        radky.indexOfFirst { Regex("""(?iu)^[ \t*#]*(zdroje|sources|reference|citace)[ \t*]*:?[ \t*]*$""").matches(it) }
            .takeIf { it > 0 }?.let { radky = radky.take(it) }
        radky = radky.filterNot { r ->
            val x = r.trim()
            Regex("""^[-–—]\s*\d{1,3}\s*[-–—]$""").matches(x) ||
                Regex("""(?iu)^(strana|str\.|page)\s*\d{1,3}(\s*(z|/|of)\s*\d{1,3})?$""").matches(x) ||
                Regex("""(?iu)^.{0,70}\s{3,}(strana|str\.|page)\s*\d{1,3}$""").matches(x) ||
                Regex("""^[-–—*_=~]{3,}$""").matches(x)
        }.map { it.replace(Regex("""(?<=[\p{L}.,!?“”"»)])(\[\d{1,2}])+"""), "") }
        // Povídání na začátku (před prvním obsahem) a na konci.
        while (radky.isNotEmpty() && (radky.first().isBlank() || POVIDANI.containsMatchIn(radky.first()))) radky = radky.drop(1)
        while (radky.isNotEmpty() && (radky.last().isBlank() || POVIDANI.containsMatchIn(radky.last()) ||
                Regex("""(?iu)^[ \t*#]*(tipy?|tip pro|pozn(á|a)mka pro (tebe|vás))[ \t*]*:""").containsMatchIn(radky.last()))) radky = radky.dropLast(1)
        // Poznámky k produkci na konci → kontinuita na začátek (hudba ne).
        val iPozn = radky.indexOfLast { Regex("""(?iu)^[ \t*#]*(poznámky k produkci|produkční poznámky|poznámky pro natáčení|production notes)[ \t*]*:?[ \t*]*$""").matches(it) }
        if (iPozn > 0) {
            val pozn = radky.drop(iPozn + 1).map { it.trim().trimStart('*', '-', '•', ' ').replace("**", "") }
                .filter { it.isNotBlank() && !Regex("""(?iu)^(hudba|music)\s*:""").containsMatchIn(it) }
            radky = listOf("Kontinuita: " + pozn.joinToString(" ")).takeIf { pozn.isNotEmpty() }.orEmpty() + radky.take(iPozn)
        }
        return radky.joinToString("\n")
    }

    /**
     * Markdown tabulka (okno | čas | obraz | [postava] | replika) → běžný zápis oken.
     * Řádky se stejným číslem okna patří do jednoho okna; „—“ je prázdná buňka.
     */
    internal fun tabulkaNaText(t: String): String {
        val radky = t.lines()
        if (radky.count { it.trim().startsWith("|") } < 3) return t
        val out = mutableListOf<String>()
        var i = 0
        while (i < radky.size) {
            if (!radky[i].trim().startsWith("|")) { out += radky[i]; i++; continue }
            val blok = mutableListOf<String>()
            while (i < radky.size && radky[i].trim().startsWith("|")) { blok += radky[i].trim(); i++ }
            fun bunky(r: String) = r.trim().trim('|').split("|").map { it.trim() }
            val hlava = bunky(blok.first()).map { it.lowercase().replace("*", "") }
            fun sloupec(vzor: String) = hlava.indexOfFirst { Regex("""(?iu)^($vzor)""").containsMatchIn(it) }
            val sOkno = sloupec("""okno|záběr|zaber|scéna|scena|panel|shot|window|#|č\.|číslo""")
            if (sOkno < 0) { out += blok; continue }
            val sCas = sloupec("""čas|time|trvání|délka""")
            val sObraz = sloupec("""obraz|popis|akce|děj|visual|picture|action|co vidíme""")
            val sKdo = sloupec("""postava|mluvčí|kdo|speaker|character""")
            val sReplika = hlava.indexOfFirst { Regex("""(?iu)replik|dialog|line|mluvené|speech""").containsMatchIn(it) }
            val sZvuk = hlava.indices.firstOrNull { it != sReplika && Regex("""(?iu)^(zvuk|sound|sfx)""").containsMatchIn(hlava[it]) } ?: -1
            val sText = sloupec("""text na|titulek|on-screen|nápis""")
            fun prazdna(v: String?) = v.isNullOrBlank() || v.trim() in setOf("—", "–", "-", "…", "x")
            var posledni = ""
            blok.drop(1).filterNot { Regex("""^\|?[\s:|-]+\|?$""").matches(it) }.forEach { r ->
                val b = bunky(r)
                val cislo = b.getOrNull(sOkno).orEmpty().replace("*", "").trim()
                if (cislo.isNotBlank() && cislo != posledni) {
                    posledni = cislo
                    val cas = b.getOrNull(sCas)?.takeUnless { prazdna(it) }?.let { " ($it)" }.orEmpty()
                    out += ""
                    out += "OKNO ${cislo.filter { it.isDigit() }.ifBlank { cislo }}$cas"
                    b.getOrNull(sObraz)?.takeUnless { prazdna(it) }?.let { out += "Obraz: $it" }
                    b.getOrNull(sZvuk)?.takeUnless { prazdna(it) }?.let { out += "Zvuk: $it" }
                    b.getOrNull(sText)?.takeUnless { prazdna(it) }?.let { out += "Text na videu: $it" }
                } else b.getOrNull(sObraz)?.takeUnless { prazdna(it) }?.let { out += it }
                val rep = b.getOrNull(sReplika)?.takeUnless { prazdna(it) } ?: return@forEach
                val kdo = b.getOrNull(sKdo)?.takeUnless { prazdna(it) }
                rep.split(Regex("""\s+/\s+|<br\s*/?>""")).map { it.trim() }.filter { it.isNotBlank() }.forEach { kus ->
                    out += when {
                        kdo != null -> "$kdo: $kus"
                        Regex("""^\*?\(.*\)\*?$""").matches(kus) -> "Zvuk: " + kus.trim('*', '(', ')')
                        else -> kus
                    }
                }
            }
        }
        return out.joinToString("\n")
    }

    private val ID_V_POSTAVACH = Regex("""(?m)^([ \t]*(?:[-•*–—][ \t]+)?)\(?S(\d{1,2})\)?[ \t]*[–—=-][ \t]*(\p{Lu}[\p{L}]*(?:[ \t]\p{L}+){0,2}?)[ \t]*(?=[:,(–—-])""")

    /**
     * Scénář zapsaný ve tvaru H3 (Otevřené dveře 30. 9. 2026): repliky v `<d>…</d>`
     * místo uvozovek a mluvčí jako S1–S4 s obsazením „S1 – Návštěvník: …“.
     * Rozbor našel 0 replik. Převede se na běžný zápis: `<d>` → „…“, S1 → jméno
     * z obsazení (u řádku obsazení i u mluvčího na začátku řádku).
     */
    internal fun zapisH3(t: String): String {
        var s = t
        val jmena = ID_V_POSTAVACH.findAll(s).associate { it.groupValues[2] to it.groupValues[3].trim() }
        if (jmena.isNotEmpty()) {
            s = ID_V_POSTAVACH.replace(s) { m -> m.groupValues[1] + m.groupValues[3].trim() }
            s = Regex("""(?m)^([ \t]*)\(?S(\d{1,2})\)?(?=[ \t]*[,:(–—])""").replace(s) { m ->
                m.groupValues[1] + (jmena[m.groupValues[2]] ?: m.value.trim())
            }
        }
        return s.replace(Regex("""<d>\s*(?:\[[^\]\n]{1,20}]\s*)?(.*?)\s*</d>""", RegexOption.DOT_MATCHES_ALL), "„$1“")
    }

    /**
     * Replika „…“ zalomená přes řádek (PDF, úzké okno) se spojí — jinak by ji
     * rozbor nenašel. Jen české uvozovky: „ otevírá, “ zavírá, jednoznačně.
     */
    private fun spojReplikyPresRadek(t: String): String {
        var s = spojOtevreneUvozovky(spojRovneUvozovky(t))
        repeat(5) {
            val dalsi = s.replace(Regex("""(„[^“”„\n]{0,300})\n[ \t]*(?=[^\n]*“)"""), "$1 ")
            if (dalsi == s) return s
            s = dalsi
        }
        return s
    }

    /** Řádek s otevřenou „ bez zavírací pokračuje na dalších řádcích, dokud se nezavře (nejvýš 6 řádků). */
    private fun spojOtevreneUvozovky(t: String): String {
        val r = t.lines()
        val out = mutableListOf<String>()
        var i = 0
        fun otevrena(x: String) = x.count { it == '„' } > x.count { it == '“' || it == '”' || it == '"' }
        while (i < r.size) {
            var radek = r[i]
            var j = i + 1
            if (otevrena(radek)) {
                val kandidat = StringBuilder(radek)
                var k = i + 1
                while (k < r.size && k - i <= 6 && r[k].isNotBlank()) {
                    kandidat.append(' ').append(r[k].trim())
                    if (!otevrena(kandidat.toString())) { radek = kandidat.toString(); j = k + 1; break }
                    k++
                }
            }
            out += radek
            i = j
        }
        return out.joinToString("\n")
    }

    /** Řádek s lichým počtem rovných uvozovek pokračuje na dalším (PDF zalomil repliku). */
    private fun spojRovneUvozovky(t: String): String {
        val out = mutableListOf<String>()
        var otevreny: String? = null
        t.lines().forEach { r ->
            val o = otevreny
            if (o != null) {
                val spojeny = o.trimEnd() + " " + r.trim()
                otevreny = if (spojeny.count { it == '"' } % 2 == 1 && r.isNotBlank()) spojeny else { out += spojeny; null }
                return@forEach
            }
            val posledni = r.trimEnd().lastIndexOf('"')
            val otevira = r.count { it == '"' } % 2 == 1 && posledni < r.trimEnd().length - 1 && '„' !in r
            if (otevira) otevreny = r else out += r
        }
        otevreny?.let { out += it }
        return out.joinToString("\n")
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
        // „SCÉNA 1 / Záběr 1 / Záběr 2 / SCÉNA 2 / Záběr 1…“: okna jsou záběry; řádky scén jdou pryč
        // a záběry se očíslují popořadě (kritik 30. 9. 2026 — dřív zbyla 2 okna ze 4).
        vnoreneZabery(text)?.let { text = it }
        var znacky = rostouci(ZNACKA.findAll(text).toList())
        if (znacky.size < 2) {
            // Slitý text: značky na vlastní řádky.
            val rozdeleny = ZNACKA_V_RADKU.replace(text, "\n")
            val z = rostouci(ZNACKA.findAll(rozdeleny).toList())
            if (z.size >= 2) { text = rozdeleny; znacky = z }
        }
        if (znacky.size < 2) {
            val vse = ZNACKA_CISLO.findAll(text).toList()
            znacky = vse.indices.filter { vse[it].groupValues[1].toInt() == 1 }.map { start ->
                vse.drop(start).fold(mutableListOf<MatchResult>()) { acc, m ->
                    val n = m.groupValues[1].toInt()
                    if (n == (acc.lastOrNull()?.groupValues?.get(1)?.toInt() ?: 0) + 1) acc += m
                    acc
                }
            }.fold(emptyList()) { nej, rada -> if (rada.size >= nej.size) rada else nej }
        }
        if (znacky.size < 2) {
            val casove = Regex("""(?m)^[ \t]*(\d{1,2}(?::\d{2}){1,2}(?:[.,]\d+)?[ \t]*[–—-][ \t]*\d{1,2}(?::\d{2}){1,2}(?:[.,]\d+)?)[ \t]*[|:–—-]?[ \t]*""")
            if (casove.findAll(text).count() >= 2) {
                var n = 0
                text = casove.replace(text) { m -> n++; "OKNO $n (${m.groupValues[1]})\n" }
                znacky = rostouci(ZNACKA.findAll(text).toList())
            }
        }
        if (znacky.size < 2) {
            val cislo = Regex("""(?m)^[ \t]*(\d{1,2})[ \t]+(?=\p{L})""").findAll(text).toList()
            val rada = cislo.fold(mutableListOf<MatchResult>()) { acc, m ->
                if (m.groupValues[1].toInt() == (acc.lastOrNull()?.groupValues?.get(1)?.toInt() ?: 0) + 1) acc += m
                acc
            }
            if (rada.size >= 2) {
                val mista = rada.map { it.range.first }.toSet()
                val sb = StringBuilder()
                var posl = 0
                rada.forEach { m -> sb.append(text, posl, m.range.first).append("OKNO ${m.groupValues[1]}\n"); posl = m.range.last + 1 }
                sb.append(text.substring(posl))
                if (mista.isNotEmpty()) { text = sb.toString(); znacky = rostouci(ZNACKA.findAll(text).toList()) }
            }
        }
        if (znacky.size < 2) return null
        val hlavicka = rozeberHlavicku(text.substring(0, znacky.first().range.first))
        val celyFilm = mutableListOf<String>()
        val znami = (hlavicka.postavy.keys - hlavicka.rekvizity + mluvciBezSeznamu(text.substring(znacky.first().range.first)))
            .filterNot { k -> hlavicka.rekvizity.any { it.equals(k, true) } }.toSet()
        rekvizityOkna = hlavicka.rekvizity.flatMap { it.lowercase().split(Regex("""\s+""")) }.filter { it.length >= 3 }.toSet()
        val okna = znacky.mapIndexed { i, m ->
            val konec = znacky.getOrNull(i + 1)?.range?.first ?: text.length
            rozeberOkno(m.groupValues[1].toInt(), text.substring(m.range.last + 1, konec), znami, celyFilm)
        }
        val kontinuita = (listOf(hlavicka.kontinuita) + celyFilm).filter { it.isNotBlank() }.joinToString(" ")
        // Jen délky („OKNO 1 (3 s)“, „#2 | 4 s“) → časy popořadě; dřív se délka zahodila.
        val delky = znacky.map { m ->
            val radek = text.substring(m.range.last + 1).substringBefore('\n')
            if (cas(radek) != null) null
            else Regex("""(?<![\d:.,–—-])(\d{1,2}(?:[.,]\d)?)[ \t]*(?:s|sek|sekund|sec)(?![\p{L}])""").find(radek)
                ?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull()
        }
        val sCasy = if (okna.all { it.od == null } && delky.all { it != null && it > 0 }) {
            var t = 0.0
            okna.mapIndexed { i, o -> val d = delky[i]!!; o.copy(od = t, doS = t + d).also { t += d } }
        } else okna
        return hlavicka.copy(kontinuita = kontinuita, okna = sCasy.map { sjednotMluvci(it, hlavicka.postavy) })
    }

    /** Jen rostoucí čísla — „jako v okně 3“ značka není. */
    private fun vnoreneZabery(text: String): String? {
        val vse = ZNACKA.findAll(text).toList()
        if (vse.size < 3) return null
        fun slovo(m: MatchResult) = Regex("""(?iu)\p{L}+""").find(m.value)?.value?.lowercase().orEmpty()
        val druhy = vse.groupBy { slovo(it) }
        if (druhy.size < 2) return null
        // Vnitřní úroveň = druh, jehož číslování začíná znovu od 1.
        val vnitrni = druhy.entries.firstOrNull { (_, z) -> z.count { it.groupValues[1].toInt() == 1 } >= 2 }?.key ?: return null
        var n = 0
        val radky = text.lines().map { r ->
            val m = ZNACKA.find(r)?.takeIf { r.substring(0, it.range.first).isBlank() }
            when {
                m == null -> r
                slovo(m) == vnitrni -> { n++; r.replaceRange(m.range, "OKNO $n") }
                else -> ""
            }
        }
        return radky.joinToString("\n")
    }

    private fun rostouci(z: List<MatchResult>): List<MatchResult> = z.fold(mutableListOf()) { acc, m ->
        val n = m.groupValues[1].toInt()
        if (acc.isEmpty() || n > acc.last().groupValues[1].toInt()) acc += m
        acc
    }

    /**
     * Klasický filmový zápis v okně: JMÉNO velkými na samostatném řádku, pod ním
     * případně (podání) a replika. Převede se na „JMÉNO (podání): replika“.
     */
    private fun filmovyFormat(telo: String): String {
        val r = telo.lines().toMutableList()
        val out = mutableListOf<String>()
        var i = 0
        while (i < r.size) {
            val jm = r[i].trim().replace(Regex("""(?iu)\s*\((pokrač\.?|pokračuje|cont'?d|contd|v\.?o\.?|o\.?s\.?|mimo obraz|off)\)$"""), "")
            val bezTitulu = jm.replace(Regex("""^\p{Lu}\p{L}{0,5}\.\s+"""), "")
            val jeJmeno = bezTitulu.length in 2..30 && bezTitulu == bezTitulu.uppercase() && bezTitulu.any { it.isLetter() } &&
                Regex("""^[\p{Lu}][\p{Lu}\d ]{1,29}$""").matches(bezTitulu) && bezTitulu.split(' ').size <= 3 &&
                jm.lowercase() !in STITKY_JMENA && jm.lowercase() !in NE_MLUVCI && jm.lowercase().split(' ').first() !in NE_MLUVCI
            if (jeJmeno && i > 0) {
                var j = i + 1
                var jak = ""
                r.getOrNull(j)?.trim()?.let { p -> Regex("""^\(([^)]{1,40})\)$""").find(p)?.let { jak = it.groupValues[1]; j++ } }
                val replika = r.getOrNull(j)?.trim().orEmpty()
                val jeReplika = replika.isNotBlank() && replika != replika.uppercase() &&
                    !Regex("""^[\p{L} ]{1,40}:""").containsMatchIn(replika)
                if (jeReplika) {
                    // Pokračovací řádky repliky až do prázdného řádku, dalšího JMÉNA nebo štítku.
                    val kusy = mutableListOf(replika)
                    var k = j + 1
                    while (k < r.size) {
                        val d = r[k].trim()
                        val predchozi = kusy.last().trimEnd()
                        if (d.isEmpty() || (d == d.uppercase() && d.any { it.isLetter() }) || d.startsWith("(") ||
                            Regex("""^[\p{L} ]{1,40}:""").containsMatchIn(d) || ZNACKA.containsMatchIn(r[k]) ||
                            // „…roh a…“ + „Přiletí poryv větru…“ — replika skončila, dál je děj.
                            (predchozi.last() in ".!?…" && d.first().isUpperCase())) break
                        kusy += d; k++
                    }
                    out += jm + (if (jak.isNotBlank()) " ($jak)" else "") + ": " + kusy.joinToString(" ")
                    i = k
                    continue
                }
            }
            out += r[i]
            i++
        }
        return out.joinToString("\n")
    }

    /**
     * „Mladá“ + podání „žena“ → mluvčí „Mladá žena“: první slovo podání je role
     * (žena, muž, dívka…), ne způsob řeči. Vrátí (jméno, zbytek podání).
     */
    private fun sRoli(jm: String, podaniSlovy: String): Pair<String, String> {
        val slova = podaniSlovy.trim().trimStart(',').trim().split(Regex("""\s+""")).filter { it.isNotBlank() }
        val prvni = slova.firstOrNull()?.lowercase() ?: return jm to podaniSlovy
        val pridavne = Regex("""(?iu)^\p{L}+[ýáé]$""").matches(jm.trim().split(Regex("""\s+""")).last())
        return if (pridavne && (prvni in ZENSKE || prvni in MUZSKE)) "$jm ${slova.first()}" to slova.drop(1).joinToString(" ")
        else jm to podaniSlovy
    }

    /** Slovo, které je role (číšník, pomocnice, včelař) — ze seznamů nebo podle přípony. */
    internal fun jeRole(slovo: String): Boolean {
        val w = slovo.lowercase().trim('.', ',')
        if (w in ZENSKE || w in MUZSKE || w in DETI || w in RODICE || w in PRARODICE) return true
        if (w in STITKY_JMENA || w in NE_MLUVCI || w in STITKY_NAVIC) return false
        return Regex("""(nice|yně|ník|ník|ař|ář|tel|telka|ista|istka|ér|érka|ačka|ečka|ička|ák|ačka)$""").containsMatchIn(w)
    }

    /** Slova z názvů rekvizit rozebíraného scénáře — mluvčí jimi být nemůže. */
    @Volatile private var rekvizityOkna: Set<String> = emptySet()

    /** „Jana … řekne „…““, „Lucka … přečte: „…““ — mluvčí na začátku věty, sloveso řeči před uvozovkou. */
    private val SLOVESO_RECI = Regex(
        """(?<![\p{L}])(\p{Lu}[\p{L}]{1,20})(?![\p{L}])((?:[^.!?„“"«\n]{0,90}?)(?<![\p{L}])(?:řekne|přečte|zavolá|zašeptá|odpoví|zakřičí|vykřikne|dodá|poví|zamumlá|špitne|houkne|zeptá se|volá|říká|čte)(?:[ \t]+\p{Ll}+)?[ \t]*:?[ \t]*[„“"«]([^„“"«»”\n]{1,300})[“”"»])""",
    )

    /** Štítky, které lidé píšou velkými písmeny (LOKACE:, SVĚTLO:, PŘECHOD:) — nikdy mluvčí. */
    private val STITKY_NAVIC = setOf(
        "lokace", "světlo", "svetlo", "osvětlení", "přechod", "prechod", "fx", "vfx", "sfx", "oblečení", "kostým", "kostymy",
        "atmosféra", "exteriér", "interiér", "int", "ext", "nápis", "napis", "displej", "displeji", "obrazovka", "obrazovce",
        "cedule", "ceduli", "sms", "zpráva", "zprava", "titulek", "střih", "strih", "rekvizita", "rekvizity", "hudba",
        "prostředí", "prostor", "čas", "místo", "misto", "den", "noc", "ráno", "večer", "detail", "celek", "polocelek",
    )

    /** „Petr - hodnej kluk…“ u známého mluvčího → „Petr: hodnej kluk…“. */
    private fun pomlckaMluvci(telo: String, znami: Set<String>): String = telo.lines().map { r ->
        val m = Regex("""^([ \t]*)([\p{L}][\p{L}]{1,20})[ \t]+(?=[„“"«'])""").find(r) ?: return@map r
        if (znami.any { it.equals(m.groupValues[2], true) }) m.groupValues[1] + m.groupValues[2] + ": " + r.substring(m.range.last + 1) else r
    }.joinToString("\n") { r ->
        val m = Regex("""^[ \t]*(\p{Lu}[\p{L}]{1,20})[ \t]+[–—-][ \t]+(\S.*)$""").find(r) ?: return@joinToString r
        val jm = m.groupValues[1]
        if (znami.any { it.equals(jm, true) || it.lowercase().split(' ').contains(jm.lowercase()) } && !Regex("""^\d""").containsMatchIn(m.groupValues[2]))
            "$jm: ${m.groupValues[2]}" else r
    }

    /**
     * Replika bez uvozovek zalomená do dalšího řádku (Word, Google Docs, PDF): když řádek
     * mluvčího nekončí tečkou/otazníkem/vykřičníkem, další řádek (není-li štítek, mluvčí
     * ani značka) je její pokračování.
     */
    private fun pokracovaniReplik(telo: String): String {
        val r = telo.lines()
        val out = mutableListOf<String>()
        r.forEach { radek ->
            val posledni = out.lastOrNull()
            val t = radek.trim()
            val jePokracovani = posledni != null && t.isNotEmpty() &&
                RADEK_MLUVCI.find(posledni)?.let { m ->
                    val obsah = m.groupValues[4].trim().replace(Regex("""^\([^)]{1,40}\)\s*"""), "")
                    obsah.isNotEmpty() && obsah.first() !in "„“\"«'(" &&
                        (obsah.last() !in ".!?…“”\"»:" || ((obsah.endsWith("…") || obsah.endsWith("...")) && (t.first().isLowerCase() || t.startsWith("…"))))
                } == true &&
                !Regex("""^[\p{L}][\p{L}\d .]{0,40}(\([^)]*\))?[ \t]*:""").containsMatchIn(t) &&
                !Regex("""^\(""").containsMatchIn(t) && t != t.uppercase()
            if (jePokracovani) out[out.size - 1] = posledni!!.trimEnd() + " " + t else out += radek
        }
        return out.joinToString("\n")
    }

    /** „(přátelsky) Dobrý den.“ — podání v závorce hned za dvojtečkou. */
    private val PODANI_ZA_DVOJTECKOU = Regex("""^\(([^)\n]{1,40})\)\s*(.+)$""")

    /** Štítky hlavičky a poznámek, které nejsou mluvčí („Prostředí: …“, „Poznámka: …“). */
    private val NE_MLUVCI = setOf(
        "název", "nazev", "title", "formát", "format", "délka", "length", "postavy", "postava", "rekvizity", "rekvizita",
        "props", "prop", "kontinuita", "continuity", "prostředí", "prostor", "lokace", "místo", "setting", "location",
        "poznámka", "poznámky", "note", "notes", "styl", "style", "tón", "tone", "cíl", "goal", "hudba", "music",
        "střih", "cut", "čas", "time", "trvání", "duration", "scéna", "scene", "okno", "záběr", "panel", "shot",
        "obsazení", "cast", "characters", "tip", "pozor", "upozornění", "varianta", "verze",
        // Předložky a spojky — „Na obrazovce: …“ není mluvčí „Na“ (hrnek 30. 9. 2026).
        "na", "v", "ve", "u", "do", "od", "po", "při", "za", "před", "mezi", "pod", "nad", "o", "s", "se", "k", "ke",
        "z", "ze", "a", "i", "the", "in", "on", "at", "with", "then", "pak", "potom", "nakonec", "konec", "závěr",
        "počasí", "pozadí", "barvy", "barva", "nálada", "atmosféra", "efekt", "efekty", "tempo", "rytmus", "poměr",
        "rozlišení", "žánr", "publikum", "cílovka", "produkt", "značka", "logo", "hashtag", "hashtagy", "popisek",
        "hook", "háček", "pointa", "úvod", "začátek", "koncept", "concept", "téma", "námět", "tipy", "zdroje", "sources",
        "kamera", "camera", "zvuk", "sound", "obraz", "visual", "akce", "action", "děj", "dialog", "dialogue",
    )

    /**
     * Mluvčí scénáře bez seznamu postav (human_01 30. 9. 2026: „Máma: Dobré ráno…“ bez
     * uvozovek se dřív zahodil — repliky bez uvozovek bral rozbor jen od známých postav).
     * Jméno na začátku řádku před dvojtečkou platí, když se opakuje, je to role (máma,
     * číšník) nebo se jméno objeví i jinde v textu.
     */
    private fun mluvciBezSeznamu(telo: String): Set<String> {
        val radky = telo.lines().mapNotNull { RADEK_MLUVCI.find(it)?.groupValues?.get(1)?.trim() }
            .filter { j ->
                val l = j.lowercase()
                l !in STITKY_JMENA && l !in NE_MLUVCI && l.split(' ').first() !in NE_MLUVCI &&
                    j.split(Regex("""\s+""")).size <= 3 && !Regex("""^\d""").containsMatchIn(j)
            }
        val pocty = radky.groupingBy { it.lowercase() }.eachCount()
        val celeRadky = telo.lines().mapNotNull { r -> RADEK_MLUVCI.find(r)?.let { it.groupValues[1].trim() to it.groupValues[4].trim() } }
        val jisti = radky.filter { j ->
            val l = j.lowercase()
            val role = l.split(' ').any { jeRole(it) }
            val jinde = Regex("""(?iu)(?<![\p{L}])""" + Regex.escape(l.take(maxOf(3, l.length - 2)))).findAll(telo).count() >= 2
            (pocty.getValue(l) >= 2 || role || jinde) && (j.first().isUpperCase() || role || pocty.getValue(l) >= 2)
        }.toSet()
        val doplneni = if (jisti.isEmpty()) emptySet() else radky.filter { j ->
            // Jméno, ne poznámka: „Důležité:“, „Hlášení:“, „Uvnitř:“ (přídavná jména, -ení, příslovce) neprojdou.
            j !in jisti && j.first().isUpperCase() && j.split(' ').size <= 2 &&
                !Regex("""(?iu)(é|ení|ání|ství|ost|ně|ky|itř|ku|ce)$""").containsMatchIn(j) &&
                j.lowercase() !in setOf("důležité", "pozor", "upozornění", "uvnitř", "venku", "mezitím", "později", "nakonec", "hlášení", "oznámení") &&
                celeRadky.any { (k, obsah) ->
                    k == j && obsah.firstOrNull()?.isUpperCase() == true &&
                        Regex("""[.!?…]["“”»]?$""").containsMatchIn(obsah) && obsah.split(' ').size >= 2
                }
        }.toSet()
        return (jisti + doplneni).map { jmeno(it) }.toSet()
    }

    /** Hlavička: název, formát, celková délka, postavy, kontinuita. */
    private fun rozeberHlavicku(text0: String): SbScenarCteni {
        // Nadpis bez dvojtečky na vlastním řádku („POSTAVY A REKVIZITY“ — Dar mudrců 30. 9. 2026).
        val text = text0.replace(
            // PROSTOR / PROSTŘEDÍ jako nadpis ukončí seznam postav (Otevřené dveře 30. 9. 2026:
            // „Vchodové dveře do domu jsou vlevo“ se četlo jako postava „Vchodové“).
            Regex("""(?imu)^[ \t]*(postavy a rekvizity|postavy|rekvizity|rekvizita|produkt|characters|cast|props|obsazení|hrají|účinkují|prostor|prostředí|lokace|místo děje|setting|location|kontinuita|continuity|styl|style|tón|tone|formát|format)[ \t]*$"""), "$1:",
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
        val rekvizity = mutableSetOf<String>()
        val kontinuita = mutableListOf<String>()
        // Další řádky pod názvem („Adaptace povídky O. Henryho pro realistické krátké video.“)
        // popisují celý film — dřív se tiše ztratily (kontrola pokrytí 30. 9. 2026).
        pred.lines().map { it.trim() }.filter { it.isNotBlank() }.drop(1)
            .filterNot { it.none { ch -> ch.isLowerCase() } }
            .forEach { kontinuita += it }
        if (stitky.none { Regex("""(?iu)postavy|characters|cast|obsazení|hrají|účinkují""").matches(it.groupValues[1]) }) {
            Regex("""(?<![\p{L}])(\p{Lu}[\p{L}]{1,20})\s*\((\d{1,3})(?:\s*let)?[^)]{0,40}\)""").findAll(pred).forEach { m ->
                val jm = jmeno(m.groupValues[1])
                postavy[jm] = m.groupValues[2] + " let"
                zena(jm, "")?.let { zeny[jm] = it }
            }
        }
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
                "produkt", "product" -> {
                    postavy["Produkt"] = v.replace(Regex("""\s+"""), " ").trim()
                    rekvizity += "Produkt"
                    rekvizity += Regex("""(?<![\p{L}])\p{Lu}{3,}(?![\p{L}])""").findAll(v).map { it.value }
                }
                "rekvizity", "rekvizita", "props" -> postavyZ(v).forEach { p ->
                    postavy[p.jmeno] = p.popis
                    rekvizity += p.jmeno
                    rekvizity += Regex("""(?<![\p{L}])\p{Lu}{3,}(?![\p{L}])""").findAll(p.popis).map { it.value }
                }
                "postavy", "characters", "cast", "obsazení", "hrají", "účinkují", "postavy a rekvizity" -> postavyZ(v).forEach { p ->
                    postavy[p.jmeno] = p.popis
                    p.zena?.let { zeny[p.jmeno] = it }
                }
                "důležitá kontinuita", "kontinuita", "continuity" -> kontinuita += v.replace(Regex("""\s+"""), " ")
                "prostředí", "setting", "místo děje" -> kontinuita += "Prostředí: " + v.replace(Regex("""\s+"""), " ")
                "prostor", "lokace", "location" -> kontinuita += "Prostor: " + v.replace(Regex("""\s+"""), " ")
                // Styl, tón a cíl se dřív zahazovaly (kritik 30. 9. 2026) — platí pro celý film.
                "styl", "style" -> kontinuita += "Styl: " + v.replace(Regex("""\s+"""), " ")
                "tón", "tone" -> kontinuita += "Tón: " + v.replace(Regex("""\s+"""), " ")
                "cíl", "goal" -> kontinuita += "Cíl: " + v.replace(Regex("""\s+"""), " ")
            }
        }
        return SbScenarCteni(
            nazev = nazev.take(80), pomer = pomer, celkem = celkem, postavy = postavy, zeny = zeny, rekvizity = rekvizity,
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
            // Ale „Tomáš: 30 let…; Jana: 28 let…“ (šablona appky) jsou dvě postavy — dělí se jen před „Jméno:“.
            if (Regex("""^\s*[\p{L}][\p{L} ]{1,40}:""").containsMatchIn(r))
                r.split(Regex(""";\s*(?=\p{Lu}[\p{L}\d]{1,29}(?:\s\p{Lu}[\p{L}]{1,30})?\s*[:(–—-])"""))
            // „Libuše – grandma (75), Nela – granddaughter (16)“: čárka před „Jméno –“ dělí postavy.
            else r.split(Regex(""";|,\s*(?=(?:\p{L}{2,6}\.\s+)?\p{Lu}[\p{L}]{1,29}(?:\s\p{Lu}[\p{L}]{1,30}){0,2}\s*[–—]\s)"""))
        }
        polozky.flatMap { p ->
            // „MUDr. Jana Kovářová (lékařka, 45), pan Novák (důchodce, 70)“, „holčička Bára (6 let), VO muž, VO žena“:
            // krátké kusy oddělené čárkou mimo závorky jsou samostatné postavy.
            val kusy = mimoZavorky(p)
            if (kusy.size >= 2 && kusy.all { it.split(Regex("""\s+""")).size <= 6 } && kusy.count { '(' in it } >= 1) kusy else listOf(p)
        }.map { it.trim().trimEnd('.').replace(Regex("""^\d{1,2}[.)]\s+"""), "")
            // „Vojtěch „Vojta“ – 9 let“ → přezdívka do popisu.
            .replace(Regex("""^(\p{Lu}[\p{L}]+)\s+[„“"]([\p{L}]+)[“”"]\s*"""), "$1 (přezdívka „$2“) ")
        }.filter { it.isNotBlank() }.forEach { polozka ->
            // Vypravěč v seznamu postav („Voice-over – warm male narrator“, „Vypravěč (VO)“).
            Regex("""(?iu)^(vypravěč\p{L}*|voice[- ]?over|narrator|vo|hlas mimo obraz)\b(?:\s+(muž|žena|mužský|ženský|male|female))?\s*(?:\(([^)]*)\))?\s*(?:[:–—-]\s*(.*))?$""").find(polozka)?.let { v ->
                val popis = listOf(v.groupValues[2], v.groupValues[3], v.groupValues[4]).filter { it.isNotBlank() && !Regex("""(?i)^v\.?o\.?$""").matches(it.trim()) }.joinToString(", ")
                out += Postava("Vypravěč", popis, zenaZeSlov("", popis))
                return@forEach
            }
            // „Lékařka Jitka (42) – popis“, „Rozálie (10): popis“, „GRANDMA ZDENA (78) – …“.
            Regex("""^((?:\p{Lu}[\p{L}.]*\s+){0,2}\p{Lu}[\p{L}\d]*)\s*\(([^)]{1,80})\)\s*[:–—-]\s*(.{2,300})$""").find(polozka)?.let { m ->
                val jm = jmeno(m.groupValues[1])
                if (jm.lowercase() !in NE_JMENA) {
                    val popis = m.groupValues[2].trim() + ", " + m.groupValues[3].trim()
                    out += Postava(jm, popis, zena(jm, popis))
                    return@forEach
                }
            }
            // Jméno až o třech slovech s titulem („MUDr. Jana Kovářová (…)“, „pan Novák (…)“, „SOUSED KAREL (65)“).
            val zavorky = Regex("""(?<![\p{L}\d.])((?:(?:\p{Lu}\p{L}{0,5}\.|pan|paní|slečna|\p{Lu}[\p{L}]+)\s+){0,2}[\p{L}][\p{L}\d]{1,29})\s*\(([^)]{1,200})\)""").findAll(polozka)
                .filter { it.groupValues[1].lowercase() !in NE_JMENA }.toList()
            if (zavorky.isNotEmpty()) {
                zavorky.forEach { m ->
                    // Dvě slova před jménem prozradí pohlaví („jeho syn Kuba“) — jen ze stejné položky, ne z „Máma, Tonda“.
                    val pred = polozka.substring(0, m.range.first).substringAfterLast(',').substringAfterLast(';')
                        .trim().split(Regex("""\s+""")).takeLast(2).joinToString(" ")
                    val jm = jmeno(m.groupValues[1])
                    out += Postava(jm, m.groupValues[2].trim(), zena(jm, m.groupValues[2], pred))
                }
                return@forEach
            }
            val m = Regex("""^((?:\p{L}{2,6}\.\s+)?[\p{L}][\p{L}\d]{1,29}(?:\s[\p{L}]{2,30}){0,2})\s*:\s*(.{2,300})$""").find(polozka)
                ?: Regex("""^((?:\p{L}{2,6}\.\s+)?[\p{L}][\p{L}\d]{1,29}(?:\s\p{Lu}[\p{L}]{1,30}){0,2})\s*[–—,-]\s*(.{2,300})$""").find(polozka)
            if (m == null && Regex("""^(?:(?:\p{L}{2,6}\.\s+)?\p{Lu}[\p{L}]*(?:\s(?:\p{L}{2,6}\.\s+)?\p{Lu}[\p{L}]*){0,3})(?:\s*,\s*(?:\p{L}{2,6}\.\s+)?\p{Lu}[\p{L}]*(?:\s(?:\p{L}{2,6}\.\s+)?\p{Lu}[\p{L}]*){0,3})+$""").matches(polozka)) {
                polozka.split(Regex("""\s*,\s*""")).forEach { jm0 -> val jm = jmeno(jm0); out += Postava(jm, "", zena(jm, "")) }
                return@forEach
            }
            if (m == null && Regex("""^[\p{L}]{2,20}$""").matches(polozka) && polozka.lowercase() !in NE_JMENA) {
                val jm = jmeno(polozka)
                out += Postava(jm, "", zena(jm, ""))
                return@forEach
            }
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
        if (s == s.uppercase() && s.any { it.isLetter() } && ' ' in s)
            return s.lowercase().split(' ').joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }
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
            val pred = hlav.substring(0, c.range.first).let { it.substring(UVOD_HLAVICKY.find(it)?.value?.length ?: 0) }
                .trim().trimEnd('(', '[', '|', '–', '—', '-', ',', ' ').trim()
            val obsah = listOf(pred.takeUnless { jeNadpis(it) }.orEmpty(), zbytek.takeUnless { jeNadpis(it) }.orEmpty())
                .filter { it.isNotBlank() }.joinToString(" ")
            return Triple(cas(c.value), typZ(hlav.substring(0, za)), obsah)
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

    private fun rozeberOkno(cislo: Int, teloVstup: String, znamiMluvci: Set<String>, celyFilm: MutableList<String>): SbOkno {
        val telo = pokracovaniReplik(pomlckaMluvci(filmovyFormat(teloVstup), znamiMluvci))
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
            if (l.split(Regex("""\s+""")).any { it in rekvizityOkna } && znamiMluvci.none { it.equals(j, true) }) return false
            // „Na displeji“, „NÁPIS NA ZDI“, „LOKACE“, „SVĚTLO“ — obraty a štítky, ne mluvčí.
            if (l.split(Regex("""\s+""")).any { it in NE_MLUVCI || it in STITKY_NAVIC }) return false
            if (znamiMluvci.any { stejnyMluvci(it, j) }) return true
            // Křestní jméno nebo příjmení z obsazení („Jan“ → Jan Novák, „MUDr. Malá“ → MUDr. Eva Malá).
            if (znamiMluvci.any { z -> z.lowercase().split(Regex("""\s+""")).any { w -> w.trim('.') == l.split(Regex("""\s+""")).last() } }) return true
            if (naZacatku && j.first().isUpperCase() && j.split(Regex("""\s+""")).size <= 2 && jeRole(j.split(Regex("""\s+""")).last())) return true
            if (j.length >= 2 && j == j.uppercase() && j.any { it.isLetter() }) return true
            if (predUvozovkou && (j.first().isUpperCase() || naZacatku)) return true
            return false
        }

        /** Repliky se jménem v libovolném poli; vrátí zbytek textu bez nich. */
        fun vytahni(v: String): String {
            val zbyle = v.lines().mapNotNull { radek ->
                // Celý řádek je replika: `DCERA: To jsi ty?`, `Maminka (dojatě): „…“`.
                val r = RADEK_MLUVCI.find(radek)?.takeUnless {
                    Regex("""[“”"»][ \t]+\p{Lu}[\p{L}\d ]{0,30}(?:\([^)]*\))?[ \t]*:[ \t]*[„“"«]""").containsMatchIn(it.groupValues[4])
                }
                if (r != null) {
                    val zavorka = PODANI_ZA_DVOJTECKOU.find(r.groupValues[4].trim())
                    val obsah0 = (zavorka?.groupValues?.get(2) ?: r.groupValues[4]).trim()
                    // Podání až za replikou: „Pššt.“ (šeptem), „…“ – dojatě (r3_07 30. 9. 2026).
                    val zaReplikou = Regex("""^([„“"«][^„“"«»]+[“”"»][.!?…]?)\s*(?:\(([^)]{1,60})\)|[–—]\s*([^„“"«»]{1,80}))\s*$""").find(obsah0)
                    val sDejem = if (zaReplikou == null) Regex("""^([„“"«][^„“"«»”]+[“”"»][.!?…]?)\s+(\p{Lu}[^„“"«]*)$""").find(obsah0) else null
                    val obsah = zaReplikou?.groupValues?.get(1) ?: sDejem?.groupValues?.get(1) ?: obsah0
                    val podaniZa = zaReplikou?.let { it.groupValues[2].ifBlank { it.groupValues[3] } }.orEmpty().trim()
                    val vUvozovkach = celaVUvozovkach(obsah)
                    if (jeMluvci(r.groupValues[1], vUvozovkach != null, true)) {
                        val (kdo, slovo) = sRoli(r.groupValues[1], r.groupValues[2])
                        repliky += SbReplika(
                            jmeno(kdo),
                            podani(listOf(slovo, r.groupValues[3], zavorka?.groupValues?.get(1).orEmpty(), podaniZa).joinToString(", "), false),
                            vUvozovkach ?: obsah.trim(),
                        )
                        // Děj za replikou zůstane v popisu.
                        return@mapNotNull sDejem?.groupValues?.get(2)
                    }
                }
                // Uprostřed textu: `Zvuk: šustění. Syn tiše: „Tati, podívej.“`
                var zbytekRadku = radek
                MLUVCI_REPLIKA.findAll(radek).toList().forEach { m ->
                    val zacatek = m.range.first == 0 || radek.substring(0, m.range.first).trimEnd().let { it.isEmpty() || it.last() in ".!?…:" }
                    if (!jeMluvci(m.groupValues[1], true, zacatek)) return@forEach
                    val (kdo, slovo) = sRoli(m.groupValues[1], m.groupValues[2])
                    repliky += SbReplika(
                        jmeno(kdo),
                        podani(listOf(slovo, m.groupValues[3]).joinToString(", "), false),
                        m.groupValues[4].trim(),
                    )
                    zbytekRadku = zbytekRadku.replace(m.value, " ")
                }
                if (znamiMluvci.isNotEmpty()) SLOVESO_RECI.findAll(zbytekRadku).toList().forEach { m ->
                    val pred = m.value.substringBefore(m.groupValues[3])
                    val kdo = Regex("""(?<![\p{L}])\p{Lu}[\p{L}]{1,20}(?![\p{L}])""").findAll(pred).map { it.value }.toList().asReversed()
                        .firstNotNullOfOrNull { w -> znamiMluvci.firstOrNull { it.equals(w, true) || it.lowercase().split(' ').contains(w.lowercase()) } }
                        ?: return@forEach
                    repliky += SbReplika(kdo, "", m.groupValues[3].trim())
                    // Děj zůstane, řečená věta z popisu pryč.
                    zbytekRadku = zbytekRadku.replace(m.groupValues[2], m.groupValues[2].substringBefore(m.groupValues[3]).trimEnd(' ', ':', '„', '“', '"', '«').let { "$it." })
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
                // Repliky pod textovým polem („Na displeji: „Máma volá““ + „Syn: „…““) patří mluvčím, ne do nápisu.
                Pole.TEXT -> bezUvozovek(jednoradkove(if (surove.lines().count { it.isNotBlank() } > 1) vytahni(surove) else surove))
                    .takeIf { it.isNotBlank() && !jeNic(it) }?.let { texty += it }
                Pole.VYZVA -> bezUvozovek(jednoradkove(if (surove.lines().count { it.isNotBlank() } > 1) vytahni(surove) else surove)).takeUnless { jeNic(it) }?.let {
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
            // Delší věta („Detail ruky, která píše:“) je popis — zůstane bez dvojtečky (r3_24 30. 9. 2026).
            s = vety(s).mapNotNull { v ->
                val x = v.trim()
                if (!Regex("""^[\p{L} ,]{2,50}:\s*[.!]?$""").matches(x)) v
                else if (x.substringBefore(':').trim().split(Regex("""\s+""")).size > 3) x.substringBefore(':').trim() + "."
                else null
            }.joinToString(" ")
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
        val (kdoKv0, jakKv0) = kvalifikator.split(",", limit = 2).let { it[0].trim() to it.getOrElse(1) { "" }.trim() }
        // Rod vypravěče v závorce je podání, ne jméno.
        val rodVypravece = Regex("""(?iu)^(ženský|mužský|dívčí|chlapecký|female|male|žena|muž|woman|man)(\s+hlas|\s+voice)?$""").matches(kdoKv0) ||
            Regex("""(?iu)(ženský|mužský|female|male)\s+(hlas|voice)|(hlas|voice)""").containsMatchIn(kdoKv0) ||
            (voiceover && kdoKv0.isNotBlank() && kdoKv0.first().isLowerCase() &&
                kdoKv0.lowercase() !in ZENSKE && kdoKv0.lowercase() !in MUZSKE && znami.none { stejnyMluvci(it, kdoKv0) })
        val kdoKv = if (rodVypravece) "" else kdoKv0
        val jakKv = if (rodVypravece) listOf(kdoKv0.lowercase().let {
            when {
                it.startsWith("žen") || it.startsWith("dív") || it.startsWith("fem") -> "ženský hlas"
                it.startsWith("muž") || it.startsWith("chlap") || it == "male" || it.startsWith("male ") || it == "man" -> "mužský hlas"
                it.contains("žensk") || it.contains("female") || it == "woman" -> "ženský hlas, $it"
                it.contains("mužsk") || it.contains(" male") -> "mužský hlas, $it"
                else -> kdoKv0
            }
        }, jakKv0).filter { it.isNotBlank() }.joinToString(", ") else jakKv0
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
                // Vypravěč: jen první řádek je jeho; další řádky bez uvozovek jsou děj.
                if (voiceover && out.isNotEmpty()) { zbytek.append(' ').append(radek); return@forEach }
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
        fun najdi(kdo: String): String {
            val k = kdo.lowercase().trim()
            postavy.keys.firstOrNull { it.lowercase() == k }?.let { return it }
            // „Robert“ → „Kadeřník Robert“, „Alena Horáková“ → „MUDr. Alena Horáková“.
            postavy.keys.filter { p -> p.lowercase().split(Regex("""\s+""")).let { w -> k in w || p.lowercase().endsWith(" $k") } }
                .singleOrNull()?.let { return it }
            postavy.entries.filter { (_, popis) ->
                Regex("""(?iu)(říkají|říká se (?:jí|mu)|přezdívka|alias|zvan[ýá])\s+[„“"]?""" + Regex.escape(kdo.trim()) + """(?![\p{L}])""").containsMatchIn(popis)
            }.singleOrNull()?.let { return it.key }
            // „MUDr. Malá“ → „MUDr. Eva Malá“, „Učitelka“ → „Učitelka Mgr. Dana Šťastná“: poslední nebo první slovo.
            val slova = k.split(Regex("""\s+""")).map { it.trim('.') }
            postavy.keys.filter { p ->
                val w = p.lowercase().split(Regex("""\s+""")).map { it.trim('.') }
                w.last() == slova.last() || (slova.size == 1 && w.first() == slova.first())
            }.singleOrNull()?.let { return it }
            // Kmen (Dcera / dcery) jen když sedí na jedinou postavu — Martin ≠ Martina.
            return postavy.keys.filter { stejnyMluvci(it, kdo) && !(it.length != kdo.length && (it.lowercase().startsWith(k) || k.startsWith(it.lowercase()))) }
                .singleOrNull() ?: kdo
        }
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
    fun replikyText(o: SbOkno): String = o.repliky.joinToString("; ") {
        "${it.kdo}: „${it.text.replace(Regex(""""([^"\n]{1,80})""""), "‚$1‘").replace("\"", "")}“"
    }

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
        "učitelka", "majitelka", "maminka",
        // Role bez -a na konci (Otevřené dveře 30. 9. 2026: „Neteř“ dostala mužský hlas).
        "neteř", "sestřenice", "tchyně", "snacha", "švagrová", "vdova", "sousedka", "návštěvnice", "hostitelka",
        "herečka", "zpěvačka", "princezna", "královna", "čarodějnice", "víla", "kmotra", "macecha", "sestřička",
        "šéfová", "kolegyně", "studentka", "holčička", "stařenka", "babka", "vnučka", "teta", "tetička", "woman", "girl", "mother", "mom", "mum", "daughter", "grandma", "grandmother", "sister",
        "aunt", "lady", "wife", "bride", "she", "her",
        // Časté role (Špatný stůl 30. 9. 2026) a anglické protějšky.
        "číšnice", "servírka", "doktorka", "policistka", "řidička", "ředitelka", "novinářka", "barmanka", "prodavačka",
        "zdravotnice", "uklízečka", "sekretářka", "manažerka", "podnikatelka", "tanečnice", "modelka", "influencerka",
        "girlfriend", "niece", "waitress", "actress", "queen", "princess", "granddaughter", "stepmother", "widow",
        "baristka", "female", "ženský", "ženská", "ženským", "vnučka", "holčička", "sousedka", "kadeřnice", "hasička",
        "babi", "bábi", "bába", "babča", "mamka", "mamča", "mami", "ségra", "sestra", "segra", "dcerka", "tetka", "tetička",
    )
    private val MUZSKE = setOf(
        "muž", "muz", "otec", "táta", "tata", "tatínek", "děda", "deda", "dědeček", "syn", "bratr", "strýc", "kluk",
        "chlapec", "pán", "pan", "vnuk", "manžel", "přítel", "kamarád", "ženich", "jeho", "tvůrce", "fotograf",
        "kameraman", "režisér", "zákazník", "prodavač", "kuchař", "lékař", "učitel", "majitel", "soudce", "správce",
        "synovec", "bratranec", "tchán", "zeť", "švagr", "vdovec", "soused", "návštěvník", "host", "hostitel",
        "herec", "zpěvák", "princ", "král", "čaroděj", "kmotr", "otčím", "strejda", "strýček", "šéf", "kolega",
        "student", "stařec", "dědek",
        "man", "boy", "father", "dad", "son", "grandpa", "grandfather", "brother", "uncle", "husband", "groom", "he", "his",
        "číšník", "doktor", "policista", "řidič", "ředitel", "novinář", "barman", "voják", "detektiv", "kněz", "farář",
        "pilot", "taxikář", "pošťák", "zahradník", "šofér", "manažer", "podnikatel", "tanečník", "model", "influencer",
        "boyfriend", "nephew", "waiter", "actor", "king", "prince", "grandson", "stepfather", "widower",
        "barista", "male", "mužský", "mužským", "pošťák", "hasič", "kadeřník", "hlídač", "knihovník", "pekař", "řezník",
        "brácha", "brácha", "bratříček", "taťka", "taťko", "tati", "dědek", "děda", "dědeček", "strejda", "synek", "kluk",
    )
    /** Mužská jména na -a / -e (Honza, Kuba…). */
    /** Mužská jména na -e/-ě (výjimky z pravidla, že -e je žena). */
    private val MUZSKA_NA_E = setOf("rené", "rene", "mike", "joe", "uwe", "jose", "josé", "george", "dave", "steve", "pepe", "jiří", "jirzi", "tvůrce", "soudce", "vůdce", "správce", "zástupce", "průvodce", "strážce")

    /** Ženská jména, která nekončí na -a/-e (Nikol, Ester — kritik 30. 9. 2026). */
    private val ZENSKA_JMENA_BEZ_A = setOf(
        "nikol", "nicol", "nicole", "ester", "karin", "dagmar", "miriam", "ingrid", "carmen", "rut", "ruth", "noemi",
        "naomi", "isabel", "beatrix", "ines", "doris", "zoe", "lili", "lily", "eliška", "sofie", "kim", "abigail",
        "megan", "sarah", "rachel", "ellen", "helen", "caroline", "madison", "hanneke", "gwen", "margit", "edit",
        "bety", "betty", "katy", "kitty", "lucy", "emmy", "amy", "mary", "sally", "zoey", "nelly", "elly", "dolly", "lenny",
    )

    private val MUZSKA_NA_A = setOf(
        "honza", "kuba", "jirka", "standa", "franta", "vojta", "míra", "mira", "pepa", "ota", "láďa", "jarda", "ruda",
        "tonda", "venca", "zbyňa", "béďa", "ferda", "luboš", "joshua", "luca", "andrea", "nikola", "saša", "sasha",
        "ondra", "jenda", "olda", "véna", "ríša", "bohouš", "mikuláš", "lukáš", "tomáš", "matyáš", "tadeáš", "ondřej",
    )

    /**
     * Žena (true), muž (false), nepoznané (null). Slova před jménem a v popisu
     * mají přednost („jeho syn Kuba“ je chlapec).
     */
    fun zena(jmeno: String, popis: String, pred: String = ""): Boolean? {
        fun rod(t: String): Boolean? = rodSlov(t)
        rod(jmeno)?.let { return it }
        rod(pred.split(Regex("""\s+""")).lastOrNull().orEmpty())?.let { return it }
        rod(popis)?.let { return it }
        val j = jmeno.lowercase().trim().split(Regex("""\s+""")).last()
        return when {
            j in MUZSKA_NA_A -> if (j in setOf("andrea", "nikola", "saša", "sasha")) null else false
            j.endsWith("ová") -> true
            // -a (Jana, keramička) žena; -e je nejisté (tvůrce, soudce × Marie) — hlas pak z obrázku.
            Regex("""(a|ice|yně)$""").containsMatchIn(j) -> true
            // Marie, Lucie, Julie, Libuše, Alice jsou ženy; role na -e (tvůrce, soudce) řeší seznamy výš.
            j.endsWith("ie") -> true
            j in MUZSKA_NA_E -> false
            j.endsWith("e") || j.endsWith("ě") -> true
            j in ZENSKA_JMENA_BEZ_A -> true
            j in MUZSKA_NA_E -> false
            // Příjmení jako přídavné jméno: Malá, Kopecká × Veselý.
            j.endsWith("á") -> true
            j.endsWith("ý") -> false
            j.isNotEmpty() && j.last().isLetter() -> false
            else -> null
        }
    }

    /** Jen podle slov (role, zájmena) — bez odhadu podle koncovky jména. */
    private fun zenaZeSlov(jmeno: String, popis: String): Boolean? = rodSlov(jmeno) ?: rodSlov(popis)

    /** Zájmena, která říkají, ČÍ postava je, ne jakého je rodu. */
    private val PRIVLASTNOVACI = setOf("jeho", "její", "jejich", "his", "her", "their")

    /**
     * Rod podle slov. Nejdřív bez přivlastňovacích zájmen — „jeho přítelkyně“ je žena,
     * „její manžel“ muž (Špatný stůl 30. 9. 2026: dřív se to vyrušilo a hlas chyběl).
     */
    private fun rodSlov(t: String): Boolean? {
        val slova0 = t.lowercase().split(Regex("""[^\p{L}]+""")).toSet()
        // Role mimo seznamy podle přípony: včelař, knihovník × pomocnice, učitelka.
        val slova = slova0 + slova0.mapNotNull { w ->
            when {
                w in ZENSKE || w in MUZSKE -> null
                Regex("""(nice|yně|telka|istka|érka|ařka|ářka)$""").containsMatchIn(w) && w.length > 5 -> "žena"
                Regex("""(ník|ař|ář|tel|ista)$""").containsMatchIn(w) && w.length > 4 -> "muž"
                else -> null
            }
        }
        fun rod(s: Set<String>): Boolean? {
            val z = s.any { it in ZENSKE }
            val m = s.any { it in MUZSKE }
            return if (z && !m) true else if (m && !z) false else null
        }
        return rod(slova - PRIVLASTNOVACI) ?: rod(slova)
    }

    /**
     * Rod podle toho, jak postava mluví o sobě: „nechala jsem“ / „byla jsem“
     * = žena, „nevěděl jsem“ = muž (5.39). Platí pro každý český scénář.
     */
    fun rodZReplik(repliky: List<String>): Boolean? {
        var z = 0; var m = 0
        val zensky = Regex("""(?iu)(?<![\p{L}])(\p{L}{2,}la\s+jsem|jsem\s+(?:se\s+|si\s+|to\s+|ho\s+|ji\s+|vás\s+|tě\s+)?\p{L}{2,}la|byla\s+jsem|jsem\s+byla)(?![\p{L}])""")
        val muzsky = Regex("""(?iu)(?<![\p{L}])(\p{L}{2,}[^a\s]l\s+jsem|jsem\s+(?:se\s+|si\s+|to\s+|ho\s+|ji\s+|vás\s+|tě\s+)?\p{L}{2,}[^a\s]l|byl\s+jsem|jsem\s+byl)(?![\p{L}])""")
        val zenskyPridavne = Regex("""(?iu)(?<![\p{L}])(jsem\s+(?:už\s+|moc\s+|tak\s+|strašně\s+|hrozně\s+)?(?:\p{L}{2,}á|ráda|sama)|ráda\s+jsem)(?![\p{L}])""")
        val muzskyPridavne = Regex("""(?iu)(?<![\p{L}])(jsem\s+(?:už\s+|moc\s+|tak\s+|strašně\s+|hrozně\s+)?(?:\p{L}{2,}ý|rád|sám)|rád\s+jsem)(?![\p{L}])""")
        repliky.forEach { r ->
            z += zensky.findAll(r).count() + zenskyPridavne.findAll(r).count()
            m += muzsky.findAll(r).count() + muzskyPridavne.findAll(r).count()
        }
        return if (z > m) true else if (m > z) false else null
    }

    private val BARVY_M = listOf(
        "warm, natural, medium-pitched", "deep, resonant", "bright, clear tenor", "slightly husky, gravelly",
        "smooth, calm baritone", "light, slightly nasal", "rough, low",
    )
    private val BARVY_Z = listOf(
        "warm, natural, medium-pitched", "soft, gentle", "bright, clear", "low, smoky", "crisp, energetic",
        "light, airy", "slightly husky",
    )

    /**
     * Každá postava svůj hlas (5.39, uživatel: „u dvou lidí se opakuje stejný
     * hlas“). Dva mluvčí stejného rodu se stejnou barvou („warm, natural,
     * medium-pitched“ lišící se jen věkem) H3 namluví stejně — druhý dostane
     * jinou barvu z palety. Odlišné hlasy se nemění.
     */
    fun rozlisHlasy(hlasy: Map<String, String>): Map<String, String> {
        val barvaRe = Regex("""with an? (.+?) voice""")
        fun zena(h: String) = Regex("""(?i)\b(woman|girl|lady|female|her)\b""").containsMatchIn(h)
        val pouzite = mutableMapOf<Boolean, MutableSet<String>>(true to mutableSetOf(), false to mutableSetOf())
        return hlasy.mapValues { (_, h) ->
            val m = barvaRe.find(h) ?: return@mapValues h
            val barva = m.groupValues[1].lowercase().trim()
            val z = zena(h)
            val set = pouzite.getValue(z)
            if (barva !in set) { set += barva; return@mapValues h }
            val nova = (if (z) BARVY_Z else BARVY_M).firstOrNull { it !in set } ?: return@mapValues h
            set += nova
            h.replaceRange(m.groups[1]!!.range, nova)
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
        val mluvi = s.okna.flatMap { o -> o.repliky.map { it.kdo } }.filter { it !in s.rekvizity }
        // Jen kdo mluví (nebo scénář repliky nemá) — rekvizita ze seznamu postav hlas nedostane (Špatný stůl 30. 9. 2026).
        val mluvci = (s.postavy.keys.filter { k -> k !in s.rekvizity && (mluvi.isEmpty() || mluvi.any { stejnyMluvci(it, k) }) } + mluvi).distinct()
        val role = mluvci.map { it.lowercase() }.toSet()
        return mluvci.mapNotNull { jm ->
            val popis = s.postavy[jm].orEmpty()
            // Role a zájmena mají přednost, pak to, jak postava mluví o sobě, pak koncovka jména.
            val repliky = s.okna.flatMap { o -> o.repliky.filter { stejnyMluvci(it.kdo, jm) }.map { it.text } }
            val zena = zenaZeSlov(jm, popis) ?: rodZReplik(repliky) ?: s.zeny[jm] ?: zena(jm, popis) ?: return@mapNotNull null
            val l = jm.lowercase()
            val vek = vek(popis) ?: when {
                l in PRARODICE -> 75
                l in RODICE && role.any { it in DETI } -> 68
                l in DETI && role.any { it in RODICE || it in PRARODICE } -> 35
                else -> null
            }
            if (jm == "Vypravěč") {
                val podani = s.okna.flatMap { o -> o.repliky.filter { it.kdo == jm }.map { it.podani } }.joinToString(" ")
                val vyslovne = zenaZeSlov("", popis) ?: zenaZeSlov("", podani) ?: return@mapNotNull null
                return@mapNotNull jm to (if (vyslovne) "a woman narrator with a warm, clear voice" else "a man narrator with a calm, deep voice")
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
        // Víc kol: kdo je jednoznačný (starší žena), vezme si svůj hlas, a tím se
        // vyjasní ostatní (přítelkyně pak dostane mladší ženu) — Špatný stůl 30. 9. 2026.
        repeat(mluvci.size) { mluvci.filter { it !in out }.forEach { m ->
            val l = m.lowercase()
            val popis = s.postavy[m].orEmpty()
            val starsi: Boolean? = when {
                l in PRARODICE -> true
                l in RODICE && maDite -> true
                l in DETI && maRodice -> false
                Regex("""(?iu)(?<![\p{L}])(starš\p{L}*|star[ýáé]|důchod\p{L}*|senior\p{L}*|elderly|older)(?![\p{L}])""").containsMatchIn(popis) -> true
                Regex("""(?iu)(?<![\p{L}])(mlad\p{L}*|young)(?![\p{L}])""").containsMatchIn(popis) -> false
                else -> null
            }
            val rod = s.zeny[m] ?: zena(m, popis)
            val kandidati = vzhled.keys.filter { it !in pouzite }.filter { k ->
                val t = "$k ${vzhled[k]} ${napovedy[k].orEmpty()}"
                (rod == null || zenaEn(t) == null || zenaEn(t) == rod) &&
                    (starsi == null || stary(t) == starsi)
            }
            if (kandidati.size == 1 && (starsi != null || rod != null || vzhled.size == 1)) {
                out[m] = vzhled.getValue(kandidati.single())
                pouzite += kandidati.single()
            }
        } }
        val serazene = linkedMapOf<String, String>()
        mluvci.filter { it in out }.forEach { serazene[it] = out.getValue(it) }
        vzhled.filterKeys { it !in pouzite }.forEach { (k, v) -> serazene[k] = v }
        return serazene
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
        return rozlisHlasy(out)
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

    private val OKNA_ROZSAH = Regex(
        """(?iu)(?<![\p{L}])(?:(od|from|do|until|to)\s+)?(?:okn\p{L}*|panel\p{L}*|záběr\p{L}*|window\p{L}*|shot\p{L}*)\s*(\d+)(?:\s*[–—-]\s*(\d+))?(?:\s*(dál|dále|onwards?|and later))?""",
    )

    /**
     * Vzhled postavy jen pro dané panely úseku (Dar mudrců 30. 9. 2026: popis
     * „V oknech 1–4 dlouhé vlasy; od okna 5 krátké kudrliny“ a přepisovač
     * v úseku s okny 9–10 napsal dlouhé vlasy). Věty bez čísla okna platí
     * vždy, věty s oknem jen ve svých panelech. Když se v úseku vzhled mění,
     * dostane každá věta seznam záběrů `[Shot k]`. Nečitelný zápis → null
     * (zůstane celý popis s obecným pravidlem).
     */
    fun vzhledProPanely(popis: String, panely: List<Int>): String? {
        val vety = popis.split(Regex("""(?<=[.;])\s+""")).map { it.trim().trimEnd('.', ';') }.filter { it.isNotBlank() }
        val out = mutableListOf<String>()
        var menil = false
        for (v in vety) {
            val m = OKNA_ROZSAH.findAll(v).toList()
            if (m.isEmpty()) { out += v; continue }
            if (m.size > 1) return null
            val g = m[0].groupValues
            val a = g[2].toInt()
            val b = g[3].toIntOrNull()
            val rozsah = when (g[1].lowercase()) {
                "od", "from" -> a..Int.MAX_VALUE
                "do", "until", "to" -> 1..(b ?: a)
                else -> if (g[4].isNotEmpty()) a..Int.MAX_VALUE else a..(b ?: a)
            }
            menil = true
            val zbytek = v.removeRange(m[0].range).replace(Regex("""(?iu)^\s*(v|ve|in|at|on)\s+"""), "")
                .replace(Regex("""\s+(v|ve|in)\s*$"""), "").replace(Regex("""\s{2,}"""), " ").trim().trimStart(',', ':').trim()
            if (zbytek.isBlank()) return null
            val kde = panely.withIndex().filter { it.value in rozsah }.map { it.index + 1 }
            when {
                kde.isEmpty() -> {}
                kde.size == panely.size -> out += zbytek
                else -> out += kde.joinToString(", ", "in ") { "[Shot $it]" } + ": " + zbytek
            }
        }
        return if (menil) out.joinToString("; ") else null
    }

    /**
     * Postavy, které popis záběru jen zmiňuje přivlastňovacím tvarem („představí
     * si jej na Jimových hodinkách“, „Jim's watch“) a jinak v něm nejsou.
     * Dar mudrců 30. 9. 2026: přepisovač postavil Jima k pultu místo prodavače.
     */
    fun jenZminene(popis: String, jmena: Collection<String>): List<String> {
        val slova = Regex("""[\p{L}'’]+""").findAll(popis.lowercase()).map { it.value }.toList()
        return jmena.filter { j -> j.isNotBlank() && !j.contains(' ') }.filter { j ->
            val jm = j.lowercase()
            val kmen = if (jm.length > 3 && jm.last() in "ae") jm.dropLast(1) else jm
            val privl = Regex("""^(ův|ov(a|o|y|ých|ým|ými|ou|ě|u)|in(a|o|y|ých|ým|ými|ou|ě|u)?)$""")
            fun jePrivl(w: String) = w == "$jm's" || w == "$jm’s" ||
                (w.startsWith(kmen) && privl.matches(w.removePrefix(kmen)))
            val zminky = slova.filter { it.startsWith(kmen) }
            zminky.isNotEmpty() && zminky.all { jePrivl(it) }
        }
    }

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
