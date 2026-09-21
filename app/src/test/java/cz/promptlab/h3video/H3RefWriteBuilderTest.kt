package cz.promptlab.h3video

import cz.promptlab.h3video.comfy.H3RefWriteBuilder
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Přepis zadání s referencemi (MiniMax H3, Ref2VA).
 *
 * Starý přepisovač reference neumí, tenhle graf je jediná cesta k nim —
 * testy proto hlídají hlavně to, co by H3 dostal špatně a nikdo by si toho
 * hned nevšiml: role předloh, pořadí a vymyšlené štítky.
 */
class H3RefWriteBuilderTest {

    private fun inputs(wf: JSONObject, uzel: String): JSONObject =
        wf.getJSONObject(uzel).getJSONObject("inputs")

    private fun graf(pocet: Int = 2) = H3RefWriteBuilder.build(
        zadani = "žena a muž v lese",
        obrazky = (1..pocet).map { "ref$it.png" },
        sekundy = 10.0,
        pomer = "16:9",
        captioner = H3RefWriteBuilder.CAPTIONER_ODVAZANY,
        writer = H3RefWriteBuilder.WRITER_ODVAZANY,
        seed = 5L,
    )

    @Test
    fun `uloha je Ref2VA, jinak by se reference zahodily`() {
        assertEquals("Ref2VA", inputs(graf(), H3RefWriteBuilder.N_WRITER).getString("task"))
    }

    @Test
    fun `kazda fotka ma svuj vstup a je v rozvrzeni jako Subject`() {
        val wf = graf(3)
        val w = inputs(wf, H3RefWriteBuilder.N_WRITER)
        assertEquals("ref1.png", inputs(wf, "100").getString("image"))
        assertEquals("ref3.png", inputs(wf, "102").getString("image"))
        assertEquals("100", w.getJSONArray("references.ref_0").getString(0))
        assertEquals("102", w.getJSONArray("references.ref_2").getString(0))

        // Rozvrzeni rozhoduje o poradi i roli — bez nej by o obojim rozhodlo
        // to, do ktereho slotu se co nahodou zapojilo.
        val layout = JSONObject(w.getString("reference_layout")).getJSONArray("items")
        assertEquals(3, layout.length())
        for (i in 0 until layout.length()) {
            val it = layout.getJSONObject(i)
            assertEquals("ref_$i", it.getString("slot"))
            // Fotky lidi jsou Subject, ne Picture: rikaji "takhle vypadaji",
            // ne "tenhle snimek je prvni".
            assertEquals("subj", it.getString("role"))
            assertTrue(it.getBoolean("on"))
        }
    }

    @Test
    fun `zadani nese hlidku proti vymyslenym referencim`() {
        val prompt = inputs(graf(2), H3RefWriteBuilder.N_WRITER).getString("prompt")
        assertTrue(prompt.startsWith("žena a muž v lese"))
        assertTrue(prompt.contains("exactly 2 reference images"))
        assertTrue(prompt.contains("<Subject 1>, <Subject 2>"))
        // Model si jinak pridal "<Video 1> is the source video..." k zadani,
        // kde zadne video nebylo (21. 9. 2026).
        assertTrue(prompt.contains("Do not introduce <Video> or <Audio> labels"))
    }

    @Test
    fun `obe role obsadi odblokovany model a nic se nestahuje`() {
        val wf = graf()
        val w = inputs(wf, H3RefWriteBuilder.N_WRITER)
        assertTrue(w.getString("caption_model").contains("abliterated"))
        assertTrue(w.getString("writer_model").contains("abliterated"))
        assertTrue(w.getString("caption_model").startsWith("on disk"))
        assertTrue(w.getString("writer_model").startsWith("on disk"))
        assertTrue(!inputs(wf, H3RefWriteBuilder.N_OPTIONS).getBoolean("auto_download"))
    }

    @Test
    fun `vzorkovani je greedy a strop neni nekonecny`() {
        val wf = graf()
        // Sest poli v pevnem poradi se malym modelum pri vzorkovani rozpadne.
        assertTrue(inputs(wf, H3RefWriteBuilder.N_WRITER).getBoolean("greedy"))
        assertEquals(
            H3RefWriteBuilder.MAX_TOKENU,
            inputs(wf, H3RefWriteBuilder.N_OPTIONS).getInt("max_new_tokens"),
        )
        assertTrue(H3RefWriteBuilder.MAX_TOKENU in 512..8192)
    }

    @Test
    fun `vyber modelu drzi presny retezec a nespadne na stahovani`() {
        val nabidka = listOf(
            "Qwen3.5-4B — 2.6 GB download",
            H3RefWriteBuilder.WRITER_ODVAZANY,
            "on disk: Qwen3.5-9B-Q8_0.gguf [qwen35, 8.9 GB]",
        )
        assertEquals(
            H3RefWriteBuilder.WRITER_ODVAZANY,
            H3RefWriteBuilder.vyberOdblokovany(nabidka, H3RefWriteBuilder.WRITER_ODVAZANY),
        )
        // Kdyz odblokovany chybi, vezme se jina polozka z disku — ne stahovani.
        val bez = nabidka - H3RefWriteBuilder.WRITER_ODVAZANY
        assertTrue(
            H3RefWriteBuilder.vyberOdblokovany(bez, H3RefWriteBuilder.WRITER_ODVAZANY)!!
                .startsWith("on disk")
        )
        assertNull(
            H3RefWriteBuilder.vyberOdblokovany(
                listOf("Qwen3.5-4B — 2.6 GB download"), H3RefWriteBuilder.WRITER_ODVAZANY,
            )
        )
    }

    @Test
    fun `graf nema visici odkazy`() {
        val wf = graf(4)
        wf.keys().asSequence().toList().forEach { id ->
            val ins = wf.getJSONObject(id).getJSONObject("inputs")
            ins.keys().asSequence().toList().forEach { k ->
                val v = ins.opt(k)
                if (v is org.json.JSONArray && v.length() == 2 && v.opt(0) is String) {
                    assertTrue("uzel $id → ${v.getString(0)}", wf.has(v.getString(0)))
                }
            }
        }
    }
}
