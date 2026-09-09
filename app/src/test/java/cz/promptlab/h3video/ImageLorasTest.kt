package cz.promptlab.h3video

import cz.promptlab.h3video.comfy.T2iModel
import cz.promptlab.h3video.comfy.ZImageBuilder
import cz.promptlab.h3video.data.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class ImageLorasTest {
    @Test fun compatibilityUsesArchitectureAndMetadata() {
        assertEquals(LoraCompatibility.MATCH, ImageLoras.compatibility(T2iModel.KLEIN, EditLoraFile("FLUX2_nsfw_F2K9B.safetensors")))
        assertEquals(LoraCompatibility.INCOMPATIBLE, ImageLoras.compatibility(T2iModel.KLEIN, EditLoraFile("klein_4b.safetensors")))
        assertEquals(LoraCompatibility.UNKNOWN, ImageLoras.compatibility(T2iModel.KLEIN, EditLoraFile("custom.safetensors")))
        assertEquals(LoraCompatibility.INCOMPATIBLE, ImageLoras.compatibility(T2iModel.KLEIN,
            EditLoraFile("klein9b.safetensors", JSONObject().put("base_model", "SDXL"))))
    }

    @Test fun choicesPersistPerModelAndMigrateLegacyTurboOnly() {
        val choices = mapOf("klein" to listOf(EditLora("klein9b.safetensors", .6f)), "turbo" to emptyList())
        assertEquals(choices, ImageLoras.decode(ImageLoras.encode(choices)))
        val legacy = GenParams(zimageNsfw = true, zimageNsfwLora = "old.safetensors")
        assertEquals("old.safetensors", ImageLoras.selected(legacy).first().name)
        assertTrue(ImageLoras.selected(legacy.copy(zimageModel = "klein")).isEmpty())
        assertTrue(ImageLoras.selected(legacy.copy(imageLoras = choices)).isEmpty())
        assertEquals(.6f, ImageLoras.selected(legacy.copy(zimageModel = "klein", imageLoras = choices)).first().strength)
    }

    @Test fun allImageModelsRouteConsumersThroughBothLoras() {
        for (model in T2iModel.entries) {
            val name = if (model.zRodinyZImage) "zimage" else if (model == T2iModel.KLEIN) "flux2_klein" else "ernie"
            val template = File("src/main/res/raw/workflow_${name}_t2i.json").readText()
            fun build(loras: List<EditLora>) = ZImageBuilder.build(template, "landscape", Aspect.LANDSCAPE_16_9, 42,
                model = model.id, userLoras = loras)
            val plain = build(emptyList())
            val wf = build(listOf(EditLora("first", .5f), EditLora("second", .7f)))
            val loader = plain.keys().asSequence().single { plain.getJSONObject(it).getString("class_type") in listOf("UNETLoader", "UnetLoaderGGUF") }
            assertEquals(loader, wf.getJSONObject("800").getJSONObject("inputs").getJSONArray("model").getString(0))
            assertEquals("800", wf.getJSONObject("801").getJSONObject("inputs").getJSONArray("model").getString(0))
            var consumers = 0
            for (id in plain.keys()) {
                val inputs = plain.getJSONObject(id).optJSONObject("inputs") ?: continue
                if (inputs.optJSONArray("model")?.optString(0) == loader) {
                    consumers++
                    assertEquals("801", wf.getJSONObject(id).getJSONObject("inputs").getJSONArray("model").getString(0))
                }
            }
            assertTrue(consumers > 0)
            assertEquals(plain.toString(), build(listOf(EditLora("disabled", 0f))).toString())
        }
    }
}
