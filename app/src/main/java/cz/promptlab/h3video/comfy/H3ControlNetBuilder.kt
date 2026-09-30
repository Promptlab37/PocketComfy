package cz.promptlab.h3video.comfy

import android.content.Context
import cz.promptlab.h3video.R
import cz.promptlab.h3video.data.PredlohaDruh
import cz.promptlab.h3video.data.UpravaScene
import org.json.JSONArray
import org.json.JSONObject

/**
 * Stavitel grafu pro **Upravit video → Podle předlohy** — MiniMax H3
 * s Fun ControlNet-Union (`res/raw/workflow_h3_controlnet.json`, z oficiální
 * šablony `video_minimax_h3_fun_controlnet_union`).
 *
 * Z hotového videa se vezme obrys (Canny) nebo póza (SDPose) a nové video
 * se podle něj vygeneruje znovu podle popisu. Délka 5–15 s na mřížce 17n+5
 * při 24 fps, jak ji chce MiniMax H3.
 *
 * ### Odchylky od šablony
 *
 * 1. Enkodér `qwen3vl_32b_minimax_h3_int8_convrot` místo `nvfp4_awq` —
 *    RTX 4060 Ti nvfp4 neumí.
 * 2. Předzpracování přímo v grafu, jedna větev podle volby (šablona má
 *    SDPose v podgrafu a obrysy jen v odkazu na návod).
 * 3. Plátno 0,4 MP (864 × 480) podle orientace videa, jako výchozí
 *    ResolutionSelector šablony.
 * 4. Video se načítá přes `VHS_LoadVideo` přepočtené na 24 fps a zmenšené na
 *    delší stranu plátna. ControlNet bere snímky předlohy podle pořadí
 *    (`_fit_frames` v nodes_minimax_h3.py) a výstup má 24 fps — video z telefonu
 *    (30/60 fps) by jinak vyšlo zpomalené, a celé by se načetlo do RAM
 *    v plném rozlišení.
 */
object H3ControlNetBuilder {

    const val N_VIDEO = "901"
    const val N_OBRYSY = "800"
    val N_POZA = listOf("810", "811", "812", "813", "814", "815")
    const val N_POZA_VYSTUP = "815"
    const val N_RYCHLE = "145"
    const val N_MODEL = "127"
    const val N_CONTROLNET = "172"
    const val N_ZADANI = "136"
    const val N_SEED = "129"
    const val N_KROKY = "124"
    const val N_ULOZ = "911"
    /** Plná přesnost pozornosti, když je Sage vypnutá (přebíjí --use-sage-attention serveru). */
    const val N_POZORNOST = "174"

    const val KROKY_RYCHLE = 4
    const val KROKY_KVALITA = 20
    const val KRATSI = 480
    const val DELSI = 864
    const val FPS = 24

    private var cached: String? = null

    private fun template(ctx: Context): String = cached ?: ctx.resources
        .openRawResource(R.raw.workflow_h3_controlnet)
        .bufferedReader().use { it.readText() }.also { cached = it }

    fun build(ctx: Context, scene: UpravaScene, seed: Long, video: String, pozornost: cz.promptlab.h3video.data.Pozornost = cz.promptlab.h3video.data.Pozornost.SAGE): JSONObject =
        build(template(ctx), scene, seed, video, pozornost)

    /** Snímky na mřížce 17n+5 při 24 fps (vzorec ze šablony). */
    fun snimku(sekundy: Float): Int {
        val z = maxOf(5, Math.round(sekundy * FPS))
        return z + (5 - z % 17 + 17) % 17
    }

