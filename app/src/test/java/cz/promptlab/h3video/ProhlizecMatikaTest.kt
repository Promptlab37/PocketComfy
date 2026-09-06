package cz.promptlab.h3video

import androidx.compose.ui.geometry.Offset
import cz.promptlab.h3video.ui.ProhlizecMatika
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Prohlížení obrázku na celou obrazovku se má chovat jako galerie Androidu.
 *
 * Obojí, co se tu hlídá, selhává **tiše** — nic nespadne, jen se to špatně
 * ovládá: obrázek se dá odtáhnout do černa a přiblížení uteče od prstu.
 */
class ProhlizecMatikaTest {

    // Obrazovka na výšku a fotka na šířku — vznikne letterbox nahoře a dole.
    private val plochaW = 1080
    private val plochaH = 2000
    private val fotkaW = 4000
    private val fotkaH = 3000

    private fun meze(meritko: Float) =
        ProhlizecMatika.meze(plochaW, plochaH, fotkaW, fotkaH, meritko)

    @Test
    fun `v zakladni velikosti neni kam posouvat`() {
        // Fotka se celá vejde, kolem ní jsou černé pruhy. Kdyby meze vycházely
        // z rozměrů obrazovky, dala by se odtáhnout právě do těch pruhů.
        assertEquals(Offset.Zero, meze(1f))
        assertEquals(
            Offset.Zero,
            ProhlizecMatika.srovnej(
                Offset(500f, 900f), plochaW, plochaH, fotkaW, fotkaH, 1f,
            ),
        )
    }

    @Test
    fun `meze odpovidaji skutecne velikosti obrazku, ne obrazovky`() {
        // Fotka 4:3 na obrazovce 1080×2000: vejde se na šířku, tedy je
        // 1080×810. Při dvojnásobku je 2160×1620 — vodorovně přeteče
        // o 1080 px (mez 540), svisle je pořád menší než obrazovka (mez 0).
        val m = meze(2f)
        assertEquals(540f, m.x, 0.5f)
        assertEquals(0f, m.y, 0.5f)

        // Chybný výpočet z rozměrů obrazovky by dal svislou mez 1000 px.
        assertTrue("svisle se nemá kam hnout, fotka je nižší než obrazovka", m.y < 1f)
    }

    @Test
    fun `posun se orizne na meze`() {
        val daleko = Offset(9_000f, 9_000f)
        val srovnany = ProhlizecMatika.srovnej(
            daleko, plochaW, plochaH, fotkaW, fotkaH, 2f,
        )
        assertEquals(540f, srovnany.x, 0.5f)
        assertEquals(0f, srovnany.y, 0.5f)
    }

    @Test
    fun `pri velkem zvetseni jde posouvat obema smery`() {
        val m = meze(4f)
        assertTrue("vodorovně: ${m.x}", m.x > 1000f)
        assertTrue("svisle: ${m.y}", m.y > 600f)
    }

    /** Kde na obrazovce skončí bod obrazu při daném měřítku a posunu. */
    private fun naObrazovce(bod: Offset, stred: Offset, meritko: Float, posun: Offset) =
        stred + (bod - stred) * meritko + posun

    @Test
    fun `zvetseni drzi bod pod prstem na miste`() {
        val stred = Offset(plochaW / 2f, plochaH / 2f)
        val prst = Offset(200f, 1500f)          // daleko od středu, schválně

        // Kde je ten kus obrazu, který je teď pod prstem?
        val meritko = 1.4f
        val posun = Offset(-40f, 25f)
        val kusObrazu = stred + (prst - stred - posun) / meritko

        val cil = 3.2f
        val novyPosun = ProhlizecMatika.posunPriZvetseni(prst, stred, posun, meritko, cil)
        val kdeJe = naObrazovce(kusObrazu, stred, cil, novyPosun)

        assertTrue(
            "bod pod prstem ujel na $kdeJe místo $prst",
            abs(kdeJe.x - prst.x) < 0.5f && abs(kdeJe.y - prst.y) < 0.5f,
        )
    }

    @Test
    fun `zvetseni kolem stredu se chova jako driv`() {
        val stred = Offset(plochaW / 2f, plochaH / 2f)
        val novy = ProhlizecMatika.posunPriZvetseni(stred, stred, Offset.Zero, 1f, 2.5f)
        assertEquals(Offset.Zero, novy)
    }

    @Test
    fun `nesmyslne rozmery nic nerozbiji`() {
        assertEquals(Offset.Zero, ProhlizecMatika.meze(0, 0, 100, 100, 2f))
        assertEquals(Offset.Zero, ProhlizecMatika.meze(100, 100, 0, 0, 2f))
        val p = Offset(5f, 5f)
        assertEquals(p, ProhlizecMatika.posunPriZvetseni(Offset.Zero, Offset.Zero, p, 0f, 2f))
    }
}
