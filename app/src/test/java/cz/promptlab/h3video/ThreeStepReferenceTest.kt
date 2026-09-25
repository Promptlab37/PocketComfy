package cz.promptlab.h3video

import cz.promptlab.h3video.comfy.ThreeStepBuilder
import cz.promptlab.h3video.data.Aspect
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Karta 3 kroky s fotkami: v obou průchodech se ImageToVideo vymění za
 * ReferenceToVideo, zbytek receptu zůstane. Bez fotek graf beze změny.
 * Grafy se zároveň vypíšou do build/3kroky-grafy/ ke kontrole proti serveru.
 */
class ThreeStepReferenceTest {
    private val sablona = File("src/main/res/raw/workflow_h3_3step.json").readText()

    private fun JSONObject.ins(id: String) = getJSONObject(id).getJSONObject("inputs")

    @Test fun `bez fotek zustava predloha`() {
        val g = ThreeStepBuilder.build(sablona, "a woman walks", 5.0, Aspect.entries.first(), 1L)
        ThreeStepBuilder.N_PODMINKY.forEach {
            assertEquals("MiniMaxH3ImageToVideo", g.getJSONObject(it).getString("class_type"))
        }
        assertFalse(g.has("700"))
        vypis(g, "bez-referenci")
    }

    @Test fun `s fotkami jedou oba pruchody pres reference`() {
        val g = ThreeStepBuilder.build(
            sablona, "a woman walks", 5.0, Aspect.entries.first(), 1L,
            reference = listOf("a.png", "b.png"),
        )
        ThreeStepBuilder.N_PODMINKY.forEach { id ->
            assertEquals("MiniMaxH3ReferenceToVideo", g.getJSONObject(id).getString("class_type"))
            val i = g.ins(id)
            assertEquals("700", i.getJSONArray("ref_images.ref_image_0").getString(0))
            assertEquals("701", i.getJSONArray("ref_images.ref_image_1").getString(0))
            assertFalse(i.has("ref_images.ref_image_2"))
            assertEquals(ThreeStepBuilder.N_VIDEO_VAE, i.getJSONArray("vae").getString(0))
        }
        // Rozměry obou průchodů zůstaly napojené, jak byly (0,2 -> 0,5 MP).
        assertEquals("22", g.ins("16").getJSONArray("width").getString(0))
        assertEquals("302", g.ins("19").getJSONArray("width").getString(0))
        assertEquals("a.png", g.ins("700").getString("image"))
        // Značky se doplnily, protože v zadání chyběly.
        assertTrue(g.ins(ThreeStepBuilder.N_PROMPT).getString("value").startsWith("Reference images: <Picture 1>, <Picture 2>."))
        vypis(g, "s-referencemi")
    }

    @Test fun `vlastni znacky se neprepisuji`() {
        val g = ThreeStepBuilder.build(
            sablona, "The woman from <Picture 1> walks", 5.0, Aspect.entries.first(), 1L,
            reference = listOf("a.png"),
        )
        assertEquals("The woman from <Picture 1> walks", g.ins(ThreeStepBuilder.N_PROMPT).getString("value"))
    }

    private fun vypis(g: JSONObject, jmeno: String) {
        val cil = File("build/3kroky-grafy").apply { mkdirs() }
        File(cil, "$jmeno.json").writeText(g.toString(1))
    }
}
