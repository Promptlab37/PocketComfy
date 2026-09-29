package cz.promptlab.h3video.engine

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Stahování výsledků „Na serveru“ na požádání (5.03).
 *
 * Běží v rozsahu procesu, ne obrazovky — odchod z výsledku nebo z galerie
 * stažení nepřeruší. Když proces zanikne, zůstane `.part` a další pokus ho
 * dotáhne přes `Range`. Stav se drží podle id záznamu: rozběhnuté stažení
 * se podruhé nespustí.
 */
object RucniStahovani {

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    data class Stav(
        /** 0..1 během stahování, null = neběží. */
        val prubeh: Float? = null,
        val chyba: String? = null,
        /** Server odpověděl 404 — soubor na počítači už není. */
        val chybi: Boolean = false,
    )

    private val _stav = MutableStateFlow<Map<String, Stav>>(emptyMap())
    val stav: StateFlow<Map<String, Stav>> = _stav.asStateFlow()

    fun bezi(id: String): Boolean = _stav.value[id]?.prubeh != null

    fun nastav(id: String, s: Stav?) = _stav.update { if (s == null) it - id else it + (id to s) }

    /** Atomicky zabere stahování záznamu; false = už běží. */
    fun zaber(id: String): Boolean {
        var ok = false
        _stav.update {
            if (it[id]?.prubeh != null) it
            else { ok = true; it + (id to Stav(prubeh = 0f)) }
        }
        return ok
    }
}
