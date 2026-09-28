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
)

/** Co model ze storyboardu přečetl, ještě bez naplánovaných délek. */
data class SbCteni(
    val nazev: String?,
    val celkemVepsano: Double?,
    val zaberuVepsano: Int?,
    val panely: List<SbPrecteny>,
)

data class SbPrecteny(
    val cislo: Int,
    val od: Double?,
    val doS: Double?,
    val typ: String,
    val kamera: String,
    val popis: String,
)

/** Naplánované panely a jestli délky pocházejí z časů vepsaných ve storyboardu. */
data class SbPlan(val panely: List<SbPanel>, val zeStoryboardu: Boolean)

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
            "printed on the sheet, or none>\n" +
            "Then one line per numbered shot panel, in the panel order: PANEL <number> | <the time " +
            "range exactly as printed on that panel, for example 00-04s, or none> | <shot size: " +
            "wide, medium, close-up, extreme close-up, insert or detail> | <camera movement, or " +
            "static> | <what happens in the panel, one short sentence>\n" +
            "Skip boxes that are only colour, texture or environment swatches. Do not invent " +
            "panels or times that are not on the sheet."

    private val CISLO = Regex("""\d+(?:[.,]\d+)?""")
    private val CAS = Regex("""(\d{1,2})(?::(\d{2}(?:[.,]\d+)?))?\s*s?\s*[-–—]\s*(\d{1,2})(?::(\d{2}(?:[.,]\d+)?))?\s*s?""")

    /** Rozebere odpověď modelu. Neznámé řádky přeskočí. */
    fun precti(text: String): SbCteni {
        var nazev: String? = null
        var celkem: Double? = null
        var zaberu: Int? = null
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
                        popis = casti.drop(4).joinToString(" | ").ifBlank { casti.lastOrNull().orEmpty() },
                    )
                }
            }
        }
        return SbCteni(nazev, celkem, zaberu, panely)
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
            )
        }
        return SbPlan(panely, vepsane != null)
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
    /** Plátno filmu. Rozlišení je pevné (480p), jen ověřené. */
    val pomer: LongMmPomer = LongMmPomer.NASIRKU,
    /** Naplánované panely (po „Přečíst“). */
    val panely: List<SbPanel> = emptyList(),
    val nazev: String = "",
    /** Byly délky vepsané ve storyboardu (true), nebo odhadnuté (false)? */
    val casyZeStoryboardu: Boolean = false,
    /** Zadání úseků z přepisovače (po „Natočit“, před během). */
    val zadaniUseku: List<String> = emptyList(),
) {
    val useky: List<SbUsek> get() = SbFilmPlan.rozdel(panely)
    val sekundy: Double get() = panely.sumOf { it.sekundy }

    /** Nahrávají se v tomhle pořadí: storyboard = `<Picture 1>`, postavy dál. */
    val uploadImages: List<File>
        get() = listOfNotNull(storyboard) + postavy.map { it.soubor }

    companion object {
        const val MAX_POSTAV = 3
    }
}

/** Co kartě chybí, než se dá natočit. */
fun sbFilmProblem(s: SbFilmScene): String? = when {
    s.storyboard == null -> t("Vyber obrázek se storyboardem.")
    s.panely.isEmpty() -> t("Nejdřív storyboard přečti.")
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
            .put("nazev", s.nazev)
            .put("casyZeStoryboardu", s.casyZeStoryboardu)
            .put("panely", org.json.JSONArray().also { a ->
                s.panely.forEach {
                    a.put(org.json.JSONObject().put("cislo", it.cislo).put("popis", it.popis)
                        .put("typ", it.typ).put("kamera", it.kamera).put("sekundy", it.sekundy))
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
                p.optString("kamera"), p.optDouble("sekundy", 3.0))
        }
        SbFilmScene(
            storyboard = sb, postavy = postavy, dej = j.optString("dej"),
            pomer = runCatching { LongMmPomer.valueOf(j.optString("pomer")) }.getOrDefault(LongMmPomer.NASIRKU),
            nazev = j.optString("nazev"), casyZeStoryboardu = j.optBoolean("casyZeStoryboardu"),
            panely = panely,
        )
    }.getOrDefault(SbFilmScene())

    companion object {
        const val KEY = "sbFilmScene"
    }
}

/** Zadání a dovětek pro přepisovač jednoho úseku filmu. */
object SbFilmPrepis {

    /**
     * Zadání úseku: děj celého filmu (když ho uživatel napsal) a kde v něm
     * úsek je. Přepisovač píše anglicky; repliky ze scénáře hlídá [DialogyH3].
     */
    fun zadani(scene: SbFilmScene, k: Int, n: Int): String {
        val dej = scene.dej.trim()
        val cast = if (n > 1) "Part ${k + 1} of $n of one continuous film" else "A short film"
        return if (dej.isEmpty()) "$cast, told by the storyboard in <Picture 1>."
        else "$cast. The whole story: $dej"
    }

    /**
     * Dovětek: přesný seznam záběrů úseku s časy (délky počítá appka, ne
     * model), role obrázků a navázání na předchozí úsek.
     */
    fun hlidka(pocetObrazku: Int, usek: SbUsek, k: Int, n: Int): String {
        val sb = StringBuilder("\n\n[There are exactly $pocetObrazku reference images and nothing else: ")
        sb.append((1..pocetObrazku).joinToString(", ") { "<Picture $it>" }).append(". ")
        sb.append("<Picture 1> is a storyboard reference: it defines the viewpoint, subject placement ")
        sb.append("and look of these shots. It is not a frame of the video.")
        if (pocetObrazku > 1) {
            sb.append(" ").append((2..pocetObrazku).joinToString(", ") { "<Picture $it>" })
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
            sb.append(" storyboard panel ${p.cislo}")
            if (p.typ.isNotBlank()) sb.append(", ${p.typ}")
            if (p.kamera.isNotBlank()) sb.append(", camera ${p.kamera}")
            sb.append(": ${p.popis}")
            t += p.sekundy
        }
        // Bez tohohle úsek 2 Iron Mask ukázal i odhalení krále z panelu 6,
        // které patří do dalšího úseku (28. 9. 2026).
        // 28. 9. 2026 úsek 2 začal „ruka otáčí klíčem v zámku dveří“ z panelu 2
        // a ve filmu se dveře odemykaly podruhé.
        sb.append("\nEach shot shows only the action of its own panel. Never repeat an action from an ")
        sb.append("earlier shot or an earlier part, and do not use storyboard panels that are not in ")
        sb.append("this list — they are either in another part or cut from the film.")
        sb.append("\nTotal ${"%.1f".format(java.util.Locale.ROOT, usek.sekundy)} seconds. Do not introduce ")
        sb.append("<Video> or <Audio> labels and do not refer to any reference that was not provided.\n]")
        return sb.toString()
    }
}
