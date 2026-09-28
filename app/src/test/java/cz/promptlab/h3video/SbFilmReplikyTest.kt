package cz.promptlab.h3video

import cz.promptlab.h3video.data.SbFilmPlan
import cz.promptlab.h3video.data.SbFilmPrepis
import cz.promptlab.h3video.data.SbPanel
import cz.promptlab.h3video.data.SbUsek
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Repliky ze storyboardu (testerův „Pepa a chytrá domácnost“, 12 panelů,
 * 28. 9. 2026). Odpovědi modelu jsou skutečné — z celého obrázku a po řádcích.
 */
class SbFilmReplikyTest {

    private val zCelku = "TITLE: none | TOTAL: none | SHOTS: 12 | GRID: 3x4 PANEL 1 | none | medium | static | Pepa proudly shows his friend his smart home. | Pepa: \"Tady všechno řídí AI. Už nemusím dělat vůbec nic.\" PANEL 2 | none | medium | static | Pepa commands the system. | Pepa: \"Rozsviť obývák.\" ; AI: \"Objednávám obývací stůl.\" PANEL 5 | none | medium | static | Robot vacuum hits Pepa. | Kamrád: \"Aspoň ti to uklidí cestu k vypínání.\""

    @Test
    fun `cteni celku - repliky v sestem poli a mrizka`() {
        val c = SbFilmPlan.precti(zCelku)
        assertEquals(3, c.radku)
        assertEquals(4, c.sloupcu)
        assertEquals("Pepa commands the system.", c.panely[1].popis)
        assertTrue(c.panely[1].repliky.contains("Objednávám obývací stůl."))
    }

    @Test
    fun `cteni po radcich`() {
        val r = SbFilmPlan.prectiRepliky(
            "PANEL 5 | Kamarád: „Aspoň ti to uklidí cestu k vypínači.“ PANEL 6 | Pepa: „Pusť nějakou hudbu.“; Al: „Trouba předehřátá na 250 stupňů.“ PANEL 7 | none",
        )
        assertEquals(setOf(5, 6), r.keys)
        assertTrue(r[5]!!.contains("k vypínači"))
    }

    @Test
    fun `slouceni - jmena z celku, text z radku, hacky`() {
        val celek = "Pepa: \"Rozsviť obývák.\" ; AI: \"Objednávám obývací stůl.\""
        val radek = "Pepa: „Rozsvít’ obývák.“; Al: „Objednávám obývací stůl.“"
        assertEquals("Pepa: „Rozsvíť obývák.“; AI: „Objednávám obývací stůl.“", SbFilmPrepis.sloucit(celek, radek))
        // Jiný počet replik: platí ostřejší čtení celé.
        assertEquals("Kamarád: „Aspoň ti to uklidí cestu k vypínači.“",
            SbFilmPrepis.sloucit("", "Kamarád: „Aspoň ti to uklidí cestu k vypínači.“"))
    }

    @Test
    fun `apostrof modifikatoru a mluvci Al`() {
        assertEquals("Pepa: „Tak teď je to fakt chytrý.“", SbFilmPrepis.sloucit("", "Pepa: „Tak tedʼ je to fakt chytrý.“"))
        assertEquals("AI", SbFilmPrepis.repliky("Al: „Potvrzuji spuštění všech zařízení.“").single().first)
    }

    @Test
    fun `repliky bez strednika mezi uvozovkami`() {
        val r = SbFilmPrepis.repliky("Pepa: „Hlavně že mi ta chytrá domácnost šetří čas.“ Kamarád: „Jo. Už jsme ušetřili celej večer.“")
        assertEquals(listOf("Pepa", "Kamarád"), r.map { it.first })
        assertEquals("Jo. Už jsme ušetřili celej večer.", r[1].second)
    }

    @Test
    fun `hlidka useku - repliky svych panelu, stala ID pres cely film`() {
        val panely = listOf(
            SbPanel(1, "a", sekundy = 4.0, repliky = "Pepa: „Tady všechno řídí AI.“"),
            SbPanel(2, "b", sekundy = 4.0, repliky = "Pepa: „Rozsviť obývák.“; AI: „Objednávám obývací stůl.“"),
            SbPanel(3, "c", sekundy = 4.0, repliky = "Kamarád: „Výborný.“"),
        )
        val id = SbFilmPrepis.idMluvcich(panely)
        assertEquals(mapOf("Pepa" to "S1", "AI" to "S2", "Kamarád" to "S3"), id)
        // Úsek jen s panelem 3: jen jeho replika, ale ID z celého filmu.
        val h = SbFilmPrepis.hlidka(1, SbUsek(listOf(panely[2])), 1, 2, true, id, SbFilmPrepis.jazykFilmu(panely))
        assertTrue(h.contains("Kamarád (S3) says <d>[Czech] Výborný.</d>"))
        assertTrue(!h.contains("Rozsviť"))
        assertTrue(h.contains("never translate"))
    }
}
