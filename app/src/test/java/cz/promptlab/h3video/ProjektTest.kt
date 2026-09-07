package cz.promptlab.h3video

import cz.promptlab.h3video.data.KARTY_PRO_ZABER
import cz.promptlab.h3video.data.Mode
import cz.promptlab.h3video.data.Projekt
import cz.promptlab.h3video.data.Zaber
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Projekt drží záběry v pořadí — a pořadí je u filmu to hlavní, co se nesmí
 * rozsypat. Přehazování je proto pod testem včetně krajů seznamu.
 */
class ProjektTest {

    private fun projekt(pocet: Int) = Projekt(
        id = 1L,
        nazev = "Film",
        zabery = (1..pocet).map { Zaber(id = it.toLong(), nazev = "Z$it") },
    )

    private fun poradi(p: Projekt) = p.zabery.map { it.nazev }

    @Test
    fun `presun nahoru a dolu prohodi sousedy`() {
        val p = projekt(4)
        assertEquals(listOf("Z1", "Z3", "Z2", "Z4"), poradi(p.presun(3L, -1)))
        assertEquals(listOf("Z2", "Z1", "Z3", "Z4"), poradi(p.presun(1L, 1)))
    }

    @Test
    fun `na kraji seznamu se nic nestane`() {
        val p = projekt(3)
        assertEquals(poradi(p), poradi(p.presun(1L, -1)))
        assertEquals(poradi(p), poradi(p.presun(3L, 1)))
    }

    @Test
    fun `presun neznameho zaberu nic nerozbije`() {
        val p = projekt(3)
        assertEquals(poradi(p), poradi(p.presun(99L, 1)))
    }

    @Test
    fun `uprava se tyka jen sveho zaberu`() {
        val p = projekt(3).uprav(2L) { it.copy(popis = "Anna vejde do haly") }
        assertEquals("Anna vejde do haly", p.zabery[1].popis)
        assertTrue(p.zabery.filterIndexed { i, _ -> i != 1 }.all { it.popis.isBlank() })
    }

    @Test
    fun `hotovy je jen zaber s vysledkem`() {
        val p = projekt(3).uprav(2L) { it.copy(vysledek = "abc") }
        assertEquals(1, p.hotovych)
        assertTrue(p.zabery[1].jeHotovy)
        assertFalse(p.zabery[0].jeHotovy)

        // Odpojení vrátí záběr mezi nevyrobené — soubor v galerii zůstává.
        assertEquals(0, p.uprav(2L) { it.copy(vysledek = null) }.hotovych)
    }

    @Test
    fun `smazani nechá ostatni v poradi`() {
        assertEquals(listOf("Z1", "Z3"), poradi(projekt(3).smaz(2L)))
    }

    @Test
    fun `projekt sam sebe nenabizi jako kartu k vyrobe`() {
        // Jinak by šlo zacyklit záběr sám do sebe.
        assertFalse(Mode.PROJEKT in KARTY_PRO_ZABER)
        assertTrue(Mode.ALLINONE in KARTY_PRO_ZABER)
        assertEquals(Mode.entries.size - 1, KARTY_PRO_ZABER.size)
    }

    @Test
    fun `zaber bez vysledku hlasi prazdno`() {
        assertNull(Zaber(id = 1L).vysledek)
        assertFalse(Zaber(id = 1L).jeHotovy)
    }
}
