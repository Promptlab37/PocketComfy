package cz.promptlab.h3video

import cz.promptlab.h3video.data.VideoItem
import cz.promptlab.h3video.engine.GenState
import cz.promptlab.h3video.engine.QueueCore
import cz.promptlab.h3video.engine.QueuedRun
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fronta běhů: druhý zařazený běh se MUSÍ spustit, jakmile první skončí —
 * bez ohledu na to, jestli obrazovka žije. Jádro fronty je bez Androidu,
 * takže se tu simuluje jen stav enginu.
 */
class RunQueueTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val stav = MutableStateFlow<GenState>(GenState.Idle)
    private var bezi = false
    private val spustene = mutableListOf<String>()

    private fun jadro() = QueueCore(
        stav = stav,
        bezi = { bezi },
        zavriVysledek = { stav.value = GenState.Idle },
        scope = scope,
        prodlevaPoHotovoMs = 100,
    ).also { it.start() }

    private fun beh(nazev: String) = QueuedRun(System.nanoTime(), nazev, "") {
        spustene += nazev
        // stejně jako engine: po spuštění je „v běhu"
        bezi = true
        stav.value = running()
    }

    private fun running() = GenState.Running(
        promptId = "x", stage = cz.promptlab.h3video.comfy.Stage.SAMPLING, overall = 0.5f,
        startedAt = 0L,
    )

    private fun hotovo() = GenState.Done(
        VideoItem("id", "f.mp4", "", 0L, 5f, "1x1", 1L, false)
    )

    private fun pockej(ms: Long = 3000, podminka: () -> Boolean) {
        val konec = System.currentTimeMillis() + ms
        while (System.currentTimeMillis() < konec && !podminka()) Thread.sleep(20)
        assertTrue("podmínka nenastala do $ms ms", podminka())
    }

    @After
    fun uklid() = scope.cancel()

    @Test
    fun `kdyz je volno, bezi se hned`() {
        val q = jadro()
        q.add(beh("A"))
        pockej { spustene == listOf("A") }
        assertEquals(0, q.queue.value.size)
    }

    @Test
    fun `druhy beh ceka a spusti se po dokonceni prvniho`() {
        val q = jadro()
        q.add(beh("A"))
        pockej { spustene == listOf("A") }
        q.add(beh("B"))
        // A běží → B čeká ve frontě
        Thread.sleep(300)
        assertEquals(listOf("A"), spustene)
        assertEquals(1, q.queue.value.size)

        // A skončí → hotovo se chvíli ukáže → B se spustí sám
        bezi = false
        stav.value = hotovo()
        pockej { spustene == listOf("A", "B") }
        assertEquals(0, q.queue.value.size)
        assertTrue(stav.value is GenState.Running)
    }

    @Test
    fun `po chybe fronta ceka na zavreni a pak jede dal`() {
        val q = jadro()
        q.add(beh("A"))
        pockej { spustene == listOf("A") }
        q.add(beh("B"))
        bezi = false
        stav.value = GenState.Failed("spadlo")
        Thread.sleep(600)
        // chyba visí → nic se nespouští, ať ji uživatel vidí
        assertEquals(listOf("A"), spustene)
        // zavření chyby = Idle → další běh
        stav.value = GenState.Idle
        pockej { spustene == listOf("A", "B") }
    }

    @Test
    fun `odebrani z fronty`() {
        val q = jadro()
        q.add(beh("A"))
        pockej { spustene == listOf("A") }
        val b = beh("B")
        q.add(b)
        q.remove(b.id)
        bezi = false
        stav.value = hotovo()
        Thread.sleep(500)
        assertEquals(listOf("A"), spustene)
    }

    @Test
    fun `selhani jednoho spusteni neshodi hlidac`() {
        val q = jadro()
        q.add(QueuedRun(1, "vadny", "") { throw IllegalStateException("smazaný podklad") })
        q.add(beh("B"))
        pockej { spustene == listOf("B") }
    }
}
