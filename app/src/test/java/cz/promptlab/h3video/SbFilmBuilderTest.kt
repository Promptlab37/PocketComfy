package cz.promptlab.h3video

import cz.promptlab.h3video.comfy.H3RefWriteBuilder
import cz.promptlab.h3video.comfy.SbFilmBuilder
import cz.promptlab.h3video.data.LongMmPomer
import cz.promptlab.h3video.data.SbFilmPlan
import cz.promptlab.h3video.data.SbFilmPrepis
import cz.promptlab.h3video.data.SbFilmScene
import cz.promptlab.h3video.data.SbModel
import cz.promptlab.h3video.comfy.Stage
import cz.promptlab.h3video.data.SbPanel
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Grafy Filmu ze storyboardu. Test je i vypíše do `build/sbfilm-grafy/`, kde je
 * prověří nástroj proti `/object_info` (jména uzlů, nabídky, soubory na disku).
 */
class SbFilmBuilderTest {

    private val panely = listOf(4.0, 3.0, 4.0, 3.0, 4.0, 5.0, 4.0, 3.0).mapIndexed { i, s ->
        SbPanel(i + 1, "panel ${i + 1}", "wide", "static", s)
    }
    private val scene = SbFilmScene(panely = panely, pomer = LongMmPomer.NASIRKU)
    private val useky = SbFilmPlan.rozdel(panely)
    private val zadani = useky.mapIndexed { k, _ -> "[Shot 1] part $k <Picture 1> <Picture 2>. [Shot 2] At 00:03.000, x" }

    private fun graf() = SbFilmBuilder.buildFilm(scene, useky, zadani, listOf("sb.png", "p1.png"), "16:9", 7L)

    private fun JSONObject.vstupy(id: String) = getJSONObject(id).getJSONObject("inputs")

    @Test
    fun `retez useku - setup jen u prvniho, dalsi na predchozim, zadani v override`() {
        val wf = graf()
        val kroky = wf.keys().asSequence().filter { wf.getJSONObject(it).getString("class_type") == "MiniMaxH3EasySegmentStep_SatoDive" }
            .map { it.toInt() }.sorted().toList()
        assertEquals(useky.size, kroky.size)
        assertTrue(wf.vstupy(kroky[0].toString()).has("sample_setup"))
        for (i in 1 until kroky.size) {
            val v = wf.vstupy(kroky[i].toString())
            assertFalse(v.has("sample_setup"))
            assertEquals(kroky[i - 1].toString(), v.getJSONArray("previous_segment").getString(0))
            assertEquals(zadani[i], v.getString("prompt_override"))
        }
        assertEquals(kroky.last().toString(),
            wf.vstupy(SbFilmBuilder.N_COLLECT).getJSONArray("final_segment").getString(0))
    }

    @Test
    fun `plan urcuje delky - celkem a znacky zacatku useku`() {
        val k = graf().vstupy(SbFilmBuilder.N_KONTEXT)
        assertEquals(30.0, k.getDouble("seconds"), 0.001)
        val casti = k.getString("prompt").split("\n---\n")
        assertEquals(useky.size, casti.size)
        assertTrue(casti[1].startsWith("[Shot 1] At " + SbFilmPlan.casH3(useky[0].sekundy)))
        // Každý úsek označí storyboard i postavu — jinak by je nedostal.
        assertTrue(casti.all { it.contains("<Picture 1>") && it.contains("<Picture 2>") })
        // native_guide přehrával na švu 22 snímků znovu (28. 9. 2026).
        assertEquals("latent_guide", k.getString("continuity_mode"))
    }

    @Test
    fun `storyboard je prvni obrazek v referencich`() {
        val wf = graf()
        val media = wf.vstupy(SbFilmBuilder.N_MEDIA)
        assertEquals(2, media.getInt("image_count"))
        assertEquals("sb.png", wf.vstupy(media.getJSONArray("image_1").getString(0)).getString("image"))
    }

