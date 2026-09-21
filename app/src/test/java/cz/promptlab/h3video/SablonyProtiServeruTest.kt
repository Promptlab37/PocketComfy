package cz.promptlab.h3video

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Porovná KAŽDOU šablonu v `res/raw` se skutečnými schématy uzlů, která hlásí
 * běžící ComfyUI (`/object_info`).
 *
 * Vzniklo z ostudy 3.04: karta 3D model spadla hned při odeslání na
 * „Prompt outputs failed validation" — uzlu `ImageCropToMask` chybělo povinné
 * `background` (typ COLOR) a uzlu `RemeshMesh` se posunuly hodnoty, protože
 * má rozbalovací volbu s podnastavením, která ve `widgets_values` zabírá víc
 * než jednu položku. Převod z editorové podoby jsem tehdy ověřil jen na
 * zapojení a existenci uzlů, ne na hodnoty — a chybu našel až uživatel.
 *
 * Kontroluje se to, co kontroluje ComfyUI před spuštěním:
 *  - jsou vyplněné všechny povinné vstupy?
 *  - sedí typy (číslo je číslo, přepínač je true/false)?
 *  - vejdou se čísla do svých mezí?
 *
 * Neznámé vstupy navíc se neřeší — ComfyUI je ignoruje a některé balíky
 * (rgthree Power Lora Loader) si je přidávají schválně. Názvy modelů taky ne;
 * od toho je Kontrola serveru v Nastavení.
 *
 * Když server neodpovídá, test se přeskočí.
 */
class SablonyProtiServeruTest {

    private val server = System.getenv("COMFY_URL") ?: "http://127.0.0.1:8188"

    private val rawDir = File("src/main/res/raw")

    private val cache = HashMap<String, JSONObject?>()

    private fun stahni(url: String, timeoutMs: Int = 20_000): String? = runCatching {
        (URL(url).openConnection() as HttpURLConnection).run {
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            requestMethod = "GET"
            if (responseCode != 200) return@runCatching null
            inputStream.bufferedReader().use { it.readText() }
        }
    }.getOrNull()

    /** Schéma jedné třídy uzlu. `null` = server ji nezná. */
    private fun schema(cls: String): JSONObject? = cache.getOrPut(cls) {
        val text = stahni("$server/object_info/${cls.replace(" ", "%20")}") ?: return@getOrPut null
        runCatching { JSONObject(text).optJSONObject(cls) }.getOrNull()
    }

    private fun jeOdkaz(v: Any?): Boolean =
        v is JSONArray && v.length() == 2 && v.opt(0) is String

    @Test
    fun `sablony sedi se schematy uzlu na serveru`() {
        assumeTrue("ComfyUI neodpovídá — kontrola se přeskočí", stahni("$server/system_stats", 4_000) != null)

        val sablony = rawDir.listFiles { f -> f.extension == "json" }?.sortedBy { it.name }.orEmpty()
        assertTrue("v res/raw nejsou žádné šablony", sablony.isNotEmpty())

        val chyby = mutableListOf<String>()
        sablony.forEach { soubor ->
            val wf = runCatching { JSONObject(soubor.readText()) }.getOrNull() ?: return@forEach
            chyby += zkontroluj(wf, soubor.name)
        }

        assertTrue(
            "Šablony nesedí se schématy uzlů — ComfyUI by je odmítl hláškou " +
                "„Prompt outputs failed validation\":\n" + chyby.joinToString("\n"),
            chyby.isEmpty(),
        )
    }

