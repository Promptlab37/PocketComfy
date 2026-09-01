package cz.promptlab.h3video.comfy

import android.content.Context
import cz.promptlab.h3video.R
import org.json.JSONObject

/**
 * Stavitel grafu pro kartu **Výměna tváře** — ACE++ (Flux Fill inpaint
 * s portrétní LoRA), `res/raw/workflow_ace_faceswap.json` z uživatelova
 * `FACESWAP_THEBEST_ACE++.json`. Dosazují se JEN dvě fotky a seed.
 *
 * Odchylky od exportu: náhledové uzly (PreviewImage, ImageAndMaskPreview)
 * jsou vynechané — v historii by se pletly do výstupů; Flux Fill je fp8
 * kvantizace (`flux1-Fill-Dev_FP8`) místo plného dev modelu, jak ostatně
 * doporučuje poznámka přímo v jeho workflow; a TeaCache je pryč — balík
 * je nekompatibilní s dnešním ComfyUI a jeho vlastní poznámka říká, že
 * bez TeaCache je výsledek nejkvalitnější (jen o desítky sekund pomalejší).
 */
object FaceSwapBuilder {

    const val N_UNET = "340"
    const val N_TARGET = "239"
    const val N_FACE = "240"
    const val N_LORA = "337"
    const val N_CROP = "420"
    const val N_STITCH = "421"
    const val N_SAMPLER = "346"
    const val N_SAVE = "413"

    /** Kroky z předlohy (Turbo LoRA na 12 kroků). */
    const val STEPS = 12

    private var cached: String? = null

    private fun template(ctx: Context): String = cached ?: ctx.resources
        .openRawResource(R.raw.workflow_ace_faceswap)
        .bufferedReader().use { it.readText() }.also { cached = it }

    fun build(ctx: Context, seed: Long, images: List<String>): JSONObject =
        build(template(ctx), seed, images)

    /**
     * [images] v pořadí: cílová fotka s maskou v alfě, nová tvář.
     * Stejné sestavení z textu předlohy, ať jde graf ověřit testem.
     */
    fun build(template: String, seed: Long, images: List<String>): JSONObject {
        val wf = JSONObject(template)
        wf.inputs(N_TARGET).put("image", images.getOrElse(0) { "" })
        wf.inputs(N_FACE).put("image", images.getOrElse(1) { "" })
        wf.inputs(N_SAMPLER).put("seed", seed)
        return wf
    }

    private fun JSONObject.inputs(node: String): JSONObject =
        getJSONObject(node).getJSONObject("inputs")

    fun stageForClass(cls: String?): Stage = when (cls) {
        "UNETLoader", "DualCLIPLoader", "VAELoader",
        "Power Lora Loader (rgthree)" -> Stage.MODELS
        "LoadImage", "InpaintCropImproved", "ImageResize+", "ImageConcanate",
        "EmptyImage", "ResizeMask", "MaskToImage", "ImageToMask",
        "ImpactGaussianBlurMask" -> Stage.REFERENCES
        "CLIPTextEncode", "FluxGuidance", "ConditioningZeroOut",
        "InpaintModelConditioning" -> Stage.ENCODING
        "KSampler" -> Stage.SAMPLING
        "VAEDecode", "ImageCrop", "InpaintStitchImproved", "SaveImage" -> Stage.MUXING
        else -> Stage.SAMPLING
    }

    fun rangeForClass(cls: String?): Pair<Float, Float> = when (stageForClass(cls)) {
        Stage.MODELS -> 0.00f to 0.10f
        Stage.REFERENCES -> 0.10f to 0.14f
        Stage.ENCODING -> 0.14f to 0.18f
        Stage.SAMPLING -> 0.18f to 0.84f
        else -> 0.84f to 0.90f
    }

    fun reportsSteps(cls: String?): Boolean = cls == "KSampler"

    fun nodeClasses(wf: JSONObject): Map<String, String> =
        wf.keys().asSequence().mapNotNull { id ->
            wf.optJSONObject(id)?.optString("class_type")?.takeIf { it.isNotEmpty() }?.let { id to it }
        }.toMap()
}
