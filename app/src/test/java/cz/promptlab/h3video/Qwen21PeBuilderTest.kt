package cz.promptlab.h3video

import cz.promptlab.h3video.comfy.ImagePromptBuilder
import cz.promptlab.h3video.comfy.Qwen21PeBuilder
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Přepisovač promptu od Qwenu. Hlídá hodnoty z referenčního skriptu autorů —
 * ty se liší od výchozích hodnot uzlu `TextGenerate` a ovlivňují výstup.
 */
class Qwen21PeBuilderTest {

    private fun inputs(wf: JSONObject, uzel: String): JSONObject =
        wf.getJSONObject(uzel).getJSONObject("inputs")

    @Test
    fun `chat zacina systemovou zpravou, jinak ji ComfyUI zahodi`() {
        val text = Qwen21PeBuilder.chatText("SYSTEM", "uprav oblohu", 0)
        // Tokenizér v qwen35.py použije text beze změny jen tehdy, když začíná
        // na <|im_start|>. Cokoli jiného znamená ztrátu systémového promptu.
        assertTrue(text.startsWith("<|im_start|>"))
        assertTrue(text.contains("<|im_start|>system\nSYSTEM<|im_end|>"))
        assertTrue(text.contains("uprav oblohu"))
        // Bez uvažování končí chat prázdným blokem <think> — model pak píše
        // rovnou odpověď místo tisíců tokenů rozvahy.
        assertTrue(text.endsWith("<|im_start|>assistant\n<think>\n</think>\n"))
        assertTrue(
            Qwen21PeBuilder.chatText("S", "x", 0, uvazovani = true)
                .endsWith("<|im_start|>assistant\n")
        )
    }

    @Test
    fun `kazda fotka ma svuj obrazovy blok`() {
        val bez = Qwen21PeBuilder.chatText("S", "x", 0)
        val dve = Qwen21PeBuilder.chatText("S", "x", 2)
        assertEquals(0, Regex("<\\|image_pad\\|>").findAll(bez).count())
        assertEquals(2, Regex("<\\|image_pad\\|>").findAll(dve).count())
    }

    @Test
    fun `vzorkovani drzi hodnoty z referencniho skriptu`() {
        val wf = Qwen21PeBuilder.build(
            "S", "zadani", Qwen21PeBuilder.MODEL_T2I, emptyList(),
            Qwen21PeBuilder.MAX_TOKENU_T2I, 42L,
        )
        val g = inputs(wf, Qwen21PeBuilder.N_GEN)
        assertEquals("on", g.getString("sampling_mode"))
        assertEquals(1.0, g.getDouble("sampling_mode.temperature"), 1e-9)
        assertEquals(20, g.getInt("sampling_mode.top_k"))
        assertEquals(0.95, g.getDouble("sampling_mode.top_p"), 1e-9)
        // Uzel má ve výchozím stavu min_p 0.05 a repetition_penalty 1.05;
        // autoři je nepředávají, takže musí zůstat neutrální.
        assertEquals(0.0, g.getDouble("sampling_mode.min_p"), 1e-9)
        assertEquals(1.0, g.getDouble("sampling_mode.repetition_penalty"), 1e-9)
        // Výchozí stav je bez rozvahy: s ní model na téhle kartě píše
        // desítky minut (změřeno 21. 9. 2026, ~2,8 tokenu za vteřinu).
        assertFalse(g.getBoolean("thinking"))
    }

    @Test
    fun `rychly rezim ma rozumny strop, aby se beh nemohl rozjet`() {
        val wf = Qwen21PeBuilder.build(
            "S", "x", Qwen21PeBuilder.MODEL_T2I, emptyList(),
            Qwen21PeBuilder.MAX_TOKENU_RYCHLE, 1L,
        )
        assertEquals(640, inputs(wf, Qwen21PeBuilder.N_GEN).getInt("max_length"))
        assertTrue(Qwen21PeBuilder.MAX_TOKENU_RYCHLE < Qwen21PeBuilder.MAX_TOKENU_T2I)
    }

    @Test
    fun `delka odpovedi je autorova, ne vychozich 512`() {
        val i2i = Qwen21PeBuilder.build(
            "S", "x", Qwen21PeBuilder.MODEL_I2I, listOf("a.png"),
            Qwen21PeBuilder.MAX_TOKENU_I2I, 1L,
        )
        val t2i = Qwen21PeBuilder.build(
            "S", "x", Qwen21PeBuilder.MODEL_T2I, emptyList(),
            Qwen21PeBuilder.MAX_TOKENU_T2I, 1L,
        )
        assertEquals(24000, inputs(i2i, Qwen21PeBuilder.N_GEN).getInt("max_length"))
        assertEquals(16256, inputs(t2i, Qwen21PeBuilder.N_GEN).getInt("max_length"))
    }

