package cz.promptlab.h3video.comfy

import cz.promptlab.h3video.data.GenParams
import cz.promptlab.h3video.data.LongScene
import cz.promptlab.h3video.data.LongStart
import cz.promptlab.h3video.data.framesForSeconds
import org.json.JSONArray
import org.json.JSONObject

/**
 * Karta **Dlouhé video** — až šest navazujících úseků v JEDNOM běhu.
 *
 * Postavené na balíku `ComfyUI-H3-Motion-Context-MultiRef`. Každý úsek je
 * vlastní vzorkování, které dostane chráněný začátek (kontext) z konce
 * předchozího, takže na sebe navazují bez střihu. Poslední uzel
 * `MiniMaxH3StreamLiveExtensionAVToVHS` dekóduje klip po klipu a rovnou je
 * streamuje do H.264 MP4 — proto se na 16GB kartě vejde i minuta a půl videa.
 *
 * Zapojení řetězu je převzaté z Aitrepreneurova ULTRA V3 workflow (skupiny
 * EXTEND VIDEO a LONG TXT/IMG TO VIDEO), ale graf se nestaví z jeho souboru:
 * jména vstupů jsou přečtená přímo ze zdrojů uzlů, protože jeho export je
 * v editorové podobě se subgrafy a jeden odkaz v něm chybí.
 *
 * ```
 *  UNETLoader → LoRA (společné) → SigmaShift → SageAttention → Spectrum
 *                                                   │
 *              ┌────────────────────────────────────┴─── BasicScheduler
 *              ↓
 *      LoRA úseku i → BasicGuider ← ReferenceToVideo(zadání úseku i)
 *                          ↓
 *                  SamplerCustomAdvanced ← maskovaný kontext
 *                          ↓                (úsek 1: StartMaskedContext,
 *                    extension_i             další: GeneratedAVMaskedContext
 *                          ↓                 s latentem předchozího úseku)
 *          StreamLiveExtensionAVToVHS → jedno MP4
 * ```
 */
object LongVideoBuilder {

    /** Kolik úseků uzel `MiniMaxH3StreamLiveExtensionAVToVHS` zvládne. */
    const val MAX_USEKU = 6

    // --- pevné uzly --------------------------------------------------------
    const val N_CLIP = "1"
    const val N_UNET = "2"
    const val N_VAE_VIDEO = "3"
    const val N_VAE_AUDIO = "4"
    const val N_LORA_SPOLECNA = "5"
    const val N_SHIFT = "6"
    const val N_SAGE = "7"
    const val N_SPECTRUM = "8"
    const val N_SAMPLER_SELECT = "10"
    const val N_SCHEDULER = "11"

    /** Navázání na hotové video. */
    const val N_LOAD_VIDEO = "20"
    const val N_VIDEO_PARTS = "21"
    const val N_CROP32 = "22"
    const val N_CANVAS = "30"

    /** Vygenerovaný začátek (když se nenavazuje na existující video). */
    const val N_START_COND = "40"
    const val N_START_GUIDER = "41"
    const val N_START_NOISE = "42"
    const val N_START_SAMPLER = "43"

    /** Rychlý začátek: dvouprůchodová cesta s neuronovým zvětšením latentu. */
    const val N_FAST_SEPARATE = "44"
    const val N_FAST_UPSCALE = "45"
    const val N_FAST_CONCAT = "46"
    const val N_FAST_COND = "47"
    const val N_FAST_GUIDER = "48"
    const val N_FAST_SIGMAS = "49"
    const val N_FAST_SAMPLER = "50"

    const val N_STREAM = "99"

    /** Model neuronového zvětšovače latentu (models/latent_upscale_models). */
    const val UPSCALER_MODEL = "minimax_h3_latent_upscaler_3d_fp16.safetensors"

    /**
     * Sigmy doostřovacího průchodu, převzaté z Aitrepreneurova FAST řetězu.
     * Nezačínají na jedničce schválně — obraz už existuje, jen se dopočítává
     * detail, který se do malého plátna nevešel.
     */
    const val REFINE_SIGMAS = "0.9231, 0.8780, 0.8000, 0.6316, 0.3158, 0.0000"

