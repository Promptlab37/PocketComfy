package cz.promptlab.h3video.data

import org.json.JSONObject
import java.util.Locale

data class EditLora(val name: String = "", val strength: Float = 0.8f)
enum class LoraCompatibility { MATCH, UNKNOWN, INCOMPATIBLE, BUILT_IN }
data class EditLoraFile(val name: String, val metadata: JSONObject? = null) {
    fun compatibility(motor: EditMotor): LoraCompatibility = EditLoras.compatibility(motor, name, metadata)
}

/** Metadata mají přednost před názvem. Neoznačený soubor vyžaduje přiřazení uživatelem. */
object EditLoras {
    private fun normalized(text: String) = text.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]"), "")

    fun compatibility(motor: EditMotor, name: String, metadata: JSONObject? = null): LoraCompatibility {
        val filename = normalized(name)
        if (filename.contains("krea2identityedit") ||
            (filename.contains("qwen") && listOf("lightning", "4steps", "8steps").any { it in filename })) {
            return LoraCompatibility.BUILT_IN
        }
        // Pouze údaje o základním modelu, nikdy popis nebo trénovací prompty.
        val base = metadata?.let { m -> listOf(
            "ss_base_model_version", "ss_sd_model_name", "modelspec.architecture",
            "modelspec.implementation", "base_model", "base_model_name_or_path",
        ).mapNotNull { key -> m.optString(key).takeIf { it.isNotBlank() } }.joinToString(" ") }.orEmpty()
        val fromMetadata = classify(motor, normalized(base))
        if (fromMetadata != LoraCompatibility.UNKNOWN) return fromMetadata
        return classify(motor, filename)
    }

    private fun classify(motor: EditMotor, value: String): LoraCompatibility {
        if (value.isBlank()) return LoraCompatibility.UNKNOWN
        val family = when {
            "krea2" in value -> EditMotor.KREA2
            "qwen" in value && ("edit" in value || "2511" in value) -> EditMotor.QWEN
            "klein" in value && "4b" in value -> return LoraCompatibility.INCOMPATIBLE
            "klein" in value && "9b" in value -> EditMotor.KLEIN
            // Označení Klein bez velikosti nebo obecné Qwen/FLUX.2 nestačí.
            "klein" in value || "qwen" in value || "flux2" in value -> return LoraCompatibility.UNKNOWN
            listOf("flux1", "fluxdev", "fluxfill", "fluxkontext", "sdxl", "sd15", "sd3", "zimage", "minimax", "wan2")
                .any { it in value } -> return LoraCompatibility.INCOMPATIBLE
            else -> return LoraCompatibility.UNKNOWN
        }
        return if (family == motor) LoraCompatibility.MATCH else LoraCompatibility.INCOMPATIBLE
    }

    fun encode(choices: Map<EditMotor, EditLora>): JSONObject = JSONObject().apply {
        choices.forEach { (motor, lora) -> put(motor.name, JSONObject()
            .put("name", lora.name).put("strength", lora.strength.toDouble())) }
    }

    fun decode(root: JSONObject?): Map<EditMotor, EditLora> = EditMotor.entries.mapNotNull { motor ->
        val entry = root?.optJSONObject(motor.name) ?: return@mapNotNull null
        val strength = entry.optDouble("strength", .8).toFloat()
        motor to EditLora(entry.optString("name"), strength.takeIf { it.isFinite() }?.coerceIn(0f, 2f) ?: .8f)
    }.toMap()
}
