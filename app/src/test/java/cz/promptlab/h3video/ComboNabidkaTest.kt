package cz.promptlab.h3video

import cz.promptlab.h3video.comfy.nabidka
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Nabídka výběru se musí přečíst ve starém i novém tvaru. 25. 9. 2026 přešel
 * přepisovač pro reference na nový tvar a appka hlásila, že nemá model,
 * přestože na serveru ležel.
 */
class ComboNabidkaTest {
    @Test fun `stary tvar`() {
        val req = JSONObject("""{"model": [["a.gguf", "b.gguf"], {"default": "a.gguf"}]}""")
        assertEquals(listOf("a.gguf", "b.gguf"), req.nabidka("model"))
    }

    @Test fun `novy tvar COMBO`() {
        val req = JSONObject(
            """{"caption_model": ["COMBO", {"options": ["on disk: Huihui-x.gguf [+mmproj]", "z"]}]}"""
        )
        assertEquals(listOf("on disk: Huihui-x.gguf [+mmproj]", "z"), req.nabidka("caption_model"))
    }

    @Test fun `chybejici vstup nepada`() {
        assertEquals(emptyList<String>(), JSONObject("{}").nabidka("model"))
        assertEquals(emptyList<String>(), JSONObject("""{"x": ["INT", {}]}""").nabidka("x"))
    }
}
