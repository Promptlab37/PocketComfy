package cz.promptlab.h3video.data

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Immutable
import java.io.File
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Karta **Film ze storyboardu** (experimentální).
 *
 * Jeden obrázek storyboardu (mřížka panelů) + fotky postav → jedno souvislé
 * video, klidně delší než 15 s. Délku určí appka sama:
 *
 *  1. **Přečíst** — vidoucí model (přepisovač MiniMax, uzel
 *     `MiniMaxH3ReferenceCaption`) vypíše panely jako řádky
 *     `PANEL n | čas | typ záběru | kamera | děj` ([OTAZKA_CTENI]).
 *     Délky počítá Kotlin, ne model ([naplanuj]).
 *  2. **Natočit** — každý úsek (≤ [MAX_USEK_S] s) dostane vlastní zadání
 *     z přepisovače a všechny se vykreslí v jednom běhu uzly balíku SatoDive
 *     (`ContextSegments` → `SegmentStep`… → `Decode`), navázané přes latent.
 *
 * Pravidla délek navrhl kritik 28. 9. 2026 (viz [naplanuj]).
 */

/** Odkud se bere plán: přečtený storyboard, nebo návrh z děje a postav. */
enum class SbZdroj(private val titleCs: String) {
    STORYBOARD("Mám storyboard"),
    DEJ("Vytvořit z děje"),
    /** Obrázek bez textu + scénář zvlášť (5.14, [SbScenar]). */
    SCENAR("Storyboard + scénář");

    val title: String get() = t(titleCs)
}

/**
 * Rozlišení filmu. 480p je ověřené (2×5 s = 7,6 min, VRAM 12,1 GB);
 * 720p na přání uživatele 28. 9. 2026 — má 2,25× víc bodů, běh je řádově
 * delší.
 */
/**
 * Model filmu. Turbo = sestava autora balíku (Turbo LoRA, 8 kroků), Kvalita =
 * plný model bez LoRA jako profil Kvalita v All in One (euler + beta, shift
 * 12,19/3); kroky volí uživatel, ověřeně dobré je 10. 3 + 2 = navazující
 * záběr sestavy 3 + 2 z Long MiniMax (TaoMate, 3 kroky, res_multistep, shift
 * 12/3) — dva průchody v úsecích balík neumí, jede ta část, co v Long MM
 * vzorkuje navázání. Nové hodnoty jen na konec (ukládá se jméno).
 */
enum class SbModel(private val titleCs: String) {
    TURBO("Turbo"),
    KVALITA("Kvalita"),
    TRIPLUSDVA("3 + 2");

    val title: String get() = t(titleCs)
}

enum class SbRozliseni(val kod: String, private val titleCs: String) {
    R480("480P", "480p"),
    R720("720P", "720p");

    val title: String get() = t(titleCs)
}

/** Jeden panel storyboardu, jak ho appka naplánovala. */
@Immutable
data class SbPanel(
    val cislo: Int,
    /** Jednořádkový popis děje (z modelu, uživatel ho smí upravit). */
    val popis: String,
    /** Typ záběru, jak ho model pojmenoval (wide, close-up…). */
    val typ: String = "",
    /** Pohyb kamery (static, push-in…). */
    val kamera: String = "",
    /** Délka panelu v sekundách. */
    val sekundy: Double,
    /**
     * Repliky panelu doslova, v původním jazyce: `Pepa: „Rozsviť obývák.“; AI: „…“`.
     * Do 4.91 se při čtení zahazovaly (otázka chtěla jen „co se děje, jednou
     * větou“) — tester měl storyboard s českými dialogy a film by byl bez nich.
     */
    val repliky: String = "",
    /** Podání každé repliky ze scénáře („překvapeně“), oddělené `;` ve stejném pořadí (5.14). */
    val podani: String = "",
    /** Zvuk záběru ze scénáře („šustění stránek“, 5.14). */
    val zvuk: String = "",
    /** Detail telefonu / displeje (5.14) — H3 tam jinak píše nesmyslná písmena. */
    val obrazovka: Boolean = false,
)

/** Co model ze storyboardu přečetl, ještě bez naplánovaných délek. */
data class SbCteni(
    val nazev: String?,
    val celkemVepsano: Double?,
    val zaberuVepsano: Int?,
    val panely: List<SbPrecteny>,
    /** Mřížka panelů (řádky × sloupce), když ji model uvedl. */
    val radku: Int? = null,
    val sloupcu: Int? = null,
    /** Hlas každého mluvčího (jméno → „a man in his 30s with a low, calm voice“). */
    val hlasy: Map<String, String> = emptyMap(),
    /** Vzhled postav a zvířat (jméno → „long dark hair, beige knit cardigan“). */
    val vzhled: Map<String, String> = emptyMap(),
    /** Styl podkresové hudby podle děje (anglicky, pro YuE2). */
    val hudbaStyl: String? = null,
)

data class SbPrecteny(
    val cislo: Int,
    val od: Double?,
    val doS: Double?,
    val typ: String,
    val kamera: String,
    val popis: String,
    val repliky: String = "",
)

/** Naplánované panely a jestli délky pocházejí z časů vepsaných ve storyboardu. */
data class SbPlan(
    val panely: List<SbPanel>,
    val zeStoryboardu: Boolean,
    val hlasy: Map<String, String> = emptyMap(),
    val vzhled: Map<String, String> = emptyMap(),
    val hudbaStyl: String? = null,
)

/** Úsek videa: souvislá řada panelů, kterou H3 vykreslí najednou. */
data class SbUsek(val panely: List<SbPanel>) {
    val sekundy: Double get() = panely.sumOf { it.sekundy }
}

object SbFilmPlan {

    /** Strop jednoho úseku: H3 zvládne 15 s (362 snímků). Kontext navazujícího
     *  úseku se do délky nepřičítá — 2×5 s dalo 248 snímků = 2×124 (28. 9. 2026). */
    // Navazující úsek se v latent_guide vzorkuje o kontext (22 snímků) delší:
    // 14 s = 336 + 22 = 358 → mřížka 17k+5 = 362, trénovaný strop H3.
    const val MAX_USEK_S = 14.0
    const val MIN_PANEL_S = 2.0
    const val MAX_PANEL_S = 8.0
    /** Strop celého filmu: 3–4 úseky, běh kolem půl hodiny. */
    const val MAX_CELKEM_S = 45.0
    const val MAX_PANELU = 12

    /**
     * Otázka pro vidoucí model. Formát řádků je pevný, aby ho Kotlin přečetl;
     * výslovně se vylučují pole, která nejsou záběry (vzorky barev, textur).
     */
    const val OTAZKA_CTENI =
        "This image is a film storyboard. Answer in plain lines only, no other text.\n" +
            "First line: TITLE: <the title printed on the sheet, or none> | TOTAL: <the total " +
            "duration printed on the sheet in seconds, or none> | SHOTS: <the number of shots " +
            "printed on the sheet, or none> | GRID: <rows>x<columns> of the shot panels | VOICES: <for " +
            "every speaker who has a line: Name = age, gender and voice in plain English words (pitch, " +
            "timbre), for example Anna = a woman in her 30s with a warm, low voice; separated by ;. Always " +
            "give a voice for every speaker, guessed from how they look> | LOOKS: <for every person and " +
            "animal who appears in more than one panel: Name = how they look in the panel where they first " +
            "appear (hair, clothing and its colours for people; breed and fur colour for animals), for " +
            "example Anna = long red hair, green raincoat; Dog = golden retriever with golden fur; separated " +
            "by ;. Use the printed speaker names where there are any> | MUSIC: <a short style for " +
            "instrumental background music that fits this story: genre, mood, instruments and tempo, for " +
            "example cinematic, tense, low strings and piano, 80 BPM>\n" +
            "Then one line per numbered shot panel, in the panel order: PANEL <number> | <the time " +
            "range exactly as printed on that panel, for example 00-04s, or none> | <shot size: " +
            "wide, medium, close-up, extreme close-up, insert or detail> | <camera movement, or " +
            "static> | <what happens in the panel, one short sentence> | <every spoken line " +
            "printed with that panel, copied word for word in its original language, as " +
            "Speaker: \"line\", separated by ; — or none>\n" +
            "Text labelled as action, plot, description, emotion, mood or a note (for example DĚJ, AKCE, " +
            "POPIS, EMOCE, ACTION, MOOD, NOTE) is not a spoken line: put it into the action field, never " +
            "into the lines. A panel with no printed line gets none; each line belongs only to the panel " +
            "it is printed in.\n" +
            "Skip boxes that are only colour, texture or environment swatches. Do not invent " +
            "panels, times or lines that are not on the sheet: if no time is printed, write none."

    private val CISLO = Regex("""\d+(?:[.,]\d+)?""")
    private val CAS = Regex("""(\d{1,2})(?::(\d{2}(?:[.,]\d+)?))?\s*s?\s*[-–—]\s*(\d{1,2})(?::(\d{2}(?:[.,]\d+)?))?\s*s?""")

