package cz.promptlab.h3video

import cz.promptlab.h3video.data.SbFilmPlan
import cz.promptlab.h3video.data.SbScenar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Třetí scénář uživatele (hrnek, 30. 9. 2026), rekonstruovaný z toho, co
 * dostal přepisovač: název okna za hlavičkou, „Keramička, spokojeně: „…““,
 * „tvůrce“ (rod na -e), postavy bez popisu lidí.
 */
class SbScenarHrnekTest {

    private val scenar = """
        Scénář – produktové video
        Formát: 9:16, přibližně 19 sekund.
        Postavy:
        Produkt: Jeden konkrétní modrý keramický hrnek se zlatým sluncem. Ve všech oknech musí zachovat stejný tvar, barvu, znak i ouško.
        [OKNO 1 | 0–3 s] NÁPAD
        Obraz: Keramička ukazuje tvůrci svůj nový hrnek.
        Keramička: „Chtěla bych ho lidem ukázat ve videu.“
        [OKNO 2 | 3–6 s]
        PODKLADY
        Obraz: Tvůrce fotografuje tentýž hrnek na neutrálním stole.
        Zvuk: Cvaknutí závěrky.
        [OKNO 3 | 6–10 s] PLÁN ZÁBĚRŮ
        Obraz: Oba sledují na monitoru tři záběry.
        Tvůrce: „Začneme detailem. Pak ukážeme hrnek v použití.“
        [OKNO 4 | 10–13 s] TVORBA VIDEA
        Obraz: Na monitoru běží střih.
        [OKNO 5 | 13–16 s] HOTOVÝ VÝSLEDEK
        Obraz: Detail telefonu v ruce keramičky. Z kávy stoupá pára.
        Keramička, spokojeně: „Přesně takhle jsem si to představovala.“
        [OKNO 6 | 16–19 s] ZÁVĚR
        Obraz: Keramička se přirozeně usměje.
    """.trimIndent()

    private val obrazek = SbFilmPlan.precti(
        "TITLE: none | TOTAL: none | SHOTS: 6 | GRID: 2x3 | VOICES: Man = a man in his 30s with a calm, neutral voice; " +
            "Woman = a woman in her 30s with a warm, soft voice | LOOKS: Man = dark hair, dark shirt; Woman = curly brown hair, " +
            "orange shirt | MUSIC: warm " + (1..6).joinToString(" ") { "PANEL $it | none | medium | static | shot $it | none" },
    )

    @Test
    fun `nazvy oken, podani za carkou, rod a hlasy z obrazku`() {
        val s = SbScenar.rozeber(scenar)!!
        assertEquals(6, s.okna.size)
        // Název okna (NÁPAD, PODKLADY…) do popisu nepatří.
        assertEquals("Keramička ukazuje tvůrci svůj nový hrnek.", s.okna[0].obraz)
        assertEquals("Tvůrce fotografuje tentýž hrnek na neutrálním stole.", s.okna[1].obraz)
        assertEquals("Oba sledují na monitoru tři záběry.", s.okna[2].obraz)
        // Replika s podáním za čárkou se nesmí ztratit.
        val r5 = s.okna[4].repliky.single()
        assertEquals("Keramička", r5.kdo)
        assertEquals("spokojeně", r5.podani)
        assertEquals("Přesně takhle jsem si to představovala.", r5.text)
        assertFalse(s.okna[4].obraz.contains("spokojeně"))
        // Rod: -e nejisté, keramička žena.
        assertEquals(false, SbScenar.zena("Tvůrce", ""))
        assertNull(SbScenar.zena("Marie", ""))
        assertEquals(true, SbScenar.zena("Keramička", ""))
        // Hlasy a vzhled mluvčích z obrázku podle rodu, produkt ze scénáře zůstává.
        val c = SbScenar.cteni(s, obrazek)
        assertEquals("a woman in her 30s with a warm, soft voice", c.hlasy["Keramička"])
        assertEquals("a man in his 30s with a calm, neutral voice", c.hlasy["Tvůrce"])
        assertEquals("curly brown hair, orange shirt", c.vzhled["Keramička"])
        assertEquals("dark hair, dark shirt", c.vzhled["Tvůrce"])
        assertTrue(c.vzhled.getValue("Produkt").startsWith("Jeden konkrétní modrý"))
    }

    @Test
    fun `vymyslene znacky na subject`() {
        val t = "<Subject 1> is the woman. <Subject 2> is the man. <Product> is the mug. <Product> appears in [Shot 1]. <d>[Czech] Ahoj.</d> <Picture 1>"
        val o = SbScenar.opravZnacky(t)
        assertEquals("<Subject 1> is the woman. <Subject 2> is the man. <Subject 3> is the mug. <Subject 3> appears in [Shot 1]. <d>[Czech] Ahoj.</d> <Picture 1>", o)
    }
}
