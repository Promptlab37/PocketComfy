package cz.promptlab.h3video

import cz.promptlab.h3video.comfy.NahledQwen21
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Ostrý náhled Qwen 2.1 (5.34): vloží se před každý KSampler, jinde nic. */
class NahledQwen21Test {

    private fun wf(n: String) = JSONObject(File("src/main/res/raw/$n.json").readText())

    @Test
    fun `vsechny sablony qwen 21`() {
        for (n in listOf("workflow_qwen21_t2i", "workflow_qwen21_edit", "workflow_qwen21_faceswap", "workflow_inpaint_qwen21", "workflow_outpaint_qwen21")) {
            val w = wf(n)
            assertTrue(n, NahledQwen21.jeQwen21(w))
            val puvodni = w.getJSONObject(w.keys().asSequence().first { w.getJSONObject(it).optString("class_type") == "KSampler" })
                .getJSONObject("inputs").getJSONArray("model").toString()
            NahledQwen21.vloz(w)
            val nahled = w.keys().asSequence().filter { w.getJSONObject(it).optString("class_type") == "ModelPreviewOverrideKJ" }.toList()
            assertEquals(n, 1, nahled.size)
            val uzel = w.getJSONObject(nahled[0]).getJSONObject("inputs")
            assertEquals(puvodni, uzel.getJSONArray("model").toString())
            assertEquals(NahledQwen21.TAE, uzel.getString("tiny_vae"))
            val ks = w.keys().asSequence().first { w.getJSONObject(it).optString("class_type") == "KSampler" }
            assertEquals("[\"${nahled[0]}\",0]", w.getJSONObject(ks).getJSONObject("inputs").getJSONArray("model").toString())
            // Podruhé se nic nepřidá.
            val pocet = w.length()
            NahledQwen21.vloz(w)
            assertEquals(pocet, w.length())
        }
        assertFalse(NahledQwen21.jeQwen21(wf("workflow_flux2_klein_t2i")))
    }

    @Test
    fun `server bez souboru nebo uzlu`() {
        assertFalse(NahledQwen21.serverUmi(null))
        val bez = JSONObject("""{"input":{"optional":{"tiny_vae":["COMBO",{"options":["none","taeh3.safetensors"]}]}}}""")
        assertFalse(NahledQwen21.serverUmi(bez))
        val s = JSONObject("""{"input":{"optional":{"tiny_vae":["COMBO",{"options":["none","${NahledQwen21.TAE}"]}]}}}""")
        assertTrue(NahledQwen21.serverUmi(s))
    }
}
