package cz.promptlab.h3video.comfy

import android.content.Context
import cz.promptlab.h3video.R
import cz.promptlab.h3video.data.ImageEditScene
import org.json.JSONArray
import org.json.JSONObject

/**
 * Stavitel grafu pro kartu **Úprava obrázku**, větev **FLUX.2 Klein 9B**.
 *
 * Klein není jen model na obrázky z textu — Black Forest Labs ho vydali
 * i jako editační model a ComfyUI k tomu má vlastní ukázkové předlohy
 * (`image_flux2_klein_image_edit_9b_distilled`). Appka ho do 3.29 uměla
 * jen na kartě Obrázek a v Domalovat; na Úpravě chyběl.
 *
 * Jak editace funguje: fotka se **nedosazuje jako latent k převzorkování**
 * (to by byl obyčejný img2img), ale zakóduje se a přiváže k zadání uzlem
 * `ReferenceLatent` — a to k pozitivu i k negativu, jak to má předloha.
 * Vzorkuje se do prázdného plátna o rozměrech předlohy, takže model scénu
 * skládá znovu, ale ví, jak má vypadat.
 *
 * Proti Krea 2 má dvě přednosti: bere **dvě předlohy** najednou (v zadání
 * se na ně odkazuje jako „Figure 1" a „Figure 2") a nepotřebuje k držení
 * podoby žádnou LoRA. Za to je pomalejší a nemá páčku na věrnost.
 *
 * VAE je schválně `full_encoder_small_decoder` a ne `flux2-vae` jako
 * u obrázků z textu: při editaci se předloha **kóduje**, takže na kvalitě
 * enkodéru záleží. Tak to má i oficiální předloha.
 */
object KleinEditBuilder {

    const val N_UNET = "1"
    const val N_CLIP = "2"
    const val N_VAE = "3"
    const val N_IMAGE = "20"
    const val N_SCALE = "21"
    const val N_SIZE = "22"
    const val N_ENCODE = "23"
    const val N_TEXT = "30"
    const val N_ZERO = "32"
    const val N_REF_POS = "33"
    const val N_REF_NEG = "34"
    const val N_LATENT = "10"
    const val N_SCHEDULER = "40"
    const val N_NOISE = "42"
    const val N_GUIDER = "43"
    const val N_SAMPLER = "44"
    const val N_SAVE = "60"

    /** Druhá předloha: uzly vzniknou, jen když ji uživatel přiloží. */
    const val N_IMAGE2 = "25"
    const val N_SCALE2 = "26"
    const val N_ENCODE2 = "27"
    const val N_REF_POS2 = "35"
    const val N_REF_NEG2 = "36"

    /** Kroky z předlohy — destilovaný Klein jich víc nepotřebuje. */
    const val STEPS = 4

    private var cached: String? = null

    private fun template(ctx: Context): String = cached ?: ctx.resources
        .openRawResource(R.raw.workflow_flux2_klein_edit)
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
        wf.inputs(N_TEXT).put("text", scene.prompt.trim())
        wf.inputs(N_NOISE).put("noise_seed", seed)

        // Druhá předloha se nevkládá jako druhý obrázek do plátna, ale jako
        // další reference navěšená na tutéž podmínku. Odkazuje se na ni
        // v zadání slovy („the bag from Figure 2"), proto se nikam nekreslí.
        val druha = images.getOrNull(1)
        if (!druha.isNullOrBlank()) {
            wf.put(
                N_IMAGE2,
                node("LoadImage", "Druha predloha", JSONObject().put("image", druha)),
            )
            wf.put(
                N_SCALE2,
                node(
                    "ImageScaleToTotalPixels", "Srovnat druhou na 1 MP",
                    JSONObject()
                        .put("image", link(N_IMAGE2))
                        .put("upscale_method", "lanczos")
                        .put("megapixels", 1.0)
                        .put("resolution_steps", 1),
                ),
            )
            wf.put(
                N_ENCODE2,
                node(
                    "VAEEncode", "Druha predloha do latentu",
                    JSONObject().put("pixels", link(N_SCALE2)).put("vae", link(N_VAE)),
                ),
            )
            wf.put(
                N_REF_POS2,
                node(
                    "ReferenceLatent", "Druha predloha k zadani",
                    JSONObject().put("conditioning", link(N_REF_POS)).put("latent", link(N_ENCODE2)),
                ),
            )
            wf.put(
                N_REF_NEG2,
                node(
                    "ReferenceLatent", "Druha predloha k negativu",
                    JSONObject().put("conditioning", link(N_REF_NEG)).put("latent", link(N_ENCODE2)),
                ),
            )
            wf.inputs(N_GUIDER).put("positive", link(N_REF_POS2))
            wf.inputs(N_GUIDER).put("negative", link(N_REF_NEG2))
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
        "UNETLoader", "CLIPLoader", "VAELoader" -> Stage.MODELS
        "LoadImage", "ImageScaleToTotalPixels", "GetImageSize", "VAEEncode" -> Stage.REFERENCES
        "CLIPTextEncode", "ConditioningZeroOut", "ReferenceLatent",
        "EmptyFlux2LatentImage", "Flux2Scheduler", "KSamplerSelect", "RandomNoise",
        "CFGGuider" -> Stage.ENCODING
        "SamplerCustomAdvanced" -> Stage.SAMPLING
        "VAEDecode" -> Stage.DECODING
        "SaveImage" -> Stage.MUXING
        else -> Stage.SAMPLING
    }

    fun rangeForClass(cls: String?): Pair<Float, Float> = when (stageForClass(cls)) {
        Stage.MODELS -> 0.00f to 0.10f
        Stage.REFERENCES -> 0.10f to 0.18f
        Stage.ENCODING -> 0.18f to 0.26f
        Stage.SAMPLING -> 0.26f to 0.90f
        Stage.DECODING -> 0.90f to 0.96f
        else -> 0.96f to 1.00f
    }

    fun reportsSteps(cls: String?): Boolean = cls == "SamplerCustomAdvanced"

    fun nodeClasses(wf: JSONObject): Map<String, String> =
        wf.keys().asSequence().mapNotNull { id ->
            wf.optJSONObject(id)?.optString("class_type")?.takeIf { it.isNotEmpty() }?.let { id to it }
        }.toMap()
}
