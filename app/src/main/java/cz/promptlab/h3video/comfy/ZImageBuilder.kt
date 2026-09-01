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

    /** Kroky z předlohy — ukazatel průběhu na ně přepočítává hlášení serveru. */
    const val STEPS = 8

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

    fun build(ctx: Context, prompt: String, aspect: Aspect, seed: Long): JSONObject =
        build(template(ctx), prompt, aspect, seed)

    /** Stejné sestavení z textu předlohy, ať jde graf ověřit testem bez Androidu. */
    fun build(template: String, prompt: String, aspect: Aspect, seed: Long): JSONObject {
        val wf = JSONObject(template)
        wf.inputs(N_TEXT).put("text", prompt)
        val (w, h) = sizeFor(aspect)
        wf.inputs(N_LATENT).put("width", w)
        wf.inputs(N_LATENT).put("height", h)
        wf.inputs(N_SAMPLER).put("seed", seed)
        return wf
    }

    private fun JSONObject.inputs(node: String): JSONObject =
        getJSONObject(node).getJSONObject("inputs")

    fun stageForClass(cls: String?): Stage = when (cls) {
        "UNETLoader", "CLIPLoader", "VAELoader", "ModelSamplingAuraFlow" -> Stage.MODELS
        "CLIPTextEncode", "ConditioningZeroOut", "EmptySD3LatentImage" -> Stage.ENCODING
        "KSampler" -> Stage.SAMPLING
        "VAEDecode", "SaveImage" -> Stage.MUXING
        else -> Stage.SAMPLING
    }

    fun rangeForClass(cls: String?): Pair<Float, Float> = when (cls) {
        "UNETLoader", "CLIPLoader", "VAELoader", "ModelSamplingAuraFlow" -> 0.00f to 0.10f
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