    /**
     * Grafy, které appka teprve **staví** — ty v `res/raw` nejsou, takže by
     * je kontrola šablon minula.
     *
     * Větev Pixal3D karty 3D model si přidává čtyři uzly navíc a přepojuje
     * zadání. Kdyby jim něco chybělo, spadne to až uživateli při odeslání.
     */
    @Test
    fun `stavene grafy sedi se schematy uzlu na serveru`() {
        assumeTrue("ComfyUI neodpovídá — kontrola se přeskočí", stahni("$server/system_stats", 4_000) != null)

        val sablona = File(rawDir, "workflow_trellis2.json").readText()
        val chyby = mutableListOf<String>()
        cz.promptlab.h3video.data.Model3dMotor.entries.forEach { motor ->
            val wf = cz.promptlab.h3video.comfy.Trellis2Builder.build(
                template = sablona,
                scene = cz.promptlab.h3video.data.Model3dScene(source = null, motor = motor),
                seed = 1L,
                images = listOf("foto.png"),
                uklidVram = true,
            )
            chyby += zkontroluj(wf, "3D model / $motor")
        }

        assertTrue(
            "Stavěný graf nesedí se schématy uzlů:\n" + chyby.joinToString("\n"),
            chyby.isEmpty(),
        )
    }

    /**
     * Karta Hudba, volba **Předělat nahrávku**. Graf přidává tři uzly proti
     * nové skladbě (načtení nahrávky, přepisovač a jeho model) a mění `mode`
     * na dvou místech naráz.
     */
    @Test
    fun `predelani nahravky sedi se schematy uzlu`() {
        assumeTrue("ComfyUI neodpovídá — kontrola se přeskočí", stahni("$server/system_stats", 4_000) != null)

        val sablona = File(rawDir, "workflow_yue2_cover.json").readText()
        val chyby = mutableListOf<String>()
        listOf(false, true).forEach { akordy ->
            val wf = cz.promptlab.h3video.comfy.Yue2MusicBuilder.buildCover(
                sablona,
                cz.promptlab.h3video.data.MusicScene(
                    motor = cz.promptlab.h3video.data.MusicMotor.YUE2,
                    rezim = cz.promptlab.h3video.data.MusicRezim.PREDELAT,
                    styl = "jazz",
                    maxSeconds = cz.promptlab.h3video.data.MusicScene.YUE2_MAX_SECONDS,
                    predlohaAkordy = akordy,
                ),
                seed = 1L,
                predloha = "predloha.mp3",
            )
            chyby += zkontroluj(wf, "Hudba / předělat nahrávku / akordy=$akordy")
        }

        assertTrue(
            "Graf předělání nahrávky nesedí se schématy uzlů:\n" + chyby.joinToString("\n"),
            chyby.isEmpty(),
        )
    }

    /**
     * Kraje posuvníků kroků a cfg na kartě Obrázek. Co appka dovolí nastavit,
     * to musí uzel přijmout — jinak běh spadne až na serveru.
     */
    @Test
    fun `kraje posuvniku na karte Obrazek sedi se schematy uzlu`() {
        assumeTrue("ComfyUI neodpovídá — kontrola se přeskočí", stahni("$server/system_stats", 4_000) != null)

        val sablona = File(rawDir, "workflow_zimage_t2i.json").readText()
        val B = cz.promptlab.h3video.comfy.ZImageBuilder
        val varianty = listOf(
            "nejmíň kroků" to (B.KROKY_MIN to 1f),
            "nejvíc kroků a cfg" to (B.KROKY_MAX to B.CFG_MAX),
            "výchozí Turbo" to (0 to 0f),
        )

        val chyby = mutableListOf<String>()
        varianty.forEach { (popis, hodnoty) ->
            val (kroky, cfg) = hodnoty
            val wf = B.build(
                sablona, "kocka", cz.promptlab.h3video.data.Aspect.SQUARE_1_1, 1L,
                model = "turbo", kroky = kroky, cfg = cfg,
            )
            chyby += zkontroluj(wf, "Obrázek / $popis")
        }

        assertTrue(
            "Graf karty Obrázek nesedí se schématy uzlů:\n" + chyby.joinToString("\n"),
            chyby.isEmpty(),
        )
    }

