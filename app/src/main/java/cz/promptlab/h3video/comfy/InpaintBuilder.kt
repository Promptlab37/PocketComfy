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
    const val N_VYREZ = "20"

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
     * Slovo pro směr tak, jak ho model čte. Qwen má outpainting ve své
     * příručce popsaný právě přes směr rozšíření (扩图 / 延伸画面).
     */
    fun smerSlovem(smer: Smer): String = when (smer) {
        Smer.DOLU -> "downward"
        Smer.NAHORU -> "upward"
        Smer.VLEVO -> "to the left"
        Smer.VPRAVO -> "to the right"
    }

    /** Směry v pořadí enumu, spojené do „downward and to the left". */
    fun smeryVetou(smery: Set<Smer>): String {
        val slova = Smer.entries.filter { it in smery }.map { smerSlovem(it) }
        return when (slova.size) {
            0 -> ""
            1 -> slova[0]
            else -> slova.dropLast(1).joinToString(", ") + " and " + slova.last()
        }
    }

    /**
     * Pokyn k rozšíření.
     *
     * Vede **operace se směrem**, ne popis výsledku — u Qwenu je to stejný
     * případ jako u karty Úhel kamery: název úlohy bez pokynu skončí tím, že
     * model nechá skoro všechno být.
     *
     * **Předlohou `<image1>` je původní fotka, NE výřez se šedým okrajem.**
     * Do 4.38 šel jako `<image1>` výřez s přilepeným šedým plátnem a Qwen
     * jako editační model ten šedý pás občas věrně zkopíroval — 25. 9. 2026
     * vyšel přidaný pás jako rovná šedá (rozptyl 0,6), přestože maska byla
     * správně. Ověřeno pokusem: se samotnou fotkou jako předlohou pás obsahuje
     * obraz a šev není vidět. Velikost plátna určuje latent, ne předloha.
     */
    fun zadaniRozsireni(prompt: String, smery: Set<Smer>): String {
        val text = prompt.trim()
        val kam = smeryVetou(smery)
        // Bez zadání se model řídí jen tím, co na fotce vidí — má ji celou
        // jako <image2>, takže scénu dotáhne sám. Věta ho k tomu musí
        // vyloženě vyzvat, jinak je v pokusu nechat všechno být.
        val co = if (text.isEmpty())
            " Work out what continues there from what the photo already shows."
        else " Fill the new area with: $text."
        return "Extend the picture in <image1> $kam and paint the empty area that " +
            "was added there.$co " +
            "Continue the subject, the perspective, the lighting and the background " +
            "across the seam so the added part looks like it was always in the frame: " +
            "bodies, edges, horizon and floor must line up exactly where they meet. " +
            "Match the grain, focus and colour of the original."
    }

    /**
     * Kolik pixelů se přilepí na jednu stranu. Uzel bere celé pixely s krokem
     * 8, ne procenta — proto se to počítá z rozměru fotky tady.
     */
    fun okrajPx(rozmer: Int, procent: Int, zvoleny: Boolean): Int =
        if (!zvoleny || rozmer <= 0) 0
        else ((rozmer * procent / 100) / 8) * 8

    /**
     * Jak hluboko do původní fotky smí model přepisovat — „rozjezd", na kterém
     * protáhne tvary přes hranici.
     *
     * Ve 3.79 byl 48 px a bylo to málo: změřeno na hotovém běhu, že barevný
     * skok přes šev je 0,83 (uvnitř fotky je běžně 2,5) a ostrost nad a pod
     * švem je 7,96 / 8,01 — tedy **prolnutí ani ostrost problém nebyly**.
     * Vidět byl nesouhlas obsahu: model dokresloval na doraz k hranici a
     * neměl kde navázat tělo, obzor ani podlahu.
     *
     * Počítá se z toho, kolik se přilepuje — u velkého rozšíření je potřeba
     * větší rozjezd než u malého. Strop drží cenu: každý pixel navíc je kus
     * fotky, který se přepočítá znovu.
     */
    fun presahPx(nejvetsiOkraj: Int): Int =
        (nejvetsiOkraj / 3).coerceIn(PRESAH_MIN, PRESAH_MAX)

    const val PRESAH_MIN = 128
    const val PRESAH_MAX = 512

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
     * [images] nese jen fotku — maska přidané plochy vzniká v grafu
     * (`ImagePadForOutpaint`), ne v telefonu.
     *
     * ### Proč přes `ImagePadForOutpaint` a ne přes `extend_for_outpainting`
     *
     * Do 3.78 se plátno přilepovalo přímo v `InpaintCropImproved`. Jenže ten
     * uzel zpracuje masku (`mask_expand_pixels`, `mask_blend_pixels`) **dřív**,
     * než přilepí nové místo, a to pak do masky zapíše natvrdo jedničky
     * (`expanded_mask = torch.ones`). Bez namalované masky tedy prolnutí
     * nemělo na čem pracovat a hranice zůstala jako nůž — přesně ten viditelný
     * přechod, který uživatel nahlásil 22. 9. 2026.
     *
     * Teď plátno i masku vyrobí `ImagePadForOutpaint` (s `feathering = 0`,
     * protože jeho změkčení je Pythonovská smyčka přes každý pixel) a výřez
     * dostane masku už na vstupu — takže se na ni `mask_expand_pixels`
     * i `mask_blend_pixels` normálně uplatní.
     */
    fun buildRozsireni(
        template: String, scene: InpaintScene, seed: Long, images: List<String>,
        sirka: Int, vyska: Int,
    ): JSONObject {
        val wf = JSONObject(template)
        wf.inputs(N_IMAGE).put("image", images.getOrElse(0) { "" })
        wf.inputs(N_TEXT).put("prompt", zadaniRozsireni(scene.prompt, scene.smery))
        // Rozšíření jede vždy na Qwen 2.1 — projde jen LoRA pro 2.1.
        if (cz.promptlab.h3video.data.Qwen21Lora.soubor(scene.lora))
            zapojLoraQwen21(wf, scene.lora, scene.loraSila)
        val okraje = listOf(
            okrajPx(vyska, scene.procent, Smer.NAHORU in scene.smery),
            okrajPx(vyska, scene.procent, Smer.DOLU in scene.smery),
            okrajPx(sirka, scene.procent, Smer.VLEVO in scene.smery),
            okrajPx(sirka, scene.procent, Smer.VPRAVO in scene.smery),
        )
        wf.inputs(N_PLATNO).apply {
            put("top", okraje[0])
            put("bottom", okraje[1])
            put("left", okraje[2])
            put("right", okraje[3])
        }
        wf.inputs(N_VYREZ).put("mask_expand_pixels", presahPx(okraje.max()))
        wf.inputs(N_SAMPLER).put("seed", seed)
        // Přilepené místo je prázdné — dokreslovat tam není co, vzniká celé.
        wf.inputs(N_SAMPLER).put("denoise", 1.0)
        return wf
    }

    private fun JSONObject.inputs(node: String): JSONObject =
        getJSONObject(node).getJSONObject("inputs")

    fun stageForClass(cls: String?): Stage = when (cls) {
        "UNETLoader", "CLIPLoader", "DualCLIPLoader", "VAELoader",
        "Power Lora Loader (rgthree)", "LoraLoaderModelOnly",
        "QwenImage21Cache" -> Stage.MODELS
        "LoadImage", "ImageToMask", "InpaintCropImproved", "GetImageSize",
        "VAEEncode", "SetLatentNoiseMask" -> Stage.REFERENCES
        "CLIPTextEncode", "ReferenceLatent", "ConditioningZeroOut", "FluxGuidance",
        "InpaintModelConditioning", "Flux2Scheduler", "KSamplerSelect", "RandomNoise",
        "CFGGuider", "TextEncodeQwenImage21" -> Stage.ENCODING
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
