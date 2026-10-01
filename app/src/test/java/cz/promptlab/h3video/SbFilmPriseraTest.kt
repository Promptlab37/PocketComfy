package cz.promptlab.h3video

import cz.promptlab.h3video.data.SbFilmPlan
import cz.promptlab.h3video.data.SbFilmPrepis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Storyboard „Internetu už bylo dost“ (1. 10. 2026): čtení po řádcích vrátilo repliky
 * bez jména a bez uvozovek („PANEL 1 | Tak, pro dnešek už bylo internetu dost.“) —
 * appka je zahodila a všech 6 záběrů šlo jako SILENT SHOT. Odpovědi jsou přesně
 * ze serveru.
 */
class SbFilmPriseraTest {

    private val cele = "TITLE: INTERNETU UŽ BYLO DOST | TOTAL: 20s | SHOTS: 6 | GRID: 2x3 | VOICES: none | LOOKS: none | " +
        "MUSIC: dance, upbeat, electronic drums and synths, 120 BPM PANEL 1 | 0-4s | extreme close-up | static | " +
        "The woman stares directly into the camera with a stern expression. | none PANEL 2 | 4-6s | medium | static | " +
        "The woman points her finger directly at the viewer. | none PANEL 3 | 6-9s | wide | static | The woman strikes " +
        "a dance pose with her hands on her hips. | none PANEL 4 | 9-13s | wide | static | The woman dances energetically, " +
        "pumping her arms and moving her hips. | none PANEL 5 | 13-16s | close-up | static | The woman leans forward, " +
        "looking surprised and confused. | none PANEL 6 | 16-20s | medium | static | The woman dances and points toward a door. | none"

    private val radky = listOf(
        "PANEL 1 | Tak, pro dnešek už bylo internetu dost. | MOOD: Rozhořčení. PANEL 2 | Běž spát. | MOOD: Autoritativní.",
        "PANEL 3 | none | MOOD: Drzé sebevědomí. PANEL 4 | none | MOOD: Nadšení.",
        "PANEL 5 | Ty tu ještě jsi? | MOOD: Pobouřený údiv. PANEL 6 | Vypnout. A do postele! | MOOD: Komicky přísná.",
    )

    @Test
    fun `repliky bez jmena se nezahodi`() {
        val repliky = radky.fold(mapOf<Int, String>()) { acc, r -> acc + SbFilmPlan.prectiRepliky(r) }
        val panely = SbFilmPrepis.slucCteni(SbFilmPlan.precti(cele).panely, repliky)
        val r = panely.associate { it.cislo to SbFilmPrepis.repliky(it.repliky).map { x -> x.second } }
        assertEquals(listOf("Tak, pro dnešek už bylo internetu dost."), r[1])
        assertEquals(listOf("Běž spát."), r[2])
        assertEquals(emptyList<String>(), r[3])
        assertEquals(emptyList<String>(), r[4])
        assertEquals(listOf("Ty tu ještě jsi?"), r[5])
        assertEquals(listOf("Vypnout. A do postele!"), r[6])
    }

    @Test
    fun `nalada se nebere jako replika a zvuk se precte`() {
        val odpoved = "PANEL 3 | none | MOOD: Drzé sebevědomí. | SOUND: Nástup tanečního beatu. " +
            "PANEL 4 | none | MOOD: Nadšení. | SOUND: Taneční hudba."
        assertEquals(mapOf(3 to "", 4 to ""), SbFilmPlan.prectiRepliky(odpoved))
        assertEquals(mapOf(3 to "Nástup tanečního beatu.", 4 to "Taneční hudba."), SbFilmPlan.prectiZvuky(odpoved))
        assertEquals("Drzé sebevědomí.", SbFilmPlan.prectiNalady(odpoved)[3])
        val p = SbFilmPlan.doplnZvuk("The woman dances. Mood: Nadšení.", "Taneční hudba.")
        assertTrue(p.endsWith("Sound: Taneční hudba."))
    }

