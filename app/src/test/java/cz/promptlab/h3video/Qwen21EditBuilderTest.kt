package cz.promptlab.h3video

import cz.promptlab.h3video.comfy.Qwen21EditBuilder
import cz.promptlab.h3video.data.EditMotor
import cz.promptlab.h3video.data.EditReference
import cz.promptlab.h3video.data.ImageEditScene
import cz.promptlab.h3video.data.Qwen21CacheDevice
import cz.promptlab.h3video.data.Qwen21CachePrecision
import cz.promptlab.h3video.data.Qwen21Resolution
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class Qwen21EditBuilderTest {
    private val sablona: String
        get() = File("src/main/res/raw/workflow_qwen21_edit.json").readText()

    private fun inputs(wf: JSONObject, id: String): JSONObject =
        wf.getJSONObject(id).getJSONObject("inputs")

    private fun source(wf: JSONObject, id: String, input: String): Pair<String, Int> {
        val value = inputs(wf, id).getJSONArray(input)
        return value.getString(0) to value.getInt(1)
    }

    @Test
    fun `stavitel dosadi oficialni parametry a rgba pokyn`() {
        val scene = ImageEditScene(
            motor = EditMotor.QWEN21,
            prompt = "  odstraň pozadí  ",
            qwen21Steps = 40,
            qwen21Resolution = Qwen21Resolution.NATIVE_2K,
            qwen21CacheDevice = Qwen21CacheDevice.CPU,
            qwen21CachePrecision = Qwen21CachePrecision.INT8,
            qwen21Transparent = true,
        )
        val wf = Qwen21EditBuilder.build(sablona, scene, 42, listOf("hlavni.png"))

        assertEquals("hlavni.png", inputs(wf, Qwen21EditBuilder.N_FIRST_IMAGE).getString("image"))
        assertEquals(2048, inputs(wf, Qwen21EditBuilder.N_TEXT).getInt("resolution"))
        assertTrue(inputs(wf, Qwen21EditBuilder.N_TEXT).getString("prompt").contains("RGBA image"))
        assertEquals(42L, inputs(wf, Qwen21EditBuilder.N_SAMPLER).getLong("seed"))
        assertEquals(40, inputs(wf, Qwen21EditBuilder.N_SAMPLER).getInt("steps"))
        assertEquals(1.0, inputs(wf, Qwen21EditBuilder.N_SAMPLER).getDouble("cfg"), .001)
        assertEquals("cpu", inputs(wf, Qwen21EditBuilder.N_CACHE).getString("device"))
        assertEquals("int8", inputs(wf, Qwen21EditBuilder.N_CACHE).getString("dtype"))
        assertEquals(Qwen21EditBuilder.N_TEXT to 2, source(wf, Qwen21EditBuilder.N_SAMPLER, "latent_image"))
    }

    @Test
    fun `az deset obrazku jde do dynamickych vstupu ve spravnem poradi`() {
        val images = (1..12).map { "image$it.png" }
        val wf = Qwen21EditBuilder.build(
            sablona, ImageEditScene(motor = EditMotor.QWEN21, prompt = "mix"), 1, images,
        )

        assertEquals("image1.png", inputs(wf, "100").getString("image"))
        for (index in 2..10) {
            val node = (99 + index).toString()
            assertEquals("image$index.png", inputs(wf, node).getString("image"))
            assertEquals(node to 0, source(wf, Qwen21EditBuilder.N_TEXT, "images.image_$index"))
        }
        assertFalse(wf.has("110"))
        assertFalse(inputs(wf, Qwen21EditBuilder.N_TEXT).has("images.image_11"))
    }

    @Test
    fun `vsechny odkazy vedou na existujici uzly`() {
        val wf = Qwen21EditBuilder.build(
            sablona, ImageEditScene(motor = EditMotor.QWEN21, prompt = "edit"),
            1, listOf("a.png", "b.png", "c.png"),
        )
        val ids = wf.keys().asSequence().toSet()
        wf.keys().forEach { id ->
            val fields = inputs(wf, id)
            fields.keys().forEach { key ->
                val value = fields.opt(key)
                if (value is JSONArray && value.length() == 2 && value.opt(0) is String) {
                    assertTrue("$id.$key", value.getString(0) in ids)
                }
            }
        }
    }

    @Test
    fun `scene drzi kompaktni poradi a limit deseti obrazku`() {
        val source = File("source.png")
        val refs = (2..12).map { EditReference(File("ref$it.png"), null) }
        val scene = ImageEditScene(motor = EditMotor.QWEN21, source = source).withReferences(refs)

        assertEquals(9, scene.references.size)
        assertEquals((listOf(source) + (2..10).map { File("ref$it.png") }), scene.uploadImages)
        val afterRemove = scene.withReferences(scene.references.filterIndexed { index, _ -> index != 2 })
        assertEquals("ref5.png", afterRemove.references[2].file.name)
        assertEquals(9, afterRemove.uploadImages.size)
    }
}
