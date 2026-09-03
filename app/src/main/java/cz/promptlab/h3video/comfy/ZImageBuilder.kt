package cz.promptlab.h3video.comfy

import android.content.Context
import cz.promptlab.h3video.R
import cz.promptlab.h3video.data.Aspect
import org.json.JSONObject

/**
 * Který model staví novou fotku na kartě **Obrázek**.
 *
 * Do 3.01 tu byly dvě volby a rozlišovaly se názvem souboru modelu. Od 3.02
 * je z toho pořádný číselník: přibyl Z-Image **Base** (nedestilovaný sourozenec
 * Turba), **FLUX.2 Klein 9B** a **ERNIE Image Turbo**. Poslední dva mají jinou
 * architekturu, takže nejedou na šabloně Z-Image, ale na vlastní — stejně jako
 * to má karta Domalovat.
 *
 * `id` je to, co se ukládá do nastavení. Staré uložené hodnoty (prázdno =
 * Turbo, název GGUF souboru = Photoreal) se čtou dál, viz [zId].
 */
enum class T2iModel(
    val id: String,
    val stitek: String,
    val popis: String,
    /** Kroky vzorkování z předlohy — ukazatel průběhu s nimi musí souhlasit. */
    val kroky: Int,
) {
    TURBO(
        "turbo", "Z-Image Turbo",
        "Nejrychlejší. Fotorealismus za pár sekund, na text v obraze slabší.",
        8,
    ),
    PHOTOREAL(
        "photoreal", "Photoreal (odvázaný)",
        "NSFW Photorealistic v6.1 — nic neodmítá. LoRA s ním není potřeba.",
        12,
    ),
    BASE(
        "base", "Z-Image Base",
        "Nedestilovaný základ. Poslouchá zadání líp než Turbo, ale trvá to násobně dýl.",
        30,
    ),
    KLEIN(
        "klein", "FLUX.2 Klein 9B",
        "Nejlíp drží složité zadání a text v obraze. Velký model, načítá se dýl.",
        4,
    ),
    ERNIE(
        "ernie", "ERNIE Image Turbo",
        "Baidu ERNIE na architektuře FLUX.2. Jiný rukopis než Z-Image.",
        9,
    );

    /** Jede na šabloně Z-Image (a smí se k němu tedy přimíchat zimage LoRA)? */
    val zRodinyZImage: Boolean get() = this == TURBO || this == PHOTOREAL || this == BASE

    companion object {
        /**
         * Model z uložené hodnoty. Snese i staré zápisy z verzí do 3.01, kde se
         * ukládal rovnou název souboru modelu.
         */
        fun zId(id: String): T2iModel = when {
            id.isBlank() -> TURBO
            id == ZImageBuilder.NSFW_MODEL_FILE -> PHOTOREAL
            else -> entries.firstOrNull { it.id == id } ?: TURBO
        }
    }
}

/**
 * Stavitel grafu pro kartu **Obrázek**.
 *
 * Základ je pořád uživatelovo Z-Image Turbo workflow
 * (`res/raw/workflow_zimage_t2i.json`, převzaté 1:1 z jeho exportu
 * `image_z_image_turbo_PROMPTLAB.json`, jen narovnané ze subgrafu do API
 * podoby). Dosazují se přesně čtyři hodnoty: zadání, šířka, výška a seed.
 * Kroky, cfg, sampler i shift zůstávají z předlohy.
 *
 * Od 3.02 umí karta i tři další modely. Z-Image Base jede na téže šabloně
 * (mění se jen soubor modelu, kroky a cfg — Base není destilovaný, takže
 * s cfg 1 by nic nevedlo); FLUX.2 Klein a ERNIE mají vlastní předlohu,
 * protože jsou z rodiny FLUX.2 (jiný latent, jiný text encoder, vzorkování
 * přes SamplerCustomAdvanced).
 */
object ZImageBuilder {

    const val N_UNET = "28"
    const val N_CLIP = "30"
    const val N_VAE = "29"
    const val N_TEXT = "27"
    const val N_ZERO = "33"
    const val N_LATENT = "13"
    const val N_SHIFT = "11"
    const val N_SAMPLER = "3"
    const val N_DECODE = "8"
    const val N_SAVE = "9"

    /** Odvázaná LoRA: uzel se do grafu vkládá jen se zapnutým přepínačem. */
    const val N_NSFW_LORA = "90"
    const val NSFW_LORA_FILE = "zimage_nsfw_v1.safetensors"

    /**
     * Odvázaný finetune místo základního Turbo: Z-Image Turbo NSFW
     * Photorealistic v6.1 (Q8 GGUF, jediná volně šiřitelná podoba — novější
     * verze si autor zamyká za Buzz). Autor doporučuje 12 kroků, cfg 1
     * a dpmpp_sde; LoRA s ním není potřeba. GGUF se načítá uzlem
     * UnetLoaderGGUF (na serveru je, pack ComfyUI-GGUF).
     */
    const val NSFW_MODEL_FILE = "zimage_nsfw_photoreal_v61_Q8.gguf"
    const val NSFW_MODEL_SAMPLER = "dpmpp_sde"
    const val NSFW_MODEL_STEPS = 12

