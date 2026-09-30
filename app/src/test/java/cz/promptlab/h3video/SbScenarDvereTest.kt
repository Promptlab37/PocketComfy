package cz.promptlab.h3video

import cz.promptlab.h3video.data.SbScenar
import cz.promptlab.h3video.data.SbScenarModel
import cz.promptlab.h3video.data.SbScenarPokryti
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Scénář „Otevřené dveře“ ve tvaru H3: <d>…</d> a mluvčí S1–S4 (30. 9. 2026). */
class SbScenarDvereTest {

    private val text = File("src/test/resources/scenar_otevrene_dvere.txt").readText().replace("\r\n", "\n")

    @Test
    fun `repliky v d a mluvci S1 az S4`() {
        val s = SbScenar.rozeber(text)!!
        assertEquals(8, s.okna.size)
        assertEquals(listOf("Návštěvník", "Neteř", "Teta", "Manžel tety"), s.postavy.keys.toList().take(4))
        val r = s.okna.flatMap { o -> o.repliky.map { Triple(o.cislo, it.kdo, it.text) } }
        assertEquals(15, r.size)
        assertEquals(Triple(1, "Neteř", "Teta za chvíli přijde. Zatím vás zabavím já."), r[0])
        assertEquals(Triple(6, "Manžel tety", "Jsem doma!"), r.first { it.first == 6 })
        assertEquals(Triple(8, "Neteř", "Asi ten pes. Říkal, že se jich strašně bojí."), r.last())
        assertTrue(r.none { it.third.contains("<d>") || it.third.contains("</d>") })
        assertEquals(emptyList<String>(), SbScenarPokryti.nepokryte(text, s))
    }

    @Test
    fun `model vidi stejne repliky`() {
        val j = SbScenarModel.jednotky(text).map { it.text }
        assertTrue(j.any { it == "Neteř, zdvořile: „Teta za chvíli přijde. Zatím vás zabavím já.“" })
        assertTrue(j.none { it.contains("<d>") })
        // Skutečná odpověď lokálního modelu (server 30. 9. 2026): s rozborem se shodne, nic se nehlásí.
        val jednotky = SbScenarModel.jednotky(text)
        val odpoved = File("src/test/resources/model_dvere.txt").readText().replace("\r\n", "\n")
        val model = SbScenarModel.cteni(jednotky, SbScenarModel.stitky(odpoved, jednotky)!!)
        assertEquals(emptyList<String>(), SbScenarModel.rozdily(SbScenar.rozeber(text)!!, model))
    }
}
