package cz.promptlab.h3video

import cz.promptlab.h3video.comfy.InpaintBuilder
import cz.promptlab.h3video.comfy.Qwen21EditBuilder
import cz.promptlab.h3video.comfy.T2iModel
import cz.promptlab.h3video.comfy.ZImageBuilder
import cz.promptlab.h3video.data.Aspect
import cz.promptlab.h3video.data.EditLora
import cz.promptlab.h3video.data.EditLoraFile
import cz.promptlab.h3video.data.EditLoras
import cz.promptlab.h3video.data.EditMotor
import cz.promptlab.h3video.data.ImageEditScene
import cz.promptlab.h3video.data.ImageLoras
import cz.promptlab.h3video.data.InpaintModel
import cz.promptlab.h3video.data.InpaintRezim
import cz.promptlab.h3video.data.InpaintScene
import cz.promptlab.h3video.data.LoraCompatibility
import cz.promptlab.h3video.data.loryProModel
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * LoRA pro Qwen Image 2.1 (32 bloků). Soubory pro starý Qwen-Image (60 bloků)
 * se na něj nenačtou, takže se nesmí nabízet. Jména i metadata jsou ze
 * skutečných souborů na serveru (25. 9. 2026).
 */
class Qwen21LoraTest {

    private val meta21 = JSONObject().put("ss_base_model_version", "qwen_image_2")
    private val nsfw = "qwen_image_2.1_nsfw_v1.0.safetensors"
    private val vagina = "qwen_image_2.1_vagina_v1.0.safetensors"
    private val detailer = "elusarcas-qwen2-1-detailer-v1.safetensors"
    private val stare = listOf(
        "qwen_2512_pussy_anus_v2.safetensors",
        "PROMPTLAB_SEXGOD_FemaleNudity_QwenEdit_2511_v2.safetensors",
        "Qwen-Image-2512-Lightning-4steps-V1.0-fp32.safetensors",
        "QWEN_EDIT_Unchained-XXX.safetensors",
    )

    @Test fun `uprava pozna LoRA pro 2_1 a stare Qwen odmitne`() {
        assertEquals(LoraCompatibility.MATCH, EditLoras.compatibility(EditMotor.QWEN21, nsfw, meta21))
        // i bez metadat — podle jména
        assertEquals(LoraCompatibility.MATCH, EditLoras.compatibility(EditMotor.QWEN21, vagina))
        // metadata qwen_image_2 bez „2.1" ve jménu
        assertEquals(LoraCompatibility.MATCH,
            EditLoras.compatibility(EditMotor.QWEN21, "NSFW Qwen Lora.safetensors", meta21))
        assertEquals(LoraCompatibility.BUILT_IN, EditLoras.compatibility(EditMotor.QWEN21, detailer, meta21))
        for (f in stare) assertEquals(f, LoraCompatibility.INCOMPATIBLE, EditLoras.compatibility(EditMotor.QWEN21, f))
        // LoRA pro 2.1 na Krea/Klein nepatří
        assertEquals(LoraCompatibility.INCOMPATIBLE, EditLoras.compatibility(EditMotor.KREA2, nsfw, meta21))
    }

    @Test fun `obrazek nabidne jen LoRA pro 2_1`() {
        assertEquals(LoraCompatibility.MATCH, ImageLoras.compatibility(T2iModel.QWEN21, EditLoraFile(nsfw, meta21)))
        assertEquals(LoraCompatibility.BUILT_IN, ImageLoras.compatibility(T2iModel.QWEN21, EditLoraFile(detailer, meta21)))
        for (f in stare) assertEquals(f, LoraCompatibility.INCOMPATIBLE,
            ImageLoras.compatibility(T2iModel.QWEN21, EditLoraFile(f)))
        assertEquals(LoraCompatibility.INCOMPATIBLE,
            ImageLoras.compatibility(T2iModel.TURBO, EditLoraFile(nsfw, meta21)))
    }

    @Test fun `domalovat nabidne jen LoRA pro 2_1 bez detaileru`() {
        val n = loryProModel(InpaintModel.QWEN21, stare + listOf(nsfw, vagina, detailer))
        assertEquals(listOf(nsfw, vagina).sorted(), n)
        assertFalse(loryProModel(InpaintModel.KLEIN, listOf(nsfw)).isNotEmpty())
    }

    private fun JSONObject.ins(id: String) = getJSONObject(id).getJSONObject("inputs")
    private val edit = File("src/main/res/raw/workflow_qwen21_edit.json").readText()
    private val t2i = File("src/main/res/raw/workflow_qwen21_t2i.json").readText()

    @Test fun `uprava zapoji LoRA za detailer pred cache`() {
        val s = ImageEditScene(motor = EditMotor.QWEN21, prompt = "undress her", qwen21Detailer = true)
            .withLora(EditLora(nsfw, 0.9f))
        val g = Qwen21EditBuilder.build(edit, s, 1L, listOf("a.png"))
        val cache = g.ins(Qwen21EditBuilder.N_CACHE).getJSONArray("model").getString(0)
        assertEquals(nsfw, g.ins(cache).getString("lora_name"))
        assertEquals(Qwen21EditBuilder.N_DETAILER, g.ins(cache).getJSONArray("model").getString(0))
        assertEquals(Qwen21EditBuilder.N_UNET,
            g.ins(Qwen21EditBuilder.N_DETAILER).getJSONArray("model").getString(0))
    }

    @Test fun `obrazek zapoji LoRA pred detailer`() {
        val g = ZImageBuilder.build(
            t2i, "a woman", Aspect.entries.first(), 1L, model = "qwen21", qwenDetailer = true,
            userLoras = listOf(EditLora(vagina, 0.8f)),
        )
        val det = g.ins(Qwen21EditBuilder.N_DETAILER).getJSONArray("model").getString(0)
        assertEquals(vagina, g.ins(det).getString("lora_name"))
        assertEquals(Qwen21EditBuilder.N_UNET, g.ins(det).getJSONArray("model").getString(0))
    }

    @Test fun `domalovani i rozsireni vlozi LoRA mezi model a cache`() {
        val inp = InpaintBuilder.build(
            File("src/main/res/raw/workflow_inpaint_qwen21.json").readText(),
            InpaintModel.QWEN21, "naked", 1L, listOf("a.png", "m.png"), lora = nsfw, loraSila = 0.9f,
        )
        assertEquals(InpaintBuilder.N_LORA_QWEN21, inp.ins("4").getJSONArray("model").getString(0))
        assertEquals(nsfw, inp.ins(InpaintBuilder.N_LORA_QWEN21).getString("lora_name"))

        val scena = InpaintScene(model = InpaintModel.QWEN21, rezim = InpaintRezim.ROZSIRIT, lora = vagina)
        val out = InpaintBuilder.buildRozsireni(
            File("src/main/res/raw/workflow_outpaint_qwen21.json").readText(),
            scena, 1L, listOf("a.png"), 800, 1200,
        )
        assertEquals(InpaintBuilder.N_LORA_QWEN21, out.ins("4").getJSONArray("model").getString(0))
        // LoRA z jiného modelu rozšíření nedostane
        val cizi = InpaintBuilder.buildRozsireni(
            File("src/main/res/raw/workflow_outpaint_qwen21.json").readText(),
            scena.copy(lora = stare.first()), 1L, listOf("a.png"), 800, 1200,
        )
        assertFalse(cizi.has(InpaintBuilder.N_LORA_QWEN21))
        assertTrue(cizi.ins("4").getJSONArray("model").getString(0) == "1")
    }
}
