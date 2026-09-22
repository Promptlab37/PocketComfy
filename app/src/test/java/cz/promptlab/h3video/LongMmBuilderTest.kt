package cz.promptlab.h3video

import cz.promptlab.h3video.comfy.LongMmBuilder
import cz.promptlab.h3video.comfy.Stage
import cz.promptlab.h3video.data.LongMmPomer
import cz.promptlab.h3video.data.LongMmRef
import cz.promptlab.h3video.data.LongMmRezim
import cz.promptlab.h3video.data.LongMmRozliseni
import cz.promptlab.h3video.data.LongMmScene
import cz.promptlab.h3video.data.longMmProblem
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Karta **Long MiniMax** — navazující záběry přes latent.
 *
 * Testy hlídají to, co by se dalo tiše zkazit: že se dosazuje do správných
 * uzlů, že po odebrání referencí v grafu nezbydou odkazy na smazané uzly,
 * že počet délek úseků sedí na počet částí zadání (jinak uzel celý běh
 * odmítne) a že v předloze nezůstalo zadání z minulého běhu.
 */
class LongMmBuilderTest {

    private val prvni: String =
        File("src/main/res/raw/workflow_longmm_start.json").readText()
    private val dalsi: String =
        File("src/main/res/raw/workflow_longmm_dalsi.json").readText()

    private fun JSONObject.inputs(node: String): JSONObject =
        getJSONObject(node).getJSONObject("inputs")

    private fun scena(
        rezim: LongMmRezim = LongMmRezim.PRVNI,
        prompt: String = "muž u okna",
        sekundy: Int = 5,
        referenci: Int = 0,
        latent: String = "kuchyne_00001.h3latent.safetensors",
        nazev: String = "kuchyne",
    ) = LongMmScene(
        rezim = rezim, prompt = prompt, sekundy = sekundy, nazev = nazev, latent = latent,
        reference = (1..referenci).map { LongMmRef(File("ref$it.png")) },
        zdroj = File("predchozi.mp4"),
    )

    // ------------------------------------------------------------ první záběr

    @Test fun `prvni zaber dosadi zadani, platno, seed a nazev latentu`() {
        val wf = LongMmBuilder.buildPrvni(
            prvni,
            scena(prompt = "muž u okna", sekundy = 8).copy(
                rozliseni = LongMmRozliseni.R720, pomer = LongMmPomer.NAVYSKU,
            ),
            42L, emptyList(),
        )
        val zadani = wf.inputs(LongMmBuilder.N_ZADANI)
        assertEquals("muž u okna", zadani.getString("prompt"))
        assertEquals("720P", zadani.getString("resolution"))
        assertEquals("9:16", zadani.getString("aspect_ratio"))
        assertEquals(8.0, zadani.getDouble("seconds"), 0.001)
        assertEquals(42L, wf.inputs(LongMmBuilder.N_SEED).getLong("noise_seed"))
        assertEquals("kuchyne", wf.inputs(LongMmBuilder.N_LATENT_ULOZ).getString("filename_prefix"))
    }

    /**
     * Bez referencí musí zmizet sběrač i všechny načítače obrázků — a hlavně
     * odkaz na sběrač ze zadání. Odkaz na neexistující uzel server odmítne
     * ještě před vzorkováním.
     */
    @Test fun `bez referenci nezbyde odkaz na smazany uzel`() {
        val wf = LongMmBuilder.buildPrvni(prvni, scena(), 1L, emptyList())
        assertFalse(wf.has(LongMmBuilder.N_MEDIA))
        LongMmBuilder.N_REFERENCE.forEach { assertFalse(wf.has(it)) }
        assertFalse(wf.inputs(LongMmBuilder.N_ZADANI).has("media"))
        zkontrolujOdkazy(wf)
    }

