package cz.promptlab.h3video.comfy

import android.content.Context
import cz.promptlab.h3video.R
import cz.promptlab.h3video.data.EditMotor
import cz.promptlab.h3video.data.ImageEditScene
import cz.promptlab.h3video.data.Qwen21Resolution
import cz.promptlab.h3video.data.t
import org.json.JSONObject

/** Karta **Úhel kamery** převedená na nativní editaci Qwen Image 2.1 bez LoRA. */
object AngleBuilder {

    const val N_IMAGE = Qwen21EditBuilder.N_FIRST_IMAGE
    const val N_PROMPT = Qwen21EditBuilder.N_TEXT
    const val N_SAMPLER = Qwen21EditBuilder.N_SAMPLER
    const val N_SAVE = Qwen21EditBuilder.N_SAVE
    const val STEPS = Qwen21EditBuilder.DEFAULT_STEPS

    val AZIMUTY: List<Pair<String, String>> = listOf(
        "front view" to "zepředu",
        "front-right quarter view" to "zprava zepředu",
        "right side view" to "zprava",
        "back-right quarter view" to "zprava zezadu",
        "back view" to "zezadu",
        "back-left quarter view" to "zleva zezadu",
        "left side view" to "zleva",
        "front-left quarter view" to "zleva zepředu",
    )

    val VYSKY: List<Pair<String, String>> = listOf(
        "low-angle shot" to "podhled",
        "eye-level shot" to "v úrovni očí",
        "elevated shot" to "mírný nadhled",
        "high-angle shot" to "nadhled",
    )

    val ODSTUPY: List<Pair<String, String>> = listOf(
        "close-up" to "detail",
        "medium shot" to "polocelek",
        "wide shot" to "celek",
    )

    const val POZ = 96

    fun uhelProSmer(smer: Int): Float = (smer.coerceIn(AZIMUTY.indices) * 45).toFloat()

    fun smerZUhlu(stupne: Float): Int {
        val norm = ((stupne % 360f) + 360f) % 360f
        return (Math.round(norm / 45f) % AZIMUTY.size)
    }

    val VYSKA_STUPNE = listOf(-30f, 0f, 30f, 60f)

    fun uhelProVysku(vyska: Int): Float = VYSKA_STUPNE[vyska.coerceIn(VYSKY.indices)]

    fun vyskaZUhlu(stupne: Float): Int {
        val omezene = stupne.coerceIn(VYSKA_STUPNE.first(), VYSKA_STUPNE.last())
        return VYSKA_STUPNE.indices.minByOrNull {
            kotlin.math.abs(VYSKA_STUPNE[it] - omezene)
        } ?: 1
    }

    val ODSTUP_POMER = listOf(0.42f, 0.66f, 0.92f)

    fun pomerProOdstup(odstup: Int): Float = ODSTUP_POMER[odstup.coerceIn(ODSTUPY.indices)]

    fun odstupZPomeru(pomer: Float): Int =
        ODSTUP_POMER.indices.minByOrNull {
            kotlin.math.abs(ODSTUP_POMER[it] - pomer)
        } ?: 1

    /** Přirozený pokyn pro Qwen 2.1; starý LoRA spouštěč už není potřeba. */
    fun prompt(azimut: Int, vyska: Int, odstup: Int): String {
        val smer = AZIMUTY[azimut.coerceIn(AZIMUTY.indices)].first
        val vyskaKamery = VYSKY[vyska.coerceIn(VYSKY.indices)].first
        val zaber = ODSTUPY[odstup.coerceIn(ODSTUPY.indices)].first
        return "Edit <image1> into a $smer, $vyskaKamery, $zaber. " +
            "Change only the camera viewpoint and framing. Preserve the exact subject, " +
            "identity, clothing, materials, proportions, lighting and scene; reconstruct " +
            "newly visible surfaces consistently and photorealistically."
    }

    fun popis(azimut: Int, vyska: Int, odstup: Int): String = listOf(
        AZIMUTY[azimut.coerceIn(AZIMUTY.indices)].second,
        VYSKY[vyska.coerceIn(VYSKY.indices)].second,
        ODSTUPY[odstup.coerceIn(ODSTUPY.indices)].second,
    ).joinToString(", ") { t(it) }

    private var cached: String? = null

    private fun template(ctx: Context): String = cached ?: ctx.resources
        .openRawResource(R.raw.workflow_qwen21_edit)
        .bufferedReader().use { it.readText() }.also { cached = it }

    fun build(
        ctx: Context, seed: Long, images: List<String>,
        azimut: Int, vyska: Int, odstup: Int,
    ): JSONObject = build(template(ctx), seed, images, azimut, vyska, odstup)

    fun build(
        template: String, seed: Long, images: List<String>,
        azimut: Int, vyska: Int, odstup: Int,
    ): JSONObject {
        val scene = ImageEditScene(
            motor = EditMotor.QWEN21,
            prompt = prompt(azimut, vyska, odstup),
            qwen21Steps = STEPS,
            qwen21Resolution = Qwen21Resolution.STANDARD,
        )
        return Qwen21EditBuilder.build(template, scene, seed, images).also { wf ->
            wf.getJSONObject(N_SAVE).getJSONObject("inputs")
                .put("filename_prefix", "H3AngleQwen21")
        }
    }

    fun stageForClass(cls: String?): Stage = Qwen21EditBuilder.stageForClass(cls)
    fun rangeForClass(cls: String?): Pair<Float, Float> = Qwen21EditBuilder.rangeForClass(cls)
    fun reportsSteps(cls: String?): Boolean = Qwen21EditBuilder.reportsSteps(cls)
    fun nodeClasses(wf: JSONObject): Map<String, String> = Qwen21EditBuilder.nodeClasses(wf)
}
