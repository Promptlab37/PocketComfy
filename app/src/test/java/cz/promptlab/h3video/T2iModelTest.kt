package cz.promptlab.h3video

import cz.promptlab.h3video.comfy.Stage
import cz.promptlab.h3video.comfy.T2iModel
import cz.promptlab.h3video.comfy.ZImageBuilder
import cz.promptlab.h3video.data.Aspect
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Karta Obrázek umí od 3.02 pět modelů. Tři jedou na šabloně Z-Image,
 * dva (FLUX.2 Klein a ERNIE) na vlastní předloze. Testy hlídají, že se
 * i u nich dosazuje jen zadání, rozměry a seed — a že staré uložené
 * hodnoty z verzí do 3.01 se pořád čtou správně.
 */
class T2iModelTest {

    private val zimage: String = File("src/main/res/raw/workflow_zimage_t2i.json").readText()
    private val klein: String = File("src/main/res/raw/workflow_flux2_klein_t2i.json").readText()
    private val ernie: String = File("src/main/res/raw/workflow_ernie_t2i.json").readText()

    private fun JSONObject.inputs(node: String): JSONObject =
        getJSONObject(node).getJSONObject("inputs")

    @Test
    fun `stare ulozene hodnoty se ctou dal`() {
        assertEquals(T2iModel.TURBO, T2iModel.zId(""))
        assertEquals(T2iModel.PHOTOREAL, T2iModel.zId(ZImageBuilder.NSFW_MODEL_FILE))
        assertEquals(T2iModel.KLEIN, T2iModel.zId("klein"))
        // Nesmysl v nastavení nesmí appku shodit — spadne se na Turbo.
        assertEquals(T2iModel.TURBO, T2iModel.zId("neznamy_model"))
        T2iModel.entries.forEach { assertEquals(it, T2iModel.zId(it.id)) }
    }

    @Test
    fun `jen Turbo snese odvazanou LoRA`() {
        assertTrue(T2iModel.TURBO.zRodinyZImage)
        assertTrue(T2iModel.BASE.zRodinyZImage)
        assertFalse(T2iModel.KLEIN.zRodinyZImage)
        assertFalse(T2iModel.ERNIE.zRodinyZImage)
    }

    @Test
    fun `Base meni model, kroky a cfg, zbytek predlohy neche`() {
        val wf = ZImageBuilder.build(zimage, "x", Aspect.SQUARE_1_1, 7L, model = "base")
        val u = wf.getJSONObject(ZImageBuilder.N_UNET)
        // Base je safetensors, takže loader zůstává UNETLoader.
        assertEquals("UNETLoader", u.getString("class_type"))
        assertEquals(ZImageBuilder.BASE_MODEL_FILE, u.getJSONObject("inputs").getString("unet_name"))
        val s = wf.inputs(ZImageBuilder.N_SAMPLER)
        assertEquals(T2iModel.BASE.kroky, s.getInt("steps"))
        // Nedestilovaný model s cfg 1 nevede vůbec — musí se zvednout.
        assertEquals(ZImageBuilder.BASE_CFG, s.getDouble("cfg"), 0.001)
        assertEquals("res_multistep", s.getString("sampler_name"))
        assertEquals(3.0, wf.inputs(ZImageBuilder.N_SHIFT).getDouble("shift"), 0.001)
        assertEquals(7L, s.getLong("seed"))
    }

    @Test
    fun `Klein dosadi zadani, rozmery i plan kroku a seed`() {
        val wf = ZImageBuilder.build(klein, "kocka", Aspect.LANDSCAPE_16_9, 42L, model = "klein")
        assertEquals("kocka", wf.inputs(ZImageBuilder.N_F2_TEXT).getString("text"))
        assertEquals(1344, wf.inputs(ZImageBuilder.N_F2_LATENT).getInt("width"))
        assertEquals(768, wf.inputs(ZImageBuilder.N_F2_LATENT).getInt("height"))
        // Flux2Scheduler si z rozměrů počítá délku sekvence — musí sedět s latentem.
        assertEquals(1344, wf.inputs(ZImageBuilder.N_F2_KROKY).getInt("width"))
        assertEquals(768, wf.inputs(ZImageBuilder.N_F2_KROKY).getInt("height"))
        assertEquals(42L, wf.inputs(ZImageBuilder.N_F2_NOISE).getLong("noise_seed"))
        // Kroky a cfg zůstávají z předlohy.
        assertEquals(T2iModel.KLEIN.kroky, wf.inputs(ZImageBuilder.N_F2_KROKY).getInt("steps"))
        assertEquals(1.0, wf.inputs("43").getDouble("cfg"), 0.001)
        assertEquals("euler", wf.inputs("41").getString("sampler_name"))
    }

