package cz.promptlab.h3video

import cz.promptlab.h3video.data.AioMode
import cz.promptlab.h3video.data.AioScene
import cz.promptlab.h3video.data.AioSlot
import cz.promptlab.h3video.data.vstupUrcujePomer
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Které vstupy určují tvar plátna — černé na bílém pro každý režim karty
 * All in One.
 *
 * Vstup, který se do videa vkládá doslova (první snímek, klíčový snímek,
 * navazované video), musí plátnu diktovat tvar. Jinak ho model roztáhne —
 * přesně na to uživatel narazil u rozhýbané fotky.
 */
class PomerZeVstupuTest {

    private val foto = File("foto.jpg")

    private fun scena(mode: AioMode) = AioScene(mode = mode)

    // ------------------------------------------------------- Z obrázku (i2v)

    @Test
    fun `prvni snimek urcuje plátno`() {
        assertTrue(vstupUrcujePomer("first", scena(AioMode.IMAGE)))
    }

    @Test
    fun `posledni snimek se ridi prvnim, plátno neurcuje`() {
        assertFalse(vstupUrcujePomer("last", scena(AioMode.IMAGE)))
    }

    // ------------------------------------------------------ Reference (r2v)

    @Test
    fun `jedina referencni fotka plátno urcuje`() {
        // Fotka je ve scéně dřív, než se ptáme — ukládá se před tímhle krokem.
        val s = scena(AioMode.REFERENCE).copy(refs = listOf(AioSlot(key = 1, image = foto)))
        assertTrue(vstupUrcujePomer("ref", s))
    }

    @Test
    fun `pri vice referencich se na plátno nesaha`() {
        // Které z nich by mělo patřit? Hádat se nemá.
        val s = scena(AioMode.REFERENCE).copy(
            refs = listOf(
                AioSlot(key = 1, image = foto),
                AioSlot(key = 2, image = foto),
            )
        )
        assertFalse(vstupUrcujePomer("ref", s))
    }

    @Test
    fun `referencni video plátno urcuje`() {
        assertTrue(vstupUrcujePomer("refvideo", scena(AioMode.REFERENCE)))
    }

    @Test
    fun `fotka neprebiji uz vlozene referencni video`() {
        val s = scena(AioMode.REFERENCE).copy(
            refs = listOf(AioSlot(key = 1, image = foto)),
            refVideo = File("video.mp4"),
        )
        assertFalse(vstupUrcujePomer("ref", s))
    }

    // ---------------------------------------------- klíčové snímky a navázání

    @Test
    fun `klicovy snimek plátno urcuje`() {
        assertTrue(vstupUrcujePomer("key", scena(AioMode.KEYFRAMES)))
    }

    @Test
    fun `navazovane video plátno urcuje, jinde zdrojove video ne`() {
        assertTrue(vstupUrcujePomer("source", scena(AioMode.EXTEND)))
        // U zvětšení si plátno určuje šablona sama.
        assertFalse(vstupUrcujePomer("source", scena(AioMode.UPSCALE)))
    }

    // ------------------------------------- kde si plátno určuje šablona sama

    @Test
    fun `tam kde appka plátno nedosazuje se nesaha na nic`() {
        listOf(AioMode.UPSCALE, AioMode.MASK, AioMode.CHARSHEET).forEach { mode ->
            listOf("first", "key", "ref", "refvideo", "source").forEach { druh ->
                assertFalse("$mode/$druh", vstupUrcujePomer(druh, scena(mode)))
            }
        }
    }

    @Test
    fun `neznamy druh vstupu nic nemeni`() {
        assertFalse(vstupUrcujePomer("neco", scena(AioMode.IMAGE)))
    }
}
