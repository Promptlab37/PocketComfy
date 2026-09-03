package cz.promptlab.h3video.comfy

import android.content.Context
import cz.promptlab.h3video.R
import cz.promptlab.h3video.data.InpaintModel
import org.json.JSONArray
import org.json.JSONObject

/**
 * Stavitel grafu pro kartu **Domalovat** (inpainting) — zamaskovanou část
 * fotky přemaluje podle věty, zbytek zůstane bajt po bajtu stejný.
 *
 * Dvě předlohy, obě z uzlů, které server má kvůli jiným kartám:
 *
 * - **FLUX.2 Klein 9B** (`workflow_inpaint_klein.json`) — výchozí. Model
 *   z konce roku 2025 dělá inpaint jako běžnou úpravu s referencí: původní
 *   výřez jde do `ReferenceLatent`, `SetLatentNoiseMask` pak pustí přepis
 *   jen pod masku. Destilovaný, takže mu stačí 4 kroky a cfg 1 — přesně
 *   jak to má uživatel ve svém `Flux2-Klein_PROMPTLAB.json`.
 * - **Flux Fill dev** (`workflow_inpaint_fill.json`) — druhá volba. Model
 *   trénovaný přímo na díry v obraze (`InpaintModelConditioning`), stejná
 *   sestava jako u karty Výměna tváře, jen bez vlepované tváře.
 *
 * Obě předlohy vyřezávají okolí masky uzlem `InpaintCropImproved` a hotový
 * kus vlepují zpět (`InpaintStitchImproved`) — model tak pracuje v plném
 * rozlišení na výřezu a zbytek fotky se vůbec nepřepočítává.
 *
 * Dosazují se JEN dvě fotky, zadání a seed.
 */
object InpaintBuilder {

    /** Uzly jsou v obou předlohách schválně stejně očíslované. */
    const val N_IMAGE = "10"
    const val N_MASK = "11"
    const val N_TEXT = "30"

    /**
     * Seed: Klein ho má v RandomNoise, Flux Fill přímo v KSampleru. Čísla se
     * mezi předlohami nesmí překrývat — u Kleina je 40 plánovač sigem, takže
     * kdyby tam Fill měl KSampler, dosadil by špatný uzel do rozvrhu kroků.
     */
    const val N_NOISE = "42"
    const val N_SAMPLER = "45"

    /** Kroky z předloh — ukazatel průběhu na ně přepočítává hlášení serveru. */
    const val KLEIN_STEPS = 4
    const val FILL_STEPS = 8

    fun stepsFor(model: InpaintModel): Int =
        if (model == InpaintModel.KLEIN) KLEIN_STEPS else FILL_STEPS

    /**
     * Klein je editační model: dostane původní výřez jako referenci a zadání
     * čte jako **příkaz, co s ním udělat**. Holý popis („very well-rounded
     * man") pro něj znamená „tohle tam je, nech to být" — ověřeno na běhu
     * z 2. 9. 2026, kde se pod maskou změnilo jen 3,9 % pixelů a k nepoznání.
     * Proto se jeho zadání zabalí do instrukce; Flux Fill nic takového nechce,
     * ten maluje do díry rovnou to, co je v textu.
     */
    fun zadaniProModel(model: InpaintModel, prompt: String): String {
        val text = prompt.trim()
        if (model != InpaintModel.KLEIN || text.isEmpty()) return text
        return "Repaint the masked region of the image so that it shows: $text. " +
            "Actually change that region — do not return it unchanged. " +
            "Match the surrounding lighting, perspective and grain, and keep " +
            "the rest of the image exactly as it is."
    }

    private val cached = HashMap<InpaintModel, String>()

    private fun template(ctx: Context, model: InpaintModel): String = cached.getOrPut(model) {
        val res = if (model == InpaintModel.KLEIN) R.raw.workflow_inpaint_klein
        else R.raw.workflow_inpaint_fill
        ctx.resources.openRawResource(res).bufferedReader().use { it.readText() }
    }

    /** Odvázaná LoRA se do grafu vkládá, jen když je vybraná (Klein). */
    const val N_LORA_KLEIN = "5"

    fun build(
        ctx: Context, model: InpaintModel, prompt: String, seed: Long, images: List<String>,
        lora: String = "", loraSila: Float = 0.9f, sila: Float = 1f,
    ): JSONObject = build(template(ctx, model), model, prompt, seed, images, lora, loraSila, sila)

