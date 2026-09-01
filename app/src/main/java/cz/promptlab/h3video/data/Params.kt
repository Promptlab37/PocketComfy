package cz.promptlab.h3video.data

import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Parametry generování – společné nastavení pro kartu All in One.
 *
 * Zadání (co se má dít, jaké obrázky, jak dlouho) si drží [AioScene]; tady je
 * to, co platí napříč režimy karty: rozlišení, vzorkování, model a LoRA.
 */
data class GenParams(
    val mode: Mode = Mode.ALLINONE,
    val profile: Profile = Profile.TURBO,
    /** Soubor Turbo LoRA. Autoři jich vydávají víc a liší se, proto volitelný. */
    val turboLora: String = TURBO.LORA_FILE,
    /** Turbo LoRA zapnutá? V profilu Kvalita se vypíná (model jede „na plno"). */
    val turboLoraOn: Boolean = true,
    val turboLoraStrength: Float = TURBO.STRENGTH.toFloat(),
    val prompt: String = "",
    val aspect: Aspect = Aspect.LANDSCAPE_16_9,
    val megapixels: Float = 0.4f,
    /** Jak velké se posílají reference: `match` = na velikost výstupu, `max` = 2048 px. */
    val refImageSize: String = "match",
    val steps: Int = 8,
    val sampler: String = "euler",
    val scheduler: String = "beta",
    val seed: Long = 0L,
    val randomSeed: Boolean = true,
    val shiftVideo: Float = 12.191111f,
    val shiftAudio: Float = 3f,
    val sageAttention: Boolean = true,
    /** Spectrum – přibližný akcelerátor v ULTRA workflow; týká se jen Časové osy. */
    val spectrum: Boolean = true,
    /** Komprese hotového souboru, ne kvalita generování. */
    val crf: Int = 19,
    /** Textový enkodér. Liší se mezi profily, viz [Profile]. */
    val clipName: String = CLIP_NVFP4,
    /** Živý náhled během vzorkování – posílá ho `ModelPreviewOverrideKJ`. */
    val livePreview: Boolean = true,
    /**
     * TeaCache pro H3 (Icyoung port): přeskakuje kroky, jejichž vstup se skoro
     * nezměnil — až ~3× rychlejší za cenu drobného driftu. Vypnuté schválně;
     * zapíná se v Pokročilém. Přidává do grafu uzel MiniMaxH3TeaCache.
     */
    val teaCache: Boolean = false,
    /** Další LoRA nad rámec Turbo. */
    val extraLoras: List<LoraEntry> = emptyList(),
    /**
     * Časová osa: jméno projektu v cache uzlu. Hotové segmenty se pod ním drží
     * mezi běhy, takže úprava jednoho záběru neznamená počítat celé video znovu.
     */
    val timelineProject: String = "h3app",
    /** Časová osa: přegenerovat jen tento segment (1 = první), 0 = celou osu. */
    val timelineOnlySegment: Int = 0,
    /** Časová osa: délka jednoho segmentu v sekundách. */
    val seconds: Int = 8,
    /**
     * Vlastní model místo toho ze šablony. Prázdné = model ze šablony balíku.
     * Týká se jen nereferenční cesty; reference mají vlastní váhy (ref2va).
     */
    val unetFl2va: String = "",
) {
    val resolution: Resolution get() = Resolution.of(aspect, megapixels)
    val frames: Int get() = framesForSeconds(seconds)
    val realSeconds: Float get() = frames / 24f

    /** Plátno, na kterém model vznikl – pro zvolený poměr stran. */
    val nativeResolution: Resolution get() = nativeCanvas(aspect)

    val isNativeResolution: Boolean get() = resolution == nativeResolution

    /**
     * Znatelně nad plátnem modelu? Rozhoduje PLOCHA, ne kratší hrana.
     *
     * Třetina navíc se toleruje schválně: 832×1120 proti nativním 768×1024 je
     * osm procent na hraně a na výsledku se to neprojeví. Hlásit i tohle
     * znamenalo, že se oranžová hláška ukazovala skoro pořád a přestala něco
     * znamenat. Upozornění tak zbyde na případy, kde už model opravdu dostává
     * plátno, jaké nikdy neviděl.
     */
    val aboveNative: Boolean get() = resolution.pixels > nativeResolution.pixels * 1.35

    /** O kolik procent je zvolená plocha nad plátnem modelu (0 = na něm nebo pod ním). */
    val nativeOverhead: Int
        get() = maxOf(
            0,
            ((resolution.pixels.toDouble() / nativeResolution.pixels - 1.0) * 100).toInt()
        )

    /**
     * Horní mez počtu obrázků. Skutečný počet si u všech tří karet řídí jejich
     * vlastní scéna (postavy dialogu, segmenty osy, reference All in One), takže
     * se seznam podle téhle hodnoty nikde nezkracuje.
     */
    val imageSlots: Int
        get() = when (mode) {
            Mode.TALK -> MAX_SPEAKERS
            Mode.TIMELINE -> TimelineScene.MAX_SEGMENTS
            Mode.ALLINONE -> AioScene.MAX_REFS
            // Úprava obrázku: upravovaná fotka a nepovinná vkládaná osoba.
            Mode.EDIT -> 2
            Mode.UPSCALE -> 1
            // Obrázek z textu ani hudba žádnou fotku neberou.
            Mode.IMAGE -> 0
            Mode.MUSIC -> 0
            Mode.RESTORE -> 1
            // Výměna tváře: cílová fotka s maskou a nová tvář.
            Mode.FACESWAP -> 2
        }

    /**
     * Scheduler pro referenční (ref2va) cestu. V2 Turbo má od autora u referencí
     * `simple` proti `beta` u textu a snímků – není to sjednocené schválně, je to
     * jeho ladění. Ruční změna v Pokročilém má ale přednost: jakmile se uživatelův
     * scheduler liší od výchozího schedulera profilu, posílá se ten jeho.
     */
    val schedulerForRefPath: String
        get() = if (scheduler == profile.scheduler && profile.schedulerRef.isNotBlank())
            profile.schedulerRef else scheduler
}

