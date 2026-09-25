package cz.promptlab.h3video.comfy

import android.content.Context
import cz.promptlab.h3video.R
import cz.promptlab.h3video.data.ImageEditScene
import org.json.JSONArray
import org.json.JSONObject

/**
 * API graf pro nativní podporu **Qwen-Image 2.1** v ComfyUI.
 *
 * Qwen Image 2.1 je sjednocený model pro generování i editaci. Obrázky se
 * neposílají zvlášť do VAE a textového enkodéru: nový uzel
 * [TextEncodeQwenImage21] je sám zakóduje oběma cestami, vrátí pozitiv,
 * negativ i prázdný latent ve velikosti prvního obrázku. Dynamické vstupy
 * mají v API tečkovaný tvar `images.image_1` až `images.image_10`.
 */
object Qwen21EditBuilder {
    const val N_UNET = "1"
    const val N_CLIP = "2"
    const val N_VAE = "3"
    const val N_CACHE = "4"
    const val N_TEXT = "10"
    const val N_SAMPLER = "20"
    const val N_DECODE = "30"
    const val N_SAVE = "40"
    const val N_FIRST_IMAGE = "100"

    /**
     * Detailer LoRA pro Qwen Image 2.1 (reverentelusarca,
     * `elusarcas-qwen-2.1-detail-enhancer-lora`). Podle autora na „detail
     * enhancement, creative upscaling, photo restoration and quality
     * improvements"; spouštěcí fráze „enhance this image".
     */
    const val DETAILER = "elusarcas-qwen2-1-detailer-v1.safetensors"
    const val N_DETAILER = "5"

    /** Věta za spouštěcí frází — autorův startovní prompt, zkrácený. */
    const val DETAILER_PROMPT =
        "Enhance this image with rich fine details, natural microdetails and improved " +
            "clarity while preserving the original composition, lighting and style."

    /**
     * Vloží detailer mezi načtený model (uzel 1) a KV cache (uzel 4). Obě
     * šablony Qwen 2.1 (úprava i text → obrázek) mají tahle čísla stejná,
     * proto jedna funkce pro všechny karty.
     */
    fun zapojDetailer(wf: JSONObject, sila: Double = 1.0) {
        wf.put(
            N_DETAILER,
            node(
                "LoraLoaderModelOnly", "Detailer",
                JSONObject()
                    .put("model", link(N_UNET))
                    .put("lora_name", DETAILER)
                    .put("strength_model", sila),
            ),
        )
        wf.inputs(N_CACHE).put("model", link(N_DETAILER))
    }

    const val MAX_IMAGES = 10
    const val DEFAULT_STEPS = 25
    const val MIN_STEPS = 10
    const val MAX_STEPS = 50

    private var cached: String? = null

    private fun template(ctx: Context): String = cached ?: ctx.resources
        .openRawResource(R.raw.workflow_qwen21_edit)
        .bufferedReader().use { it.readText() }.also { cached = it }

    fun build(ctx: Context, scene: ImageEditScene, seed: Long, images: List<String>): JSONObject =
        build(template(ctx), scene, seed, images)

    fun build(
        template: String,
        scene: ImageEditScene,
        seed: Long,
        images: List<String>,
    ): JSONObject {
        require(images.isNotEmpty()) { "Qwen Image 2.1 potřebuje upravovaný obrázek." }
        val wf = JSONObject(template)
        val used = images.filter { it.isNotBlank() }.take(MAX_IMAGES)

        wf.inputs(N_FIRST_IMAGE).put("image", used.first())
        wf.inputs(N_TEXT)
            .put(
                "prompt",
                if (scene.qwen21Detailer) scene.qwen21Prompt.trim() + " " + DETAILER_PROMPT
                else scene.qwen21Prompt,
            )
            .put("resolution", scene.qwen21Resolution.pixels)
        wf.inputs(N_SAMPLER)
            .put("seed", seed)
            .put("steps", scene.qwen21Steps.coerceIn(MIN_STEPS, MAX_STEPS))
        wf.inputs(N_CACHE)
            .put("device", scene.qwen21CacheDevice.value)
            .put("dtype", scene.qwen21CachePrecision.value)

        // První LoadImage už je v předloze. Další vznikají jen pro skutečně
        // vyplněné reference, takže API neposílá uzlu prázdné dynamické sloty.
        for (index in 1 until used.size) {
            val nodeId = (N_FIRST_IMAGE.toInt() + index).toString()
            wf.put(
                nodeId,
                node(
                    "LoadImage", "Obrázek ${index + 1}",
                    JSONObject().put("image", used[index]),
                ),
            )
            wf.inputs(N_TEXT).put("images.image_${index + 1}", link(nodeId))
        }
        if (scene.qwen21Detailer) zapojDetailer(wf)
        return wf
    }

    private fun node(trida: String, titulek: String, vstupy: JSONObject): JSONObject =
        JSONObject()
            .put("class_type", trida)
            .put("inputs", vstupy)
            .put("_meta", JSONObject().put("title", titulek))

    private fun link(id: String, slot: Int = 0): JSONArray = JSONArray().put(id).put(slot)

    private fun JSONObject.inputs(node: String): JSONObject =
        getJSONObject(node).getJSONObject("inputs")

    fun stageForClass(cls: String?): Stage = when (cls) {
        "UNETLoader", "CLIPLoader", "VAELoader", "QwenImage21Cache" -> Stage.MODELS
        "LoadImage" -> Stage.REFERENCES
        "TextEncodeQwenImage21" -> Stage.ENCODING
        "KSampler" -> Stage.SAMPLING
        "VAEDecode" -> Stage.DECODING
        "SaveImage" -> Stage.MUXING
        else -> Stage.SAMPLING
    }

    fun rangeForClass(cls: String?): Pair<Float, Float> = when (stageForClass(cls)) {
        Stage.MODELS -> 0.00f to 0.10f
        Stage.REFERENCES -> 0.10f to 0.16f
        Stage.ENCODING -> 0.16f to 0.28f
        Stage.SAMPLING -> 0.28f to 0.92f
        Stage.DECODING -> 0.92f to 0.97f
        else -> 0.97f to 1.00f
    }

    fun reportsSteps(cls: String?): Boolean = cls == "KSampler"

    fun nodeClasses(wf: JSONObject): Map<String, String> =
        wf.keys().asSequence().mapNotNull { id ->
            wf.optJSONObject(id)?.optString("class_type")?.takeIf { it.isNotEmpty() }?.let { id to it }
        }.toMap()
}
