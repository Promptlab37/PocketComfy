package cz.promptlab.h3video

import cz.promptlab.h3video.comfy.H3ControlNetBuilder
import cz.promptlab.h3video.data.Mode
import cz.promptlab.h3video.data.Ovlada
import cz.promptlab.h3video.data.PredlohaDruh
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

/** Upravit video → Podle předlohy (MiniMax H3 + Fun ControlNet-Union). */
class H3ControlNetBuilderTest {

    private val sablona = File("src/main/res/raw/workflow_h3_controlnet.json").readText()

    private fun JSONObject.inputs(n: String) = getJSONObject(n).getJSONObject("inputs")

    private fun scena(
        druh: PredlohaDruh = PredlohaDruh.POZA, rychle: Boolean = true,
        naVysku: Boolean = true, sekundy: Float = 5f,
    ) = UpravaScene(
        video = File("v.mp4"), rezim = UpravaRezim.PREDLOHA, popis = "a knight dances",
        predlohaDruh = druh, predlohaRychle = rychle, naVysku = naVysku, predlohaSekundy = sekundy,
    )

    /** Všechny odkazy vedou na uzly, které v grafu jsou. */
    private fun spojeCele(wf: JSONObject) = wf.keys().forEach { id ->
        val ins = wf.inputs(id)
        ins.keys().forEach { k ->
            val v = ins.get(k)
            if (v is JSONArray && v.length() == 2 && v.get(0) is String) {
                assertTrue("$id.$k -> ${v.getString(0)}", wf.has(v.getString(0)))
            }
        }
    }

    @Test fun `dosadi se video, popis a seed`() {
        val wf = H3ControlNetBuilder.build(sablona, scena(), 9L, "v.mp4")
        assertEquals("v.mp4", wf.inputs(H3ControlNetBuilder.N_VIDEO).getString("video"))
        // Předloha se přepočte na 24 fps, jinak by video z telefonu vyšlo zpomalené.
        assertEquals(24.0, wf.inputs(H3ControlNetBuilder.N_VIDEO).getDouble("force_rate"), 0.0)
        assertEquals(H3ControlNetBuilder.snimku(5f), wf.inputs(H3ControlNetBuilder.N_VIDEO).getInt("frame_load_cap"))
        assertEquals(wf.inputs(H3ControlNetBuilder.N_ZADANI).getInt("length"), wf.inputs(H3ControlNetBuilder.N_VIDEO).getInt("frame_load_cap"))
        assertEquals("a knight dances", wf.inputs(H3ControlNetBuilder.N_ZADANI).getString("prompt"))
        assertEquals(9L, wf.inputs(H3ControlNetBuilder.N_SEED).getLong("noise_seed"))
    }

    @Test fun `v grafu zustane jen zvolena vetev`() {
        for (druh in PredlohaDruh.entries) for (rychle in listOf(true, false)) {
            val wf = H3ControlNetBuilder.build(sablona, scena(druh, rychle), 1L, "v")
            val rizeni = wf.inputs(H3ControlNetBuilder.N_CONTROLNET).getJSONArray("control_video").getString(0)
            when (druh) {
                PredlohaDruh.OBRYSY -> {
                    assertEquals(H3ControlNetBuilder.N_OBRYSY, rizeni)
                    H3ControlNetBuilder.N_POZA.forEach { assertFalse(wf.has(it)) }
                }
                PredlohaDruh.POZA -> {
                    assertEquals(H3ControlNetBuilder.N_POZA_VYSTUP, rizeni)
                    assertFalse(wf.has(H3ControlNetBuilder.N_OBRYSY))
                }
            }
            spojeCele(wf)
        }
    }

    @Test fun `rychle ma LoRA a 4 kroky, kvalitne bez ni a 20`() {
        val r = H3ControlNetBuilder.build(sablona, scena(rychle = true), 1L, "v")
        assertTrue(r.has(H3ControlNetBuilder.N_RYCHLE))
        assertEquals(4, r.inputs(H3ControlNetBuilder.N_KROKY).getInt("steps"))
        val k = H3ControlNetBuilder.build(sablona, scena(rychle = false), 1L, "v")
        assertFalse(k.has(H3ControlNetBuilder.N_RYCHLE))
        assertEquals(20, k.inputs(H3ControlNetBuilder.N_KROKY).getInt("steps"))
        assertEquals(H3ControlNetBuilder.N_MODEL,
            k.inputs(H3ControlNetBuilder.N_CONTROLNET).getJSONArray("model").getString(0))
        assertEquals(20, H3ControlNetBuilder.kroky(scena(rychle = false)))
    }

    @Test fun `platno podle orientace a delka na mrizce 17n+5`() {
        for (naVysku in listOf(true, false)) {
            val z = H3ControlNetBuilder.build(sablona, scena(naVysku = naVysku), 1L, "v")
                .inputs(H3ControlNetBuilder.N_ZADANI)
            assertEquals(naVysku, z.getInt("height") > z.getInt("width"))
        }
        for (s in listOf(5f, 7f, 10f, 15f)) {
            val n = H3ControlNetBuilder.snimku(s)
            assertEquals(5, n % 17)
            assertTrue("$s s -> $n", n >= Math.round(s * 24))
        }
        // Úsek se ořízne do povoleného rozsahu i na délku kratšího videa.
        val wf = H3ControlNetBuilder.build(sablona, scena(sekundy = 40f), 1L, "v")
        assertEquals(H3ControlNetBuilder.snimku(15f), wf.inputs(H3ControlNetBuilder.N_ZADANI).getInt("length"))
        val kratke = scena(sekundy = 15f).copy(videoSekund = 7f)
        assertEquals(7f, kratke.predlohaDelka, 0f)
        assertNotNull(upravaProblem(scena().copy(videoSekund = 3f)))
    }

    @Test fun `predloha nenese zadani`() {
        val wf = JSONObject(sablona)
        assertEquals("", wf.inputs(H3ControlNetBuilder.N_VIDEO).getString("video"))
        assertEquals("", wf.inputs(H3ControlNetBuilder.N_ZADANI).getString("prompt"))
        spojeCele(wf)
    }

    @Test fun `karta nenabizi nastaveni, ktera graf nepouzije, a chce popis`() {
        assertEquals(Ovlada.NIC, ovladaProKartu(Mode.UPRAVA_VIDEA, upravaRezim = UpravaRezim.PREDLOHA))
        assertNotNull(upravaProblem(scena().copy(popis = " ")))
        assertNull(upravaProblem(scena()))
    }
}
