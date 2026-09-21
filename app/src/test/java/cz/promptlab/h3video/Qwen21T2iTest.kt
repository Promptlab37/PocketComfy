package cz.promptlab.h3video

import cz.promptlab.h3video.comfy.T2iModel
import cz.promptlab.h3video.comfy.ZImageBuilder
import cz.promptlab.h3video.data.Aspect
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Qwen Image 2.1 na kartě **Obrázek**. Je to tentýž model, co jede na kartě
 * Úprava obrázku — proto se hlídá, že obě předlohy sahají na stejné soubory
 * a že se drží hodnot z oficiální předlohy ComfyUI.
 */
class Qwen21T2iTest {

    private val sablona = File("src/main/res/raw/workflow_qwen21_t2i.json").readText()
    private val sablonaUpravy = File("src/main/res/raw/workflow_qwen21_edit.json").readText()

    private fun graf(aspect: Aspect = Aspect.SQUARE_1_1, kroky: Int = 0, cfg: Float = 0f) =
        ZImageBuilder.build(
            template = sablona, prompt = "kocka na gauci", aspect = aspect, seed = 7L,
            model = T2iModel.QWEN21.id, kroky = kroky, cfg = cfg,
        )

    private fun inputs(wf: JSONObject, uzel: String): JSONObject =
        wf.getJSONObject(uzel).getJSONObject("inputs")

    @Test
    fun `zadani a seed se dosadi`() {
        val wf = graf()
        assertEquals("kocka na gauci", inputs(wf, ZImageBuilder.N_Q21_TEXT).getString("prompt"))
        assertEquals(7L, inputs(wf, ZImageBuilder.N_Q21_SAMPLER).getLong("seed"))
    }

    @Test
    fun `rozmer urcuje prazdne platno, ne enkoder`() {
        val wf = graf(Aspect.LANDSCAPE_16_9)
        val (w, h) = ZImageBuilder.sizeFor(Aspect.LANDSCAPE_16_9)
        assertEquals(w, inputs(wf, ZImageBuilder.N_Q21_LATENT).getInt("width"))
        assertEquals(h, inputs(wf, ZImageBuilder.N_Q21_LATENT).getInt("height"))
        // Vzorkování si latent bere z plátna, ne z textového enkodéru — ten
        // bez předlohy žádnou velikost nezná.
        assertEquals(
            ZImageBuilder.N_Q21_LATENT,
            inputs(wf, ZImageBuilder.N_Q21_SAMPLER).getJSONArray("latent_image").getString(0),
        )
    }

    @Test
    fun `vychozi vzorkovani drzi oficialni predlohu`() {
        val s = inputs(graf(), ZImageBuilder.N_Q21_SAMPLER)
        assertEquals(25, s.getInt("steps"))
        assertEquals(1.0, s.getDouble("cfg"), 1e-9)
        assertEquals("euler", s.getString("sampler_name"))
        assertEquals("simple", s.getString("scheduler"))
        assertEquals(25, T2iModel.QWEN21.kroky)
    }

    @Test
    fun `nastaveni uzivatele prebije predlohu`() {
        val s = inputs(graf(kroky = 40, cfg = 2.5f), ZImageBuilder.N_Q21_SAMPLER)
        assertEquals(40, s.getInt("steps"))
        assertEquals(2.5, s.getDouble("cfg"), 1e-6)
    }

    @Test
    fun `obe karty jedou na stejnych souborech`() {
        val t2i = JSONObject(sablona)
        val edit = JSONObject(sablonaUpravy)
        listOf("1" to "unet_name", "2" to "clip_name", "3" to "vae_name").forEach { (uzel, pole) ->
            assertEquals(
                inputs(edit, uzel).getString(pole),
                inputs(t2i, uzel).getString(pole),
            )
        }
    }

    @Test
    fun `graf ma cache i enkoder Qwenu 2_1`() {
        val wf = graf()
        assertEquals("QwenImage21Cache", wf.getJSONObject("4").getString("class_type"))
        assertEquals(
            "TextEncodeQwenImage21",
            wf.getJSONObject(ZImageBuilder.N_Q21_TEXT).getString("class_type"),
        )
        // Cache sedí mezi modelem a vzorkováním, jinak by se neuplatnila.
        assertEquals(
            "4",
            inputs(wf, ZImageBuilder.N_Q21_SAMPLER).getJSONArray("model").getString(0),
        )
    }

    @Test
    fun `bez predlohy se do enkoderu neposilaji zadne obrazky`() {
        val e = inputs(graf(), ZImageBuilder.N_Q21_TEXT)
        assertTrue(e.keys().asSequence().none { it.startsWith("images") })
    }
}