    /** Rozebere odpověď modelu. Neznámé řádky přeskočí. */
    fun precti(text: String): SbCteni {
        var nazev: String? = null
        var celkem: Double? = null
        var zaberu: Int? = null
        var radku: Int? = null
        var sloupcu: Int? = null
        val hlasy = linkedMapOf<String, String>()
        val vzhled = linkedMapOf<String, String>()
        var hudbaStyl: String? = null
        val panely = mutableListOf<SbPrecteny>()
        // Přepisovač slučuje řádky do jednoho (`" ".join(caption.split())`) —
        // před každý PANEL/TITLE se proto zalomení vrátí.
        val radky = text.replace(Regex("""(?i)\s*(?=\b(?:PANEL\s*\d|TITLE\s*:))"""), "\n")
        radky.lines().map { it.trim().trim('*', '-', '•').trim() }.forEach { radek ->
            val velky = radek.uppercase()
            when {
                velky.startsWith("TITLE") -> {
                    radek.split("|").forEach { cast ->
                        val (k, v) = cast.split(":", limit = 2).let {
                            it[0].trim().uppercase() to it.getOrElse(1) { "" }.trim()
                        }
                        if (v.isBlank() || v.equals("none", true)) return@forEach
                        when (k) {
                            "TITLE" -> nazev = v
                            "TOTAL" -> celkem = CISLO.find(v)?.value?.replace(',', '.')?.toDoubleOrNull()
                            "SHOTS" -> zaberu = CISLO.find(v)?.value?.toIntOrNull()
                            "GRID" -> Regex("""(\d+)\s*[x×X]\s*(\d+)""").find(v)?.let {
                                radku = it.groupValues[1].toIntOrNull()
                                sloupcu = it.groupValues[2].toIntOrNull()
                            }
                            "VOICES" -> v.split(";").forEach { h ->
                                val (kdo, jak) = h.split("=", limit = 2).let {
                                    it[0].trim() to it.getOrElse(1) { "" }.trim().trimEnd('.')
                                }
                                if (kdo.isNotBlank() && jak.isNotBlank() && SbFilmPrepis.jeMluvci(kdo))
                                    hlasy[SbFilmPrepis.opravMluvciho(kdo)] = jak
                            }
                            "MUSIC" -> hudbaStyl = v.trim().trimEnd('.')
                            "LOOKS" -> v.split(";").forEach { h ->
                                val (kdo, jak) = h.split("=", limit = 2).let {
                                    it[0].trim() to it.getOrElse(1) { "" }.trim().trimEnd('.')
                                }
                                if (kdo.isNotBlank() && jak.isNotBlank() && !jak.equals("none", true))
                                    vzhled[SbFilmPrepis.opravMluvciho(kdo)] = jak
                            }
                        }
                    }
                }
                velky.startsWith("PANEL") -> {
                    val casti = radek.split("|").map { it.trim() }
                    val cislo = CISLO.find(casti[0])?.value?.toIntOrNull() ?: (panely.size + 1)
                    val cas = casti.getOrNull(1)?.let { rozsah(it) }
                    panely += SbPrecteny(
                        cislo = cislo,
                        od = cas?.first, doS = cas?.second,
                        typ = casti.getOrNull(2).orEmpty().takeUnless { it.equals("none", true) }.orEmpty(),
                        kamera = casti.getOrNull(3).orEmpty().takeUnless { it.equals("none", true) }.orEmpty(),
                        // 6. pole = repliky (od 4.92); starší odpověď ho nemá.
                        popis = (if (casti.size >= 6) casti[4] else casti.drop(4).joinToString(" | "))
                            .ifBlank { casti.lastOrNull().orEmpty() },
                        repliky = if (casti.size >= 6) casti.drop(5).joinToString(" | ")
                            .takeUnless { it.isBlank() || it.equals("none", true) }.orEmpty() else "",
                    ).let { p ->
                        // „DĚJ: Žena pochopí narážku.“ je popis, ne replika — 29. 9. 2026
                        // ho H3 přečetl nahlas jako třetí mluvčí.
                        val (repliky, dej) = SbFilmPrepis.oddelDej(p.repliky)
                        p.copy(repliky = repliky, popis = listOf(p.popis, dej).filter { it.isNotBlank() }.joinToString(" "))
                    }
                }
            }
        }
        return SbCteni(nazev, celkem, zaberu, panely, radku, sloupcu, hlasy, vzhled, hudbaStyl)
    }

    /**
     * Druhé čtení replik po řádcích mřížky. Celý storyboard se pro vidoucí
     * model zmenší a drobné písmo pod panely pak čte s chybami (tester:
     * „k vypínání“ místo „k vypínači“, „celý“ místo „celej“). Pruh s jedním
     * řádkem panelů dostane stejný počet obrazových tokenů na třetinu plochy.
     */
    fun otazkaRadku(prvni: Int, posledni: Int): String =
        "This image is one row of a film storyboard: the shot panels numbered $prvni to $posledni, " +
            "left to right. Answer in plain lines only, one line per panel, exactly in this form: " +
            "PANEL <number> | <every spoken line printed with that panel, with the speaker's name as " +
            "printed, copied letter by letter exactly as printed, in its original language, as " +
            "Speaker: \"line\", separated by ; — or none> | MOOD: <the text printed after EMOCE, " +
            "NÁLADA, EMOTION or MOOD on that panel, copied exactly, or none>\n" +
            "For example: PANEL 3 | ANNA: \"Kde je?\" | MOOD: Anna je netrpělivá.\n" +
            "Keep the exact spelling, including colloquial words and punctuation. Do not translate or " +
            "correct anything. Text labelled as action, plot, description, emotion, mood or a note (for " +
            "example DĚJ, AKCE, POPIS, EMOCE) is not a spoken line — never put it among the lines. A " +
            "panel with no printed line gets none."

    /** Odpověď [otazkaRadku] → číslo panelu → repliky. */
    fun prectiRepliky(text: String): Map<Int, String> {
        val radky = text.replace(Regex("""(?i)\s*(?=\bPANEL\s*\d)"""), "\n")
        return radky.lines().mapNotNull { r ->
            val casti = r.trim().split("|").map { it.trim() }
            if (casti.size < 2 || !casti[0].uppercase().startsWith("PANEL")) return@mapNotNull null
            val n = CISLO.find(casti[0])?.value?.toIntOrNull() ?: return@mapNotNull null
            // Prázdný panel se vrací jako "" — přesnější čtení tím říká „tady
            // replika není“; celé čtení ji 29. 9. 2026 zdvojilo ze sousedního panelu.
            // Pole MOOD (od 5.10) do replik nepatří.
            // Třetí pole je nálada — i když model návěští MOOD vynechá (ověřeno
            // 29. 9. 2026), jinak by ji postava řekla nahlas.
            val repliky = casti[1].takeUnless { NALADA_POLE.containsMatchIn(it) }.orEmpty()
            n to repliky.takeUnless { it.equals("none", true) }.orEmpty().trim()
        }.toMap()
    }

    private val NALADA_POLE = Regex("""(?i)^\s*MOOD\s*:""")

    /**
     * Odpověď [otazkaRadku] → číslo panelu → nálada (text za EMOCE / NÁLADA).
     * Celé čtení ji 29. 9. 2026 vynechalo a nahradilo vlastním shrnutím;
     * ostřejší čtení po řádcích ji opíše, jak je vytištěná.
     */
    fun prectiNalady(text: String): Map<Int, String> {
        val radky = text.replace(Regex("""(?i)\s*(?=\bPANEL\s*\d)"""), "\n")
        return radky.lines().mapNotNull { r ->
            val casti = r.trim().split("|").map { it.trim() }
            if (!casti[0].uppercase().startsWith("PANEL")) return@mapNotNull null
            val n = CISLO.find(casti[0])?.value?.toIntOrNull() ?: return@mapNotNull null
            val pole = casti.drop(1).firstOrNull { NALADA_POLE.containsMatchIn(it) }
                ?.substringAfter(":") ?: casti.getOrNull(2)
            val nalada = pole?.trim()?.trim('"', '„', '“', '”')?.trim()
                ?.takeUnless { it.isEmpty() || it.equals("none", true) } ?: return@mapNotNull null
            n to nalada
        }.toMap()
    }

    /** Doplní náladu do popisu panelu jako „Mood: …“ (jen když tam ještě není). */
    fun doplnNaladu(popis: String, nalada: String?): String {
        if (nalada.isNullOrBlank() || popis.contains("Mood:", ignoreCase = true)) return popis
        return popis.trimEnd() + " Mood: " + nalada.trimEnd('.') + "."
    }

    /** `00-04s`, `0:00–0:04`, `27–30 s` → (od, do) v sekundách. */
    fun rozsah(text: String): Pair<Double, Double>? {
        val m = CAS.find(text) ?: return null
        fun cas(a: String, b: String): Double {
            val x = a.toDouble()
            return if (b.isEmpty()) x else x * 60 + b.replace(',', '.').toDouble()
        }
        val od = cas(m.groupValues[1], m.groupValues[2])
        val doS = cas(m.groupValues[3], m.groupValues[4])
        return if (doS > od) od to doS else null
    }

    /**
     * Délky panelů.
     *
     * (a) Vepsané časy platí, jen když dávají smysl: začátky rostou, panel má
     * 1–20 s, díra/překryv mezi panely ≤ 0,5 s, součet sedí s vepsaným
     * „TOTAL“ (±1 s) a počet s „SHOTS“. Jinak se zahodí — model snadno splete
     * 0 a 8 a špatný plán je horší než odhad.
     * (b) Bez časů odhad podle typu záběru ([odhad]), meze 2–8 s na panel.
     * Celek nad [MAX_CELKEM_S] se poměrně zkrátí.
     */
    fun naplanuj(cteni: SbCteni): SbPlan {
        val zdroj = cteni.panely.sortedBy { it.cislo }.take(MAX_PANELU)
        if (zdroj.isEmpty()) return SbPlan(emptyList(), false)
        val vepsane = vepsaneDelky(cteni, zdroj)
        // Mluvený panel trvá tak dlouho, jak dlouho se replika říká — do 5.04
        // se délka brala z typu záběru (3–3,5 s) a krátká věta nechala 2 s
        // ticha („videa jsou utahaná“, 29. 9. 2026). Vepsaný čas platí, jen
        // se nezkrátí pod délku řeči (useknutá replika).
        val reci = zdroj.map { delkaReci(it.repliky)?.coerceAtMost(MAX_PANEL_S) }
        var delky = vepsane?.mapIndexed { i, d -> maxOf(d, reci[i] ?: 0.0) }
            ?: zdroj.mapIndexed { i, p -> reci[i] ?: odhadTicha(p.typ, p.kamera) }
        if (delky.sum() > MAX_CELKEM_S) {
            // Nejdřív ubrat ticho, řeč až nakonec; když to nestačí, poměrně.
            delky = rozlozCas(delky, reci, MAX_CELKEM_S)
            if (delky.sum() > MAX_CELKEM_S + 1e-9) {
                val k = MAX_CELKEM_S / delky.sum()
                delky = delky.map { kotlin.math.floor(it * k * 10) / 10 }
            }
        }
        // Číslo panelu zůstává takové, jaké je ve storyboardu: přepisovač vidí
        // celý obrázek a podle čísla panel dohledává. Jen když model čísla
        // zdvojí, spadne se na pořadí.
        val cislaSedi = zdroj.map { it.cislo }.distinct().size == zdroj.size
        val panely = zdroj.mapIndexed { i, p ->
            SbPanel(
                cislo = if (cislaSedi) p.cislo else i + 1,
                popis = p.popis,
                typ = p.typ,
                kamera = p.kamera,
                sekundy = zaokrouhli(delky[i]),
                repliky = p.repliky,
            )
        }
        return SbPlan(panely, vepsane != null, cteni.hlasy, cteni.vzhled, cteni.hudbaStyl)
    }

