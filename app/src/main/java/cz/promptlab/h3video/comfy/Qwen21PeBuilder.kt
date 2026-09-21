package cz.promptlab.h3video.comfy

import org.json.JSONArray
import org.json.JSONObject

/**
 * Stavitel grafu pro **✨ Vylepšit zadání** u Qwen Image 2.1.
 *
 * Qwen k modelu vydal dva vlastní přepisovače promptu (oba Qwen3.5-VL 9B):
 *  - `Qwen-Image-2.1-PE-I2I` — z vágního pokynu k úpravě a vstupních fotek
 *    složí přesný pokyn pro editaci,
 *  - `Qwen-Image-2.1-PE-T2I` — z krátkého nápadu složí dlouhý anglický popis
 *    obrázku a doporučí poměr stran.
 *
 * Zadání smí být v **libovolném jazyce**, češtinu včetně; popis model napíše
 * anglicky. Text, který se má vykreslit do obrázku, se řídí zvlášť (pravidla
 * jsou v systémovém promptu).
 *
 * ### Odchylky od referenčního skriptu autorů a proč
 *
 * Autoři volají model přes `transformers` s `apply_chat_template`. V ComfyUI
 * se jede přes core uzel `TextGenerate` a tam je jeden zásadní rozdíl:
 * **ComfyUI systémovou zprávu nedoplní.** Jeho šablona pro Qwen3.5 je jen
 * `<|im_start|>user\n{}<|im_end|>\n<|im_start|>assistant\n`
 * (`comfy/text_encoders/qwen35.py`). Proto si celý chat skládáme sami v
 * [chatText] — tokenizér má zadní vrátka: text začínající na `<|im_start|>`
 * použije **beze změny** (tamtéž, `tokenize_with_weights`).
 *
 * Druhá vědomá odchylka: **uvažování je ve výchozím stavu vypnuté**, i když
 * autoři pouští model s `enable_thinking=True`. Změřeno 21. 9. 2026 na
 * RTX 4060 Ti: model píše ~2,8 tokenu za vteřinu, takže rozvaha dlouhá
 * tisíce tokenů znamená desítky minut na jedno kliknutí. To se jako tlačítko
 * v appce používat nedá. Bez rozvahy napíše rovnou odpověď v řádu stovek
 * tokenů. Zapíná se parametrem `uvazovani` v [build].
 *
 * Všechno ostatní drží autorovy hodnoty, protože se liší od výchozích hodnot
 * uzlu a ovlivňují výstup:
 *
 * | parametr | autoři | výchozí v uzlu | u nás |
 * |---|---|---|---|
 * | max_new_tokens | 24000 / 16256 | 512 | [MAX_TOKENU_RYCHLE] (bez rozvahy) |
 * | temperature | 1.0 | 0.7 | 1.0 |
 * | top_k | 20 | 64 | 20 |
 * | top_p | 0.95 | 0.95 | 0.95 |
 * | min_p | neuvádí (0.0) | 0.05 | 0.0 |
 * | repetition_penalty | neuvádí (1.0) | 1.05 | 1.0 |
 *
 * Výchozích 512 tokenů je past: model nejdřív píše rozvahu v `<think>` a
 * teprve za ní odpověď. S 512 tokeny by se k odpovědi nedostal a vracel by
 * odseknutou rozvahu.
 *
 * Jediná vědomá odchylka navíc: víc fotek se uzlu předává jako dávka přes
 * `ImageBatch`, protože `TextGenerate` má jediný obrazový vstup. ComfyUI si
 * dávku zase rozebere na `<image1>`, `<image2>`… ale sjednotí jim rozměr.
 * Na pochopení scény to stačí, na přesné poměry stran se tu nespoléhá.
 */
object Qwen21PeBuilder {

    const val N_CLIP = "1"
    const val N_GEN = "10"
    const val N_PREVIEW = "20"

    /** První uzel `LoadImage`; další jdou po jedné nahoru. */
    const val N_OBRAZEK_PRVNI = 100

    /** První uzel zmenšení předlohy; jeden ke každé fotce. */
    const val N_ZMENSENI_PRVNI = 150

    /** První uzel `ImageBatch` ve slepenci dávky. */
    const val N_DAVKA_PRVNI = 200

    /**
     * Na kolik pixelů se srazí delší strana předlohy, než ji uvidí přepisovač.
     *
     * Přepisovač má scénu jen **pochopit**, ne z ní číst drobné písmo. Každý
     * pixel navíc se ale promítne do obrazových tokenů, přes které pak model
     * počítá každé napsané slovo. Změřeno 21. 9. 2026: fotka 2048×1693 plus
     * reference 944×2048 srazily rychlost z ~29 na **2 tokeny za vteřinu**,
     * tedy z dvaceti vteřin na jedenáct minut.
     *
     * Na velikost výsledného obrázku to nemá vliv — ta se řídí předlohou
     * v samotném generování, ne tím, co viděl přepisovač.
     */
    const val PREDLOHA_MAX_PX = 1024

