package cz.promptlab.h3video

import cz.promptlab.h3video.data.SbFilmPlan
import cz.promptlab.h3video.data.SbFilmPrepis
import cz.promptlab.h3video.data.SbFilmScene
import cz.promptlab.h3video.data.SbPanel
import cz.promptlab.h3video.data.sbFilmProblem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 5.10 (29. 9. 2026): pořadí kroků karty Film ze storyboardu, nálada
 * (EMOCE) z čtení po řádcích a replika opsaná mimo `<d>`.
 */
class SbFilmKrokyTest {

    private val odpovedRadku =
        "PANEL 1 | none | MOOD: Žena je lehce nervózní, muž je v pohodě. " +
            "PANEL 2 | STRÁŽNÝ: „Prosím, vyndejte kovové věci, elektroniku a tekutiny.“ | MOOD: Strážný je klidný a profesionální. " +
            "PANEL 3 | none | MOOD: none " +
            "PANEL 4 | ŽENA: „Mám tekutiny, notebook... a citové zavazadlo.“"

    @Test
    fun `radek vrati repliky bez nalady a naladu zvlast`() {
        val r = SbFilmPlan.prectiRepliky(odpovedRadku)
        assertEquals("", r[1])
        assertEquals("STRÁŽNÝ: „Prosím, vyndejte kovové věci, elektroniku a tekutiny.“", r[2])
        assertEquals("", r[3])
        assertTrue(r[4]!!.startsWith("ŽENA:"))
        val n = SbFilmPlan.prectiNalady(odpovedRadku)
        assertEquals("Žena je lehce nervózní, muž je v pohodě.", n[1])
        assertEquals("Strážný je klidný a profesionální.", n[2])
        assertFalse(n.containsKey(3))
        assertFalse(n.containsKey(4))
        // Starý tvar odpovědi (bez MOOD) funguje dál.
        assertEquals(mapOf(5 to "", 6 to "MUŽ: „Ahoj.“"), SbFilmPlan.prectiRepliky("PANEL 5 | none PANEL 6 | MUŽ: „Ahoj.“"))
        assertTrue(SbFilmPlan.otazkaRadku(1, 4).contains("EMOCE"))
    }

    @Test
    fun `nalada se doplni do popisu jednou`() {
        val p = SbFilmPlan.doplnNaladu("The guard keeps a stone face.", "Strážný je nečitelný, muž je zmatený.")
        assertEquals("The guard keeps a stone face. Mood: Strážný je nečitelný, muž je zmatený.", p)
        assertEquals(p, SbFilmPlan.doplnNaladu(p, "cokoli"))
        assertEquals("x", SbFilmPlan.doplnNaladu("x", null))
    }

    @Test
    fun `replika opsana mimo d se smaze`() {
        val text = "summary:\n[reference generation] In [Shot 4], the guard laughs as <Subject 3> asks in Czech, " +
            "“To bylo na mě?” The entire sequence is visual.\n\ndetailed_description:\n" +
            "[Shot 4] At 00:07.800, <Subject 3> (S3) says <d>[Czech] To bylo na mě?</d> He closes his lips."
        val cisty = SbFilmPrepis.odstranCitaceReplik(text)
        assertEquals(1, Regex("To bylo na mě").findAll(cisty).count())
        assertTrue(cisty.contains("asks in Czech. The entire sequence"))
        assertTrue(cisty.contains("<d>[Czech] To bylo na mě?</d>"))
        // Skutečný scénář bez opsané repliky zůstane beze změny.
        val vzor = javaClass.getResource("/sbfilm/prepis_panely_misto_zaberu.txt")!!.readText()
        assertEquals(vzor, SbFilmPrepis.odstranCitaceReplik(vzor))
    }

    @Test
    fun `natocit jde az s hotovym scenarem`() {
        val sb = File.createTempFile("sbfilm", ".png")
        val panely = listOf(SbPanel(1, "a", sekundy = 3.0), SbPanel(2, "b", sekundy = 3.0))
        val s = SbFilmScene(storyboard = sb, panely = panely)
        assertEquals("Nejdřív napiš prompty.", sbFilmProblem(s))
        assertEquals("Doplň prompty.", sbFilmProblem(s.copy(zadaniUseku = listOf(" "))))
        assertNull(sbFilmProblem(s.copy(zadaniUseku = listOf("[Shot 1] x"))))
        assertEquals("Nejdřív připrav film.", sbFilmProblem(s.copy(panely = emptyList())))
        sb.delete()
    }

    /** Skutečná odpověď serveru 29. 9. 2026 (otázka 5.10) a starší tvar bez návěští MOOD. */
    @Test
    fun `skutecne cteni radku s naladou`() {
        val odpoved = "PANEL 5 | none | MOOD: Strážný je nečitelný, muž je zmatený. PANEL 6 | STRÁŽNÝ: " +
            "\"Elektroniku a křehké věci dejte zvlášť.\" | MOOD: Strážný je věcný a bez emoci. PANEL 8 | MUŽ: " +
            "\"To bylo na mě?\" | MOOD: Muž je šokovaný, strážný potlačuje smích, žena má radost sama ze sebe."
        val r = SbFilmPlan.prectiRepliky(odpoved)
        assertEquals("", r[5])
        assertEquals("MUŽ: \"To bylo na mě?\"", r[8])
        assertEquals("Strážný je nečitelný, muž je zmatený.", SbFilmPlan.prectiNalady(odpoved)[5])
        // Model návěští vynechal: nálada nesmí skončit v replikách.
        val bez = "PANEL 2 | Prosím, vyndejte věci. | Strážný je klidný a profesionální."
        assertEquals("Prosím, vyndejte věci.", SbFilmPlan.prectiRepliky(bez)[2])
        assertEquals("Strážný je klidný a profesionální.", SbFilmPlan.prectiNalady(bez)[2])
    }

    /** 5.11: vzhled postav z čtení (skutečná odpověď serveru 29. 9. 2026) jde do každého úseku. */
    @Test
    fun `vzhled postav se precte a da do kazdeho useku`() {
        val c = SbFilmPlan.precti(
            "TITLE: none | TOTAL: none | SHOTS: none | GRID: 2x4 | VOICES: Žena = a woman in her 30s with a warm, " +
                "high voice | LOOKS: Žena = brown hair in a bun, beige sweater; Dog = golden retriever with golden fur " +
                "PANEL 1 | none | wide | static | A woman arrives. | ŽENA: \"Jdeme na dovolenou!\""
        )
        assertEquals(mapOf("Žena" to "brown hair in a bun, beige sweater", "Dog" to "golden retriever with golden fur"), c.vzhled)
        assertEquals(c.vzhled, SbFilmPlan.naplanuj(c).vzhled)
        val usek = cz.promptlab.h3video.data.SbUsek(listOf(SbPanel(1, "A woman arrives.", sekundy = 3.0)))
        val h = SbFilmPrepis.hlidka(1, usek, 1, 2, true, vzhled = c.vzhled)
        assertTrue(h.contains("Žena — brown hair in a bun, beige sweater; Dog — golden retriever with golden fur."))
        assertTrue(h.contains("with exactly these looks"))
        assertFalse(SbFilmPrepis.hlidka(1, usek, 1, 2, true).contains("these looks"))
        assertTrue(SbFilmPlan.OTAZKA_CTENI.contains("LOOKS:"))
    }
}
