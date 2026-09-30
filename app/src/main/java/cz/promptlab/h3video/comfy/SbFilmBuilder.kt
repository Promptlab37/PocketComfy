package cz.promptlab.h3video.comfy

import cz.promptlab.h3video.data.t
import cz.promptlab.h3video.data.SbFilmPlan
import cz.promptlab.h3video.data.SbFilmScene
import cz.promptlab.h3video.data.SbModel
import cz.promptlab.h3video.data.SbRozliseni
import cz.promptlab.h3video.data.SbUsek
import org.json.JSONArray
import org.json.JSONObject

/**
 * Grafy karty **Film ze storyboardu**.
 *
 * ### Čtení
 * `LoadImage` → `MiniMaxH3ReferenceCaption` (odblokovaný Qwen3-VL z disku,
 * otázka [SbFilmPlan.OTAZKA_CTENI] nahrazuje otázku role) → `PreviewAny`.
 *
 * ### Film
 * Úseky vykreslí v jednom běhu balík SatoDive (Minimax-H3-Latent-Continuation):
 * `ContextSegments` → `SegmentSampleSetup` → `SegmentStep`×N → `Collect` →
 * `Decode` → `SaveVideo`. Navazuje se přes latent (`native_guide`, kontext
 * 22 snímků jako v autorově předloze), v paměti je vždy jen jeden úsek.
 *
 * **Past, kterou tohle zapojení obchází:** `ContextSegments` bere délku úseku
 * z první značky `[Shot N] At MM:SS` v textu úseku jako jeho **celkový**
 * začátek (`_segment_durations_from_prompt`). Zadání z přepisovače má ale
 * v každém úseku časy od nuly — úsek 2 by dostal délku podle `At 00:03`.
 * Délky proto určuje plánovací text ([SbFilmPlan.planovaciText]) a skutečné
 * zadání jde do `SegmentStep.prompt_override`, kde se nic nepřepočítává
 * (`_segment_step_prompt_media`). Ověřeno během 2×5 s 28. 9. 2026.
 *
 * Dva modely ([SbFilmScene.model]):
 *  - **Turbo** — od 5.46 oficiální ref2v 8step v1.0 (lightx2v) podle specifikace
 *    autora LoRA: síla 1,0, shift 12/3, `euler`/`simple`, 8 kroků (uživatel:
 *    „dej to na 8 kroků“). Dřív Comfy-Org 4step v0.1 na 0,8 se 7 kroky bez shiftu.
 *  - **Kvalita** — plný model bez LoRA jako profil Kvalita v All in One:
 *    `MiniMaxH3SigmaShift` 12,191111/3, `euler`/`beta`, kroky 10–30.
 *    Plán kroků i nastavení úseků berou model ZA shiftem (jako uzel 9
 *    v šablonách All in One), jinak by sigmy shift nerespektovaly.
 *  - **3 + 2** — navazující záběr sestavy 3 + 2 z Long MiniMax
 *    (`LongMmBuilder.zapojShiftNavazani`): TaoMate LoRA 1,0, shift 12/3 jen
 *    na vzorkovacím modelu, plán kroků z holého UNETu (`simple`, 3 kroky),
 *    `res_multistep`.
 */
object SbFilmBuilder {

    // --- čtení
    const val N_CTENI_OBRAZEK = "1"
    const val N_CTENI = "2"
    const val N_CTENI_VYSTUP = "3"
    const val CTENI_CLASS = "MiniMaxH3ReferenceCaption"

    fun buildCteni(
        obrazek: String, model: String, seed: Long,
        otazka: String = SbFilmPlan.OTAZKA_CTENI,
    ): JSONObject = JSONObject()
        .put(N_CTENI_OBRAZEK, uzel("LoadImage", "Storyboard", JSONObject().put("image", obrazek)))
        .put(
            N_CTENI, uzel(
                CTENI_CLASS, "Čtení storyboardu", JSONObject()
                    .put("role", "Picture")
                    .put("model", model)
                    // Otázka si délku odpovědi určuje sama (řádek na panel).
                    .put("length", "detailed")
                    .put("seed", seed)
                    .put("image", odkaz(N_CTENI_OBRAZEK))
                    .put("instruction", otazka),
            ),
        )
        // Výstup 1 = samotný popis; 0 je řádek bloku referencí s „Picture 1:".
        .put(N_CTENI_VYSTUP, uzel("PreviewAny", "Panely", JSONObject().put("source", odkaz(N_CTENI, 1))))

