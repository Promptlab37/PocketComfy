package cz.promptlab.h3video

import cz.promptlab.h3video.comfy.ScailBuilder
import cz.promptlab.h3video.data.PostavaMotor
import cz.promptlab.h3video.data.UpravaScene
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Vyměnit postavu: úsek videa od–do (28. 9. 2026 — uživatel chtěl uříznout
 * začátek i konec a u H3 pustit i video delší než 15 s).
 */
class PostavaUsekTest {

    private val video30 = UpravaScene(video = File("v.mp4"), videoSekund = 30f, postava = File("p.png"))

    @Test
    fun `SCAIL bez vyberu zpracuje cele video, bez orezu`() {
        assertEquals(0f, video30.postavaZacatek)
        assertEquals(30f, video30.postavaKonec)
        assertFalse(video30.postavaOrez)
    }

    @Test
    fun `H3 bez vyberu vezme prvnich 20 s`() {
        val s = video30.copy(motorPostavy = PostavaMotor.H3)
        assertEquals(20f, s.postavaDelka)
        assertTrue(s.postavaOrez)
        val aio = s.doSwapAio()
        assertEquals(20f, aio.seconds)
        assertEquals(0f, aio.refVideoOd)
        assertEquals(20f, aio.refVideoSekund)
    }

    @Test
    fun `usek od do se drzi stropu motoru a minima 2 s`() {
        val h3 = video30.copy(motorPostavy = PostavaMotor.H3, postavaOd = 5f, postavaDo = 29f)
        assertEquals(5f, h3.postavaZacatek)
        assertEquals(25f, h3.postavaKonec)   // 5 + 20
        val kratky = video30.copy(postavaOd = 10f, postavaDo = 10f)
        assertEquals(2f, kratky.postavaDelka)
    }

    @Test
    fun `nova validace nepusti pryc H3 u delsiho videa`() {
        val s = video30.copy(motorPostavy = PostavaMotor.H3, kohoVymenit = "person")
        assertEquals(null, cz.promptlab.h3video.data.upravaProblem(s.copy(rezim = cz.promptlab.h3video.data.UpravaRezim.POSTAVA)))
    }

    private fun template(): String = File("src/main/res/raw/workflow_scail_postava.json").readText()

    @Test
    fun `SCAIL s vyberem - Video Slice za nacteni a vsechny odkazy na nem`() {
        val s = video30.copy(postavaOd = 4f, postavaDo = 12f)
        val wf = ScailBuilder.build(template(), s, 1L, "p.png", "v.mp4")
        val sl = wf.getJSONObject(ScailBuilder.N_OREZ)
        assertEquals("Video Slice", sl.getString("class_type"))
        assertEquals(4.0, sl.getJSONObject("inputs").getDouble("start_time"), 0.001)
        assertEquals(8.0, sl.getJSONObject("inputs").getDouble("duration"), 0.001)
        // Nikdo kromě řezu už nečte přímo načtené video.
        wf.keys().forEach { id ->
            if (id == ScailBuilder.N_OREZ) return@forEach
            val ins = wf.getJSONObject(id).optJSONObject("inputs") ?: return@forEach
            ins.keys().forEach { k ->
                val v = ins.opt(k)
                if (v is JSONArray && v.length() == 2) assertTrue("$id.$k", v.opt(0) != ScailBuilder.N_VIDEO)
            }
        }
    }

    @Test
    fun `SCAIL cele video - zadny rez`() {
        val wf = ScailBuilder.build(template(), video30, 1L, "p.png", "v.mp4")
        assertFalse(wf.has(ScailBuilder.N_OREZ))
    }
}
