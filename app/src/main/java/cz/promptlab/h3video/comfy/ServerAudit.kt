package cz.promptlab.h3video.comfy

import org.json.JSONArray
import org.json.JSONObject

/**
 * Výsledek kontroly „má server všechno, co karty appky potřebují?"
 *
 * Kontroluje se proti předlohám workflow zabaleným v APK: každá třída uzlu
 * se ověří dotazem `/object_info/<třída>` a u výběrových vstupů (modely,
 * LoRA, VAE…) se ověří, že server hodnotu z předlohy opravdu nabízí. Není
 * potřeba žádný ručně udržovaný seznam — co karty potřebují, plyne přímo
 * z předloh.
 */
/** Soubor, který server nenabízí — i s tím, kdo si o něj řekl. */
data class ChybejiciSoubor(val trida: String, val vstup: String, val soubor: String)

data class AuditReport(
    /** Třídy uzlů, které server vůbec nezná (chybějící custom nody). */
    val missingNodes: List<String>,
    /** Hodnoty výběrových vstupů mimo nabídku serveru (modely, LoRA, VAE…). */
    val missingModels: List<ChybejiciSoubor>,
    /** Je na serveru balík ComfyUI-ALLinONE-MinimaxH3 (karty All in One a Dialogy)? */
    val aioOk: Boolean,
    val checkedClasses: Int,
) {
    val ok: Boolean get() = missingNodes.isEmpty() && missingModels.isEmpty() && aioOk
}

object ServerAudit {

    /**
     * Vstupy, které appka dosazuje až za běhu — hodnota v předloze je jen
     * zástupná (prázdný název souboru apod.) a nemá smysl ji kontrolovat.
     */
    private val DOSAZOVANE = setOf(
        "LoadImageWithFilename.image",
        "LoadImage.image",
    )

    /**
     * Třídy, které v předlohách nejsou, protože je stavitelé přidávají do
     * grafu až za běhu — bez nich by kontrola prošla a karta pak spadla:
     * LSI nody staví Časovou osu, náhledový uzel živý náhled.
     */
    val PRIDAVANE_ZA_BEHU = listOf(
        "LSIMinimaxTimeline",
        "LSIMinimaxTimelineRender",
        "ModelPreviewOverrideKJ",
        "MiniMaxH3TeaCache",
        // Obrázek: odvázaný model v GGUF načítá jiný loader (vkládá se za běhu).
        "UnetLoaderGGUF",
        // Časová osa (buildTimeline) si graf skládá celý sama z těchto tříd:
        "H3CacheBust",
        "H3IdentityAnchor",
        "MiniMaxH3MemoryEfficientSageAttentionPatch",
        "LoraLoaderModelOnly",
        "LoadVideo",
        "LoadAudio",
        "GetVideoComponents",
        "VAEEncode",
    )

    /** Z předloh: třída uzlu → textové vstupy (název → hodnota). */
    fun collect(templates: List<String>): Map<String, List<Pair<String, String>>> {
        val out = mutableMapOf<String, MutableList<Pair<String, String>>>()
        templates.forEach { text ->
            val wf = JSONObject(text)
            wf.keys().forEach { id ->
                val node = wf.optJSONObject(id) ?: return@forEach
                val cls = node.optString("class_type").takeIf { it.isNotEmpty() } ?: return@forEach
                val list = out.getOrPut(cls) { mutableListOf() }
                val inputs = node.optJSONObject("inputs") ?: return@forEach
                inputs.keys().forEach { k ->
                    val v = inputs.opt(k)
                    if (v is String && v.isNotBlank() && "$cls.$k" !in DOSAZOVANE) list += k to v
                }
            }
        }
        return out
    }

