package cz.promptlab.h3video.comfy

import cz.promptlab.h3video.data.AioMode
import cz.promptlab.h3video.data.AioScene
import cz.promptlab.h3video.data.GenParams
import cz.promptlab.h3video.data.Upscaler
import cz.promptlab.h3video.data.planExtend
import org.json.JSONArray
import org.json.JSONObject

/**
 * Karta **All in One**: dosazení hodnot do hotových šablon balíku
 * ComfyUI-ALLinONE-MinimaxH3, které si appka stahuje ze serveru
 * (`/h3one/workflow/{jméno}`).
 *
 * Čísla uzlů jsou ve všech video šablonách balíku stejná:
 * ```
 *  1 CLIPLoader      2 UNETLoader     3 VAE obraz     4 VAE zvuk
 *  5 MiniMaxH3SigmaShift             6 podmínka (ImageToVideo / ReferenceToVideo)
 *  7 BasicGuider     8 RandomNoise    9 BasicScheduler  10 KSamplerSelect
 * 11 SamplerCustomAdvanced          12 VAEDecode     13 VAEDecodeAudio
 * 14 CreateVideo    15 SaveVideo
 * ```
 * Klíčové snímky přidávají uzel 16 (`MiniMaxH3CustomKeyframes`), prodloužení
 * uzly 16–21. Zvětšení má vlastní, kratší šablonu bez modelu.
 *
 * Cizí graf se nepředělává: mění se jen hodnoty a připojují se uzly pro
 * obrázky a videa, které v šabloně být nemůžou (jejich jména vzniknou až
 * nahráním do ComfyUI).
 */
object AioBuilder {

    // uzly šablony
    const val N_CLIP = "1"
    const val N_UNET = "2"
    const val N_VAE_VIDEO = "3"
    const val N_VAE_AUDIO = "4"
    const val N_SHIFT = "5"
    const val N_COND = "6"
    const val N_GUIDER = "7"
    const val N_NOISE = "8"
    const val N_SCHEDULER = "9"
    const val N_SAMPLER_SELECT = "10"
    const val N_SAMPLING = "11"
    const val N_DECODE_VIDEO = "12"
    const val N_DECODE_AUDIO = "13"
    const val N_CREATE_VIDEO = "14"
    const val N_SAVE_VIDEO = "15"

    /** Klíčové snímky, u prodloužení zdrojové video. */
    const val N_EXTRA = "16"

    /** Prodloužení: kontext ze zdrojového videa. */
    const val N_EXTEND_CONTEXT = "18"

    // --- přemalování ve videu (šablona mask.json) --------------------------
    /** Ořez zdrojového videa na zpracovávaný úsek. */
    const val N_MASK_SLICE = "34"
    /** Příprava snímků a zvuku pro H3 (délka úseku, cílové fps). */
    const val N_MASK_PREPARE = "18"
    /** Checkpoint SAM 3, který objekt sleduje napříč snímky. */
    const val N_MASK_SAM = "19"
    /** Text pro SAM 3 — co ve videu hledat. */
    const val N_MASK_TARGET = "20"
    /** Sledování: kolik objektů se má chytit. */
    const val N_MASK_TRACK = "21"

    /**
     * Váhy, které si šablona `mask.json` na rozdíl od ostatních nenese —
     * má u všech loaderů prázdno a čeká, že je dosadí ten, kdo ji spouští.
     * Jména jsou stejná jako v ostatních šablonách balíku.
     */
    const val VAE_VIDEO = "minimax_h3_video_vae_fp16.safetensors"
    const val VAE_AUDIO = "minimax_h3_audio_vae_fp32.safetensors"
    const val UNET_FL2VA = "minimax_h3_fl2va_pruned_int8_convrot.safetensors"
    /** Referenční váhy — jede na nich karta Dlouhé video. */
    const val UNET_REF2VA = "minimax_h3_ref2va_pruned_int8_convrot.safetensors"
    const val SAM3_CKPT = "sam3.1_multiplex_fp16.safetensors"