/** Poměry stran nabízené uzlem ResolutionSelector. */
enum class Aspect(val comfyValue: String, val label: String, val w: Int, val h: Int) {
    LANDSCAPE_16_9("16:9 (Widescreen)", "16:9", 16, 9),
    PORTRAIT_9_16("9:16 (Portrait Widescreen)", "9:16", 9, 16),
    SQUARE_1_1("1:1 (Square)", "1:1", 1, 1),
    LANDSCAPE_4_3("4:3 (Standard)", "4:3", 4, 3),
    PORTRAIT_3_4("3:4 (Portrait Standard)", "3:4", 3, 4),
    LANDSCAPE_3_2("3:2 (Photo)", "3:2", 3, 2),
    PORTRAIT_2_3("2:3 (Portrait Photo)", "2:3", 2, 3),
    ULTRAWIDE_21_9("21:9 (Ultrawide)", "21:9", 21, 9),
}

data class Resolution(val width: Int, val height: Int) {
    val label: String get() = "${width}×${height}"
    val shortEdge: Int get() = minOf(width, height)
    val pixels: Long get() = width.toLong() * height

    companion object {
        fun of(aspect: Aspect, megapixels: Float): Resolution {
            // Nativní plátno se trefí přesně, ne „skoro". Bez toho by u 21:9
            // vyšlo o jeden krok 32 px jinak než uzel a pilulka „nativní" by
            // ukazovala rozlišení, které nativní není.
            if (kotlin.math.abs(megapixels - nativeMegapixels(aspect)) < 0.004f) {
                return nativeCanvas(aspect)
            }
            val (w, h) = calc(aspect, megapixels)
            return Resolution(w, h)
        }

        /**
         * Doslova výpočet z comfy_extras/nodes_resolution.py při multiple = 32.
         * Reprodukuje tabulku: 0.4 MP / 16:9 → 864×480, 0.98 → 1344×768, 2.0 → 1920×1088.
         */
        fun calc(aspect: Aspect, megapixels: Float, multiple: Int = 32): Pair<Int, Int> {
            val total = megapixels.toDouble() * 1024.0 * 1024.0
            val scale = sqrt(total / (aspect.w * aspect.h))
            val w = (aspect.w * scale / multiple).roundToInt() * multiple
            val h = (aspect.h * scale / multiple).roundToInt() * multiple
            return w to h
        }
    }
}

