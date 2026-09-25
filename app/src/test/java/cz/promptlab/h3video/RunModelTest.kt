package cz.promptlab.h3video

import cz.promptlab.h3video.engine.RunModel
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * Text „Načítám …" se bere z grafu, ne z karty. 25. 9. 2026 psala Úprava
 * „Načítám Krea 2", přestože jel Qwen. Tady se pro každou šablonu ověří,
 * co by se ukázalo.
 */
class RunModelTest {
    private fun zeSablony(jmeno: String): String =
        RunModel.zGrafu(JSONObject(File("src/main/res/raw/$jmeno").readText())).first

    @Test fun `kazda sablona hlasi svuj model`() {
        val ocekavane = mapOf(
            "workflow_qwen21_edit.json" to "Qwen Image 2.1",
            "workflow_qwen21_t2i.json" to "Qwen Image 2.1",
            "workflow_outpaint_qwen21.json" to "Qwen Image 2.1",
            "workflow_inpaint_qwen21.json" to "Qwen Image 2.1",
            "workflow_krea2_edit.json" to "Krea 2",
            "workflow_flux2_klein_edit.json" to "FLUX.2 Klein",
            "workflow_flux2_klein_t2i.json" to "FLUX.2 Klein",
            "workflow_inpaint_klein.json" to "FLUX.2 Klein",
            "workflow_inpaint_fill.json" to "Flux Fill",
            "workflow_ace_faceswap.json" to "Flux Fill",
            "workflow_zimage_t2i.json" to "Z-Image",
            "workflow_ernie_t2i.json" to "ERNIE Image",
            "workflow_qwen_angle.json" to "Qwen Image Edit 2511",
            "workflow_ltx25_t2v.json" to "LTX 2.5",
            "workflow_dance_wan.json" to "Wan 2.2 Dancer",
            "workflow_h3_3step.json" to "MiniMax H3",
            "workflow_longmm_start.json" to "MiniMax H3",
            "workflow_trellis2.json" to "TRELLIS.2",
            "workflow_ace_music.json" to "ACE-Step 1.5",
            "workflow_yue2_music.json" to "YuE2",
        )
        ocekavane.forEach { (sablona, model) -> assertEquals(sablona, model, zeSablony(sablona)) }
    }

    /** All in One nese dva načítače — hlásit se smí jen ten, který se používá. */
    @Test fun `z dvou nacitacu se hlasi ten pouzity`() {
        assertEquals("MiniMax H3", zeSablony("workflow_h3_ultra.json"))
        val (_, soubor) = RunModel.zGrafu(JSONObject(File("src/main/res/raw/workflow_h3_ultra.json").readText()))
        assertEquals("MiniMax_H3_FL2VA_pruned_int8_convrot.safetensors", soubor)
    }

    @Test fun `bez nacitace je prazdno a plati text karty`() {
        assertEquals("", zeSablony("workflow_dlss_enhance.json"))
    }
}
