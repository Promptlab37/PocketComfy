package cz.promptlab.h3video.comfy

import android.content.Context
import cz.promptlab.h3video.R
import cz.promptlab.h3video.data.LtxRezim
import cz.promptlab.h3video.data.LtxScene
import org.json.JSONObject

/**
 * Stavitel grafu pro kartu **LTX 2.5** — dvouprůchodová
 * destilovaná předloha (`res/raw/workflow_ltx25_audio.json`, převzatá
 * z uživatelova workflow „LTX-2.5 Image Audio to Video – Lipsync").
 *
 * Dosazuje se fotka, zvuk, popis, tvar obrazu a dva seedy. Kroky, sigmy
 * (8 + 3), sampler i zvětšení latentu mezi průchody zůstávají z předlohy.
 *
 * Tři věci, které tahle předloha dělá jinak a je snadné je zlomit:
 *  - **Délku určuje zvuk, ne zadání.** Uzel [N_DELKA] počítá
 *    `fps × délka_zvuku + 1` z druhého výstupu načítače zvuku. Kdyby se do
 *    `EmptyLTXVLatentVideo.length` dosadilo číslo z karty, video by přestalo
 *    sedět na řeč — a přesně kvůli tomu tahle karta vznikla.
 *  - **Zvuk se nesmí ořezávat.** `duration = 0` u načítače znamená „celý
 *    soubor"; jakákoli jiná hodnota je strop, který zvuk utne.
 *  - **Dva samostatné seedy.** Každý průchod má vlastní [N_NOISE_1] /
 *    [N_NOISE_2]. Druhý se odvozuje od prvního, aby se běh dal zopakovat
 *    z jednoho čísla v historii.
 */
object Ltx25Builder {

    /** Načtení fotky prvního snímku. */
    const val N_OBRAZEK = "452"
    /** Načtení zvuku; druhý výstup je jeho délka v sekundách. */
    const val N_ZVUK = "460"
    /** Kladný popis scény. */
    const val N_POPIS = "411"
    /** Tvar obrazu → šířka a výška. */
    const val N_ROZLISENI = "474"
    /** Snímková frekvence, ze které se počítá délka latentu. */
    const val N_FPS = "422"
    /** `fps × délka_zvuku + 1` — počet snímků. Appka ho NEPŘEPISUJE. */
    const val N_DELKA = "436"
    const val N_NOISE_1 = "433"
    const val N_NOISE_2 = "442"

    /** Ručně zadaná délka v sekundách (režimy Z textu a Z obrázku). */
    const val N_SEKUNDY = "481"

    /**
     * Textový enkodér (gemma4). Appka ho nedosazuje — čte ho
     * [encoderZPredlohy] pro přepisovač promptu, aby vylepšování popisu
     * jelo nad **týmž** modelem, který pak bude popis číst.
     */
    const val N_CLIP = "418"

    /** Kroky obou průchodů dohromady — podle nich se počítá ukazatel průběhu. */
    const val STEPS = 11

    private val cached = mutableMapOf<LtxRezim, String>()

    private fun zdroj(rezim: LtxRezim): Int = when (rezim) {
        LtxRezim.TEXT -> R.raw.workflow_ltx25_t2v
        LtxRezim.OBRAZEK -> R.raw.workflow_ltx25_i2v
        LtxRezim.ZVUK -> R.raw.workflow_ltx25_audio
    }

    private fun template(ctx: Context, rezim: LtxRezim): String = cached.getOrPut(rezim) {
        ctx.resources.openRawResource(zdroj(rezim)).bufferedReader().use { it.readText() }
    }

    fun build(ctx: Context, scene: LtxScene, seed: Long, obrazek: String, zvuk: String): JSONObject =
        build(template(ctx, scene.rezim), scene, seed, obrazek, zvuk)

    /** Stejné sestavení z textu předlohy, ať jde graf ověřit testem bez Androidu. */
    fun build(
        template: String, scene: LtxScene, seed: Long, obrazek: String, zvuk: String,
    ): JSONObject {
        val wf = JSONObject(template)

        // Uzly, které v předloze daného režimu vůbec nejsou, se nedosazují:
        // graf Z textu nemá načítač fotky a graf bez nahraného zvuku nemá
        // načítač zvuku. Sahat na ně by skončilo výjimkou.
        if (scene.rezim.chceObrazek) wf.inputs(N_OBRAZEK).put("image", obrazek)
        if (scene.rezim == LtxRezim.ZVUK) {
            wf.inputs(N_ZVUK).apply {
                put("audio", zvuk)
                put("start_time", 0.0)
                // 0 = celý soubor. Cokoli jiného je strop, který by řeč utnul.
                put("duration", 0.0)
            }
        } else {
            // Délka jde do TÉHOŽ vzorce `fps × délka + 1` jako u nahraného
            // zvuku — jen se druhý činitel bere ze zadání, ne ze souboru.
            wf.inputs(N_SEKUNDY).put("value", scene.sekundy.toDouble())
        }
        wf.inputs(N_POPIS).put("text", scene.popis)
        wf.inputs(N_ROZLISENI).put("aspect_ratio", scene.pomer.hodnota)
        wf.inputs(N_FPS).put("value", LtxScene.FPS)

        zapojLoru(wf, scene.lora)

        wf.inputs(N_NOISE_1).put("noise_seed", seed)
        // Druhý průchod má vlastní šum. Odvozený, ne náhodný — jinak by se
        // běh nedal zopakovat z jednoho čísla v historii.
        wf.inputs(N_NOISE_2).put("noise_seed", druhySeed(seed))
        return wf
    }

