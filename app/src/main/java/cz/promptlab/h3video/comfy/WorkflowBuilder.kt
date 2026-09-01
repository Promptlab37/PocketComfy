package cz.promptlab.h3video.comfy

import android.content.Context
import cz.promptlab.h3video.R
import cz.promptlab.h3video.data.GenParams
import org.json.JSONArray
import org.json.JSONObject

/**
 * Stavitel grafu pro kartu **Časová osa**.
 *
 * Do verze 2.61 tenhle soubor obsluhoval sedm karet a byl podle toho velký.
 * Od 2.62 zbyla jediná: osa jede na uživatelově vyladěném ULTRA workflow
 * (`res/raw/workflow_h3_ultra.json`) a dosazují se do něj jen jeho volby —
 * rozměry, délka segmentu, kroky, sampler, seed, LoRA a samotná osa.
 *
 * Karta All in One tudy neprochází vůbec; ta si staví graf z šablon, které
 * stahuje ze serveru (viz [AioBuilder]).
 */
object WorkflowBuilder {

    // ---- uzly ULTRA workflow
    const val N_UNET_FL2VA = "8"
    const val N_CLIP = "12"
    const val N_VAE_VIDEO = "14"
    const val N_VAE_AUDIO = "16"
    const val N_WIDTH = "26"
    const val N_HEIGHT = "27"
    const val N_DURATION = "28"
    const val N_SEED = "30"
    const val N_SAMPLER = "31"
    const val N_SCHEDULER = "32"
    const val N_VIDEO_OUT = "1980"
    const val N_LORA = "1981"
    const val N_COND = "1983"
    const val N_RESOLUTION = "1987"
    const val N_SAGE = "1990"
    const val N_SHIFT = "2000:1988"
    const val N_SPECTRUM = "2000:1989"
    const val N_DECODE_VIDEO = "2057:2050"
    const val N_SAMPLING = "2057:2051"
    const val N_DECODE_AUDIO = "2057:2052"
    const val N_GUIDER = "2057:2053"
    const val N_FRAMES = "2057:2054"
    const val N_SWITCH_W = "2057:2055"
    const val N_SWITCH_H = "2057:2056"

    /** Uzly, které do grafu přidává appka. */
    const val N_PREVIEW = "3050"      // ModelPreviewOverrideKJ (živý náhled)
    const val N_LSI_TIMELINE = "3070" // LSIMinimaxTimeline
    const val N_LSI_RENDER = "3071"   // LSIMinimaxTimelineRender

    /** Malý dekodér pro živý náhled – leží v ComfyUI/models/vae_approx. */
    private const val TAEH3 = "taeh3.safetensors"

    private var cached: String? = null

    private fun template(ctx: Context): String = cached ?: ctx.resources
        .openRawResource(R.raw.workflow_h3_ultra)
        .bufferedReader().use { it.readText() }.also { cached = it }

    fun build(ctx: Context, p: GenParams, timelineData: String): JSONObject =
        build(template(ctx), p, timelineData)

