package cz.promptlab.h3video

import cz.promptlab.h3video.comfy.SbFilmBuilder
import cz.promptlab.h3video.data.LongMmPomer
import cz.promptlab.h3video.data.Pozornost
import cz.promptlab.h3video.data.SbDialogy
import cz.promptlab.h3video.data.SbFilmPrepis
import cz.promptlab.h3video.data.SbFilmScene
import cz.promptlab.h3video.data.SbPanel
import cz.promptlab.h3video.data.SbRozliseni
import cz.promptlab.h3video.data.VoiceSource
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Pomocník pro ruční ověřovací běh filmu s dialogy (5.71): ze zadaného plánu (panely, nahrávky,
 * výstup přepisovače bez zvukových doplňků) postaví graf přesně kódem appky — planuj,
 * doplnPrompt, buildFilm, doplnGraf. Běží jen s proměnnými prostředí SB_OVERENI_VSTUP=<json>
 * a SB_OVERENI_DO=<složka>; vstup ani prompty v repu nejsou.
 */
class SbOvereniGrafTest {
    @Test
    fun graf() {
        val vstupCesta = System.getenv("SB_OVERENI_VSTUP")
        val cil = System.getenv("SB_OVERENI_DO")
        assumeTrue(vstupCesta != null && cil != null)
        val v = JSONObject(File(vstupCesta!!).readText())
        val pj = v.getJSONArray("panely")
        val panely = (0 until pj.length()).map { i ->
            val p = pj.getJSONObject(i)
            SbPanel(p.getInt("cislo"), p.getString("popis"), p.getString("typ"), p.getString("kamera"), p.getDouble("sekundy"),
                repliky = p.getString("repliky"))
        }
        val nj = v.getJSONArray("nahravky")
        val hlasy = mutableMapOf<String, String>()
        val nahravky = mutableMapOf<String, String>()
        val soubory = mutableMapOf<File, String>()
        for (i in 0 until nj.length()) {
            val n = nj.getJSONObject(i)
            val kdo = n.getString("kdo")
            val hlas = VoiceSource.Library("hlas-$kdo", kdo)
            hlasy[kdo] = SbDialogy.zakoduj(hlas)
            val f = File(n.getString("cesta"))
            soubory[f] = n.getString("soubor")
            val index = SbFilmPrepis.repliky(panely.first { it.cislo == n.getInt("panel") }.repliky).indexOfFirst { it.second == n.getString("text") }
            nahravky[SbDialogy.klic(n.getInt("panel"), index)] =
                SbDialogy.zakoduj(SbDialogy.Nahravka(f, n.getString("text"), hlas.klic, n.getDouble("delka"), n.getDouble("od")))
        }
        val s = SbFilmScene(
            panely = panely, dialogyHiggs = true, hlasyMluvcich = hlasy, nahravky = nahravky,
            rozliseni = SbRozliseni.valueOf(v.getString("rozliseni")),
            pomer = LongMmPomer.values().first { it.kod == v.getString("pomer") },
        )
        val useky = s.useky
        val stopy = SbDialogy.planuj(s, useky)
        // Rekonstrukce: bez předpony musí časy sedět na původní stopy (délky řeči jsou z délky souboru, ±0,05 s).
        val puvodni = v.getJSONArray("puvodniStarty")
        val bezPredpony = SbDialogy.planuj(s, useky, predpona = { 0.0 }).flatMap { it.umisteni }
        bezPredpony.forEachIndexed { i, u -> assertEquals("replika $i", puvodni.getDouble(i), u.startS, 0.05) }

        val ids = SbFilmPrepis.idMluvcich(s.panely)
        val prompty = v.getJSONArray("prompty")
        val zadani = useky.indices.map { k -> SbDialogy.doplnPrompt(prompty.getString(k), stopy.filter { it.usek == k }, ids) }
        val wf = SbFilmBuilder.buildFilm(s, useky, zadani, listOf(v.getString("obrazek")), v.getString("pomer"), v.getLong("seed"),
            pozornost = Pozornost.KITCHEN, shiftZvuk = v.getDouble("shiftZvuk"))
        val g = SbDialogy.doplnGraf(wf, stopy, soubory, SbFilmBuilder.N_KONTEXT, SbFilmBuilder.N_MEDIA)
        File(cil).mkdirs()
        File(cil, "graf.json").writeText(g.toString(1))
        val plan = JSONObject().put("useky", JSONArray(useky.map { it.sekundy })).put("stopy", JSONArray(stopy.map { st ->
            JSONObject().put("usek", st.usek).put("cislo", st.cislo).put("predpona", st.predponaS).put("delka", st.delkaStopyS)
                .put("repliky", JSONArray(st.umisteni.map { u ->
                    JSONObject().put("kdo", u.kdo).put("text", u.text).put("start", u.startS).put("delka", u.delkaS).put("soubor", u.soubor.name)
                }))
        })).put("zaberu", JSONArray(useky.map { u -> JSONArray(u.panely.map { it.sekundy }) }))
        File(cil, "plan.json").writeText(plan.toString(1))
    }
}
