package cz.promptlab.h3video

import java.io.File
import java.util.Properties

/**
 * Kde na tomhle počítači leží ComfyUI.
 *
 * Testy, které porovnávají šablony v appce s balíky nainstalovanými na disku,
 * potřebují cestu ke ComfyUI. Ta je u každého jiná a **do repozitáře nepatří**,
 * proto se bere odjinud:
 *
 *  1. proměnná prostředí `COMFYUI_ROOT`,
 *  2. klíč `comfyDir` v `local.properties` (soubor je mimo git).
 *
 * Když není ani jedno, vrátí `null` a test se přeskočí přes `assumeTrue`.
 */
object TestCesty {

    val comfyRoot: File? by lazy {
        val zProstredi = System.getenv("COMFYUI_ROOT")?.takeIf { it.isNotBlank() }
        val cesta = zProstredi ?: zLocalProperties()
        cesta?.let { File(it) }?.takeIf { it.isDirectory }
    }

    /**
     * Regresní sada storyboardů (5.67): `<jmeno>/list.png` + `pravda.json`. Obrázky jsou osobní,
     * do repozitáře nepatří — proměnná `SB_KORPUS` nebo klíč `sbKorpusDir` v `local.properties`.
     */
    val sbKorpus: File? by lazy {
        (System.getenv("SB_KORPUS")?.takeIf { it.isNotBlank() } ?: vlastnost("sbKorpusDir"))
            ?.let { File(it) }?.takeIf { it.isDirectory }
    }

    /** Složka s nainstalovanými custom nody, nebo `null`, když ComfyUI neznáme. */
    fun customNodes(podsloz: String): File? =
        comfyRoot?.let { File(it, "custom_nodes/$podsloz") }

    private fun zLocalProperties(): String? = vlastnost("comfyDir")

    private fun vlastnost(klic: String): String? {
        // Unit testy běží se spuštěcí složkou modulu `app/`, soubor je o patro výš.
        for (kandidat in listOf(File("../local.properties"), File("local.properties"))) {
            if (!kandidat.isFile) continue
            val p = Properties()
            kandidat.inputStream().use { p.load(it) }
            p.getProperty(klic)?.takeIf { it.isNotBlank() }?.let { return it }
        }
        return null
    }
}
