package cz.promptlab.h3video

import cz.promptlab.h3video.comfy.Stage
import cz.promptlab.h3video.comfy.ZImageBuilder
import cz.promptlab.h3video.data.Aspect
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Karta Obrázek jede na uživatelově Z-Image Turbo workflow převzatém 1:1.
 * Testy hlídají, že appka dosazuje JEN zadání, rozměry a seed — kroky, cfg,
 * sampler i shift musí zůstat z předlohy.
 */
class ZImageBuilderTest {

    private val sablona: String =
        File("src/main/res/raw/workflow_zimage_t2i.json").readText()

    private fun JSONObject.inputs(node: String): JSONObject =
        getJSONObject(node).getJSONObject("inputs")

    @Test
    fun `dosadi se jen zadani, rozmery a seed`() {
        val wf = ZImageBuilder.build(sablona, "kocka na strese", Aspect.LANDSCAPE_16_9, 99L)
        assertEquals("kocka na strese", wf.inputs(ZImageBuilder.N_TEXT).getString("text"))
        assertEquals(1344, wf.inputs(ZImageBuilder.N_LATENT).getInt("width"))
        assertEquals(768, wf.inputs(ZImageBuilder.N_LATENT).getInt("height"))
        assertEquals(99L, wf.inputs(ZImageBuilder.N_SAMPLER).getLong("seed"))
    }

    @Test
    fun `vyladene hodnoty z predlohy zustavaji netknute`() {
        val wf = ZImageBuilder.build(sablona, "x", Aspect.SQUARE_1_1, 1L)
        val s = wf.inputs(ZImageBuilder.N_SAMPLER)
        assertEquals(8, s.getInt("steps"))
        assertEquals(1.0, s.getDouble("cfg"), 0.001)
        assertEquals("res_multistep", s.getString("sampler_name"))
        assertEquals("simple", s.getString("scheduler"))
        assertEquals(1.0, s.getDouble("denoise"), 0.001)
        assertEquals(3.0, wf.inputs(ZImageBuilder.N_SHIFT).getDouble("shift"), 0.001)
        assertEquals(
            "z_image_turbo_bf16.safetensors",
            wf.inputs(ZImageBuilder.N_UNET).getString("unet_name")
        )
        assertEquals("qwen_3_4b.safetensors", wf.inputs(ZImageBuilder.N_CLIP).getString("clip_name"))
        assertEquals("lumina2", wf.inputs(ZImageBuilder.N_CLIP).getString("type"))
        assertEquals("ae.sft", wf.inputs(ZImageBuilder.N_VAE).getString("vae_name"))
        assertEquals(ZImageBuilder.STEPS, 8)
    }

    @Test
    fun `zapojeni sedi a nejsou visici odkazy`() {
        val wf = ZImageBuilder.build(sablona, "x", Aspect.SQUARE_1_1, 1L)
        // text → sampler (positive) i zero-out (negative)
        assertEquals(ZImageBuilder.N_TEXT,
            wf.inputs(ZImageBuilder.N_SAMPLER).getJSONArray("positive").getString(0))
        assertEquals(ZImageBuilder.N_ZERO,
            wf.inputs(ZImageBuilder.N_SAMPLER).getJSONArray("negative").getString(0))
        assertEquals(ZImageBuilder.N_TEXT,
            wf.inputs(ZImageBuilder.N_ZERO).getJSONArray("conditioning").getString(0))
        // model přes sigma shift, latent z plátna, dekódování do uložení
        assertEquals(ZImageBuilder.N_SHIFT,
            wf.inputs(ZImageBuilder.N_SAMPLER).getJSONArray("model").getString(0))
        assertEquals(ZImageBuilder.N_LATENT,
            wf.inputs(ZImageBuilder.N_SAMPLER).getJSONArray("latent_image").getString(0))
        assertEquals(ZImageBuilder.N_SAMPLER,
            wf.inputs(ZImageBuilder.N_DECODE).getJSONArray("samples").getString(0))
        assertEquals(ZImageBuilder.N_DECODE,
            wf.inputs(ZImageBuilder.N_SAVE).getJSONArray("images").getString(0))
        // žádné visící odkazy
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

    @Test
    fun `odvazana lora se vklada jen se zapnutym prepinacem`() {
        // Vypnuto = graf beze změny (šablona 1:1).
        val bez = ZImageBuilder.build(sablona, "x", Aspect.SQUARE_1_1, 1L)
        assertFalse(bez.has(ZImageBuilder.N_NSFW_LORA))
        assertEquals(ZImageBuilder.N_UNET,
            bez.inputs(ZImageBuilder.N_SHIFT).getJSONArray("model").getString(0))

        // Zapnuto = LoraLoaderModelOnly mezi UNETLoader a sigma shift.
        val s = ZImageBuilder.build(
            sablona, "x", Aspect.SQUARE_1_1, 1L, nsfwLora = true, nsfwSila = 0.75f,
        )
        val lora = s.inputs(ZImageBuilder.N_NSFW_LORA)
        assertEquals("LoraLoaderModelOnly",
            s.getJSONObject(ZImageBuilder.N_NSFW_LORA).getString("class_type"))
        assertEquals(ZImageBuilder.NSFW_LORA_FILE, lora.getString("lora_name"))
        assertEquals(0.75, lora.getDouble("strength_model"), 0.001)
        assertEquals(ZImageBuilder.N_UNET, lora.getJSONArray("model").getString(0))
        assertEquals(ZImageBuilder.N_NSFW_LORA,
            s.inputs(ZImageBuilder.N_SHIFT).getJSONArray("model").getString(0))
        // Zbytek grafu nedotčený.
        assertEquals(8, s.inputs(ZImageBuilder.N_SAMPLER).getInt("steps"))
    }

    @Test
    fun `rozmery jsou nasobky 16 kolem jednoho megapixelu`() {
        Aspect.entries.forEach { a ->
            val (w, h) = ZImageBuilder.sizeFor(a)
            assertEquals("sirka $a", 0, w % 16)
            assertEquals("vyska $a", 0, h % 16)
            val mp = w.toLong() * h
            assertTrue("$a ma $mp bodu", mp in 950_000..1_100_000)
        }
    }

    @Test
    fun `faze a kroky podle tridy uzlu`() {
        assertEquals(Stage.SAMPLING, ZImageBuilder.stageForClass("KSampler"))
        assertEquals(Stage.MODELS, ZImageBuilder.stageForClass("UNETLoader"))
        assertEquals(Stage.MUXING, ZImageBuilder.stageForClass("SaveImage"))
        assertTrue(ZImageBuilder.reportsSteps("KSampler"))
        assertFalse(ZImageBuilder.reportsSteps("VAEDecode"))
    }
}