    /** List postavy: složení promptu (popis postavy jde do `string_a`). */
    const val N_SHEET_PROMPT = "17"

    /** List postavy: alternativní, fotorealistická větev promptu. */
    const val N_SHEET_STYLE_REAL = "32"

    /** Vlastní uzly, které appka do grafu přidává. */
    const val N_PREVIEW = "3050"
    const val N_CACHE_BUST = "499"

    /** Malý dekodér pro živý náhled – leží v ComfyUI/models/vae_approx. */
    private const val TAEH3 = "taeh3.safetensors"

    /**
     * @param template šablona stažená ze serveru (text, ať jde otestovat bez Androidu)
     * @param images jména obrázků nahraných do ComfyUI, v pořadí [AioScene.uploadImages]
     * @param video jméno nahraného videa (reference / prodloužení / zvětšení)
     */
    /**
     * Dialogy (karta Mluvící scéna) přes tuhle šablonu.
     *
     * Je to obyčejné reference-to-video: fotky postav jdou jako `ref_images`,
     * namluvené repliky jako `ref_audios` a hotový prompt (se značkami
     * `<Picture N>`, mluvčími `(S1)` a replikami v `<d>[Czech] …</d>`) do
     * podmínky. Pořadí obojího je závazné – podle něj se v promptu číslují
     * `<Picture N>` a `<Audio N>`.
     */
    fun buildTalk(
        template: String,
        p: GenParams,
        prompt: String,
        frames: Int,
        images: List<String>,
        audios: List<String>,
    ): JSONObject {
        val wf = JSONObject(template)
        var nextId = 200
        fun newId(): String = (nextId++).toString()

        patchModels(wf, p, referencni = true)
        val res = p.resolution
        wf.inputs(N_COND).apply {
            put("prompt", prompt)
            put("width", res.width)
            put("height", res.height)
            put("length", frames)
            if (has("ref_image_size")) put("ref_image_size", p.refImageSize)
        }
        wf.inputs(N_NOISE).put("noise_seed", p.seed)
        wf.inputs(N_SCHEDULER).put("steps", p.steps)
        // Dialogy jedou referenční cestou – platí pro ně scheduler pro reference.
        wf.inputs(N_SCHEDULER).put("scheduler", p.schedulerForRefPath)
        if (wf.optJSONObject(N_SAMPLER_SELECT)?.optString("class_type") == "KSamplerSelect") {
            wf.inputs(N_SAMPLER_SELECT).put("sampler_name", p.sampler)
        }

        images.forEachIndexed { idx, name ->
            val id = newId()
            wf.put(id, loadImage(name, "Postava ${idx + 1}"))
            wf.inputs(N_COND).put("ref_images.ref_image_$idx", link(id))
        }
        audios.forEachIndexed { idx, name ->
            val id = newId()
            wf.put(id, node("LoadAudio", "Replika ${idx + 1}", JSONObject().put("audio", name)))
            wf.inputs(N_COND).put("ref_audios.ref_audio_$idx", link(id))
        }

        insertModelChain(wf, p, referencni = true)
        insertCacheBust(wf, p, prompt, images + audios, frames)
        return wf
    }

