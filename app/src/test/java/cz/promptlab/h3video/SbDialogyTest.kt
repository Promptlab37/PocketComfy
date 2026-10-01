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
    fun `plan stop po mluvcich a casech panelu`() {
        val s = scena()
        val stopy = SbDialogy.planuj(s, existuje = vse)
        assertEquals(1, s.useky.size)
        assertEquals(listOf("Anna" to 1, "Pavel" to 2), stopy.map { it.mluvci to it.cislo })
        val anna = stopy[0].umisteni
        assertEquals(0.15, anna[0].startS, 1e-9)
        // Panel 3 začíná po 4 + 3 s.
        assertEquals(7.15, anna[1].startS, 1e-9)
        // Pavel až po Annině replice a mezeře: 0,15 + 0,8 + 0,3.
        assertEquals(1.25, stopy[1].umisteni[0].startS, 1e-9)
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
        assertTrue(o.contains("<Audio 1> is the recorded voice of the speaker Anna (S1).\n<Audio 2> is the recorded voice of the speaker Pavel (S2).\n\nsummary:"))
        assertTrue(o.contains("[reference generation + audio reuse]"))
        assertTrue(o.contains("<Audio 1>: partially_copy"))
        assertTrue(o.contains("of <Audio 1>, <d>[Czech] Ahoj.</d>"))
        assertTrue(o.contains("of <Audio 2>, <d>[Czech] Čau.</d>"))
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
        assertEquals("audio", k.getString("media_type_3"))
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

    /** Víc úseků s nahrávkami → navazování „guide“ (latent_guide se zvukovou referencí padá). */
    @Test
    fun `vic useku s nahravkami navazuje guide`() {
        val s = scena().let { it.copy(panely = it.panely.map { p -> p.copy(sekundy = 8.0) }) }
        assertTrue(s.useky.size >= 2)
        val stopy = SbDialogy.planuj(s, existuje = vse)
        val nazvy = stopy.flatMap { it.umisteni }.map { it.soubor }.distinct().associateWith { "h3app/${it.name}" }
        val wf = SbFilmBuilder.buildFilm(s, s.useky, s.useky.map { "p" }, listOf("h3app/sb.png"), "16:9", 1L)
        assertEquals("latent_guide", wf.getJSONObject(SbFilmBuilder.N_KONTEXT).getJSONObject("inputs").getString("continuity_mode"))
        val g = SbDialogy.doplnGraf(wf, stopy, nazvy, SbFilmBuilder.N_KONTEXT, SbFilmBuilder.N_MEDIA)
        assertEquals("guide", g.getJSONObject(SbFilmBuilder.N_KONTEXT).getJSONObject("inputs").getString("continuity_mode"))
    }
}
