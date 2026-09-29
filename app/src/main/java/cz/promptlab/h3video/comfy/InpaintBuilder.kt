package cz.promptlab.h3video.comfy

import android.content.Context
import cz.promptlab.h3video.R
import cz.promptlab.h3video.data.InpaintModel
import cz.promptlab.h3video.data.InpaintRezim
import cz.promptlab.h3video.data.InpaintScene
import cz.promptlab.h3video.data.Smer
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
    const val QWEN21_STEPS = 25

    fun stepsFor(model: InpaintModel): Int = when (model) {
        InpaintModel.KLEIN -> KLEIN_STEPS
        InpaintModel.FILL -> FILL_STEPS
        InpaintModel.QWEN21 -> QWEN21_STEPS
    }

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
        if (text.isEmpty()) return text
        return when (model) {
            InpaintModel.FILL -> text
            InpaintModel.KLEIN ->
                "Repaint the masked region of the image so that it shows: $text. " +
                    "Actually change that region — do not return it unchanged. " +
                    "Match the surrounding lighting, perspective and grain, and keep " +
                    "the rest of the image exactly as it is."
            // Qwen 2.1 čte zadání jako pokyn k úpravě a výřez zná jako
            // <image1> — stejný tvar jako na kartě Úprava obrázku. Zbytek
            // fotky za maskou neřeší: ten do grafu ani nevstupuje.
            InpaintModel.QWEN21 ->
                "In <image1>, repaint the masked region so that it shows: $text. " +
                    "Apply the change clearly — the region must not come back unchanged. " +
                    "Keep the people, objects and style outside the masked region " +
                    "exactly as they are, and match the surrounding lighting, " +
                    "perspective, focus and grain so the repainted part blends in."
        }
    }

    private val cached = HashMap<InpaintModel, String>()

    private fun template(ctx: Context, model: InpaintModel): String = cached.getOrPut(model) {
        val res = when (model) {
            InpaintModel.KLEIN -> R.raw.workflow_inpaint_klein
            InpaintModel.FILL -> R.raw.workflow_inpaint_fill
            InpaintModel.QWEN21 -> R.raw.workflow_inpaint_qwen21
        }
        ctx.resources.openRawResource(res).bufferedReader().use { it.readText() }
    }

    private var cachedRozsireni: String? = null

    /**
     * Předloha rozšíření. Je to tentýž graf jako inpaint přes Qwen 2.1, jen
     * bez načítače masky — tu vyrobí `ImagePadForOutpaint` z přilepeného
     * místa — a s původní fotkou jako jedinou předlohou `<image1>`.
     */
    private fun templateRozsireni(ctx: Context): String = cachedRozsireni
        ?: ctx.resources.openRawResource(R.raw.workflow_outpaint_qwen21)
            .bufferedReader().use { it.readText() }.also { cachedRozsireni = it }

    /** Odvázaná LoRA se do grafu vkládá, jen když je vybraná (Klein). */
    const val N_LORA_KLEIN = "5"

    /** Výřez kolem masky; u rozšíření navíc přesah do fotky a prolnutí. */
    const val N_POPIS = "23"
    const val N_LORA_ROZSIRENI = "5"

    /** Přilepení místa a maska přidané plochy (jen u rozšíření). */
    const val N_PLATNO = "15"

    /**
     * Strop rozšíření v procentech. Qwen ve své příručce doporučuje 30–50 %
     * plochy navíc; nad 100 % už model nemá z čeho vycházet a scénu si
     * vymýšlí od nuly, což s původní fotkou přestane ladit.
     */
    const val ROZSIRENI_MAX = 100

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
        // Qwen má zadání v `prompt`, oba flux modely v `text` — je to jiná
        // třída uzlu, ne jiné pojmenování téhož.
        wf.inputs(N_TEXT).put(
            if (model == InpaintModel.QWEN21) "prompt" else "text",
            zadaniProModel(model, prompt),
        )
        if (model == InpaintModel.QWEN21) {
            wf.inputs(N_SAMPLER).put("seed", seed)
            // Jen LoRA pro 2.1 — starý Qwen-Image (60 bloků) by graf shodil.
            if (cz.promptlab.h3video.data.Qwen21Lora.soubor(lora)) zapojLoraQwen21(wf, lora, loraSila)
            // Pod maskou má vzniknout obsah ze zadání, ne dokreslení toho,
            // co tam bylo. Sílu karta u tohohle modelu ani nenabízí.
            wf.inputs(N_SAMPLER).put("denoise", 1.0)
        } else if (model == InpaintModel.KLEIN) {
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

    /** LoRA pro Qwen 2.1 mezi načtený model a jeho KV cache. */
    const val N_LORA_QWEN21 = "6"

    /**
     * Qwen 2.1 (domalování i rozšíření mají stejná čísla uzlů): UNETLoader 1
     * → QwenImage21Cache 4. LoRA se vkládá mezi ně; bez ní se graf nezmění.
     */
    fun zapojLoraQwen21(wf: JSONObject, lora: String, sila: Float) {
        if (lora.isBlank() || sila <= 0f) return
        wf.put(
            N_LORA_QWEN21,
            JSONObject()
                .put("class_type", "LoraLoaderModelOnly")
                .put(
                    "inputs",
                    JSONObject()
                        .put("model", JSONArray().put("1").put(0))
                        .put("lora_name", lora)
                        .put("strength_model", sila.toDouble()),
                )
                .put("_meta", JSONObject().put("title", "Doplňková LoRA")),
        )
        wf.inputs("4").put("model", JSONArray().put(N_LORA_QWEN21).put(0))
    }

    /**
     * Spouštěč outpaint LoRA — doslova z její dokumentace
     * (huggingface.co/ausboss/Qwen-Image-2.1-Outpaint-LoRA): „The instruction
     * is the trigger. Put it first.“ Za něj graf připojí „ Scene: “ a popis
     * fotky, který napíše Qwen3-VL (uzel TextGenerate), stejně jako autorův
     * workflow na Civitai.
     */
    const val SPOUSTEC_ROZSIRENI = "Outpaint the image: replace the solid gray areas with a " +
        "seamless continuation of the scene, keeping the existing picture unchanged."

    /** Pokyn pro popis fotky — doslova z autorova workflow (Qwen Image 2.1 Outpaint v1.1). */
    const val POPIS_FOTKY = "Write the text-to-image prompt that would generate this exact picture. " +
        "One paragraph of 50 to 90 words. Begin with the medium and style in a few words, such as " +
        "\"Photograph\", \"Smartphone photo\", \"Anime illustration\", \"Oil painting\" or \"3D render\". " +
        "Then describe the subject, the setting, and what fills every part of the frame from edge to " +
        "edge, including the background and the areas near the borders. Include materials, colours, " +
        "lighting, time of day, camera angle and framing. Quote any readable text exactly. Use plain " +
        "factual language; do not start with 'The image' or 'This picture' and do not give opinions. " +
        "Output only the prompt."

    /**
     * Zadání pro popisovač. Co uživatel napíše, jde podle autora do popisu
     * („Extra detail … goes to the captioner, which writes it into the
     * description“), ne přímo do pokynu pro Qwen — spouštěč LoRA musí zůstat
     * beze změny.
     */
    fun zadaniPopisu(prompt: String): String {
        val text = prompt.trim()
        return if (text.isEmpty()) POPIS_FOTKY
        else "$POPIS_FOTKY Also include in the prompt: $text"
    }

    /**
     * Kolik pixelů se přilepí na jednu stranu. Uzel bere celé pixely s krokem
     * 8, ne procenta — proto se to počítá z rozměru fotky tady.
     */
    fun okrajPx(rozmer: Int, procent: Int, zvoleny: Boolean): Int =
        if (!zvoleny || rozmer <= 0) 0
        else ((rozmer * procent / 100) / 8) * 8

    fun buildRozsireni(
        ctx: Context, scene: InpaintScene, seed: Long, images: List<String>,
    ): JSONObject {
        val (w, h) = rozmeryFotky(scene.source)
        return buildRozsireni(templateRozsireni(ctx), scene, seed, images, w, h)
    }

    /** Rozměry fotky v pixelech; bez dekódování celé bitmapy do paměti. */
    private fun rozmeryFotky(f: java.io.File?): Pair<Int, Int> {
        if (f == null || !f.exists()) return 0 to 0
        val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeFile(f.absolutePath, opts)
        return opts.outWidth to opts.outHeight
    }

    /**
     * Rozšíření obrázku přesně podle autora outpaint LoRA pro Qwen Image 2.1
     * (README na Hugging Face + workflow „Qwen Image 2.1 Outpaint v1.1“ na
     * Civitai, převzato 1:1): šedé plátno #808080 (AusBoss Load Image + Pad,
     * prolnutí 32, násobek 32, 1 MP) jako `image_1` s `resolution` 0,
     * vzorkuje se z latentu enkodéru **bez masky** (Set Latent Noise Mask na
     * Qwen 2.1 kreslí na švu obdélník), 25 kroků, CFG 1, euler/simple;
     * RGBA → RGB a AusBoss Stitch Inpaint vrátí původní pixely.
     *
     * Bez LoRA Qwen 2.1 fotku přerámuje, zmenší nebo nechá šedou — přesně
     * to se v appce dělo do 4.95 („dolů a doleva“ = zdvojená postava,
     * „dolů“ = šedý pruh, 29. 9. 2026).
     *
     * Jediná odchylka: autorův LoRA Loader (AusBoss) je tu jádrový
     * `LoraLoaderModelOnly` se stejnou vahou 1,0.
     */
    fun buildRozsireni(
        template: String, scene: InpaintScene, seed: Long, images: List<String>,
        sirka: Int, vyska: Int,
    ): JSONObject {
        val wf = JSONObject(template)
        val foto = images.getOrElse(0) { "" }
        wf.inputs(N_IMAGE).put("image", foto)
        wf.inputs(N_PLATNO).put("image", foto)
        wf.inputs(N_POPIS).put("prompt", zadaniPopisu(scene.prompt))
        // Doplňková LoRA jde ZA outpaint LoRA, ne místo ní.
        if (cz.promptlab.h3video.data.Qwen21Lora.soubor(scene.lora)) {
            zapojLoraQwen21(wf, scene.lora, scene.loraSila)
            if (wf.has(N_LORA_QWEN21))
                wf.inputs(N_LORA_QWEN21).put("model", JSONArray().put(N_LORA_ROZSIRENI).put(0))
        }
        wf.inputs(N_PLATNO).apply {
            put("pad_top", okrajPx(vyska, scene.procento(Smer.NAHORU), Smer.NAHORU in scene.smery))
            put("pad_bottom", okrajPx(vyska, scene.procento(Smer.DOLU), Smer.DOLU in scene.smery))
            put("pad_left", okrajPx(sirka, scene.procento(Smer.VLEVO), Smer.VLEVO in scene.smery))
            put("pad_right", okrajPx(sirka, scene.procento(Smer.VPRAVO), Smer.VPRAVO in scene.smery))
        }
        wf.inputs(N_SAMPLER).put("seed", seed)
        return wf
    }

    private fun JSONObject.inputs(node: String): JSONObject =
        getJSONObject(node).getJSONObject("inputs")

    fun stageForClass(cls: String?): Stage = when (cls) {
        "UNETLoader", "CLIPLoader", "DualCLIPLoader", "VAELoader",
        "Power Lora Loader (rgthree)", "LoraLoaderModelOnly",
        "QwenImage21Cache" -> Stage.MODELS
        "LoadImage", "ImageToMask", "InpaintCropImproved", "GetImageSize",
        "VAEEncode", "SetLatentNoiseMask", "AUSBOSS_NODES_LoadImagePad" -> Stage.REFERENCES
        "CLIPTextEncode", "ReferenceLatent", "ConditioningZeroOut", "FluxGuidance",
        "InpaintModelConditioning", "Flux2Scheduler", "KSamplerSelect", "RandomNoise",
        "CFGGuider", "TextEncodeQwenImage21", "TextGenerate", "StringConcatenate" -> Stage.ENCODING
        "KSampler", "SamplerCustomAdvanced" -> Stage.SAMPLING
        "VAEDecode", "InpaintStitchImproved", "SaveImage", "SplitImageWithAlpha",
        "AUSBOSS_NODES_StitchInpaint" -> Stage.MUXING
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
