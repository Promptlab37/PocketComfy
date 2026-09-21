package cz.promptlab.h3video.comfy

import cz.promptlab.h3video.data.LtxRezim
import org.json.JSONArray
import org.json.JSONObject

/**
 * Stavitel grafů pro **✨ Vylepšit popis** na kartě LTX 2.5.
 *
 * ### Jak se LTX 2.5 promptuje
 *
 * LTX 2.5 není model, kterému se píší klíčová slova. Je učený na **dlouhých
 * popiskách jednoho záběru**, které skládají obraz i zvuk zároveň — proto
 * karta chce jeden souvislý odstavec o zhruba 150–220 slovech, ne odrážky.
 * Závazná pravidla (zjištěno z `comfy_extras/nodes_textgen.py`, uzel
 * `TextGenerateLTX2Prompt`, větev `gemma4`, což je přesně ta, kterou naše
 * předloha s `gemma4-12b-with-proj-ltx-2.5` používá):
 *
 *  - začít rovnou dějem, nikdy „The scene opens…",
 *  - popisovat **jen pozorovatelné** — ne „vypadá smutně", ale co je vidět,
 *  - u každého záběru uvést **trojici**: velikost záběru, pohyb kamery
 *    (i když se nehýbe, musí to být řečeno) a odkud kamera kouká,
 *  - zvuk vplétat **průběžně k ději**, ne jako seznam na konci,
 *  - repliky doslova v uvozovkách i s popisem hlasu,
 *  - chronologicky, jeden nepřerušený záběr, bez střihů a časů,
 *  - jeden odstavec, anglicky, bez nadpisů a odrážek.
 *
 * Systémové prompty níž jsou **vlastní formulace** těchto pravidel, ne
 * kopie z ComfyUI — ten je pod GPL-3.0 a doslovný opis by tu licenci
 * zatáhl do appky. Pravidla samotná jsou fakta o modelu, ta se přebírat
 * smí. (Stejná úvaha jako u příruček MiniMaxu v [H3RefWriteBuilder], jen
 * s jiným závěrem: tam nešlo ani to, tady stačí přepsat vlastními slovy.)
 *
 * ### Dvě cesty, dvě tlačítka
 *
 *  1. **Oficiální** — uzel `TextGenerateLTX2Prompt` nad `gemma4` enkodérem.
 *     Je to tentýž soubor, který si karta stejně načte kvůli generování,
 *     takže nic navíc se nestahuje. Model je na LTX vycvičený, ale odvážnější
 *     zadání potichu zjemní — stejně jako Qwenův PE-I2I u karty Úprava.
 *  2. **Odvázaná** — odblokovaný Qwen3-VL přes llama.cpp ([ImagePromptBuilder]).
 *     Nic nepřepisuje; pravidla LTX dostane v systémovém promptu.
 */
object Ltx25PromptBuilder {

    // ---------------------------------------------------------------- oficiální

    const val N_CLIP = "1"
    const val N_GEN = "10"
    const val N_PREVIEW = "20"
    const val N_OBRAZEK = "100"

    const val NODE_CLASS = "TextGenerateLTX2Prompt"
    const val LOADER_CLASS = "CLIPLoader"

    /** Typ enkodéru; `ltxv` je jediný, pod kterým ComfyUI gemma4 pro LTX načte. */
    const val CLIP_TYPE = "ltxv"

    /**
     * Strop odpovědi. Cílová délka je 150–220 slov, tedy zhruba 300 tokenů;
     * 512 je výchozí hodnota uzlu a nechává rezervu, aniž by se běh protáhl.
     */
    const val MAX_TOKENU = 512

    /**
     * Graf oficiálního přepisovače.
     *
     * @param encoder jméno souboru enkodéru **z předlohy karty** — ne
     *   vybrané podle jména, aby přepisovač a generování jely nad týmž
     *   modelem i po jeho výměně
     * @param obrazek název nahrané fotky prvního snímku, nebo null u „Z textu";
     *   uzel si podle přítomnosti obrázku sám vybere i2v/t2v systémový prompt
     */
    fun buildOficialni(
        zadani: String,
        encoder: String,
        obrazek: String?,
        seed: Long,
        maxTokenu: Int = MAX_TOKENU,
    ): JSONObject {
        val wf = JSONObject()
        wf.put(
            N_CLIP,
            uzel(
                LOADER_CLASS, "Enkodér LTX 2.5",
                JSONObject()
                    .put("clip_name", encoder)
                    .put("type", CLIP_TYPE)
                    .put("device", "default"),
            ),
        )

        val vstupy = JSONObject()
            .put("clip", odkaz(N_CLIP))
            .put("prompt", zadani)
            .put("max_length", maxTokenu)
            .put("sampling_mode", "on")
            .put("sampling_mode.temperature", 0.7)
            .put("sampling_mode.top_k", 64)
            .put("sampling_mode.top_p", 0.95)
            .put("sampling_mode.min_p", 0.05)
            .put("sampling_mode.repetition_penalty", 1.05)
            .put("sampling_mode.seed", seed)
            // Uvažování nahlas by na domácí kartě sežralo celý strop tokenů
            // dřív, než se model dostane k popisku. Ověřeno u Qwenova PE.
            .put("thinking", false)

        if (obrazek != null) {
            wf.put(N_OBRAZEK, uzel("LoadImage", "První snímek", JSONObject().put("image", obrazek)))
            vstupy.put("image", odkaz(N_OBRAZEK))
        }

        wf.put(N_GEN, uzel(NODE_CLASS, "Vylepšení popisu (LTX)", vstupy))
        wf.put(
            N_PREVIEW,
            uzel("PreviewAny", "Výsledek", JSONObject().put("source", odkaz(N_GEN))),
        )
        return wf
    }