    /**
     * Nabídka výběrového vstupu z definice uzlu, null = vstup není výběr
     * (třeba obyčejný text). ComfyUI má dva zápisy: starší `[["a","b"], {…}]`
     * a novější `["COMBO", {"options": ["a","b"]}]`.
     */
    fun options(spec: JSONObject, input: String): List<String>? {
        for (grp in listOf("required", "optional")) {
            val arr = spec.optJSONObject("input")?.optJSONObject(grp)
                ?.optJSONArray(input) ?: continue
            val first = arr.opt(0)
            if (first is JSONArray) return (0 until first.length()).map { first.optString(it) }
            if (first == "COMBO") {
                val opts = arr.optJSONObject(1)?.optJSONArray("options") ?: return emptyList()
                return (0 until opts.length()).map { opts.optString(it) }
            }
            return null
        }
        return null
    }

    /** Síťová chyba se vyhazuje (kontrola bez odpovídajícího serveru nemá smysl). */
    fun run(client: ComfyClient, templates: List<String>): AuditReport {
        val potreby = collect(templates).toMutableMap()
        PRIDAVANE_ZA_BEHU.forEach { potreby.getOrPut(it) { mutableListOf() } }
        val chybiUzly = mutableListOf<String>()
        val chybiModely = mutableListOf<ChybejiciSoubor>()
        potreby.keys.sorted().forEach { cls ->
            val spec = client.objectInfo(cls)
            if (spec == null) {
                chybiUzly += cls
                return@forEach
            }
            potreby.getValue(cls).distinct().forEach { (input, value) ->
                val opts = options(spec, input) ?: return@forEach
                if (opts.isNotEmpty() && value !in opts) {
                    chybiModely += ChybejiciSoubor(cls, input, value)
                }
            }
        }
        return AuditReport(
            missingNodes = chybiUzly,
            missingModels = chybiModely.distinct(),
            aioOk = client.hasAllInOne(),
            checkedClasses = potreby.size,
        )
    }

    /**
     * Výsledek kontroly slovy, kterými se dá řídit: co doinstalovat, odkud
     * a do jaké složky. Používá ho obrazovka Nastavení i tlačítko Zkopírovat
     * (na počítači se pak dá jen klikat na odkazy).
     */
    fun zprava(r: AuditReport): String = buildString {
        if (r.ok) {
            append("Server má všechno (${r.checkedClasses} druhů uzlů, balík All in One i modely).")
            return@buildString
        }
        if (!r.aioOk) {
            append("Chybí balík ComfyUI-ALLinONE-MinimaxH3 — bez něj nejedou karty ")
            append("All in One a Dialogy.\n")
            append("  https://github.com/LeonQ8/ComfyUI-ALLinONE-MinimaxH3\n\n")
        }
        if (r.missingNodes.isNotEmpty()) {
            append("CHYBĚJÍCÍ UZLY — doinstaluj balík a restartuj ComfyUI:\n")
            // Seskupeno po balících, ať se stejný odkaz neopakuje u každého uzlu.
            r.missingNodes.groupBy { Katalog.balikProUzel(it) }.forEach { (balik, uzly) ->
                if (balik == null) {
                    uzly.sorted().forEach { append("• ").append(it).append('\n') }
                    append("  (neznámý balík — hledej název uzlu v ComfyUI-Manageru)\n")
                } else {
                    append("• ").append(balik.nazev).append(" — ").append(balik.karty).append('\n')
                    append("  chybí: ").append(uzly.sorted().joinToString(", ")).append('\n')
                    if (balik.odkaz.isNotBlank()) append("  ").append(balik.odkaz).append('\n')
                }
            }
            append('\n')
        }
        if (r.missingModels.isNotEmpty()) {
            append("CHYBĚJÍCÍ MODELY — stáhni a nakopíruj do složky (restart netřeba):\n")
            r.missingModels.forEach { m ->
                val info = Katalog.soubor(m.soubor)
                val slozka = info?.slozka ?: Katalog.slozkaPodleVstupu(m.vstup) ?: "models"
                append("• ").append(m.soubor).append('\n')
                append("  → ").append(slozka)
                info?.karta?.let { append(" · karta ").append(it) }
                append('\n')
                info?.odkaz?.let { append("  ").append(it).append('\n') }
            }
            append('\n')
        }
        append("Úplný seznam včetně velikostí je v POZADAVKY.md v repozitáři appky.")
    }
}
