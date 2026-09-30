package cz.promptlab.h3video

import cz.promptlab.h3video.data.LongMmPomer
import cz.promptlab.h3video.data.SbFilmPlan
import cz.promptlab.h3video.data.SbFilmPrepis
import cz.promptlab.h3video.data.SbFilmScene
import cz.promptlab.h3video.data.SbScenar
import cz.promptlab.h3video.data.SbZdroj
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Scénář „Dar mudrců“ a skutečné čtení obrázku ze serveru (30. 9. 2026). */
class SbScenarDarMudrcuTest {

    private val text = File("src/test/resources/scenar_dar_mudrcu.txt").readText()
    private val obrazek = SbFilmPlan.precti(File("src/test/resources/cteni_dar_mudrcu.txt").readText())

    @Test
    fun `postavy a rekvizity, prostredi, meneny vzhled`() {
        val s = SbScenar.rozeber(text)!!
        assertEquals(10, s.okna.size)
        assertEquals(LongMmPomer.NAVYSKU, s.pomer)
        // Nadpis „POSTAVY A REKVIZITY“ bez dvojtečky, řádky „Jméno: popis“, středník uvnitř popisu.
        assertEquals(listOf("Della", "Jim", "Jimovy hodinky", "Dellin dárek", "Jimův dárek"), s.postavy.keys.toList())
        assertTrue(s.postavy.getValue("Della").contains("od okna 5 má krátké hnědé kudrliny"))
        // Prostředí je kontinuita; postavy v ní nejsou.
        assertTrue(s.kontinuita.contains("Prostředí: skromný newyorský byt"))
        assertTrue(s.kontinuita.startsWith("Adaptace povídky O. Henryho"))
        assertFalse(s.kontinuita.contains("POSTAVY"))
        assertTrue(SbScenar.vzhledSeMeni(s.postavy))
        // Repliky: 7 Della tiše, 9 Della, 10 Jim tiše.
        val r = s.okna.flatMap { o -> o.repliky.map { o.cislo to it } }
        assertEquals(listOf(7 to "Della", 9 to "Della", 10 to "Jim"), r.map { it.first to it.second.kdo })
        // Hlasy z obrázku (Anna/Gilbert) pod jmény ze scénáře podle rodu z hlasu.
        val c = SbScenar.cteni(s, obrazek)
        assertEquals("a young woman in her 20s with a soft, gentle voice", c.hlasy["Della"])
        assertEquals("a young man in his 20s with a warm, slightly husky voice", c.hlasy["Jim"])
        assertTrue(c.vzhled.getValue("Della").contains("od okna 5"))
        assertFalse(c.vzhled.containsKey("Anna"))
        // Hlídka: vzhled se mění podle oken, jména ze scénáře.
        val plan = SbFilmPlan.naplanuj(c)
        val sc = SbFilmScene(storyboard = File("s.png"), zdroj = SbZdroj.SCENAR, scenar = text,
            panely = SbScenar.doplnPanely(plan.panely, s), hlasy = plan.hlasy, vzhled = plan.vzhled, kontinuita = s.kontinuita)
        val h = SbFilmPrepis.hlidka(1, sc.useky.last(), sc.useky.size - 1, sc.useky.size, true, SbFilmPrepis.idMluvcich(sc.panely),
            SbFilmPrepis.jazykFilmu(sc), sc.hlasy, vzhled = sc.vzhled, kontinuita = sc.kontinuita, vzhledSeMeni = true)
        // Poslední úsek (okna 9–10) dostane jen krátké vlasy — dlouhé v něm nesmí být vůbec.
        assertTrue(h, h.contains("a look below names the shots it belongs to"))
        assertTrue(h, h.contains("Della — mladá žena; má krátké hnědé kudrliny"))
        assertFalse(h, h.contains("dlouhé hnědé vlasy"))
        assertFalse(h.contains("Anna"))
        // Plátno: nově vložený scénář předvyplní 9:16, příprava ho už nemění (VM) — tady jen formát.
        assertEquals(LongMmPomer.NAVYSKU, SbScenar.pomerZ("9:16"))
    }
}
