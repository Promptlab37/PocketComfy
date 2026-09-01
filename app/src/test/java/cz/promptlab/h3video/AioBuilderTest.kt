package cz.promptlab.h3video

import cz.promptlab.h3video.comfy.AioBuilder
import cz.promptlab.h3video.comfy.Stage
import cz.promptlab.h3video.data.AioMode
import cz.promptlab.h3video.data.AioScene
import cz.promptlab.h3video.data.AioSlot
import cz.promptlab.h3video.data.GenParams
import cz.promptlab.h3video.data.Mode
import cz.promptlab.h3video.data.Upscaler
import cz.promptlab.h3video.data.planExtend
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Karta All in One staví graf z cizí šablony, kterou si stahuje ze serveru.
 * Testy proto hlídají dvě různé věci:
 *
 *  1. **Dosazení a zapojení** – na kopiích šablon se stejnými čísly uzlů a
 *     třídami, jaké má balík. Tady vznikají chyby, které překlad neodhalí:
 *     uzel v grafu je, ale visí mimo řetězec.
 *  2. **Že se ta čísla nezměnila** – pokud je balík na tomhle počítači
 *     nainstalovaný, porovná se šablona z disku s tím, co appka předpokládá.
 *     Jinde se test přeskočí.
 */
class AioBuilderTest {

    private val packDir = File(
        "D:/COMFYUI_SAGE_DVOJKA_TEST/custom_nodes/ComfyUI-ALLinONE-MinimaxH3/workflows"
    )

    // ------------------------------------------------------------- kopie šablon

    /** Kostra videových šablon balíku: čísla uzlů 1–15, jak je má t2v/i2v/r2v. */
    private fun videoTemplate(
        condClass: String = "MiniMaxH3ImageToVideo",
        unet: String = "minimax_h3_fl2va_pruned_int8_convrot.safetensors",
        extra: JSONObject? = null,
    ): String {
        val wf = JSONObject()
        fun node(id: String, cls: String, inputs: JSONObject) {
            wf.put(id, JSONObject().put("class_type", cls).put("inputs", inputs))
        }
        fun link(id: String, slot: Int = 0) = JSONArray().put(id).put(slot)

        node("1", "CLIPLoader", JSONObject().put("clip_name", "qwen3vl_32b_minimax_h3_nvfp4_awq.safetensors"))
        node("2", "UNETLoader", JSONObject().put("unet_name", unet))
        node("3", "VAELoader", JSONObject().put("vae_name", "minimax_h3_video_vae_fp16.safetensors"))
        node("4", "VAELoader", JSONObject().put("vae_name", "minimax_h3_audio_vae_fp32.safetensors"))
        node("5", "MiniMaxH3SigmaShift", JSONObject()
            .put("model", link("2")).put("shift_video", 12).put("shift_audio", 3))
        val cond = JSONObject()
            .put("clip", link("1")).put("vae", link("3"))
            .put("prompt", "").put("width", 960).put("height", 544).put("length", 124)
        if (condClass == "MiniMaxH3ReferenceToVideo") cond.put("ref_image_size", "max")
        node("6", condClass, cond)
        node("7", "BasicGuider", JSONObject().put("model", link("5")).put("conditioning", link("6")))
        node("8", "RandomNoise", JSONObject().put("noise_seed", 0))
        node("9", "BasicScheduler", JSONObject()
            .put("model", link("5")).put("scheduler", "simple").put("steps", 30).put("denoise", 1))
        node("10", "KSamplerSelect", JSONObject().put("sampler_name", "res_multistep"))
        node("11", "SamplerCustomAdvanced", JSONObject()
            .put("noise", link("8")).put("guider", link("7"))
            .put("sampler", link("10")).put("sigmas", link("9"))
            .put("latent_image", link("6", 1)))
        node("12", "VAEDecode", JSONObject().put("samples", link("11")).put("vae", link("3")))
        node("13", "VAEDecodeAudio", JSONObject().put("samples", link("11")).put("vae", link("4")))
        node("14", "CreateVideo", JSONObject()
            .put("images", link("12")).put("fps", 24).put("audio", link("13")))
        node("15", "SaveVideo", JSONObject()
            .put("video", link("14")).put("filename_prefix", "one-node-minimax-h3/h3"))
        extra?.keys()?.forEach { wf.put(it, extra.getJSONObject(it)) }
        return wf.toString()
    }

    private fun keyframeTemplate(): String {
        val extra = JSONObject().put("16", JSONObject()
            .put("class_type", "MiniMaxH3CustomKeyframes")
            .put("inputs", JSONObject()
                .put("conditioning", JSONArray().put("6").put(0))
                .put("vae", JSONArray().put("3").put(0))
                .put("latent", JSONArray().put("6").put(1))
                .put("keyframe_state", "{\"count\":3,\"positions\":[1,62,124]}")
                .put("indexing", "1-based").put("crop", "center")))
        val wf = JSONObject(videoTemplate(extra = extra))
        // Guider bere podmínku od uzlu klíčových snímků, ne přímo od šestky.
        wf.getJSONObject("7").getJSONObject("inputs")
            .put("conditioning", JSONArray().put("16").put(0))
        return wf.toString()
    }