    fun build(
        template: String,
        p: GenParams,
        scene: AioScene,
        images: List<String> = emptyList(),
        video: String? = null,
    ): JSONObject {
        val wf = JSONObject(template)
        if (scene.mode == AioMode.UPSCALE) return buildUpscale(wf, scene, video)
        if (scene.mode == AioMode.CHARSHEET) return buildCharSheet(wf, p, scene, images)

        var nextId = 200
        fun newId(): String = (nextId++).toString()

        patchCommon(wf, p, scene)

        when (scene.mode) {
            AioMode.IMAGE -> {
                var i = 0
                if (scene.first.image != null) {
                    val id = newId()
                    wf.put(id, loadImage(images.getOrElse(i++) { "" }, "První snímek"))
                    wf.inputs(N_COND).put("first_frame", link(id))
                }
                if (scene.useLastFrame && scene.last.image != null) {
                    val id = newId()
                    wf.put(id, loadImage(images.getOrElse(i) { "" }, "Poslední snímek"))
                    wf.inputs(N_COND).put("last_frame", link(id))
                }
            }

            AioMode.REFERENCE -> {
                var firstImageId: String? = null
                images.forEachIndexed { idx, name ->
                    val id = newId()
                    wf.put(id, loadImage(name, "Reference ${idx + 1}"))
                    wf.inputs(N_COND).put("ref_images.ref_image_$idx", link(id))
                    if (idx == 0) firstImageId = id
                }
                // Kotva totožnosti: první referenční obrázek se připne jako snímek 0.
                // Bez ní mluvící referenční video přebije stojící fotku (v balíku je
                // to ověřené poměrem zhruba 2:1) a ve výsledku je vidět obličej
                // z videa, ne z fotky.
                firstImageId?.let { imgId ->
                    val cond = wf.inputs(N_COND)
                    val kf = newId()
                    wf.put(
                        kf, node(
                            "H3IdentityAnchor", "Kotva totožnosti", JSONObject()
                                .put("conditioning", link(N_COND, 0))
                                .put("vae", link(N_VAE_VIDEO, 0))
                                .put("latent", link(N_COND, 1))
                                .put("frame_count", cond.optInt("length", scene.frames))
                                .put("width", cond.optInt("width", p.resolution.width))
                                .put("height", cond.optInt("height", p.resolution.height))
                                .put("anchor", "first")
                                .put("image", link(imgId))
                        )
                    )
                    wf.inputs(N_GUIDER).put("conditioning", link(kf))
                }
                if (video != null) {
                    val lv = newId()
                    val gc = newId()
                    wf.put(
                        lv, node(
                            "LoadVideo", "Referenční video", JSONObject()
                                .put("file", video).put("video-preview", "")
                        )
                    )
                    wf.put(gc, node("GetVideoComponents", "Rozklad videa", JSONObject().put("video", link(lv))))
                    wf.inputs(N_COND).put("ref_videos.ref_video_0", link(gc))
                    // Zvuk z referenčního videa jde do modelu jen když ho uživatel chce –
                    // jinak by si model bral i ruchy, které do nové scény nepatří.
                    if (scene.refVideoAudio) {
                        wf.inputs(N_COND).put("ref_video_audios.ref_video_audio_0", link(gc, 1))
                    }
                }
            }

            AioMode.KEYFRAMES -> {
                val positions = JSONArray()
                var cislo = 0
                scene.keysWithImage.forEachIndexed { idx, slot ->
                    cislo++
                    val id = newId()
                    wf.put(id, loadImage(images.getOrElse(idx) { "" }, "Klíčový snímek $cislo"))
                    wf.inputs(N_EXTRA).put("keyframe_image_$cislo", link(id))
                    positions.put(slot.position.coerceIn(1, scene.frames))
                }
                wf.inputs(N_EXTRA).put(
                    "keyframe_state",
                    JSONObject().put("count", cislo).put("positions", positions).toString()
                )
            }

            AioMode.EXTEND -> wf.inputs(N_EXTRA).put("file", video.orEmpty())

            AioMode.MASK -> {
                wf.inputs(N_EXTRA).put("file", video.orEmpty())
                // Zpracovává se jen úsek od začátku – delší video by znamenalo
                // sledovat a přegenerovat i to, co uživatel měnit nechce.
                wf.inputs(N_MASK_SLICE).put("duration", scene.seconds.toDouble())
                wf.inputs(N_MASK_PREPARE).put("max_seconds", scene.seconds.toDouble())
                wf.inputs(N_MASK_SAM).put("ckpt_name", SAM3_CKPT)
                wf.inputs(N_MASK_TARGET).put("text", scene.maskTarget.trim())
                wf.inputs(N_MASK_TRACK).put("max_objects", scene.maskObjects.coerceIn(1, 8))
                // Čím se sledovaný kus nahradí. Stejné sloty jako u referencí,
                // takže i stejné pořadí nahrávání a značky <Picture N> v promptu.
                images.forEachIndexed { idx, name ->
                    val id = newId()
                    wf.put(id, loadImage(name, "Náhrada ${idx + 1}"))
                    wf.inputs(N_COND).put("ref_images.ref_image_$idx", link(id))
                }
            }

            else -> Unit // z textu se nic nepřipojuje
        }
        return wf
    }

