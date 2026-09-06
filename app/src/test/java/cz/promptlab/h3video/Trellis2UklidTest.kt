package cz.promptlab.h3video

import cz.promptlab.h3video.comfy.Trellis2Builder
import cz.promptlab.h3video.data.Model3dKvalita
import cz.promptlab.h3video.data.Model3dScene
import cz.promptlab.h3video.ui.radaKChybe
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Úklid paměti grafiky uvnitř grafu 3D modelu.
 *
 * Běh na „Maximální" spadl v `RemeshMesh`: obsazeno 13,4 GB z 16, volných
 * 0 bajtů. Síťové fáze si berou paměť mimo správu ComfyUI, takže o ní ComfyUI
 * neví a difuzní modely drží dál. Test hlídá, že úklid opravdu **stojí
 * v cestě dat** — jako slepé rameno by ComfyUI neměl důvod ho spustit včas.
 */
class Trellis2UklidTest {

    private val sablona: String
        get() = File("src/main/res/raw/workflow_trellis2.json").readText()

    private fun scena(kvalita: Model3dKvalita = Model3dKvalita.MAXIMALNI) =
        Model3dScene(source = null, kvalita = kvalita)

    private fun uzel(wf: JSONObject, id: String): JSONObject =
        wf.getJSONObject(id).getJSONObject("inputs")

    private fun zdroj(wf: JSONObject, id: String, vstup: String): String =
        uzel(wf, id).getJSONArray(vstup).getString(0)

    private fun graf(uklid: Boolean): JSONObject = Trellis2Builder.build(
        template = sablona, scene = scena(), seed = 11L,
        images = listOf("foto.png"), uklidVram = uklid,
    )

    @Test
    fun `uklid stoji v ceste pred remeshem i pred pecenim`() {
        val wf = graf(uklid = true)

        assertEquals(
            Trellis2Builder.N_MESHINFO,
            zdroj(wf, Trellis2Builder.N_UKLID_SIT, "anything"),
        )
        assertEquals(
            Trellis2Builder.N_UKLID_SIT,
            zdroj(wf, Trellis2Builder.N_REMESH, "mesh"),
        )

        assertEquals(
            Trellis2Builder.N_DECODE_TEXTURE,
            zdroj(wf, Trellis2Builder.N_UKLID_PECENI, "anything"),
        )
        assertEquals(
            Trellis2Builder.N_UKLID_PECENI,
            zdroj(wf, Trellis2Builder.N_BAKE, "voxel_colors"),
        )

        for (id in listOf(Trellis2Builder.N_UKLID_SIT, Trellis2Builder.N_UKLID_PECENI)) {
            assertEquals(Trellis2Builder.TRIDA_UKLIDU, wf.getJSONObject(id).getString("class_type"))
            assertTrue(uzel(wf, id).getBoolean("offload_model"))
            assertTrue(uzel(wf, id).getBoolean("offload_cache"))
        }
    }

    @Test
    fun `server bez toho uzlu dostane graf jako driv`() {
        // Uzel navíc, který server nezná, neshodí jen sebe — shodí celý běh
        // na kontrole grafu. Proto se bez něj musí graf postavit beze změny.
        val wf = graf(uklid = false)
        assertFalse(wf.has(Trellis2Builder.N_UKLID_SIT))
        assertFalse(wf.has(Trellis2Builder.N_UKLID_PECENI))
        assertEquals(Trellis2Builder.N_MESHINFO, zdroj(wf, Trellis2Builder.N_REMESH, "mesh"))
        assertEquals(
            Trellis2Builder.N_DECODE_TEXTURE,
            zdroj(wf, Trellis2Builder.N_BAKE, "voxel_colors"),
        )
    }

    @Test
    fun `uklid stoji i pred prevodem tvaru na sit`() {
        // Paměťový vrchol celého běhu. Oba dřívější úklidy byly až ZA ním,
        // takže tomu pádu na 1536 vůbec nepomáhaly.
        val wf = graf(uklid = true)
        assertEquals(
            Trellis2Builder.N_KS_UPSAMPLE,
            zdroj(wf, Trellis2Builder.N_UKLID_TVAR, "anything"),
        )
        assertEquals(
            Trellis2Builder.N_UKLID_TVAR,
            zdroj(wf, Trellis2Builder.N_DECODE_SHAPE, "samples"),
        )
    }