    private fun extendTemplate(): String {
        val extra = JSONObject()
            .put("16", JSONObject().put("class_type", "LoadVideo")
                .put("inputs", JSONObject().put("file", "").put("video-preview", "")))
            .put("18", JSONObject().put("class_type", "MiniMaxH3ExistingVideoMaskedContext")
                .put("inputs", JSONObject().put("context_length", 39).put("crop", "center")))
        return videoTemplate(extra = extra)
    }

    private fun upscaleTemplate(): String = JSONObject()
        .put("1", JSONObject().put("class_type", "LoadVideo")
            .put("inputs", JSONObject().put("file", "").put("video-preview", "")))
        .put("2", JSONObject().put("class_type", "GetVideoComponents")
            .put("inputs", JSONObject().put("video", JSONArray().put("1").put(0))))
        .put("5", JSONObject().put("class_type", "SeedVR2VideoUpscaler")
            .put("inputs", JSONObject().put("seed", 42).put("resolution", 1080)))
        .put("6", JSONObject().put("class_type", "CreateVideo")
            .put("inputs", JSONObject()
                .put("images", JSONArray().put("5").put(0))
                .put("fps", JSONArray().put("2").put(2))
                .put("audio", JSONArray().put("2").put(1))))
        .toString()

    /**
     * Kostra šablony listu postavy (charsheet.json): NEMÁ SigmaShift (5),
     * prompt jde přes StringConcatenate (17) a vedle videa se ukládá i slepený
     * list (SaveImage 29). Uzel 32 je alternativní, fotorealistická větev.
     */
    private fun charSheetTemplate(): String {
        val wf = JSONObject()
        fun node(id: String, cls: String, inputs: JSONObject) {
            wf.put(id, JSONObject().put("class_type", cls).put("inputs", inputs))
        }
        fun link(id: String, slot: Int = 0) = JSONArray().put(id).put(slot)

        node("1", "CLIPLoader", JSONObject().put("clip_name", "qwen3vl_32b_minimax_h3_nvfp4_awq.safetensors"))
        node("2", "UNETLoader", JSONObject().put("unet_name", "minimax_h3_ref2va_pruned_int8_convrot.safetensors"))
        node("3", "VAELoader", JSONObject().put("vae_name", "minimax_h3_video_vae_fp16.safetensors"))
        node("4", "VAELoader", JSONObject().put("vae_name", "minimax_h3_audio_vae_fp32.safetensors"))
        node("6", "MiniMaxH3ReferenceToVideo", JSONObject()
            .put("clip", link("1")).put("vae", link("3")).put("audio_vae", link("4"))
            .put("prompt", link("17"))
            .put("width", 480).put("height", 864).put("length", 124)
            .put("ref_image_size", "max"))
        node("7", "BasicGuider", JSONObject().put("model", link("2")).put("conditioning", link("6")))
        node("8", "RandomNoise", JSONObject().put("noise_seed", 0))
        node("9", "BasicScheduler", JSONObject()
            .put("model", link("2")).put("scheduler", "simple").put("steps", 30).put("denoise", 1))
        node("10", "KSamplerSelect", JSONObject().put("sampler_name", "res_multistep"))
        node("11", "SamplerCustomAdvanced", JSONObject()
            .put("noise", link("8")).put("guider", link("7"))
            .put("sampler", link("10")).put("sigmas", link("9"))
            .put("latent_image", link("6", 1)))
        node("12", "VAEDecode", JSONObject().put("samples", link("11")).put("vae", link("3")))
        node("16", "PrimitiveStringMultiline", JSONObject().put("value", "[STYLE] podle predlohy"))
        node("17", "StringConcatenate", JSONObject()
            .put("string_a", "").put("string_b", link("16")).put("delimiter", "\n\n"))
        node("29", "SaveImage", JSONObject()
            .put("images", link("12")).put("filename_prefix", "one-node-minimax-h3/charsheet/SHEET"))
        node("30", "CreateVideo", JSONObject().put("images", link("12")).put("fps", 24))
        node("31", "SaveVideo", JSONObject()
            .put("video", link("30")).put("filename_prefix", "one-node-minimax-h3/charsheet/orbit"))
        node("32", "PrimitiveStringMultiline", JSONObject().put("value", "[STYLE] fotorealisticky"))
        return wf.toString()
    }

    private fun params(
        steps: Int = 8,
        turbo: Boolean = true,
        sage: Boolean = true,
        preview: Boolean = false,
    ) = GenParams(
        mode = Mode.ALLINONE,
        steps = steps,
        sampler = "euler",
        scheduler = "beta",
        seed = 4242L,
        turboLoraOn = turbo,
        sageAttention = sage,
        livePreview = preview,
    )

