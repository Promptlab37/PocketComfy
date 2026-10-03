package cz.promptlab.h3video

import cz.promptlab.h3video.comfy.SbFilmBuilder
import cz.promptlab.h3video.data.SbDialogy
import cz.promptlab.h3video.data.SbFilmPrepis
import cz.promptlab.h3video.data.SbFilmScene
import cz.promptlab.h3video.data.SbPanel
import cz.promptlab.h3video.data.VLASTNI_ZVUK
import cz.promptlab.h3video.data.VoiceSource
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** 5.64: dialogy přes Higgs ve Filmu ze storyboardu. */
class SbDialogyTest {

    private val anna = VoiceSource.Library("anna", "Anna")
    private val petr = VoiceSource.Library("pavel", "Pavel")

    private fun scena(dialogy: Boolean = true): SbFilmScene {
        val panely = listOf(
            SbPanel(1, "A.", "close-up", "static", 4.0, repliky = "Anna: „Ahoj.“; Pavel: „Čau.“"),
            SbPanel(2, "B.", "wide", "static", 3.0),
            SbPanel(3, "C.", "medium", "static", 4.0, repliky = "Anna: „Jdeme.“"),
        )
        fun n(c: Int, i: Int, text: String, hlas: String, d: Double) =
            SbDialogy.klic(c, i) to SbDialogy.zakoduj(SbDialogy.Nahravka(File("/x/$c-$i.wav"), text, hlas, d))
        return SbFilmScene(
            panely = panely, dialogyHiggs = dialogy,
            hlasyMluvcich = mapOf("Anna" to SbDialogy.zakoduj(anna), "Pavel" to SbDialogy.zakoduj(petr)),
            nahravky = mapOf(n(1, 0, "Ahoj.", anna.klic, 0.8), n(1, 1, "Čau.", petr.klic, 0.6), n(3, 0, "Jdeme.", anna.klic, 1.0)),
        )
    }

    private val vse: (File) -> Boolean = { true }

    @Test
    fun `plan jedne stopy na usek v casech panelu`() {
        val s0 = scena()
        val s = s0.copy(panely = SbDialogy.upravPanely(s0, vse))
        val stopy = SbDialogy.planuj(s, existuje = vse)
        assertEquals(1, s.useky.size)
        // 5.71: jedna stopa na úsek se všemi mluvčími.
        assertEquals(1, stopy.size)
        assertEquals(listOf("Anna", "Pavel"), stopy[0].mluvci)
        assertEquals(1, stopy[0].cislo)
        val u = stopy[0].umisteni
        assertEquals(listOf("Anna", "Pavel", "Anna"), u.map { it.kdo })
        // Úvodní záběr filmu: 0,75 s vzduchu (+ polovina zbytku do celého snímku).
        assertEquals(0.758, u[0].startS, 0.01)
        // Pavel po Anně a mezeře při střídání mluvčích 0,25 s.
        assertEquals(0.758 + 0.8 + 0.25, u[1].startS, 0.01)
        // Panel 3 začíná po zkráceném panelu 1 (2,92 s) a tichém panelu 2 (3 s), nástup 0,25 s.
        assertEquals(2.9167 + 3.0 + 0.25, u[2].startS, 0.01)
        assertEquals(0.0, stopy[0].predponaS, 0.0)
        assertTrue(SbDialogy.problemy(stopy).isEmpty())
    }

    @Test
    fun `bez dialogu nic`() {
        assertTrue(SbDialogy.planuj(scena(false), existuje = vse).isEmpty())
    }

