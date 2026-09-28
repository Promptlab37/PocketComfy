package cz.promptlab.h3video

import cz.promptlab.h3video.comfy.RestoreBuilder
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/** Oprava fotky → Obličeje: Věrně = 2048 px (výchozí autorů Qwenu 2.1), Rychle = 1024 px. */
class RestoreVerneTest {

    private val sablona = File("src/main/res/raw/workflow_qwen21_edit.json").readText()

    private fun rozliseni(verne: Boolean): Int =
        RestoreBuilder.build(sablona, 1L, listOf("a.png"), verne = verne)
            .getJSONObject(RestoreBuilder.N_PROMPT).getJSONObject("inputs").getInt("resolution")

    @Test fun `verne jede ve 2048, rychle v 1024`() {
        assertEquals(2048, rozliseni(true))
        assertEquals(1024, rozliseni(false))
    }
}
