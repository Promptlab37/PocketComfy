package cz.promptlab.h3video

import cz.promptlab.h3video.data.SbCteni
import cz.promptlab.h3video.data.SbFilmPlan
import cz.promptlab.h3video.data.SbPanel
import cz.promptlab.h3video.data.SbPrecteny
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Plán filmu ze storyboardu: čtení odpovědi modelu, délky panelů, úseky. */
class SbFilmPlanTest {

    /** Odpověď ve tvaru, o který si appka říká (Iron Mask od testera). */
    private val ironMask = """
        TITLE: The Man in the Iron Mask | TOTAL: 30 | SHOTS: 8
        PANEL 1 | 00-04s | wide | slow push-in | A masked man waits in the moonlit cell.
        PANEL 2 | 04-07s | insert | static | A stolen key turns. The door unlocks.
        PANEL 3 | 07-11s | medium | dolly forward | Two musketeers step out of the shadows.
        PANEL 4 | 11-14s | medium | gentle tilt down | The prisoner is freed from his chains.
        PANEL 5 | 14-18s | detail | slow push-in | The iron mask is finally unlocked.
        PANEL 6 | 18-23s | close-up | slow arc | Behind the iron: Louis XIV, King of France.
        PANEL 7 | 23-27s | medium | pull back | The rescuers kneel before their king.
        PANEL 8 | 27-30s | wide | tracking shot | Louis leaves the darkness behind.
    """.trimIndent()

    /** Skutečná odpověď přepisovače na storyboard Iron Mask (28. 9. 2026) — jeden řádek. */
    private val ironMaskZeServeru = "TITLE: THE MAN IN THE IRON MASK | TOTAL: 30 | SHOTS: 8 PANEL 01 | 00-04s | wide | slow push-in | A masked man waits in the moonlit cell. PANEL 02 | 04-07s | insert | static | A stolen key turns. The door unlocks. PANEL 03 | 07-11s | low angle | dolly forward | Two musketeers step out of the shadows. PANEL 04 | 11-14s | medium shot | gentle tilt down | The prisoner is freed from his chains. PANEL 05 | 14-18s | detail shot | slow push-in | The iron mask is finally unlocked. PANEL 06 | 18-23s | close-up | slow arc | Behind the iron: Louis XIV, King of France. PANEL 07 | 23-27s | low angle | pull back | The rescuers kneel before their king. PANEL 08 | 27-30s | tracking shot | follow forward | Louis leaves the darkness behind."

    @Test
    fun `skutecna odpoved na jednom radku`() {
        val c = SbFilmPlan.precti(ironMaskZeServeru)
        assertEquals("THE MAN IN THE IRON MASK", c.nazev)
        assertEquals(8, c.panely.size)
        assertEquals("Louis leaves the darkness behind.", c.panely.last().popis)
        val plan = SbFilmPlan.naplanuj(c)
        assertTrue(plan.zeStoryboardu)
        assertEquals(30.0, plan.panely.sumOf { it.sekundy }, 0.001)
    }

    @Test
    fun `cteni - hlavicka i panely`() {
        val c = SbFilmPlan.precti(ironMask)
        assertEquals("The Man in the Iron Mask", c.nazev)
        assertEquals(30.0, c.celkemVepsano!!, 0.001)
        assertEquals(8, c.zaberuVepsano)
        assertEquals(8, c.panely.size)
        assertEquals(0.0, c.panely[0].od!!, 0.001)
        assertEquals(4.0, c.panely[0].doS!!, 0.001)
        assertEquals("insert", c.panely[1].typ)
        assertEquals("A stolen key turns. The door unlocks.", c.panely[1].popis)
    }

    @Test
    fun `vepsane casy se pouziji, kdyz sedi`() {
        val plan = SbFilmPlan.naplanuj(SbFilmPlan.precti(ironMask))
        assertTrue(plan.zeStoryboardu)
        assertEquals(listOf(4.0, 3.0, 4.0, 3.0, 4.0, 5.0, 4.0, 3.0), plan.panely.map { it.sekundy })
        assertEquals(30.0, plan.panely.sumOf { it.sekundy }, 0.001)
    }

    @Test
    fun `30 s se rozdeli na hranicich panelu, zadny usek nad strop`() {
        val useky = SbFilmPlan.rozdel(SbFilmPlan.naplanuj(SbFilmPlan.precti(ironMask)).panely)
        assertTrue(useky.all { it.sekundy <= SbFilmPlan.MAX_USEK_S })
        assertEquals(30.0, useky.sumOf { it.sekundy }, 0.001)
        // Pořadí panelů zůstane a žádný se neztratí.
        assertEquals((1..8).toList(), useky.flatMap { u -> u.panely.map { it.cislo } })
        assertEquals(3, useky.size)
        // Vyvážené: nejlepší dělení 4+3+4 | 3+4 | 5+4+3 má nejdelší úsek 12 s.
        assertEquals(12.0, useky.maxOf { it.sekundy }, 0.001)
    }

