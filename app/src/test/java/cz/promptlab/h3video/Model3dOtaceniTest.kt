package cz.promptlab.h3video

import cz.promptlab.h3video.ui.natoceniModelu
import dev.romainguy.kotlin.math.Float3
import dev.romainguy.kotlin.math.inverse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Otáčení 3D modelu prstem musí jít **za prstem** — a to i tehdy, když je
 * model už otočený zády.
 *
 * Právě tohle se dřív rozbíjelo: natočení se počítalo Eulerovými úhly, takže
 * svislý tah točil kolem osy MODELU, ne obrazovky. Sotva se model pootočil do
 * strany (a samovolné otáčení ho pootočí hned po otevření), překlápěl se
 * obráceně. Test to hlídá z několika výchozích poloh, ne jen z té základní.
 */
class Model3dOtaceniTest {

    /** Bod na přední ploše — ten kousek modelu, na který se uživatel dívá. */
    private val predni = Float3(0f, 0f, 1f)

    /**
     * Kam se na obrazovce posune kousek modelu, který byl vepředu, když se
     * natočení změní z [zYaw]/[zPitch] na [naYaw]/[naPitch].
     *
     * Osy světa jsou i osy obrazovky: +x doprava, +y nahoru, +z ke kameře.
     */
    private fun posunPredniho(zYaw: Float, zPitch: Float, naYaw: Float, naPitch: Float): Float3 {
        val kousekModelu = inverse(natoceniModelu(zYaw, zPitch)) * predni
        return natoceniModelu(naYaw, naPitch) * kousekModelu
    }

    @Test
    fun `tah doprava otaci model doprava`() {
        val p = posunPredniho(0f, 0f, 30f, 0f)
        assertTrue("přední kousek má jít doprava, je na x=${p.x}", p.x > 0.2f)
        assertTrue("vodorovný tah nemá překlápět, y=${p.y}", abs(p.y) < 0.05f)
    }

    @Test
    fun `tah nahoru preklopi model nahoru`() {
        val p = posunPredniho(0f, 0f, 0f, 30f)
        assertTrue("přední kousek má jít nahoru, je na y=${p.y}", p.y > 0.2f)
        assertTrue("svislý tah nemá otáčet do strany, x=${p.x}", abs(p.x) < 0.05f)
    }

    @Test
    fun `tah nahoru preklopi nahoru i kdyz je model otoceny zady`() {
        for (yaw in listOf(90f, 150f, 180f, 270f, -120f)) {
            val p = posunPredniho(yaw, 0f, yaw, 30f)
            assertTrue("při natočení $yaw° jde přední kousek dolů (y=${p.y})", p.y > 0.2f)
        }
    }

    @Test
    fun `tah doprava otaci doprava i pri preklopenem modelu`() {
        for (pitch in listOf(-60f, -30f, 30f, 60f)) {
            val p = posunPredniho(0f, pitch, 30f, pitch)
            assertTrue("při překlopení $pitch° jde přední kousek doleva (x=${p.x})", p.x > 0.2f)
        }
    }
}
