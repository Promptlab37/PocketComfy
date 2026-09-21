package cz.promptlab.h3video.comfy

import org.json.JSONArray
import org.json.JSONObject

/**
 * Stavitel grafu pro **✨ Vylepšit prompt na kartě Obrázek**.
 *
 * Přepisovač od MiniMaxu (karta All in One) sem nesedí — píše scénář videa
 * se záběry, časováním a zvukem. Statický obrázek potřebuje jinou stavbu,
 * proto se tu jede na obecném llama.cpp uzlu s vlastním systémovým promptem
 * a stejném odblokovaném modelu (`models/LLM`), který už appka používá.
 *
 * Pravidla v systémovém promptu vychází z návodu k Z-Image Turbo:
 * souvislé věty (ne seznam tagů), 80–200 slov, žádný negativní prompt —
 * výjimky se říkají kladně uvnitř věty, text v obraze do uvozovek.
 */
object ImagePromptBuilder {

    const val N_LOADER = "1"
    const val N_PARAMS = "2"
    const val N_LLM = "3"
    const val N_PREVIEW = "4"

    const val NODE_CLASS = "llama_cpp_instruct_adv"
    const val LOADER_CLASS = "llama_cpp_model_loader"

    /** Návod pro model. Anglicky — LLM se tím řídí spolehlivěji. */
    private const val SYSTEM = """You write prompts for the Z-Image Turbo text-to-image model.
The user gives a short idea in any language. Reply with ONE English prompt and nothing else:
no preamble, no quotes around the whole answer, no bullet points, no explanation.

How the prompt must look:
- 80-200 words of flowing natural sentences, the way a person describes a photograph.
- Never a list of comma-separated tags, never weights or parentheses.
- Cover, roughly in this order: shot type and framing; the subject with two to four
  concrete traits; what the subject is doing; clothing and materials; environment and
  background; lighting; mood; style and medium (for example realistic photography,
  85mm lens, shallow depth of field, or flat vector illustration).
- Z-Image has no negative prompt, so say exclusions positively inside the sentences,
  for example "plain uncluttered background" or "clean skin without blemishes".
- If the user wants words visible in the image, put the exact words in double quotes
  and say where they sit; otherwise state that the image carries no text or watermark.
- Lighting matters a lot to this model: name it (soft diffused daylight, warm cinematic
  key light, high-contrast noir lighting, studio softbox).

Keep every detail the user asked for and add only what makes the scene concrete.
Never swap the subject for a different one and never refuse."""

    /**
     * Návod pro **úpravu fotky**, ne pro nový obrázek.
     *
     * Qwenův vlastní přepisovač (PE-I2I) je na to vycvičený líp a hlavně vidí
     * fotku — jenže odvážnější zadání potichu zjemní (ověřeno 21. 9. 2026).
     * Tenhle jede na odblokovaném modelu z `models/LLM`, takže nepřepisuje nic,
     * ale **fotku nevidí** (uzel má `mmproj` vypnutý). Píše tedy jen z toho,
     * co napsal uživatel, a nesmí si domýšlet, co je na obrázku.
     */
    private const val SYSTEM_UPRAVA =
        """You turn a short, vague photo-editing request into one precise English
editing instruction for an image-editing model. Reply with the instruction only:
no preamble, no quotes around the answer, no explanation, no list.

You are NOT looking at the photo. Never invent what is in it — no hair colour,
no clothing, no background, no pose that the user did not mention. Refer to
what is there in general terms ("the subject", "the background") unless the
user named it.

The instruction must:
- start with the change itself, in the imperative ("Replace…", "Remove…",
  "Add…", "Change…"),
- say explicitly what must stay the same: pose, framing, identity and facial
  features, lighting direction and the rest of the scene, unless the user
  asked to change them,
- describe the new element concretely — material, colour, fit, how it sits in
  the scene and how it is lit, so it matches the existing photo,
- keep any reference markers the user used (<image2>, <image3>, …) exactly as
  written, and keep their meaning,
- stay under about 120 words and be one flowing instruction, not bullet points.

Keep every detail the user asked for and add only what makes the change
concrete. Never swap the requested change for a different one, never soften or
sanitise it, and never refuse."""

    /**
     * Návod pro překlad. Schválně nic nevylepšuje: kdo si prompt napsal sám,
     * chce ho anglicky, ne přepsaný. Model tedy nesmí nic přidat ani ubrat.
     */
    private const val SYSTEM_PREKLAD =
        """You are a translation engine for image and video prompts.
Translate the user's text into English and output ONLY the translation:
no preamble, no quotes around the answer, no notes, no explanation.

Rules:
- Keep the meaning, the order and EVERY detail. Add nothing, drop nothing.
- Keep the style of a prompt — do not turn it into a sentence about a prompt.
- Leave technical tokens exactly as they are: <Picture 1>, <d>…</d>, [S1],
  numbers, model names, camera terms, words already in English.
- Text that should appear inside the image stays in its original language,
  in the same quotation marks.
- If the text already is English, repeat it unchanged.
- Never comment on the content and never refuse."""

    /**
     * @param zadani krátký nápad uživatele (klidně česky)
     * @param model soubor v `models/LLM`, viz [vyberModel]
     */
    fun build(zadani: String, model: String, seed: Long): JSONObject =
        graf(zadani, model, seed, SYSTEM, maxTokens = 400, teplota = 0.65)

