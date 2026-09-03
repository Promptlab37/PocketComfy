package cz.promptlab.h3video

import cz.promptlab.h3video.comfy.DlssBuilder
import cz.promptlab.h3video.comfy.SeedVr2Builder
import cz.promptlab.h3video.comfy.Stage
import cz.promptlab.h3video.data.DlssStyl
import cz.promptlab.h3video.data.UpscaleScene
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Druhá metoda karty Zvětšit: NVIDIA DLSS 5 přes balík ComfyUI-DLSS5-Enhancer.
 * Testy hlídají, že se do předlohy dosazuje jen fotka a čtyři volby z karty —
 * zbytek (presety, práh scény, warmup) zůstává na výchozích hodnotách uzlu.
 */
class DlssBuilderTest {

    private val sablona: String =
        File("src/main/res/raw/workflow_dlss_enhance.json").readText()

    private fun JSONObject.inputs(node: String): JSONObject =
        getJSONObject(node).getJSONObject("inputs")

    @Test
    fun `dosadi se fotka a volby z karty`() {
        val scene = UpscaleScene(
            dlssNasobek = "2x", dlssStyl = DlssStyl.FILMOVY, dlssSila = 0.75f, dlssPlet = true,
        )
        val wf = DlssBuilder.build(sablona, scene, listOf("fotka.png"))
        assertEquals("fotka.png", wf.inputs(DlssBuilder.N_IMAGE).getString("image"))
        val s = wf.inputs(DlssBuilder.N_SETTINGS)
        assertEquals(DlssBuilder.MODE_2X, s.getString("upscaling_mode"))
        assertEquals("Cinematic", s.getString("nr_style"))
        assertEquals(0.75, s.getDouble("nr_intensity"), 0.001)
        assertTrue(s.getBoolean("automatic_mask"))
        // Jedna fotka není sekvence — pohybové vektory nemají co sledovat.
        assertEquals("none", s.getString("motion"))
    }

    @Test
    fun `vypnuta plet necha rekonstrukci kuze na modelu`() {
        val wf = DlssBuilder.build(sablona, UpscaleScene(dlssPlet = false), listOf("a.png"))
        val s = wf.inputs(DlssBuilder.N_SETTINGS)
        assertFalse(s.getBoolean("automatic_mask"))
        // -1 znamená „nech to na modelu"; kladná síla bez masky nedělá nic.
        assertEquals(-1.0, s.getDouble("skin_structure_strength"), 0.001)
    }

    @Test
    fun `vyladene hodnoty predlohy zustavaji netknute`() {
        val wf = DlssBuilder.build(sablona, UpscaleScene(), listOf("a.png"))
        val s = wf.inputs(DlssBuilder.N_SETTINGS)
        assertEquals("Default", s.getString("nr_preset"))
        assertEquals("M", s.getString("dlss_model_preset"))
        assertEquals(1.5, s.getDouble("local_structure_strength"), 0.001)
        assertEquals(1.0, s.getDouble("local_tone_strength"), 0.001)
        assertEquals(0.24, s.getDouble("scene_change_threshold"), 0.001)
        assertEquals(0, s.getInt("warmup_frames"))
        assertEquals("", s.getString("runtime_dir"))
        // Ověření podpisu feature 18 se nevypíná: bez něj by uzel mlčky vrátil
        // jen obyčejně zvětšenou fotku a nikdo by nepoznal, že DLSS neběželo.
        assertTrue(wf.inputs(DlssBuilder.N_ENHANCE).getBoolean("verify_neural_rendering"))
    }

    @Test
    fun `vychozi volba jen doostruje, nezvetsuje`() {
        val wf = DlssBuilder.build(sablona, UpscaleScene(), listOf("a.png"))
        assertEquals(
            DlssBuilder.MODE_1X,
            wf.inputs(DlssBuilder.N_SETTINGS).getString("upscaling_mode")
        )
        assertEquals(1f, DlssBuilder.factorFor("1x"), 0.001f)
        assertEquals(3f, DlssBuilder.factorFor("3x"), 0.001f)
        UpscaleScene.DLSS_NASOBKY.forEach { n ->
            assertTrue("nasobek $n nema popisek", DlssBuilder.modeFor(n).isNotBlank())
        }
    }

    @Test
    fun `zapojeni sedi a nejsou visici odkazy`() {
        val wf = DlssBuilder.build(sablona, UpscaleScene(), listOf("a.png"))
        assertEquals(
            DlssBuilder.N_IMAGE,
            wf.inputs(DlssBuilder.N_ENHANCE).getJSONArray("images").getString(0)
        )
        assertEquals(
            DlssBuilder.N_SETTINGS,
            wf.inputs(DlssBuilder.N_ENHANCE).getJSONArray("settings").getString(0)
        )
        assertEquals(
            DlssBuilder.N_ENHANCE,
            wf.inputs(DlssBuilder.N_SAVE).getJSONArray("images").getString(0)
        )
    }

    /**
     * Běh se nepozná podle příznaku, ale podle tříd uzlů — proto musí
     * `stageForClass` vrátit `null` u všeho, co do DLSS grafu nepatří.
     * Kdyby vracel fázi, spolkl by i uzly SeedVR2 a průběh by lhal.
     */
    @Test
    fun `cizi tridy propadnou na SeedVR2`() {
        assertEquals(Stage.SAMPLING, DlssBuilder.stageForClass("DLSS5EnhanceImages"))
        assertEquals(Stage.MODELS, DlssBuilder.stageForClass("DLSS5Settings"))
        assertNull(DlssBuilder.stageForClass("SeedVR2VideoUpscaler"))
        assertNull(DlssBuilder.stageForClass("ImageTileSplit"))
        assertNull(DlssBuilder.rangeForClass("SeedVR2VideoUpscaler"))
        assertEquals(
            Stage.SAMPLING,
            SeedVr2Builder.stageForClass("SeedVR2VideoUpscaler")
        )
    }

    @Test
    fun `dlss graf se pozna podle trid`() {
        val dlss = DlssBuilder.build(sablona, UpscaleScene(), listOf("a.png"))
        assertTrue(DlssBuilder.jeDlss(DlssBuilder.nodeClasses(dlss)))
        val seedvr2 = SeedVr2Builder.nodeClasses(
            JSONObject(File("src/main/res/raw/workflow_seedvr2_upscale.json").readText())
        )
        assertFalse(DlssBuilder.jeDlss(seedvr2))
    }
}
