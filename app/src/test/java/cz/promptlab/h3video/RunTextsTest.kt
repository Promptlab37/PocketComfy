package cz.promptlab.h3video

import cz.promptlab.h3video.comfy.Stage
import cz.promptlab.h3video.engine.RunKind
import cz.promptlab.h3video.engine.firstPhaseTitle
import cz.promptlab.h3video.engine.mainPhaseTitle
import cz.promptlab.h3video.engine.notificationTitle
import cz.promptlab.h3video.engine.stageDetailText
import cz.promptlab.h3video.engine.stageText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Matice textů průběhu — vznikla po chybě, kdy karta Hudba hlásila
 * „Odesílám obrázky". Testy projdou KAŽDÝ druh běhu × KAŽDOU fázi
 * a hlídají, že se nikde nemluví o cizím druhu výsledku.
 */
class RunTextsTest {

    private val vsechnyFaze = Stage.entries.toList()
    private val vsechnyDruhy = RunKind.entries.toList()

    @Test
    fun `zadny neVideo beh nikdy nemluvi o videu`() {
        vsechnyDruhy.filter { it != RunKind.VIDEO }.forEach { druh ->
            vsechnyFaze.forEach { faze ->
                val t = stageText(faze, druh).lowercase()
                assertFalse("$druh/$faze: '$t'", "video" in t)
                val d = stageDetailText(faze, druh).lowercase()
                assertFalse("$druh/$faze detail: '$d'", "video" in d)
            }
            assertFalse("video" in mainPhaseTitle(druh).lowercase())
            assertFalse("video" in firstPhaseTitle(druh).lowercase())
            assertFalse("video" in notificationTitle(druh).lowercase())
        }
    }

    @Test
    fun `hudba nikdy nemluvi o obrazcich ani fotkach`() {
        vsechnyFaze.forEach { faze ->
            val t = stageText(faze, RunKind.MUSIC).lowercase()
            assertFalse("MUSIC/$faze: '$t'", "obráz" in t || "fotk" in t)
            val d = stageDetailText(faze, RunKind.MUSIC).lowercase()
            assertFalse("MUSIC/$faze detail: '$d'", "obraz" in d || "snímk" in d || "fotk" in d)
        }
        // přesně ta nahlášená chyba: odesílání u hudby
        assertEquals("Připravuji zadání", stageText(Stage.UPLOADING, RunKind.MUSIC))
        assertEquals("Skládám hudbu", notificationTitle(RunKind.MUSIC))
        assertEquals("Spojení se serverem", firstPhaseTitle(RunKind.MUSIC))
        assertEquals("Skládání hudby", mainPhaseTitle(RunKind.MUSIC))
    }

    @Test
    fun `karty bez vstupnich fotek nic neodesilaji`() {
        assertEquals("Připravuji zadání", stageText(Stage.UPLOADING, RunKind.T2I))
        assertEquals("Spojení se serverem", firstPhaseTitle(RunKind.T2I))
    }

    @Test
    fun `hlavni faze ma pro kazdy druh vlastni nazev`() {
        val nazvy = vsechnyDruhy.map { mainPhaseTitle(it) }
        assertEquals(nazvy.size, nazvy.toSet().size)
        val titulky = vsechnyDruhy.map { notificationTitle(it) }
        assertEquals(titulky.size, titulky.toSet().size)
    }

    @Test
    fun `zadny text neni prazdny`() {
        vsechnyDruhy.forEach { druh ->
            vsechnyFaze.forEach { faze ->
                assertTrue(stageText(faze, druh).isNotBlank())
                assertTrue(stageDetailText(faze, druh).isNotBlank())
            }
        }
    }
}
