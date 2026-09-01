package cz.promptlab.h3video.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cz.promptlab.h3video.MainViewModel
import cz.promptlab.h3video.ui.theme.Cyan
import cz.promptlab.h3video.ui.theme.Ink
import cz.promptlab.h3video.ui.theme.TextHi
import cz.promptlab.h3video.ui.theme.TextMid

/**
 * První spuštění bez nastaveného serveru (veřejné sestavení bez zapečené
 * adresy). Appka je jen klient — bez vlastního ComfyUI nedělá nic, takže
 * se na adresu zeptá dřív, než pustí dál. Osobní sestavení s výchozí
 * adresou v `local.properties` tuhle obrazovku nikdy neukáže.
 */
@Composable
fun OnboardingScreen(vm: MainViewModel) {
    val server by vm.server.collectAsStateWithLifecycle()
    val check by vm.check.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .background(Ink)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        Text(
            "Vítej v H3 Video",
            style = MaterialTheme.typography.headlineSmall,
            color = TextHi,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "Appka je klient pro tvůj vlastní ComfyUI server — všechno se " +
                "generuje na tvém počítači, nikam jinam se nic neposílá.\n\n" +
                "Zadej adresu počítače, na kterém ComfyUI běží. Musí být " +
                "spuštěné s parametrem --listen 0.0.0.0 a telefon musí být " +
                "na stejné síti nebo VPN (např. Tailscale).",
            style = MaterialTheme.typography.bodyMedium,
            color = TextMid
        )
        Spacer(Modifier.height(24.dp))
        DarkTextField(
            value = server,
            onValueChange = { vm.setServer(it) },
            placeholder = "http://192.168.1.23:8188",
            minHeight = 58.dp,
            singleLine = true,
        )
        Spacer(Modifier.height(14.dp))
        GradientButton(
            if (check.checking) "Zkouším spojení…" else "Otestovat spojení",
            enabled = !check.checking && server.isNotBlank(),
            onClick = { vm.testServer() }
        )

        if (check.checking) {
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), color = Cyan, strokeWidth = 2.dp)
                Text(
                    "  Připojuji se…",
                    style = MaterialTheme.typography.bodySmall, color = TextMid
                )
            }
        }
        check.ok?.let { ok ->
            Spacer(Modifier.height(14.dp))
            VysledekRamecek(ok = ok, text = check.message)
        }

        Spacer(Modifier.height(24.dp))
        if (check.ok == true) {
            GradientButton("Vstoupit do appky", onClick = { vm.finishOnboarding() })
        } else {
            // I bez úspěšného testu se dá pokračovat — počítač třeba zrovna
            // neběží a adresu jde doladit později v Nastavení.
            OutlineButton(
                "Pokračovat bez testu",
                modifier = Modifier.fillMaxWidth(),
                onClick = { if (server.isNotBlank()) vm.finishOnboarding() }
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(
            "V Nastavení pak najdeš tlačítko „Zkontrolovat server\" — vypíše, " +
                "jestli na serveru nechybí custom nody nebo modely, které " +
                "karty appky potřebují.",
            style = MaterialTheme.typography.bodySmall,
            color = TextMid
        )
    }
}
