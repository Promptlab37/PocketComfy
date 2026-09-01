package cz.promptlab.h3video.comfy

import android.content.Context
import cz.promptlab.h3video.R
import org.json.JSONObject

/**
 * Stavitel grafu pro kartu **Oprava fotky** — Qwen Image Edit 2511
 * (`res/raw/workflow_qwen_restore.json`, převzaté z uživatelova
 * `Qwen_2511_Restore_Promptlab.json` včetně vyladěného opravovacího
 * zadání). Dosazuje se JEN fotka a seed.
 *
 * Jediná úmyslná odchylka od exportu: model je fp8 kvantizace
 * (`qwen_image_edit_2511_fp8_e4m3fn`) místo bf16 — bf16 má 40 GB a na
 * disku serveru nebyl; fp8 je poloviční a na 16GB kartě běží líp.
 */
object RestoreBuilder {

    const val N_UNET = "325"
    const val N_IMAGE = "107"
    const val N_PROMPT = "223"
    const val N_ENCODE = "284"
    const val N_SAMPLER = "302"
    const val N_SAVE = "320"

    /** Kroky z předlohy (Lightning LoRA na 4 kroky). */
    const val STEPS = 4

    private var cached: String? = null

    private fun template(ctx: Context): String = cached ?: ctx.resources
        .openRawResource(R.raw.workflow_qwen_restore)
        .bufferedReader().use { it.readText() }.also { cached = it }

    fun build(ctx: Context, seed: Long, images: List<String>): JSONObject =
        build(template(ctx), seed, images)

    /** Stejné sestavení z textu předlohy, ať jde graf ověřit testem bez Androidu. */
    fun build(template: String, seed: Long, images: List<String>): JSONObject {
        val wf = JSONObject(template)
        wf.inputs(N_IMAGE).put("image", images.getOrElse(0) { "" })
        wf.inputs(N_SAMPLER).put("seed", seed)
        return wf
    }

    private fun JSONObject.inputs(node: String): JSONObject =
        getJSONObject(node).getJSONObject("inputs")

    fun stageForClass(cls: String?): Stage = when (cls) {
        "UNETLoader", "LoraLoaderModelOnly", "CLIPLoader", "VAELoader" -> Stage.MODELS
        "LoadImage", "QwenEditConfigPreparer" -> Stage.REFERENCES
        "PrimitiveStringMultiline", "TextEncodeQwenImageEditPlusCustom_lrzjason",
        "ConditioningZeroOut" -> Stage.ENCODING
        "KSampler" -> Stage.SAMPLING
        "VAEDecode", "SaveImage" -> Stage.MUXING
        else -> Stage.SAMPLING
    }

    fun rangeForClass(cls: String?): Pair<Float, Float> = when (cls) {
        "UNETLoader", "LoraLoaderModelOnly", "CLIPLoader", "VAELoader" -> 0.00f to 0.10f
        "LoadImage", "QwenEditConfigPreparer" -> 0.10f to 0.12f
        "PrimitiveStringMultiline", "TextEncodeQwenImageEditPlusCustom_lrzjason",
        "ConditioningZeroOut" -> 0.12f to 0.18f
        "KSampler" -> 0.18f to 0.84f
        "VAEDecode" -> 0.84f to 0.89f
        "SaveImage" -> 0.89f to 0.90f
        else -> 0.18f to 0.84f
    }

    fun reportsSteps(cls: String?): Boolean = cls == "KSampler"

    fun nodeClasses(wf: JSONObject): Map<String, String> =
        wf.keys().asSequence().mapNotNull { id ->
            wf.optJSONObject(id)?.optString("class_type")?.takeIf { it.isNotEmpty() }?.let { id to it }
        }.toMap()
}
