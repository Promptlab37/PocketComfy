package cz.promptlab.h3video

import cz.promptlab.h3video.comfy.AngleBuilder
import cz.promptlab.h3video.comfy.Stage
import cz.promptlab.h3video.data.AngleScene
import cz.promptlab.h3video.data.angleProblem
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Karta **Úhel kamery** jede na uživatelově workflow s LoRA `multiple-angles`.
 *
 * Testy hlídají hlavně dvě věci, na kterých by karta tiše selhala:
 *  - **tvar zadání** — LoRA je natrénovaná na `<sks> {azimut} {výška} {odstup}`
 *    v tomhle pořadí a s těmihle anglickými slovy; jakákoli změna slovníku
 *    znamená, že se pózy neprojeví a model fotku jen lehce překreslí,
 *  - **vyladěné hodnoty předlohy** zůstávají netknuté (dosazuje se jen fotka,
 *    seed, zadání a síla LoRA).
 */
class AngleBuilderTest {

    private val sablona: String =
        File("src/main/res/raw/workflow_qwen_angle.json").readText()

    private fun JSONObject.inputs(node: String): JSONObject =
        getJSONObject(node).getJSONObject("inputs")

    private fun bezVisicichOdkazu(wf: JSONObject) {
        wf.keys().asSequence().toList().forEach { id ->
            val ins = wf.getJSONObject(id).getJSONObject("inputs")
            ins.keys().asSequence().toList().forEach { k ->
                val v = ins.opt(k)
                if (v is JSONArray && v.length() == 2 && v.opt(0) is String) {
                    assertTrue("uzel $id → ${v.getString(0)}", wf.has(v.getString(0)))
                }
            }
        }
    }

    // --------------------------------------------------------------- zadání

    @Test
    fun `zadani ma tvar, na kterem je LoRA trenovana`() {
        assertEquals(
            "<sks> front view eye-level shot medium shot",
            AngleBuilder.prompt(0, 1, 1),
        )
        assertEquals(
            "<sks> right side view high-angle shot close-up",
            AngleBuilder.prompt(2, 3, 0),
        )
        assertEquals(
            "<sks> back-left quarter view low-angle shot wide shot",
            AngleBuilder.prompt(5, 0, 2),
        )
    }

    @Test
    fun `slovnik odpovida uzlu QwenMultiangleCameraNode`() {
        // Pořadí směrů je pořadí úhlů 0°, 45°, … 315° v uzlu balíčku.
        assertEquals(
            listOf(
                "front view", "front-right quarter view", "right side view",
                "back-right quarter view", "back view", "back-left quarter view",
                "left side view", "front-left quarter view",
            ),
            AngleBuilder.AZIMUTY.map { it.first },
        )
        assertEquals(
            listOf("low-angle shot", "eye-level shot", "elevated shot", "high-angle shot"),
            AngleBuilder.VYSKY.map { it.first },
        )
        assertEquals(
            listOf("close-up", "medium shot", "wide shot"),
            AngleBuilder.ODSTUPY.map { it.first },
        )
        assertEquals(
            AngleBuilder.POZ,
            AngleBuilder.AZIMUTY.size * AngleBuilder.VYSKY.size * AngleBuilder.ODSTUPY.size,
        )
    }

    @Test
    fun `spoustec je v kazde poze`() {
        for (a in AngleBuilder.AZIMUTY.indices) {
            for (v in AngleBuilder.VYSKY.indices) {
                for (o in AngleBuilder.ODSTUPY.indices) {
                    assertTrue(AngleBuilder.prompt(a, v, o).startsWith(AngleBuilder.SPOUSTEC + " "))
                }
            }
        }
    }

    @Test
    fun `mimo rozsah se sroluje, misto aby to spadlo`() {
        assertEquals(AngleBuilder.prompt(0, 0, 0), AngleBuilder.prompt(-3, -1, -9))
        assertEquals(AngleBuilder.prompt(7, 3, 2), AngleBuilder.prompt(99, 99, 99))
    }

    // ----------------------------------------------------------------- graf

    @Test
    fun `dosadi se fotka, seed, zadani a sila`() {
        val wf = AngleBuilder.build(sablona, 42L, listOf("clovek.png"), 2, 3, 0, 0.8f)
        assertEquals("clovek.png", wf.inputs(AngleBuilder.N_IMAGE).getString("image"))
        assertEquals(42L, wf.inputs(AngleBuilder.N_SAMPLER).getLong("seed"))
        assertEquals(
            "<sks> right side view high-angle shot close-up",
            wf.inputs(AngleBuilder.N_PROMPT).getString("prompt"),
        )
        assertEquals(0.8, wf.inputs(AngleBuilder.N_LORA_UHLY).getDouble("strength_model"), 1e-6)
        bezVisicichOdkazu(wf)
    }

    @Test
    fun `vyladene hodnoty predlohy zustavaji`() {
        val wf = AngleBuilder.build(sablona, 1L, listOf("a.png"), 0, 1, 1)
        val s = wf.inputs(AngleBuilder.N_SAMPLER)
        assertEquals(AngleBuilder.STEPS, s.getInt("steps"))
        assertEquals(1.0, s.getDouble("cfg"), 1e-6)
        assertEquals("euler", s.getString("sampler_name"))
        assertEquals("simple", s.getString("scheduler"))
        assertEquals(1.0, s.getDouble("denoise"), 1e-6)
        // Shift a CFGNorm z předlohy — obojí se nesmí přepsat.
        assertEquals(3.1, wf.inputs("94").getDouble("shift"), 1e-6)
        assertEquals(1.0, wf.inputs("98").getDouble("strength"), 1e-6)
    }

    @Test
    fun `zaporne zadani zustava prazdne`() {
        val wf = AngleBuilder.build(sablona, 1L, listOf("a.png"), 0, 1, 1)
        assertEquals("", wf.inputs(AngleBuilder.N_PROMPT_ZAPORNY).getString("prompt"))
    }

    @Test
    fun `predloha nese obe LoRA a editacni vahy`() {
        val wf = JSONObject(sablona)
        val lory = wf.keys().asSequence().toList().mapNotNull { id ->
            wf.getJSONObject(id).takeIf { it.getString("class_type") == "LoraLoaderModelOnly" }
                ?.getJSONObject("inputs")?.getString("lora_name")
        }
        assertTrue(lory.any { it.contains("multiple-angles") })
        assertTrue(lory.any { it.contains("Lightning") })
        assertTrue(
            wf.inputs("108").getString("unet_name").contains("qwen_image_edit_2511")
        )
    }

    // ---------------------------------------------------------------- karta

    @Test
    fun `bez fotky karta rekne, co chybi`() {
        assertNotNull(angleProblem(AngleScene()))
        assertNull(angleProblem(AngleScene(source = File("a.png"))))
    }

    @Test
    fun `popis do historie je lidsky, ne spoustec`() {
        val s = AngleScene(azimut = 2, vyska = 3, odstup = 0)
        assertTrue(!s.popis.contains("<sks>"))
        assertTrue(s.zadani.startsWith("<sks>"))
    }

    @Test
    fun `faze pokryvaji vsechny uzly predlohy`() {
        val wf = JSONObject(sablona)
        wf.keys().asSequence().toList().forEach { id ->
            val cls = wf.getJSONObject(id).getString("class_type")
            // Nic nesmí propadnout na výchozí větev jako neznámé — kdyby ano,
            // pásek fází by u téhle karty stál na místě.
            assertTrue(
                "uzel $cls nemá fázi",
                AngleBuilder.stageForClass(cls) != Stage.SAMPLING || cls == "KSampler",
            )
        }
    }
}