    // --- film
    const val N_UNET = "190"
    const val N_CLIP = "191"
    const val N_VAE = "192"
    const val N_VAE_ZVUK = "193"
    const val N_SAGE = "18"
    const val N_POZORNOST = "400"
    const val N_LORA = "271"
    const val N_SHIFT = "204"
    const val N_ADAPTER = "269"
    const val N_MEDIA = "280"
    const val N_KONTEXT = "328"
    const val N_KROKY = "5"
    const val N_SAMPLER = "7"
    const val N_SETUP = "500"
    /** První úsek; další jdou po jedné nahoru. */
    const val N_USEK_PRVNI = 501
    const val N_OBRAZEK_PRVNI = 214
    const val N_COLLECT = "600"
    const val N_DECODE = "601"
    const val N_ULOZ = "602"

    const val UNET = "minimax_h3_fl2va_pruned_int8_convrot.safetensors"
    const val CLIP = "qwen3vl_32b_minimax_h3_nvfp4_awq.safetensors"
    const val VAE = "minimax_h3_video_vae_fp16.safetensors"
    const val VAE_ZVUK = "minimax_h3_audio_vae_fp32.safetensors"
    const val LORA = "minimax_h3_ref2v_turbo_8step_v1.0_768p_comfyui_bf16.safetensors"
    const val LORA_SILA = 1.0
    /** Shift obrazu Turbo LoRA podle lightx2v (HF disc. 51). */
    const val SHIFT_VIDEO_TURBO = 12.0
    const val KROKU = SbFilmScene.TURBO_KROKY
    const val SHIFT_VIDEO = 12.191111
    const val SHIFT_AUDIO = 3.0
    /** 3 + 2: posun z karty „3 kroky“ / Long MM (`LongMmBuilder.SHIFT_VIDEO`). */
    const val SHIFT_VIDEO_32 = 12.0
    const val LORA_32 = "h3\\TaoMate-H3-3step-ComfyUI.safetensors"
    const val KONTEXT_SNIMKU = 22
    const val ROZLISENI = "480P"
    const val CONTINUITY = "latent_guide"

