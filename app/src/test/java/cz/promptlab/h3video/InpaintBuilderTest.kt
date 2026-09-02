package cz.promptlab.h3video

import cz.promptlab.h3video.comfy.InpaintBuilder
import cz.promptlab.h3video.comfy.Stage
import cz.promptlab.h3video.data.GenParams
import cz.promptlab.h3video.data.InpaintModel
import cz.promptlab.h3video.data.InpaintScene
import cz.promptlab.h3video.data.Mode
import cz.promptlab.h3video.data.inpaintProblem
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Karta **Domalovat** má dvě předlohy (FLUX.2 Klein a Flux Fill). Testy hlídají,
 * že se do nich dosazují JEN fotka, maska, zadání a seed — a že vyladěné
 * hodnoty (kroky, cfg, vedení, model, VAE, enkodér) zůstávají netknuté.
 */
class InpaintBuilderTest {

    private val klein: String =
        File("src/main/res/raw/workflow_inpaint_klein.json").readText()
    private val fill: String =
        File("src/main/res/raw/workflow_inpaint_fill.json").readText()

    private fun JSONObject.inputs(node: String): JSONObject =
        getJSONObject(node).getJSONObject("inputs")

    private fun bezVisicichOdkazu(wf: JSONObject) {
        wf.keys().asSequence().toList().forEach { id ->
            val ins = wf.getJSONObject(id).getJSONObject("inputs")
            ins.keys().asSequence().toList().forEach { k ->
                val v = ins.opt(k)
                if (v is JSONArray && v.length() == 2 && v.opt(0) is String) {
                    assertTrue("uzel $id → ${v.getString(0)}", wf.has(v.getString(0)))
                }
            }
        }
    }

    // -------------------------------------------------------------- Klein

    @Test
    fun `klein - dosadi se fotka, maska, zadani a seed`() {
        val wf = InpaintBuilder.build(
            klein, InpaintModel.KLEIN, "dřevěná lavička", 42L, listOf("foto.png", "maska.png")
        )
        assertEquals("foto.png", wf.inputs(InpaintBuilder.N_IMAGE).getString("image"))
        assertEquals("maska.png", wf.inputs(InpaintBuilder.N_MASK).getString("image"))
        // Klein dostává zadání zabalené do instrukce (viz vlastní test níž).
        assertTrue(wf.inputs(InpaintBuilder.N_TEXT).getString("text").contains("dřevěná lavička"))
        assertEquals(42L, wf.inputs(InpaintBuilder.N_NOISE).getLong("noise_seed"))
        bezVisicichOdkazu(wf)
    }

    @Test
    fun `klein - vyladene hodnoty predlohy zustavaji`() {
        val wf = InpaintBuilder.build(klein, InpaintModel.KLEIN, "x", 1L, listOf("a.png", "m.png"))
        assertEquals("flux-2-klein-9b.safetensors", wf.inputs("1").getString("unet_name"))
        assertEquals("qwen_3_8b_fp8mixed.safetensors", wf.inputs("2").getString("clip_name"))
        assertEquals("flux2", wf.inputs("2").getString("type"))
        assertEquals("flux2-vae.safetensors", wf.inputs("3").getString("vae_name"))
        assertEquals(4, wf.inputs("40").getInt("steps"))
        assertEquals(1.0, wf.inputs("43").getDouble("cfg"), 0.001)
        assertEquals("euler", wf.inputs("41").getString("sampler_name"))
        assertEquals(InpaintBuilder.KLEIN_STEPS, wf.inputs("40").getInt("steps"))
    }

