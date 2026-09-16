package cz.promptlab.h3video

import cz.promptlab.h3video.util.Soubory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Název souboru z odpovědi serveru se nesmí stát cestou. Audit 16. 9. 2026
 * (M-2): přípona brána jako „vše za poslední tečkou" pustila `..` a lomítka.
 */
class BezpecnaPriponaTest {

    @Test
    fun `bezna pripona projde a je mala`() {
        assertEquals("mp4", Soubory.bezpecnaPripona("MiMx_00012.MP4", "webm"))
        assertEquals("png", Soubory.bezpecnaPripona("list.postavy.png", "jpg"))
        assertEquals("glb", Soubory.bezpecnaPripona("model.glb", "mp4"))
    }

    @Test
    fun `bez pripony nebo s divnou priponou se pouzije vychozi`() {
        assertEquals("mp4", Soubory.bezpecnaPripona("video", "mp4"))
        assertEquals("mp3", Soubory.bezpecnaPripona("skladba.", "mp3"))
        assertEquals("jpg", Soubory.bezpecnaPripona("obr.png?x=1", "jpg"))
        assertEquals("mp4", Soubory.bezpecnaPripona("a.dlouhapripona", "mp4"))
    }

    @Test
    fun `cesta v nazvu ze serveru neprojde`() {
        assertEquals("mp4", Soubory.bezpecnaPripona("x.mp4/../../evil", "mp4"))
        assertEquals("png", Soubory.bezpecnaPripona("x.png\\..\\..\\evil", "png"))
        assertEquals("mp4", Soubory.bezpecnaPripona("../../shared_prefs/h3secrets", "mp4"))
    }

    @Test
    fun `uvnitr pozna utek ze slozky`() {
        val slozka = File(System.getProperty("java.io.tmpdir"), "h3_test_videos")
        assertTrue(Soubory.uvnitr(File(slozka, "abc.mp4"), slozka))
        assertFalse(Soubory.uvnitr(File(slozka, "x.mp4/../../evil"), slozka))
        assertFalse(Soubory.uvnitr(File(slozka.parentFile, "evil"), slozka))
        assertFalse(Soubory.uvnitr(slozka, slozka))
    }
}
