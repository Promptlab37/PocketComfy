package cz.promptlab.h3video.comfy

import android.content.Context
import cz.promptlab.h3video.R
import cz.promptlab.h3video.data.t
import org.json.JSONObject

/**
 * Stavitel grafu pro kartu **Úhel kamery** — Qwen Image Edit 2511
 * s LoRA `qwen-image-edit-2511-multiple-angles`
 * (`res/raw/workflow_qwen_angle.json`, převzaté z uživatelova
 * `image_qwen_image_edit_2511_multiangle_camera_PROMPTLAB.json`).
 *
 * Dvě úmyslné odchylky od předlohy, obě mají důvod:
 *
 * 1. **Model je fp8 kvantizace** (`qwen_image_edit_2511_fp8_e4m3fn`) místo
 *    bf16 — bf16 má 40 GB a na disku serveru nebyl. Stejná záměna jako
 *    u karty Oprava fotky.
 * 2. **Uzel `QwenMultiangleCameraNode` v grafu není.** Předloha ho má, ale
 *    na serveru nainstalovaný není a hlavně nedělá nic jiného, než že
 *    z tříwidgetového posuvníku složí textový řetězec. Ten si appka poskládá
 *    sama v [prompt] — je to čistý převodník UI→text, ne výpočet.
 *
 * Předloha má pipeline schovanou v subgrafu; do API podoby je narovnaná,
 * protože subgrafy v API grafu neexistují.
 */
object AngleBuilder {

    const val N_IMAGE = "41"
    const val N_PROMPT = "103"
    const val N_PROMPT_ZAPORNY = "100"
    const val N_LORA_UHLY = "110"
    const val N_SAMPLER = "105"
    const val N_SAVE = "9"

    /** Kroky z předlohy (Lightning LoRA na 8 kroků). */
    const val STEPS = 8

    /**
     * Spouštěč LoRA. Bez něj se natrénované pózy neprojeví — model se zachová
     * jako obyčejný Qwen Edit a fotku jen lehce překreslí.
     */
    const val SPOUSTEC = "<sks>"

    /**
     * Osm směrů dokola kolem objektu, v pořadí po směru hodinových ručiček
     * od pohledu zepředu. Anglická slova jsou slovník, na kterém je LoRA
     * natrénovaná — nesmí se přepisovat ani překládat.
     */
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

    /** Čtyři výšky kamery (−30°, 0°, 30°, 60°). */
    val VYSKY: List<Pair<String, String>> = listOf(
        "low-angle shot" to "podhled",
        "eye-level shot" to "v úrovni očí",
        "elevated shot" to "mírný nadhled",
        "high-angle shot" to "nadhled",
    )

    /** Tři odstupy od objektu. */
    val ODSTUPY: List<Pair<String, String>> = listOf(
        "close-up" to "detail",
        "medium shot" to "polocelek",
        "wide shot" to "celek",
    )

    /** Kolik póz LoRA umí — 8 × 4 × 3. Karta nenabízí nic mimo tenhle rozsah. */
    const val POZ = 96

    // --------------------------------------------------- geometrie ovladače
    //
    // Karta kameru neukazuje posuvníky, ale půdorysem: objekt uprostřed,
    // kamera kolem něj. Přepočty sedí tady, aby šly ověřit testem bez UI.

    /**
     * Stupně na půdorysu pro daný směr. **0° je dole** (kamera před objektem,
     * tedy tam, kde stojí divák) a roste po směru hodinových ručiček — stejně
     * jako `horizontal_angle` v uzlu balíčku.
     *
     * Že „zprava" vychází na obrazovce vlevo, není chyba: objekt je k divákovi
     * čelem, takže jeho pravá ruka je na divákově levé straně.
     */
    fun uhelProSmer(smer: Int): Float = (smer.coerceIn(AZIMUTY.indices) * 45).toFloat()

    /** Opačný převod: úhel na půdorysu → nejbližší z osmi směrů. */
    fun smerZUhlu(stupne: Float): Int {
        val norm = ((stupne % 360f) + 360f) % 360f
        return (Math.round(norm / 45f) % AZIMUTY.size)
    }

    /** Výška kamery ve stupních, jak ji čeká uzel (−30°, 0°, 30°, 60°). */
    val VYSKA_STUPNE = listOf(-30f, 0f, 30f, 60f)

    fun uhelProVysku(vyska: Int): Float = VYSKA_STUPNE[vyska.coerceIn(VYSKY.indices)]

    /** Opačný převod: úhel nad obzorem → nejbližší ze čtyř výšek. */
    fun vyskaZUhlu(stupne: Float): Int {
        val omezene = stupne.coerceIn(VYSKA_STUPNE.first(), VYSKA_STUPNE.last())
        return VYSKA_STUPNE.indices.minByOrNull { kotlin.math.abs(VYSKA_STUPNE[it] - omezene) } ?: 1
    }

