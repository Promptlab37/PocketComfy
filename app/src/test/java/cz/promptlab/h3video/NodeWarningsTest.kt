package cz.promptlab.h3video

import cz.promptlab.h3video.engine.NodeWarnings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Rámeček „Co k tomu řekly uzly" má hlásit jen věci, se kterými uživatel
 * může něco udělat. Známé kosmetické hlášky ComfyUI (opakují se při každém
 * běhu dané karty a na výsledek nemají vliv) se schovávají.
 */
class NodeWarningsTest {

    @Test
    fun `preskoceny klic turbo lora u vymeny tvare se neukazuje`() {
        assertTrue(
            NodeWarnings.jeNeskodna(
                "ERROR lora diffusion_model.img_in.weight shape '[3072, 384]' " +
                    "is invalid for input of size 196608"
            )
        )
    }

    @Test
    fun `chybejici text_projection u Fluxu se neukazuje`() {
        assertTrue(NodeWarnings.jeNeskodna("clip missing: ['text_projection.weight']"))
    }

    @Test
    fun `hlasky, ktere uzivateli neco rikaji, zustavaji`() {
        val skutecne = listOf(
            "Dialogue line 2 was rendered as narration, not as speech.",
            "Prompt is empty, the model will improvise.",
            "ERROR lora key not loaded: diffusion_model.double_blocks.0.img_mod.lin.weight",
        )
        skutecne.forEach { assertFalse(it, NodeWarnings.jeNeskodna(it)) }
        assertEquals(skutecne, NodeWarnings.filtruj(skutecne))
    }

    @Test
    fun `filtr necha jen uzitecne hlasky`() {
        val vstup = listOf(
            "ERROR lora diffusion_model.img_in.weight shape '[3072, 384]' is invalid for input of size 196608",
            "Dialogue line 1 was skipped.",
            "clip missing: ['text_projection.weight']",
        )
        assertEquals(listOf("Dialogue line 1 was skipped."), NodeWarnings.filtruj(vstup))
    }
}
