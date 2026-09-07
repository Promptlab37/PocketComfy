package cz.promptlab.h3video.comfy

import android.content.Context
import cz.promptlab.h3video.R
import cz.promptlab.h3video.data.ImageEditScene
import org.json.JSONArray
import org.json.JSONObject

/**
 * Stavitel grafu pro kartu **Úprava obrázku**, větev **Qwen Image Edit 2511**.
 *
 * Ze tří motorů karty je tenhle na zadání nejposlušnější: Krea 2 drží podobu
 * až moc a Klein skládá scénu znovu, kdežto Qwen je od začátku dělaný na
 * pokyny typu „převleč ji do červeného kabátu" a původní obraz mění jen tam,
 * kde má.
 *
 * Model dostane fotku dvakrát a pokaždé jinak: jednou jako **plátno**
 * (zakódovaná jde do vzorkovače) a jednou jako **obrázek k zadání**
 * (`TextEncodeQwenImageEditPlus` ji vidí spolu s textem). Proto se do obou
 * míst musí posílat tatáž, už srovnaná podoba — a proto je před nimi
 * `FluxKontextImageScale`, který ji upraví na rozměr, jaký model umí.
 *
 * Rychlost proti kvalitě řeší Lightning LoRA: s ní čtyři kroky a cfg 1,
 * bez ní čtyřicet kroků a cfg 4. Obojí má oficiální předloha ComfyUI, jen
 * schované za přepínačem — appka z toho dělá volbu na kartě.
 */
object QwenEditBuilder {

    const val N_UNET = "1"
    const val N_CLIP = "2"
    const val N_VAE = "3"
    const val N_SHIFT = "5"
    const val N_CFGNORM = "6"
    const val N_IMAGE = "20"
    const val N_SCALE = "21"
    const val N_ENCODE = "23"
    const val N_TEXT = "30"
    const val N_NEG = "31"
    const val N_REF_POS = "32"
    const val N_REF_NEG = "33"
    const val N_SAMPLER = "40"
    const val N_SAVE = "60"

    /** Lightning LoRA. Do grafu se vkládá, jen když se zvolí rychlá cesta. */
    const val N_LORA = "7"
    const val LORA_FILE = "Qwen-Image-Edit-2511-Lightning-4steps-V1.0-bf16.safetensors"

    /** Druhá předloha. */
    const val N_IMAGE2 = "25"
    const val N_SCALE2 = "26"

    /** Hodnoty z oficiální předlohy pro obě cesty. */
    const val KROKY_RYCHLE = 4
    const val CFG_RYCHLE = 1.0
    const val KROKY_KVALITNE = 40
    const val CFG_KVALITNE = 4.0

    private var cached: String? = null

    private fun template(ctx: Context): String = cached ?: ctx.resources
        .openRawResource(R.raw.workflow_qwen_edit)
        .bufferedReader().use { it.readText() }.also { cached = it }

    fun build(ctx: Context, scene: ImageEditScene, seed: Long, images: List<String>): JSONObject =
        build(template(ctx), scene, seed, images)

    /** Stejné sestavení z textu předlohy, ať jde graf ověřit testem bez Androidu. */
    fun build(
        template: String,
        scene: ImageEditScene,
        seed: Long,
        images: List<String>,
    ): JSONObject {
        val wf = JSONObject(template)
        wf.inputs(N_IMAGE).put("image", images.getOrElse(0) { "" })
        wf.inputs(N_TEXT).put("prompt", scene.prompt.trim())
        wf.inputs(N_SAMPLER).put("seed", seed)

        if (scene.qwenRychle) {
            // Lightning se věší až za srovnání CFG, přesně jak to má předloha.
            wf.put(
                N_LORA,
                node(
                    "LoraLoaderModelOnly", "Lightning 4 kroky",
                    JSONObject()
                        .put("model", link(N_CFGNORM))
                        .put("lora_name", LORA_FILE)
                        .put("strength_model", 1.0),
                ),
            )
            wf.inputs(N_SAMPLER).put("model", link(N_LORA))
            wf.inputs(N_SAMPLER).put("steps", KROKY_RYCHLE)
            wf.inputs(N_SAMPLER).put("cfg", CFG_RYCHLE)
        } else {
            wf.inputs(N_SAMPLER).put("steps", KROKY_KVALITNE)
            wf.inputs(N_SAMPLER).put("cfg", CFG_KVALITNE)
        }

        // Druhá předloha jde do OBOU kódování textu — pozitiv i negativ musí
        // vidět tytéž obrázky, jinak se od sebe podmínky liší víc než jen
        // zadáním a vedení se rozjede.
        val druha = images.getOrNull(1)
        if (!druha.isNullOrBlank()) {
            wf.put(N_IMAGE2, node("LoadImage", "Druha predloha", JSONObject().put("image", druha)))
            wf.put(
                N_SCALE2,
                node(
                    "FluxKontextImageScale", "Srovnat druhou",
                    JSONObject().put("image", link(N_IMAGE2)),
                ),
            )
            wf.inputs(N_TEXT).put("image2", link(N_SCALE2))
            wf.inputs(N_NEG).put("image2", link(N_SCALE2))
        }
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
        "UNETLoader", "CLIPLoader", "VAELoader", "LoraLoaderModelOnly",
        "ModelSamplingAuraFlow", "CFGNorm" -> Stage.MODELS
        "LoadImage", "FluxKontextImageScale", "VAEEncode" -> Stage.REFERENCES
        "TextEncodeQwenImageEditPlus", "FluxKontextMultiReferenceLatentMethod" -> Stage.ENCODING
        "KSampler" -> Stage.SAMPLING
        "VAEDecode" -> Stage.DECODING
        "SaveImage" -> Stage.MUXING
        else -> Stage.SAMPLING
    }

    fun rangeForClass(cls: String?): Pair<Float, Float> = when (stageForClass(cls)) {
        Stage.MODELS -> 0.00f to 0.08f
        Stage.REFERENCES -> 0.08f to 0.14f
        Stage.ENCODING -> 0.14f to 0.24f
        Stage.SAMPLING -> 0.24f to 0.92f
        Stage.DECODING -> 0.92f to 0.97f
        else -> 0.97f to 1.00f
    }

    fun reportsSteps(cls: String?): Boolean = cls == "KSampler"

    fun nodeClasses(wf: JSONObject): Map<String, String> =
        wf.keys().asSequence().mapNotNull { id ->
            wf.optJSONObject(id)?.optString("class_type")?.takeIf { it.isNotEmpty() }?.let { id to it }
        }.toMap()
}
