package cz.promptlab.h3video

import cz.promptlab.h3video.data.SbFilmPlan
import cz.promptlab.h3video.data.SbFilmPrepis
import cz.promptlab.h3video.data.SbFilmScene
import cz.promptlab.h3video.data.SbScenar
import cz.promptlab.h3video.data.SbTextStrihu
import cz.promptlab.h3video.data.SbZdroj
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Přesné znění scénáře s hrnkem od uživatele (30. 9. 2026). */
class SbScenarHrnekPresneTest {

    private val text = File("src/test/resources/scenar_hrnek.txt").readText()

    /** Skutečné čtení obrázku ze serveru. */
    private val obrazek = SbFilmPlan.precti(
        "TITLE: none | TOTAL: none | SHOTS: 6 | GRID: 2x3 | VOICES: Man = a man in his 30s with a calm, neutral voice; Woman = a woman in her 30s with a warm, soft voice | LOOKS: Man = dark hair, dark shirt; Woman = curly brown hair, orange shirt | MUSIC: warm, cozy, acoustic guitar and soft piano, 60 BPM PANEL 1 | none | medium | static | A woman in an apron holds a blue mug with a sun design while a man watches her. | none PANEL 2 | none | close-up | static | A man holds a camera, photographing the blue mug with a sun design on a table with coffee beans. | none PANEL 3 | none | close-up | static | A man and a woman look at a computer screen showing a sequence of images of the mug being filled with coffee. | none PANEL 4 | none | medium | static | A man edits a video on a computer while a woman watches him. | none PANEL 5 | none | close-up | static | A woman holds a smartphone showing a video of the blue mug with steam rising from it. | none PANEL 6 | none | medium | static | A woman shows a video of the blue mug on her smartphone to a man sitting across from her. | none",
    )

    @Test
    fun `hrnek - vsechno na svem miste`() {
        val s = SbScenar.rozeber(text)!!
        assertEquals(6, s.okna.size)
        assertEquals(listOf(0.0, 3.0, 6.0, 9.0, 12.0, 15.0), s.okna.map { it.od })
        // Postavy z věty „Keramička s … a tvůrce videa v …“ + produkt.
        assertEquals(listOf("Keramička", "Tvůrce", "Produkt"), s.postavy.keys.toList())
        assertTrue(s.postavy.getValue("Keramička").contains("rezavé halence"))
        assertTrue(s.postavy.getValue("Tvůrce").contains("tmavé košili"))
        // Názvy oken v popisu nejsou.
        assertTrue(s.okna[0].obraz.startsWith("Keramička ukazuje tvůrci"))
        assertTrue(s.okna[2].obraz.startsWith("Oba sledují"))
        // Repliky: 1, 3 a 5 (podání za čárkou).
        val r = s.okna.flatMap { o -> o.repliky.map { o.cislo to it } }
        assertEquals(listOf(1, 3, 5), r.map { it.first })
        assertEquals(listOf("Keramička", "Tvůrce", "Keramička"), r.map { it.second.kdo })
        assertEquals("spokojeně", r[2].second.podani)
        assertEquals("Jo. Takhle ho chci ukázat.", r[2].second.text)
        // Závěrečný text jde do střihu jako výzva, ne do obrazu.
        assertEquals("Od nápadu k hotovému videu. promptlab.cz", s.okna[5].vyzva)
        assertFalse(s.okna[5].obraz.contains("Závěrečný text"))
        assertFalse(s.okna[5].obraz.contains("promptlab"))
        assertEquals(1, SbScenar.strih(s).count { it.druh == SbTextStrihu.Druh.VYZVA })
        // Hlasy z obrázku podle rodu (věk scénář neuvádí), vzhled ze scénáře.
        val c = SbScenar.cteni(s, obrazek)
        assertEquals("a woman in her 30s with a warm, soft voice", c.hlasy["Keramička"])
        assertEquals("a man in his 30s with a calm, neutral voice", c.hlasy["Tvůrce"])
        assertTrue(c.vzhled.getValue("Keramička").contains("rezavé halence"))
        // Prompt: Keramička má v okně 5 repliku, nic ze střihu.
        val plan = SbFilmPlan.naplanuj(c)
        val sc = SbFilmScene(storyboard = File("s.png"), zdroj = SbZdroj.SCENAR, scenar = text,
            panely = SbScenar.doplnPanely(plan.panely, s), hlasy = plan.hlasy, vzhled = plan.vzhled, kontinuita = s.kontinuita)
        val vse = sc.useky.mapIndexed { k, u ->
            SbFilmPrepis.hlidka(1, u, k, sc.useky.size, true, SbFilmPrepis.idMluvcich(sc.panely), SbFilmPrepis.jazykFilmu(sc),
                sc.hlasy, predchozi = sc.useky.getOrNull(k - 1)?.panely?.lastOrNull(), vzhled = sc.vzhled, kontinuita = sc.kontinuita)
        }.joinToString("\n")
        assertTrue(vse.contains("Keramička (S1), spokojeně, says <d>[Czech] Jo. Takhle ho chci ukázat.</d>"))
        assertTrue(vse.contains("Tvůrce (S2) — a man in his 30s"))
        for (x in listOf("NÁPAD", "PODKLADY", "Od nápadu", "promptlab", "Závěrečný text", "spokojeně:."))
            assertFalse(x, vse.contains(x))
        File(File("build/sbscenar").also { it.mkdirs() }, "hrnek.txt").writeText(vse)
    }
}
