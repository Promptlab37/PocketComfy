package cz.promptlab.h3video

import cz.promptlab.h3video.comfy.Ltx25Builder
import cz.promptlab.h3video.comfy.Stage
import cz.promptlab.h3video.data.LtxPomer
import cz.promptlab.h3video.data.LtxScene
import cz.promptlab.h3video.data.ltxProblem
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Karta **Video ze zvuku** (LTX 2.5).
 *
 * Celý smysl karty je v tom, že se délka nezadává — počet snímků si graf
 * spočítá z délky nahraného zvuku. Testy proto hlídají hlavně to, co by tenhle
 * mechanismus tiše rozbilo: dosazené číslo do délky latentu, useknutý zvuk
 * (`duration` jinak než 0) nebo odpojený výpočet `fps × délka + 1`.
 */
class Ltx25BuilderTest {

    private val sablona: String = File("src/main/res/raw/workflow_ltx25_audio.json").readText()

    private fun JSONObject.inputs(node: String): JSONObject =
        getJSONObject(node).getJSONObject("inputs")

    private fun scene() = LtxScene(
        obrazek = File("neexistuje.png"),
        zvuk = File("neexistuje.wav"),
        zvukSekund = 8.4f,
        popis = "A news anchor speaks to the camera in a dark blue studio",
        pomer = LtxPomer.NA_VYSKU,
    )

    @Test
    fun `fotka a zvuk se dosadi do nacitacu`() {
        val wf = Ltx25Builder.build(sablona, scene(), 11L, "prvni.png", "rec.wav")

        assertEquals("prvni.png", wf.inputs(Ltx25Builder.N_OBRAZEK).getString("image"))
        assertEquals("rec.wav", wf.inputs(Ltx25Builder.N_ZVUK).getString("audio"))
    }

    @Test
    fun `zvuk se nesmi orezat`() {
        val wf = Ltx25Builder.build(sablona, scene(), 11L, "prvni.png", "rec.wav")

        // `duration` je u VHS_LoadAudioUpload STROP, ne délka. Cokoli jiného
        // než 0 by řeč utnulo — a přesně kvůli tomu tahle karta vznikla.
        assertEquals(0.0, wf.inputs(Ltx25Builder.N_ZVUK).getDouble("duration"), 0.0)
        assertEquals(0.0, wf.inputs(Ltx25Builder.N_ZVUK).getDouble("start_time"), 0.0)
    }

    @Test
    fun `delku latentu pocita graf ze zvuku, ne appka`() {
        val wf = Ltx25Builder.build(sablona, scene(), 11L, "prvni.png", "rec.wav")

        // EmptyLTXVLatentVideo.length musí zůstat ODKAZEM na výpočet, ne číslem.
        val delka = wf.inputs("462").get("length")
        assertTrue("délka latentu se dosadila jako číslo: $delka", delka is org.json.JSONArray)
        assertEquals(Ltx25Builder.N_DELKA, (delka as org.json.JSONArray).getString(0))

        // A ten výpočet je pořád `fps × délka_zvuku + 1`.
        assertEquals("a * b + 1", wf.inputs(Ltx25Builder.N_DELKA).getString("expression"))
        // Druhý činitel je druhý výstup načítače zvuku, tedy jeho délka.
        val b = wf.inputs(Ltx25Builder.N_DELKA).getJSONArray("values.b")
        assertEquals(Ltx25Builder.N_ZVUK, b.getString(0))
        assertEquals(1, b.getInt(1))
    }

    @Test
    fun `popis a pomer stran jdou do grafu`() {
        val wf = Ltx25Builder.build(sablona, scene(), 11L, "prvni.png", "rec.wav")

        assertTrue(wf.inputs(Ltx25Builder.N_POPIS).getString("text").startsWith("A news anchor"))
        assertEquals(
            LtxPomer.NA_VYSKU.hodnota,
            wf.inputs(Ltx25Builder.N_ROZLISENI).getString("aspect_ratio"),
        )
        assertEquals(LtxScene.FPS, wf.inputs(Ltx25Builder.N_FPS).getInt("value"))
    }

