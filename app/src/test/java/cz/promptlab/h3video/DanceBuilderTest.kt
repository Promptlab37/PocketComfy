package cz.promptlab.h3video

import cz.promptlab.h3video.comfy.DanceBuilder
import cz.promptlab.h3video.comfy.Stage
import cz.promptlab.h3video.data.DanceRozsah
import cz.promptlab.h3video.data.DanceScene
import cz.promptlab.h3video.data.DanceStyl
import cz.promptlab.h3video.data.danceProblem
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Karta **Dance** (Wan-Dancer 14B).
 *
 * Předloha je narovnaná z oficiálního subgrafu, takže testy hlídají hlavně to,
 * co by se při tom narovnávání dalo tiše zkazit: že se dosazuje do správných
 * uzlů, že délka hudby a počet úseků drží spolu, a že v předloze nezůstalo
 * zadání z minulého běhu.
 */
class DanceBuilderTest {

    private val sablona: String =
        File("src/main/res/raw/workflow_dance_wan.json").readText()

    private fun JSONObject.inputs(node: String): JSONObject =
        getJSONObject(node).getJSONObject("inputs")

    private fun scena(
        sekundy: Int = 5,
        styl: DanceStyl = DanceStyl.LATINA,
        rozsah: DanceRozsah = DanceRozsah.STREDNI,
        popis: String = "",
    ) = DanceScene(
        fotka = File("a.png"), hudba = File("b.mp3"), hudbaSekund = 60f,
        styl = styl, rozsah = rozsah, sekundy = sekundy, popis = popis,
    )

    @Test fun `dosadi se fotka, hudba, seed a zadani`() {
        val wf = DanceBuilder.build(sablona, scena(popis = "na pódiu"), 42L, "clovek.png", "song.mp3")
        assertEquals("clovek.png", wf.inputs(DanceBuilder.N_FOTKA).getString("image"))
        assertEquals("song.mp3", wf.inputs(DanceBuilder.N_HUDBA).getString("audio"))
        assertEquals(42L, wf.inputs(DanceBuilder.N_SEED).getLong("noise_seed"))
        assertEquals("na pódiu", wf.inputs(DanceBuilder.N_POPIS).getString("value"))
    }

    /**
     * Délka se řídí dvěma místy naráz — ořezem hudby a počtem úseků. Kdyby se
     * rozešly, druhá fáze by skládala video z jiného počtu kusů, než na kolik
     * je nastříhaná hudba.
     */
    @Test fun `delka hudby a pocet useku drzi spolu`() {
        for (s in DanceScene.DELKY) {
            val wf = DanceBuilder.build(sablona, scena(sekundy = s), 1L, "a.png", "b.mp3")
            assertEquals(
                "délka $s s",
                s.toDouble(),
                wf.inputs(DanceBuilder.N_DELKA).getDouble("duration"),
                1e-6,
            )
            assertEquals(
                "úseky pro $s s",
                s / DanceScene.SEKUND_NA_USEK,
                wf.inputs(DanceBuilder.N_USEKY).getInt("num_segments"),
            )
        }
    }

    /** Styl i rozsah jdou do modelu jako slovo, na kterém je vycvičený. */
    @Test fun `styl a rozsah se dosadi do sablony promptu`() {
        val wf = DanceBuilder.build(
            sablona, scena(styl = DanceStyl.KPOP, rozsah = DanceRozsah.MAXIMALNI),
            1L, "a.png", "b.mp3",
        )
        assertEquals(DanceStyl.KPOP.zadani, wf.inputs(DanceBuilder.N_STYL).getString("replace"))
        assertEquals(DanceRozsah.MAXIMALNI.zadani, wf.inputs(DanceBuilder.N_ROZSAH).getString("replace"))
        // Šablona musí mít značku, do které se to dosazuje — bez ní by se
        // styl v promptu neobjevil.
        assertTrue(wf.inputs(DanceBuilder.N_STYL).getString("string").contains("<dance style>"))
        assertEquals("<dance style>", wf.inputs(DanceBuilder.N_STYL).getString("find"))
    }

