package cz.promptlab.h3video

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Každé víceřádkové textové pole musí mít křížek na vymazání.
 *
 * Víceřádkové pole je vždycky něco, co člověk sám píše — zadání, text písně,
 * popis scény. Mazat to po znacích je otrava a uživatel na to narazil u karty
 * Obrázek, kde křížek jako u jediného zadání v appce chyběl. Jednořádková
 * pole (adresa serveru, token, seed) sem nepatří: ta se vyplní jednou
 * a přepisují se celá.
 *
 * Test čte zdroje, protože jinak by to uhlídal jen pohled na obrazovku.
 */
class KrizekVPoliTest {

    private val slozka = File("src/main/java/cz/promptlab/h3video/ui")

    /** Použití `DarkTextField(...)` i s vnořenými závorkami — bez deklarace. */
    private fun pouziti(zdroj: String): List<Pair<Int, String>> {
        val vysledek = mutableListOf<Pair<Int, String>>()
        var od = 0
        while (true) {
            val zacatek = zdroj.indexOf(ZNACKA, od)
            if (zacatek < 0) return vysledek
            od = zacatek + 1
            if (zdroj.substring(maxOf(0, zacatek - 4), zacatek) == "fun ") continue

            var i = zacatek + ZNACKA.length
            var hloubka = 1
            while (hloubka > 0 && i < zdroj.length) {
                when (zdroj[i]) {
                    '(' -> hloubka++
                    ')' -> hloubka--
                }
                i++
            }
            val radek = zdroj.substring(0, zacatek).count { it == '\n' } + 1
            vysledek += radek to zdroj.substring(zacatek, i)
        }
    }

    @Test
    fun `viceradkova pole maji krizek na vymazani`() {
        val chybi = mutableListOf<String>()
        var nalezeno = 0

        slozka.listFiles { f -> f.extension == "kt" }?.forEach { soubor ->
            pouziti(soubor.readText()).forEach { (radek, blok) ->
                if ("singleLine = true" in blok) return@forEach
                nalezeno++
                if ("onClear" !in blok) chybi += "${soubor.name}:$radek"
            }
        }

        assertTrue("žádné pole se nenašlo — test by hlídal prázdno", nalezeno >= 10)
        assertTrue("víceřádkové pole bez křížku: $chybi", chybi.isEmpty())
    }

    private companion object {
        const val ZNACKA = "DarkTextField("
    }
}
