package cz.promptlab.h3video

import cz.promptlab.h3video.comfy.BerniniBuilder
import cz.promptlab.h3video.data.Mode
import cz.promptlab.h3video.data.Ovlada
import cz.promptlab.h3video.data.UpravaRezim
import cz.promptlab.h3video.data.UpravaScene
import cz.promptlab.h3video.data.ovladaProKartu
import cz.promptlab.h3video.data.upravaProblem
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Upravit video → Podle zadání (Bernini-R na Wan 2.2). */
class BerniniBuilderTest {

    private val sablona = File("src/main/res/raw/workflow_bernini_edit.json").readText()

    private fun JSONObject.inputs(n: String) = getJSONObject(n).getJSONObject("inputs")

    private fun scena(
        rychle: Boolean = true, sirka: Int = 1080, vyska: Int = 1920,
        snimku: Int = 300, sekund: Float = 10f,
    ) = UpravaScene(
        video = File("v.mp4"), rezim = UpravaRezim.ZADANI, popis = "Make it night",
        zadaniRychle = rychle, videoSirka = sirka, videoVyska = vyska,
        videoSnimku = snimku, videoSekund = sekund, naVysku = vyska >= sirka,
    )

    private fun spojeCele(wf: JSONObject) = wf.keys().forEach { id ->
        val ins = wf.inputs(id)
        ins.keys().forEach { k ->
            val v = ins.get(k)
            if (v is JSONArray && v.length() == 2 && v.get(0) is String) {
                assertTrue("$id.$k -> ${v.getString(0)}", wf.has(v.getString(0)))
            }
        }
    }

    @Test fun `zadani dostane systemovou vetu autoru bez mezery`() {
        val wf = BerniniBuilder.build(sablona, scena(), 5L, "v.mp4", null)
        assertEquals(BerniniBuilder.SYSTEM_V2V + "Make it night", wf.inputs(BerniniBuilder.N_ZADANI).getString("text"))
        assertEquals("v.mp4", wf.inputs(BerniniBuilder.N_VIDEO).getString("file"))
        assertEquals(5L, wf.inputs(BerniniBuilder.N_SEED).getLong("noise_seed"))
        assertFalse(wf.has(BerniniBuilder.N_REFERENCE))
        spojeCele(wf)
    }

    @Test fun `s fotkou je to uloha s referenci`() {
        val wf = BerniniBuilder.build(sablona, scena(), 1L, "v.mp4", "ref.png")
        assertTrue(wf.inputs(BerniniBuilder.N_ZADANI).getString("text").startsWith(BerniniBuilder.SYSTEM_RV2V))
        assertEquals("ref.png", wf.inputs(BerniniBuilder.N_REFERENCE).getString("image"))
        assertEquals(BerniniBuilder.N_REFERENCE, wf.inputs(BerniniBuilder.N_PODMINKA)
            .getJSONArray("reference_images.reference_image_0").getString(0))
        spojeCele(wf)
    }

    @Test fun `platno drzi pomer videa a delsi strana je 848`() {
        val (w, h) = BerniniBuilder.platno(scena(sirka = 1080, vyska = 1920))
        assertEquals(848, h); assertEquals(0, w % 16); assertEquals(480, w)
        val (w2, h2) = BerniniBuilder.platno(scena(sirka = 1920, vyska = 1080))
        assertEquals(848, w2); assertEquals(480, h2)
        val (w3, h3) = BerniniBuilder.platno(scena(sirka = 1000, vyska = 1000))
        assertEquals(848, w3); assertEquals(848, h3)
    }

    @Test fun `usek videa odpovida 81 snimkum pri jeho fps`() {
        val s = scena(snimku = 300, sekund = 10f)          // 30 fps
        val wf = BerniniBuilder.build(sablona, s, 1L, "v", null)
        assertEquals(81, wf.inputs(BerniniBuilder.N_PODMINKA).getInt("length"))
        assertEquals(81.5 / 30.0, wf.inputs(BerniniBuilder.N_USEK).getDouble("duration"), 0.001)
        // Kratší video: nejbližší nižší 4n+1.
        val kratke = scena(snimku = 50, sekund = 2f)
        assertEquals(49, BerniniBuilder.snimku(kratke))
        assertEquals(1, BerniniBuilder.snimku(kratke) % 4)
    }

    @Test fun `rychle 6 kroku s LoRA, kvalitne 40 bez ni a cfg 5`() {
        val r = BerniniBuilder.build(sablona, scena(rychle = true), 1L, "v", null)
        assertEquals(6, r.inputs(BerniniBuilder.N_KROKY).getInt("steps"))
        assertEquals(3, r.inputs(BerniniBuilder.N_ROZDELENI).getInt("step"))
        assertTrue(r.has(BerniniBuilder.N_LORA_HIGH) && r.has(BerniniBuilder.N_LORA_LOW))
        val k = BerniniBuilder.build(sablona, scena(rychle = false), 1L, "v", null)
        assertEquals(40, k.inputs(BerniniBuilder.N_KROKY).getInt("steps"))
        assertEquals(20, k.inputs(BerniniBuilder.N_ROZDELENI).getInt("step"))
        assertFalse(k.has(BerniniBuilder.N_LORA_HIGH) || k.has(BerniniBuilder.N_LORA_LOW))
        assertEquals(5.0, k.inputs(BerniniBuilder.N_HIGH).getDouble("cfg"), 0.0)
        assertEquals(5.0, k.inputs(BerniniBuilder.N_LOW).getDouble("cfg"), 0.0)
        spojeCele(k)
    }

    @Test fun `prubeh nejde zpet mezi dvema vzorkovaci`() {
        val (_, konecHigh) = BerniniBuilder.rangeForNode(BerniniBuilder.N_HIGH, "SamplerCustom")
        val (zacatekLow, _) = BerniniBuilder.rangeForNode(BerniniBuilder.N_LOW, "SamplerCustom")
        assertEquals(konecHigh, zacatekLow)
    }

    @Test fun `predloha nenese zadani a karta chce popis`() {
        val wf = JSONObject(sablona)
        assertEquals("", wf.inputs(BerniniBuilder.N_ZADANI).getString("text"))
        assertEquals("", wf.inputs(BerniniBuilder.N_VIDEO).getString("file"))
        assertEquals(Ovlada.NIC, ovladaProKartu(Mode.UPRAVA_VIDEA, upravaRezim = UpravaRezim.ZADANI))
        assertNotNull(upravaProblem(scena().copy(popis = "")))
        assertNull(upravaProblem(scena()))
    }
}