    // ---------------------------------------------------------------- odvázaná

    /** Kde v odvázaném grafu sedí náhled; graf staví [ImagePromptBuilder]. */
    const val N_PREVIEW_ODVAZANY = ImagePromptBuilder.N_PREVIEW

    /**
     * Strop odpovědi u odvázaného modelu. 220 slov je kolem 300 tokenů,
     * 480 nechává rezervu a drží běh v jednotkách sekund.
     */
    const val MAX_TOKENU_ODVAZANY = 480

    /**
     * Graf odvázaného přepisovače. Fotku dostane jen tehdy, když je na serveru
     * projektor — bez něj by ji model dostal, ale neviděl.
     */
    fun buildOdvazany(
        rezim: LtxRezim,
        zadani: String,
        sekundy: Float,
        model: String,
        seed: Long,
        mmproj: String = "None",
        obrazky: List<String> = emptyList(),
    ): JSONObject = ImagePromptBuilder.graf(
        zadani = sKontextem(rezim, zadani, sekundy, obrazky.isNotEmpty()),
        model = model,
        seed = seed,
        system = systemProRezim(rezim),
        maxTokens = MAX_TOKENU_ODVAZANY,
        // Popisek má být konkrétní a poslušný, ne básnický.
        teplota = 0.6,
        mmproj = mmproj,
        obrazky = obrazky,
    )

    /** Systémový prompt podle toho, co karta modelu opravdu dá. */
    fun systemProRezim(rezim: LtxRezim): String = when (rezim) {
        LtxRezim.TEXT -> SYSTEM_T2V
        LtxRezim.OBRAZEK -> SYSTEM_I2V
        LtxRezim.ZVUK -> SYSTEM_ZVUK
    }

    /**
     * Obalí zadání kontextem: jak dlouhé video vzniká a jestli je přiložená
     * fotka opravdu vidět. Délka se hodí — model podle ní rozvrhne děj tak,
     * aby se do záběru vešel.
     */
    fun sKontextem(
        rezim: LtxRezim,
        zadani: String,
        sekundy: Float,
        vidiFotku: Boolean,
    ): String {
        val delka = "The finished video is about %.0f seconds long, one continuous take."
            .format(sekundy.coerceAtLeast(1f))
        val fotka = when {
            !rezim.chceObrazek -> ""
            vidiFotku -> " The attached photo is the exact first frame of that video."
            // Fotka existuje, ale model ji nevidí (chybí projektor). Ať si ji
            // nevymýšlí — jinak popíše scénu, která na prvním snímku není.
            else -> " A photo will be used as the first frame, but you cannot see it. " +
                "Describe only what the request itself states and do not invent " +
                "details of the setting or of anyone's appearance."
        }
        return "$delka$fotka\n\nRequest: $zadani"
    }

    // ------------------------------------------------------------ systémové prompty

