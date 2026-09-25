package cz.promptlab.h3video.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cz.promptlab.h3video.MainViewModel
import cz.promptlab.h3video.MainViewModel.FazePrepisu
import cz.promptlab.h3video.data.t
import cz.promptlab.h3video.ui.theme.Cyan
import cz.promptlab.h3video.ui.theme.TextLow
import cz.promptlab.h3video.ui.theme.TextMid
import kotlinx.coroutines.delay

/** Čas jako 0:07 / 1:23. */
internal fun minSek(s: Long): String = "%d:%02d".format(s / 60, s % 60)

/**
 * Průběh vylepšovače / překladače: co server právě dělá, kolik času uběhlo
 * a kolik asi zbývá. Samotné kolečko uživatel hlásil jako „vůbec nevím, co
 * se děje" (25. 9. 2026) — přepis umí stát minuty ve frontě za generováním
 * nebo při načítání jazykového modelu.
 *
 * Odhad zbývajícího času bere, kolik trval tentýž přepisovač minule (bez
 * čekání ve frontě). Při prvním použití odhad není, jen uběhlý čas.
 */
@Composable
fun PrubehPrepisu(
    vm: MainViewModel,
    druh: MainViewModel.PraceNaPromptu = MainViewModel.PraceNaPromptu.VYLEPSENI,
    barva: Color = Cyan,
) {
    val stav by vm.rewriteState.collectAsStateWithLifecycle()
    val busy = (stav as? MainViewModel.RewriteState.Busy)?.takeIf { it.druh == druh } ?: return
    val prubeh by vm.prubehPrepisu.collectAsStateWithLifecycle()
    val napsano by vm.rewriteProgress.collectAsStateWithLifecycle()

    var ted by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(busy.od) {
        while (true) {
            ted = System.currentTimeMillis()
            delay(1000)
        }
    }
    val ubehlo = ((ted - busy.od) / 1000).coerceAtLeast(0)

    val nadpis = when (prubeh.faze) {
        FazePrepisu.PRIPRAVA -> t("Posílám zadání na server")
        FazePrepisu.FRONTA -> t("Čekám ve frontě serveru — před tebou %d").format(prubeh.predTebou)
        FazePrepisu.MODEL -> t("Načítám jazykový model")
        FazePrepisu.PSANI -> t("Píšu zadání")
    }
    val beziS = if (prubeh.beziOd > 0L) ((ted - prubeh.beziOd) / 1000).coerceAtLeast(0) else 0L
    val odhad = prubeh.obvykleS.toLong()
    val maOdhad = odhad > 0 && prubeh.beziOd > 0L
    val detail = buildString {
        append(t("uběhlo %s").format(minSek(ubehlo)))
        if (maOdhad) {
            append(" · ")
            append(
                if (beziS < odhad) t("zbývá asi %s").format(minSek(odhad - beziS))
                else t("trvá déle než minule (%s)").format(minSek(odhad))
            )
        }
        napsano?.let { (kolik, _) ->
            append(" · ")
            append(t("napsáno %d").format(kolik))
        }
    }

    Spacer(Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        CircularProgressIndicator(Modifier.size(18.dp), color = barva, strokeWidth = 2.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(nadpis, style = MaterialTheme.typography.bodySmall, color = TextMid)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = TextLow)
        }
    }
    if (maOdhad) {
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { (beziS.toFloat() / odhad).coerceIn(0f, 0.97f) },
            color = barva,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