    private fun JSONObject.inputs(node: String): JSONObject =
        getJSONObject(node).getJSONObject("inputs")

    private fun JSONObject.classOf(node: String): String =
        getJSONObject(node).getString("class_type")

    /** Projde řetěz modelu od uzlu 5 zpět k UNET loaderu a vrátí třídy po cestě. */
    private fun modelChain(wf: JSONObject): List<String> {
        val out = mutableListOf<String>()
        var id = wf.inputs("5").getJSONArray("model").getString(0)
        var pojistka = 0
        while (id != "2" && pojistka++ < 20) {
            out += wf.classOf(id)
            id = wf.inputs(id).getJSONArray("model").getString(0)
        }
        return out.reversed()
    }

    // ------------------------------------------------------------------- testy

    @Test
    fun `z textu dosadi prompt, rozmery a delku`() {
        val scene = AioScene(mode = AioMode.TEXT, prompt = "muz jde po plazi", seconds = 5f)
        val wf = AioBuilder.build(videoTemplate(), params(), scene)

        val cond = wf.inputs("6")
        assertEquals("muz jde po plazi", cond.getString("prompt"))
        assertEquals(scene.frames, cond.getInt("length"))
        // 5 s × 24 snímků = 120, po zaokrouhlení na mřížku 17k+5 je to 124
        assertEquals(124, cond.getInt("length"))
        assertEquals(4242L, wf.inputs("8").getLong("noise_seed"))
        assertEquals(8, wf.inputs("9").getInt("steps"))
        assertEquals("beta", wf.inputs("9").getString("scheduler"))
        assertEquals("euler", wf.inputs("10").getString("sampler_name"))
    }

    @Test
    fun `z obrazku pripoji prvni a posledni snimek`() {
        val scene = AioScene(
            mode = AioMode.IMAGE,
            prompt = "kamera se priblizuje",
            first = AioSlot(key = 1, image = File("a.jpg")),
            last = AioSlot(key = 2, image = File("b.jpg")),
            useLastFrame = true,
        )
        val wf = AioBuilder.build(videoTemplate(), params(), scene, listOf("a.jpg", "b.jpg"))

        val first = wf.inputs("6").getJSONArray("first_frame").getString(0)
        val last = wf.inputs("6").getJSONArray("last_frame").getString(0)
        assertEquals("LoadImage", wf.classOf(first))
        assertEquals("a.jpg", wf.inputs(first).getString("image"))
        assertEquals("b.jpg", wf.inputs(last).getString("image"))
    }

    @Test
    fun `vypnuty posledni snimek se do grafu nedostane`() {
        val scene = AioScene(
            mode = AioMode.IMAGE,
            prompt = "x",
            first = AioSlot(key = 1, image = File("a.jpg")),
            last = AioSlot(key = 2, image = File("b.jpg")),
            useLastFrame = false,
        )
        val wf = AioBuilder.build(videoTemplate(), params(), scene, listOf("a.jpg"))
        assertFalse(wf.inputs("6").has("last_frame"))
    }

    @Test
    fun `reference pripoji obrazky, kotvu totoznosti i video`() {
        val scene = AioScene(
            mode = AioMode.REFERENCE,
            prompt = "zena v kavarne",
            refs = listOf(
                AioSlot(key = 1, image = File("r1.jpg")),
                AioSlot(key = 2, image = File("r2.jpg")),
            ),
            refVideo = File("pohyb.mp4"),
            refVideoAudio = true,
        )
        val wf = AioBuilder.build(
            videoTemplate(condClass = "MiniMaxH3ReferenceToVideo"),
            params(), scene, listOf("r1.jpg", "r2.jpg"), "pohyb.mp4",
        )

        val cond = wf.inputs("6")
        assertEquals("LoadImage", wf.classOf(cond.getJSONArray("ref_images.ref_image_0").getString(0)))
        assertEquals("LoadImage", wf.classOf(cond.getJSONArray("ref_images.ref_image_1").getString(0)))

        // Kotva totožnosti musí VISET V ŘETĚZCI: guider bere podmínku od ní,
        // ne přímo od uzlu 6 – jinak by v grafu jen ležela a nic nedělala.
        val guiderCond = wf.inputs("7").getJSONArray("conditioning").getString(0)
        assertEquals("H3IdentityAnchor", wf.classOf(guiderCond))
        assertEquals(
            wf.inputs("6").getInt("length"),
            wf.inputs(guiderCond).getInt("frame_count")
        )

        val video = cond.getJSONArray("ref_videos.ref_video_0").getString(0)
        assertEquals("GetVideoComponents", wf.classOf(video))
        // zvuk z videa jde ze druhého výstupu téhož uzlu
        assertEquals(1, cond.getJSONArray("ref_video_audios.ref_video_audio_0").getInt(1))
    }

