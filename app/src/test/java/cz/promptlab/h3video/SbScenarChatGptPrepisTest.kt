package cz.promptlab.h3video

import cz.promptlab.h3video.data.SbFilmPlan
import cz.promptlab.h3video.data.SbFilmPrepis
import cz.promptlab.h3video.data.SbScenar
import cz.promptlab.h3video.data.SbScenarModel
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * 5.68: storyboard přes ChatGPT. Uživatel vloží do ChatGPT obrázek a [SbScenarModel.ZADANI_CHATGPT_CS],
 * odpověď vloží do volby Storyboard + scénář. Pro každý storyboard ze sady ([TestCesty.sbKorpus],
 * `pravda.json`) se sestaví odpověď, jakou by ChatGPT podle zadání vrátil bezchybně, a projde
 * rozborem i plánováním jako v appce (MainViewModel.pouzijScenar). Okna, časy, mluvčí a repliky
 * musí zůstat přesně. K tomu odchylky, které ChatGPT dělá (tučné písmo, blok kódu, jiná hlavička,
 * prázdné řádky).
 */
class SbScenarChatGptPrepisTest {

    private data class Panel(val cislo: Int, val repliky: List<Pair<String, String>>, val nalada: String?, val dej: String,
                             val zvuk: String?, val od: Double?, val doS: Double?)
    private data class Pravda(val jmeno: String, val panely: List<Panel>, val kontinuita: String?)

    private fun nacti(d: File): Pravda {
        val j = JSONObject(File(d, "pravda.json").readText())
        val a = j.getJSONArray("panely")
        fun JSONObject.text(k: String) = if (isNull(k) || !has(k)) null else getString(k).takeIf { it.isNotBlank() }
        val panely = (0 until a.length()).map { i ->
            val p = a.getJSONObject(i)
            val r = p.getJSONArray("repliky")
            Panel(
                p.getInt("cislo"),
                (0 until r.length()).map { r.getJSONArray(it).let { x -> x.getString(0) to x.getString(1) } },
                p.text("nalada"), p.getString("dej"), p.text("zvuk"),
                if (p.has("od") && !p.isNull("od")) p.getDouble("od") else null,
                if (p.has("do") && !p.isNull("do")) p.getDouble("do") else null,
            )
        }
        return Pravda(d.name, panely, j.text("kontinuita"))
    }

    /** Mluvčí bez ohledu na velikost písmen (rozbor sjednotí STRÁŽNÝ → Strážný), text repliky přesně. */
    private fun stejne(a: List<Pair<String, String>>, b: List<Pair<String, String>>) =
        a.size == b.size && a.zip(b).all { (x, y) -> x.first.equals(y.first, ignoreCase = true) && x.second == y.second }

    private fun cas(x: Double) = if (x == Math.floor(x)) x.toInt().toString() else x.toString().replace('.', ',')

    /** Bezchybná odpověď ChatGPT podle [SbScenarModel.ZADANI_CHATGPT_CS]. */
    private fun odpoved(p: Pravda): String = buildString {
        append("STORYBOARD – ${p.jmeno.uppercase()}\n")
        append("Formát: 9:16\n")
        append("Postavy:\n")
        p.panely.flatMap { it.repliky.map { r -> r.first } }.distinct().forEach {
            append("$it: 40 let, krátké hnědé vlasy, brýle, modrá košile, tmavé džíny, bílé tenisky\n")
        }
        p.kontinuita?.let { append("Kontinuita: ${it.removePrefix("Kontinuita:").trim()}\n") }
        p.panely.forEach { x ->
            append("\n")
            append(if (x.od != null && x.doS != null) "[OKNO ${x.cislo} | ${cas(x.od)}–${cas(x.doS)} s]\n" else "[OKNO ${x.cislo}]\n")
            append("Děj: ${x.dej}\n")
            x.nalada?.let { append("Emoce: $it\n") }
            x.repliky.forEach { (kdo, co) -> append("$kdo: „$co“\n") }
            x.zvuk?.let { append("Zvuk: $it\n") }
        }
    }