    @Test
    fun `bez fotek se obrazovy vstup vubec neposila`() {
        val wf = Qwen21PeBuilder.build(
            "S", "x", Qwen21PeBuilder.MODEL_T2I, emptyList(),
            Qwen21PeBuilder.MAX_TOKENU_T2I, 1L,
        )
        assertTrue(!inputs(wf, Qwen21PeBuilder.N_GEN).has("image"))
        assertTrue(!wf.has(Qwen21PeBuilder.N_OBRAZEK_PRVNI.toString()))
        assertTrue(!wf.has(Qwen21PeBuilder.N_ZMENSENI_PRVNI.toString()))
    }

    @Test
    fun `vic fotek se slepi do jedne davky`() {
        val wf = Qwen21PeBuilder.build(
            "S", "x", Qwen21PeBuilder.MODEL_I2I, listOf("a.png", "b.png", "c.png"),
            Qwen21PeBuilder.MAX_TOKENU_I2I, 1L,
        )
        // Tři LoadImage, ke každé zmenšení, a dva ImageBatch.
        assertEquals("a.png", inputs(wf, "100").getString("image"))
        assertEquals("c.png", inputs(wf, "102").getString("image"))
        assertEquals("ImageBatch", wf.getJSONObject("201").getString("class_type"))
        val vstup = inputs(wf, Qwen21PeBuilder.N_GEN).getJSONArray("image")
        assertEquals("201", vstup.getString(0))
        // Do dávky jdou ZMENŠENÉ fotky, ne originály — jinak přepisovač počítá
        // přes tisíce obrazových tokenů a spadne z ~29 na 2 tokeny za vteřinu.
        assertEquals("100", inputs(wf, "150").getJSONArray("image").getString(0))
        assertEquals(Qwen21PeBuilder.PREDLOHA_MAX_PX, inputs(wf, "150").getInt("largest_size"))
        assertEquals("150", inputs(wf, "200").getJSONArray("image1").getString(0))
        assertEquals("151", inputs(wf, "200").getJSONArray("image2").getString(0))
        assertEquals("152", inputs(wf, "201").getJSONArray("image2").getString(0))
    }

    @Test
    fun `z odpovedi se vytahne prompt za blokem uvahy`() {
        val odpoved = """<think>chvíli přemýšlím</think>
            {"rewritten_prompt": "A cat on a red sofa", "wh_ratio": "16:9", "ratio_follow": ""}"""
        val v = Qwen21PeBuilder.parse(odpoved)!!
        assertEquals("A cat on a red sofa", v.prompt)
        assertEquals("16:9", v.pomer)
        assertEquals(2752 to 1536, v.rozmer)
    }

    @Test
    fun `dedeny pomer stran nema vlastni rozmer`() {
        val v = Qwen21PeBuilder.parse(
            """</think>{"rewritten_prompt": "x", "wh_ratio": "", "ratio_follow": "<image1>"}"""
        )!!
        assertEquals("<image1>", v.pomerZObrazku)
        assertNull(v.rozmer)
    }

    @Test
    fun `useknuta odpoved se pozna a nepodstrci se puvodni zadani`() {
        assertNull(Qwen21PeBuilder.parse("<think>uvaha bez konce"))
        assertNull(Qwen21PeBuilder.parse("""<think>x</think> {"rewritten_prompt": ""}"""))
    }

    @Test
    fun `json v uvozovkach nerozhodi hledani konce objektu`() {
        val v = Qwen21PeBuilder.parse(
            """</think>{"rewritten_prompt": "napis \"AHOJ}\" na ceduli", "wh_ratio": "1:1"}"""
        )!!
        assertTrue(v.prompt.contains("AHOJ}"))
        assertEquals(2048 to 2048, v.rozmer)
    }

    /**
     * Odvázaný vylepšovač pro kartu Úprava obrázku. Jede na jiném uzlu než
     * Qwenův přepisovač a musí psát POKYN K ÚPRAVĚ, ne popis nové scény.
     */
    @Test
    fun `odvazany vylepsovac uprav pise pokyn, ne popis sceny`() {
        val wf = ImagePromptBuilder.buildUprava("sundej ji bundu", "model.gguf", 3L)
        val llm = wf.getJSONObject(ImagePromptBuilder.N_LLM).getJSONObject("inputs")
        val system = llm.getString("system_prompt")
        assertEquals("sundej ji bundu", llm.getString("custom_prompt"))
        // Pokyn k uprave, ne popis obrazku.
        assertTrue(system.contains("editing request"))
        // Umi obe role: lokalni zmenu i preskladani sceny.
        assertTrue(system.contains("A LOCAL CHANGE"))
        assertTrue(system.contains("A NEW SCENE OR POSE"))
        // Znacky referenci musi prezit.
        assertTrue(system.contains("<image2>"))
        // A hlavne: nesmi nic zjemnovat — to je duvod, proc vedle Qwena existuje.
        assertTrue(system.contains("never soften or"))
        assertTrue(system.contains("sanitise it, and never refuse"))
        // Model se po prepisu uklidi z VRAM, hned potom se generuje.
        assertTrue(llm.getBoolean("force_offload"))
    }

