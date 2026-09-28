package cz.promptlab.h3video

import cz.promptlab.h3video.comfy.H3RefWriteBuilder
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        // to, do ktereho slotu se co nahodou zapojilo. Tvar musi byt ten, ktery
        // cte universal.layout_of (order/off/roles) — {"items":…} do 4.74 uzel
        // tise zahodil.
        val layout = JSONObject(w.getString("reference_layout"))
        val poradi = layout.getJSONArray("order")
        assertEquals(3, poradi.length())
        assertEquals(0, layout.getJSONArray("off").length())
        for (i in 0 until poradi.length()) {
            assertEquals("ref_$i", poradi.getString(i))
            // Fotky lidi jsou Subject, ne Picture: rikaji "takhle vypadaji",
            // ne "tenhle snimek je prvni".
            assertEquals("Subject", layout.getJSONObject("roles").getString("ref_$i"))
        }
        // Bez storyboardu se kazda predloha pta otazkou sve role.
        assertEquals(0, JSONObject(w.getString("reference_instructions")).length())
    }

    private fun grafStoryboard(pocet: Int = 3) = H3RefWriteBuilder.build(
        zadani = "honička v uličce",
        obrazky = (1..pocet).map { "ref$it.png" },
        sekundy = 12.0,
        pomer = "16:9",
        captioner = H3RefWriteBuilder.CAPTIONER_ODVAZANY,
        writer = H3RefWriteBuilder.WRITER_ODVAZANY,
        seed = 5L,
        storyboard = true,
    )

    @Test
    fun `storyboard posila vse jako Picture, cislovani sedi s uzlem H3`() {
        val w = inputs(grafStoryboard(3), H3RefWriteBuilder.N_WRITER)
        val role = JSONObject(w.getString("reference_layout")).getJSONObject("roles")
        // Uzel H3 cisluje <Picture i> podle poradi obrazku; mrizka je prvni.
        for (i in 0 until 3) assertEquals("Picture", role.getString("ref_$i"))
        assertEquals("ref_0", JSONObject(w.getString("reference_layout")).getJSONArray("order").getString(0))
    }

    @Test
    fun `storyboard ma vlastni otazku pro mrizku a pro postavy`() {
        val w = inputs(grafStoryboard(3), H3RefWriteBuilder.N_WRITER)
        val o = JSONObject(w.getString("reference_instructions"))
        val mrizka = o.getJSONObject("ref_0")
        assertEquals(H3RefWriteBuilder.OTAZKA_STORYBOARD, mrizka.getString("text"))
        // Otazka role Picture popisuje jeden snimek — u mrizky se nahrazuje.
        assertFalse(mrizka.getBoolean("add"))
        assertEquals(H3RefWriteBuilder.OTAZKA_POSTAVA, o.getJSONObject("ref_1").getString("text"))
        assertEquals(H3RefWriteBuilder.OTAZKA_POSTAVA, o.getJSONObject("ref_2").getString("text"))
    }

    @Test
    fun `storyboard hlidka rika co je mrizka a co postavy`() {
        val prompt = inputs(grafStoryboard(3), H3RefWriteBuilder.N_WRITER).getString("prompt")
        assertTrue(prompt.startsWith("honička v uličce"))
        assertTrue(prompt.contains("exactly 3 reference images"))
        assertTrue(prompt.contains("<Picture 1>, <Picture 2>, <Picture 3>"))
        // Veta z oficialni prirucky Ref2VA.
        assertTrue(prompt.contains("<Picture 1> is a storyboard reference for every shot"))
        assertTrue(prompt.contains("It is not a frame of the video"))
        assertTrue(prompt.contains("<Picture 2>, <Picture 3> show the characters"))
        assertFalse(prompt.contains("<Subject 1>, <Subject 2>"))
        assertTrue(prompt.contains("Do not introduce <Video> or <Audio> labels"))
    }

    @Test
    fun `storyboard bez postav nezminuje neexistujici obrazky`() {
        val prompt = inputs(grafStoryboard(1), H3RefWriteBuilder.N_WRITER).getString("prompt")
        assertTrue(prompt.contains("exactly 1 reference images"))
        assertFalse(prompt.contains("<Picture 2>"))
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