    /** Rozbor a plán jako v appce; vrací popisy odchylek od pravdy (prázdné = v pořádku). */
    private fun over(p: Pravda, text: String): List<String> {
        val chyby = mutableListOf<String>()
        val s = SbScenar.rozeber(text) ?: return listOf("${p.jmeno}: nerozebráno")
        if (s.okna.size != p.panely.size) chyby += "${p.jmeno}: oken ${s.okna.size}, panelů ${p.panely.size}"
        p.panely.forEachIndexed { i, x ->
            val o = s.okna.getOrNull(i) ?: return@forEachIndexed
            if (o.cislo != x.cislo) chyby += "${p.jmeno} okno ${i + 1}: číslo ${o.cislo}, čekáno ${x.cislo}"
            if (x.od != null && (o.od != x.od || o.doS != x.doS)) chyby += "${p.jmeno} okno ${x.cislo}: čas ${o.od}–${o.doS}, čekáno ${x.od}–${x.doS}"
            val r = o.repliky.map { it.kdo to it.text }
            if (!stejne(r, x.repliky)) chyby += "${p.jmeno} okno ${x.cislo}: repliky $r, čekáno ${x.repliky}"
            // Hudba ze zvuku jde záměrně do poznámek pro střih (hudba přijde ze střihu), ostatní zvuk do okna.
            if (x.zvuk != null) {
                val z = x.zvuk.trim().trimEnd('.')
                val hudba = Regex("""(?iu)hudb|music""").containsMatchIn(z)
                val ok = if (hudba) o.poznamky.any { z in it } else o.zvuk.trim().trimEnd('.') == z
                if (!ok) chyby += "${p.jmeno} okno ${x.cislo}: zvuk „${o.zvuk}“, poznámky ${o.poznamky}"
            }
        }
        p.kontinuita?.let { k ->
            if (!s.kontinuita.contains(k.removePrefix("Kontinuita:").trim().trimEnd('.'))) chyby += "${p.jmeno}: kontinuita „${s.kontinuita}“"
        }
        // Plán jako MainViewModel.pouzijScenar (bez čtení obrázku).
        val plan = SbFilmPlan.naplanuj(SbScenar.cteni(s, null))
        val panely = SbScenar.doplnPanely(plan.panely, s)
        if (panely.size != p.panely.size) chyby += "${p.jmeno}: plán má ${panely.size} panelů"
        val sCasy = p.panely.all { it.od != null && it.doS != null }
        if (sCasy && !plan.zeStoryboardu) chyby += "${p.jmeno}: plán nepoužil natištěné časy"
        p.panely.forEachIndexed { i, x ->
            val pn = panely.getOrNull(i) ?: return@forEachIndexed
            val r = SbFilmPrepis.repliky(pn.repliky)
            if (!stejne(r, x.repliky)) chyby += "${p.jmeno} panel ${x.cislo}: repliky v plánu $r, čekáno ${x.repliky}"
            // Vytištěný čas platí přesně. Delší smí být jen panel, kam se replika fyzicky nevejde (5.69,
            // BYTY: 12 slabik do 2 s) — pak přesně na délku řeči ([SbFilmPlan.delkaReci]: tempo, nástup
            // a dozvuk, bez nichž H3 usekne poslední slabiku). Kratší než vytištěný nikdy (dřív strop 45 s).
            if (sCasy) {
                val tisk = x.doS!! - x.od!!
                val rec = SbFilmPlan.delkaReci(pn.repliky)?.coerceAtMost(SbFilmPlan.MAX_PANEL_S)
                val ocek = if (rec != null && rec > tisk) Math.round(rec * 10) / 10.0 else tisk
                if (kotlin.math.abs(pn.sekundy - ocek) > 1e-6) chyby += "${p.jmeno} panel ${x.cislo}: ${pn.sekundy} s, čekáno $ocek s (vytištěno $tisk s)"
            }
        }
        return chyby
    }