    @Test
    fun `nahravka plati jen pro stejny text a hlas, vlastni soubor vzdy`() {
        val s = scena()
        assertTrue(SbDialogy.chybejici(s, vse).isEmpty())
        // Jiný hlas Anny → její repliky chybí.
        val jinyHlas = s.copy(hlasyMluvcich = s.hlasyMluvcich + ("Anna" to SbDialogy.zakoduj(VoiceSource.Library("eva", "Eva"))))
        assertEquals(listOf("Ahoj.", "Jdeme."), SbDialogy.chybejici(jinyHlas, vse).map { it.text })
        // Upravená replika → nahrávka neplatí.
        val jinyText = s.copy(panely = s.panely.map { if (it.cislo == 3) it.copy(repliky = "Anna: „Jdeme domů.“") else it })
        assertEquals(listOf("Jdeme domů."), SbDialogy.chybejici(jinyText, vse).map { it.text })
        // Vlastní soubor platí i bez hlasu postavy.
        val vlastni = s.copy(hlasyMluvcich = emptyMap(), nahravky = s.nahravky.mapValues { (_, v) ->
            SbDialogy.zakoduj(SbDialogy.nahravkaZ(v)!!.copy(hlas = VLASTNI_ZVUK)) })
        assertTrue(SbDialogy.chybejici(vlastni, vse).isEmpty())
        assertTrue(SbDialogy.bezHlasu(vlastni, vse).isEmpty())
        assertEquals(listOf("Anna", "Pavel"), SbDialogy.bezHlasu(s.copy(hlasyMluvcich = emptyMap(), nahravky = emptyMap()), vse))
    }

    @Test
    fun `replika delsi nez usek je problem`() {
        val s = scena().let { it.copy(nahravky = it.nahravky + (SbDialogy.klic(3, 0) to
            SbDialogy.zakoduj(SbDialogy.Nahravka(File("/x/3-0.wav"), "Jdeme.", anna.klic, 9.0)))) }
        val p = SbDialogy.problemy(SbDialogy.planuj(s, existuje = vse))
        assertEquals(1, p.size)
        assertTrue(p[0].contains("Jdeme."))
    }

    @Test
    fun `prompt dostane Audio, audio reuse, partially_copy a hlas z nahravky`() {
        val stopy = SbDialogy.planuj(scena(), existuje = vse)
        val prompt = "subject_definitions:\n<Subject 1> is Anna.\n\nsummary:\n[reference generation] Two people talk.\n\n" +
            "retention_analysis:\n<Subject 1>: fully_preserved.\n\ndetailed_description:\n" +
            "[Shot 1] Anna smiles. In a warm, low voice, she says (S1) <d>[Czech] Ahoj.</d> Pavel nods and says (S2) <d>[Czech] Čau.</d>\n" +
            "[Shot 3] At 00:07.000, Anna, in the same warm voice, says (S1) <d>[Czech] Jdeme.</d>\n\noverall_soundscape:\nN/A"
        val o = SbDialogy.doplnPrompt(prompt, stopy, mapOf("Anna" to "S1", "Pavel" to "S2"))
        assertTrue(o.contains("<Audio 1> is the recorded dialogue track of this part, with the voices of Anna (S1) and Pavel (S2); " +
            "each line is spoken by the subject named in its shot.\n\nsummary:"))
        assertFalse(o.contains("recorded voice of the speaker"))
        assertTrue(o.contains("[reference generation + audio reuse]"))
        assertTrue(o.contains("<Audio 1>: partially_copy"))
        assertFalse(o.contains("<Audio 2>"))
        assertTrue(o.contains("of <Audio 1>, <d>[Czech] Ahoj.</d>"))
        assertTrue(o.contains("of <Audio 1>, <d>[Czech] Čau.</d>"))
        assertTrue(o.contains("of <Audio 1>, <d>[Czech] Jdeme.</d>"))
        assertFalse(o.contains("warm, low voice"))
        assertFalse(o.contains("same warm voice"))
        assertEquals(3, Regex("""<d>""").findAll(o).count())
    }

