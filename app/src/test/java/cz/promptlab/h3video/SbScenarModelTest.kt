package cz.promptlab.h3video

import cz.promptlab.h3video.data.SbScenar
import cz.promptlab.h3video.data.SbScenarModel
import cz.promptlab.h3video.data.SbScenarPokryti
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Druhé čtení scénáře modelem (5.27). Odpovědi modelu jsou skutečné —
 * nahrané ze serveru (Qwen3-VL 8B, teplota 0) do test/resources/model_*.txt.
 */
class SbScenarModelTest {

    private val scenare = listOf("fotozije", "svatba", "hrnek", "dar_mudrcu")

    /** Zadání pro model — z nich skript na PC nahraje odpovědi. */
    @Test
    fun `zadani pro model`() {
        val dir = File("build/sbscenar/model").also { it.mkdirs() }
        scenare.forEach { n ->
            val j = SbScenarModel.jednotky(File("src/test/resources/scenar_$n.txt").readText())
            File(dir, "zadani_$n.txt").writeText(SbScenarModel.zadani(j))
        }
        File(dir, "system.txt").writeText(SbScenarModel.SYSTEM)
    }

    @Test
    fun `odpovedi modelu - shoda s rozborem`() {
        val zprava = StringBuilder()
        scenare.forEach { n ->
            val f = File("src/test/resources/model_$n.txt")
            if (!f.exists()) return@forEach
            val text = File("src/test/resources/scenar_$n.txt").readText()
            val j = SbScenarModel.jednotky(text)
            val st = SbScenarModel.stitky(f.readText(), j)
            assertNotNull(n, st)
            val b = SbScenarModel.cteni(j, st!!)
            val a = SbScenar.rozeber(text)!!
            val rozdily = SbScenarModel.rozdily(a, b)
            val nepokryte = SbScenarPokryti.nepokryte(text, b)
            zprava.append("== $n\nrozdily: $rozdily\nnepokryte model: $nepokryte\n")
            assertEquals("$n rozdíly", emptyList<String>(), rozdily)
            assertEquals("$n nepokryté", emptyList<String>(), nepokryte)
            b.okna.forEach { zprava.append("  ${it.cislo}: ${it.repliky} | texty ${it.texty} | vyzva ${it.vyzva}\n") }
        }
        File("build/sbscenar/model/vysledek.txt").writeText(zprava.toString())
    }
}