    /**
     * Modely a sigma shift. Váhy zůstávají ze šablony: balík má u referenční
     * cesty ref2va a u ostatních fl2va, obojí ve verzi, na které je vyladěný.
     * Přepisuje se jen tehdy, když si uživatel vlastní model výslovně vybral –
     * a jen u nereferenční cesty, protože komunitní modely na referenční větev
     * většinou nesedí.
     */
    private fun patchModels(wf: JSONObject, p: GenParams, referencni: Boolean) {
        wf.inputs(N_CLIP).put("clip_name", p.clipName)
        if (p.unetFl2va.isNotBlank() && !referencni) {
            wf.inputs(N_UNET).put("unet_name", p.unetFl2va)
        }
        // Šablona přemalování přichází s prázdnými loadery – ostatní si své
        // váhy nesou samy a přepisovat je není proč.
        if (wf.inputs(N_UNET).optString("unet_name").isBlank()) {
            wf.inputs(N_UNET).put("unet_name", UNET_FL2VA)
        }
        if (wf.optJSONObject(N_VAE_VIDEO)?.optJSONObject("inputs")
                ?.optString("vae_name").isNullOrBlank()
        ) {
            wf.inputs(N_VAE_VIDEO).put("vae_name", VAE_VIDEO)
            wf.inputs(N_VAE_AUDIO).put("vae_name", VAE_AUDIO)
        }
        wf.inputs(N_SHIFT).put("shift_video", p.shiftVideo.toDouble())
        wf.inputs(N_SHIFT).put("shift_audio", p.shiftAudio.toDouble())
    }

    /** Společné hodnoty – rozměry, délka, modely, vzorkování, náhled. */
    private fun patchCommon(wf: JSONObject, p: GenParams, scene: AioScene) {
        patchModels(wf, p, referencni = scene.mode.usesRefWeights)

        val res = p.resolution
        val frames = if (scene.mode == AioMode.EXTEND) {
            val (kontext, cil, _) = planExtend(scene.seconds)
            if (wf.optJSONObject(N_EXTEND_CONTEXT)?.optString("class_type") ==
                "MiniMaxH3ExistingVideoMaskedContext"
            ) {
                wf.inputs(N_EXTEND_CONTEXT).put("context_length", kontext)
            }
            cil
        } else scene.frames

        wf.inputs(N_COND).apply {
            put("prompt", scene.prompt.trim())
            // U přemalování jsou rozměry i délka odkazy na výřez kolem
            // sledovaného objektu – přepsat je čísly by rozhodilo masku
            // i vlepení zpátky do původního záběru.
            if (!scene.mode.fixedSize) {
                put("width", res.width)
                put("height", res.height)
                put("length", frames)
            }
            // Reference se posílají v nastavené velikosti; „max" znamená 2048 px
            // a mnohonásobně delší běh, proto se sem posílá volba z parametrů.
            if (has("ref_image_size")) put("ref_image_size", p.refImageSize)
        }

        wf.inputs(N_NOISE).put("noise_seed", p.seed)
        wf.inputs(N_SCHEDULER).put("steps", p.steps)
        // Referenční cesta má u profilu V2 Turbo vlastní scheduler (autorovo
        // ladění, `simple` místo `beta`) – do 2.62 se sem omylem posílal vždycky
        // ten obecný, přestože profil hodnotu nesl.
        wf.inputs(N_SCHEDULER).put(
            "scheduler",
            if (scene.mode.usesRefWeights) p.schedulerForRefPath else p.scheduler
        )
        if (wf.optJSONObject(N_SAMPLER_SELECT)?.optString("class_type") == "KSamplerSelect") {
            wf.inputs(N_SAMPLER_SELECT).put("sampler_name", p.sampler)
        }

        insertModelChain(wf, p, referencni = scene.mode.usesRefWeights)
        insertCacheBust(wf, p, scene)
    }

