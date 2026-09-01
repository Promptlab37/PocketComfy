package cz.promptlab.h3video

import cz.promptlab.h3video.comfy.SeedVr2Builder
import cz.promptlab.h3video.comfy.Stage
import cz.promptlab.h3video.data.UpscaleScene
import cz.promptlab.h3video.data.upscaleProblem
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

/**
 * Karta Zvětšit jede na uživatelově SeedVR2 gigapixel workflow převzatém 1:1.
 * Testy hlídají, že appka dosazuje JEN fotku, mřížku a seed — všechno ostatní
 * (modely, dlaždicové překryvy, prolnutí) musí zůstat z jeho exportu.
 */
class SeedVr2BuilderTest {

    private val sablona: String =
        File("src/main/res/raw/workflow_seedvr2_upscale.json").readText()

    private fun JSONObject.inputs(node: String): JSONObject =
        getJSONObject(node).getJSONObject("inputs")

    @Test
    fun `dosadi se jen fotka, mrizka a seed`() {
        val scene = UpscaleScene(source = File("velka.png"), grid = "3x3")
        val wf = SeedVr2Builder.build(sablona, scene, seed = 77L, images = listOf("velka.png"))

        assertEquals("velka.png", wf.inputs(SeedVr2Builder.N_IMAGE).getString("image"))
        assertEquals("3x3", wf.inputs(SeedVr2Builder.N_SPLIT).getString("grid_size"))
        assertEquals(77L, wf.inputs(SeedVr2Builder.N_UPSCALER).getLong("seed"))
    }

    @Test
    fun `seed se orizne na strop uzlu SeedVR2`() {
        // Appka losuje seed do 999 bilionů (MiniMax H3 ho bere), ale
        // SeedVR2VideoUpscaler má strop 2^32-1 — větší hodnota shodila
        // validaci celého grafu na serveru (chyba z 31. 8. 2026).
        val scene = UpscaleScene(source = File("a.png"))
        val wf = SeedVr2Builder.build(sablona, scene, 114_914_657_461_872L, listOf("a.png"))
        val seed = wf.inputs(SeedVr2Builder.N_UPSCALER).getLong("seed")
        assertTrue("seed $seed přes strop", seed in 0..4_294_967_295L)
        assertEquals(114_914_657_461_872L and 0xFFFF_FFFFL, seed)
    }

    @Test
    fun `vyladene hodnoty z uzivatelova exportu zustavaji netknute`() {
        val wf = SeedVr2Builder.build(
            sablona, UpscaleScene(source = File("a.png")), 1L, listOf("a.png")
        )
        // upscaler: 3200 na dlaždici, lab korekce
        val up = wf.inputs(SeedVr2Builder.N_UPSCALER)
        assertEquals(3200, up.getInt("resolution"))
        assertEquals(3200, up.getInt("max_resolution"))
        assertEquals("lab", up.getString("color_correction"))
        // dlaždice: překryv 2 %, ořez 25 %, kosinové prolnutí
        assertEquals(2.0, wf.inputs(SeedVr2Builder.N_SPLIT).getDouble("overlap_percent"), 0.001)
        val merge = wf.inputs(SeedVr2Builder.N_MERGE)
        assertEquals(25.0, merge.getDouble("crop_percent"), 0.001)
        assertEquals(100.0, merge.getDouble("fade_percent"), 0.001)
        assertEquals("cosine", merge.getString("blend_mode"))
        // modely přesně podle jeho workflow (GGUF Q4 + fp16 VAE)
        assertEquals(
            "seedvr2_ema_7b-Q4_K_M.gguf",
            wf.inputs(SeedVr2Builder.N_DIT).getString("model")
        )
        assertEquals(
            "ema_vae_fp16.safetensors",
            wf.inputs(SeedVr2Builder.N_VAE).getString("model")
        )
    }

    @Test
    fun `zapojeni sedi - dlazdice pres upscaler do slepeni a ulozeni`() {
        val wf = SeedVr2Builder.build(
            sablona, UpscaleScene(source = File("a.png")), 1L, listOf("a.png")
        )
        assertEquals(SeedVr2Builder.N_IMAGE,
            wf.inputs(SeedVr2Builder.N_SPLIT).getJSONArray("image").getString(0))
        assertEquals(SeedVr2Builder.N_SPLIT,
            wf.inputs(SeedVr2Builder.N_UPSCALER).getJSONArray("image").getString(0))
        assertEquals(SeedVr2Builder.N_UPSCALER,
            wf.inputs(SeedVr2Builder.N_MERGE).getJSONArray("tiles").getString(0))
        // tile_info jde ze splitu (výstup 1), ne z upscaleru
        val info = wf.inputs(SeedVr2Builder.N_MERGE).getJSONArray("tile_info")
        assertEquals(SeedVr2Builder.N_SPLIT, info.getString(0))
        assertEquals(1, info.getInt(1))
        assertEquals(SeedVr2Builder.N_MERGE,
            wf.inputs(SeedVr2Builder.N_SAVE).getJSONArray("images").getString(0))
        // žádné visící odkazy
        wf.keys().asSequence().toList().forEach { id ->
            val ins = wf.getJSONObject(id).getJSONObject("inputs")
            ins.keys().asSequence().toList().forEach { k ->
                val v = ins.opt(k)
                if (v is JSONArray && v.length() == 2 && v.opt(0) is String) {
                    assertTrue("uzel $id → ${v.getString(0)}", wf.has(v.getString(0)))
                }
            }
        }
    }

    @Test
    fun `faze a kroky podle tridy uzlu`() {
        assertEquals(Stage.SAMPLING, SeedVr2Builder.stageForClass("SeedVR2VideoUpscaler"))
        assertEquals(Stage.MODELS, SeedVr2Builder.stageForClass("SeedVR2LoadDiTModel"))
        assertEquals(Stage.MUXING, SeedVr2Builder.stageForClass("ImageTileMerge"))
        assertTrue(SeedVr2Builder.reportsSteps("SeedVR2VideoUpscaler"))
        assertFalse(SeedVr2Builder.reportsSteps("ImageTileMerge"))
    }

    @Test
    fun `validace chce fotku a mrizky jsou jen platne`() {
        assertEquals("Vyber fotku, kterou chceš zvětšit.", upscaleProblem(UpscaleScene()))
        assertNull(upscaleProblem(UpscaleScene(source = File("a.png"))))
        assertEquals(listOf("2x2", "3x3", "4x4"), UpscaleScene.GRIDS)
        assertEquals(3, UpscaleScene(grid = "3x3").tilesPerSide)
    }
}
