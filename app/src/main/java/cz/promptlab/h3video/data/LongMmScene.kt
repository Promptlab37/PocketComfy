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
    /**
     * **0,5 MP.** Na tomhle stupni dotahuje dva průchody kolega (23. 9. 2026:
     * „0.2 základ a refine 0.5, protože 0.75 nepřidá tolik kvality co stojí
     * času"). V tabulce balíku je 360P = 0,2 MP a 540P = 0,5 MP, takže jeho
     * dvojice je přesně R540 s prvním průchodem na 360P.
     */
    R540("540P", "540p · 0,5 MP"),
    R640("640P", "640p"),
    R720("720P", "720p"),
    /**
     * Přesně **1 megapixel** — v balíku je rozlišení rozpočet plochy a tenhle
     * stupeň je v jeho tabulce zapsaný jako 1.0 MP. Čtyřkrokový FastVideo je
     * dokumentovaný právě na čtyři kroky a 1 MP.
     */
    R768("768P", "768p · 1 MP");

    val title: String get() = t(titleCs)

    /**
     * Rozlišení **prvního** průchodu u dvouprůchodových sestav.
     *
     * Nesmí to být pevná hodnota. Skok mezi průchody se drží kolem
     * **2–2,5násobku plochy**; při pětinásobku (360P → 768P) zůstala po
     * roztažení latentu barevná kaše (23. 9. 2026). Kolega jede 0,2 → 0,5 MP,
     * tedy 360P → 540P, což je přesně 2,5×; stejný poměr drží i ostatní
     * dvojice tady. Hodnoty musí být z autorovy nabídky
     * (`_LOW_RES_CHOICES`: 360P, 416P, 480P, 540P, 640P).
     */
    /**
     * Dává tenhle stupeň u dvou průchodů použitelný obraz?
     *
     * Ne podle úvahy, podle pokusů: 768P (1 MP) dopadlo dvakrát artefakty
     * a rozsypanou barvou — poprvé s prvním průchodem na 360P, podruhé na
     * 480P, tedy i s rozumným poměrem. Kolega dotahuje na **0,5 MP** a výš
     * podle něj nejde: „0.75 nepřidá tolik kvality co stojí času."
     */
    val zvladneDvaPruchody: Boolean get() = this <= R540

    /** Rozpočet plochy v megapixelech — tabulka `RESOLUTION_MEGAPIXELS` z balíku. */
    val megapixely: Double get() = when (this) {
        R480 -> 0.4
        R540 -> 0.5
        R640 -> 0.7
        R720 -> 0.9
        R768 -> 1.0
    }

    val nizkeProDvaPruchody: String get() = when (this) {
        R480 -> "360P"   // 0,4 ← 0,2 MP = 2,0×
        R540 -> "360P"   // 0,5 ← 0,2 MP = 2,5×  (dvojice kolegy)
        R640 -> "416P"   // 0,7 ← 0,3 MP = 2,3×
        R720 -> "480P"   // 0,9 ← 0,4 MP = 2,25×
        R768 -> "480P"   // 1,0 ← 0,4 MP = 2,5×
    }

    /**
     * Kolikrát víc bodů než nejnižší stupeň. Uzel bere rozlišení jako **rozpočet
     * plochy**, ne jako pevný rozměr — „480P" znamená kratší hranu 480 a poměr
     * stran si tu plochu jen přerozdělí. Čas běhu roste zhruba s plochou.
     */
    val nasobekPlochy: Float get() = when (this) {
        R480 -> 1f
        R540 -> 1.25f
        R640 -> 1.78f
        R720 -> 2.25f
        R768 -> 2.5f
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
 * Které zapojení pozornosti se vloží do řetězu modelu.
 *
 * Zdroj: kolegův workflow „Ultra Speed Singularity" (23. 9. 2026) — Sage uzel
 * má **přemostěný** a místo něj vede model přes uzel pozornosti přepnutý na
 * `comfy kitchen (int8)`. Ten jeho uzel je obal z balíku ComfyUI-OrbitSheets;
 * totéž umí **vestavěný** `ModelAttentionBackend` z jádra ComfyUI
 * (`comfy_extras/nodes_model_advanced.py:374`), takže se nic neinstaluje.
 *
 * Volba [SERVER] do grafu nepřidá nic. Pozor: server se spouští s přepínačem
 * `--use-sage-attention`, takže „nic v grafu" neznamená žádnou zrychlovací
 * pozornost — znamená to tu, kterou má server globálně.
 */
enum class LongMmPozornost(private val titleCs: String, private val popisCs: String) {
    /** Autorovo zapojení: `MiniMaxH3MemoryEfficientSageAttentionPatch`. */
    SAGE("Sage", "Zapojení autora balíku. Přesné, ale běh trvá déle"),

    /**
     * `ModelAttentionBackend` s volbou `comfy kitchen attention` —
     * kvantovaná INT8 pozornost z jádra ComfyUI. Uzel ji do nabídky přidá
     * jen tam, kde ji grafická karta zvládne; na tomhle serveru v nabídce je.
     * Jádro ji značí jako experimentální.
     */
    KITCHEN("Comfy Kitchen INT8", "Jako kolega. Kvantovaná pozornost přímo z jádra ComfyUI"),

    /** Do grafu se nepřidá žádný uzel pozornosti. */
    SERVER("Nechat na serveru", "Nic se nepřidá, platí globální nastavení ComfyUI");

    val title: String get() = t(titleCs)
    val popis: String get() = t(popisCs)
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
     * Uzel, kterým se LoRA načítá. Není to kosmetika — rozhoduje formát klíčů:
     *
     *  - `MiniMaxH3TurboLoRA` umí `lora_A` / `lora_B` (tak je uložená turbo,
     *    TaoMate i realistická) a navíc si poradí s prořezaným kvantovaným
     *    základem,
     *  - `LoraLoaderModelOnly` je standardní načítač ComfyUI a umí
     *    `lora_down` / `lora_up` plus přímé rozdíly `.diff` a `.diff_b` —
     *    tak je uložená Eros LoRA.
     *
     * Záměna skončí hláškou typu „'diffusion_model.blocks.0.adaln_proj.linear.lora_A.weight'".
     */
    val nacitac: String = "MiniMaxH3TurboLoRA",
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
        nacitac = "LoraLoaderModelOnly",
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
        silaPrvni = 1.0f, silaDalsi = 1.0f, kroky = 3, krokyNahore = 2,
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
     * Kolik z [kroky] proběhne až v cílovém rozlišení (dvouprůchodové sestavy).
     * Záporná hodnota = vzít počet ze sestavy.
     *
     * Rozhoduje to o výsledku víc než cokoli jiného: první průchod běží dole,
     * latent se pak roztáhne a **tyhle** kroky mají obraz dotáhnout. Když jich
     * je málo, zůstane po roztažení rozmazaná barevná kaše. Autor uzlu má
     * výchozí 6; naše sestava „3 + 2" jich dává 2, proto to jde nastavit.
     */
    val krokyNahore: Int = -1,
    /**
     * Které zapojení pozornosti jde do řetězu. Viz [LongMmPozornost].
     *
     * Výchozí je autorovo (Sage) — v obou jeho předlohách je aktivní.
     */
    val pozornost: LongMmPozornost = LongMmPozornost.SAGE,
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

    /**
     * Kolik kroků poběží nahoře. Uživatelova volba má přednost, jinak sestava.
     * Uzel stejně přijme nejvýš [kroky] − 1, proto se to sem rovnou vejde.
     */
    val krokyNahoreEfektivni: Int
        get() = (if (krokyNahore > 0) krokyNahore else model.krokyNahore)
            .coerceIn(1, (kroky - 1).coerceAtLeast(1))

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
        if (s.model.dvojiPruchod && !s.rozliseni.zvladneDvaPruchody) {
            add(t("Dva průchody nad 0,5 MP dělají artefakty a rozsypanou barvu — ověřeno na 768p. Kolega dotahuje na 540p."))
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

    /**
     * Zahozené záběry — jména latentů a id videí, které se mají přeskočit.
     *
     * Karta navazuje vždy na **nejnovější** latent a video scény. Bez tohohle
     * seznamu by se nepovedený záběr stal základem dalšího a uživatel by se
     * ho nezbavil jinak než mazáním souborů na serveru. Na serveru se nic
     * nemaže — zahození je jen rozhodnutí appky a dá se vzít zpět.
     */
    fun zahozene(): Set<String> =
        sp.getStringSet(KEY_ZAHOZENE, emptySet())?.toSet() ?: emptySet()

    fun zahod(klice: Collection<String>) {
        if (klice.isEmpty()) return
        sp.edit().putStringSet(KEY_ZAHOZENE, zahozene() + klice).apply()
    }

    fun vratZahozene(klice: Collection<String>) {
        sp.edit().putStringSet(KEY_ZAHOZENE, zahozene() - klice.toSet()).apply()
    }

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
            krokyNahore = j.optInt("krokyNahore", -1),
            // Starší uložená scéna měla jen boolean `sage`; ať se po
            // aktualizaci nikomu volba nepřeklopí sama.
            pozornost = j.optString("pozornost").takeIf { it.isNotBlank() }
                ?.let { runCatching { LongMmPozornost.valueOf(it) }.getOrNull() }
                ?: if (j.optBoolean("sage", true)) LongMmPozornost.SAGE
                else LongMmPozornost.SERVER,
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
                .put("krokyNahore", s.krokyNahore)
                .put("pozornost", s.pozornost.name)
                .put("referenceVNavazani", s.referenceVNavazani)
                .put("realismus", s.realismus)
                .put("realismusSila", s.realismusSila.toDouble())
                .put("nazev", s.nazev)
                .toString()
        ).apply()
    }

    private companion object {
        const val KEY = "longMmScene"
        const val KEY_ZAHOZENE = "longMmZahozene"
    }
}
