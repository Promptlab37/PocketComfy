package cz.promptlab.h3video

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Hlídá dvě věci ve `MainViewModel`, které překlad nechytí a jednotkový test
 * ViewModelu by na ně potřeboval Android. Obojí už jednou shodilo vydanou verzi:
 *
 *  - **3.04**: obrazovka si hlášku „co chybí" pamatovala podle ručně vypsaného
 *    seznamu karet a na nové karty se v něm zapomnělo → tlačítko zůstalo
 *    šedivé i po vyplnění.
 *  - **3.05**: oprava toho seznamu se počítala jako obyčejné `val` nahoře
 *    v třídě, jenže stavy karet vznikají až o stovky řádků níž. V Kotlinu se
 *    vlastnosti inicializují v pořadí zápisu, takže seznam obsahoval `null`
 *    a konstruktor spadl dřív, než se stihlo cokoli vykreslit.
 *
 * Kontroluje se proto přímo zdroják.
 */
class PoradiInicializaceTest {

    private val zdroj: String =
        File("src/main/java/cz/promptlab/h3video/MainViewModel.kt").readText()

    private val radky: List<String> = zdroj.lines()

    private fun radekS(hledane: String): Int =
        radky.indexOfFirst { it.contains(hledane) }

    @Test
    fun `odvozene toky se pocitaji az po stavech karet`() {
        val stavy = radky.withIndex()
            .filter { (_, r) -> Regex("private val _\\w+ = MutableStateFlow").containsMatchIn(r) }
            .map { it.index }
        assertTrue("nenašly se stavy karet — změnil se zápis?", stavy.size >= 10)
        val posledniStav = stavy.max()

        listOf("val problem: StateFlow", "val hints: StateFlow").forEach { deklarace ->
            val i = radekS(deklarace)
            assertTrue("$deklarace ve zdrojáku není", i >= 0)
            val lazy = radky[i].contains("by lazy") ||
                radky.getOrNull(i + 1)?.contains("by lazy") == true
            assertTrue(
                "$deklarace se počítá na řádku ${i + 1}, ale stavy karet vznikají " +
                    "až do řádku ${posledniStav + 1}. Bez `by lazy` spadne appka " +
                    "hned při startu (viz 3.05).",
                lazy || i > posledniStav,
            )
        }
    }

    /**
     * Seznam, ze kterého se odvozené toky počítají, musí obsahovat všechno,
     * z čeho validace a upozornění opravdu čtou. Jinak se hláška „co chybí"
     * po vyplnění karty nepřepočítá.
     */
    @Test
    fun `seznam vsech scen obsahuje vse, z ceho validace cte`() {
        val zacatek = radekS("private val vsechnySceny")
        assertTrue("vsechnySceny ve zdrojáku není", zacatek >= 0)
        val seznam = radky.drop(zacatek).take(8).joinToString("\n")

        val telo = buildString {
            listOf(
                "fun validation(p: GenParams): String?",
                "fun hints(p: GenParams): List<String>",
            ).forEach { hlavicka ->
                val i = radekS(hlavicka)
                assertTrue("$hlavicka ve zdrojáku není", i >= 0)
                append(radky.drop(i).take(70).joinToString("\n"))
                append("\n")
            }
        }
        val ctene = Regex("(_\\w+)\\.value").findAll(telo)
            .map { it.groupValues[1] }.toSet()
        assertTrue("nenašlo se, z čeho validace čte — změnil se zápis?", ctene.size >= 8)

        ctene.forEach { stav ->
            assertTrue(
                "V `vsechnySceny` chybí $stav, ačkoli z něj validace čte — karta, " +
                    "která ho používá, bude mít šedivé tlačítko i po vyplnění (viz 3.04).",
                seznam.contains(stav),
            )
        }
    }
}
