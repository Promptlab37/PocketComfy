package cz.promptlab.h3video

import cz.promptlab.h3video.comfy.Stage
import cz.promptlab.h3video.comfy.Trellis2Builder
import cz.promptlab.h3video.data.Model3dKvalita
import cz.promptlab.h3video.data.Model3dScene
import cz.promptlab.h3video.data.model3dProblem
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Karta **3D model** (TRELLIS.2, nativní uzly ComfyUI).
 *
 * Předloha vznikla z oficiálního workflow ComfyUI narovnaného do API podoby,
 * takže testy hlídají hlavně to, co se při tom narovnávání dá pokazit:
 * zapojení čtyř průchodů za sebou, správný konec grafu a to, že se maska
 * bere z odstranění pozadí (fotka z telefonu alfu nemá).
 */
class Trellis2BuilderTest {

    private val sablona: String =
        File("src/main/res/raw/workflow_trellis2.json").readText()

    private fun JSONObject.inputs(node: String): JSONObject =
        getJSONObject(node).getJSONObject("inputs")

    private fun JSONObject.classOf(node: String): String =
        getJSONObject(node).getString("class_type")

    private fun scene(
        kvalita: Model3dKvalita = Model3dKvalita.PBR,
        detail: Int = 1536,
        textura: Int = 2048,
    ) = Model3dScene(source = File("predmet.jpg"), kvalita = kvalita, detail = detail, textura = textura)

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
        Model3dKvalita.entries.forEach { k ->
            val wf = Trellis2Builder.build(sablona, scene(k), 7L, listOf("a.jpg"))
            assertEquals(k.name, emptyList<String>(), visiciOdkazy(wf))
        }
    }

    @Test
    fun `dosadi se fotka, detail a textura`() {
        val wf = Trellis2Builder.build(sablona, scene(detail = 2048, textura = 4096), 7L, listOf("a.jpg"))
        assertEquals("a.jpg", wf.inputs(Trellis2Builder.N_IMAGE).getString("image"))
        assertEquals(2048, wf.inputs(Trellis2Builder.N_UPSAMPLE).getInt("target_resolution"))
        assertEquals(4096, wf.inputs(Trellis2Builder.N_TEXTURE_SIZE).getInt("value"))
    }

    /**
     * Fotka z telefonu alfu nemá. Kdyby maska šla z alfa kanálu jako
     * v oficiální předloze, byla by prázdná a výřez by nevznikl.
     */
    @Test
    fun `maska jde z odstraneni pozadi, ne z alfy fotky`() {
        val wf = Trellis2Builder.build(sablona, scene(), 7L, listOf("a.jpg"))
        val crop = wf.inputs(Trellis2Builder.N_CROP)
        assertEquals(Trellis2Builder.N_BG, crop.getJSONArray("masks").getString(0))
        assertEquals("RemoveBackground", wf.classOf(Trellis2Builder.N_BG))
        // Obraz naopak jde z fotky napřímo.
        assertEquals(Trellis2Builder.N_IMAGE, crop.getJSONArray("images").getString(0))
        // Odstranění pozadí bere tutéž fotku.
        assertEquals(
            Trellis2Builder.N_IMAGE,
            wf.inputs(Trellis2Builder.N_BG).getJSONArray("image").getString(0)
        )
    }

    /**
     * Tooltip uzlu `Trellis2Conditioning` říká pad_factor 1.0 pro TRELLIS.2
     * (1.1 má oficiální předloha kvůli sdílení s Pixal3D).
     */
    @Test
    fun `vyrez pouziva pad factor pro trellis`() {
        val wf = JSONObject(sablona)
        assertEquals(1.0, wf.inputs(Trellis2Builder.N_CROP).getDouble("pad_factor"), 0.001)
    }

    @Test
    fun `kvalita prepina konec grafu`() {
        val pbr = Trellis2Builder.build(sablona, scene(Model3dKvalita.PBR), 7L, listOf("a.jpg"))
        assertEquals(
            Trellis2Builder.N_PBR,
            pbr.inputs(Trellis2Builder.N_SAVE).getJSONArray("mesh").getString(0)
        )
        val rychla = Trellis2Builder.build(sablona, scene(Model3dKvalita.RYCHLA), 7L, listOf("a.jpg"))
        assertEquals(
            Trellis2Builder.N_PAINT,
            rychla.inputs(Trellis2Builder.N_SAVE).getJSONArray("mesh").getString(0)
        )
        assertEquals("SaveGLB", pbr.classOf(Trellis2Builder.N_SAVE))
    }

    /**
     * Čtyři průchody musí jít v pořadí struktura → tvar → zjemnění → textura
     * a každý navazovat na výstup předchozího. Tohle je při narovnávání cizího
     * grafu ta nejsnáz rozbitelná věc.
     */
    @Test
    fun `ctyri pruchody navazuji ve spravnem poradi`() {
        val wf = Trellis2Builder.build(sablona, scene(), 7L, listOf("a.jpg"))
        // struktura -> voxel
        assertEquals(
            Trellis2Builder.N_KS_STRUCTURE,
            wf.inputs("119").getJSONArray("samples").getString(0)
        )
        // voxel -> tvar
        assertEquals("119", wf.inputs("91").getJSONArray("voxel").getString(0))
        // tvar -> zjemneni
        assertEquals(
            Trellis2Builder.N_KS_SHAPE,
            wf.inputs(Trellis2Builder.N_UPSAMPLE).getJSONArray("shape_latent").getString(0)
        )
        // zjemneni -> textura
        assertEquals(
            Trellis2Builder.N_KS_UPSAMPLE,
            wf.inputs("98").getJSONArray("shape_latent").getString(0)
        )
        // textura -> dekodovani barev
        assertEquals(
            Trellis2Builder.N_KS_TEXTURE,
            wf.inputs("93").getJSONArray("samples").getString(0)
        )
    }

    @Test
    fun `kazdy pruchod ma vlastni seed`() {
        val wf = Trellis2Builder.build(sablona, scene(), 1000L, listOf("a.jpg"))
        val seedy = listOf(
            Trellis2Builder.N_KS_STRUCTURE, Trellis2Builder.N_KS_SHAPE,
            Trellis2Builder.N_KS_UPSAMPLE, Trellis2Builder.N_KS_TEXTURE,
        ).map { wf.inputs(it).getLong("seed") }
        assertEquals(4, seedy.toSet().size)
        seedy.forEach { assertTrue("seed $it je mimo rozsah", it in 0..0xFFFF_FFFFL) }
        // Jiný seed = jiný graf.
        val jiny = Trellis2Builder.build(sablona, scene(), 2000L, listOf("a.jpg"))
        assertNotEquals(wf.toString(), jiny.toString())
    }

    @Test
    fun `vyladene hodnoty predlohy zustavaji`() {
        val wf = Trellis2Builder.build(sablona, scene(), 7L, listOf("a.jpg"))
        assertEquals(12, wf.inputs(Trellis2Builder.N_KS_STRUCTURE).getInt("steps"))
        assertEquals(20, wf.inputs(Trellis2Builder.N_KS_SHAPE).getInt("steps"))
        // Texturový průchod jede na cfg 1 — v předloze schválně jinak než ostatní.
        assertEquals(1.0, wf.inputs(Trellis2Builder.N_KS_TEXTURE).getDouble("cfg"), 0.001)
        assertEquals(7.5, wf.inputs(Trellis2Builder.N_KS_SHAPE).getDouble("cfg"), 0.001)
        assertEquals(Trellis2Builder.STEPS_CELKEM, 12 + 20 + 12 + 12)
    }

    @Test
    fun `modely sedi s tim, co se stahovalo`() {
        val wf = JSONObject(sablona)
        assertEquals(
            "trellis_2_int8_convrot.safetensors",
            wf.inputs(Trellis2Builder.N_UNET).getString("unet_name")
        )
        assertEquals(
            "dino_v3_vit_l.safetensors",
            wf.inputs(Trellis2Builder.N_CLIP_VISION).getString("clip_name")
        )
        assertEquals(
            "trellis_2_shape_vae_bf16.safetensors",
            wf.inputs(Trellis2Builder.N_VAE_SHAPE).getString("vae_name")
        )
        assertEquals(
            "trellis_2_texture_vae_bf16.safetensors",
            wf.inputs(Trellis2Builder.N_VAE_TEXTURE).getString("vae_name")
        )
        assertEquals(
            "birefnet.safetensors",
            wf.inputs(Trellis2Builder.N_BG_MODEL).getString("bg_removal_name")
        )
    }

    @Test
    fun `co karte chybi`() {
        assertNull(model3dProblem(scene()))
        assertNotNull(model3dProblem(Model3dScene()))
    }

    @Test
    fun `faze podle tridy uzlu`() {
        assertEquals(Stage.SAMPLING, Trellis2Builder.stageForClass("KSampler"))
        assertEquals(Stage.MODELS, Trellis2Builder.stageForClass("UNETLoader"))
        assertEquals(Stage.REFERENCES, Trellis2Builder.stageForClass("RemoveBackground"))
        assertEquals(Stage.MUXING, Trellis2Builder.stageForClass("UnwrapMesh"))
        assertEquals(Stage.FINISHING, Trellis2Builder.stageForClass("SaveGLB"))
        assertTrue(Trellis2Builder.reportsSteps("KSampler"))
        assertFalse(Trellis2Builder.reportsSteps("SaveGLB"))
    }
}
