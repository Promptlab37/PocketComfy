package cz.promptlab.h3video.data

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Immutable
import java.io.File

/**
 * Co se právě dělá: začíná se nový řetěz, nebo se navazuje na hotový celek.
 */
enum class LongMmRezim(private val titleCs: String, private val popisCs: String) {
    PRVNI("První záběr", "Založí nový řetěz a uloží jeho latent"),
    NAVAZANI("Navázat", "Přidá další kus k hotovému videu");

    val title: String get() = t(titleCs)
    val popis: String get() = t(popisCs)
}

/**
 * Plátno záběru.
 *
 * **Jednou zvolené se v řetězu už nemění.** Latent se nedá přepočítat na jiné
 * rozměry, takže každé navázání musí jet na tom samém, na čem začal první
 * záběr. Karta to proto při navazování zamkne a jen ukáže, na čem řetěz jede.
 */
enum class LongMmRozliseni(val kod: String, private val titleCs: String) {
    R480("480P", "480p"),
    // Prostřední stupeň. Uzel nabízí i 540P, ale 640 je blíž půlce mezi
    // krajními volbami — jak kratší hranou (600), tak plochou.
    R640("640P", "640p"),
    R720("720P", "720p");

    val title: String get() = t(titleCs)

    /**
     * Kolikrát víc bodů než nejnižší stupeň. Uzel bere rozlišení jako **rozpočet
     * plochy**, ne jako pevný rozměr — „480P" znamená kratší hranu 480 a poměr
     * stran si tu plochu jen přerozdělí. Čas běhu roste zhruba s plochou.
     */
    val nasobekPlochy: Float get() = when (this) {
        R480 -> 1f
        R640 -> 1.78f
        R720 -> 2.25f
    }
}

/** Poměr stran. Plátno se z něj a z rozlišení dopočítá až v grafu. */
enum class LongMmPomer(val kod: String, private val titleCs: String) {
    NASIRKU("16:9", "Na šířku"),
    NAVYSKU("9:16", "Na výšku"),
    CTVEREC("1:1", "Čtverec");

    val title: String get() = t(titleCs)
}

/**
 * Sestava modelu a zrychlovací LoRA pro kartu Long MiniMax.
 *
 * Autorovo zapojení je [TURBO] a je výchozí. Zbylé dvě si vyžádal uživatel;
 * váhy k nim na serveru leží a LoRA sedí na architekturu H3 (50 bloků,
 * stejné šířky vrstev) — **puštěná ale ani jedna nebyla**.
 */
/**
 * Oddělovač podsložek tak, jak ho hlásí ComfyUI — **zpětné** lomítko.
 * S dopředným by uzel hodnotu odmítl jako mimo nabídku.
 */
private const val SLOZKA = '\\'

