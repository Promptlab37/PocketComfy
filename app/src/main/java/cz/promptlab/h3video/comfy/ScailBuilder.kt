package cz.promptlab.h3video.comfy

import android.content.Context
import cz.promptlab.h3video.R
import cz.promptlab.h3video.data.UpravaScene
import org.json.JSONObject

/**
 * Stavitel grafu pro **Upravit video → Vyměnit postavu** — SCAIL-2
 * (`res/raw/workflow_scail_postava.json`, z oficiálních předloh
 * „Character Replacement (SCAIL-2 Int8 Base)" a „(SCAIL-2 Extend)").
 *
 * Postava z fotky nahradí člověka ve videu, pozadí zůstane. Masku najde
 * SAM 3.1 podle textu. Video se projde smyčkou po úsecích 81 snímků:
 * každý další začíná o 76 dál a pět snímků předchozího mu slouží jako
 * navázání. Turbo cesta z předlohy: distill LoRA 0,8 + DPO LoRA 1,0,
 * 6 kroků, euler, cfg 1, shift 5.
 *
 * ### Odchylky od předloh
 *
 * 1. Base a Extend jsou dvě předlohy na ruční řetězení — tady je to jedna
 *    smyčka `StartLoop`/`EndLoop` přes celé video. Navazující úsek zahodí
 *    prvních 5 snímků a srovná barvy s koncem předchozího (`ColorTransfer`),
 *    přesně jako Extend.
 * 2. Poslední úsek je kratší (délka 4n+1 podle zbytku videa); zbytek pod
 *    9 snímků se vynechá. Zvuk zdroje se zkrátí na délku výsledku.
 * 3. Model int8 i pro navazující úseky (Extend v předloze bere fp16 33 GB,
 *    na 16 GB kartu se nevejde).
 * 4. Plátno 512 × 896 podle orientace videa (strany jsou násobky 32).
 */
object ScailBuilder {

    const val N_FOTKA = "900"
    const val N_VIDEO = "901"
    const val N_POPIS = "363"
    const val N_KOHO_VIDEO = "348"
    const val N_KOHO_FOTKA = "365"
    const val N_SEED = "333"
    const val N_PLATNO = "324"
    const val N_ULOZ = "911"

    /** Kroky jednoho úseku. */
    const val STEPS = 6

    /** Plátno: kratší a delší strana (násobky 32). */
    const val KRATSI = 512
    const val DELSI = 896

    private var cached: String? = null

    private fun template(ctx: Context): String = cached ?: ctx.resources
        .openRawResource(R.raw.workflow_scail_postava)
        .bufferedReader().use { it.readText() }.also { cached = it }

    fun build(ctx: Context, scene: UpravaScene, seed: Long, fotka: String, video: String): JSONObject =
        build(template(ctx), scene, seed, fotka, video)

    fun build(template: String, scene: UpravaScene, seed: Long, fotka: String, video: String): JSONObject {
        val wf = JSONObject(template)
        wf.inputs(N_FOTKA).put("image", fotka)
        wf.inputs(N_VIDEO).put("file", video)
        wf.inputs(N_POPIS).put("text", scene.popis.trim())
        val koho = scene.kohoVymenit.trim().ifEmpty { UpravaScene.KOHO_VYCHOZI }
        wf.inputs(N_KOHO_VIDEO).put("text", koho)
        wf.inputs(N_KOHO_FOTKA).put("text", koho)
        wf.inputs(N_SEED).put("noise_seed", seed)
        wf.inputs(N_PLATNO).put("resize_type.width", if (scene.naVysku) KRATSI else DELSI)
        wf.inputs(N_PLATNO).put("resize_type.height", if (scene.naVysku) DELSI else KRATSI)
        return wf
    }

    private fun JSONObject.inputs(node: String): JSONObject =
        getJSONObject(node).getJSONObject("inputs")

    fun stageForClass(cls: String?): Stage = when (cls) {
        "UNETLoader", "LoraLoaderModelOnly", "CLIPLoader", "VAELoader", "CLIPVisionLoader",
        "CheckpointLoaderSimple", "ModelSamplingSD3" -> Stage.MODELS
        "LoadImage", "LoadVideo", "GetVideoComponents", "ResizeImageMaskNode", "GetImageSize",
        "ImageFromBatch", "CLIPVisionEncode", "SAM3_VideoTrack", "SCAIL2ColoredMask" -> Stage.REFERENCES
        "CLIPTextEncode", "WanSCAILToVideo", "BasicScheduler", "KSamplerSelect",
        "ComfyMathExpression" -> Stage.ENCODING
        "SamplerCustom" -> Stage.SAMPLING
        "VAEDecode", "ColorTransfer", "RebatchImages", "TrimAudioDuration", "CreateVideo", "SaveVideo" -> Stage.MUXING
        else -> Stage.SAMPLING
    }

    fun rangeForClass(cls: String?): Pair<Float, Float> = when (stageForClass(cls)) {
        Stage.MODELS -> 0.00f to 0.08f
        Stage.REFERENCES -> 0.08f to 0.16f
        Stage.ENCODING -> 0.16f to 0.20f
        Stage.SAMPLING -> 0.20f to 0.90f
        else -> 0.90f to 1.00f
    }

    fun reportsSteps(cls: String?): Boolean = cls == "SamplerCustom"

    fun nodeClasses(wf: JSONObject): Map<String, String> =
        wf.keys().asSequence().mapNotNull { id ->
            wf.optJSONObject(id)?.optString("class_type")?.takeIf { it.isNotEmpty() }?.let { id to it }
        }.toMap()
}
