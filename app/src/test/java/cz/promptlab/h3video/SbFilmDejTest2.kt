package cz.promptlab.h3video

import cz.promptlab.h3video.data.SbFilmPlan
import cz.promptlab.h3video.data.SbFilmPrepis
import cz.promptlab.h3video.data.SbPanel
import cz.promptlab.h3video.data.SbUsek
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Text se štítkem DĚJ není replika. 29. 9. 2026 (film „restaurace“, panel 6)
 * ho čtení vzalo jako mluvčího „DĚJ“, přepisovač z něj udělal (S3) a H3 na
 * konci filmu nahlas řekl „Žena pochopí narážku. Muž se nevinně usměje.“
 */
class SbFilmDejTest2 {

    private val panel6 = "PANEL 6 | 20-24s | medium | static | The woman takes a bite, the man smiles. | " +
        "DĚJ: \"Žena pochopí narážku. Muž se nevinně usměje.\""

    @Test fun `stitek DEJ jde do popisu, ne do replik`() {
        val c = SbFilmPlan.precti("TITLE: none | TOTAL: none | SHOTS: 6 | GRID: 2x3\n$panel6")
        val p = c.panely.single()
        assertEquals("", p.repliky)
        assertTrue(p.popis.startsWith("The woman takes a bite, the man smiles."))
        assertTrue(p.popis.endsWith("Žena pochopí narážku. Muž se nevinně usměje."))
    }

    @Test fun `skutecne repliky zustanou, popis se oddeli`() {
        val (repliky, dej) = SbFilmPrepis.oddelDej("MUŽ: „To je doma.“; AKCE: „Muž se usměje.“")
        assertEquals("MUŽ: „To je doma.“", repliky)
        assertEquals("Muž se usměje.", dej)
        // Bez popisného štítku se text nemění.
        assertEquals("Pepa: „Ahoj.“" to "", SbFilmPrepis.oddelDej("Pepa: „Ahoj.“"))
    }

    @Test fun `ulozeny plan s DEJ se do dialogu nedostane`() {
        // Plán načtený starší verzí: DĚJ ještě v poli replik.
        val panely = listOf(
            SbPanel(5, "a", sekundy = 4.0, repliky = "MUŽ: „Tady kuchař umí vařit.“"),
            SbPanel(6, "b", sekundy = 4.0, repliky = "DĚJ: „Žena pochopí narážku. Muž se nevinně usměje.“"),
        )
        val id = SbFilmPrepis.idMluvcich(panely)
        assertEquals(mapOf("MUŽ" to "S1"), id)
        val h = SbFilmPrepis.hlidka(2, SbUsek(panely), 1, 2, true, id, SbFilmPrepis.jazykFilmu(panely))
        assertFalse(h.contains("Žena pochopí narážku"))
        assertTrue(h.contains("MUŽ (S1) says"))
    }

    /**
     * Záběr bez repliky je tichý (29. 9. 2026: „doktorka volá ke dveřím“ bez
     * textu — přepisovač napsal volání a H3 si vymyslel slova).
     */
    @Test fun `zaber bez repliky je tichy`() {
        val panely = listOf(
            SbPanel(5, "Doktorka zvedne hlavu.", sekundy = 3.5, repliky = "ŽENA: „Další!“"),
            SbPanel(6, "Doktorka volá ke dveřím.", sekundy = 3.5),
        )
        val h = SbFilmPrepis.hlidka(2, SbUsek(panely), 1, 2)
        val radky = h.lines()
        val i5 = radky.indexOfFirst { it.contains("panel 5") }
        val i6 = radky.indexOfFirst { it.contains("panel 6") }
        assertTrue(radky[i5 + 1].contains("<d>"))
        assertTrue(radky[i6 + 1].contains("SILENT SHOT"))
        assertTrue(h.contains("never put quoted words outside <d>"))
        assertFalse(radky[i5 + 1].contains("SILENT SHOT"))
    }

    /** Herecké podání (hlas, emoce, reakce) jen u úseku s replikami. */
    @Test fun `pokyn k hereckemu podani jen s replikami`() {
        val s = listOf(SbPanel(1, "a", sekundy = 4.0, repliky = "MUŽ: „Dobrý den.“"))
        val bez = listOf(SbPanel(1, "a", sekundy = 4.0))
        assertTrue(SbFilmPrepis.hlidka(1, SbUsek(s), 0, 1).contains("Acting (only in shots with a listed line)"))
        assertFalse(SbFilmPrepis.hlidka(1, SbUsek(bez), 0, 1).contains("Acting (only in shots with a listed line)"))
        // Příručka se nejmenuje — přepisovač by napodobil její vzorový příklad.
        assertFalse(SbFilmPrepis.hlidka(1, SbUsek(s), 0, 1).contains("guide"))
    }

    /** Hlasy ze čtení jdou stejně do všech úseků a zavřené rty po replice. */
    @Test fun `hlasy postav jsou v kazdem useku stejne`() {
        val c = SbFilmPlan.precti(
            "TITLE: none | TOTAL: none | SHOTS: 2 | GRID: 1x2 | VOICES: MUŽ = a man in his 30s with a low, " +
                "calm voice; DĚJ = nothing; ŽENA = a young woman with a bright voice\n" +
                "PANEL 1 | none | medium | static | He enters. | MUŽ: \"Dobrý den.\"\n" +
                "PANEL 2 | none | medium | static | She smiles. | ŽENA: \"Ahoj.\"",
        )
        assertEquals(mapOf("MUŽ" to "a man in his 30s with a low, calm voice", "ŽENA" to "a young woman with a bright voice"), c.hlasy)
        val plan = SbFilmPlan.naplanuj(c)
        assertEquals(c.hlasy, plan.hlasy)
        val id = SbFilmPrepis.idMluvcich(plan.panely)
        val h1 = SbFilmPrepis.hlidka(1, SbUsek(plan.panely.take(1)), 0, 2, true, id, "Czech", plan.hlasy)
        val h2 = SbFilmPrepis.hlidka(1, SbUsek(plan.panely.drop(1)), 1, 2, true, id, "Czech", plan.hlasy)
        val veta = "MUŽ (S1) — a man in his 30s with a low, calm voice"
        assertTrue(h1.contains(veta) && h2.contains(veta))
        assertTrue(h1.contains("closes their lips"))
    }
}
