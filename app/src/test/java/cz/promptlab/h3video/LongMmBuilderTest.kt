package cz.promptlab.h3video

import cz.promptlab.h3video.comfy.LongMmBuilder
import cz.promptlab.h3video.comfy.Stage
import cz.promptlab.h3video.data.LongMmPomer
import cz.promptlab.h3video.data.LongMmRef
import cz.promptlab.h3video.data.LongMmPozornost
import cz.promptlab.h3video.data.LongMmRezim
import cz.promptlab.h3video.data.LongMmRozliseni
import cz.promptlab.h3video.data.LongMmScene
import cz.promptlab.h3video.data.longMmHints
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
     * Do řetězu smí jít **jen ta** pozornost, kterou si uživatel zvolil.
     * Nevybraná musí z grafu zmizet a ten, kdo od ní bral model, musí dostat
     * rovnou její zdroj — odkaz na smazaný uzel server odmítne ještě před
     * vzorkováním. Volba „nechat na serveru" vyhodí obě.
     */
    @Test fun `v grafu zustane jen zvolena pozornost`() {
        for (navazani in listOf(false, true)) {
            fun graf(p: LongMmPozornost): JSONObject {
                val sc = scena(rezim = if (navazani) LongMmRezim.NAVAZANI else LongMmRezim.PRVNI)
                    .copy(pozornost = p)
                return if (navazani) LongMmBuilder.buildDalsi(dalsi, sc, 1L, "c.mp4")
                else LongMmBuilder.buildPrvni(prvni, sc, 1L, emptyList())
            }
            val turbo = if (navazani) LongMmBuilder.N_TURBO_DALSI else LongMmBuilder.N_TURBO
            val S = LongMmBuilder.N_SAGE
            val K = LongMmBuilder.N_POZORNOST

            // Zdroj, který v předloze krmí první uzel v řetězu pozornosti.
            val zdroj = graf(LongMmPozornost.SAGE).inputs(S).getJSONArray("model").getString(0)

            val sage = graf(LongMmPozornost.SAGE)
            assertTrue(sage.has(S))
            assertFalse(sage.has(K))
            assertEquals(S, sage.inputs(turbo).getJSONArray("model").getString(0))
            zkontrolujOdkazy(sage)

            val kitchen = graf(LongMmPozornost.KITCHEN)
            assertFalse(kitchen.has(S))
            assertTrue(kitchen.has(K))
            assertEquals(
                "ModelAttentionBackend",
                kitchen.getJSONObject(K).getString("class_type"),
            )
            // Přesně ta hodnota, kterou uzel nabízí; jiná by skončila
            // hláškou „není v nabídce".
            assertEquals("comfy kitchen attention", kitchen.inputs(K).getString("attention"))
            // Sage byla přemostěna, takže uzel pozornosti visí rovnou na zdroji.
            assertEquals(zdroj, kitchen.inputs(K).getJSONArray("model").getString(0))
            assertEquals(K, kitchen.inputs(turbo).getJSONArray("model").getString(0))
            zkontrolujOdkazy(kitchen)

            val server = graf(LongMmPozornost.SERVER)
            assertFalse(server.has(S))
            assertFalse(server.has(K))
            assertEquals(zdroj, server.inputs(turbo).getJSONArray("model").getString(0))
            zkontrolujOdkazy(server)
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
                m.nacitac,
                prvniWf.getJSONObject(LongMmBuilder.N_TURBO).getString("class_type"),
            )
            assertEquals(
                m.silaPrvni.toDouble(),
                prvniWf.inputs(LongMmBuilder.N_TURBO).getDouble(poleSily(m)), 0.001,
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
                dalsiWf.inputs(LongMmBuilder.N_TURBO_DALSI).getDouble(poleSily(m)), 0.001,
            )
            assertEquals(m.kroky, dalsiWf.inputs(LongMmBuilder.N_KROKY_DALSI).getInt("steps"))
            zkontrolujOdkazy(dalsiWf)
        }
    }

    /**
     * Každý načítač má jiné jméno pro sílu — standardní ComfyUI uzel
     * `strength_model`, uzel balíku `strength`.
     */
    private fun poleSily(m: cz.promptlab.h3video.data.LongMmModel): String =
        if (m.nacitac == "LoraLoaderModelOnly") "strength_model" else "strength"

    /** Vlastní síla LoRA přebije tu ze sestavy; záporná znamená „vezmi ze sestavy". */
    @Test fun `vlastni sila lory prebije sestavu`() {
        val sc = scena().copy(model = cz.promptlab.h3video.data.LongMmModel.TURBO, loraSila = 0.45f)
        val wf = LongMmBuilder.buildPrvni(prvni, sc, 1L, emptyList())
        assertEquals(0.45, wf.inputs(LongMmBuilder.N_TURBO).getDouble("strength"), 0.001)
        assertEquals(
            "MiniMaxH3TurboLoRA",
            wf.getJSONObject(LongMmBuilder.N_TURBO).getString("class_type"),
        )

        val vychozi = scena().copy(loraSila = -1f)
        val wf2 = LongMmBuilder.buildPrvni(prvni, vychozi, 1L, emptyList())
        assertEquals(
            cz.promptlab.h3video.data.LongMmModel.TURBO.silaPrvni.toDouble(),
            wf2.inputs(LongMmBuilder.N_TURBO).getDouble("strength"), 0.001,
        )
    }


    /** Jednoprůchodové sestavy ten uzel do grafu vůbec nedají. */
    @Test fun `jednopruchodove sestavy zustavaji beze zmeny`() {
        for (m in cz.promptlab.h3video.data.LongMmModel.entries.filterNot { it.dvojiPruchod }) {
            val wf = LongMmBuilder.buildPrvni(
                prvni, scena().copy(model = m, kroky = m.kroky), 1L, emptyList(),
            )
            assertFalse(wf.has(LongMmBuilder.N_DVA_PRUCHODY))
            zkontrolujOdkazy(wf)
        }
    }

    /**
     * Nulová síla znamená „bez LoRA" — uzel musí z grafu zmizet a řetěz se
     * spojit. Nechat ho tam s nulou by model stejně obalilo.
     */
    @Test fun `nulova sila lory uzel vyradi`() {
        for (navazani in listOf(false, true)) {
            val sc = scena(rezim = if (navazani) LongMmRezim.NAVAZANI else LongMmRezim.PRVNI)
                .copy(model = cz.promptlab.h3video.data.LongMmModel.EROS, loraSila = 0f)
            val wf = if (navazani) LongMmBuilder.buildDalsi(dalsi, sc, 1L, "c.mp4")
            else LongMmBuilder.buildPrvni(prvni, sc, 1L, emptyList())
            val uzel = if (navazani) LongMmBuilder.N_TURBO_DALSI else LongMmBuilder.N_TURBO
            assertFalse("LoRA uzel měl zmizet", wf.has(uzel))
            zkontrolujOdkazy(wf)
        }
    }

    /** Eros jede na deseti krocích a bez zrychlovací LoRA. */
    @Test fun `eros ma deset kroku`() {
        val m = cz.promptlab.h3video.data.LongMmModel.EROS
        assertEquals(10, m.kroky)
        assertFalse(m.dvojiPruchod)
        // Síla z popisu modelu: konceptové LoRA na něm chtějí 0,2 až 0,6.
        assertTrue(m.silaPrvni in 0.2f..0.6f)
        assertTrue(m.silaDalsi in 0.2f..0.6f)
        // Eros LoRA má klíče `lora_down`/`lora_up` a přímé rozdíly, takže ji
        // uzel balíku načíst neumí — musí jít standardním načítačem ComfyUI.
        assertEquals("LoraLoaderModelOnly", m.nacitac)
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


    /**
     * Dva pruchody jsou oba `SamplerCustomAdvanced`, takze pasmo procent
     * se musi delit podle ID uzlu. Kdyz se delilo podle tridy, dojel
     * ukazatel v prvnim pruchodu na 88 % a pak zacal znovu od 22 %.
     */
    @Test fun `pasmo procent se deli podle uzlu, ne tridy`() {
        val S = "SamplerCustomAdvanced"
        assertTrue(LongMmBuilder.reportsSteps(S))
        assertEquals(Stage.SAMPLING, LongMmBuilder.stageForClass(S))

        // Jeden pruchod: cele pasmo.
        assertEquals(0.22f to 0.88f, LongMmBuilder.rangeForNode("8", S, false))

        // Dva pruchody: navazuji na sebe a neprekryvaji se.
        val prvniPruchod = LongMmBuilder.rangeForNode(LongMmBuilder.N_PRUCHOD1, S, true)
        val druhyPruchod = LongMmBuilder.rangeForNode("8", S, true)
        assertEquals(0.22f, prvniPruchod.first)
        assertEquals(prvniPruchod.second, druhyPruchod.first)
        assertEquals(0.88f, druhyPruchod.second)

        // Dvoupruchodovy beh se pozna podle uzlu prvniho pruchodu.
        val g = LongMmBuilder.buildPrvni(
            prvni,
            scena().copy(
                model = cz.promptlab.h3video.data.LongMmModel.TRIPLUSDVA,
                rozliseni = LongMmRozliseni.R540,
            ),
            1L, emptyList(),
        )
        assertTrue(LongMmBuilder.maDvaPruchody(LongMmBuilder.nodeClasses(g)))
        val jeden = LongMmBuilder.buildPrvni(
            prvni, scena().copy(model = cz.promptlab.h3video.data.LongMmModel.TURBO),
            1L, emptyList(),
        )
        assertFalse(LongMmBuilder.maDvaPruchody(LongMmBuilder.nodeClasses(jeden)))
    }

    /**
     * Skok mezi průchody se musí držet kolem 2–2,5násobku plochy. Při pěti-
     * násobku (360P → 768P) zůstala po roztažení latentu barevná kaše, a
     * pevné 480P by zas u cíle 480P dva průchody úplně zrušilo.
     *
     * Hodnoty jsou megapixely z tabulky balíku (`RESOLUTION_MEGAPIXELS`).
     */
    @Test fun `skok mezi pruchody zustava v rozumnem pomeru`() {
        val mp = mapOf(
            "360P" to 0.2, "416P" to 0.3, "480P" to 0.4,
            "540P" to 0.5, "640P" to 0.7, "720P" to 0.9, "768P" to 1.0,
        )
        // Autorova nabídka pro nízké rozlišení, nic jiného uzel nepřijme.
        val povolene = setOf("360P", "416P", "480P", "540P", "640P")
        for (r in LongMmRozliseni.entries) {
            val nizke = r.nizkeProDvaPruchody
            assertTrue("$r -> $nizke neni v nabidce uzlu", nizke in povolene)
            val pomer = mp.getValue(r.kod) / mp.getValue(nizke)
            assertTrue("$r: skok $pomer x je moc", pomer <= 2.6)
            assertTrue("$r: skok $pomer x je maly", pomer >= 1.9)
        }
        // Dvojice, kterou jede kolega: 0,2 -> 0,5 MP.
        assertEquals("360P", LongMmRozliseni.R540.nizkeProDvaPruchody)
        assertEquals(0.5, mp.getValue(LongMmRozliseni.R540.kod), 1e-9)
    }

    /**
     * Strop pro dva průchody je 0,5 MP. Není to úvaha: 768P dopadlo artefakty
     * a rozsypanou barvou dvakrát po sobě — s prvním průchodem na 360P
     * i na 480P (23. 9. 2026). Kolega dotahuje na 0,5 MP a výš nejde.
     */
    @Test fun `dva pruchody maji strop na pul megapixelu`() {
        assertTrue(LongMmRozliseni.R480.zvladneDvaPruchody)
        assertTrue(LongMmRozliseni.R540.zvladneDvaPruchody)
        assertFalse(LongMmRozliseni.R640.zvladneDvaPruchody)
        assertFalse(LongMmRozliseni.R720.zvladneDvaPruchody)
        assertFalse(LongMmRozliseni.R768.zvladneDvaPruchody)

        // Nad stropem to karta musí říct, pod ním mlčet.
        val nad = longMmHints(
            scena().copy(
                model = cz.promptlab.h3video.data.LongMmModel.TRIPLUSDVA,
                rozliseni = LongMmRozliseni.R768,
            )
        )
        assertTrue(nad.any { it.contains("artefakty") || it.contains("artefacts") })
        val pod = longMmHints(
            scena().copy(
                model = cz.promptlab.h3video.data.LongMmModel.TRIPLUSDVA,
                rozliseni = LongMmRozliseni.R540,
            )
        )
        assertFalse(pod.any { it.contains("artefakty") || it.contains("artefacts") })
        // Jednoprůchodová sestava na 768P se nevaruje, tam to jde.
        val jeden = longMmHints(
            scena().copy(
                model = cz.promptlab.h3video.data.LongMmModel.TURBO,
                rozliseni = LongMmRozliseni.R768,
            )
        )
        assertFalse(jeden.any { it.contains("artefakty") || it.contains("artefacts") })
    }

    /**
     * Dva pruchody musi byt zapojene stejne jako na karte "3 kroky", ktera
     * tenhle postup v appce dela spravne. Klicove je, ze druhy pruchod
     * dostane PEVNE sigmy zacinajici vysoko - ne konec rozvrhu prvniho
     * pruchodu. Uzel MiniMaxH3EasyProgressiveUpscale_SatoDive delal to
     * druhe a vysla z nej barevna kase.
     */
    @Test fun `dva pruchody jsou zapojene jako karta tri kroky`() {
        val m = cz.promptlab.h3video.data.LongMmModel.TRIPLUSDVA
        assertTrue(m.dvojiPruchod)
        val sc = scena().copy(
            model = m, kroky = m.kroky, rozliseni = LongMmRozliseni.R540,
            pomer = cz.promptlab.h3video.data.LongMmPomer.NASIRKU,
        )
        val g = LongMmBuilder.buildPrvni(prvni, sc, 1L, emptyList())

        // Stary uzel uz v grafu nesmi byt vubec.
        assertFalse(g.has(LongMmBuilder.N_DVA_PRUCHODY))

        // Posun sigm z karty "3 kroky".
        val shift = g.inputs(LongMmBuilder.N_SHIFT)
        assertEquals("MiniMaxH3SigmaShift", g.getJSONObject(LongMmBuilder.N_SHIFT).getString("class_type"))
        assertEquals(12.0, shift.getDouble("shift_video"), 1e-9)
        assertEquals(3.0, shift.getDouble("shift_audio"), 1e-9)
        // Rozvrh si bere NEPOSUNUTY model, stejne jako predloha.
        assertEquals(
            LongMmBuilder.N_UNET,
            g.inputs(LongMmBuilder.N_KROKY).getJSONArray("model").getString(0),
        )

        // Nizky kontext je kopie zadani s jinym stupnem rozliseni.
        val nizke = g.inputs(LongMmBuilder.N_NIZKE_ZADANI)
        assertEquals("360P", nizke.getString("resolution"))
        assertEquals(
            g.inputs(LongMmBuilder.N_ZADANI).getString("prompt"),
            nizke.getString("prompt"),
        )

        // Prvni pruchod: CELY rozvrh, latent z nizkeho kontextu.
        val p1 = g.inputs(LongMmBuilder.N_PRUCHOD1)
        assertEquals(LongMmBuilder.N_KROKY, p1.getJSONArray("sigmas").getString(0))
        assertEquals(LongMmBuilder.N_NIZKY_VYSTUP, p1.getJSONArray("latent_image").getString(0))
        assertEquals(LongMmBuilder.N_NIZKY_GUIDER, p1.getJSONArray("guider").getString(0))

        // Zvetseni ucenym 3D modelem na rozmery cile: 540P 16:9 = 960x544.
        val up = g.inputs(LongMmBuilder.N_ZVETSENI)
        assertEquals("target dimensions", up.getString("mode"))
        assertEquals(960, up.getInt("mode.width"))
        assertEquals(544, up.getInt("mode.height"))
        assertEquals(LongMmBuilder.UPSCALER, up.getString("model_name"))
        assertEquals(LongMmBuilder.N_ROZDELENI, up.getJSONArray("latent").getString(0))

        // Zvuk se nezvetsuje, jde rovnou z rozdeleni zpatky do slozeni.
        val slozeni = g.inputs(LongMmBuilder.N_SLOZENI)
        assertEquals(LongMmBuilder.N_ZVETSENI, slozeni.getJSONArray("video_latent").getString(0))
        assertEquals(LongMmBuilder.N_ROZDELENI, slozeni.getJSONArray("audio_latent").getString(0))
        assertEquals(1, slozeni.getJSONArray("audio_latent").getInt(1))

        // DRUHY pruchod: pevne sigmy, ne konec rozvrhu.
        assertEquals(
            LongMmBuilder.SIGMY_ZJEMNENI,
            g.inputs(LongMmBuilder.N_SIGMY_ZJEMNENI).getString("sigmas"),
        )
        val p2 = g.inputs("8")
        assertEquals(LongMmBuilder.N_SIGMY_ZJEMNENI, p2.getJSONArray("sigmas").getString(0))
        assertEquals(LongMmBuilder.N_SLOZENI, p2.getJSONArray("latent_image").getString(0))
        // Ulozeny latent i obraz porad visi na DRUHEM pruchodu.
        assertEquals("8", g.inputs(LongMmBuilder.N_LATENT_ULOZ).getJSONArray("latent").getString(0))
        zkontrolujOdkazy(g)
    }

    /**
     * Jednopruchodove sestavy nesmi zadny z novych uzlu dostat - jinak by
     * se jim zmenilo vzorkovani.
     */
    @Test fun `jeden pruchod zustava beze zmeny`() {
        val g = LongMmBuilder.buildPrvni(
            prvni, scena().copy(model = cz.promptlab.h3video.data.LongMmModel.TURBO),
            1L, emptyList(),
        )
        listOf(
            LongMmBuilder.N_SHIFT, LongMmBuilder.N_NIZKE_ZADANI, LongMmBuilder.N_PRUCHOD1,
            LongMmBuilder.N_ZVETSENI, LongMmBuilder.N_SLOZENI, LongMmBuilder.N_SIGMY_ZJEMNENI,
        ).forEach { assertFalse("$it nema byt v grafu", g.has(it)) }
        assertEquals(LongMmBuilder.N_KROKY, g.inputs("8").getJSONArray("sigmas").getString(0))
        zkontrolujOdkazy(g)
    }

    /**
     * Navazani dva pruchody neumi (SegmentRender si useky vzorkuje sam),
     * ale posun sigm tam patri taky - dela pulku kvality.
     */
    @Test fun `navazani ma posun sigm ale jeden pruchod`() {
        val m = cz.promptlab.h3video.data.LongMmModel.TRIPLUSDVA
        val g = LongMmBuilder.buildDalsi(
            dalsi, scena(rezim = LongMmRezim.NAVAZANI).copy(model = m, kroky = m.kroky),
            1L, "c.mp4",
        )
        assertTrue(g.has(LongMmBuilder.N_SHIFT))
        assertFalse(g.has(LongMmBuilder.N_PRUCHOD1))
        assertFalse(g.has(LongMmBuilder.N_DVA_PRUCHODY))
        assertEquals(
            LongMmBuilder.N_SHIFT,
            g.inputs(LongMmBuilder.N_SEED_DALSI).getJSONArray("model").getString(0),
        )
        zkontrolujOdkazy(g)
    }

    /**
     * Rozmery musi vyjit presne tak, jak si je spocita balik - jinak by
     * zvetseny latent nesedel s podminkou postavenou na cilovem platne.
     */
    @Test fun `rozmery sedi s tabulkou balicku`() {
        val p = cz.promptlab.h3video.data.LongMmPomer.NASIRKU
        // 540P = 0,5 MP na sirku; presne dvojice, na ktere jede karta "3 kroky".
        assertEquals(960 to 544, LongMmBuilder.rozmery(LongMmRozliseni.R540, p))
        // 768P = 1,0 MP: vzorec da 1376x768, ne 1344x768 — ta hodnota byla jen
        // stara vychozi v predloze a uzel si plátno stejne pocita sam.
        assertEquals(1376 to 768, LongMmBuilder.rozmery(LongMmRozliseni.R768, p))
        val naVysku = cz.promptlab.h3video.data.LongMmPomer.NAVYSKU
        assertEquals(544 to 960, LongMmBuilder.rozmery(LongMmRozliseni.R540, naVysku))
    }
}
