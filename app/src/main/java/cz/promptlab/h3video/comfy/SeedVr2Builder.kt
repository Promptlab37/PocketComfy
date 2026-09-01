package cz.promptlab.h3video.comfy

import android.content.Context
import cz.promptlab.h3video.R
import cz.promptlab.h3video.data.UpscaleScene
import org.json.JSONObject

/**
 * Stavitel grafu pro kartu **Zvětšit** — uživatelovo SeedVR2 gigapixel
 * workflow (`res/raw/workflow_seedvr2_upscale.json`, převzaté 1:1 z jeho
 * exportu). Dosazují se přesně tři hodnoty: obrázek, mřížka dlaždic a seed.
 * Nic jiného se v grafu nemění.
 */
object SeedVr2Builder {

    const val N_DIT = "14"
    const val N_VAE = "13"
    const val N_IMAGE = "43"
    const val N_SPLIT = "47"
    const val N_UPSCALER = "10"
    const val N_MERGE = "48"
    const val N_SAVE = "15"

    private var cached: String? = null

    private fun template(ctx: Context): String = cached ?: ctx.resources
        .openRawResource(R.raw.workflow_seedvr2_upscale)
        .bufferedReader().use { it.readText() }.also { cached = it }

    fun build(ctx: Context, scene: UpscaleScene, seed: Long, images: List<String>): JSONObject =
        build(template(ctx), scene, seed, images)

    /** Stejné sestavení z textu předlohy, ať jde graf ověřit testem bez Androidu. */
    fun build(template: String, scene: UpscaleScene, seed: Long, images: List<String>): JSONObject {
        val wf = JSONObject(template)
        wf.inputs(N_IMAGE).put("image", images.getOrElse(0) { "" })
        wf.inputs(N_SPLIT).put("grid_size", scene.grid)
        // SeedVR2VideoUpscaler bere seed jen do 2³²−1 (MiniMax snese bilionové),
        // větší hodnotu server odmítne při validaci celého grafu.
        wf.inputs(N_UPSCALER).put("seed", seed and 0xFFFF_FFFFL)
        return wf
    }

    private fun JSONObject.inputs(node: String): JSONObject =
        getJSONObject(node).getJSONObject("inputs")

    /** Fáze podle třídy uzlu; dlouhá část je samotný upscaler. */
    fun stageForClass(cls: String?): Stage = when (cls) {
        "SeedVR2LoadDiTModel", "SeedVR2LoadVAEModel" -> Stage.MODELS
        "LoadImageWithFilename", "ImageTileSplit" -> Stage.REFERENCES
        "SeedVR2VideoUpscaler" -> Stage.SAMPLING
        "ImageTileMerge", "SaveImage" -> Stage.MUXING
        else -> Stage.SAMPLING
    }

    fun rangeForClass(cls: String?): Pair<Float, Float> = when (cls) {
        "SeedVR2LoadDiTModel", "SeedVR2LoadVAEModel" -> 0.00f to 0.05f
        "LoadImageWithFilename", "ImageTileSplit" -> 0.05f to 0.08f
        "SeedVR2VideoUpscaler" -> 0.08f to 0.86f
        "ImageTileMerge" -> 0.86f to 0.89f
        "SaveImage" -> 0.89f to 0.90f
        else -> 0.08f to 0.86f
    }

    fun reportsSteps(cls: String?): Boolean = cls == "SeedVR2VideoUpscaler"

    fun nodeClasses(wf: JSONObject): Map<String, String> =
        wf.keys().asSequence().mapNotNull { id ->
            wf.optJSONObject(id)?.optString("class_type")?.takeIf { it.isNotEmpty() }?.let { id to it }
        }.toMap()
}