    @Test
    fun `kroky se pocitaji pres cely film`() {
        val tridy = SbFilmBuilder.nodeClasses(graf())
        val useky = tridy.filterValues { it == "MiniMaxH3EasySegmentStep_SatoDive" }.keys.sortedBy { it.toInt() }
        assertEquals(0 to 24, SbFilmBuilder.globalniKrok(useky[0], tridy, 0, 8))
        assertEquals(11 to 24, SbFilmBuilder.globalniKrok(useky[1], tridy, 3, 8))
        assertEquals(24 to 24, SbFilmBuilder.globalniKrok(useky[2], tridy, 8, 8))
        // Uzel mimo úseky: beze změny.
        assertEquals(3 to 8, SbFilmBuilder.globalniKrok("999", tridy, 3, 8))
    }

    @Test
    fun `graf nema visici odkazy`() {
        val wf = graf()
        wf.keys().forEach { id ->
            val ins = wf.vstupy(id)
            ins.keys().forEach { k ->
                val v = ins.opt(k)
                if (v is JSONArray && v.length() == 2 && v.opt(0) is String) {
                    assertTrue("uzel $id → ${v.getString(0)}", wf.has(v.getString(0)))
                }
            }
        }
    }

    @Test
    fun `hlidka useku - zaznamy s casy od nuly a role obrazku`() {
        val h = SbFilmPrepis.hlidka(2, useky[1], 1, useky.size)
        assertTrue(h.contains("<Picture 1> is a storyboard reference"))
        assertTrue(h.contains("<Picture 2> show the characters"))
        assertTrue(h.contains("continues directly from the previous part"))
        assertTrue(h.contains("[Shot 1] storyboard panel ${useky[1].panely[0].cislo}"))
        if (useky[1].panely.size > 1) assertTrue(h.contains("[Shot 2] At " + SbFilmPlan.casH3(useky[1].panely[0].sekundy)))
        // Závorka pokynu na vlastním řádku (jinak ji model opíše).
        assertTrue(h.endsWith("\n]"))
    }

    @Test
    fun `cteni - otazka nahrazuje roli a vystup je popis`() {
        val wf = SbFilmBuilder.buildCteni("sb.png", H3RefWriteBuilder.CAPTIONER_ODVAZANY, 3L)
        assertEquals(SbFilmPlan.OTAZKA_CTENI, wf.vstupy(SbFilmBuilder.N_CTENI).getString("instruction"))
        assertEquals(1, wf.vstupy(SbFilmBuilder.N_CTENI_VYSTUP).getJSONArray("source").getInt(1))
    }

    @Test
    fun `vypis grafu pro kontrolu proti serveru`() {
        val dir = File("build/sbfilm-grafy").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        File(dir, "film.json").writeText(graf().toString(2))
        File(dir, "cteni.json").writeText(
            SbFilmBuilder.buildCteni("sb.png", H3RefWriteBuilder.CAPTIONER_ODVAZANY, 3L).toString(2),
        )
    }

    /** Všechny odkazy grafu vedou na uzly, které v grafu jsou. */
    private fun odkazyPlati(g: JSONObject) {
        for (id in g.keys()) {
            val vstupy = g.getJSONObject(id).getJSONObject("inputs")
            for (k in vstupy.keys()) {
                val v = vstupy.get(k)
                if (v is JSONArray && v.length() == 2 && v.get(0) is String)
                    assertTrue("$id.$k -> ${v.get(0)}", g.has(v.getString(0)))
            }
        }
    }

    private fun g(s: SbFilmScene) = SbFilmBuilder.buildFilm(s, useky, zadani, listOf("sb.png", "p1.png"), "16:9", 7L)

