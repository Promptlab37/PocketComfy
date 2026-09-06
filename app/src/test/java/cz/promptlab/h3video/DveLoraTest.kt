package cz.promptlab.h3video

import cz.promptlab.h3video.comfy.RestoreBuilder
import cz.promptlab.h3video.comfy.ZImageBuilder
import cz.promptlab.h3video.data.Aspect
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Řetězení LoRA na kartách **Obrázek** a **Oprava**.
 *
 * Jedna LoRA umí jednu věc — anatomii, nebo kůži, nebo styl. Spojit dvě jde
 * jen tak, že model projde oběma za sebou; `LoraLoaderModelOnly` bere právě
 * jednu. Testy hlídají, že řetěz opravdu vede skrz obě a že se do něj
 * nedostane táž LoRA dvakrát (to by jen zdvojilo sílu).
 */
class DveLoraTest {

    private fun sablona(nazev: String): String =
        File("src/main/res/raw/$nazev").readText()

    private fun uzel(wf: JSONObject, id: String): JSONObject =
        wf.getJSONObject(id).getJSONObject("inputs")

    /** Ze kterého uzlu bere vstup `model`. */
    private fun zdrojModelu(wf: JSONObject, id: String): String =
        uzel(wf, id).getJSONArray("model").getString(0)

    // ------------------------------------------------------ karta Obrázek

    private fun zimage(lora: String, lora2: String, sila2: Float = 1f): JSONObject =
        ZImageBuilder.build(
            template = sablona("workflow_zimage_t2i.json"),
            prompt = "test", aspect = Aspect.SQUARE_1_1, seed = 7L,
            nsfwLora = true, nsfwSila = 1f, model = "",
            loraFile = lora, loraFile2 = lora2, nsfwSila2 = sila2,
        )

    @Test
    fun `obe lory visi za sebou a vzorkovani bere tu druhou`() {
        val wf = zimage("zimage_nsfw_v1.safetensors", "zimage_skin_texture.safetensors", 0.8f)

        assertEquals(ZImageBuilder.N_UNET, zdrojModelu(wf, ZImageBuilder.N_NSFW_LORA))
        assertEquals(ZImageBuilder.N_NSFW_LORA, zdrojModelu(wf, ZImageBuilder.N_NSFW_LORA2))
        assertEquals(ZImageBuilder.N_NSFW_LORA2, zdrojModelu(wf, ZImageBuilder.N_SHIFT))

        assertEquals(
            "zimage_skin_texture.safetensors",
            uzel(wf, ZImageBuilder.N_NSFW_LORA2).getString("lora_name"),
        )
        assertEquals(0.8, uzel(wf, ZImageBuilder.N_NSFW_LORA2).getDouble("strength_model"), 1e-6)
    }

    @Test
    fun `bez druhe lory zustava retez jednoclanny`() {
        val wf = zimage("zimage_nsfw_v1.safetensors", "")
        assertFalse("uzel druhé LoRA nemá vzniknout", wf.has(ZImageBuilder.N_NSFW_LORA2))
        assertEquals(ZImageBuilder.N_NSFW_LORA, zdrojModelu(wf, ZImageBuilder.N_SHIFT))
    }

    @Test
    fun `tataz lora dvakrat se do retezu nedostane`() {
        val wf = zimage("zimage_nsfw_v1.safetensors", "zimage_nsfw_v1.safetensors")
        assertFalse(wf.has(ZImageBuilder.N_NSFW_LORA2))
        assertEquals(ZImageBuilder.N_NSFW_LORA, zdrojModelu(wf, ZImageBuilder.N_SHIFT))
    }

    // -------------------------------------------------------- karta Oprava

    private fun oprava(pokyn: String = "", lora: String = "", sila: Float = 1f): JSONObject =
        RestoreBuilder.build(
            template = sablona("workflow_qwen_restore.json"),
            seed = 3L, images = listOf("foto.png"),
            pokyn = pokyn, lora = lora, loraSila = sila,
        )

    @Test
    fun `bez nastaveni se predloha nemeni`() {
        val puvodni = JSONObject(sablona("workflow_qwen_restore.json"))
        val wf = oprava()
        assertFalse(wf.has(RestoreBuilder.N_LORA_CILENA))
        assertEquals(
            RestoreBuilder.N_LORA_POSLEDNI,
            zdrojModelu(wf, RestoreBuilder.N_SAMPLER),
        )
        assertEquals(
            uzel(puvodni, RestoreBuilder.N_PROMPT).getString("value"),
            uzel(wf, RestoreBuilder.N_PROMPT).getString("value"),
        )
    }

    @Test
    fun `cilena lora se vesi na konec retezu predlohy`() {
        val wf = oprava(lora = "m99_labiaplasty_pussy_4_qwen-image-edit-2511.safetensors", sila = 0.9f)

        assertEquals(
            RestoreBuilder.N_LORA_POSLEDNI,
            zdrojModelu(wf, RestoreBuilder.N_LORA_CILENA),
        )
        assertEquals(
            RestoreBuilder.N_LORA_CILENA,
            zdrojModelu(wf, RestoreBuilder.N_SAMPLER),
        )
        assertEquals(0.9, uzel(wf, RestoreBuilder.N_LORA_CILENA).getDouble("strength_model"), 1e-6)
    }

    @Test
    fun `lora uz nactenou predlohou karta nepridava podruhe`() {
        val uzTam = RestoreBuilder.loraVRetezu(JSONObject(sablona("workflow_qwen_restore.json")))
        assertTrue("předloha má načítat vlastní LoRA", uzTam.isNotEmpty())

        val wf = oprava(lora = uzTam.first())
        assertFalse(wf.has(RestoreBuilder.N_LORA_CILENA))
        assertEquals(RestoreBuilder.N_LORA_POSLEDNI, zdrojModelu(wf, RestoreBuilder.N_SAMPLER))
    }

    @Test
    fun `vlastni zadani nahrazuje to predlohove, nepridava se k nemu`() {
        // V předlohovém zadání stojí „no shape deformation" — kdyby zůstalo,
        // popřelo by právě tu opravu tvaru, kvůli které sem člověk píše.
        val wf = oprava(pokyn = "fix the anatomy")
        val zadani = uzel(wf, RestoreBuilder.N_PROMPT).getString("value")

        assertTrue(zadani.startsWith("fix the anatomy"))
        assertFalse(zadani.contains("no shape deformation"))
        assertTrue("kvalitní ocásek má zůstat", zadani.contains("ultra sharp"))
    }
}