    @Test
    fun `oba pruchody maji seed odvozeny z jednoho cisla`() {
        val wf = Ltx25Builder.build(sablona, scene(), 12345L, "prvni.png", "rec.wav")

        assertEquals(12345L, wf.inputs(Ltx25Builder.N_NOISE_1).getLong("noise_seed"))
        assertEquals(
            Ltx25Builder.druhySeed(12345L),
            wf.inputs(Ltx25Builder.N_NOISE_2).getLong("noise_seed"),
        )
        // Stejné zadání = stejný běh; jinak by se povedený výsledek nedal zopakovat.
        val znovu = Ltx25Builder.build(sablona, scene(), 12345L, "prvni.png", "rec.wav")
        assertEquals(
            wf.inputs(Ltx25Builder.N_NOISE_2).getLong("noise_seed"),
            znovu.inputs(Ltx25Builder.N_NOISE_2).getLong("noise_seed"),
        )
    }

    @Test
    fun `predloha nedosazuje kroky ani sigmy`() {
        val puvodni = JSONObject(sablona)
        val wf = Ltx25Builder.build(sablona, scene(), 11L, "prvni.png", "rec.wav")

        assertEquals(
            puvodni.inputs("434").getString("sigmas"), wf.inputs("434").getString("sigmas"),
        )
        assertEquals(
            puvodni.inputs("444").getString("sigmas"), wf.inputs("444").getString("sigmas"),
        )
    }

    @Test
    fun `karta rekne, co chybi`() {
        val fotka = File.createTempFile("ltx", ".png").also { it.deleteOnExit() }
        val zvuk = File.createTempFile("ltx", ".wav").also { it.deleteOnExit() }

        assertNotNull(ltxProblem(LtxScene()))
        // Každý chybějící kus má vlastní hlášku — ať člověk ví, co doplnit.
        assertFalse(ltxProblem(LtxScene()) == ltxProblem(LtxScene(obrazek = fotka)))
        assertNotNull(ltxProblem(LtxScene(obrazek = fotka, zvuk = zvuk)))
        assertNull(ltxProblem(LtxScene(obrazek = fotka, zvuk = zvuk, popis = "a cat")))
        // Neexistující soubor se počítá jako nevybraný (mohl zmizet z cache).
        assertNotNull(
            ltxProblem(LtxScene(obrazek = File("neexistuje.png"), zvuk = zvuk, popis = "a cat"))
        )
    }

    @Test
    fun `fáze behu sedi na tridy uzlu`() {
        assertEquals(Stage.MODELS, Ltx25Builder.stageForClass("UNETLoader"))
        assertEquals(Stage.REFERENCES, Ltx25Builder.stageForClass("VHS_LoadAudioUpload"))
        assertEquals(Stage.SAMPLING, Ltx25Builder.stageForClass("SamplerCustomAdvanced"))
        assertEquals(Stage.MUXING, Ltx25Builder.stageForClass("SaveVideo"))
        assertTrue(Ltx25Builder.reportsSteps("SamplerCustomAdvanced"))
        assertFalse(Ltx25Builder.reportsSteps("VAEDecodeTiled"))
    }

    @Test
    fun `snimku podle delky zvuku`() {
        assertEquals(0, LtxScene().snimku)
        assertEquals(211, LtxScene(zvukSekund = 8.4f).snimku)
    }

    @Test
    fun `sablona nenese zadani predchoziho behu`() {
        val puvodni = JSONObject(sablona)
        assertEquals("", puvodni.inputs(Ltx25Builder.N_OBRAZEK).getString("image"))
        assertEquals("", puvodni.inputs(Ltx25Builder.N_ZVUK).getString("audio"))
        assertEquals("", puvodni.inputs(Ltx25Builder.N_POPIS).getString("text"))
    }

    @Test
    fun `nulova delka zvuku neni v poradku`() {
        assertNull(LtxScene().zvuk)
        assertEquals(0f, LtxScene().zvukSekund, 0f)
    }
}
