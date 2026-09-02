package cz.promptlab.h3video.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cz.promptlab.h3video.MainViewModel
import cz.promptlab.h3video.data.t
import cz.promptlab.h3video.ui.theme.Danger
import cz.promptlab.h3video.ui.theme.TextLow
import cz.promptlab.h3video.ui.theme.TextMid
import cz.promptlab.h3video.ui.theme.Violet

/**
 * 🌐 **Přeložit do angličtiny** — jedno tlačítko pod pole se zadáním.
 *
 * Modely rozumí anglicky nejlíp, ale nutit uživatele psát anglicky je otrava.
 * Na rozdíl od ✨ vylepšovače tohle zadání **nerozepisuje** — vrátí přesně to,
 * co napsal, jen anglicky. Kdo chce rozepsat, má vedle druhé tlačítko.
 *
 * Běží to na tomtéž llama.cpp uzlu a modelu v `models/LLM` jako vylepšovač,
 * takže když je na serveru jeden, funguje obojí.
 *
 * [popisek] se hodí tam, kde je potřeba říct, co se přeloží (segment osy…).
 */
@Composable
fun PrekladPromptu(
    vm: MainViewModel,
    pole: MainViewModel.PromptPole,
    popisek: String = t("🌐 Přeložit do angličtiny"),
) {
    val stav by vm.rewriteState.collectAsStateWithLifecycle()
    val puvodni by vm.rewriteOriginal.collectAsStateWithLifecycle()
    val bezi = stav is MainViewModel.RewriteState.Busy

    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlineButton(
            if (bezi) t("Překládám…") else popisek,
            color = Violet,
        ) { if (!bezi) vm.prelozPrompt(pole) }
        if (bezi) {
            Spacer(Modifier.width(10.dp))
            CircularProgressIndicator(Modifier.size(18.dp), color = Violet, strokeWidth = 2.dp)
        }
        if (!bezi && puvodni != null) {
            Spacer(Modifier.width(12.dp))
            Text(
                t("Vrátit původní"),
                style = MaterialTheme.typography.bodySmall, color = TextMid,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { vm.vratPuvodni(pole) }
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            )
        }
    }
    (stav as? MainViewModel.RewriteState.Fail)?.let {
        Spacer(Modifier.height(4.dp))
        Text(it.message, style = MaterialTheme.typography.bodySmall, color = Danger)
    }
    Spacer(Modifier.height(4.dp))
    Text(
        t("Napiš zadání česky a nech ho přeložit — obsah zůstane, jen bude anglicky."),
        style = MaterialTheme.typography.bodySmall, color = TextLow
    )
}
