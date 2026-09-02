package cz.promptlab.h3video.comfy

import android.content.Context
import cz.promptlab.h3video.R
import cz.promptlab.h3video.data.Aspect
import org.json.JSONObject

/**
 * Stavitel grafu pro kartu **Obrázek** — uživatelovo Z-Image Turbo workflow
 * (`res/raw/workflow_zimage_t2i.json`, převzaté 1:1 z jeho exportu
 * `image_z_image_turbo_PROMPTLAB.json`, jen narovnané ze subgrafu do API
 * podoby). Dosazují se přesně čtyři hodnoty: zadání, šířka, výška a seed.
 * Kroky (8), cfg 1, sampler i shift zůstávají z předlohy.
 */
object ZImageBuilder {

    const val N_UNET = "28"
    const val N_CLIP = "30"
    const val N_VAE = "29"
    const val N_TEXT = "27"
    const val N_ZERO = "33"
    const val N_LATENT = "13"
    const val N_SHIFT = "11"
    const val N_SAMPLER = "3"
    const val N_DECODE = "8"
    const val N_SAVE = "9"

    /** Odvázaná LoRA: uzel se do grafu vkládá jen se zapnutým přepínačem. */
    const val N_NSFW_LORA = "90"
    const val NSFW_LORA_FILE = "zimage_nsfw_v1.safetensors"

    /**
     * Odvázaný finetune místo základního Turbo: Z-Image Turbo NSFW
     * Photorealistic v6.1 (Q8 GGUF, jediná volně šiřitelná podoba — novější
     * verze si autor zamyká za Buzz). Autor doporučuje 12 kroků, cfg 1
     * a dpmpp_sde; LoRA s ním není potřeba. GGUF se načítá uzlem
     * UnetLoaderGGUF (na serveru je, pack ComfyUI-GGUF).
     */
    const val NSFW_MODEL_FILE = "zimage_nsfw_photoreal_v61_Q8.gguf"
    const val NSFW_MODEL_STEPS = 12
    const val NSFW_MODEL_SAMPLER = "dpmpp_sde"

    /** Kroky z předlohy — ukazatel průběhu na ně přepočítává hlášení serveru. */
    const val STEPS = 8

    /** Kroky podle zvoleného modelu (ukazatel průběhu s nimi musí souhlasit). */
    fun stepsFor(model: String): Int =
        if (model.isBlank()) STEPS else NSFW_MODEL_STEPS

    private var cached: String? = null

    private fun template(ctx: Context): String = cached ?: ctx.resources
        .openRawResource(R.raw.workflow_zimage_t2i)
        .bufferedReader().use { it.readText() }.also { cached = it }

    /**
     * Rozměry pro poměr stran: kolem 1 MPx (na tom Z-Image Turbo vznikl)
     * a násobky 16, které chce SD3 latent.
     */
    fun sizeFor(aspect: Aspect): Pair<Int, Int> = when (aspect) {
        Aspect.SQUARE_1_1 -> 1024 to 1024
        Aspect.LANDSCAPE_16_9 -> 1344 to 768
        Aspect.PORTRAIT_9_16 -> 768 to 1344
        Aspect.LANDSCAPE_4_3 -> 1152 to 864
        Aspect.PORTRAIT_3_4 -> 864 to 1152
        Aspect.LANDSCAPE_3_2 -> 1248 to 832
        Aspect.PORTRAIT_2_3 -> 832 to 1248
        Aspect.ULTRAWIDE_21_9 -> 1568 to 672
    }

    fun build(
        ctx: Context, prompt: String, aspect: Aspect, seed: Long,
        nsfwLora: Boolean = false, nsfwSila: Float = 1f, model: String = "",
        loraFile: String = NSFW_LORA_FILE,
    ): JSONObject = build(template(ctx), prompt, aspect, seed, nsfwLora, nsfwSila, model, loraFile)

