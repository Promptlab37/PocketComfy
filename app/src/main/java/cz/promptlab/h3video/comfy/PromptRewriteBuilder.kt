package cz.promptlab.h3video.comfy

import org.json.JSONArray
import org.json.JSONObject

/**
 * Stavitel grafu pro **✨ Vylepšit prompt** — MiniMax-H3 Prompt Rewriter 8B
 * (balík pytraveler/MiniMax-H3-Prompt-Rewriter-ComfyUI na serveru).
 *
 * Uživatel napíše česky pár slov, uzel z nich složí plný anglický H3 prompt
 * (záběry, časování, zvuk). Základem je odblokovaný Qwen3-VL-8B (GGUF), takže
 * nic neodmítá; po přepsání se model z VRAM sám uklidí. Výsledek se čte přes
 * core uzel PreviewAny, jehož text končí ve výstupech historie.
 */
object PromptRewriteBuilder {

    const val N_REWRITER = "1"
    const val N_PREVIEW = "2"
    const val N_FIRST = "3"
    const val N_LAST = "4"

    const val NODE_CLASS = "MiniMaxH3PromptWriter8B"

    /**
     * Z nabídky modelů uzlu (enum z /object_info) vybere odblokovaný základ.
     * Přednost má položka „on disk" s Huihui/abliterated v názvu; bez ní
     * první „on disk" položka; jinak první v nabídce (stáhl by si ji uzel).
     */
    fun vyberModel(nabidka: List<String>): String? =
        nabidka.firstOrNull {
            it.contains("on disk", true) &&
                (it.contains("huihui", true) || it.contains("abliterated", true))
        }
            ?: nabidka.firstOrNull { it.contains("on disk", true) }
            ?: nabidka.firstOrNull()

    fun build(
        prompt: String,
        model: String,
        task: String,
        resolution: String,
        durationSec: Int,
        seed: Long,
        /** Název nahraného souboru pro first_frame (I2VA/FL2VA), null = bez. */
        firstImage: String? = null,
        /** Název nahraného souboru pro last_frame (FL2VA/L2VA), null = bez. */
        lastImage: String? = null,
    ): JSONObject {
        val wf = JSONObject()
        val inputs = JSONObject()
            .put("prompt", prompt)
            .put("model", model)
            .put("task", task)
            .put("resolution", resolution)
            .put("duration", durationSec)
            // Kvantizaci si GGUF nese v sobě — hodnota se ignoruje.
            .put("quantization", "nf4")
            .put("greedy", true)
            .put("seed", seed)
            .put("keep_model_loaded", false)
        if (firstImage != null) {
            wf.put(N_FIRST, loadImage(firstImage))
            inputs.put("first_frame", JSONArray().put(N_FIRST).put(0))
        }
        if (lastImage != null) {
            wf.put(N_LAST, loadImage(lastImage))
            inputs.put("last_frame", JSONArray().put(N_LAST).put(0))
        }
        wf.put(
            N_REWRITER,
            JSONObject()
                .put("class_type", NODE_CLASS)
                .put("inputs", inputs)
                .put("_meta", JSONObject().put("title", "Vylepšení promptu")),
        )
        wf.put(
            N_PREVIEW,
            JSONObject()
                .put("class_type", "PreviewAny")
                .put(
                    "inputs",
                    JSONObject().put("source", JSONArray().put(N_REWRITER).put(0)),
                )
                .put("_meta", JSONObject().put("title", "Výsledek")),
        )
        return wf
    }

    private fun loadImage(name: String): JSONObject = JSONObject()
        .put("class_type", "LoadImage")
        .put("inputs", JSONObject().put("image", name))
        .put("_meta", JSONObject().put("title", "Snímek pro přepis"))
}
