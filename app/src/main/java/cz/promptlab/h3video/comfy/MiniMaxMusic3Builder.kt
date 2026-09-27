package cz.promptlab.h3video.comfy

import android.content.Context
import cz.promptlab.h3video.R
import cz.promptlab.h3video.data.MusicScene
import org.json.JSONObject

/**
 * Stavitel grafu pro kartu **Hudba** — MiniMax Music 3
 * (`res/raw/workflow_minimax_music3.json`, z blueprintu ComfyUI
 * „Text to Music (MiniMax Music 3)" 1:1, jen výstup je MP3 jako u ostatních
 * motorů karty). Dosazuje se styl (`caption`), text písně, strop délky a seed;
 * kroky, cfg, top_k i sampler zůstávají z blueprintu.
 *
 * `max_duration` je strop — model může skončit dřív (nápověda uzlu). Délku
 * latentu si graf bere z enkodéru.
 */
object MiniMaxMusic3Builder {

    const val N_ZADANI = "13"
    const val N_SAMPLER = "9"

    /** Kroky z blueprintu. */
    const val STEPS = 30

    /** Uzel, podle kterého se běh pozná i po znovupřipojení. */
    const val TRIDA_ZADANI = "MiniMaxMusic3TextEncode"

    private var cached: String? = null

    private fun template(ctx: Context): String = cached ?: ctx.resources
        .openRawResource(R.raw.workflow_minimax_music3)
        .bufferedReader().use { it.readText() }.also { cached = it }

    fun build(ctx: Context, scene: MusicScene, seed: Long): JSONObject =
        build(template(ctx), scene, seed)

    fun build(template: String, scene: MusicScene, seed: Long): JSONObject {
        val wf = JSONObject(template)
        wf.inputs(N_ZADANI).apply {
            put("caption", scene.styl.trim())
            put("lyrics", scene.text.trim())
            put("seed", seed)
            put("max_duration", scene.delka.toDouble())
        }
        wf.inputs(N_SAMPLER).put("seed", seed)
        return wf
    }

    private fun JSONObject.inputs(node: String): JSONObject =
        getJSONObject(node).getJSONObject("inputs")

    /** Je odeslaný graf MiniMax Music 3? */
    fun jeMm3(nodeClasses: Map<String, String>): Boolean = nodeClasses.containsValue(TRIDA_ZADANI)

    fun stageForClass(cls: String?): Stage = when (cls) {
        "UNETLoader", "CLIPLoader", "VAELoader" -> Stage.MODELS
        TRIDA_ZADANI, "EmptyMiniMaxMusic3LatentAudio", "ConditioningZeroOut" -> Stage.ENCODING
        "KSampler" -> Stage.SAMPLING
        "VAEDecodeAudioTiled", "VAEDecodeAudio", "SaveAudioMP3" -> Stage.MUXING
        else -> Stage.SAMPLING
    }

    /**
     * Enkodér tu není jen čtení textu: 8B jazykový model v něm napíše celou
     * skladbu (tokeny), proto má velký díl pásma.
     */
    fun rangeForClass(cls: String?): Pair<Float, Float> = when (cls) {
        "UNETLoader", "CLIPLoader", "VAELoader" -> 0.00f to 0.06f
        TRIDA_ZADANI -> 0.06f to 0.55f
        "EmptyMiniMaxMusic3LatentAudio", "ConditioningZeroOut" -> 0.55f to 0.56f
        "KSampler" -> 0.56f to 0.90f
        "VAEDecodeAudioTiled", "VAEDecodeAudio" -> 0.90f to 0.97f
        "SaveAudioMP3" -> 0.97f to 0.99f
        else -> 0.56f to 0.90f
    }

    fun reportsSteps(cls: String?): Boolean = cls == "KSampler"

    fun nodeClasses(wf: JSONObject): Map<String, String> =
        wf.keys().asSequence().mapNotNull { id ->
            wf.optJSONObject(id)?.optString("class_type")?.takeIf { it.isNotEmpty() }?.let { id to it }
        }.toMap()
}
