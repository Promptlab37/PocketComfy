package cz.promptlab.h3video

import cz.promptlab.h3video.data.SbFilmPlan
import cz.promptlab.h3video.data.SbFilmPrepis
import cz.promptlab.h3video.data.SbPanel
import cz.promptlab.h3video.data.SbUsek
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 5.56: storyboard „Internetu už bylo dost“ (1. 10. 2026) — popisy panelů říkaly „The woman“,
 * repliky a fotka „Příšera“; přepisovač v úseku 2 definoval dvě postavy. Zvuk schovaný
 * v popisu se v úseku 1 ztratil a bez VOICES měl každý úsek jiný hlas.
 */
class SbJednaPostavaTest {

    private val p4 = SbPanel(4, "The woman dances energetically, pumping her arms and moving her hips. Mood: Nadšení. Sound: Taneční hudba.",
        "wide", "static", 4.0)
    private val p5 = SbPanel(5, "The woman leans forward, looking surprised and confused. Mood: Pobouřený údiv.", "close-up", "static", 3.0,
        repliky = "Příšera: „Ty tu ještě jsi?“")
    private val p3 = SbPanel(3, "The woman strikes a dance pose with her hands on her hips. Sound: Nástup tanečního beatu.", "wide", "static", 3.0)

    private fun hlidka(fotky: List<String> = listOf("Příšera"), popisy: List<String> = listOf(p3.popis, p4.popis, p5.popis)) =
        SbFilmPrepis.hlidka(
            2, SbUsek(listOf(p4, p5)), 1, 2, true,
            idMluvcich = mapOf("Příšera" to "S1"), jazykFilmu = "Czech",
            hlasy = mapOf("Příšera" to "a woman in her 50s with a deep, raspy voice"),
            predchozi = p3, jmenaFotek = fotky, popisyFilmu = popisy,
        )

    @Test
    fun `jedina postava ma vsude jmeno`() {
        val h = hlidka()
        assertFalse(h.contains("The woman"))
        assertTrue(h.contains("storyboard panel 4, wide, camera static: Příšera dances energetically"))
        assertTrue(h.contains("The previous part ended with: Příšera strikes a dance pose"))
        assertTrue(h.contains("Příšera (S1) — a woman in her 50s with a deep, raspy voice"))
    }

    @Test
    fun `zvuk je samostatny radek`() {
        val h = hlidka()
        assertTrue(h.contains("Mood: Nadšení.\n    sound in this shot (write it in English in your own words, in this shot and in overall_soundscape, without quotation marks): Taneční hudba."))
        assertFalse(h.contains("Sound: Taneční hudba"))
    }

    @Test
    fun `dve osoby se neprepisuji`() {
        val popisy = listOf("The woman hands the man a cup.", "The man smiles.")
        assertNull(SbFilmPrepis.jedinaPostava(popisy, listOf("Příšera"), listOf("Příšera"), emptyList()))
        assertNull(SbFilmPrepis.jedinaPostava(listOf("The woman sits."), listOf("Anna", "Petr"), emptyList(), emptyList()))
        assertTrue(hlidka(popisy = listOf("The woman hands the man a cup.")).contains("The woman dances"))
    }

    @Test
    fun `hlas se doplni do cteni`() {
        val cele = "TITLE: X | TOTAL: 20s | SHOTS: 6 | GRID: 2x3 | VOICES: none | LOOKS: none | MUSIC: dance PANEL 1 | 0-4s | close-up | static | A. | none"
        val h = SbFilmPlan.prectiHlasy("Příšera = a woman in her 50s with a deep, raspy voice.", listOf("Příšera"))
        assertEquals(mapOf("Příšera" to "a woman in her 50s with a deep, raspy voice"), h)
        val c = SbFilmPlan.precti(SbFilmPlan.doplnHlasy(cele, h))
        assertEquals("a woman in her 50s with a deep, raspy voice", c.hlasy["Příšera"])
        assertEquals("dance", c.hudbaStyl)
        assertEquals(1, c.panely.size)
        assertTrue(SbFilmPlan.otazkaHlasu(listOf("Příšera")).contains("Příšera"))
    }

