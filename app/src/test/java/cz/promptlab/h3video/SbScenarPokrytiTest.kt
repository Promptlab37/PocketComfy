package cz.promptlab.h3video

import cz.promptlab.h3video.data.SbScenar
import cz.promptlab.h3video.data.SbScenarPokryti
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** 5.27: „každá věta ze scénáře někam patří“ — na všech skutečných scénářích nic nezbude. */
class SbScenarPokrytiTest {

    @Test
    fun `skutecne scenare jsou pokryte cele`() {
        for (f in listOf("scenar_fotozije.txt", "scenar_svatba.txt", "scenar_hrnek.txt", "scenar_dar_mudrcu.txt")) {
            val t = File("src/test/resources/$f").readText()
            val c = SbScenar.rozeber(t)!!
            assertEquals(f, emptyList<String>(), SbScenarPokryti.nepokryte(t, c))
        }
    }

    @Test
    fun `ztracena veta se najde`() {
        val t = File("src/test/resources/scenar_hrnek.txt").readText()
        val c = SbScenar.rozeber(t)!!
        // Simulace chyby z 5.21: replika okna 5 se ztratila.
        val bez = c.copy(okna = c.okna.map { if (it.cislo == 5) it.copy(repliky = emptyList()) else it })
        val n = SbScenarPokryti.nepokryte(t, bez)
        assertTrue(n.toString(), n.any { it.contains("Takhle ho chci ukázat") })
        // Simulace chyby z 5.26: postavy spadly jinam.
        val bezPostav = c.copy(postavy = emptyMap())
        assertTrue(SbScenarPokryti.nepokryte(t, bezPostav).any { it.contains("Keramička s kudrnatými") })
    }
}
