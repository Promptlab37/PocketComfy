package cz.promptlab.h3video.ui

import androidx.compose.ui.geometry.Offset

/**
 * Počty pro prohlížení obrázku na celou obrazovku.
 *
 * Jsou tu zvlášť, aby se daly ověřit testem. Právě tyhle vzorce totiž selhávají
 * tiše: špatné meze posunu se neprojeví pádem, jen se obrázek dá odtáhnout do
 * černa, a špatné zvětšení uteče od prstu. Na obojí se přijde až na displeji.
 */
object ProhlizecMatika {

    /** Nejmenší a největší přiblížení, stejně jako v galerii Androidu. */
    const val MIN = 1f
    const val MAX = 5f

    /**
     * O kolik pixelů se smí obrázek posunout od středu.
     *
     * Obrázek se vykresluje způsobem „vejde se celý", takže kolem něj zůstávají
     * černé pruhy a na obrazovce je menší, než je obrazovka sama. Meze se proto
     * počítají z jeho skutečné velikosti, ne z rozměrů obrazovky — jinak by
     * šel odtáhnout mimo.
     *
     * Když je obrázek menší než obrazovka (nebo je v základní velikosti),
     * vyjde nula: není kam posouvat.
     */
    fun meze(
        sirkaPlochy: Int, vyskaPlochy: Int,
        sirkaObrazku: Int, vyskaObrazku: Int,
        meritko: Float,
    ): Offset {
        if (sirkaPlochy <= 0 || vyskaPlochy <= 0) return Offset.Zero
        if (sirkaObrazku <= 0 || vyskaObrazku <= 0) return Offset.Zero
        val zaklad = minOf(
            sirkaPlochy.toFloat() / sirkaObrazku,
            vyskaPlochy.toFloat() / vyskaObrazku,
        )
        val sirka = sirkaObrazku * zaklad * meritko
        val vyska = vyskaObrazku * zaklad * meritko
        return Offset(
            ((sirka - sirkaPlochy) / 2f).coerceAtLeast(0f),
            ((vyska - vyskaPlochy) / 2f).coerceAtLeast(0f),
        )
    }

    /** Posun oříznutý na meze. */
    fun srovnej(
        posun: Offset,
        sirkaPlochy: Int, vyskaPlochy: Int,
        sirkaObrazku: Int, vyskaObrazku: Int,
        meritko: Float,
    ): Offset {
        val m = meze(sirkaPlochy, vyskaPlochy, sirkaObrazku, vyskaObrazku, meritko)
        if (m == Offset.Zero) return Offset.Zero
        return Offset(posun.x.coerceIn(-m.x, m.x), posun.y.coerceIn(-m.y, m.y))
    }

    /**
     * Nový posun tak, aby bod [bod] zůstal po změně přiblížení na svém místě.
     *
     * Obraz se vykresluje jako `střed + (bod − střed) × měřítko + posun`.
     * Z podmínky, že se [bod] nesmí hnout, vyjde vzorec níž — díky němu roste
     * obraz „od prstu", ne od středu obrazovky.
     */
    fun posunPriZvetseni(
        bod: Offset,
        stred: Offset,
        posun: Offset,
        meritko: Float,
        noveMeritko: Float,
    ): Offset {
        if (meritko <= 0f) return posun
        val v = bod - stred
        val k = noveMeritko / meritko
        return v - (v - posun) * k
    }
}
