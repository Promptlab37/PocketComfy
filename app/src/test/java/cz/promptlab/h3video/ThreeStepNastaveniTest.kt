package cz.promptlab.h3video

import cz.promptlab.h3video.comfy.ThreeStepBuilder
import cz.promptlab.h3video.comfy.ThreeStepBuilder.Nastaveni
import cz.promptlab.h3video.data.Aspect
import cz.promptlab.h3video.data.GenParams
import cz.promptlab.h3video.data.Mode
import cz.promptlab.h3video.data.ovladaProKartu
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Vlastní nastavení karty 3 kroky (od 4.45). Do té doby karta ukazovala
 * sdílené rozlišení, kroky, shift i model, ale stavitel je zahodil.
 */
class ThreeStepNastaveniTest {

    private val sablona = File("src/main/res/raw/workflow_h3_3step.json").readText()
    private val a169 = Aspect.entries.first { it.comfyValue.startsWith("16:9") }

    private fun graf(n: Nastaveni) =
        ThreeStepBuilder.build(sablona, "kocka", 5.0, a169, 1L, nastaveni = n)

    private fun JSONObject.ins(id: String) = getJSONObject(id).getJSONObject("inputs")

    @Test fun `vychozi nastaveni nemeni predlohu`() {
        val g = graf(Nastaveni())
        val p = JSONObject(sablona)
        for (id in listOf("3", "8", "29", "34", "42", "302", "303")) {
            assertEquals(id, p.ins(id).toString(), g.ins(id).toString())
        }
        assertEquals(p.ins("22").toString(), g.ins("22").toString())
        assertFalse(g.has(ThreeStepBuilder.N_NAHLED))
        // i výchozí GenParams dávají předlohu
        assertEquals(Nastaveni(nahled = GenParams().livePreview), Nastaveni.z(GenParams()))
    }

    @Test fun `kazda volba dojde do sveho uzlu`() {
        val g = graf(
            Nastaveni(
                mpx = 0.4f, zvetseni = 2f, kroky = 5, sampler = "res_multistep",
                scheduler = "beta", shiftObraz = 8f, shiftZvuk = 2.5f,
                unet = "minimax_h3_ref2va_pruned_int8_convrot.safetensors", nahled = true,
            )
        )
        assertEquals(0.4, g.ins("22").getDouble("megapixels"), 1e-6)
        assertEquals("round(a*2/32)*32", g.ins("302").getString("expression"))
        assertEquals("round(a*2/32)*32", g.ins("303").getString("expression"))
        assertEquals(5, g.ins("8").getInt("steps"))
        assertEquals(5, g.ins("3").getInt("step"))
        assertEquals("beta", g.ins("8").getString("scheduler"))
        assertEquals("res_multistep", g.ins("29").getString("sampler_name"))
        assertEquals(8.0, g.ins("42").getDouble("shift_video"), 1e-6)
        assertEquals(2.5, g.ins("42").getDouble("shift_audio"), 1e-6)
        assertEquals("minimax_h3_ref2va_pruned_int8_convrot.safetensors", g.ins("34").getString("unet_name"))
        // náhled před oběma průvodci
        assertEquals(ThreeStepBuilder.N_NAHLED, g.ins("33").getJSONArray("model").getString(0))
        assertEquals(ThreeStepBuilder.N_NAHLED, g.ins("35").getJSONArray("model").getString(0))
        assertEquals(ThreeStepBuilder.N_ATTENTION, g.ins(ThreeStepBuilder.N_NAHLED).getJSONArray("model").getString(0))
    }

    @Test fun `vernost referenci jde do obou pruchodu`() {
        val g = ThreeStepBuilder.build(
            sablona, "<Picture 1> kocka", 5.0, a169, 1L,
            reference = listOf("a.png"), nastaveni = Nastaveni(vernost = "match"),
        )
        ThreeStepBuilder.N_PODMINKY.forEach { assertEquals("match", g.ins(it).getString("ref_image_size")) }
    }

    @Test fun `rozmery odpovidaji uzlum`() {
        // 0,2 MPx 16:9 = 608×352, ×1,5811 → 960×544 (předloha)
        val (prvni, druhy) = ThreeStepBuilder.rozmery(a169, Nastaveni())
        assertEquals(608 to 352, prvni)
        assertEquals(960 to 544, druhy)
    }

    @Test fun `sdilene nastaveni se u 3 kroku neukazuje krome LoRA`() {
        val o = ovladaProKartu(Mode.THREESTEP)
        assertFalse(o.rozliseni || o.kroky || o.model || o.shift || o.profil)
        assertTrue(o.lora)
    }
}
