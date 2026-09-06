package cz.promptlab.h3video

import cz.promptlab.h3video.comfy.KleinEditBuilder
import cz.promptlab.h3video.data.ImageEditScene
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Úprava obrázku přes **FLUX.2 Klein 9B**.
 *
 * Editace u Kleina nestojí na převzorkování fotky (to by byl obyčejný
 * img2img), ale na tom, že se předloha zakóduje a přiváže k zadání uzlem
 * `ReferenceLatent` — a to k pozitivu **i k negativu**. Vzorkuje se do
 * prázdného plátna o rozměrech předlohy. Kdyby cokoli z toho chybělo,
 * výsledek by tiše přestal držet předlohu, aniž by běh spadl.
 */
class KleinEditTest {

    private val sablona: String
        get() = File("src/main/res/raw/workflow_flux2_klein_edit.json").readText()

    private fun graf(druhaFotka: Boolean = false): JSONObject = KleinEditBuilder.build(
        template = sablona,
        scene = ImageEditScene(prompt = "  dej mu klobouk  "),
        seed = 42L,
        images = if (druhaFotka) listOf("scena.png", "osoba.png") else listOf("scena.png"),
    )

    private fun vstupy(wf: JSONObject, id: String): JSONObject =
        wf.getJSONObject(id).getJSONObject("inputs")

    private fun zdroj(wf: JSONObject, id: String, vstup: String): Pair<String, Int> {
        val a = vstupy(wf, id).getJSONArray(vstup)
        return a.getString(0) to a.getInt(1)
    }

    @Test
    fun `dosazuje se fotka, zadani a seed`() {
        val wf = graf()
        assertEquals("scena.png", vstupy(wf, KleinEditBuilder.N_IMAGE).getString("image"))
        assertEquals("dej mu klobouk", vstupy(wf, KleinEditBuilder.N_TEXT).getString("text"))
        assertEquals(42L, vstupy(wf, KleinEditBuilder.N_NOISE).getLong("noise_seed"))
    }

    @Test
    fun `predloha visi na pozitivu i na negativu`() {
        val wf = graf()
        assertEquals(
            KleinEditBuilder.N_ENCODE to 0,
            zdroj(wf, KleinEditBuilder.N_REF_POS, "latent"),
        )
        assertEquals(
            KleinEditBuilder.N_ENCODE to 0,
            zdroj(wf, KleinEditBuilder.N_REF_NEG, "latent"),
        )
        assertEquals(KleinEditBuilder.N_REF_POS to 0, zdroj(wf, KleinEditBuilder.N_GUIDER, "positive"))
        assertEquals(KleinEditBuilder.N_REF_NEG to 0, zdroj(wf, KleinEditBuilder.N_GUIDER, "negative"))
    }

    @Test
    fun `vzorkuje se do prazdneho platna o rozmerech predlohy`() {
        val wf = graf()
        // Ne do latentu předlohy — ten jde do conditioningu, ne do plátna.
        assertEquals(
            KleinEditBuilder.N_LATENT to 0,
            zdroj(wf, KleinEditBuilder.N_SAMPLER, "latent_image"),
        )
        assertEquals(KleinEditBuilder.N_SIZE to 0, zdroj(wf, KleinEditBuilder.N_LATENT, "width"))
        assertEquals(KleinEditBuilder.N_SIZE to 1, zdroj(wf, KleinEditBuilder.N_LATENT, "height"))
        assertEquals(KleinEditBuilder.N_SIZE to 0, zdroj(wf, KleinEditBuilder.N_SCHEDULER, "width"))
        assertEquals(KleinEditBuilder.N_SIZE to 1, zdroj(wf, KleinEditBuilder.N_SCHEDULER, "height"))
    }

    @Test
    fun `bez druhe fotky se nic navic nepridava`() {
        val wf = graf()
        listOf(
            KleinEditBuilder.N_IMAGE2, KleinEditBuilder.N_SCALE2, KleinEditBuilder.N_ENCODE2,
            KleinEditBuilder.N_REF_POS2, KleinEditBuilder.N_REF_NEG2,
        ).forEach { assertFalse("uzel $it tu nemá co dělat", wf.has(it)) }
    }

    @Test
    fun `druha fotka se retezi za prvni, ne misto ni`() {
        val wf = graf(druhaFotka = true)

        assertEquals("osoba.png", vstupy(wf, KleinEditBuilder.N_IMAGE2).getString("image"))
        // Druhá reference navazuje na první — obě musí zůstat v řetězu.
        assertEquals(
            KleinEditBuilder.N_REF_POS to 0,
            zdroj(wf, KleinEditBuilder.N_REF_POS2, "conditioning"),
        )
        assertEquals(
            KleinEditBuilder.N_REF_NEG to 0,
            zdroj(wf, KleinEditBuilder.N_REF_NEG2, "conditioning"),
        )
        assertEquals(
            KleinEditBuilder.N_ENCODE2 to 0,
            zdroj(wf, KleinEditBuilder.N_REF_POS2, "latent"),
        )
        assertEquals(KleinEditBuilder.N_REF_POS2 to 0, zdroj(wf, KleinEditBuilder.N_GUIDER, "positive"))
        assertEquals(KleinEditBuilder.N_REF_NEG2 to 0, zdroj(wf, KleinEditBuilder.N_GUIDER, "negative"))
    }

    @Test
    fun `obe predlohy jdou pres stejny VAE jako prvni`() {
        val wf = graf(druhaFotka = true)
        assertEquals(
            zdroj(wf, KleinEditBuilder.N_ENCODE, "vae"),
            zdroj(wf, KleinEditBuilder.N_ENCODE2, "vae"),
        )
    }

    @Test
    fun `zadny uzel neodkazuje na neexistujici`() {
        listOf(false, true).forEach { druha ->
            val wf = graf(druha)
            val ids = wf.keys().asSequence().toSet()
            wf.keys().forEach { id ->
                val ins = wf.getJSONObject(id).getJSONObject("inputs")
                ins.keys().forEach { key ->
                    val v = ins.opt(key)
                    if (v is org.json.JSONArray && v.length() == 2 && v.opt(0) is String) {
                        assertTrue(
                            "$id.$key vede na neexistující uzel ${v.getString(0)}",
                            v.getString(0) in ids,
                        )
                    }
                }
            }
        }
    }
}
