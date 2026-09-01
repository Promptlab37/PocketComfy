package cz.promptlab.h3video

import cz.promptlab.h3video.comfy.Krea2Builder
import cz.promptlab.h3video.comfy.Stage
import cz.promptlab.h3video.data.Aspect
import cz.promptlab.h3video.data.ImageEditScene
import cz.promptlab.h3video.data.imageEditHints
import cz.promptlab.h3video.data.imageEditProblem
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Karta Úprava obrázku staví graf z předlohy v APK. Testy hlídají hlavně
 * zapojení — LoRA je trénovaná na to, že předloha jde do modelu DVĚMA cestami
 * naráz, takže když jedna vypadne, výsledek tiše ztratí podobu.
 */
class Krea2BuilderTest {

    private val sablona: String =
        File("src/main/res/raw/workflow_krea2_edit.json").readText()

    private fun JSONObject.inputs(node: String): JSONObject =
        getJSONObject(node).getJSONObject("inputs")

    private fun JSONObject.classOf(node: String): String =
        getJSONObject(node).getString("class_type")

    private fun scena() = ImageEditScene(
        source = File("foto.jpg"),
        prompt = "dej jí červenou bundu",
        aspect = Aspect.SQUARE_1_1,
        megapixels = 1f,
        groundingPx = 640,
        refBoost = 1.5f,
    )

    @Test
    fun `predloha jde do modelu i do enkoderu zaroven`() {
        val wf = Krea2Builder.build(sablona, scena(), seed = 42L, images = listOf("foto.jpg"))

        // 1) vzhled: latent i pixelová cesta do uzlu, který model záplatuje
        val patch = wf.inputs(Krea2Builder.N_PATCH)
        assertEquals("Krea2EditModelPatch", wf.classOf(Krea2Builder.N_PATCH))
        assertEquals(Krea2Builder.N_ENCODE, patch.getJSONArray("source_latent").getString(0))
        assertEquals(Krea2Builder.N_IMAGE, patch.getJSONArray("source_image").getString(0))
        assertEquals(Krea2Builder.N_VAE, patch.getJSONArray("vae").getString(0))

        // 2) sémantika: tatáž fotka jde i do textového enkodéru
        val pos = wf.inputs(Krea2Builder.N_POSITIVE)
        assertEquals(Krea2Builder.N_IMAGE, pos.getJSONArray("image").getString(0))
        assertEquals("dej jí červenou bundu", pos.getString("prompt"))

        // 3) vzorkovač bere model od záplaty, ne rovnou od LoRA
        assertEquals(
            Krea2Builder.N_PATCH,
            wf.inputs(Krea2Builder.N_SAMPLER).getJSONArray("model").getString(0)
        )
        assertEquals("foto.jpg", wf.inputs(Krea2Builder.N_IMAGE).getString("image"))
        assertEquals(42L, wf.inputs(Krea2Builder.N_SAMPLER).getLong("seed"))
    }

    @Test
    fun `dosadi se rozliseni, grounding a vernost predlohy`() {
        val wf = Krea2Builder.build(sablona, scena(), 1L, listOf("foto.jpg"))
        val res = scena().resolution
        assertEquals(res.width, wf.inputs(Krea2Builder.N_LATENT).getInt("width"))
        assertEquals(res.height, wf.inputs(Krea2Builder.N_LATENT).getInt("height"))
        assertEquals(640, wf.inputs(Krea2Builder.N_POSITIVE).getInt("grounding_px"))
        assertEquals(640, wf.inputs(Krea2Builder.N_NEGATIVE).getInt("grounding_px"))
        assertEquals(1.5, wf.inputs(Krea2Builder.N_PATCH).getDouble("ref_boost"), 0.001)
    }

    @Test
    fun `druha predloha se pripoji jako osoba a poradi zustane scena, pak osoba`() {
        val scene = scena().copy(person = File("osoba.jpg"))
        val wf = Krea2Builder.build(sablona, scene, 1L, listOf("foto.jpg", "osoba.jpg"))

        val patch = wf.inputs(Krea2Builder.N_PATCH)
        val bId = patch.getJSONArray("source_latent_b").getString(0)
        assertEquals("VAEEncode", wf.classOf(bId))
        val imgB = wf.inputs(bId).getJSONArray("pixels").getString(0)
        assertEquals("osoba.jpg", wf.inputs(imgB).getString("image"))
        // druhá předloha musí jít i do enkodéru, jinak ji model „neuvidí"
        assertEquals(imgB, wf.inputs(Krea2Builder.N_POSITIVE).getJSONArray("image_b").getString(0))
        // první zůstává scéna
        assertEquals("foto.jpg", wf.inputs(Krea2Builder.N_IMAGE).getString("image"))
    }

