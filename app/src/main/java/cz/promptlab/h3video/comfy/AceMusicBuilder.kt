package cz.promptlab.h3video.comfy

import android.content.Context
import cz.promptlab.h3video.R
import cz.promptlab.h3video.data.MusicScene
import org.json.JSONObject

/**
 * Stavitel grafu pro kartu **Hudba** — ACE-Step 1.5 Turbo
 * (`res/raw/workflow_ace_music.json`, převzaté 1:1 z uživatelova exportu
 * `audio_ace_step_1_5__MUSIC_PROMPTLAB.json`). Dosazuje se zadání skladby
 * (styl, text, délka, jazyk, BPM, tónina) a seed; kroky, cfg, sampler
 * i shift zůstávají z předlohy.
 */
object AceMusicBuilder {

    const val N_CKPT = "97"
    const val N_SHIFT = "78"
    const val N_TEXT = "94"
    const val N_LATENT = "98"
    const val N_ZERO = "47"
    const val N_SAMPLER = "3"
    const val N_DECODE = "18"
    const val N_SAVE = "104"

    /** Kroky z předlohy — ukazatel průběhu na ně přepočítává hlášení serveru. */
    const val STEPS = 8

    private var cached: String? = null

    private fun template(ctx: Context): String = cached ?: ctx.resources
        .openRawResource(R.raw.workflow_ace_music)
        .bufferedReader().use { it.readText() }.also { cached = it }

    fun build(ctx: Context, scene: MusicScene, seed: Long): JSONObject =
        build(template(ctx), scene, seed)

    /** Stejné sestavení z textu předlohy, ať jde graf ověřit testem bez Androidu. */
    fun build(template: String, scene: MusicScene, seed: Long): JSONObject {
        val wf = JSONObject(template)
        wf.inputs(N_TEXT).apply {
            put("tags", scene.styl)
            put("lyrics", scene.text)
            put("seed", seed)
            put("bpm", scene.bpm)
            put("duration", scene.seconds)
            put("language", scene.language)
            put("keyscale", scene.keyscale)
        }
        wf.inputs(N_LATENT).put("seconds", scene.seconds)
        wf.inputs(N_SAMPLER).put("seed", seed)
        return wf
    }

    private fun JSONObject.inputs(node: String): JSONObject =
        getJSONObject(node).getJSONObject("inputs")

    fun stageForClass(cls: String?): Stage = when (cls) {
        "CheckpointLoaderSimple", "ModelSamplingAuraFlow" -> Stage.MODELS
        "TextEncodeAceStepAudio1.5", "EmptyAceStep1.5LatentAudio",
        "ConditioningZeroOut" -> Stage.ENCODING
        "KSampler" -> Stage.SAMPLING
        "VAEDecodeAudio", "SaveAudioMP3" -> Stage.MUXING
        else -> Stage.SAMPLING
    }

    fun rangeForClass(cls: String?): Pair<Float, Float> = when (cls) {
        "CheckpointLoaderSimple", "ModelSamplingAuraFlow" -> 0.00f to 0.10f
        "TextEncodeAceStepAudio1.5", "EmptyAceStep1.5LatentAudio",
        "ConditioningZeroOut" -> 0.10f to 0.14f
        "KSampler" -> 0.14f to 0.82f
        "VAEDecodeAudio" -> 0.82f to 0.88f
        "SaveAudioMP3" -> 0.88f to 0.90f
        else -> 0.14f to 0.82f
    }

    fun reportsSteps(cls: String?): Boolean = cls == "KSampler"

    fun nodeClasses(wf: JSONObject): Map<String, String> =
        wf.keys().asSequence().mapNotNull { id ->
            wf.optJSONObject(id)?.optString("class_type")?.takeIf { it.isNotEmpty() }?.let { id to it }
        }.toMap()
}