    /**
     * Řetěz úprav modelu: LoRA → Turbo LoRA → Sage Attention → živý náhled.
     * Náhled musí být až na konci, aby vzorkovač počítal s modelem, na kterém
     * se skutečně generuje.
     */
    private fun insertModelChain(wf: JSONObject, p: GenParams, referencni: Boolean = false) {
        var model = link(N_UNET, 0)
        var nextId = 100
        fun newId(): String = (nextId++).toString()

        p.extraLoras.filter { it.name.isNotBlank() && it.enabled && it.strength != 0f }
            .forEach { lora ->
                val id = newId()
                wf.put(
                    id, node(
                        "LoraLoaderModelOnly", "LoRA ${lora.name}", JSONObject()
                            .put("model", model)
                            .put("lora_name", lora.name)
                            .put("strength_model", lora.strength.toDouble())
                    )
                )
                model = link(id)
            }

        // Pojistka: zrychlovací LoRA profilu, který je jen pro nereferenční
        // cestu (FastH3), se na ref2va váhy nedosazuje. Sedí na jiný model a
        // výsledek by jen kazila. Do UI se takový profil u referenčních režimů
        // vůbec nenabízí, tohle je druhá záchranná brzda.
        val loraSedi = !(referencni && p.profile.bezReferenci)
        if (p.turboLoraOn && p.turboLora.isNotBlank() && loraSedi) {
            val id = newId()
            wf.put(
                id, node(
                    "LoraLoaderModelOnly", "Turbo LoRA", JSONObject()
                        .put("model", model)
                        .put("lora_name", p.turboLora)
                        .put("strength_model", p.turboLoraStrength.toDouble())
                )
            )
            model = link(id)
        }

        if (p.sageAttention) {
            val id = newId()
            wf.put(
                id, node(
                    "MiniMaxH3MemoryEfficientSageAttentionPatch", "Sage Attention",
                    JSONObject().put("model", model)
                )
            )
            model = link(id)
        }

        // TeaCache (H3 port od Icyoung) — volitelné zrychlení; hodnoty jsou
        // doporučené autorem uzlu, total_steps musí sedět na skutečné kroky.
        if (p.teaCache) {
            val id = newId()
            wf.put(
                id, node(
                    "MiniMaxH3TeaCache", "TeaCache", JSONObject()
                        .put("model", model)
                        .put("rel_l1_thresh", 0.15)
                        .put("start_step", 2)
                        .put("end_step", -2)
                        .put("total_steps", p.steps)
                )
            )
            model = link(id)
        }

        if (p.livePreview) {
            wf.put(
                N_PREVIEW, node(
                    "ModelPreviewOverrideKJ", "Model Preview Override", JSONObject()
                        .put("model", model)
                        .put("max_resolution", 512)
                        .put("jpeg_quality", 70)
                        .put("suppress_default_preview", true)
                        // Osm snímků, ne jeden – uzel z nich pošle animaci a je
                        // vidět pohyb, ne zamrzlý obrázek.
                        .put("preview_frames", 8)
                        .put("preview_fps", 12)
                        .put("tiny_vae", TAEH3)
                )
            )
            model = link(N_PREVIEW)
        }

        wf.inputs(N_SHIFT).put("model", model)
    }

    /**
     * ComfyUI nevidí dovnitř samorostoucích vstupů (`ref_images.*`), takže po
     * výměně reference vrátí z mezipaměti staré video. `H3CacheBust` sedí za
     * CLIP loaderem a při každé změně zadání zneplatní všechno za sebou.
     */
    private fun insertCacheBust(wf: JSONObject, p: GenParams, scene: AioScene) =
        insertCacheBust(wf, otisk = fingerprint(p, scene))

