package cz.promptlab.h3video

import cz.promptlab.h3video.comfy.AioBuilder
import cz.promptlab.h3video.comfy.LongVideoBuilder
import cz.promptlab.h3video.comfy.Trellis2Builder
import cz.promptlab.h3video.data.AioMode
import cz.promptlab.h3video.data.AioScene
import cz.promptlab.h3video.data.AioSlot
import cz.promptlab.h3video.data.Aspect
import cz.promptlab.h3video.data.GenParams
import cz.promptlab.h3video.data.LongScene
import cz.promptlab.h3video.data.LongStart
import cz.promptlab.h3video.data.LongUsek
import cz.promptlab.h3video.data.Mode
import cz.promptlab.h3video.data.Model3dScene
import cz.promptlab.h3video.data.Ovlada
import cz.promptlab.h3video.data.Upscaler
import cz.promptlab.h3video.data.ovladaProKartu
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Karta nesmí nabízet volby, které stavitel grafu zahodí.
 *
 * Vzniklo z konkrétní výtky: karta chtěla nastavit délku u přemalování videa,
 * kde délku určuje zdrojové video, a celý panel Nastavení u zvětšení, kde se
 * model vůbec nespouští. Nabízet knoflík, který nic nedělá, je horší než ho
 * neukázat — uživatel podle něj rozhoduje a pak nechápe, proč se nic nezměnilo.
 *
 * Testy proto porovnávají graf postavený se DVĚMA hodně odlišnými nastaveními:
 * když se výsledek neliší, nastavení se na kartě nesmí ukazovat.
 */
class NastaveniKaretTest {

    private fun params(
        steps: Int = 8,
        aspect: Aspect = Aspect.LANDSCAPE_16_9,
        mp: Float = 0.4f,
        sampler: String = "euler",
        shift: Float = 12f,
    ) = GenParams(
        mode = Mode.ALLINONE,
        steps = steps,
        sampler = sampler,
        scheduler = "beta",
        seed = 42L,
        aspect = aspect,
        megapixels = mp,
        shiftVideo = shift,
    )

    /** Dvoje nastavení, která nemají společného skoro nic. */
    private val prvni = params(steps = 8, aspect = Aspect.LANDSCAPE_16_9, mp = 0.4f)
    private val druhe = params(
        steps = 33, aspect = Aspect.PORTRAIT_9_16, mp = 1.0f,
        sampler = "res_multistep", shift = 4f,
    )

    // --------------------------------------------------------------- pravidla

    @Test
    fun `zvetseni z nastaveni nepouzije nic`() {
        val o = ovladaProKartu(Mode.ALLINONE, AioMode.UPSCALE)
        assertEquals(Ovlada.NIC, o)
        assertFalse("panel Nastavení se u zvětšení nemá ukazovat vůbec", o.neco)
    }

    @Test
    fun `premalovani neurcuje platno, ale vzorkovani ano`() {
        val o = ovladaProKartu(Mode.ALLINONE, AioMode.MASK)
        assertFalse(o.rozliseni)
        assertTrue(o.kroky)
        assertTrue(o.model)
    }

    @Test
    fun `dlouhe video urcuje platno jen kdyz zacina od nuly`() {
        assertFalse(ovladaProKartu(Mode.LONG, dlouheNavazuje = true).rozliseni)
        assertTrue(ovladaProKartu(Mode.LONG, dlouheNavazuje = false).rozliseni)
    }

    @Test
    fun `bezne karty nabizeji vse`() {
        listOf(Mode.TALK, Mode.TIMELINE).forEach {
            assertEquals(Ovlada(), ovladaProKartu(it))
        }
        assertEquals(Ovlada(), ovladaProKartu(Mode.ALLINONE, AioMode.TEXT))
    }

    // ------------------------------------------- pravidla proti stavitelům

