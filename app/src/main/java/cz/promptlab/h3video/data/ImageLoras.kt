package cz.promptlab.h3video.data

import cz.promptlab.h3video.comfy.T2iModel
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/** Image-generation choices are independent of the video and image-editing cards. */
object ImageLoras {
    private fun norm(s: String) = s.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]"), "")

    fun compatibility(model: T2iModel, file: EditLoraFile): LoraCompatibility {
        val metadata = file.metadata?.let { m -> listOf("ss_base_model_version", "ss_sd_model_name",
            "modelspec.architecture", "modelspec.implementation", "base_model", "base_model_name_or_path")
            .mapNotNull { m.optString(it).takeIf(String::isNotBlank) }.joinToString(" ") }.orEmpty()
        val fromMetadata = classify(model, norm(metadata))
        return if (fromMetadata != LoraCompatibility.UNKNOWN) fromMetadata else classify(model, norm(file.name))
    }

    private fun classify(model: T2iModel, value: String): LoraCompatibility {
        val family = when {
            "zimage" in value || value.startsWith("zit") -> "zimage"
            "ernie" in value -> "ernie"
            ("klein" in value && "4b" in value) || "f2k4b" in value -> "klein4"
            ("klein" in value && "9b" in value) || "f2k9b" in value -> "klein9"
            listOf("qwen", "krea", "minimax", "wan", "sdxl", "sd15", "flux1", "fluxdev", "fluxkontext")
                .any { it in value } -> "other"
            else -> return LoraCompatibility.UNKNOWN
        }
        val expected = if (model.zRodinyZImage) "zimage" else if (model == T2iModel.KLEIN) "klein9" else "ernie"
        return if (family == expected) LoraCompatibility.MATCH else LoraCompatibility.INCOMPATIBLE
    }

    fun selected(p: GenParams): List<EditLora> {
        val model = T2iModel.zId(p.zimageModel)
        p.imageLoras[model.id]?.let { return it }
        // Preserve the two old Turbo choices, including their disabled state.
        return if (model == T2iModel.TURBO && p.zimageNsfw)
            listOf(EditLora(p.zimageNsfwLora, p.zimageNsfwSila), EditLora(p.zimageNsfwLora2, p.zimageNsfwSila2))
        else emptyList()
    }

    fun encode(choices: Map<String, List<EditLora>>) = JSONObject().apply {
        choices.forEach { (model, loras) -> put(model, JSONArray().apply {
            loras.take(2).forEach { put(JSONObject().put("name", it.name).put("strength", it.strength.toDouble())) }
        }) }
    }.toString()

    fun decode(text: String): Map<String, List<EditLora>> = runCatching {
        val root = JSONObject(text)
        T2iModel.entries.mapNotNull { model ->
            val a = root.optJSONArray(model.id) ?: return@mapNotNull null
            model.id to (0 until minOf(a.length(), 2)).map { i ->
                val item = a.optJSONObject(i) ?: JSONObject()
                val strength = item.optDouble("strength", .8).toFloat()
                EditLora(item.optString("name"), strength.takeIf { it.isFinite() }?.coerceIn(0f, 2f) ?: .8f)
            }
        }.toMap()
    }.getOrDefault(emptyMap())
}