    /** Varianta pro dialogy: otisk se skládá z promptu, souborů a délky. */
    private fun insertCacheBust(
        wf: JSONObject,
        p: GenParams,
        prompt: String,
        soubory: List<String>,
        frames: Int,
    ) = insertCacheBust(
        wf,
        otisk = JSONObject()
            .put("mode", "talk")
            .put("prompt", prompt)
            .put("seed", p.seed)
            .put("steps", p.steps)
            .put("width", p.resolution.width)
            .put("height", p.resolution.height)
            .put("frames", frames)
            .put("files", JSONArray().also { arr -> soubory.forEach { arr.put(it) } })
            .toString()
    )

    private fun insertCacheBust(wf: JSONObject, otisk: String) {
        wf.put(
            N_CACHE_BUST, node(
                "H3CacheBust", "Zneplatnění mezipaměti", JSONObject()
                    .put("clip", link(N_CLIP))
                    .put("fingerprint", otisk)
            )
        )
        wf.keys().asSequence().toList().forEach { id ->
            if (id == N_CACHE_BUST) return@forEach
            val inputs = wf.optJSONObject(id)?.optJSONObject("inputs") ?: return@forEach
            inputs.keys().asSequence().toList().forEach { key ->
                val v = inputs.opt(key)
                if (v is JSONArray && v.length() == 2 && v.optString(0) == N_CLIP && v.optInt(1) == 0) {
                    inputs.put(key, link(N_CACHE_BUST))
                }
            }
        }
    }

    /** Otisk zadání pro zneplatnění mezipaměti. */
    fun fingerprint(p: GenParams, scene: AioScene): String = JSONObject()
        .put("mode", scene.mode.kod)
        .put("prompt", scene.prompt.trim())
        .put("seed", p.seed)
        .put("steps", p.steps)
        // Rozměry a délka patří do otisku jen tam, kde je appka opravdu
        // dosazuje. U přemalování si je určuje výřez kolem sledovaného
        // objektu — mít je v otisku znamená zbytečně zahazovat mezipaměť
        // pokaždé, když si uživatel jinde přepne rozlišení.
        .apply {
            if (!scene.mode.fixedSize) {
                put("width", p.resolution.width)
                put("height", p.resolution.height)
                put("frames", scene.frames)
            } else {
                put("seconds", scene.seconds)
            }
        }
        .put("files", JSONArray().also { arr ->
            scene.uploadImages.forEach { arr.put(it.name + ":" + it.length()) }
            scene.uploadVideo?.let { arr.put(it.name + ":" + it.length()) }
        })
        .put("keys", JSONArray().also { arr ->
            scene.keysWithImage.forEach { arr.put(it.position) }
        })
        // List postavy: jiný styl nebo počet panelů = jiný výsledek, mezipaměť
        // se musí zneplatnit i bez změny promptu.
        .put("sheet", "${scene.sheetPanels}:${scene.sheetPhotoreal}")
        .toString()

