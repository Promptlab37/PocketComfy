package cz.promptlab.h3video

import cz.promptlab.h3video.comfy.AioBuilder
import cz.promptlab.h3video.data.AioMode
import cz.promptlab.h3video.data.AioScene
import cz.promptlab.h3video.data.AioSlot
import cz.promptlab.h3video.data.GenParams
import cz.promptlab.h3video.data.Mode
import cz.promptlab.h3video.data.aioProblem
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Režim **Přemalovat ve videu** (šablona `mask.json` balíku All in One).
 *
 * Na rozdíl od ostatních režimů si rozměry i délku určuje šablona sama —
 * šířka, výška a délka v podmínce jsou odkazy na výřez kolem sledovaného
 * objektu. Testy hlídají hlavně to, že je appka nepřepíše čísly: to je
 * chyba, kterou by překlad nechytil a projevila by se až tím, že vlepený
 * kus sedí mimo.
 */
class AioMaskTest {

    private val packDir = File(
        "D:/COMFYUI_SAGE_DVOJKA_TEST/custom_nodes/ComfyUI-ALLinONE-MinimaxH3/workflows"
    )

    private fun JSONObject.inputs(node: String): JSONObject =
        getJSONObject(node).getJSONObject("inputs")

    private fun JSONObject.classOf(node: String): String =
        getJSONObject(node).getString("class_type")

    /**
     * Kopie šablony `mask.json` — jen uzly, na které appka sahá, plus ty,
     * přes které vede zapojení. Čísla i třídy sedí s balíkem; hlídá to test
     * `cisla_uzlu_masky_sedi_s_balikem` níž.
     */
    private fun maskTemplate(): String {
        val wf = JSONObject()
        fun node(id: String, cls: String, inputs: JSONObject) {
            wf.put(id, JSONObject().put("class_type", cls).put("inputs", inputs))
        }
        fun link(id: String, slot: Int = 0) =
            org.json.JSONArray().put(id).put(slot)

        node("1", "CLIPLoader", JSONObject().put("clip_name", "").put("type", "minimax"))
        node("2", "UNETLoader", JSONObject().put("unet_name", "").put("weight_dtype", "default"))
        node("3", "VAELoader", JSONObject().put("vae_name", ""))
        node("4", "VAELoader", JSONObject().put("vae_name", ""))
        node(
            "5", "MiniMaxH3SigmaShift",
            JSONObject().put("model", link("2")).put("shift_video", 12).put("shift_audio", 3)
        )
        node(
            "6", "MiniMaxH3ReferenceToVideo",
            JSONObject()
                .put("clip", link("1")).put("vae", link("3")).put("audio_vae", link("4"))
                .put("prompt", "")
                .put("width", link("25", 0)).put("height", link("25", 1))
                .put("length", link("18", 4))
                .put("ref_image_size", "max")
                .put("ref_videos.ref_video_0", link("24"))
        )
        node("7", "BasicGuider", JSONObject().put("model", link("5")).put("conditioning", link("6")))
        node("8", "RandomNoise", JSONObject().put("noise_seed", 0))
        node(
            "9", "BasicScheduler",
            JSONObject().put("model", link("5")).put("scheduler", "simple")
                .put("steps", 20).put("denoise", 1)
        )
        node("10", "KSamplerSelect", JSONObject().put("sampler_name", "res_multistep"))
        node("11", "SamplerCustomAdvanced", JSONObject().put("guider", link("7")))
        node("16", "LoadVideo", JSONObject().put("file", ""))
        node(
            "18", "H3MaskVideoPrepare",
            JSONObject().put("images", link("17")).put("max_seconds", 5).put("target_fps", 24)
        )
        node("19", "CheckpointLoaderSimple", JSONObject().put("ckpt_name", ""))
        node("20", "CLIPTextEncode", JSONObject().put("text", "").put("clip", link("19", 1)))
        node(
            "21", "SAM3_VideoTrack",
            JSONObject().put("images", link("18")).put("model", link("19"))
                .put("conditioning", link("20"))
                .put("detection_threshold", 0.5).put("max_objects", 1).put("detect_interval", 5)
        )
        node("24", "MVEx_SubjectCrop", JSONObject().put("original_images", link("18")))
        node("25", "GetImageSize", JSONObject().put("image", link("24")))
        node(
            "34", "Video Slice",
            JSONObject().put("video", link("16")).put("start_time", 0).put("duration", 5)
        )
        return wf.toString()
    }

    private fun params() = GenParams(
        mode = Mode.ALLINONE,
        steps = 8,
        sampler = "euler",
        scheduler = "beta",
        seed = 4242L,
    )

    private fun scene(
        target: String = "head",
        objects: Int = 1,
        seconds: Float = 5f,
    ) = AioScene(
        mode = AioMode.MASK,
        prompt = "<Picture 1> replaces the head",
        seconds = seconds,
        maskTarget = target,
        maskObjects = objects,
        refs = listOf(AioSlot(key = 1, image = File("tvar.jpg"))),
        sourceVideo = File("zdroj.mp4"),
    )

    @Test
    fun `dosadi se video, cil sledovani a nahrady`() {
        val wf = AioBuilder.build(maskTemplate(), params(), scene(), listOf("tvar.jpg"), "zdroj.mp4")
        assertEquals("zdroj.mp4", wf.inputs(AioBuilder.N_EXTRA).getString("file"))
        assertEquals("head", wf.inputs(AioBuilder.N_MASK_TARGET).getString("text"))
        assertEquals(AioBuilder.SAM3_CKPT, wf.inputs(AioBuilder.N_MASK_SAM).getString("ckpt_name"))
        assertEquals(1, wf.inputs(AioBuilder.N_MASK_TRACK).getInt("max_objects"))
        // Náhrada visí na podmínce jako obyčejná reference.
        val ref = wf.inputs("6").getJSONArray("ref_images.ref_image_0").getString(0)
        assertEquals("LoadImage", wf.classOf(ref))
        assertEquals("tvar.jpg", wf.inputs(ref).getString("image"))
    }

