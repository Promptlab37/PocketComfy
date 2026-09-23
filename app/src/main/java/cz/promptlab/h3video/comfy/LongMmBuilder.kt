package cz.promptlab.h3video.comfy

import android.content.Context
import cz.promptlab.h3video.R
import cz.promptlab.h3video.data.LongMmPozornost
import cz.promptlab.h3video.data.LongMmRozliseni
import cz.promptlab.h3video.data.LongMmScene
import org.json.JSONArray
import org.json.JSONObject

/**
 * Stavitel grafů karty **Long MiniMax** — navazující záběry přes latent.
 * Předlohy `res/raw/workflow_longmm_start.json` a `workflow_longmm_dalsi.json`
 * stojí na balíku [Minimax-H3-Latent-Continuation](https://github.com/SatoDive/Minimax-H3-Latent-Continuation).
 *
 * ### Proč to existuje vedle karty Dlouhé video
 *
 * Dlouhé video počítá všechny úseky v **jednom** běhu a drží kontext v paměti.
 * Tohle je opak: jeden záběr = jeden běh. Latent hotového záběru se uloží na
 * server a další běh z něj začne. Výhoda je, že se každý kus dá prohlédnout a
 * případně zopakovat, aniž by se cokoli před ním počítalo znovu, a že paměť
 * nevzrůstá s délkou výsledku.
 *
 * Navazovat přes hotové video by znamenalo dekódovat a zase zakódovat, a ten
 * okruh není neutrální — autor balíku ho změřil na zhruba 2,4 % ztmavení na
 * jedno navázání, což se v řetězu sčítá. Přes latent ta cesta odpadá úplně.
 *
 * ### Odchylky od autorových předloh a proč
 *
 * 1. **Widgety s popisky ven, hodnoty ze schématu dovnitř.** V editorové
 *    podobě stojí ve widgetech hezké popisky (`"Reference-to-video"`,
 *    `"1K area (~1MP)"`), které server nezná — v API podobě musí stát
 *    `reference` a `1k`. Předlohy se proto nepřeváděly, skládaly se proti
 *    `/object_info`.
 * 2. **Vyhozeny uzly, které v předloze stejně nejedou** (`SolAttnPatch`,
 *    `H3SLAAttention`, `PathchSageAttentionKJ`, `MiniMaxH3SigmaShift` — autor
 *    je má všechny přemostěné) a pomocné uzly cizích balíků, které jen
 *    dodávaly číslo (`Seed (rgthree)`, `Float`) nebo uklízely paměť
 *    (`VRAMCleanup`, `RAMCleanup`) — to dělá appka sama před během.
 * 3. **Z navázání zůstalo jediné uložené video: slepený celek.** Autor ukládá
 *    i samotný přidaný kus; appka bere jako výsledek první video, které najde,
 *    a dvě uložená by se jí pletla.
 *
 * 4. **Obrazový VAE je fp16, ne autorův kvantovaný int8.** Kvantizace se týká
 *    jen dekódování latentu do pixelů; uživatel si 23. 9. 2026 vyžádal plnou
 *    přesnost napevno pro všechny sestavy.
 *
 * Vzorkovací nastavení jsou autorova až na počet kroků: plánovač `simple`,
 * `euler` u prvního záběru a `res_multistep` u navázání, Turbo LoRA 0,8
 * respektive 0,75. Kroků má autor sedm, karta jede od 23. 9. 2026 na osmi.
 */
object LongMmBuilder {

    // --- první záběr -------------------------------------------------------
    /** Referenční fotky (`LoadImage`) v pořadí, v jakém je appka nahrála. */
    val N_REFERENCE = listOf("214", "215", "216", "217")
    /** Sběrač referencí — uzel balíku určený přímo pro API grafy. */
    const val N_MEDIA = "280"
    /** Načtení modelu. Sestavu volí karta, viz `LongMmModel`. */
    const val N_UNET = "190"
    /** Plánovač kroků. */
    const val N_KROKY = "5"
    const val N_KROKY_DALSI = "293"
    /** Zadání, plátno a délka. */
    const val N_ZADANI = "270"
    /** Rozbalení kontextu na latent, VAE a fps. */
    const val N_VYSTUP = "268"
    const val N_SEED = "6"
    /** Uložení latentu, ze kterého se příště navazuje. */
    const val N_LATENT_ULOZ = "272"

