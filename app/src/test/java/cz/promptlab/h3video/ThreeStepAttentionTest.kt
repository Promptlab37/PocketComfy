package cz.promptlab.h3video

import cz.promptlab.h3video.comfy.ThreeStepBuilder
import cz.promptlab.h3video.data.Aspect
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * Karta 3 kroky nemá uzel `PathchSageAttentionKJ` jako ostatní video karty,
 * ale nativní `ModelAttentionBackend`. Vypínač z Nastavení se proto věší na
 * něj — bez toho byl přepínač na téhle kartě mrtvý (uživatel to našel
 * 15. 9. 2026: „potřeboval bych vypnout sage attention i v té kartě").
 */
class ThreeStepAttentionTest {

    private val sablona: String =
        File("src/main/res/raw/workflow_h3_3step.json").readText()

    private fun attention(zapnuto: Boolean): String =
        ThreeStepBuilder.build(sablona, "kocka", 5.0, Aspect.entries.first(), 1L, zapnuto)
            .getJSONObject(ThreeStepBuilder.N_ATTENTION)
            .getJSONObject("inputs").getString("attention")

    @Test
    fun `vypinac prepne pozornost na pytorch`() {
        assertEquals(ThreeStepBuilder.ATTENTION_RYCHLA, attention(true))
        assertEquals(ThreeStepBuilder.ATTENTION_PYTORCH, attention(false))
    }

    @Test
    fun `predloha jede na rychle pozornosti`() {
        assertEquals(
            ThreeStepBuilder.ATTENTION_RYCHLA,
            JSONObject(sablona).getJSONObject(ThreeStepBuilder.N_ATTENTION)
                .getJSONObject("inputs").getString("attention")
        )
    }
}
