package cz.promptlab.h3video.comfy

import android.content.Context
import cz.promptlab.h3video.R
import cz.promptlab.h3video.data.VylepseniScene
import org.json.JSONArray
import org.json.JSONObject

/**
 * Stavitel grafu pro **Vylepšit video → Zplynulit** — interpolace snímků FILM
 * z jádra ComfyUI (`res/raw/workflow_interpolace.json`, podle oficiální
 * předlohy „Frame Interpolation").
 *
 * ### Odchylky od předlohy
 *
 * 1. Subgraf narovnaný, přepínač fps je rozhodnutý v appce: buď se fps násobí
 *    (plynulejší video stejné délky), nebo zůstane (zpomalený pohyb).
 * 2. **Při zpomalení se zvuk nepřikládá** — předloha ho nechává, jenže obraz
 *    je pak N× delší než zvuk a rozejdou se.
 */
object InterpolaceBuilder {

    const val N_VIDEO = "901"
    const val N_INTERPOLACE = "2"
    const val N_FPS = "11"
    const val N_VIDEO_VYSTUP = "5"
    const val N_ULOZ = "911"

    private var cached: String? = null

    private fun template(ctx: Context): String = cached ?: ctx.resources
        .openRawResource(R.raw.workflow_interpolace)
        .bufferedReader().use { it.readText() }.also { cached = it }

    fun build(ctx: Context, scene: VylepseniScene, video: String): JSONObject =
        build(template(ctx), scene, video)

    fun build(template: String, scene: VylepseniScene, video: String): JSONObject {
        val wf = JSONObject(template)
        wf.inputs(N_VIDEO).put("file", video)
        wf.inputs(N_INTERPOLACE).put("multiplier", scene.nasobek)
        wf.inputs(N_FPS).put("values.b", scene.nasobek)
        val vystup = wf.inputs(N_VIDEO_VYSTUP)
        if (scene.zpomalit) {
            vystup.put("fps", JSONArray().put("3").put(2))
            vystup.remove("audio")
            wf.remove(N_FPS)
        }
        return wf
    }

    private fun JSONObject.inputs(node: String): JSONObject =
        getJSONObject(node).getJSONObject("inputs")

    fun stageForClass(cls: String?): Stage = when (cls) {
        "FrameInterpolationModelLoader" -> Stage.MODELS
        "LoadVideo", "GetVideoComponents" -> Stage.REFERENCES
        "FrameInterpolate" -> Stage.SAMPLING
        else -> Stage.MUXING
    }

    fun rangeForClass(cls: String?): Pair<Float, Float> = when (stageForClass(cls)) {
        Stage.MODELS -> 0.00f to 0.05f
        Stage.REFERENCES -> 0.05f to 0.15f
        Stage.SAMPLING -> 0.15f to 0.90f
        else -> 0.90f to 1.00f
    }

    /** FrameInterpolate hlásí průběh po snímcích. */
    fun reportsSteps(cls: String?): Boolean = cls == "FrameInterpolate"

    fun nodeClasses(wf: JSONObject): Map<String, String> =
        wf.keys().asSequence().mapNotNull { id ->
            wf.optJSONObject(id)?.optString("class_type")?.takeIf { it.isNotEmpty() }?.let { id to it }
        }.toMap()
}
