package cz.promptlab.h3video.comfy

import android.content.Context
import cz.promptlab.h3video.R
import cz.promptlab.h3video.data.UpravaScene
import org.json.JSONArray
import org.json.JSONObject

/**
 * Stavitel grafu pro **Upravit video → Podle zadání** — Bernini-R na Wan 2.2
 * (`res/raw/workflow_bernini_edit.json`, z blueprintu ComfyUI
 * „Video Edit (Bernini-R)").
 *
 * Video se upraví podle věty („vyměň pozadí za ulici", „ať je to v noci"…),
 * volitelně s fotkou jako referencí (úloha rv2v). Pohyb, záběr a délka zůstávají
 * z videa.
 *
 * ### Odchylky od blueprintu
 *
 * 1. Váhy `int8_convrot` místo `fp8_scaled` — jako SCAIL-2 a Wan Animate na
 *    tomhle serveru.
 * 2. Systémová věta se k zadání lepí v appce (blueprint ji vybírá uzlem
 *    CustomCombo + regex). Věty i lepení bez mezery jsou z
 *    `bernini/prompt_enhancer.py` a `pipeline.py` autorů.
 * 3. Video se předem ořízne na [SNIMKU] snímků (Video Slice podle fps videa),
 *    aby zvuk nebyl delší než obraz. Blueprint bere prvních 81 snímků a zvuk
 *    nechává celý.
 * 4. Plátno podle poměru stran videa, delší strana [DELSI] — autoři u v2v
 *    nechávají velikost zdroje s `max_image_size` 848.
 */
object BerniniBuilder {

    const val N_VIDEO = "901"
    const val N_USEK = "705"
    const val N_REFERENCE = "902"
    const val N_ZADANI = "354"
    const val N_PODMINKA = "352"
    const val N_SEED = "349"
    const val N_HIGH = "349"
    const val N_LOW = "351"
    const val N_UNET_HIGH = "344"
    const val N_UNET_LOW = "346"
    const val N_LORA_HIGH = "345"
    const val N_LORA_LOW = "340"
    const val N_KROKY = "350"
    const val N_ROZDELENI = "342"

    /** Snímků na běh — výchozí `num_frames` autorů i blueprintu. */
    const val SNIMKU = 81
    const val DELSI = 848

    /** Blueprint: turbo LoRA 6 kroků (3 + 3), cfg 1; bez ní 40 (20 + 20), cfg 5. */
    const val KROKY_RYCHLE = 6
    const val KROKY_KVALITA = 40
    private const val CFG_RYCHLE = 1.0
    private const val CFG_KVALITA = 5.0

    /** Systémové věty autorů (`SYSTEM_PROMPTS` v prompt_enhancer.py). */
    const val SYSTEM_V2V = "You are a helpful assistant specialized in video editing."
    const val SYSTEM_RV2V = "You are a helpful assistant specialized in video editing with reference."

    /** Když appka fps videa nezjistí — běžná hodnota telefonů. */
    private const val FPS_NEZNAME = 30f

    private var cached: String? = null

    private fun template(ctx: Context): String = cached ?: ctx.resources
        .openRawResource(R.raw.workflow_bernini_edit)
        .bufferedReader().use { it.readText() }.also { cached = it }

    fun build(ctx: Context, scene: UpravaScene, seed: Long, video: String, reference: String?): JSONObject =
        build(template(ctx), scene, seed, video, reference)

    /**
     * Snímky běhu: 81, u kratšího videa nejbližší nižší 4n+1. Když telefon
     * počet snímků neřekne (Android 8), odhadne se z délky a fps.
     */
    fun snimku(scene: UpravaScene): Int {
        val dostupne = when {
            scene.videoSnimku > 0 -> scene.videoSnimku
            scene.videoSekund > 0f -> (scene.videoSekund * fps(scene)).toInt()
            else -> SNIMKU
        }
        return if (dostupne in 5 until SNIMKU) (dostupne - 1) / 4 * 4 + 1 else SNIMKU
    }

    fun fps(scene: UpravaScene): Float =
        if (scene.videoSnimku > 0 && scene.videoSekund > 0f) scene.videoSnimku / scene.videoSekund
        else FPS_NEZNAME

    /** Kolik sekund z videa se upraví. */
    fun sekund(scene: UpravaScene): Float = snimku(scene) / fps(scene)