    /**
     * [images] v pořadí: fotka, maska štětce (černobílý PNG, bílá = domalovat).
     * Stejné sestavení z textu předlohy, ať jde graf ověřit testem bez Androidu.
     */
    fun build(
        template: String, model: InpaintModel, prompt: String, seed: Long, images: List<String>,
        lora: String = "", loraSila: Float = 0.9f, sila: Float = 1f,
    ): JSONObject {
        val wf = JSONObject(template)
        wf.inputs(N_IMAGE).put("image", images.getOrElse(0) { "" })
        wf.inputs(N_MASK).put("image", images.getOrElse(1) { "" })
        wf.inputs(N_TEXT).put("text", zadaniProModel(model, prompt))
        if (model == InpaintModel.KLEIN) {
            wf.inputs(N_NOISE).put("noise_seed", seed)
            // Klein: LoraLoaderModelOnly mezi UNETLoader a vedení. Bez vybrané
            // LoRA se graf šablony nezmění ani o bajt.
            if (lora.isNotBlank()) {
                wf.put(
                    N_LORA_KLEIN,
                    JSONObject()
                        .put("class_type", "LoraLoaderModelOnly")
                        .put(
                            "inputs",
                            JSONObject()
                                .put("model", JSONArray().put("1").put(0))
                                .put("lora_name", lora)
                                .put("strength_model", loraSila.toDouble()),
                        )
                        .put("_meta", JSONObject().put("title", "Doplňková LoRA")),
                )
                wf.inputs("43").put("model", JSONArray().put(N_LORA_KLEIN).put(0))
            }
        } else {
            wf.inputs(N_SAMPLER).put("seed", seed)
            // Flux Fill: síla přemalování. 1,0 = pod maskou vzniká všechno
            // znovu, nižší hodnota nechá tvar z předlohy a jen ho dokreslí.
            wf.inputs(N_SAMPLER).put("denoise", sila.coerceIn(0.1f, 1f).toDouble())
            // LoRA jde do stávajícího Power Lora Loaderu vedle Turba, ať se
            // aplikuje na model i na textový enkodér.
            if (lora.isNotBlank()) {
                wf.inputs("4").put(
                    "lora_2",
                    JSONObject()
                        .put("on", true)
                        .put("lora", lora)
                        .put("strength", loraSila.toDouble()),
                )
            }
        }
        return wf
    }

    private fun JSONObject.inputs(node: String): JSONObject =
        getJSONObject(node).getJSONObject("inputs")

    fun stageForClass(cls: String?): Stage = when (cls) {
        "UNETLoader", "CLIPLoader", "DualCLIPLoader", "VAELoader",
        "Power Lora Loader (rgthree)", "LoraLoaderModelOnly" -> Stage.MODELS
        "LoadImage", "ImageToMask", "InpaintCropImproved", "GetImageSize",
        "VAEEncode", "SetLatentNoiseMask" -> Stage.REFERENCES
        "CLIPTextEncode", "ReferenceLatent", "ConditioningZeroOut", "FluxGuidance",
        "InpaintModelConditioning", "Flux2Scheduler", "KSamplerSelect", "RandomNoise",
        "CFGGuider" -> Stage.ENCODING
        "KSampler", "SamplerCustomAdvanced" -> Stage.SAMPLING
        "VAEDecode", "InpaintStitchImproved", "SaveImage" -> Stage.MUXING
        else -> Stage.SAMPLING
    }

    fun rangeForClass(cls: String?): Pair<Float, Float> = when (stageForClass(cls)) {
        Stage.MODELS -> 0.00f to 0.10f
        Stage.REFERENCES -> 0.10f to 0.14f
        Stage.ENCODING -> 0.14f to 0.18f
        Stage.SAMPLING -> 0.18f to 0.84f
        else -> 0.84f to 0.90f
    }

    fun reportsSteps(cls: String?): Boolean = cls == "KSampler" || cls == "SamplerCustomAdvanced"

    fun nodeClasses(wf: JSONObject): Map<String, String> =
        wf.keys().asSequence().mapNotNull { id ->
            wf.optJSONObject(id)?.optString("class_type")?.takeIf { it.isNotEmpty() }?.let { id to it }
        }.toMap()
}
