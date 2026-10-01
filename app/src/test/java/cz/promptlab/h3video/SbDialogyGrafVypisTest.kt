package cz.promptlab.h3video

import cz.promptlab.h3video.comfy.SbFilmBuilder
import cz.promptlab.h3video.data.SbDialogy
import cz.promptlab.h3video.data.SbFilmScene
import cz.promptlab.h3video.data.SbPanel
import cz.promptlab.h3video.data.SbRozliseni
import cz.promptlab.h3video.data.VoiceSource
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Pomocník pro ruční ověřovací běh na serveru: vypíše graf krátkého filmu s dialogem
 * (2 úseky po 3 s, replika ve 2. úseku). Běží jen s proměnnou prostředí SB_GRAF_DO=<soubor>.
 */
class SbDialogyGrafVypisTest {
    @Test
    fun vypis() {
        val cil = System.getenv("SB_GRAF_DO")
        assumeTrue(cil != null)
        val hlas = VoiceSource.Library("babi-cd0dd24e", "Babiš")
        val panely = listOf(
            SbPanel(1, "The man looks at the camera.", "close-up", "static", 3.0),
            SbPanel(2, "The man points at the viewer.", "medium", "static", 3.0, repliky = "Příšera: „Běž spát.“"),
        )
        val wav = File("ref_70fce36c35700cc0.wav").absoluteFile
        val s = SbFilmScene(
            panely = panely, dialogyHiggs = true, rozliseni = SbRozliseni.values().first(),
            hlasyMluvcich = mapOf("Příšera" to SbDialogy.zakoduj(hlas)),
            nahravky = mapOf(SbDialogy.klic(2, 0) to SbDialogy.zakoduj(SbDialogy.Nahravka(wav, "Běž spát.", hlas.klic, 1.56))),
        )
        val useky = listOf(cz.promptlab.h3video.data.SbUsek(listOf(panely[0])), cz.promptlab.h3video.data.SbUsek(listOf(panely[1])))
        val stopy = SbDialogy.planuj(s, useky) { true }
        val p1 = "subject_definitions:\n<Subject 1> is the man in <Picture 1>.\n\nsummary:\n[reference generation] A man looks at the camera.\n\n" +
            "retention_analysis:\n<Subject 1>: fully_preserved.\n\ndetailed_description:\n[Shot 1] The man looks at the camera.\n\n" +
            "overall_soundscape:\nN/A\n\nnon_diegetic_music:\nN/A"
        val p2 = SbDialogy.doplnPrompt(
            "subject_definitions:\n<Subject 1> is the man in <Picture 1>.\n\nsummary:\n[reference generation] The man speaks.\n\n" +
                "retention_analysis:\n<Subject 1>: fully_preserved.\n\ndetailed_description:\n[Shot 1] The man points and says (S1) <d>[Czech] Běž spát.</d>\n\n" +
                "overall_soundscape:\nN/A\n\nnon_diegetic_music:\nN/A",
            stopy.filter { it.usek == 1 }, mapOf("Příšera" to "S1"),
        )
        val wf = SbFilmBuilder.buildFilm(s, useky, listOf(p1, p2), listOf("ref_d55a7b92b8f86554.jpg"), "9:16", 12345L)
        val g = SbDialogy.doplnGraf(wf, stopy, mapOf(wav to wav.name), SbFilmBuilder.N_KONTEXT, SbFilmBuilder.N_MEDIA)
        File(cil).writeText(g.toString(1))
    }
}