    // --- navázání ----------------------------------------------------------
    /** Hotový celek, na který se navazuje. */
    const val N_ZDROJ = "264"
    /** Konec zdroje: vodítko přechodu i makro kontext scény. */
    const val N_VODITKO = "375"
    /** Latent předchozího záběru. */
    const val N_LATENT_NACTI = "356"
    /** Zadání, plátno a délka navázání. */
    const val N_USEK = "328"
    const val N_SEED_DALSI = "343"
    const val N_LATENT_ULOZ_DALSI = "355"
    /** Slepení zdroje s novým kusem do jednoho celku. */
    const val N_SLEPENI = "335"

    /**
     * Zrychlovací záplata pozornosti. Autor ji má v obou předlohách aktivní;
     * v kartě jde vypnout — pak se z řetězu vyřadí a model jde rovnou dál.
     */
    const val N_SAGE = "18"

    /**
     * Vestavěný `ModelAttentionBackend`. V předloze je zařazený **za** Sage,
     * takže podle volby stačí přemostit jeden z nich, druhý, nebo oba.
     */
    const val N_POZORNOST = "400"

    /** Turbo LoRA, na kterou se realistická věší. */
    const val N_TURBO = "271"
    const val N_TURBO_DALSI = "339"
    /** Doplněná realistická LoRA. Čísla jsou volná v obou předlohách. */
    const val N_REALISMUS = "281"
    /** Doplněný dvouprůchodový uzel balíku. */
    const val N_DVA_PRUCHODY = "282"

    /**
     * Zvětšovač latentu mezi průchody. Tentýž, na kterém jede karta 3 kroky.
     */
    const val UPSCALER = "minimax_h3_latent_upscaler_3d_fp16.safetensors"

    /**
     * Rozlišení prvního průchodu. Karta 3 kroky jede dole na 0,2 MPx, což je
     * zhruba tenhle stupeň.
     */
    /**
     * Rozlišení prvního průchodu už není konstanta — odvozuje se z cíle,
     * viz [LongMmRozliseni.nizkeProDvaPruchody]. Pevná hodnota tu byla past:
     * s 360P proti cíli 768P byl skok pětinásobek plochy a vyšla z toho
     * barevná kaše, a s pevným 480P by u cíle 480P dva průchody zmizely.
     */

    /** Soubor realistické LoRA. Stejný základ (`minimax-h3-fl2va`) jako turbo. */
    const val LORA_REALISMUS = "h3-realism-people-t2v-i2v-r2v.safetensors"

    /**
     * Kroky vzorkování — podle nich se počítá ukazatel průběhu.
     * Autor má v obou předlohách sedm; od 23. 9. 2026 jede karta na osmi
     * na přání uživatele.
     */
    const val STEPS = 8

    /**
     * Největší seed, který uzel navázání přijme.
     *
     * `MiniMaxH3EasySegmentRender` má seed omezený na 32 bitů, zatímco
     * `RandomNoise` u prvního záběru snese 64. Appka losuje do 10^15, takže
     * první záběr prošel a navázání spadlo na
     * „Value … bigger than max of 4294967295". Seed se proto zbytkem po dělení
     * složí do povoleného rozsahu — stejné zadání tak pořád dává stejný seed.
     */
    const val SEED_MAX = 4_294_967_295L

    private var cachePrvni: String? = null
    private var cacheDalsi: String? = null

    private fun sablonaPrvni(ctx: Context): String = cachePrvni ?: ctx.resources
        .openRawResource(R.raw.workflow_longmm_start)
        .bufferedReader().use { it.readText() }.also { cachePrvni = it }

    private fun sablonaDalsi(ctx: Context): String = cacheDalsi ?: ctx.resources
        .openRawResource(R.raw.workflow_longmm_dalsi)
        .bufferedReader().use { it.readText() }.also { cacheDalsi = it }

    fun buildPrvni(ctx: Context, scene: LongMmScene, seed: Long, reference: List<String>): JSONObject =
        buildPrvni(sablonaPrvni(ctx), scene, seed, reference)

    fun buildDalsi(
        ctx: Context, scene: LongMmScene, seed: Long, zdroj: String, reference: List<String>,
    ): JSONObject = buildDalsi(sablonaDalsi(ctx), scene, seed, zdroj, reference)

