package cz.promptlab.h3video

import cz.promptlab.h3video.data.VideoItem
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Zkratka „Zvětšit video" pod hotovým výsledkem.
 *
 * Zvětšení dělá karta Vylepšit video → Zvětšit (SeedVR2 i RTX Video SR). Tenhle test hlídá jen to, komu se
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
     * vstupu. Od 4.62 vede do karty Vylepšit video (režim Zvětšit).
     */
    @Test
    fun `zkratka kopiruje soubor, neodkazuje na historii`() {
        val zdroj = File("src/main/java/cz/promptlab/h3video/MainViewModel.kt").readText()
        fun telo(fn: String): String {
            val i = zdroj.indexOf("fun $fn")
            assertTrue("$fn ve zdrojáku není", i >= 0)
            return zdroj.substring(i, minOf(i + 900, zdroj.length))
        }
        assertTrue("nepřepíná do režimu Zvětšit", telo("posliVideoDoZvetseni").contains("VylepseniRezim.ZVETSIT"))
        val vylepseni = telo("posliVideoDoVylepseni")
        assertTrue("chybí kopie souboru", vylepseni.contains("kopieVysledku"))
        assertTrue("nepřepíná na kartu Vylepšit video", vylepseni.contains("Mode.VYLEPSENI_VIDEA"))
        assertTrue("kopie nepoužívá copyTo", telo("kopieVysledku").contains("copyTo"))
    }
}
