package cz.promptlab.h3video

import cz.promptlab.h3video.data.SbFilmPlan
import cz.promptlab.h3video.data.SbFilmPrepis
import cz.promptlab.h3video.data.SbFilmScene
import cz.promptlab.h3video.data.SbScenar
import cz.promptlab.h3video.data.SbScenarPokryti
import cz.promptlab.h3video.data.SbZdroj
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Korpus scénářů v zápisech, jak je píšou ChatGPT, Claude, Gemini i lidé na telefonu
 * (odborníci 30. 9. 2026, po „Špatném stole“). Každý soubor nese očekávaná okna,
 * repliky s mluvčími, rod a rekvizity — appka musí všechno přečíst přesně.
 */
class SbScenarKorpusFormatuTest {

    private val dir = File("src/test/resources/korpus_formaty")

    private fun norm(t: String) = t.replace(Regex("""[„“”"«»‚‘]"""), "").replace(Regex("""\s+"""), " ").trim()
    /** Mluvčí malými písmeny; všechna označení vypravěče jsou „vypravěč“. */
    private fun mluvci(t: String): String {
        val l = t.trim().lowercase()
        return if (Regex("""^(vo|v\.o\.|voice.?over|narrator|vypravěč\p{L}*|hlas mimo obraz.*|hlas vypravěče)$""").matches(l)) "vypravěč" else l
    }

    /** Stejný mluvčí: celé jméno, nebo jméno o slovo kratší („tvůrce“ = „tvůrce videa“). */
    private fun stejny(a: String, b: String) = a == b || a.startsWith("$b ") || b.startsWith("$a ") || a.endsWith(" $b") || b.endsWith(" $a")

    @Test
    fun `kazdy scenar z korpusu se precte presne`() {
        val soubory = dir.listFiles { f -> f.extension == "json" }?.sortedBy { it.name }.orEmpty()
        assertTrue("prázdný korpus", soubory.isNotEmpty())
        val chyby = mutableListOf<String>()
        val log = StringBuilder()
        soubory.forEach { f ->
            val j = JSONObject(f.readText())
            val text = j.getString("text")
            fun chyba(t: String) { chyby += "${f.name} (${j.optString("name")}): $t" }
            val s = SbScenar.rozeber(text)
            if (s == null) { chyba("nerozebráno"); return@forEach }
            log.append("==== ${f.name} ${j.optString("name")}\npostavy=${s.postavy}\n")
            val okna = j.getInt("windows")
            if (s.okna.size != okna) chyba("oken ${s.okna.size}, čekáno $okna")
            j.optJSONArray("times")?.let { t ->
                (0 until t.length()).forEach { i ->
                    val ocek = t.getJSONArray(i).let { it.getDouble(0) to it.getDouble(1) }
                    val o = s.okna.getOrNull(i)
                    if (o?.od != ocek.first || o.doS != ocek.second) chyba("okno ${i + 1}: čas ${o?.od}–${o?.doS}, čekáno ${ocek.first}–${ocek.second}")
                }
            }
            val r = s.okna.flatMap { o -> o.repliky.map { Triple(o.cislo, it.kdo, it.text) } }
            log.append(r.joinToString("\n") { "  ${it.first} ${it.second}: ${it.third}" } + "\n")
            s.okna.forEach { o -> log.append("  [okno ${o.cislo}] obraz=${o.obraz} | akce=${o.akce} | texty=${o.texty}\n") }
            val ocek = j.getJSONArray("lines").let { a -> (0 until a.length()).map { i -> a.getJSONArray(i) } }
                .map { Triple(it.getInt(0), it.getString(1), it.getString(2)) }
            val dostal = r.map { Triple(it.first, mluvci(it.second), norm(it.third)) }
            val chtel = ocek.map { Triple(it.first, mluvci(it.second), norm(it.third)) }
            val shoda = dostal.size == chtel.size && dostal.zip(chtel).all { (x, y) -> x.first == y.first && x.third == y.third && stejny(x.second, y.second) }
            if (!shoda) {
                val a = dostal.filter { it !in chtel }; val b = chtel.filter { it !in dostal }
                chyba("repliky navíc $a / chybí $b")
            }
            val nepokryte = SbScenarPokryti.nepokryte(text, s)
            if (nepokryte.isNotEmpty()) chyba("nepokryté $nepokryte")

            val plan = SbFilmPlan.naplanuj(SbScenar.cteni(s, null))
            val hlasy = plan.hlasy.mapKeys { mluvci(it.key) }
            j.optJSONObject("genders")?.let { g ->
                g.keys().forEach { k ->
                    val rod = g.getString(k)
                    if (rod == "?") return@forEach
                    val h = hlasy.entries.firstOrNull { stejny(it.key, mluvci(k)) }?.value
                    if (h == null) { if (chtel.any { it.second == mluvci(k) }) chyba("$k nemá hlas"); return@forEach }
                    val zena = Regex("""\b(woman|girl|lady)\b""").containsMatchIn(h)
                    if ((rod == "f") != zena) chyba("$k má hlas „$h“, čekán rod $rod")
                }
            }
            j.optJSONArray("props")?.let { a -> (0 until a.length()).map { a.getString(it) } }?.forEach { p ->
                if (hlasy.keys.any { stejny(it, mluvci(p)) }) chyba("rekvizita $p má hlas")
            }
            val sc = SbFilmScene(storyboard = File("s.png"), zdroj = SbZdroj.SCENAR, scenar = text,
                panely = SbScenar.doplnPanely(plan.panely, s), hlasy = plan.hlasy, vzhled = plan.vzhled, kontinuita = s.kontinuita)
            val id = SbFilmPrepis.idMluvcich(sc.panely)
            if (id.values.toSet().size != id.size) chyba("dvě postavy se stejnou značkou $id")
            val vse = sc.useky.mapIndexed { k, u ->
                SbFilmPrepis.hlidka(1, u, k, sc.useky.size, true, id, SbFilmPrepis.jazykFilmu(sc),
                    sc.hlasy, predchozi = sc.useky.getOrNull(k - 1)?.panely?.lastOrNull(), vzhled = sc.vzhled, kontinuita = sc.kontinuita)
            }.joinToString("\n")
            val vZadani = Regex("""<d>\[[^\]]+]\s*(.*?)</d>""").findAll(vse).map { norm(it.groupValues[1]) }.toList()
            chtel.forEach { (_, _, t) -> if (vZadani.none { it == t }) chyba("v zadání pro H3 chybí „$t“") }
        }
        File(File("build/sbscenar").also { it.mkdirs() }, "korpus_formaty.txt")
            .writeText(log.toString() + "\nCHYBY (${chyby.size}):\n" + chyby.joinToString("\n"))
        assertTrue("${chyby.size} chyb:\n" + chyby.joinToString("\n"), chyby.isEmpty())
    }
}