    @Test fun `pouzije se jen tolik nacitacu, kolik je fotek`() {
        for (pocet in 1..LongMmScene.MAX_REFERENCI) {
            val jmena = (1..pocet).map { "ref$it.png" }
            val wf = LongMmBuilder.buildPrvni(prvni, scena(referenci = pocet), 1L, jmena)
            assertEquals(pocet, wf.inputs(LongMmBuilder.N_MEDIA).getInt("image_count"))
            LongMmBuilder.N_REFERENCE.forEachIndexed { i, uzel ->
                if (i < pocet) {
                    assertEquals(jmena[i], wf.inputs(uzel).getString("image"))
                    assertTrue(wf.inputs(LongMmBuilder.N_MEDIA).has("image_${i + 1}"))
                } else {
                    assertFalse("uzel $uzel měl zmizet", wf.has(uzel))
                    assertFalse(wf.inputs(LongMmBuilder.N_MEDIA).has("image_${i + 1}"))
                }
            }
            zkontrolujOdkazy(wf)
        }
    }

    @Test fun `víc fotek než karta dovolí se zahodí`() {
        val jmena = (1..9).map { "ref$it.png" }
        val wf = LongMmBuilder.buildPrvni(prvni, scena(referenci = 9), 1L, jmena)
        assertEquals(
            LongMmScene.MAX_REFERENCI,
            wf.inputs(LongMmBuilder.N_MEDIA).getInt("image_count"),
        )
        zkontrolujOdkazy(wf)
    }

    // --------------------------------------------------------------- navázání

    @Test fun `navazani dosadi video, latent, zadani a seed`() {
        val wf = LongMmBuilder.buildDalsi(
            dalsi, scena(rezim = LongMmRezim.NAVAZANI, prompt = "přejde ke stolu"), 7L, "celek.mp4",
        )
        assertEquals("celek.mp4", wf.inputs(LongMmBuilder.N_ZDROJ).getString("file"))
        assertEquals(
            "kuchyne_00001.h3latent.safetensors",
            wf.inputs(LongMmBuilder.N_LATENT_NACTI).getString("latent_file"),
        )
        assertEquals("přejde ke stolu", wf.inputs(LongMmBuilder.N_USEK).getString("prompt"))
        assertEquals(7L, wf.inputs(LongMmBuilder.N_SEED_DALSI).getLong("seed"))
        assertEquals(
            "kuchyne",
            wf.inputs(LongMmBuilder.N_LATENT_ULOZ_DALSI).getString("filename_prefix"),
        )
        zkontrolujOdkazy(wf)
    }

    /**
     * Uzel porovnává počet délek s počtem částí zadání a při nesouladu celý
     * běh odmítne. Zadání se dělí na samostatných řádcích s `---`.
     */
    @Test fun `pocet delek useku sedi na pocet casti zadani`() {
        val zadani = mapOf(
            "jeden záběr" to 1,
            "první\n---\ndruhý" to 2,
            "a\n---\nb\n---\nc" to 3,
            "pomlčka - uvnitř věty nedělí" to 1,
        )
        for ((prompt, ocekavano) in zadani) {
            assertEquals(ocekavano, LongMmBuilder.useku(prompt))
            val wf = LongMmBuilder.buildDalsi(
                dalsi, scena(rezim = LongMmRezim.NAVAZANI, prompt = prompt, sekundy = 10),
                1L, "celek.mp4",
            )
            val delky = wf.inputs(LongMmBuilder.N_USEK).getString("segment_seconds").split(",")
            assertEquals(ocekavano, delky.size)
            delky.forEach { assertEquals("10", it) }
        }
    }

    @Test fun `vodítko i překryv drží hodnoty z předlohy`() {
        val wf = LongMmBuilder.buildDalsi(dalsi, scena(rezim = LongMmRezim.NAVAZANI), 1L, "c.mp4")
        assertEquals(
            LongMmScene.VODITKO_S,
            wf.inputs(LongMmBuilder.N_VODITKO).getDouble("seconds"), 0.001,
        )
        assertEquals(
            LongMmScene.KONTEXT_SNIMKU,
            wf.inputs(LongMmBuilder.N_SLEPENI).getInt("overlap_frames"),
        )
    }

