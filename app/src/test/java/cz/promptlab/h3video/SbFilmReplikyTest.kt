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
        // Panel bez repliky je informace „tady replika není“ (5.05).
        assertEquals(setOf(5, 6, 7), r.keys)
        assertEquals("", r[7])
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

    /**
     * Skutečný běh 29. 9. 2026 (servis mobilů): celé čtení zdvojilo repliky do
     * panelů 5 a 7, řádek je správně nechal prázdné.
     */
    @Test
    fun `zdvojena replika ze souseda se smaze, stejne repliky u obou zustanou`() {
        val celek = SbFilmPlan.precti(
            "TITLE: none | TOTAL: none | SHOTS: none | GRID: 2x4 | VOICES: none " +
                "PANEL 5 | none | close-up | static | The woman looks thoughtful. | ŽENA: „A basmati, nebo jasmínové?“ " +
                "PANEL 6 | none | medium | static | The woman asks. | ŽENA: „A basmati, nebo jasmínové?“ " +
                "PANEL 7 | none | close-up | static | The technician is shocked. | MUŽ: „To je... vlastně skoro jedno.“ " +
                "PANEL 8 | none | medium | static | He rubs his face. | MUŽ: „To je... vlastně skoro jedno.“"
        )
        val radek = SbFilmPlan.prectiRepliky(
            "PANEL 5 | PANEL 6 | ŽENA: „A basmati, nebo jasmínové?“ PANEL 7 | PANEL 8 | MUŽ: „To je... vlastně skoro jedno.“"
        )
        val p = SbFilmPrepis.slucCteni(celek.panely, radek).associate { it.cislo to it.repliky }
        assertEquals("", p[5])
        assertEquals("", p[7])
        assertTrue(p[6]!!.contains("basmati"))
        assertTrue(p[8]!!.contains("skoro jedno"))
        // Dvě skutečně stejné repliky vedle sebe: řádek je vypsal u obou → zůstanou.
        val stejne = SbFilmPrepis.slucCteni(celek.panely,
            SbFilmPlan.prectiRepliky("PANEL 5 | ŽENA: „A basmati, nebo jasmínové?“ PANEL 6 | ŽENA: „A basmati, nebo jasmínové?“"))
        assertTrue(stejne.first { it.cislo == 5 }.repliky.contains("basmati"))
    }

    @Test
    fun `emoce jde do popisu jako Mood`() {
        val (rep, dej) = SbFilmPrepis.oddelDej("EMOCE: „Žena je vyděšená a ve stresu.“; ŽENA: „Prosím vás!“")
        assertEquals("ŽENA: „Prosím vás!“", rep)
        assertEquals("Mood: Žena je vyděšená a ve stresu.", dej)
    }

    @Test
    fun `vadny prepis se pozna`() {
        assertTrue(SbFilmPrepis.vadnyPrepis("<Audio 1> is the synchronized audio track", 4))
        assertTrue(SbFilmPrepis.vadnyPrepis("<Subject 1> (appears in [Shot 1], [Shot 6])", 4))
        assertFalse(SbFilmPrepis.vadnyPrepis("[Shot 4] At 00:09.500, <Subject 2> says <d>[Czech] Dejte ho do rýže.</d>", 4))
    }

    private fun vzor(nazev: String) =
        javaClass.getResource("/sbfilm/$nazev")!!.readText(Charsets.UTF_8)

    /**
     * Skutečné vadné přepisy z 29. 9. 2026 (4 záběry): vymyšlené <Audio 1>
     * a [Shot 6]/[Shot 7] v retenci. Čištění je odstraní, repliky a záběry
     * zůstanou beze změny.
     */
    @Test
    fun `cisteni odstrani vymyslene stopy a cizi zabery`() {
        for (nazev in listOf("prepis_vadny_audio_shoty.txt", "prepis_vadny_audio.txt")) {
            val puvodni = vzor(nazev)
            val cisty = SbFilmPrepis.ocistiPrepis(puvodni, 4)
            assertFalse(nazev, SbFilmPrepis.vadnyPrepis(cisty, 4))
            // Popis záběrů beze změny, repliky všechny.
            fun popis(t: String) = t.substringAfter("detailed_description:").substringBefore("overall_soundscape:")
            assertEquals(nazev, popis(puvodni), popis(cisty))
            assertEquals(nazev, 4, Regex("<d>").findAll(popis(cisty)).count())
            // Šest polí v pořadí a žádné prázdné.
            val pole = listOf("subject_definitions:", "summary:", "retention_analysis:",
                "detailed_description:", "overall_soundscape:", "non_diegetic_music:")
            assertEquals(nazev, pole, cisty.lines().filter { it.trim() in pole }.map { it.trim() })
            assertTrue(nazev, cisty.contains("<Subject 1> (appears in [Shot 1], [Shot 3]): fully_preserved"))
            assertTrue(nazev, cisty.contains("<Subject 2> (appears in [Shot 2], [Shot 4]): fully_preserved"))
            assertTrue(nazev, cisty.contains("<Subject 2> is the calm technician"))
            assertTrue(nazev, cisty.contains("[reference generation] The target video is"))
        }
        // Čistý přepis zůstane znak po znaku stejný.
        val dobry = "summary:\n[reference generation] Two shots.\n\nretention_analysis:\n" +
            "<Subject 1> (appears in [Shot 1]): fully_preserved - ok."
        assertEquals(dobry, SbFilmPrepis.ocistiPrepis(dobry, 2))
    }

    /** Úsek 2 (panely 5–8) po opravě hlídky: v retenci čísla panelů místo záběrů. */
    @Test
    fun `cisteni opravi cisla panelu v retenci`() {
        val puvodni = vzor("prepis_panely_misto_zaberu.txt")
        val cisty = SbFilmPrepis.ocistiPrepis(puvodni, 4)
        assertFalse(SbFilmPrepis.vadnyPrepis(cisty, 4))
        assertTrue(cisty.contains("<Subject 1> (appears in [Shot 1], [Shot 2]): fully_preserved"))
        assertTrue(cisty.contains("<Subject 2> (appears in [Shot 3], [Shot 4]): fully_preserved"))
        assertEquals(puvodni.substringAfter("summary:").substringBefore("retention_analysis:"),
            cisty.substringAfter("summary:").substringBefore("retention_analysis:"))
        assertEquals(puvodni.substringAfter("detailed_description:"), cisty.substringAfter("detailed_description:"))
    }
}
