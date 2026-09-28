package cz.promptlab.h3video

import androidx.compose.ui.graphics.Color
import cz.promptlab.h3video.ui.theme.Motiv
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/**
 * Každý vzhled musí být čitelný (WCAG): text na pozadí i kartě ≥ 4,5 : 1,
 * text na hlavním tlačítku ≥ 3 : 1 (tučné 16 sp se počítá jako velký text).
 * Rešerše 28. 9. 2026 — na zelené proto tmavý text, ne bílý.
 */
class VzhledKontrastTest {

    private fun kanal(c: Float): Double = if (c <= 0.03928f) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    private fun jas(c: Color): Double = 0.2126 * kanal(c.red) + 0.7152 * kanal(c.green) + 0.0722 * kanal(c.blue)
    private fun kontrast(a: Color, b: Color): Double {
        val (s, t) = listOf(jas(a), jas(b)).sortedDescending()
        return (s + 0.05) / (t + 0.05)
    }

    @Test fun `text je citelny v kazdem vzhledu`() {
        val chyby = mutableListOf<String>()
        Motiv.entries.forEach { m ->
            listOf("textHi" to m.textHi, "textMid" to m.textMid, "textLow" to m.textLow, "accent" to m.accent).forEach { (jm, c) ->
                listOf("bg" to m.bg, "surface1" to m.surface1).forEach { (pj, p) ->
                    val k = kontrast(c, p)
                    if (k < 4.5) chyby += "${m.name}: $jm na $pj = %.2f".format(k)
                }
            }
            // Původní vzhled zůstává přesně jako do 4.72 (přání uživatele).
            if (m != Motiv.PUVODNI) m.cta.forEach { c ->
                val k = kontrast(m.naAkcentu, c)
                if (k < 3.0) chyby += "${m.name}: text tlačítka = %.2f".format(k)
            }
        }
        assertTrue(chyby.joinToString("\n"), chyby.isEmpty())
    }
}