    /**
     * Stejné sestavení, jen s předlohou předanou jako text – díky tomu jde hotový
     * graf zkontrolovat testem, bez Androidu. Zapojení uzlů je přesně to, na čem
     * záleží (a kde už jednou náhled skončil mimo řetězec), takže to test hlídá.
     */
    fun build(template: String, p: GenParams, timelineData: String): JSONObject {
        val wf = JSONObject(template)

        // ---- rozměry, délka, seed, sampler
        val res = p.resolution
        wf.inputs(N_WIDTH).put("value", res.width)
        wf.inputs(N_HEIGHT).put("value", res.height)
        wf.inputs(N_RESOLUTION).apply {
            put("aspect_ratio", p.aspect.comfyValue)
            put("megapixels", p.megapixels.toDouble())
            put("multiple", 32)
        }
        wf.inputs(N_DURATION).put("value", p.seconds.toDouble())
        wf.inputs(N_SEED).put("noise_seed", p.seed)
        wf.inputs(N_SAMPLER).put("sampler_name", p.sampler)
        wf.inputs(N_SCHEDULER).apply {
            put("steps", p.steps)
            put("scheduler", p.scheduler)
        }

        // ---- vylepšení z workflow, jen zapnout/vypnout
        wf.inputs(N_SAGE).put("sage_attention", if (p.sageAttention) "auto" else "disabled")
        wf.inputs(N_SHIFT).apply {
            put("shift_video", p.shiftVideo.toDouble())
            put("shift_audio", p.shiftAudio.toDouble())
        }
        wf.inputs(N_SPECTRUM).put("enabled", p.spectrum)
        wf.inputs(N_VIDEO_OUT).apply {
            put("crf", p.crf)
            put("filename_prefix", "MiMx-OSA")
        }

        // Turbo LoRA jako lora_1. Vypnutá (profil Kvalita) se posílá s on=false –
        // Power Lora Loader takový vstup přeskočí, takže model jede „na plno“.
        wf.inputs(N_LORA).getJSONObject("lora_1").apply {
            put("on", p.turboLoraOn)
            put("lora", p.turboLora)
            put("strength", p.turboLoraStrength.toDouble())
        }
        // Další LoRA se přidávají jako lora_2, lora_3… Power Lora Loader projde
        // všechny vstupy začínající „lora_" v pořadí, v jakém jsou v JSONu,
        // a vynechá ty s on=false nebo nulovou silou.
        p.extraLoras.forEachIndexed { i, l ->
            wf.inputs(N_LORA).put(
                "lora_${i + 2}", JSONObject()
                    .put("on", l.enabled)
                    .put("lora", l.name)
                    .put("strength", l.strength.toDouble())
            )
        }

        // Osa nabízí segmenty z textu a z prvního snímku, tedy FL cestu.
        wf.inputs(N_LORA).put("model", link(N_UNET_FL2VA))
        if (p.unetFl2va.isNotBlank()) {
            wf.inputs(N_UNET_FL2VA).put("unet_name", p.unetFl2va)
        }
        wf.inputs(N_CLIP).put("clip_name", p.clipName)

        // ---- živý náhled během vzorkování
        // ComfyUI se spouští bez --preview-method, takže standardní náhledy vůbec
        // nevznikají. Tenhle uzel je posílá vlastní zprávou (kj_preview_override),
        // kterou si appka odchytává – proto se z něj dá vidět, co se kreslí.
        if (p.livePreview) {
            wf.put(
                N_PREVIEW, JSONObject()
                    .put(
                        "inputs", JSONObject()
                            .put("model", link(N_SPECTRUM))
                            .put("max_resolution", 512)
                            .put("jpeg_quality", 70)
                            // Osm snímků, ne jeden: uzel z nich pošle animaci
                            // a je vidět pohyb, ne zamrzlý obrázek.
                            .put("suppress_default_preview", true)
                            .put("preview_frames", 8)
                            .put("preview_fps", 12)
                            .put("tiny_vae", TAEH3)
                    )
                    .put("class_type", "ModelPreviewOverrideKJ")
                    .put("_meta", JSONObject().put("title", "Model Preview Override"))
            )
            wf.inputs(N_SCHEDULER).put("model", link(N_PREVIEW))
            wf.inputs(N_GUIDER).put("model", link(N_PREVIEW))
        }

        buildTimeline(wf, p, timelineData)
        return wf
    }

    /**
     * Sampling i dekódování si řídí uzel `…TimelineRender` sám – segment po
     * segmentu, každý do 15 s, s navázáním na poslední snímek toho předchozího.
     * Původní vzorkovací větev se proto celá obchází: do skládání videa jdou
     * rovnou obraz a zvuk od něj.
     */
    private fun buildTimeline(wf: JSONObject, p: GenParams, timelineData: String) {
        val res = p.resolution
        wf.put(
            N_LSI_TIMELINE, JSONObject()
                .put(
                    "inputs", JSONObject()
                        // FL váhy s Turbo LoRA; referenční vstup dostane týž řetězec,
                        // protože osa zatím nabízí jen T2V a I2V, tedy FL cestu. Kdyby
                        // se přidaly R2V segmenty, musí sem přijít ref2va řetězec.
                        .put("fl_model", link(N_SPECTRUM))
                        .put("ref_model", link(N_SPECTRUM))
                        .put("clip", link(N_CLIP))
                        .put("video_vae", link(N_VAE_VIDEO))
                        .put("audio_vae", link(N_VAE_AUDIO))
                        .put("width", res.width)
                        .put("height", res.height)
                        .put("ref_image_size", p.refImageSize)
                        .put("timeline_data", timelineData)
                        .put("global_prompt", p.prompt)
                )
                .put("class_type", "LSIMinimaxTimeline")
                .put("_meta", JSONObject().put("title", "LSI MiniMax Timeline"))
        )
        wf.put(
            N_LSI_RENDER, JSONObject()
                .put(
                    "inputs", JSONObject()
                        .put("timeline", link(N_LSI_TIMELINE))
                        .put("seed", p.seed)
                        .put("steps", p.steps)
                        .put("sampler_name", p.sampler)
                        .put("scheduler", p.scheduler)
                        .put("denoise", 1.0)
                        // Prolnutí na švu mezi segmenty; autorova výchozí hodnota.
                        .put("boundary_fade_ms", 80)
                        .put("memory_cleanup", "auto")
                        .put("ref_steps", p.steps)
                        .put("cache_project", p.timelineProject)
                        .put("resume_from_segment", 1)
                        .put("selected_segment", maxOf(1, p.timelineOnlySegment))
                        .put("render_selected_segment", p.timelineOnlySegment > 0)
                        .put("regenerate_selected", p.timelineOnlySegment > 0)
                        .put("reuse_cached_segments", true)
                        .put("stitch_cached_timeline", false)
                        .put("video_shift", p.shiftVideo.toDouble())
                        .put("audio_shift", p.shiftAudio.toDouble())
                )
                .put("class_type", "LSIMinimaxTimelineRender")
                .put("_meta", JSONObject().put("title", "LSI MiniMax Timeline Render"))
        )
        // Skládání videa bere obraz i zvuk od uzlu, ne z původních dekodérů.
        wf.inputs(N_VIDEO_OUT).put("images", link(N_LSI_RENDER, 0))
        wf.inputs(N_VIDEO_OUT).put("audio", link(N_LSI_RENDER, 1))
        wf.remove(N_COND)
    }

