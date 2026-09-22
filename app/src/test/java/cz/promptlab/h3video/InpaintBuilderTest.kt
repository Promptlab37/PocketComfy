package cz.promptlab.h3video

import cz.promptlab.h3video.comfy.InpaintBuilder
import cz.promptlab.h3video.comfy.Stage
import cz.promptlab.h3video.data.GenParams
import cz.promptlab.h3video.data.InpaintModel
import cz.promptlab.h3video.data.InpaintScene
import cz.promptlab.h3video.data.Mode
import cz.promptlab.h3video.data.inpaintProblem
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
 * Karta **Domalovat** má tři předlohy (Flux Fill, FLUX.2 Klein, Qwen Image 2.1).
 * Testy hlídají,
 * že se do nich dosazují JEN fotka, maska, zadání a seed — a že vyladěné
 * hodnoty (kroky, cfg, vedení, model, VAE, enkodér) zůstávají netknuté.
 */
class InpaintBuilderTest {

    private val klein: String =
        File("src/main/res/raw/workflow_inpaint_klein.json").readText()
    private val fill: String =
        File("src/main/res/raw/workflow_inpaint_fill.json").readText()
    private val qwen: String =
        File("src/main/res/raw/workflow_inpaint_qwen21.json").readText()
    private val rozsireni: String =
        File("src/main/res/raw/workflow_outpaint_qwen21.json").readText()

    private fun JSONObject.inputs(node: String): JSONObject =
        getJSONObject(node).getJSONObject("inputs")