enum class LongMmModel(
    val unet: String,
    val lora: String,
    /** Síla LoRA u prvního záběru a u navázání — autor je má různé. */
    val silaPrvni: Float,
    val silaDalsi: Float,
    /** Výchozí počet kroků. Přenastavit jde posuvníkem. */
    val kroky: Int,
    /**
     * Kolik z těch kroků se počítá až ve zvoleném rozlišení. 0 = jeden
     * průchod. Kladné číslo zapne dvouprůchodové zapojení: zbytek kroků
     * proběhne dole, latent se zvětší a tady se dotáhne.
     */
    val krokyNahore: Int = 0,
    private val titleCs: String,
    private val popisCs: String,
) {
    TURBO(
        unet = "minimax_h3_fl2va_pruned_int8_convrot.safetensors",
        lora = "minimax_h3_ref2v_turbo_4step_v0.1_comfyui_bf16.safetensors",
        silaPrvni = 0.8f, silaDalsi = 0.75f, kroky = 7,
        titleCs = "Turbo",
        popisCs = "Zapojení autora balíku, beze změny",
    ),

    /**
     * Čtyřkrokový FastVideo s řídkou pozorností (VSA), k němu LoRA TaoMate.
     * Počet kroků je z názvu samotného modelu (`4step`).
     */
    FASTVIDEO(
        unet = "minimax_h3_fastvideo_vsa_datafree_1300step_4step_int8_convrot.safetensors",
        // ComfyUI hlásí podsložky se ZPĚTNÝM lomítkem; s dopředným by uzel
        // hodnotu odmítl jako mimo nabídku.
        lora = "h3" + SLOZKA + "TaoMate-H3-3step-ComfyUI.safetensors",
        silaPrvni = 1.0f, silaDalsi = 1.0f, kroky = 4,
        titleCs = "FastVideo VSA",
        popisCs = "Čtyřkrokový model, k němu LoRA TaoMate",
    ),

    /**
     * Eros Max s vlastní LoRA. **Žádná zrychlovací LoRA se k němu nepřidává** —
     * Eros LoRA je konceptová, ne turbo, takže málo kroků nemá co dohnat.
     * Proto deset kroků místo sedmi.
     *
     * Síla 0,5 je z popisu modelu na CivitAI: *„Other concept Loras will also
     * load on top more readily, usually needing lower strength 0.2-0.6."*
     * Eros LoRA na Eros checkpointu je přesně ten případ. Posuvníkem se dá
     * změnit; nula LoRA z grafu úplně vyřadí a jede jen model.
     */
    EROS(
        unet = "10Eros_Max_h3_fl2va_pruned_int8_convrot.safetensors",
        lora = "10Eros_Max_H3_test2_pruned_r128.safetensors",
        silaPrvni = 0.5f, silaDalsi = 0.5f, kroky = 10,
        titleCs = "Eros Max",
        popisCs = "Eros model i Eros LoRA",
    ),

    /**
     * Dvouprůchodové zapojení po vzoru karty **3 kroky**: tři kroky dole,
     * zvětšení latentu učeným modelem a dva kroky nahoře.
     *
     * Nepřenáší se sem kolegův graf — balík má na to **vlastní uzel**
     * `MiniMaxH3EasyProgressiveUpscale`, který umí i tentýž zvětšovač
     * (`minimax_h3_latent_upscaler_3d_fp16`). Zapojení tím zůstává autorovo
     * a funguje i v navázání, kde se vzorkuje jedním uzlem a zvenčí
     * se do něj vstoupit nedá.
     */
    TRIPLUSDVA(
        unet = "minimax_h3_fl2va_pruned_int8_convrot.safetensors",
        lora = "h3" + SLOZKA + "TaoMate-H3-3step-ComfyUI.safetensors",
        silaPrvni = 1.0f, silaDalsi = 1.0f, kroky = 5, krokyNahore = 2,
        titleCs = "3 + 2",
        popisCs = "Tři kroky dole, zvětšení latentu, dva nahoře",
    );

    /** Jede tahle sestava na dva průchody? */
    val dvojiPruchod: Boolean get() = krokyNahore > 0

    val title: String get() = t(titleCs)
    val popis: String get() = t(popisCs)
}

/** Jedna referenční fotka prvního záběru. */
@Immutable
data class LongMmRef(val soubor: File, val nahled: Bitmap? = null)

/**
 * Karta **Long MiniMax** — navazující záběry přes latent.
 *
 * Běžné navazování vede přes hotové video: předchozí klip se dekóduje,
 * model z něj vyjde a výsledek se znovu zakóduje. Ten okruh není neutrální —
 * autor balíku ho změřil na **zhruba 2,4 % ztmavení na jedno navázání** a chyba
 * se v řetězu sčítá. Tahle karta místo toho ukládá **latent** hotového záběru
 * na server a další běh začíná přesně z těch čísel, ze kterých záběr vznikl.
 *
 * Řetěz se proto nedělá na jeden běh, ale po záběrech: každý se dá pustit,
 * prohlédnout a případně zopakovat, aniž by se cokoli před ním počítalo znovu.
 * Spojování dělá graf sám — místo střihu se **hledá porovnáním snímků**, ne
 * odhaduje z délky kontextu, takže výsledkem je jeden souvislý celek.
 */