    /** Stejné sestavení z textu předlohy, ať jde graf ověřit testem bez Androidu. */
    fun buildPrvni(
        sablona: String, scene: LongMmScene, seed: Long, reference: List<String>,
    ): JSONObject {
        val wf = JSONObject(sablona)
        val zadani = wf.inputs(N_ZADANI)
        zadani.put("prompt", scene.prompt.trim())
        zadani.put("resolution", scene.rozliseni.kod)
        zadani.put("aspect_ratio", scene.pomer.kod)
        zadani.put("seconds", scene.sekundy.toDouble())
        wf.inputs(N_SEED).put("noise_seed", seed)
        wf.inputs(N_LATENT_ULOZ).put("filename_prefix", nazevLatentu(scene))
        zapojSestavu(wf, scene, N_TURBO, N_KROKY)
        zapojDvaPruchody(
            wf, scene, seed,
            model = N_TURBO, kontext = N_ZADANI, kontextSlot = 1,
            sampler = "7", sigmy = N_KROKY,
            beruModel = listOf("4"), beruKontext = listOf(N_VYSTUP), beruSigmy = listOf("8"),
        )
        zapojPozornost(wf, scene)
        zapojRealismus(wf, scene, N_TURBO)

        val pouzite = reference.take(LongMmScene.MAX_REFERENCI)
        if (pouzite.isEmpty()) {
            // Bez referencí nemá sběrač co sbírat. Prázdný uzel by nespadl, ale
            // model by dostal prázdný seznam médií místo žádného — proto pryč
            // i s odkazem na něj.
            zadani.remove("media")
            wf.remove(N_MEDIA)
            N_REFERENCE.forEach { wf.remove(it) }
        } else {
            val media = wf.inputs(N_MEDIA)
            media.put("image_count", pouzite.size)
            N_REFERENCE.forEachIndexed { i, uzel ->
                if (i < pouzite.size) {
                    wf.inputs(uzel).put("image", pouzite[i])
                } else {
                    wf.remove(uzel)
                    media.remove("image_${i + 1}")
                }
            }
        }
        return wf
    }

    /**
     * Přidá referenční fotky do grafu **navázání**.
     *
     * Autor je v předloze navázání nemá — uzel `LoadImage` v ní leží nezapojený
     * a scénu drží jen latent a konec předchozího videa. `Context Segments` je
     * ale přijímá (`media` plus celá sada `ref_image_*`), takže se podoba dá
     * držet i tady, stejnými fotkami jako v prvním záběru.
     */
    private fun zapojReference(wf: JSONObject, reference: List<String>, doUzlu: String) {
        val pouzite = reference.take(LongMmScene.MAX_REFERENCI)
        if (pouzite.isEmpty()) return
        val media = JSONObject()
            .put("image_count", pouzite.size)
            .put("video_count", 0)
            .put("audio_count", 0)
        pouzite.forEachIndexed { i, jmeno ->
            val uzel = N_REFERENCE[i]
            wf.put(
                uzel,
                JSONObject()
                    .put("class_type", "LoadImage")
                    .put("inputs", JSONObject().put("image", jmeno)),
            )
            media.put("image_${i + 1}", JSONArray().put(uzel).put(0))
        }
        wf.put(
            N_MEDIA,
            JSONObject()
                .put("class_type", "MiniMaxH3EasyMediaBridge_SatoDive")
                .put("inputs", media),
        )
        wf.inputs(doUzlu).put("media", JSONArray().put(N_MEDIA).put(0))
    }

    /** Stejné sestavení z textu předlohy, ať jde graf ověřit testem bez Androidu. */
    fun buildDalsi(
        sablona: String, scene: LongMmScene, seed: Long, zdroj: String,
        reference: List<String> = emptyList(),
    ): JSONObject {
        val wf = JSONObject(sablona)
        wf.inputs(N_ZDROJ).put("file", zdroj)
        wf.inputs(N_LATENT_NACTI).put("latent_file", scene.latent)
        wf.inputs(N_SEED_DALSI).put("seed", seedProNavazani(seed))
        wf.inputs(N_LATENT_ULOZ_DALSI).put("filename_prefix", nazevLatentu(scene))

        val usek = wf.inputs(N_USEK)
        val prompt = scene.prompt.trim()
        usek.put("prompt", prompt)
        usek.put("resolution", scene.rozliseni.kod)
        usek.put("aspect_ratio", scene.pomer.kod)
        usek.put("seconds", scene.sekundy.toDouble())
        // Délka se zadává na KAŽDÝ úsek zvlášť a počet položek musí přesně
        // sednout na počet částí zadání. Uzel jinak celý běh odmítne.
        usek.put("segment_seconds", List(useku(prompt)) { scene.sekundy }.joinToString(","))

        zapojSestavu(wf, scene, N_TURBO_DALSI, N_KROKY_DALSI)
        zapojDvaPruchody(
            wf, scene, seed,
            model = N_TURBO_DALSI, kontext = N_USEK, kontextSlot = 1,
            sampler = "294", sigmy = N_KROKY_DALSI,
            beruModel = listOf(N_SEED_DALSI), beruKontext = listOf(N_SEED_DALSI, N_SLEPENI),
            beruSigmy = listOf(N_SEED_DALSI),
        )
        zapojPozornost(wf, scene)
        zapojRealismus(wf, scene, N_TURBO_DALSI)
        if (scene.referenceVNavazani) zapojReference(wf, reference, N_USEK)
        wf.inputs(N_VODITKO).put("seconds", LongMmScene.VODITKO_S)
        wf.inputs(N_SLEPENI).put("overlap_frames", LongMmScene.KONTEXT_SNIMKU)
        return wf
    }