    /** Vrátí seznam nesrovnalostí jednoho grafu proti schématům ze serveru. */
    private fun zkontroluj(wf: JSONObject, kde: String): List<String> {
        val chyby = mutableListOf<String>()
        run {
            wf.keys().forEach { id ->
                val uzel = wf.optJSONObject(id) ?: return@forEach
                val cls = uzel.optString("class_type").ifBlank { return@forEach }
                val info = schema(cls) ?: return@forEach   // neznámou třídu řeší Kontrola serveru
                val ins = uzel.optJSONObject("inputs") ?: JSONObject()
                val vstupy = info.optJSONObject("input") ?: JSONObject()

                // 1) povinné vstupy musí být vyplněné
                vstupy.optJSONObject("required")?.keys()?.forEach { jm ->
                    val ma = ins.has(jm) ||
                        ins.keys().asSequence().any { it.startsWith("$jm.") }
                    if (!ma && !nepovinnyAutogrow(vstupy, jm)) {
                        chyby += "$kde · uzel $id ($cls): chybí povinný vstup „$jm\""
                    }
                }

                // 2) hodnoty musí sedět se schématem
                ins.keys().forEach { jm ->
                    val v = ins.opt(jm)
                    if (jeOdkaz(v)) return@forEach
                    val spec = listOf("required", "optional").firstNotNullOfOrNull {
                        vstupy.optJSONObject(it)?.optJSONArray(jm)
                    } ?: return@forEach          // neznámý vstup ComfyUI ignoruje
                    val typ = spec.opt(0)
                    if (typ !is String) return@forEach   // výčet hodnot — řeší Kontrola serveru
                    val opts = spec.optJSONObject(1) ?: JSONObject()
                    when (typ) {
                        "INT", "FLOAT" -> {
                            // Pozor: JSON má true/false taky jako "hodnotu", ale
                            // číslo to není — proto se zvlášť vylučuje.
                            val cislo = if (v is Boolean) null else v as? Number
                            if (cislo == null) {
                                chyby += "$kde · uzel $id ($cls).$jm: „$v\" není číslo ($typ)"
                            } else {
                                val d = cislo.toDouble()
                                if (opts.has("min") && d < opts.getDouble("min")) {
                                    chyby += "$kde · uzel $id ($cls).$jm: $d je pod min ${opts.getDouble("min")}"
                                }
                                if (opts.has("max") && d > opts.getDouble("max")) {
                                    chyby += "$kde · uzel $id ($cls).$jm: $d je nad max ${opts.getDouble("max")}"
                                }
                            }
                        }
                        "BOOLEAN" -> if (v !is Boolean) {
                            chyby += "$kde · uzel $id ($cls).$jm: „$v\" není true/false"
                        }
                    }
                }
            }
        }
        return chyby
    }

    /**
     * Autogrow vstup (`COMFY_AUTOGROW_V3`) se v `/object_info` hlásí mezi
     * povinnými, ale ComfyUI ho při spuštění rozbalí na jednotlivé sloty
     * `jmeno.slot_1…N` a **sám rodičovský klíč do grafu nepatří**. Když má
     * šablona `min = 0`, jsou všechny sloty nepovinné a graf bez jediného
     * obrázku je v pořádku — viz `Autogrow._expand_schema_for_dynamic`
     * v `comfy_api/latest/_io.py`.
     *
     * Bez téhle výjimky by test hlásil chybu u `TextEncodeQwenImage21`
     * v šabloně text→obrázek, kde se žádná předloha nepoužívá.
     */
    private fun nepovinnyAutogrow(vstupy: JSONObject, jmeno: String): Boolean {
        val spec = vstupy.optJSONObject("required")?.optJSONArray(jmeno) ?: return false
        if (spec.opt(0) != "COMFY_AUTOGROW_V3") return false
        val sablona = spec.optJSONObject(1)?.optJSONObject("template") ?: return false
        return sablona.optInt("min", 0) == 0
    }

}
