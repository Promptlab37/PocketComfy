package cz.promptlab.h3video

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Každý vylepšovač i překladač musí před prací zajistit běžící ComfyUI
 * (a když neběží, nechat ho nahodit). Do 4.46 to uměl jen generovací běh;
 * uživatel 25. 9. 2026: „musí to platit pro všechny přepisovače v appce".
 *
 * Hlídá to zdrojový kód: funkce, která nastavuje `RewriteState.Busy`, musí
 * volat `zajistiComfy` — jinak nový vylepšovač pojistku zase obejde.
 */
class PrepisZapneComfyTest {

    @Test fun `kazdy prepis nejdriv zajisti ComfyUI`() {
        val kod = File("src/main/java/cz/promptlab/h3video/MainViewModel.kt").readText()
        val funkce = Regex("\n    (?:private )?fun (\\w+)\\(").findAll(kod).map { it.range.first to it.groupValues[1] }.toList()
        val chybi = mutableListOf<String>()
        funkce.forEachIndexed { i, (od, jmeno) ->
            val telo = kod.substring(od, funkce.getOrNull(i + 1)?.first ?: kod.length)
            if ("RewriteState.Busy(" in telo && "zajistiComfy(" !in telo) chybi += jmeno
        }
        assertTrue("Bez zajištění ComfyUI: $chybi", chybi.isEmpty())
    }
}
