package cz.promptlab.h3video

import cz.promptlab.h3video.data.Slovnik
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Šablona a její překlad musí mít stejné `%d`/`%s`/`%.2f`: jinak `.format()`
 * v angličtině spadne nebo dosadí hodnotu na špatné místo. Hlídá hlavně
 * doplňky SlovnikA–D z 4.98, kde se stovky hlášek přepisovaly na šablony.
 */
class SlovnikFormatTest {

    private val spec = Regex("""%(\d+\$)?[-#+0,(]*\d*(\.\d+)?[dsfx%]""")

    @Test
    fun `preklad ma stejne formatovaci znacky jako cesky original`() {
        val spatne = Slovnik.EN.filter { (cz, en) ->
            spec.findAll(cz).map { it.value }.sorted().toList() !=
                spec.findAll(en).map { it.value }.sorted().toList()
        }
        assertTrue("Nesedí značky: ${spatne.entries.take(5)}", spatne.isEmpty())
    }

    @Test
    fun `slovnik neni prazdny a obsahuje doplnky`() {
        assertTrue(Slovnik.EN.size > 1700)
        assertTrue(Slovnik.EN.containsKey("generuje na tvém počítači, nikam jinam se nic neposílá."))
        // Doplňky A–D se opravdu slučují do slovníku.
        assertTrue(Slovnik.EN.containsKey("%d • náročné"))
        assertTrue(Slovnik.EN.containsKey("Plné textury"))
    }
}
