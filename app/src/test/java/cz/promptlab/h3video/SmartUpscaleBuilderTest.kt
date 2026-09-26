package cz.promptlab.h3video

import cz.promptlab.h3video.comfy.SmartUpscaleBuilder
import cz.promptlab.h3video.comfy.Stage
import cz.promptlab.h3video.data.UpscaleMetoda
import cz.promptlab.h3video.data.UpscaleScene
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Chytré zvětšení (Smart Upscaler) na kartě Zvětšit — od 4.57. */
class SmartUpscaleBuilderTest {

    private val sablona = File("src/main/res/raw/workflow_smart_upscale.json").readText()

    private fun JSONObject.ins(id: String) = getJSONObject(id).getJSONObject("inputs")

    @Test fun `dosadi fotku, zvetseni a seed`() {
        val s = UpscaleScene(metoda = UpscaleMetoda.CHYTRE, chytreNasobek = 3f)
        val g = SmartUpscaleBuilder.build(sablona, s, 42L, listOf("h3app/foto.png"))
        assertEquals("h3app/foto.png", g.ins(SmartUpscaleBuilder.N_IMAGE).getString("image"))
        assertEquals(3.0, g.ins(SmartUpscaleBuilder.N_PLANOVAC).getDouble("scale_factor"), 1e-9)
        assertEquals(42L, g.ins(SmartUpscaleBuilder.N_DIREKTOR).getLong("base_seed"))
        File("build/smart").mkdirs(); File("build/smart/graf.json").writeText(g.toString(1))
    }

    @Test fun `predloha je pripravena pro tento server`() {
        val g = JSONObject(sablona)
        // nvfp4 umí jen Blackwell; na serveru je bf16
        assertEquals("z_image_turbo_bf16.safetensors", g.ins("1205").getString("unet_name"))
        assertEquals("ae.sft", g.ins("1199").getString("vae_name"))
        // žádné uzly jen pro editor a žádný přepínač generátorů
        val tridy = g.keys().asSequence().map { g.getJSONObject(it).getString("class_type") }.toSet()
        assertFalse(tridy.any { it in setOf("SetNode", "GetNode", "Note", "SmartModelEngineSwitch") })
        assertTrue(SmartUpscaleBuilder.jeChytre(tridy.associateWith { it }.mapKeys { it.key }))
        // bez uloženého zadání či fotky z autorova vzoru
        assertEquals("", g.ins("1").getString("image"))
    }

    @Test fun `faze pruchodu nejdou zpet`() {
        val poradi = listOf(
            "SmartUpscaledTilePlanner", "SmartCachedTextGenerate", "UNETLoader",
            "KSampler", "SmartTileFinalizer",
        ).map { SmartUpscaleBuilder.rangeForClass(it)!!.first }
        assertEquals(poradi.sorted(), poradi)
        assertEquals(Stage.ENCODING, SmartUpscaleBuilder.stageForClass("SmartCachedTilePromptGenerator"))
        assertFalse(SmartUpscaleBuilder.reportsSteps("KSampler"))
    }
}
