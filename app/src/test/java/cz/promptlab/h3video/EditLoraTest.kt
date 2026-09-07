package cz.promptlab.h3video

import cz.promptlab.h3video.comfy.*
import cz.promptlab.h3video.data.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class EditLoraTest {
    private fun template(name: String) = File("src/main/res/raw/$name.json").readText()
    private fun inputs(graph: JSONObject, id: String) = graph.getJSONObject(id).getJSONObject("inputs")
    private fun scene(motor: EditMotor) = ImageEditScene(motor = motor).withLora(EditLora("custom.safetensors", .7f))
    private fun upstream(graph: JSONObject, id: String) = inputs(graph, id).getJSONArray("model").getString(0)

    @Test fun `krea pridava lora za identitu a pred referencni patch`() {
        val graph = Krea2Builder.build(template("workflow_krea2_edit"), scene(EditMotor.KREA2), 42, listOf("image.png"))
        assertEquals(EditLoraBuilder.NODE, upstream(graph, Krea2Builder.N_PATCH))
        assertEquals(Krea2Builder.N_LORA, upstream(graph, EditLoraBuilder.NODE))
        assertEquals("krea2_identity_edit_v1_2.safetensors", inputs(graph, Krea2Builder.N_LORA).getString("lora_name"))
        assertLora(graph)
    }

    @Test fun `qwen prida lora na rychle i kvalitni ceste a zachova lightning`() {
        for (fast in listOf(true, false)) {
            val graph = QwenEditBuilder.build(template("workflow_qwen_edit"), scene(EditMotor.QWEN).copy(qwenRychle = fast), 42, listOf("image.png"))
            assertEquals(EditLoraBuilder.NODE, upstream(graph, QwenEditBuilder.N_SAMPLER))
            assertEquals(if (fast) QwenEditBuilder.N_LORA else QwenEditBuilder.N_CFGNORM, upstream(graph, EditLoraBuilder.NODE))
            assertEquals(if (fast) 4 else 40, inputs(graph, QwenEditBuilder.N_SAMPLER).getInt("steps"))
            assertLora(graph)
        }
    }

    @Test fun `klein vede lora do guideru a zachova obe reference`() {
        val graph = KleinEditBuilder.build(template("workflow_flux2_klein_edit"), scene(EditMotor.KLEIN), 42, listOf("a.png", "b.png"))
        assertEquals(EditLoraBuilder.NODE, upstream(graph, KleinEditBuilder.N_GUIDER))
        assertEquals(KleinEditBuilder.N_UNET, upstream(graph, EditLoraBuilder.NODE))
        assertEquals(KleinEditBuilder.N_REF_POS2, inputs(graph, KleinEditBuilder.N_GUIDER).getJSONArray("positive").getString(0))
        assertLora(graph)
    }

    private fun assertLora(graph: JSONObject) {
        assertEquals("LoraLoaderModelOnly", graph.getJSONObject(EditLoraBuilder.NODE).getString("class_type"))
        assertEquals("custom.safetensors", inputs(graph, EditLoraBuilder.NODE).getString("lora_name"))
        assertEquals(.7, inputs(graph, EditLoraBuilder.NODE).getDouble("strength_model"), .001)
        // Všechny odkazy musejí mířit na existující uzly.
        graph.keys().forEach { id ->
            val fields = inputs(graph, id)
            fields.keys().forEach { key ->
                fields.optJSONArray(key)?.let { ref -> assertTrue("$id.$key", graph.has(ref.getString(0))) }
            }
        }
    }

    @Test fun `nulova a prazdna lora nemení graf`() {
        for (selection in listOf(EditLora(), EditLora("custom.safetensors", 0f))) {
            val graph = KleinEditBuilder.build(template("workflow_flux2_klein_edit"),
                ImageEditScene(motor = EditMotor.KLEIN).withLora(selection), 1, listOf("a.png"))
            assertFalse(graph.has(EditLoraBuilder.NODE))
            assertEquals(KleinEditBuilder.N_UNET, upstream(graph, KleinEditBuilder.N_GUIDER))
        }
    }

    @Test fun `volba patri modelu a prezije serializaci i prepnuti zpet`() {
        val krea = scene(EditMotor.KREA2)
        val qwen = krea.copy(motor = EditMotor.QWEN)
        assertTrue(qwen.selectedLora.name.isEmpty())
        val both = qwen.withLora(EditLora("qwen.safetensors", 1.1f))
        val restored = both.copy(modelLoras = EditLoras.decode(EditLoras.encode(both.modelLoras)))
        assertEquals(EditLora("qwen.safetensors", 1.1f), restored.selectedLora)
        assertEquals(krea.selectedLora, restored.copy(motor = EditMotor.KREA2).selectedLora)
    }

    @Test fun `nabidka rozlisuje rodiny a velikost klein`() {
        assertEquals(LoraCompatibility.MATCH, EditLoras.compatibility(EditMotor.KREA2, "Krea_2/style.safetensors"))
        assertEquals(LoraCompatibility.MATCH, EditLoras.compatibility(EditMotor.QWEN, "qwen_image_edit/custom.safetensors"))
        assertEquals(LoraCompatibility.MATCH, EditLoras.compatibility(EditMotor.KLEIN, "Flux-2-Klein-9B/style.safetensors"))
        assertEquals(LoraCompatibility.INCOMPATIBLE, EditLoras.compatibility(EditMotor.KLEIN, "klein-4b.safetensors"))
        assertEquals(LoraCompatibility.INCOMPATIBLE, EditLoras.compatibility(EditMotor.KREA2, "qwen_edit.safetensors"))
        assertEquals(LoraCompatibility.UNKNOWN, EditLoras.compatibility(EditMotor.KLEIN, "portrait.safetensors"))
        assertEquals(LoraCompatibility.UNKNOWN, EditLoras.compatibility(EditMotor.KLEIN, "klein_portrait.safetensors"))
    }

    @Test fun `metadata mohou rozpoznat libovolne pojmenovany soubor`() {
        val metadata = JSONObject().put("ss_base_model_version", "flux-2-klein-9b")
        assertEquals(LoraCompatibility.MATCH, EditLoras.compatibility(EditMotor.KLEIN, "portrait.safetensors", metadata))
        assertEquals(LoraCompatibility.INCOMPATIBLE, EditLoras.compatibility(EditMotor.QWEN, "qwen_edit_name.safetensors", metadata))
        val prompt = JSONObject().put("ss_tag_frequency", "qwen edit flux klein 9b")
        assertEquals(LoraCompatibility.UNKNOWN, EditLoras.compatibility(EditMotor.KLEIN, "portrait.safetensors", prompt))
    }

    @Test fun `vestavene lora se nenabizi podruhe`() {
        assertEquals(LoraCompatibility.BUILT_IN, EditLoras.compatibility(EditMotor.KREA2, "krea2_identity_edit_v1_2.safetensors"))
        assertEquals(LoraCompatibility.BUILT_IN, EditLoras.compatibility(EditMotor.QWEN, QwenEditBuilder.LORA_FILE))
    }
}
