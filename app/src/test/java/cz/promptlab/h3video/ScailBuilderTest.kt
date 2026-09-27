package cz.promptlab.h3video

import cz.promptlab.h3video.comfy.ScailBuilder
import cz.promptlab.h3video.data.UpravaRezim
import cz.promptlab.h3video.data.UpravaScene
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Upravit video → Vyměnit postavu (SCAIL-2, smyčka po úsecích). */
class ScailBuilderTest {

    private val sablona = File("src/main/res/raw/workflow_scail_postava.json").readText()

    private fun JSONObject.inputs(n: String) = getJSONObject(n).getJSONObject("inputs")

    private fun scena(naVysku: Boolean = true, koho: String = "") = UpravaScene(
        video = File("v.mp4"), rezim = UpravaRezim.POSTAVA, postava = File("p.png"),
        popis = "a dancer", kohoVymenit = koho, naVysku = naVysku,
    )

    @Test fun `dosadi se vstupy, seed a koho vymenit`() {
        val wf = ScailBuilder.build(sablona, scena(koho = "the man in black"), 7L, "p.png", "v.mp4")
        assertEquals("p.png", wf.inputs(ScailBuilder.N_FOTKA).getString("image"))
        assertEquals("v.mp4", wf.inputs(ScailBuilder.N_VIDEO).getString("file"))
        assertEquals("a dancer", wf.inputs(ScailBuilder.N_POPIS).getString("text"))
        assertEquals("the man in black", wf.inputs(ScailBuilder.N_KOHO_VIDEO).getString("text"))
        assertEquals("the man in black", wf.inputs(ScailBuilder.N_KOHO_FOTKA).getString("text"))
        assertEquals(7L, wf.inputs(ScailBuilder.N_SEED).getLong("noise_seed"))
    }

    @Test fun `bez zadani koho se hleda vychozi human`() {
        val wf = ScailBuilder.build(sablona, scena(), 1L, "p.png", "v.mp4")
        assertEquals(UpravaScene.KOHO_VYCHOZI, wf.inputs(ScailBuilder.N_KOHO_VIDEO).getString("text"))
    }

    @Test fun `platno podle orientace a nasobek 32`() {
        for (naVysku in listOf(true, false)) {
            val i = ScailBuilder.build(sablona, scena(naVysku = naVysku), 1L, "p", "v").inputs(ScailBuilder.N_PLATNO)
            val w = i.getInt("resize_type.width"); val h = i.getInt("resize_type.height")
            assertEquals(naVysku, h > w)
            assertEquals(0, w % 32); assertEquals(0, h % 32)
        }
    }

    @Test fun `predloha nenese zadani a spoje vedou na existujici uzly`() {
        val wf = JSONObject(sablona)
        assertEquals("", wf.inputs(ScailBuilder.N_FOTKA).getString("image"))
        assertEquals("", wf.inputs(ScailBuilder.N_POPIS).getString("text"))
        wf.keys().forEach { id ->
            val ins = wf.inputs(id)
            ins.keys().forEach { k ->
                val v = ins.get(k)
                if (v is JSONArray && v.length() == 2 && v.get(0) is String) {
                    assertTrue("$id.$k -> ${v.getString(0)}", wf.has(v.getString(0)))
                }
            }
        }
    }

    @Test fun `navazujici usek zahodi prekryv a srovna barvy`() {
        val wf = JSONObject(sablona)
        assertEquals(5, wf.inputs("357").getInt("batch_index"))
        assertEquals(5, wf.inputs("364").getInt("previous_frame_count"))
        assertEquals("ColorTransfer", wf.getJSONObject("358").getString("class_type"))
    }
}