    private fun bezVisicichOdkazu(wf: JSONObject) {
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

    // -------------------------------------------------------------- Klein

    @Test
    fun `klein - dosadi se fotka, maska, zadani a seed`() {
        val wf = InpaintBuilder.build(
            klein, InpaintModel.KLEIN, "dřevěná lavička", 42L, listOf("foto.png", "maska.png")
        )
        assertEquals("foto.png", wf.inputs(InpaintBuilder.N_IMAGE).getString("image"))
        assertEquals("maska.png", wf.inputs(InpaintBuilder.N_MASK).getString("image"))
        // Klein dostává zadání zabalené do instrukce (viz vlastní test níž).
        assertTrue(wf.inputs(InpaintBuilder.N_TEXT).getString("text").contains("dřevěná lavička"))
        assertEquals(42L, wf.inputs(InpaintBuilder.N_NOISE).getLong("noise_seed"))
        bezVisicichOdkazu(wf)
    }

    @Test
    fun `klein - vyladene hodnoty predlohy zustavaji`() {
        val wf = InpaintBuilder.build(klein, InpaintModel.KLEIN, "x", 1L, listOf("a.png", "m.png"))
        assertEquals("flux-2-klein-9b.safetensors", wf.inputs("1").getString("unet_name"))
        assertEquals("qwen_3_8b_fp8mixed.safetensors", wf.inputs("2").getString("clip_name"))
        assertEquals("flux2", wf.inputs("2").getString("type"))
        assertEquals("flux2-vae.safetensors", wf.inputs("3").getString("vae_name"))
        assertEquals(4, wf.inputs("40").getInt("steps"))
        assertEquals(1.0, wf.inputs("43").getDouble("cfg"), 0.001)
        assertEquals("euler", wf.inputs("41").getString("sampler_name"))
        assertEquals(InpaintBuilder.KLEIN_STEPS, wf.inputs("40").getInt("steps"))
    }

    @Test
    fun `klein - maluje se jen pod maskou a vysledek se vlepi zpet`() {
        val wf = InpaintBuilder.build(klein, InpaintModel.KLEIN, "x", 1L, listOf("a.png", "m.png"))
        // maska: LoadImage → ImageToMask(red) → výřez
        assertEquals("ImageToMask", wf.getJSONObject("12").getString("class_type"))
        assertEquals("red", wf.inputs("12").getString("channel"))
        assertEquals(InpaintBuilder.N_MASK, wf.inputs("12").getJSONArray("image").getString(0))
        assertEquals("12", wf.inputs("20").getJSONArray("mask").getString(0))
        // šum jen pod maskou výřezu (jinak by se přepsal celý výřez)
        assertEquals("SetLatentNoiseMask", wf.getJSONObject("23").getString("class_type"))
        assertEquals("20", wf.inputs("23").getJSONArray("mask").getString(0))
        assertEquals(2, wf.inputs("23").getJSONArray("mask").getInt(1))
        assertEquals("23", wf.inputs("44").getJSONArray("latent_image").getString(0))
        // původní výřez jde modelu jako reference, ať naváže na okolí
        assertEquals("ReferenceLatent", wf.getJSONObject("31").getString("class_type"))
        assertEquals("22", wf.inputs("31").getJSONArray("latent").getString(0))
        // hotový kus se vlepí zpět přes stitcher z výřezu
        assertEquals("20", wf.inputs("51").getJSONArray("stitcher").getString(0))
        assertEquals("51", wf.inputs("60").getJSONArray("images").getString(0))
    }

    // ----------------------------------------------------------- Flux Fill

    @Test
    fun `fill - dosadi se fotka, maska, zadani a seed`() {
        val wf = InpaintBuilder.build(
            fill, InpaintModel.FILL, "cihlová zeď", 7L, listOf("foto.png", "maska.png")
        )
        assertEquals("foto.png", wf.inputs(InpaintBuilder.N_IMAGE).getString("image"))
        assertEquals("maska.png", wf.inputs(InpaintBuilder.N_MASK).getString("image"))
        assertEquals("cihlová zeď", wf.inputs(InpaintBuilder.N_TEXT).getString("text"))
        assertEquals(7L, wf.inputs(InpaintBuilder.N_SAMPLER).getLong("seed"))
        bezVisicichOdkazu(wf)
    }

    @Test
    fun `fill - vyladene hodnoty predlohy zustavaji`() {
        val wf = InpaintBuilder.build(fill, InpaintModel.FILL, "x", 1L, listOf("a.png", "m.png"))
        assertEquals("flux1-Fill-Dev_FP8.safetensors", wf.inputs("1").getString("unet_name"))
        assertEquals("ae.sft", wf.inputs("3").getString("vae_name"))
        assertEquals(
            "FLUX.1-Turbo-Alpha.safetensors",
            wf.inputs("4").getJSONObject("lora_1").getString("lora")
        )
        val s = wf.inputs(InpaintBuilder.N_SAMPLER)
        assertEquals(8, s.getInt("steps"))
        assertEquals("dpmpp_2m", s.getString("sampler_name"))
        assertEquals("sgm_uniform", s.getString("scheduler"))
        assertEquals(1.0, s.getDouble("denoise"), 0.001)
        assertEquals(30.0, wf.inputs("31").getDouble("guidance"), 0.001)
        // model trénovaný na díry dostává masku přes InpaintModelConditioning
        assertEquals("InpaintModelConditioning", wf.getJSONObject("33").getString("class_type"))
        assertTrue(wf.inputs("33").getBoolean("noise_mask"))
        assertEquals(InpaintBuilder.FILL_STEPS, s.getInt("steps"))
    }

    // ------------------------------------------------------------ společné

    @Test
    fun `seed jde do spravneho uzlu podle modelu`() {
        val k = InpaintBuilder.build(klein, InpaintModel.KLEIN, "x", 5L, listOf("a.png", "m.png"))
        assertEquals(5L, k.inputs(InpaintBuilder.N_NOISE).getLong("noise_seed"))
        val f = InpaintBuilder.build(fill, InpaintModel.FILL, "x", 5L, listOf("a.png", "m.png"))
        assertEquals(5L, f.inputs(InpaintBuilder.N_SAMPLER).getLong("seed"))
        // Klein nemá KSampler a Fill nemá RandomNoise — čísla uzlů se nesmí plést.
        assertFalse(k.has(InpaintBuilder.N_SAMPLER))
        assertFalse(f.has(InpaintBuilder.N_NOISE))
    }

    @Test
    fun `klein dostane zadani jako prikaz, fill doslova`() {
        // Klein drží původní výřez jako referenci a holý popis pro něj znamená
        // „nech to být" — proto se jeho zadání balí do instrukce. Flux Fill
        // maluje do díry rovnou to, co je v textu, tomu se nesmí sahat.
        val k = InpaintBuilder.build(klein, InpaintModel.KLEIN, "dřevěná lavička", 1L,
            listOf("a.png", "m.png"))
        val textK = k.inputs(InpaintBuilder.N_TEXT).getString("text")
        assertTrue(textK.contains("dřevěná lavička"))
        assertTrue(textK.startsWith("Repaint the masked region"))

        val f = InpaintBuilder.build(fill, InpaintModel.FILL, "dřevěná lavička", 1L,
            listOf("a.png", "m.png"))
        assertEquals("dřevěná lavička", f.inputs(InpaintBuilder.N_TEXT).getString("text"))

        // Prázdné zadání se nesmí proměnit v instrukci bez obsahu.
        assertEquals("", InpaintBuilder.zadaniProModel(InpaintModel.KLEIN, "   "))
    }

    @Test
    fun `vychozi model karty je Flux Fill`() {
        // Klein na popisné zadání často nezmění nic (ověřeno na běhu 2. 9. 2026),
        // takže výchozí je model trénovaný přímo na domalovávání.
        assertEquals(InpaintModel.FILL, InpaintScene().model)
        assertEquals(InpaintModel.FILL, InpaintModel.entries.first())
    }

    @Test
    fun `kroky hlasene ukazateli sedi s predlohou`() {
        assertEquals(4, InpaintBuilder.stepsFor(InpaintModel.KLEIN))
        assertEquals(8, InpaintBuilder.stepsFor(InpaintModel.FILL))
    }

    @Test
    fun `validace chce fotku, masku i zadani po rade`() {
        assertEquals(
            "Vyber fotku, do které se má domalovávat.",
            inpaintProblem(InpaintScene())
        )
        assertEquals(
            "Začmárej prstem místo, které se má přemalovat.",
            inpaintProblem(InpaintScene(source = File("a.png")))
        )
        assertEquals(
            "Napiš, co má na zamaskovaném místě být.",
            inpaintProblem(InpaintScene(source = File("a.png"), mask = File("m.png")))
        )
        assertNull(
            inpaintProblem(
                InpaintScene(source = File("a.png"), mask = File("m.png"), prompt = "lavička")
            )
        )
    }

    @Test
    fun `imageSlots pokryje vsechny soubory sceny`() {
        // Past z 2.89 u výměny tváře: scéna nesla víc souborů, než kolik jich
        // engine podle imageSlots nahrál, a maska se cestou ztratila.
        val scena = InpaintScene(source = File("a.png"), mask = File("m.png"), prompt = "x")
        assertEquals(listOf("a.png", "m.png"), scena.uploadImages.map { it.name })
        assertTrue(GenParams(mode = Mode.INPAINT).imageSlots >= scena.uploadImages.size)
    }

    @Test
    fun `lora a sila premalovani se dosadi podle modelu`() {
        // Flux Fill: LoRA jde do stávajícího Power Lora Loaderu vedle Turba,
        // síla přemalování do denoise KSampleru.
        val f = InpaintBuilder.build(
            fill, InpaintModel.FILL, "x", 1L, listOf("a.png", "m.png"),
            lora = "nipplediffusion-f1.safetensors", loraSila = 0.85f, sila = 0.6f,
        )
        val l = f.inputs("4").getJSONObject("lora_2")
        assertEquals("nipplediffusion-f1.safetensors", l.getString("lora"))
        assertEquals(0.85, l.getDouble("strength"), 0.001)
        assertTrue(l.getBoolean("on"))
        assertEquals(0.6, f.inputs(InpaintBuilder.N_SAMPLER).getDouble("denoise"), 0.001)
        // Turbo LoRA z předlohy zůstává.
        assertEquals(
            "FLUX.1-Turbo-Alpha.safetensors",
            f.inputs("4").getJSONObject("lora_1").getString("lora")
        )

        // Klein: samostatný LoraLoaderModelOnly mezi modelem a vedením.
        val k = InpaintBuilder.build(
            klein, InpaintModel.KLEIN, "x", 1L, listOf("a.png", "m.png"),
            lora = "FLUX2_NAHOTASQNSFW_F2K9B_v1.0.safetensors", loraSila = 1.0f,
        )
        assertEquals("LoraLoaderModelOnly",
            k.getJSONObject(InpaintBuilder.N_LORA_KLEIN).getString("class_type"))
        assertEquals("1",
            k.inputs(InpaintBuilder.N_LORA_KLEIN).getJSONArray("model").getString(0))
        assertEquals(InpaintBuilder.N_LORA_KLEIN,
            k.inputs("43").getJSONArray("model").getString(0))
        bezVisicichOdkazu(k)
    }

    @Test
    fun `bez vybrane lory se sablona nemeni`() {
        val s = InpaintBuilder.build(klein, InpaintModel.KLEIN, "x", 1L, listOf("a.png", "m.png"))
        assertFalse(s.has(InpaintBuilder.N_LORA_KLEIN))
        assertEquals("1", s.inputs("43").getJSONArray("model").getString(0))
        val f = InpaintBuilder.build(fill, InpaintModel.FILL, "x", 1L, listOf("a.png", "m.png"))
        assertFalse(f.inputs("4").has("lora_2"))
        assertEquals(1.0, f.inputs(InpaintBuilder.N_SAMPLER).getDouble("denoise"), 0.001)
    }

    @Test
    fun `faze podle trid`() {
        assertEquals(Stage.MODELS, InpaintBuilder.stageForClass("UNETLoader"))
        assertEquals(Stage.REFERENCES, InpaintBuilder.stageForClass("InpaintCropImproved"))
        assertEquals(Stage.SAMPLING, InpaintBuilder.stageForClass("SamplerCustomAdvanced"))
        assertEquals(Stage.SAMPLING, InpaintBuilder.stageForClass("KSampler"))
        assertEquals(Stage.MUXING, InpaintBuilder.stageForClass("InpaintStitchImproved"))
        assertTrue(InpaintBuilder.reportsSteps("SamplerCustomAdvanced"))
        assertTrue(InpaintBuilder.reportsSteps("KSampler"))
        assertFalse(InpaintBuilder.reportsSteps("VAEDecode"))
    }

    /**
     * Qwen Image 2.1 vlastní uzel na masku nemá. Maskuje se tím, že do
     * vzorkování jde místo prázdného latentu **zdrojový výřez** přes
     * `VAEEncode` + `SetLatentNoiseMask`. Kdyby latent přišel odjinud
     * (třeba z třetího výstupu textového uzlu), maska by nedržela a model
     * by přemaloval celý výřez.
     */
    @Test fun `qwen 21 maskuje pres zdrojovy latent, ne prazdny`() {
        val wf = InpaintBuilder.build(qwen, InpaintModel.QWEN21, "lavička", 7L,
            listOf("foto.png", "maska.png"))
        assertEquals("foto.png", wf.inputs(InpaintBuilder.N_IMAGE).getString("image"))
        assertEquals("maska.png", wf.inputs(InpaintBuilder.N_MASK).getString("image"))

        val latent = wf.inputs(InpaintBuilder.N_SAMPLER).getJSONArray("latent_image")
        assertEquals("SetLatentNoiseMask", wf.getJSONObject(latent.getString(0)).getString("class_type"))
        val maskovany = wf.inputs(latent.getString(0))
        assertEquals("VAEEncode",
            wf.getJSONObject(maskovany.getJSONArray("samples").getString(0)).getString("class_type"))
        // Maska je ta vyříznutá kolem štětce, ne původní celá.
        assertEquals("InpaintCropImproved",
            wf.getJSONObject(maskovany.getJSONArray("mask").getString(0)).getString("class_type"))
        bezVisicichOdkazu(wf)
    }

    /**
     * Výřez musí být zároveň **referencí** — jen tak model vidí okolí masky
     * a udrží podobu. A `resolution` musí zůstat 0: jakákoli jiná hodnota
     * zmenší referenci jinak než vzorkovaný latent a edit ujede.
     */
    @Test fun `qwen 21 vidi vyrez jako referenci ve stejne velikosti`() {
        val wf = InpaintBuilder.build(qwen, InpaintModel.QWEN21, "x", 1L, listOf("a.png", "b.png"))
        val text = wf.inputs(InpaintBuilder.N_TEXT)
        assertEquals(0, text.getInt("resolution"))
        val ref = text.getJSONArray("images.image_1")
        assertEquals("InpaintCropImproved", wf.getJSONObject(ref.getString(0)).getString("class_type"))
        // Reference i latent berou TENTÝŽ výstup výřezu (1 = oříznutý obraz).
        assertEquals(1, ref.getInt(1))
        val vae = wf.inputs(
            wf.inputs(wf.inputs(InpaintBuilder.N_SAMPLER).getJSONArray("latent_image").getString(0))
                .getJSONArray("samples").getString(0)
        )
        assertEquals(ref.toString(), vae.getJSONArray("pixels").toString())
    }

    @Test fun `qwen 21 dostane zadani jako pokyn a do spravneho pole`() {
        val wf = InpaintBuilder.build(qwen, InpaintModel.QWEN21, "dřevěná lavička", 3L,
            listOf("a.png", "b.png"))
        val text = wf.inputs(InpaintBuilder.N_TEXT)
        // Uzel má pole `prompt`, ne `text` — je to jiná třída, ne jiný název.
        assertFalse(text.has("text"))
        val zadani = text.getString("prompt")
        assertTrue(zadani.contains("<image1>"))
        assertTrue(zadani.contains("dřevěná lavička"))
        assertTrue(zadani.contains("must not come back unchanged"))
        assertEquals("", text.getString("negative_prompt"))
        assertEquals(3L, wf.inputs(InpaintBuilder.N_SAMPLER).getLong("seed"))
        // Pod maskou vzniká obsah ze zadání, ne dokreslení původního.
        assertEquals(1.0, wf.inputs(InpaintBuilder.N_SAMPLER).getDouble("denoise"), 1e-6)
        assertEquals(InpaintBuilder.QWEN21_STEPS,
            wf.inputs(InpaintBuilder.N_SAMPLER).getInt("steps"))
    }

    /** Na Qwen 2.1 nesedí žádná LoRA — karta žádnou nenabízí a graf žádnou nemá. */
    @Test fun `qwen 21 nema lora`() {
        assertEquals(emptyList<String>(), cz.promptlab.h3video.data.loryProModel(
            InpaintModel.QWEN21,
            listOf("flux-fill.safetensors", "klein-neco.safetensors", "qwen-neco.safetensors"),
        ))
        val wf = InpaintBuilder.build(qwen, InpaintModel.QWEN21, "x", 1L, listOf("a.png", "b.png"),
            lora = "cokoli.safetensors", loraSila = 1f)
        assertFalse(wf.keys().asSequence().any {
            wf.getJSONObject(it).getString("class_type") == "LoraLoaderModelOnly"
        })
    }

    @Test fun `qwen 21 predloha nenese zadani predchoziho behu`() {
        val p = JSONObject(qwen)
        assertEquals("", p.inputs(InpaintBuilder.N_IMAGE).getString("image"))
        assertEquals("", p.inputs(InpaintBuilder.N_MASK).getString("image"))
        assertEquals("", p.inputs(InpaintBuilder.N_TEXT).getString("prompt"))
    }

    @Test fun `vsechny tridy qwen predlohy maji fazi`() {
        val p = JSONObject(qwen)
        p.keys().forEach { id ->
            val cls = p.getJSONObject(id).getString("class_type")
            assertTrue("uzel $cls nemá fázi",
                InpaintBuilder.stageForClass(cls) != Stage.MODELS || cls.contains("Loader") ||
                    cls == "QwenImage21Cache")
        }
    }

    // --------------------------------------------------- rozšíření obrázku

    private fun rozsir(
        smery: Set<cz.promptlab.h3video.data.Smer>, procent: Int = 50, prompt: String = "nohy",
    ) = InpaintBuilder.buildRozsireni(
        rozsireni,
        InpaintScene(
            prompt = prompt, model = InpaintModel.QWEN21,
            rezim = cz.promptlab.h3video.data.InpaintRezim.ROZSIRIT,
            smery = smery, procent = procent,
        ),
        5L, listOf("foto.png"),
    )

    /**
     * Faktor 1,0 znamená „v tomhle směru neměnit". Kdyby se dosadil i tam,
     * kde uživatel směr nezvolil, fotka by se roztáhla do všech stran.
     */
    @Test fun `rozsiruje se jen do zvolenych smeru`() {
        val wf = rozsir(setOf(cz.promptlab.h3video.data.Smer.DOLU), procent = 50)
        val v = wf.inputs(InpaintBuilder.N_VYREZ)
        assertTrue(v.getBoolean("extend_for_outpainting"))
        assertEquals(1.5, v.getDouble("extend_down_factor"), 1e-6)
        assertEquals(1.0, v.getDouble("extend_up_factor"), 1e-6)
        assertEquals(1.0, v.getDouble("extend_left_factor"), 1e-6)
        assertEquals(1.0, v.getDouble("extend_right_factor"), 1e-6)

        val oba = rozsir(
            setOf(cz.promptlab.h3video.data.Smer.DOLU, cz.promptlab.h3video.data.Smer.VPRAVO),
            procent = 30,
        ).inputs(InpaintBuilder.N_VYREZ)
        assertEquals(1.3, oba.getDouble("extend_down_factor"), 1e-6)
        assertEquals(1.3, oba.getDouble("extend_right_factor"), 1e-6)
        assertEquals(1.0, oba.getDouble("extend_up_factor"), 1e-6)
    }

    /**
     * Masku si uzel vyrobí z přilepeného místa sám. Kdyby do grafu šla ta
     * namalovaná (zbylá z druhého režimu), přemalovala by i kus uvnitř fotky.
     */
    @Test fun `rozsireni neposila zadnou masku`() {
        val wf = rozsir(setOf(cz.promptlab.h3video.data.Smer.DOLU))
        assertFalse(wf.inputs(InpaintBuilder.N_VYREZ).has("mask"))
        assertFalse(wf.keys().asSequence().any {
            wf.getJSONObject(it).getString("class_type") == "ImageToMask"
        })
        val scene = InpaintScene(
            source = File("a.png"), mask = File("m.png"),
            rezim = cz.promptlab.h3video.data.InpaintRezim.ROZSIRIT,
        )
        assertEquals(listOf(File("a.png")), scene.uploadImages)
    }

    /**
     * Fotka jde do grafu dvakrát: `<image1>` je přilepené plátno (určuje
     * velikost), `<image2>` je celá fotka jako kontext. Bez druhé by model
     * kreslil nohy k tělu, které nevidí — vyříznut je totiž jen okolí masky.
     */
    @Test fun `rozsireni vidi celou fotku jako druhou referenci`() {
        val wf = rozsir(setOf(cz.promptlab.h3video.data.Smer.DOLU))
        val text = wf.inputs(InpaintBuilder.N_TEXT)
        assertEquals("InpaintCropImproved",
            wf.getJSONObject(text.getJSONArray("images.image_1").getString(0))
                .getString("class_type"))
        assertEquals(InpaintBuilder.N_IMAGE, text.getJSONArray("images.image_2").getString(0))
        // Kontext musí pokrýt celou fotku, ne jen okolí přilepeného pruhu.
        assertTrue(wf.inputs(InpaintBuilder.N_VYREZ)
            .getDouble("context_from_mask_extend_factor") >= 5.0)
        // Zaplnění děr by u rozšíření na víc stran označilo celou fotku.
        assertFalse(wf.inputs(InpaintBuilder.N_VYREZ).getBoolean("mask_fill_holes"))
        bezVisicichOdkazu(wf)
    }

    @Test fun `pokyn nese smer a vede operace`() {
        val dolu = InpaintBuilder.zadaniRozsireni("nohy v džínách",
            setOf(cz.promptlab.h3video.data.Smer.DOLU))
        assertTrue(dolu.startsWith("Extend the picture in <image1> downward"))
        assertTrue(dolu.contains("nohy v džínách"))
        assertTrue(dolu.contains("<image2>"))
        assertTrue(dolu.indexOf("downward") < dolu.indexOf("must stay exactly as it is"))

        assertEquals("downward and to the left", InpaintBuilder.smeryVetou(
            setOf(cz.promptlab.h3video.data.Smer.VLEVO, cz.promptlab.h3video.data.Smer.DOLU)))
        // Pořadí je dané enumem, ne pořadím klikání — jinak by se stejné
        // zadání pokaždé přeložilo jinak a seed by přestal být opakovatelný.
        assertEquals(
            InpaintBuilder.smeryVetou(setOf(cz.promptlab.h3video.data.Smer.DOLU, cz.promptlab.h3video.data.Smer.VLEVO)),
            InpaintBuilder.smeryVetou(setOf(cz.promptlab.h3video.data.Smer.VLEVO, cz.promptlab.h3video.data.Smer.DOLU)),
        )
    }

    @Test fun `karta nepusti rozsireni bez smeru ani bez zadani`() {
        val zaklad = InpaintScene(
            source = File("a.png"),
            rezim = cz.promptlab.h3video.data.InpaintRezim.ROZSIRIT,
            prompt = "nohy",
        )
        assertNull(cz.promptlab.h3video.data.inpaintProblem(zaklad))
        // Bez štětce se u rozšíření startovat smí — masku dělá graf.
        assertFalse(zaklad.maskPainted)
        assertNotNull(cz.promptlab.h3video.data.inpaintProblem(zaklad.copy(smery = emptySet())))
        assertNotNull(cz.promptlab.h3video.data.inpaintProblem(zaklad.copy(prompt = "")))
    }

    @Test fun `rozsireni predloha nenese zadani predchoziho behu`() {
        val p = JSONObject(rozsireni)
        assertEquals("", p.inputs(InpaintBuilder.N_IMAGE).getString("image"))
        assertEquals("", p.inputs(InpaintBuilder.N_TEXT).getString("prompt"))
    }
}
