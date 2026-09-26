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
 * Karty Oprava fotky (Qwen 2.1) a Výměna tváře (ACE++).
 */
class RestoreFaceSwapBuilderTest {

    private val restore: String =
        File("src/main/res/raw/workflow_qwen21_edit.json").readText()
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
        assertEquals(25, s.getInt("steps"))
        assertEquals("euler", s.getString("sampler_name"))
        assertEquals("simple", s.getString("scheduler"))
        // opravovací zadání je pevné a nesmí být prázdné
        assertTrue(
            wf.inputs(RestoreBuilder.N_PROMPT).getString("prompt")
                .contains("Ultra high-resolution photo reconstruction")
        )
        // 26. 9. 2026: výsledky byly málo barevné — obarvení je výslovný úkol,
        // identita se drží odkazem na fotku a „střídmá sytost" je pryč.
        val p = wf.inputs(RestoreBuilder.N_PROMPT).getString("prompt")
        assertTrue(p.startsWith("Restore <image1> into a full-color professional photograph"))
        assertTrue(p.contains("same person as in <image1>"))
        assertTrue(p.contains("rich vivid true-to-life colors"))
        assertTrue(!p.contains("restrained saturation"))
        assertTrue(!p.contains("lighting and style"))
        assertTrue(p.contains(RestoreBuilder.DETAILER_VETA))
        assertEquals(RestoreBuilder.STEPS, 25)
        assertEquals("H3RestoreQwen21", wf.inputs(RestoreBuilder.N_SAVE).getString("filename_prefix"))
        assertTrue(wf.toString().contains("qwen_image_2.1_int8_convrot.safetensors"))
        assertTrue(!wf.toString().contains("2511"))
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
    fun `tvar - dosadi se cil, tvar, maska a seed`() {
        val wf = FaceSwapBuilder.build(swap, 7L, listOf("cil.png", "tvar.png", "maska.png"))
        assertEquals("cil.png", wf.inputs(FaceSwapBuilder.N_TARGET).getString("image"))
        assertEquals("tvar.png", wf.inputs(FaceSwapBuilder.N_FACE).getString("image"))
        assertEquals("maska.png", wf.inputs(FaceSwapBuilder.N_MASK).getString("image"))
        assertEquals(7L, wf.inputs(FaceSwapBuilder.N_SAMPLER).getLong("seed"))
        bezVisicichOdkazu(wf)
    }

    @Test
    fun `tvar - vyladene hodnoty zustavaji`() {
        val wf = FaceSwapBuilder.build(swap, 1L, listOf("a.png", "b.png", "m.png"))
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
    fun `tvar - maska jde ze samostatneho souboru pres ImageToMask`() {
        val wf = FaceSwapBuilder.build(swap, 1L, listOf("cil.png", "tvar.png", "maska.png"))
        // maska: LoadImage(244) → ImageToMask(245, kanál red) → výřez
        assertEquals("ImageToMask", wf.getJSONObject("245").getString("class_type"))
        assertEquals(FaceSwapBuilder.N_MASK,
            wf.inputs("245").getJSONArray("image").getString(0))
        assertEquals("red", wf.inputs("245").getString("channel"))
        val maska = wf.inputs(FaceSwapBuilder.N_CROP).getJSONArray("mask")
        assertEquals("245", maska.getString(0))
        assertEquals(0, maska.getInt(1))
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
            faceSwapProblem(FaceSwapScene(target = File("c.png"), mask = File("m.png")))
        )
        assertNull(
            faceSwapProblem(
                FaceSwapScene(
                    target = File("c.png"), mask = File("m.png"), face = File("f.png")
                )
            )
        )
        // Pořadí nahrávání je závazné pro stavitele: cíl, tvář, maska.
        val scena = FaceSwapScene(
            target = File("c.png"), mask = File("m.png"), face = File("f.png")
        )
        assertEquals(listOf("c.png", "f.png", "m.png"), scena.uploadImages.map { it.name })
    }

    @Test
    fun `tvar - imageSlots pokryje vsechny soubory sceny`() {
        // 2.89 regrese: scéna nese 3 soubory (cíl, tvář, maska), ale limit
        // slotů zůstal na 2 a engine masku při nahrávání uřízl.
        val scena = cz.promptlab.h3video.data.FaceSwapScene(
            target = File("c.png"), mask = File("m.png"), face = File("f.png")
        )
        assertTrue(
            cz.promptlab.h3video.data.GenParams(mode = cz.promptlab.h3video.data.Mode.FACESWAP)
                .imageSlots >= scena.uploadImages.size
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

    @Test
    fun `oprava - doostreni DLSS na konci grafu`() {
        val bez = RestoreBuilder.build(restore, 1L, listOf("a.png"))
        assertTrue(!bez.has(RestoreBuilder.N_DLSS))
        assertEquals(RestoreBuilder.N_DEKODER,
            bez.inputs(RestoreBuilder.N_SAVE).getJSONArray("images").getString(0))

        val se = RestoreBuilder.build(restore, 1L, listOf("a.png"), doostrit = true, nasobek = "2x")
        assertEquals("DLSS5EnhanceImages", se.getJSONObject(RestoreBuilder.N_DLSS).getString("class_type"))
        assertEquals(RestoreBuilder.N_DEKODER,
            se.inputs(RestoreBuilder.N_DLSS).getJSONArray("images").getString(0))
        assertTrue(se.inputs(RestoreBuilder.N_DLSS).getBoolean("verify_neural_rendering"))
        assertEquals(RestoreBuilder.N_DLSS,
            se.inputs(RestoreBuilder.N_SAVE).getJSONArray("images").getString(0))
        assertEquals("2x (Performance)",
            se.inputs(RestoreBuilder.N_DLSS_NASTAVENI).getString("upscaling_mode"))
        java.io.File("build/qwen21-grafy").mkdirs()
        java.io.File("build/qwen21-grafy/oprava_dlss.json").writeText(se.toString(1))
    }
}