    /**
     * Vylepšení zadání na kartě **Úprava obrázku**. Kratší strop než u popisu
     * scény: pokyn k úpravě má být jedna věta, ne odstavec.
     */
    fun buildUprava(zadani: String, model: String, seed: Long): JSONObject =
        graf(zadani, model, seed, SYSTEM_UPRAVA, maxTokens = 300, teplota = 0.5)

    /**
     * Překlad zadání do angličtiny — bez vylepšování. Nižší teplota a víc
     * tokenů: dlouhý prompt se musí vejít celý, ale nesmí se rozjet.
     */
    fun buildPreklad(text: String, model: String, seed: Long): JSONObject =
        graf(text, model, seed, SYSTEM_PREKLAD, maxTokens = 900, teplota = 0.15)

    private fun graf(
        zadani: String,
        model: String,
        seed: Long,
        system: String,
        maxTokens: Int,
        teplota: Double,
    ): JSONObject {
        val wf = JSONObject()
        wf.put(
            N_LOADER,
            uzel(
                LOADER_CLASS, "Jazykový model",
                JSONObject()
                    .put("model", model)
                    .put("mmproj", "None")
                    .put("chat_handler", "None")
                    .put("n_ctx", 4096)
                    .put("vram_limit", -1)
                    .put("image_min_tokens", 0)
                    .put("image_max_tokens", 0)
                    .put("load_mtp", false),
            ),
        )
        wf.put(
            N_PARAMS,
            uzel(
                "llama_cpp_parameters", "Nastavení generování",
                JSONObject()
                    .put("max_tokens", maxTokens)
                    .put("top_k", 40)
                    .put("top_p", 0.9)
                    .put("min_p", 0.05)
                    .put("typical_p", 1.0)
                    // Nižší teplota než výchozí 0.8 — prompt má být konkrétní,
                    // ne básnický; model si pak míň vymýšlí vlastní scénu.
                    .put("temperature", teplota)
                    .put("repeat_penalty", 1.05)
                    .put("frequency_penalty", 0.0)
                    .put("present_penalty", 0.0)
                    .put("mirostat_mode", 0)
                    .put("mirostat_eta", 0.1)
                    .put("mirostat_tau", 5.0)
                    .put("state_uid", -1),
            ),
        )
        wf.put(
            N_LLM,
            uzel(
                NODE_CLASS, "Vylepšení promptu",
                JSONObject()
                    .put("llama_model", odkaz(N_LOADER))
                    .put("parameters", odkaz(N_PARAMS))
                    .put("preset_prompt", "Empty - Nothing")
                    .put("custom_prompt", zadani)
                    .put("system_prompt", system)
                    .put("inference_mode", "one by one")
                    .put("max_frames", 24)
                    .put("max_size", 256)
                    .put("seed", seed)
                    // Po dopsání promptu model pustí paměť grafiky — hned potom
                    // se obvykle generuje obrázek a ten ji potřebuje celou.
                    .put("force_offload", true)
                    .put("save_states", false),
            ),
        )
        wf.put(
            N_PREVIEW,
            uzel("PreviewAny", "Výsledek", JSONObject().put("source", odkaz(N_LLM))),
        )
        return wf
    }

    /**
     * Z nabídky uzlu vybere použitelný model: přednost má odblokovaný
     * (Huihui/abliterated), pak jakýkoli GGUF — safetensors složky loader
     * sice nabízí, ale llama.cpp je nenačte.
     */
    fun vyberModel(nabidka: List<String>): String? {
        val gguf = nabidka.filter { it.endsWith(".gguf", ignoreCase = true) }
            // Adaptér k přepisovači videa není samostatný model.
            .filterNot { it.contains("Rewriter", ignoreCase = true) }
            .filterNot { it.contains("mmproj", ignoreCase = true) }
        return gguf.firstOrNull {
            it.contains("huihui", true) || it.contains("abliterated", true)
        } ?: gguf.firstOrNull()
    }

    private fun uzel(cls: String, titulek: String, vstupy: JSONObject) = JSONObject()
        .put("class_type", cls)
        .put("inputs", vstupy)
        .put("_meta", JSONObject().put("title", titulek))

    private fun odkaz(uzel: String, slot: Int = 0) = JSONArray().put(uzel).put(slot)

    /**
     * Model občas i přes zákaz uvede odpověď větou („Here is the prompt:")
     * nebo ji zabalí do uvozovek. Tohle nechá jen samotný prompt.
     */
    fun ocisti(text: String): String {
        var s = text.trim()
        val uvozovky = listOf("```", "\"", "'")
        uvozovky.forEach { z ->
            if (s.startsWith(z) && s.endsWith(z) && s.length > 2 * z.length) {
                s = s.removePrefix(z).removeSuffix(z).trim()
            }
        }
        // Uvozovací věta na prvním řádku (končí dvojtečkou a je krátká).
        val radky = s.split("\n")
        if (radky.size > 1 && radky[0].trimEnd().endsWith(":") && radky[0].length < 80) {
            s = radky.drop(1).joinToString("\n").trim()
        }
        return s
    }
}
