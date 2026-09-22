package cz.promptlab.h3video

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Výsledkem běhu je jen to, co server označil jako `type: "output"`.
 *
 * `LoadVideo` je v ComfyUI **výstupní uzel** a do odpovědi hlásí soubor, který
 * načetl — tedy vstup, s `type: "input"`. Appka brala první video, na které
 * v odpovědi narazila, a pořadí klíčů není dané: u navazujícího záběru si tak
 * 22. 9. 2026 stáhla vlastní nahraný zdroj (5 s) místo slepeného celku (9,4 s).
 * V galerii to vypadalo, jako by se navázání vůbec neprovedlo.
 *
 * Testuje se tu pravidlo výběru, ne celý engine — ten potřebuje Android.
 */
class VystupNeniVstupTest {

    private data class OutFile(val filename: String, val subfolder: String, val type: String)

    /** Kopie výběru z `GenerationEngine`, zkrácená na to podstatné. */
    private fun hlavniVideo(outputs: JSONObject): String? {
        for (key in outputs.keys()) {
            val o = outputs.optJSONObject(key) ?: continue
            for (arrayKey in listOf("images", "videos", "gifs", "audio", "3d")) {
                val arr = o.optJSONArray(arrayKey) ?: continue
                for (i in 0 until arr.length()) {
                    val f = arr.getJSONObject(i)
                    val out = OutFile(
                        f.optString("filename"),
                        f.optString("subfolder", ""),
                        f.optString("type", "output"),
                    )
                    if (out.filename.isBlank()) continue
                    if (out.type != "output") continue
                    return out.filename
                }
            }
        }
        return null
    }

    private fun odpoved(vararg uzly: Pair<String, Pair<String, String>>): JSONObject {
        val out = JSONObject()
        uzly.forEach { (uzel, soubor) ->
            val (jmeno, typ) = soubor
            out.put(
                uzel,
                JSONObject().put(
                    "images",
                    org.json.JSONArray().put(
                        JSONObject().put("filename", jmeno).put("subfolder", "").put("type", typ)
                    ),
                ),
            )
        }
        return out
    }

    @Test fun `nahrany zdroj se nebere jako vysledek`() {
        // Pořadí klíčů v odpovědi není dané, takže musí projít obě.
        assertEquals(
            "PocketLongMM_00003_.mp4",
            hlavniVideo(
                odpoved(
                    "264" to ("ref_69043ff6.mp4" to "input"),
                    "374" to ("PocketLongMM_00003_.mp4" to "output"),
                )
            ),
        )
        assertEquals(
            "PocketLongMM_00003_.mp4",
            hlavniVideo(
                odpoved(
                    "374" to ("PocketLongMM_00003_.mp4" to "output"),
                    "264" to ("ref_69043ff6.mp4" to "input"),
                )
            ),
        )
    }

    /** Běžná odpověď s jediným výstupem se chovat nemění. */
    @Test fun `jediny vystup projde`() {
        assertEquals(
            "PocketDance_00001_.mp4",
            hlavniVideo(odpoved("911" to ("PocketDance_00001_.mp4" to "output"))),
        )
    }

    /** Když server typ neuvede, platí výchozí „output" — jinak by nezbylo nic. */
    @Test fun `chybejici typ se bere jako vystup`() {
        val out = JSONObject().put(
            "9",
            JSONObject().put(
                "images",
                org.json.JSONArray().put(JSONObject().put("filename", "a.mp4")),
            ),
        )
        assertEquals("a.mp4", hlavniVideo(out))
    }

    /** Dočasný soubor z náhledu (`type: "temp"`) taky není výsledek. */
    @Test fun `docasny nahled se nebere`() {
        assertEquals(
            null,
            hlavniVideo(odpoved("5" to ("nahled.mp4" to "temp"))),
        )
    }
}
