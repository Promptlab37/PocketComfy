package cz.promptlab.h3video

import cz.promptlab.h3video.comfy.ImagePromptBuilder
import cz.promptlab.h3video.comfy.Ltx25Builder
import cz.promptlab.h3video.comfy.Ltx25PromptBuilder
import cz.promptlab.h3video.data.LtxRezim
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Přepisovač popisu na kartě LTX 2.5.
 *
 * Hlídá dvě věci, které se snadno tiše rozbijí: že oficiální cesta jede nad
 * **enkodérem z předlohy** (ne nad náhodně vybraným souborem) a že systémový
 * prompt odpovídá tomu, co karta modelu v daném režimu opravdu dá.
 */
class Ltx25PromptBuilderTest {

    private fun inputs(wf: JSONObject, uzel: String): JSONObject =
        wf.getJSONObject(uzel).getJSONObject("inputs")

    private val predloha = """
        {
          "418": {"class_type": "CLIPLoader",
                  "inputs": {"clip_name": "gemma4-12b-neco.safetensors",
                             "type": "ltxv", "device": "default"}}
        }
    """.trimIndent()

    @Test
    fun `oficialni prepisovac jede nad enkoderem z predlohy`() {
        val encoder = Ltx25Builder.encoderZPredlohy(predloha)
        assertEquals("gemma4-12b-neco.safetensors", encoder)

        val wf = Ltx25PromptBuilder.buildOficialni("les v mlze", encoder, null, seed = 7)
        val clip = inputs(wf, Ltx25PromptBuilder.N_CLIP)
        assertEquals(encoder, clip.getString("clip_name"))
        // `ltxv` je jediný typ, pod kterým ComfyUI gemma4 pro LTX načte.
        assertEquals(Ltx25PromptBuilder.CLIP_TYPE, clip.getString("type"))
        assertEquals(
            Ltx25PromptBuilder.NODE_CLASS,
            wf.getJSONObject(Ltx25PromptBuilder.N_GEN).getString("class_type"),
        )
    }

    @Test
    fun `bez fotky se obrazovy vstup vubec nezapoji`() {
        // Uzel si podle přítomnosti obrázku vybírá i2v/t2v pravidla sám.
        // Prázdný LoadImage by ho poslal do i2v větve bez prvního snímku.
        val bez = Ltx25PromptBuilder.buildOficialni("x", "enc", null, seed = 1)
        assertFalse(bez.has(Ltx25PromptBuilder.N_OBRAZEK))
        assertFalse(inputs(bez, Ltx25PromptBuilder.N_GEN).has("image"))

        val s = Ltx25PromptBuilder.buildOficialni("x", "enc", "prvni.png", seed = 1)
        assertEquals("prvni.png", inputs(s, Ltx25PromptBuilder.N_OBRAZEK).getString("image"))
        assertTrue(inputs(s, Ltx25PromptBuilder.N_GEN).has("image"))
    }

    @Test
    fun `uvazovani nahlas je vypnute a strop je rozumny`() {
        val wf = Ltx25PromptBuilder.buildOficialni("x", "enc", null, seed = 1)
        val g = inputs(wf, Ltx25PromptBuilder.N_GEN)
        // Rozvaha by na domácí kartě sežrala celý strop dřív, než model
        // napíše popisek — ověřeno u Qwenova PE.
        assertFalse(g.getBoolean("thinking"))
        // 150–220 slov je kolem 300 tokenů; strop musí nechat rezervu,
        // ale nesmí být nekonečný (past s 24000 tokeny u Qwen 2.1).
        assertTrue(g.getInt("max_length") in 384..1024)
    }

    @Test
    fun `kazdy rezim ma svuj systemovy prompt`() {
        val t2v = Ltx25PromptBuilder.systemProRezim(LtxRezim.TEXT)
        val i2v = Ltx25PromptBuilder.systemProRezim(LtxRezim.OBRAZEK)
        val zvuk = Ltx25PromptBuilder.systemProRezim(LtxRezim.ZVUK)
        assertTrue(setOf(t2v, i2v, zvuk).size == 3)

        // Bez fotky se o prvním snímku mluvit nesmí — model by popisoval
        // něco, co žádný vstup nenese.
        assertFalse(t2v.contains("first frame"))
        assertTrue(i2v.contains("first frame"))

        // Hotová zvuková stopa je daná. Vymyšlená hudba a ruch by tahaly
        // obraz proti tomu, co je opravdu slyšet.
        assertTrue(zvuk.contains("Do not invent music"))
        assertFalse(i2v.contains("Do not invent music"))
    }

    @Test
    fun `vsechny systemove prompty nesou zavazna pravidla LTX`() {
        for (rezim in LtxRezim.entries) {
            val s = Ltx25PromptBuilder.systemProRezim(rezim)
            val chybi = listOf(
                // trojice, kterou model čeká u každého záběru
                "extreme close-up", "camera", "front-facing",
                // jeden odstavec, chronologicky, anglicky
                "single paragraph", "Simultaneously", "always English",
            ).filterNot { s.contains(it, ignoreCase = true) }
            assertEquals("$rezim postrádá: $chybi", emptyList<String>(), chybi)
        }
    }

    @Test
    fun `odvazany graf jede pres llama uzly a nese delku`() {
        val wf = Ltx25PromptBuilder.buildOdvazany(
            rezim = LtxRezim.OBRAZEK,
            zadani = "sedí v lese",
            sekundy = 8f,
            model = "Huihui-Qwen3-VL-8B-abliterated-Q6_K.gguf",
            seed = 3,
            mmproj = "Huihui-Qwen3-VL-8B-Instruct-abliterated-mmproj-F16.gguf",
            obrazky = listOf("a.png"),
        )
        assertEquals(
            ImagePromptBuilder.NODE_CLASS,
            wf.getJSONObject(ImagePromptBuilder.N_LLM).getString("class_type"),
        )
        val llm = inputs(wf, ImagePromptBuilder.N_LLM)
        assertTrue(llm.getString("system_prompt").contains("first frame"))
        val zadani = llm.getString("custom_prompt")
        assertTrue(zadani.contains("8 seconds"))
        assertTrue(zadani.contains("sedí v lese"))
        // S projektorem fotku opravdu vidí — smí se o ní tedy mluvit.
        assertTrue(zadani.contains("exact first frame"))
        assertTrue(llm.has("images"))
    }

    @Test
    fun `bez projektoru si model nesmi vymyslet, co na fotce je`() {
        val zadani = Ltx25PromptBuilder.sKontextem(
            LtxRezim.OBRAZEK, "rozhýbej to", sekundy = 5f, vidiFotku = false,
        )
        assertTrue(zadani.contains("cannot see it"))
        assertTrue(zadani.contains("do not invent"))
        // U „Z textu" žádná fotka není, takže se o ní nemluví vůbec.
        val bezFotky = Ltx25PromptBuilder.sKontextem(
            LtxRezim.TEXT, "les", sekundy = 5f, vidiFotku = false,
        )
        assertFalse(bezFotky.contains("photo"))
    }
}
