package cz.promptlab.h3video.comfy

import android.content.Context
import cz.promptlab.h3video.R
import cz.promptlab.h3video.data.AnimateScene
import org.json.JSONObject

/**
 * Stavitel grafu pro kartu **Wan Animate** — Wan-Animate 2
 * (`res/raw/workflow_wan_animate2.json`, narovnané z oficiální předlohy
 * `video_wan_animate2.json` od Comfy-Org).
 *
 * Fotka postavy + řídicí video → postava zopakuje pohyb z videa. Graf projde
 * celé video smyčkou `StartLoop`/`EndLoop` po úsecích 81 snímků, výsledek má
 * snímkovou frekvenci i zvuk zdrojového videa. Base model s LoRA `lightx2v`,
 * 6 kroků, `lcm`, cfg 1, shift 5 — vše jako v předloze.
 *
 * ### Odchylky od předlohy a proč
 *
 * 1. **Pipeline byla v subgrafu** — narovnaná ven. Druhý `GetVideoComponents`
 *    na totéž video je sloučený s prvním.
 * 2. **`pose_end_percent` = 1.0.** Předloha ho má spojený se stejným vstupem
 *    jako `pose_start_percent` (0), čímž okno vlivu pohybu splaskne na nulu
 *    a větev s pohybem se neuplatní. 1.0 je výchozí hodnota uzlu.
 * 3. **Cache pózy na `cpu`.** Předloha má `gpu`, ale její vlastní poznámka
 *    radí `cpu` kvůli špičkám VRAM; 16 GB karta se s modelem 16,7 GB nevejde.
 * 4. **Plátno 480 × 832 podle orientace videa** místo pevných 482 × 854 —
 *    strany jsou násobky 16, jak chce `WanAnimate2ToVideo`.
 * 5. **Kontextová okna jsou pryč** — v předloze vypnutá přepínačem,
 *    úseky řeší smyčka.
 * 6. **Bez srovnání vedle zdrojového videa** (`Video Stitch`) — ukládá se
 *    jen výsledek.
 */
object AnimateBuilder {

    const val N_FOTKA = "900"
    const val N_VIDEO = "901"
    /** Postava a prostředí. */
    const val N_ZADANI = "582"
    /** Popis pohybu pro větev řídicího videa. */
    const val N_POHYB = "585"
    /** Zmenšení řídicího videa — odsud bere plátno celý graf. */
    const val N_PLATNO = "600"
    const val N_SEED = "597"
    const val N_ULOZ = "911"

    /** Kroky jednoho úseku. */
    const val STEPS = 6

    private var cached: String? = null

    private fun template(ctx: Context): String = cached ?: ctx.resources
        .openRawResource(R.raw.workflow_wan_animate2)
        .bufferedReader().use { it.readText() }.also { cached = it }

    fun build(ctx: Context, scene: AnimateScene, seed: Long, fotka: String, video: String): JSONObject =
        build(template(ctx), scene, seed, fotka, video)

    /** Stejné sestavení z textu předlohy, ať jde graf ověřit testem bez Androidu. */
    fun build(
        template: String, scene: AnimateScene, seed: Long, fotka: String, video: String,
    ): JSONObject {
        val wf = JSONObject(template)
        wf.inputs(N_FOTKA).put("image", fotka)
        wf.inputs(N_VIDEO).put("file", video)
        wf.inputs(N_ZADANI).put("text", scene.zadani)
        wf.inputs(N_POHYB).put("text", scene.pohyb.trim())
        wf.inputs(N_SEED).put("noise_seed", seed)
        wf.inputs(N_PLATNO).put("resize_type.width", scene.sirka)
        wf.inputs(N_PLATNO).put("resize_type.height", scene.vyska)
        return wf
    }

    private fun JSONObject.inputs(node: String): JSONObject =
        getJSONObject(node).getJSONObject("inputs")

    fun stageForClass(cls: String?): Stage = when (cls) {
        "UNETLoader", "LoraLoaderModelOnly", "CLIPLoader", "VAELoader",
        "CLIPVisionLoader", "ModelSamplingSD3", "WanAnimate2Cache" -> Stage.MODELS
        "LoadImage", "LoadVideo", "GetVideoComponents", "ResizeImageMaskNode",
        "GetImageSize", "ImageFromBatch", "CLIPVisionEncode" -> Stage.REFERENCES
        "CLIPTextEncode", "WanAnimate2ToVideo", "BasicScheduler", "KSamplerSelect",
        "PrimitiveInt", "ComfyMathExpression" -> Stage.ENCODING
        "SamplerCustom" -> Stage.SAMPLING
        "VAEDecode", "RebatchImages", "CreateVideo", "SaveVideo" -> Stage.MUXING
        else -> Stage.SAMPLING
    }

    fun rangeForClass(cls: String?): Pair<Float, Float> = when (stageForClass(cls)) {
        Stage.MODELS -> 0.00f to 0.10f
        Stage.REFERENCES -> 0.10f to 0.14f
        Stage.ENCODING -> 0.14f to 0.20f
        Stage.SAMPLING -> 0.20f to 0.90f
        else -> 0.90f to 1.00f
    }

    fun reportsSteps(cls: String?): Boolean = cls == "SamplerCustom"

    fun nodeClasses(wf: JSONObject): Map<String, String> =
        wf.keys().asSequence().mapNotNull { id ->
            wf.optJSONObject(id)?.optString("class_type")?.takeIf { it.isNotEmpty() }?.let { id to it }
        }.toMap()
}
