package cz.promptlab.h3video.engine

/**
 * Filtr hlášek uzlů, které se ukazují v rámečku „Co k tomu řekly uzly".
 *
 * Rámeček má smysl jen pro věci, se kterými uživatel může něco udělat —
 * replika, kterou model neudělal jako dialog, prázdné povinné pole.
 * Některé hlášky ComfyUI ale vypisuje při KAŽDÉM běhu dané karty, jsou
 * neškodné a na výsledek nemají vliv; pak jen budí dojem, že je něco
 * rozbité. Ty se sem přidávají — vždy s důvodem, proč je neškodná.
 */
object NodeWarnings {

    /**
     * Hlášky, které se schovávají:
     *
     * 1. `lora … img_in.weight shape '[3072, 384]' is invalid for input of
     *    size 196608` — karta Výměna tváře. FLUX.1-Turbo-Alpha je trénovaná
     *    na Flux dev (vstupní vrstva 64 kanálů), inpaint model Fill jich má
     *    384. ComfyUI proto přeskočí JEDINÝ klíč (`x_embedder`) a zbytek
     *    LoRA aplikuje normálně — ověřeno v hlavičce obou souborů 2. 9. 2026.
     *    Stejně to má každé ACE++ workflow.
     * 2. `clip missing: ['text_projection.weight']` — standardní hláška
     *    dvojitého CLIP loaderu u Fluxu, ten klíč se nepoužívá.
     */
    private val NESKODNE = listOf(
        Regex("""lora .*img_in\.weight shape .* is invalid for input of size""", RegexOption.IGNORE_CASE),
        Regex("""clip missing: \['text_projection\.weight'\]""", RegexOption.IGNORE_CASE),
    )

    /** Je hláška známá a neškodná (tedy se uživateli neukazuje)? */
    fun jeNeskodna(hlaska: String): Boolean = NESKODNE.any { it.containsMatchIn(hlaska) }

    /** Ponechá jen hlášky, které uživateli něco řeknou. */
    fun filtruj(hlasky: List<String>): List<String> = hlasky.filterNot { jeNeskodna(it) }
}
