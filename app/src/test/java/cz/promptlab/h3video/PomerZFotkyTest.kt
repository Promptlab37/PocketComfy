package cz.promptlab.h3video

import cz.promptlab.h3video.data.Aspect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Poměr plátna se má vzít z vložené fotky.
 *
 * Plátno videa je vždycky jeden z pevných poměrů. Když se netrefí do fotky,
 * model ji do plátna roztáhne a postava vyjde zploštělá — přesně na to
 * uživatel narazil. Rozlišení má měnit velikost, ne tvar.
 */
class PomerZFotkyTest {

    @Test
    fun `bezne fotky z telefonu`() {
        // Samsung na výšku i na šířku (4:3 senzor).
        assertEquals(Aspect.PORTRAIT_3_4, Aspect.nejblizsi(3024, 4032))
        assertEquals(Aspect.LANDSCAPE_4_3, Aspect.nejblizsi(4032, 3024))
        // Snímek obrazovky telefonu — nejblíž je 9:16.
        assertEquals(Aspect.PORTRAIT_9_16, Aspect.nejblizsi(1080, 2340))
        // Čtverec z Instagramu.
        assertEquals(Aspect.SQUARE_1_1, Aspect.nejblizsi(1080, 1080))
    }

    @Test
    fun `filmove a fotoaparatove pomery`() {
        assertEquals(Aspect.LANDSCAPE_16_9, Aspect.nejblizsi(1920, 1080))
        assertEquals(Aspect.LANDSCAPE_3_2, Aspect.nejblizsi(6000, 4000))
        assertEquals(Aspect.PORTRAIT_2_3, Aspect.nejblizsi(4000, 6000))
        assertEquals(Aspect.ULTRAWIDE_21_9, Aspect.nejblizsi(2560, 1080))
    }

    @Test
    fun `na vysku se nesleva dohromady`() {
        // Logaritmické porovnání: „dvakrát vyšší" váží stejně jako
        // „dvakrát širší". S obyčejným rozdílem poměrů by 9:16 přebíralo
        // i fotky, které jsou mnohem blíž k 3:4.
        assertEquals(Aspect.PORTRAIT_3_4, Aspect.nejblizsi(1500, 2000))
        assertEquals(Aspect.PORTRAIT_9_16, Aspect.nejblizsi(1500, 2666))
    }

    @Test
    fun `nesmyslne rozmery nic nemeni`() {
        assertNull(Aspect.nejblizsi(0, 100))
        assertNull(Aspect.nejblizsi(100, 0))
        assertNull(Aspect.nejblizsi(-4, -3))
    }

    @Test
    fun `kazdy pomer se trefi sam do sebe`() {
        Aspect.entries.forEach { a ->
            assertEquals("$a se má trefit sám do sebe", a, Aspect.nejblizsi(a.w * 100, a.h * 100))
        }
    }
}
