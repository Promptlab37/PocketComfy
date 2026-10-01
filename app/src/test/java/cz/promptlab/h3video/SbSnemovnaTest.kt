package cz.promptlab.h3video

import cz.promptlab.h3video.data.SbFilmPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 5.67: storyboard „Noční směna ve Sněmovně“ (1. 10. 2026) — mřížka 4×2, tři mluvčí se
 * jmenovkami v obrázcích, dvouřádkové repliky a řádek „Kontinuita:“ pod mřížkou.
 * Čtení 5.66 mělo repliky přesně, ale: nálada „Děsivě přísný“ vyšla jako „Terrifying monster“
 * (panely po 330 px), kontinuita se nečetla vůbec a děj i kamera se brali z odhadu obrázku
 * („Prudký odjezd kamery“ → static, „podívá se na boty“ → points at their shoes).
 */
class SbSnemovnaTest {

    /** Skutečné přepisy řádků ze serveru (spojené do jednoho řádku přepisovačem). */
    private val radek1 = "PANEL 1 BABIŠ 1 | 0-4 s Obraz: Babíš se nakloní přímo do kamery. Emoce: Děsivé příšny. " +
        "BABIŠ: „Přestaň scrollovat!“ PANEL 2 BABIŠ 2 | 4-8 s Obraz: Babíš ukáže telefon s dalším videem. " +
        "Emoce: Škodolibost. BABIŠ: „Nebo ti pustím dalších sedmnáct minut.“ PANEL 3 PAVEL 3 | 8-12 s " +
        "Obraz: Pavel sedí na jediné židli. Emoce: Ledový klid. PAVEL: „Já už sedím. Vy si to vyřešte.“ " +
        "PANEL 4 MACINKA 4 | 12-16 s Obraz: Macinka ukazuje na Pavlovu židli. Emoce: Rozhořčení. " +
        "MACINKA: „Tohle místo bylo slíbený mně!“"
    private val radek2 = "PANEL 5 5 | 16-20 s Obraz: Babiš posune židli s Pavlem o půl metru. Emoce: Vítězoslavná mazanost. " +
        "BABIŠ: „Tak. Ted' je to jiný místo.“ PANEL 6 6 | 20-24 s Obraz: Kamera odhalí jejich vysoké platformové " +
        "kozačky. Emoce: Macinkova pýcha. MACINKA: „Já mám stejně nejvyšší mandát.“ PANEL 7 7 | 24-27 s " +
        "Obraz: Pavel se podívá na boty, pak na Macinku. Emoce: Suchý humor. PAVEL: „To jsou podpatky.“ " +
        "PANEL 8 8 | 27-32 s Obraz: Prudký odjezd kamery. Tři malé postavy se hádají o židli. Emoce: Absurdní " +
        "beznaděj. BABIŠ: „Běž do postele. My se o tu židli pohádáme i bez tebe.“"

    @Test
    fun `dej pod panelem se precte a nesplete se s replikou ani naladou`() {
        val p1 = SbFilmPlan.prevedPrepis(radek1)
        val p2 = SbFilmPlan.prevedPrepis(radek2)
        val deje = SbFilmPlan.prectiDeje(p1) + SbFilmPlan.prectiDeje(p2)
        assertEquals((1..8).toSet(), deje.keys)
        assertEquals("Pavel se podívá na boty, pak na Macinku.", deje[7])
        assertEquals("Prudký odjezd kamery. Tři malé postavy se hádají o židli.", deje[8])
        val repliky = SbFilmPlan.prectiRepliky(p1) + SbFilmPlan.prectiRepliky(p2)
        assertEquals("BABIŠ: \"Běž do postele. My se o tu židli pohádáme i bez tebe.\"", repliky[8])
        assertEquals("PAVEL: \"Já už sedím. Vy si to vyřešte.\"", repliky[3])
        val nalady = SbFilmPlan.prectiNalady(p1) + SbFilmPlan.prectiNalady(p2)
        assertEquals("Absurdní beznaděj.", nalady[8])
        assertFalse(deje.values.any { "Emoce" in it || "„" in it })
    }

    @Test
    fun `preklad deje a kamery, none kameru nemeni`() {
        val otazka = SbFilmPlan.otazkaPrekladu(emptyMap(), mapOf(1 to "Děsivě přísný."), mapOf(8 to "Prudký odjezd kamery."))
        assertTrue("ACTION 8: Prudký odjezd kamery." in otazka)
        assertTrue("CAMERA <number>" in otazka)
        assertTrue("odjezd kamery = the camera pulls back" in otazka)
        val odp = "MOOD 1 = Terrifyingly stern. ACTION 7 = Pavel looks at the boots, then at Macinka. CAMERA 7 = none " +
            "ACTION 8 = A sudden pull back of the camera. Three small figures argue over the chair. CAMERA 8 = fast pull back"
        val akce = SbFilmPlan.prectiPreklad(odp, "ACTION", setOf(7, 8))
        assertEquals("Pavel looks at the boots, then at Macinka", akce[7])
        assertEquals(mapOf(8 to "fast pull back"), SbFilmPlan.prectiPreklad(odp, "CAMERA", setOf(7, 8)))
        assertEquals(mapOf(1 to "Terrifyingly stern"), SbFilmPlan.prectiPreklad(odp, "MOOD", setOf(1)))
        // Bez děje zůstává otázka jako dřív.
        assertFalse("ACTION" in SbFilmPlan.otazkaPrekladu(emptyMap(), mapOf(1 to "x"), emptyMap()))
    }

    @Test
    fun `kontinuita z prvniho cteni`() {
        val c = SbFilmPlan.precti(
            "TITLE: NOČNÍ SMĚNA VE SNĚMOVNĚ | TOTAL: 32 | SHOTS: 8 | GRID: 2x4 | VOICES: none | LOOKS: none | " +
                "MUSIC: comedic, 100 BPM | CONTINUITY: Three identical caricatures, suits, boots, one chair and the same hall. " +
                "PANEL 1 | 0-4s | extreme close-up | static | Babiš leans in. | Babiš: \"Přestaň scrollovat!\"",
        )
        assertEquals("Three identical caricatures, suits, boots, one chair and the same hall", c.kontinuita)
        assertEquals("comedic, 100 BPM", c.hudbaStyl)
        assertEquals(1, c.panely.size)
        assertEquals(null, SbFilmPlan.precti("TITLE: x | CONTINUITY: none").kontinuita)
    }

    @Test
    fun `zvetseni podle sirky panelu`() {
        // 4 panely v řádku 1312 px → každý by měl 700 px, ale řádek nejvýš 2300 px.
        assertEquals(2300f / 1312, SbFilmPlan.zvetseniRadku(1312, 4), 0.001f)
        // 2 panely v řádku 683 px — stejně jako do 5.66.
        assertEquals(1400f / 683, SbFilmPlan.zvetseniRadku(683, 2), 0.001f)
        assertEquals(2300f / 600, 3f, 1f)
        assertEquals(3f, SbFilmPlan.zvetseniRadku(500, 4), 0f)
        assertEquals(2300f / 1000, SbFilmPlan.zvetseniRadku(1000, 4), 0.001f)
        assertEquals(1f, SbFilmPlan.zvetseniRadku(3000, 2), 0f)
    }
}
