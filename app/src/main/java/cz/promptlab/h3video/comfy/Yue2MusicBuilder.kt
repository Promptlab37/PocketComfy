package cz.promptlab.h3video.comfy

import android.content.Context
import cz.promptlab.h3video.R
import cz.promptlab.h3video.data.MusicScene
import org.json.JSONObject

/**
 * Stavitel grafu pro kartu **Hudba**, volba **YuE2 3B**
 * (`res/raw/workflow_yue2_music.json`, převzaté z oficiální předlohy ComfyUI
 * `audio_yue2_text2music.json` — rozbalený subgraph bez náhledů a přepínače).
 *
 * Dosazuje se styl, text písně, strop délky, plán not a seed. Kroky, cfg,
 * sampler i doladění vzorkování (32 kroků, dpm_2 / sgm_uniform) zůstávají
 * z předlohy.
 *
 * Dvě věci, které tenhle model dělá jinak než ACE-Step a je snadné je zlomit:
 *  - **Délku určuje model.** `EmptyYuE2LatentAudio.seconds` se bere z výstupu
 *    `YuE2GenerateMusic` (druhý slot), ne ze zadání. Zadání říká jen strop
 *    (`max_duration`). Kdyby se sem dosadilo číslo, latent by neseděl
 *    na délku, kterou model opravdu nazpíval.
 *  - **Prázdné noty přepínají režim.** Když `abc` dojde prázdné, uzel si sám
 *    přepne `mode` na „off". Appka proto při volbě „bez plánu" uzel s notami
 *    z grafu rovnou VYPOUŠTÍ — jinak by se počítal nadarmo (je to samostatná
 *    generace jazykového modelu, klidně minuty).
 */
object Yue2MusicBuilder {

    const val N_CKPT = "15"
    const val N_ABC = "24"
    const val N_MUSIC = "25"
    const val N_LATENT = "5"
    const val N_ZERO = "18"
    const val N_SAMPLER = "8"
    const val N_DECODE = "9"
    const val N_SAVE = "10"

    /** Kroky z předlohy — ukazatel průběhu na ně přepočítává hlášení serveru. */
    const val STEPS = 32

    private var cached: String? = null

    private fun template(ctx: Context): String = cached ?: ctx.resources
        .openRawResource(R.raw.workflow_yue2_music)
        .bufferedReader().use { it.readText() }.also { cached = it }

    fun build(ctx: Context, scene: MusicScene, seed: Long): JSONObject =
        build(template(ctx), scene, seed)

    /** Stejné sestavení z textu předlohy, ať jde graf ověřit testem bez Androidu. */
    fun build(template: String, scene: MusicScene, seed: Long): JSONObject {
        val wf = JSONObject(template)

        wf.inputs(N_MUSIC).apply {
            put("style", scene.styl)
            put("lyrics", scene.text)
            put("seed", seed)
            put("mode", scene.plan.mode)
            // FLOAT vstup — číslo musí odejít jako desetinné, ať sedí se schématem.
            put("max_duration", scene.maxSeconds.toDouble())
        }

        if (scene.plan.piseNoty) {
            wf.inputs(N_ABC).apply {
                put("style", scene.styl)
                put("lyrics", scene.text)
                put("seed", seed)
                put("mode", scene.plan.mode)
            }
        } else {
            // Bez plánu: `abc` odchází jako prázdný text a uzel s notami se
            // z grafu vypouští. Samotné odpojení by stačilo (ComfyUI počítá
            // jen to, na čem visí výstup), ale osiřelý uzel by pak zbytečně
            // procházel kontrolou serveru i hlášením průběhu.
            wf.inputs(N_MUSIC).put("abc", "")
            wf.remove(N_ABC)
        }

        wf.inputs(N_SAMPLER).put("seed", seed)
        return wf
    }

    private fun JSONObject.inputs(node: String): JSONObject =
        getJSONObject(node).getJSONObject("inputs")

    fun stageForClass(cls: String?): Stage = when (cls) {
        "CheckpointLoaderSimple" -> Stage.MODELS
        // Psaní not je samostatný běh jazykového modelu — patří do přípravy,
        // ne do vzorkování, aby ukazatel průběhu neskákal.
        "YuE2GenerateABC" -> Stage.REFERENCES
        "YuE2GenerateMusic", "EmptyYuE2LatentAudio",
        "ConditioningZeroOut" -> Stage.ENCODING
        "KSampler" -> Stage.SAMPLING
        "VAEDecodeAudio", "SaveAudioMP3" -> Stage.MUXING
        else -> Stage.SAMPLING
    }

    fun rangeForClass(cls: String?): Pair<Float, Float> = when (cls) {
        "CheckpointLoaderSimple" -> 0.00f to 0.08f
        "YuE2GenerateABC" -> 0.08f to 0.28f
        "YuE2GenerateMusic" -> 0.28f to 0.58f
        "EmptyYuE2LatentAudio", "ConditioningZeroOut" -> 0.58f to 0.60f
        "KSampler" -> 0.60f to 0.86f
        "VAEDecodeAudio" -> 0.86f to 0.90f
        "SaveAudioMP3" -> 0.90f to 0.92f
        else -> 0.60f to 0.86f
    }

    fun reportsSteps(cls: String?): Boolean = cls == "KSampler"

    fun nodeClasses(wf: JSONObject): Map<String, String> = AceMusicBuilder.nodeClasses(wf)
}
