package cz.promptlab.h3video

import cz.promptlab.h3video.comfy.AngleBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Půdorys ovladače na kartě **Úhel kamery**.
 *
 * Do 3.80 byl zrcadlově obrácený: „zprava" se kreslilo vlevo. Kdo tedy táhl
 * kameru doleva v domnění, že chce pohled zleva, dostal pravý bok — a protože
 * ve stejné době nefungovala LoRA, vypadalo to jako jedna chyba, přitom byly
 * dvě. Tenhle test hlídá, že souřadnice sedí na `QwenMultiangleCameraNode`.
 *
 * Uzel počítá `cam_x = sin(azimut)`, `cam_z = cos(azimut)` a 90° pojmenovává
 * `right side view`. Na obrazovce je `+x` vpravo a půdorys má „zepředu" dole,
 * takže **90° musí padnout napravo**.
 */
class AngleDialTest {

    /** Stejný výpočet jako `bodNaKruhu` v `AngleDial.kt`. */
    private fun bod(stupne: Float): Pair<Float, Float> {
        val rad = Math.toRadians(stupne.toDouble())
        return sin(rad).toFloat() to cos(rad).toFloat()
    }

    /** Stejný výpočet jako převod dotyku na úhel. */
    private fun uhel(x: Float, y: Float): Float =
        Math.toDegrees(atan2(x.toDouble(), y.toDouble())).toFloat()

    @Test
    fun `zepredu je dole, zezadu nahore`() {
        val (xz, yz) = bod(AngleBuilder.uhelProSmer(0))   // zepředu
        assertEquals(0f, xz, 1e-4f)
        assertTrue("zepředu patří dolů (kladné y)", yz > 0.9f)

        val (xb, yb) = bod(AngleBuilder.uhelProSmer(4))   // zezadu
        assertEquals(0f, xb, 1e-4f)
        assertTrue("zezadu patří nahoru (záporné y)", yb < -0.9f)
    }

    @Test
    fun `zprava je napravo a zleva nalevo, jako v uzlu`() {
        val zprava = AngleBuilder.AZIMUTY.indexOfFirst { it.first == "right side view" }
        val zleva = AngleBuilder.AZIMUTY.indexOfFirst { it.first == "left side view" }
        assertTrue(zprava >= 0 && zleva >= 0)

        val (xp, _) = bod(AngleBuilder.uhelProSmer(zprava))
        val (xl, _) = bod(AngleBuilder.uhelProSmer(zleva))
        assertTrue("„zprava\" se musí kreslit napravo", xp > 0.9f)
        assertTrue("„zleva\" se musí kreslit nalevo", xl < -0.9f)
    }

    @Test
    fun `dotyk a kresleni jsou navzajem opacne`() {
        AngleBuilder.AZIMUTY.indices.forEach { i ->
            val (x, y) = bod(AngleBuilder.uhelProSmer(i))
            assertEquals("směr $i", i, AngleBuilder.smerZUhlu(uhel(x, y)))
        }
    }

    @Test
    fun `klepnuti nalevo vybere zleva`() {
        // Bod nalevo od středu: x záporné, y nula.
        val zleva = AngleBuilder.AZIMUTY.indexOfFirst { it.first == "left side view" }
        assertEquals(zleva, AngleBuilder.smerZUhlu(uhel(-1f, 0f)))
        val zprava = AngleBuilder.AZIMUTY.indexOfFirst { it.first == "right side view" }
        assertEquals(zprava, AngleBuilder.smerZUhlu(uhel(1f, 0f)))
    }

    /**
     * Bokorys nesmí kreslit mimo sebe. Do 3.80 se délka ramene počítala jen
     * ze šířky, takže nadhled (60°) vystřelil rameno stovky pixelů nad plátno
     * a kreslilo se přes půdorys nad ním.
     */
    @Test
    fun `rameno bokorysu se vejde na vysku platna`() {
        val sirka = 1000f
        val vyskaPlatna = 470f          // 170.dp při běžné hustotě
        val zakladY = vyskaPlatna - 122f // 44.dp od spodku
        val nejvyssi = AngleBuilder.VYSKA_STUPNE.max()
        val stropVys = (zakladY - 128f) / sin(Math.toRadians(nejvyssi.toDouble())).toFloat()
        val delka = minOf(sirka - 266f, stropVys)

        AngleBuilder.VYSKA_STUPNE.forEach { stupen ->
            val y = zakladY - 20f - delka * sin(Math.toRadians(stupen.toDouble())).toFloat()
            assertTrue("výška $stupen° vyjela nad plátno (y = $y)", y >= 0f)
            assertTrue("výška $stupen° vyjela pod plátno (y = $y)", y <= vyskaPlatna)
        }
        assertTrue("rameno musí zůstat kladné", delka > 0f)
        // A pořád se musí vejít i na šířku.
        assertTrue(abs(delka) <= sirka)
    }
}
