package cz.promptlab.h3video.data

import org.json.JSONObject
import java.util.Locale

data class EditLora(val name: String = "", val strength: Float = 0.8f)
enum class LoraCompatibility { MATCH, UNKNOWN, INCOMPATIBLE, BUILT_IN }
data class EditLoraFile(val name: String, val metadata: JSONObject? = null) {
    fun compatibility(motor: EditMotor): LoraCompatibility = EditLoras.compatibility(motor, name, metadata)
}

/**
 * Spouštěcí slovo LoRA — to, bez čeho se natrénovaný styl v obrázku vůbec
 * neprojeví.
 *
 * Čte se z metadat souboru (`/view_metadata/loras`), protože jinde ho appka
 * vzít nemá: v ComfyUI je vidět jen jméno souboru a na webu, odkud LoRA
 * pochází, se appka nedostane. Pořadí zdrojů jde od nejpřesnějšího:
 *
 *  1. `modelspec.trigger_phrase` — standardizované pole, když ho autor vyplnil
 *  2. `ss_trigger_words` / `trigger_words` / `activation_text` — starší tvary
 *     z Kohya a CivitAI, klidně jako JSON pole nebo seznam oddělený čárkami
 *  3. `ss_tag_frequency` — nejčastější značka trénovací sady. Když autor
 *     nevyplnil nic, tohle je jediná stopa; u stylových LoRA to bývá přesně
 *     ten řetězec, co se má napsat do zadání.
 *
 * Vrací **jedno** slovo nebo frázi, ne celý seznam: do zadání se dosazuje
 * automaticky a nacpat tam deset značek by ho rozbilo víc, než by pomohlo.
 */
object LoraTrigger {

    /** Písmeno nebo číslice — hranice slova, ať „cat" nesedí uvnitř „catalog". */
    private const val PISMENO = "\\p{L}\\p{N}"
    private const val MEZERY = "\\s*"
    private const val MEZERA = "\\s"

    private val KLICE = listOf(
        "modelspec.trigger_phrase", "ss_trigger_words", "trigger_words",
        "activation_text", "ss_activation_text", "trainedWords",
    )

    /** Značky, které nejsou spouštěčem ničeho — nemá smysl je psát do zadání. */
    private val NUDNE = setOf(
        "1girl", "1boy", "solo", "woman", "man", "person", "photo", "photograph",
        "realistic", "photorealistic", "highres", "best quality", "masterpiece",
    )

    fun prosoubor(file: EditLoraFile?): String? = zMetadat(file?.metadata)

    fun zMetadat(m: JSONObject?): String? {
        if (m == null) return null
        for (klic in KLICE) {
            prvni(m.optString(klic))?.let { return it }
        }
        return zTagu(m.optString("ss_tag_frequency"))
    }

    /**
     * Z hodnoty vytáhne první použitelnou frázi. Hodnota může přijít jako
     * prosté slovo, jako seznam oddělený čárkami i jako JSON pole — CivitAI
     * a Kohya to ukládají každý jinak.
     */
    private fun prvni(hodnota: String?): String? {
        val text = hodnota?.trim().orEmpty()
        if (text.isBlank() || text == "null" || text == "[]" || text == "{}") return null
        val pole = runCatching { org.json.JSONArray(text) }.getOrNull()
        if (pole != null) {
            for (i in 0 until pole.length()) {
                val v = pole.optString(i).trim()
                if (v.isNotBlank() && v.lowercase(Locale.ROOT) !in NUDNE) return v
            }
            return null
        }
        return text.split(',')
            .map { it.trim().trim('"', '[', ']') }
            .firstOrNull { it.isNotBlank() && it.lowercase(Locale.ROOT) !in NUDNE }
    }

    /**
     * `ss_tag_frequency` je JSON `{"složka": {"značka": počet}}`. Bere se
     * značka s nejvyšším počtem — ta, která byla u každého obrázku, tedy
     * ta, kterou se styl volá.
     */
    private fun zTagu(text: String?): String? {
        val root = runCatching { JSONObject(text.orEmpty()) }.getOrNull() ?: return null
        var nejlepsi: String? = null
        var nejvic = 0
        for (slozka in root.keys()) {
            val tagy = root.optJSONObject(slozka) ?: continue
            for (tag in tagy.keys()) {
                val pocet = tagy.optInt(tag)
                val cisty = tag.trim()
                if (cisty.isBlank() || cisty.lowercase(Locale.ROOT) in NUDNE) continue
                if (pocet > nejvic) { nejvic = pocet; nejlepsi = cisty }
            }
        }
        return nejlepsi
    }

