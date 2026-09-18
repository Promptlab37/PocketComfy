package cz.promptlab.h3video

import cz.promptlab.h3video.data.EditLoraFile
import cz.promptlab.h3video.data.LoraTrigger
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Spouštěcí slovo LoRA se dosazuje do zadání samo.
 *
 * Testy hlídají dvě věci, které by uživateli ublížily: aby se spouštěč vzal
 * ze správného místa metadat, a aby odebrání LoRA neškrtlo nic z toho, co si
 * do zadání napsal člověk.
 */
class LoraTriggerTest {

    private fun meta(vararg dvojice: Pair<String, String>) = JSONObject().apply {
        dvojice.forEach { (k, v) -> put(k, v) }
    }

    @Test
    fun `standardizovane pole ma prednost`() {
        val m = meta(
            "modelspec.trigger_phrase" to "ohwx style",
            "ss_tag_frequency" to """{"10_set":{"jiny tag":50}}""",
        )
        assertEquals("ohwx style", LoraTrigger.zMetadat(m))
    }

    @Test
    fun `seznam oddeleny carkami da prvni polozku`() {
        assertEquals("r34l1sm", LoraTrigger.zMetadat(meta("ss_trigger_words" to "r34l1sm, photo, detailed")))
    }

    @Test
    fun `JSON pole se rozbali`() {
        assertEquals("cinem4tic", LoraTrigger.zMetadat(meta("trainedWords" to """["cinem4tic","1girl"]""")))
    }

    @Test
    fun `bez metadat i bez pouzitelneho tagu vrati null`() {
        assertNull(LoraTrigger.zMetadat(null))
        assertNull(LoraTrigger.zMetadat(JSONObject()))
        // Samé obecné značky — psát „1girl" do zadání nikomu nepomůže.
        assertNull(LoraTrigger.zMetadat(meta("ss_tag_frequency" to """{"10_set":{"1girl":40,"solo":40}}""")))
    }

    @Test
    fun `nejcastejsi znacka jako posledni moznost`() {
        val m = meta("ss_tag_frequency" to """{"10_set":{"zx9 style":120,"tree":4,"1girl":200}}""")
        assertEquals("zx9 style", LoraTrigger.zMetadat(m))
    }

    @Test
    fun `spoustec se prida na konec zadani`() {
        assertEquals("a cat on a roof, zx9 style",
            LoraTrigger.dosad("a cat on a roof", null, "zx9 style"))
        assertEquals("zx9 style", LoraTrigger.dosad("", null, "zx9 style"))
    }

    @Test
    fun `dvakrat se neprida`() {
        val prompt = "zx9 style, a cat"
        assertEquals(prompt, LoraTrigger.dosad(prompt, null, "zx9 style"))
    }

    @Test
    fun `pri vymene LoRA zmizi jen ten stary spoustec`() {
        val prompt = "a cat on a roof, zx9 style"
        assertEquals("a cat on a roof, ohwx", LoraTrigger.dosad(prompt, "zx9 style", "ohwx"))
    }

    @Test
    fun `odebrani LoRA nechá uzivateluv text byt`() {
        val prompt = "zx9 style, a cat on a roof at sunset"
        assertEquals("a cat on a roof at sunset", LoraTrigger.dosad(prompt, "zx9 style", null))
    }

    @Test
    fun `spoustec se nehleda uvnitr jineho slova`() {
        // „cat" je v „catalog"; kdyby se to bralo jako shodu, spouštěč by se
        // nikdy nepřidal a LoRA by tiše nefungovala.
        assertEquals("a catalog of things, cat",
            LoraTrigger.dosad("a catalog of things", null, "cat"))
    }

    @Test
    fun `soubor bez metadat nema spoustec`() {
        assertNull(LoraTrigger.prosoubor(EditLoraFile("neco.safetensors")))
    }
}