    /**
     * @param obrazky jména nahraných obrázků v pořadí [SbFilmScene.uploadImages]
     *   (storyboard = `<Picture 1>`, postavy dál)
     * @param zadani zadání úseků z přepisovače, jedno na úsek
     */
    fun buildFilm(
        scene: SbFilmScene,
        useky: List<SbUsek>,
        zadani: List<String>,
        obrazky: List<String>,
        pomer: String,
        seed: Long,
        /** Společné nastavení „Kvalita a rychlost videa“ (5.39): Sage, nebo plná přesnost. */
        pozornost: cz.promptlab.h3video.data.Pozornost = cz.promptlab.h3video.data.Pozornost.KITCHEN,
        shiftZvuk: Double = SHIFT_AUDIO,
    ): JSONObject {
        require(useky.isNotEmpty() && useky.size == zadani.size) { t("úseky a zadání nesedí") }
        val wf = JSONObject()
        wf.put(N_UNET, uzel("UNETLoader", "Model", JSONObject().put("unet_name", UNET).put("weight_dtype", "default")))
        wf.put(N_CLIP, uzel("CLIPLoader", "Textový enkodér", JSONObject()
            .put("clip_name", CLIP).put("type", "minimax").put("device", "default")))
        wf.put(N_VAE, uzel("VAELoader", "VAE obrazu", JSONObject().put("vae_name", VAE)))
        wf.put(N_VAE_ZVUK, uzel("VAELoader", "VAE zvuku", JSONObject().put("vae_name", VAE_ZVUK)))
        // Plná přesnost pozornosti (5.39, uživatel: „Sage attention vypnout, rychle ale bez
        // ztráty kvality“). Dřív Sage + „comfy kitchen attention“ = kvantovaná INT8 pozornost.
        // „pytorch attention“ přebíjí i --use-sage-attention ze startu serveru (ostatní karty ho mají dál).
        if (pozornost == cz.promptlab.h3video.data.Pozornost.SAGE) {
            // Rychlejší: Sage patch autora (kvantovaná pozornost, šetří i VRAM).
            wf.put(N_SAGE, uzel("MiniMaxH3MemoryEfficientSageAttentionPatch", "Sage", JSONObject().put("model", odkaz(N_UNET))))
            wf.put(N_POZORNOST, uzel("ModelAttentionBackend", "Pozornost", JSONObject()
                .put("model", odkaz(N_SAGE)).put("attention", "pytorch attention")))
        } else {
            wf.put(N_POZORNOST, uzel("ModelAttentionBackend", "Attention", JSONObject()
                .put("model", odkaz(N_UNET)).put("attention", pozornost.backend)))
        }
        // model = co vzorkuje úseky, rozvrh = z čeho BasicScheduler počítá sigmy.
        val (model, rozvrh) = when (scene.model) {
            SbModel.TURBO -> {
                wf.put(N_LORA, uzel("MiniMaxH3TurboLoRA", "Turbo LoRA", JSONObject()
                    .put("model", odkaz(N_POZORNOST)).put("lora_name", LORA)
                    .put("strength", LORA_SILA).put("low_vram", false)))
                wf.put(N_SHIFT, uzel("MiniMaxH3SigmaShift", "Shift", JSONObject()
                    .put("model", odkaz(N_LORA))
                    .put("shift_video", SHIFT_VIDEO_TURBO).put("shift_audio", shiftZvuk)))
                N_SHIFT to N_SHIFT
            }
            SbModel.KVALITA -> {
                wf.put(N_SHIFT, uzel("MiniMaxH3SigmaShift", "Shift", JSONObject()
                    .put("model", odkaz(N_POZORNOST))
                    .put("shift_video", SHIFT_VIDEO).put("shift_audio", shiftZvuk)))
                N_SHIFT to N_SHIFT
            }
            SbModel.TRIPLUSDVA -> {
                wf.put(N_LORA, uzel("MiniMaxH3TurboLoRA", "TaoMate LoRA", JSONObject()
                    .put("model", odkaz(N_POZORNOST)).put("lora_name", LORA_32)
                    .put("strength", 1.0).put("low_vram", false)))
                wf.put(N_SHIFT, uzel("MiniMaxH3SigmaShift", "Shift", JSONObject()
                    .put("model", odkaz(N_LORA))
                    .put("shift_video", SHIFT_VIDEO_32).put("shift_audio", shiftZvuk)))
                // Rozvrh z holého modelu, stejně jako navázání v Long MM.
                N_SHIFT to N_UNET
            }
        }
        wf.put(N_ADAPTER, uzel("MiniMaxH3EasyModelAdapter_SatoDive", "Balík H3", JSONObject()
            .put("text_encoder", odkaz(N_CLIP)).put("video_vae", odkaz(N_VAE))
            .put("audio_vae", odkaz(N_VAE_ZVUK)).put("ref2va_model", odkaz(N_UNET))))

        val media = JSONObject().put("image_count", obrazky.size).put("video_count", 0).put("audio_count", 0)
        obrazky.forEachIndexed { i, jmeno ->
            val id = (N_OBRAZEK_PRVNI + i).toString()
            val popisek = when {
                !scene.seStoryboardem -> "Postava ${i + 1}"
                i == 0 -> "Storyboard"
                // Jméno postavy z plánu (5.17), jinak pořadí.
                else -> scene.postavy.getOrNull(i - 1)?.let { scene.jmenoFotky(it.soubor) } ?: "Postava $i"
            }
            wf.put(id, uzel("LoadImage", popisek, JSONObject().put("image", jmeno)))
            media.put("image_${i + 1}", odkaz(id))
        }
        wf.put(N_MEDIA, uzel("MiniMaxH3EasyMediaBridge_SatoDive", "Reference", media))

        val znacky = (1..obrazky.size).joinToString(" ") { "<Picture $it>" }
        val celkem = useky.sumOf { it.sekundy }
        wf.put(N_KONTEXT, uzel("MiniMaxH3EasyContextSegments_SatoDive", "Plán úseků", JSONObject()
            .put("h3_bundle", odkaz(N_ADAPTER))
            .put("mode", "context_segments").put("audio_mode", "generated")
            .put("prompt", SbFilmPlan.planovaciText(useky, znacky))
            .put("resolution", scene.rozliseni.kod).put("aspect_ratio", pomer).put("custom_ratio", pomer)
            .put("width", 1344).put("height", 768)
            .also { u ->
                // 768p = přesný nativ přes „Custom“ (balík bere width/height doslova, nodes.py ř. 3016);
                // u ostatních štítků width/height balík nečte.
                if (scene.rozliseni == SbRozliseni.R768) {
                    val (w, h) = when (pomer) { "9:16" -> 768 to 1344; "1:1" -> 768 to 768; else -> 1344 to 768 }
                    u.put("aspect_ratio", "Custom").put("width", w).put("height", h)
                }
            }
            .put("seconds", celkem)
            .put("segment_seconds", useky.joinToString(",") { "%.3f".format(java.util.Locale.ROOT, it.sekundy) })
            // latent_guide, NE native_guide: v native_guide jde chvost předchozího
            // úseku jako vodítko na snímek 0 a zůstane ve videu — 22 snímků se
            // na každém švu přehrálo znovu (změřeno 28. 9. 2026 na filmu
            // uživatele: snímky 177–186 ≈ 155–164). latent_guide vzorkuje kontext
            // jako skrytou předponu (head_frames) a Decode z ní nechá jen jeden
            // navazovací snímek.
            .put("context_length", KONTEXT_SNIMKU).put("continuity_mode", CONTINUITY)
            .put("transition_seconds", 0.0).put("advanced", false).put("fps", 24.0)
            .put("keyframe_role", "first").put("ref_image_size", "1k")
            .put("reference_mention_mode", "index")
            .put("prompt_optimizer_settings", false).put("prompt_optimizer_scene_guide", "none")
            .put("context_prompt_optimizer_mode", "whole_sequence").put("context_prompt_optimizer_concurrency", 3)
            .put("enable_detail_daemon", false).put("detail_strength", 0.0)
            .put("ref_image_strength", 1.0).put("ref_video_strength", 1.0)
            .put("ref_video1_strength", 1.0).put("ref_video2_strength", 1.0).put("ref_video3_strength", 1.0)
            .put("ref_video1_range", "").put("ref_video2_range", "").put("ref_video3_range", "")
            .put("seed_video_strength", 1.0).put("seed_video_reserved_seconds", 0.0)
            .put("media", odkaz(N_MEDIA))))

        wf.put(N_KROKY, uzel("BasicScheduler", "Kroky", JSONObject()
            .put("model", odkaz(rozvrh))
            .put("scheduler", if (scene.model == SbModel.KVALITA) "beta" else "simple")
            .put("steps", scene.kroky).put("denoise", 1.0)))
        wf.put(N_SAMPLER, uzel("KSamplerSelect", "Sampler", JSONObject().put("sampler_name",
            if (scene.model == SbModel.TRIPLUSDVA) "res_multistep" else "euler")))
        wf.put(N_SETUP, uzel("MiniMaxH3EasySegmentSampleSetup_SatoDive", "Nastavení úseků", JSONObject()
            .put("h3_context", odkaz(N_KONTEXT, 1)).put("model", odkaz(model))
            .put("sampler", odkaz(N_SAMPLER)).put("sigmas", odkaz(N_KROKY))))

        var predchozi: String? = null
        zadani.forEachIndexed { i, text ->
            val id = (N_USEK_PRVNI + i).toString()
            val vstupy = JSONObject()
                .put("seed", (seed + i) and 0xFFFFFFFFL)
                .put("prompt_override", text)
            if (predchozi == null) vstupy.put("sample_setup", odkaz(N_SETUP))
            else vstupy.put("previous_segment", odkaz(predchozi!!))
            wf.put(id, uzel("MiniMaxH3EasySegmentStep_SatoDive", "Úsek ${i + 1}", vstupy))
            predchozi = id
        }
        wf.put(N_COLLECT, uzel("MiniMaxH3EasySegmentCollect_SatoDive", "Sběr úseků",
            JSONObject().put("final_segment", odkaz(predchozi!!))))
        wf.put(N_DECODE, uzel("MiniMaxH3EasySegmentDecode_SatoDive", "Dekódování",
            JSONObject().put("segments", odkaz(N_COLLECT))))
        wf.put(N_ULOZ, uzel("SaveVideo", "Uložit", JSONObject()
            .put("video", odkaz(N_DECODE)).put("filename_prefix", "PocketSbFilm")
            .put("format", "auto").put("codec", "auto")))
        return wf
    }

