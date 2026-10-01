package cz.promptlab.h3video

import cz.promptlab.h3video.data.SbCteniTok
import cz.promptlab.h3video.data.SbFilmPrepis
import cz.promptlab.h3video.data.SbPanelyObrazu
import cz.promptlab.h3video.data.SbShoda
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 5.67: hlasování mezi čteními (uzel čte greedy — různé výřezy chybují jinde). */
class SbShodaTest {

    @Test fun `vetsina vyhrava, uvozovky a apostrof nevadi`() {
        assertEquals("PAVEL: \"Vy si to vyřešte.\"",
            SbShoda.vyber(listOf("PAVEL: \"Vy si to vyřešte.\"", "PAVEL: \"Vy si to vyřešíte.\"", "Pavel: „Vy si to vyřešte.“")))
        assertEquals("Tak. Ted' je to jiný místo.", SbShoda.vyber(listOf("Tak. Ted' je to jiný místo.", "Tak. Teď je to jiný místo.")))
        assertNull(SbShoda.vyber(listOf("Děsivě příšný", "Děsivě přísný")))
        assertEquals("x", SbShoda.vyber(listOf("x")))
        // Prázdné = „tady replika není“ je taky hlas.
        assertEquals("", SbShoda.vyber(listOf("", "", "BABIŠ: \"Navíc.\"")))
    }

    private class FalesnyObraz : SbCteniTok.Obraz {
        override val sirka = 400
        override val vyska = 1200
        override fun original() = byteArrayOf(0)
        override fun vyrezPng(x0: Int, y0: Int, x1: Int, y1: Int, meritko: Float) = "$y0-$meritko".toByteArray()
        override fun bunky(): List<SbPanelyObrazu.Bunka>? = null
    }

    @Test fun `cteni hlasuje mezi dvema meritky a celym ctenim`() {
        val kroky = mutableListOf<Int>()
        val nahrane = HashMap<String, String>()
        val model = object : SbCteniTok.Model {
            override suspend fun nahraj(png: ByteArray, nazev: String): String = nazev.also { nahrane[it] = String(png) }
            override suspend fun precti(jmeno: String, otazka: String, krok: Int): String {
                kroky += krok
                return when {
                    jmeno == "sbfilm_storyboard.png" && otazka.startsWith("This image is a film storyboard. Answer") ->
                        "TITLE: T | TOTAL: 8 | SHOTS: 2 | GRID: 2x1 | VOICES: Pavel = a man | LOOKS: Pavel = grey suit | MUSIC: none | CONTINUITY: none " +
                            "PANEL 1 | 0-4s | medium | static | Pavel sits. | Pavel: \"Vy si to vyřešte.\" " +
                            "PANEL 2 | 4-8s | medium | static | Pavel stands. | none"
                    otazka.startsWith("This image is a film storyboard. Translate") -> "MOOD 1 = Calm"
                    jmeno == "sbfilm_radek1.png" -> "PANEL 1 Obraz: Pavel sedí. Emoce: Klid. PAVEL: „Vy si to vyřešíte.“"
                    jmeno == "sbfilm_radek1b.png" -> "PANEL 1 Obraz: Pavel sedí. Emoce: Klid. PAVEL: „Vy si to vyřešte.“"
                    jmeno.startsWith("sbfilm_radek2") -> "PANEL 2 Obraz: Pavel stojí. Emoce: Klid."
                    else -> error("nečekané čtení $jmeno: ${otazka.take(60)}")
                }
            }
        }
        var navic = 10
        val v = runBlocking {
            SbCteniTok.precti(FalesnyObraz(), model, object : SbCteniTok.Prubeh { override fun krokNavic() = navic++ }, SbCteniTok.Nastaveni(dvaPohledy = true))
        }
        assertEquals("Vy si to vyřešte.", SbFilmPrepis.repliky(v.repliky.getValue(1)).single().second)
        assertEquals("", v.repliky[2])
        assertTrue(v.nejiste.isEmpty())
        // Druhé měřítko je menší (0,8× cílové šířky panelu).
        val a = nahrane.getValue("sbfilm_radek1.png").substringAfter('-').toFloat()
        val b = nahrane.getValue("sbfilm_radek1b.png").substringAfter('-').toFloat()
        assertTrue("$a $b", b < a)
        assertEquals("Klid.", v.naladyOpis[1])
    }
}