    @Test
    fun `bez zaskrtnuteho zvuku se stopa z referencniho videa nepripoji`() {
        val scene = AioScene(
            mode = AioMode.REFERENCE,
            prompt = "x",
            refVideo = File("pohyb.mp4"),
            refVideoAudio = false,
        )
        val wf = AioBuilder.build(
            videoTemplate(condClass = "MiniMaxH3ReferenceToVideo"),
            params(), scene, emptyList(), "pohyb.mp4",
        )
        assertFalse(wf.inputs("6").has("ref_video_audios.ref_video_audio_0"))
    }

    @Test
    fun `klicove snimky maji spravny stav i pozice`() {
        val scene = AioScene(
            mode = AioMode.KEYFRAMES,
            prompt = "promena",
            seconds = 5f,
            keys = listOf(
                AioSlot(key = 1, image = File("k1.jpg"), position = 1),
                AioSlot(key = 2, image = File("k2.jpg"), position = 62),
                // pozice za koncem videa se musí srovnat na poslední snímek
                AioSlot(key = 3, image = File("k3.jpg"), position = 999),
            ),
        )
        val wf = AioBuilder.build(
            keyframeTemplate(), params(), scene, listOf("k1.jpg", "k2.jpg", "k3.jpg")
        )

        val state = JSONObject(wf.inputs("16").getString("keyframe_state"))
        assertEquals(3, state.getInt("count"))
        assertEquals(1, state.getJSONArray("positions").getInt(0))
        assertEquals(62, state.getJSONArray("positions").getInt(1))
        assertEquals(scene.frames, state.getJSONArray("positions").getInt(2))
        assertEquals("k3.jpg", wf.inputs(
            wf.inputs("16").getJSONArray("keyframe_image_3").getString(0)
        ).getString("image"))
    }

    @Test
    fun `prodlouzeni nastavi kontext i celkovou delku`() {
        val scene = AioScene(
            mode = AioMode.EXTEND,
            prompt = "pokracuje dal",
            seconds = 5f,
            sourceVideo = File("zdroj.mp4"),
        )
        val wf = AioBuilder.build(extendTemplate(), params(), scene, emptyList(), "zdroj.mp4")

        val (kontext, cil, _) = planExtend(scene.seconds)
        assertEquals(39, kontext)
        assertEquals(kontext, wf.inputs("18").getInt("context_length"))
        assertEquals(cil, wf.inputs("6").getInt("length"))
        assertEquals("zdroj.mp4", wf.inputs("16").getString("file"))
        // cíl i kontext musí ležet na mřížce modelu
        assertEquals(5, cil % 17)
        assertEquals(5, kontext % 17)
    }

    @Test
    fun `zvetseni nesaha na snimkovou frekvenci ani zvuk`() {
        val scene = AioScene(
            mode = AioMode.UPSCALE,
            sourceVideo = File("hotovo.mp4"),
            upscaler = Upscaler.SEEDVR2,
            upscaleResolution = 1440,
        )
        val wf = AioBuilder.build(upscaleTemplate(), params(), scene, emptyList(), "hotovo.mp4")

        assertEquals("hotovo.mp4", wf.inputs("1").getString("file"))
        assertEquals(1440, wf.inputs("5").getInt("resolution"))
        // fps i zvuk zůstávají navázané na zdrojové video
        assertEquals("2", wf.inputs("6").getJSONArray("fps").getString(0))
        assertEquals("2", wf.inputs("6").getJSONArray("audio").getString(0))
        // model se u zvětšování vůbec nenačítá
        assertFalse(wf.has("2") && wf.classOf("2") == "UNETLoader")
    }

    @Test
    fun `retez modelu jde LoRA, Turbo, Sage a nakonec nahled`() {
        val scene = AioScene(mode = AioMode.TEXT, prompt = "x")
        val p = params(turbo = true, sage = true, preview = true).copy(
            extraLoras = listOf(cz.promptlab.h3video.data.LoraEntry("styl.safetensors", true, 0.8f))
        )
        val wf = AioBuilder.build(videoTemplate(), p, scene)

        assertEquals(
            listOf(
                "LoraLoaderModelOnly",              // uživatelova LoRA
                "LoraLoaderModelOnly",              // Turbo LoRA
                "MiniMaxH3MemoryEfficientSageAttentionPatch",
                "ModelPreviewOverrideKJ",           // náhled až úplně nakonec
            ),
            modelChain(wf)
        )
    }

    @Test
    fun `bez turba a sage visi sigma shift rovnou na modelu`() {
        val scene = AioScene(mode = AioMode.TEXT, prompt = "x")
        val wf = AioBuilder.build(
            videoTemplate(), params(turbo = false, sage = false, preview = false), scene
        )
        assertEquals(emptyList<String>(), modelChain(wf))
        assertEquals("2", wf.inputs("5").getJSONArray("model").getString(0))
    }

