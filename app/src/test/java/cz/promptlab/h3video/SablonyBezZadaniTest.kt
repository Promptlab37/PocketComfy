package cz.promptlab.h3video

import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Žádná předloha v `res/raw` nesmí nést zadání, se kterým ji někdo
 * vyexportoval z ComfyUI.
 *
 * Vzniklo 15. 9. 2026: v šabloně karty 3 kroky zůstal celý uživatelův prompt
 * (scéna na dvě a půl tisíce znaků) a odešel s ní do VEŘEJNÉHO repozitáře.
 * Uživatel to shrnul jasně: „nesmí být ve veřejném repu prompty a moje
 * obrázky a videa co jsem vygeneroval v apce".
 *
 * Technická zadání, která jsou součástí workflow (třeba popis restaurování
 * fotky u Qwenu), jsou v pořádku — proto se hlídá délka a znaky vlastního
 * příběhu, ne každý neprázdný text.
 */
class SablonyBezZadaniTest {

    private val rawDir = File("src/main/res/raw")

    /** Vstupy, kterými se do grafu dostává zadání od uživatele. */
    private val zadani = setOf("lyrics", "tags", "style", "prompt")

    /** Stopy vypravování — tohle do předlohy nepatří ani jako ukázka. */
    private val stopyPribehu = listOf(
        "[Shot", "[Verse]", "[Chorus]", "00:0", "second Czech", "woman", "man ",
    )

    @Test
    fun `predlohy neobsahuji cizi zadani`() {
        val hrisnici = mutableListOf<String>()
        rawDir.listFiles { f -> f.extension == "json" }?.sorted()?.forEach { soubor ->
            val wf = runCatching { JSONObject(soubor.readText()) }.getOrNull() ?: return@forEach
            wf.keys().forEach { id ->
                val inputs = wf.optJSONObject(id)?.optJSONObject("inputs") ?: return@forEach
                inputs.keys().forEach { klic ->
                    val hodnota = inputs.opt(klic) as? String ?: return@forEach
                    val podezrele = klic in zadani && hodnota.isNotBlank() ||
                        stopyPribehu.any { it in hodnota }
                    if (podezrele) {
                        hrisnici += "${soubor.name} · uzel $id · $klic: " +
                            hodnota.take(60).replace("\n", " ")
                    }
                }
            }
        }
        assertTrue(
            "Předloha si veze zadání — vyprázdni ho, appka dosazuje vlastní:\n" +
                hrisnici.joinToString("\n"),
            hrisnici.isEmpty(),
        )
    }
}