/**
 * Plátno, na kterém model vznikl, pro daný poměr stran.
 *
 * Přesně tak, jak si ho spočítá uzel sám (`adapt_canvas` v
 * `comfy_extras/nodes_minimax_h3.py`): kratší hrana 768 px, plocha nejvýš
 * 768 × 1344, zaokrouhleno na násobek 32. U širokých poměrů proto strop plochy
 * kratší hranu ještě stáhne – u 21:9 vyjde 1536×672, ne 1792×768.
 *
 * Tohle je jediná poctivá referenční hodnota. Ploché pravidlo „kratší hrana
 * nad 768 px" dávalo u čtverce i u 21:9 jiný výsledek než uzel.
 */
fun nativeCanvas(aspect: Aspect): Resolution {
    val ratio = aspect.w.toDouble() / aspect.h
    var w: Double
    var h: Double
    if (ratio >= 1.0) {
        w = NATIVE_SHORT_EDGE * ratio; h = NATIVE_SHORT_EDGE.toDouble()
    } else {
        w = NATIVE_SHORT_EDGE.toDouble(); h = NATIVE_SHORT_EDGE / ratio
    }
    val cap = NATIVE_SHORT_EDGE.toDouble() * NATIVE_LONG_EDGE
    if (w * h > cap) {
        val s = sqrt(cap / (w * h))
        w *= s; h *= s
    }
    // Math.rint zaokrouhluje na sudou stejně jako Python round v uzlu, takže
    // u přesných půlek (21:9) vyjde stejné číslo, ne o 32 px vedle.
    fun snap(v: Double) = maxOf(32, (Math.rint(v / 32.0) * 32).toInt())
    return Resolution(snap(w), snap(h))
}

/** Nativní plátno vyjádřené v megapixelech – pilulky velikosti jedou v nich. */
fun nativeMegapixels(aspect: Aspect): Float =
    nativeCanvas(aspect).pixels / (1024f * 1024f)

/**
 * Nabídka velikostí pro daný poměr stran. Do pevné řady se přidá nativní
 * plátno, aby šlo vybrat i tam, kde v řadě není (u 3:4 leží mezi 736×992
 * a 800×1056, u čtverce mezi 736 a 800).
 */
fun sizeStepsFor(aspect: Aspect): List<Float> {
    val native = nativeMegapixels(aspect)
    val bezBlizkych = MEGAPIXEL_STEPS.filter { kotlin.math.abs(it - native) >= 0.04f }
    return (bezBlizkych + native).sorted()
}

/**
 * Přepočet sekund na snímky – výraz z uzlu „H3 VALID FRAME LENGTH":
 *   max(5, round(a * 24)) + (5 - (max(5, round(a * 24)) % 17)) % 17
 * ComfyUI ho počítá v Pythonu, kde je modulo „floored", proto floorMod.
 */
fun framesForSeconds(seconds: Number): Int {
    val n = maxOf(5, Math.round(seconds.toDouble() * 24.0).toInt())
    return n + Math.floorMod(5 - Math.floorMod(n, 17), 17)
}

const val MIN_SECONDS = 2
const val MAX_SECONDS = 15
const val TRAINED_MIN_FRAMES = 124

/**
 * Turbo LoRA z workflow. Zůstává výchozí – uživatelovo ULTRA workflow je na ní
 * vyladěné – ale od verze 2.12 se dá vypnout i vyměnit (autoři jich vydali víc
 * a novější mají jiný trénovací shift, viz [Profile]).
 */
/** Textové enkodéry MiniMaxu, které jsou na serveru k dispozici. */
const val CLIP_NVFP4 = "qwen3vl_32b_minimax_h3_nvfp4_awq.safetensors"
const val CLIP_INT8 = "qwen3vl_32b_minimax_h3_int8_convrot.safetensors"

/** Turbo LoRA, na které stojí profil V2 Turbo. */
const val TURBO_V4 = "minimax_h3_turbo_v4_step600_ema_pruned_comfyui.safetensors"