    /** 5.57: přesný výstup přepisovače z 1. 10. 2026 — zvuk v uvozovkách mimo <d>. */
    @Test
    fun `uvozovky mimo repliku pryc`() {
        val v = "[Shot 3] At 00:06.000, wide shot. No dialogue or speech occurs. A sound cue “Nástup tanečního beatu” begins as the shot starts.\n" +
            "[Shot 1] She says, <d>[Czech] Tak, pro dnešek už bylo „internetu“ dost.</d> She closes her lips."
        val o = SbFilmPrepis.bezUvozovekMimoD(v)
        assertTrue(o.contains("A sound cue Nástup tanečního beatu begins"))
        assertTrue(o.contains("<d>[Czech] Tak, pro dnešek už bylo „internetu“ dost.</d>"))
    }

    /** 5.57: zvuk ze storyboardu se přeloží při čtení — přesná odpověď ze serveru. */
    @Test
    fun `preklad zvuku`() {
        val o = SbFilmPlan.prectiPrekladZvuku("PANEL 3 = Dance beat starts. PANEL 4 = Dance music.", setOf(3, 4))
        assertEquals(mapOf(3 to "Dance beat starts", 4 to "Dance music"), o)
        assertEquals(emptyMap<Int, String>(), SbFilmPlan.prectiPrekladZvuku("PANEL 9 = Rain.", setOf(3)))
        assertTrue(SbFilmPlan.otazkaZvuku(mapOf(3 to "Nástup tanečního beatu.")).endsWith("PANEL 3: Nástup tanečního beatu."))
    }

    /** 5.60: úsek 1 druhé Příšery — přepisovač dal obě repliky jen do uvozovek (přesný výstup ze serveru). */
    @Test
    fun `replika bez d se zabali`() {
        val surovy = java.io.File("src/test/resources/prisera2_usek1_bez_d.txt").readText()
        val usek = SbUsek(listOf(
            SbPanel(1, "Příšera stares.", "close-up", "static", 4.0, repliky = "Příšera: „Pro dnes bylo internetu dost.“"),
            SbPanel(2, "Příšera points.", "medium", "static", 2.0, repliky = "Příšera: „Běž spát.“"),
            SbPanel(3, "Příšera dances.", "wide", "static", 3.0),
        ))
        assertEquals(2, SbFilmPrepis.chybejiciRepliky(surovy, usek).size)
        val o = SbFilmPrepis.bezUvozovekMimoD(SbFilmPrepis.doplnD(surovy, usek, "Czech", mapOf("Příšera" to "S1")))
        assertEquals(emptyList<String>(), SbFilmPrepis.chybejiciRepliky(o, usek))
        assertTrue(o.contains("as described: (S1) <d>[Czech] Pro dnes bylo internetu dost.</d> His lips"))
        assertTrue(o.contains("gravelly voice: (S1) <d>[Czech] Běž spát.</d> His lips"))
        assertFalse(o.contains("“"))
        // Hotový prompt se nezmění.
        val hotovy = "[Shot 1] She says (S1) <d>[Czech] Běž spát.</d>"
        assertEquals(hotovy, SbFilmPrepis.doplnD(hotovy, SbUsek(listOf(usek.panely[1])), "Czech", mapOf("Příšera" to "S1")))
    }

    /** 5.60: hlas i vzhled jedním dotazem — přesná odpověď ze serveru (druhá Příšera). */
    @Test
    fun `hlas a vzhled z jedne odpovedi`() {
        val odp = "VOICE Příšera = an older man with a deep, commanding voice LOOK Příšera = long curly brown hair, wrinkled face, muscular body, black tank top, black boots"
        assertEquals(mapOf("Příšera" to "an older man with a deep, commanding voice"), SbFilmPlan.prectiHlasy(odp, listOf("Příšera")))
        assertEquals(mapOf("Příšera" to "long curly brown hair, wrinkled face, muscular body, black tank top, black boots"),
            SbFilmPlan.prectiVzhled(odp, listOf("Příšera")))
        val cele = "TITLE: X | TOTAL: 20s | SHOTS: 6 | GRID: 2x3 | VOICES: none | LOOKS: none | MUSIC: dance PANEL 1 | 0-4s | close-up | static | A. | none"
        val c = SbFilmPlan.precti(SbFilmPlan.doplnVzhled(SbFilmPlan.doplnHlasy(cele, SbFilmPlan.prectiHlasy(odp, listOf("Příšera"))),
            SbFilmPlan.prectiVzhled(odp, listOf("Příšera"))))
        assertEquals("an older man with a deep, commanding voice", c.hlasy["Příšera"])
        assertEquals("long curly brown hair, wrinkled face, muscular body, black tank top, black boots", c.vzhled["Příšera"])
        assertEquals("dance", c.hudbaStyl)
        // Jediná postava zůstane jediná, i když má vzhled pod svým jménem.
        assertTrue(SbFilmPrepis.jedinaPostava(listOf("The man dances."), listOf("Příšera"), emptyList(), c.vzhled.keys) != null)
    }

