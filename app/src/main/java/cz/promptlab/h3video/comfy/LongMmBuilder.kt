package cz.promptlab.h3video.comfy

import android.content.Context
import cz.promptlab.h3video.R
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
 * Vzorkovací nastavení jsou autorova beze změny: 7 kroků, plánovač `simple`,
 * `euler` u prvního záběru a `res_multistep` u navázání, Turbo LoRA 0,8
 * respektive 0,75.
 */
object LongMmBuilder {

    // --- první záběr -------------------------------------------------------
    /** Referenční fotky (`LoadImage`) v pořadí, v jakém je appka nahrála. */
    val N_REFERENCE = listOf("214", "215", "216", "217")
    /** Sběrač referencí — uzel balíku určený přímo pro API grafy. */
    const val N_MEDIA = "280"
    /** Zadání, plátno a délka. */
    const val N_ZADANI = "270"
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

    /** Turbo LoRA, na kterou se realistická věší. */
    const val N_TURBO = "271"
    const val N_TURBO_DALSI = "339"
    /** Doplněná realistická LoRA. Čísla jsou volná v obou předlohách. */
    const val N_REALISMUS = "281"

    /** Soubor realistické LoRA. Stejný základ (`minimax-h3-fl2va`) jako turbo. */
    const val LORA_REALISMUS = "h3-realism-people-t2v-i2v-r2v.safetensors"

    /** Kroky vzorkování — podle nich se počítá ukazatel průběhu. */
    const val STEPS = 7

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

    fun buildDalsi(ctx: Context, scene: LongMmScene, seed: Long, zdroj: String): JSONObject =
        buildDalsi(sablonaDalsi(ctx), scene, seed, zdroj)

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

    /** Stejné sestavení z textu předlohy, ať jde graf ověřit testem bez Androidu. */
    fun buildDalsi(
        sablona: String, scene: LongMmScene, seed: Long, zdroj: String,
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

        zapojRealismus(wf, scene, N_TURBO_DALSI)
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
        "LoadImage", "LoadVideo", "GetVideoComponents",
        "MiniMaxH3EasyMediaBridge_SatoDive",
        "MiniMaxH3EasyVideoTailSlicer_SatoDive",
        "MiniMaxH3EasyLoadLatent_SatoDive" -> Stage.REFERENCES
        "MiniMaxH3Easy_SatoDive", "MiniMaxH3EasyContextSegments_SatoDive",
        "MiniMaxH3EasyOutput_SatoDive", "BasicGuider", "BasicScheduler",
        "KSamplerSelect", "RandomNoise" -> Stage.ENCODING
        "SamplerCustomAdvanced", "MiniMaxH3EasySegmentRender_SatoDive" -> Stage.SAMPLING
        else -> Stage.MUXING
    }

    fun rangeForClass(cls: String?): Pair<Float, Float> = when (stageForClass(cls)) {
        Stage.MODELS -> 0.00f to 0.10f
        Stage.REFERENCES -> 0.10f to 0.16f
        Stage.ENCODING -> 0.16f to 0.22f
        Stage.SAMPLING -> 0.22f to 0.88f
        else -> 0.88f to 1.00f
    }

    fun reportsSteps(cls: String?): Boolean =
        cls == "SamplerCustomAdvanced" || cls == "MiniMaxH3EasySegmentRender_SatoDive"

    fun nodeClasses(wf: JSONObject): Map<String, String> =
        wf.keys().asSequence().mapNotNull { id ->
            wf.optJSONObject(id)?.optString("class_type")?.takeIf { it.isNotEmpty() }?.let { id to it }
        }.toMap()
}