    /**
     * List postavy (`charsheet.json` / `charsheet4.json`, balík 0.17).
     *
     * Šablona zrcadlí referenční graf, ale NEMÁ SigmaShift (uzel 5) a délka
     * (124 snímků), rozlišení, kroky, sampler i scheduler jsou vyladěné na
     * choreografii kamery v promptu – indexy snímků pro slepení listu na ně
     * sedí na snímek přesně. Proto se z parametrů dosazuje jen to, co
     * choreografii nerozbije: enkodér, seed, popis postavy, styl, reference,
     * Sage Attention a živý náhled. Profil, kroky ani Turbo LoRA sem nesahají
     * (fl2v Turbo na ref2va váhy stejně nepatří).
     */
    private fun buildCharSheet(
        wf: JSONObject,
        p: GenParams,
        scene: AioScene,
        images: List<String>,
    ): JSONObject {
        var nextId = 200
        fun newId(): String = (nextId++).toString()

        wf.inputs(N_CLIP).put("clip_name", p.clipName)
        wf.inputs(N_NOISE).put("noise_seed", p.seed)

        // Popis postavy jde PŘED pevný prompt šablony (string_a uzlu 17),
        // přesně jako to dělá balík. Fotorealistický styl je přepnutí na druhou
        // větev promptu (uzel 32), která v šabloně na tohle přepínání čeká.
        wf.inputs(N_SHEET_PROMPT).put("string_a", scene.prompt.trim())
        if (scene.sheetPhotoreal && wf.has(N_SHEET_STYLE_REAL)) {
            wf.inputs(N_SHEET_PROMPT).put("string_b", link(N_SHEET_STYLE_REAL))
        }

        images.forEachIndexed { idx, name ->
            val id = newId()
            wf.put(id, loadImage(name, "Reference ${idx + 1}"))
            wf.inputs(N_COND).put("ref_images.ref_image_$idx", link(id))
        }

        // Bez SigmaShiftu se upravený model zapojuje rovnou do guideru (7)
        // a scheduleru (9) – stejné zapojení používá balík.
        var model = link(N_UNET, 0)
        if (p.sageAttention) {
            val id = newId()
            wf.put(
                id, node(
                    "MiniMaxH3MemoryEfficientSageAttentionPatch", "Sage Attention",
                    JSONObject().put("model", model)
                )
            )
            model = link(id)
        }
        if (p.livePreview) {
            wf.put(
                N_PREVIEW, node(
                    "ModelPreviewOverrideKJ", "Model Preview Override", JSONObject()
                        .put("model", model)
                        .put("max_resolution", 512)
                        .put("jpeg_quality", 70)
                        .put("suppress_default_preview", true)
                        .put("preview_frames", 8)
                        .put("preview_fps", 12)
                        .put("tiny_vae", TAEH3)
                )
            )
            model = link(N_PREVIEW)
        }
        wf.inputs(N_GUIDER).put("model", model)
        wf.inputs(N_SCHEDULER).put("model", model)

        insertCacheBust(wf, p, scene)
        return wf
    }

    /**
     * Zvětšení. Šablona nemá model ani prompt – jen načte video, zvětší snímky
     * a složí je zpátky. Snímková frekvence i zvuk se berou ze zdrojového videa
     * (`GetVideoComponents`), takže se do nich nesmí sahat.
     */
    private fun buildUpscale(wf: JSONObject, scene: AioScene, video: String?): JSONObject {
        wf.inputs("1").put("file", video.orEmpty())
        when (scene.upscaler) {
            Upscaler.SEEDVR2 -> if (wf.optJSONObject("5")?.optString("class_type") == "SeedVR2VideoUpscaler") {
                wf.inputs("5").put("resolution", scene.upscaleResolution)
            }
            Upscaler.RTX -> if (wf.optJSONObject("3")?.optString("class_type") == "RTXVideoSuperResolution") {
                wf.inputs("3").put("resize_type", "scale by multiplier")
                wf.inputs("3").put("resize_type.scale", scene.upscaleMultiplier)
            }
        }
        return wf
    }

    // ------------------------------------------------------------------ pomocné

    private fun node(cls: String, title: String, inputs: JSONObject) = JSONObject()
        .put("inputs", inputs)
        .put("class_type", cls)
        .put("_meta", JSONObject().put("title", title))

    private fun loadImage(name: String, title: String) =
        node("LoadImage", title, JSONObject().put("image", name))

    private fun link(node: String, slot: Int = 0) = JSONArray().put(node).put(slot)

    private fun JSONObject.inputs(node: String): JSONObject =
        getJSONObject(node).getJSONObject("inputs")

