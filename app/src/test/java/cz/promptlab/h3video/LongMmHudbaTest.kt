package cz.promptlab.h3video

import cz.promptlab.h3video.comfy.LongMmBuilder
import cz.promptlab.h3video.data.LongMmScene
import org.junit.Assert.assertEquals
import org.junit.Test

/** Long MM → Podkresová hudba vypnutá = `non_diegetic_music: N/A` v každém úseku. */
class LongMmHudbaTest {

    @Test fun `zapnuta hudba zadani nemeni`() {
        val s = LongMmScene(prompt = "A man walks.", hudba = true)
        assertEquals("A man walks.", LongMmBuilder.zadaniUseku(s))
    }

    @Test fun `vypnuta hudba doplni pole do kazdeho useku`() {
        val s = LongMmScene(prompt = "A man walks.\n---\nHe sits down.", hudba = false)
        assertEquals(
            "A man walks.\n\nnon_diegetic_music: N/A\n---\nHe sits down.\n\nnon_diegetic_music: N/A",
            LongMmBuilder.zadaniUseku(s),
        )
        // Počet úseků se nemění — na něm stojí délky úseků v grafu.
        assertEquals(2, LongMmBuilder.useku(LongMmBuilder.zadaniUseku(s)))
    }

    @Test fun `popsana hudba se nahradi`() {
        val p = "integrated_multimodal_description: [Shot 1] Rain.\n\noverall_soundscape: Rain on glass.\n\n" +
            "non_diegetic_music: Soft piano at a slow tempo,\nfading out."
        assertEquals(
            "integrated_multimodal_description: [Shot 1] Rain.\n\noverall_soundscape: Rain on glass.\n\n" +
                "non_diegetic_music: N/A",
            LongMmBuilder.bezHudby(p),
        )
    }
}