    @Test
    fun `nejvyssi kvalita odpovida ukazkove sablone`() {
        // Předloha ComfyUI: tvar 1536, textura 4096, remesh 768, 700k ploch.
        assertTrue(1536 in cz.promptlab.h3video.data.Model3dScene.DETAILY)
        assertTrue(4096 in cz.promptlab.h3video.data.Model3dScene.TEXTURY)
        assertEquals(768, cz.promptlab.h3video.data.Model3dKvalita.MAXIMALNI.remesh)
        assertEquals(700_000, cz.promptlab.h3video.data.Model3dKvalita.MAXIMALNI.plochy)
    }

    @Test
    fun `maximalni kvalita zvedne kroky vzorkovani na doporucenych 20`() {
        // Do 3.28 se kroky nenastavovaly vůbec a jelo se na 12/20/12/12
        // z šablony. Autoři modelu doporučují pro produkční kvalitu 16–20
        // a krok navíc stojí čas, ne paměť grafiky.
        val wf = Trellis2Builder.build(
            template = sablona,
            scene = Model3dScene(source = null, kvalita = Model3dKvalita.MAXIMALNI),
            seed = 4L, images = listOf("foto.png"),
        )
        listOf(
            Trellis2Builder.N_KS_STRUCTURE, Trellis2Builder.N_KS_SHAPE,
            Trellis2Builder.N_KS_UPSAMPLE, Trellis2Builder.N_KS_TEXTURE,
        ).forEach { assertEquals(20, uzel(wf, it).getInt("steps")) }
    }

    @Test
    fun `nizsi kvalita nechava kroky sablony`() {
        val wf = Trellis2Builder.build(
            template = sablona,
            scene = Model3dScene(source = null, kvalita = Model3dKvalita.PBR),
            seed = 4L, images = listOf("foto.png"),
        )
        assertEquals(12, uzel(wf, Trellis2Builder.N_KS_STRUCTURE).getInt("steps"))
        assertEquals(20, uzel(wf, Trellis2Builder.N_KS_SHAPE).getInt("steps"))
        assertEquals(12, uzel(wf, Trellis2Builder.N_KS_UPSAMPLE).getInt("steps"))
        assertEquals(12, uzel(wf, Trellis2Builder.N_KS_TEXTURE).getInt("steps"))
    }

    @Test
    fun `cfg a sampler zustavaji z predlohy`() {
        // Vodítko autorů k síle navádění mluví o jejich vlastním rozhraní;
        // tenhle graf jede navíc přes CFGOverride a RescaleCFG, takže by
        // přepsané cfg znamenalo něco jiného. Nesahá se na ně.
        val puvodni = JSONObject(sablona)
        val wf = Trellis2Builder.build(
            template = sablona,
            scene = Model3dScene(source = null, kvalita = Model3dKvalita.MAXIMALNI),
            seed = 4L, images = listOf("foto.png"),
        )
        listOf(
            Trellis2Builder.N_KS_STRUCTURE, Trellis2Builder.N_KS_SHAPE,
            Trellis2Builder.N_KS_UPSAMPLE, Trellis2Builder.N_KS_TEXTURE,
        ).forEach { id ->
            assertEquals(uzel(puvodni, id).getDouble("cfg"), uzel(wf, id).getDouble("cfg"), 1e-9)
            assertEquals(
                uzel(puvodni, id).getString("sampler_name"),
                uzel(wf, id).getString("sampler_name"),
            )
        }
    }

    @Test
    fun `uklid neprebiji hodnoty kvality`() {
        val wf = graf(uklid = true)
        assertEquals(
            Model3dKvalita.MAXIMALNI.remesh,
            uzel(wf, Trellis2Builder.N_REMESH).getInt("resolution"),
        )
    }

    @Test
    fun `rada u padu v siti mluvi o kvalite, ne o jemnosti tvaru`() {
        val hlaska = "Error in node RemeshMesh: Allocation on device 0 would exceed " +
            "allowed memory. (out of memory)"
        val rada = radaKChybe(hlaska)!!

        assertTrue(rada.contains("Kvalitu"))
        assertFalse("jemnost tvaru se sítě netýká", rada.contains("Jemnost tvaru"))
    }
}