    /** 5.46: Turbo = ref2v 8step v1.0 (lightx2v) podle specifikace: 8 kroků, síla 1,0, shift 12/3. */
    @Test
    fun `turbo je ref2v 8step s osmi kroky a shiftem`() {
        val t = g(scene.copy(model = SbModel.TURBO, krokyKvalita = 25))
        odkazyPlati(t)
        assertEquals(8, t.vstupy(SbFilmBuilder.N_KROKY).getInt("steps"))
        assertEquals("simple", t.vstupy(SbFilmBuilder.N_KROKY).getString("scheduler"))
        assertEquals("minimax_h3_ref2v_turbo_8step_v1.0_768p_comfyui_bf16.safetensors",
            t.vstupy(SbFilmBuilder.N_LORA).getString("lora_name"))
        assertEquals(1.0, t.vstupy(SbFilmBuilder.N_LORA).getDouble("strength"), 1e-9)
        assertEquals(12.0, t.vstupy(SbFilmBuilder.N_SHIFT).getDouble("shift_video"), 1e-9)
        assertEquals(3.0, t.vstupy(SbFilmBuilder.N_SHIFT).getDouble("shift_audio"), 1e-9)
        assertEquals(SbFilmBuilder.N_LORA, t.vstupy(SbFilmBuilder.N_SHIFT).getJSONArray("model").getString(0))
        assertEquals(SbFilmBuilder.N_SHIFT, t.vstupy(SbFilmBuilder.N_KROKY).getJSONArray("model").getString(0))
        assertEquals(SbFilmBuilder.N_SHIFT, t.vstupy(SbFilmBuilder.N_SETUP).getJSONArray("model").getString(0))
        assertEquals(graf().toString(), t.toString())
        File(File("build/sbfilm-modely").also { it.mkdirs() }, "film_turbo.json").writeText(t.toString(2))
    }

    /** 5.07: Kvalita = plný model jako profil Kvalita v All in One, kroky podle volby. */
    @Test
    fun `kvalita je plny model se shiftem a volbou kroku`() {
        val k = g(scene.copy(model = SbModel.KVALITA))
        odkazyPlati(k)
        assertFalse(k.has(SbFilmBuilder.N_LORA))
        assertTrue(k.toString().contains("turbo").not())
        val sh = k.vstupy(SbFilmBuilder.N_SHIFT)
        assertEquals("MiniMaxH3SigmaShift", k.getJSONObject(SbFilmBuilder.N_SHIFT).getString("class_type"))
        assertEquals(12.191111, sh.getDouble("shift_video"), 1e-9)
        assertEquals(3.0, sh.getDouble("shift_audio"), 1e-9)
        assertEquals(SbFilmBuilder.N_POZORNOST, sh.getJSONArray("model").getString(0))
        // Plán kroků i úseky berou model ZA shiftem.
        assertEquals(SbFilmBuilder.N_SHIFT, k.vstupy(SbFilmBuilder.N_KROKY).getJSONArray("model").getString(0))
        assertEquals(SbFilmBuilder.N_SHIFT, k.vstupy(SbFilmBuilder.N_SETUP).getJSONArray("model").getString(0))
        assertEquals("beta", k.vstupy(SbFilmBuilder.N_KROKY).getString("scheduler"))
        assertEquals("euler", k.vstupy(SbFilmBuilder.N_SAMPLER).getString("sampler_name"))
        assertEquals(10, k.vstupy(SbFilmBuilder.N_KROKY).getInt("steps"))
        assertEquals(20, g(scene.copy(model = SbModel.KVALITA, krokyKvalita = 20)).vstupy(SbFilmBuilder.N_KROKY).getInt("steps"))
        // Mimo rozsah 10–30 se hodnota srovná.
        assertEquals(30, scene.copy(model = SbModel.KVALITA, krokyKvalita = 99).kroky)
        assertEquals(10, scene.copy(model = SbModel.KVALITA, krokyKvalita = 4).kroky)
        assertEquals(8, scene.copy(model = SbModel.TURBO, krokyKvalita = 20).kroky)
        assertEquals(SbModel.TURBO, SbFilmScene().model)
        assertEquals(10, SbFilmScene().krokyKvalita)
        assertEquals(Stage.MODELS, SbFilmBuilder.stageForClass("MiniMaxH3SigmaShift"))
        // Grafy pro kontrolu proti /object_info.
        val dir = File("build/sbfilm-modely").also { it.mkdirs() }
        File(dir, "film_kvalita.json").writeText(k.toString(2))
    }