    private fun vepsaneDelky(cteni: SbCteni, panely: List<SbPrecteny>): List<Double>? {
        if (panely.any { it.od == null || it.doS == null }) return null
        val delky = mutableListOf<Double>()
        for (i in panely.indices) {
            val p = panely[i]
            val d = p.doS!! - p.od!!
            if (d < 1.0 || d > 20.0) return null
            if (i > 0) {
                val dira = p.od - panely[i - 1].doS!!
                if (dira < -0.5 || dira > 0.5) return null
                // Malou díru připočíst k předchozímu panelu.
                if (dira > 0) delky[i - 1] = delky[i - 1] + dira
            }
            delky += d
        }
        val soucet = delky.sum()
        cteni.celkemVepsano?.let { if (kotlin.math.abs(it - soucet) > 1.0) return null }
        cteni.zaberuVepsano?.let { if (it != panely.size) return null }
        return delky
    }

    /** Odhad délky panelu podle typu záběru a pohybu kamery. */
    fun odhad(typ: String, kamera: String): Double {
        val t = typ.lowercase()
        val zaklad = when {
            "extreme" in t || "insert" in t || "detail" in t -> 2.5
            "close" in t -> 3.0
            "medium" in t -> 3.5
            "wide" in t || "establish" in t || "long" in t -> 4.0
            else -> 3.5
        }
        val k = kamera.lowercase()
        val pohyb = k.isNotBlank() && "static" !in k && "none" !in k
        return (if (pohyb) maxOf(zaklad, 4.5) else zaklad).coerceIn(MIN_PANEL_S, MAX_PANEL_S)
    }

    /**
     * Tempo řeči pro odhad délky mluveného panelu. Neověřený odhad (kritik
     * 29. 9. 2026) — jediné místo, kde se dá doladit.
     */
    const val SLABIK_ZA_S = 5.5
    const val NASTUP_S = 0.3
    const val DOZVUK_S = 0.5
    /** Rezerva za poslední replikou úseku — tam už nic nenavazuje (useknutá slabika). */
    const val REZERVA_KONCE_S = 0.5

    /** Slabiky: skupiny samohlásek (ou/au/eu jedna) + slabikotvorné r/l (vlk, krk). */
    fun slabiky(text: String): Int {
        val t = text.lowercase()
        val sam = Regex("ou|au|eu|[aeiouyáéíóúůýě]").findAll(t).count()
        val sou = "bcčdďfghjkmnňpqřsštťvwxzž"
        // Jen mezi dvěma souhláskami — „spadl“ je jedna slabika.
        val rl = Regex("(?<=[$sou])[rl](?=[$sou])").findAll(t).count()
        return maxOf(1, sam + rl)
    }

    /** Jak dlouho zabere říct repliky panelu (null = panel je tichý). */
    fun delkaReci(repliky: String): Double? {
        val r = SbFilmPrepis.repliky(repliky)
        if (r.isEmpty()) return null
        val s = r.sumOf { (_, co) -> NASTUP_S + slabiky(co) / SLABIK_ZA_S + 0.2 * co.count { it == ',' } + DOZVUK_S } +
            0.3 * (r.size - 1)
        return maxOf(MIN_PANEL_S, s)
    }

    /** Tichý panel: krátká reakce, delší jen celek nebo pohyb kamery. */
    fun odhadTicha(typ: String, kamera: String): Double {
        val k = kamera.lowercase()
        val pohyb = k.isNotBlank() && "static" !in k && "none" !in k
        val t = typ.lowercase()
        return when {
            pohyb -> 3.5
            "wide" in t || "establish" in t || "long" in t -> 2.5
            else -> MIN_PANEL_S
        }
    }

    /**
     * Rozloží čas na [cil]: přidává nejdřív tichým panelům (do 6 s), pak
     * mluveným nad řeč (do +0,8 s), a až potom všem. Ubírá nejdřív tichým
     * (do minima) a pak mluveným jen po odhad řeči — replika se nezkrátí.
     */
    fun rozlozCas(delky: List<Double>, reci: List<Double?>, cil: Double, strop: Double = MAX_USEK_S): List<Double> {
        val d = delky.map { zaokrouhli(it) }.toMutableList()
        var rozdil = zaokrouhli(cil - d.sum())
        val krok = 0.1
        fun kandidat(pridat: Boolean): Int? {
            val idx = d.indices
            return if (pridat) {
                idx.filter { reci[it] == null && d[it] + krok <= 6.0 + 1e-9 }.minByOrNull { d[it] }
                    ?: idx.filter { reci[it] != null && d[it] + krok <= reci[it]!! + 0.8 + 1e-9 }.minByOrNull { d[it] - reci[it]!! }
                    ?: idx.filter { d[it] + krok <= strop + 1e-9 }.minByOrNull { d[it] }
            } else {
                idx.filter { reci[it] == null && d[it] - krok >= MIN_PANEL_S - 1e-9 }.maxByOrNull { d[it] }
                    ?: idx.filter { reci[it] != null && d[it] - krok >= reci[it]!! - 1e-9 }.maxByOrNull { d[it] - reci[it]!! }
            }
        }
        var pojistka = 5000
        while (kotlin.math.abs(rozdil) >= 0.05 && pojistka-- > 0) {
            val pridat = rozdil > 0
            val i = kandidat(pridat) ?: break
            d[i] = zaokrouhli(d[i] + if (pridat) krok else -krok)
            rozdil = zaokrouhli(rozdil - if (pridat) krok else -krok)
        }
        return d
    }

    /** Na desetiny sekundy — jemněji model stejně nestřihá. */
    private fun zaokrouhli(s: Double): Double = (s * 10).roundToInt() / 10.0

    /**
     * Rozdělí panely na úseky ≤ [MAX_USEK_S] vždy na hranici panelů, s co
     * nejmenším nejdelším úsekem (vyvažuje se čas, ne počet panelů). Panel
     * delší než strop se nejdřív rozpůlí.
     */
    fun rozdel(panely: List<SbPanel>): List<SbUsek> = rozdelBezRezervy(panely).map { u ->
        // Konec úseku je skutečný konec videa: poslední replika potřebuje
        // rezervu, jinak se useká poslední slabika. Strop H3 je 15 s.
        val posledni = u.panely.lastOrNull()
        if (posledni == null || delkaReci(posledni.repliky) == null || u.sekundy + REZERVA_KONCE_S > 15.0) u
        else SbUsek(u.panely.dropLast(1) + posledni.copy(sekundy = zaokrouhli(posledni.sekundy + REZERVA_KONCE_S)))
    }

    private fun rozdelBezRezervy(panely: List<SbPanel>): List<SbUsek> {
        if (panely.isEmpty()) return emptyList()
        val kusy = panely.flatMap { p ->
            if (p.sekundy <= MAX_USEK_S) listOf(p)
            else {
                val n = ceil(p.sekundy / MAX_USEK_S).toInt()
                List(n) { p.copy(sekundy = zaokrouhli(p.sekundy / n)) }
            }
        }
        val celkem = kusy.sumOf { it.sekundy }
        var pocet = maxOf(1, ceil(celkem / MAX_USEK_S - 1e-9).toInt())
        while (pocet <= kusy.size) {
            val rez = rozdelNa(kusy, pocet)
            if (rez != null && rez.all { it.sekundy <= MAX_USEK_S + 1e-9 }) return rez
            pocet++
        }
        return kusy.map { SbUsek(listOf(it)) }
    }

    /** Lineární rozdělení na `k` souvislých skupin s minimálním maximem (DP). */
    private fun rozdelNa(kusy: List<SbPanel>, k: Int): List<SbUsek>? {
        val n = kusy.size
        if (k > n) return null
        val pref = DoubleArray(n + 1).also { for (i in 0 until n) it[i + 1] = it[i] + kusy[i].sekundy }
        val inf = Double.MAX_VALUE
        val dp = Array(k + 1) { DoubleArray(n + 1) { inf } }
        val odkud = Array(k + 1) { IntArray(n + 1) }
        dp[0][0] = 0.0
        for (j in 1..k) for (i in j..n) for (m in (j - 1) until i) {
            if (dp[j - 1][m] == inf) continue
            val v = maxOf(dp[j - 1][m], pref[i] - pref[m])
            if (v < dp[j][i]) { dp[j][i] = v; odkud[j][i] = m }
        }
        if (dp[k][n] == inf) return null
        val hranice = mutableListOf<Int>()
        var i = n
        for (j in k downTo 1) { hranice += i; i = odkud[j][i] }
        hranice += 0
        hranice.reverse()
        return (0 until k).map { SbUsek(kusy.subList(hranice[it], hranice[it + 1])) }
    }

    /**
     * Plánovací text pro `ContextSegments`: úseky oddělené `---`, každý další
     * začíná `[Shot 1] At MM:SS.mmm` = svým začátkem v celém filmu. Podle
     * téhle značky uzel počítá délky úseků (`_segment_durations_from_prompt`);
     * skutečné zadání úseku jde až do `SegmentStep.prompt_override`, kde se
     * délky už nepřepočítávají — proto v něm smí být běžné časy záběrů.
     */
    fun planovaciText(useky: List<SbUsek>, znacky: String): String {
        var zacatek = 0.0
        return useky.mapIndexed { i, u ->
            val text = if (i == 0) "$znacky Part 1."
            else "[Shot 1] At ${casH3(zacatek)}, $znacky Part ${i + 1}."
            zacatek += u.sekundy
            text
        }.joinToString("\n---\n")
    }

