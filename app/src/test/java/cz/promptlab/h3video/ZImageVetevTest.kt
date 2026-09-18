package cz.promptlab.h3video

import cz.promptlab.h3video.comfy.T2iModel
import cz.promptlab.h3video.data.EditLoraFile
import cz.promptlab.h3video.data.ImageLoras
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Turbo a Base jsou jedna rodina — LoRA se načte na obojím. Liší se ale režim,
 * ve kterém vznikla, takže nabídka má ty pro zvolenou větev dávat nahoru
 * a zbytek popsat. Nic se nesmí zahazovat: `ss_base_model_version` u obou
 * větví říká jen „zimage", takže rozpoznání je odhad z názvu.
 */
class ZImageVetevTest {

    private fun soubor(jmeno: String, vararg meta: Pair<String, String>) =
        EditLoraFile(jmeno, JSONObject().apply { meta.forEach { (k, v) -> put(k, v) } })

    @Test
    fun `vetev se pozna z nazvu`() {
        assertEquals(ImageLoras.ZVetev.BASE, ImageLoras.vetev(soubor("MysticXXX-ZIB-v1.safetensors")))
        assertEquals(ImageLoras.ZVetev.BASE, ImageLoras.vetev(soubor("amateur_nudes_base.safetensors")))
        assertEquals(ImageLoras.ZVetev.TURBO, ImageLoras.vetev(soubor("zit_nsfw_v2.safetensors")))
        assertEquals(ImageLoras.ZVetev.TURBO, ImageLoras.vetev(soubor("Z-Image Turbo NSFW.safetensors")))
    }

    @Test
    fun `soubor pro obe vetve i nic nerikajici nazev zustane neurceny`() {
        assertEquals(
            ImageLoras.ZVetev.NEURCENO,
            ImageLoras.vetev(soubor("amat_nude_selfie_z-img-base_&_turbo_aidoc.safetensors")),
        )
        assertEquals(ImageLoras.ZVetev.NEURCENO, ImageLoras.vetev(soubor("gp.safetensors")))
    }

    @Test
    fun `metadata maji stejnou vahu jako nazev`() {
        assertEquals(
            ImageLoras.ZVetev.BASE,
            ImageLoras.vetev(soubor("gp.safetensors", "ss_output_name" to "godpussy_zib_v1")),
        )
    }

    @Test
    fun `u Base jdou nahoru Base LoRA, Turbo az za neurcene`() {
        val seznam = listOf(
            soubor("zit_turbo_nsfw.safetensors"),
            soubor("gp.safetensors"),
            soubor("MysticXXX-ZIB-v1.safetensors"),
        )
        val serazene = ImageLoras.seradPodleVetve(T2iModel.BASE, seznam).map { it.name }
        assertEquals(
            listOf("MysticXXX-ZIB-v1.safetensors", "gp.safetensors", "zit_turbo_nsfw.safetensors"),
            serazene,
        )
    }

    @Test
    fun `poznamka se ukaze jen u cizi vetve`() {
        assertTrue(ImageLoras.poznamkaVetve(T2iModel.BASE, soubor("zit_turbo.safetensors")).isNotBlank())
        assertEquals("", ImageLoras.poznamkaVetve(T2iModel.BASE, soubor("neco_zib.safetensors")))
        assertEquals("", ImageLoras.poznamkaVetve(T2iModel.BASE, soubor("gp.safetensors")))
    }

    @Test
    fun `zadna LoRA z rodiny Z-Image nezmizi z nabidky`() {
        val seznam = listOf(soubor("zit_turbo.safetensors"), soubor("neco_zib.safetensors"))
        assertEquals(seznam.size, ImageLoras.seradPodleVetve(T2iModel.BASE, seznam).size)
    }
}
