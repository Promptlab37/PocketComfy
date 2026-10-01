package cz.promptlab.h3video

import cz.promptlab.h3video.comfy.H3RefWriteBuilder
import cz.promptlab.h3video.comfy.ImagePromptBuilder
import cz.promptlab.h3video.comfy.PromptRewriteBuilder
import cz.promptlab.h3video.comfy.Qwen21PeBuilder
import cz.promptlab.h3video.data.TypKroku
import org.json.JSONObject
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Každý vylepšovač i překladač musí ukazovat stejný průběh s odhadem (uživatel 1. 10. 2026:
 * „některé průběh ukazují, jiné ne“). Odhad má jen graf, který [TypKroku.proGraf] pozná.
 */
class PrubehVsechPrepisuTest {

    private fun graf(vararg tridy: String) = JSONObject().apply {
        tridy.forEachIndexed { i, t -> put("${i + 1}", JSONObject().put("class_type", t).put("inputs", JSONObject())) }
    }

    @Test
    fun `kazdy prepisovac ma typ prubehu`() {
        val grafy = mapOf(
            "odvázaný (llama.cpp) — Obrázek, Úprava, LTX, překlad" to graf(ImagePromptBuilder.LOADER_CLASS, ImagePromptBuilder.NODE_CLASS, "PreviewAny"),
            "odvázaný s fotkou" to graf(ImagePromptBuilder.LOADER_CLASS, ImagePromptBuilder.NODE_CLASS, "LoadImage", "PreviewAny"),
            "MiniMax PromptWriter 8B — All in One, 3 kroky, Long MM" to graf(PromptRewriteBuilder.NODE_CLASS, "PreviewAny"),
            "Universal Writer — reference, film" to graf(H3RefWriteBuilder.NODE_CLASS, "PreviewAny"),
            "Qwen 2.1 PE — Obrázek" to graf(Qwen21PeBuilder.LOADER_CLASS, Qwen21PeBuilder.NODE_CLASS, "PreviewAny"),
        )
        grafy.forEach { (nazev, wf) -> assertNotNull(nazev, TypKroku.proGraf(wf)) }
    }
}
