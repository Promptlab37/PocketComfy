package cz.promptlab.h3video

import cz.promptlab.h3video.comfy.ServerAudit
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import org.junit.Assert.assertTrue

/**
 * Jména modelů ve všech šablonách musí server nabízet.
 *
 * Vzniklo 28. 9. 2026: Kontrola serveru v appce hlásila chybějící
 * `krea2_turbo_fp8_scaled` a `MiniMax_H3_FL2VA_…`. Na serveru byly pod jiným
 * jménem (`krea2_turbo_int8_convrot`, malá písmena) — karta Úprava obrázku
 * s Krea 2 by spadla. [SablonyProtiServeruTest] jména modelů schválně neřeší;
 * tenhle test to před vydáním chytí. Bez běžícího ComfyUI se přeskočí.
 */
class ModelySablonNaServeruTest {

    private val server = System.getenv("COMFY_URL") ?: "http://127.0.0.1:8188"

    private fun stahni(url: String, timeoutMs: Int): String? = runCatching {
        (URL(url).openConnection() as HttpURLConnection).run {
            connectTimeout = timeoutMs; readTimeout = timeoutMs
            inputStream.bufferedReader().use { it.readText() }
        }
    }.getOrNull()

    private val pripony = listOf(".safetensors", ".sft", ".gguf", ".pt", ".pth", ".ckpt", ".bin", ".onnx")

    @Test
    fun `modely v sablonach server nabizi`() {
        assumeTrue("ComfyUI neodpovídá — kontrola se přeskočí", stahni("$server/system_stats", 4_000) != null)
        val oi = JSONObject(stahni("$server/object_info", 120_000) ?: return)
        val chyby = mutableListOf<String>()
        File("src/main/res/raw").listFiles { f -> f.name.startsWith("workflow_") && f.extension == "json" }
            ?.sortedBy { it.name }.orEmpty().forEach { soubor ->
                val wf = JSONObject(soubor.readText())
                wf.keys().forEach { id ->
                    val uzel = wf.optJSONObject(id) ?: return@forEach
                    val spec = oi.optJSONObject(uzel.optString("class_type")) ?: return@forEach
                    val vstupy = uzel.optJSONObject("inputs") ?: return@forEach
                    vstupy.keys().forEach { k ->
                        val v = vstupy.opt(k) as? String ?: return@forEach
                        if (pripony.none { v.lowercase().endsWith(it) }) return@forEach
                        val moznosti = ServerAudit.options(spec, k) ?: return@forEach
                        if (moznosti.isNotEmpty() && v !in moznosti) chyby += "${soubor.name} · $id ${uzel.optString("class_type")}.$k = $v"
                    }
                }
            }
        assertTrue("Šablony chtějí modely, které server nemá:\n" + chyby.joinToString("\n"), chyby.isEmpty())
    }
}
