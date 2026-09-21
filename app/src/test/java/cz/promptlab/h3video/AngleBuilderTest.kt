package cz.promptlab.h3video

import cz.promptlab.h3video.comfy.AngleBuilder
import cz.promptlab.h3video.comfy.Stage
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Úhel kamery používá přímo Qwen Image 2.1, bez starého 2511 LoRA řetězu. */
class AngleBuilderTest {
    private val sablona = File("src/main/res/raw/workflow_qwen21_edit.json").readText()

    private fun JSONObject.inputs(node: String): JSONObject =
        getJSONObject(node).getJSONObject("inputs")

    private fun bezVisicichOdkazu(wf: JSONObject) {
        val ids = wf.keys().asSequence().toSet()
        wf.keys().forEach { id ->
            val inputs = wf.inputs(id)
            inputs.keys().forEach { key ->
                val value = inputs.opt(key)
                if (value is JSONArray && value.length() == 2 && value.opt(0) is String) {
                    assertTrue("uzel $id → ${value.getString(0)}", value.getString(0) in ids)
                }
            }
        }
    }

    @Test fun `geometrie ovladace zustava obousmerna`() {
        for (smer in AngleBuilder.AZIMUTY.indices) {
            assertEquals(smer, AngleBuilder.smerZUhlu(AngleBuilder.uhelProSmer(smer)))
        }
        for (vyska in AngleBuilder.VYSKY.indices) {
            assertEquals(vyska, AngleBuilder.vyskaZUhlu(AngleBuilder.uhelProVysku(vyska)))
        }
        for (odstup in AngleBuilder.ODSTUPY.indices) {
            assertEquals(odstup, AngleBuilder.odstupZPomeru(AngleBuilder.pomerProOdstup(odstup)))
        }
        assertEquals(96, AngleBuilder.POZ)
    }

    @Test fun `prompt je prirozeny pokyn pro qwen 21 bez stareho spoustece`() {
        val prompt = AngleBuilder.prompt(2, 3, 0)
        assertTrue(prompt.contains("<image1>"))
        assertTrue(prompt.contains("right side view"))
        assertTrue(prompt.contains("high-angle shot"))
        assertTrue(prompt.contains("close-up"))
        assertFalse(prompt.contains("<sks>"))
    }

    /**
     * Qwen 2.1 rozumí změně pohledu jako **otočení o stupně** — samotný název
     * pohledu bral jako slabý pokyn a fotku nechal skoro beze změny. Hlídá se
     * proto, že v pokynu stupně a strana opravdu jsou.
     */
    @Test fun `pokyn nese otoceni ve stupnich a spravnou stranu`() {
        assertEquals(0 to "right", AngleBuilder.otoceni(0))
        assertEquals(90 to "right", AngleBuilder.otoceni(2))
        assertEquals(180 to "right", AngleBuilder.otoceni(4))
        // 225° se říká „o 135 doleva", ne „na 225" — kratší cesta kolem objektu.
        assertEquals(135 to "left", AngleBuilder.otoceni(5))
        assertEquals(90 to "left", AngleBuilder.otoceni(6))
        assertEquals(45 to "left", AngleBuilder.otoceni(7))

        val zleva = AngleBuilder.prompt(6, 1, 1)
        assertTrue(zleva, zleva.contains("90 degrees to the left"))
        assertTrue(zleva.contains("left side view"))

        val zezadu = AngleBuilder.prompt(4, 1, 1)
        assertTrue(zezadu, zezadu.contains("180 degrees"))

        // Zepředu se neotáčí o nic — „orbit 0 degrees" by byl nesmysl.
        val zepredu = AngleBuilder.prompt(0, 1, 1)
        assertFalse(zepredu.contains("0 degrees"))
        assertTrue(zepredu.contains("Keep the camera in front"))
    }

    /**
     * Zachování nesmí pokyn přehlušit: oficiální systémový prompt Qwenu varuje
     * před *under-editing*, kdy se žádaná změna provede jen naznačeně. Proto
     * vede operace a věta o otočení stojí před větou o zachování.
     */
    @Test fun `operace vede pred zachovanim`() {
        val p = AngleBuilder.prompt(6, 1, 1)
        assertTrue(p.indexOf("degrees to the left") < p.indexOf("Keep the same subject"))
        assertTrue(p.contains("not a crop"))
    }

    @Test fun `vyska je vzdy i ve stupnich`() {
        assertTrue(AngleBuilder.vyskaPopis(0).contains("30 degrees below eye level"))
        assertTrue(AngleBuilder.vyskaPopis(1).contains("at eye level"))
        assertTrue(AngleBuilder.vyskaPopis(2).contains("30 degrees above eye level"))
        assertTrue(AngleBuilder.vyskaPopis(3).contains("60 degrees above eye level"))
        // Slovník uzlu zůstává v závorce — je to slovo, na kterém je model učený.
        AngleBuilder.VYSKY.indices.forEach {
            assertTrue(AngleBuilder.vyskaPopis(it).contains(AngleBuilder.VYSKY[it].first))
        }
    }

    @Test fun `graf dosadi fotku seed prompt a oficialni vzorkovani`() {
        val wf = AngleBuilder.build(sablona, 42L, listOf("clovek.png"), 2, 3, 0)
        assertEquals("clovek.png", wf.inputs(AngleBuilder.N_IMAGE).getString("image"))
        assertEquals(42L, wf.inputs(AngleBuilder.N_SAMPLER).getLong("seed"))
        assertEquals(
            AngleBuilder.prompt(2, 3, 0),
            wf.inputs(AngleBuilder.N_PROMPT).getString("prompt"),
        )
        val sampler = wf.inputs(AngleBuilder.N_SAMPLER)
        assertEquals(25, sampler.getInt("steps"))
        assertEquals(1.0, sampler.getDouble("cfg"), 1e-6)
        assertEquals("euler", sampler.getString("sampler_name"))
        assertEquals("simple", sampler.getString("scheduler"))
        assertEquals("H3AngleQwen21", wf.inputs(AngleBuilder.N_SAVE).getString("filename_prefix"))
        bezVisicichOdkazu(wf)
    }

    @Test fun `graf obsahuje pouze novy qwen model a zadnou lora`() {
        val wf = AngleBuilder.build(sablona, 1L, listOf("a.png"), 0, 1, 1)
        assertEquals(
            "qwen_image_2.1_int8_convrot.safetensors",
            wf.inputs("1").getString("unet_name"),
        )
        assertFalse(wf.toString().contains("2511"))
        assertFalse(wf.keys().asSequence().any {
            wf.getJSONObject(it).optString("class_type") == "LoraLoaderModelOnly"
        })
    }

    @Test fun `vsechny tridy predlohy maji fazi`() {
        val wf = JSONObject(sablona)
        wf.keys().forEach { id ->
            val cls = wf.getJSONObject(id).getString("class_type")
            assertTrue(
                "uzel $cls nemá fázi",
                AngleBuilder.stageForClass(cls) != Stage.SAMPLING || cls == "KSampler",
            )
        }
    }
}
