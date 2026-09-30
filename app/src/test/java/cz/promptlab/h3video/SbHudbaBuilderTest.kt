package cz.promptlab.h3video

import cz.promptlab.h3video.comfy.SbHudbaBuilder
import cz.promptlab.h3video.data.SbFilmPlan
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Podkresová hudba k filmu ze storyboardu (5.13): YuE2 + instrumentální LoRA, smíchání pod dialogy. */
class SbHudbaBuilderTest {

    private fun g(delka: Double = 24.2, db: Int = -14) =
        SbHudbaBuilder.build(SbHudbaBuilder.zVystupu("PocketSbFilm_00013_.mp4", ""), "cinematic, soft piano", delka, db, 7L)

    private fun JSONObject.vstupy(id: String) = getJSONObject(id).getJSONObject("inputs")

    @Test
    fun `graf hudby`() {
        val w = g()
        // Odkazy vedou na existující uzly.
        for (id in w.keys()) {
            val ins = w.vstupy(id)
            for (k in ins.keys()) {
                val v = ins.get(k)
                if (v is JSONArray && v.length() == 2 && v.get(0) is String) assertTrue("$id.$k", w.has(v.getString(0)))
            }
        }
        assertEquals("PocketSbFilm_00013_.mp4 [output]", w.vstupy(SbHudbaBuilder.N_VIDEO).getString("file"))
        assertEquals("sub/a.mp4 [output]", SbHudbaBuilder.zVystupu("a.mp4", "sub"))
        // LoRA v CLIP slotu a oba YuE2 uzly berou CLIP z LoRA, mode full.
        val lora = w.vstupy(SbHudbaBuilder.N_LORA)
        assertEquals("yue2\\ar_lora_inst_v3abc_comfyui.safetensors", lora.getString("lora_name"))
        assertEquals(1.0, lora.getDouble("strength_clip"), 1e-9)
        for (n in listOf(SbHudbaBuilder.N_ABC, SbHudbaBuilder.N_MUSIC)) {
            assertEquals(SbHudbaBuilder.N_LORA, w.vstupy(n).getJSONArray("clip").getString(0))
            assertEquals(1, w.vstupy(n).getJSONArray("clip").getInt(1))
            assertEquals("full", w.vstupy(n).getString("mode"))
        }
        assertEquals(44.2, w.vstupy(SbHudbaBuilder.N_MUSIC).getDouble("max_duration"), 1e-9)
        // Časované značky částí přes konec filmu, jedna na řádek, nic jiného.
        val casti = w.vstupy(SbHudbaBuilder.N_MUSIC).getString("lyrics")
        assertEquals("[intro 0:00-0:05]\n[verse 0:05-0:19]\n[chorus 0:19-0:29]\n[outro 0:29-0:34]", casti)
        // Tělo + 8 čtvrtsekund zeslabení = délka filmu.
        assertEquals(22.2, w.vstupy(SbHudbaBuilder.N_TELO).getDouble("duration"), 1e-9)
        val kusy = (0 until 8).map { w.vstupy((40 + it).toString()) }
        assertEquals(2.0, kusy.sumOf { it.getDouble("duration") }, 1e-9)
        assertEquals(-2.0, kusy.first().getDouble("start_index"), 1e-9)
        assertEquals(-0.25, kusy.last().getDouble("start_index"), 1e-9)
        // Mix: dialogy první (určují délku), hudba ztišená.
        assertEquals(SbHudbaBuilder.N_CASTI, w.vstupy(SbHudbaBuilder.N_MIX).getJSONArray("audio1").getString(0))
        assertEquals(-14, w.vstupy(SbHudbaBuilder.N_HLASITOST).getInt("volume"))
        assertEquals(-24, g(db = -60).vstupy(SbHudbaBuilder.N_HLASITOST).getInt("volume"))
        assertEquals(-6, g(db = 0).vstupy(SbHudbaBuilder.N_HLASITOST).getInt("volume"))
        assertEquals(SbHudbaBuilder.PREFIX, w.vstupy(SbHudbaBuilder.N_ULOZ).getString("filename_prefix"))
        File(File("build/sbhudba").also { it.mkdirs() }, "hudba.json").writeText(w.toString(2))
    }

    @Test
    fun `styl hudby z cteni`() {
        val c = SbFilmPlan.precti(
            "TITLE: none | TOTAL: none | SHOTS: none | GRID: 2x4 | VOICES: none | LOOKS: none | " +
                "MUSIC: playful comedy, pizzicato strings, light percussion, 110 BPM PANEL 1 | none | wide | static | x | none"
        )
        assertEquals("playful comedy, pizzicato strings, light percussion, 110 BPM", c.hudbaStyl)
        assertEquals(c.hudbaStyl, SbFilmPlan.naplanuj(c).hudbaStyl)
        assertTrue(SbFilmPlan.OTAZKA_CTENI.contains("MUSIC:"))
        assertTrue(SbFilmPlan.SYSTEM_NAVRH.contains("MUSIC:"))
    }

    private fun film(vararg upravy: (cz.promptlab.h3video.data.VideoItem) -> cz.promptlab.h3video.data.VideoItem) =
        upravy.fold(cz.promptlab.h3video.data.VideoItem(
            id = "a", fileName = "a.mp4", prompt = "p", createdAt = 1L, seconds = 20f, resolution = "",
            seed = 1L, twoImages = false, mode = "SBFILM", filmNaServeru = "PocketSbFilm_00013_.mp4",
        )) { acc, f -> f(acc) }

    /** 5.13: hudba jen k filmu ze storyboardu, který leží na serveru a hudbu ještě nemá. */
    @Test
    fun `kdy jde pridat hudbu`() {
        assertTrue(cz.promptlab.h3video.data.jdePridatHudbu(film(), true))
        assertTrue(!cz.promptlab.h3video.data.jdePridatHudbu(film(), false))
        assertTrue(!cz.promptlab.h3video.data.jdePridatHudbu(film({ it.copy(sHudbou = true) }), true))
        assertTrue(!cz.promptlab.h3video.data.jdePridatHudbu(film({ it.copy(filmNaServeru = "") }), true))
        assertTrue(!cz.promptlab.h3video.data.jdePridatHudbu(film({ it.copy(mode = "ALLINONE") }), true))
        // Délka: změřená má přednost před plánovanou.
        assertEquals(20.0, cz.promptlab.h3video.data.delkaProHudbu(film()), 1e-6)
        assertEquals(24.125, cz.promptlab.h3video.data.delkaProHudbu(film({ it.copy(filmSekundy = 24.125f) })), 1e-6)
    }
}