    /** Kolik panelů navrhnout na danou délku: ~3,5 s na panel, 3–12. */
    fun panelyNaDelku(sekundy: Int): Int = (sekundy / 3.5).roundToInt().coerceIn(3, MAX_PANELU)

    /** Systémový prompt pro návrh záběrů z děje (výstup v tvaru [OTAZKA_CTENI]). */
    const val SYSTEM_NAVRH =
        "You are a film director writing a shot list for a short AI video. Answer in plain lines " +
            "only, no other text. First line: TITLE: <a short title> | TOTAL: <total seconds> | " +
            "SHOTS: <number of shots> | VOICES: <for every speaker who has a line: Name = age, gender " +
            "and voice in plain English words (pitch, timbre), separated by ;, or none> | MUSIC: <a short style " +
            "for instrumental background music that fits the story: genre, mood, instruments and tempo>. Then one line " +
            "per shot, in story order: PANEL <number> | " +
            "<start-end seconds, for example 00-04s> | <shot size: wide, medium, close-up, extreme " +
            "close-up, insert or detail> | <camera movement, or static> | <what happens in the shot, " +
            "one short sentence of visible action> | <the spoken lines of that shot as Speaker: " +
            "\"line\", separated by ;, in their original language, or none>. The shots follow each " +
            "other without gaps and add up to the total. Each shot shows one clear moment and never " +
            "repeats an earlier action. Spoken lines given in the story are copied word for word."

    /** Zadání návrhu: děj, délka, počet záběrů, postavy z fotek. */
    fun zadaniNavrhu(dej: String, sekundy: Int, pocetPostav: Int): String {
        val panelu = panelyNaDelku(sekundy)
        val postavy = if (pocetPostav > 0)
            " The characters are the people in the attached photos (photo 1 = character 1, and so on); " +
                "refer to them by what they look like."
        else ""
        return "Story: ${dej.trim()}\nTotal length: $sekundy seconds. Number of shots: $panelu.$postavy"
    }

    /**
     * Návrh z děje: délky se dorovnají přesně na cílovou délku (model počítá
     * sekundy nespolehlivě). Poměr mezi panely zůstane, meze 2–8 s.
     */
    fun naplanujNavrh(text: String, cilSekund: Int): SbPlan {
        val cteni = precti(text)
        val plan = naplanuj(cteni.copy(celkemVepsano = null, zaberuVepsano = null))
        if (plan.panely.isEmpty()) return plan
        // Čas navíc (uživatel zvolil 15/30/45 s) jde nejdřív do tichých
        // záběrů, ne do pomlk za replikou.
        val reci = plan.panely.map { delkaReci(it.repliky) }
        val delky = rozlozCas(plan.panely.map { it.sekundy }, reci, cilSekund.toDouble())
        val panely = plan.panely
        return SbPlan(panely.mapIndexed { i, p -> p.copy(sekundy = delky[i]) }, false, plan.hlasy, plan.vzhled, plan.hudbaStyl)
    }

    /** `MM:SS.mmm` jako v příručce H3. */
    fun casH3(s: Double): String {
        val ms = (s * 1000).roundToInt()
        return "%02d:%02d.%03d".format(ms / 60000, (ms / 1000) % 60, ms % 1000)
    }
}

/** Stav karty. */
@Immutable
data class SbFilmScene(
    val storyboard: File? = null,
    val storyboardNahled: Bitmap? = null,
    /** Fotky postav (0–3). */
    val postavy: List<LongMmRef> = emptyList(),
    /** Volitelná věta o ději. */
    val dej: String = "",
    /** Plátno filmu. */
    val pomer: LongMmPomer = LongMmPomer.NASIRKU,
    val rozliseni: SbRozliseni = SbRozliseni.R480,
    /** Odkud je plán. */
    val zdroj: SbZdroj = SbZdroj.STORYBOARD,
    /** Vytvořit z děje: cílová délka filmu v sekundách. */
    val cilSekund: Int = 30,
    /** Naplánované panely (po „Přečíst“). */
    val panely: List<SbPanel> = emptyList(),
    val nazev: String = "",
    /** Byly délky vepsané ve storyboardu (true), nebo odhadnuté (false)? */
    val casyZeStoryboardu: Boolean = false,
    /** Zadání úseků z přepisovače (po „Natočit“, před během). */
    val zadaniUseku: List<String> = emptyList(),
    /** Hlas každého mluvčího — stejný popis jde do všech úseků filmu. */
    val hlasy: Map<String, String> = emptyMap(),
    /** Vzhled postav a zvířat — stejný popis jde do všech úseků filmu. */
    val vzhled: Map<String, String> = emptyMap(),
    /** Styl hudby — z čtení storyboardu, jde upravit. */
    val hudbaStyl: String = "",
    /** Hlasitost hudby pod dialogy v dB. */
    val hudbaHlasitost: Int = cz.promptlab.h3video.comfy.SbHudbaBuilder.HLASITOST_VYCHOZI,
    val model: SbModel = SbModel.TURBO,
    /** Kroky plného modelu (Kvalita). Turbo má pevných [TURBO_KROKY]. */
    val krokyKvalita: Int = KVALITA_KROKY,
    /** Storyboard + scénář: text scénáře, jak ho uživatel vložil. */
    val scenar: String = "",
    /** Storyboard + scénář: kontinuita celého filmu ze scénáře. */
    val kontinuita: String = "",
    /** Storyboard + scénář: texty na videu, výzva a poznámky — do H3 nejdou. */
    val strih: List<SbTextStrihu> = emptyList(),
    /** Storyboard + scénář: okna rozdělil jazykový model (scénář je neoznačoval). */
    val scenarOdhadem: Boolean = false,
    /** Storyboard + scénář: kolik panelů přečetl obrázek (0 = nevíme). */
    val panelyObrazku: Int = 0,
    /** Storyboard + scénář: kolik oken měl scénář při čtení. */
    val oknaScenare: Int = 0,
    /** Storyboard + scénář: otisk scénáře, ze kterého je plán (změna → přečíst znovu). */
    val scenarPlanu: Int = 0,
    /** Co kontrola čtení našla (5.17) — příprava se před prompty zastavila. */
    val nalezy: List<SbNalez> = emptyList(),
    /** Mřížka storyboardu z čtení (výřezy panelů v kontrole). */
    val radku: Int = 0,
    val sloupcu: Int = 0,
    /**
     * Kdo je na které fotce (cesta fotky → jméno postavy z plánu). Ve volbách se
     * storyboardem musí mít každá fotka jméno — jinak přepisovač hádá (5.17, kritici).
     */
    val jmenaFotek: Map<String, String> = emptyMap(),
) {
    /** Kroky, se kterými se opravdu vzorkuje. */
    val kroky: Int
        get() = when (model) {
            SbModel.TURBO -> TURBO_KROKY
            SbModel.TRIPLUSDVA -> TRIPLUSDVA_KROKY
            SbModel.KVALITA -> krokyKvalita.coerceIn(KVALITA_MIN_KROKU, KVALITA_MAX_KROKU)
        }

    val useky: List<SbUsek> get() = SbFilmPlan.rozdel(panely)
    val sekundy: Double get() = panely.sumOf { it.sekundy }

    /** Jde storyboard do H3 jako `<Picture 1>`? Jen když je z něj plán. */
    val seStoryboardem: Boolean get() = zdroj != SbZdroj.DEJ && storyboard != null

    /** Jméno postavy na fotce (null = nepřiřazená). */
    fun jmenoFotky(f: File): String? = jmenaFotek[f.absolutePath]?.takeIf { it.isNotBlank() }

    /** Nahrávají se v tomhle pořadí: storyboard = `<Picture 1>`, postavy dál. */
    val uploadImages: List<File>
        get() = listOfNotNull(storyboard.takeIf { seStoryboardem }) + postavy.map { it.soubor }

    companion object {
        const val MAX_POSTAV = 3
        val DELKY = listOf(15, 30, 45)

        /** Turbo: sestava autora balíku, kroky se nemění (5.06 je nabízel — chyba). */
        const val TURBO_KROKY = 8
        /** 3 + 2: TaoMate je tříkroková destilace, kroky jsou součást receptu. */
        const val TRIPLUSDVA_KROKY = 3
        /** Plný model: 10 kroků uživatel ověřil (lepší než Turbo), strop jen prodlužuje čas. */
        const val KVALITA_KROKY = 10
        const val KVALITA_MIN_KROKU = 10
        const val KVALITA_MAX_KROKU = 30
    }
}

/**
 * Podkresová hudba k hotovému filmu ze storyboardu (5.13): co se zmrazí při
 * zařazení do fronty. Film se čte z výstupů serveru, původní zůstává.
 */
data class SbHudbaZadani(
    val zdrojId: String,
    val soubor: String,
    val slozka: String,
    val styl: String,
    val hlasitost: Int,
    val sekundy: Double,
    val seed: Long,
    val prompt: String = "",
)

/**
 * Lze k položce galerie přidat hudbu? Jen film ze storyboardu, který leží
 * na serveru a sám hudbu ještě nemá (jinak by hudba přibyla dvakrát).
 */
fun jdePridatHudbu(item: VideoItem, serverUmi: Boolean): Boolean =
    serverUmi && item.mode == Mode.SBFILM.name && item.filmNaServeru.isNotBlank() && !item.sHudbou

/** Délka filmu pro hudbu: změřená, jinak plánovaná. */
fun delkaProHudbu(item: VideoItem): Double =
    (if (item.filmSekundy > 0f) item.filmSekundy else item.seconds).toDouble()

/** Jde spustit příprava (plán + prompty)? Podklady dané volby jsou vyplněné. */
fun sbLzePripravit(s: SbFilmScene): Boolean = when (s.zdroj) {
    SbZdroj.STORYBOARD -> s.storyboard != null
    SbZdroj.SCENAR -> s.storyboard != null && s.scenar.isNotBlank()
    SbZdroj.DEJ -> s.dej.isNotBlank() && s.postavy.isNotEmpty()
}

