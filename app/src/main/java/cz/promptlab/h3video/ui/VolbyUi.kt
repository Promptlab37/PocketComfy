package cz.promptlab.h3video.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cz.promptlab.h3video.MainViewModel
import cz.promptlab.h3video.data.Mode
import cz.promptlab.h3video.data.VolbyKaret

/**
 * Volby karty bez těch, které si uživatel v Nastavení → Karty v aplikaci
 * skryl. Když zbude jediná, výběr se neukazuje vůbec (nabízet jednu možnost
 * je mrtvá volba).
 */
@Composable
fun <T> nabidkaVoleb(vm: MainViewModel, karta: Mode, vse: List<T>, jmeno: (T) -> String): List<T> {
    val skryte by vm.skryteVolby.collectAsStateWithLifecycle()
    return VolbyKaret.viditelne(karta, vse, skryte, jmeno)
}
