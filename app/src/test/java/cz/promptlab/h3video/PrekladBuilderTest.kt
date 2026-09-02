package cz.promptlab.h3video

import cz.promptlab.h3video.comfy.ImagePromptBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 🌐 Překladač promptu jede na tomtéž llama.cpp uzlu jako ✨ vylepšovač,
 * jen s jiným systémovým promptem. Test hlídá to podstatné: že překlad
 * zadání NEROZEPISUJE a že se text pošle beze změny.
 */
class PrekladBuilderTest {

    private fun system(wf: org.json.JSONObject): String =
        wf.getJSONObject(ImagePromptBuilder.N_LLM).getJSONObject("inputs").getString("system_prompt")

    private fun zadani(wf: org.json.JSONObject): String =
        wf.getJSONObject(ImagePromptBuilder.N_LLM).getJSONObject("inputs").getString("custom_prompt")

    @Test
    fun `preklad posle text beze zmeny`() {
        val wf = ImagePromptBuilder.buildPreklad("žena jde po nábřeží za svítání", "m.gguf", 7L)
        assertEquals("žena jde po nábřeží za svítání", zadani(wf))
        assertEquals("m.gguf", wf.getJSONObject(ImagePromptBuilder.N_LOADER)
            .getJSONObject("inputs").getString("model"))
        assertEquals(7L, wf.getJSONObject(ImagePromptBuilder.N_LLM)
            .getJSONObject("inputs").getLong("seed"))
    }

    @Test
    fun `preklad ma jiny navod nez vylepsovac`() {
        val preklad = system(ImagePromptBuilder.buildPreklad("x", "m.gguf", 1L))
        val vylepseni = system(ImagePromptBuilder.build("x", "m.gguf", 1L))
        assertFalse(preklad == vylepseni)
        // Překlad nesmí nic dopisovat ani mazat.
        assertTrue(preklad.contains("Add nothing, drop nothing"))
        assertTrue(preklad.contains("<Picture 1>"))
        // Vylepšovač naopak rozepisuje podle pravidel Z-Image.
        assertTrue(vylepseni.contains("Z-Image Turbo"))
    }

    @Test
    fun `preklad ma dost tokenu a nizkou teplotu`() {
        val ins = ImagePromptBuilder.buildPreklad("x", "m.gguf", 1L)
            .getJSONObject(ImagePromptBuilder.N_PARAMS).getJSONObject("inputs")
        // Dlouhý prompt se musí vejít celý…
        assertTrue(ins.getInt("max_tokens") >= 800)
        // …a překlad se nesmí rozjet do vlastní tvorby.
        assertTrue(ins.getDouble("temperature") <= 0.3)
    }

    @Test
    fun `graf konci nahledem, ze ktereho appka cte text`() {
        val wf = ImagePromptBuilder.buildPreklad("x", "m.gguf", 1L)
        assertEquals(
            "PreviewAny",
            wf.getJSONObject(ImagePromptBuilder.N_PREVIEW).getString("class_type")
        )
        assertEquals(
            ImagePromptBuilder.N_LLM,
            wf.getJSONObject(ImagePromptBuilder.N_PREVIEW).getJSONObject("inputs")
                .getJSONArray("source").getString(0)
        )
    }
}