/** Je film připravený (prompty ke všem úsekům, u scénáře z jeho aktuální podoby)? */
fun sbPripraveno(s: SbFilmScene): Boolean =
    s.zadaniUseku.isNotEmpty() && s.zadaniUseku.size == s.useky.size &&
        (s.zdroj != SbZdroj.SCENAR || otiskScenare(s.scenar) == s.scenarPlanu)

/**
 * Hlavní tlačítko dole má místo „Natočit film“ nabídnout „Připravit film“
 * (5.21, uživatel: „aby to připravit film bylo vidět lépe“): podklady jsou,
 * film připravený není a kontrola nic nenašla (nálezy se řeší v Kontrole).
 */
fun sbTlacitkoPripravit(s: SbFilmScene): Boolean = sbHlavniKrok(s) != null

/** Co nabídne hlavní tlačítko dole místo „Natočit film“ (oranžově). */
enum class SbKrok { PRIPRAVIT, NAPSAT, POKRACOVAT }

/**
 * Další krok na hlavním tlačítku (5.25). Po úpravě záběrů jen dopsat prompty —
 * „Připravit film“ by plán přečetl znovu a úpravy přepsal (kritik 30. 9. 2026).
 * Nálezy kontroly: „Pokračovat – napsat prompty“. Nepřiřazená fotka: nic, stavový
 * řádek řekne „Urči, kdo je na fotce.“
 */
fun sbHlavniKrok(s: SbFilmScene): SbKrok? = when {
    !sbLzePripravit(s) || sbPripraveno(s) -> null
    s.panely.isEmpty() || (s.zdroj == SbZdroj.SCENAR && otiskScenare(s.scenar) != s.scenarPlanu) -> SbKrok.PRIPRAVIT
    s.zdroj != SbZdroj.DEJ && s.postavy.any { s.jmenoFotky(it.soubor) == null } -> null
    s.nalezy.isNotEmpty() -> SbKrok.POKRACOVAT
    else -> SbKrok.NAPSAT
}

/** Otisk scénáře — bílé znaky na krajích nerozhodují. */
fun otiskScenare(t: String): Int = t.trim().hashCode()

/** Co kartě chybí, než se dá natočit. */
fun sbFilmProblem(s: SbFilmScene): String? = when (s.zdroj) {
    SbZdroj.STORYBOARD -> when {
        s.storyboard == null -> t("Vyber obrázek se storyboardem.")
        s.panely.isEmpty() -> t("Nejdřív připrav film.")
        else -> fotkyProblem(s) ?: scenarProblem(s)
    }
    // Bez obrázku storyboardu musí mít H3 aspoň jednu referenci — Ref2VA
    // bez předloh přepisovač odmítne.
    SbZdroj.DEJ -> when {
        s.postavy.isEmpty() -> t("Přidej aspoň jednu fotku postavy.")
        s.panely.isEmpty() -> t("Nejdřív připrav film.")
        else -> scenarProblem(s)
    }
    SbZdroj.SCENAR -> when {
        s.storyboard == null -> t("Vyber obrázek se storyboardem.")
        s.scenar.isBlank() -> t("Vlož scénář.")
        s.panely.isEmpty() -> t("Nejdřív připrav film.")
        otiskScenare(s.scenar) != s.scenarPlanu -> t("Scénář se změnil. Připrav film znovu.")
        else -> fotkyProblem(s) ?: scenarProblem(s)
    }
}

/** Ve volbách se storyboardem musí mít každá fotka postavy jméno (5.17). */
private fun fotkyProblem(s: SbFilmScene): String? =
    if (s.postavy.any { s.jmenoFotky(it.soubor) == null }) t("Urči, kdo je na fotce.") else null

/**
 * Jména postav, ke kterým jde přiřadit fotku: mluvčí z plánu a postavy se
 * vzhledem; u scénáře i ze samotného textu scénáře (jména jsou hned po vložení).
 */
fun jmenaPostav(s: SbFilmScene): List<String> {
    val zPlanu = s.panely.flatMap { SbFilmPrepis.repliky(it.repliky).map { r -> r.first } } + s.vzhled.keys
    val zeScenare = if (s.zdroj == SbZdroj.SCENAR) SbScenar.rozeber(s.scenar)?.let { c ->
        c.postavy.keys + c.okna.flatMap { o -> o.repliky.map { it.kdo } }
    }.orEmpty() else emptyList()
    return (zeScenare + zPlanu).map { it.trim() }.filter { it.isNotBlank() && it != "Vypravěč" }
        .distinctBy { it.lowercase() }
}

/**
 * Natočit jde až s hotovým scénářem (krok 2) — žádné skryté psaní při natáčení.
 * Se vloženým scénářem (5.14) se prompty jmenují prompty — slovo scénář patří jemu.
 */
private fun scenarProblem(s: SbFilmScene): String? = when {
    s.zadaniUseku.size != s.useky.size -> if (s.nalezy.isNotEmpty()) t("Zkontroluj záběry.") else t("Nejdřív napiš prompty.")
    s.zadaniUseku.any { it.isBlank() } -> t("Doplň prompty.")
    else -> null
}

class SbFilmStore(private val ctx: Context) {
    private val sp = ctx.getSharedPreferences("h3video", Context.MODE_PRIVATE)
    private fun dir(): File = File(ctx.filesDir, "sbfilm").apply { mkdirs() }
    fun soubor(jmeno: String) = File(dir(), jmeno)

    fun save(s: SbFilmScene) {
        val j = org.json.JSONObject()
            .put("storyboard", s.storyboard?.absolutePath ?: "")
            .put("postavy", org.json.JSONArray().also { a -> s.postavy.forEach { a.put(it.soubor.absolutePath) } })
            .put("dej", s.dej)
            .put("pomer", s.pomer.name)
            .put("rozliseni", s.rozliseni.name)
            .put("zdroj", s.zdroj.name)
            .put("cilSekund", s.cilSekund)
            .put("model", s.model.name)
            .put("krokyKvalita", s.krokyKvalita)
            .put("zadaniUseku", org.json.JSONArray().also { a -> s.zadaniUseku.forEach { a.put(it) } })
            .put("nazev", s.nazev)
            .put("casyZeStoryboardu", s.casyZeStoryboardu)
            .put("hlasy", org.json.JSONObject().also { j -> s.hlasy.forEach { (k, v) -> j.put(k, v) } })
            .put("vzhled", org.json.JSONObject().also { j -> s.vzhled.forEach { (k, v) -> j.put(k, v) } })
            .put("hudbaStyl", s.hudbaStyl)
            .put("hudbaHlasitost", s.hudbaHlasitost)
            .put("scenar", s.scenar)
            .put("kontinuita", s.kontinuita)
            .put("scenarOdhadem", s.scenarOdhadem)
            .put("panelyObrazku", s.panelyObrazku)
            .put("oknaScenare", s.oknaScenare)
            .put("scenarPlanu", s.scenarPlanu)
            .put("radku", s.radku)
            .put("sloupcu", s.sloupcu)
            .put("nalezy", org.json.JSONArray().also { a -> s.nalezy.forEach { a.put(org.json.JSONObject().put("cislo", it.cislo).put("text", it.text)) } })
            .put("jmenaFotek", org.json.JSONObject().also { j -> s.jmenaFotek.forEach { (k, v) -> j.put(k, v) } })
            .put("strih", org.json.JSONArray().also { a ->
                s.strih.forEach { a.put(org.json.JSONObject().put("cislo", it.cislo).put("text", it.text).put("druh", it.druh.name)) }
            })
            .put("panely", org.json.JSONArray().also { a ->
                s.panely.forEach {
                    a.put(org.json.JSONObject().put("cislo", it.cislo).put("popis", it.popis)
                        .put("typ", it.typ).put("kamera", it.kamera).put("sekundy", it.sekundy)
                        .put("repliky", it.repliky).put("podani", it.podani).put("zvuk", it.zvuk)
                        .put("obrazovka", it.obrazovka))
                }
            })
        sp.edit().putString(KEY, j.toString()).apply()
    }

    fun load(): SbFilmScene = runCatching {
        val j = org.json.JSONObject(sp.getString(KEY, "{}")!!)
        fun soubor(cesta: String) = cesta.takeIf { it.isNotBlank() }?.let { File(it) }?.takeIf { it.exists() }
        val sb = soubor(j.optString("storyboard"))
        val postavy = (0 until (j.optJSONArray("postavy")?.length() ?: 0)).mapNotNull {
            soubor(j.getJSONArray("postavy").getString(it))?.let { f -> LongMmRef(f) }
        }
        val panely = (0 until (j.optJSONArray("panely")?.length() ?: 0)).map {
            val p = j.getJSONArray("panely").getJSONObject(it)
            SbPanel(p.optInt("cislo", it + 1), p.optString("popis"), p.optString("typ"),
                p.optString("kamera"), p.optDouble("sekundy", 3.0), p.optString("repliky"),
                podani = p.optString("podani"), zvuk = p.optString("zvuk"), obrazovka = p.optBoolean("obrazovka"))
        }
        SbFilmScene(
            storyboard = sb, postavy = postavy, dej = j.optString("dej"),
            pomer = runCatching { LongMmPomer.valueOf(j.optString("pomer")) }.getOrDefault(LongMmPomer.NASIRKU),
            rozliseni = runCatching { SbRozliseni.valueOf(j.optString("rozliseni")) }.getOrDefault(SbRozliseni.R480),
            zdroj = runCatching { SbZdroj.valueOf(j.optString("zdroj")) }.getOrDefault(SbZdroj.STORYBOARD),
            cilSekund = j.optInt("cilSekund", 30),
            // „kroky“ z 5.06 se nečtou — patřily k Turbo, kde se měnit nemají.
            model = runCatching { SbModel.valueOf(j.optString("model")) }.getOrDefault(SbModel.TURBO),
            krokyKvalita = j.optInt("krokyKvalita", SbFilmScene.KVALITA_KROKY)
                .coerceIn(SbFilmScene.KVALITA_MIN_KROKU, SbFilmScene.KVALITA_MAX_KROKU),
            zadaniUseku = (0 until (j.optJSONArray("zadaniUseku")?.length() ?: 0))
                .map { j.getJSONArray("zadaniUseku").getString(it) },
            nazev = j.optString("nazev"), casyZeStoryboardu = j.optBoolean("casyZeStoryboardu"),
            hlasy = j.optJSONObject("hlasy")?.let { h -> h.keys().asSequence().associateWith { h.optString(it) } }
                .orEmpty().filterValues { it.isNotBlank() },
            vzhled = j.optJSONObject("vzhled")?.let { h -> h.keys().asSequence().associateWith { h.optString(it) } }
                .orEmpty().filterValues { it.isNotBlank() },
            hudbaStyl = j.optString("hudbaStyl"),
            hudbaHlasitost = j.optInt("hudbaHlasitost", cz.promptlab.h3video.comfy.SbHudbaBuilder.HLASITOST_VYCHOZI)
                .coerceIn(cz.promptlab.h3video.comfy.SbHudbaBuilder.HLASITOST_MIN, cz.promptlab.h3video.comfy.SbHudbaBuilder.HLASITOST_MAX),
            panely = panely,
            scenar = j.optString("scenar"),
            kontinuita = j.optString("kontinuita"),
            scenarOdhadem = j.optBoolean("scenarOdhadem"),
            oknaScenare = j.optInt("oknaScenare"),
            scenarPlanu = j.optInt("scenarPlanu"),
            radku = j.optInt("radku"),
            sloupcu = j.optInt("sloupcu"),
            nalezy = (0 until (j.optJSONArray("nalezy")?.length() ?: 0)).map {
                val n = j.getJSONArray("nalezy").getJSONObject(it)
                SbNalez(n.optInt("cislo"), n.optString("text"))
            },
            jmenaFotek = j.optJSONObject("jmenaFotek")?.let { h -> h.keys().asSequence().associateWith { h.optString(it) } }
                .orEmpty().filterValues { it.isNotBlank() },
            panelyObrazku = j.optInt("panelyObrazku"),
            strih = (0 until (j.optJSONArray("strih")?.length() ?: 0)).mapNotNull {
                val r = j.getJSONArray("strih").getJSONObject(it)
                val druh = runCatching { SbTextStrihu.Druh.valueOf(r.optString("druh")) }.getOrNull() ?: return@mapNotNull null
                SbTextStrihu(r.optInt("cislo"), r.optString("text"), druh)
            },
        )
    }.getOrDefault(SbFilmScene())

