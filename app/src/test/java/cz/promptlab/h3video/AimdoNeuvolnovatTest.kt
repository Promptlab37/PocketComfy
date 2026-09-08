package cz.promptlab.h3video

import cz.promptlab.h3video.comfy.ComfyClient
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Se serverem na **comfy-aimdo** (ComfyUI ≥ 0.34) se před během nesmí volat
 * `/free`. Aimdo drží modely v zamčené RAM (na uživatelově stroji 39 GB);
 * vyhození a opětovné zamknutí desítek GB zastavuje celý počítač — 8. 9. 2026
 * to uživatel viděl jako zamrznutí při každém běhu z appky, zatímco z ComfyUI
 * UI (které `/free` nevolá) šlo totéž bez problému.
 */
class AimdoNeuvolnovatTest {

    /** Zkrácená skutečná odpověď `/system_stats` z uživatelova serveru 8. 9. 2026. */
    private val sAimdo = JSONObject(
        """{"system":{"os":"win32","comfyui_version":"0.34.1",
            "comfy_package_versions":[
              {"name":"comfyui-frontend-package","installed":"1.49.6"},
              {"name":"comfy-kitchen","installed":"0.2.31"},
              {"name":"comfy-aimdo","installed":"0.4.15"}]},
           "devices":[{"name":"cuda:0","vram_total":17175347200,"vram_free":15000000000}]}"""
    )

    private val sStary = JSONObject(
        """{"system":{"os":"win32","comfyui_version":"0.33.1",
            "comfy_package_versions":[{"name":"comfyui-frontend-package","installed":"1.40.0"}]},
           "devices":[{"name":"cuda:0","vram_total":17175347200,"vram_free":4000000000}]}"""
    )

    @Test
    fun `server s aimdo se pozna z balicku`() {
        assertTrue(ComfyClient.maAimdo(sAimdo))
        assertFalse(ComfyClient.maAimdo(sStary))
    }

    @Test
    fun `bez sekce system se nic nehada`() {
        assertFalse(ComfyClient.maAimdo(JSONObject("{}")))
        assertFalse(ComfyClient.maAimdo(JSONObject("""{"system":{}}""")))
    }

    @Test
    fun `vram se cte z prvni grafiky`() {
        assertEquals(15000000000L to 17175347200L, ComfyClient.vramZe(sAimdo))
        assertNull(ComfyClient.vramZe(JSONObject("{}")))
        assertNull(ComfyClient.vramZe(JSONObject("""{"devices":[{"vram_total":0}]}""")))
    }

    /**
     * Zdroják enginu musí aimdo zkontrolovat DŘÍV, než sáhne na `/free` —
     * kdyby se kontrola přesunula za uvolnění, test na JSON by dál procházel
     * a přitom by appka počítač zase zamrazovala.
     */
    @Test
    fun `engine kontroluje aimdo pred uvolnenim`() {
        val zdroj = java.io.File(
            "src/main/java/cz/promptlab/h3video/engine/GenerationEngine.kt"
        ).readText()
        val telo = zdroj.substringAfter("private suspend fun uvolniPametKdyzTreba")
        val kontrola = telo.indexOf("maAimdo(")
        val uvolneni = telo.indexOf("freeMemory()")
        assertTrue("kontrola aimdo v uvolniPametKdyzTreba chybí", kontrola >= 0)
        assertTrue("freeMemory se volá dřív než kontrola aimdo", kontrola < uvolneni)
    }
}