    /** Podíl plochy prvního průchodu proti cílovému rozlišení. */
    const val FAST_PODIL = 0.2f

    /**
     * Hodnota `start_mode`, kterou uzly řetězu čekají.
     *
     * Pozor na past: přepínač `MiniMaxH3ExtensionStartMode` vrací
     * `load_video`, jenže streamovací uzel porovnává jen s `existing_video` —
     * s `load_video` by sáhl po vygenerovaném začátku a spadl by na chybějící
     * `starter_latent`. Kontext i výběr plátna berou obojí, proto se všude
     * posílá `existing_video`.
     */
    const val START_EXISTING = "existing_video"
    const val START_GENERATED = "generate_starter"

    /** Chráněný začátek úseku ve snímcích a doběh zvuku v ticích (40 Hz). */
    const val CONTEXT_FRAMES = 39
    const val AUDIO_FEATHER = 8
    const val FPS = 24.0

    fun uzelUseku(i: Int, cast: Int): String = (100 + i * 10 + cast).toString()

    /** Podmínka, maskovaný kontext, vedení, šum a vzorkování jednoho úseku. */
    fun condUseku(i: Int) = uzelUseku(i, 0)
    fun kontextUseku(i: Int) = uzelUseku(i, 1)
    fun guiderUseku(i: Int) = uzelUseku(i, 2)
    fun sumUseku(i: Int) = uzelUseku(i, 3)
    fun samplerUseku(i: Int) = uzelUseku(i, 4)

    private fun node(cls: String, title: String, inputs: JSONObject): JSONObject =
        JSONObject()
            .put("class_type", cls)
            .put("inputs", inputs)
            .put("_meta", JSONObject().put("title", title))

    private fun link(id: String, slot: Int = 0): JSONArray =
        JSONArray().put(id).put(slot)

