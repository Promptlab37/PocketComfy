package cz.promptlab.h3video

import cz.promptlab.h3video.data.CLIP_INT8
import cz.promptlab.h3video.data.CLIP_NVFP4
import cz.promptlab.h3video.data.Profile
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Žádný profil nesmí posílat enkodér `int8_convrot`: drží v RAM o 11 GB víc než
 * `nvfp4_awq` a byl jediný rozdíl v modelech mezi appkou a uživatelovým ComfyUI
 * UI, kde se počítač tři roky nezasekával (9. 9. 2026).
 */
class EnkoderJakoUiTest {
    @Test
    fun `vsechny profily jedou na nvfp4`() {
        for (p in Profile.entries) {
            assertEquals("profil ${p.name}", CLIP_NVFP4, p.clip)
        }
        assert(CLIP_INT8 != CLIP_NVFP4)
    }
}
