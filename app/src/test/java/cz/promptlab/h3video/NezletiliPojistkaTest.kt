package cz.promptlab.h3video

import cz.promptlab.h3video.data.NezletiliPojistka.zakazano
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Nahota nebo sex + věk pod 18 / slovo pro dítě se nesmí odeslat (4.56). */
class NezletiliPojistkaTest {

    @Test fun `nahota s nezletilym vekem nebo ditetem se blokuje`() {
        assertTrue(zakazano("žena je nahá, 15 let"))
        assertTrue(zakazano("nahá 16letá"))
        assertTrue(zakazano("naked girl, 14 years old"))
        assertTrue(zakazano("svlékni to dítě"))
        assertTrue(zakazano("nude schoolgirl"))
        assertTrue(zakazano("holčička nahá"))
        assertTrue(zakazano("teen topless"))
    }

    @Test fun `dospeli a nevinne zadani projdou`() {
        assertFalse(zakazano("žena je nahá"))
        assertFalse(zakazano("dívka je nahá a oholená"))
        assertFalse(zakazano("nahá žena, 25 let"))
        assertFalse(zakazano("naked woman, 18 years old"))
        assertFalse(zakazano("nude couple, 30 yo"))
        assertFalse(zakazano("dítě si hraje na zahradě, 5 let"))
        assertFalse(zakazano("the kid rides a bike"))
        // „nahoru" / „nahoře" není nahota
        assertFalse(zakazano("dítě se dívá nahoru, 5 let"))
    }
}
