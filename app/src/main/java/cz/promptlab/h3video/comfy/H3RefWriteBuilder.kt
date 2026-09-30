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
 * ### Storyboard (experimentální)
 *
 * Oficiální příručka Ref2VA zná obrázek jako plán záběrů: „`<Picture 3>` is
 * a storyboard reference for [Shot 1] and [Shot 2], defining their viewpoint,
 * subject placement, and shot order." Při zapnutém storyboardu je proto
 * **první** předloha mřížka panelů a všechny předlohy jdou jako Picture —
 * přesně jak je čísluje uzel H3 (`<Picture i>` = i-tý obrázek). Postavy pak
 * přepisovač definuje po vzoru příručky: „`<Subject 1>` is the man in
 * `<Picture 2>`". Mřížka dostane vlastní otázku (popsat panely po řadě),
 * postavy otázku role Subject.
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
     * rozhodovalo to, do kterého slotu se co náhodou zapojilo. Tvar je ten,
     * který čte `universal.layout_of`: `order` (pořadí slotů), `off`
     * (vypnuté) a `roles` (slot → `Picture`/`Subject`/`Video`).
     *
     * Do 4.74 šel tvar `{"items":[…]}`, který uzel nezná — tiše ho zahodil
     * a všechny fotky vzal jako Picture (výchozí role). Zjištěno 28. 9. 2026
     * při rešerši storyboardu ze zdrojáku balíku 0.27.0.
     */
    fun layout(pocet: Int, storyboard: Boolean = false): String {
        val poradi = JSONArray()
        val role = JSONObject()
        for (i in 0 until pocet) {
            poradi.put("ref_$i")
            role.put("ref_$i", if (storyboard) ROLE_PICTURE else ROLE_SUBJECT)
        }
        return JSONObject()
            .put("order", poradi)
            .put("off", JSONArray())
            .put("roles", role)
            .toString()
    }

    const val ROLE_PICTURE = "Picture"
    const val ROLE_SUBJECT = "Subject"

    /**
     * Otázky pro vidoucí model po předlohách (widget `reference_instructions`).
     * Bez storyboardu prázdné — každá předloha dostane otázku své role.
     *
     * Mřížka otázku své role **nahrazuje** (`add: false`): výchozí otázka
     * Picture popisuje jeden snímek, storyboard jich má víc a záleží na
     * pořadí. Postavy jedou jako Picture (kvůli číslování), ale ptát se na
     * ně je potřeba jako na Subject — jinak přijde popis kompozice místo
     * podoby.
     */
    fun otazky(pocet: Int, storyboard: Boolean): String {
        val o = JSONObject()
        if (!storyboard || pocet == 0) return o.toString()
        o.put("ref_0", JSONObject().put("text", OTAZKA_STORYBOARD).put("add", false))
        for (i in 1 until pocet) {
            o.put("ref_$i", JSONObject().put("text", OTAZKA_POSTAVA).put("add", false))
        }
        return o.toString()
    }

    /**
     * Popisky u panelů se čtou (děj, kdo to je, replika) — do 4.87 je otázka
     * kázala ignorovat a ztrácel se tím význam („Behind the iron: Louis XIV“).
     * Ignorují se jen rámečky, čísla a časy.
     */
    const val OTAZKA_STORYBOARD =
        "This image is a storyboard: a grid of numbered panels, each panel one planned shot. " +
            "Go through the panels in reading order (left to right, top to bottom) and write " +
            "one line per panel as 'Panel K: shot size, camera angle, where the subjects are " +
            "in the frame, what they do, the setting, and what the caption printed with that " +
            "panel says'. Copy a printed caption or spoken line word for word. Keep the panel " +
            "order. Ignore only borders, panel numbers and timecodes."

    /**
     * Jen osoba sama (5.46): dřív „any distinguishing object it carries“ — z fotky
     * lékařky s žebříkem se žebřík dostal do popisu postavy a model ho nesl do
     * každého záběru místo stolu ze storyboardu (film uživatele 30. 9. 2026).
     */
    const val OTAZKA_POSTAVA =
        "Describe only the person so they can be recognised again in another shot: gender, " +
            "age and build, face, hair, and the clothing they wear with its colours. Describe " +
            "the person alone, as if cut out of the photo. Answer in two or three sentences."

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
        storyboard: Boolean = false,
        /** Vlastní dovětek místo [hlidkaStitku] (úseky filmu ze storyboardu). */
        hlidka: String? = null,
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
                    // Modely se berou jen „on disk" (vyberOdblokovany), takže
                    // se nestahují. Zapnuté stahování je kvůli psací příručce
                    // Ref2VA: na čerstvém serveru chybí a uzel ji s vypnutým
                    // stahováním odmítne (tester 28. 9. 2026 — Vylepšit
                    // u Reference vždy selhal). Příručka se do appky balit
                    // nesmí (licence), stahuje si ji uzel sám z HF MiniMaxu.
                    .put("auto_download", true)
                    .put("use_lora", false),
            ),
        )

        val vstupy = JSONObject()
            .put("reference_layout", layout(obrazky.size, storyboard))
            .put("reference_instructions", otazky(obrazky.size, storyboard))
            .put("task", TASK)
            .put("resolution", pomer)
            .put("duration", sekundy)
            .put("prompt", zadani + (hlidka ?: hlidkaStitku(obrazky.size, storyboard)))
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
    fun hlidkaStitku(pocet: Int, storyboard: Boolean = false): String {
        if (storyboard) return hlidkaStoryboardu(pocet)
        val stitky = (1..pocet).joinToString(", ") { "<Subject $it>" }
        return "\n\n[There are exactly $pocet reference images and nothing else: " +
            "$stitky. Do not introduce <Video> or <Audio> labels, and do not refer " +
            "to any reference that was not provided.]"
    }

    /**
     * Hlídka pro storyboard: kolik je obrázků, co je mřížka a co postavy,
     * a že každý panel je jeden `[Shot K]` v pořadí. Věta o mřížce je
     * doslova vzor z oficiální příručky Ref2VA; to, že mřížka není první
     * snímek, je nutné říct — jinak ji model může otevřít jako záběr.
     */
    fun hlidkaStoryboardu(pocet: Int): String {
        val postavy = if (pocet > 1) {
            val obr = (2..pocet).joinToString(", ") { "<Picture $it>" }
            " $obr show the characters: define each one in subject_definitions as a " +
                "<Subject K> taken from its picture (for example, <Subject 1> is the man in " +
                "<Picture 2>) and keep every character identical in all shots."
        } else ""
        return "\n\n[There are exactly $pocet reference images and nothing else: " +
            (1..pocet).joinToString(", ") { "<Picture $it>" } + ". " +
            "<Picture 1> is a storyboard reference for every shot, defining their viewpoint, " +
            "subject placement, and shot order. It is not a frame of the video. Its description " +
            "lists the panels as Panel 1, Panel 2 and so on: count them and write exactly " +
            "that many timed shots, [Shot K] following Panel K, without skipping or merging " +
            "panels; the last panel is the last shot. Split the duration evenly between " +
            "them." + postavy +
            " Do not introduce <Video> or <Audio> labels, and " +
            "do not refer to any reference that was not provided.]"
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