@Immutable
data class LongMmScene(
    val rezim: LongMmRezim = LongMmRezim.PRVNI,
    /** Popis toho, co se má v záběru dít. */
    val prompt: String = "",
    /** Délka záběru v sekundách. */
    val sekundy: Int = 5,
    val rozliseni: LongMmRozliseni = LongMmRozliseni.R480,
    val pomer: LongMmPomer = LongMmPomer.NASIRKU,
    /**
     * Poslat referenční fotky i do navázání.
     *
     * **Odchylka od autora.** Ten v předloze navázání reference nemá — uzel
     * `LoadImage` v ní leží nezapojený a scénu drží jen latent a konec
     * předchozího videa. `Context Segments` je ale přijímá (`media` plus celá
     * sada `ref_image_*`), takže jde podobu držet i tady. Vypnuto = jako
     * u autora.
     */
    val referenceVNavazani: Boolean = false,
    /** Sestava modelu a zrychlovací LoRA. */
    val model: LongMmModel = LongMmModel.TURBO,
    /** Kroky vzorkování. Výchozí bere ze sestavy, dál je to na uživateli. */
    val kroky: Int = LongMmModel.TURBO.kroky,
    /** Síla zrychlovací LoRA; záporná hodnota = vzít tu ze sestavy. */
    val loraSila: Float = -1f,
    /**
     * Nechat v grafu uzel `MiniMaxH3MemoryEfficientSageAttentionPatch`.
     *
     * Autor ho v obou předlohách má a je aktivní, proto je zapnutý i tady.
     * Vypnutý se z řetězu vyřadí a model jde rovnou dál — 23. 9. 2026 to
     * uživatel zkusil a běh bez něj trval neúnosně dlouho.
     */
    val sage: Boolean = true,
    /** Realistická LoRA `h3-realism-people-t2v-i2v-r2v`. */
    val realismus: Boolean = false,
    val realismusSila: Float = 0.7f,
    /** První záběr: fotky, podle kterých model drží podobu (nepovinné). */
    val reference: List<LongMmRef> = emptyList(),
    /**
     * Navázání: hotový celek, na který se navazuje. Jeho konec slouží jako
     * vodítko přechodu a zároveň se k němu výsledek přilepí.
     */
    val zdroj: File? = null,
    val zdrojNahled: Bitmap? = null,
    /** Navázání: soubor latentu na serveru, ze kterého se pokračuje. */
    val latent: String = "",
    /**
     * Jméno řetězu. Latenty se pod ním na serveru číslují
     * (`nazev_00001`, `nazev_00002`…), takže se záběry jedné scény drží
     * pohromadě a v nabídce jdou poznat.
     */
    val nazev: String = "zaber",
) {
    /** Pořadí je závazné — stavitel čte reference v tomhle pořadí. */
    val uploadImages: List<File> get() = reference.map { it.soubor }

    /** Síla zrychlovací LoRA pro tenhle běh — buď vlastní, nebo ze sestavy. */
    val silaLory: Float
        get() = if (loraSila >= 0f) loraSila
        else if (rezim == LongMmRezim.PRVNI) model.silaPrvni else model.silaDalsi

    /** Video, které se před během nahraje na server. */
    val uploadVideo: File? get() = zdroj.takeIf { rezim == LongMmRezim.NAVAZANI }

    /**
     * Patří vybraný latent k tomuhle řetězu? Uzel ho ukládá jako
     * `<jméno>_00001.h3latent.safetensors`, takže jméno řetězu je v názvu.
     */
    val latentSediNaRetez: Boolean
        get() {
            val jmeno = nazev.trim().replace(' ', '_')
                .filter { it.isLetterOrDigit() || it == '-' || it == '_' }
                .ifBlank { "zaber" }
            return latent.isBlank() || latent.startsWith(jmeno + "_")
        }

    companion object {
        /** Víc referencí model neunese smysluplně a karta by se nafoukla. */
        const val MAX_REFERENCI = 4

        /**
         * Meze délky jednoho záběru. Nejsou vymyšlené — uzel má v sobě
         * `MIN_SECONDS 0.2` a `MAX_SECONDS 30`, a delší úsek prostě odmítne.
         * Spodní hranici drží appka na dvou sekundách, pod tím nemá záběr
         * co ukázat. Autorova předloha jede na 5 s, navázání na 10 s.
         */
        const val MIN_S = 2
        const val MAX_S = 30

        /** Meze posuvníku kroků. */
        const val MIN_KROKU = 2
        const val MAX_KROKU = 30

        /**
         * Kolik sekund konce předchozího videa jde do modelu jako vodítko.
         * Hodnota z autorovy předlohy; kratší kus přechod nedrží, delší jen
         * ubírá místo v kontextu.
         */
        const val VODITKO_S = 8.0

        /**
         * Kolik snímků na začátku navázání je přegenerovaný konec předchozího
         * záběru. Stejné číslo dostane slepovač jako výchozí překryv.
         */
        const val KONTEXT_SNIMKU = 22
    }
}

/** Co kartě chybí, než se dá spustit. */
fun longMmProblem(s: LongMmScene): String? = when {
    s.prompt.isBlank() -> t("Napiš, co se má v záběru dít.")
    s.nazev.isBlank() -> t("Pojmenuj řetěz — podle toho se latenty na serveru číslují.")
    s.rezim == LongMmRezim.NAVAZANI && s.zdroj == null ->
        t("Vyber video, na které se má navázat.")
    s.rezim == LongMmRezim.NAVAZANI && s.latent.isBlank() ->
        t("Vyber latent záběru, ze kterého se pokračuje. Nabídku plní server.")
    // Latent a zdrojové video musí patřit k témuž řetězu. Když se rozejdou,
    // slepovač přilepí nový záběr k cizímu videu — 22. 9. 2026 se takhle loď
    // přilepila k ženě v kavárně, protože v kartě viselo video ze starého
    // řetězu a latent už byl z nového.
    s.rezim == LongMmRezim.NAVAZANI && !s.latentSediNaRetez ->
        t("Vybraný latent patří k jinému řetězu než „%s“. Vyber latent, který začíná tímhle jménem, nebo řetěz přejmenuj.")
            .format(s.nazev.trim())
    else -> null
}

