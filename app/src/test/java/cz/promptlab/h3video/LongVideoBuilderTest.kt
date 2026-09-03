package cz.promptlab.h3video

import cz.promptlab.h3video.comfy.LongVideoBuilder
import cz.promptlab.h3video.comfy.Stage
import cz.promptlab.h3video.data.AioSlot
import cz.promptlab.h3video.data.GenParams
import cz.promptlab.h3video.data.LongScene
import cz.promptlab.h3video.data.LongStart
import cz.promptlab.h3video.data.LongUsek
import cz.promptlab.h3video.data.Mode
import cz.promptlab.h3video.data.framesForSeconds
import cz.promptlab.h3video.data.longProblem
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
 * Karta **Dlouhé video**: až šest navazujících úseků v jednom běhu.
 *
 * Graf se nestaví z předlohy, ale skládá v Kotlinu — testy proto musí hlídat
 * i to, co u ostatních karet hlídá sama šablona: že zapojení nikde nevisí,
 * že úseky navazují ve správném pořadí a že se do uzlů posílají hodnoty,
 * které jejich zdrojáky opravdu čekají.
 */
class LongVideoBuilderTest {

    private fun params() = GenParams(
        mode = Mode.LONG,
        steps = 8,
        sampler = "res_multistep",
        scheduler = "beta",
        seed = 100L,
        sageAttention = true,
    )

    private fun scene(
        zacatek: LongStart = LongStart.EXISTING_VIDEO,
        pocet: Int = 3,
        lora: String = "",
    ) = LongScene(
        zacatek = zacatek,
        sourceVideo = File("zdroj.mp4"),
        startPrompt = "muz stoji na plazi",
        refs = listOf(AioSlot(key = 1, image = File("postava.jpg"))),
        useky = (1..pocet).map {
            LongUsek(key = it, prompt = "usek $it", seconds = 7f, lora = if (it == 2) lora else "")
        },
    )

    private fun JSONObject.inputs(node: String): JSONObject =
        getJSONObject(node).getJSONObject("inputs")

    private fun JSONObject.classOf(node: String): String =
        getJSONObject(node).getString("class_type")

    /** Projde všechny odkazy grafu a vrátí ty, které nikam nevedou. */
    private fun visiciOdkazy(wf: JSONObject): List<String> {
        val out = mutableListOf<String>()
        wf.keys().forEach { id ->
            val ins = wf.getJSONObject(id).getJSONObject("inputs")
            ins.keys().forEach { key ->
                val v = ins.opt(key)
                if (v is JSONArray && v.length() == 2 && v.opt(0) is String) {
                    if (!wf.has(v.getString(0))) out += "$id.$key -> ${v.getString(0)}"
                }
            }
        }
        return out
    }

    @Test
    fun `graf nema visici odkazy`() {
        LongStart.entries.forEach { z ->
            (1..LongVideoBuilder.MAX_USEKU).forEach { n ->
                val wf = LongVideoBuilder.build(
                    scene(z, n), params(), 7L, "zdroj.mp4", listOf("postava.jpg")
                )
                assertEquals("$z / $n úseků", emptyList<String>(), visiciOdkazy(wf))
            }
        }
    }

    /**
     * Nejdůležitější vlastnost karty: úsek N navazuje na latent úseku N−1.
     * Kdyby se pořadí rozpadlo, video by se poskládalo přeházeně a poznalo
     * by se to až na hotovém souboru.
     */
    @Test
    fun `useky navazuji jeden na druhy`() {
        val wf = LongVideoBuilder.build(scene(pocet = 4), params(), 7L, "zdroj.mp4", emptyList())
        // Úsek 1 začíná od zdrojového videa.
        assertEquals(
            "MiniMaxH3StartMaskedContext",
            wf.classOf(LongVideoBuilder.kontextUseku(1))
        )
        // Úseky 2..N berou latent předchozího vzorkování.
        (2..4).forEach { i ->
            val ctx = wf.inputs(LongVideoBuilder.kontextUseku(i))
            assertEquals(
                "MiniMaxH3GeneratedAVMaskedContext",
                wf.classOf(LongVideoBuilder.kontextUseku(i))
            )
            assertEquals(
                "úsek $i nenavazuje na $i-1",
                LongVideoBuilder.samplerUseku(i - 1),
                ctx.getJSONArray("source_latent").getString(0)
            )
        }
        // A vzorkování úseku i jede z jeho vlastního kontextu.
        (1..4).forEach { i ->
            assertEquals(
                LongVideoBuilder.kontextUseku(i),
                wf.inputs(LongVideoBuilder.samplerUseku(i)).getJSONArray("latent_image").getString(0)
            )
        }
    }

