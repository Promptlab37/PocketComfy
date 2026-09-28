package cz.promptlab.h3video.comfy

import android.content.Context
import cz.promptlab.h3video.R
import org.json.JSONObject

/**
 * Stavitel grafu pro kartu **Výměna tváře** — ACE++ (Flux Fill inpaint
 * s portrétní LoRA), `res/raw/workflow_ace_faceswap.json` z uživatelova
 * `FACESWAP_THEBEST_ACE++.json`. Dosazují se fotky, maska, seed a pevná instrukce pro ACE++.
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
    const val N_MASK = "244"
    const val N_LORA = "337"
    const val N_CROP = "420"
    const val N_STITCH = "421"
    const val N_SAMPLER = "346"
    const val N_SAVE = "413"

    /** Kroky z předlohy (Turbo LoRA na 12 kroků). */
    const val STEPS = 12

    // --- Qwen Image 2.1 + BFS Head (`res/raw/workflow_qwen21_faceswap.json`,
    // autorovo „Head Swap V1 Qwen 2.1 Workflow" 1:1 bez náhledových uzlů):
    // obrázek 1 = cíl (tělo, póza, scéna), obrázek 2 = hlava. Obě fotky se
    // vejdou do 2 MP, latent má velikost cíle, 8 kroků s LoRA Pruna, deis_2m.
    const val Q_CIL = "470"
    const val Q_HLAVA = "475"
    const val Q_SAMPLER = "490"
    const val STEPS_QWEN = 8
    const val TRIDA_QWEN = "TextEncodeQwenImage21"
    const val Q_ZADANI = "496"

    /** Zadání z autorova workflow (docs/qwen-image-2.1.md), doslova. */
    const val ZADANI_QWEN = "head_swap: start with <image1> as the base image, keeping its lighting, environment, and background. remove the head from <image1> completely and replace it with the head from <image2>, strictly preserving the hair, eye color, nose structure from <image2>. copy the direction of the eye, head rotation, micro expressions from <image1>, high quality, sharp details, 4k"

    fun kroky(motor: cz.promptlab.h3video.data.SwapMotor): Int =
        if (motor == cz.promptlab.h3video.data.SwapMotor.QWEN21) STEPS_QWEN else STEPS

    /** Jede odeslaný graf na Qwenu? (Pozná se i po znovupřipojení.) */
    fun jeQwen(nodeClasses: Map<String, String>): Boolean = nodeClasses.containsValue(TRIDA_QWEN)

    private var cached: String? = null
    private var cachedQwen: String? = null

    private fun template(ctx: Context): String = cached ?: ctx.resources
        .openRawResource(R.raw.workflow_ace_faceswap)
        .bufferedReader().use { it.readText() }.also { cached = it }

    private fun templateQwen(ctx: Context): String = cachedQwen ?: ctx.resources
        .openRawResource(R.raw.workflow_qwen21_faceswap)
        .bufferedReader().use { it.readText() }.also { cachedQwen = it }

    fun build(ctx: Context, motor: cz.promptlab.h3video.data.SwapMotor, seed: Long, images: List<String>): JSONObject =
        if (motor == cz.promptlab.h3video.data.SwapMotor.QWEN21) buildQwen(templateQwen(ctx), seed, images)
        else build(template(ctx), seed, images)

    /** [images]: cílová fotka, nová tvář. Zadání je autorovo a pevné. */
    fun buildQwen(template: String, seed: Long, images: List<String>): JSONObject {
        val wf = JSONObject(template)
        wf.inputs(Q_CIL).put("image", images.getOrElse(0) { "" })
        wf.inputs(Q_HLAVA).put("image", images.getOrElse(1) { "" })
        wf.inputs(Q_SAMPLER).put("seed", seed)
        wf.inputs(Q_ZADANI).put("prompt", ZADANI_QWEN)
        return wf
    }

    /**
     * [images] v pořadí: čistá cílová fotka, nová tvář, maska štětce
     * (černobílý PNG, bílá = vyměnit). Stejné sestavení z textu předlohy,
     * ať jde graf ověřit testem.
     */
    fun build(template: String, seed: Long, images: List<String>): JSONObject {
        val wf = JSONObject(template)
        // Pevná instrukce modelu; šablona neuchovává uživatelská zadání.
        wf.inputs("343").put("text", "Retain face. ")
        wf.inputs(N_TARGET).put("image", images.getOrElse(0) { "" })
        wf.inputs(N_FACE).put("image", images.getOrElse(1) { "" })
        wf.inputs(N_MASK).put("image", images.getOrElse(2) { "" })
        wf.inputs(N_SAMPLER).put("seed", seed)
        return wf
    }

    private fun JSONObject.inputs(node: String): JSONObject =
        getJSONObject(node).getJSONObject("inputs")

    fun stageForClass(cls: String?): Stage = when (cls) {
        "UNETLoader", "DualCLIPLoader", "VAELoader", "CLIPLoader", "LoraLoaderModelOnly",
        "QwenImage21Cache", "Power Lora Loader (rgthree)" -> Stage.MODELS
        "LoadImage", "InpaintCropImproved", "ImageResize+", "ImageConcanate",
        "EmptyImage", "ResizeMask", "MaskToImage", "ImageToMask",
        "ImpactGaussianBlurMask", "ResolutionSelector", "ImageResizeKJv2" -> Stage.REFERENCES
        "CLIPTextEncode", "FluxGuidance", "ConditioningZeroOut",
        "InpaintModelConditioning", TRIDA_QWEN, "EmptyLatentImage" -> Stage.ENCODING
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