/** Upozornění, která nebrání spuštění. */
fun longMmHints(s: LongMmScene): List<String> = buildList {
    if (s.rezim == LongMmRezim.PRVNI) {
        add(t("Až záběr doběhne, zůstane na serveru jeho latent. Z něj se v režimu Navázat pokračuje bez ztráty kvality."))
        // Jen to číslo, které si člověk z hlavy nespočítá. Že plátno platí
        // pro celou scénu, stojí u volby samotné — opakovat to je vysvětlivka
        // navíc a ta jen zabírá místo.
        if (s.rozliseni != LongMmRozliseni.R480) {
            add(t("%s má %.2f× víc bodů než 480p a úměrně tomu déle trvá.")
                .format(s.rozliseni.title, s.rozliseni.nasobekPlochy))
        }
        if (s.reference.isNotEmpty()) {
            add(t("Na fotky se v zadání odkazuje značkami <Picture 1>, <Picture 2>… Bez zmínky si jich model nemusí všimnout."))
        }
    } else {
        add(t("Rozlišení i poměr musí zůstat stejné jako u prvního záběru. Latent se nedá přepočítat na jiné plátno."))
        add(t("Výsledkem je celé video od začátku, ne jen přidaný kus — příště navazuj na něj."))
    }
}

/** Uložené zadání karty — přežije zavření aplikace. */
class LongMmStore(private val ctx: Context) {

    private val sp = ctx.getSharedPreferences("h3video", Context.MODE_PRIVATE)

    fun dir(): File = File(ctx.filesDir, "longmm").also { it.mkdirs() }

    fun refFile(i: Int): File = File(dir(), "ref$i.png")

    fun load(): LongMmScene = runCatching {
        val j = org.json.JSONObject(sp.getString(KEY, "{}")!!)
        val refy = j.optJSONArray("reference")
        LongMmScene(
            rezim = runCatching { LongMmRezim.valueOf(j.optString("rezim")) }
                .getOrDefault(LongMmRezim.PRVNI),
            prompt = j.optString("prompt"),
            sekundy = j.optInt("sekundy", 5),
            rozliseni = runCatching { LongMmRozliseni.valueOf(j.optString("rozliseni")) }
                .getOrDefault(LongMmRozliseni.R480),
            pomer = runCatching { LongMmPomer.valueOf(j.optString("pomer")) }
                .getOrDefault(LongMmPomer.NASIRKU),
            // Soubor mohl mezitím zmizet (úklid cache, přeinstalace) — pak je
            // to, jako by vybraný nebyl. Stejně to mají ostatní karty.
            reference = (0 until (refy?.length() ?: 0))
                .mapNotNull { refy!!.optString(it).takeIf { p -> p.isNotBlank() } }
                .map { File(it) }.filter { it.exists() }.map { LongMmRef(it) },
            zdroj = j.optString("zdroj").takeIf { it.isNotBlank() }
                ?.let { File(it) }?.takeIf { it.exists() },
            latent = j.optString("latent"),
            model = runCatching { LongMmModel.valueOf(j.optString("model")) }
                .getOrDefault(LongMmModel.TURBO),
            kroky = j.optInt("kroky", LongMmModel.TURBO.kroky)
                .coerceIn(LongMmScene.MIN_KROKU, LongMmScene.MAX_KROKU),
            loraSila = j.optDouble("loraSila", -1.0).toFloat(),
            sage = j.optBoolean("sage", true),
            referenceVNavazani = j.optBoolean("referenceVNavazani"),
            realismus = j.optBoolean("realismus"),
            realismusSila = j.optDouble("realismusSila", 0.7).toFloat().coerceIn(0f, 1.5f),
            nazev = j.optString("nazev").ifBlank { "zaber" },
        )
    }.getOrDefault(LongMmScene())

    fun save(s: LongMmScene) {
        val refy = org.json.JSONArray()
        s.reference.forEach { refy.put(it.soubor.absolutePath) }
        sp.edit().putString(
            KEY,
            org.json.JSONObject()
                .put("rezim", s.rezim.name)
                .put("prompt", s.prompt)
                .put("sekundy", s.sekundy)
                .put("rozliseni", s.rozliseni.name)
                .put("pomer", s.pomer.name)
                .put("reference", refy)
                .put("zdroj", s.zdroj?.absolutePath ?: "")
                .put("latent", s.latent)
                .put("model", s.model.name)
                .put("kroky", s.kroky)
                .put("loraSila", s.loraSila.toDouble())
                .put("sage", s.sage)
                .put("referenceVNavazani", s.referenceVNavazani)
                .put("realismus", s.realismus)
                .put("realismusSila", s.realismusSila.toDouble())
                .put("nazev", s.nazev)
                .toString()
        ).apply()
    }

    private companion object { const val KEY = "longMmScene" }
}
