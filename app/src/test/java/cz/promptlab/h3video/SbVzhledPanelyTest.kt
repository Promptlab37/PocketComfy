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

class SbJenZmineneTest {
    private val lide = listOf("Della", "Jim", "Jimovy hodinky")
    private fun z(t: String) = cz.promptlab.h3video.data.SbScenar.jenZminene(t, lide)

    @Test
    fun `dar mudrcu`() {
        assertEquals(listOf("Jim"), z("U pultu vybere jednoduchý kovový řetízek ke kapesním hodinkám. Podrží ho na otevřené dlani a představí si jej na Jimových hodinkách."))
        assertEquals(listOf("Jim"), z("Della sedí u stolu. Sklopí oči k místu, kde chtěla mít Jimův vánoční dárek."))
        assertEquals(emptyList<String>(), z("Della se pousměje a podá Jimovi řetízek na otevřené dlani. Jim se podívá na řetízek."))
        assertEquals(emptyList<String>(), z("Jim vstoupí do bytu. Uvidí Delleny krátké vlasy a zarazí se."))
        assertEquals(listOf("Della"), z("Jim drží Dellin dárek."))
        assertEquals(listOf("Jim"), z("She looks at Jim's watch."))
    }
}

class SbReplikaNaZacatekTest {
    @Test
    fun `dar mudrcu usek 4`() {
        val r = "[Shot 2] At 00:05.000, the shot cuts to a medium static frame. Jim (S2) holds up his empty coat pocket. " +
            "Della (S1) understands and places the necklace on the table. They embrace. " +
            "Jim says in his warm voice: <d>[Czech] Prodal jsem je, abych ti mohl koupit ty hřebeny.</d> He closes his lips after speaking as the shot ends."
        assertEquals(
            "[Shot 2] At 00:05.000, the shot cuts to a medium static frame. Jim says in his warm voice: <d>[Czech] Prodal jsem je, abych ti mohl koupit ty hřebeny.</d> " +
                "He closes his lips after speaking. Jim (S2) holds up his empty coat pocket. Della (S1) understands and places the necklace on the table. They embrace.",
            cz.promptlab.h3video.data.SbFilmPrepis.replikaNaZacatek("summary:\nx\n$r").lines().last(),
        )
        val ok = "[Shot 1] A close-up. Della says <d>[Czech] A já.</d> She closes her lips. Jim looks."
        assertEquals(ok, cz.promptlab.h3video.data.SbFilmPrepis.replikaNaZacatek(ok))
    }
}
