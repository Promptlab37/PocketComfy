package cz.promptlab.h3video

import cz.promptlab.h3video.data.InpaintModel
import cz.promptlab.h3video.data.loryProModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Filtr LoRA pro kartu Domalovat. Adaptér pro FLUX.1 se na FLUX.2 Klein
 * nenasadí (jiné tvary vah) a naopak — nabídka proto musí být oddělená.
 * Vzorek jmen je z reálné složky `models/loras`.
 */
class InpaintLoraFiltrTest {

    private val vzorek = listOf(
        "FLUX.1-Turbo-Alpha.safetensors",
        "nipplediffusion-f1.safetensors",
        "LargeAreolaeFlux.safetensors",
        "flux_realism_lora.safetensors",
        "FLUX2_NAHOTASQNSFW_F2K9B_v1.0.safetensors",
        "FLUXY_KLEINF2K9B_ballgag_b4llg4g_V1E8.safetensors",
        "Flux.2_klein_9b_Giger_v1.safetensors",
        "bfs_head_v1_flux-klein_9b_step3500_rank128.safetensors",
        "Z-IMAGE_pussy-zimage-v1_000026000.safetensors",
        "qwen_2512_pussy_anus_v2.safetensors",
        "Wan_Pussy_LoRA_Hearmeman.safetensors",
        "MINIMAX_H3_vaglokr_e25.safetensors",
    )

    @Test
    fun `fill nabizi jen adaptery pro Flux 1`() {
        val n = loryProModel(InpaintModel.FILL, vzorek)
        assertTrue(n.contains("nipplediffusion-f1.safetensors"))
        assertTrue(n.contains("LargeAreolaeFlux.safetensors"))
        assertTrue(n.contains("flux_realism_lora.safetensors"))
        // rodina FLUX.2 / Klein tam nemá co dělat
        assertFalse(n.any { it.contains("klein", true) })
        assertFalse(n.any { it.contains("F2K", true) })
        // ani modely úplně jiných rodin
        assertFalse(n.any { it.contains("qwen", true) || it.contains("wan", true) })
        assertFalse(n.any { it.contains("zimage", true) || it.contains("minimax", true) })
    }

    @Test
    fun `klein nabizi jen adaptery pro Flux 2`() {
        val n = loryProModel(InpaintModel.KLEIN, vzorek)
        assertTrue(n.contains("FLUX2_NAHOTASQNSFW_F2K9B_v1.0.safetensors"))
        assertTrue(n.contains("Flux.2_klein_9b_Giger_v1.safetensors"))
        assertTrue(n.contains("bfs_head_v1_flux-klein_9b_step3500_rank128.safetensors"))
        assertFalse(n.contains("FLUX.1-Turbo-Alpha.safetensors"))
        assertFalse(n.contains("nipplediffusion-f1.safetensors"))
    }

    @Test
    fun `nabidky se neprekryvaji a jsou serazene`() {
        val fill = loryProModel(InpaintModel.FILL, vzorek)
        val klein = loryProModel(InpaintModel.KLEIN, vzorek)
        assertEquals(emptySet<String>(), fill.toSet() intersect klein.toSet())
        assertEquals(fill.sorted(), fill)
        assertEquals(klein.sorted(), klein)
    }

    @Test
    fun `prazdna nabidka serveru nic nerozbije`() {
        assertEquals(emptyList<String>(), loryProModel(InpaintModel.FILL, emptyList()))
        assertEquals(emptyList<String>(), loryProModel(InpaintModel.KLEIN, emptyList()))
    }
}