    /** Plátno: delší strana 848, poměr z videa, obě strany násobkem 16. */
    fun platno(scene: UpravaScene): Pair<Int, Int> {
        val w = scene.videoSirka; val h = scene.videoVyska
        val pomer = if (w > 0 && h > 0) w.toFloat() / h else if (scene.naVysku) 9f / 16f else 16f / 9f
        fun na16(x: Float) = maxOf(16, Math.round(x / 16f) * 16)
        return if (pomer >= 1f) DELSI to na16(DELSI / pomer) else na16(DELSI * pomer) to DELSI
    }

    fun build(template: String, scene: UpravaScene, seed: Long, video: String, reference: String?): JSONObject {
        val wf = JSONObject(template)
        wf.inputs(N_VIDEO).put("file", video)
        val snimku = snimku(scene)
        // Půl snímku navíc, ať řez nepadne těsně před posledním snímkem.
        wf.inputs(N_USEK).put("duration", ((snimku + 0.5f) / fps(scene)).toDouble())

        val (w, h) = platno(scene)
        wf.inputs(N_PODMINKA).apply {
            put("width", w)
            put("height", h)
            put("length", snimku)
        }

        val system = if (reference != null) {
            wf.inputs(N_REFERENCE).put("image", reference)
            wf.inputs(N_PODMINKA).put("reference_images.reference_image_0", JSONArray().put(N_REFERENCE).put(0))
            SYSTEM_RV2V
        } else {
            wf.remove(N_REFERENCE)
            SYSTEM_V2V
        }
        wf.inputs(N_ZADANI).put("text", system + scene.popis.trim())

        if (scene.zadaniRychle) {
            wf.inputs(N_KROKY).put("steps", KROKY_RYCHLE)
            wf.inputs(N_ROZDELENI).put("step", KROKY_RYCHLE / 2)
        } else {
            wf.remove(N_LORA_HIGH)
            wf.remove(N_LORA_LOW)
            wf.inputs(N_HIGH).put("model", JSONArray().put(N_UNET_HIGH).put(0))
            wf.inputs(N_LOW).put("model", JSONArray().put(N_UNET_LOW).put(0))
            wf.inputs(N_KROKY).put("steps", KROKY_KVALITA)
            wf.inputs(N_ROZDELENI).put("step", KROKY_KVALITA / 2)
            wf.inputs(N_HIGH).put("cfg", CFG_KVALITA)
            wf.inputs(N_LOW).put("cfg", CFG_KVALITA)
        }
        wf.inputs(N_SEED).put("noise_seed", seed)
        return wf
    }

    private fun JSONObject.inputs(node: String): JSONObject =
        getJSONObject(node).getJSONObject("inputs")

    fun kroky(scene: UpravaScene): Int = if (scene.zadaniRychle) KROKY_RYCHLE else KROKY_KVALITA

    fun stageForClass(cls: String?): Stage = when (cls) {
        "UNETLoader", "LoraLoaderModelOnly", "VAELoader", "CLIPLoader" -> Stage.MODELS
        "LoadVideo", "Video Slice", "GetVideoComponents", "LoadImage" -> Stage.REFERENCES
        "CLIPTextEncode", "BerniniConditioning", "KSamplerSelect", "BasicScheduler",
        "SplitSigmas" -> Stage.ENCODING
        "SamplerCustom" -> Stage.SAMPLING
        else -> Stage.MUXING
    }

    fun rangeForClass(cls: String?): Pair<Float, Float> = when (stageForClass(cls)) {
        Stage.MODELS -> 0.00f to 0.08f
        Stage.REFERENCES -> 0.08f to 0.12f
        Stage.ENCODING -> 0.12f to 0.20f
        Stage.SAMPLING -> 0.20f to 0.92f
        else -> 0.92f to 1.00f
    }

    /**
     * Dva vzorkovače za sebou (high a low noise), každý hlásí své kroky od nuly —
     * pásmo vzorkování se proto dělí napůl podle ID uzlu, jinak by ukazatel
     * v polovině skočil zpátky.
     */
    fun rangeForNode(node: String?, cls: String?): Pair<Float, Float> = when (node) {
        N_HIGH -> 0.20f to 0.56f
        N_LOW -> 0.56f to 0.92f
        else -> rangeForClass(cls)
    }

    fun reportsSteps(cls: String?): Boolean = cls == "SamplerCustom"

    fun nodeClasses(wf: JSONObject): Map<String, String> =
        wf.keys().asSequence().mapNotNull { id ->
            wf.optJSONObject(id)?.optString("class_type")?.takeIf { it.isNotEmpty() }?.let { id to it }
        }.toMap()
}