    @Test
    fun `zneplatneni mezipameti sedi mezi CLIP loaderem a zbytkem`() {
        val scene = AioScene(mode = AioMode.TEXT, prompt = "x")
        val wf = AioBuilder.build(videoTemplate(), params(), scene)

        assertEquals("H3CacheBust", wf.classOf("499"))
        assertEquals("1", wf.inputs("499").getJSONArray("clip").getString(0))
        // podmínka už nesmí brát CLIP přímo, jinak by se změna zadání „schovala"
        assertEquals("499", wf.inputs("6").getJSONArray("clip").getString(0))
        val otisk = wf.inputs("499").getString("fingerprint")
        assertTrue(otisk.contains("\"prompt\":\"x\""))
    }

    @Test
    fun `otisk se zmeni s jinym obrazkem`() {
        val a = AioScene(mode = AioMode.REFERENCE, prompt = "x", refs = listOf(
            AioSlot(key = 1, image = File("r1.jpg"))
        ))
        val b = a.copy(refs = listOf(AioSlot(key = 1, image = File("r2.jpg"))))
        assertTrue(
            AioBuilder.fingerprint(params(), a) != AioBuilder.fingerprint(params(), b)
        )
    }

    @Test
    fun `sablona v mezipameti zustane nedotcena`() {
        val template = videoTemplate()
        val scene = AioScene(mode = AioMode.TEXT, prompt = "novy text")
        AioBuilder.build(template, params(), scene)
        // build dostává text, ne objekt – druhé sestavení musí vyjít stejně
        val druhy = AioBuilder.build(template, params(), scene)
        assertEquals("novy text", druhy.inputs("6").getString("prompt"))
        assertEquals(124, druhy.inputs("6").getInt("length"))
    }

    @Test
    fun `faze se poznavaji podle tridy uzlu, ne podle cisla`() {
        // Uzel číslo 3 je u SeedVR2 načtení modelu, u RTX rovnou celé zvětšení –
        // podle čísel by ukazatel průběhu hlásil nesmysly.
        assertEquals(Stage.ENCODING, AioBuilder.stageForClass("RandomNoise"))
        assertEquals(Stage.SAMPLING, AioBuilder.stageForClass("SamplerCustomAdvanced"))
        assertEquals(Stage.SAMPLING, AioBuilder.stageForClass("SeedVR2VideoUpscaler"))
        assertEquals(Stage.MODELS, AioBuilder.stageForClass("SeedVR2LoadDiTModel"))
        assertEquals(Stage.MUXING, AioBuilder.stageForClass("SaveVideo"))
        // dlouhé části běhu dostávají skoro celou škálu
        assertEquals(0.08f to 0.86f, AioBuilder.rangeForClass("SamplerCustomAdvanced"))
        assertEquals(0.08f to 0.86f, AioBuilder.rangeForClass("RTXVideoSuperResolution"))
        assertTrue(AioBuilder.reportsSteps("SamplerCustomAdvanced"))
        assertFalse(AioBuilder.reportsSteps("CreateVideo"))
    }

    @Test
    fun `dialogy jedou na tehle sablone - fotky jako reference, repliky jako zvuk`() {
        // Karta Dialogy měla do 2.61 vlastní větev v ULTRA workflow; od 2.62
        // jede na téže šabloně jako All in One. Prompt si skládá scéna, sem
        // dorazí hotový.
        val prompt = "<Picture 1> (S1) says: <d>[Czech] Ahoj.</d>"
        val wf = AioBuilder.buildTalk(
            // r2v.json má v šabloně referenční váhy, stejně jako na serveru
            videoTemplate(
                condClass = "MiniMaxH3ReferenceToVideo",
                unet = "minimax_h3_ref2va_pruned_int8_convrot.safetensors",
            ),
            params().copy(unetFl2va = "muj_model.safetensors"),
            prompt = prompt,
            frames = 158,
            images = listOf("a.jpg", "b.jpg"),
            audios = listOf("r1.wav", "r2.wav"),
        )

        val cond = wf.inputs("6")
        assertEquals(prompt, cond.getString("prompt"))
        assertEquals(158, cond.getInt("length"))
        // 158 = 17×9+5, tedy na mřížce modelu
        assertEquals(5, cond.getInt("length") % 17)

        assertEquals("a.jpg", wf.inputs(cond.getJSONArray("ref_images.ref_image_0").getString(0))
            .getString("image"))
        assertEquals("b.jpg", wf.inputs(cond.getJSONArray("ref_images.ref_image_1").getString(0))
            .getString("image"))

        val zvuk0 = cond.getJSONArray("ref_audios.ref_audio_0").getString(0)
        assertEquals("LoadAudio", wf.classOf(zvuk0))
        assertEquals("r1.wav", wf.inputs(zvuk0).getString("audio"))
        assertEquals("r2.wav", wf.inputs(
            cond.getJSONArray("ref_audios.ref_audio_1").getString(0)
        ).getString("audio"))

        // Vlastní model se u referenční cesty NESMÍ dosadit – ref2va váhy
        // zůstávají ze šablony, komunitní přetrénování pro ně nevycházejí.
        assertEquals(
            "minimax_h3_ref2va_pruned_int8_convrot.safetensors",
            wf.inputs("2").getString("unet_name")
        )
        // a zneplatnění mezipaměti musí viset v řetězci i tady
        assertEquals("499", wf.inputs("6").getJSONArray("clip").getString(0))
    }