    @Test
    fun `bez druhe predlohy se uzly navic vubec nepridaji`() {
        val wf = Krea2Builder.build(sablona, scena(), 1L, listOf("foto.jpg"))
        assertFalse(wf.has(Krea2Builder.N_IMAGE_B))
        assertFalse(wf.has(Krea2Builder.N_ENCODE_B))
        assertFalse(wf.inputs(Krea2Builder.N_PATCH).has("source_latent_b"))
    }

    @Test
    fun `graf nema visici odkazy`() {
        val scene = scena().copy(person = File("osoba.jpg"))
        val wf = Krea2Builder.build(sablona, scene, 1L, listOf("foto.jpg", "osoba.jpg"))
        wf.keys().asSequence().toList().forEach { id ->
            val ins = wf.getJSONObject(id).getJSONObject("inputs")
            ins.keys().asSequence().toList().forEach { klic ->
                val v = ins.opt(klic)
                if (v is JSONArray && v.length() == 2 && v.opt(0) is String) {
                    assertTrue("uzel $id → ${v.getString(0)}", wf.has(v.getString(0)))
                }
            }
        }
    }

    @Test
    fun `sablona pouziva Krea 2 model, jeho enkoder a identity edit LoRA`() {
        val wf = JSONObject(sablona)
        assertTrue(wf.inputs("2").getString("unet_name").startsWith("krea2_"))
        assertEquals("krea2", wf.inputs("1").getString("type"))
        assertTrue(
            wf.inputs(Krea2Builder.N_LORA).getString("lora_name").contains("identity_edit")
        )
        // Turbo se vzorkuje bez guidance.
        assertEquals(1.0, wf.inputs(Krea2Builder.N_SAMPLER).getDouble("cfg"), 0.001)
        // 12 kroků: autorův „step dial" — 8 poslušnost, 12 detail obličeje.
        assertEquals(12, wf.inputs(Krea2Builder.N_SAMPLER).getInt("steps"))
        assertEquals("euler", wf.inputs(Krea2Builder.N_SAMPLER).getString("sampler_name"))
        assertEquals("simple", wf.inputs(Krea2Builder.N_SAMPLER).getString("scheduler"))
        // výsledkem je obrázek, ne video
        assertEquals("SaveImage", wf.classOf(Krea2Builder.N_SAVE))
    }

    @Test
    fun `sablona v mezipameti zustane nedotcena`() {
        Krea2Builder.build(sablona, scena().copy(prompt = "prvni"), 1L, listOf("a.jpg"))
        val druhy = Krea2Builder.build(sablona, scena().copy(prompt = "druhy"), 2L, listOf("b.jpg"))
        assertEquals("druhy", druhy.inputs(Krea2Builder.N_POSITIVE).getString("prompt"))
        assertEquals("b.jpg", druhy.inputs(Krea2Builder.N_IMAGE).getString("image"))
    }

    @Test
    fun `faze a kroky se poznavaji podle tridy uzlu`() {
        assertEquals(Stage.SAMPLING, Krea2Builder.stageForClass("KSampler"))
        assertEquals(Stage.MODELS, Krea2Builder.stageForClass("UNETLoader"))
        assertEquals(Stage.MUXING, Krea2Builder.stageForClass("SaveImage"))
        assertTrue(Krea2Builder.reportsSteps("KSampler"))
        assertFalse(Krea2Builder.reportsSteps("VAEDecode"))
    }

    @Test
    fun `validace chce fotku i zadani`() {
        assertEquals(
            "Vyber fotku, kterou chceš upravit.",
            imageEditProblem(ImageEditScene())
        )
        assertEquals(
            "Napiš, co se má na fotce změnit.",
            imageEditProblem(ImageEditScene(source = File("a.jpg")))
        )
        assertNull(imageEditProblem(scena()))
    }

    @Test
    fun `upozorni na mazani, ktere tenhle model neumi`() {
        val h = imageEditHints(scena().copy(prompt = "odeber ten stůl vlevo"))
        assertTrue(h.any { it.contains("Mazání") })
        assertTrue(imageEditHints(scena()).none { it.contains("Mazání") })
    }
}