    fun nodeClasses(wf: JSONObject): Map<String, String> =
        wf.keys().asSequence().mapNotNull { id ->
            wf.optJSONObject(id)?.optString("class_type")?.takeIf { it.isNotEmpty() }?.let { id to it }
        }.toMap()

    fun stageForClass(cls: String?): Stage = when (cls) {
        "UNETLoader", "CLIPLoader", "VAELoader", "MiniMaxH3TurboLoRA", "MiniMaxH3SigmaShift",
        "MiniMaxH3EasyModelAdapter_SatoDive", "MiniMaxH3MemoryEfficientSageAttentionPatch",
        "ModelAttentionBackend" -> Stage.MODELS
        "LoadImage", "MiniMaxH3EasyMediaBridge_SatoDive" -> Stage.REFERENCES
        "MiniMaxH3EasyContextSegments_SatoDive", "BasicScheduler", "KSamplerSelect",
        "MiniMaxH3EasySegmentSampleSetup_SatoDive" -> Stage.ENCODING
        "MiniMaxH3EasySegmentStep_SatoDive" -> Stage.SAMPLING
        "MiniMaxH3EasySegmentCollect_SatoDive", "MiniMaxH3EasySegmentDecode_SatoDive" -> Stage.DECODING
        else -> Stage.MUXING
    }