    @Test
    fun `nesedici soucet - odhad podle typu zaberu`() {
        val spatne = ironMask.replace("TOTAL: 30", "TOTAL: 80")
        val plan = SbFilmPlan.naplanuj(SbFilmPlan.precti(spatne))
        assertFalse(plan.zeStoryboardu)
        // wide + pohyb kamery = 4,5 s; insert static = 2,5 s
        assertEquals(4.5, plan.panely[0].sekundy, 0.001)
        assertEquals(2.5, plan.panely[1].sekundy, 0.001)
    }

    @Test
    fun `dira mezi panely nad pul sekundy - casy se zahodi`() {
        val c = SbCteni(null, null, null, listOf(
            SbPrecteny(1, 0.0, 4.0, "wide", "static", "a"),
            SbPrecteny(2, 6.0, 9.0, "close-up", "static", "b"),
        ))
        assertFalse(SbFilmPlan.naplanuj(c).zeStoryboardu)
    }

    @Test
    fun `mala dira se pricte k predchozimu panelu`() {
        val c = SbCteni(null, 7.3, 2, listOf(
            SbPrecteny(1, 0.0, 4.0, "wide", "static", "a"),
            SbPrecteny(2, 4.3, 7.3, "close-up", "static", "b"),
        ))
        val p = SbFilmPlan.naplanuj(c)
        assertTrue(p.zeStoryboardu)
        assertEquals(listOf(4.3, 3.0), p.panely.map { it.sekundy })
    }

    @Test
    fun `bez casu - odhad a meze`() {
        val text = """
            TITLE: none | TOTAL: none | SHOTS: none
            PANEL 1 | none | wide | static | A fox and an owl sit side by side.
            PANEL 2 | none | close-up | static | The fox looks at the camera.
            PANEL 3 | none | extreme close-up | static | The owl's face with glasses.
            PANEL 4 | none | extreme close-up | slow push-in | The fox's eyes.
        """.trimIndent()
        val plan = SbFilmPlan.naplanuj(SbFilmPlan.precti(text))
        assertFalse(plan.zeStoryboardu)
        assertEquals(listOf(4.0, 3.0, 2.5, 4.5), plan.panely.map { it.sekundy })
        assertEquals(1, SbFilmPlan.rozdel(plan.panely).size)
    }

    @Test
    fun `pres strop celkem se zkrati pomerne`() {
        val c = SbCteni(null, null, null, (1..12).map { SbPrecteny(it, null, null, "wide", "tracking", "x") })
        val p = SbFilmPlan.naplanuj(c)
        val celkem = p.panely.sumOf { it.sekundy }
        assertTrue(celkem <= SbFilmPlan.MAX_CELKEM_S && celkem >= SbFilmPlan.MAX_CELKEM_S - 1.5)
    }

    @Test
    fun `cisla panelu zustanou jako ve storyboardu`() {
        // Přepisovač vidí celý obrázek a panel dohledává podle čísla.
        val c = SbCteni(null, null, null, listOf(
            SbPrecteny(3, null, null, "wide", "static", "a"),
            SbPrecteny(5, null, null, "close-up", "static", "b"),
        ))
        assertEquals(listOf(3, 5), SbFilmPlan.naplanuj(c).panely.map { it.cislo })
    }

    @Test
    fun `nejvys 12 panelu`() {
        val c = SbCteni(null, null, null, (1..15).map { SbPrecteny(it, null, null, "close-up", "static", "x") })
        assertEquals(SbFilmPlan.MAX_PANELU, SbFilmPlan.naplanuj(c).panely.size)
    }

    @Test
    fun `rozsahy casu v ruznych zapisech`() {
        assertEquals(0.0 to 4.0, SbFilmPlan.rozsah("00-04s"))
        assertEquals(27.0 to 30.0, SbFilmPlan.rozsah("27–30s"))
        assertEquals(4.0 to 7.5, SbFilmPlan.rozsah("0:04 - 0:07.5"))
        assertNull(SbFilmPlan.rozsah("none"))
        assertNull(SbFilmPlan.rozsah("09-04s"))
    }

    @Test
    fun `planovaci text - druhy usek zacina celkovym casem`() {
        val useky = SbFilmPlan.rozdel(listOf(
            SbPanel(1, "a", sekundy = 8.0), SbPanel(2, "b", sekundy = 7.0), SbPanel(3, "c", sekundy = 6.0),
        ))
        val t = SbFilmPlan.planovaciText(useky, "<Picture 1>")
        val casti = t.split("\n---\n")
        assertEquals(useky.size, casti.size)
        assertFalse(casti[0].contains("At "))
        assertTrue(casti[1].startsWith("[Shot 1] At " + SbFilmPlan.casH3(useky[0].sekundy)))
    }

    @Test
    fun `panel delsi nez strop se rozpuli`() {
        val useky = SbFilmPlan.rozdel(listOf(SbPanel(1, "dlouhy", sekundy = 18.0)))
        assertEquals(2, useky.size)
        assertTrue(useky.all { it.sekundy == 9.0 })
    }
}
