package cz.promptlab.h3video

import cz.promptlab.h3video.comfy.AceMusicBuilder
import cz.promptlab.h3video.comfy.Stage
import cz.promptlab.h3video.data.MusicScene
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Karta Hudba jede na uživatelově ACE-Step 1.5 workflow převzatém 1:1
 * (jen s vyprázdněným zadáním). Testy hlídají, že appka dosazuje JEN
 * zadání skladby a seed — kroky, cfg, sampler i shift zůstávají z předlohy.
 */
class AceMusicBuilderTest {

    private val sablona: String =
        File("src/main/res/raw/workflow_ace_music.json").readText()

    private fun JSONObject.inputs(node: String): JSONObject =
        getJSONObject(node).getJSONObject("inputs")

    private val scena = MusicScene(
        styl = "czech folk rock, akusticka kytara",
        text = "Sloka jedna...",
        seconds = 90,
        language = "cs",
        bpm = 140,
        keyscale = "A minor",
    )

    @Test
    fun `dosadi se cele zadani skladby a seed`() {
        val wf = AceMusicBuilder.build(sablona, scena, 55L)
        val t = wf.inputs(AceMusicBuilder.N_TEXT)
        assertEquals("czech folk rock, akusticka kytara", t.getString("tags"))
        assertEquals("Sloka jedna...", t.getString("lyrics"))
        assertEquals(55L, t.getLong("seed"))
        assertEquals(140, t.getInt("bpm"))
        assertEquals(90, t.getInt("duration"))
        assertEquals("cs", t.getString("language"))
        assertEquals("A minor", t.getString("keyscale"))
        assertEquals(90, wf.inputs(AceMusicBuilder.N_LATENT).getInt("seconds"))
        assertEquals(55L, wf.inputs(AceMusicBuilder.N_SAMPLER).getLong("seed"))
    }

    @Test
    fun `vyladene hodnoty z predlohy zustavaji netknute`() {
        val wf = AceMusicBuilder.build(sablona, scena, 1L)
        val s = wf.inputs(AceMusicBuilder.N_SAMPLER)
        assertEquals(8, s.getInt("steps"))
        assertEquals(1.0, s.getDouble("cfg"), 0.001)
        assertEquals("euler", s.getString("sampler_name"))
        assertEquals("simple", s.getString("scheduler"))
        assertEquals(3.0, wf.inputs(AceMusicBuilder.N_SHIFT).getDouble("shift"), 0.001)
        assertEquals("4", wf.inputs(AceMusicBuilder.N_TEXT).getString("timesignature"))
        assertEquals(
            "ace_step_1.5_turbo_aio.safetensors",
            wf.inputs(AceMusicBuilder.N_CKPT).getString("ckpt_name")
        )
        assertEquals("V0", wf.inputs(AceMusicBuilder.N_SAVE).getString("quality"))
    }

    @Test
    fun `v sablone nesmi zustat zadne stare zadani`() {
        // Předloha vznikla z exportu s konkrétní písní — texty musí být pryč,
        // dosazuje se vždy čerstvé zadání od uživatele.
        val wf = JSONObject(sablona)
        assertEquals("", wf.inputs(AceMusicBuilder.N_TEXT).getString("tags"))
        assertEquals("", wf.inputs(AceMusicBuilder.N_TEXT).getString("lyrics"))
    }

    @Test
    fun `zapojeni sedi - encoder do sampleru a zvuk do mp3`() {
        val wf = AceMusicBuilder.build(sablona, scena, 1L)
        assertEquals(AceMusicBuilder.N_TEXT,
            wf.inputs(AceMusicBuilder.N_SAMPLER).getJSONArray("positive").getString(0))
        assertEquals(AceMusicBuilder.N_ZERO,
            wf.inputs(AceMusicBuilder.N_SAMPLER).getJSONArray("negative").getString(0))
        assertEquals(AceMusicBuilder.N_LATENT,
            wf.inputs(AceMusicBuilder.N_SAMPLER).getJSONArray("latent_image").getString(0))
        assertEquals(AceMusicBuilder.N_SAMPLER,
            wf.inputs(AceMusicBuilder.N_DECODE).getJSONArray("samples").getString(0))
        assertEquals(AceMusicBuilder.N_DECODE,
            wf.inputs(AceMusicBuilder.N_SAVE).getJSONArray("audio").getString(0))
        // VAE i CLIP z téhož checkpointu
        assertEquals(AceMusicBuilder.N_CKPT,
            wf.inputs(AceMusicBuilder.N_DECODE).getJSONArray("vae").getString(0))
        assertEquals(AceMusicBuilder.N_CKPT,
            wf.inputs(AceMusicBuilder.N_TEXT).getJSONArray("clip").getString(0))
    }

    @Test
    fun `faze a kroky podle tridy uzlu`() {
        assertEquals(Stage.SAMPLING, AceMusicBuilder.stageForClass("KSampler"))
        assertEquals(Stage.MODELS, AceMusicBuilder.stageForClass("CheckpointLoaderSimple"))
        assertEquals(Stage.MUXING, AceMusicBuilder.stageForClass("SaveAudioMP3"))
        assertTrue(AceMusicBuilder.reportsSteps("KSampler"))
        assertFalse(AceMusicBuilder.reportsSteps("VAEDecodeAudio"))
        assertEquals(AceMusicBuilder.STEPS, 8)
    }

    @Test
    fun `validace chce styl, text pisne je nepovinny`() {
        assertEquals(
            "Popiš styl skladby — žánr, nástroje, náladu.",
            cz.promptlab.h3video.data.musicProblem(MusicScene())
        )
        org.junit.Assert.assertNull(
            cz.promptlab.h3video.data.musicProblem(MusicScene(styl = "rock"))
        )
        assertTrue("cs" in MusicScene.LANGUAGES)
        assertEquals(34, MusicScene.KEYSCALES.size)
    }
}