    /**
     * Nejdůležitější test celého režimu. Šířka, výška a délka musí zůstat
     * odkazy — kdyby je appka přepsala čísly jako u ostatních režimů, výřez
     * a maska by si přestaly odpovídat.
     */
    @Test
    fun `rozmery a delka zustavaji odkazy na vyrez`() {
        val wf = AioBuilder.build(maskTemplate(), params(), scene(), listOf("tvar.jpg"), "zdroj.mp4")
        val cond = wf.inputs("6")
        assertTrue("sirka uz neni odkaz", cond.get("width") is org.json.JSONArray)
        assertTrue("vyska uz neni odkaz", cond.get("height") is org.json.JSONArray)
        assertTrue("delka uz neni odkaz", cond.get("length") is org.json.JSONArray)
        assertEquals("25", cond.getJSONArray("width").getString(0))
        assertEquals("18", cond.getJSONArray("length").getString(0))
        // Prompt se naopak dosadit musí.
        assertEquals("<Picture 1> replaces the head", cond.getString("prompt"))
        assertTrue(AioMode.MASK.fixedSize)
        assertFalse(AioMode.REFERENCE.fixedSize)
    }

    @Test
    fun `delka useku jde do orezu i do pripravy`() {
        val wf = AioBuilder.build(
            maskTemplate(), params(), scene(seconds = 8f), listOf("tvar.jpg"), "zdroj.mp4"
        )
        assertEquals(8.0, wf.inputs(AioBuilder.N_MASK_SLICE).getDouble("duration"), 0.001)
        assertEquals(8.0, wf.inputs(AioBuilder.N_MASK_PREPARE).getDouble("max_seconds"), 0.001)
    }

    /**
     * `mask.json` je jediná šablona balíku, která přichází s prázdnými
     * loadery — ostatní si své váhy nesou. Bez dosazení by ComfyUI graf
     * odmítl při validaci.
     */
    @Test
    fun `prazdne loadery se doplni`() {
        val wf = AioBuilder.build(maskTemplate(), params(), scene(), listOf("tvar.jpg"), "zdroj.mp4")
        assertEquals(AioBuilder.UNET_FL2VA, wf.inputs("2").getString("unet_name"))
        assertEquals(AioBuilder.VAE_VIDEO, wf.inputs("3").getString("vae_name"))
        assertEquals(AioBuilder.VAE_AUDIO, wf.inputs("4").getString("vae_name"))
        assertTrue(wf.inputs("1").getString("clip_name").isNotBlank())
    }

    @Test
    fun `pocet sledovanych objektu se drzi v rozsahu uzlu`() {
        val wf = AioBuilder.build(
            maskTemplate(), params(), scene(objects = 99), listOf("tvar.jpg"), "zdroj.mp4"
        )
        assertEquals(8, wf.inputs(AioBuilder.N_MASK_TRACK).getInt("max_objects"))
    }

    @Test
    fun `co karte chybi`() {
        assertNull(aioProblem(scene()))
        assertNotNull(aioProblem(scene().copy(sourceVideo = null)))
        assertNotNull(aioProblem(scene(target = "  ")))
        // Fotka náhrady je NEPOVINNÁ — uzel dostává i popis scény, takže
        // „nahraď hlavu helmou" projde i bez přiložené fotky helmy.
        assertNull(aioProblem(scene().copy(refs = listOf(AioSlot(key = 1)))))
    }

    @Test
    fun `nahrava se zdrojove video i fotky nahrad`() {
        val s = scene()
        assertEquals(listOf(File("zdroj.mp4")), listOfNotNull(s.uploadVideo))
        assertEquals(1, s.uploadImages.size)
        assertEquals("mask.json", s.sablona)
    }

    /**
     * Kontrola proti skutečnému balíku na tomhle počítači. Jinde se přeskočí —
     * čísla uzlů jsou cizí a musí se hlídat proti zdroji, ne proti kopii.
     */
    @Test
    fun `cisla uzlu masky sedi s balikem`() {
        val f = File(packDir, "mask.json")
        assumeTrue("balik All in One tu neni", f.isFile)
        val wf = JSONObject(f.readText().removePrefix("\uFEFF"))
        assertEquals("LoadVideo", wf.classOf(AioBuilder.N_EXTRA))
        assertEquals("Video Slice", wf.classOf(AioBuilder.N_MASK_SLICE))
        assertEquals("H3MaskVideoPrepare", wf.classOf(AioBuilder.N_MASK_PREPARE))
        assertEquals("CheckpointLoaderSimple", wf.classOf(AioBuilder.N_MASK_SAM))
        assertEquals("CLIPTextEncode", wf.classOf(AioBuilder.N_MASK_TARGET))
        assertEquals("SAM3_VideoTrack", wf.classOf(AioBuilder.N_MASK_TRACK))
        assertEquals("MiniMaxH3ReferenceToVideo", wf.classOf("6"))
        // A že opravdu přichází s prázdnými loadery.
        assertTrue(wf.inputs("2").getString("unet_name").isBlank())
        assertTrue(wf.inputs("3").getString("vae_name").isBlank())
    }
}