    @Test
    fun `mapa trid pokryva vsechny uzly hotoveho grafu`() {
        val scene = AioScene(mode = AioMode.TEXT, prompt = "x")
        val wf = AioBuilder.build(videoTemplate(), params(preview = true), scene)
        val mapa = AioBuilder.nodeClasses(wf)
        assertEquals(wf.length(), mapa.size)
        assertEquals("SamplerCustomAdvanced", mapa["11"])
        assertEquals("H3CacheBust", mapa["499"])
    }

    // ------------------------------------------------------------- list postavy

    @Test
    fun `list postavy dosadi popis, reference a seed, ale sablonu necha ridit vzorkovani`() {
        val scene = AioScene(
            mode = AioMode.CHARSHEET,
            prompt = "keep the face from Picture 1",
            refs = listOf(
                AioSlot(key = 1, image = File("r1.jpg")),
                AioSlot(key = 2, image = File("r2.jpg")),
            ),
        )
        val wf = AioBuilder.build(charSheetTemplate(), params(), scene, listOf("r1.jpg", "r2.jpg"))

        // Popis postavy jde PŘED pevný prompt šablony, do string_a uzlu 17.
        assertEquals("keep the face from Picture 1", wf.inputs("17").getString("string_a"))
        // Výchozí styl zůstává na větvi 16 (podle předlohy).
        assertEquals("16", wf.inputs("17").getJSONArray("string_b").getString(0))
        assertEquals(4242L, wf.inputs("8").getLong("noise_seed"))

        assertEquals("r1.jpg", wf.inputs(
            wf.inputs("6").getJSONArray("ref_images.ref_image_0").getString(0)
        ).getString("image"))
        assertEquals("r2.jpg", wf.inputs(
            wf.inputs("6").getJSONArray("ref_images.ref_image_1").getString(0)
        ).getString("image"))

        // Choreografie kamery je vyladěná na hodnoty šablony – kroky, sampler,
        // scheduler, rozměry ani délka se z parametrů NESMÍ přepsat.
        assertEquals(30, wf.inputs("9").getInt("steps"))
        assertEquals("simple", wf.inputs("9").getString("scheduler"))
        assertEquals("res_multistep", wf.inputs("10").getString("sampler_name"))
        assertEquals(480, wf.inputs("6").getInt("width"))
        assertEquals(124, wf.inputs("6").getInt("length"))

        // Bez SigmaShiftu se Sage zapojuje rovnou do guideru a scheduleru.
        val guiderModel = wf.inputs("7").getJSONArray("model").getString(0)
        assertEquals("MiniMaxH3MemoryEfficientSageAttentionPatch", wf.classOf(guiderModel))
        assertEquals(guiderModel, wf.inputs("9").getJSONArray("model").getString(0))
        // a zneplatnění mezipaměti visí v řetězci
        assertEquals("499", wf.inputs("6").getJSONArray("clip").getString(0))
    }

    @Test
    fun `fotorealisticky styl prepne prompt na vetev 32`() {
        val scene = AioScene(
            mode = AioMode.CHARSHEET, sheetPhotoreal = true,
            refs = listOf(AioSlot(key = 1, image = File("r1.jpg"))),
        )
        val wf = AioBuilder.build(charSheetTemplate(), params(), scene, listOf("r1.jpg"))
        assertEquals("32", wf.inputs("17").getJSONArray("string_b").getString(0))
    }

    @Test
    fun `pocet panelu vybira sablonu a meni otisk`() {
        val a = AioScene(mode = AioMode.CHARSHEET, sheetPanels = 6)
        val b = a.copy(sheetPanels = 4)
        assertEquals("charsheet.json", a.sablona)
        assertEquals("charsheet4.json", b.sablona)
        assertTrue(AioBuilder.fingerprint(params(), a) != AioBuilder.fingerprint(params(), b))
        assertTrue(
            AioBuilder.fingerprint(params(), a) !=
                AioBuilder.fingerprint(params(), a.copy(sheetPhotoreal = true))
        )
    }

    // ------------------------------------------------- scheduler referenční cesty

