package cz.promptlab.h3video

import cz.promptlab.h3video.comfy.Stage
import cz.promptlab.h3video.comfy.Yue2MusicBuilder
import cz.promptlab.h3video.data.MusicMotor
import cz.promptlab.h3video.data.MusicPlan
import cz.promptlab.h3video.data.MusicScene
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Volba YuE2 na kartě Hudba jede na oficiální předloze ComfyUI
 * (`audio_yue2_text2music.json`, rozbalený subgraph). Testy hlídají dvě věci,
 * ve kterých se tenhle model liší od ACE-Step a které jdou snadno zlomit:
 * délku si určuje sám (latent se bere z jeho výstupu, ne ze zadání) a prázdné
 * noty si přepínají režim, takže se uzel s notami musí z grafu vypustit.
 */
class Yue2MusicBuilderTest {

    private val sablona: String =
        File("src/main/res/raw/workflow_yue2_music.json").readText()

    private fun JSONObject.inputs(node: String): JSONObject =
        getJSONObject(node).getJSONObject("inputs")

    private val scena = MusicScene(
        motor = MusicMotor.YUE2,
        styl = "upbeat indie pop, warm female vocals",
        text = "[Verse]\nMorning light across the window",
        maxSeconds = 180,
        plan = MusicPlan.FULL,
    )

    @Test
    fun `dosadi se zadani skladby, strop delky a seed`() {
        val wf = Yue2MusicBuilder.build(sablona, scena, 55L)
        val m = wf.inputs(Yue2MusicBuilder.N_MUSIC)
        assertEquals("upbeat indie pop, warm female vocals", m.getString("style"))
        assertEquals("[Verse]\nMorning light across the window", m.getString("lyrics"))
        assertEquals(55L, m.getLong("seed"))
        assertEquals(180, m.getInt("max_duration"))
        assertEquals("full", m.getString("mode"))
        assertEquals(55L, wf.inputs(Yue2MusicBuilder.N_SAMPLER).getLong("seed"))
        // Noty dostávají TOTÉŽ zadání i seed — jinak by se zpívalo podle jiné
        // písně, než jakou si model naplánoval.
        val abc = wf.inputs(Yue2MusicBuilder.N_ABC)
        assertEquals(m.getString("style"), abc.getString("style"))
        assertEquals(m.getString("lyrics"), abc.getString("lyrics"))
        assertEquals(55L, abc.getLong("seed"))
        assertEquals("full", abc.getString("mode"))
    }

    @Test
    fun `delku urcuje model, ne zadani`() {
        val wf = Yue2MusicBuilder.build(sablona, scena, 1L)
        // EmptyYuE2LatentAudio.seconds MUSÍ zůstat odkazem na druhý výstup
        // YuE2GenerateMusic. Dosazené číslo by znamenalo latent jiné délky,
        // než jakou model nazpíval.
        val sekundy = wf.inputs(Yue2MusicBuilder.N_LATENT).getJSONArray("seconds")
        assertEquals(Yue2MusicBuilder.N_MUSIC, sekundy.getString(0))
        assertEquals(1, sekundy.getInt(1))
    }

    @Test
    fun `melodie posle rezim do obou uzlu`() {
        val wf = Yue2MusicBuilder.build(sablona, scena.copy(plan = MusicPlan.MELODY), 1L)
        assertEquals("melody", wf.inputs(Yue2MusicBuilder.N_MUSIC).getString("mode"))
        assertEquals("melody", wf.inputs(Yue2MusicBuilder.N_ABC).getString("mode"))
    }

    @Test
    fun `bez planu se uzel s notami z grafu vypousti`() {
        val wf = Yue2MusicBuilder.build(sablona, scena.copy(plan = MusicPlan.NONE), 1L)
        assertNull(wf.opt(Yue2MusicBuilder.N_ABC))
        // abc musí odejít jako prázdný TEXT, ne jako odkaz — na tom si uzel
        // sám přepne režim na „off".
        assertEquals("", wf.inputs(Yue2MusicBuilder.N_MUSIC).getString("abc"))
    }

    @Test
    fun `vyladene hodnoty z predlohy zustavaji netknute`() {
        val wf = Yue2MusicBuilder.build(sablona, scena, 1L)
        val s = wf.inputs(Yue2MusicBuilder.N_SAMPLER)
        assertEquals(32, s.getInt("steps"))
        assertEquals(1.0, s.getDouble("cfg"), 0.001)
        assertEquals("dpm_2", s.getString("sampler_name"))
        assertEquals("sgm_uniform", s.getString("scheduler"))
        assertEquals(1.0, s.getDouble("denoise"), 0.001)
        val m = wf.inputs(Yue2MusicBuilder.N_MUSIC)
        assertEquals(1.0, m.getDouble("temperature"), 0.001)
        assertEquals(0.95, m.getDouble("top_p"), 0.001)
        assertEquals(100, m.getInt("top_k"))
        assertEquals(1.2, m.getDouble("repetition_penalty"), 0.001)
        val a = wf.inputs(Yue2MusicBuilder.N_ABC)
        assertEquals(8192, a.getInt("max_abc_tokens"))
        assertEquals(0.7, a.getDouble("temperature"), 0.001)
        assertEquals(30, a.getInt("top_k"))
        assertEquals(
            "yue2_3b_int8_convrot.safetensors",
            wf.inputs(Yue2MusicBuilder.N_CKPT).getString("ckpt_name")
        )
        assertEquals("V0", wf.inputs(Yue2MusicBuilder.N_SAVE).getString("quality"))
    }

