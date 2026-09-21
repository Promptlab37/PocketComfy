package cz.promptlab.h3video.comfy

import org.json.JSONArray
import org.json.JSONObject

/**
 * Stavitel grafu pro **✨ Vylepšit zadání s referencemi** (MiniMax H3, Ref2VA).
 *
 * ### Proč zvlášť od [PromptRewriteBuilder]
 *
 * Dosavadní přepisovač (`MiniMaxH3PromptWriter8B`) umí jen `T2VA`, `I2VA`,
 * `FL2VA` a `L2VA` — tedy text a první/poslední snímek. **Reference neumí.**
 * Na ty je `MiniMaxH3UniversalWriter`: jeden uzel, který si nejdřív nechá
 * předlohy popsat vidoucím modelem a pak z popisů napíše celý H3 prompt.
 *
 * ### Jak se H3 promptuje (oficiální příručky MiniMaxu)
 *
 * Výstup není odstavec jako u Qwena, ale **šest polí** v pevném pořadí:
 * `subject_definitions`, `summary`, `retention_analysis`,
 * `detailed_description`, `overall_soundscape`, `non_diegetic_music`.
 * Reference se v nich označují štítky a ty si drží význam napříč poli:
 *
 *  - `<Subject N>` — znovupoužitelný obsah: osoba, zvíře, prostředí, oblečení,
 *    styl, póza. **Tohle je případ uživatelových fotek lidí.**
 *  - `<Picture N>` — konkrétní snímek (první, poslední, kompoziční kotva)
 *  - `<Video N>` — klip jako zdroj střihu nebo časové struktury
 *  - `<Audio N>` — hlas, hudba, ruch
 *
 * Appka posílá fotky jako **Subject**, ne Picture: uživatel jimi říká „takhle
 * ti lidé vypadají", ne „tenhle snímek je první". Roli i pořadí nese widget
 * `reference_layout` — proto se skládá tady a ne v UI.
 *
 * ### Licence
 *
 * Psací příručka je **Dokumentace MiniMaxu** a jejich licence dovoluje šířit
 * ji jen mimo EU, Británii, Koreu a USA. Do appky se proto **nesmí zabalit**
 * (na rozdíl od systémových promptů Qwenu, viz [Qwen21PeBuilder]). Uzel si ji
 * stahuje sám do `ComfyUI/user/minimax_h3_rewriter/guides` a čte ji odtamtud.
 */
object H3RefWriteBuilder {

    const val N_OPTIONS = "1"
    const val N_WRITER = "10"
    const val N_PREVIEW = "20"

    /** První uzel `LoadImage`; další jdou po jedné nahoru. */
    const val N_OBRAZEK_PRVNI = 100

    const val NODE_CLASS = "MiniMaxH3UniversalWriter"
    const val OPTIONS_CLASS = "MiniMaxH3RewriterOptions"

    /** Úloha s referencemi. Ostatní (`T2VA`…) umí i starý přepisovač. */
    const val TASK = "Ref2VA"

    /**
     * Odblokovaný model pro obě role. Je to tentýž soubor, jen ho uzel jednou
     * bere jako vidoucí popisovač a podruhé jako pisatele — proto dva různé
     * zápisy v nabídce. Nic nezjemňuje a nic nestahuje, leží na disku.
     */
    const val CAPTIONER_ODVAZANY =
        "on disk: Huihui-Qwen3-VL-8B-Instruct-abliterated-Q6_K.gguf [+mmproj, vision, 7.3 GB]"
    const val WRITER_ODVAZANY =
        "on disk: Huihui-Qwen3-VL-8B-Instruct-abliterated-Q6_K.gguf [qwen3vl, 6.3 GB]"

    /**
     * Vybere z nabídky uzlu odblokovanou volbu; když tam není, vezme první
     * položku „on disk", ať se nic nestahuje. Hodnoty z `/object_info` se
     * musí použít **doslova** — uzel porovnává celý řetězec včetně velikosti.
     */
    fun vyberOdblokovany(nabidka: List<String>, presny: String): String? =
        nabidka.firstOrNull { it == presny }
            ?: nabidka.firstOrNull {
                it.startsWith("on disk") &&
                    (it.contains("abliterated", true) || it.contains("huihui", true))
            }
            ?: nabidka.firstOrNull { it.startsWith("on disk") }