    companion object {
        const val KEY = "sbFilmScene"
    }
}

/** Zadání a dovětek pro přepisovač jednoho úseku filmu. */
object SbFilmPrepis {

    /** Replika v uvozovkách — konec je zavírací uvozovka, středník mezi nimi není nutný. */
    private val REPLIKA_V_UVOZOVKACH = Regex("""([\p{L}][\p{L}0-9 .'’-]{0,24}?)\s*:\s*[„"“«»]([^„“”"«»]+)[“”"«»]""")
    /** Záloha bez uvozovek: repliky oddělené středníkem. */
    private val REPLIKA = Regex("""([\p{L}][\p{L}0-9 .'’-]{0,24}?)\s*:\s*([^;]+?)\s*(?:;|$)""")

    /**
     * Štítky, kterými storyboard značí popis děje nebo poznámku, ne postavu.
     * Text za nimi se nesmí dostat do `<d>` — H3 by ho řekl nahlas.
     */
    /** Štítky nálady — text za nimi jde do popisu jako „Mood: …“ (herecké podání). */
    private val EMOCE = setOf("emoce", "emotion", "emotions", "nálada", "nalada", "mood", "pocit", "pocity")

    private val NE_MLUVCI = setOf(
        "děj", "dej", "akce", "popis", "poznámka", "poznamka", "scéna", "scena", "záběr", "zaber",
        "kamera", "titulek", "střih", "strih", "zvuk", "hudba", "ruch", "ruchy",
        "action", "plot", "description", "note", "notes", "scene", "shot", "camera", "caption",
        "direction", "stage direction", "sfx", "sound", "music",
    ) + EMOCE

    /** Je to jméno postavy (a ne štítek popisu)? */
    fun jeMluvci(jmeno: String): Boolean = jmeno.trim().lowercase() !in NE_MLUVCI

    /** Všechny dvojice (štítek, text) včetně popisných štítků. */
    private fun vsechnyRepliky(text: String): List<Pair<String, String>> {
        val vUvozovkach = REPLIKA_V_UVOZOVKACH.findAll(text)
            .map { it.groupValues[1].trim() to it.groupValues[2].trim() }.toList()
        val vysledek = vUvozovkach.ifEmpty {
            REPLIKA.findAll(text).map { it.groupValues[1].trim() to it.groupValues[2].trim() }.toList()
        }
        return vysledek.filter { it.second.isNotBlank() }.map { (kdo, co) -> opravMluvciho(kdo) to co }
    }

    /** `Pepa: „…“; AI: „…“` → dvojice (mluvčí, text). Popisné štítky (DĚJ…) vynechá. */
    fun repliky(text: String): List<Pair<String, String>> =
        vsechnyRepliky(text).filter { jeMluvci(it.first) }

    /**
     * Rozdělí pole replik panelu na skutečné repliky a popis děje (text se
     * štítkem DĚJ, AKCE, POPIS…). Když popisný štítek chybí, vrátí text beze změny.
     */
    fun oddelDej(text: String): Pair<String, String> {
        val vse = vsechnyRepliky(text)
        val dej = vse.filterNot { jeMluvci(it.first) }
        if (dej.isEmpty()) return text to ""
        val repliky = vse.filter { jeMluvci(it.first) }
            .joinToString("; ") { (kdo, co) -> "$kdo: „$co“" }
        return repliky to dej.joinToString(" ") { (kdo, co) ->
            if (kdo.trim().lowercase() in EMOCE) "Mood: ${co.trimEnd('.')}." else co
        }
    }

    /**
     * Spojí dvě čtení replik jednoho panelu: jména mluvčích z celého
     * storyboardu (tam model čte „AI“ správně), text z ostřejšího čtení po
     * řádcích (tam „Al“, ale správně „k vypínači“). Když se počty neshodují,
     * platí ostřejší čtení celé.
     */
    fun sloucit(zCelku: String, zRadku: String): String {
        val a = repliky(zCelku)
        val b = repliky(zRadku)
        if (b.isEmpty()) return zCelku
        val spojene = if (a.size == b.size) a.zip(b).map { (x, y) -> x.first to y.second } else b
        return spojene.joinToString("; ") { (kdo, co) -> "$kdo: „${opravHacky(co)}“" }
    }

    private fun normalizuj(t: String) = t.lowercase()
        .replace(Regex("""[„“”"«»‚‘’']"""), "").replace("…", "...")
        .replace(Regex("""\s+"""), " ").trim()

    /**
     * Repliky z celku + ostřejší čtení po řádcích. Když řádek panel vypsal bez
     * repliky a tutéž větu přisoudil sousednímu panelu, je replika v celku
     * zdvojená a panel ji ztratí. Dvě skutečně stejné repliky vedle sebe řádek
     * vypíše u obou panelů, takže se nesmažou.
     */
    fun slucCteni(panely: List<SbPrecteny>, radky: Map<Int, String>): List<SbPrecteny> = panely.map { p ->
        val r = radky[p.cislo] ?: return@map p
        if (r.isNotBlank()) return@map p.copy(repliky = sloucit(p.repliky, r))
        val celek = repliky(p.repliky).map { normalizuj(it.second) }
        val uSouseda = listOf(p.cislo - 1, p.cislo + 1).mapNotNull { radky[it] }
            .flatMap { repliky(it) }.map { normalizuj(it.second) }
        if (celek.isNotEmpty() && celek.all { it in uSouseda }) p.copy(repliky = "") else p
    }

    private val CIZI_STOPA = Regex("""<(Audio|Video)\s*\d+>""")
    private val ODKAZ_ZABERU = Regex("""\[Shot (\d+)]""")
    private val REPLIKA_D = Regex("""<d>.*?</d>""", RegexOption.DOT_MATCHES_ALL)

    /**
     * Vadný přepis úseku: vymyšlená `<Audio>`/`<Video>` (žádné nejsou) nebo
     * odkaz na záběr, který v úseku není.
     */
    fun vadnyPrepis(text: String, pocetZaberu: Int): Boolean =
        CIZI_STOPA.containsMatchIn(text) ||
            ODKAZ_ZABERU.findAll(text).any { (it.groupValues[1].toIntOrNull() ?: 0) > pocetZaberu }

    private fun ciziZaber(t: String, n: Int) =
        ODKAZ_ZABERU.findAll(t).any { (it.groupValues[1].toIntOrNull() ?: 0) > n }

    /**
     * Replika opsaná v uvozovkách MIMO `<d>` (např. v shrnutí: „asks in Czech,
     * “To bylo na mě?”“) se smaže — jinak je v promptu dvakrát a H3 ji může
     * říct navíc (scénář uživatele 29. 9. 2026). Uvnitř `<d>` se nic nemění.
     */
    fun odstranCitaceReplik(text: String): String {
        val repliky = REPLIKA_D.findAll(text)
            .map { it.value.removePrefix("<d>").removeSuffix("</d>").replace(Regex("""^\s*\[[^\]]*]\s*"""), "").trim() }
            .filter { it.length >= 4 }.toSet()
        if (repliky.isEmpty()) return text
        fun cisti(usek: String): String = repliky.fold(usek) { acc, r ->
            val konec = if (r.last() in ".?!…") "." else ""
            Regex("""[,:]?\s*[“"„«]\s*""" + Regex.escape(r) + """\s*[”"“»]""").replace(acc, konec)
        }
        val out = StringBuilder()
        var od = 0
        REPLIKA_D.findAll(text).forEach { m ->
            out.append(cisti(text.substring(od, m.range.first))).append(m.value)
            od = m.range.last + 1
        }
        out.append(cisti(text.substring(od)))
        return out.toString()
    }

    /** Věty textu; tečka nebo vykřičník uvnitř `<d>…</d>` větu nekončí. */
    private fun vety(t: String): List<String> {
        val chranene = REPLIKA_D.findAll(t).map { it.range }.toList()
        val out = mutableListOf<String>()
        var od = 0
        Regex("""(?<=[.!?])\s+""").findAll(t).forEach { m ->
            if (chranene.none { m.range.first in it }) {
                out += t.substring(od, m.range.last + 1)
                od = m.range.last + 1
            }
        }
        out += t.substring(od)
        return out
    }

