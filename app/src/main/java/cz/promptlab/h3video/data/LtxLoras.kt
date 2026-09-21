package cz.promptlab.h3video.data

import org.json.JSONObject
import java.util.Locale

/**
 * Které LoRA sednou na **LTX 2.5**.
 *
 * ### Proč to není jen „co má v názvu 2.5"
 *
 * Ověřeno 21. 9. 2026 čtením hlaviček souborů: každá LTX LoRA na serveru —
 * ať je psaná pro LTX-2, 2.3 nebo 2.5 — má **48 bloků a šířku 4096**, což je
 * přesně tvar transformeru `ltx-2.5-22b-distilled`. Na CivitAI to potvrzuje
 * i autor, který jeden soubor vydává pod 2.3 i 2.5 zároveň.
 *
 * Je to tedy opačná situace než u Qwen Image 2.1, kde nová architektura
 * zneplatnila všechny starší LoRA (viz [EditLoras]). Tady se rodina LTX bere
 * celá a vylučují se jen **cizí modely**.
 *
 * Zbytek (neoznačené soubory) zůstává [LoraCompatibility.UNKNOWN] — karta je
 * ukáže pod čarou a nechá uživatele potvrdit. Skrýt je nejde: spousta LoRA
 * nemá v metadatech ani v názvu nic, z čeho by se rodina dala poznat.
 */
object LtxLoras {

    private fun norm(text: String) =
        text.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]"), "")

    /** Cizí rodiny. Jejich LoRA mají jiný tvar a ComfyUI je buď odmítne, nebo tiše ignoruje. */
    private val CIZI = listOf(
        "flux1", "flux2", "fluxdev", "fluxfill", "fluxkontext", "krea2",
        "sdxl", "sd15", "sd3", "zimage", "qwenimage", "qwen2", "wan2",
        "minimaxh3", "hunyuan", "cogvideo", "mochi",
    )

    fun compatibility(file: EditLoraFile): LoraCompatibility =
        compatibility(file.name, file.metadata)

    fun compatibility(name: String, metadata: JSONObject? = null): LoraCompatibility {
        // Pouze údaje o základním modelu, nikdy popis nebo trénovací prompty.
        val base = metadata?.let { m ->
            listOf(
                "ss_base_model_version", "ss_sd_model_name", "modelspec.architecture",
                "modelspec.implementation", "base_model", "base_model_name_or_path",
            ).mapNotNull { k -> m.optString(k).takeIf { it.isNotBlank() } }.joinToString(" ")
        }.orEmpty()
        zMetadat(norm(base))?.let { return it }
        return zMetadat(norm(name)) ?: LoraCompatibility.UNKNOWN
    }

    private fun zMetadat(value: String): LoraCompatibility? = when {
        value.isBlank() -> null
        // „ltx" kdekoli v názvu i v metadatech stačí — celá rodina má tentýž tvar.
        "ltx" in value -> LoraCompatibility.MATCH
        CIZI.any { it in value } -> LoraCompatibility.INCOMPATIBLE
        else -> null
    }

    /**
     * Nahoru patří soubory, které mají v názvu verzi blízkou 2.5; zbytek
     * rodiny za nimi. Fungují oboje, ale trénované na 2.3/2.5 dávají
     * předvídatelnější výsledek než ty z devatenáctimiliardové větve.
     */
    fun serad(soubory: List<EditLoraFile>): List<EditLoraFile> =
        soubory.sortedWith(
            compareByDescending<EditLoraFile> { blizkost(norm(it.name)) }
                .thenBy { it.name.lowercase(Locale.ROOT) }
        )

    private fun blizkost(value: String): Int = when {
        "ltx25" in value || "ltx2.5" in value -> 3
        "ltx23" in value || "ltx2.3" in value -> 2
        "ltx" in value -> 1
        else -> 0
    }

    /** Krátká poznámka pod jménem souboru ve výběru. */
    fun poznamka(file: EditLoraFile): String = when (blizkost(norm(file.name))) {
        3 -> t("pro 2.5")
        2 -> t("pro 2.3 — na 2.5 sedí")
        1 -> t("rodina LTX")
        else -> ""
    }
}
