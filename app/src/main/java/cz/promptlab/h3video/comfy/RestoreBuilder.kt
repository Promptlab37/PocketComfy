package cz.promptlab.h3video.comfy

import android.content.Context
import cz.promptlab.h3video.R
import cz.promptlab.h3video.data.EditMotor
import cz.promptlab.h3video.data.ImageEditScene
import cz.promptlab.h3video.data.Qwen21Resolution
import org.json.JSONObject

/** Stavitel karty **Oprava fotky** nad jediným podporovaným Qwen Image 2.1. */
object RestoreBuilder {

    const val N_IMAGE = Qwen21EditBuilder.N_FIRST_IMAGE
    const val N_PROMPT = Qwen21EditBuilder.N_TEXT
    const val N_SAMPLER = Qwen21EditBuilder.N_SAMPLER
    const val N_SAVE = Qwen21EditBuilder.N_SAVE
    const val STEPS = Qwen21EditBuilder.DEFAULT_STEPS

    /** Výchozí obecná záchrana staré nebo poškozené fotografie. */
    const val DEFAULT_PROMPT =
        "Restore <image1> as a high-resolution photograph while strictly preserving " +
            "the original people, facial identity, composition, pose and geometry. " +
            "Remove dust, scratches, stains, grain and paper artifacts; repair torn " +
            "edges and missing areas with realistic texture continuity; restore natural " +
            "colors, neutral white balance, sharp hair and eyes, fine skin micro-texture " +
            "and physically accurate lighting. No smoothing, blur or identity drift."

    /** Kvalitativní doplněk pro uživatelovo cílené zadání. */
    const val KVALITA =
        "ultra sharp, fine detail, natural skin micro-texture, " +
            "physically accurate lighting, photorealistic, no blur, no smoothing"

    private var cached: String? = null

    private fun template(ctx: Context): String = cached ?: ctx.resources
        .openRawResource(R.raw.workflow_qwen21_edit)
        .bufferedReader().use { it.readText() }.also { cached = it }

    fun build(
        ctx: Context, seed: Long, images: List<String>, pokyn: String = "",
    ): JSONObject = build(template(ctx), seed, images, pokyn)

    fun build(
        template: String, seed: Long, images: List<String>, pokyn: String = "",
    ): JSONObject {
        val prompt = if (pokyn.isBlank()) DEFAULT_PROMPT else
            "Edit <image1>: ${pokyn.trim()}, $KVALITA"
        val scene = ImageEditScene(
            motor = EditMotor.QWEN21,
            prompt = prompt,
            qwen21Steps = STEPS,
            qwen21Resolution = Qwen21Resolution.STANDARD,
            // Detailer je na tohle přímo stavěný („photo restoration and
            // quality improvements") — v Opravě jede vždy.
            qwen21Detailer = true,
        )
        return Qwen21EditBuilder.build(template, scene, seed, images).also { wf ->
            wf.getJSONObject(N_SAVE).getJSONObject("inputs")
                .put("filename_prefix", "H3RestoreQwen21")
        }
    }

    fun stageForClass(cls: String?): Stage = Qwen21EditBuilder.stageForClass(cls)
    fun rangeForClass(cls: String?): Pair<Float, Float> = Qwen21EditBuilder.rangeForClass(cls)
    fun reportsSteps(cls: String?): Boolean = Qwen21EditBuilder.reportsSteps(cls)
    fun nodeClasses(wf: JSONObject): Map<String, String> = Qwen21EditBuilder.nodeClasses(wf)
}