    @Test
    fun `graf media N misto mostu a stopy ze standardnich uzlu`() {
        val s = scena()
        val stopy = SbDialogy.planuj(s, existuje = vse)
        val nazvy = stopy.flatMap { it.umisteni }.map { it.soubor }.distinct().associateWith { "h3app/${it.name}" }
        val wf = SbFilmBuilder.buildFilm(s, s.useky, listOf("prompt"), listOf("h3app/sb.png"), "16:9", 1L)
        val g = SbDialogy.doplnGraf(wf, stopy, nazvy, SbFilmBuilder.N_KONTEXT, SbFilmBuilder.N_MEDIA)
        val k = g.getJSONObject(SbFilmBuilder.N_KONTEXT).getJSONObject("inputs")
        assertFalse(g.has(SbFilmBuilder.N_MEDIA))
        assertFalse(k.has("media"))
        assertEquals("image", k.getString("media_type_1"))
        assertEquals("audio", k.getString("media_type_2"))
        // Jedna stopa na úsek (5.71).
        assertFalse(k.has("media_type_3"))
        val tridy = g.keys().asSequence().map { g.getJSONObject(it).getString("class_type") }.toList()
        assertEquals(3, tridy.count { it == "LoadAudio" })
        assertTrue(tridy.containsAll(listOf("EmptyAudio", "AudioConcat", "AudioMerge")))
        // Jeden úsek: navazování se nemění.
        assertEquals(wf.getJSONObject(SbFilmBuilder.N_KONTEXT).getJSONObject("inputs").getString("continuity_mode"), k.getString("continuity_mode"))
        // Bez dialogů se graf nemění.
        val bez = SbFilmBuilder.buildFilm(s, s.useky, listOf("prompt"), listOf("h3app/sb.png"), "16:9", 1L)
        assertEquals(bez.toString(), SbDialogy.doplnGraf(JSONObject(bez.toString()), emptyList(), emptyMap(),
            SbFilmBuilder.N_KONTEXT, SbFilmBuilder.N_MEDIA).toString())
    }

    @Test
    fun `oprava hole znacky a pozadi fotky, repliky beze zmeny`() {
        val v = "subject_definitions:\n<Subject 1> is the white cat, standing in profile against a gray background.\n" +
            "detailed_description:\n[Shot 1] the white cat (Subject 3) jumps and says <d>[Czech] Subject 2 je tady.</d>"
        val o = SbFilmPrepis.opravZnackyAPozadi(v)
        assertTrue(o.contains("<Subject 1> is the white cat."))
        assertTrue(o.contains("the white cat (<Subject 3>) jumps"))
        assertTrue(o.contains("<d>[Czech] Subject 2 je tady.</d>"))
    }

    @Test
    fun `hlidka s nahravkami nepopisuje hlas`() {
        val h = SbFilmPrepis.hlidkaSNahravkami("[Write exactly these shots.\n]", listOf("Anna", "Pavel"))
        assertTrue(h.contains("The spoken lines of Anna, Pavel come with recorded voices supplied separately: never describe their voice"))
        assertTrue(h.trimEnd().endsWith("]"))
        assertEquals("[x]", SbFilmPrepis.hlidkaSNahravkami("[x]", emptyList()))
    }

    /** Víc úseků s nahrávkami navazuje latent_guide (oprava balíku na serveru 1. 10. 2026). */
    @Test
    fun `vic useku s nahravkami navazuje latent_guide`() {
        val s = scena().let { it.copy(panely = it.panely.map { p -> p.copy(sekundy = 8.0) }) }
        assertTrue(s.useky.size >= 2)
        val stopy = SbDialogy.planuj(s, existuje = vse)
        val nazvy = stopy.flatMap { it.umisteni }.map { it.soubor }.distinct().associateWith { "h3app/${it.name}" }
        val wf = SbFilmBuilder.buildFilm(s, s.useky, s.useky.map { "p" }, listOf("h3app/sb.png"), "16:9", 1L)
        assertEquals("latent_guide", wf.getJSONObject(SbFilmBuilder.N_KONTEXT).getJSONObject("inputs").getString("continuity_mode"))
        val g = SbDialogy.doplnGraf(wf, stopy, nazvy, SbFilmBuilder.N_KONTEXT, SbFilmBuilder.N_MEDIA)
        assertEquals("latent_guide", g.getJSONObject(SbFilmBuilder.N_KONTEXT).getJSONObject("inputs").getString("continuity_mode"))
    }

