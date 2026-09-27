package cz.promptlab.h3video

import cz.promptlab.h3video.comfy.MiniMaxMusic3Builder
import cz.promptlab.h3video.data.MusicMotor
import cz.promptlab.h3video.data.MusicScene
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Hudba → MiniMax Music 3. */
class MiniMaxMusic3BuilderTest {

    private val sablona = File("src/main/res/raw/workflow_minimax_music3.json").readText()

    private fun JSONObject.inputs(n: String) = getJSONObject(n).getJSONObject("inputs")

    @Test fun `dosadi styl, text, strop a seed do enkoderu i vzorkovace`() {
        val s = MusicScene(motor = MusicMotor.MM3, styl = "Indie pop", text = "[Verse]\nla la", maxSeconds = 90)
        val wf = MiniMaxMusic3Builder.build(sablona, s, 42L)
        val z = wf.inputs(MiniMaxMusic3Builder.N_ZADANI)
        assertEquals("Indie pop", z.getString("caption"))
        assertEquals("[Verse]\nla la", z.getString("lyrics"))
        assertEquals(90.0, z.getDouble("max_duration"), 0.0)
        assertEquals(42L, z.getLong("seed"))
        assertEquals(42L, wf.inputs(MiniMaxMusic3Builder.N_SAMPLER).getLong("seed"))
        assertTrue(MiniMaxMusic3Builder.jeMm3(MiniMaxMusic3Builder.nodeClasses(wf)))
    }

    @Test fun `strop je nejvys pet minut, i kdyz zbyl z YuE2 delsi`() {
        val s = MusicScene(motor = MusicMotor.MM3, styl = "x", maxSeconds = MusicScene.YUE2_MAX_SECONDS)
        assertTrue(s.jenStrop)
        assertEquals(MusicScene.MM3_MAX_SECONDS, s.delka)
        val wf = MiniMaxMusic3Builder.build(sablona, s, 1L)
        assertEquals(300.0, wf.inputs(MiniMaxMusic3Builder.N_ZADANI).getDouble("max_duration"), 0.0)
    }

    @Test fun `nastaveni blueprintu zustava`() {
        val wf = JSONObject(sablona)
        val k = wf.inputs(MiniMaxMusic3Builder.N_SAMPLER)
        assertEquals(30, k.getInt("steps"))
        assertEquals(1.7, k.getDouble("cfg"), 0.0)
        assertEquals("euler", k.getString("sampler_name"))
        assertEquals("", wf.inputs(MiniMaxMusic3Builder.N_ZADANI).getString("caption"))
        assertFalse(MiniMaxMusic3Builder.jeMm3(mapOf("1" to "KSampler")))
    }
}
