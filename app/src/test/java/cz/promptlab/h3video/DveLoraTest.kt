package cz.promptlab.h3video

import cz.promptlab.h3video.comfy.ZImageBuilder
import cz.promptlab.h3video.data.Aspect
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

/** Řetězení dvou volitelných LoRA na kartě Obrázek. */
class DveLoraTest {
    private fun sablona(nazev: String): String =
        File("src/main/res/raw/$nazev").readText()

    private fun uzel(wf: JSONObject, id: String): JSONObject =
        wf.getJSONObject(id).getJSONObject("inputs")

    private fun zdrojModelu(wf: JSONObject, id: String): String =
        uzel(wf, id).getJSONArray("model").getString(0)

    private fun zimage(lora: String, lora2: String, sila2: Float = 1f): JSONObject =
        ZImageBuilder.build(
            template = sablona("workflow_zimage_t2i.json"),
            prompt = "test", aspect = Aspect.SQUARE_1_1, seed = 7L,
            nsfwLora = true, nsfwSila = 1f, model = "",
            loraFile = lora, loraFile2 = lora2, nsfwSila2 = sila2,
        )

    @Test fun `obe lory visi za sebou a vzorkovani bere tu druhou`() {
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

    @Test fun `bez druhe lory zustava retez jednoclanny`() {
        val wf = zimage("zimage_nsfw_v1.safetensors", "")
        assertFalse(wf.has(ZImageBuilder.N_NSFW_LORA2))
        assertEquals(ZImageBuilder.N_NSFW_LORA, zdrojModelu(wf, ZImageBuilder.N_SHIFT))
    }

    @Test fun `tataz lora dvakrat se do retezu nedostane`() {
        val wf = zimage("zimage_nsfw_v1.safetensors", "zimage_nsfw_v1.safetensors")
        assertFalse(wf.has(ZImageBuilder.N_NSFW_LORA2))
        assertEquals(ZImageBuilder.N_NSFW_LORA, zdrojModelu(wf, ZImageBuilder.N_SHIFT))
    }
}
