package cz.promptlab.h3video

import cz.promptlab.h3video.comfy.ZImageBuilder
import cz.promptlab.h3video.data.Aspect
import cz.promptlab.h3video.data.AioMode
import cz.promptlab.h3video.data.AioScene
import cz.promptlab.h3video.data.vstupUrcujePomer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * „Rozhýbat" pod hotovým obrázkem přenese fotku na kartu All in One.
 *
 * Do 3.75 u toho **nepřevzalo poměr stran** — na kartě zůstalo plátno
 * z minulé úlohy a H3 obrázek na výšku roztáhl. Tohle hlídá obě půlky
 * smlouvy: že se u první fotky poměr přebírat má, a že se z rozměrů
 * vygenerovaného obrázku trefí ten správný.
 */
class RozhybatPomerTest {

    @Test
    fun `prvni fotka na karte Z obrazku urcuje platno`() {
        val scena = AioScene(mode = AioMode.IMAGE)
        assertTrue(vstupUrcujePomer("first", scena))
    }

    @Test
    fun `rozmery z karty Obrazek se trefi do spravneho pomeru`() {
        // 1 Mpx tabulka karty…
        Aspect.entries.forEach { a ->
            val (w, h) = ZImageBuilder.sizeFor(a)
            assertEquals("1 Mpx $a", a, Aspect.nejblizsi(w, h))
        }
        // …i 2K tabulka Qwenu 2.1. 21:9 tam autoři neuvádějí, dopočítává se,
        // tak se u něj jen ověří, že nespadne na jiný poměr.
        Aspect.entries.forEach { a ->
            val (w, h) = ZImageBuilder.size2kFor(a)
            assertEquals("2K $a", a, Aspect.nejblizsi(w, h))
        }
    }

    @Test
    fun `fotka na vysku neskonci na platne na sirku`() {
        val (w, h) = ZImageBuilder.size2kFor(Aspect.PORTRAIT_2_3)
        val novy = Aspect.nejblizsi(w, h)!!
        assertTrue("$novy nesmi byt na sirku", novy.w < novy.h)
    }
}
