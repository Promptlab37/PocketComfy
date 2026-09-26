package cz.promptlab.h3video.comfy

import android.content.Context
import cz.promptlab.h3video.R
import cz.promptlab.h3video.data.UpscaleScene
import org.json.JSONObject

/**
 * Stavitel grafu pro **chytré zvětšení** — třetí metoda karty *Zvětšit*.
 *
 * Balík [ComfyUI-Smart-Upscaler](https://github.com/HallettVisual/ComfyUI-Smart-Upscaler)
 * (HallettVisual, MIT, v1.3.0): vidoucí model Qwen3-VL 4B přečte celý obrázek
 * a každé dlaždici napíše vlastní prompt; dlaždice pak přegeneruje Z-Image
 * Turbo s ControlNetem Tile (drží tvar) a nakonec se slepí se srovnáním barev.
 * Na rozdíl od SeedVR2 a DLSS **dokresluje nový detail**.
 *
 * Předloha `res/raw/workflow_smart_upscale.json` je autorovo
 * `Smart-Upscaler-Z-Turbo-v2.json` převedené do API tvaru. Odchylky od něj:
 *  - uzly Set/Get (jen pro editor) jsou rozpojené na přímé spoje,
 *  - přepínač generátorů je pryč — generátor je jediný (Z-Turbo),
 *  - `z_image_turbo_nvfp4` → `z_image_turbo_bf16` (nvfp4 umí jen Blackwell,
 *    na serveru je RTX 4060 Ti),
 *  - `ae.safetensors` → `ae.sft` (tentýž soubor, jiné jméno na serveru),
 *  - barvy: předvolba „Automatic" (v balíku 1.3.0 se ta z workflow přejmenovala),
 *  - náhledy, porovnávače, inspektor a log promptů jsou vynechané.
 * Dosazuje se jen fotka, míra zvětšení a seed.
 */
object SmartUpscaleBuilder {

    const val N_IMAGE = "1"
    const val N_PLANOVAC = "1217"
    const val N_DIREKTOR = "1218"
    const val N_SAVE = "32"

    /** Míry zvětšení nabízené v kartě (autor: vyšší = víc dlaždic a času). */
    val NASOBKY = listOf(1.5f, 2f, 3f, 4f)

    private var cached: String? = null

    private fun template(ctx: Context): String = cached ?: ctx.resources
        .openRawResource(R.raw.workflow_smart_upscale)
        .bufferedReader().use { it.readText() }.also { cached = it }

    fun build(ctx: Context, scene: UpscaleScene, seed: Long, images: List<String>): JSONObject =
        build(template(ctx), scene, seed, images)

    fun build(template: String, scene: UpscaleScene, seed: Long, images: List<String>): JSONObject {
        val wf = JSONObject(template)
        wf.inputs(N_IMAGE).put("image", images.getOrElse(0) { "" })
        wf.inputs(N_PLANOVAC).put("scale_factor", scene.chytreNasobek.toString().toDouble())
        wf.inputs(N_DIREKTOR).put("base_seed", seed)
        return wf
    }

    private fun JSONObject.inputs(node: String): JSONObject =
        getJSONObject(node).getJSONObject("inputs")

    /** Pozná se chytré zvětšení mezi třídami odeslaného workflow? */
    fun jeChytre(classes: Map<String, String>): Boolean =
        classes.containsValue("SmartUpscaledTilePlanner")

    /** Fáze podle třídy uzlu; `null` = do tohohle grafu nepatří. */
    fun stageForClass(cls: String?): Stage? = when (cls) {
        "LoadImage", "SmartUpscaledTilePlanner" -> Stage.REFERENCES
        "SmartCachedTextGenerate", "SmartCachedTilePromptGenerator", "SmartTileJobDirector",
        "SmartUnifiedPromptGuidance" -> Stage.ENCODING
        "UNETLoader", "CLIPLoader", "VAELoader", "ModelPatchLoader",
        "ModelSamplingAuraFlow" -> Stage.MODELS
        "SmartSamplerTileSelector", "KSampler", "VAEEncode", "VAEDecode",
        "QwenImageDiffsynthControlnet", "CLIPTextEncode", "ConditioningZeroOut",
        "SmartTileColorMatch" -> Stage.SAMPLING
        "SmartTileFinalizer", "SaveImage" -> Stage.MUXING
        else -> null
    }

    fun rangeForClass(cls: String?): Pair<Float, Float>? = when (stageForClass(cls)) {
        Stage.REFERENCES -> 0.00f to 0.05f
        Stage.ENCODING -> 0.05f to 0.35f
        Stage.MODELS -> 0.35f to 0.40f
        Stage.SAMPLING -> 0.40f to 0.94f
        Stage.MUXING -> 0.94f to 0.99f
        else -> null
    }

    /**
     * KSampler běží jednou za každou dlaždici a pokaždé hlásí kroky od nuly —
     * ukazatel navázaný na kroky by u každé dlaždice skočil zpátky. Průběh se
     * vede podle fází.
     */
    fun reportsSteps(cls: String?): Boolean = false
}
