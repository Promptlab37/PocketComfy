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
    DEJ("Vytvořit z děje");

    val title: String get() = t(titleCs)
}

/**
 * Rozlišení filmu. 480p je ověřené (2×5 s = 7,6 min, VRAM 12,1 GB);
 * 720p na přání uživatele 28. 9. 2026 — má 2,25× víc bodů, běh je řádově
 * delší.
 */
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
            "timbre), for example Anna = a woman in her 30s with a warm, low voice; separated by ; — or none>\n" +
            "Then one line per numbered shot panel, in the panel order: PANEL <number> | <the time " +
            "range exactly as printed on that panel, for example 00-04s, or none> | <shot size: " +
            "wide, medium, close-up, extreme close-up, insert or detail> | <camera movement, or " +
            "static> | <what happens in the panel, one short sentence> | <every spoken line " +
            "printed with that panel, copied word for word in its original language, as " +
            "Speaker: \"line\", separated by ; — or none>\n" +
            "Text labelled as action, plot, description or a note (for example DĚJ, AKCE, POPIS, " +
            "ACTION, NOTE) is not a spoken line: put it into the action field, never into the lines.\n" +
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
        return SbCteni(nazev, celkem, zaberu, panely, radku, sloupcu, hlasy)
    }

    /**
     * Druhé čtení replik po řádcích mřížky. Celý storyboard se pro vidoucí
     * model zmenší a drobné písmo pod panely pak čte s chybami (tester:
     * „k vypínání“ místo „k vypínači“, „celý“ místo „celej“). Pruh s jedním
     * řádkem panelů dostane stejný počet obrazových tokenů na třetinu plochy.
     */
    fun otazkaRadku(prvni: Int, posledni: Int): String =
        "This image is one row of a film storyboard: the shot panels numbered $prvni to $posledni, " +
            "left to right. Answer in plain lines only, one line per panel: PANEL <number> | <every " +
            "spoken line printed with that panel, copied letter by letter exactly as printed, in its " +
            "original language, as Speaker: \"line\", separated by ; — or none>. Keep the exact " +
            "spelling, including colloquial words and punctuation. Do not translate or correct anything. " +
            "Text labelled as action, plot, description or a note (for example DĚJ, AKCE, POPIS) is not " +
            "a spoken line — leave it out."

    /** Odpověď [otazkaRadku] → číslo panelu → repliky. */
    fun prectiRepliky(text: String): Map<Int, String> {
        val radky = text.replace(Regex("""(?i)\s*(?=\bPANEL\s*\d)"""), "\n")
        return radky.lines().mapNotNull { r ->
            val casti = r.trim().split("|", limit = 2).map { it.trim() }
            if (casti.size < 2 || !casti[0].uppercase().startsWith("PANEL")) return@mapNotNull null
            val n = CISLO.find(casti[0])?.value?.toIntOrNull() ?: return@mapNotNull null
            val rep = casti[1].takeUnless { it.isBlank() || it.equals("none", true) } ?: return@mapNotNull null
            n to rep
        }.toMap()
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
        var delky = vepsane ?: zdroj.map { odhad(it.typ, it.kamera) }
        val soucet = delky.sum()
        if (soucet > MAX_CELKEM_S) {
            // Dolů, ať zaokrouhlení nepřeleze strop.
            val k = MAX_CELKEM_S / soucet
            delky = delky.map { kotlin.math.floor(it * k * 10) / 10 }
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
        return SbPlan(panely, vepsane != null, cteni.hlasy)
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

    /** Na desetiny sekundy — jemněji model stejně nestřihá. */
    private fun zaokrouhli(s: Double): Double = (s * 10).roundToInt() / 10.0

    /**
     * Rozdělí panely na úseky ≤ [MAX_USEK_S] vždy na hranici panelů, s co
     * nejmenším nejdelším úsekem (vyvažuje se čas, ne počet panelů). Panel
     * delší než strop se nejdřív rozpůlí.
     */
    fun rozdel(panely: List<SbPanel>): List<SbUsek> {
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
            "and voice in plain English words (pitch, timbre), separated by ;, or none>. Then one line " +
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
        val soucet = plan.panely.sumOf { it.sekundy }
        val k = cilSekund / soucet
        // Strop panelu je tu úsek (14 s), ne 8 s: když model navrhne málo
        // záběrů na dlouhý film, musí se délka dorovnat i tak.
        var panely = plan.panely.map { it.copy(sekundy = zaokrouhli((it.sekundy * k).coerceIn(MIN_PANEL_S, MAX_USEK_S))) }
        // Po mezích může součet ujet — rozdíl se rozdělí mezi panely, které
        // mají ještě místo (po desetinách, ať se zaokrouhlení nesčítá).
        val delky = panely.map { it.sekundy }.toMutableList()
        var rozdil = zaokrouhli(cilSekund - delky.sum())
        var pojistka = 1000
        while (kotlin.math.abs(rozdil) >= 0.05 && pojistka-- > 0) {
            val krok = if (rozdil > 0) 0.1 else -0.1
            val i = delky.indices
                .filter { if (krok > 0) delky[it] + krok <= MAX_USEK_S + 1e-9 else delky[it] + krok >= MIN_PANEL_S - 1e-9 }
                .maxByOrNull { if (krok > 0) MAX_USEK_S - delky[it] else delky[it] } ?: break
            delky[i] = zaokrouhli(delky[i] + krok)
            rozdil = zaokrouhli(rozdil - krok)
        }
        return SbPlan(panely.mapIndexed { i, p -> p.copy(sekundy = delky[i]) }, false, plan.hlasy)
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
) {
    val useky: List<SbUsek> get() = SbFilmPlan.rozdel(panely)
    val sekundy: Double get() = panely.sumOf { it.sekundy }

    /** Jde storyboard do H3 jako `<Picture 1>`? Jen když je z něj plán. */
    val seStoryboardem: Boolean get() = zdroj == SbZdroj.STORYBOARD && storyboard != null

    /** Nahrávají se v tomhle pořadí: storyboard = `<Picture 1>`, postavy dál. */
    val uploadImages: List<File>
        get() = listOfNotNull(storyboard.takeIf { seStoryboardem }) + postavy.map { it.soubor }

    companion object {
        const val MAX_POSTAV = 3
        val DELKY = listOf(15, 30, 45)
    }
}

/** Co kartě chybí, než se dá natočit. */
fun sbFilmProblem(s: SbFilmScene): String? = when (s.zdroj) {
    SbZdroj.STORYBOARD -> when {
        s.storyboard == null -> t("Vyber obrázek se storyboardem.")
        s.panely.isEmpty() -> t("Nejdřív storyboard přečti.")
        else -> null
    }
    // Bez obrázku storyboardu musí mít H3 aspoň jednu referenci — Ref2VA
    // bez předloh přepisovač odmítne.
    SbZdroj.DEJ -> when {
        s.postavy.isEmpty() -> t("Přidej aspoň jednu fotku postavy.")
        s.panely.isEmpty() -> t("Nejdřív nech navrhnout záběry.")
        else -> null
    }
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
            .put("zadaniUseku", org.json.JSONArray().also { a -> s.zadaniUseku.forEach { a.put(it) } })
            .put("nazev", s.nazev)
            .put("casyZeStoryboardu", s.casyZeStoryboardu)
            .put("hlasy", org.json.JSONObject().also { j -> s.hlasy.forEach { (k, v) -> j.put(k, v) } })
            .put("panely", org.json.JSONArray().also { a ->
                s.panely.forEach {
                    a.put(org.json.JSONObject().put("cislo", it.cislo).put("popis", it.popis)
                        .put("typ", it.typ).put("kamera", it.kamera).put("sekundy", it.sekundy)
                        .put("repliky", it.repliky))
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
                p.optString("kamera"), p.optDouble("sekundy", 3.0), p.optString("repliky"))
        }
        SbFilmScene(
            storyboard = sb, postavy = postavy, dej = j.optString("dej"),
            pomer = runCatching { LongMmPomer.valueOf(j.optString("pomer")) }.getOrDefault(LongMmPomer.NASIRKU),
            rozliseni = runCatching { SbRozliseni.valueOf(j.optString("rozliseni")) }.getOrDefault(SbRozliseni.R480),
            zdroj = runCatching { SbZdroj.valueOf(j.optString("zdroj")) }.getOrDefault(SbZdroj.STORYBOARD),
            cilSekund = j.optInt("cilSekund", 30),
            zadaniUseku = (0 until (j.optJSONArray("zadaniUseku")?.length() ?: 0))
                .map { j.getJSONArray("zadaniUseku").getString(it) },
            nazev = j.optString("nazev"), casyZeStoryboardu = j.optBoolean("casyZeStoryboardu"),
            hlasy = j.optJSONObject("hlasy")?.let { h -> h.keys().asSequence().associateWith { h.optString(it) } }
                .orEmpty().filterValues { it.isNotBlank() },
            panely = panely,
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
    private val NE_MLUVCI = setOf(
        "děj", "dej", "akce", "popis", "poznámka", "poznamka", "scéna", "scena", "záběr", "zaber",
        "kamera", "titulek", "střih", "strih", "zvuk", "hudba", "ruch", "ruchy",
        "action", "plot", "description", "note", "notes", "scene", "shot", "camera", "caption",
        "direction", "stage direction", "sfx", "sound", "music",
    )

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
        return repliky to dej.joinToString(" ") { it.second }
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

    /** `t’` / `d’` na konci slova je ť / ď, jak ho model vidí v tištěném písmu. */
    fun opravHacky(text: String): String =
        text.replace(Regex("""t[’'ʼ´`](?=\s|[.,!?…]|$)"""), "ť").replace(Regex("""d[’'ʼ´`](?=\s|[.,!?…]|$)"""), "ď")

    /** Mluvčí „Al“ je v tištěném písmu „AI“ (malé L a velké I vypadají stejně). */
    fun opravMluvciho(jmeno: String): String = if (jmeno == "Al") "AI" else jmeno

    /** Jazyk všech replik filmu dohromady (null = nepoznaný, model ho určí sám). */
    fun jazykFilmu(panely: List<SbPanel>): String? =
        DialogyH3.jazyk(panely.flatMap { repliky(it.repliky) }.map { DialogyH3.Replika(it.first, it.second) })

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
    ): String {
        val sb = StringBuilder("\n\n[There are exactly $pocetObrazku reference images and nothing else: ")
        sb.append((1..pocetObrazku).joinToString(", ") { "<Picture $it>" }).append(". ")
        val prvniPostava = if (seStoryboardem) 2 else 1
        if (seStoryboardem) {
            sb.append("<Picture 1> is a storyboard reference: it defines the viewpoint, subject placement ")
            sb.append("and look of these shots. It is not a frame of the video.")
        }
        if (pocetObrazku >= prvniPostava) {
            sb.append(" ").append((prvniPostava..pocetObrazku).joinToString(", ") { "<Picture $it>" })
            sb.append(" show the characters: define each one in subject_definitions as a <Subject K> ")
            sb.append("taken from its picture and keep them identical in every shot.")
        }
        if (k > 0) {
            sb.append(" This part continues directly from the previous part: the first moment picks up ")
            sb.append("the motion of the previous shot before the first cut.")
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
            repl.forEach { (kdo, text) ->
                val tag = jazykFilmu ?: "Language"
                sb.append("\n    spoken in this shot: $kdo (${idMluvcich[kdo] ?: "S?"}) says <d>[$tag] $text</d>")
            }
            // Záběr bez napsané repliky: „doktorka volá ke dveřím“ bez textu
            // přepisovač popsal jako volání a H3 si slova vymyslel (29. 9. 2026).
            if (repl.isEmpty()) {
                sb.append("\n    SILENT SHOT — nobody speaks. If the action above mentions calling, shouting or ")
                sb.append("saying something, show it only as a silent gesture (for example beckoning toward ")
                sb.append("the door) and quote no words.")
            }
            t += p.sekundy
        }
        // Bez tohohle úsek 2 Iron Mask ukázal i odhalení krále z panelu 6,
        // které patří do dalšího úseku (28. 9. 2026).
        // 28. 9. 2026 úsek 2 začal „ruka otáčí klíčem v zámku dveří“ z panelu 2
        // a ve filmu se dveře odemykaly podruhé.
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
                sb.append(znameHlasy.joinToString("; ") { "$it (${idMluvcich[it]}) — ${hlasPro(it)}" }).append(".")
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
            sb.append("their voice in plain words (the voice given above when there is one); in their later ")
            sb.append("lines, repeat it as \"in the same ... voice\" with those same words. Before each line, add ")
            sb.append("a few words of delivery that fit this moment of the story (emotion, tone) and the facial ")
            sb.append("expression while speaking. Every spoken line stays inside <d>[Language] ...</d> exactly as ")
            sb.append("listed, and its words appear nowhere else. Right after each line, the speaker closes their ")
            sb.append("lips and holds a silent facial reaction; add no new action, gesture, laughter or sound, and ")
            sb.append("keep every shot within its time. The setting, characters and actions stay exactly as listed above.")
        }
        // Obecné pravidlo samo nestačilo: přepisovač napsal „calls out toward the door, “Next!”“
        // (ověřeno přepisem 29. 9. 2026); pokyn přímo u záběru + zákaz uvozovek mimo <d> ano.
        sb.append("\nA SILENT SHOT has no voice at all: never write calling, shouting, talking or an ")
        sb.append("off-screen voice in it, and never put quoted words outside <d> anywhere — the video ")
        sb.append("speaks every quoted or described utterance, and without an exact <d> line it invents the words.")
        sb.append("\nEach shot shows only the action of its own panel. Never repeat an action from an ")
        sb.append("earlier shot or an earlier part, and do not use storyboard panels that are not in ")
        sb.append("this list — they are either in another part or cut from the film.")
        sb.append("\nTotal ${"%.1f".format(java.util.Locale.ROOT, usek.sekundy)} seconds. Do not introduce ")
        sb.append("<Video> or <Audio> labels and do not refer to any reference that was not provided.\n]")
        return sb.toString()
    }
}