    /** 5.66: délky panelů podle řeči — žádné dlouhé pauzy, tichý panel beze změny, konec filmu s rezervou. */
    @Test
    fun `delky panelu podle reci`() {
        val s = scena()
        val p = SbDialogy.upravPanely(s, vse)
        // Panel 1: 0,75 + 0,8 + 0,25 + 0,6 + 0,5 (před tichým záběrem) = 2,90 → 70 snímků.
        assertEquals(70 / 24.0, p[0].sekundy, 1e-9)
        // Tichý panel 2 přesně podle storyboardu.
        assertEquals(3.0, p[1].sekundy, 0.0)
        // Poslední panel filmu: 0,25 + 1,0 + 1,5 (+0,5 z rozdělení na úseky) = 2,75.
        assertEquals(2.75, p[2].sekundy, 1e-9)
        // Delší replika na konci: 0,25 + 4,24 + 1,5 = 5,99 → 144 snímků = 6,0 s.
        val dlouha = s.copy(nahravky = s.nahravky + (SbDialogy.klic(3, 0) to
            SbDialogy.zakoduj(SbDialogy.Nahravka(File("/x/3-0.wav"), "Jdeme.", anna.klic, 4.24))))
        assertEquals(6.0, SbDialogy.upravPanely(dlouha, vse)[2].sekundy, 1e-9)
    }

    @Test
    fun `panel s pauzou nebo pohledem drzi delku a cas jde pred repliku`() {
        val s0 = scena().let { it.copy(panely = it.panely.map { p -> if (p.cislo == 1) p.copy(popis = "The man stares at the camera.") else p }) }
        val p = SbDialogy.upravPanely(s0, vse)
        assertEquals(4.0, p[0].sekundy, 0.0)
        val stopy = SbDialogy.planuj(s0.copy(panely = p), existuje = vse)
        // Pohled, pak řeč: nástup až 1,0 s.
        assertEquals(1.0, stopy[0].umisteni[0].startS, 1e-9)
    }

    @Test
    fun `delky se pocitaji od storyboardu a orez posune soubor`() {
        val s = scena().copy(delkyStoryboardu = mapOf(1 to 4.0, 2 to 3.0, 3 to 4.0))
        // I když byl panel dřív prodloužený (třeba 6 s), počítá se od délky ze storyboardu.
        val prodlouzena = s.copy(panely = s.panely.map { if (it.cislo == 3) it.copy(sekundy = 6.0) else it })
        assertEquals(2.75, SbDialogy.upravPanely(prodlouzena, vse)[2].sekundy, 1e-9)
        // Ořezaná nahrávka: řeč začíná 0,03 s po začátku souboru → soubor se položí o 0,03 s dřív.
        val orez = s.copy(nahravky = s.nahravky + (SbDialogy.klic(1, 0) to
            SbDialogy.zakoduj(SbDialogy.Nahravka(File("/x/1-0r.wav"), "Ahoj.", anna.klic, 0.8, 0.03))))
        val bez = SbDialogy.planuj(s, existuje = vse)[0].umisteni[0].startS
        val sOrez = SbDialogy.planuj(orez, existuje = vse)[0].umisteni[0]
        assertEquals(bez - 0.03, sOrez.startS, 1e-9)
        assertEquals(0.83, sOrez.delkaS, 1e-9)
    }