    /**
     * Fáze se u téhle karty poznává podle TŘÍDY uzlu, ne podle jeho čísla.
     *
     * Čísla si nemůžou být jistá dvakrát: s hlavním workflow se překrývají
     * (osmička je tu šum, tam model) a i mezi šablonami balíku znamenají něco
     * jiného – uzel 3 je u SeedVR2 načtení modelu, u RTX rovnou celé zvětšení.
     * Třída je jednoznačná vždycky.
     */
    fun stageForClass(cls: String?): Stage = when (cls) {
        null -> Stage.SAMPLING
        "CLIPLoader", "UNETLoader", "VAELoader", "MiniMaxH3SigmaShift",
        "LoraLoaderModelOnly", "MiniMaxH3MemoryEfficientSageAttentionPatch",
        "ModelPreviewOverrideKJ", "MiniMaxH3TeaCache",
        "SeedVR2LoadDiTModel", "SeedVR2LoadVAEModel" -> Stage.MODELS

        "LoadImage", "LoadVideo", "LoadAudio", "GetVideoComponents",
        "MiniMaxH3CustomKeyframes", "MiniMaxH3ExistingVideoMaskedContext",
        "H3IdentityAnchor", "H3CacheBust" -> Stage.REFERENCES

        "MiniMaxH3ImageToVideo", "MiniMaxH3ReferenceToVideo", "BasicGuider",
        "RandomNoise", "BasicScheduler", "KSamplerSelect",
        "PrimitiveStringMultiline", "StringConcatenate" -> Stage.ENCODING

        "SamplerCustomAdvanced", "SeedVR2VideoUpscaler", "RTXVideoSuperResolution" -> Stage.SAMPLING

        "VAEDecode", "VAEDecodeAudio" -> Stage.DECODING

        "CreateVideo", "SaveVideo", "MiniMaxH3MotionContextTrim",
        "MiniMaxH3AssembleExtension", "H3AudioJoinSmooth",
        "ImageFromBatch", "ImageStitch", "SaveImage" -> Stage.MUXING

        else -> Stage.SAMPLING
    }

    /** Rozsah postupu podle třídy uzlu; vzorkování zabírá skoro celý čas. */
    fun rangeForClass(cls: String?): Pair<Float, Float> = when (cls) {
        "CLIPLoader", "UNETLoader", "VAELoader",
        "SeedVR2LoadDiTModel", "SeedVR2LoadVAEModel" -> 0.00f to 0.03f

        "MiniMaxH3SigmaShift", "LoraLoaderModelOnly",
        "MiniMaxH3MemoryEfficientSageAttentionPatch", "ModelPreviewOverrideKJ" -> 0.03f to 0.04f

        "LoadImage", "LoadVideo", "LoadAudio", "GetVideoComponents",
        "MiniMaxH3CustomKeyframes", "MiniMaxH3ExistingVideoMaskedContext",
        "H3IdentityAnchor", "H3CacheBust" -> 0.04f to 0.055f

        "MiniMaxH3ImageToVideo", "MiniMaxH3ReferenceToVideo" -> 0.055f to 0.075f
        "BasicGuider", "RandomNoise", "BasicScheduler", "KSamplerSelect",
        "PrimitiveStringMultiline", "StringConcatenate" -> 0.075f to 0.08f

        // Zvětšovač je stejně jako vzorkovač ta dlouhá část běhu.
        "SamplerCustomAdvanced", "SeedVR2VideoUpscaler",
        "RTXVideoSuperResolution" -> 0.08f to 0.86f

        "VAEDecodeAudio" -> 0.86f to 0.87f
        "VAEDecode" -> 0.87f to 0.89f
        "CreateVideo", "SaveVideo", "MiniMaxH3MotionContextTrim",
        "MiniMaxH3AssembleExtension", "H3AudioJoinSmooth",
        "ImageFromBatch", "ImageStitch", "SaveImage" -> 0.89f to 0.90f

        else -> 0.08f to 0.86f
    }

    /** Uzly, které hlásí kroky (vzorkování i zvětšování). */
    fun reportsSteps(cls: String?): Boolean =
        cls == "SamplerCustomAdvanced" || cls == "SeedVR2VideoUpscaler"

    /** Mapa „číslo uzlu → třída" z hotového grafu, podle které se fáze poznávají. */
    fun nodeClasses(wf: JSONObject): Map<String, String> =
        wf.keys().asSequence().mapNotNull { id ->
            wf.optJSONObject(id)?.optString("class_type")?.takeIf { it.isNotEmpty() }?.let { id to it }
        }.toMap()
}