    /** Každý styl i rozsah musí mít neprázdné zadání pro model. */
    @Test fun `kazda volba ma co poslat modelu`() {
        DanceStyl.entries.forEach { assertTrue(it.name, it.zadani.isNotBlank()) }
        DanceRozsah.entries.forEach { assertTrue(it.name, it.zadani.isNotBlank()) }
        assertEquals(DanceStyl.entries.size, DanceStyl.entries.map { it.zadani }.toSet().size)
        assertEquals(DanceRozsah.entries.size, DanceRozsah.entries.map { it.zadani }.toSet().size)
    }

    @Test fun `predloha nenese zadani predchoziho behu`() {
        val p = JSONObject(sablona)
        assertEquals("", p.inputs(DanceBuilder.N_FOTKA).getString("image"))
        assertEquals("", p.inputs(DanceBuilder.N_HUDBA).getString("audio"))
        assertEquals("", p.inputs(DanceBuilder.N_POPIS).getString("value"))
    }

    /** Model i obě fáze musí v grafu opravdu být — jinak by běh tiše jel jen půlku. */
    @Test fun `graf ma obe faze a spravne modely`() {
        val wf = JSONObject(sablona)
        val unety = wf.keys().asSequence()
            .filter { wf.getJSONObject(it).getString("class_type") == "UNETLoader" }
            .map { wf.inputs(it).getString("unet_name") }.toSet()
        assertEquals(
            setOf(
                "wan2.2_dancer_14b_global_fp8_scaled.safetensors",
                "wan2.2_dancer_14b_local_fp8_scaled.safetensors",
            ),
            unety,
        )
        // Dva vzorkovače = dvě fáze.
        val vzorkovace = wf.keys().asSequence().count {
            wf.getJSONObject(it).getString("class_type") in setOf("SamplerCustom", "SamplerCustomAdvanced")
        }
        assertEquals(2, vzorkovace)
        // A dva výstupy: náhled z první fáze a hotové video z druhé.
        assertEquals("SaveVideo", wf.getJSONObject(DanceBuilder.N_ULOZ).getString("class_type"))
        assertEquals("SaveVideo", wf.getJSONObject(DanceBuilder.N_ULOZ_NAHLED).getString("class_type"))
    }

    /**
     * Při narovnávání subgrafu se vyhazovaly uzly z cizích balíků, které jen
     * skládaly ovládání v okně. Když se některý vrátí, je to známka toho, že
     * se předloha přepsala surovým exportem.
     */
    @Test fun `v predloze nezustaly uzly jen pro ovladani v okne`() {
        val wf = JSONObject(sablona)
        val tridy = wf.keys().asSequence()
            .map { wf.getJSONObject(it).getString("class_type") }.toSet()
        listOf("CustomCombo", "ComfySwitchNode", "ComfyMathExpression", "MarkdownNote", "PreviewAny")
            .forEach { assertFalse("uzel $it se vrátil do předlohy", it in tridy) }
    }

    @Test fun `bez visicich odkazu`() {
        val wf = DanceBuilder.build(sablona, scena(), 1L, "a.png", "b.mp3")
        wf.keys().asSequence().toList().forEach { id ->
            val ins = wf.inputs(id)
            ins.keys().asSequence().toList().forEach { k ->
                val v = ins.opt(k)
                if (v is JSONArray && v.length() == 2 && v.opt(0) is String) {
                    assertTrue("uzel $id → ${v.getString(0)}", wf.has(v.getString(0)))
                }
            }
        }
    }

    @Test fun `karta nepusti beh bez fotky, hudby a na kratkou skladbu`() {
        assertNull(danceProblem(scena()))
        assertNotNull(danceProblem(scena().copy(fotka = null)))
        assertNotNull(danceProblem(scena().copy(hudba = null)))
        // Hudba kratší než video by ve druhé fázi nestačila na všechny úseky.
        assertNotNull(danceProblem(scena(sekundy = 30).copy(hudbaSekund = 10f)))
    }

    @Test fun `vsechny tridy predlohy maji fazi`() {
        val wf = JSONObject(sablona)
        wf.keys().forEach { id ->
            val cls = wf.getJSONObject(id).getString("class_type")
            val faze = DanceBuilder.stageForClass(cls)
            // Do vzorkování smí spadnout jen to, co opravdu vzorkuje.
            if (faze == Stage.SAMPLING) {
                assertTrue("$cls nemá vlastní fázi", DanceBuilder.reportsSteps(cls))
            }
        }
    }
}