    @Test
    fun `klein - maluje se jen pod maskou a vysledek se vlepi zpet`() {
        val wf = InpaintBuilder.build(klein, InpaintModel.KLEIN, "x", 1L, listOf("a.png", "m.png"))
        // maska: LoadImage → ImageToMask(red) → výřez
        assertEquals("ImageToMask", wf.getJSONObject("12").getString("class_type"))
        assertEquals("red", wf.inputs("12").getString("channel"))
        assertEquals(InpaintBuilder.N_MASK, wf.inputs("12").getJSONArray("image").getString(0))
        assertEquals("12", wf.inputs("20").getJSONArray("mask").getString(0))
        // šum jen pod maskou výřezu (jinak by se přepsal celý výřez)
        assertEquals("SetLatentNoiseMask", wf.getJSONObject("23").getString("class_type"))
        assertEquals("20", wf.inputs("23").getJSONArray("mask").getString(0))
        assertEquals(2, wf.inputs("23").getJSONArray("mask").getInt(1))
        assertEquals("23", wf.inputs("44").getJSONArray("latent_image").getString(0))
        // původní výřez jde modelu jako reference, ať naváže na okolí
        assertEquals("ReferenceLatent", wf.getJSONObject("31").getString("class_type"))
        assertEquals("22", wf.inputs("31").getJSONArray("latent").getString(0))
        // hotový kus se vlepí zpět přes stitcher z výřezu
        assertEquals("20", wf.inputs("51").getJSONArray("stitcher").getString(0))
        assertEquals("51", wf.inputs("60").getJSONArray("images").getString(0))
    }

    // ----------------------------------------------------------- Flux Fill

    @Test
    fun `fill - dosadi se fotka, maska, zadani a seed`() {
        val wf = InpaintBuilder.build(
            fill, InpaintModel.FILL, "cihlová zeď", 7L, listOf("foto.png", "maska.png")
        )
        assertEquals("foto.png", wf.inputs(InpaintBuilder.N_IMAGE).getString("image"))
        assertEquals("maska.png", wf.inputs(InpaintBuilder.N_MASK).getString("image"))
        assertEquals("cihlová zeď", wf.inputs(InpaintBuilder.N_TEXT).getString("text"))
        assertEquals(7L, wf.inputs(InpaintBuilder.N_SAMPLER).getLong("seed"))
        bezVisicichOdkazu(wf)
    }

    @Test
    fun `fill - vyladene hodnoty predlohy zustavaji`() {
        val wf = InpaintBuilder.build(fill, InpaintModel.FILL, "x", 1L, listOf("a.png", "m.png"))
        assertEquals("flux1-Fill-Dev_FP8.safetensors", wf.inputs("1").getString("unet_name"))
        assertEquals("ae.sft", wf.inputs("3").getString("vae_name"))
        assertEquals(
            "FLUX.1-Turbo-Alpha.safetensors",
            wf.inputs("4").getJSONObject("lora_1").getString("lora")
        )
        val s = wf.inputs(InpaintBuilder.N_SAMPLER)
        assertEquals(8, s.getInt("steps"))
        assertEquals("dpmpp_2m", s.getString("sampler_name"))
        assertEquals("sgm_uniform", s.getString("scheduler"))
        assertEquals(1.0, s.getDouble("denoise"), 0.001)
        assertEquals(30.0, wf.inputs("31").getDouble("guidance"), 0.001)
        // model trénovaný na díry dostává masku přes InpaintModelConditioning
        assertEquals("InpaintModelConditioning", wf.getJSONObject("33").getString("class_type"))
        assertTrue(wf.inputs("33").getBoolean("noise_mask"))
        assertEquals(InpaintBuilder.FILL_STEPS, s.getInt("steps"))
    }

    // ------------------------------------------------------------ společné

    @Test
    fun `seed jde do spravneho uzlu podle modelu`() {
        val k = InpaintBuilder.build(klein, InpaintModel.KLEIN, "x", 5L, listOf("a.png", "m.png"))
        assertEquals(5L, k.inputs(InpaintBuilder.N_NOISE).getLong("noise_seed"))
        val f = InpaintBuilder.build(fill, InpaintModel.FILL, "x", 5L, listOf("a.png", "m.png"))
        assertEquals(5L, f.inputs(InpaintBuilder.N_SAMPLER).getLong("seed"))
        // Klein nemá KSampler a Fill nemá RandomNoise — čísla uzlů se nesmí plést.
        assertFalse(k.has(InpaintBuilder.N_SAMPLER))
        assertFalse(f.has(InpaintBuilder.N_NOISE))
    }