    /**
     * Rozvržení referencí pro widget `reference_layout`.
     *
     * Uzel jím řídí **pořadí a roli** každé předlohy — bez něj by o obojím
     * rozhodovalo to, do kterého slotu se co náhodou zapojilo. `slot` je
     * jméno vstupu, `role` je štítek, pod kterým se reference objeví v textu.
     */
    fun layout(pocet: Int): String {
        val pole = JSONArray()
        for (i in 0 until pocet) {
            pole.put(
                JSONObject()
                    .put("slot", "ref_$i")
                    .put("role", "subj")
                    .put("on", true)
            )
        }
        return JSONObject().put("items", pole).toString()
    }

    /**
     * @param zadani co chce uživatel, klidně česky — model píše anglicky
     * @param obrazky názvy už nahraných předloh na serveru
     * @param sekundy délka cílového videa
     * @param pomer poměr stran, jak ho zná uzel (`16:9`, `21:9`, …)
     */
    fun build(
        zadani: String,
        obrazky: List<String>,
        sekundy: Double,
        pomer: String,
        captioner: String,
        writer: String,
        seed: Long,
        maxTokenu: Int = MAX_TOKENU,
    ): JSONObject {
        val wf = JSONObject()

        wf.put(
            N_OPTIONS,
            uzel(
                OPTIONS_CLASS, "Nastavení přepisovače",
                JSONObject()
                    .put("max_new_tokens", maxTokenu)
                    // Greedy dekódování se zapíná na uzlu writeru; tyhle
                    // hodnoty se při něm neuplatní, ale uzel je vyžaduje.
                    .put("temperature", 0.7)
                    .put("top_p", 0.9)
                    .put("top_k", 40)
                    .put("repetition_penalty", 1.05)
                    .put("attn_implementation", "sdpa")
                    // Model i projektor leží na disku — nic se nestahuje.
                    .put("auto_download", false)
                    .put("use_lora", false),
            ),
        )

        val vstupy = JSONObject()
            .put("reference_layout", layout(obrazky.size))
            .put("task", TASK)
            .put("resolution", pomer)
            .put("duration", sekundy)
            .put("prompt", zadani + hlidkaStitku(obrazky.size))
            .put("caption_model", captioner)
            .put("caption_length", "standard")
            .put("writer_model", writer)
            // Malé modely se při vzorkování rozpadnou z formátu — šest polí
            // v pevném pořadí je přesně ten případ.
            .put("greedy", true)
            .put("seed", seed)
            .put("keep_model_loaded", false)
            .put("options", odkaz(N_OPTIONS))

        obrazky.forEachIndexed { i, jmeno ->
            val id = (N_OBRAZEK_PRVNI + i).toString()
            wf.put(id, uzel("LoadImage", "Reference ${i + 1}", JSONObject().put("image", jmeno)))
            vstupy.put("references.ref_$i", odkaz(id))
        }

        wf.put(N_WRITER, uzel(NODE_CLASS, "Vylepšení zadání (reference)", vstupy))
        wf.put(
            N_PREVIEW,
            uzel("PreviewAny", "Výsledek", JSONObject().put("source", odkaz(N_WRITER))),
        )
        return wf
    }

    /**
     * Dovětek, který zakáže vymýšlet si reference, co uživatel nedal.
     *
     * Bez něj si model přidal `<Video 1> is the source video for camera
     * movement` k zadání, kde žádné video nebylo (ověřeno 21. 9. 2026).
     * H3 pak dostane štítek, ke kterému neexistuje podklad.
     */
    fun hlidkaStitku(pocet: Int): String {
        val stitky = (1..pocet).joinToString(", ") { "<Subject $it>" }
        return "\n\n[There are exactly $pocet reference images and nothing else: " +
            "$stitky. Do not introduce <Video> or <Audio> labels, and do not refer " +
            "to any reference that was not provided.]"
    }

    /**
     * Strop odpovědi. Šest polí je násobně delších než jeden prompt Qwenu,
     * ale nekonečno to být nesmí — viz past s 24000 tokeny u Qwen 2.1.
     */
    const val MAX_TOKENU = 2048

    private fun uzel(cls: String, titulek: String, vstupy: JSONObject) = JSONObject()
        .put("class_type", cls)
        .put("inputs", vstupy)
        .put("_meta", JSONObject().put("title", titulek))

    private fun odkaz(uzel: String, slot: Int = 0) = JSONArray().put(uzel).put(slot)
}
