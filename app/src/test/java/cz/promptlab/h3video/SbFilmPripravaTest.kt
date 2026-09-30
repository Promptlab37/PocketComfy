package cz.promptlab.h3video

import cz.promptlab.h3video.data.LongMmRef
import cz.promptlab.h3video.data.SbFilmKontrola
import cz.promptlab.h3video.data.SbFilmPlan
import cz.promptlab.h3video.data.SbFilmPrepis
import cz.promptlab.h3video.data.SbFilmScene
import cz.promptlab.h3video.data.SbPanel
import cz.promptlab.h3video.data.SbPrecteny
import cz.promptlab.h3video.data.SbScenar
import cz.promptlab.h3video.data.SbUsek
import cz.promptlab.h3video.data.SbZdroj
import cz.promptlab.h3video.data.jmenaPostav
import cz.promptlab.h3video.data.sbFilmProblem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** 5.17: kontrola čtení před prompty a fotky přiřazené postavám. */
class SbFilmPripravaTest {

    private fun p(c: Int, repl: String = "") = SbPrecteny(c, null, null, "medium", "static", "panel $c", repl)
    private fun panel(c: Int, repl: String = "", s: Double = 3.0) = SbPanel(c, "panel $c", "medium", "static", s, repl)

    @Test
    fun `kontrola - ciste cteni nic nenajde`() {
        val cele = listOf(p(1, "Pepa: „Ahoj.“"), p(2), p(3, "Jana: „Čau.“"), p(4))
        val radky = mapOf(1 to "Pepa: „Ahoj.“", 2 to "", 3 to "Jana: „Čau.“", 4 to "")
        val panely = listOf(panel(1, "Pepa: „Ahoj.“"), panel(2), panel(3, "Jana: „Čau.“"), panel(4))
        assertTrue(SbFilmKontrola.storyboard(cele, radky, 2, 2, true, panely).isEmpty())
    }

    @Test
    fun `kontrola - podezrele cteni`() {
        // Řádek panel 3 nevypsal, u panelu 1 řádek říká „bez repliky“ a u souseda není.
        val cele = listOf(p(1, "Pepa: „Ahoj.“"), p(2), p(3, "Jana: „Čau.“"), p(4))
        val radky = mapOf(1 to "", 2 to "", 4 to "")
        val panely = listOf(panel(1, "Pepa: „Ahoj.“"), panel(2, "Jana: „Kde je ### ~~~?“"), panel(3, "Jana: „Čau.“"), panel(4))
        val n = SbFilmKontrola.storyboard(cele, radky, 2, 2, true, panely)
        assertEquals(listOf(1, 3, 2), n.map { it.cislo })
        assertEquals("Replika nejistá", n[0].text)
        assertEquals("Replika neověřena", n[1].text)
        assertEquals("Replika má podivné znaky", n[2].text)
        // Zdvojená replika u souseda není nález (to řeší sloučení čtení).
        val zdvojena = SbFilmKontrola.storyboard(
            listOf(p(1, "Pepa: „Ahoj.“"), p(2, "Pepa: „Ahoj.“")), mapOf(1 to "Pepa: „Ahoj.“", 2 to ""), 1, 2, true,
            listOf(panel(1, "Pepa: „Ahoj.“"), panel(2)),
        )
        assertTrue(zdvojena.isEmpty())
        // Mřížka 2×2 a 6 panelů.
        assertEquals(0, SbFilmKontrola.storyboard((1..6).map { p(it) }, emptyMap(), 2, 2, false, emptyList()).single().cislo)
        // Replika delší než strop panelu.
        val dlouha = "Pepa: „" + "Tohle je opravdu velmi dlouhá replika, ".repeat(4) + "konec.“"
        assertEquals("Replika je na panel moc dlouhá",
            SbFilmKontrola.storyboard(emptyList(), emptyMap(), null, null, false, listOf(panel(1, dlouha))).single().text)
        assertEquals(1, SbFilmKontrola.scenar(4, 5).size)
        assertTrue(SbFilmKontrola.scenar(4, 4).isEmpty())
    }