    @Test
    fun `v sablone nesmi zustat zadne stare zadani`() {
        val wf = JSONObject(sablona)
        assertEquals("", wf.inputs(Yue2MusicBuilder.N_MUSIC).getString("style"))
        assertEquals("", wf.inputs(Yue2MusicBuilder.N_MUSIC).getString("lyrics"))
        assertEquals("", wf.inputs(Yue2MusicBuilder.N_ABC).getString("style"))
        assertEquals("", wf.inputs(Yue2MusicBuilder.N_ABC).getString("lyrics"))
    }

    @Test
    fun `zapojeni sedi - noty do zpevu a zvuk do mp3`() {
        val wf = Yue2MusicBuilder.build(sablona, scena, 1L)
        assertEquals(Yue2MusicBuilder.N_ABC,
            wf.inputs(Yue2MusicBuilder.N_MUSIC).getJSONArray("abc").getString(0))
        assertEquals(Yue2MusicBuilder.N_MUSIC,
            wf.inputs(Yue2MusicBuilder.N_SAMPLER).getJSONArray("positive").getString(0))
        assertEquals(Yue2MusicBuilder.N_ZERO,
            wf.inputs(Yue2MusicBuilder.N_SAMPLER).getJSONArray("negative").getString(0))
        assertEquals(Yue2MusicBuilder.N_LATENT,
            wf.inputs(Yue2MusicBuilder.N_SAMPLER).getJSONArray("latent_image").getString(0))
        assertEquals(Yue2MusicBuilder.N_SAMPLER,
            wf.inputs(Yue2MusicBuilder.N_DECODE).getJSONArray("samples").getString(0))
        assertEquals(Yue2MusicBuilder.N_DECODE,
            wf.inputs(Yue2MusicBuilder.N_SAVE).getJSONArray("audio").getString(0))
        // MODEL, CLIP i VAE z téhož checkpointu
        assertEquals(Yue2MusicBuilder.N_CKPT,
            wf.inputs(Yue2MusicBuilder.N_SAMPLER).getJSONArray("model").getString(0))
        assertEquals(Yue2MusicBuilder.N_CKPT,
            wf.inputs(Yue2MusicBuilder.N_MUSIC).getJSONArray("clip").getString(0))
        assertEquals(Yue2MusicBuilder.N_CKPT,
            wf.inputs(Yue2MusicBuilder.N_DECODE).getJSONArray("vae").getString(0))
    }

    @Test
    fun `faze a kroky podle tridy uzlu`() {
        assertEquals(Stage.MODELS, Yue2MusicBuilder.stageForClass("CheckpointLoaderSimple"))
        assertEquals(Stage.REFERENCES, Yue2MusicBuilder.stageForClass("YuE2GenerateABC"))
        assertEquals(Stage.ENCODING, Yue2MusicBuilder.stageForClass("YuE2GenerateMusic"))
        assertEquals(Stage.SAMPLING, Yue2MusicBuilder.stageForClass("KSampler"))
        assertEquals(Stage.MUXING, Yue2MusicBuilder.stageForClass("SaveAudioMP3"))
        assertTrue(Yue2MusicBuilder.reportsSteps("KSampler"))
        assertFalse(Yue2MusicBuilder.reportsSteps("YuE2GenerateMusic"))
        assertEquals(32, Yue2MusicBuilder.STEPS)
        // Fáze jdou v čase za sebou, ukazatel průběhu nesmí couvat.
        val poradi = listOf(
            "CheckpointLoaderSimple", "YuE2GenerateABC", "YuE2GenerateMusic",
            "KSampler", "VAEDecodeAudio", "SaveAudioMP3",
        ).map { Yue2MusicBuilder.rangeForClass(it) }
        poradi.zipWithNext { a, b -> assertTrue("$a před $b", a.second <= b.first) }
    }

    @Test
    fun `karta nabizi jen to, co model opravdu pouzije`() {
        // YuE2 nemá na vstupu jazyk, BPM ani tóninu — u ACE-Step naopak platí
        // přesná délka. Kdyby se pole prohodila, karta by slibovala něco,
        // co graf zahodí.
        val yue = MusicScene(motor = MusicMotor.YUE2, maxSeconds = 300, seconds = 90)
        assertEquals(300, yue.delka)
        assertEquals(90, yue.copy(motor = MusicMotor.ACE).delka)
    }

    /**
     * Karta Hudba nesmí uživateli radit, ať přepne motor kvůli češtině —
     * uživatel si to nepřeje. Kdyby se to do upozornění vrátilo, spadne to tady.
     */
    @Test
    fun `karta neradi prepnout na ACE-Step kvuli cestine`() {
        val cesky = MusicScene(
            motor = MusicMotor.YUE2, styl = "folk", text = "Příliš žluťoučký kůň",
        )
        val hlasky = cz.promptlab.h3video.data.musicHints(cesky)
        assertFalse(hlasky.any { "ACE-Step" in it })
        assertFalse(hlasky.any { "zkomolen" in it })
    }
}
