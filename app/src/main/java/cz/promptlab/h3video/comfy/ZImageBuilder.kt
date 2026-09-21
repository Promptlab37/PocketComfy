package cz.promptlab.h3video.comfy

import android.content.Context
import cz.promptlab.h3video.R
import cz.promptlab.h3video.data.Aspect
import cz.promptlab.h3video.data.EditLora
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
    /**
     * Vedení promptem. **Na cfg 1 model prompt nevede vůbec** — destilované
     * Turbo jede bez classifier-free guidance, takže pokyny na pózu nebo
     * kompozici si model vyloží po svém. Nad 1 se začne uplatňovat i negativ,
     * proto se k tomu do grafu přidá skutečný prázdný negativ.
     */
    val cfg: Float,
    /**
     * Soubor, který se pro tuhle volbu dosadí do grafu. Karta ho ukazuje,
     * aby bylo vidět, na čem se doopravdy generuje — a aby si člověk všiml,
     * že se dá vyměnit. U [VLASTNI] žádný není, ten si vybírá uživatel.
     */
    val soubor: String = "",
) {
    TURBO(
        "turbo", "Z-Image Turbo",
        "Nejrychlejší. Fotorealismus za pár sekund, na text v obraze slabší.",
        8,
        1f,
        "z_image_turbo_bf16.safetensors",
    ),
    PHOTOREAL(
        "photoreal", "Photoreal (odvázaný)",
        "NSFW Photorealistic v6.1 — nic neodmítá. LoRA s ním není potřeba.",
        20,
        2f,
        ZImageBuilder.NSFW_MODEL_FILE,
    ),
    BASE(
        "base", "Z-Image Base",
        "Nedestilovaný základ. Poslouchá zadání líp než Turbo, ale trvá to násobně dýl.",
        // 25 kroků má oficiální předloha ComfyUI pro Base. Dřív tu bylo 30 —
        // vlastní číslo bez opory, jen pomalejší.
        25,
        ZImageBuilder.BASE_CFG.toFloat(),
        ZImageBuilder.BASE_MODEL_FILE,
    ),
    KLEIN(
        "klein", "FLUX.2 Klein 9B",
        "Nejlíp drží složité zadání a text v obraze. Velký model, načítá se dýl.",
        4,
        1f,
        "flux-2-klein-9b.safetensors",
    ),
    ERNIE(
        "ernie", "ERNIE Image Turbo",
        "Baidu ERNIE na architektuře FLUX.2. Jiný rukopis než Z-Image.",
        9,
        1f,
        "ernie-image-turbo-Q8_0.gguf",
    ),
    QWEN21(
        "qwen21", "Qwen Image 2.1",
        "Tentýž model, co upravuje fotky. Zvládá text v obraze a průhledné pozadí.",
        // 25 kroků a cfg 1 má oficiální předloha ComfyUI pro Qwen 2.1.
        25,
        1f,
        "qwen_image_2.1_int8_convrot.safetensors",
    ),

    /**
     * Cokoli dalšího z rodiny Z-Image, co je na serveru — finetune z CivitAI,
     * vlastní trénink, jiná kvantizace. Soubor, kroky i cfg si volí uživatel,
     * zbytek grafu zůstává z předlohy Turba.
     *
     * Kroky tady nic neznamenají: [ZImageBuilder.stepsFor] si je u téhle volby
     * bere z nastavení. Osmička je jen výchozí stav nového výběru.
     */
    VLASTNI(
        "vlastni", "Vlastní model",
        "Jiný Z-Image model ze serveru. Vybereš soubor a řekneš kroky a cfg.",
        8,
        1f,
    );

    /** Jede na šabloně Z-Image (a smí se k němu tedy přimíchat zimage LoRA)? */
    val zRodinyZImage: Boolean
        get() = this == TURBO || this == PHOTOREAL || this == BASE || this == VLASTNI

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

    /**
     * Skutečný prázdný negativ pro Base.
     *
     * Šablona (Turbo) posílá do negativu `ConditioningZeroOut`, tedy vynulovaný
     * tenzor. U Turba je to jedno — jede na cfg 1, kde se negativ vůbec
     * nepoužije. Base ale jede na cfg 4 a tam vzorkovač počítá
     * `neg + cfg * (pos − neg)`: nula není totéž co „prázdné zadání" a výsledek
     * je přepálený a míň soudržný. Oficiální předloha ComfyUI pro Base
     * (`image_z_image.json`) má proto v negativu obyčejný CLIPTextEncode
     * s prázdným textem — přesně tohle.
     */
    const val N_NEG_BASE = "34"
    const val N_LATENT = "13"
    const val N_SHIFT = "11"
    const val N_SAMPLER = "3"
    const val N_DECODE = "8"
    const val N_SAVE = "9"

    /** Odvázaná LoRA: uzel se do grafu vkládá jen se zapnutým přepínačem. */
    const val N_NSFW_LORA = "90"

    /**
     * Druhá LoRA, nepovinná. Model jimi projde za sebou (90 → 91), takže jde
     * spojit třeba anatomii s kůží — na to jedna LoRA nestačí a míchat je
     * v jednom uzlu nejde, `LoraLoaderModelOnly` bere právě jednu.
     */
    const val N_NSFW_LORA2 = "91"
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

    /** Z-Image Base — nedestilovaná varianta téhož modelu, na serveru vedle Turba. */
    const val BASE_MODEL_FILE = "z_image_bf16.safetensors"

    /**
     * Vlastní model: co uživatel vybral ze serveru a s jakým vzorkováním.
     *
     * Kroky a cfg se ptát musíme — z názvu souboru se nepoznají. Destilované
     * finetuny Turba jedou na cfg 1 a 8–12 krocích, nedestilované potřebují
     * cfg kolem čtyř a kroků násobně víc; špatná dvojice nedá chybu, jen
     * ošklivý obrázek.
     */
    data class Vlastni(val soubor: String, val kroky: Int = VLASTNI_KROKY, val cfg: Float = 1f)

    const val VLASTNI_KROKY = 8
    const val VLASTNI_CFG = 1f

    /** Meze posuvníků u vlastního modelu — širší už nedává smysl ani u Base. */
    const val VLASTNI_KROKY_MIN = 1
    const val VLASTNI_KROKY_MAX = 50
    const val VLASTNI_CFG_MAX = 10f

    /** GGUF umí načíst jen uzel z balíku ComfyUI-GGUF, UNETLoader ne. */
    fun jeGguf(soubor: String): Boolean = soubor.endsWith(".gguf", ignoreCase = true)

    /**
     * Vlastní model z nastavení — jedno místo pro stavitele grafu i pro
     * ukazatel průběhu, ať se nerozejdou v tom, kolik kroků běh má.
     * Null znamená „tahle volba se teď nepoužívá", včetně stavu, kdy je
     * vybraná, ale soubor ještě žádný.
     */
    fun vlastniZ(p: cz.promptlab.h3video.data.GenParams): Vlastni? =
        if (T2iModel.zId(p.zimageModel) == T2iModel.VLASTNI && p.zimageVlastniModel.isNotBlank())
            Vlastni(p.zimageVlastniModel, p.zimageVlastniKroky, p.zimageVlastniCfg)
        else null

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

    /**
     * Uzly šablony Qwen Image 2.1 pro text→obrázek
     * (`res/raw/workflow_qwen21_t2i.json`).
     *
     * Je to **tentýž model**, který jede na kartě Úprava obrázku — Qwen 2.1
     * umí generovat i upravovat jedním modelem. Liší se jen zapojení: tady
     * jde do vzorkování prázdné plátno [N_Q21_LATENT], při úpravě latent
     * z předlohy. Stejně je na tom FLUX.2 Klein, který je na obou kartách
     * taky.
     */
    const val N_Q21_UNET = "1"
    const val N_Q21_TEXT = "10"
    const val N_Q21_LATENT = "15"
    const val N_Q21_SAMPLER = "20"

    /**
     * Kroky a cfg, se kterými se běh opravdu pošle.
     *
     * Uživatelovo nastavení má přednost před výchozím stavem modelu; nula
     * v nastavení znamená „nech výchozí". Jedno místo pro stavitele grafu
     * i pro ukazatel průběhu, ať se nerozejdou v tom, kolik kroků běh má.
     */
    fun vzorkovani(p: cz.promptlab.h3video.data.GenParams): Pair<Int, Float> {
        val m = T2iModel.zId(p.zimageModel)
        val kroky = if (p.zimageKroky > 0) p.zimageKroky else m.kroky
        val cfg = if (p.zimageCfg > 0f) p.zimageCfg else m.cfg
        return kroky to cfg
    }

    /** Kroky podle zvoleného modelu (ukazatel průběhu s nimi musí souhlasit). */
    fun stepsFor(model: String, vlastni: Vlastni? = null): Int {
        val m = T2iModel.zId(model)
        return if (m == T2iModel.VLASTNI && vlastni != null) vlastni.kroky else m.kroky
    }

    private val cached = HashMap<T2iModel, String>()

    /** Který soubor předlohy patří modelu. */
    private fun rawFor(m: T2iModel): Int = when (m) {
        T2iModel.KLEIN -> R.raw.workflow_flux2_klein_t2i
        T2iModel.ERNIE -> R.raw.workflow_ernie_t2i
        T2iModel.QWEN21 -> R.raw.workflow_qwen21_t2i
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
        loraFile2: String = "", nsfwSila2: Float = 1f,
        userLoras: List<EditLora> = emptyList(),
        vlastni: Vlastni? = null,
        kroky: Int = 0,
        cfg: Float = 0f,
    ): JSONObject = build(
        template(ctx, T2iModel.zId(model)),
        prompt, aspect, seed, nsfwLora, nsfwSila, model, loraFile, loraFile2, nsfwSila2,
        userLoras, vlastni, kroky, cfg,
    )

    /** Stejné sestavení z textu předlohy, ať jde graf ověřit testem bez Androidu. */
    fun build(
        template: String, prompt: String, aspect: Aspect, seed: Long,
        nsfwLora: Boolean = false, nsfwSila: Float = 1f, model: String = "",
        loraFile: String = NSFW_LORA_FILE,
        loraFile2: String = "", nsfwSila2: Float = 1f,
        userLoras: List<EditLora> = emptyList(),
        vlastni: Vlastni? = null,
        kroky: Int = 0,
        cfg: Float = 0f,
    ): JSONObject {
        val m = T2iModel.zId(model)
        val wf = JSONObject(template)
        val (w, h) = sizeFor(aspect)
        if (m.zRodinyZImage) {
            buildZImage(
                wf, m, prompt, w, h, seed, nsfwLora, nsfwSila, loraFile, loraFile2, nsfwSila2,
                vlastni, kroky, cfg,
            )
        } else if (m == T2iModel.QWEN21) {
            buildQwen21(wf, prompt, w, h, seed, kroky, cfg)
        } else {
            buildFlux2(wf, m, prompt, w, h, seed)
        }
        addUserLoras(wf, userLoras)
        return wf
    }

    private fun addUserLoras(wf: JSONObject, loras: List<EditLora>) {
        val active = loras.filter { it.name.isNotBlank() && it.strength.isFinite() && it.strength > 0f }
            .distinctBy { it.name }.take(2)
        if (active.isEmpty()) return
        val originalNodes = wf.keys().asSequence().toList()
        val loader = originalNodes.single { wf.getJSONObject(it).optString("class_type") in
            listOf("UNETLoader", "UnetLoaderGGUF") }
        var upstream = loader
        var next = 800
        for (lora in active) {
            while (wf.has(next.toString())) next++
            val id = (next++).toString()
            wf.put(id, JSONObject().put("class_type", "LoraLoaderModelOnly").put("inputs", JSONObject()
                .put("model", org.json.JSONArray().put(upstream).put(0))
                .put("lora_name", lora.name).put("strength_model", lora.strength.coerceIn(0f, 2f).toDouble())))
            upstream = id
        }
        // Route every original consumer through the chain; never change CLIP or VAE links.
        for (id in originalNodes) {
            val inputs = wf.getJSONObject(id).optJSONObject("inputs") ?: continue
            val link = inputs.optJSONArray("model") ?: continue
            if (link.optString(0) == loader && link.optInt(1) == 0)
                inputs.put("model", org.json.JSONArray().put(upstream).put(0))
        }
    }

    /**
     * Skutečný prázdný negativ místo vynulovaného tenzoru — viz [N_NEG_BASE].
     * Potřebuje ho každý běh se cfg nad jedničkou, ne jen Base.
     */
    private fun skutecnyNegativ(wf: JSONObject) {
        wf.put(
            N_NEG_BASE,
            JSONObject()
                .put("class_type", "CLIPTextEncode")
                .put(
                    "inputs",
                    JSONObject()
                        .put("clip", org.json.JSONArray().put(N_CLIP).put(0))
                        .put("text", ""),
                )
                .put("_meta", JSONObject().put("title", "Prázdný negativ")),
        )
        wf.inputs(N_SAMPLER).put("negative", org.json.JSONArray().put(N_NEG_BASE).put(0))
    }

    /**
     * Šablona Z-Image pro celou rodinu — Turbo, Photoreal, Base i vlastní model.
     *
     * Mění se jen soubor modelu, loader (GGUF chce vlastní) a **vzorkování**.
     * Kroky a cfg se dosazují vždy, na jednom místě: předloha má Turbo hodnoty
     * a na cfg 1 model prompt vůbec nevede, takže pokyny na pózu si vykládá
     * po svém. Nad cfg 1 se začne uplatňovat negativ a musí být skutečný,
     * ne vynulovaný tenzor — viz [skutecnyNegativ].
     */
    private fun buildZImage(
        wf: JSONObject, m: T2iModel, prompt: String, w: Int, h: Int, seed: Long,
        nsfwLora: Boolean, nsfwSila: Float, loraFile: String,
        loraFile2: String = "", nsfwSila2: Float = 1f,
        vlastni: Vlastni? = null,
        kroky: Int = 0,
        cfg: Float = 0f,
    ): JSONObject {
        // Poradi prednosti: co rekl volajici > co si nese vlastni model
        // > vychozi hodnota volby z vyctu.
        val ucinneKroky = when {
            kroky > 0 -> kroky
            m == T2iModel.VLASTNI && vlastni != null -> vlastni.kroky
            else -> m.kroky
        }
        val ucinneCfg = when {
            cfg > 0f -> cfg
            m == T2iModel.VLASTNI && vlastni != null -> vlastni.cfg
            else -> m.cfg
        }

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
                wf.inputs(N_SAMPLER).put("sampler_name", NSFW_MODEL_SAMPLER)
            }
            // Base jede na stejném grafu, jen s vlastním modelem — sampler
            // i shift zůstávají z předlohy.
            T2iModel.BASE -> wf.inputs(N_UNET).put("unet_name", BASE_MODEL_FILE)
            // Vlastní model ze serveru: mění se jen loader, soubor a vzorkování.
            // Když uživatel nic nevybral, zůstane předloha Turba — prázdné jméno
            // by ComfyUI odmítlo a spadlo by to až na serveru.
            T2iModel.VLASTNI -> if (vlastni != null && vlastni.soubor.isNotBlank()) {
                if (jeGguf(vlastni.soubor)) {
                    wf.put(
                        N_UNET,
                        JSONObject()
                            .put("class_type", "UnetLoaderGGUF")
                            .put("inputs", JSONObject().put("unet_name", vlastni.soubor))
                            .put("_meta", JSONObject().put("title", "Vlastní model (GGUF)")),
                    )
                } else {
                    wf.inputs(N_UNET).put("unet_name", vlastni.soubor)
                }
            }
            // Turbo: soubor modelu z předlohy zůstává.
            else -> Unit
        }

        wf.inputs(N_SAMPLER).put("steps", ucinneKroky)
        wf.inputs(N_SAMPLER).put("cfg", ucinneCfg.toDouble())
        if (ucinneCfg > 1f) skutecnyNegativ(wf)

        wf.inputs(N_TEXT).put("text", prompt)
        wf.inputs(N_LATENT).put("width", w)
        wf.inputs(N_LATENT).put("height", h)
        wf.inputs(N_SAMPLER).put("seed", seed)
        // Odvázaný režim: LoraLoaderModelOnly mezi UNETLoader a sigma shift.
        // Se zhasnutým přepínačem se graf šablony nemění ani o bajt.
        if (nsfwLora) {
            fun uzel(id: String, zdroj: String, soubor: String, sila: Float, titulek: String) {
                wf.put(
                    id,
                    JSONObject()
                        .put("class_type", "LoraLoaderModelOnly")
                        .put(
                            "inputs",
                            JSONObject()
                                .put("model", org.json.JSONArray().put(zdroj).put(0))
                                .put("lora_name", soubor)
                                .put("strength_model", sila.toDouble()),
                        )
                        .put("_meta", JSONObject().put("title", titulek)),
                )
            }
            uzel(N_NSFW_LORA, N_UNET, loraFile, nsfwSila, "Odvázaná LoRA")
            // Druhá LoRA visí za první, ne vedle ní — model prochází řetězem.
            // Stejná LoRA dvakrát by jen zdvojila sílu, proto se přeskočí.
            val druha = loraFile2.isNotBlank() && loraFile2 != loraFile
            if (druha) uzel(N_NSFW_LORA2, N_NSFW_LORA, loraFile2, nsfwSila2, "Druhá LoRA")
            wf.inputs(N_SHIFT).put(
                "model",
                org.json.JSONArray().put(if (druha) N_NSFW_LORA2 else N_NSFW_LORA).put(0),
            )
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

    /**
     * Qwen Image 2.1 pro text→obrázek.
     *
     * Bez předlohy nemá `TextEncodeQwenImage21` z čeho odvodit velikost, proto
     * rozměr určuje prázdné plátno — přesně jako v oficiální předloze ComfyUI.
     * Negativ zůstává prázdný: předloha jede na cfg 1, kde se neuplatní.
     */
    private fun buildQwen21(
        wf: JSONObject, prompt: String, w: Int, h: Int, seed: Long, kroky: Int, cfg: Float,
    ): JSONObject {
        wf.inputs(N_Q21_TEXT).put("prompt", prompt)
        wf.inputs(N_Q21_LATENT).put("width", w)
        wf.inputs(N_Q21_LATENT).put("height", h)
        val s = wf.inputs(N_Q21_SAMPLER)
        s.put("seed", seed)
        if (kroky > 0) s.put("steps", kroky)
        if (cfg > 0f) s.put("cfg", cfg.toDouble())
        return wf
    }

    private fun JSONObject.inputs(node: String): JSONObject =
        getJSONObject(node).getJSONObject("inputs")

    fun stageForClass(cls: String?): Stage = when (cls) {
        "UNETLoader", "UnetLoaderGGUF", "CLIPLoader", "VAELoader",
        "ModelSamplingAuraFlow", "LoraLoaderModelOnly" -> Stage.MODELS
        "CLIPTextEncode", "ConditioningZeroOut", "EmptySD3LatentImage",
        "EmptyFlux2LatentImage", "Flux2Scheduler", "KSamplerSelect", "RandomNoise",
        "CFGGuider", "TextEncodeQwenImage21", "EmptyLatentImage",
        "QwenImage21Cache" -> Stage.ENCODING
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