    /**
     * Společná část pravidel. Je v obou promptech stejná, proto se skládá —
     * dvě ručně udržované kopie by se rozešly.
     */
    private const val SPOLECNE = """
WHAT TO WRITE
- One single paragraph of flowing English prose, roughly 150 to 220 words.
  No headings, no bullet points, no labels such as "Audio:" or "Visual:",
  no markdown, no preamble, no quotation marks around the whole caption.
- Open on the action or on a visual detail. Never open with "The scene
  opens", "We see", "There is" or "The video starts".
- Keep everything the request states. Expand around it; never drop,
  replace or contradict anything the user asked for.

HOW TO DESCRIBE
- Only what a camera and a microphone would record. No emotions, no
  intentions, no smell or touch. Instead of "he looks nervous", write what
  is visible: "his jaw tightens and his fingers tap the table".
- Describe the place (materials, textures, light, colour) and each person
  (apparent gender, skin tone, rough age group, hair, build, clothing,
  posture) in enough detail that several people stay clearly apart. Do not
  state nationality, ethnicity, religion or culture.
- Weave these three into the prose, never as labels:
  * the shot size, exactly one of extreme wide shot, wide shot, medium
    shot, medium close-up, close-up, extreme close-up;
  * the camera move, always stated, and if the camera does not move say so
    explicitly. Use the move the user asked for; if they asked for none,
    choose the one that presents the scene best.
  * where the camera sits: front-facing, back-facing, side view,
    over-the-shoulder, top-down, low angle or high angle.
  Written like this: "a medium shot frames her from a front-facing angle as
  the camera drifts slowly to the left".
- Move through time in order, with connectors such as "Initially",
  "A moment later", "Simultaneously", "as" and "while". Keep the action
  running to the end of the caption.
- One continuous take. Never invent cuts, scene changes or timestamps.
- Restrained, plain wording. Add production value the same observable way:
  "warm low sunlight", "film-grade colour", "crisp fine texture". Never add
  objects or actions nobody asked for.

The request may be written in any language. The caption is always English.
Output the caption itself and nothing else."""

    /** Z textu: model si skládá obraz i zvuk, takže se popisuje obojí. */
    const val SYSTEM_T2V: String =
        """You turn a short request into ONE caption for the LTX 2.5 video model,
which generates picture and sound together from that single caption.
$SPOLECNE

SOUND
- Sound belongs inside the sentences, next to the action it accompanies,
  never collected at the end. Name it precisely: "boots scuffing wet
  concrete", not "ambient noise". Cover room tone, effects, and music when
  it is wanted.
- Speech: give the exact words in quotation marks and say how the voice
  sounds. Keep the user's own lines word for word, fixing only typos. Add
  dialogue only if the request mentions talking, singing or conversation."""

    /** Z obrázku: první snímek je daný, a popisek na něm musí začít. */
    const val SYSTEM_I2V: String =
        """You turn a short request and a reference photo into ONE caption for the
LTX 2.5 video model, which generates picture and sound together from that
single caption. The photo is the exact first frame of the video.

FIRST FRAME
- Begin from the photo. The people, their faces, clothing, the setting, the
  light and the framing at the start of your caption must match it. Then
  narrate, in order, what the request asks to happen from that frame on.
- Do not describe the photo at length as if nothing moves, and do not
  contradict it or add things that are not in it.
$SPOLECNE

SOUND
- Sound belongs inside the sentences, next to the action it accompanies,
  never collected at the end. Name it precisely: "boots scuffing wet
  concrete", not "ambient noise". Cover room tone, effects, and music when
  it is wanted.
- Speech: give the exact words in quotation marks and say how the voice
  sounds. Keep the user's own lines word for word, fixing only typos. Add
  dialogue only if the request mentions talking, singing or conversation."""

    /**
     * Ze zvuku: zvuková stopa je hotový soubor, který graf zamkne
     * (`SetLatentNoiseMask` s nulovou maskou). Popisovat vymyšlený ruch nebo
     * hudbu by tahalo obraz proti tomu, co je opravdu slyšet — proto se tady
     * zvuk popisuje jen potud, pokud je z něj něco vidět.
     */
    const val SYSTEM_ZVUK: String =
        """You turn a short request and a reference photo into ONE caption for the
LTX 2.5 video model. The photo is the exact first frame. The soundtrack is
a finished recording the user supplies: it is already fixed and the caption
cannot change it.

FIRST FRAME
- Begin from the photo. The people, their faces, clothing, the setting, the
  light and the framing at the start of your caption must match it. Then
  narrate, in order, what the request asks to happen from that frame on.
- Do not contradict the photo or add things that are not in it.

THE SUPPLIED SOUND
- Describe sound only where it is visible: mouth and jaw movement while
  speaking or singing, breathing, a head nodding in time, a hand striking
  an instrument. Keep the performance running for the whole caption.
- Do not invent music, effects or background noise. Anything you add that
  is not on the recording pulls the picture away from what is heard.
- If the request quotes what is said, keep those words exactly and describe
  how they are delivered, but do not add lines of your own.
$SPOLECNE"""

    // ---------------------------------------------------------------- pomocné

    private fun uzel(cls: String, titulek: String, vstupy: JSONObject) = JSONObject()
        .put("class_type", cls)
        .put("inputs", vstupy)
        .put("_meta", JSONObject().put("title", titulek))

    private fun odkaz(uzel: String, slot: Int = 0) = JSONArray().put(uzel).put(slot)
}