    /**
     * Pásmo procent pro uzel. Vzorkování (22–90 %) se dělí rovnoměrně mezi
     * úseky podle čísla uzlu, ať ukazatel mezi úseky nejde zpátky.
     */
    fun rangeForNode(id: String?, cls: String?, nodeClasses: Map<String, String>): Pair<Float, Float> =
        when (stageForClass(cls)) {
            Stage.MODELS -> 0.00f to 0.10f
            Stage.REFERENCES -> 0.10f to 0.14f
            Stage.ENCODING -> 0.14f to 0.22f
            Stage.SAMPLING -> {
                val useky = nodeClasses.filterValues { it == "MiniMaxH3EasySegmentStep_SatoDive" }
                    .keys.mapNotNull { it.toIntOrNull() }.sorted()
                val poradi = id?.toIntOrNull()?.let { useky.indexOf(it) } ?: -1
                if (useky.isEmpty() || poradi < 0) 0.22f to 0.90f
                else {
                    val krok = 0.68f / useky.size
                    (0.22f + krok * poradi) to (0.22f + krok * (poradi + 1))
                }
            }
            Stage.DECODING -> 0.90f to 0.98f
            else -> 0.98f to 1.00f
        }

    fun reportsSteps(cls: String?): Boolean = cls == "MiniMaxH3EasySegmentStep_SatoDive"

    /**
     * Krok úseku → krok celého filmu. Každý `SegmentStep` hlásí vlastní
     * `value/max` od nuly, takže počítadlo i odhad času šly u každého úseku
     * znovu od začátku (uživatel 28. 9. 2026: „ať to počítá celé video“).
     * Úsek k posune krok o k × max, celkem je počet úseků × max.
     */
    fun globalniKrok(
        id: String?, nodeClasses: Map<String, String>, value: Int, max: Int,
    ): Pair<Int, Int> {
        val useky = nodeClasses.filterValues { it == "MiniMaxH3EasySegmentStep_SatoDive" }
            .keys.mapNotNull { it.toIntOrNull() }.sorted()
        val poradi = id?.toIntOrNull()?.let { useky.indexOf(it) } ?: -1
        if (poradi < 0 || max <= 0) return value to max
        return (poradi * max + value) to (useky.size * max)
    }

    private fun uzel(cls: String, titulek: String, vstupy: JSONObject) = JSONObject()
        .put("class_type", cls)
        .put("inputs", vstupy)
        .put("_meta", JSONObject().put("title", titulek))

    private fun odkaz(uzel: String, slot: Int = 0) = JSONArray().put(uzel).put(slot)
}
