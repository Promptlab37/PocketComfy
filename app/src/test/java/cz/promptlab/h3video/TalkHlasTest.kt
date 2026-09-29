package cz.promptlab.h3video

import cz.promptlab.h3video.data.Line
import cz.promptlab.h3video.data.Speaker
import cz.promptlab.h3video.data.TalkScene
import cz.promptlab.h3video.data.VLASTNI_ZVUK
import cz.promptlab.h3video.data.VoiceSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Hotový zvuk repliky platí jen pro hlas, kterým vznikl (4.99). Do té doby
 * po změně hlasu postavy zůstávala replika „hotová“ a do videa šel starý hlas.
 */
class TalkHlasTest {

    private val wav = File.createTempFile("line", ".wav").apply { writeText("x"); deleteOnExit() }
    private val ana = VoiceSource.Library("ana", "Ana")
    private val eva = VoiceSource.Library("eva", "Eva")

    private fun scena(hlas: VoiceSource?, spokenVoice: String, mluvci: Int = 1) = TalkScene(
        speakers = listOf(Speaker(key = 1, voice = hlas), Speaker(key = 2, voice = eva)),
        lines = listOf(
            Line(key = 1, speakerKey = mluvci, text = "Ahoj", audio = wav, spokenText = "Ahoj",
                spokenVoice = spokenVoice),
        ),
    )

    @Test fun `zmena a vraceni hlasu`() {
        assertTrue(scena(ana, ana.klic).let { it.hlasPlati(it.lines[0]) })
        // Jiný hlas: nahrávka přestane platit, ale nemaže se.
        assertFalse(scena(eva.copy(voiceId = "marek"), ana.klic).let { it.hlasPlati(it.lines[0]) })
        // Odebraný hlas: neplatí.
        assertFalse(scena(null, ana.klic).let { it.hlasPlati(it.lines[0]) })
        // Vrácení původního hlasu: zase platí.
        assertTrue(scena(ana, ana.klic).let { it.hlasPlati(it.lines[0]) })
    }

    @Test fun `prepnuti mluvciho zneplatni cizi hlas`() {
        val s = scena(ana, ana.klic, mluvci = 2)
        assertFalse(s.hlasPlati(s.lines[0]))
        assertTrue(s.voiced.isEmpty())
    }

    @Test fun `vlastni zvuk a starsi nahravky plati vzdy`() {
        assertTrue(scena(null, VLASTNI_ZVUK).let { it.hlasPlati(it.lines[0]) })
        assertTrue(scena(eva, "").let { it.hlasPlati(it.lines[0]) })
    }

    @Test fun `klic vzorku je podle souboru`() {
        val a = VoiceSource.Sample(File("sample_1_1.m4a"), "moje")
        val b = VoiceSource.Sample(File("sample_1_2.m4a"), "moje")
        assertEquals("sample:sample_1_1.m4a", a.klic)
        assertFalse(a.klic == b.klic)
    }
}
