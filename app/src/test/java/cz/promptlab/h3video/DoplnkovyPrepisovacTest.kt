package cz.promptlab.h3video

import cz.promptlab.h3video.comfy.H3RefWriteBuilder
import cz.promptlab.h3video.data.DoplnkovyPrepisovac
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Hák doplňkového přepisovače: bez volby nebo bez jejího modelu na serveru beze změny. */
class DoplnkovyPrepisovacTest {

    private val volba = DoplnkovyPrepisovac.Volba("Doplnek-Q6_K.gguf", "systém")
    private val doplnek = "on disk: Doplnek-Q6_K.gguf [qwen3vl, 6.3 GB]"
    private val nabidka = listOf(
        "on disk: Huihui-Qwen3-VL-8B-Instruct-abliterated-Q6_K.gguf [qwen3vl, 6.3 GB]",
        doplnek,
    )

    @After fun vypnout() { DoplnkovyPrepisovac.zapnuto = false }

    @Test fun `bez volby dosavadni vyber`() {
        val p = DoplnkovyPrepisovac.pisatel(nabidka, null)
        assertEquals(H3RefWriteBuilder.vyberOdblokovany(nabidka, H3RefWriteBuilder.WRITER_ODVAZANY), p.writer)
        assertNull(p.systemPrompt)
    }

    @Test fun `volba s modelem na serveru`() {
        val p = DoplnkovyPrepisovac.pisatel(nabidka, volba)
        assertEquals(doplnek, p.writer)
        assertEquals("systém", p.systemPrompt)
    }

    @Test fun `model chybi - dosavadni vyber`() {
        val bez = nabidka - doplnek
        assertNull(DoplnkovyPrepisovac.polozka(bez, volba))
        assertEquals(DoplnkovyPrepisovac.pisatel(bez, null), DoplnkovyPrepisovac.pisatel(bez, volba))
    }

    @Test fun `vypnuty prepinac nic nevraci`() {
        DoplnkovyPrepisovac.zapnuto = false
        assertNull(DoplnkovyPrepisovac.aktivni())
        assertNull(DoplnkovyPrepisovac.nacti("cz.promptlab.h3video.neexistuje.Zdroj"))
    }
}