/**
 * lightx2v Turbo v1.0 — osmikroková destilace z 768p řady, na které stojí
 * profil [Profile.TURBO8]. Podle autorů výrazně lepší obraz i ZVUK než starší
 * čtyřkrokové LoRA. 768p řada je trénovaná na shift 6 (viz TURBO.KNOWN).
 */
const val TURBO8_LORA = "minimax_h3_fl2v_turbo_8step_v1.0_768p_comfyui_bf16.safetensors"

/**
 * FastH3 — čtyřkroková destilace MiniMaxu H3 od FastVideo, na které stojí
 * profil [Profile.FAST].
 *
 * Tohle je komunitní převod do ComfyUI (drozbay), ne původní model: ten má
 * 148 GB, běží na čtyřech B200 a do ComfyUI se nenačte vůbec. Převod je navíc
 * z verze 0.2, ne z v1, a autor sám měří, že plný checkpoint plně nenahrazuje.
 *
 * **Jede jen na nereferenční (fl2va) cestě.** FastVideo destiloval pouze
 * text→video; referenční student neexistuje, takže na ref2va vahách by tahle
 * LoRA výsledek jen kazila.
 */
const val FASTH3_LORA =
    "minimax_h3_fl2va_fasth3_preview_v0.2_lora_pruned_rank128_fp16.safetensors"

object TURBO {
    const val LORA_FILE = "minimax_h3_turbo_4step_ckpt500_pruned_comfyui.safetensors"
    const val STRENGTH = 1.0

    /**
     * Známé Turbo LoRA a jejich trénovací shift. Zdroje: model card
     * larryvrh/MiniMax-H3-Turbo-Lora a ModelTC/Minimax-H3-Turbo (lightx2v).
     * Shift se musí LoRA přizpůsobit – 768p verze je trénovaná na 6, ne na 12,
     * a s cizím shiftem dává znatelně horší výsledek.
     */
    val KNOWN = listOf(
        LoraProfile(LORA_FILE, "ckpt500 (vyladěné workflow)", steps = 8, shiftVideo = 12.191111f),
        LoraProfile(
            "minimax_h3_turbo_v4_step600_ema_pruned_comfyui.safetensors",
            "v4 step600 EMA (autor doporučuje)", steps = 8, shiftVideo = 12.191111f
        ),
        LoraProfile(
            "minimax_h3_fl2v_turbo_8step_v1.0_comfyui_bf16.safetensors",
            "lightx2v 8step v1.0 (544p)", steps = 8, shiftVideo = 12f
        ),
        LoraProfile(
            "minimax_h3_fl2v_turbo_4step_v1.0_768p_comfyui_bf16.safetensors",
            "lightx2v 4step v1.0 (768p)", steps = 4, shiftVideo = 6f
        ),
        LoraProfile(
            TURBO8_LORA,
            "lightx2v 8step v1.0 (768p) — nejlepší zvuk", steps = 8, shiftVideo = 6f
        ),
        LoraProfile(
            "minimax_h3_ref2v_turbo_4step_v0.1_comfyui_bf16.safetensors",
            "lightx2v ref2v 4step (pro reference)", steps = 4, shiftVideo = 12f
        ),
        LoraProfile(FASTH3_LORA, "FastH3 4step (bez referencí)", steps = 4, shiftVideo = 12f),
    )

    fun profileFor(file: String): LoraProfile? = KNOWN.firstOrNull { it.file == file }
}

/** Doporučené hodnoty ke konkrétní Turbo LoRA. */
data class LoraProfile(
    val file: String,
    val label: String,
    val steps: Int,
    val shiftVideo: Float,
)

/**
 * Turbo vs. plný model. Turbo drží uživatelovo vyladěné ULTRA workflow beze změny;
 * Kvalita jede podle oficiální šablony ComfyUI (`video_minimax_h3_i2v.json`), kde
 * není žádná LoRA ani Spectrum a samplerem je res_multistep + simple.
 *
 * Proč to vůbec je: Turbo LoRA je podle autorů i podle ComfyUI Wiki slabá právě
 * na zvuku („audio stream je slabé místo tohoto raného checkpointu") a stock
 * samplery při málo krocích audio rozbíjejí. Bez LoRA je hlas znatelně lepší,
 * zaplatí se to časem.
 */