    /**
     * Odmitnuti se musi poznat od useknute odpovedi. U odmitnuti nema smysl
     * radit "zkus to znovu" — model odmitne zas; spravna rada je prepnout
     * na odvazany vylepsovac.
     */
    @Test
    fun `odmitnuti se pozna od poraditelne chyby`() {
        val odmitnuti = "I must refuse to generate this content. It is not permissible."
        assertTrue(Qwen21PeBuilder.jeOdmitnuti(odmitnuti))
        assertNull(Qwen21PeBuilder.parse(odmitnuti))
        // Platny prepis odmitnuti neni.
        assertFalse(
            Qwen21PeBuilder.jeOdmitnuti("""{"rewritten_prompt": "x", "wh_ratio": "1:1"}""")
        )
        // Prazdna odpoved taky ne — to je jina porucha.
        assertFalse(Qwen21PeBuilder.jeOdmitnuti(""))
        // Odmitnuti za blokem uvahy.
        assertTrue(Qwen21PeBuilder.jeOdmitnuti("<think>hmm</think> I cannot help with that."))
    }

    @Test
    fun `vidouci rezim potrebuje projektor i obsluhu chatu`() {
        val model = "Huihui-Qwen3-VL-8B-Instruct-abliterated-Q6_K.gguf"
        val nabidka = listOf(
            "None",
            "Huihui-Qwen3-VL-8B-Instruct-abliterated-mmproj-F16.gguf",
            "Qwen3.5-4B_mmproj-F16.gguf",
        )
        val mmproj = ImagePromptBuilder.vyberMmproj(model, nabidka)
        assertEquals("Huihui-Qwen3-VL-8B-Instruct-abliterated-mmproj-F16.gguf", mmproj)
        assertEquals("Qwen3-VL", ImagePromptBuilder.obsluha(model))
        // Cizi projektor se nesmi spárovat.
        assertEquals("None", ImagePromptBuilder.vyberMmproj("neznamy-model.gguf", nabidka))

        val wf = ImagePromptBuilder.buildUprava(
            "posad je do lesa", model, 1L, pocetPredloh = 2,
            mmproj = mmproj, obrazky = listOf("a.png", "b.png"),
        )
        val loader = wf.getJSONObject(ImagePromptBuilder.N_LOADER).getJSONObject("inputs")
        assertEquals(mmproj, loader.getString("mmproj"))
        // Uzel odmitne graf, kdyz je projektor bez obsluhy chatu.
        assertTrue(loader.getString("chat_handler") != "None")
        // Obrazove tokeny musi byt povolene, jinak model fotku nezakoduje.
        assertTrue(loader.getInt("image_max_tokens") > 0)
        val llm = wf.getJSONObject(ImagePromptBuilder.N_LLM).getJSONObject("inputs")
        assertTrue(llm.has("images"))
        // Predlohy jdou do modelu zmensene.
        assertEquals(
            ImagePromptBuilder.PREDLOHA_MAX_PX,
            wf.getJSONObject("150").getJSONObject("inputs").getInt("largest_size"),
        )
        // Zadani nese, kolik predloh je a co znamenaji.
        assertTrue(llm.getString("custom_prompt").contains("<image2>"))
    }

    @Test
    fun `bez projektoru se obrazky vubec neposilaji`() {
        val wf = ImagePromptBuilder.buildUprava("x", "model.gguf", 1L, pocetPredloh = 2)
        val loader = wf.getJSONObject(ImagePromptBuilder.N_LOADER).getJSONObject("inputs")
        assertEquals("None", loader.getString("mmproj"))
        assertEquals("None", loader.getString("chat_handler"))
        assertEquals(0, loader.getInt("image_max_tokens"))
        assertTrue(!wf.getJSONObject(ImagePromptBuilder.N_LLM).getJSONObject("inputs").has("images"))
    }

    /**
     * S vic predlohami nesmi kontext tvrdit, ze jedna z nich je "scena, do
     * ktere se ostatni vlepi". Model si to precetl jako vymenu lidi a psal
     * "Replace the woman in <image1> with the woman from <image2>", i kdyz
     * uzivatel chtel oba posadit do lesa (21. 9. 2026).
     */
    @Test
    fun `kontext predloh nesvadi k vymene lidi mezi fotkami`() {
        val k = ImagePromptBuilder.sKontextem("posad je do lesa", 2)
        assertTrue(k.contains("<image1>, <image2>"))
        assertTrue(k.contains("only sets the output size"))
        assertTrue(k.contains("do NOT describe swapping people"))
        assertTrue(!k.contains("is the photo being edited"))
        // Jedina predloha kontext nepotrebuje.
        assertEquals("posad je do lesa", ImagePromptBuilder.sKontextem("posad je do lesa", 1))
    }

    @Test
    fun `preskladani sceny ma zakazane Replace`() {
        val system = ImagePromptBuilder.buildUprava("x", "m.gguf", 1L)
            .getJSONObject(ImagePromptBuilder.N_LLM).getJSONObject("inputs")
            .getString("system_prompt")
        val vetevB = system.substringAfter("(B) A NEW SCENE OR POSE")
        assertTrue(vetevB.contains("NEVER write \"Replace"))
        assertTrue(vetevB.contains("never describe swapping one person for"))
    }
}
