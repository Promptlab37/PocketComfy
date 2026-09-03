package cz.promptlab.h3video.engine

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** Jeden běh čekající ve frontě. Zadání se zmrazí ve chvíli zařazení. */
data class QueuedRun(
    val id: Long,
    val title: String,
    val prompt: String,
    val spust: () -> Unit,
)

/**
 * Jádro fronty — bez Androidu, ať jde ověřit testem.
 *
 * Do 3.00 žila fronta i její hlídač ve `MainViewModel`, tedy s obrazovkou.
 * Jenže video se generuje deset minut a telefon mezitím leží zamčený:
 * Android obrazovku na pozadí klidně zahodí (a s ní ViewModel), zatímco
 * samotný běh dojede — drží ho služba na popředí a engine je objekt
 * procesu. Výsledek: první běh skončil, druhý zařazený **tiše zmizel**
 * a nic se nespustilo. Proto fronta patří k procesu, stejně jako engine.
 *
 * @param stav      stav enginu, na který se hlídá „hotovo" a „prázdno"
 * @param bezi      je zrovna něco v běhu? (další se pouští jen když ne)
 * @param zavriVysledek odklidí obrazovku hotového výsledku, ať může další
 */
class QueueCore(
    private val stav: StateFlow<GenState>,
    private val bezi: () -> Boolean,
    private val zavriVysledek: () -> Unit,
    private val scope: CoroutineScope,
    /** Kolik se hotový výsledek nechá ukázat, než se rozjede další běh. */
    private val prodlevaPoHotovoMs: Long = 1500,
) {
    private val _queue = MutableStateFlow<List<QueuedRun>>(emptyList())
    val queue: StateFlow<List<QueuedRun>> = _queue.asStateFlow()

    private var hlidac: Job? = null

    /** Hlídač: jakmile běh skončí, spustí další zařazený. Volat jednou. */
    fun start() {
        if (hlidac?.isActive == true) return
        hlidac = scope.launch {
            stav.collectLatest { s ->
                when {
                    // Hotovo se chvíli ukáže a samo se odklidí. Po chybě se
                    // čeká, až ji uživatel zavře (Idle) — jinak by ji přebil
                    // další běh a nikdo by se nedozvěděl, co se stalo.
                    s is GenState.Done && _queue.value.isNotEmpty() -> {
                        delay(prodlevaPoHotovoMs)
                        zavriVysledek()
                        dalsi()
                    }
                    s is GenState.Idle && _queue.value.isNotEmpty() -> {
                        delay(300)
                        dalsi()
                    }
                }
            }
        }
    }

    fun add(run: QueuedRun) {
        _queue.value = _queue.value + run
        if (!bezi()) dalsi()
    }

    fun remove(id: Long) {
        _queue.value = _queue.value.filterNot { it.id == id }
    }

    private fun dalsi() {
        if (bezi()) return
        val next = _queue.value.firstOrNull() ?: return
        _queue.value = _queue.value.drop(1)
        // Spuštění nesmí shodit hlídač: kdyby zmrazené zadání selhalo
        // (smazaný podklad), fronta jede dál dalším během.
        runCatching { next.spust() }
    }
}

/** Fronta běhů na úrovni procesu — přežije zahození obrazovky. */
object RunQueue {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val jadro by lazy {
        QueueCore(
            stav = GenerationEngine.state,
            bezi = { GenerationEngine.isRunning },
            zavriVysledek = { GenerationEngine.dismissResult() },
            scope = scope,
        ).also { it.start() }
    }

    val queue: StateFlow<List<QueuedRun>> get() = jadro.queue
    fun add(run: QueuedRun) = jadro.add(run)
    fun remove(id: Long) = jadro.remove(id)

    /** Zapne hlídač — volá se při startu aplikace, ať běží od první chvíle. */
    fun init() { jadro }
}
