package cz.promptlab.h3video

import cz.promptlab.h3video.data.SbFilmPlan
import cz.promptlab.h3video.data.SbFilmPrepis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Storyboard „Internetu už bylo dost“ (1. 10. 2026): čtení po řádcích vrátilo repliky
 * bez jména a bez uvozovek („PANEL 1 | Tak, pro dnešek už bylo internetu dost.“) —
 * appka je zahodila a všech 6 záběrů šlo jako SILENT SHOT. Odpovědi jsou přesně
 * ze serveru.
 */
class SbFilmPriseraTest {

    private val cele = "TITLE: INTERNETU UŽ BYLO DOST | TOTAL: 20s | SHOTS: 6 | GRID: 2x3 | VOICES: none | LOOKS: none | " +
        "MUSIC: dance, upbeat, electronic drums and synths, 120 BPM PANEL 1 | 0-4s | extreme close-up | static | " +
        "The woman stares directly into the camera with a stern expression. | none PANEL 2 | 4-6s | medium | static | " +
        "The woman points her finger directly at the viewer. | none PANEL 3 | 6-9s | wide | static | The woman strikes " +
        "a dance pose with her hands on her hips. | none PANEL 4 | 9-13s | wide | static | The woman dances energetically, " +
        "pumping her arms and moving her hips. | none PANEL 5 | 13-16s | close-up | static | The woman leans forward, " +
        "looking surprised and confused. | none PANEL 6 | 16-20s | medium | static | The woman dances and points toward a door. | none"

    private val radky = listOf(
        "PANEL 1 | Tak, pro dnešek už bylo internetu dost. | MOOD: Rozhořčení. PANEL 2 | Běž spát. | MOOD: Autoritativní.",
        "PANEL 3 | none | MOOD: Drzé sebevědomí. PANEL 4 | none | MOOD: Nadšení.",
        "PANEL 5 | Ty tu ještě jsi? | MOOD: Pobouřený údiv. PANEL 6 | Vypnout. A do postele! | MOOD: Komicky přísná.",
    )

    @Test
    fun `repliky bez jmena se nezahodi`() {
        val repliky = radky.fold(mapOf<Int, String>()) { acc, r -> acc + SbFilmPlan.prectiRepliky(r) }
        val panely = SbFilmPrepis.slucCteni(SbFilmPlan.precti(cele).panely, repliky)
        val r = panely.associate { it.cislo to SbFilmPrepis.repliky(it.repliky).map { x -> x.second } }
        assertEquals(listOf("Tak, pro dnešek už bylo internetu dost."), r[1])
        assertEquals(listOf("Běž spát."), r[2])
        assertEquals(emptyList<String>(), r[3])
        assertEquals(emptyList<String>(), r[4])
        assertEquals(listOf("Ty tu ještě jsi?"), r[5])
        assertEquals(listOf("Vypnout. A do postele!"), r[6])
    }

    @Test
    fun `nalada se nebere jako replika a zvuk se precte`() {
        val odpoved = "PANEL 3 | none | MOOD: Drzé sebevědomí. | SOUND: Nástup tanečního beatu. " +
            "PANEL 4 | none | MOOD: Nadšení. | SOUND: Taneční hudba."
        assertEquals(mapOf(3 to "", 4 to ""), SbFilmPlan.prectiRepliky(odpoved))
        assertEquals(mapOf(3 to "Nástup tanečního beatu.", 4 to "Taneční hudba."), SbFilmPlan.prectiZvuky(odpoved))
        assertEquals("Drzé sebevědomí.", SbFilmPlan.prectiNalady(odpoved)[3])
        val p = SbFilmPlan.doplnZvuk("The woman dances. Mood: Nadšení.", "Taneční hudba.")
        assertTrue(p.endsWith("Sound: Taneční hudba."))
    }

    @Test
    fun `replika se jmenem zustane, jak je`() {
        assertEquals(mapOf(1 to "Příšera: \"Běž spát.\""), SbFilmPlan.prectiRepliky("PANEL 1 | Příšera: \"Běž spát.\" | MOOD: none | SOUND: none"))
        assertTrue(SbFilmPlan.otazkaRadku(1, 2).contains("SOUND:"))
    }

    /** 5.54: bez vytištěného jména si model vymyslel „TY“ a „VY“ z první věty (odpověď ze serveru). */
    @Test
    fun `zajmeno neni mluvci`() {
        val odpoved = "PANEL 5 | TY: \"Ty tu ještě jsi?\" | MOOD: Pobouřený údiv. | SOUND: none " +
            "PANEL 6 | VY: \"Vypnout. A do postele!\" | MOOD: Komicky přísná. | SOUND: none"
        val r = SbFilmPlan.prectiRepliky(odpoved)
        assertEquals(listOf("Mluvčí" to "Ty tu ještě jsi?"), SbFilmPrepis.repliky(SbFilmPrepis.sloucit("", r.getValue(5))))
        assertEquals(listOf("Mluvčí" to "Vypnout. A do postele!"), SbFilmPrepis.repliky(SbFilmPrepis.sloucit("", r.getValue(6))))
        assertEquals(listOf("Anna" to "Kde je?", "Mluvčí" to "Tady."),
            SbFilmPrepis.repliky(SbFilmPrepis.sloucit("", "Anna: \"Kde je?\"; \"Tady.\"")))
        assertTrue(SbFilmPlan.otazkaRadku(5, 6).contains("only when a name is printed"))
    }
}
