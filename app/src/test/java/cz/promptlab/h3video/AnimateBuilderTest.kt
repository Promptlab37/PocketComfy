package cz.promptlab.h3video

import cz.promptlab.h3video.comfy.AnimateBuilder
import cz.promptlab.h3video.data.AnimateScene
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Karta **Wan Animate** (Wan-Animate 2).
 *
 * Předloha je narovnaná z oficiálního subgrafu se smyčkou, takže testy hlídají
 * dosazování do správných uzlů, plátno podle orientace videa a vědomé
 * odchylky od předlohy (okno vlivu pohybu, cache na CPU).
 */
class AnimateBuilderTest {

    private val sablona: String =
        File("src/main/res/raw/workflow_wan_animate2.json").readText()

    private fun JSONObject.inputs(node: String): JSONObject =
        getJSONObject(node).getJSONObject("inputs")

    private fun scena(naVysku: Boolean = true, postava: String = "", prostredi: String = "", pohyb: String = "") =
        AnimateScene(
            fotka = File("a.png"), video = File("b.mp4"), videoSekund = 5f, videoSnimku = 150,
            naVysku = naVysku, postava = postava, prostredi = prostredi, pohyb = pohyb,
        )

    @Test fun `dosadi se fotka, video, seed a zadani`() {
        val wf = AnimateBuilder.build(
            sablona, scena(postava = "red hair", prostredi = "beach", pohyb = "dancing"),
            42L, "postava.png", "pohyb.mp4",
        )
        assertEquals("postava.png", wf.inputs(AnimateBuilder.N_FOTKA).getString("image"))
        assertEquals("pohyb.mp4", wf.inputs(AnimateBuilder.N_VIDEO).getString("file"))
        assertEquals(42L, wf.inputs(AnimateBuilder.N_SEED).getLong("noise_seed"))
        assertEquals(
            "Character Description: red hair\nBackground description: beach",
            wf.inputs(AnimateBuilder.N_ZADANI).getString("text"),
        )
        assertEquals("dancing", wf.inputs(AnimateBuilder.N_POHYB).getString("text"))
    }

    @Test fun `nevyplnene casti zadani se vynechaji`() {
        assertEquals("", scena().zadani)
        assertEquals("Background description: beach", scena(prostredi = " beach ").zadani)
    }

    @Test fun `platno se ridi orientaci videa a je nasobkem 16`() {
        for (naVysku in listOf(true, false)) {
            val wf = AnimateBuilder.build(sablona, scena(naVysku = naVysku), 1L, "a.png", "b.mp4")
            val w = wf.inputs(AnimateBuilder.N_PLATNO).getInt("resize_type.width")
            val h = wf.inputs(AnimateBuilder.N_PLATNO).getInt("resize_type.height")
            assertEquals(naVysku, h > w)
            assertEquals(0, w % 16)
            assertEquals(0, h % 16)
        }
    }

    @Test fun `pohyb pusobi po celou dobu vzorkovani`() {
        // Oficiální předloha má konec okna spojený se začátkem (0), čímž by se
        // větev s pohybem neuplatnila.
        val p = JSONObject(sablona).inputs("587")
        assertEquals(0.0, p.getDouble("pose_start_percent"), 0.0)
        assertEquals(1.0, p.getDouble("pose_end_percent"), 0.0)
    }

    @Test fun `cache pozy je v RAM`() {
        assertEquals("cpu", JSONObject(sablona).inputs("594").getString("device"))
    }

    @Test fun `predloha nenese zadani`() {
        val p = JSONObject(sablona)
        assertEquals("", p.inputs(AnimateBuilder.N_FOTKA).getString("image"))
        assertEquals("", p.inputs(AnimateBuilder.N_VIDEO).getString("file"))
        assertEquals("", p.inputs(AnimateBuilder.N_ZADANI).getString("text"))
        assertEquals("", p.inputs(AnimateBuilder.N_POHYB).getString("text"))
    }

    @Test fun `vsechny spoje vedou na existujici uzly`() {
        val wf = JSONObject(sablona)
        wf.keys().forEach { id ->
            val ins = wf.inputs(id)
            ins.keys().forEach { k ->
                val v = ins.get(k)
                if (v is org.json.JSONArray && v.length() == 2 && v.get(0) is String) {
                    assertTrue("$id.$k míří na ${v.getString(0)}", wf.has(v.getString(0)))
                }
            }
        }
        assertEquals("SaveVideo", wf.getJSONObject(AnimateBuilder.N_ULOZ).getString("class_type"))
    }

    @Test fun `pocet useku odpovida vzorci predlohy`() {
        // floor((F - 2) / 80) + 1
        assertEquals(1, scena().copy(videoSnimku = 81).useku)
        assertEquals(2, scena().copy(videoSnimku = 82).useku)
        assertEquals(2, scena().copy(videoSnimku = 150).useku)
        assertEquals(1, scena().copy(videoSnimku = 0).useku)
    }
}