    fun build(template: String, scene: UpravaScene, seed: Long, video: String, pozornost: cz.promptlab.h3video.data.Pozornost = cz.promptlab.h3video.data.Pozornost.SAGE): JSONObject {
        val wf = JSONObject(template)
        val sekundy = scene.predlohaDelka
        wf.inputs(N_VIDEO).apply {
            put("video", video)
            put("force_rate", FPS.toDouble())
            put("frame_load_cap", snimku(sekundy))
            // Jen delší strana, poměr zůstává; na plátno ořízne až ControlNet.
            put("custom_width", if (scene.naVysku) 0 else DELSI)
            put("custom_height", if (scene.naVysku) DELSI else 0)
        }

        // Jedna větev řídicího videa, druhá z grafu pryč.
        val rizeni = when (scene.predlohaDruh) {
            PredlohaDruh.OBRYSY -> { N_POZA.forEach { wf.remove(it) }; N_OBRYSY }
            PredlohaDruh.POZA -> { wf.remove(N_OBRYSY); N_POZA_VYSTUP }
        }
        wf.inputs(N_CONTROLNET).put("control_video", JSONArray().put(rizeni).put(0))

        // Rychle = LoRA Lightning a 4 kroky; kvalitně bez ní a 20 kroků (šablona).
        if (scene.predlohaRychle) {
            wf.inputs(N_KROKY).put("steps", KROKY_RYCHLE)
        } else {
            wf.remove(N_RYCHLE)
            wf.inputs(N_CONTROLNET).put("model", JSONArray().put(N_MODEL).put(0))
            wf.inputs(N_KROKY).put("steps", KROKY_KVALITA)
        }
        // Společné nastavení „Zrychlení a vzorkování“ (audit 30. 9. 2026): Sage vypnutá =
        // pytorch attention hned za modelem, stejně jako v ostatních kartách H3.
        if (pozornost != cz.promptlab.h3video.data.Pozornost.SAGE) {
            wf.put(N_POZORNOST, JSONObject()
                .put("class_type", "ModelAttentionBackend")
                .put("_meta", JSONObject().put("title", "Pozornost"))
                .put("inputs", JSONObject().put("model", JSONArray().put(N_MODEL).put(0)).put("attention", pozornost.backend)))
            val dalsi = if (scene.predlohaRychle) N_RYCHLE else N_CONTROLNET
            wf.inputs(dalsi).put("model", JSONArray().put(N_POZORNOST).put(0))
        }

        val zadani = wf.inputs(N_ZADANI)
        zadani.put("prompt", scene.popis.trim())
        zadani.put("width", if (scene.naVysku) KRATSI else DELSI)
        zadani.put("height", if (scene.naVysku) DELSI else KRATSI)
        zadani.put("length", snimku(sekundy))
        wf.inputs(N_SEED).put("noise_seed", seed)
        return wf
    }

    private fun JSONObject.inputs(node: String): JSONObject =
        getJSONObject(node).getJSONObject("inputs")

    fun kroky(scene: UpravaScene): Int = if (scene.predlohaRychle) KROKY_RYCHLE else KROKY_KVALITA

    fun stageForClass(cls: String?): Stage = when (cls) {
        "UNETLoader", "LoraLoaderModelOnly", "ModelPatchLoader", "VAELoader", "CLIPLoader",
        "CheckpointLoaderSimple", "MiniMaxH3FunControlNetApply" -> Stage.MODELS
        "VHS_LoadVideo", "Canny", "ResizeImageMaskNode",
        "RTDETR_detect", "SDPoseKeypointExtractor", "SDPoseDrawKeypoints" -> Stage.REFERENCES
        "MiniMaxH3ReferenceToVideo", "RandomNoise", "KSamplerSelect", "BasicScheduler",
        "BasicGuider" -> Stage.ENCODING
        "SamplerCustomAdvanced" -> Stage.SAMPLING
        else -> Stage.MUXING
    }

    fun rangeForClass(cls: String?): Pair<Float, Float> = when (stageForClass(cls)) {
        Stage.MODELS -> 0.00f to 0.10f
        Stage.REFERENCES -> 0.10f to 0.20f
        Stage.ENCODING -> 0.20f to 0.26f
        Stage.SAMPLING -> 0.26f to 0.90f
        else -> 0.90f to 1.00f
    }

    fun reportsSteps(cls: String?): Boolean = cls == "SamplerCustomAdvanced"

    fun nodeClasses(wf: JSONObject): Map<String, String> =
        wf.keys().asSequence().mapNotNull { id ->
            wf.optJSONObject(id)?.optString("class_type")?.takeIf { it.isNotEmpty() }?.let { id to it }
        }.toMap()
}