    /**
     * 5.71 (film 33 „Sněmovna“, AUDIT_v1): tři mluvčí v jednom úseku = jedna stopa se všemi
     * replikami v jejich časech, v grafu jediné médium audio a v promptu jediné <Audio N>.
     */
    @Test
    fun `tri mluvci v jednom useku - jedna stopa, casy replik, jedno Audio`() {
        val babis = VoiceSource.Library("babis", "Babiš")
        val pavel = VoiceSource.Library("pavel", "Pavel")
        val macinka = VoiceSource.Library("macinka", "Macinka")
        val panely = listOf(
            SbPanel(1, "Babiš leans in.", "extreme close-up", "static", 2.25, repliky = "Babiš: „Přestaň scrollovat!“"),
            SbPanel(2, "Babiš shows a phone.", "medium", "static", 3.5, repliky = "Babiš: „Nebo ti pustím dalších sedmnáct minut.“"),
            SbPanel(3, "Pavel sits.", "medium", "static", 2.333, repliky = "Pavel: „Já už sedím. Vy si to vyřešte.“"),
            SbPanel(4, "Macinka points.", "medium", "static", 2.833, repliky = "Macinka: „Tohle místo bylo slíbený mně!“"),
        )
        fun n(c: Int, text: String, hlas: String, d: Double) =
            SbDialogy.klic(c, 0) to SbDialogy.zakoduj(SbDialogy.Nahravka(File("/x/r$c.wav"), text, hlas, d, 0.03))
        val s = SbFilmScene(
            panely = panely, dialogyHiggs = true,
            hlasyMluvcich = mapOf("Babiš" to SbDialogy.zakoduj(babis), "Pavel" to SbDialogy.zakoduj(pavel), "Macinka" to SbDialogy.zakoduj(macinka)),
            nahravky = mapOf(
                n(1, "Přestaň scrollovat!", babis.klic, 1.34), n(2, "Nebo ti pustím dalších sedmnáct minut.", babis.klic, 3.06),
                n(3, "Já už sedím. Vy si to vyřešte.", pavel.klic, 1.92), n(4, "Tohle místo bylo slíbený mně!", macinka.klic, 2.12),
            ),
        )
        val useky = listOf(cz.promptlab.h3video.data.SbUsek(panely))
        val stopy = SbDialogy.planuj(s, useky, existuje = vse)
        assertEquals(1, stopy.size)
        val st = stopy[0]
        assertEquals(listOf("Babiš", "Pavel", "Macinka"), st.mluvci)
        assertEquals(listOf("Babiš", "Babiš", "Pavel", "Macinka"), st.umisteni.map { it.kdo })
        // Řeč začíná po nástupu panelu (úvod filmu 0,75 s, jinak 0,25 s + polovina přebytku), soubor o 0,03 s dřív.
        val zacatky = listOf(0.0, 2.25, 5.75, 8.083)
        st.umisteni.forEachIndexed { i, u -> assertTrue("replika $i v panelu", u.startS + 0.03 >= zacatky[i] && u.startS < zacatky[i] + 1.0) }
        assertEquals(0.75 - 0.03, st.umisteni[0].startS, 0.01)
        assertEquals(2.25 + 0.25 - 0.03, st.umisteni[1].startS, 0.01)
        assertTrue(SbDialogy.problemy(stopy).isEmpty())

        val prompt = "subject_definitions:\n<Subject 1> is Babiš.\n<Subject 2> is Pavel.\n<Subject 3> is Macinka.\n\nsummary:\n[reference generation] Three men argue.\n\n" +
            "retention_analysis:\n<Subject 1>: fully_preserved.\n\ndetailed_description:\n" +
            "[Shot 1] <Subject 1> (S1) says <d>[Czech] Přestaň scrollovat!</d>\n" +
            "[Shot 2] At 00:02.250, <Subject 1> (S1) says <d>[Czech] Nebo ti pustím dalších sedmnáct minut.</d>\n" +
            "[Shot 3] At 00:05.750, <Subject 2> (S2) says <d>[Czech] Já už sedím. Vy si to vyřešte.</d>\n" +
            "[Shot 4] At 00:08.083, <Subject 3> (S3) says <d>[Czech] Tohle místo bylo slíbený mně!</d>\n\noverall_soundscape:\nN/A"
        val o = SbDialogy.doplnPrompt(prompt, stopy, mapOf("Babiš" to "S1", "Pavel" to "S2", "Macinka" to "S3"))
        assertEquals(setOf("<Audio 1>"), Regex("""<Audio \d+>""").findAll(o).map { it.value }.toSet())
        assertTrue(o.contains("<Audio 1> is the recorded dialogue track of this part, with the voices of Babiš (S1), Pavel (S2) and Macinka (S3); " +
            "each line is spoken by the subject named in its shot."))
        assertEquals(1, Regex("""partially_copy""").findAll(o).count())
        assertEquals(4, Regex("""of <Audio 1>, <d>""").findAll(o).count())
        assertTrue(o.contains("<Subject 2> (S2) says, in exactly the voice, pitch, timing and intonation of <Audio 1>, <d>[Czech] Já už sedím."))

        val nazvy = st.umisteni.associate { it.soubor to "ref_${it.soubor.name}" }
        val wf = SbFilmBuilder.buildFilm(s, useky, listOf(o), listOf("sb.png"), "16:9", 1L)
        val g = SbDialogy.doplnGraf(wf, stopy, nazvy, SbFilmBuilder.N_KONTEXT, SbFilmBuilder.N_MEDIA)
        val k = g.getJSONObject(SbFilmBuilder.N_KONTEXT).getJSONObject("inputs")
        assertEquals(listOf("image", "audio"), (1..9).mapNotNull { k.optString("media_type_$it").takeIf { t -> t.isNotEmpty() } })
        val uzly = g.keys().asSequence().map { g.getJSONObject(it) }.toList()
        assertEquals(4, uzly.count { it.getString("class_type") == "LoadAudio" })
        // Stopa = ticho délky úseku, předsazky = časy replik.
        val ticha = g.keys().asSequence().sortedBy { it.toInt() }.map { g.getJSONObject(it) }
            .filter { it.getString("class_type") == "EmptyAudio" }.map { it.getJSONObject("inputs").getDouble("duration") }.toList()
        assertEquals(useky[0].sekundy, ticha[0], 1e-9)
        assertEquals(st.umisteni.map { it.startS }, ticha.drop(1))
    }