    /**
     * Vrátí zadání s dosazeným spouštěčem nové LoRA a bez spouštěče té
     * předchozí.
     *
     * Odebírá se **jen** přesně ten řetězec, který tam appka sama vložila
     * ([stary]) — kdyby se sahalo na cokoli jiného, škrtla by uživateli
     * vlastní text. Když už nový spouštěč v zadání je (napsal si ho sám),
     * nepřidává se podruhé.
     */
    fun dosad(prompt: String, stary: String?, novy: String?): String {
        var text = prompt
        if (!stary.isNullOrBlank() && stary != novy) text = odeber(text, stary)
        if (novy.isNullOrBlank()) return text.trim()
        if (obsahuje(text, novy)) return text.trim()
        return if (text.isBlank()) novy else text.trimEnd().trimEnd(',') + ", " + novy
    }

    private fun obsahuje(prompt: String, fraze: String): Boolean =
        Regex("(^|[^" + PISMENO + "])" + Regex.escape(fraze) + "($|[^" + PISMENO + "])",
            RegexOption.IGNORE_CASE).containsMatchIn(prompt)

    private fun odeber(prompt: String, fraze: String): String =
        prompt.replace(
            Regex("(^|," + MEZERY + ")" + Regex.escape(fraze) + "(?=$|[," + MEZERA + "])",
                RegexOption.IGNORE_CASE), "",
        ).replace(Regex("," + MEZERY + ","), ", ").trim().trim(',').trim()
}

/**
 * LoRA pro **Qwen Image 2.1**. Starý Qwen-Image (2509/2511/2512) má 60 bloků,
 * 2.1 jen 32 — soubory se navzájem nenačtou. Soubory pro 2.1 nesou v metadatech
 * `ss_base_model_version = qwen_image_2` (ověřeno na třech z CivitAI, 25. 9. 2026),
 * v názvu bývá „qwen_image_2.1" nebo „qwen2-1". Pozor: „qwen_image_2512" začíná
 * stejně, proto za dvojkou nesmí následovat další číslice kromě jedničky.
 */
object Qwen21Lora {
    private val VZOR = Regex("qwenimage2(?![02-9])|qwen21(?![0-9])")

    /** [normalizovany] = malá písmena bez interpunkce. */
    fun je(normalizovany: String): Boolean = VZOR.containsMatchIn(normalizovany)

    /** Detailer má na kartách vlastní vypínač — do nabídky nepatří. */
    fun jeDetailer(normalizovany: String): Boolean = "detailer" in normalizovany && je(normalizovany)

    /** Podle jména souboru (karta Domalovat metadata nečte). */
    fun soubor(jmeno: String): Boolean {
        val n = jmeno.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]"), "")
        return je(n) && !jeDetailer(n)
    }
}

/** Metadata mají přednost před názvem. Neoznačený soubor vyžaduje přiřazení uživatelem. */
object EditLoras {
    private fun normalized(text: String) = text.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]"), "")

    fun compatibility(motor: EditMotor, name: String, metadata: JSONObject? = null): LoraCompatibility {
        val filename = normalized(name)
        if (filename.contains("krea2identityedit") || Qwen21Lora.jeDetailer(filename)) {
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
            Qwen21Lora.je(value) -> EditMotor.QWEN21
            "klein" in value && "4b" in value -> return LoraCompatibility.INCOMPATIBLE
            "klein" in value && "9b" in value -> EditMotor.KLEIN
            // Qwen, který není 2.1, je starý Qwen-Image (60 bloků) — na žádný
            // z motorů karty nesedí.
            "qwen" in value -> return LoraCompatibility.INCOMPATIBLE
            // Označení Klein bez velikosti nebo obecné FLUX.2 nestačí.
            "klein" in value || "flux2" in value -> return LoraCompatibility.UNKNOWN
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
