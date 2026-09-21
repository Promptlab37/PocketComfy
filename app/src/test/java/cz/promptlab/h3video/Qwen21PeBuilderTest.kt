package cz.promptlab.h3video

import cz.promptlab.h3video.comfy.Qwen21PeBuilder
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Přepisovač promptu od Qwenu. Hlídá hodnoty z referenčního skriptu autorů —
 * ty se liší od výchozích hodnot uzlu `TextGenerate` a ovlivňují výstup.
 */
class Qwen21PeBuilderTest {

    private fun inputs(wf: JSONObject, uzel: String): JSONObject =
        wf.getJSONObject(uzel).getJSONObject("inputs")

    @Test
    fun `chat zacina systemovou zpravou, jinak ji ComfyUI zahodi`() {
        val text = Qwen21PeBuilder.chatText("SYSTEM", "uprav oblohu", 0)
        // Tokenizér v qwen35.py použije text beze změny jen tehdy, když začíná
        // na <|im_start|>. Cokoli jiného znamená ztrátu systémového promptu.
        assertTrue(text.startsWith("<|im_start|>"))
        assertTrue(text.contains("<|im_start|>system\nSYSTEM<|im_end|>"))
        assertTrue(text.contains("uprav oblohu"))
        assertTrue(text.endsWith("<|im_start|>assistant\n"))
    }

    @Test
    fun `kazda fotka ma svuj obrazovy blok`() {
        val bez = Qwen21PeBuilder.chatText("S", "x", 0)
        val dve = Qwen21PeBuilder.chatText("S", "x", 2)
        assertEquals(0, Regex("<\\|image_pad\\|>").findAll(bez).count())
        assertEquals(2, Regex("<\\|image_pad\\|>").findAll(dve).count())
    }

    @Test
    fun `vzorkovani drzi hodnoty z referencniho skriptu`() {
        val wf = Qwen21PeBuilder.build(
            "S", "zadani", Qwen21PeBuilder.MODEL_T2I, emptyList(),
            Qwen21PeBuilder.MAX_TOKENU_T2I, 42L,
        )
        val g = inputs(wf, Qwen21PeBuilder.N_GEN)
        assertEquals("on", g.getString("sampling_mode"))
        assertEquals(1.0, g.getDouble("sampling_mode.temperature"), 1e-9)
        assertEquals(20, g.getInt("sampling_mode.top_k"))
        assertEquals(0.95, g.getDouble("sampling_mode.top_p"), 1e-9)
        // Uzel má ve výchozím stavu min_p 0.05 a repetition_penalty 1.05;
        // autoři je nepředávají, takže musí zůstat neutrální.
        assertEquals(0.0, g.getDouble("sampling_mode.min_p"), 1e-9)
        assertEquals(1.0, g.getDouble("sampling_mode.repetition_penalty"), 1e-9)
        assertTrue(g.getBoolean("thinking"))
    }

    @Test
    fun `delka odpovedi je autorova, ne vychozich 512`() {
        val i2i = Qwen21PeBuilder.build(
            "S", "x", Qwen21PeBuilder.MODEL_I2I, listOf("a.png"),
            Qwen21PeBuilder.MAX_TOKENU_I2I, 1L,
        )
        val t2i = Qwen21PeBuilder.build(
            "S", "x", Qwen21PeBuilder.MODEL_T2I, emptyList(),
            Qwen21PeBuilder.MAX_TOKENU_T2I, 1L,
        )
        assertEquals(24000, inputs(i2i, Qwen21PeBuilder.N_GEN).getInt("max_length"))
        assertEquals(16256, inputs(t2i, Qwen21PeBuilder.N_GEN).getInt("max_length"))
    }

    @Test
    fun `bez fotek se obrazovy vstup vubec neposila`() {
        val wf = Qwen21PeBuilder.build(
            "S", "x", Qwen21PeBuilder.MODEL_T2I, emptyList(),
            Qwen21PeBuilder.MAX_TOKENU_T2I, 1L,
        )
        assertTrue(!inputs(wf, Qwen21PeBuilder.N_GEN).has("image"))
        assertTrue(!wf.has(Qwen21PeBuilder.N_OBRAZEK_PRVNI.toString()))
    }

    @Test
    fun `vic fotek se slepi do jedne davky`() {
        val wf = Qwen21PeBuilder.build(
            "S", "x", Qwen21PeBuilder.MODEL_I2I, listOf("a.png", "b.png", "c.png"),
            Qwen21PeBuilder.MAX_TOKENU_I2I, 1L,
        )
        // Tři LoadImage a dva ImageBatch; na vstup jde poslední dávka.
        assertEquals("a.png", inputs(wf, "100").getString("image"))
        assertEquals("c.png", inputs(wf, "102").getString("image"))
        assertEquals("ImageBatch", wf.getJSONObject("201").getString("class_type"))
        val vstup = inputs(wf, Qwen21PeBuilder.N_GEN).getJSONArray("image")
        assertEquals("201", vstup.getString(0))
    }

    @Test
    fun `z odpovedi se vytahne prompt za blokem uvahy`() {
        val odpoved = """<think>chvíli přemýšlím</think>
            {"rewritten_prompt": "A cat on a red sofa", "wh_ratio": "16:9", "ratio_follow": ""}"""
        val v = Qwen21PeBuilder.parse(odpoved)!!
        assertEquals("A cat on a red sofa", v.prompt)
        assertEquals("16:9", v.pomer)
        assertEquals(2752 to 1536, v.rozmer)
    }

    @Test
    fun `dedeny pomer stran nema vlastni rozmer`() {
        val v = Qwen21PeBuilder.parse(
            """</think>{"rewritten_prompt": "x", "wh_ratio": "", "ratio_follow": "<image1>"}"""
        )!!
        assertEquals("<image1>", v.pomerZObrazku)
        assertNull(v.rozmer)
    }

    @Test
    fun `useknuta odpoved se pozna a nepodstrci se puvodni zadani`() {
        assertNull(Qwen21PeBuilder.parse("<think>uvaha bez konce"))
        assertNull(Qwen21PeBuilder.parse("""<think>x</think> {"rewritten_prompt": ""}"""))
    }

    @Test
    fun `json v uvozovkach nerozhodi hledani konce objektu`() {
        val v = Qwen21PeBuilder.parse(
            """</think>{"rewritten_prompt": "napis \"AHOJ}\" na ceduli", "wh_ratio": "1:1"}"""
        )!!
        assertTrue(v.prompt.contains("AHOJ}"))
        assertEquals(2048 to 2048, v.rozmer)
    }
}