    fun build(
        scene: LongScene,
        p: GenParams,
        seed: Long,
        video: String? = null,
        images: List<String> = emptyList(),
    ): JSONObject {
        val wf = JSONObject()
        val useky = scene.aktivniUseky
        require(useky.isNotEmpty()) { "Dlouhé video potřebuje aspoň jeden úsek." }
        val navazuje = scene.zacatek == LongStart.EXISTING_VIDEO
        val startMode = if (navazuje) START_EXISTING else START_GENERATED

        // --- modely --------------------------------------------------------
        wf.put(
            N_CLIP, node(
                "CLIPLoader", "Textový enkodér", JSONObject()
                    .put("clip_name", p.clipName)
                    .put("type", "minimax")
                    .put("device", "default")
            )
        )
        // Řetěz jede na referenčních vahách — každý úsek dostává jako
        // podmínku ReferenceToVideo, ne ImageToVideo.
        wf.put(
            N_UNET, node(
                "UNETLoader", "MiniMax H3 (ref2va)", JSONObject()
                    .put("unet_name", AioBuilder.UNET_REF2VA)
                    .put("weight_dtype", "default")
            )
        )
        wf.put(N_VAE_VIDEO, node("VAELoader", "VAE obrazu", JSONObject().put("vae_name", AioBuilder.VAE_VIDEO)))
        wf.put(N_VAE_AUDIO, node("VAELoader", "VAE zvuku", JSONObject().put("vae_name", AioBuilder.VAE_AUDIO)))

        // Společná LoRA (platí pro všechny úseky) → sigma shift → Sage → Spectrum.
        // Pořadí je z předlohy: LoRA jednotlivých úseků se věší až na konec.
        var model = link(N_UNET, 0)
        if (scene.spolecnaLora.isNotBlank()) {
            wf.put(
                N_LORA_SPOLECNA, node(
                    "LoraLoaderModelOnly", "Společná LoRA", JSONObject()
                        .put("model", model)
                        .put("lora_name", scene.spolecnaLora)
                        .put("strength_model", scene.spolecnaLoraSila.toDouble())
                )
            )
            model = link(N_LORA_SPOLECNA, 0)
        }
        wf.put(
            N_SHIFT, node(
                "MiniMaxH3SigmaShift", "Sigma shift", JSONObject()
                    .put("model", model)
                    .put("shift_video", p.shiftVideo.toDouble())
                    .put("shift_audio", p.shiftAudio.toDouble())
            )
        )
        model = link(N_SHIFT, 0)
        if (p.sageAttention) {
            wf.put(
                N_SAGE, node(
                    "PathchSageAttentionKJ", "Sage Attention", JSONObject()
                        .put("model", model)
                        .put("sage_attention", "auto")
                )
            )
            model = link(N_SAGE, 0)
        }
        wf.put(
            N_SPECTRUM, node(
                "SpectrumApplyMiniMaxH3", "Spectrum", JSONObject()
                    .put("model", model)
                    .put("enabled", true)
                    .put("blend_weight", 0.5)
                    .put("degree", 1)
                    .put("ridge_lambda", 0.1)
                    .put("window_size", 2)
                    .put("flex_window", 0.75)
                    .put("warmup_steps", 1)
                    .put("tail_actual_steps", 1)
                    .put("max_history", 8)
                    .put("debug", false)
            )
        )
        val modelZaklad = link(N_SPECTRUM, 0)

        wf.put(N_SAMPLER_SELECT, node("KSamplerSelect", "Vzorkovač", JSONObject().put("sampler_name", p.sampler)))
        wf.put(
            N_SCHEDULER, node(
                "BasicScheduler", "Plán kroků", JSONObject()
                    .put("model", modelZaklad)
                    .put("scheduler", p.schedulerForRefPath)
                    .put("steps", p.steps)
                    .put("denoise", 1.0)
            )
        )

        // --- začátek řetězu -------------------------------------------------
        val res = p.resolution
        if (navazuje) {
            wf.put(N_LOAD_VIDEO, node("LoadVideo", "Video k navázání", JSONObject().put("file", video.orEmpty())))
            wf.put(
                N_VIDEO_PARTS,
                node("GetVideoComponents", "Rozklad videa", JSONObject().put("video", link(N_LOAD_VIDEO)))
            )
            // H3 pracuje po 32 pixelech; ořez dolů je levnější než dopočítávat.
            wf.put(
                N_CROP32,
                node("MiniMaxH3CropTo32", "Ořez na násobek 32", JSONObject().put("images", link(N_VIDEO_PARTS, 0)))
            )
        }
        // Při navázání určuje plátno zdrojové video (uzel si vybere source_*),
        // takže se do grafu posílají výchozí hodnoty uzlu, ne uživatelovo
        // rozlišení — jinak by graf vypadal, že na něm záleží.
        val platnoW = if (navazuje) 960 else res.width
        val platnoH = if (navazuje) 544 else res.height
        wf.put(
            N_CANVAS, node(
                "MiniMaxH3StartCanvasSelector", "Plátno", JSONObject()
                    .put("start_mode", startMode)
                    .put("generated_width", platnoW)
                    .put("generated_height", platnoH)
                    .apply {
                        if (navazuje) {
                            put("source_width", link(N_CROP32, 1))
                            put("source_height", link(N_CROP32, 2))
                        }
                    }
            )
        )

        // Reference drží podobu postav napříč všemi úseky — připojují se
        // ke každé podmínce zvlášť, protože každý úsek je vlastní běh.
        val refIds = images.mapIndexed { idx, name ->
            val id = (300 + idx).toString()
            wf.put(
                id, node(
                    "LoadImage", "Reference ${idx + 1}",
                    JSONObject().put("image", name).put("upload", "image")
                )
            )
            id
        }

        fun podminka(
            id: String, titulek: String, prompt: String, snimku: Int,
            sirka: Int? = null, vyska: Int? = null,
        ) {
            wf.put(
                id, node(
                    "MiniMaxH3ReferenceToVideo", titulek, JSONObject()
                        .put("clip", link(N_CLIP, 0))
                        .put("vae", link(N_VAE_VIDEO, 0))
                        .put("audio_vae", link(N_VAE_AUDIO, 0))
                        .put("prompt", prompt)
                        .put("width", sirka ?: link(N_CANVAS, 0))
                        .put("height", vyska ?: link(N_CANVAS, 1))
                        .put("length", snimku)
                        .put("ref_image_size", p.refImageSize)
                        .apply {
                            refIds.forEachIndexed { idx, rid -> put("ref_images.ref_image_$idx", link(rid)) }
                        }
                )
            )
        }

        // Vygenerovaný začátek: první klip vznikne jako obyčejné ref2v a slouží
        // pak jako podklad, na který se úseky nabalují.
        var zacatekLatent = link(N_START_SAMPLER, 0)
        if (!navazuje) {
            val snimku = framesForSeconds(scene.startSeconds)
            // Rychlý režim: první průchod na pětině plochy. Podmínka proto
            // dostává menší plátno a cílové rozlišení se dohání až zvětšením.
            val maly = if (scene.rychlyZacatek) mensiPlatno(res.width, res.height) else null
            podminka(
                N_START_COND, "Zadání začátku", scene.startPrompt.trim(), snimku,
                sirka = maly?.first, vyska = maly?.second,
            )
            wf.put(
                N_START_GUIDER,
                node("BasicGuider", "Vedení začátku", JSONObject()
                    .put("model", modelZaklad).put("conditioning", link(N_START_COND, 0)))
            )
            wf.put(N_START_NOISE, node("RandomNoise", "Šum začátku", JSONObject().put("noise_seed", seed)))
            wf.put(
                N_START_SAMPLER, node(
                    "SamplerCustomAdvanced", "Vzorkování začátku", JSONObject()
                        .put("noise", link(N_START_NOISE, 0))
                        .put("guider", link(N_START_GUIDER, 0))
                        .put("sampler", link(N_SAMPLER_SELECT, 0))
                        .put("sigmas", link(N_SCHEDULER, 0))
                        .put("latent_image", link(N_START_COND, 1))
                )
            )

            if (scene.rychlyZacatek) {
                // H3 nese obraz i zvuk v jednom latentu; zvětšovač umí jen obraz,
                // takže se latent rozdělí, zvětší se obrazová část a zase slepí
                // s původním zvukem. Bere se DRUHÝ výstup vzorkovače
                // (denoised_output) — s ním pracuje i předloha.
                wf.put(
                    N_FAST_SEPARATE, node(
                        "LTXVSeparateAVLatent", "Rozdělit obraz a zvuk",
                        JSONObject().put("av_latent", link(N_START_SAMPLER, 1))
                    )
                )
                wf.put(
                    N_FAST_UPSCALE, node(
                        "MinimaxH3LatentUpscaler3D", "Zvětšení latentu", JSONObject()
                            .put("latent", link(N_FAST_SEPARATE, 0))
                            .put("model_name", UPSCALER_MODEL)
                            .put("mode", "target dimensions")
                            .put("mode.width", res.width)
                            .put("mode.height", res.height)
                            .put("align", 32)
                            .put("enable_temporal_chunking", true)
                            .put("force_unload", true)
                            .put("device", "cuda")
                            .put("precision", "fp16")
                    )
                )
                wf.put(
                    N_FAST_CONCAT, node(
                        "LTXVConcatAVLatent", "Zpátky dohromady", JSONObject()
                            .put("video_latent", link(N_FAST_UPSCALE, 0))
                            .put("audio_latent", link(N_FAST_SEPARATE, 1))
                    )
                )
                // Doostřovací průchod běží na cílovém rozlišení, takže potřebuje
                // vlastní podmínku — ta ze zmenšeného plátna by mu nesedla.
                podminka(N_FAST_COND, "Zadání doostření", scene.startPrompt.trim(), snimku)
                wf.put(
                    N_FAST_GUIDER,
                    node("BasicGuider", "Vedení doostření", JSONObject()
                        .put("model", modelZaklad).put("conditioning", link(N_FAST_COND, 0)))
                )
                wf.put(
                    N_FAST_SIGMAS,
                    node("ManualSigmas", "Sigmy doostření", JSONObject().put("sigmas", REFINE_SIGMAS))
                )
                wf.put(
                    N_FAST_SAMPLER, node(
                        "SamplerCustomAdvanced", "Doostření začátku", JSONObject()
                            .put("noise", link(N_START_NOISE, 0))
                            .put("guider", link(N_FAST_GUIDER, 0))
                            .put("sampler", link(N_SAMPLER_SELECT, 0))
                            .put("sigmas", link(N_FAST_SIGMAS, 0))
                            .put("latent_image", link(N_FAST_CONCAT, 0))
                    )
                )
                zacatekLatent = link(N_FAST_SAMPLER, 0)
            }
        }

        // --- jednotlivé úseky ------------------------------------------------
        useky.forEachIndexed { idx, usek ->
            val i = idx + 1
            val cond = condUseku(i)
            val ctx = kontextUseku(i)
            val guider = guiderUseku(i)
            val sum = sumUseku(i)
            val sampler = samplerUseku(i)

            podminka(cond, "Zadání úseku $i", usek.prompt.trim(), framesForSeconds(usek.seconds))

            if (i == 1) {
                wf.put(
                    ctx, node(
                        "MiniMaxH3StartMaskedContext", "Kontext úseku 1", JSONObject()
                            .put("latent", link(cond, 1))
                            .put("vae", link(N_VAE_VIDEO, 0))
                            .put("audio_vae", link(N_VAE_AUDIO, 0))
                            .put("start_mode", startMode)
                            .put("context_length", CONTEXT_FRAMES)
                            .put("audio_feather_ticks", AUDIO_FEATHER)
                            .put("source_fps", FPS)
                            .put("crop", "disabled")
                            .apply {
                                if (navazuje) {
                                    put("source_frames", link(N_CROP32, 0))
                                    put("source_audio", link(N_VIDEO_PARTS, 1))
                                } else {
                                    put("live_starter_latent", zacatekLatent)
                                }
                            }
                    )
                )
            } else {
                wf.put(
                    ctx, node(
                        "MiniMaxH3GeneratedAVMaskedContext", "Kontext úseku $i", JSONObject()
                            .put("latent", link(cond, 1))
                            .put("source_latent", link(samplerUseku(i - 1), 0))
                            .put("context_length", CONTEXT_FRAMES)
                            .put("audio_feather_ticks", AUDIO_FEATHER)
                    )
                )
            }

            // LoRA úseku se věší až za Spectrum — proto může být u každého
            // úseku jiná, aniž by se přenastavoval celý řetěz.
            var modelUseku = modelZaklad
            if (usek.lora.isNotBlank()) {
                val id = (200 + i).toString()
                wf.put(
                    id, node(
                        "LoraLoaderModelOnly", "LoRA úseku $i", JSONObject()
                            .put("model", modelUseku)
                            .put("lora_name", usek.lora)
                            .put("strength_model", usek.loraSila.toDouble())
                    )
                )
                modelUseku = link(id, 0)
            }

            wf.put(
                guider,
                node("BasicGuider", "Vedení úseku $i", JSONObject()
                    .put("model", modelUseku).put("conditioning", link(cond, 0)))
            )
            // Každý úsek má vlastní šum, jinak by z nich vycházel stejný pohyb.
            wf.put(sum, node("RandomNoise", "Šum úseku $i", JSONObject().put("noise_seed", seed + i)))
            wf.put(
                sampler, node(
                    "SamplerCustomAdvanced", "Vzorkování úseku $i", JSONObject()
                        .put("noise", link(sum, 0))
                        .put("guider", link(guider, 0))
                        .put("sampler", link(N_SAMPLER_SELECT, 0))
                        .put("sigmas", link(N_SCHEDULER, 0))
                        .put("latent_image", link(ctx, 0))
                )
            )
        }

        // --- složení do jednoho MP4 -------------------------------------------
        wf.put(
            N_STREAM, node(
                "MiniMaxH3StreamLiveExtensionAVToVHS", "Složení do MP4", JSONObject()
                    .put("video_vae", link(N_VAE_VIDEO, 0))
                    .put("audio_vae", link(N_VAE_AUDIO, 0))
                    .put("start_mode", startMode)
                    .put("active_extensions", useky.size)
                    .put("context_frames", CONTEXT_FRAMES)
                    .put("video_overlap_frames", CONTEXT_FRAMES)
                    .put("source_fps", FPS)
                    .put("crop", "disabled")
                    .put("filename_prefix", "PocketLong")
                    .put("pix_fmt", "yuv420p")
                    .put("crf", 19)
                    .put("save_metadata", false)
                    .put("trim_to_audio", true)
                    .put("save_output", true)
                    .apply {
                        if (navazuje) {
                            put("source_frames", link(N_CROP32, 0))
                            put("source_audio", link(N_VIDEO_PARTS, 1))
                        } else {
                            put("starter_latent", zacatekLatent)
                        }
                        useky.forEachIndexed { idx, _ ->
                            put("extension_${idx + 1}", link(samplerUseku(idx + 1), 0))
                        }
                    }
            )
        )
        return wf
    }