    /**
     * Přepis úseku bez vymyšlených stop a cizích záběrů — deterministicky, aby
     * úsek vyšel napoprvé (29. 9. 2026: opakovaný přepis s kontrolní větou
     * vadu neodstranil, přepis je hladový). Model občas přidá `<Audio 1>` do
     * definic, shrnutí i retence a do seznamu „appears in“ záběry z jiného
     * úseku. Odstraní se jen tyhle kousky: řádek s `<Audio>`, věta s ním,
     * záběr mimo úsek ze seznamu. Věta s replikou `<d>` se nikdy nemaže.
     */
    fun ocistiPrepis(puvodni: String, pocetZaberu: Int): String {
        val text = odstranCitaceReplik(puvodni)
        if (!vadnyPrepis(text, pocetZaberu)) return text
        val radky = text.split("\n").mapNotNull { r ->
            val bezD = REPLIKA_D.replace(r, "")
            // „[Shot 6] …“ — celý záběr, který v úseku není; s replikou zůstane.
            val zacatek = Regex("""^\s*\[Shot (\d+)]""").find(r)?.groupValues?.get(1)?.toIntOrNull()
            if (zacatek != null && zacatek > pocetZaberu && bezD == r) return@mapNotNull null
            // Řádek, který celý patří vymyšlené stopě (definice, retence).
            if (Regex("""^\s*<(Audio|Video)\s*\d+>""").containsMatchIn(r)) return@mapNotNull null
            if (!CIZI_STOPA.containsMatchIn(bezD) && !ciziZaber(bezD, pocetZaberu)) return@mapNotNull r
            // Seznam „(appears in [Shot 1], [Shot 3], [Shot 6])“ → jen záběry úseku.
            var s = Regex("""\(appears in ([^)]*)\)""").replace(r) { m ->
                val zbyle = ODKAZ_ZABERU.findAll(m.groupValues[1]).map { it.groupValues[1].toInt() }
                    .filter { it <= pocetZaberu }.toList()
                if (zbyle.isEmpty()) "" else "(appears in ${zbyle.joinToString(", ") { "[Shot $it]" }})"
            }.replace(Regex("""\s+:"""), ":")
            // Zbytek po větách: věta s vymyšlenou stopou nebo cizím záběrem pryč.
            s = vety(s).filter { v ->
                val vBezD = REPLIKA_D.replace(v, "")
                vBezD != v || (!CIZI_STOPA.containsMatchIn(vBezD) && !ciziZaber(vBezD, pocetZaberu))
            }.joinToString("").trimEnd()
            s.ifBlank { null }
        }
        // Pole, které tím zůstalo prázdné, dostane N/A (tvar šesti polí se nemění).
        val out = mutableListOf<String>()
        radky.forEachIndexed { i, r ->
            out += r
            val pole = Regex("""^[a-z_]+:$""").matches(r.trim())
            val dalsi = radky.getOrNull(i + 1)
            if (pole && (dalsi == null || dalsi.isBlank() || Regex("""^[a-z_]+:$""").matches(dalsi.trim()))) out += "N/A"
        }
        return out.joinToString("\n")
    }

    /** `t’` / `d’` na konci slova je ť / ď, jak ho model vidí v tištěném písmu. */
    fun opravHacky(text: String): String =
        text.replace(Regex("""t[’'ʼ´`](?=\s|[.,!?…]|$)"""), "ť").replace(Regex("""d[’'ʼ´`](?=\s|[.,!?…]|$)"""), "ď")

    /** Mluvčí „Al“ je v tištěném písmu „AI“ (malé L a velké I vypadají stejně). */
    fun opravMluvciho(jmeno: String): String = if (jmeno == "Al") "AI" else jmeno

    /**
     * Repliky úseku, které v hotovém promptu nejsou uvnitř `<d>…</d>` (5.27):
     * přepisovač je vynechal nebo změnil — video by je neřeklo.
     */
    fun chybejiciRepliky(prompt: String, usek: SbUsek): List<String> {
        fun n(t: String) = t.lowercase().replace(Regex("""[„“”"«».,!?…:;–—\-]"""), " ").replace(Regex("""\s+"""), " ").trim()
        val d = Regex("""<d>(.*?)</d>""", RegexOption.DOT_MATCHES_ALL).findAll(prompt)
            .map { n(it.groupValues[1].replace(Regex("""^\s*\[[^\]]*]\s*"""), "")) }.toList()
        val spojene = d.indices.flatMap { i -> (i until minOf(d.size, i + 3)).map { j -> d.subList(i, j + 1).joinToString(" ") } }
        fun je(r: String): Boolean {
            val x = n(r)
            if (x.isEmpty()) return true
            return spojene.any { it == x || Regex("""(?<![\p{L}])""" + Regex.escape(x) + """(?![\p{L}])""").containsMatchIn(it) }
        }
        return usek.panely.flatMap { repliky(it.repliky) }.map { it.second }.filterNot { je(it) }
    }

    /** Jazyk všech replik filmu dohromady (null = nepoznaný, model ho určí sám). */
    fun jazykFilmu(panely: List<SbPanel>): String? =
        DialogyH3.jazyk(panely.flatMap { repliky(it.repliky) }.map { DialogyH3.Replika(it.first, it.second) })

    /**
     * Jazyk filmu pro scénu. U scénáře (5.16) i podle celého textu scénáře:
     * krátké repliky („Tati, podívej.“, „Na ten den si pamatuju.“) češtinu
     * samy neprozradí a H3 pak dostal `<d>[Language]` (svatební film 30. 9. 2026).
     */
    fun jazykFilmu(s: SbFilmScene): String? = jazykFilmu(s.panely)
        ?: if (s.zdroj == SbZdroj.SCENAR && s.panely.any { repliky(it.repliky).isNotEmpty() })
            DialogyH3.jazyk(listOf(DialogyH3.Replika("scénář", s.scenar)))
        else null

    /** Stálá ID mluvčích přes celý film (S1, S2… podle prvního výskytu). */
    fun idMluvcich(panely: List<SbPanel>): Map<String, String> =
        panely.flatMap { repliky(it.repliky).map { r -> r.first } }.distinct()
            .withIndex().associate { (i, m) -> m to "S${i + 1}" }

    /**
     * Zadání úseku: děj celého filmu (když ho uživatel napsal) a kde v něm
     * úsek je. Přepisovač píše anglicky; repliky ze scénáře hlídá [DialogyH3].
     */
    fun zadani(scene: SbFilmScene, k: Int, n: Int): String {
        val dej = scene.dej.trim()
        val cast = if (n > 1) "Part ${k + 1} of $n of one continuous film" else "A short film"
        return when {
            scene.zdroj == SbZdroj.SCENAR -> "$cast, told by the storyboard in <Picture 1>."
            dej.isNotEmpty() -> "$cast. The whole story: $dej"
            scene.seStoryboardem -> "$cast, told by the storyboard in <Picture 1>."
            else -> "$cast."
        }
    }

