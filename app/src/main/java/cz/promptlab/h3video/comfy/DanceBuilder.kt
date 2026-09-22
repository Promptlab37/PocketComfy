package cz.promptlab.h3video.comfy

import android.content.Context
import cz.promptlab.h3video.R
import cz.promptlab.h3video.data.DanceScene
import org.json.JSONObject

/**
 * Stavitel grafu pro kartu **Dance** — Wan-Dancer 14B
 * (`res/raw/workflow_dance_wan.json`, narovnané z oficiální předlohy
 * `video_wan_dancer.json` od Comfy-Org).
 *
 * ### Jak to funguje
 *
 * Dovnitř jde **fotka člověka a hudba**, ven video, kde ten člověk tančí do
 * rytmu. Vzorové video s tancem se nikam nedává — `WanDancerEncodeAudio` si
 * z hudby spočítá obálku nástupů, MFCC, chroma a pozice dob a ty vstřikuje
 * do modelu, takže choreografii si model vymýšlí sám.
 *
 * Běh má dvě fáze: globální model rozvrhne pohyb (náhled v 8 fps), lokální
 * ho dopiluje na 30 fps. Obojí jede v rychlém režimu s LoRA `lightx2v`, tedy
 * 6 kroků a cfg 1.
 *
 * ### Odchylky od předlohy a proč
 *
 * 1. **Pipeline byla v subgrafu**, API graf subgrafy nezná — narovnaná ven.
 * 2. **Vyhozeny uzly, které existují jen kvůli ovládání v okně**:
 *    `CustomCombo` a „Prompt Selector" (vybíraly N-té slovo ze seznamu),
 *    `ComfySwitchNode` (přepínač rychlého režimu) a `ComfyMathExpression`
 *    (zaokrouhlení rozměrů). Hodnoty dosazuje appka — o patnáct uzlů z cizích
 *    balíků míň, které se můžou po aktualizaci rozejít.
 * 3. **Rozlišení si volí uživatel.** Předloha jede na 720 × 1280, ale to je
 *    2,25× víc bodů; karta proto nabízí i 480 × 832. Pozor na past, na kterou
 *    jsem naletěl: uzel `WanDancerVideo` má ve widgetech 480 × 832, jenže ty
 *    hodnoty jsou v předloze **přepsané spojem** ze dvou `PrimitiveInt`
 *    (720 a 1280). Kdo vezme widget, generuje v nižším rozlišení, než předloha
 *    zamýšlí.
 */
object DanceBuilder {

    /** Fotka tanečníka. */
    const val N_FOTKA = "900"
    /** Hudba. */
    const val N_HUDBA = "901"
    /** Ořez hudby na zvolenou délku. */
    const val N_DELKA = "494"
    /** Druh tance — dosazuje se do čínské šablony promptu. */
    const val N_STYL = "685"
    /** Rozsah pohybu. */
    const val N_ROZSAH = "686"
    /** Vlastní dovětek uživatele k zadání. */
    const val N_POPIS = "631"
    /** Počet úseků po 149 snímcích. */
    const val N_USEKY = "670"
    /** Seed. */
    const val N_SEED = "654"
    /** Zmenšení fotky a obě fáze berou rozlišení odsud. */
    val N_ROZMERY = listOf("640", "647", "668")
    /** Uložení náhledu z první fáze a hotového videa z druhé. */
    const val N_ULOZ_NAHLED = "910"
    const val N_ULOZ = "911"

    /** Kroky obou fází dohromady — podle nich se počítá ukazatel průběhu. */
    const val STEPS = 12

    private var cached: String? = null

    private fun template(ctx: Context): String = cached ?: ctx.resources
        .openRawResource(R.raw.workflow_dance_wan)
        .bufferedReader().use { it.readText() }.also { cached = it }

    fun build(ctx: Context, scene: DanceScene, seed: Long, fotka: String, hudba: String): JSONObject =
        build(template(ctx), scene, seed, fotka, hudba)