    /** 5.61: přepisovač vynechal (S1) — přesné výstupy ze serveru (třetí Příšera, 1. 10. 2026). */
    @Test
    fun `id mluvciho se doplni u jedineho mluvciho`() {
        for (f in listOf("prisera3_usek1.txt", "prisera3_usek2.txt")) {
            val o = SbFilmPrepis.doplnIdMluvciho(java.io.File("src/test/resources/$f").readText(), mapOf("Příšera" to "S1"))
            val d = Regex("""<d>""").findAll(o).count()
            assertEquals(f, 2, d)
            // Před každou <d> je (S1), nikde dvakrát.
            assertEquals(f, d, Regex("""\(S1\)\s*<d>|\(S1\)[^<]{0,40}<d>""").findAll(o).count())
            assertFalse(f, o.contains("(S1) (S1)"))
        }
        val o1 = SbFilmPrepis.doplnIdMluvciho(java.io.File("src/test/resources/prisera3_usek1.txt").readText(), mapOf("Příšera" to "S1"))
        assertTrue(o1.contains("he says, (S1) <d>[Czech] Pro dnes bylo internetu dost.</d>"))
        // Dva mluvčí: nic se nedoplňuje.
        val dva = "A says <d>[Czech] Ahoj.</d> B says <d>[Czech] Čau.</d>"
        assertEquals(dva, SbFilmPrepis.doplnIdMluvciho(dva, mapOf("A" to "S1", "B" to "S2")))
        // Už tam je: beze změny.
        val uz = "Příšera (S1) says <d>[Czech] Běž spát.</d>"
        assertEquals(uz, SbFilmPrepis.doplnIdMluvciho(uz, mapOf("Příšera" to "S1")))
    }

    /** 5.61: emoce i zvuky do angličtiny jedním dotazem. */
    @Test
    fun `preklad zvuku i emoci`() {
        val q = SbFilmPlan.otazkaPrekladu(mapOf(3 to "Nástup tanečního beatu."), mapOf(4 to "Nadšení.", 6 to "Komicky příšná."))
        assertTrue(q.endsWith("SOUND 3: Nástup tanečního beatu.\nMOOD 4: Nadšení.\nMOOD 6: Komicky příšná."))
        val odp = "SOUND 3 = Dance beat starts. MOOD 4 = Excited. MOOD 6 = Comically stern."
        assertEquals(mapOf(3 to "Dance beat starts"), SbFilmPlan.prectiPreklad(odp, "SOUND", setOf(3)))
        assertEquals(mapOf(4 to "Excited", 6 to "Comically stern"), SbFilmPlan.prectiPreklad(odp, "MOOD", setOf(4, 6)))
    }

    /** 5.62: řádek 683 px se čte zvětšený (≈2×), velký zůstane. */
    @Test
    fun `zvetseni radku`() {
        assertEquals(1400f / 683, SbFilmPlan.zvetseniRadku(683), 0.001f)
        assertEquals(1f, SbFilmPlan.zvetseniRadku(2048), 0f)
        assertEquals(3f, SbFilmPlan.zvetseniRadku(300), 0f)
    }

    /** 5.62: smazaný panel neposune výřezy ostatních — počet panelů obrázku se nemění. */
    @Test
    fun `pocet panelu obrazku po smazani`() {
        val panely = (1..6).map { SbPanel(it, "p", "wide", "static", 3.0) }
        val s = cz.promptlab.h3video.data.SbFilmScene(panely = panely.filter { it.cislo != 5 })
        assertEquals(6, SbFilmPlan.panelyObrazku(s))
        assertEquals(6, SbFilmPlan.panelyObrazku(s.copy(panelyObrazku = 6, panely = panely.filter { it.cislo != 6 })))
    }
}