    private val svatba = File("src/test/resources/scenar_svatba.txt").readText()

    private fun scena(vararg jmena: String?): SbFilmScene {
        val s = SbScenar.rozeber(svatba)!!
        val plan = SbFilmPlan.naplanuj(SbScenar.cteni(s, null))
        val fotky = jmena.indices.map { LongMmRef(File("postava$it.jpg")) }
        return SbFilmScene(
            storyboard = File("s.png"), zdroj = SbZdroj.SCENAR, scenar = svatba, panely = SbScenar.doplnPanely(plan.panely, s),
            vzhled = mapOf("Syn" to "brown hair, green shirt", "Otec" to "white hair, red sweater"),
            postavy = fotky, jmenaFotek = fotky.zip(jmena.toList()).filter { it.second != null }
                .associate { it.first.soubor.absolutePath to it.second!! },
            scenarPlanu = cz.promptlab.h3video.data.otiskScenare(svatba),
        )
    }

    /** 5.25: po úpravě záběrů hlavní tlačítko nabídne jen prompty, ne novou přípravu. */
    @Test
    fun `hlavni tlacitko - dalsi krok`() {
        val k = cz.promptlab.h3video.data.SbKrok.entries
        fun krok(s: SbFilmScene) = cz.promptlab.h3video.data.sbHlavniKrok(s)
        val bezPlanu = scena().copy(panely = emptyList())
        assertEquals(k[0], krok(bezPlanu))
        // Záběry jsou, prompty ne (upravený záběr) → Napsat prompty.
        assertEquals(k[1], krok(scena()))
        // Nálezy kontroly → Pokračovat.
        assertEquals(k[2], krok(scena().copy(nalezy = listOf(cz.promptlab.h3video.data.SbNalez(2, "x")))))
        // Scénář se změnil → znovu připravit.
        assertEquals(k[0], krok(scena().copy(scenar = svatba + " x")))
        // Nepřiřazená fotka → nic (stavový řádek).
        assertEquals(null, krok(scena(null)))
        // Hotovo → Natočit.
        val sc = scena()
        assertEquals(null, krok(sc.copy(zadaniUseku = sc.useky.map { "p" })))
    }

    @Test
    fun `fotky - jmena z planu a bez jmena se nenatoci`() {
        assertEquals(listOf("Syn", "Otec"), jmenaPostav(scena()))
        assertEquals("Urči, kdo je na fotce.", sbFilmProblem(scena("Otec", null)))
        assertEquals("Nejdřív napiš prompty.", sbFilmProblem(scena("Otec")))
    }

    @Test
    fun `fotky - prompt rika kdo je na ktere fotce a textovy vzhled vynecha`() {
        val sc = scena("Otec")
        val u = SbUsek(sc.panely.take(2))
        val h = SbFilmPrepis.hlidka(
            sc.uploadImages.size, u, 0, 2, sc.seStoryboardem, SbFilmPrepis.idMluvcich(sc.panely), "Czech", emptyMap(),
            vzhled = sc.vzhled, jmenaFotek = listOf("Otec"),
        )
        assertTrue(h.contains("<Picture 2> is Otec: define each of them in subject_definitions as a <Subject K> with the face, hair, body and clothing from their picture"))
        assertFalse(h.contains("show the characters"))
        assertTrue(h.contains("Syn — brown hair, green shirt"))
        assertFalse(h.contains("Otec — white hair"))
        // Bez jmen beze změny (staré obecné znění).
        val bez = SbFilmPrepis.hlidka(sc.uploadImages.size, u, 0, 2, true, SbFilmPrepis.idMluvcich(sc.panely), "Czech", emptyMap(), vzhled = sc.vzhled)
        assertTrue(bez.contains("<Picture 2> show the characters"))
        assertTrue(bez.contains("Otec — white hair"))
    }
}
