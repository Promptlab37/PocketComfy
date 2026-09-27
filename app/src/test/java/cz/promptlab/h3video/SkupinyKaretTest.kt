package cz.promptlab.h3video

import cz.promptlab.h3video.comfy.InterpolaceBuilder
import cz.promptlab.h3video.data.AioMode
import cz.promptlab.h3video.data.AioSlot
import cz.promptlab.h3video.data.KARTY_PRO_ZABER
import cz.promptlab.h3video.data.Mode
import cz.promptlab.h3video.data.NABIZENE_KARTY
import cz.promptlab.h3video.data.PohybRezim
import cz.promptlab.h3video.data.Skupina
import cz.promptlab.h3video.data.UpravaScene
import cz.promptlab.h3video.data.VylepseniScene
import cz.promptlab.h3video.data.ovladaProKartu
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Nabídka karet ve skupinách (4.62). Hlídá, aby každá nabízená karta byla
 * právě v jedné skupině, schované karty měly kam vést, a aby přesun Zvětšit
 * a Přemalovat z All in One nic neztratil.
 */
class SkupinyKaretTest {

    @Test fun `kazda nabizena karta je prave v jedne skupine`() {
        val vsechny = Skupina.entries.flatMap { it.karty }
        assertEquals("karta ve dvou skupinách", vsechny.size, vsechny.toSet().size)
        assertEquals(vsechny, NABIZENE_KARTY)
    }

    @Test fun `schovane karty vedou na nabizenou`() {
        Mode.entries.filterNot { it.nabizena }.forEach {
            assertTrue("${it.name} → ${it.nahradniKarta.name}", it.nahradniKarta.nabizena)
        }
        assertEquals(Mode.POHYB, Mode.DANCE.nahradniKarta)
        assertEquals(Mode.POHYB, Mode.ANIMATE.nahradniKarta)
        assertEquals(Mode.LONGMM, Mode.LONG.nahradniKarta)
    }

    @Test fun `rezimy pohybu vedou na puvodni karty`() {
        assertEquals(Mode.DANCE, PohybRezim.HUDBA.karta)
        assertEquals(Mode.ANIMATE, PohybRezim.VIDEO.karta)
    }

    @Test fun `projekt nabizi jen nabizene karty`() {
        assertTrue(KARTY_PRO_ZABER.all { it.nabizena })
        assertFalse(Mode.PROJEKT in KARTY_PRO_ZABER)
    }

    @Test fun `all in one uz nenabizi zvetsit ani premalovat`() {
        assertFalse(AioMode.UPSCALE in AioMode.VKARTE)
        assertFalse(AioMode.MASK in AioMode.VKARTE)
        // Nic dalšího nezmizelo.
        assertEquals(AioMode.entries.size - 2, AioMode.VKARTE.size)
    }

    @Test fun `premalovat sklada stejnou ulohu jako drive all in one`() {
        val video = File("v.mp4")
        val foto = File("r.jpg")
        val s = UpravaScene(
            video = video, popis = "helmet", maskTarget = "head", maskObjects = 2,
            refs = listOf(AioSlot(key = 1, image = foto)), sekundy = 7f,
        )
        val aio = s.doAio()
        assertEquals(AioMode.MASK, aio.mode)
        assertEquals(video, aio.uploadVideo)
        assertEquals(listOf(foto), aio.uploadImages)
        assertEquals("head", aio.maskTarget)
        assertEquals(2, aio.maskObjects)
        assertEquals(7f, aio.seconds)
    }

    @Test fun `zvetsit sklada ulohu zvetseni`() {
        val aio = VylepseniScene(video = File("v.mp4"), upscaleResolution = 1440).doAio()
        assertEquals(AioMode.UPSCALE, aio.mode)
        assertEquals(1440, aio.upscaleResolution)
        assertNotNull(aio.uploadVideo)
    }

    @Test fun `upravy videa neukazuji rozliseni, vylepseni nic`() {
        assertFalse(ovladaProKartu(Mode.UPRAVA_VIDEA).rozliseni)
        assertTrue(ovladaProKartu(Mode.UPRAVA_VIDEA).kroky)
        assertFalse(ovladaProKartu(Mode.VYLEPSENI_VIDEA).neco)
        assertFalse(ovladaProKartu(Mode.POHYB).neco)
    }

