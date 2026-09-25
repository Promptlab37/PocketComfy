package cz.promptlab.h3video.engine

import org.json.JSONObject

/**
 * Jaký model běh OPRAVDU načítá — přečtený z grafu, který jde na server.
 *
 * Texty průběhu byly pevně dané podle karty: Úprava psala „Načítám Krea 2"
 * i když jel Qwen nebo Klein, Obrázek vždy „Z-Image", video karty
 * „MiniMax H3" i u LTX nebo Wanu (hlášeno 25. 9. 2026). Z grafu to lhát
 * nemůže: je to přesně ten soubor, který si ComfyUI nahraje.
 */
object RunModel {

    /** Zpětné lomítko podsložek, jak ho hlásí ComfyUI na Windows. */
    private const val ZPETNE = '\\'

    /** Načítače a vstup, ve kterém mají soubor s modelem — v pořadí priority. */
    private val NACITACE = listOf(
        "UNETLoader" to "unet_name",
        "UnetLoaderGGUF" to "unet_name",
        "CheckpointLoaderSimple" to "ckpt_name",
        "LTXVLoader" to "ckpt_name",
    )

    /** (čitelný název, soubor) — prázdné, když graf běžný načítač nemá. */
    fun zGrafu(wf: JSONObject?): Pair<String, String> {
        if (wf == null) return "" to ""
        // Jen načítač, na který v grafu něco navazuje. Šablona All in One nese
        // dva (obyčejný i referenční) a ten nepoužitý ComfyUI vůbec nespustí.
        val pouzite = HashSet<String>()
        for (id in wf.keys()) {
            val ins = wf.optJSONObject(id)?.optJSONObject("inputs") ?: continue
            for (k in ins.keys()) {
                val v = ins.optJSONArray(k) ?: continue
                if (v.length() == 2) pouzite.add(v.optString(0))
            }
        }
        for ((trida, vstup) in NACITACE) {
            for (id in wf.keys()) {
                if (id !in pouzite) continue
                val u = wf.optJSONObject(id) ?: continue
                if (u.optString("class_type") != trida) continue
                val soubor = u.optJSONObject("inputs")?.opt(vstup) as? String ?: continue
                if (soubor.isBlank()) continue
                val jmeno = soubor.substringAfterLast(ZPETNE).substringAfterLast('/')
                return nazev(jmeno) to jmeno
            }
        }
        return "" to ""
    }

    /** Čitelný název modelu podle souboru. Neznámý soubor = jeho jméno. */
    fun nazev(soubor: String): String {
        val s = soubor.lowercase()
        return when {
            "qwen_image_2.1" in s || "qwen-image-2.1" in s -> "Qwen Image 2.1"
            "2511" in s && "qwen" in s -> "Qwen Image Edit 2511"
            "2509" in s && "qwen" in s -> "Qwen Image Edit 2509"
            "2512" in s && "qwen" in s -> "Qwen Image 2512"
            "krea" in s -> "Krea 2"
            "klein" in s -> "FLUX.2 Klein"
            "fill" in s && "flux" in s -> "Flux Fill"
            "z_image" in s || "z-image" in s || "zimage" in s -> "Z-Image"
            "ernie" in s -> "ERNIE Image"
            "singularity" in s -> "MiniMax H3 Singularity"
            "10eros" in s && "turbo" in s -> "Eros Max Turbo"
            "10eros" in s -> "Eros Max"
            "fastvideo" in s -> "MiniMax H3 FastVideo"
            "minimax_h3_ref2va" in s -> "MiniMax H3 (reference)"
            "minimax_h3" in s || "minimax-h3" in s -> "MiniMax H3"
            "ltx" in s -> "LTX 2.5"
            "dancer" in s -> "Wan 2.2 Dancer"
            "wan" in s -> "Wan"
            "seedvr2" in s -> "SeedVR2"
            "trellis" in s -> "TRELLIS.2"
            "ace_step" in s || "ace-step" in s -> "ACE-Step 1.5"
            "yue2" in s -> "YuE2"
            else -> soubor.substringBeforeLast('.')
        }
    }
}