    /**
     * Dovětek: přesný seznam záběrů úseku s časy (délky počítá appka, ne
     * model), role obrázků a navázání na předchozí úsek.
     */
    fun hlidka(
        pocetObrazku: Int, usek: SbUsek, k: Int, n: Int, seStoryboardem: Boolean = true,
        idMluvcich: Map<String, String> = idMluvcich(usek.panely),
        /** Jazyk replik celého filmu — krátká replika („Výborný.“) sama češtinu neprozradí. */
        jazykFilmu: String? = jazykFilmu(usek.panely),
        /** Hlas každého mluvčího z čtení storyboardu — stejný pro všechny úseky. */
        hlasy: Map<String, String> = emptyMap(),
        /** Poslední panel předchozího úseku — co se už stalo, ať se to neopakuje. */
        predchozi: SbPanel? = null,
        /** Vzhled postav a zvířat z čtení storyboardu — stejný pro všechny úseky. */
        vzhled: Map<String, String> = emptyMap(),
        /** Kontinuita celého filmu ze scénáře (5.14) — stejná věta v každém úseku. */
        kontinuita: String = "",
        /**
         * Jména postav na fotkách v pořadí nahrání (5.17). Prázdné = staré
         * obecné „show the characters“. S jmény: `<Picture 2> is Syn` a jejich
         * textový vzhled se vynechá — vzhled dává fotka.
         */
        jmenaFotek: List<String> = emptyList(),
        /** Popis vzhledu uvádí změnu podle oken (5.26, Dar mudrců: vlasy do okna 4 dlouhé, pak krátké). */
        vzhledSeMeni: Boolean = false,
    ): String {
        val sb = StringBuilder("\n\n[There are exactly $pocetObrazku reference images and nothing else: ")
        sb.append((1..pocetObrazku).joinToString(", ") { "<Picture $it>" }).append(". ")
        val prvniPostava = if (seStoryboardem) 2 else 1
        if (seStoryboardem) {
            sb.append("<Picture 1> is a storyboard reference: it defines the viewpoint, subject placement ")
            sb.append("and look of these shots. It is not a frame of the video.")
        }
        if (pocetObrazku >= prvniPostava && jmenaFotek.size == pocetObrazku - prvniPostava + 1) {
            sb.append(" ").append(jmenaFotek.mapIndexed { i, j -> "<Picture ${prvniPostava + i}> is $j" }.joinToString(", "))
            sb.append(": define each of them in subject_definitions as a <Subject K> with the face, hair, body and ")
            sb.append("clothing from their picture, and keep them identical in every shot.")
        } else if (pocetObrazku >= prvniPostava) {
            sb.append(" ").append((prvniPostava..pocetObrazku).joinToString(", ") { "<Picture $it>" })
            sb.append(" show the characters: define each one in subject_definitions as a <Subject K> ")
            sb.append("taken from its picture and keep them identical in every shot.")
        }
        if (k > 0) {
            sb.append(" This part continues directly from the previous part: the first moment picks up ")
            sb.append("the motion of the previous shot before the first cut.")
            predchozi?.popis?.takeIf { it.isNotBlank() }?.let {
                sb.append(" The previous part ended with: ").append(it.trim().trimEnd('.')).append(".")
            }
        }
        sb.append(" Write exactly these ${usek.panely.size} timed shots, in this order, one per line of ")
        sb.append("the list, with these start times, and nothing after the last one:")
        var t = 0.0
        usek.panely.forEachIndexed { i, p ->
            sb.append("\n[Shot ${i + 1}]")
            if (i > 0) sb.append(" At ${SbFilmPlan.casH3(t)},")
            sb.append(if (seStoryboardem) " storyboard panel ${p.cislo}" else " shot ${p.cislo} of the film")
            if (p.typ.isNotBlank()) sb.append(", ${p.typ}")
            if (p.kamera.isNotBlank()) sb.append(", camera ${p.kamera}")
            sb.append(": ${p.popis}")
            val repl = repliky(p.repliky)
            val podani = if (p.podani.isBlank()) emptyList() else p.podani.split(";").map { it.trim() }
            repl.forEachIndexed { r, (kdo, text) ->
                val tag = jazykFilmu ?: "Language"
                // Podání ze scénáře (5.14) jako příslovečné určení před <d>, jako v příručce H3.
                val jak = podani.getOrNull(r)?.takeIf { it.isNotBlank() }?.let { ", $it," }.orEmpty()
                sb.append("\n    spoken right as this shot begins: $kdo (${idMluvcich[kdo] ?: "S?"})$jak says <d>[$tag] $text</d>")
            }
            // Zvuk záběru ze scénáře jako fyzická událost (5.14).
            if (p.zvuk.isNotBlank()) sb.append("\n    sound in this shot: ").append(p.zvuk.trim().trimEnd('.')).append(".")
            // Displej telefonu: H3 na něm píše nesmyslná písmena — jen tvary a barvy (5.14, kritici).
            // Osoba na displeji je fotka, ne postava filmu („mladá maminka mrkne“ vs. 72letá maminka).
            if (p.obrazovka) {
                // Obraz a video na displeji zůstávají, jen text a ovládání jsou tvary — dřív přepisovač
                // nahradil i hrnek ve videu na telefonu barevnými plochami (hrnek 30. 9. 2026).
                sb.append("\n    pictures and video on the phone screen play as described; any text or app interface on it ")
                sb.append("is only soft colour blocks and simple shapes, and any person seen on the screen is a picture on the display.")
            }
            // Záběr bez napsané repliky: „doktorka volá ke dveřím“ bez textu
            // přepisovač popsal jako volání a H3 si slova vymyslel (29. 9. 2026).
            if (repl.isEmpty()) {
                sb.append("\n    SILENT SHOT — nobody speaks. If the action above mentions calling, shouting or ")
                sb.append("saying something, show it only as a silent look or ")
                sb.append("small gesture and quote no words.")
            }
            t += p.sekundy
        }
        // Bez tohohle úsek 2 Iron Mask ukázal i odhalení krále z panelu 6,
        // které patří do dalšího úseku (28. 9. 2026).
        // 28. 9. 2026 úsek 2 začal „ruka otáčí klíčem v zámku dveří“ z panelu 2
        // a ve filmu se dveře odemykaly podruhé.
        // Vzhled je v celém filmu stejný: bez pevného popisu si přepisovač
        // v úseku 2 vymyslel modrý svetr a bílého psa z vzoru příručky
        // (film uživatele 29. 9. 2026, na storyboardu béžový svetr a zlatý retrívr).
        if (kontinuita.isNotBlank()) {
            sb.append("\nContinuity for the whole film, keep it in every shot: ").append(kontinuita.trim().trimEnd('.')).append(".")
        }
        // Postava s fotkou má vzhled z fotky — textový popis by se s ní přel.
        @Suppress("NAME_SHADOWING")
        val vzhled0 = vzhled.filterKeys { k -> jmenaFotek.none { it.equals(k, ignoreCase = true) } }
        // Změna vzhledu podle oken se rozhodne tady, ne v přepisovači: úsek dostane
        // jen vzhled svých panelů (Dar mudrců 30. 9. 2026 — v oknech 9–10 napsal dlouhé vlasy).
        val panelyUseku = usek.panely.map { it.cislo }
        val vUseku = if (vzhledSeMeni) vzhled0.mapValues { (_, v) -> SbScenar.vzhledProPanely(v, panelyUseku) } else emptyMap()
        val menene = vzhled0.filterValues { SbScenar.vzhledSeMeni(mapOf("" to it)) }.keys
        val vyreseno = vzhledSeMeni && menene.isNotEmpty() && menene.all { vUseku[it] != null }
        val vzhled = if (vyreseno) vzhled0.mapValues { (k, v) -> if (k in menene) vUseku.getValue(k)!! else v } else vzhled0
        if (vzhled.isNotEmpty()) {
            sb.append("\nCharacters for the whole film: define each of them in subject_definitions as a ")
            sb.append("<Subject K> with exactly these looks, and keep the looks identical in every shot unless ")
            if (vyreseno) sb.append("a look below names the shots it belongs to — then use it exactly in those shots and nowhere else: ")
            else if (vzhledSeMeni) sb.append("a shot's action or the look itself says it changes from a certain window (window N = ")
                .append("storyboard panel N) — then use the look that belongs to that panel: ")
            else sb.append("a shot's action says they change clothes: ")
            sb.append(vzhled.entries.joinToString("; ") { "${it.key} — ${it.value}" }).append(".")
        }
        if (usek.panely.any { repliky(it.repliky).isNotEmpty() }) {
            sb.append("\nEvery spoken line listed above goes into its shot word for word, in its original ")
            sb.append("language, inside <d>; never translate, shorten or drop a line. The speaker ")
            sb.append("description and the speaker ID stay outside <d>. Speaker IDs for the whole film: ")
            sb.append(idMluvcich.entries.joinToString(", ") { "${it.key} = (${it.value})" }).append(".")
            // Hlas je v celém filmu stejný: úseky se přepisují zvlášť a bez
            // pevného popisu by si přepisovač v každém vymyslel jiný.
            // Jména z popisu hlasů a z replik se můžou lišit velikostí písmen (Muž / MUŽ).
            fun hlasPro(kdo: String) = hlasy.entries.firstOrNull { it.key.equals(kdo, ignoreCase = true) }?.value
            val znameHlasy = idMluvcich.keys.filter { !hlasPro(it).isNullOrBlank() }
            if (znameHlasy.isNotEmpty()) {
                sb.append(" Voices for the whole film, use these exact words: ")
                sb.append(znameHlasy.joinToString("; ") {
                    "$it (${idMluvcich[it]}) — ${hlasPro(it)}, speaking at a lively, natural pace"
                }).append(".")
            }
            // Herecké podání podle oficiální příručky H3 (4.4 + vzory Ref2VA),
            // doladěné s kritikem: hlas jednou a pak „in the same … voice“,
            // podání před <d>, po </d> zavřené rty a tichá reakce (krátké záběry
            // nesnesou další akci). Příručka se nejmenuje — model by napodobil
            // její vzorový příklad.
            // Ověřeno přepisem obou úseků (29. 9. 2026). Zástupný text v ostrých
            // závorkách („<those words>“) a zákaz „never quote outside <d>“ vedly
            // k replikám v uvozovkách bez <d> — proto kladně a bez závorek.
            sb.append("\nActing (only in shots with a listed line): the first time a speaker talks, name ")
            if (znameHlasy.isNotEmpty()) sb.append("their voice with the words given above; in their later ")
            else sb.append("their voice in plain words from how they look (age, gender, pitch, timbre); in their later ")
            sb.append("lines, repeat it as \"in the same ... voice\" with those same words. Before each line, add ")
            sb.append("a few words of delivery that fit this moment of the story (emotion, tone) and the facial ")
            sb.append("expression while speaking. Every spoken line stays inside <d>[Language] ...</d> exactly as ")
            sb.append("listed, and its words appear nowhere else. Each line starts right as its shot begins, at a ")
            sb.append("lively, natural conversational pace, and the next speaker answers right on the cut, like a real ")
            sb.append("quick exchange. After the line the speaker closes their lips with a brief natural reaction until ")
            sb.append("the cut; add no new action, laughter or sound. The setting, characters and actions stay exactly ")
            sb.append("as listed above.")
        }
        // Obecné pravidlo samo nestačilo: přepisovač napsal „calls out toward the door, “Next!”“
        // (ověřeno přepisem 29. 9. 2026); pokyn přímo u záběru + zákaz uvozovek mimo <d> ano.
        sb.append("\nA SILENT SHOT has no voice at all: never write calling, shouting, talking or an ")
        sb.append("off-screen voice in it, and never put quoted words outside <d> anywhere — the video ")
        sb.append("speaks every quoted or described utterance, and without an exact <d> line it invents the words.")
        sb.append("\nEach shot shows only the action of its own panel. Never repeat an action from an ")
        sb.append("earlier shot or an earlier part, and do not use storyboard panels that are not in ")
        sb.append("this list — they are either in another part or cut from the film.")
        // Příručka Ref2VA zná <Audio N> pro převzatý zvuk; repliky „slovo od slova“
        // si model vykládal jako převzatou stopu a přidal <Audio 1>. Pojmenovat
        // úlohu jejím typem z příručky to odstranilo u obou úseků (přepis 29. 9. 2026).
        val n = usek.panely.size
        sb.append("\nTotal ${"%.1f".format(java.util.Locale.ROOT, usek.sekundy)} seconds. The task is ")
        sb.append("[reference generation] only: the voices and sounds are generated together with the picture, ")
        sb.append("so the only labels are ")
        sb.append(if (pocetObrazku == 1) "<Picture 1> and the <Subject K> defined from it."
        else "<Picture 1> to <Picture $pocetObrazku> and the <Subject K> defined from them.")
        sb.append(" This part has $n ${if (n == 1) "shot, [Shot 1]" else "shots, [Shot 1] to [Shot $n]"}")
        sb.append(", and every field refers only to them.\n]")
        return sb.toString()
    }
}