    /**
     * Zavěsí realistickou LoRA za turbo LoRA a přepojí na ni všechny, kdo brali
     * model z [poTurbu].
     *
     * Věší se až za turbo schválně: obě patchují stejné bloky a pořadí určuje,
     * která má poslední slovo nad krokováním. Latentu se to netýká vůbec — ten
     * nese stav obrazu, ne váhy, takže jeho tvar ani obsah LoRA nemění.
     */
    private fun zapojRealismus(wf: JSONObject, scene: LongMmScene, poTurbu: String) {
        if (!scene.realismus) return
        wf.put(
            N_REALISMUS,
            JSONObject()
                .put("class_type", "MiniMaxH3TurboLoRA")
                .put(
                    "inputs",
                    JSONObject()
                        .put("model", JSONArray().put(poTurbu).put(0))
                        .put("lora_name", LORA_REALISMUS)
                        .put("strength", scene.realismusSila.toDouble())
                        .put("low_vram", false),
                ),
        )
        val naRealismus = JSONArray().put(N_REALISMUS).put(0)
        wf.keys().asSequence().toList().forEach { id ->
            if (id == N_REALISMUS) return@forEach
            val ins = wf.getJSONObject(id).getJSONObject("inputs")
            val odkaz = ins.optJSONArray("model") ?: return@forEach
            if (odkaz.optString(0) == poTurbu) ins.put("model", naRealismus)
        }
    }

    /**
     * Zavěsí dvouprůchodový uzel balíku a přepojí na něj ty, kdo brali model,
     * kontext nebo sigmy z jednoprůchodové cesty.
     *
     * Uzel vrací všechny tři věci upravené naráz, takže se nedá zapojit jen
     * částečně — kdo by zůstal na staré cestě, vzorkoval by v jiném rozlišení
     * než zbytek grafu.
     */
    private fun zapojDvaPruchody(
        wf: JSONObject,
        scene: LongMmScene,
        seed: Long,
        model: String,
        kontext: String,
        kontextSlot: Int,
        sampler: String,
        sigmy: String,
        beruModel: List<String>,
        beruKontext: List<String>,
        beruSigmy: List<String>,
    ) {
        if (!scene.model.dvojiPruchod) return
        wf.put(
            N_DVA_PRUCHODY,
            JSONObject()
                .put("class_type", "MiniMaxH3EasyProgressiveUpscale_SatoDive")
                .put(
                    "inputs",
                    JSONObject()
                        .put("enabled", true)
                        .put("model", JSONArray().put(model).put(0))
                        .put("h3_context", JSONArray().put(kontext).put(kontextSlot))
                        .put("sampler", JSONArray().put(sampler).put(0))
                        .put("sigmas", JSONArray().put(sigmy).put(0))
                        .put("seed", seedProNavazani(seed))
                        .put("low_res_resolution", scene.rozliseni.nizkeProDvaPruchody)
                        .put("high_res_steps", scene.krokyNahoreEfektivni)
                        .put("high_res_resolution", scene.rozliseni.kod)
                        // Autorova výchozí metoda. Volba `latent_upscale_model`
                        // vypadá lákavě (vyhrazený 3D zvětšovač latentu), ale
                        // je v balíku **rozbitá**: `nodes.py:8387` volá
                        // `MiniMaxH3EasyLatentUpscaler3D.execute()` ještě starým
                        // tvarem — místo `mode` a `align` jí podá rozměry a
                        // `enable_chunking` nepředá vůbec. Běh spadne až na
                        // konci, po dokončeném prvním průchodu, hláškou
                        // „missing 1 required positional argument:
                        // 'enable_chunking'". Ověřeno 23. 9. 2026 na nejnovějším
                        // commitu autora (4142527) — nahoře je to stejně.
                        // Schématem se to nechytí, je to chyba uvnitř uzlu.
                        .put("upscale_method", "bislerp")
                        .put("latent_upscale_model", UPSCALER)
                        .put("latent_upscale_device", "cuda")
                        .put("latent_upscale_precision", "fp16"),
                ),
        )
        fun prepoj(uzly: List<String>, pole: String, slot: Int) {
            uzly.forEach { id ->
                val ins = wf.optJSONObject(id)?.optJSONObject("inputs") ?: return@forEach
                if (ins.has(pole)) ins.put(pole, JSONArray().put(N_DVA_PRUCHODY).put(slot))
            }
        }
        prepoj(beruModel, "model", 0)
        prepoj(beruKontext, "h3_context", 1)
        prepoj(beruSigmy, "sigmas", 2)
    }

