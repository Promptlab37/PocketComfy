package cz.promptlab.h3video

import cz.promptlab.h3video.comfy.Trellis2Builder
import cz.promptlab.h3video.data.Model3dMotor
import cz.promptlab.h3video.data.Model3dScene
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Větev **Pixal3D** karty 3D model.
 *
 * Obě větve jsou z téže ukázkové šablony ComfyUI. Rozdíl není v nastavení,
 * ale v tom, co model o fotce ví: TRELLIS z ní udělá jediný otisk, Pixal3D
 * k tomu přidá podrobnosti z celé plochy a odhadnutý úhel záběru objektivu.
 * Proto k ní patří i jiné váhy a jiný ořez — a to se tu hlídá, protože
 * záměna by skončila buď pádem, nebo tiše horším modelem.
 */
class Pixal3dTest {

    private val sablona: String
        get() = File("src/main/res/raw/workflow_trellis2.json").readText()

    private fun graf(motor: Model3dMotor): JSONObject = Trellis2Builder.build(
        template = sablona,
        scene = Model3dScene(source = null, motor = motor),
        seed = 9L, images = listOf("foto.png"),
    )

    private fun vstupy(wf: JSONObject, id: String): JSONObject =
        wf.getJSONObject(id).getJSONObject("inputs")

    private fun zdroj(wf: JSONObject, id: String, vstup: String): Pair<String, Int> {
        val a = vstupy(wf, id).getJSONArray(vstup)
        return a.getString(0) to a.getInt(1)
    }

    @Test
    fun `pixal3d ma vlastni vahy, enkoder i orez`() {
        val wf = graf(Model3dMotor.PIXAL3D)

        assertEquals(
            "pixal3d_int8_convrot.safetensors",
            vstupy(wf, Trellis2Builder.N_UNET).getString("unet_name"),
        )
        // Obyčejný DINOv3 nemá NAF modul, který si Pixal3D bere.
        assertEquals(
            "dino_v3_L_naf_fp32.safetensors",
            vstupy(wf, Trellis2Builder.N_CLIP_VISION).getString("clip_name"),
        )
        assertEquals(1.1, vstupy(wf, Trellis2Builder.N_CROP).getDouble("pad_factor"), 1e-9)
    }

    @Test
    fun `uhel zaberu se cte z vodorovne osy`() {
        // Uzel má ve výchozím stavu svislou osu. Kdyby se to nechalo být,
        // model dostane jiné číslo, než na jaké byl trénovaný — a nic
        // nespadne, jen bude výsledek hůř posazený.
        val wf = graf(Model3dMotor.PIXAL3D)
        assertEquals("horizontal", vstupy(wf, Trellis2Builder.N_FOV).getString("axis"))
        assertEquals("degrees", vstupy(wf, Trellis2Builder.N_FOV).getString("unit"))
    }

    @Test
    fun `retez odhadu geometrie vede az do zadani`() {
        val wf = graf(Model3dMotor.PIXAL3D)

        assertEquals(Trellis2Builder.N_MOGE_MODEL to 0, zdroj(wf, Trellis2Builder.N_MOGE, "moge_model"))
        assertEquals(Trellis2Builder.N_CROP to 0, zdroj(wf, Trellis2Builder.N_MOGE, "image"))
        assertEquals(Trellis2Builder.N_MOGE to 0, zdroj(wf, Trellis2Builder.N_FOV, "moge_geometry"))
        assertEquals(
            Trellis2Builder.N_FOV to 0,
            zdroj(wf, Trellis2Builder.N_PIXAL_COND, "camera_angle_x"),
        )
        // Zadání i odhad geometrie musí vidět TUTÉŽ oříznutou fotku.
        assertEquals(Trellis2Builder.N_CROP to 0, zdroj(wf, Trellis2Builder.N_PIXAL_COND, "image"))
    }

    @Test
    fun `tvarova faze si bere oba vystupy nove vetve`() {
        val wf = graf(Model3dMotor.PIXAL3D)
        assertEquals(
            Trellis2Builder.N_PIXAL_COND to 0,
            zdroj(wf, Trellis2Builder.N_SHAPE_STAGE, "positive"),
        )
        assertEquals(
            Trellis2Builder.N_PIXAL_COND to 1,
            zdroj(wf, Trellis2Builder.N_SHAPE_STAGE, "negative"),
        )
        assertFalse("staré zadání už nikam nevede", wf.has(Trellis2Builder.N_COND))
    }

    @Test
    fun `trellis zustava presne jako driv`() {
        val wf = graf(Model3dMotor.TRELLIS)

        assertEquals(
            "trellis_2_int8_convrot.safetensors",
            vstupy(wf, Trellis2Builder.N_UNET).getString("unet_name"),
        )
        assertEquals(
            "dino_v3_vit_l.safetensors",
            vstupy(wf, Trellis2Builder.N_CLIP_VISION).getString("clip_name"),
        )
        assertEquals(1.0, vstupy(wf, Trellis2Builder.N_CROP).getDouble("pad_factor"), 1e-9)
        assertTrue(wf.has(Trellis2Builder.N_COND))
        assertEquals(
            Trellis2Builder.N_COND to 0,
            zdroj(wf, Trellis2Builder.N_SHAPE_STAGE, "positive"),
        )
        listOf(
            Trellis2Builder.N_MOGE_MODEL, Trellis2Builder.N_MOGE,
            Trellis2Builder.N_FOV, Trellis2Builder.N_PIXAL_COND,
        ).forEach { assertFalse("uzel $it do TRELLIS větve nepatří", wf.has(it)) }
    }

    @Test
    fun `zadny uzel neodkazuje na neexistujici`() {
        Model3dMotor.entries.forEach { motor ->
            val wf = graf(motor)
            val ids = wf.keys().asSequence().toSet()
            wf.keys().forEach { id ->
                val ins = wf.getJSONObject(id).getJSONObject("inputs")
                ins.keys().forEach { key ->
                    val v = ins.opt(key)
                    if (v is org.json.JSONArray && v.length() == 2 && v.opt(0) is String) {
                        assertTrue(
                            "$motor: $id.$key vede na neexistující uzel ${v.getString(0)}",
                            v.getString(0) in ids,
                        )
                    }
                }
            }
        }
    }
}
