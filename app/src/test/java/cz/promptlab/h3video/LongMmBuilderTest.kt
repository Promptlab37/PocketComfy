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
     * `MiniMaxH3EasySegmentRender` bere seed jen do 32 bitů, zatímco
     * `RandomNoise` u prvního záběru snese 64. Appka losuje do 10^15, takže
     * první záběr prošel a navázání spadlo na „Value … bigger than max of
     * 4294967295" (22. 9. 2026, verze 3.87).
     */
    @Test fun `seed navazani se vejde do rozsahu uzlu`() {
        val seedy = listOf(0L, 1L, 4_294_967_295L, 4_294_967_296L, 999_999_999_999_999L)
        for (seed in seedy) {
            val v = wf(seed).inputs(LongMmBuilder.N_SEED_DALSI).getLong("seed")
            assertTrue("seed $seed -> $v mimo rozsah", v in 0L..LongMmBuilder.SEED_MAX)
        }
        // Malý seed se nesmí měnit — jinak by opakování se stejným seedem
        // vracelo něco jiného, než co uživatel viděl minule.
        assertEquals(12345L, wf(12345L).inputs(LongMmBuilder.N_SEED_DALSI).getLong("seed"))
        // A stejné zadání musí dát pořád stejný seed.
        assertEquals(
            wf(999_999_999_999_999L).inputs(LongMmBuilder.N_SEED_DALSI).getLong("seed"),
            wf(999_999_999_999_999L).inputs(LongMmBuilder.N_SEED_DALSI).getLong("seed"),
        )
    }

    private fun wf(seed: Long) = LongMmBuilder.buildDalsi(
        dalsi, scena(rezim = LongMmRezim.NAVAZANI), seed, "celek.mp4",
    )

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

    /**
     * Latent a zdrojové video musí patřit k témuž řetězu. Když se rozejdou,
     * slepovač přilepí nový záběr k cizímu videu — 22. 9. 2026 se takhle loď
     * přilepila k ženě v kavárně.
     */
    @Test fun `latent z ciziho retezu karta nepusti`() {
        assertNull(longMmProblem(scena(
            rezim = LongMmRezim.NAVAZANI, nazev = "lod", latent = "lod_00002.h3latent.safetensors",
        )))
        assertNotNull(longMmProblem(scena(
            rezim = LongMmRezim.NAVAZANI, nazev = "lod", latent = "kavarna_00001.h3latent.safetensors",
        )))
        // Podobný začátek nestačí, oddělovač musí sedět taky.
        assertNotNull(longMmProblem(scena(
            rezim = LongMmRezim.NAVAZANI, nazev = "lod", latent = "lodnice_00001.h3latent.safetensors",
        )))
        // Jméno se před porovnáním ořezává stejně jako při ukládání.
        assertNull(longMmProblem(scena(
            rezim = LongMmRezim.NAVAZANI, nazev = "moje loď", latent = "moje_loď_00001.h3latent.safetensors",
        )))
        // U prvního záběru se latent nevybírá, takže se ani neposuzuje.
        assertNull(longMmProblem(scena(nazev = "lod", latent = "kavarna_00001.h3latent.safetensors")))
    }

    /**
     * Realistická LoRA se věší za turbo a všichni, kdo brali model z turba,
     * musí přejít na ni. Kdyby se přepojil jen někdo, vzorkovač a plánovač
     * kroků by jely na jiných vahách.
     */
    @Test fun `realisticka lora se zavesi za turbo a prepoji odberatele`() {
        for (navazani in listOf(false, true)) {
            val sc = scena(rezim = if (navazani) LongMmRezim.NAVAZANI else LongMmRezim.PRVNI)
                .copy(realismus = true, realismusSila = 0.6f)
            val wf = if (navazani) LongMmBuilder.buildDalsi(dalsi, sc, 1L, "c.mp4")
            else LongMmBuilder.buildPrvni(prvni, sc, 1L, emptyList())
            val turbo = if (navazani) LongMmBuilder.N_TURBO_DALSI else LongMmBuilder.N_TURBO
            val lora = wf.inputs(LongMmBuilder.N_REALISMUS)
            assertEquals(LongMmBuilder.LORA_REALISMUS, lora.getString("lora_name"))
            assertEquals(0.6, lora.getDouble("strength"), 0.001)
            assertEquals(turbo, lora.getJSONArray("model").getString(0))
            // Z turba už nesmí brát model nikdo jiný než ta nová LoRA.
            wf.keys().forEach { id ->
                if (id == LongMmBuilder.N_REALISMUS) return@forEach
                val m = wf.inputs(id).optJSONArray("model") ?: return@forEach
                assertFalse("$id bere model rovnou z turba", m.optString(0) == turbo)
            }
            zkontrolujOdkazy(wf)
        }
    }

    /** Vypnutá LoRA nesmí v grafu zanechat nic. */
    @Test fun `bez realisticke lory zustava graf beze zmeny`() {
        val wf = LongMmBuilder.buildPrvni(prvni, scena(), 1L, emptyList())
        assertFalse(wf.has(LongMmBuilder.N_REALISMUS))
        zkontrolujOdkazy(wf)
    }

    /**
     * Reference v navázání jsou **odchylka od autora** — v jeho předloze uzel
     * `LoadImage` leží nezapojený. Vypnutá volba proto musí nechat graf přesně
     * takový, jaký ho má on.
     */
    @Test fun `reference v navazani jen kdyz jsou zapnute`() {
        val jmena = listOf("a.png", "b.png")
        val vypnuto = LongMmBuilder.buildDalsi(
            dalsi, scena(rezim = LongMmRezim.NAVAZANI, referenci = 2), 1L, "c.mp4", jmena,
        )
        assertFalse(vypnuto.has(LongMmBuilder.N_MEDIA))
        assertFalse(vypnuto.inputs(LongMmBuilder.N_USEK).has("media"))
        LongMmBuilder.N_REFERENCE.forEach { assertFalse(vypnuto.has(it)) }
        zkontrolujOdkazy(vypnuto)

        val zapnuto = LongMmBuilder.buildDalsi(
            dalsi,
            scena(rezim = LongMmRezim.NAVAZANI, referenci = 2).copy(referenceVNavazani = true),
            1L, "c.mp4", jmena,
        )
        val media = zapnuto.inputs(LongMmBuilder.N_MEDIA)
        assertEquals(2, media.getInt("image_count"))
        assertEquals("a.png", zapnuto.inputs(LongMmBuilder.N_REFERENCE[0]).getString("image"))
        assertEquals("b.png", zapnuto.inputs(LongMmBuilder.N_REFERENCE[1]).getString("image"))
        assertEquals(
            LongMmBuilder.N_MEDIA,
            zapnuto.inputs(LongMmBuilder.N_USEK).getJSONArray("media").getString(0),
        )
        // Načítače navíc se nezakládají.
        assertFalse(zapnuto.has(LongMmBuilder.N_REFERENCE[2]))
        zkontrolujOdkazy(zapnuto)
    }

    /**
     * Vypnutá záplata pozornosti musí z grafu zmizet a ten, kdo od ní bral
     * model, musí dostat rovnou její zdroj. Odkaz na smazaný uzel server
     * odmítne ještě před vzorkováním.
     */
    @Test fun `vypnuta sage zmizi a retez se spoji`() {
        for (navazani in listOf(false, true)) {
            fun graf(sage: Boolean): JSONObject {
                val sc = scena(rezim = if (navazani) LongMmRezim.NAVAZANI else LongMmRezim.PRVNI)
                    .copy(sage = sage)
                return if (navazani) LongMmBuilder.buildDalsi(dalsi, sc, 1L, "c.mp4")
                else LongMmBuilder.buildPrvni(prvni, sc, 1L, emptyList())
            }
            val turbo = if (navazani) LongMmBuilder.N_TURBO_DALSI else LongMmBuilder.N_TURBO

            val zapnuto = graf(true)
            assertTrue(zapnuto.has(LongMmBuilder.N_SAGE))
            val zdroj = zapnuto.inputs(LongMmBuilder.N_SAGE).getJSONArray("model").getString(0)
            assertEquals(
                LongMmBuilder.N_SAGE,
                zapnuto.inputs(turbo).getJSONArray("model").getString(0),
            )
            zkontrolujOdkazy(zapnuto)

            val vypnuto = graf(false)
            assertFalse(vypnuto.has(LongMmBuilder.N_SAGE))
            // Turbo teď bere model rovnou z toho, co krmilo záplatu.
            assertEquals(zdroj, vypnuto.inputs(turbo).getJSONArray("model").getString(0))
            zkontrolujOdkazy(vypnuto)
        }
    }

    /**
     * Sestavy se liší jen modelem, LoRA, její silou a počtem kroků. Musí se
     * dosadit do obou předloh a do obou režimů stejně.
     */
    @Test fun `sestava dosadi model, loru, silu i kroky`() {
        for (m in cz.promptlab.h3video.data.LongMmModel.entries) {
            val prvniWf = LongMmBuilder.buildPrvni(
                prvni, scena().copy(model = m, kroky = m.kroky), 1L, emptyList(),
            )
            assertEquals(m.unet, prvniWf.inputs(LongMmBuilder.N_UNET).getString("unet_name"))
            assertEquals(m.lora, prvniWf.inputs(LongMmBuilder.N_TURBO).getString("lora_name"))
            assertEquals(
                m.silaPrvni.toDouble(),
                prvniWf.inputs(LongMmBuilder.N_TURBO).getDouble("strength"), 0.001,
            )
            assertEquals(m.kroky, prvniWf.inputs(LongMmBuilder.N_KROKY).getInt("steps"))
            zkontrolujOdkazy(prvniWf)

            val dalsiWf = LongMmBuilder.buildDalsi(
                dalsi,
                scena(rezim = LongMmRezim.NAVAZANI).copy(model = m, kroky = m.kroky),
                1L, "c.mp4",
            )
            assertEquals(m.unet, dalsiWf.inputs(LongMmBuilder.N_UNET).getString("unet_name"))
            assertEquals(m.lora, dalsiWf.inputs(LongMmBuilder.N_TURBO_DALSI).getString("lora_name"))
            assertEquals(
                m.silaDalsi.toDouble(),
                dalsiWf.inputs(LongMmBuilder.N_TURBO_DALSI).getDouble("strength"), 0.001,
            )
            assertEquals(m.kroky, dalsiWf.inputs(LongMmBuilder.N_KROKY_DALSI).getInt("steps"))
            zkontrolujOdkazy(dalsiWf)
        }
    }

    /** Vlastní síla LoRA přebije tu ze sestavy; záporná znamená „vezmi ze sestavy". */
    @Test fun `vlastni sila lory prebije sestavu`() {
        val sc = scena().copy(model = cz.promptlab.h3video.data.LongMmModel.TURBO, loraSila = 0.45f)
        val wf = LongMmBuilder.buildPrvni(prvni, sc, 1L, emptyList())
        assertEquals(0.45, wf.inputs(LongMmBuilder.N_TURBO).getDouble("strength"), 0.001)

        val vychozi = scena().copy(loraSila = -1f)
        val wf2 = LongMmBuilder.buildPrvni(prvni, vychozi, 1L, emptyList())
        assertEquals(
            cz.promptlab.h3video.data.LongMmModel.TURBO.silaPrvni.toDouble(),
            wf2.inputs(LongMmBuilder.N_TURBO).getDouble("strength"), 0.001,
        )
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