    /** Z-Image Base — nedestilovaná varianta téhož modelu, na serveru vedle Turba. */
    const val BASE_MODEL_FILE = "z_image_bf16.safetensors"

    /**
     * Base není destilovaný, takže cfg 1 (co má Turbo) nevede vůbec. Autoři
     * i komunitní měření se drží 3–5; 4 je střed, který nepřepaluje kontrast.
     */
    const val BASE_CFG = 4.0

    /** Kroky z předlohy — ukazatel průběhu na ně přepočítává hlášení serveru. */
    const val STEPS = 8

    /** Uzly společné šablonám FLUX.2 (Klein i ERNIE) — čísla drží obě předlohy. */
    const val N_F2_UNET = "1"
    const val N_F2_CLIP = "2"
    const val N_F2_VAE = "3"
    const val N_F2_LATENT = "10"
    const val N_F2_TEXT = "30"
    const val N_F2_ZERO = "32"

    /**
     * Uzel, který drží kroky. U Kleina je to `Flux2Scheduler` (a bere i rozměry),
     * u ERNIE obyčejný `KSampler` (a bere i seed). Číslo je v obou předlohách
     * schválně stejné, ať se nemusí větvit i adresa.
     */
    const val N_F2_KROKY = "40"
    const val N_F2_NOISE = "42"
    const val N_F2_SAMPLER = "44"
    const val N_F2_SAVE = "60"

    /** Kroky podle zvoleného modelu (ukazatel průběhu s nimi musí souhlasit). */
    fun stepsFor(model: String): Int = T2iModel.zId(model).kroky

    private val cached = HashMap<T2iModel, String>()

    /** Který soubor předlohy patří modelu. */
    private fun rawFor(m: T2iModel): Int = when (m) {
        T2iModel.KLEIN -> R.raw.workflow_flux2_klein_t2i
        T2iModel.ERNIE -> R.raw.workflow_ernie_t2i
        else -> R.raw.workflow_zimage_t2i
    }

    private fun template(ctx: Context, m: T2iModel): String = cached[m] ?: ctx.resources
        .openRawResource(rawFor(m))
        .bufferedReader().use { it.readText() }.also { cached[m] = it }

    /**
     * Rozměry pro poměr stran: kolem 1 MPx (na tom Z-Image Turbo vznikl)
     * a násobky 16, které chce SD3 latent. FLUX.2 latent dělí šestnácti taky,
     * takže tabulka platí pro všechny modely karty.
     */
    fun sizeFor(aspect: Aspect): Pair<Int, Int> = when (aspect) {
        Aspect.SQUARE_1_1 -> 1024 to 1024
        Aspect.LANDSCAPE_16_9 -> 1344 to 768
        Aspect.PORTRAIT_9_16 -> 768 to 1344
        Aspect.LANDSCAPE_4_3 -> 1152 to 864
        Aspect.PORTRAIT_3_4 -> 864 to 1152
        Aspect.LANDSCAPE_3_2 -> 1248 to 832
        Aspect.PORTRAIT_2_3 -> 832 to 1248
        Aspect.ULTRAWIDE_21_9 -> 1568 to 672
    }

    fun build(
        ctx: Context, prompt: String, aspect: Aspect, seed: Long,
        nsfwLora: Boolean = false, nsfwSila: Float = 1f, model: String = "",
        loraFile: String = NSFW_LORA_FILE,
    ): JSONObject = build(
        template(ctx, T2iModel.zId(model)),
        prompt, aspect, seed, nsfwLora, nsfwSila, model, loraFile,
    )

    /** Stejné sestavení z textu předlohy, ať jde graf ověřit testem bez Androidu. */
    fun build(
        template: String, prompt: String, aspect: Aspect, seed: Long,
        nsfwLora: Boolean = false, nsfwSila: Float = 1f, model: String = "",
        loraFile: String = NSFW_LORA_FILE,
    ): JSONObject {
        val m = T2iModel.zId(model)
        val wf = JSONObject(template)
        val (w, h) = sizeFor(aspect)
        return if (m.zRodinyZImage) {
            buildZImage(wf, m, prompt, w, h, seed, nsfwLora, nsfwSila, loraFile)
        } else {
            buildFlux2(wf, m, prompt, w, h, seed)
        }
    }

