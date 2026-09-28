package cz.promptlab.h3video

import cz.promptlab.h3video.data.Mode
import cz.promptlab.h3video.data.MusicMotor
import cz.promptlab.h3video.data.PostavaMotor
import cz.promptlab.h3video.data.UpravaRezim
import cz.promptlab.h3video.data.VolbyKaret
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Nastavení → Karty v aplikaci, druhá úroveň: volby uvnitř karet. */
class VolbyKaretTest {

    @Test fun `kazda sablona u volby existuje`() {
        VolbyKaret.VSECHNY.forEach { v ->
            v.sablony.forEach { s ->
                assertTrue("${v.klic}: chybí res/raw/$s.json", File("src/main/res/raw/$s.json").exists())
            }
        }
    }

    @Test fun `klice jsou jedinecne`() {
        val klice = VolbyKaret.VSECHNY.map { it.klic }
        assertEquals(klice.size, klice.toSet().size)
    }

    @Test fun `skryta volba zmizi z nabidky`() {
        val skryte = setOf(VolbyKaret.klic(Mode.MUSIC, MusicMotor.YUE2.name))
        val nabidka = VolbyKaret.viditelne(Mode.MUSIC, MusicMotor.entries.toList(), skryte) { it.name }
        assertEquals(listOf(MusicMotor.ACE, MusicMotor.MM3), nabidka)
    }

    @Test fun `karta bez viditelnych voleb je skryta cela`() {
        val vsechnyHudby = MusicMotor.entries.map { VolbyKaret.klic(Mode.MUSIC, it.name) }.toSet()
        assertTrue(Mode.MUSIC in VolbyKaret.ucinneSkryte(emptySet(), vsechnyHudby))
        assertFalse(Mode.MUSIC in VolbyKaret.ucinneSkryte(emptySet(), vsechnyHudby - vsechnyHudby.first()))
    }

    @Test fun `vymenit postavu zustane, dokud ma aspon jeden motor`() {
        val bezScail = setOf(VolbyKaret.klic(Mode.UPRAVA_VIDEA, VolbyKaret.UPRAVA_POSTAVA_SCAIL))
        assertTrue(UpravaRezim.POSTAVA in VolbyKaret.upravaRezimy(bezScail))
        assertEquals(listOf(PostavaMotor.H3), VolbyKaret.postavaMotory(bezScail))
        val bezObou = bezScail + VolbyKaret.klic(Mode.UPRAVA_VIDEA, VolbyKaret.UPRAVA_POSTAVA_H3)
        assertFalse(UpravaRezim.POSTAVA in VolbyKaret.upravaRezimy(bezObou))
    }

    @Test fun `kazdy rezim upravy videa ma volbu`() {
        // Nový režim musí přibýt i do VolbyKaret, jinak ho v Nastavení nejde skrýt.
        val klice = VolbyKaret.proKartu(Mode.UPRAVA_VIDEA).map { it.jmeno }.toSet()
        UpravaRezim.entries.filter { it != UpravaRezim.POSTAVA }.forEach {
            assertTrue("chybí volba pro ${it.name}", it.name in klice)
        }
        assertEquals(MusicMotor.entries.size, VolbyKaret.proKartu(Mode.MUSIC).size)
    }
}