    @Test
    fun `replika se jmenem zustane, jak je`() {
        assertEquals(mapOf(1 to "Příšera: \"Běž spát.\""), SbFilmPlan.prectiRepliky("PANEL 1 | Příšera: \"Běž spát.\" | MOOD: none | SOUND: none"))
        assertTrue(SbFilmPlan.otazkaRadku(1, 2).contains("Transcribe"))
    }

    /** 5.54: bez vytištěného jména si model vymyslel „TY“ a „VY“ z první věty (odpověď ze serveru). */
    @Test
    fun `zajmeno neni mluvci`() {
        val odpoved = "PANEL 5 | TY: \"Ty tu ještě jsi?\" | MOOD: Pobouřený údiv. | SOUND: none " +
            "PANEL 6 | VY: \"Vypnout. A do postele!\" | MOOD: Komicky přísná. | SOUND: none"
        val r = SbFilmPlan.prectiRepliky(odpoved)
        assertEquals(listOf("Mluvčí" to "Ty tu ještě jsi?"), SbFilmPrepis.repliky(SbFilmPrepis.sloucit("", r.getValue(5))))
        assertEquals(listOf("Mluvčí" to "Vypnout. A do postele!"), SbFilmPrepis.repliky(SbFilmPrepis.sloucit("", r.getValue(6))))
        assertEquals(listOf("Anna" to "Kde je?", "Mluvčí" to "Tady."),
            SbFilmPrepis.repliky(SbFilmPrepis.sloucit("", "Anna: \"Kde je?\"; \"Tady.\"")))
    }

    /** 5.55: řádek se jen opíše a roztřídí appka — přesná odpověď ze serveru (jméno přečteno). */
    @Test
    fun `prepis radku se roztridi`() {
        val prepis = "PANEL 5 5 | 13–16 s Obraz: Ztuhne a zírá do kamery. Emoce: Pobouřený údiv. Příšera: „Ty tu ještě jsi?“ " +
            "PANEL 6 6 | 16–20 s Obraz: Dupne a ukáže ke dveřím. Emoce: Komicky příšná. Příšera: „Vypnout. A do postele!“"
        val o = SbFilmPlan.prevedPrepis(prepis)
        val r = SbFilmPlan.prectiRepliky(o)
        assertEquals(listOf("Příšera" to "Ty tu ještě jsi?"), SbFilmPrepis.repliky(SbFilmPrepis.sloucit("", r.getValue(5))))
        assertEquals(listOf("Příšera" to "Vypnout. A do postele!"), SbFilmPrepis.repliky(SbFilmPrepis.sloucit("", r.getValue(6))))
        assertEquals("Pobouřený údiv.", SbFilmPlan.prectiNalady(o)[5])
        assertEquals(emptyMap<Int, String>(), SbFilmPlan.prectiZvuky(o))
    }

    @Test
    fun `prepis po radcich se zvukem a bez repliky`() {
        val prepis = "PANEL 3\n3 | 6–9 s\nObraz: Celá postava v taneční póze.\nEmoce: Drzé sebevědomí.\nZvuk: Nástup tanečního beatu.\n" +
            "PANEL 4\n4 | 9–13 s\nObraz: Pohupuje boky a mává rukama.\nEmoce: Nadšení.\nZvuk: Taneční hudba."
        val o = SbFilmPlan.prevedPrepis(prepis)
        assertEquals(mapOf(3 to "", 4 to ""), SbFilmPlan.prectiRepliky(o))
        assertEquals(mapOf(3 to "Nástup tanečního beatu.", 4 to "Taneční hudba."), SbFilmPlan.prectiZvuky(o))
        assertEquals("Nadšení.", SbFilmPlan.prectiNalady(o)[4])
    }