    private fun sada(): List<Pravda> {
        val korpus = TestCesty.sbKorpus
        assumeTrue("sada storyboardů není na tomhle počítači", korpus != null)
        return korpus!!.listFiles { f -> File(f, "pravda.json").isFile }!!.sortedBy { it.name }.map { nacti(it) }
    }

    @Test fun `zadani pro ChatGPT chce prepis a obsahuje tvar bloku`() {
        val z = SbScenarModel.ZADANI_CHATGPT_CS
        assertTrue(z.contains("[OKNO 1 | 0–4 s]"))
        assertTrue(z.contains("písmeno po písmenu"))
        assertTrue(z.contains("Kontinuita:"))
        assertTrue(z.contains("Než odpovíš, zkontroluj:"))
        assertTrue(SbScenarModel.ZADANI_CHATGPT_EN.contains("[WINDOW 1 | 0–4 s]"))
    }

    @Test fun `bezchybna odpoved ChatGPT se precte presne`() {
        val chyby = sada().flatMap { over(it, odpoved(it)) }
        assertEquals(emptyList<String>(), chyby)
    }

    /** Markdown: tučné hlavičky a štítky, nadpis s mřížkou. */
    @Test fun `tucne pismo z ChatGPT`() {
        val chyby = sada().flatMap { p ->
            val t = odpoved(p).lines().joinToString("\n") { r ->
                when {
                    r.startsWith("[OKNO") -> "**$r**"
                    r.startsWith("STORYBOARD") -> "## $r"
                    Regex("""^(Děj|Emoce|Zvuk|Postavy|Formát|Kontinuita):""").containsMatchIn(r) -> r.replaceFirst(Regex("""^([^:]+):"""), "**$1:**")
                    else -> r
                }
            }
            over(p, t)
        }
        assertEquals(emptyList<String>(), chyby)
    }

    /** Uživatel zkopíroval celou odpověď i s ohraničením bloku kódu. */
    @Test fun `blok kodu z ChatGPT`() {
        val chyby = sada().flatMap { p -> over(p, "```text\n${odpoved(p)}```") }
        assertEquals(emptyList<String>(), chyby)
    }

    /** Hlavička okna jinak: „OKNO 1 (0–4 s)“ / „OKNO 1“ bez závorek. */
    @Test fun `jina hlavicka okna`() {
        val chyby = sada().flatMap { p ->
            val t = odpoved(p)
                .replace(Regex("""\[OKNO (\d+) \| ([^\]]+)]"""), "OKNO $1 ($2)")
                .replace(Regex("""\[OKNO (\d+)]"""), "OKNO $1")
            over(p, t)
        }
        assertEquals(emptyList<String>(), chyby)
    }

    /** Prázdné řádky navíc mezi všemi řádky a mezery na koncích. */
    @Test fun `prazdne radky navic`() {
        val chyby = sada().flatMap { p -> over(p, "\n\n" + odpoved(p).lines().joinToString("\n\n") { "$it  " } + "\n\n") }
        assertEquals(emptyList<String>(), chyby)
    }

    /** Emoce natištěná v závorce u repliky jde do podání, replika a mluvčí zůstanou. */
    @Test fun `emoce v zavorce u repliky`() {
        val chyby = sada().flatMap { p ->
            val t = odpoved(p).lines().joinToString("\n") { r ->
                val m = Regex("""^([^:„\[]{1,30}): „""").find(r)
                if (m != null && !Regex("""^(Děj|Emoce|Zvuk|Formát|Kontinuita)$""").matches(m.groupValues[1])) r.replaceFirst(": „", " (naštvaně): „") else r
            }
            val s = SbScenar.rozeber(t)
            val podani = s?.okna?.flatMap { o -> o.repliky.map { it.podani } }.orEmpty()
            over(p, t) + podani.filter { it != "naštvaně" }.map { "${p.jmeno}: podání „$it“" }
        }
        assertEquals(emptyList<String>(), chyby)
    }
}