    /**
     * Dosadí zvolenou sestavu: model, zrychlovací LoRA i její sílu a počet
     * kroků. Sestavy se liší jen těmihle čtyřmi hodnotami, zbytek grafu
     * zůstává autorův.
     */
    private fun zapojSestavu(wf: JSONObject, scene: LongMmScene, lora: String, kroky: String) {
        wf.inputs(N_UNET).put("unet_name", scene.model.unet)
        wf.inputs(kroky).put("steps", scene.kroky)
        // Nulová síla znamená „bez LoRA". Nechat ji v grafu s nulou by model
        // stejně obalilo — uzel se proto z řetězu vyřadí a jede holý model.
        if (scene.silaLory <= 0f) {
            premostiUzel(wf, lora, "model")
            return
        }
        // Uzel se vymění celý, ne jen hodnoty: každý načítač má jiná jména
        // vstupů i jiný formát klíčů, který umí přečíst.
        val vstup = wf.inputs(lora).getJSONArray("model")
        wf.put(
            lora,
            JSONObject()
                .put("class_type", scene.model.nacitac)
                .put(
                    "inputs",
                    JSONObject()
                        .put("model", vstup)
                        .put("lora_name", scene.model.lora)
                        .also { ins ->
                            if (scene.model.nacitac == "LoraLoaderModelOnly") {
                                ins.put("strength_model", scene.silaLory.toDouble())
                            } else {
                                ins.put("strength", scene.silaLory.toDouble())
                                ins.put("low_vram", false)
                            }
                        },
                ),
        )
    }

    /**
     * Nechá v řetězu jen to zapojení pozornosti, které si uživatel zvolil.
     *
     * Předloha má za sebou oba uzly (Sage → `ModelAttentionBackend`), takže
     * se vždy přemostí ty nevybrané. Volba [LongMmPozornost.SERVER] vyhodí
     * oba a nechá platit globální nastavení serveru.
     */
    private fun zapojPozornost(wf: JSONObject, scene: LongMmScene) {
        when (scene.pozornost) {
            LongMmPozornost.SAGE -> premostiUzel(wf, N_POZORNOST, "model")
            LongMmPozornost.KITCHEN -> premostiUzel(wf, N_SAGE, "model")
            LongMmPozornost.SERVER -> {
                premostiUzel(wf, N_POZORNOST, "model")
                premostiUzel(wf, N_SAGE, "model")
            }
        }
    }

    /**
     * Vyřadí uzel z řetězu: kdo bral jeho výstup, dostane rovnou to, co bral
     * on sám na vstupu [propust]. Uzel pak z grafu zmizí.
     *
     * Dělá to tedy totéž, co přemostění (Ctrl+B) v okně ComfyUI — jen bez
     * uzlu, který by v API grafu musel zůstat viset.
     */
    private fun premostiUzel(wf: JSONObject, uzel: String, propust: String) {
        val zdroj = wf.optJSONObject(uzel)?.optJSONObject("inputs")?.optJSONArray(propust)
            ?: return
        wf.keys().asSequence().toList().forEach { id ->
            if (id == uzel) return@forEach
            val ins = wf.getJSONObject(id).getJSONObject("inputs")
            ins.keys().asSequence().toList().forEach { pole ->
                val v = ins.optJSONArray(pole) ?: return@forEach
                if (v.optString(0) == uzel) ins.put(pole, zdroj)
            }
        }
        wf.remove(uzel)
    }

