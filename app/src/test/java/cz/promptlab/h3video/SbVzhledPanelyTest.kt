package cz.promptlab.h3video

import cz.promptlab.h3video.data.SbScenar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Vzhled podle oken se rozhodne pro každý úsek zvlášť (Dar mudrců 30. 9. 2026). */
class SbVzhledPanelyTest {

    private val della = "mladá žena. V oknech 1–4 má mimořádně dlouhé hnědé vlasy; od okna 5 má krátké hnědé kudrliny"

    @Test
    fun `usek jen s jednim vzhledem`() {
        assertEquals("mladá žena; má mimořádně dlouhé hnědé vlasy", SbScenar.vzhledProPanely(della, listOf(1, 2, 3)))
        assertEquals("mladá žena; má krátké hnědé kudrliny", SbScenar.vzhledProPanely(della, listOf(9, 10)))
    }

    @Test
    fun `usek pres zmenu`() {
        assertEquals(
            "mladá žena; in [Shot 1]: má mimořádně dlouhé hnědé vlasy; in [Shot 2], [Shot 3]: má krátké hnědé kudrliny",
            SbScenar.vzhledProPanely(della, listOf(4, 5, 6)),
        )
    }

    @Test
    fun `dalsi zapisy`() {
        assertEquals("young man; clean-shaven", SbScenar.vzhledProPanely("young man. Until window 3 bearded. From window 4 clean-shaven.", listOf(5)))
        assertEquals("muž; in [Shot 1]: v saku", SbScenar.vzhledProPanely("muž. V okně 2 v saku.", listOf(2, 3)))
        assertNull(SbScenar.vzhledProPanely("muž v modrém saku", listOf(1)))
        assertNull(SbScenar.vzhledProPanely("v oknech 1 a okně 3 bez klobouku", listOf(1)))
    }
}

class SbOpravObrazkyTest {
    @Test
    fun `picture za poslednim obrazkem`() {
        val t = "<Subject 2> is the young man from <Picture 7>; <Picture 1> and <Picture 2> stay."
        assertEquals("<Subject 2> is the young man from <Picture 1>; <Picture 1> and <Picture 2> stay.",
            cz.promptlab.h3video.data.SbFilmPrepis.opravObrazky(t, 2))
    }
}