    @Test
    fun `v2 turbo posila u referenci simple scheduler, jinde beta`() {
        val v2 = cz.promptlab.h3video.data.Profile.V2_TURBO.applyTo(params())
        assertEquals("simple", v2.schedulerForRefPath)
        // ruční změna v Pokročilém má přednost před hodnotou profilu
        assertEquals("karras", v2.copy(scheduler = "karras").schedulerForRefPath)
        // profily bez vlastního referenčního scheduleru jedou beze změny
        assertEquals("beta", params().schedulerForRefPath)

        val refScene = AioScene(mode = AioMode.REFERENCE, prompt = "x",
            refs = listOf(AioSlot(key = 1, image = File("r1.jpg"))))
        val wfRef = AioBuilder.build(
            videoTemplate(condClass = "MiniMaxH3ReferenceToVideo"), v2, refScene, listOf("r1.jpg")
        )
        assertEquals("simple", wfRef.inputs("9").getString("scheduler"))

        // nereferenční cesta zůstává na obecném scheduleru
        val wfText = AioBuilder.build(videoTemplate(), v2, AioScene(mode = AioMode.TEXT, prompt = "x"))
        assertEquals("beta", wfText.inputs("9").getString("scheduler"))

        // Dialogy jedou referenční cestou taky
        val wfTalk = AioBuilder.buildTalk(
            videoTemplate(condClass = "MiniMaxH3ReferenceToVideo"), v2,
            prompt = "p", frames = 124, images = listOf("a.jpg"), audios = emptyList(),
        )
        assertEquals("simple", wfTalk.inputs("9").getString("scheduler"))
    }

    // ------------------------------------------- ověření proti skutečnému balíku

    @Test
    fun `skutecne sablony maji cisla uzlu, se kterymi appka pocita`() {
        assumeTrue("balík ALL-in-ONE není na tomhle počítači", packDir.isDirectory)

        fun tridy(soubor: String): Map<String, String> {
            val wf = JSONObject(File(packDir, soubor).readText())
            return wf.keys().asSequence().associateWith { wf.getJSONObject(it).getString("class_type") }
        }

        listOf("t2v.json", "i2v.json", "r2v.json", "keyframes.json", "video_extend.json").forEach { s ->
            val t = tridy(s)
            assertEquals("$s: uzel 1", "CLIPLoader", t["1"])
            assertEquals("$s: uzel 2", "UNETLoader", t["2"])
            assertEquals("$s: uzel 5", "MiniMaxH3SigmaShift", t["5"])
            assertEquals("$s: uzel 7", "BasicGuider", t["7"])
            assertEquals("$s: uzel 8", "RandomNoise", t["8"])
            assertEquals("$s: uzel 9", "BasicScheduler", t["9"])
            assertEquals("$s: uzel 11", "SamplerCustomAdvanced", t["11"])
            assertEquals("$s: uzel 14", "CreateVideo", t["14"])
            assertTrue("$s: uzel 6", t["6"]!!.startsWith("MiniMaxH3"))
        }
        assertEquals("MiniMaxH3CustomKeyframes", tridy("keyframes.json")["16"])
        assertEquals(
            "MiniMaxH3ExistingVideoMaskedContext",
            tridy("video_extend.json")["18"]
        )
        assertEquals("SeedVR2VideoUpscaler", tridy("upscale.json")["5"])
        assertEquals("RTXVideoSuperResolution", tridy("upscale_rtx.json")["3"])
        // List postavy: bez SigmaShiftu, prompt přes StringConcatenate,
        // fotorealistická větev na uzlu 32.
        listOf("charsheet.json", "charsheet4.json").forEach { s ->
            val t = tridy(s)
            assertEquals("$s: uzel 6", "MiniMaxH3ReferenceToVideo", t["6"])
            assertEquals("$s: uzel 7", "BasicGuider", t["7"])
            assertEquals("$s: uzel 8", "RandomNoise", t["8"])
            assertEquals("$s: uzel 17", "StringConcatenate", t["17"])
            assertEquals("$s: uzel 32", "PrimitiveStringMultiline", t["32"])
            assertFalse("$s: SigmaShift tam nepatří", t.containsKey("5"))
        }
        // Karta stahuje šablony pod těmito jmény – musí existovat.
        AioMode.entries.forEach { m ->
            assertNotNull(m.nazev, File(packDir, m.sablona).takeIf { it.isFile })
        }
    }

    @Test
    fun `graf postaveny na skutecne sablone projde stejne kontroly`() {
        assumeTrue("balík ALL-in-ONE není na tomhle počítači", packDir.isDirectory)
        val template = File(packDir, "keyframes.json").readText()
        val scene = AioScene(
            mode = AioMode.KEYFRAMES,
            prompt = "promena",
            keys = listOf(
                AioSlot(key = 1, image = File("k1.jpg"), position = 1),
                AioSlot(key = 2, image = File("k2.jpg"), position = 124),
            ),
        )
        val wf = AioBuilder.build(template, params(), scene, listOf("k1.jpg", "k2.jpg"))
        val state = JSONObject(wf.inputs("16").getString("keyframe_state"))
        assertEquals(2, state.getInt("count"))
        assertEquals("499", wf.inputs("6").getJSONArray("clip").getString(0))
        assertTrue(modelChain(wf).contains("MiniMaxH3MemoryEfficientSageAttentionPatch"))
    }

