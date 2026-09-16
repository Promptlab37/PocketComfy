package cz.promptlab.h3video

import cz.promptlab.h3video.comfy.Stage
import cz.promptlab.h3video.comfy.Yue2MusicBuilder
import cz.promptlab.h3video.data.MusicMotor
import cz.promptlab.h3video.data.MusicPlan
import cz.promptlab.h3video.data.MusicRezim
import cz.promptlab.h3video.data.MusicScene
import cz.promptlab.h3video.data.musicProblem
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Karta Hudba, volba **Předělat nahrávku** (YuE2 + SheetSage2).
 *
 * Předloha se do modelu nedostane jako zvuk: `SheetSage2AudioToABC` z ní
 * přepíše melodii do not a ty nahradí noty, které by si YuE2 napsal sám.
 * Testy hlídají to, co je na tom snadné zlomit — shodu `mode` na obou
 * uzlech, dosazení nahraného souboru a to, že délku pořád určuje model.
 */
class Yue2CoverTest {

    private val cover: String = File("src/main/res/raw/workflow_yue2_cover.json").readText()

    private fun JSONObject.inputs(node: String): JSONObject =
        getJSONObject(node).getJSONObject("inputs")

    private fun scene(akordy: Boolean = false) = MusicScene(
        motor = MusicMotor.YUE2,
        rezim = MusicRezim.PREDELAT,
        styl = "80s synthpop, female vocal",
        text = "[Verse]\nnight drive",
        maxSeconds = 180,
        predlohaAkordy = akordy,
    )

    @Test
    fun `nahrana predloha se dosadi do LoadAudio`() {
        val wf = Yue2MusicBuilder.buildCover(cover, scene(), 7L, "moje_pisnicka.mp3")
        assertEquals("moje_pisnicka.mp3", wf.inputs(Yue2MusicBuilder.N_AUDIO).getString("audio"))
        assertEquals(
            Yue2MusicBuilder.SHEETSAGE,
            wf.inputs(Yue2MusicBuilder.N_ENCODER).getString("audio_encoder_name"),
        )
    }

    /**
     * Nápověda uzlu říká „use the matching mode" — přepisovač a generující
     * uzel si rozumí jen přes stejnou hodnotu. Dva různé režimy = noty, se
     * kterými model neumí pracovat.
     */
    @Test
    fun `oba uzly maji stejny rezim`() {
        listOf(false, true).forEach { akordy ->
            val wf = Yue2MusicBuilder.buildCover(cover, scene(akordy), 1L, "a.mp3")
            val prepis = wf.inputs(Yue2MusicBuilder.N_PREPIS).getString("mode")
            val hudba = wf.inputs(Yue2MusicBuilder.N_MUSIC).getString("mode")
            assertEquals(prepis, hudba)
            assertEquals(if (akordy) "full" else "melody", prepis)
        }
    }

    /** Výchozí stav je jen melodie — tak to doporučuje oficiální předloha. */
    @Test
    fun `vychozi je jen melodie`() {
        assertEquals("melody", scene().predlohaMode)
    }

    @Test
    fun `zadani se dosadi a delku urcuje model`() {
        val wf = Yue2MusicBuilder.buildCover(cover, scene(), 42L, "a.mp3")
        val m = wf.inputs(Yue2MusicBuilder.N_MUSIC)
        assertEquals("80s synthpop, female vocal", m.getString("style"))
        assertEquals("[Verse]\nnight drive", m.getString("lyrics"))
        assertEquals(42L, m.getLong("seed"))
        assertEquals(180.0, m.getDouble("max_duration"), 0.001)
        assertEquals(42L, wf.inputs(Yue2MusicBuilder.N_SAMPLER).getLong("seed"))
        // Délka latentu visí na výstupu modelu, ne na čísle ze zadání.
        val seconds = wf.inputs(Yue2MusicBuilder.N_LATENT).getJSONArray("seconds")
        assertEquals(Yue2MusicBuilder.N_MUSIC, seconds.getString(0))
        assertEquals(1, seconds.getInt(1))
        // Noty jdou z přepisovače, ne z YuE2GenerateABC — ten tu vůbec není.
        assertEquals(Yue2MusicBuilder.N_PREPIS, m.getJSONArray("abc").getString(0))
        assertFalse(wf.has(Yue2MusicBuilder.N_ABC))
    }

    /** Vzorkování zůstává z předlohy — stejné hodnoty jako u nové skladby. */
    @Test
    fun `vzorkovani zustava z predlohy`() {
        val wf = Yue2MusicBuilder.buildCover(cover, scene(), 1L, "a.mp3")
        val s = wf.inputs(Yue2MusicBuilder.N_SAMPLER)
        assertEquals(Yue2MusicBuilder.STEPS, s.getInt("steps"))
        assertEquals(1.0, s.getDouble("cfg"), 0.001)
        assertEquals("dpm_2", s.getString("sampler_name"))
        assertEquals("sgm_uniform", s.getString("scheduler"))
    }

    @Test
    fun `bez nahravky se karta nespusti`() {
        assertNotNull(musicProblem(scene()))
        // Nová skladba nahrávku nepotřebuje.
        assertNull(musicProblem(scene().copy(rezim = MusicRezim.NOVA)))
        // Ani ACE-Step: ten předělávat neumí, volba se u něj vůbec neukazuje.
        assertNull(musicProblem(scene().copy(motor = MusicMotor.ACE)))
        assertFalse(scene().copy(motor = MusicMotor.ACE).predelava)
    }

    /** Fáze: přepis patří do přípravy, ne do vzorkování. */
    @Test
    fun `faze pokryvaji uzly prepisu`() {
        assertEquals(Stage.MODELS, Yue2MusicBuilder.stageForClass("AudioEncoderLoader"))
        assertEquals(Stage.REFERENCES, Yue2MusicBuilder.stageForClass("SheetSage2AudioToABC"))
        assertEquals(Stage.REFERENCES, Yue2MusicBuilder.stageForClass("LoadAudio"))
        val (od, doo) = Yue2MusicBuilder.rangeForClass("SheetSage2AudioToABC")
        assertTrue(od < doo)
        assertTrue(doo <= 1f)
    }

    /** Plán not se u předělávání neuplatňuje — noty už přišly z nahrávky. */
    @Test
    fun `plan not se do predelavani neplete`() {
        val wf = Yue2MusicBuilder.buildCover(
            cover, scene().copy(plan = MusicPlan.NONE), 1L, "a.mp3",
        )
        assertEquals("melody", wf.inputs(Yue2MusicBuilder.N_MUSIC).getString("mode"))
        assertFalse(wf.has(Yue2MusicBuilder.N_ABC))
    }
}
