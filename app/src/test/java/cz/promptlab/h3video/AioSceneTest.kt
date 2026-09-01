package cz.promptlab.h3video

import cz.promptlab.h3video.data.AioMode
import cz.promptlab.h3video.data.AioScene
import cz.promptlab.h3video.data.AioSlot
import cz.promptlab.h3video.data.GenParams
import cz.promptlab.h3video.data.Mode
import cz.promptlab.h3video.data.Upscaler
import cz.promptlab.h3video.data.aioHints
import cz.promptlab.h3video.data.aioProblem
import cz.promptlab.h3video.data.planExtend
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Pravidla karty All in One: co smí odejít na server a co má appka zachytit
 * dřív, než uživatel čeká deset minut na chybu z ComfyUI.
 */
class AioSceneTest {

    private val obrazek = File("foto.jpg")
    private val video = File("video.mp4")

    @Test
    fun `bez popisu se negeneruje, se zvetsenim ano`() {
        assertNotNull(aioProblem(AioScene(mode = AioMode.TEXT, prompt = "")))
        assertNull(aioProblem(AioScene(mode = AioMode.TEXT, prompt = "muz jde po plazi")))
        // Zvětšování nic negeneruje, takže popis nepotřebuje – jen video.
        assertNotNull(aioProblem(AioScene(mode = AioMode.UPSCALE)))
        assertNull(aioProblem(AioScene(mode = AioMode.UPSCALE, sourceVideo = video)))
    }

    @Test
    fun `z obrazku staci jeden snimek, ale zapnuty posledni musi byt vyplneny`() {
        val jenPrvni = AioScene(
            mode = AioMode.IMAGE, prompt = "x",
            first = AioSlot(key = 1, image = obrazek),
        )
        assertNull(aioProblem(jenPrvni))
        assertNotNull(aioProblem(jenPrvni.copy(useLastFrame = true)))
        assertNull(
            aioProblem(
                jenPrvni.copy(useLastFrame = true, last = AioSlot(key = 2, image = obrazek))
            )
        )
    }

    @Test
    fun `reference bere obrazek nebo video`() {
        val prazdna = AioScene(mode = AioMode.REFERENCE, prompt = "x")
        assertNotNull(aioProblem(prazdna))
        assertNull(aioProblem(prazdna.copy(refs = listOf(AioSlot(key = 1, image = obrazek)))))
        assertNull(aioProblem(prazdna.copy(refVideo = video)))
    }

    @Test
    fun `klicovy snimek za koncem videa se zachyti`() {
        val s = AioScene(
            mode = AioMode.KEYFRAMES, prompt = "x", seconds = 5f,
            keys = listOf(AioSlot(key = 1, image = obrazek, position = 999)),
        )
        assertNotNull(aioProblem(s))
        assertNull(aioProblem(s.copy(keys = listOf(AioSlot(key = 1, image = obrazek, position = 100)))))
    }

    @Test
    fun `delka lezi na mrizce modelu`() {
        // Mimo mřížku 17k+5 se obraz a zvuk rozejdou – tohle je ta past,
        // na které už jednou skončil lip sync.
        (2..15).forEach { s ->
            val frames = AioScene(seconds = s.toFloat()).frames
            assertEquals("$s s", 5, frames % 17)
        }
        assertEquals(124, AioScene(seconds = 5f).frames)
        assertEquals(362, AioScene(seconds = 15f).frames)
    }

    @Test
    fun `prodlouzeni drzi kontext na sdilene hranici obrazu a zvuku`() {
        listOf(2f, 5f, 10f, 28f).forEach { s ->
            val (kontext, cil, nove) = planExtend(s)
            assertEquals(39, kontext)
            assertEquals(cil, kontext + nove)
            assertEquals(0, nove % 17)
            assertTrue("cíl $cil je nad stropem uzlu", cil <= 736)
        }
    }

    @Test
    fun `nahravaji se jen soubory, ktere rezim opravdu pouzije`() {
        val s = AioScene(
            mode = AioMode.IMAGE,
            first = AioSlot(key = 1, image = File("a.jpg")),
            last = AioSlot(key = 2, image = File("b.jpg")),
            useLastFrame = false,
            refs = listOf(AioSlot(key = 1, image = File("r.jpg"))),
            sourceVideo = video,
        )
        // vypnutý poslední snímek, reference ani zdrojové video sem nepatří
        assertEquals(listOf(File("a.jpg")), s.uploadImages)
        assertNull(s.uploadVideo)

        val ref = s.copy(mode = AioMode.REFERENCE, refVideo = video)
        assertEquals(listOf(File("r.jpg")), ref.uploadImages)
        assertEquals(video, ref.uploadVideo)

        val zvetseni = s.copy(mode = AioMode.UPSCALE)
        assertEquals(emptyList<File>(), zvetseni.uploadImages)
        assertEquals(video, zvetseni.uploadVideo)
    }

    @Test
    fun `sablona odpovida rezimu i vybranemu zvetsovaci`() {
        assertEquals("t2v.json", AioScene(mode = AioMode.TEXT).sablona)
        assertEquals("video_extend.json", AioScene(mode = AioMode.EXTEND).sablona)
        assertEquals("upscale.json", AioScene(mode = AioMode.UPSCALE).sablona)
        assertEquals(
            "upscale_rtx.json",
            AioScene(mode = AioMode.UPSCALE, upscaler = Upscaler.RTX).sablona
        )
    }

    @Test
    fun `upozorneni u referenci pripomene, ze Turbo LoRA na ne neni`() {
        val hints = aioHints(
            AioScene(mode = AioMode.REFERENCE, prompt = "x"),
            GenParams(mode = Mode.ALLINONE, turboLoraOn = true),
        )
        assertTrue(hints.any { it.contains("Turbo LoRA") })
    }
}