enum class Profile(
    val title: String,
    val detail: String,
    val steps: Int,
    val sampler: String,
    val scheduler: String,
    val shiftVideo: Float,
    val spectrum: Boolean,
    val useLora: Boolean,
    /**
     * Textový enkodér. Verze 1 jede na `nvfp4_awq`, jak ji má vyladěné uživatelovo
     * ULTRA. V2 přešlo na `int8_convrot` — nvfp4 je formát s nativní podporou až
     * na Blackwellu (RTX 50xx), na Ada kartě (4060 Ti) se musí rozbalovat.
     */
    val clip: String = CLIP_NVFP4,
    /** LoRA, kterou profil zapíná. Prázdné = žádná. */
    val lora: String = "",
    /**
     * Scheduler pro referenční větev, když se liší. V Turbo V2 má autor u referencí
     * `simple` proti `beta` u textu a snímků — nesjednocuju to, je to jeho ladění.
     */
    val schedulerRef: String = "",
    /**
     * Profil jede jen na nereferenční (fl2va) cestě — tedy z textu, z obrázku,
     * klíčové snímky a Časová osa. Referenční cesta (Reference, Dialogy, List
     * postavy) má vlastní ref2va váhy, na které tenhle profil nesedí.
     */
    val bezReferenci: Boolean = false,
) {
    TURBO(
        title = "Turbo",
        detail = "Turbo LoRA, 8 kroků – rychlé",
        steps = 8, sampler = "euler", scheduler = "beta",
        shiftVideo = 12.191111f, spectrum = true, useLora = true,
    ),

    /**
     * Proti oficiální šabloně ComfyUI (res_multistep + simple, 20 kroků, bez
     * Spectrum) tu jsou tři vědomé odchylky a všechny mají důvod:
     *
     *  – 10 kroků místo 20: polovina času, kvalita podle provozního ověření drží;
     *  – euler + beta místo res_multistep + simple: res_multistep si drží víc
     *    mezivýsledků a na 16GB kartě, kde má model 19995 MB vah, to při větším
     *    rozlišení skončilo přetékáním paměti a běh se zastavil.
     *
     * Spectrum je vypnuté schválně. Je to přibližný akcelerátor – jeho vlastní
     * dokumentace říká „vypni ho, když je potřeba maximální věrnost původní
     * trajektorii MiniMax H3" – a v oficiální šabloně ComfyUI vůbec není.
     * U profilu, jehož smyslem je kvalita zvuku, tedy nemá co dělat.
     * Vypínač je na hlavní obrazovce, kdyby se hodila rychlost.
     */
    FULL(
        title = "Kvalita",
        detail = "Plný model bez LoRA, 10 kroků – lepší hlas",
        steps = 10, sampler = "euler", scheduler = "beta",
        shiftVideo = 12.191111f, spectrum = false, useLora = false,
    ),

    /**
     * ULTRA V2 Turbo – přesně podle `MINIMAX_H3_ULTRA_TURBO_WORKFLOW-V2.json`.
     *
     * Proti verzi 1 je jiná LoRA (v4 step600 EMA místo ckpt500), rovný shift 12
     * místo 12,191111, novější textový enkodér a u referencí `simple` scheduler.
     * Spectrum je v jeho V2 samostatná skupina „SPEEDUP", kterou má vypnutou –
     * proto je vypnuté i tady.
     */
    V2_TURBO(
        title = "V2 Turbo",
        detail = "Nová Turbo LoRA v4, 8 kroků – rychlé",
        steps = 8, sampler = "euler", scheduler = "beta",
        shiftVideo = 12f, spectrum = false, useLora = true,
        clip = CLIP_INT8,
        lora = TURBO_V4,
        schedulerRef = "simple",
    ),

    /**
     * lightx2v Turbo v1.0 — nová osmikroková destilace (768p řada, srpen 2026).
     * Podle autorů i komunity lepší obraz a hlavně ZVUK než čtyřkrokové LoRA.
     * Shift 6 podle trénovací hodnoty 768p řady (viz TURBO.KNOWN — cizí shift
     * kvalitu znatelně sráží). Jen nereferenční (fl2va) cesta.
     */
    TURBO8(
        title = "Turbo 8 v1",
        detail = "Nová lightx2v LoRA, 8 kroků – lepší zvuk",
        steps = 8, sampler = "euler", scheduler = "simple",
        shiftVideo = 6f, spectrum = false, useLora = true,
        clip = CLIP_INT8,
        lora = TURBO8_LORA,
        bezReferenci = true,
    ),

    /**
     * ULTRA V2 bez Turba – `MINIMAX_H3_ULTRA_WORKFLOW-V2_apka-normalWF.json`.
     *
     * Autor tam má 20 kroků; tady je jich 10. Důvod je měřený, ne odhadnutý:
     * `res_multistep` drží víc mezivýsledků a na 16GB kartě už jednou při větším
     * rozlišení přetekl (viz historie profilu Kvalita). Dvacet kroků by navíc
     * na jeden klip znamenalo přes dvacet minut.
     */
    V2_QUALITY(
        title = "V2 Kvalita",
        detail = "Bez LoRA, res_multistep, 10 kroků – nejvěrnější",
        steps = 10, sampler = "res_multistep", scheduler = "simple",
        shiftVideo = 12f, spectrum = false, useLora = false,
        clip = CLIP_INT8,
    ),

    /**
     * FastH3 — čtyřkroková destilace (FastVideo, DMD2). Nejrychlejší profil,
     * ale **jen tam, kde se nepoužívají reference**.
     *
     * Hodnoty jsou od autorů destilace, ne odhad: čtyři kroky na natrénovaném
     * žebříku, euler + simple, sigma shift 12/3, síla LoRA 1,0. Jiný počet
     * kroků je mimo trénink a kvalita spadne. Guidance model nepoužívá
     * (appka žádnou CFG neposílá), takže požadavek na CFG 1,0 je splněný sám.
     *
     * Pozor na očekávání: autoři píší, že „obtížný pohyb, jemný detail a část
     * zvuku můžou zůstat pod základním modelem". Není to zrychlení zadarmo.
     */
    FAST(
        title = "Fast",
        detail = "FastH3, 4 kroky – nejrychlejší, ale bez referencí",
        steps = 4, sampler = "euler", scheduler = "simple",
        shiftVideo = 12f, spectrum = false, useLora = true,
        clip = CLIP_INT8,
        lora = FASTH3_LORA,
        bezReferenci = true,
    );

    /** Hodnoty profilu dosazené do parametrů (prompt a vstupy zůstávají). */
    fun applyTo(p: GenParams): GenParams = p.copy(
        profile = this,
        steps = steps,
        sampler = sampler,
        scheduler = scheduler,
        shiftVideo = shiftVideo,
        spectrum = spectrum,
        turboLoraOn = useLora,
        // LoRA patří k profilu: V2 Turbo je vyladěné na v4 step600, verze 1 na
        // ckpt500. Přenést jednu do druhého profilu znamená jiný výsledek, než
        // jaký má autor odzkoušený.
        turboLora = lora.ifBlank { p.turboLora },
        clipName = clip,
    )
}

/**
 * Další LoRA přidaná uživatelem. Power Lora Loader bere každý vstup `lora_N`
 * s klíči `on`, `lora` a `strength`; vypnutá nebo s nulovou silou se přeskočí.
 */
@androidx.compose.runtime.Immutable
data class LoraEntry(
    val name: String,
    val enabled: Boolean = true,
    val strength: Float = 1f,
)

val SAMPLERS = listOf(
    "euler", "res_multistep", "res_multistep_ancestral", "euler_ancestral",
    "dpmpp_2m", "dpmpp_2m_sde", "dpmpp_3m_sde", "uni_pc", "ddim", "deis", "lcm"
)

val SCHEDULERS = listOf(
    "beta", "simple", "normal", "sgm_uniform", "karras", "exponential",
    "ddim_uniform", "linear_quadratic", "kl_optimal", "bong_tangent", "beta57"
)
