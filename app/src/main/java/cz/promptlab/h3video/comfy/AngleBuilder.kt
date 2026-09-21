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

    /**
     * O kolik stupňů a kterým směrem kamera objede objekt.
     *
     * Vrací kladné číslo a slovo `right`/`left`, ne 0–360°: otočení „o 90°
     * doleva" je pro model srozumitelnější než „na 270°". Směry odpovídají
     * uzlu `QwenMultiangleCameraNode` — ten je počítá od kamery, ne od
     * objektu (na 90° sedí kamera vpravo od původního místa a říká tomu
     * „right side view"), takže i tady je `right` = kamera jde doprava.
     */
    fun otoceni(azimut: Int): Pair<Int, String> {
        val stupne = (azimut.coerceIn(AZIMUTY.indices) * 45)
        return if (stupne <= 180) stupne to "right" else (360 - stupne) to "left"
    }

    /** Výška kamery ve stupních nad úrovní očí; záporná je podhled. */
    fun vyskaPopis(vyska: Int): String {
        val slovo = VYSKY[vyska.coerceIn(VYSKY.indices)].first
        return when (val st = VYSKA_STUPNE[vyska.coerceIn(VYSKY.indices)].toInt()) {
            0 -> "at eye level ($slovo)"
            in Int.MIN_VALUE..-1 -> "${-st} degrees below eye level, tilted up ($slovo)"
            else -> "$st degrees above eye level, tilted down ($slovo)"
        }
    }

    /**
     * Pokyn pro Qwen Image 2.1.
     *
     * **Píše se jako otočení o stupně, ne jako název pohledu.** Qwen popisuje
     * změnu pohledu v dokumentaci právě takhle („rotate by 90 degrees", „full
     * 180-degree rotation") a jen na to je vycvičený. Samotné „left side view"
     * bral jako slabý pokyn a fotku nechal skoro zepředu; „back view" prošlo,
     * protože 180° je natolik jednoznačné, že se splete těžko. Nahlášeno
     * 21. 9. 2026 („dám z levé strany a je to pořád zepředu, zezadu funguje").
     *
     * Druhá polovina opravy je **poměr vět**: původní pokyn měl jednu větu
     * o otočení a dlouhý výčet toho, co se nesmí změnit. Oficiální systémový
     * prompt Qwenu (`qwen21_pe_system_i2i.txt`) přitom varuje přesně před
     * tímhle — *under-editing*, kdy se žádaná změna provede jen naznačeně.
     * Zachování se proto drží krátce a pokyn vede operace.
     */
    fun prompt(azimut: Int, vyska: Int, odstup: Int): String {
        val smer = AZIMUTY[azimut.coerceIn(AZIMUTY.indices)].first
        val zaber = ODSTUPY[odstup.coerceIn(ODSTUPY.indices)].first
        val (stupne, strana) = otoceni(azimut)
        val otoc = when {
            stupne == 0 -> "Keep the camera in front of the subject in <image1>"
            stupne == 180 -> "Orbit the camera a full 180 degrees around the subject " +
                "in <image1> to look at it from directly behind"
            else -> "Orbit the camera $stupne degrees to the $strana around the subject " +
                "in <image1>, keeping the subject centred"
        }
        return "$otoc. Render the subject from that new $smer, " +
            "${vyskaPopis(vyska)}, as a $zaber. " +
            "This is a real change of viewpoint, not a crop or a rotation of the picture: " +
            "surfaces that were hidden before must now be drawn, and surfaces that turn " +
            "away must disappear. Keep the same subject, clothing, materials, colours " +
            "and lighting, and draw the newly visible parts photorealistically."
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
