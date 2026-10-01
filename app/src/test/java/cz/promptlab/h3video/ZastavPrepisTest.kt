package cz.promptlab.h3video

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Zastavit u vylepšovače nesmí běžící přepis přerušit na serveru.
 *
 * Přerušení během nahrávání jazykového modelu (llama-cpp v procesu ComfyUI)
 * nechá model v grafice a `/free` ho neuvolní — 28. 9. 2026 se po tom další
 * H3 video 19 minut dusilo. Smí se jen smazat úloha, která čeká ve frontě.
 */
class ZastavPrepisTest {

    private fun teloFunkce(): String {
        val zdroj = File("src/main/java/cz/promptlab/h3video/MainViewModel.kt").readText()
        val od = zdroj.indexOf("fun zastavPrepis()")
        assertTrue("zastavPrepis v MainViewModel chybí", od >= 0)
        val konec = zdroj.indexOf("\n    }\n", od)
        return zdroj.substring(od, konec)
    }

    @Test
    fun `zastaveni maze z fronty a preruseni hlida pravidlo`() {
        val telo = teloFunkce()
        assertTrue(telo.contains("deleteFromQueue("))
        // interrupt jen za podmínkou PrerusitPrepis.bezpecne (llama-cpp v procesu nikdy).
        val i = telo.indexOf("interrupt(")
        assertTrue(i < 0 || telo.substring(0, i).contains("PrerusitPrepis.bezpecne("))
    }

    @Test
    fun `llama v procesu se neprerusuje, ostatni ano`() {
        assertFalse(cz.promptlab.h3video.data.PrerusitPrepis.bezpecne(setOf("llama_cpp_model_loader", "llama_cpp_instruct_adv", "PreviewAny")))
        assertFalse(cz.promptlab.h3video.data.PrerusitPrepis.bezpecne(setOf("MiniMaxH3PromptWriter8B", "PreviewAny")))
        assertTrue(cz.promptlab.h3video.data.PrerusitPrepis.bezpecne(setOf("MiniMaxH3UniversalWriter", "LoadImage", "PreviewAny")))
        assertTrue(cz.promptlab.h3video.data.PrerusitPrepis.bezpecne(setOf("TextGenerateLTX2Prompt", "CLIPLoader", "PreviewAny")))
        assertFalse(cz.promptlab.h3video.data.PrerusitPrepis.bezpecne(emptySet()))
    }
}
