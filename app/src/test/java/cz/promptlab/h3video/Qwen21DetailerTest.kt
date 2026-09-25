package cz.promptlab.h3video

import cz.promptlab.h3video.comfy.Qwen21EditBuilder
import cz.promptlab.h3video.comfy.RestoreBuilder
import cz.promptlab.h3video.comfy.ZImageBuilder
import cz.promptlab.h3video.data.Aspect
import cz.promptlab.h3video.data.EditMotor
import cz.promptlab.h3video.data.ImageEditScene
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Detailer LoRA pro Qwen 2.1: vždy v Opravě, volitelně v Úpravě a Obrázku. */
class Qwen21DetailerTest {
    private val edit = File("src/main/res/raw/workflow_qwen21_edit.json").readText()
    private val t2i = File("src/main/res/raw/workflow_qwen21_t2i.json").readText()

    private fun JSONObject.ins(id: String) = getJSONObject(id).getJSONObject("inputs")

    private fun zkontroluj(g: JSONObject) {
        val d = Qwen21EditBuilder.N_DETAILER
        assertEquals("LoraLoaderModelOnly", g.getJSONObject(d).getString("class_type"))
        assertEquals(Qwen21EditBuilder.DETAILER, g.ins(d).getString("lora_name"))
        assertEquals(Qwen21EditBuilder.N_UNET, g.ins(d).getJSONArray("model").getString(0))
        assertEquals(d, g.ins(Qwen21EditBuilder.N_CACHE).getJSONArray("model").getString(0))
        File("build/qwen21-grafy").mkdirs()
    }

    @Test fun `oprava fotky ma detailer vzdy`() {
        val g = RestoreBuilder.build(edit, 1L, listOf("a.png"))
        zkontroluj(g)
        assertTrue(g.ins(Qwen21EditBuilder.N_TEXT).getString("prompt").contains("Enhance this image"))
        File("build/qwen21-grafy").mkdirs(); File("build/qwen21-grafy/oprava.json").writeText(g.toString(1))
    }

    @Test fun `uprava jen kdyz je zapnuty`() {
        val s = ImageEditScene(motor = EditMotor.QWEN21, prompt = "remove the background")
        val bez = Qwen21EditBuilder.build(edit, s, 1L, listOf("a.png"))
        assertFalse(bez.has(Qwen21EditBuilder.N_DETAILER))
        assertEquals("1", bez.ins(Qwen21EditBuilder.N_CACHE).getJSONArray("model").getString(0))
        val s2 = s.copy(qwen21Detailer = true)
        val se = Qwen21EditBuilder.build(edit, s2, 1L, listOf("a.png"))
        zkontroluj(se)
        File("build/qwen21-grafy/uprava.json").writeText(se.toString(1))
    }

    @Test fun `obrazek jen kdyz je zapnuty`() {
        val bez = ZImageBuilder.build(t2i, "a cat", Aspect.entries.first(), 1L, model = "qwen21")
        assertFalse(bez.has(Qwen21EditBuilder.N_DETAILER))
        val se = ZImageBuilder.build(t2i, "a cat", Aspect.entries.first(), 1L, model = "qwen21", qwenDetailer = true)
        zkontroluj(se)
        File("build/qwen21-grafy/obrazek.json").writeText(se.toString(1))
    }
}