    @Test
    fun `jeden mluvci v useku drzi tvar filmu 32`() {
        val s = scena().let { it.copy(panely = it.panely.map { p -> if (p.cislo == 1) p.copy(repliky = "Anna: „Ahoj.“") else p }) }
        val stopy = SbDialogy.planuj(s, existuje = vse)
        val o = SbDialogy.doplnPrompt("summary:\n[reference generation] x\n\nretention_analysis:\n\ndetailed_description:\n" +
            "[Shot 1] Anna says (S1) <d>[Czech] Ahoj.</d>\n", stopy, mapOf("Anna" to "S1"))
        assertTrue(o.contains("<Audio 1> is the recorded voice of the speaker Anna (S1).\n\nsummary:"))
    }

    /**
     * 5.71: od 2. úseku počítá H3 obraz i zvuk od začátku vzorku včetně skryté předpony
     * navazování (22 snímků / 24 fps) — stopa i časy záběrů se o ni posunou.
     */
    @Test
    fun `usek 2 - stopa i casy zaberu posunute o skrytou predponu`() {
        val predpona = SbFilmBuilder.KONTEXT_SNIMKU / SbFilmBuilder.FPS
        assertEquals(22 / 24.0, predpona, 1e-12)
        assertEquals(0.0, SbFilmBuilder.skrytaPredponaS(0), 0.0)
        assertEquals(predpona, SbFilmBuilder.skrytaPredponaS(1), 1e-12)
        val s = scena()
        val useky = listOf(cz.promptlab.h3video.data.SbUsek(s.panely.take(2)), cz.promptlab.h3video.data.SbUsek(s.panely.drop(2)))
        val bez = SbDialogy.planuj(s, useky, predpona = { 0.0 }, existuje = vse)
        val sPosunem = SbDialogy.planuj(s, useky, existuje = vse)
        assertEquals(listOf(0, 1), sPosunem.map { it.usek })
        assertEquals(0.0, sPosunem[0].predponaS, 0.0)
        assertEquals(bez[0].umisteni, sPosunem[0].umisteni)
        assertEquals(predpona, sPosunem[1].predponaS, 1e-12)
        assertEquals(bez[1].umisteni[0].startS + predpona, sPosunem[1].umisteni[0].startS, 1e-9)
        assertEquals(useky[1].sekundy + predpona, sPosunem[1].delkaStopyS, 1e-9)
        // První replika úseku 2 (panel 3, nástup 0,4 s) je za předponou, ne v ní.
        assertTrue(sPosunem[1].umisteni[0].startS > predpona + 0.3)

        val p2 = "summary:\n[reference generation] In [Shot 2], at 00:03.000, he leaves.\n\ndetailed_description:\n" +
            "[Shot 1] A close-up of <Subject 1> holding a phone showing 17:00. He says <d>[Czech] Je 12:30.500.</d>\n" +
            "[Shot 2] At 00:03.000, the shot cuts to a wide frame.\n\noverall_soundscape:\nA beat begins at 00:03.000."
        val wf = SbFilmBuilder.buildFilm(s, useky, listOf("[Shot 1] A wide shot.\n[Shot 2] At 00:02.000, x", p2), listOf("sb.png"), "16:9", 1L)
        val prvni = wf.getJSONObject(SbFilmBuilder.N_USEK_PRVNI.toString()).getJSONObject("inputs").getString("prompt_override")
        assertEquals("[Shot 1] A wide shot.\n[Shot 2] At 00:02.000, x", prvni)
        val druhy = wf.getJSONObject((SbFilmBuilder.N_USEK_PRVNI + 1).toString()).getJSONObject("inputs").getString("prompt_override")
        assertTrue(druhy, druhy.contains("[Shot 1] At 00:00.917, a close-up of <Subject 1> holding a phone showing 17:00."))
        assertTrue(druhy.contains("<d>[Czech] Je 12:30.500.</d>"))
        assertTrue(druhy.contains("[Shot 2] At 00:03.917, the shot cuts"))
        assertTrue(druhy.contains("In [Shot 2], at 00:03.917, he leaves."))
        assertTrue(druhy.contains("A beat begins at 00:03.917."))
        // Záběr začínající značkou: čas se vloží, text beze změny; „00:01:500“ není čas H3 a zůstane.
        assertEquals("[Shot 1] At 00:00.917, <Subject 1> sits.\n[Shot 2] At 00:01:500 x",
            SbFilmBuilder.casyVeVzorku("[Shot 1] <Subject 1> sits.\n[Shot 2] At 00:01:500 x", predpona))
        assertEquals("[Shot 1] At 00:00.917, x\n[Shot 2] At 00:02.417, y",
            SbFilmBuilder.casyVeVzorku("[Shot 1] x\n[Shot 2] At 00:01.500, y", predpona))
        // Graf úseku 2: ticho stopy = předpona + úsek.
        val g = SbDialogy.doplnGraf(JSONObject(wf.toString()), sPosunem, sPosunem.flatMap { it.umisteni }.associate { it.soubor to it.soubor.name },
            SbFilmBuilder.N_KONTEXT, SbFilmBuilder.N_MEDIA)
        val stopa2 = g.keys().asSequence().map { g.getJSONObject(it) }
            .first { it.getString("class_type") == "EmptyAudio" && it.getJSONObject("_meta").getString("title").endsWith("úsek 2") }
        assertEquals(useky[1].sekundy + predpona, stopa2.getJSONObject("inputs").getDouble("duration"), 1e-9)
    }

    /** „points his finger“ není pointa ani důvod držet délku (test na emulátoru 1. 10. 2026). */
    @Test
    fun `points neni pointa`() {
        val s0 = scena().let { it.copy(panely = it.panely.map { p -> if (p.cislo == 1) p.copy(popis = "The man points his finger at the viewer.") else p }) }
        // Jako panel bez klíčových slov: zkrácení na 2,90 s → 70 snímků.
        assertEquals(70 / 24.0, SbDialogy.upravPanely(s0, vse)[0].sekundy, 1e-9)
    }
}
