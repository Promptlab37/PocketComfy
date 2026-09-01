package cz.promptlab.h3video

import cz.promptlab.h3video.data.Line
import cz.promptlab.h3video.data.Speaker
import cz.promptlab.h3video.data.TalkScene
import cz.promptlab.h3video.data.VoiceSource
import cz.promptlab.h3video.data.VoiceStatus
import cz.promptlab.h3video.data.composePrompt
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Prompt Mluvící scény proti oficiálnímu návodu („FULL REFERENCES PROMPTING
 * GUIDE" z autorova V2 workflow). Návod předepisuje šest sekcí a u každé
 * reference chce jasně řečený úkol – tohle hlídá, že tam všechny jsou.
 */
class TalkPromptTest {

    /**
     * Skutečné soubory: `voiceCurrent` se ptá i na to, jestli nahrávka existuje –
     * replika bez existujícího zvuku se do `<Audio N>` nepočítá.
     */
    private fun docasny(pripona: String) =
        File.createTempFile("test", pripona).apply { deleteOnExit() }

    private fun scena(): TalkScene {
        val ona = Speaker(
            key = 1, image = docasny(".jpg"), look = "a young woman in a red coat",
            voice = VoiceSource.Library("v1", "Ana"),
        )
        val on = Speaker(
            key = 2, image = docasny(".jpg"), look = "a man in a grey shirt",
            voice = VoiceSource.Library("v2", "Petr"),
        )
        return TalkScene(
            speakers = listOf(ona, on),
            lines = listOf(
                Line(
                    key = 1, speakerKey = 1, text = "Pojď se mnou.",
                    audio = docasny(".wav"), status = VoiceStatus.READY,
                    spokenText = "Pojď se mnou.", audioSeconds = 1.4f,
                ),
                Line(
                    key = 2, speakerKey = 2, text = "Kam jdeme?",
                    audio = docasny(".wav"), status = VoiceStatus.READY,
                    spokenText = "Kam jdeme?", audioSeconds = 1.1f,
                ),
            ),
        )
    }

    @Test fun `prompt ma vsech sest sekci z navodu`() {
        val p = composePrompt(scena())
        listOf(
            "subject_definitions:", "summary:", "retention_analysis:",
            "detailed_description:", "overall_soundscape:", "non_diegetic_music:",
        ).forEach { assertTrue("chybí sekce $it", p.contains(it)) }
    }

    @Test fun `vsechny repliky jsou v promptu, ne jen prvni`() {
        val p = composePrompt(scena())
        assertTrue(p.contains("<d>[Czech] Pojď se mnou.</d>"))
        assertTrue(p.contains("<d>[Czech] Kam jdeme?</d>"))
    }

    @Test fun `druha replika ma casovou znacku, aby nezapadla`() {
        val p = composePrompt(scena())
        // 1,4 s první replika + 0,6 s pauza = 00:02.000
        assertTrue("chybí čas druhé repliky:\n$p", p.contains("At 00:02.000,"))
    }

    @Test fun `kazda reference ma v promptu ukol`() {
        val p = composePrompt(scena())
        assertTrue(p.contains("<Audio 1> is the voice-timbre reference for <Subject 1> (S1)."))
        assertTrue(p.contains("<Subject 1>: fully_preserved"))
        assertTrue(p.contains("<Audio 1>: reference"))
    }

    @Test fun `mluvci ma stabilni ID pres vic replik`() {
        val s = scena().let {
            it.copy(lines = it.lines + Line(
                key = 3, speakerKey = 1, text = "Uvidíš.",
                audio = docasny(".wav"), status = VoiceStatus.READY,
                spokenText = "Uvidíš.", audioSeconds = 0.9f,
            ))
        }
        val p = composePrompt(s)
        // Obě repliky té samé postavy pod stejným ID.
        assertTrue(p.contains("<Subject 1> (S1)"))
        assertTrue(p.contains("<d>[Czech] Uvidíš.</d>"))
    }
}