    /**
     * Plátno prvního průchodu rychlého začátku: pětina plochy při zachovaném
     * poměru stran, zaokrouhlená na násobek 32 (menší krok H3 neumí).
     */
    fun mensiPlatno(w: Int, h: Int): Pair<Int, Int> {
        val k = kotlin.math.sqrt(FAST_PODIL.toDouble())
        fun zaokrouhli(v: Double) = maxOf(32, (Math.round(v / 32.0) * 32).toInt())
        return zaokrouhli(w * k) to zaokrouhli(h * k)
    }

    fun stageForClass(cls: String?): Stage = when (cls) {
        "CLIPLoader", "UNETLoader", "VAELoader", "LoraLoaderModelOnly",
        "MiniMaxH3SigmaShift", "PathchSageAttentionKJ", "SpectrumApplyMiniMaxH3" -> Stage.MODELS
        "LoadVideo", "GetVideoComponents", "MiniMaxH3CropTo32", "LoadImage",
        "MiniMaxH3StartCanvasSelector" -> Stage.REFERENCES
        "MiniMaxH3ReferenceToVideo", "MiniMaxH3StartMaskedContext",
        "MiniMaxH3GeneratedAVMaskedContext", "BasicGuider", "RandomNoise",
        "KSamplerSelect", "BasicScheduler", "ManualSigmas",
        "LTXVSeparateAVLatent", "LTXVConcatAVLatent",
        "MinimaxH3LatentUpscaler3D" -> Stage.ENCODING
        "SamplerCustomAdvanced" -> Stage.SAMPLING
        "MiniMaxH3StreamLiveExtensionAVToVHS" -> Stage.MUXING
        else -> Stage.SAMPLING
    }

    fun rangeForClass(cls: String?): Pair<Float, Float> = when (stageForClass(cls)) {
        Stage.MODELS -> 0.00f to 0.06f
        Stage.REFERENCES -> 0.06f to 0.10f
        Stage.ENCODING -> 0.10f to 0.14f
        Stage.SAMPLING -> 0.14f to 0.80f
        else -> 0.80f to 0.90f
    }

    fun reportsSteps(cls: String?): Boolean = cls == "SamplerCustomAdvanced"

    fun nodeClasses(wf: JSONObject): Map<String, String> =
        wf.keys().asSequence().mapNotNull { id ->
            wf.optJSONObject(id)?.optString("class_type")?.takeIf { it.isNotEmpty() }?.let { id to it }
        }.toMap()
}