    @Test
    fun `vsechny useky konci ve streamovacim uzlu`() {
        val wf = LongVideoBuilder.build(scene(pocet = 5), params(), 7L, "zdroj.mp4", emptyList())
        val s = wf.inputs(LongVideoBuilder.N_STREAM)
        assertEquals(5, s.getInt("active_extensions"))
        (1..5).forEach { i ->
            assertEquals(
                LongVideoBuilder.samplerUseku(i),
                s.getJSONArray("extension_$i").getString(0)
            )
        }
        assertFalse("úsek navíc se do uzlu nesmí dostat", s.has("extension_6"))
    }

    /**
     * Past, na kterou se dá naletět: přepínač `MiniMaxH3ExtensionStartMode`
     * vrací `load_video`, ale streamovací uzel porovnává jen s `existing_video`
     * — s jinou hodnotou by sáhl po vygenerovaném začátku a spadl.
     */
    @Test
    fun `navazani posila existing_video vsem uzlum retezu`() {
        val wf = LongVideoBuilder.build(scene(), params(), 7L, "zdroj.mp4", emptyList())
        listOf(
            LongVideoBuilder.N_STREAM,
            LongVideoBuilder.N_CANVAS,
            LongVideoBuilder.kontextUseku(1),
        ).forEach { id ->
            assertEquals(id, LongVideoBuilder.START_EXISTING, wf.inputs(id).getString("start_mode"))
        }
        // Zdrojové snímky i zvuk jdou z rozloženého videa, ne odjinud.
        assertEquals(
            LongVideoBuilder.N_CROP32,
            wf.inputs(LongVideoBuilder.N_STREAM).getJSONArray("source_frames").getString(0)
        )
        assertEquals(
            LongVideoBuilder.N_VIDEO_PARTS,
            wf.inputs(LongVideoBuilder.N_STREAM).getJSONArray("source_audio").getString(0)
        )
        assertFalse(wf.inputs(LongVideoBuilder.N_STREAM).has("starter_latent"))
    }

    @Test
    fun `zacatek od nuly si nejdriv vyrobi prvni zaber`() {
        val wf = LongVideoBuilder.build(scene(LongStart.GENERATED), params(), 7L, null, emptyList())
        assertEquals("MiniMaxH3ReferenceToVideo", wf.classOf(LongVideoBuilder.N_START_COND))
        assertEquals(
            "muz stoji na plazi",
            wf.inputs(LongVideoBuilder.N_START_COND).getString("prompt")
        )
        // Ten záběr je zároveň podkladem pro kontext prvního úseku i pro sešití.
        assertEquals(
            LongVideoBuilder.N_START_SAMPLER,
            wf.inputs(LongVideoBuilder.kontextUseku(1)).getJSONArray("live_starter_latent").getString(0)
        )
        assertEquals(
            LongVideoBuilder.N_START_SAMPLER,
            wf.inputs(LongVideoBuilder.N_STREAM).getJSONArray("starter_latent").getString(0)
        )
        // Video se nenačítá vůbec.
        assertFalse(wf.has(LongVideoBuilder.N_LOAD_VIDEO))
        assertFalse(wf.inputs(LongVideoBuilder.N_CANVAS).has("source_width"))
    }

    @Test
    fun `kazdy usek ma vlastni zadani, delku i sum`() {
        val s = scene(pocet = 3).copy(
            useky = listOf(
                LongUsek(1, "prvni", seconds = 5f),
                LongUsek(2, "druhy", seconds = 9f),
                LongUsek(3, "treti", seconds = 7f),
            )
        )
        val wf = LongVideoBuilder.build(s, params(), 1000L, "zdroj.mp4", emptyList())
        assertEquals("prvni", wf.inputs(LongVideoBuilder.condUseku(1)).getString("prompt"))
        assertEquals("druhy", wf.inputs(LongVideoBuilder.condUseku(2)).getString("prompt"))
        assertEquals(
            framesForSeconds(9f),
            wf.inputs(LongVideoBuilder.condUseku(2)).getInt("length")
        )
        val sumy = (1..3).map { wf.inputs(LongVideoBuilder.sumUseku(it)).getLong("noise_seed") }
        assertEquals(sumy.toSet().size, sumy.size)
    }