    /**
     * Zvětšení: graf musí vyjít IDENTICKY i s úplně jiným nastavením. Kdyby
     * se lišil, znamenalo by to, že panel Nastavení skrýváme neprávem.
     */
    @Test
    fun `zvetseni ignoruje nastaveni doopravdy`() {
        val sablona = upscaleTemplate()
        val scene = AioScene(mode = AioMode.UPSCALE, sourceVideo = File("v.mp4"))
        val a = AioBuilder.build(sablona, prvni, scene, emptyList(), "v.mp4")
        val b = AioBuilder.build(sablona, druhe, scene, emptyList(), "v.mp4")
        assertEquals(a.toString(), b.toString())
    }

    /**
     * Přemalování: rozlišení se do grafu nesmí promítnout, ale kroky ano.
     * Obojí najednou — jinak by test prošel i u karty, která ignoruje všechno.
     */
    @Test
    fun `premalovani ignoruje rozliseni, ale kroky bere`() {
        val sablona = maskTemplate()
        val scene = AioScene(
            mode = AioMode.MASK, prompt = "x", maskTarget = "head",
            sourceVideo = File("v.mp4"),
        )
        val a = AioBuilder.build(sablona, prvni, scene, emptyList(), "v.mp4")
        val b = AioBuilder.build(
            sablona, prvni.copy(aspect = Aspect.PORTRAIT_9_16, megapixels = 1.0f),
            scene, emptyList(), "v.mp4",
        )
        assertEquals("rozlišení se do přemalování promítlo", a.toString(), b.toString())

        val c = AioBuilder.build(sablona, prvni.copy(steps = 33), scene, emptyList(), "v.mp4")
        assertNotEquals("kroky se do přemalování NEpromítly", a.toString(), c.toString())
    }

    /**
     * Dlouhé video při navázání: plátno diktuje zdrojové video, takže se
     * rozlišení z nastavení nesmí nikam dostat.
     */
    @Test
    fun `dlouhe video pri navazani ignoruje rozliseni`() {
        val scene = LongScene(
            zacatek = LongStart.EXISTING_VIDEO,
            sourceVideo = File("v.mp4"),
            useky = listOf(LongUsek(1, "usek")),
        )
        val a = LongVideoBuilder.build(scene, prvni, 7L, "v.mp4", emptyList())
        val b = LongVideoBuilder.build(
            scene, prvni.copy(aspect = Aspect.PORTRAIT_9_16, megapixels = 1.0f),
            7L, "v.mp4", emptyList(),
        )
        assertEquals(a.toString(), b.toString())

        // A při začátku od nuly se naopak projevit MUSÍ.
        val odNuly = scene.copy(zacatek = LongStart.GENERATED, startPrompt = "zacatek")
        val c = LongVideoBuilder.build(odNuly, prvni, 7L, null, emptyList())
        val d = LongVideoBuilder.build(
            odNuly, prvni.copy(aspect = Aspect.PORTRAIT_9_16, megapixels = 1.0f),
            7L, null, emptyList(),
        )
        assertNotEquals(c.toString(), d.toString())
    }

    /** Karta 3D model nemá s videem nic společného a nesmí nabízet jeho volby. */
    @Test
    fun `3D model nepatri mezi videove karty`() {
        assertFalse(Mode.MODEL3D.isVideo)
        val sablona = File("src/main/res/raw/workflow_trellis2.json").readText()
        val scene = Model3dScene(source = File("f.jpg"))
        val a = Trellis2Builder.build(sablona, scene, 5L, listOf("f.jpg"))
        val b = Trellis2Builder.build(sablona, scene, 5L, listOf("f.jpg"))
        assertEquals(a.toString(), b.toString())
    }