    @Test
    fun `list postavy na skutecne sablone - vsechny odkazy grafu vedou na existujici uzly`() {
        assumeTrue("balík ALL-in-ONE není na tomhle počítači", packDir.isDirectory)
        val template = File(packDir, "charsheet.json").readText()
        val scene = AioScene(
            mode = AioMode.CHARSHEET,
            prompt = "keep the face",
            refs = listOf(AioSlot(key = 1, image = File("r1.jpg"))),
        )
        val wf = AioBuilder.build(template, params(preview = true), scene, listOf("r1.jpg"))

        assertEquals("keep the face", wf.inputs("17").getString("string_a"))
        // Každý odkaz [id, slot] musí vést na uzel, který v grafu je – visící
        // odkaz by ComfyUI odmítl až při validaci na serveru.
        wf.keys().asSequence().toList().forEach { id ->
            val inputs = wf.getJSONObject(id).getJSONObject("inputs")
            inputs.keys().asSequence().toList().forEach { key ->
                val v = inputs.opt(key)
                if (v is JSONArray && v.length() == 2 && v.opt(0) is String) {
                    assertTrue("uzel $id → ${v.getString(0)}", wf.has(v.getString(0)))
                }
            }
        }
    }

    // ------------------------------------------------------ profil Fast (FastH3)

    @Test
    fun `fast profil ma ctyri kroky, euler a simple podle autoru destilace`() {
        val fast = cz.promptlab.h3video.data.Profile.FAST.applyTo(params())
        assertEquals(4, fast.steps)
        assertEquals("euler", fast.sampler)
        assertEquals("simple", fast.scheduler)
        assertEquals(12f, fast.shiftVideo)
        assertEquals(3f, fast.shiftAudio)
        assertTrue(fast.turboLoraOn)
        assertEquals(cz.promptlab.h3video.data.FASTH3_LORA, fast.turboLora)
        assertTrue(cz.promptlab.h3video.data.Profile.FAST.bezReferenci)
    }

    @Test
    fun `fast LoRA se dosadi na nereferencni ceste`() {
        val fast = cz.promptlab.h3video.data.Profile.FAST.applyTo(params())
        val wf = AioBuilder.build(videoTemplate(), fast, AioScene(mode = AioMode.TEXT, prompt = "x"))
        val retez = modelChain(wf)
        assertTrue("LoRA chybi v retezci: " + retez, retez.contains("LoraLoaderModelOnly"))
        val lora = retez.indexOf("LoraLoaderModelOnly")
        assertTrue(lora >= 0)
        // a je to opravdu FastH3, ne turbo
        val jmena = wf.keys().asSequence().toList().mapNotNull { id ->
            wf.optJSONObject(id)?.optJSONObject("inputs")?.optString("lora_name")?.takeIf { it.isNotEmpty() }
        }
        assertTrue(jmena.contains(cz.promptlab.h3video.data.FASTH3_LORA))
    }

    @Test
    fun `fast LoRA se NEdosadi na referencni ceste ani u dialogu`() {
        val fast = cz.promptlab.h3video.data.Profile.FAST.applyTo(params())
        val refScene = AioScene(mode = AioMode.REFERENCE, prompt = "x",
            refs = listOf(AioSlot(key = 1, image = File("r1.jpg"))))
        val wfRef = AioBuilder.build(
            videoTemplate(condClass = "MiniMaxH3ReferenceToVideo"), fast, refScene, listOf("r1.jpg")
        )
        val jmenaRef = wfRef.keys().asSequence().toList().mapNotNull { id ->
            wfRef.optJSONObject(id)?.optJSONObject("inputs")?.optString("lora_name")?.takeIf { it.isNotEmpty() }
        }
        assertFalse("fl2va LoRA nesmi na ref2va vahy", jmenaRef.contains(cz.promptlab.h3video.data.FASTH3_LORA))

        val wfTalk = AioBuilder.buildTalk(
            videoTemplate(condClass = "MiniMaxH3ReferenceToVideo"), fast,
            prompt = "p", frames = 124, images = listOf("a.jpg"), audios = emptyList(),
        )
        val jmenaTalk = wfTalk.keys().asSequence().toList().mapNotNull { id ->
            wfTalk.optJSONObject(id)?.optJSONObject("inputs")?.optString("lora_name")?.takeIf { it.isNotEmpty() }
        }
        assertFalse(jmenaTalk.contains(cz.promptlab.h3video.data.FASTH3_LORA))
    }

    @Test
    fun `bezne turbo LoRA na referencni ceste zustava`() {
        // Pojistka se smi tykat JEN profilu, ktery je oznaceny jako bez referenci.
        val turbo = cz.promptlab.h3video.data.Profile.V2_TURBO.applyTo(params())
        val wf = AioBuilder.build(
            videoTemplate(condClass = "MiniMaxH3ReferenceToVideo"), turbo,
            AioScene(mode = AioMode.REFERENCE, prompt = "x",
                refs = listOf(AioSlot(key = 1, image = File("r1.jpg")))), listOf("r1.jpg")
        )
        val jmena = wf.keys().asSequence().toList().mapNotNull { id ->
            wf.optJSONObject(id)?.optJSONObject("inputs")?.optString("lora_name")?.takeIf { it.isNotEmpty() }
        }
        assertTrue(jmena.contains(cz.promptlab.h3video.data.TURBO_V4))
    }
}
