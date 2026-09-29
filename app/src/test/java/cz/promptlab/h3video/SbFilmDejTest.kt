package cz.promptlab.h3video

import cz.promptlab.h3video.data.LongMmRef
import cz.promptlab.h3video.data.SbFilmPlan
import cz.promptlab.h3video.data.SbFilmPrepis
import cz.promptlab.h3video.data.SbFilmScene
import cz.promptlab.h3video.data.SbPanel
import cz.promptlab.h3video.data.SbUsek
import cz.promptlab.h3video.data.SbZdroj
import cz.promptlab.h3video.data.sbFilmProblem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Film „Vytvořit z děje“: návrh záběrů bez obrázku storyboardu. */
class SbFilmDejTest {

    /** Typická odpověď modelu — časy nesedí s cílem (model počítá špatně). */
    private val navrh = "TITLE: Sůl v kávě | TOTAL: 20 | SHOTS: 4 " +
        "PANEL 1 | 00-05s | wide | static | Two friends in a kitchen, one pours coffee. " +
        "PANEL 2 | 05-09s | close-up | push-in | He secretly adds salt to the cup. " +
        "PANEL 3 | 09-13s | medium | static | His friend sips and spits the coffee out, \"Fuj! Co jsi tam dal?!\" " +
        "PANEL 4 | 13-20s | medium | static | Both burst out laughing."

    @Test
    fun `delka se dorovna presne na cil`() {
        for (cil in SbFilmScene.DELKY) {
            val p = SbFilmPlan.naplanujNavrh(navrh, cil)
            assertEquals(4, p.panely.size)
            assertEquals(cil.toDouble(), p.panely.sumOf { it.sekundy }, 0.05)
            assertFalse(p.zeStoryboardu)
            assertTrue(p.panely.all { it.sekundy >= SbFilmPlan.MIN_PANEL_S && it.sekundy <= SbFilmPlan.MAX_USEK_S })
        }
    }

    @Test
    fun `replika v uvozovkach zustane v popisu panelu`() {
        val p = SbFilmPlan.naplanujNavrh(navrh, 15)
        assertTrue(p.panely[2].popis.contains("Fuj! Co jsi tam dal?!"))
    }

    @Test
    fun `pocet panelu podle delky`() {
        assertEquals(4, SbFilmPlan.panelyNaDelku(15))
        assertEquals(9, SbFilmPlan.panelyNaDelku(30))
        assertEquals(12, SbFilmPlan.panelyNaDelku(45))
    }

    @Test
    fun `bez storyboardu jdou do H3 jen postavy`() {
        val s = SbFilmScene(
            storyboard = File("sb.png"), zdroj = SbZdroj.DEJ,
            postavy = listOf(LongMmRef(File("a.png")), LongMmRef(File("b.png"))),
        )
        assertFalse(s.seStoryboardem)
        assertEquals(listOf("a.png", "b.png"), s.uploadImages.map { it.name })
        assertTrue(s.copy(zdroj = SbZdroj.STORYBOARD).uploadImages.first().name == "sb.png")
    }

    @Test
    fun `hlidka bez storyboardu - obrazky jsou postavy, zadny storyboard`() {
        val u = SbUsek(listOf(SbPanel(1, "a", "wide", "static", 4.0), SbPanel(2, "b", "close-up", "static", 3.0)))
        val h = SbFilmPrepis.hlidka(2, u, 0, 1, seStoryboardem = false)
        assertFalse(h.contains("storyboard reference"))
        assertTrue(h.contains("<Picture 1>, <Picture 2> show the characters"))
        assertTrue(h.contains("[Shot 1] shot 1 of the film"))
        assertTrue(h.contains("[Shot 2] At 00:04.000, shot 2 of the film"))
    }

    @Test
    fun `validace z deje`() {
        val bez = SbFilmScene(zdroj = SbZdroj.DEJ, dej = "x")
        assertEquals("Přidej aspoň jednu fotku postavy.", sbFilmProblem(bez))
        val sPostavou = bez.copy(postavy = listOf(LongMmRef(File("a.png"))))
        assertEquals("Nejdřív nech navrhnout záběry.", sbFilmProblem(sPostavou))
        val sZabery = sPostavou.copy(panely = listOf(SbPanel(1, "a", sekundy = 4.0)))
        // Od 5.10 jde natočit až s napsaným scénářem.
        assertEquals("Nejdřív napiš scénář.", sbFilmProblem(sZabery))
        assertNull(sbFilmProblem(sZabery.copy(zadaniUseku = listOf("[Shot 1] x"))))
    }
}
