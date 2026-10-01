package cz.promptlab.h3video

import cz.promptlab.h3video.data.SbFilmPrepis
import cz.promptlab.h3video.data.SbPanel
import cz.promptlab.h3video.data.SbUsek
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Běh z telefonu 1. 10. 2026 (5.61): syrové výstupy přepisovače ze serveru prošlé
 * stejnými úpravami jako v appce (MainViewModel.napisPromptySb pro storyboard).
 */
class SbTelefonPrisera4Test {

    private val id = mapOf("Příšera" to "S1")

    private fun hotovy(soubor: String, usek: SbUsek): String {
        val sp = SbFilmPrepis
        val zabalene = sp.doplnD(sp.opravZnackyAPozadi(java.io.File("src/test/resources/$soubor").readText()), usek, "Czech", id)
        val cisty = sp.opravObrazky(sp.ocistiPrepis(zabalene, usek.panely.size), 1)
        return sp.doplnIdMluvciho(sp.bezDuplicitD(sp.bezUvozovekMimoD(sp.doplnD(cisty, usek, "Czech", id))), id)
    }

    private fun zkontroluj(z: String, repliky: List<String>) {
        val d = Regex("""<d>\[Czech] (.*?)</d>""").findAll(z).map { it.groupValues[1] }.toList()
        assertEquals(repliky, d)
        Regex("""<d>""").findAll(z).forEach { m ->
            assertTrue("(S1) před <d>", z.substring(maxOf(0, m.range.first - 40), m.range.first).contains("(S1)"))
        }
        val mimo = Regex("""<d>.*?</d>""", RegexOption.DOT_MATCHES_ALL).replace(z, "").replace("Příšera", "")
        assertFalse("uvozovky mimo <d>: $mimo", Regex("""[“„"«»”]""").containsMatchIn(mimo))
        assertFalse("čeština mimo <d>: $mimo", Regex("""[ěščřžůťďňĚŠČŘŽŮŤĎŇ]""").containsMatchIn(mimo))
        assertEquals(setOf("<Subject 1>"), Regex("""<Subject \d+>""").findAll(z).map { it.value }.toSet())
    }

    @Test
    fun `usek 1`() {
        val u = SbUsek(listOf(
            SbPanel(1, "a", "close-up", "static", 4.0, repliky = "Příšera: „Pro dnes bylo internetu dost.“"),
            SbPanel(2, "b", "medium", "static", 2.0, repliky = "Příšera: „Běž spát.“"),
            SbPanel(3, "c", "wide", "static", 3.0),
        ))
        zkontroluj(hotovy("prisera4_usek1.txt", u), listOf("Pro dnes bylo internetu dost.", "Běž spát."))
    }

    @Test
    fun `usek 2`() {
        val u = SbUsek(listOf(
            SbPanel(4, "a", "wide", "static", 4.0),
            SbPanel(5, "b", "close-up", "static", 3.0, repliky = "Příšera: „Ty tu ještě jsi?“"),
            SbPanel(6, "c", "wide", "static", 4.0, repliky = "Příšera: „Vypnout. A do postele!“"),
        ))
        zkontroluj(hotovy("prisera4_usek2.txt", u), listOf("Ty tu ještě jsi?", "Vypnout. A do postele!"))
    }

    /** 5.62: panel 5 smazaný, přepisovač dal repliku v <d> i do shrnutí (přesný výstup ze serveru). */
    @Test
    fun `replika ve shrnuti jen jednou`() {
        val u = SbUsek(listOf(
            SbPanel(4, "a", "wide", "static", 4.0),
            SbPanel(6, "c", "wide", "static", 4.0, repliky = "Příšera: „Vypnout. A do postele!“"),
        ))
        val z = hotovy("prisera5_usek2_dup.txt", u)
        zkontroluj(z, listOf("Vypnout. A do postele!"))
        assertTrue(z.contains("in Czech as the line, voiced"))
    }

    /** 5.66: replika v záběru jen v uvozovkách + v <d> ve shrnutí — úklid ji dřív ze záběru smazal. */
    @Test
    fun `replika v zaberu i kdyz je ve shrnuti`() {
        val u = SbUsek(listOf(
            SbPanel(4, "a", "wide", "static", 4.0),
            SbPanel(6, "c", "wide", "static", 4.0, repliky = "Příšera: „Vypnout. A do postele!“"),
        ))
        val z = hotovy("prisera6_usek2_citace.txt", u)
        val zabery = z.substring(z.indexOf("detailed_description:"))
        assertTrue(zabery.contains("<d>[Czech] Vypnout. A do postele!</d>"))
        assertEquals(emptyList<String>(), SbFilmPrepis.chybejiciRepliky(z, u))
        // Ve shrnutí už značka není (jen jednou ve videu).
        assertEquals(1, Regex("""<d>""").findAll(z).count())
    }
}
