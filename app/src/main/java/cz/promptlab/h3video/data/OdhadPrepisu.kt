package cz.promptlab.h3video.data

/**
 * Odhad zbývajícího času přepisovače / překladače.
 *
 * Server po každém napsaném tokenu hlásí `value / max` (uzel `TextGenerate`,
 * `comfy.utils.ProgressBar(max_length)`). Odhad se proto počítá jako u
 * stahování — z rychlosti a z toho, kolik zbývá:
 *
 *  - **rychlost** se měří živě od prvního napsaného tokenu; dokud je vzorek
 *    malý, bere se rychlost z minulých přepisů,
 *  - **celkový počet tokenů** NENÍ `max` — to je jen strop a přepis obvykle
 *    skončí dřív (koncovým tokenem). Bere se, kolik tentýž přepisovač napsal
 *    posledně; když už se napsalo víc, cíl se posune, nejvýš ke stropu,
 *  - **před prvním tokenem** (načítání modelu) se k tomu přičte, jak dlouho
 *    trvalo načítání minule.
 *
 * Do 4.66 se odhad bral jako „kolik trval celý minulý přepis" — jenže jednou
 * byl model v grafice (16 s), jindy se načítal (150 s), a každý přepis napíše
 * jinak dlouhý text. Odhad pak neodpovídal ničemu.
 */
object OdhadPrepisu {

    /** Nejmenší vzorek pro živou rychlost: tokeny a sekundy od prvního tokenu. */
    const val MIN_TOKENU = 8
    const val MIN_SEKUND = 3.0

    data class Vysledek(
        /** Zbývající sekundy; null = nejde odhadnout (první použití, nic nepíše). */
        val zbyvaS: Long?,
        /** Hotová část 0–1 pro ukazatel; null = neurčitý. */
        val podil: Float?,
    )

    /**
     * @param ted aktuální čas (ms)
     * @param beziOd kdy server přepis převzal (ms), 0 = ještě ve frontě
     * @param prvniTokenOd kdy přišel první token (ms), 0 = ještě se nepíše
     * @param prvniHodnota kolik tokenů hlásil server v tu chvíli
     * @param napsano kolik tokenů je napsáno teď
     * @param strop `max` ze serveru (max_length), 0 = neznámý
     * @param obvykleTokeny kolik tentýž přepisovač napsal minule, 0 = neznámé
     * @param obvyklaRychlost tokeny za sekundu minule, 0 = neznámá
     * @param obvykleNacitaniS od převzetí po první token minule, 0 = neznámé
     * @param obvykleCelkemS jak dlouho trval minule celý přepis — jen náhrada
     *   pro přepisovače, které průběh po tokenech nehlásí (llama.cpp)
     */
    fun spocitej(
        ted: Long,
        beziOd: Long,
        prvniTokenOd: Long,
        prvniHodnota: Int,
        napsano: Int,
        strop: Int,
        obvykleTokeny: Int,
        obvyklaRychlost: Double,
        obvykleNacitaniS: Int,
        obvykleCelkemS: Int = 0,
    ): Vysledek {
        val cil = cilTokenu(napsano, strop, obvykleTokeny)

        // Píše se: rychlost živě, jakmile je vzorek dost velký.
        if (prvniTokenOd > 0L) {
            val sekund = (ted - prvniTokenOd) / 1000.0
            val pribylo = napsano - prvniHodnota
            val rychlost = when {
                pribylo >= MIN_TOKENU && sekund >= MIN_SEKUND -> pribylo / sekund
                obvyklaRychlost > 0 -> obvyklaRychlost
                else -> 0.0
            }
            val podil = if (cil > 0) (napsano.toFloat() / cil).coerceIn(0f, 0.99f) else null
            if (rychlost <= 0 || cil <= 0) return Vysledek(null, podil)
            val zbyva = ((cil - napsano).coerceAtLeast(0) / rychlost).toLong()
            return Vysledek(zbyva, podil)
        }

        // Ještě se nepíše (načítání modelu): minulé načítání + celé psaní.
        if (beziOd > 0L && obvyklaRychlost > 0 && cil > 0) {
            val nacitaniZbyva = (obvykleNacitaniS - (ted - beziOd) / 1000).coerceAtLeast(0)
            return Vysledek(nacitaniZbyva + (cil / obvyklaRychlost).toLong(), null)
        }
        // Přepisovač, který tokeny nehlásí: nic lepšího než minulá délka není.
        if (beziOd > 0L && obvyklaRychlost <= 0 && obvykleCelkemS > 0) {
            val bezi = (ted - beziOd) / 1000
            val zbyva = (obvykleCelkemS - bezi).coerceAtLeast(0)
            return Vysledek(zbyva, (bezi.toFloat() / obvykleCelkemS).coerceIn(0f, 0.97f))
        }
        return Vysledek(null, null)
    }

    /**
     * Kolik tokenů asi bude celkem. Minulá délka, ale nikdy méně než to, co už
     * je napsané (s malou rezervou), a nikdy víc než strop.
     */
    fun cilTokenu(napsano: Int, strop: Int, obvykleTokeny: Int): Int {
        val zaklad = if (obvykleTokeny > 0) obvykleTokeny else strop
        if (zaklad <= 0) return 0
        val cil = maxOf(zaklad, napsano + REZERVA)
        return if (strop > 0) minOf(cil, strop) else cil
    }

    /** O kolik tokenů nad napsané se cíl posune, když se přepis protáhne. */
    const val REZERVA = 20
}
