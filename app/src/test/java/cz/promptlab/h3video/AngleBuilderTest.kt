package cz.promptlab.h3video

import cz.promptlab.h3video.comfy.AngleBuilder
import cz.promptlab.h3video.comfy.Stage
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Úhel kamery používá přímo Qwen Image 2.1, bez starého 2511 LoRA řetězu. */
class AngleBuilderTest {
    private val sablona = File("src/main/res/raw/workflow_qwen21_edit.json").readText()

    private fun JSONObject.inputs(node: String): JSONObject =
        getJSONObject(node).getJSONObject("inputs")

    private fun bezVisicichOdkazu(wf: JSONObject) {
        val ids = wf.keys().asSequence().toSet()
        wf.keys().forEach { id ->
            val inputs = wf.inputs(id)
            inputs.keys().forEach { key ->
                val value = inputs.opt(key)
                if (value is JSONArray && value.length() == 2 && value.opt(0) is String) {
                    assertTrue("uzel $id → ${value.getString(0)}", value.getString(0) in ids)
                }
            }
        }
    }

    @Test fun `geometrie ovladace zustava obousmerna`() {
        for (smer in AngleBuilder.AZIMUTY.indices) {
            assertEquals(smer, AngleBuilder.smerZUhlu(AngleBuilder.uhelProSmer(smer)))
        }
        for (vyska in AngleBuilder.VYSKY.indices) {
            assertEquals(vyska, AngleBuilder.vyskaZUhlu(AngleBuilder.uhelProVysku(vyska)))
        }
        for (odstup in AngleBuilder.ODSTUPY.indices) {
            assertEquals(odstup, AngleBuilder.odstupZPomeru(AngleBuilder.pomerProOdstup(odstup)))
        }
        assertEquals(96, AngleBuilder.POZ)
    }

    @Test fun `prompt je prirozeny pokyn pro qwen 21 bez stareho spoustece`() {
        val prompt = AngleBuilder.prompt(2, 3, 0)
        assertTrue(prompt.contains("<image1>"))
        assertTrue(prompt.contains("right side view"))
        assertTrue(prompt.contains("high-angle shot"))
        assertTrue(prompt.contains("close-up"))
        assertTrue(prompt.contains("Preserve the exact subject"))
        assertFalse(prompt.contains("<sks>"))
    }

    @Test fun `graf dosadi fotku seed prompt a oficialni vzorkovani`() {
        val wf = AngleBuilder.build(sablona, 42L, listOf("clovek.png"), 2, 3, 0)
        assertEquals("clovek.png", wf.inputs(AngleBuilder.N_IMAGE).getString("image"))
        assertEquals(42L, wf.inputs(AngleBuilder.N_SAMPLER).getLong("seed"))
        assertEquals(
            AngleBuilder.prompt(2, 3, 0),
            wf.inputs(AngleBuilder.N_PROMPT).getString("prompt"),
        )
        val sampler = wf.inputs(AngleBuilder.N_SAMPLER)
        assertEquals(25, sampler.getInt("steps"))
        assertEquals(1.0, sampler.getDouble("cfg"), 1e-6)
        assertEquals("euler", sampler.getString("sampler_name"))
        assertEquals("simple", sampler.getString("scheduler"))
        assertEquals("H3AngleQwen21", wf.inputs(AngleBuilder.N_SAVE).getString("filename_prefix"))
        bezVisicichOdkazu(wf)
    }

    @Test fun `graf obsahuje pouze novy qwen model a zadnou lora`() {
        val wf = AngleBuilder.build(sablona, 1L, listOf("a.png"), 0, 1, 1)
        assertEquals(
            "qwen_image_2.1_int8_convrot.safetensors",
            wf.inputs("1").getString("unet_name"),
        )
        assertFalse(wf.toString().contains("2511"))
        assertFalse(wf.keys().asSequence().any {
            wf.getJSONObject(it).optString("class_type") == "LoraLoaderModelOnly"
        })
    }

    @Test fun `vsechny tridy predlohy maji fazi`() {
        val wf = JSONObject(sablona)
        wf.keys().forEach { id ->
            val cls = wf.getJSONObject(id).getString("class_type")
            assertTrue(
                "uzel $cls nemá fázi",
                AngleBuilder.stageForClass(cls) != Stage.SAMPLING || cls == "KSampler",
            )
        }
    }
}
