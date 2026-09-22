package cz.promptlab.h3video.comfy

import org.json.JSONObject

/**
 * Zadání pro **navázání** na kartě Long MiniMax.
 *
 * ### Proč to nemůže psát obvyklý přepisovač
 *
 * `MiniMaxH3PromptWriter8B` je stavěný na **jeden samostatný klip**: vždycky
 * popíše kulisy, oblečení, světlo i kameru od nuly. U navázání to aktivně
 * škodí — 22. 9. 2026 z „vyjde z kavárny" napsal moderní kancelář se skleněnými
 * dveřmi, trenčkot a druhou postavu. Scénu přitom drží latent předchozího
 * záběru, takže model ten nový popis zahodil a žena dál pila kafe.
 *
 * Autor balíku to má stejně: ke generování a k navázání dává **dva různé**
 * systémové prompty. Tenhle je vlastní — popisuje jen **další takt děje**
 * a kulisy nechává na latentu.
 *
 * Jede na odblokovaném modelu z `models/LLM` přes llama.cpp, tedy na tom
 * samém, co pohání „Vylepšit odvázaně" u ostatních karet.
 */
object LongMmPromptBuilder {

    /**
     * Pravidla jsou napsaná vlastními slovy. Příručky MiniMaxu se sem opsat
     * nesmí — licence dovoluje šířit Dokumentaci jen mimo EU, Británii,
     * Koreu a USA.
     */
    private const val SYSTEM_NAVAZANI =
        """You write the next beat of a shot that continues an existing video.

The previous shot already fixed the place, the people, their clothes, the
light and the look. None of that is yours to decide or restate — it is carried
over from the previous shot automatically. Your job is ONLY what happens next.

Output ONE English paragraph, 40 to 90 words, and nothing else: no preamble,
no headings, no quotes, no notes.

Rules:
- Write only the new action, in the order it happens. Start from the position
  the subject is already in.
- Do NOT describe the room, the furniture, the wardrobe, the weather, the time
  of day, the art style or the subject's appearance. Naming a thing the subject
  interacts with is fine ("sets the cup down"), describing it is not.
- Do NOT open a new scene, do NOT cut to another place and do NOT introduce a
  new character unless the user explicitly asks for one.
- One camera move at most, and only if the user asks for it. Otherwise leave
  the camera alone.
- Sound belongs in the action: name what the movement itself makes, briefly.
- If the user's text already contains a token such as <Picture 1>, <Video 1>
  or [S1], keep it exactly. NEVER add a token the user did not write, and
  never start the paragraph with one.
- Name every object the user named, and name no other object. If a word is
  unclear, keep the user's word rather than replacing it with a guess.
- Plain declarative sentences in the present tense. Name the subject as
  "she", "he" or "they" — whichever the user used.
- No lists, no adjectives piled up, no mood words.
- Never comment on the request and never refuse."""

    /**
     * @param zadani co se má dít dál, klidně česky
     * @param model soubor v `models/LLM`, viz [ImagePromptBuilder.vyberModel]
     */
    fun build(zadani: String, model: String, seed: Long): JSONObject =
        ImagePromptBuilder.graf(
            zadani = zadani,
            model = model,
            seed = seed,
            system = SYSTEM_NAVAZANI,
            // Devadesát slov se vejde do dvou set tokenů i s rezervou; vyšší
            // strop svádí model k rozepsání kulis, které sem nepatří.
            maxTokens = 220,
            teplota = 0.6,
        )

    /** Uzel, ze kterého se čte hotový text. */
    const val N_PREVIEW = ImagePromptBuilder.N_PREVIEW
}
