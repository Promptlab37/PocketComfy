package cz.promptlab.h3video

import cz.promptlab.h3video.data.SbScenar
import cz.promptlab.h3video.data.SbScenarModel
import org.junit.Assert.assertSame
import org.junit.Test
import java.io.File

/** Film s chybějícími replikami se nesmí dát natočit (Otevřené dveře 30. 9. 2026). */
class SbVyberCteniTest {

    private fun cti(f: String) = File("src/test/resources/$f").readText().replace("\r\n", "\n")

    @Test
    fun `model s vic replikami vyhrava`() {
        val text = cti("scenar_otevrene_dvere.txt")
        val j = SbScenarModel.jednotky(text)
        val model = SbScenarModel.cteni(j, SbScenarModel.stitky(cti("model_dvere.txt"), j)!!)
        // Jako ve 5.32: rozbor bez replik.
        val rozbor = SbScenar.rozeber(text)!!.let { c -> c.copy(okna = c.okna.map { it.copy(repliky = mutableListOf()) }) }
        assertSame(model, SbScenarModel.vyber(rozbor, model))
        // Stejně replik → rozbor (zamčený Dar mudrců se nemění).
        val plny = SbScenar.rozeber(text)!!
        assertSame(plny, SbScenarModel.vyber(plny, model))
        assertSame(model, SbScenarModel.vyber(null, model))
        assertSame(plny, SbScenarModel.vyber(plny, null))
    }
}