    @Test
    fun `Klein seed nepretece pres ctyri miliardy`() {
        // RandomNoise bere unsigned 64bit, ale seed z appky chodí i v bilionech;
        // ořez drží hodnotu v rozsahu, který uzel spolehlivě přijme.
        val wf = ZImageBuilder.build(klein, "x", Aspect.SQUARE_1_1, 999_999_999_999L, model = "klein")
        val seed = wf.inputs(ZImageBuilder.N_F2_NOISE).getLong("noise_seed")
        assertTrue("seed $seed je mimo rozsah", seed in 0..0xFFFF_FFFFL)
    }

    @Test
    fun `ERNIE dosadi zadani, rozmery a seed do KSampleru`() {
        val wf = ZImageBuilder.build(ernie, "pes", Aspect.PORTRAIT_9_16, 5L, model = "ernie")
        assertEquals("pes", wf.inputs(ZImageBuilder.N_F2_TEXT).getString("text"))
        assertEquals(768, wf.inputs(ZImageBuilder.N_F2_LATENT).getInt("width"))
        assertEquals(1344, wf.inputs(ZImageBuilder.N_F2_LATENT).getInt("height"))
        val s = wf.inputs(ZImageBuilder.N_F2_KROKY)
        assertEquals(5L, s.getLong("seed"))
        assertEquals(T2iModel.ERNIE.kroky, s.getInt("steps"))
        assertEquals(1.0, s.getDouble("cfg"), 0.001)
    }

    /**
     * ERNIE i Klein stojí na architektuře FLUX.2 — 16kanálový latent
     * z `EmptySD3LatentImage` by jim nesedl. Kdyby to někdo v předloze
     * přehodil, tenhle test to chytne dřív než server.
     */
    @Test
    fun `obe predlohy FLUX2 pouzivaji spravny latent a VAE`() {
        listOf(klein, ernie).forEach { raw ->
            val wf = JSONObject(raw)
            assertEquals(
                "EmptyFlux2LatentImage",
                wf.getJSONObject(ZImageBuilder.N_F2_LATENT).getString("class_type")
            )
            assertEquals(
                "flux2-vae.safetensors",
                wf.inputs(ZImageBuilder.N_F2_VAE).getString("vae_name")
            )
            assertEquals("flux2", wf.inputs(ZImageBuilder.N_F2_CLIP).getString("type"))
        }
    }

    @Test
    fun `kroky pro ukazatel prubehu sedi s predlohou`() {
        assertEquals(8, ZImageBuilder.stepsFor(""))
        assertEquals(12, ZImageBuilder.stepsFor(ZImageBuilder.NSFW_MODEL_FILE))
        assertEquals(30, ZImageBuilder.stepsFor("base"))
        assertEquals(4, ZImageBuilder.stepsFor("klein"))
        assertEquals(9, ZImageBuilder.stepsFor("ernie"))
    }

    @Test
    fun `faze pokryvaji i uzly rodiny FLUX2`() {
        assertEquals(Stage.SAMPLING, ZImageBuilder.stageForClass("SamplerCustomAdvanced"))
        assertEquals(Stage.ENCODING, ZImageBuilder.stageForClass("Flux2Scheduler"))
        assertEquals(Stage.ENCODING, ZImageBuilder.stageForClass("EmptyFlux2LatentImage"))
        assertEquals(Stage.MUXING, ZImageBuilder.stageForClass("SaveImage"))
        assertTrue(ZImageBuilder.reportsSteps("SamplerCustomAdvanced"))
    }

    @Test
    fun `zapojeni obou novych predloh sedi`() {
        listOf(klein, ernie).forEach { raw ->
            val wf = JSONObject(raw)
            val ids = wf.keys().asSequence().toSet()
            wf.keys().forEach { id ->
                val ins = wf.getJSONObject(id).getJSONObject("inputs")
                ins.keys().forEach { key ->
                    val v = ins.opt(key)
                    if (v is org.json.JSONArray && v.length() == 2 && v.opt(0) is String) {
                        assertTrue("$id.$key ukazuje na ${v.getString(0)}", v.getString(0) in ids)
                    }
                }
            }
        }
    }
}
