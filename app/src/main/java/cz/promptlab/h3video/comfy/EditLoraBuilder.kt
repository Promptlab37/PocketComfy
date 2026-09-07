package cz.promptlab.h3video.comfy

import cz.promptlab.h3video.data.ImageEditScene
import org.json.JSONArray
import org.json.JSONObject

/** Přidá uživatelskou LoRA do skutečné modelové cesty daného editačního workflow. */
object EditLoraBuilder {
    const val NODE = "edit_user_lora"

    fun attach(wf: JSONObject, scene: ImageEditScene, consumer: String) {
        val lora = scene.selectedLora
        if (lora.name.isBlank() || lora.strength == 0f) return
        require(lora.strength.isFinite() && lora.strength in 0f..2f) { "Neplatná síla LoRA" }
        val inputs = wf.getJSONObject(consumer).getJSONObject("inputs")
        val upstream = inputs.getJSONArray("model")
        wf.put(NODE, JSONObject().put("class_type", "LoraLoaderModelOnly")
            .put("inputs", JSONObject().put("model", JSONArray(upstream.toString()))
                .put("lora_name", lora.name).put("strength_model", lora.strength.toDouble()))
            .put("_meta", JSONObject().put("title", "LoRA · ${scene.motor.nazev}")))
        inputs.put("model", JSONArray().put(NODE).put(0))
    }
}
