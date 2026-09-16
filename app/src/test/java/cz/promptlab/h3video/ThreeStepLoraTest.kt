package cz.promptlab.h3video

import cz.promptlab.h3video.comfy.ThreeStepBuilder
import cz.promptlab.h3video.data.Aspect
import cz.promptlab.h3video.data.LoraEntry
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Karta 3 kroky nemá Power Lora Loader jako ostatní video karty — uživatelovy
 * LoRA se proto řetězí jako obyčejné `LoraLoaderModelOnly` **za** tu z předlohy
 * (TaoMate 3step), na které celý postup stojí. Do 3.49 se z karty nedosazovaly
 * vůbec: panel LoRA byl vidět, ale graf ho zahazoval.
 */
class ThreeStepLoraTest {

    private val sablona: String =
        File("src/main/res/raw/workflow_h3_3step.json").readText()

    private fun postav(lory: List<LoraEntry>): JSONObject =
        ThreeStepBuilder.build(sablona, "kocka", 5.0, Aspect.entries.first(), 1L, true, lory)

    private fun JSONObject.modelZ(node: String): String =
        getJSONObject(node).getJSONObject("inputs").getJSONArray("model").getString(0)

    @Test
    fun `bez lory zustane graf jako predloha`() {
        val wf = postav(emptyList())
        assertEquals(ThreeStepBuilder.N_LORA_PREDLOHA, wf.modelZ(ThreeStepBuilder.N_SHIFT))
        assertNull(wf.opt(ThreeStepBuilder.LORA_ID_OD.toString()))
    }

    @Test
    fun `lory se retezi za tu z predlohy a shift jede z posledni`() {
        val wf = postav(
            listOf(LoraEntry("detail.safetensors", true, 0.8f), LoraEntry("styl.safetensors", true, 1.2f))
        )
        val prvni = ThreeStepBuilder.LORA_ID_OD.toString()
        val druha = (ThreeStepBuilder.LORA_ID_OD + 1).toString()

        assertEquals(ThreeStepBuilder.N_LORA_PREDLOHA, wf.modelZ(prvni))
        assertEquals(prvni, wf.modelZ(druha))
        assertEquals(druha, wf.modelZ(ThreeStepBuilder.N_SHIFT))

        val a = wf.getJSONObject(prvni).getJSONObject("inputs")
        assertEquals("detail.safetensors", a.getString("lora_name"))
        assertEquals(0.8, a.getDouble("strength_model"), 0.001)
        assertEquals("LoraLoaderModelOnly", wf.getJSONObject(druha).getString("class_type"))
        assertEquals(1.2, wf.getJSONObject(druha).getJSONObject("inputs")
            .getDouble("strength_model"), 0.001)
    }

    @Test
    fun `vypnuta a prazdna lora se preskoci`() {
        val wf = postav(
            listOf(
                LoraEntry("vypnuta.safetensors", false, 1f),
                LoraEntry("", true, 1f),
                LoraEntry("platna.safetensors", true, 1f),
            )
        )
        val prvni = ThreeStepBuilder.LORA_ID_OD.toString()
        assertEquals("platna.safetensors",
            wf.getJSONObject(prvni).getJSONObject("inputs").getString("lora_name"))
        assertNull(wf.opt((ThreeStepBuilder.LORA_ID_OD + 1).toString()))
        assertEquals(prvni, wf.modelZ(ThreeStepBuilder.N_SHIFT))
    }

    /**
     * V předloze nesmí zůstat zadání, se kterým se exportovala — do veřejného
     * repa se 15. 9. 2026 takhle dostal celý uživatelův prompt. Appka do uzlu
     * vždycky dosazuje čerstvé zadání, tak tam žádné patřit nemá.
     */
    @Test
    fun `v sablone nesmi zustat zadne stare zadani`() {
        assertEquals(
            "",
            JSONObject(sablona).getJSONObject(ThreeStepBuilder.N_PROMPT)
                .getJSONObject("inputs").getString("value")
        )
    }

    @Test
    fun `lora z predlohy zustava nedotcena`() {
        val wf = postav(listOf(LoraEntry("moje.safetensors", true, 1f)))
        val predloha = wf.getJSONObject(ThreeStepBuilder.N_LORA_PREDLOHA).getJSONObject("inputs")
        assertTrue(predloha.getString("lora_name").contains("TaoMate"))
        assertEquals(1.0, predloha.getDouble("strength_model"), 0.001)
    }
}