    private fun link(node: String, slot: Int = 0) = JSONArray().put(node).put(slot)

    private fun JSONObject.inputs(node: String): JSONObject =
        getJSONObject(node).getJSONObject("inputs")

    /**
     * Uzly, které hlásí kroky vzorkování. U Časové osy vzorkuje LSI render
     * (původní vzorkovací větev se obchází), takže sem patří oba – jinak by
     * odhad času i počítadlo kroků zůstaly navždy na nule.
     */
    fun reportsSteps(node: String?): Boolean =
        node == N_SAMPLING || node == N_LSI_RENDER

    /** Lidský název fáze podle uzlu, který ComfyUI zrovna počítá. */
    fun stageFor(node: String?): Stage = when (node) {
        null -> Stage.FINISHING
        N_UNET_FL2VA, N_CLIP, N_VAE_VIDEO, N_VAE_AUDIO -> Stage.MODELS
        N_LORA, N_SAGE, N_SHIFT, N_SPECTRUM -> Stage.MODELS
        N_WIDTH, N_HEIGHT, N_DURATION, N_FRAMES, N_RESOLUTION,
        N_SWITCH_W, N_SWITCH_H -> Stage.REFERENCES
        N_COND, N_LSI_TIMELINE -> Stage.ENCODING
        N_SCHEDULER, N_GUIDER, N_SEED, N_SAMPLER -> Stage.ENCODING
        N_SAMPLING, N_LSI_RENDER -> Stage.SAMPLING
        N_DECODE_AUDIO, N_DECODE_VIDEO -> Stage.DECODING
        N_VIDEO_OUT -> Stage.MUXING
        else -> Stage.SAMPLING
    }

    /**
     * Rozsah celkového postupu podle uzlu. Vzorkování zabírá kolem 95 % času
     * (naměřeno na ostrém běhu), proto dostává skoro celou škálu.
     */
    fun rangeFor(node: String?): Pair<Float, Float> = when (node) {
        N_UNET_FL2VA, N_CLIP, N_VAE_VIDEO, N_VAE_AUDIO -> 0.00f to 0.03f
        N_LORA, N_SAGE, N_SHIFT, N_SPECTRUM -> 0.03f to 0.04f
        N_WIDTH, N_HEIGHT, N_DURATION, N_FRAMES, N_RESOLUTION,
        N_SWITCH_W, N_SWITCH_H -> 0.05f to 0.055f
        N_COND, N_LSI_TIMELINE -> 0.055f to 0.075f
        N_SCHEDULER, N_GUIDER, N_SEED, N_SAMPLER -> 0.075f to 0.08f
        N_SAMPLING, N_LSI_RENDER -> 0.08f to 0.86f
        // Nad 0,90 zůstává místo pro přenos videa do telefonu – u větších souborů
        // to trvá i minuty a musí to být na kolečku vidět.
        N_DECODE_AUDIO -> 0.86f to 0.87f
        N_DECODE_VIDEO -> 0.87f to 0.89f
        N_VIDEO_OUT -> 0.89f to 0.90f
        else -> 0.08f to 0.86f
    }
}

enum class Stage(val title: String, val detail: String) {
    // Server na počítači se po restartu teprve rozjíždí. Není to chyba – hlídač
    // na počítači ho spouští sám a appka počká, až začne odpovídat.
    STARTING("Probouzím ComfyUI", "Server na počítači ještě naskakuje"),
    UPLOADING("Odesílám obrázky", "Nahrávám podklady do ComfyUI"),
    QUEUED("Ve frontě", "Čekám, až se uvolní grafická karta"),
    MODELS("Načítám modely", "MiniMax H3 + textový enkodér"),
    REFERENCES("Připravuji podklady", "Načítám obrázky a rozměry"),
    ENCODING("Zpracovávám prompt", "Model si čte zadání"),
    SAMPLING("Generuji video", "Nejdelší část – obraz i zvuk najednou"),
    DECODING("Dekóduji obraz a zvuk", "Převádím latentní data na snímky"),
    MUXING("Skládám video", "Spojuji obraz se zvukem"),
    // Pozor na formulaci: tohle je přenos z počítače do Galerie aplikace, ne
    // ukládání do galerie telefonu (to je zvlášť a je vypnuté).
    DOWNLOADING("Přebírám video", "Přenáším ho z počítače do Galerie aplikace"),
    FINISHING("Dokončuji", "Ještě chvilku"),
}
