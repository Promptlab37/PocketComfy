package cz.promptlab.h3video

import cz.promptlab.h3video.comfy.ServerAudit
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Kontrola „co serveru chybí" se počítá z předloh workflow v APK. Testy
 * ověřují čistou logiku (sběr tříd, čtení nabídek, porovnání) bez sítě.
 */
class ServerAuditTest {

    private val sablona = """
        {
          "1": {"class_type": "UNETLoader",
                "inputs": {"unet_name": "model-a.safetensors", "weight_dtype": "default"}},
          "2": {"class_type": "LoadImageWithFilename", "inputs": {"image": "neco.png"}},
          "3": {"class_type": "KSampler",
                "inputs": {"model": ["1", 0], "steps": 10, "sampler_name": "euler"}}
        }
    """.trimIndent()

    @Test
    fun `sber tridy a textove vstupy, dosazovane hodnoty se preskakuji`() {
        val potreby = ServerAudit.collect(listOf(sablona))
        assertEquals(setOf("UNETLoader", "LoadImageWithFilename", "KSampler"), potreby.keys)
        // odkazy na uzly (pole) a čísla se neberou, texty ano
        assertEquals(
            listOf("sampler_name" to "euler"),
            potreby.getValue("KSampler")
        )
        // LoadImageWithFilename.image dosazuje appka — hodnota z předlohy je zástupná
        assertTrue(potreby.getValue("LoadImageWithFilename").isEmpty())
    }

    @Test
    fun `nabidka vyberu - starsi zapis s polem`() {
        val spec = JSONObject(
            """{"input": {"required": {"unet_name": [["a.safetensors","b.safetensors"], {}]}}}"""
        )
        assertEquals(
            listOf("a.safetensors", "b.safetensors"),
            ServerAudit.options(spec, "unet_name")
        )
    }

    @Test
    fun `nabidka vyberu - novejsi zapis COMBO s options`() {
        val spec = JSONObject(
            """{"input": {"required": {"model": ["COMBO", {"options": ["x.gguf","y.gguf"]}]}}}"""
        )
        assertEquals(listOf("x.gguf", "y.gguf"), ServerAudit.options(spec, "model"))
    }

    @Test
    fun `textovy vstup neni vyber a nekontroluje se`() {
        val spec = JSONObject(
            """{"input": {"required": {"text": ["STRING", {"multiline": true}]}}}"""
        )
        assertNull(ServerAudit.options(spec, "text"))
        assertNull(ServerAudit.options(spec, "neexistuje"))
    }

    @Test
    fun `predlohy v APK jdou nacist a maji zname tridy`() {
        // Stejné soubory, které audit čte v telefonu – kdyby některý zmizel
        // nebo přestal být platný JSON, spadne to tady a ne až u uživatele.
        val dir = File("src/main/res/raw")
        val texty = listOf(
            "workflow_h3_ultra.json", "workflow_krea2_edit.json",
            "workflow_seedvr2_upscale.json", "workflow_zimage_t2i.json",
            "workflow_ace_music.json", "workflow_qwen_restore.json",
            "workflow_ace_faceswap.json",
        ).map { File(dir, it).readText() }
        val potreby = ServerAudit.collect(texty)
        assertTrue("čekám aspoň 10 druhů uzlů, mám ${potreby.size}", potreby.size >= 10)
        assertTrue("SeedVR2VideoUpscaler" in potreby.keys)
    }

    @Test
    fun `dynamicky pridavane tridy jsou v kontrole taky`() {
        // LSI nody (Časová osa) a náhledový uzel v předlohách nejsou — do
        // grafu je přidávají stavitelé až za běhu. Kdyby vypadly z kontroly,
        // cizí server by prošel a karta pak spadla.
        assertEquals(
            listOf(
                "LSIMinimaxTimeline", "LSIMinimaxTimelineRender",
                "ModelPreviewOverrideKJ", "MiniMaxH3TeaCache",
            ),
            ServerAudit.PRIDAVANE_ZA_BEHU
        )
    }
}