    @Test
    fun `prepis jine formy titulku`() {
        // Dva mluvčí, víceslovné jméno, anglické štítky, replika přes dva řádky, věta bez jména.
        val o = SbFilmPlan.prevedPrepis(
            "PANEL 1\n00-04s\nAKCE: Anna vejde do kuchyně.\nANNA: \"Kde je?\"\nStarý pán: „No přece tady,\nza dveřmi.“\n" +
                "PANEL 2\nACTION: He shrugs.\nMOOD: tired\nSFX: rain\n\"Who cares?\""
        )
        val r = SbFilmPlan.prectiRepliky(o)
        assertEquals(listOf("ANNA" to "Kde je?", "Starý pán" to "No přece tady, za dveřmi."),
            SbFilmPrepis.repliky(SbFilmPrepis.sloucit("", r.getValue(1))))
        assertEquals(listOf("Mluvčí" to "Who cares?"), SbFilmPrepis.repliky(SbFilmPrepis.sloucit("", r.getValue(2))))
        assertEquals("tired", SbFilmPlan.prectiNalady(o)[2])
        assertEquals("rain", SbFilmPlan.prectiZvuky(o)[2])
    }

    @Test
    fun `stara odpoved projde beze zmeny`() {
        val stara = "PANEL 1 | Anna: \"Ahoj.\" | MOOD: none | SOUND: none"
        assertEquals(stara, SbFilmPlan.prevedPrepis(stara))
    }

    /** 5.55: celý storyboard s novou otázkou — tři přesné odpovědi ze serveru (1. 10. 2026). */
    @Test
    fun `cely storyboard z prepisu ma jmeno prisery`() {
        val prepisy = listOf(
            "PANEL 1 1 | 0-4 s Obraz: Přísný pohled do kamery. Emoce: Rozhořčení. Příšera: „Tak, pro dnešek už bylo internetu dost.“ PANEL 2 2 | 4-6 s Obraz: Nakloní se a ukáže na diváka. Emoce: Autoritativní. Příšera: „Běž spát.“",
            "PANEL 3 3 | 6-9 s Obraz: Celá postava v taneční póze. Emoce: Drží sebevědomí. Zvuk: Nástup tanečního beatu. PANEL 4 4 | 9-13 s Obraz: Pohupuje boky a mává rukama. Emoce: Nadšení. Zvuk: Taneční hudba.",
            "PANEL 5 5 | 13–16 s Obraz: Ztuhne a zírá do kamery. Emoce: Pobouřený údiv. Příšera: „Ty tu ještě jsi?“ PANEL 6 6 | 16–20 s Obraz: Dupne a ukáže ke dveřím. Emoce: Komicky příšná. Příšera: „Vypnout. A do postele!“",
        ).map { SbFilmPlan.prevedPrepis(it) }
        val repliky = prepisy.fold(mapOf<Int, String>()) { acc, r -> acc + SbFilmPlan.prectiRepliky(r) }
        val panely = SbFilmPrepis.slucCteni(SbFilmPlan.precti(cele).panely, repliky)
        val r = panely.associate { it.cislo to SbFilmPrepis.repliky(it.repliky) }
        assertEquals(listOf("Příšera" to "Tak, pro dnešek už bylo internetu dost."), r[1])
        assertEquals(listOf("Příšera" to "Běž spát."), r[2])
        assertEquals(emptyList<Pair<String, String>>(), r[3])
        assertEquals(emptyList<Pair<String, String>>(), r[4])
        assertEquals(listOf("Příšera" to "Ty tu ještě jsi?"), r[5])
        assertEquals(listOf("Příšera" to "Vypnout. A do postele!"), r[6])
        val zvuky = prepisy.fold(mapOf<Int, String>()) { acc, x -> acc + SbFilmPlan.prectiZvuky(x) }
        assertEquals(mapOf(3 to "Nástup tanečního beatu.", 4 to "Taneční hudba."), zvuky)
    }
}