    const val NODE_CLASS = "TextGenerate"
    const val LOADER_CLASS = "CLIPLoader"

    const val MODEL_I2I = "qwen3.5_9b_qwen_image_2.1_pe_i2i.int8_convrot.safetensors"
    const val MODEL_T2I = "qwen3.5_9b_qwen_image_2.1_pe_t2i.int8_convrot.safetensors"

    /** `max_new_tokens` z referenčního skriptu PE-I2I (s uvažováním). */
    const val MAX_TOKENU_I2I = 24000

    /** `max_new_tokens` z referenčního skriptu PE-T2I (s uvažováním). */
    const val MAX_TOKENU_T2I = 16256

    /**
     * Strop bez uvažování. Odpověď je jen JSON s přepsaným promptem, takže
     * pár set tokenů bohatě stačí — a hlavně to zastropuje běh, který by se
     * jinak mohl rozjet na desítky minut.
     *
     * Měřeno 21. 9. 2026 na RTX 4060 Ti: model píše ~2,8 tokenu za vteřinu.
     * S uvažováním napsal za osm a půl minuty necelou pětinu z 24000 tokenů
     * a uživatel to musel zabít. Proto je rychlý režim výchozí.
     */
    const val MAX_TOKENU_RYCHLE = 1536

    private const val VISION = "<|vision_start|><|image_pad|><|vision_end|>"

    /**
     * Poměr stran → rozměr, přesně podle tabulky `WH_RATIO_TO_SIZE`
     * v referenčním skriptu PE-T2I. Používá se, když model vrátí `wh_ratio`.
     */
    val ROZMERY: Map<String, Pair<Int, Int>> = mapOf(
        "1:1" to (2048 to 2048),
        "4:3" to (2400 to 1792),
        "3:4" to (1792 to 2400),
        "3:2" to (2528 to 1696),
        "2:3" to (1696 to 2528),
        "16:9" to (2752 to 1536),
        "9:16" to (1536 to 2752),
    )

    /**
     * Složí celý chat tak, jak ho autoři posílají přes `apply_chat_template`.
     * Musí začínat `<|im_start|>`, jinak ho ComfyUI zabalí do vlastní šablony
     * a systémová zpráva se zahodí.
     */
    fun chatText(
        system: String,
        zadani: String,
        pocetObrazku: Int,
        uvazovani: Boolean = false,
    ): String = buildString {
        append("<|im_start|>system\n").append(system.trim()).append("<|im_end|>\n")
        append("<|im_start|>user\n")
        repeat(pocetObrazku) { append(VISION) }
        append(zadani.trim()).append("<|im_end|>\n")
        append("<|im_start|>assistant\n")
        // Prázdný blok uvažování = model rovnou píše odpověď. Přesně tohle
        // dělá ComfyUI ve vlastní šabloně, když je `thinking` vypnuté
        // (`qwen35.py`); chat si skládáme sami, tak si to doplníme taky.
        if (!uvazovani) append("<think>\n</think>\n")
    }

    /**
     * @param system obsah `system_prompt.txt` od autorů (podle varianty)
     * @param zadani co napsal uživatel, klidně česky
     * @param model [MODEL_I2I] nebo [MODEL_T2I]
     * @param obrazky názvy už nahraných souborů na serveru; prázdné u T2I
     * @param maxTokenu [MAX_TOKENU_I2I] nebo [MAX_TOKENU_T2I]
     */
    fun build(
        system: String,
        zadani: String,
        model: String,
        obrazky: List<String>,
        maxTokenu: Int,
        seed: Long,
        uvazovani: Boolean = false,
    ): JSONObject {
        val wf = JSONObject()
        wf.put(
            N_CLIP,
            uzel(
                LOADER_CLASS, "Přepisovač promptu",
                JSONObject()
                    .put("clip_name", model)
                    // Typ je u téhle rodiny jedno: ComfyUI si Qwen3.5-9B pozná
                    // ze souboru a větev v sd.py na clip_type vůbec nekouká.
                    .put("type", "qwen_image")
                    .put("device", "default"),
            ),
        )

        val vstupy = JSONObject()
            .put("clip", odkaz(N_CLIP))
            .put("prompt", chatText(system, zadani, obrazky.size, uvazovani))
            .put("max_length", maxTokenu)
            .put("sampling_mode", "on")
            .put("sampling_mode.temperature", 1.0)
            .put("sampling_mode.top_k", 20)
            .put("sampling_mode.top_p", 0.95)
            .put("sampling_mode.min_p", 0.0)
            .put("sampling_mode.repetition_penalty", 1.0)
            .put("sampling_mode.seed", seed)
            // Při vlastním chatu se hodnota neuplatní (rozhoduje `<think>` blok
            // v textu), ale ať v grafu není opačný záměr.
            .put("thinking", uvazovani)

        obrazkyDoGrafu(wf, obrazky)?.let { vstupy.put("image", it) }

        wf.put(N_GEN, uzel(NODE_CLASS, "Vylepšení zadání", vstupy))
        wf.put(
            N_PREVIEW,
            uzel("PreviewAny", "Výsledek", JSONObject().put("source", odkaz(N_GEN))),
        )
        return wf
    }