    /**
     * Odstup jako podíl poloměru půdorysu (0 = u objektu, 1 = na kraji).
     * Detail je nejblíž, celek nejdál — pořadí odpovídá [ODSTUPY].
     */
    val ODSTUP_POMER = listOf(0.42f, 0.66f, 0.92f)

    fun pomerProOdstup(odstup: Int): Float = ODSTUP_POMER[odstup.coerceIn(ODSTUPY.indices)]

    /** Opačný převod: vzdálenost od středu (podíl poloměru) → nejbližší odstup. */
    fun odstupZPomeru(pomer: Float): Int =
        ODSTUP_POMER.indices.minByOrNull { kotlin.math.abs(ODSTUP_POMER[it] - pomer) } ?: 1

    /**
     * Složí zadání ve tvaru, na kterém je LoRA natrénovaná:
     * `<sks> {azimut} {výška} {odstup}`. Pořadí slov je závazné.
     */
    fun prompt(azimut: Int, vyska: Int, odstup: Int): String = listOf(
        SPOUSTEC,
        AZIMUTY[azimut.coerceIn(AZIMUTY.indices)].first,
        VYSKY[vyska.coerceIn(VYSKY.indices)].first,
        ODSTUPY[odstup.coerceIn(ODSTUPY.indices)].first,
    ).joinToString(" ")

    /** Lidský popis pro historii a galerii („zprava, nadhled, detail"). */
    fun popis(azimut: Int, vyska: Int, odstup: Int): String = listOf(
        AZIMUTY[azimut.coerceIn(AZIMUTY.indices)].second,
        VYSKY[vyska.coerceIn(VYSKY.indices)].second,
        ODSTUPY[odstup.coerceIn(ODSTUPY.indices)].second,
    ).joinToString(", ") { t(it) }

    private var cached: String? = null

    private fun template(ctx: Context): String = cached ?: ctx.resources
        .openRawResource(R.raw.workflow_qwen_angle)
        .bufferedReader().use { it.readText() }.also { cached = it }

    fun build(
        ctx: Context, seed: Long, images: List<String>,
        azimut: Int, vyska: Int, odstup: Int, sila: Float = 1f,
    ): JSONObject = build(template(ctx), seed, images, azimut, vyska, odstup, sila)

    /** Stejné sestavení z textu předlohy, ať jde graf ověřit testem bez Androidu. */
    fun build(
        template: String, seed: Long, images: List<String>,
        azimut: Int, vyska: Int, odstup: Int, sila: Float = 1f,
    ): JSONObject {
        val wf = JSONObject(template)
        wf.inputs(N_IMAGE).put("image", images.getOrElse(0) { "" })
        wf.inputs(N_SAMPLER).put("seed", seed)
        wf.inputs(N_PROMPT).put("prompt", prompt(azimut, vyska, odstup))
        // Záporné zadání zůstává prázdné jako v předloze; CFG je 1, takže se
        // stejně neuplatní, ale uzel musí v grafu být (vede do vzorkovače).
        wf.inputs(N_PROMPT_ZAPORNY).put("prompt", "")
        wf.inputs(N_LORA_UHLY).put("strength_model", sila.toDouble())
        return wf
    }

    private fun JSONObject.inputs(node: String): JSONObject =
        getJSONObject(node).getJSONObject("inputs")

    fun stageForClass(cls: String?): Stage = when (cls) {
        "UNETLoader", "LoraLoaderModelOnly", "CLIPLoader", "VAELoader" -> Stage.MODELS
        "LoadImage", "FluxKontextImageScale" -> Stage.REFERENCES
        "TextEncodeQwenImageEditPlus", "FluxKontextMultiReferenceLatentMethod",
        "ModelSamplingAuraFlow", "CFGNorm", "VAEEncode" -> Stage.ENCODING
        "KSampler" -> Stage.SAMPLING
        "VAEDecode", "SaveImage" -> Stage.MUXING
        else -> Stage.SAMPLING
    }

    fun rangeForClass(cls: String?): Pair<Float, Float> = when (cls) {
        "UNETLoader", "LoraLoaderModelOnly", "CLIPLoader", "VAELoader" -> 0.00f to 0.10f
        "LoadImage", "FluxKontextImageScale" -> 0.10f to 0.12f
        "TextEncodeQwenImageEditPlus", "FluxKontextMultiReferenceLatentMethod",
        "ModelSamplingAuraFlow", "CFGNorm", "VAEEncode" -> 0.12f to 0.18f
        "KSampler" -> 0.18f to 0.84f
        "VAEDecode" -> 0.84f to 0.89f
        "SaveImage" -> 0.89f to 0.90f
        else -> 0.18f to 0.84f
    }

    fun reportsSteps(cls: String?): Boolean = cls == "KSampler"

    fun nodeClasses(wf: JSONObject): Map<String, String> =
        wf.keys().asSequence().mapNotNull { id ->
            wf.optJSONObject(id)?.optString("class_type")?.takeIf { it.isNotEmpty() }?.let { id to it }
        }.toMap()
}