    /** Šablona Z-Image: Turbo 1:1, Photoreal a Base jen s výměnou modelu a vzorkování. */
    private fun buildZImage(
        wf: JSONObject, m: T2iModel, prompt: String, w: Int, h: Int, seed: Long,
        nsfwLora: Boolean, nsfwSila: Float, loraFile: String,
    ): JSONObject {
        when (m) {
            // GGUF potřebuje jiný loader — UNETLoader umí jen safetensors.
            T2iModel.PHOTOREAL -> {
                wf.put(
                    N_UNET,
                    JSONObject()
                        .put("class_type", "UnetLoaderGGUF")
                        .put("inputs", JSONObject().put("unet_name", NSFW_MODEL_FILE))
                        .put("_meta", JSONObject().put("title", "Odvázaný model (GGUF)")),
                )
                wf.inputs(N_SAMPLER).put("steps", m.kroky)
                wf.inputs(N_SAMPLER).put("sampler_name", NSFW_MODEL_SAMPLER)
            }
            // Base jede na stejném grafu, jen s vlastním modelem, víc kroky
            // a skutečným cfg — sampler i shift zůstávají z předlohy.
            T2iModel.BASE -> {
                wf.inputs(N_UNET).put("unet_name", BASE_MODEL_FILE)
                wf.inputs(N_SAMPLER).put("steps", m.kroky)
                wf.inputs(N_SAMPLER).put("cfg", BASE_CFG)
            }
            // Turbo: předloha se nemění ani o bajt.
            else -> Unit
        }
        wf.inputs(N_TEXT).put("text", prompt)
        wf.inputs(N_LATENT).put("width", w)
        wf.inputs(N_LATENT).put("height", h)
        wf.inputs(N_SAMPLER).put("seed", seed)
        // Odvázaný režim: LoraLoaderModelOnly mezi UNETLoader a sigma shift.
        // Se zhasnutým přepínačem se graf šablony nemění ani o bajt.
        if (nsfwLora) {
            wf.put(
                N_NSFW_LORA,
                JSONObject()
                    .put("class_type", "LoraLoaderModelOnly")
                    .put(
                        "inputs",
                        JSONObject()
                            .put("model", org.json.JSONArray().put(N_UNET).put(0))
                            .put("lora_name", loraFile)
                            .put("strength_model", nsfwSila.toDouble()),
                    )
                    .put("_meta", JSONObject().put("title", "Odvázaná LoRA")),
            )
            wf.inputs(N_SHIFT).put("model", org.json.JSONArray().put(N_NSFW_LORA).put(0))
        }
        return wf
    }

    /**
     * Šablony rodiny FLUX.2 (Klein 9B, ERNIE). Dosazuje se zadání, rozměry
     * a seed — kroky, cfg i vzorkovač zůstávají z předlohy. Klein vzorkuje
     * přes SamplerCustomAdvanced (seed nese RandomNoise, rozměry i plán kroků
     * Flux2Scheduler), ERNIE obyčejným KSamplerem.
     */
    private fun buildFlux2(
        wf: JSONObject, m: T2iModel, prompt: String, w: Int, h: Int, seed: Long,
    ): JSONObject {
        wf.inputs(N_F2_TEXT).put("text", prompt)
        wf.inputs(N_F2_LATENT).put("width", w)
        wf.inputs(N_F2_LATENT).put("height", h)
        if (m == T2iModel.KLEIN) {
            // Plán kroků si sám počítá délku sekvence z rozměrů — musí sedět
            // s latentem, jinak by sigmy patřily jinému obrázku.
            wf.inputs(N_F2_KROKY).put("width", w)
            wf.inputs(N_F2_KROKY).put("height", h)
            wf.inputs(N_F2_NOISE).put("noise_seed", seed and 0xFFFF_FFFFL)
        } else {
            wf.inputs(N_F2_KROKY).put("seed", seed)
        }
        return wf
    }

    private fun JSONObject.inputs(node: String): JSONObject =
        getJSONObject(node).getJSONObject("inputs")

    fun stageForClass(cls: String?): Stage = when (cls) {
        "UNETLoader", "UnetLoaderGGUF", "CLIPLoader", "VAELoader",
        "ModelSamplingAuraFlow", "LoraLoaderModelOnly" -> Stage.MODELS
        "CLIPTextEncode", "ConditioningZeroOut", "EmptySD3LatentImage",
        "EmptyFlux2LatentImage", "Flux2Scheduler", "KSamplerSelect", "RandomNoise",
        "CFGGuider" -> Stage.ENCODING
        "KSampler", "SamplerCustomAdvanced" -> Stage.SAMPLING
        "VAEDecode", "SaveImage" -> Stage.MUXING
        else -> Stage.SAMPLING
    }

    fun rangeForClass(cls: String?): Pair<Float, Float> = when (stageForClass(cls)) {
        Stage.MODELS -> 0.00f to 0.10f
        Stage.ENCODING -> 0.10f to 0.14f
        Stage.SAMPLING -> 0.14f to 0.84f
        Stage.MUXING -> 0.84f to 0.90f
        else -> 0.14f to 0.84f
    }

    fun reportsSteps(cls: String?): Boolean =
        cls == "KSampler" || cls == "SamplerCustomAdvanced"

    fun nodeClasses(wf: JSONObject): Map<String, String> =
        wf.keys().asSequence().mapNotNull { id ->
            wf.optJSONObject(id)?.optString("class_type")?.takeIf { it.isNotEmpty() }?.let { id to it }
        }.toMap()
}