    /**
     * Nahraje fotky a slepí je do jedné dávky. Vrátí odkaz na obrazový vstup,
     * nebo null, když žádná fotka není.
     */
    private fun obrazkyDoGrafu(wf: JSONObject, obrazky: List<String>): JSONArray? {
        if (obrazky.isEmpty()) return null
        obrazky.forEachIndexed { i, jmeno ->
            wf.put(
                (N_OBRAZEK_PRVNI + i).toString(),
                uzel(
                    "LoadImage", "Předloha ${i + 1}",
                    JSONObject().put("image", jmeno),
                ),
            )
            wf.put(
                (N_ZMENSENI_PRVNI + i).toString(),
                uzel(
                    "ImageScaleToMaxDimension", "Zmenšení předlohy ${i + 1}",
                    JSONObject()
                        .put("image", odkaz((N_OBRAZEK_PRVNI + i).toString()))
                        .put("upscale_method", "area")
                        .put("largest_size", PREDLOHA_MAX_PX),
                ),
            )
        }
        var posledni = odkaz((N_ZMENSENI_PRVNI).toString())
        for (i in 1 until obrazky.size) {
            val id = (N_DAVKA_PRVNI + i - 1).toString()
            wf.put(
                id,
                uzel(
                    "ImageBatch", "Dávka ${i + 1}",
                    JSONObject()
                        .put("image1", posledni)
                        .put("image2", odkaz((N_ZMENSENI_PRVNI + i).toString())),
                ),
            )
            posledni = odkaz(id)
        }
        return posledni
    }

    /** Co přepisovač vrátil. */
    data class Vysledek(
        /** Hotový prompt pro Qwen Image 2.1. */
        val prompt: String,
        /** Poměr stran, který model zvolil (`16:9`), nebo prázdno. */
        val pomer: String = "",
        /** Poměr se dědí z předlohy (`<image1>`), nebo prázdno. */
        val pomerZObrazku: String = "",
    ) {
        /** Rozměr podle tabulky autorů; null, když se poměr dědí z předlohy. */
        val rozmer: Pair<Int, Int>? get() = ROZMERY[pomer]
    }

    /**
     * Rozebere odpověď modelu. Ta má tvar `<think>…</think>` a za ním JSON
     * `{"rewritten_prompt": …, "wh_ratio": …, "ratio_follow": …}`.
     *
     * Když JSON nedorazí (typicky useknutá odpověď), vrátí null — volající
     * pak řekne uživateli, že se přepis nepovedl, a nechá původní zadání být.
     */
    fun parse(odpoved: String): Vysledek? {
        val zaUvahou = odpoved.substringAfter("</think>", odpoved).trim()
        val json = vyrizniJson(zaUvahou) ?: return null
        val o = runCatching { JSONObject(json) }.getOrNull() ?: return null
        val prompt = o.optString("rewritten_prompt").trim()
        if (prompt.isEmpty()) return null
        return Vysledek(
            prompt = prompt,
            pomer = o.optString("wh_ratio").trim(),
            pomerZObrazku = o.optString("ratio_follow").trim(),
        )
    }

    /** Vyřízne první vyvážený objekt `{…}`; snese i obalení v ```json bloku. */
    private fun vyrizniJson(text: String): String? {
        val start = text.indexOf('{')
        if (start < 0) return null
        var hloubka = 0
        var vRetezci = false
        var escape = false
        for (i in start until text.length) {
            val c = text[i]
            when {
                escape -> escape = false
                c == '\\' && vRetezci -> escape = true
                c == '"' -> vRetezci = !vRetezci
                vRetezci -> Unit
                c == '{' -> hloubka++
                c == '}' -> {
                    hloubka--
                    if (hloubka == 0) return text.substring(start, i + 1)
                }
            }
        }
        return null
    }

    private fun uzel(cls: String, titulek: String, vstupy: JSONObject) = JSONObject()
        .put("class_type", cls)
        .put("inputs", vstupy)
        .put("_meta", JSONObject().put("title", titulek))

    private fun odkaz(uzel: String, slot: Int = 0) = JSONArray().put(uzel).put(slot)
}
