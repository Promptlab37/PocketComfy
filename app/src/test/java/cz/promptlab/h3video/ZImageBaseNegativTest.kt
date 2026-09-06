package cz.promptlab.h3video

import cz.promptlab.h3video.comfy.T2iModel
import cz.promptlab.h3video.comfy.ZImageBuilder
import cz.promptlab.h3video.data.Aspect
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Z-Image **Base** musí mít skutečný prázdný negativ, ne vynulovaný.
 *
 * Šablona v APK je Turbo a ta posílá do negativu `ConditioningZeroOut`.
 * U Turba to nevadí — jede na cfg 1, kde se negativ vůbec nepoužije. Base
 * jede na cfg 4 a tam vzorkovač počítá `neg + cfg * (pos − neg)`; nulový
 * tenzor není totéž co „prázdné zadání" a obrázky vycházejí přepálené
 * a míň soudržné. Oficiální předloha ComfyUI pro Base (`image_z_image.json`)
 * má v negativu obyčejný CLIPTextEncode s prázdným textem.
 */
class ZImageBaseNegativTest {

    private val sablona: String
        get() = File("src/main/res/raw/workflow_zimage_t2i.json").readText()

    private fun graf(model: T2iModel): JSONObject = ZImageBuilder.build(
        template = sablona, prompt = "test", aspect = Aspect.SQUARE_1_1,
        seed = 5L, model = model.id,
    )

    private fun negativ(wf: JSONObject): String =
        wf.getJSONObject(ZImageBuilder.N_SAMPLER).getJSONObject("inputs")
            .getJSONArray("negative").getString(0)

    @Test
    fun `base ma negativ ze skutecneho kodovani prazdneho textu`() {
        val wf = graf(T2iModel.BASE)

        assertEquals(ZImageBuilder.N_NEG_BASE, negativ(wf))
        val uzel = wf.getJSONObject(ZImageBuilder.N_NEG_BASE)
        assertEquals("CLIPTextEncode", uzel.getString("class_type"))
        assertEquals("", uzel.getJSONObject("inputs").getString("text"))
        assertEquals(
            "negativ musí brát stejný textový enkodér jako pozitiv",
            ZImageBuilder.N_CLIP,
            uzel.getJSONObject("inputs").getJSONArray("clip").getString(0),
        )
    }

    @Test
    fun `base jede na skutecnem cfg, proto na tom negativu zalezi`() {
        val vstupy = graf(T2iModel.BASE).getJSONObject(ZImageBuilder.N_SAMPLER)
            .getJSONObject("inputs")
        assertTrue("cfg 1 by negativ zahodilo", vstupy.getDouble("cfg") > 1.0)
        assertEquals(T2iModel.BASE.kroky, vstupy.getInt("steps"))
    }

    @Test
    fun `turbo zustava presne jako predloha`() {
        val wf = graf(T2iModel.TURBO)
        assertEquals(ZImageBuilder.N_ZERO, negativ(wf))
        assertFalse("Turbo žádný uzel navíc nepotřebuje", wf.has(ZImageBuilder.N_NEG_BASE))
    }

    @Test
    fun `photoreal jede na cfg 1, takze vynulovany negativ staci`() {
        val wf = graf(T2iModel.PHOTOREAL)
        assertEquals(ZImageBuilder.N_ZERO, negativ(wf))
        assertEquals(
            1.0,
            wf.getJSONObject(ZImageBuilder.N_SAMPLER).getJSONObject("inputs").getDouble("cfg"),
            1e-6,
        )
    }
}
