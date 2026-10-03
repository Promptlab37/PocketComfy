package cz.promptlab.h3video.data

import cz.promptlab.h3video.comfy.H3RefWriteBuilder

/**
 * Doplňkový přepisovač pro Storyboard a Storyboard + scénář.
 *
 * Model i systémový prompt dodává třída [TRIDA_ZDROJE], pokud je v sestavení. Bez ní se nic
 * nenačte, [aktivni] vrací `null` a přepis běží přesně jako dosud (stejný `writer_model`,
 * žádný `system_prompt`).
 */
object DoplnkovyPrepisovac {

    /**
     * @param souborModelu jméno GGUF na serveru; uzel ho nabízí jako „on disk: <soubor> [arch, velikost]“
     * @param systemPrompt přesně ten text, který dostane vstup `system_prompt` uzlu
     */
    data class Volba(val souborModelu: String, val systemPrompt: String)

    /** Rozhraní třídy zdroje (Kotlin `object`). */
    fun interface Zdroj {
        fun volba(): Volba
    }

    const val TRIDA_ZDROJE = "cz.promptlab.h3video.doplnek.DoplnekPrepisovace"

    /** Přepínač z Nastavení. */
    @Volatile var zapnuto: Boolean = false

    /** Co dodal zdroj; bez něj v sestavení `null`. */
    val volba: Volba? by lazy { nacti() }

    internal fun nacti(trida: String = TRIDA_ZDROJE): Volba? = runCatching {
        val c = Class.forName(trida)
        (c.getField("INSTANCE").get(null) as Zdroj).volba()
    }.getOrNull()

    /** Volba pro tento přepis: dodaný zdroj a zapnutý přepínač. */
    fun aktivni(): Volba? = if (zapnuto) volba else null

    /** Položka nabídky `writer_model` pro [Volba.souborModelu], `null` = na serveru není. */
    fun polozka(nabidka: List<String>, volba: Volba): String? =
        nabidka.firstOrNull { it.startsWith("on disk: ${volba.souborModelu} [") }

    /** `writer_model` a `system_prompt` pro uzel `MiniMaxH3UniversalWriter`. */
    data class Pisatel(val writer: String?, val systemPrompt: String?)

    /**
     * S [volba] a jejím modelem v nabídce položka nabídky doslova (uzel porovnává celý řetězec
     * včetně velikosti) a její systémový prompt. Jinak — bez volby nebo bez modelu na serveru —
     * přesně dosavadní výběr ([H3RefWriteBuilder.vyberOdblokovany], žádný `system_prompt`).
     */
    fun pisatel(nabidka: List<String>, volba: Volba?): Pisatel {
        val model = volba?.let { polozka(nabidka, it) }
        return if (volba != null && model != null) Pisatel(model, volba.systemPrompt)
        else Pisatel(H3RefWriteBuilder.vyberOdblokovany(nabidka, H3RefWriteBuilder.WRITER_ODVAZANY), null)
    }
}
