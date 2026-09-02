package cz.promptlab.h3video.data

/**
 * Aplikace má tři způsoby generování.
 *
 * Do verze 2.61 jich bylo sedm a každý si nesl vlastní obrazovku i vlastní
 * větev ve stavbě grafu. Od 2.62 zbyly tři, protože ostatní uměla karta
 * All in One taky — jen líp a bez rizika, že se appka rozejde s ComfyUI.
 */
enum class Mode(
    private val titleCs: String,
    private val shortCs: String,
    private val detailCs: String,
) {
    ALLINONE(
        titleCs = "All in One",
        shortCs = "All in One",
        detailCs = "Z textu, obrázků i referencí, klíčové snímky, prodloužení a zvětšení"
    ),

    /**
     * Postavy z fotek si mezi sebou povídají. Repliky namluví Higgs, video
     * staví **šablona All in One** (reference-to-video) – vlastní větev
     * v ULTRA workflow tahle karta od 2.62 nemá.
     */
    TALK(
        titleCs = "Dialogy",
        shortCs = "Dialogy",
        detailCs = "Postavy z fotek řeknou, co napíšeš"
    ),

    /**
     * Kolegovy LSI nody: dlouhé video po segmentech. Každý segment je vlastní
     * běh do 15 s a navazuje na poslední snímek předchozího, takže výsledek
     * není limitovaný jedním záběrem modelu.
     */
    TIMELINE(
        titleCs = "Časová osa",
        shortCs = "Osa",
        detailCs = "Delší video složené ze segmentů"
    ),

    /**
     * Uživatelovo Z-Image Turbo workflow — obrázek z textu za 8 kroků.
     * Předloha 1:1; dosazuje se jen zadání, rozměry a seed. Otevírá řetěz
     * Obrázek → Úprava → Zvětšit, celý bez opuštění appky.
     */
    IMAGE(
        titleCs = "Obrázek",
        shortCs = "Obrázek",
        detailCs = "Z-Image Turbo — nová fotka z textu za pár sekund"
    ),

    /**
     * Upraví hotovou fotku podle věty („dej jí červenou bundu", „přesaď je
     * na pláž") na modelu **Krea 2 Turbo** s LoRA **Krea 2 Identity Edit** —
     * ta drží podobu člověka z předlohy. MiniMax H3 se tu vůbec nespouští;
     * je to samostatný obrázkový model.
     */
    EDIT(
        titleCs = "Úprava obrázku",
        shortCs = "Úprava",
        detailCs = "Změní hotovou fotku podle popisu, tvář zůstane"
    ),

    /**
     * Uživatelovo Qwen Image Edit 2511 workflow na záchranu starých fotek —
     * škrábance, prach, kolorizace, doostření. Zadání je vyladěné v předloze,
     * dosazuje se jen fotka a seed, takže karta je jen fotka + tlačítko.
     */
    RESTORE(
        titleCs = "Oprava fotky",
        shortCs = "Oprava",
        detailCs = "Stará či poškozená fotka jako nová, i barevně"
    ),

    /**
     * Uživatelovo ACE++ workflow (Flux Fill inpaint) — výměna obličeje.
     * Maska se maluje prstem přímo v appce (alfa kanál cílové fotky),
     * dosazují se jen dvě fotky a seed.
     */
    FACESWAP(
        titleCs = "Výměna tváře",
        shortCs = "Tvář",
        detailCs = "Začmáráš obličej, vybereš novou tvář, hotovo"
    ),

    /**
     * Domalování do masky (inpainting): začmáráš kus fotky, napíšeš, co tam
     * má být, a přepíše se jen ten kus. Výchozí je **FLUX.2 Klein 9B**
     * (destilovaný, 4 kroky, rozumí větě), druhá volba **Flux Fill dev**
     * trénovaný přímo na díry v obraze. Okolí masky se vyřízne, přemaluje
     * v plném rozlišení a vlepí zpět — zbytek fotky se nepřepočítává.
     */
    INPAINT(
        titleCs = "Domalovat",
        shortCs = "Domalovat",
        detailCs = "Začmáráš místo, napíšeš co tam má být, přepíše se jen ono"
    ),

    /**
     * Uživatelovo SeedVR2 „gigapixel" workflow — dlaždice, každá na 3200 px,
     * slepení. Předloha je jeho export 1:1; dosazuje se jen fotka, mřížka
     * a seed. Druhá karta, která nevyrábí video.
     */
    UPSCALE(
        titleCs = "Zvětšit",
        shortCs = "Zvětšit",
        detailCs = "SeedVR2 gigapixel — fotka ve velkém rozlišení"
    ),

    /**
     * Uživatelovo ACE-Step 1.5 Turbo workflow — celá skladba z textu za
     * 8 kroků, výsledkem je MP3. Jediná karta, která nevyrábí obraz.
     */
    MUSIC(
        titleCs = "Hudba",
        shortCs = "Hudba",
        detailCs = "ACE-Step 1.5 — celá píseň z textu, i česky"
    );

    /** Název karty v jazyce rozhraní (překlad až při čtení). */
    val title: String get() = t(titleCs)
    val short: String get() = t(shortCs)
    val detail: String get() = t(detailCs)

    /** Jede se na referenčních (ref2va) vahách? U dialogů ano. */
    val usesRefModel: Boolean get() = this == TALK

    /** Vyrábí tahle karta video? Obrázkové karty vrací PNG, Hudba MP3. */
    val isVideo: Boolean
        get() = this == ALLINONE || this == TALK || this == TIMELINE
}

/**
 * Rozlišení podle tabulky, kterou používá autor workflow: megapixely × poměr stran,
 * zaokrouhleno na násobek 32. Stejný výpočet jako uzel ResolutionSelector, takže
 * appka ukazuje přesně to, co ve skutečnosti vyjde.
 */
val MEGAPIXEL_STEPS = listOf(0.2f, 0.3f, 0.4f, 0.5f, 0.6f, 0.7f, 0.8f, 0.9f, 0.98f, 1.0f, 1.2f, 1.5f, 1.8f, 2.0f)

/**
 * Plátno, na kterém model vznikl: kratší hrana 768 px a plocha nejvýš
 * 768 × 1344. Obojí je převzaté z uzlu (`BASE_SHORT_EDGE`, `MAX_PIXELS`
 * v `comfy_extras/nodes_minimax_h3.py`), kde je i výchozí rozlišení 1344×768.
 * Konkrétní plátno pro poměr stran spočítá [nativeCanvas].
 */
const val NATIVE_SHORT_EDGE = 768
const val NATIVE_LONG_EDGE = 1344
