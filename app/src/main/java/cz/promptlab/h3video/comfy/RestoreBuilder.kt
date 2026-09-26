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

    /**
     * Uživatelův univerzální prompt na opravu poškozených fotek — doslova
     * z jeho předlohy `workflow_qwen_restore.json` (uzel 223), kterou appka
     * používala do 3.63. Při přechodu na Qwen 2.1 (3.64) ho nahradil vlastní
     * text; 25. 9. 2026 si ho uživatel vyžádal zpátky.
     *
     * Jediná změna proti originálu (26. 9. 2026, na přání uživatele — výsledky
     * byly „málo barevné"): „color grading with restrained saturation"
     * (střídmá sytost) je nahrazené sytými, věrnými barvami.
     */
    const val DEFAULT_PROMPT =
        "Ultra high-resolution photo reconstruction with strict sharpness control, " +
            "preserve original facial identity with zero identity drift and no shape " +
            "deformation, remove dust scratches stains grain and paper artifacts while " +
            "maintaining natural micro-texture, full realistic colorization with physically " +
            "accurate subsurface scattering on skin, crisp edges high local contrast fine " +
            "detail preservation no smoothing no blur no softness, naturalistic lighting " +
            "neutral white balance professional color grading with rich vivid true-to-life colors, " +
            "repair torn edges and missing areas with realistic texture continuity, true 4K " +
            "detail razor-sharp focus high-frequency detail retention professional modern " +
            "photography look, detailed sharp hair, focus on eyes."

    /**
     * Úvod před uživatelovým promptem: obarvení jako výslovný úkol a identita
     * odkazem na fotku. Qwen ve svých pravidlech přepisu (qwen21_pe_system_i2i)
     * radí identitu držet odkazem na obrázek, ne popisem tváře slovy.
     */
    const val UVOD =
        "Restore <image1> into a full-color professional photograph. Turn black-and-white, " +
            "sepia or faded tones into rich, vivid, natural colors: lifelike skin tones, " +
            "colorful clothing, saturated sky and greenery. Every person stays exactly the " +
            "same person as in <image1> — same face, age, expression and features."

    /**
     * Věta k LoRA Detailer pro Opravu. Obecná věta Detaileru končí „…preserving
     * the original composition, lighting and style" — u černobílé fotky je
     * „původní styl" černobílý a model barvy držel zpátky.
     */
    const val DETAILER_VETA =
        "Enhance this image with rich fine details, natural microdetails and improved " +
            "clarity while preserving the original composition and every person's identity."

    /** Při vlastním zadání se za ně připojí tentýž uživatelův prompt. */
    const val KVALITA = DEFAULT_PROMPT

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
        // <image1> říká Qwenu 2.1, kterou fotku upravuje; text za ním je doslova uživatelův.
        val prompt = if (pokyn.isBlank()) "$UVOD $DEFAULT_PROMPT" else
            "Edit <image1>: ${pokyn.trim()}. $KVALITA"
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
            // Věta Detaileru pro Opravu místo obecné (ta drží „původní styl").
            val text = wf.getJSONObject(N_PROMPT).getJSONObject("inputs")
            text.put(
                "prompt",
                text.getString("prompt").replace(Qwen21EditBuilder.DETAILER_PROMPT, DETAILER_VETA),
            )
        }
    }

    fun stageForClass(cls: String?): Stage = Qwen21EditBuilder.stageForClass(cls)
    fun rangeForClass(cls: String?): Pair<Float, Float> = Qwen21EditBuilder.rangeForClass(cls)
    fun reportsSteps(cls: String?): Boolean = Qwen21EditBuilder.reportsSteps(cls)
    fun nodeClasses(wf: JSONObject): Map<String, String> = Qwen21EditBuilder.nodeClasses(wf)
}
