package cz.promptlab.h3video

import cz.promptlab.h3video.comfy.ImagePromptBuilder
import org.junit.Assert.assertEquals
import org.junit.Test

/** Pozorování „SEEN:" si přepisovač píše napřed; do zadání nepatří (4.56). */
class OcistiSeenTest {
    @Test fun `seen se odrizne a zustane jen instrukce`() {
        val t = "SEEN: slim, fair skin.  \nINSTRUCTION: Remove all clothing from the woman in <image1>."
        assertEquals("Remove all clothing from the woman in <image1>.", ImagePromptBuilder.ocisti(t))
    }

    @Test fun `seen bez znacky instrukce`() {
        val t = "SEEN: slim, fair skin.\nRemove all clothing from the woman in <image1>."
        assertEquals("Remove all clothing from the woman in <image1>.", ImagePromptBuilder.ocisti(t))
    }

    @Test fun `bez znacek se nic nemeni`() {
        assertEquals("Add a red hat.", ImagePromptBuilder.ocisti("Add a red hat."))
    }
}
