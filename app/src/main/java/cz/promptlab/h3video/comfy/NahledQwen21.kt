package cz.promptlab.h3video.comfy

import org.json.JSONArray
import org.json.JSONObject

/**
 * Ostrý živý náhled pro Qwen Image 2.1 (5.34).
 *
 * AcademiaSD/TAE-Qwen-Image-2.1 (Apache-2.0, 3,3 MB) je malý dekodér z VAE
 * Qwen 2.1 — náhled během vzorkování je ostrý místo rozmazaného latent2rgb.
 * Na výsledný obrázek nemá vliv (ten dekóduje plné VAE). Podle autora se
 * zapojuje uzlem `ModelPreviewOverrideKJ` (KJNodes) mezi model a sampler
 * a souborem v `models/vae_approx` — stejně jako TAEH3 u H3.
 *
 * Vkládá se až těsně před odesláním, do každého grafu s Qwen 2.1, a jen když
 * server uzel i soubor má — jinak graf zůstane beze změny (starý náhled).
 * Šablony se tím nemění.
 */
object NahledQwen21 {

    const val TAE = "TAEQwenImage21_AcademiaSD.safetensors"
    private const val TRIDA = "ModelPreviewOverrideKJ"

    /** Graf je Qwen Image 2.1 (má jeho kodér textu nebo cache). */
    fun jeQwen21(wf: JSONObject): Boolean = wf.keys().asSequence().any { id ->
        wf.optJSONObject(id)?.optString("class_type") in setOf("TextEncodeQwenImage21", "QwenImage21Cache")
    }

    /**
     * Vloží náhled před každý `KSampler`: jeho vstup `model` jde přes nový
     * uzel. Graf, který už náhledový uzel má, se nemění.
     */
    fun vloz(wf: JSONObject): JSONObject {
        val ids = wf.keys().asSequence().toList()
        if (ids.any { wf.optJSONObject(it)?.optString("class_type") == TRIDA }) return wf
        var dalsi = (ids.mapNotNull { it.toIntOrNull() }.maxOrNull() ?: 0) + 1000
        ids.filter { wf.optJSONObject(it)?.optString("class_type") == "KSampler" }.forEach { id ->
            val vstupy = wf.getJSONObject(id).getJSONObject("inputs")
            val model = vstupy.opt("model") as? JSONArray ?: return@forEach
            val novy = (dalsi++).toString()
            wf.put(
                novy, JSONObject()
                    .put(
                        "inputs", JSONObject()
                            .put("model", JSONArray().put(model.get(0)).put(model.get(1)))
                            .put("max_resolution", 512)
                            .put("jpeg_quality", 70)
                            .put("suppress_default_preview", true)
                            .put("preview_frames", 1)
                            .put("preview_fps", 12)
                            .put("tiny_vae", TAE),
                    )
                    .put("class_type", TRIDA)
                    .put("_meta", JSONObject().put("title", "Model Preview Override")),
            )
            vstupy.put("model", JSONArray().put(novy).put(0))
        }
        return wf
    }

    /** Umí server náhled? `info` = /object_info uzlu (null = uzel chybí). */
    fun serverUmi(info: JSONObject?): Boolean {
        val volitelne = info?.optJSONObject("input")?.optJSONObject("optional") ?: return false
        if (!volitelne.has("tiny_vae")) return false
        val nabidka = volitelne.nabidkaArr("tiny_vae")
        return (0 until nabidka.length()).any { nabidka.optString(it) == TAE }
    }
}