    /**
     * LoRA úseku se věší až za Spectrum, takže může být u každého úseku jiná.
     * Kdyby seděla dřív, přepsala by řetěz i ostatním.
     */
    @Test
    fun `lora useku plati jen pro svuj usek`() {
        val wf = LongVideoBuilder.build(
            scene(pocet = 3, lora = "h3_kamera.safetensors"), params(), 7L, "zdroj.mp4", emptyList()
        )
        val model2 = wf.inputs(LongVideoBuilder.guiderUseku(2)).getJSONArray("model").getString(0)
        assertEquals("LoraLoaderModelOnly", wf.classOf(model2))
        assertEquals("h3_kamera.safetensors", wf.inputs(model2).getString("lora_name"))
        // Sousední úseky jedou rovnou ze společného základu.
        listOf(1, 3).forEach { i ->
            val m = wf.inputs(LongVideoBuilder.guiderUseku(i)).getJSONArray("model").getString(0)
            assertEquals("SpectrumApplyMiniMaxH3", wf.classOf(m))
        }
    }

    @Test
    fun `retez modelu ma poradi z predlohy`() {
        val wf = LongVideoBuilder.build(
            scene().copy(spolecnaLora = "spolecna.safetensors"),
            params(), 7L, "zdroj.mp4", emptyList()
        )
        // Spectrum ← Sage ← SigmaShift ← společná LoRA ← UNET
        val sage = wf.inputs(LongVideoBuilder.N_SPECTRUM).getJSONArray("model").getString(0)
        assertEquals("PathchSageAttentionKJ", wf.classOf(sage))
        val shift = wf.inputs(sage).getJSONArray("model").getString(0)
        assertEquals("MiniMaxH3SigmaShift", wf.classOf(shift))
        val lora = wf.inputs(shift).getJSONArray("model").getString(0)
        assertEquals("LoraLoaderModelOnly", wf.classOf(lora))
        assertEquals(LongVideoBuilder.N_UNET, wf.inputs(lora).getJSONArray("model").getString(0))
        // Plán kroků visí na základu, ne na LoRA konkrétního úseku.
        assertEquals(
            LongVideoBuilder.N_SPECTRUM,
            wf.inputs(LongVideoBuilder.N_SCHEDULER).getJSONArray("model").getString(0)
        )
    }

    @Test
    fun `reference visi na kazdem useku`() {
        val wf = LongVideoBuilder.build(
            scene(pocet = 2), params(), 7L, "zdroj.mp4", listOf("a.jpg", "b.jpg")
        )
        (1..2).forEach { i ->
            val c = wf.inputs(LongVideoBuilder.condUseku(i))
            assertTrue("úsek $i nemá reference", c.has("ref_images.ref_image_0"))
            assertTrue(c.has("ref_images.ref_image_1"))
            val r = c.getJSONArray("ref_images.ref_image_0").getString(0)
            assertEquals("LoadImage", wf.classOf(r))
            assertEquals("a.jpg", wf.inputs(r).getString("image"))
        }
    }

    @Test
    fun `prazdne useky se preskoci`() {
        val s = scene(pocet = 3).copy(
            useky = listOf(LongUsek(1, "prvni"), LongUsek(2, "   "), LongUsek(3, "treti"))
        )
        assertEquals(2, s.aktivniUseky.size)
        val wf = LongVideoBuilder.build(s, params(), 7L, "zdroj.mp4", emptyList())
        assertEquals(2, wf.inputs(LongVideoBuilder.N_STREAM).getInt("active_extensions"))
        assertEquals("treti", wf.inputs(LongVideoBuilder.condUseku(2)).getString("prompt"))
    }

    @Test
    fun `co karte chybi`() {
        assertNull(longProblem(scene()))
        assertNotNull(longProblem(scene().copy(sourceVideo = null)))
        assertNotNull(longProblem(scene(LongStart.GENERATED).copy(startPrompt = "")))
        assertNotNull(longProblem(scene().copy(useky = listOf(LongUsek(1, "")))))
    }

    @Test
    fun `odhad delky odecita prekryv`() {
        val s = scene(pocet = 2)
        // 2 × 7 s minus dvakrát 39 snímků kontextu (39/24 s)
        val cekano = 2 * (7f - 39f / 24f)
        assertEquals(cekano, s.odhadSekund, 0.01f)
    }