    /** Seed druhého průchodu odvozený z prvního, ať je běh zopakovatelný. */
    fun druhySeed(seed: Long): Long = (seed % 999_999_999_937L) + 1

    /** Načítač modelu, ze kterého pijí oba průchody. */
    const val N_UNET = "410"

    /** Doplňková LoRA. Vzniká jen tehdy, když je nějaká vybraná. */
    const val N_LORA = "990"

    /**
     * Vloží `LoraLoaderModelOnly` mezi načítač transformeru a **oba** vodiče.
     *
     * Přepojit se musí každý uzel, který si bere `model` z [N_UNET] — tahle
     * předloha je dvouprůchodová, takže vodiče (`LTXVDualCFGGuider`) jsou dva.
     * Přepojit jen první by znamenalo, že druhý průchod LoRA zahodí a přes
     * zvětšení latentu ji z obrazu zase vyčistí. Proto se hledají podle
     * odkazu, ne podle seznamu čísel — kdyby předloha někdy dostala třetí
     * průchod, zapojí se sám.
     */
    fun zapojLoru(wf: JSONObject, lora: cz.promptlab.h3video.data.EditLora) {
        if (lora.name.isBlank()) return
        wf.put(
            N_LORA,
            JSONObject()
                .put("class_type", "LoraLoaderModelOnly")
                .put("_meta", JSONObject().put("title", "LoRA ${lora.name}"))
                .put(
                    "inputs",
                    JSONObject()
                        .put("model", org.json.JSONArray().put(N_UNET).put(0))
                        .put("lora_name", lora.name)
                        .put("strength_model", lora.strength.toDouble()),
                ),
        )
        val naLoru = org.json.JSONArray().put(N_LORA).put(0)
        wf.keys().asSequence().toList().forEach { id ->
            if (id == N_LORA) return@forEach
            val inputs = wf.getJSONObject(id).getJSONObject("inputs")
            val odkaz = inputs.optJSONArray("model") ?: return@forEach
            if (odkaz.optString(0) == N_UNET) inputs.put("model", naLoru)
        }
    }

    /** Jméno enkodéru z předlohy karty — vstup pro oficiální přepisovač promptu. */
    fun encoderZPredlohy(ctx: Context, rezim: LtxRezim): String =
        encoderZPredlohy(template(ctx, rezim))

    /** Stejné čtení z textu předlohy, ať jde ověřit testem bez Androidu. */
    fun encoderZPredlohy(template: String): String =
        JSONObject(template).getJSONObject(N_CLIP).getJSONObject("inputs")
            .getString("clip_name")

    private fun JSONObject.inputs(node: String): JSONObject =
        getJSONObject(node).getJSONObject("inputs")

    fun stageForClass(cls: String?): Stage = when (cls) {
        "LTXVEmptyLatentAudio", "LTXVAudioVAEDecode" -> Stage.ENCODING
        "UNETLoader", "CLIPLoader", "VAELoader", "LatentUpscaleModelLoader",
        "LoraLoaderModelOnly" -> Stage.MODELS
        "LoadImage", "VHS_LoadAudioUpload", "ResizeImageMaskNode",
        "LTXVPreprocess", "ResolutionSelector" -> Stage.REFERENCES
        "CLIPTextEncode", "LTXVConditioning", "LTXVAudioVAEEncode",
        "EmptyLTXVLatentVideo", "LTXVImgToVideoInplace", "LTXVConcatAVLatent",
        "SetLatentNoiseMask", "SolidMask" -> Stage.ENCODING
        "SamplerCustomAdvanced", "LTXVLatentUpsampler" -> Stage.SAMPLING
        "VAEDecodeTiled", "CreateVideo", "SaveVideo" -> Stage.MUXING
        else -> Stage.SAMPLING
    }

    fun rangeForClass(cls: String?): Pair<Float, Float> = when (cls) {
        "UNETLoader", "CLIPLoader", "VAELoader", "LatentUpscaleModelLoader" -> 0.00f to 0.10f
        "LoadImage", "VHS_LoadAudioUpload" -> 0.10f to 0.12f
        "ResizeImageMaskNode", "LTXVPreprocess", "ResolutionSelector" -> 0.12f to 0.15f
        "CLIPTextEncode", "LTXVConditioning" -> 0.15f to 0.18f
        "LTXVAudioVAEEncode", "EmptyLTXVLatentVideo", "LTXVImgToVideoInplace",
        "LTXVConcatAVLatent", "SetLatentNoiseMask", "SolidMask" -> 0.18f to 0.22f
        // První průchod je osm kroků z jedenácti, druhý tři — proto ta nerovná
        // dělba: kdyby se rozdělily napůl, ukazatel by v půlce stál.
        "LTXVLatentUpsampler" -> 0.60f to 0.64f
        "VAEDecodeTiled" -> 0.90f to 0.95f
        "CreateVideo", "SaveVideo" -> 0.95f to 1.00f
        else -> 0.22f to 0.90f
    }

    fun reportsSteps(cls: String?): Boolean = cls == "SamplerCustomAdvanced"

    fun nodeClasses(wf: JSONObject): Map<String, String> = AceMusicBuilder.nodeClasses(wf)
}