    /** Výsledkem navázání je jediné uložené video — slepený celek. */
    @Test fun `navazani uklada jedno video`() {
        val wf = JSONObject(dalsi)
        val ulozeni = wf.keys().asSequence()
            .filter { wf.getJSONObject(it).getString("class_type") == "SaveVideo" }.toList()
        assertEquals(1, ulozeni.size)
    }

    // ----------------------------------------------------------------- ostatní

    @Test fun `v predloze nezustalo zadani z minuleho behu`() {
        for (sablona in listOf(prvni, dalsi)) {
            val wf = JSONObject(sablona)
            wf.keys().forEach { id ->
                val ins = wf.getJSONObject(id).getJSONObject("inputs")
                listOf("prompt", "image", "file", "latent_file").forEach { pole ->
                    if (ins.has(pole) && ins.get(pole) is String) {
                        assertEquals("$id.$pole", "", ins.getString(pole))
                    }
                }
            }
        }
    }

    /**
     * Stejný ořez, jaký si dělá sám uzel: mezery na podtržítka, propustí
     * písmena a číslice (Unicode, takže diakritika projde) plus `-` a `_`.
     */
    @Test fun `nazev latentu propusti jen to, co uzel prijme`() {
        assertEquals("moje_scéna", LongMmBuilder.nazevLatentu(scena(nazev = "moje scéna")))
        assertEquals("a-b_1", LongMmBuilder.nazevLatentu(scena(nazev = "a-b_1")))
        assertEquals("zaber", LongMmBuilder.nazevLatentu(scena(nazev = "?!/")))
    }

    @Test fun `karta rekne, co chybi`() {
        assertNotNull(longMmProblem(scena(prompt = "")))
        assertNotNull(longMmProblem(scena(nazev = "")))
        assertNull(longMmProblem(scena()))
        // Navázání potřebuje obojí: zdrojové video i latent.
        assertNotNull(longMmProblem(scena(rezim = LongMmRezim.NAVAZANI, latent = "")))
        assertNotNull(
            longMmProblem(scena(rezim = LongMmRezim.NAVAZANI).copy(zdroj = null))
        )
        assertNull(longMmProblem(scena(rezim = LongMmRezim.NAVAZANI)))
    }

    @Test fun `prubeh zna vzorkovaci uzly obou predloh`() {
        assertEquals(Stage.SAMPLING, LongMmBuilder.stageForClass("SamplerCustomAdvanced"))
        assertEquals(
            Stage.SAMPLING,
            LongMmBuilder.stageForClass("MiniMaxH3EasySegmentRender_SatoDive"),
        )
        assertTrue(LongMmBuilder.reportsSteps("SamplerCustomAdvanced"))
        assertTrue(LongMmBuilder.reportsSteps("MiniMaxH3EasySegmentRender_SatoDive"))
        assertFalse(LongMmBuilder.reportsSteps("VAEDecode"))
        assertEquals(Stage.MODELS, LongMmBuilder.stageForClass("UNETLoader"))
    }

    /** Každý odkaz v grafu musí mířit na uzel, který v něm opravdu je. */
    private fun zkontrolujOdkazy(wf: JSONObject) {
        wf.keys().forEach { id ->
            val ins = wf.getJSONObject(id).getJSONObject("inputs")
            ins.keys().forEach { pole ->
                val v = ins.get(pole)
                if (v is org.json.JSONArray && v.length() == 2 && v.get(0) is String) {
                    assertTrue(
                        "$id.$pole ukazuje na chybějící uzel ${v.getString(0)}",
                        wf.has(v.getString(0)),
                    )
                }
            }
        }
    }
}