    /**
     * Rychlý začátek: první průchod na menším plátně, latent se rozdělí,
     * obrazová část se zvětší a slepí zpátky se zvukem a dojede se krátký
     * doostřovací průchod. Bere se DRUHÝ výstup vzorkovače (denoised_output) —
     * s prvním by se doostřovalo z nedopočítaného obrazu.
     */
    @Test
    fun `rychly zacatek jede na dva pruchody pres zvetseni latentu`() {
        val s = scene(LongStart.GENERATED).copy(rychlyZacatek = true)
        val wf = LongVideoBuilder.build(s, params(), 7L, null, emptyList())

        val sep = wf.inputs(LongVideoBuilder.N_FAST_SEPARATE)
        assertEquals(LongVideoBuilder.N_START_SAMPLER, sep.getJSONArray("av_latent").getString(0))
        assertEquals(1, sep.getJSONArray("av_latent").getInt(1))

        val up = wf.inputs(LongVideoBuilder.N_FAST_UPSCALE)
        assertEquals(LongVideoBuilder.N_FAST_SEPARATE, up.getJSONArray("latent").getString(0))
        assertEquals(LongVideoBuilder.UPSCALER_MODEL, up.getString("model_name"))
        assertEquals("target dimensions", up.getString("mode"))
        assertEquals(params().resolution.width, up.getInt("mode.width"))
        assertEquals(params().resolution.height, up.getInt("mode.height"))

        // Zvuk se nezvětšuje, vrací se z rozdělení beze změny.
        val cat = wf.inputs(LongVideoBuilder.N_FAST_CONCAT)
        assertEquals(LongVideoBuilder.N_FAST_UPSCALE, cat.getJSONArray("video_latent").getString(0))
        assertEquals(LongVideoBuilder.N_FAST_SEPARATE, cat.getJSONArray("audio_latent").getString(0))
        assertEquals(1, cat.getJSONArray("audio_latent").getInt(1))

        // Doostření jede z ručních sigem a z cílové podmínky.
        val ref = wf.inputs(LongVideoBuilder.N_FAST_SAMPLER)
        assertEquals(LongVideoBuilder.N_FAST_SIGMAS, ref.getJSONArray("sigmas").getString(0))
        assertEquals(LongVideoBuilder.N_FAST_CONCAT, ref.getJSONArray("latent_image").getString(0))

        // Na řetěz úseků i do sešití jde až doostřený latent.
        assertEquals(
            LongVideoBuilder.N_FAST_SAMPLER,
            wf.inputs(LongVideoBuilder.N_STREAM).getJSONArray("starter_latent").getString(0)
        )
        assertEquals(
            LongVideoBuilder.N_FAST_SAMPLER,
            wf.inputs(LongVideoBuilder.kontextUseku(1)).getJSONArray("live_starter_latent").getString(0)
        )
    }

    @Test
    fun `prvni pruchod jede na mensim platne, doostreni na cilovem`() {
        val s = scene(LongStart.GENERATED).copy(rychlyZacatek = true)
        val wf = LongVideoBuilder.build(s, params(), 7L, null, emptyList())
        val res = params().resolution
        val (mw, mh) = LongVideoBuilder.mensiPlatno(res.width, res.height)
        assertEquals(mw, wf.inputs(LongVideoBuilder.N_START_COND).getInt("width"))
        assertEquals(mh, wf.inputs(LongVideoBuilder.N_START_COND).getInt("height"))
        assertTrue("menší plátno není menší", mw < res.width && mh < res.height)
        assertEquals(0, mw % 32)
        assertEquals(0, mh % 32)
        // Doostřovací podmínka bere rozměry z plátna, ne ze zmenšeniny.
        assertTrue(
            wf.inputs(LongVideoBuilder.N_FAST_COND).get("width") is JSONArray
        )
    }

    @Test
    fun `bez rychleho zacatku se zvetsovac do grafu nedostane`() {
        val wf = LongVideoBuilder.build(
            scene(LongStart.GENERATED), params(), 7L, null, emptyList()
        )
        assertFalse(wf.has(LongVideoBuilder.N_FAST_UPSCALE))
        assertFalse(wf.has(LongVideoBuilder.N_FAST_SAMPLER))
        assertEquals(
            LongVideoBuilder.N_START_SAMPLER,
            wf.inputs(LongVideoBuilder.N_STREAM).getJSONArray("starter_latent").getString(0)
        )
        // A při navazování na video nemá rychlý začátek co dělat vůbec.
        val navaz = LongVideoBuilder.build(
            scene().copy(rychlyZacatek = true), params(), 7L, "zdroj.mp4", emptyList()
        )
        assertFalse(navaz.has(LongVideoBuilder.N_FAST_UPSCALE))
    }

    @Test
    fun `faze podle tridy uzlu`() {
        assertEquals(Stage.SAMPLING, LongVideoBuilder.stageForClass("SamplerCustomAdvanced"))
        assertEquals(Stage.MODELS, LongVideoBuilder.stageForClass("UNETLoader"))
        assertEquals(
            Stage.MUXING,
            LongVideoBuilder.stageForClass("MiniMaxH3StreamLiveExtensionAVToVHS")
        )
        assertEquals(Stage.REFERENCES, LongVideoBuilder.stageForClass("MiniMaxH3CropTo32"))
        assertTrue(LongVideoBuilder.reportsSteps("SamplerCustomAdvanced"))
    }
}