    /** Stejné sestavení z textu předlohy, ať jde graf ověřit testem bez Androidu. */
    fun build(
        template: String, scene: DanceScene, seed: Long, fotka: String, hudba: String,
    ): JSONObject {
        val wf = JSONObject(template)
        wf.inputs(N_FOTKA).put("image", fotka)
        wf.inputs(N_HUDBA).put("audio", hudba)

        // Délka videa se řídí dvěma místy naráz: ořezem hudby a počtem úseků.
        // Kdyby se rozešly, druhá fáze by skládala video z jiného počtu kusů,
        // než na kolik je nastříhaná hudba.
        wf.inputs(N_DELKA).put("duration", scene.sekundy.toDouble())
        wf.inputs(N_USEKY).put("num_segments", scene.useku)

        wf.inputs(N_STYL).put("replace", scene.styl.zadani)
        wf.inputs(N_ROZSAH).put("replace", scene.rozsah.zadani)
        wf.inputs(N_POPIS).put("value", scene.popis.trim())
        wf.inputs(N_SEED).put("noise_seed", seed)

        // Rozlišení musí být stejné na všech třech místech: zmenšení fotky
        // a obě fáze. Kdyby se rozešlo, druhá fáze by dopilovávala jinak
        // velké snímky, než jí první poslala.
        N_ROZMERY.forEach { uzel ->
            val ins = wf.inputs(uzel)
            if (ins.has("resize_type.width")) {
                ins.put("resize_type.width", scene.kvalita.sirka)
                ins.put("resize_type.height", scene.kvalita.vyska)
            } else {
                ins.put("width", scene.kvalita.sirka)
                ins.put("height", scene.kvalita.vyska)
            }
        }
        return wf
    }

    private fun JSONObject.inputs(node: String): JSONObject =
        getJSONObject(node).getJSONObject("inputs")

    fun stageForClass(cls: String?): Stage = when (cls) {
        "UNETLoader", "LoraLoaderModelOnly", "CLIPLoader", "VAELoader",
        "CLIPVisionLoader", "ModelSamplingSD3", "SkipLayerGuidanceDiTSimple" -> Stage.MODELS
        "LoadImage", "LoadAudio", "ResizeImageMaskNode", "TrimAudioDuration",
        "CLIPVisionEncode" -> Stage.REFERENCES
        "CLIPTextEncode", "StringReplace", "StringConcatenate", "PrimitiveStringMultiline",
        "ConditioningZeroOut", "WanDancerEncodeAudio", "WanDancerVideo",
        "WanDancerPadKeyframesList", "BasicScheduler", "KSamplerSelect", "RandomNoise",
        "CFGGuider", "PrimitiveInt" -> Stage.ENCODING
        "SamplerCustom", "SamplerCustomAdvanced" -> Stage.SAMPLING
        "VAEDecode", "LatentCutToBatch", "ImageFromBatch", "RebatchImages",
        "CreateVideo", "SaveVideo" -> Stage.MUXING
        else -> Stage.SAMPLING
    }

    fun rangeForClass(cls: String?): Pair<Float, Float> = when (stageForClass(cls)) {
        Stage.MODELS -> 0.00f to 0.10f
        Stage.REFERENCES -> 0.10f to 0.14f
        Stage.ENCODING -> 0.14f to 0.20f
        Stage.SAMPLING -> 0.20f to 0.90f
        else -> 0.90f to 1.00f
    }

    fun reportsSteps(cls: String?): Boolean =
        cls == "SamplerCustom" || cls == "SamplerCustomAdvanced"

    fun nodeClasses(wf: JSONObject): Map<String, String> =
        wf.keys().asSequence().mapNotNull { id ->
            wf.optJSONObject(id)?.optString("class_type")?.takeIf { it.isNotEmpty() }?.let { id to it }
        }.toMap()
}
