package cz.promptlab.h3video.comfy

import android.content.Context
import cz.promptlab.h3video.R
import cz.promptlab.h3video.data.LtxScene
import org.json.JSONObject

/**
 * Stavitel grafu pro kartu **Video ze zvuku** — LTX 2.5, dvouprůchodová
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

    /** Kroky obou průchodů dohromady — podle nich se počítá ukazatel průběhu. */
    const val STEPS = 11

    private var cached: String? = null

    private fun template(ctx: Context): String = cached ?: ctx.resources
        .openRawResource(R.raw.workflow_ltx25_audio)
        .bufferedReader().use { it.readText() }.also { cached = it }

    fun build(ctx: Context, scene: LtxScene, seed: Long, obrazek: String, zvuk: String): JSONObject =
        build(template(ctx), scene, seed, obrazek, zvuk)

    /** Stejné sestavení z textu předlohy, ať jde graf ověřit testem bez Androidu. */
    fun build(
        template: String, scene: LtxScene, seed: Long, obrazek: String, zvuk: String,
    ): JSONObject {
        val wf = JSONObject(template)

        wf.inputs(N_OBRAZEK).put("image", obrazek)
        wf.inputs(N_ZVUK).apply {
            put("audio", zvuk)
            put("start_time", 0.0)
            // 0 = celý soubor. Cokoli jiného je strop, který by řeč utnul.
            put("duration", 0.0)
        }
        wf.inputs(N_POPIS).put("text", scene.popis)
        wf.inputs(N_ROZLISENI).put("aspect_ratio", scene.pomer.hodnota)
        wf.inputs(N_FPS).put("value", LtxScene.FPS)

        wf.inputs(N_NOISE_1).put("noise_seed", seed)
        // Druhý průchod má vlastní šum. Odvozený, ne náhodný — jinak by se
        // běh nedal zopakovat z jednoho čísla v historii.
        wf.inputs(N_NOISE_2).put("noise_seed", druhySeed(seed))
        return wf
    }

    /** Seed druhého průchodu odvozený z prvního, ať je běh zopakovatelný. */
    fun druhySeed(seed: Long): Long = (seed % 999_999_999_937L) + 1

    private fun JSONObject.inputs(node: String): JSONObject =
        getJSONObject(node).getJSONObject("inputs")

    fun stageForClass(cls: String?): Stage = when (cls) {
        "UNETLoader", "CLIPLoader", "VAELoader", "LatentUpscaleModelLoader" -> Stage.MODELS
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
