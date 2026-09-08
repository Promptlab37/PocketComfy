package cz.promptlab.h3video

import cz.promptlab.h3video.data.VideoItem
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Zkratka „Zvětšit video" pod hotovým výsledkem.
 *
 * Zvětšení samo o sobě nic nového neumí — dělá ho karta All in One → Zvětšit
 * (SeedVR2 i RTX Video SR) od začátku. Tenhle test hlídá jen to, komu se
 * tlačítko nabídne: musí to být video, ne obrázek, skladba ani 3D model,
 * protože ty by režim Zvětšit vůbec nenačetl (`LoadVideo`).
 */
class ZvetsitVideoTest {

    private fun polozka(jmeno: String) =
        VideoItem("x", jmeno, "", 0L, 0f, "1024x1024", 42, false)

    @Test
    fun `video se pozna podle pripony`() {
        listOf("h3_00001_.mp4", "sestrih.MP4", "klip.webm", "stary.mkv").forEach {
            assertTrue(it, polozka(it).isVideoFile)
        }
    }

    @Test
    fun `obrazek, skladba ani model se za video nevydavaji`() {
        listOf("foto.png", "foto.JPG", "foto.webp").forEach {
            assertFalse(it, polozka(it).isVideoFile)
        }
        listOf("pisen.mp3", "zvuk.wav", "zvuk.flac").forEach {
            assertFalse(it, polozka(it).isVideoFile)
        }
        listOf("model.glb", "model.gltf").forEach {
            assertFalse(it, polozka(it).isVideoFile)
        }
    }

    @Test
    fun `druhy vysledku se nepřekryvaji`() {
        listOf(
            "a.mp4", "a.png", "a.mp3", "a.glb",
        ).forEach { jmeno ->
            val i = polozka(jmeno)
            val kolik = listOf(i.isVideoFile, i.isImage, i.isAudio, i.isModel3d).count { it }
            assertTrue("$jmeno spadá do $kolik druhů místo jednoho", kolik == 1)
        }
    }

    /**
     * Zkratka kopíruje soubor do složky karty. Kdyby brala rovnou soubor
     * z historie a uživatel položku smazal, zvětšení by spadlo na chybějícím
     * vstupu — proto se v `posliVideoDoZvetseni` kopíruje.
     */
    @Test
    fun `zkratka kopiruje soubor, neodkazuje na historii`() {
        val zdroj = File("src/main/java/cz/promptlab/h3video/MainViewModel.kt").readText()
        val i = zdroj.indexOf("fun posliVideoDoZvetseni")
        assertTrue("zkratka ve zdrojáku není", i >= 0)
        val telo = zdroj.substring(i, minOf(i + 1400, zdroj.length))
        assertTrue("chybí kopie souboru", telo.contains("copyTo"))
        assertTrue("nepřepíná do režimu Zvětšit", telo.contains("AioMode.UPSCALE"))
        assertTrue("nepřepíná na kartu All in One", telo.contains("Mode.ALLINONE"))
    }
}
