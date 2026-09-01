package cz.promptlab.h3video

import cz.promptlab.h3video.comfy.FaceSwapBuilder
import cz.promptlab.h3video.comfy.RestoreBuilder
import cz.promptlab.h3video.comfy.Stage
import cz.promptlab.h3video.data.FaceSwapScene
import cz.promptlab.h3video.data.RestoreScene
import cz.promptlab.h3video.data.faceSwapProblem
import cz.promptlab.h3video.data.restoreProblem
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Karty Oprava fotky (Qwen 2511) a Výměna tváře (ACE++) jedou na
 * uživatelových workflow. Testy hlídají, že se dosazují jen fotky a seed
 * a že vyladěné hodnoty předloh zůstávají netknuté.
 */
class RestoreFaceSwapBuilderTest {

    private val restore: String =
        File("src/main/res/raw/workflow_qwen_restore.json").readText()
    private val swap: String =
        File("src/main/res/raw/workflow_ace_faceswap.json").readText()

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

    // ------------------------------------------------------------ oprava

    @Test
    fun `oprava - dosadi se jen fotka a seed`() {
        val wf = RestoreBuilder.build(restore, 42L, listOf("stara.png"))
        assertEquals("stara.png", wf.inputs(RestoreBuilder.N_IMAGE).getString("image"))
        assertEquals(42L, wf.inputs(RestoreBuilder.N_SAMPLER).getLong("seed"))
        bezVisicichOdkazu(wf)
    }

    @Test
    fun `oprava - vyladene hodnoty zustavaji`() {
        val wf = RestoreBuilder.build(restore, 1L, listOf("a.png"))
        val s = wf.inputs(RestoreBuilder.N_SAMPLER)
        assertEquals(4, s.getInt("steps"))
        assertEquals("euler_ancestral", s.getString("sampler_name"))
        assertEquals("beta57", s.getString("scheduler"))
        // opravovací zadání je pevné a nesmí být prázdné
        assertTrue(
            wf.inputs(RestoreBuilder.N_PROMPT).getString("value")
                .contains("photo reconstruction")
        )
        assertEquals(RestoreBuilder.STEPS, 4)
    }

    @Test
    fun `oprava - validace chce fotku`() {
        assertEquals(
            "Vyber fotku, kterou chceš opravit.",
            restoreProblem(RestoreScene())
        )
        assertNull(restoreProblem(RestoreScene(source = File("a.png"))))
    }

    // ------------------------------------------------------------- tvář

    @Test
    fun `tvar - dosadi se cil, tvar a seed`() {
        val wf = FaceSwapBuilder.build(swap, 7L, listOf("cil.png", "tvar.png"))
        assertEquals("cil.png", wf.inputs(FaceSwapBuilder.N_TARGET).getString("image"))
        assertEquals("tvar.png", wf.inputs(FaceSwapBuilder.N_FACE).getString("image"))
        assertEquals(7L, wf.inputs(FaceSwapBuilder.N_SAMPLER).getLong("seed"))
        bezVisicichOdkazu(wf)
    }

    @Test
    fun `tvar - vyladene hodnoty zustavaji`() {
        val wf = FaceSwapBuilder.build(swap, 1L, listOf("a.png", "b.png"))
        val s = wf.inputs(FaceSwapBuilder.N_SAMPLER)
        assertEquals(12, s.getInt("steps"))
        assertEquals("euler", s.getString("sampler_name"))
        assertEquals("normal", s.getString("scheduler"))
        // portrétní LoRA + Turbo přesně podle předlohy
        val l = wf.inputs(FaceSwapBuilder.N_LORA)
        assertEquals(
            "comfyui_portrait_lora64.safetensors",
            l.getJSONObject("lora_1").getString("lora")
        )
        assertEquals(
            "FLUX.1-Turbo-Alpha.safetensors",
            l.getJSONObject("lora_2").getString("lora")
        )
        assertEquals("Retain face. ", wf.inputs("343").getString("text"))
        assertEquals(50.0, wf.inputs("345").getDouble("guidance"), 0.001)
        assertEquals(FaceSwapBuilder.STEPS, 12)
    }

    @Test
    fun `tvar - maska jde pres alfa kanal cile do inpaint retezu`() {
        val wf = FaceSwapBuilder.build(swap, 1L, listOf("cil.png", "tvar.png"))
        // maska z LoadImage (výstup 1) vede do výřezu
        val maska = wf.inputs(FaceSwapBuilder.N_CROP).getJSONArray("mask")
        assertEquals(FaceSwapBuilder.N_TARGET, maska.getString(0))
        assertEquals(1, maska.getInt(1))
        // výsledek se vlepuje zpátky přes stitcher z výřezu
        assertEquals(FaceSwapBuilder.N_CROP,
            wf.inputs(FaceSwapBuilder.N_STITCH).getJSONArray("stitcher").getString(0))
        assertEquals(FaceSwapBuilder.N_STITCH,
            wf.inputs(FaceSwapBuilder.N_SAVE).getJSONArray("images").getString(0))
    }

    @Test
    fun `tvar - validace vyzaduje cil, masku i tvar po rade`() {
        assertEquals(
            "Vyber fotku, ve které se má vyměnit tvář.",
            faceSwapProblem(FaceSwapScene())
        )
        assertEquals(
            "Začmárej prstem obličej, který se má vyměnit.",
            faceSwapProblem(FaceSwapScene(target = File("c.png")))
        )
        assertEquals(
            "Vyber fotku s novou tváří.",
            faceSwapProblem(FaceSwapScene(target = File("c.png"), maskPainted = true))
        )
        assertNull(
            faceSwapProblem(
                FaceSwapScene(
                    target = File("c.png"), maskPainted = true, face = File("f.png")
                )
            )
        )
    }

    @Test
    fun `faze podle trid`() {
        assertEquals(Stage.SAMPLING, RestoreBuilder.stageForClass("KSampler"))
        assertEquals(Stage.MODELS, RestoreBuilder.stageForClass("UNETLoader"))
        assertEquals(Stage.SAMPLING, FaceSwapBuilder.stageForClass("KSampler"))
        assertEquals(Stage.MUXING, FaceSwapBuilder.stageForClass("InpaintStitchImproved"))
        assertTrue(FaceSwapBuilder.reportsSteps("KSampler"))
    }
}
