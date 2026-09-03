package cz.promptlab.h3video.comfy

import android.content.Context
import cz.promptlab.h3video.R
import cz.promptlab.h3video.data.DlssStyl
import cz.promptlab.h3video.data.UpscaleScene
import org.json.JSONObject

/**
 * Stavitel grafu pro **rychlé doostření DLSS 5** — druhá metoda karty
 * *Zvětšit* vedle SeedVR2.
 *
 * Jede na balíku `ComfyUI-DLSS5-Enhancer` (Blueforcer), který volá pravý
 * NVIDIA DLSS 5 Neural Rendering (NGX feature 18) přes nativní D3D12 proces.
 * Není to difuzní model: fotka se neregeneruje, jen se rekonstruují detaily
 * (lokální tón, struktura, kůže) a volitelně se rovnou zvětší 1,5× až 3×.
 * Proti SeedVR2 je to řádově rychlejší a jde to na kartu bez čekání ve frontě
 * modelů, ale nedomýšlí to, co v předloze není.
 *
 * Předloha `res/raw/workflow_dlss_enhance.json` drží výchozí hodnoty přesně
 * podle schématu uzlů; dosazuje se jen fotka, míra zvětšení, styl a síla.
 */
object DlssBuilder {

    const val N_IMAGE = "10"
    const val N_SETTINGS = "20"
    const val N_ENHANCE = "30"
    const val N_SAVE = "40"

    /** Popisky režimů zvětšení tak, jak je uzel `DLSS5Settings` čeká. */
    const val MODE_1X = "1x (DLAA / native)"
    const val MODE_15X = "1.5x (Quality)"
    const val MODE_2X = "2x (Performance)"
    const val MODE_3X = "3x (Ultra Performance)"

    /** Dlouhá hrana výsledku, kterou runtime ještě zvládne (kratší 4320). */
    const val MAX_LONG_EDGE = 7680
    const val MAX_SHORT_EDGE = 4320

    /** Míra zvětšení → popisek pro uzel. */
    fun modeFor(nasobek: String): String = when (nasobek) {
        "1.5x" -> MODE_15X
        "2x" -> MODE_2X
        "3x" -> MODE_3X
        else -> MODE_1X
    }

    /** Kolikrát se strana zvětší — pro odhad výsledné velikosti v UI. */
    fun factorFor(nasobek: String): Float = when (nasobek) {
        "1.5x" -> 1.5f
        "2x" -> 2f
        "3x" -> 3f
        else -> 1f
    }

    private var cached: String? = null

    private fun template(ctx: Context): String = cached ?: ctx.resources
        .openRawResource(R.raw.workflow_dlss_enhance)
        .bufferedReader().use { it.readText() }.also { cached = it }

    fun build(ctx: Context, scene: UpscaleScene, images: List<String>): JSONObject =
        build(template(ctx), scene, images)

    /** Stejné sestavení z textu předlohy, ať jde graf ověřit testem bez Androidu. */
    fun build(template: String, scene: UpscaleScene, images: List<String>): JSONObject {
        val wf = JSONObject(template)
        wf.inputs(N_IMAGE).put("image", images.getOrElse(0) { "" })
        val s = wf.inputs(N_SETTINGS)
        s.put("upscaling_mode", modeFor(scene.dlssNasobek))
        s.put("nr_style", scene.dlssStyl.uzel)
        s.put("nr_intensity", scene.dlssSila.toDouble())
        // Kůže se rekonstruuje jen se zapnutou automatickou maskou — ta modelu
        // teprve říká, kde pleť je. Bez ní je posuvník k ničemu.
        s.put("automatic_mask", scene.dlssPlet)
        s.put("skin_structure_strength", if (scene.dlssPlet) 2.0 else -1.0)
        // Jedna fotka není sekvence: dopočítávat pohybové vektory nemá co
        // sledovat a jen by to přidalo práci navíc.
        s.put("motion", "none")
        return wf
    }

    private fun JSONObject.inputs(node: String): JSONObject =
        getJSONObject(node).getJSONObject("inputs")

    /**
     * Fáze podle třídy uzlu. Vrací `null` u tříd, které do DLSS grafu nepatří —
     * podle toho pozná běh, že jede SeedVR2, a nemusí se držet zvlášť příznak.
     */
    fun stageForClass(cls: String?): Stage? = when (cls) {
        "LoadImage" -> Stage.REFERENCES
        "DLSS5Settings" -> Stage.MODELS
        "DLSS5EnhanceImages", "DLSS5EnhanceVideoFile" -> Stage.SAMPLING
        "SaveImage" -> Stage.MUXING
        else -> null
    }

    fun rangeForClass(cls: String?): Pair<Float, Float>? = when (stageForClass(cls)) {
        Stage.REFERENCES -> 0.00f to 0.06f
        Stage.MODELS -> 0.06f to 0.10f
        Stage.SAMPLING -> 0.10f to 0.86f
        Stage.MUXING -> 0.86f to 0.90f
        else -> null
    }

    /**
     * Uzel sice hlásí postup, ale po **snímcích** — u jedné fotky je celkem
     * jeden. Kdyby se na to ukazatel navázal, porovnával by „1" s počtem kroků
     * plánovaným pro difuzní kartu a zůstal by viset na pár procentech.
     * Průběh se tu tedy vede podle fází, ne podle kroků.
     */
    fun reportsSteps(cls: String?): Boolean = false

    /** Pozná se DLSS graf mezi třídami odeslaného workflow? */
    fun jeDlss(classes: Map<String, String>): Boolean =
        classes.containsValue("DLSS5EnhanceImages")

    fun nodeClasses(wf: JSONObject): Map<String, String> =
        wf.keys().asSequence().mapNotNull { id ->
            wf.optJSONObject(id)?.optString("class_type")?.takeIf { it.isNotEmpty() }?.let { id to it }
        }.toMap()
}
