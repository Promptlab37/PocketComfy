package cz.promptlab.h3video

import cz.promptlab.h3video.data.SbFilmPlan
import cz.promptlab.h3video.data.SbFilmPrepis
import cz.promptlab.h3video.data.SbPanel
import cz.promptlab.h3video.data.SbUsek
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 5.56: storyboard „Internetu už bylo dost“ (1. 10. 2026) — popisy panelů říkaly „The woman“,
 * repliky a fotka „Příšera“; přepisovač v úseku 2 definoval dvě postavy. Zvuk schovaný
 * v popisu se v úseku 1 ztratil a bez VOICES měl každý úsek jiný hlas.
 */
class SbJednaPostavaTest {

    private val p4 = SbPanel(4, "The woman dances energetically, pumping her arms and moving her hips. Mood: Nadšení. Sound: Taneční hudba.",
        "wide", "static", 4.0)
    private val p5 = SbPanel(5, "The woman leans forward, looking surprised and confused. Mood: Pobouřený údiv.", "close-up", "static", 3.0,
        repliky = "Příšera: „Ty tu ještě jsi?“")
    private val p3 = SbPanel(3, "The woman strikes a dance pose with her hands on her hips. Sound: Nástup tanečního beatu.", "wide", "static", 3.0)

    private fun hlidka(fotky: List<String> = listOf("Příšera"), popisy: List<String> = listOf(p3.popis, p4.popis, p5.popis)) =
        SbFilmPrepis.hlidka(
            2, SbUsek(listOf(p4, p5)), 1, 2, true,
            idMluvcich = mapOf("Příšera" to "S1"), jazykFilmu = "Czech",
            hlasy = mapOf("Příšera" to "a woman in her 50s with a deep, raspy voice"),
            predchozi = p3, jmenaFotek = fotky, popisyFilmu = popisy,
        )

    @Test
    fun `jedina postava ma vsude jmeno`() {
        val h = hlidka()
        assertFalse(h.contains("The woman"))
        assertTrue(h.contains("storyboard panel 4, wide, camera static: Příšera dances energetically"))
        assertTrue(h.contains("The previous part ended with: Příšera strikes a dance pose"))
        assertTrue(h.contains("Příšera (S1) — a woman in her 50s with a deep, raspy voice"))
    }

    @Test
    fun `zvuk je samostatny radek`() {
        val h = hlidka()
        assertTrue(h.contains("Mood: Nadšení.\n    sound in this shot: Taneční hudba."))
        assertFalse(h.contains("Sound: Taneční hudba"))
    }

    @Test
    fun `dve osoby se neprepisuji`() {
        val popisy = listOf("The woman hands the man a cup.", "The man smiles.")
        assertNull(SbFilmPrepis.jedinaPostava(popisy, listOf("Příšera"), listOf("Příšera"), emptyList()))
        assertNull(SbFilmPrepis.jedinaPostava(listOf("The woman sits."), listOf("Anna", "Petr"), emptyList(), emptyList()))
        assertTrue(hlidka(popisy = listOf("The woman hands the man a cup.")).contains("The woman dances"))
    }

    @Test
    fun `hlas se doplni do cteni`() {
        val cele = "TITLE: X | TOTAL: 20s | SHOTS: 6 | GRID: 2x3 | VOICES: none | LOOKS: none | MUSIC: dance PANEL 1 | 0-4s | close-up | static | A. | none"
        val h = SbFilmPlan.prectiHlasy("Příšera = a woman in her 50s with a deep, raspy voice.", listOf("Příšera"))
        assertEquals(mapOf("Příšera" to "a woman in her 50s with a deep, raspy voice"), h)
        val c = SbFilmPlan.precti(SbFilmPlan.doplnHlasy(cele, h))
        assertEquals("a woman in her 50s with a deep, raspy voice", c.hlasy["Příšera"])
        assertEquals("dance", c.hudbaStyl)
        assertEquals(1, c.panely.size)
        assertTrue(SbFilmPlan.otazkaHlasu(listOf("Příšera")).contains("Příšera"))
    }
}