    // ------------------------------------------------------------ zplynulení

    private val sablona = File("src/main/res/raw/workflow_interpolace.json").readText()

    private fun JSONObject.inputs(n: String) = getJSONObject(n).getJSONObject("inputs")

    @Test fun `zplynuleni nasobi fps a necha zvuk`() {
        val wf = InterpolaceBuilder.build(sablona, VylepseniScene(nasobek = 3), "a.mp4")
        assertEquals("a.mp4", wf.inputs("901").getString("file"))
        assertEquals(3, wf.inputs("2").getInt("multiplier"))
        assertEquals(3, wf.inputs("11").getInt("values.b"))
        assertEquals("11", wf.inputs("5").getJSONArray("fps").getString(0))
        assertTrue(wf.inputs("5").has("audio"))
    }

    @Test fun `zpomaleni necha fps zdroje a zahodi zvuk`() {
        val wf = InterpolaceBuilder.build(sablona, VylepseniScene(nasobek = 2, zpomalit = true), "a.mp4")
        assertEquals("3", wf.inputs("5").getJSONArray("fps").getString(0))
        assertEquals(2, wf.inputs("5").getJSONArray("fps").getInt(1))
        assertFalse(wf.inputs("5").has("audio"))
        assertFalse(wf.has("11"))
        // Žádný spoj nesmí mířit na vyhozený uzel.
        wf.keys().forEach { id ->
            val ins = wf.inputs(id)
            ins.keys().forEach { k ->
                val v = ins.get(k)
                if (v is JSONArray && v.length() == 2 && v.get(0) is String) {
                    assertTrue("$id.$k → ${v.getString(0)}", wf.has(v.getString(0)))
                }
            }
        }
    }

    @Test fun `zplynuleni nenabidne nasobek, ktery se nevejde do pameti`() {
        // Ukázkové video z testu: 720 × 1280, 121 snímků — vejde se vše.
        val male = VylepseniScene(video = File("a.mp4"), sirka = 720, vyska = 1280, snimku = 121)
        assertEquals(VylepseniScene.NASOBKY, male.nasobkyKtereSeVejdou)
        // 10 s 1080p při 30 fps: 4× by chtělo přes 30 GB.
        val velke = VylepseniScene(video = File("a.mp4"), sirka = 1920, vyska = 1080, snimku = 300, nasobek = 4)
        assertFalse(4 in velke.nasobkyKtereSeVejdou)
        assertTrue(2 in velke.nasobkyKtereSeVejdou)
        assertNotNull(cz.promptlab.h3video.data.vylepseniProblem(velke.copy(rezim = cz.promptlab.h3video.data.VylepseniRezim.ZPLYNULIT)))
        // 4K dvacet sekund se nevejde vůbec.
        val obri = VylepseniScene(video = File("a.mp4"), sirka = 3840, vyska = 2160, snimku = 600)
        assertTrue(obri.nasobkyKtereSeVejdou.isEmpty())
    }

    @Test fun `vymena postavy motorem H3 jede pres reference bez kotvy`() {
        val s = UpravaScene(
            video = File("v.mp4"), rezim = cz.promptlab.h3video.data.UpravaRezim.POSTAVA,
            postava = File("p.png"), kohoVymenit = "man in purple shirt", videoSekund = 22f,
            motorPostavy = cz.promptlab.h3video.data.PostavaMotor.H3,
        )
        val aio = s.doSwapAio()
        assertEquals(AioMode.REFERENCE, aio.mode)
        assertFalse(aio.kotva)
        assertEquals(File("v.mp4"), aio.uploadVideo)
        assertTrue(aio.prompt.contains("<Video 1>") && aio.prompt.contains("<Picture 1>"))
        assertTrue(aio.prompt.contains("man in purple shirt"))
        assertEquals(15f, aio.seconds)
        // přes 15 s to H3 nevezme — karta to řekne dřív, než se něco nahraje
        assertNotNull(cz.promptlab.h3video.data.upravaProblem(s))
    }
}