    /**
     * Přemalování nevyžaduje fotku náhrady — uzel dostává i popis scény.
     * Vyžadovat ji bylo přehnané a uživatele to zbytečně zastavilo.
     */
    @Test
    fun `premalovani se spusti i bez fotky nahrady`() {
        val scene = AioScene(
            mode = AioMode.MASK, prompt = "nahrad hlavu helmou", maskTarget = "head",
            sourceVideo = File("v.mp4"), refs = emptyList(),
        )
        assertEquals(null, cz.promptlab.h3video.data.aioProblem(scene))
        // S fotkou samozřejmě taky.
        assertEquals(
            null,
            cz.promptlab.h3video.data.aioProblem(
                scene.copy(refs = listOf(AioSlot(key = 1, image = File("a.jpg"))))
            )
        )
    }

    // ------------------------------------------------------------- šablony

    private fun node(wf: JSONObject, id: String, cls: String, inputs: JSONObject) {
        wf.put(id, JSONObject().put("class_type", cls).put("inputs", inputs))
    }

    private fun link(id: String, slot: Int = 0) = org.json.JSONArray().put(id).put(slot)

    /** Zvětšení: šablona balíku nemá model ani prompt. */
    private fun upscaleTemplate(): String {
        val wf = JSONObject()
        node(wf, "1", "LoadVideo", JSONObject().put("file", ""))
        node(wf, "2", "GetVideoComponents", JSONObject().put("video", link("1")))
        node(
            wf, "5", "SeedVR2VideoUpscaler",
            JSONObject().put("images", link("2", 0)).put("resolution", 1080)
        )
        node(wf, "9", "SaveVideo", JSONObject().put("video", link("5")))
        return wf.toString()
    }

    /** Přemalování: jen uzly, na které stavitel sahá. */
    private fun maskTemplate(): String {
        val wf = JSONObject()
        node(wf, "1", "CLIPLoader", JSONObject().put("clip_name", "").put("type", "minimax"))
        node(wf, "2", "UNETLoader", JSONObject().put("unet_name", "").put("weight_dtype", "default"))
        node(wf, "3", "VAELoader", JSONObject().put("vae_name", ""))
        node(wf, "4", "VAELoader", JSONObject().put("vae_name", ""))
        node(
            wf, "5", "MiniMaxH3SigmaShift",
            JSONObject().put("model", link("2")).put("shift_video", 12).put("shift_audio", 3)
        )
        node(
            wf, "6", "MiniMaxH3ReferenceToVideo",
            JSONObject().put("clip", link("1")).put("vae", link("3")).put("audio_vae", link("4"))
                .put("prompt", "").put("width", link("25", 0)).put("height", link("25", 1))
                .put("length", link("18", 4)).put("ref_image_size", "max")
        )
        node(wf, "7", "BasicGuider", JSONObject().put("model", link("5")).put("conditioning", link("6")))
        node(wf, "8", "RandomNoise", JSONObject().put("noise_seed", 0))
        node(
            wf, "9", "BasicScheduler",
            JSONObject().put("model", link("5")).put("scheduler", "simple").put("steps", 20)
        )
        node(wf, "10", "KSamplerSelect", JSONObject().put("sampler_name", "res_multistep"))
        node(wf, "16", "LoadVideo", JSONObject().put("file", ""))
        node(
            wf, "18", "H3MaskVideoPrepare",
            JSONObject().put("max_seconds", 5).put("target_fps", 24)
        )
        node(wf, "19", "CheckpointLoaderSimple", JSONObject().put("ckpt_name", ""))
        node(wf, "20", "CLIPTextEncode", JSONObject().put("text", "").put("clip", link("19", 1)))
        node(wf, "21", "SAM3_VideoTrack", JSONObject().put("max_objects", 1))
        node(wf, "25", "GetImageSize", JSONObject().put("image", link("24")))
        node(wf, "24", "MVEx_SubjectCrop", JSONObject().put("original_images", link("18")))
        node(
            wf, "34", "Video Slice",
            JSONObject().put("video", link("16")).put("start_time", 0).put("duration", 5)
        )
        return wf.toString()
    }
}