    /** 5.08: 3 + 2 = navazující záběr sestavy 3 + 2 z Long MiniMax, beze změny receptu. */
    @Test
    fun `triplusdva jako navazani long minimax`() {
        val g = g(scene.copy(model = SbModel.TRIPLUSDVA, krokyKvalita = 25))
        odkazyPlati(g)
        val lora = g.vstupy(SbFilmBuilder.N_LORA)
        assertEquals(cz.promptlab.h3video.data.LongMmModel.TRIPLUSDVA.lora, lora.getString("lora_name"))
        assertEquals("h3\\TaoMate-H3-3step-ComfyUI.safetensors", lora.getString("lora_name"))
        assertEquals(1.0, lora.getDouble("strength"), 1e-9)
        assertEquals(SbFilmBuilder.N_POZORNOST, lora.getJSONArray("model").getString(0))
        val sh = g.vstupy(SbFilmBuilder.N_SHIFT)
        assertEquals(SbFilmBuilder.N_LORA, sh.getJSONArray("model").getString(0))
        assertEquals(12.0, sh.getDouble("shift_video"), 1e-9)
        assertEquals(3.0, sh.getDouble("shift_audio"), 1e-9)
        // Stejné jako LongMmBuilder (posun z karty 3 kroky).
        assertEquals(cz.promptlab.h3video.comfy.LongMmBuilder.SHIFT_VIDEO, sh.getDouble("shift_video"), 1e-9)
        assertEquals(SbFilmBuilder.N_SHIFT, g.vstupy(SbFilmBuilder.N_SETUP).getJSONArray("model").getString(0))
        val kroky = g.vstupy(SbFilmBuilder.N_KROKY)
        assertEquals(SbFilmBuilder.N_UNET, kroky.getJSONArray("model").getString(0))
        assertEquals("simple", kroky.getString("scheduler"))
        assertEquals(3, kroky.getInt("steps"))
        assertEquals(1.0, kroky.getDouble("denoise"), 1e-9)
        assertEquals("res_multistep", g.vstupy(SbFilmBuilder.N_SAMPLER).getString("sampler_name"))
        assertEquals(cz.promptlab.h3video.data.LongMmModel.TRIPLUSDVA.kroky, kroky.getInt("steps"))
        // Kroky patří k receptu, volba Kvality je neovlivní.
        assertEquals(3, scene.copy(model = SbModel.TRIPLUSDVA, krokyKvalita = 30).kroky)
        // Stored names: nové hodnoty jen na konec.
        assertEquals(listOf("TURBO", "KVALITA", "TRIPLUSDVA"), SbModel.entries.map { it.name })
        File(File("build/sbfilm-modely").also { it.mkdirs() }, "film_32.json").writeText(g.toString(2))
    }

    /** 5.41: rozlišení volí uživatel; 768p = přesný nativ přes Custom, 540p štítkem. */
    @Test
    fun `rozliseni 540p a 768p nativ`() {
        val k540 = g(scene.copy(rozliseni = cz.promptlab.h3video.data.SbRozliseni.R540)).vstupy(SbFilmBuilder.N_KONTEXT)
        assertEquals("540P", k540.getString("resolution"))
        assertEquals(false, k540.getString("aspect_ratio") == "Custom")
        val k768 = g(scene.copy(rozliseni = cz.promptlab.h3video.data.SbRozliseni.R768)).vstupy(SbFilmBuilder.N_KONTEXT)
        assertEquals("Custom", k768.getString("aspect_ratio"))
        assertEquals(1344 to 768, k768.getInt("width") to k768.getInt("height"))
    }
}
