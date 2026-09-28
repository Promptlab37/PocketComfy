package cz.promptlab.h3video

import cz.promptlab.h3video.comfy.FaceSwapBuilder
import cz.promptlab.h3video.data.FaceSwapScene
import cz.promptlab.h3video.data.SwapMotor
import cz.promptlab.h3video.data.faceSwapProblem
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Výměna tváře → Qwen Image 2.1 + BFS Head (autorovo workflow). */
class QwenFaceSwapTest {

    private val sablona = File("src/main/res/raw/workflow_qwen21_faceswap.json").readText()

    private fun JSONObject.inputs(n: String) = getJSONObject(n).getJSONObject("inputs")

    @Test fun `cil je obrazek 1, hlava obrazek 2, seed do vzorkovace`() {
        val wf = FaceSwapBuilder.buildQwen(sablona, 9L, listOf("cil.png", "hlava.png"))
        assertEquals("cil.png", wf.inputs(FaceSwapBuilder.Q_CIL).getString("image"))
        assertEquals("hlava.png", wf.inputs(FaceSwapBuilder.Q_HLAVA).getString("image"))
        assertEquals(9L, wf.inputs(FaceSwapBuilder.Q_SAMPLER).getLong("seed"))
        // Pořadí obrázků je podle autora závazné: 1 = cíl, 2 = hlava.
        val enc = wf.inputs("496")
        assertEquals("477", enc.getJSONArray("images.image_1").getString(0))
        assertEquals("478", enc.getJSONArray("images.image_2").getString(0))
        assertEquals(FaceSwapBuilder.Q_CIL, wf.inputs("477").getJSONArray("image").getString(0))
        assertEquals(FaceSwapBuilder.Q_HLAVA, wf.inputs("478").getJSONArray("image").getString(0))
    }

    @Test fun `hodnoty autora zustavaji`() {
        val wf = JSONObject(sablona)
        val k = wf.inputs(FaceSwapBuilder.Q_SAMPLER)
        assertEquals(8, k.getInt("steps"))
        assertEquals(1.0, k.getDouble("cfg"), 0.0)
        assertEquals("deis_2m", k.getString("sampler_name"))
        assertEquals("bfs_head_v1.1_qwen_2.1.safetensors", wf.inputs("493").getString("lora_name"))
        assertEquals(1.0, wf.inputs("493").getDouble("strength_model"), 0.0)
        assertEquals("", wf.inputs(FaceSwapBuilder.Q_ZADANI).getString("prompt"))
        assertTrue(FaceSwapBuilder.buildQwen(sablona, 1L, listOf("a", "b")).inputs(FaceSwapBuilder.Q_ZADANI)
            .getString("prompt").startsWith("head_swap: start with <image1>"))
        assertEquals(2.0, wf.inputs("13").getDouble("megapixels"), 0.0)
        assertEquals(FaceSwapBuilder.STEPS_QWEN, FaceSwapBuilder.kroky(SwapMotor.QWEN21))
        assertEquals("", wf.inputs(FaceSwapBuilder.Q_CIL).getString("image"))
        // Všechny spoje vedou na uzly, které v grafu jsou.
        wf.keys().forEach { id ->
            wf.inputs(id).let { ins ->
                ins.keys().forEach { key ->
                    val v = ins.get(key)
                    if (v is JSONArray && v.length() == 2 && v.get(0) is String) assertTrue("$id.$key", wf.has(v.getString(0)))
                }
            }
        }
        assertTrue(FaceSwapBuilder.jeQwen(FaceSwapBuilder.nodeClasses(wf)))
    }

    @Test fun `qwen nechce masku a nenahrava ji`() {
        val s = FaceSwapScene(target = File("c.png"), face = File("t.png"), mask = File("m.png"), motor = SwapMotor.QWEN21)
        assertFalse(s.chceMasku)
        assertEquals(listOf(File("c.png"), File("t.png")), s.uploadImages)
        assertNull(faceSwapProblem(s.copy(mask = null)))
        val flux = s.copy(motor = SwapMotor.FLUX, mask = null)
        assertTrue(faceSwapProblem(flux) != null)
    }
}
