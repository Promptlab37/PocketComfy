package cz.promptlab.h3video.comfy

import android.content.Context
import cz.promptlab.h3video.R
import cz.promptlab.h3video.data.ImageEditScene
import org.json.JSONArray
import org.json.JSONObject

/**
 * Stavitel grafu pro kartu **Úprava obrázku** (Krea 2 Turbo + Identity Edit LoRA).
 *
 * Zapojení je převzaté z workflow, které autor LoRA dodává k uzlům
 * (`comfyui-krea2edit/workflows/krea2_identity_edit.json`); appka do něj jen
 * dosazuje hodnoty. Dvě věci z toho zapojení jsou podstatné a nesmí se
 * zjednodušit:
 *
 *  – předloha jde do `Krea2EditModelPatch` **i** do `Krea2EditGroundedEncode`.
 *    Latent nese vzhled, enkodér sémantiku; LoRA je trénovaná na obojí naráz.
 *  – uzel dostává kromě `source_latent` ještě `vae` + `source_image`. To je
 *    podle autora doporučená cesta: ořez a zvětšení se udělá v pixelech, takže
 *    výsledek nerozmaže nesoulad mezi rozlišením vstupu a výstupu.
 */
object Krea2Builder {

    /**
     * Kroky z předlohy. Karta je do nastavení nedosazuje (jedou z workflow
     * 1:1), ale ukazatel průběhu je vědět musí — jinak by ukazoval počet
     * kroků z nastavení videa, které s touhle kartou nemá nic společného.
     */
    const val STEPS = 12

    const val N_CLIP = "1"
    const val N_UNET = "2"
    const val N_VAE = "3"
    const val N_LORA = "4"
    const val N_IMAGE = "5"
    const val N_ENCODE = "6"
    const val N_PATCH = "7"
    const val N_POSITIVE = "8"
    const val N_NEGATIVE = "9"
    const val N_LATENT = "10"
    const val N_SAMPLER = "11"
    const val N_DECODE = "12"
    const val N_SAVE = "13"

    /** Druhá předloha (vkládaná osoba) — uzly přidává appka až podle potřeby. */
    const val N_IMAGE_B = "20"
    const val N_ENCODE_B = "21"

    private var cached: String? = null

    private fun template(ctx: Context): String = cached ?: ctx.resources
        .openRawResource(R.raw.workflow_krea2_edit)
        .bufferedReader().use { it.readText() }.also { cached = it }

    fun build(ctx: Context, scene: ImageEditScene, seed: Long, images: List<String>): JSONObject =
        build(template(ctx), scene, seed, images)

    /** Stejné sestavení z textu předlohy, ať jde graf ověřit testem bez Androidu. */
    fun build(
        template: String,
        scene: ImageEditScene,
        seed: Long,
        images: List<String>,
    ): JSONObject {
        val wf = JSONObject(template)
        val res = scene.resolution

        wf.inputs(N_IMAGE).put("image", images.getOrElse(0) { "" })
        wf.inputs(N_POSITIVE).apply {
            put("prompt", scene.prompt.trim())
            put("grounding_px", scene.groundingPx)
        }
        wf.inputs(N_NEGATIVE).put("grounding_px", scene.groundingPx)
        wf.inputs(N_PATCH).apply {
            put("ref_boost", scene.refBoost.toDouble())
            put("ref_boost_a", scene.refBoost.toDouble())
        }
        wf.inputs(N_LATENT).apply {
            put("width", res.width)
            put("height", res.height)
        }
        wf.inputs(N_SAMPLER).put("seed", seed)

        // Druhá předloha: pořadí je závazné – scéna první, vkládaná osoba druhá.
        // Tak je LoRA trénovaná a při prohození se podoba rozpadne.
        if (scene.hasPerson && images.size > 1) {
            wf.put(
                N_IMAGE_B, node(
                    "LoadImage", "Vkládaná osoba", JSONObject().put("image", images[1])
                )
            )
            wf.put(
                N_ENCODE_B, node(
                    "VAEEncode", "Osoba do latentu", JSONObject()
                        .put("pixels", link(N_IMAGE_B))
                        .put("vae", link(N_VAE))
                )
            )
            wf.inputs(N_PATCH).put("source_latent_b", link(N_ENCODE_B))
            wf.inputs(N_PATCH).put("source_image_b", link(N_IMAGE_B))
            wf.inputs(N_POSITIVE).put("image_b", link(N_IMAGE_B))
        }
        return wf
    }

    private fun node(cls: String, title: String, inputs: JSONObject) = JSONObject()
        .put("inputs", inputs)
        .put("class_type", cls)
        .put("_meta", JSONObject().put("title", title))

    private fun link(node: String, slot: Int = 0) = JSONArray().put(node).put(slot)

    private fun JSONObject.inputs(node: String): JSONObject =
        getJSONObject(node).getJSONObject("inputs")

    /** Fáze běhu podle třídy uzlu – karta má vlastní, kratší řetěz než video. */
    fun stageForClass(cls: String?): Stage = when (cls) {
        "CLIPLoader", "UNETLoader", "VAELoader", "LoraLoaderModelOnly" -> Stage.MODELS
        "LoadImage", "VAEEncode", "Krea2EditModelPatch" -> Stage.REFERENCES
        "Krea2EditGroundedEncode", "EmptySD3LatentImage" -> Stage.ENCODING
        "KSampler" -> Stage.SAMPLING
        "VAEDecode" -> Stage.DECODING
        "SaveImage" -> Stage.MUXING
        else -> Stage.SAMPLING
    }

    fun rangeForClass(cls: String?): Pair<Float, Float> = when (cls) {
        "CLIPLoader", "UNETLoader", "VAELoader" -> 0.00f to 0.04f
        "LoraLoaderModelOnly" -> 0.04f to 0.06f
        "LoadImage", "VAEEncode", "Krea2EditModelPatch" -> 0.06f to 0.09f
        "Krea2EditGroundedEncode", "EmptySD3LatentImage" -> 0.09f to 0.12f
        "KSampler" -> 0.12f to 0.86f
        "VAEDecode" -> 0.86f to 0.89f
        "SaveImage" -> 0.89f to 0.90f
        else -> 0.12f to 0.86f
    }

    /** Kroky hlásí jen vzorkovač. */
    fun reportsSteps(cls: String?): Boolean = cls == "KSampler"

    fun nodeClasses(wf: JSONObject): Map<String, String> =
        wf.keys().asSequence().mapNotNull { id ->
            wf.optJSONObject(id)?.optString("class_type")?.takeIf { it.isNotEmpty() }?.let { id to it }
        }.toMap()
}