    /** Seed složený do rozsahu, který uzel navázání přijme. Viz [SEED_MAX]. */
    fun seedProNavazani(seed: Long): Long = Math.floorMod(seed, SEED_MAX + 1L)

    /**
     * Na kolik úseků se zadání rozpadne. Uzel dělí text na samostatných
     * řádcích s `---`, takže co appka pošle jako jeden odstavec, zůstane
     * jedním záběrem.
     */
    fun useku(prompt: String): Int = prompt
        .replace("\r\n", "\n").split("\n")
        .count { it.trim() == "---" } + 1

    /**
     * Jméno souboru latentu. Uzel si za něj sám doplní pořadové číslo, takže
     * se záběry jedné scény drží pohromadě; cizí znaky by si v názvu souboru
     * neporadily, proto se propouští jen to, co projde i přes uzel.
     */
    fun nazevLatentu(scene: LongMmScene): String = scene.nazev.trim()
        .replace(' ', '_')
        .filter { it.isLetterOrDigit() || it == '-' || it == '_' }
        .ifBlank { "zaber" }

    private fun JSONObject.inputs(node: String): JSONObject =
        getJSONObject(node).getJSONObject("inputs")

    fun stageForClass(cls: String?): Stage = when (cls) {
        "UNETLoader", "CLIPLoader", "VAELoader", "MiniMaxH3TurboLoRA",
        "MiniMaxH3EasyModelAdapter_SatoDive",
        "MiniMaxH3MemoryEfficientSageAttentionPatch" -> Stage.MODELS
        "ModelAttentionBackend" -> Stage.MODELS
        "LoadImage", "LoadVideo", "GetVideoComponents",
        "MiniMaxH3EasyMediaBridge_SatoDive",
        "MiniMaxH3EasyVideoTailSlicer_SatoDive",
        "MiniMaxH3EasyLoadLatent_SatoDive" -> Stage.REFERENCES
        "MiniMaxH3Easy_SatoDive", "MiniMaxH3EasyContextSegments_SatoDive",
        "MiniMaxH3EasyOutput_SatoDive", "BasicGuider", "BasicScheduler",
        "KSamplerSelect", "RandomNoise" -> Stage.ENCODING
        // Dvouprůchodový uzel si PRVNÍ průchod vzorkuje sám uvnitř — a je to
        // ta delší půlka běhu. Dokud spadal do „else", hlásil fázi skládání,
        // takže karta ukázala 98 % hned po startu a zpátky na 55 % teprve až
        // začal druhý průchod (23. 9. 2026).
        "SamplerCustomAdvanced", "MiniMaxH3EasySegmentRender_SatoDive",
        "MiniMaxH3EasyProgressiveUpscale_SatoDive" -> Stage.SAMPLING
        else -> Stage.MUXING
    }

    /**
     * Rozsah procent pro uzel.
     *
     * [dvaPruchody] říká, jestli je v grafu dvouprůchodový uzel. Pak se pásmo
     * vzorkování dělí na dva kusy, aby ukazatel nešel zpátky: první průchod
     * doběhne do 62 % a druhý na nich naváže. U jednoho průchodu si celé
     * pásmo bere vzorkovač sám.
     */
    fun rangeForClass(cls: String?, dvaPruchody: Boolean = false): Pair<Float, Float> =
        when (stageForClass(cls)) {
            Stage.MODELS -> 0.00f to 0.10f
            Stage.REFERENCES -> 0.10f to 0.16f
            Stage.ENCODING -> 0.16f to 0.22f
            Stage.SAMPLING -> when {
                !dvaPruchody -> 0.22f to 0.88f
                cls == "MiniMaxH3EasyProgressiveUpscale_SatoDive" -> 0.22f to 0.62f
                else -> 0.62f to 0.88f
            }
            else -> 0.88f to 1.00f
        }

    fun reportsSteps(cls: String?): Boolean =
        cls == "SamplerCustomAdvanced" ||
            cls == "MiniMaxH3EasySegmentRender_SatoDive" ||
            // Bez tohohle neběželo během prvního průchodu počítadlo kroků
            // vůbec — karta čtyři minuty psala „počítám" a 0 kroků.
            cls == "MiniMaxH3EasyProgressiveUpscale_SatoDive"

    fun nodeClasses(wf: JSONObject): Map<String, String> =
        wf.keys().asSequence().mapNotNull { id ->
            wf.optJSONObject(id)?.optString("class_type")?.takeIf { it.isNotEmpty() }?.let { id to it }
        }.toMap()
}