    @Test
    fun `klein dostane zadani jako prikaz, fill doslova`() {
        // Klein drží původní výřez jako referenci a holý popis pro něj znamená
        // „nech to být" — proto se jeho zadání balí do instrukce. Flux Fill
        // maluje do díry rovnou to, co je v textu, tomu se nesmí sahat.
        val k = InpaintBuilder.build(klein, InpaintModel.KLEIN, "dřevěná lavička", 1L,
            listOf("a.png", "m.png"))
        val textK = k.inputs(InpaintBuilder.N_TEXT).getString("text")
        assertTrue(textK.contains("dřevěná lavička"))
        assertTrue(textK.startsWith("Repaint the masked region"))

        val f = InpaintBuilder.build(fill, InpaintModel.FILL, "dřevěná lavička", 1L,
            listOf("a.png", "m.png"))
        assertEquals("dřevěná lavička", f.inputs(InpaintBuilder.N_TEXT).getString("text"))

        // Prázdné zadání se nesmí proměnit v instrukci bez obsahu.
        assertEquals("", InpaintBuilder.zadaniProModel(InpaintModel.KLEIN, "   "))
    }

    @Test
    fun `vychozi model karty je Flux Fill`() {
        // Klein na popisné zadání často nezmění nic (ověřeno na běhu 2. 9. 2026),
        // takže výchozí je model trénovaný přímo na domalovávání.
        assertEquals(InpaintModel.FILL, InpaintScene().model)
        assertEquals(InpaintModel.FILL, InpaintModel.entries.first())
    }

    @Test
    fun `kroky hlasene ukazateli sedi s predlohou`() {
        assertEquals(4, InpaintBuilder.stepsFor(InpaintModel.KLEIN))
        assertEquals(8, InpaintBuilder.stepsFor(InpaintModel.FILL))
    }

    @Test
    fun `validace chce fotku, masku i zadani po rade`() {
        assertEquals(
            "Vyber fotku, do které se má domalovávat.",
            inpaintProblem(InpaintScene())
        )
        assertEquals(
            "Začmárej prstem místo, které se má přemalovat.",
            inpaintProblem(InpaintScene(source = File("a.png")))
        )
        assertEquals(
            "Napiš, co má na zamaskovaném místě být.",
            inpaintProblem(InpaintScene(source = File("a.png"), mask = File("m.png")))
        )
        assertNull(
            inpaintProblem(
                InpaintScene(source = File("a.png"), mask = File("m.png"), prompt = "lavička")
            )
        )
    }

    @Test
    fun `imageSlots pokryje vsechny soubory sceny`() {
        // Past z 2.89 u výměny tváře: scéna nesla víc souborů, než kolik jich
        // engine podle imageSlots nahrál, a maska se cestou ztratila.
        val scena = InpaintScene(source = File("a.png"), mask = File("m.png"), prompt = "x")
        assertEquals(listOf("a.png", "m.png"), scena.uploadImages.map { it.name })
        assertTrue(GenParams(mode = Mode.INPAINT).imageSlots >= scena.uploadImages.size)
    }

    @Test
    fun `faze podle trid`() {
        assertEquals(Stage.MODELS, InpaintBuilder.stageForClass("UNETLoader"))
        assertEquals(Stage.REFERENCES, InpaintBuilder.stageForClass("InpaintCropImproved"))
        assertEquals(Stage.SAMPLING, InpaintBuilder.stageForClass("SamplerCustomAdvanced"))
        assertEquals(Stage.SAMPLING, InpaintBuilder.stageForClass("KSampler"))
        assertEquals(Stage.MUXING, InpaintBuilder.stageForClass("InpaintStitchImproved"))
        assertTrue(InpaintBuilder.reportsSteps("SamplerCustomAdvanced"))
        assertTrue(InpaintBuilder.reportsSteps("KSampler"))
        assertFalse(InpaintBuilder.reportsSteps("VAEDecode"))
    }
}