    /** Stejné sestavení z textu předlohy, ať jde graf ověřit testem bez Androidu. */
    fun build(
        template: String, prompt: String, aspect: Aspect, seed: Long,
        nsfwLora: Boolean = false, nsfwSila: Float = 1f, model: String = "",
        loraFile: String = NSFW_LORA_FILE,
    ): JSONObject {
        val wf = JSONObject(template)
        // Jiný model = jiné doporučené vzorkování (12 kroků, dpmpp_sde dle
        // autora finetunu). Prázdný model nechává šablonu 1:1. GGUF soubor
        // potřebuje jiný loader — UNETLoader umí jen safetensors.
        if (model.isNotBlank()) {
            if (model.endsWith(".gguf")) {
                wf.put(
                    N_UNET,
                    JSONObject()
                        .put("class_type", "UnetLoaderGGUF")
                        .put("inputs", JSONObject().put("unet_name", model))
                        .put("_meta", JSONObject().put("title", "Odvázaný model (GGUF)")),
                )
            } else {
                wf.inputs(N_UNET).put("unet_name", model)
            }
            wf.inputs(N_SAMPLER).put("steps", stepsFor(model))
            wf.inputs(N_SAMPLER).put("sampler_name", NSFW_MODEL_SAMPLER)
        }
        wf.inputs(N_TEXT).put("text", prompt)
        val (w, h) = sizeFor(aspect)
        wf.inputs(N_LATENT).put("width", w)
        wf.inputs(N_LATENT).put("height", h)
        wf.inputs(N_SAMPLER).put("seed", seed)
        // Odvázaný režim: LoraLoaderModelOnly mezi UNETLoader a sigma shift.
        // Se zhasnutým přepínačem se graf šablony nemění ani o bajt.
        if (nsfwLora) {
            wf.put(
                N_NSFW_LORA,
                JSONObject()
                    .put("class_type", "LoraLoaderModelOnly")
                    .put(
                        "inputs",
                        JSONObject()
                            .put("model", org.json.JSONArray().put(N_UNET).put(0))
                            .put("lora_name", loraFile)
                            .put("strength_model", nsfwSila.toDouble()),
                    )
                    .put("_meta", JSONObject().put("title", "Odvázaná LoRA")),
            )
            wf.inputs(N_SHIFT).put("model", org.json.JSONArray().put(N_NSFW_LORA).put(0))
        }
        return wf
    }

    private fun JSONObject.inputs(node: String): JSONObject =
        getJSONObject(node).getJSONObject("inputs")

    fun stageForClass(cls: String?): Stage = when (cls) {
        "UNETLoader", "UnetLoaderGGUF", "CLIPLoader", "VAELoader",
        "ModelSamplingAuraFlow", "LoraLoaderModelOnly" -> Stage.MODELS
        "CLIPTextEncode", "ConditioningZeroOut", "EmptySD3LatentImage" -> Stage.ENCODING
        "KSampler" -> Stage.SAMPLING
        "VAEDecode", "SaveImage" -> Stage.MUXING
        else -> Stage.SAMPLING
    }

    fun rangeForClass(cls: String?): Pair<Float, Float> = when (cls) {
        "UNETLoader", "UnetLoaderGGUF", "CLIPLoader", "VAELoader",
        "ModelSamplingAuraFlow", "LoraLoaderModelOnly" -> 0.00f to 0.10f
        "CLIPTextEncode", "ConditioningZeroOut", "EmptySD3LatentImage" -> 0.10f to 0.14f
        "KSampler" -> 0.14f to 0.84f
        "VAEDecode" -> 0.84f to 0.89f
        "SaveImage" -> 0.89f to 0.90f
        else -> 0.14f to 0.84f
    }

    fun reportsSteps(cls: String?): Boolean = cls == "KSampler"

    fun nodeClasses(wf: JSONObject): Map<String, String> =
        wf.keys().asSequence().mapNotNull { id ->
            wf.optJSONObject(id)?.optString("class_type")?.takeIf { it.isNotEmpty() }?.let { id to it }
        }.toMap()
}
