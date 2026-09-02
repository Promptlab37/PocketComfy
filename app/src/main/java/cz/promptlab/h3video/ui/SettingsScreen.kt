package cz.promptlab.h3video.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cz.promptlab.h3video.AuditState
import cz.promptlab.h3video.MainViewModel
import cz.promptlab.h3video.UpdateState
import cz.promptlab.h3video.data.AppSettings
import cz.promptlab.h3video.update.UpdateChecker
import cz.promptlab.h3video.ui.theme.Amber
import cz.promptlab.h3video.ui.theme.Cyan
import cz.promptlab.h3video.ui.theme.Danger
import cz.promptlab.h3video.ui.theme.Ok
import cz.promptlab.h3video.ui.theme.Outline1
import cz.promptlab.h3video.ui.theme.Surface1
import cz.promptlab.h3video.ui.theme.Surface2
import cz.promptlab.h3video.ui.theme.TextLow
import cz.promptlab.h3video.ui.theme.TextMid
import cz.promptlab.h3video.ui.theme.Violet
import androidx.compose.ui.graphics.Color

@Composable
fun SettingsScreen(vm: MainViewModel, modifier: Modifier = Modifier) {
    val server by vm.server.collectAsStateWithLifecycle()
    val check by vm.check.collectAsStateWithLifecycle()

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SectionCard(
            title = "Server ComfyUI",
            subtitle = "Adresa počítače, na kterém běží generování"
        ) {
            Column {
                DarkTextField(
                    value = server,
                    onValueChange = { vm.setServer(it) },
                    placeholder = AppSettings.DEFAULT_SERVER
                        .ifBlank { "http://192.168.1.23:8188" },
                    minHeight = 58.dp,
                    singleLine = true,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Adresa se ukládá sama při psaní – tlačítko níž ji jen otestuje.",
                    style = MaterialTheme.typography.bodySmall, color = TextLow
                )
                // Rychlé volby jsou jen v osobním sestavení (local.properties);
                // veřejné žádné cizí adresy nenabízí.
                if (AppSettings.SUGGESTED.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text("Rychlá volba", style = MaterialTheme.typography.labelMedium, color = TextLow)
                    Spacer(Modifier.height(8.dp))
                    PillRow(
                        items = AppSettings.SUGGESTED,
                        selected = AppSettings.SUGGESTED.firstOrNull { it.first == server }
                            ?: (server to ""),
                        label = { it.second.ifEmpty { "vlastní" } },
                        onSelect = { vm.setServer(it.first) }
                    )
                }
                Spacer(Modifier.height(14.dp))
                GradientButton(
                    if (check.checking) "Zkouším spojení…" else "Uložit a otestovat",
                    enabled = !check.checking,
                    onClick = { vm.testServer() }
                )

                if (check.checking) {
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), color = Cyan, strokeWidth = 2.dp)
                        Spacer(Modifier.height(0.dp))
                        Text(
                            "  Připojuji se…",
                            style = MaterialTheme.typography.bodySmall, color = TextMid
                        )
                    }
                }

                check.ok?.let { ok ->
                    Spacer(Modifier.height(14.dp))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background((if (ok) Ok else Danger).copy(alpha = .08f))
                            .border(
                                1.dp, (if (ok) Ok else Danger).copy(alpha = .25f),
                                RoundedCornerShape(14.dp)
                            )
                            .padding(13.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            if (ok) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                            null, Modifier.size(18.dp), if (ok) Ok else Danger
                        )
                        Text(
                            check.message,
                            style = MaterialTheme.typography.bodySmall, color = TextMid
                        )
                    }
                }
            }
        }

        SectionCard(
            title = "Co serveru chybí",
            subtitle = "Nody a modely, které karty appky potřebují"
        ) {
            val audit by vm.audit.collectAsStateWithLifecycle()
            Column {
                Text(
                    "Porovná workflow appky s tím, co tvůj ComfyUI opravdu nabízí — " +
                        "vypíše chybějící custom nody a modely. Higgs Audio je " +
                        "volitelný, bez něj nefunguje jen namlouvání replik.",
                    style = MaterialTheme.typography.bodySmall, color = TextMid
                )
                Spacer(Modifier.height(12.dp))
                GradientButton(
                    if (audit is AuditState.Running) "Porovnávám…" else "Zkontrolovat server",
                    enabled = audit !is AuditState.Running,
                    onClick = { vm.runServerAudit() }
                )
                when (val a = audit) {
                    AuditState.Idle -> {}
                    AuditState.Running -> {
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(18.dp), color = Cyan, strokeWidth = 2.dp)
                            Text(
                                "  Čtu definice uzlů ze serveru…",
                                style = MaterialTheme.typography.bodySmall, color = TextMid
                            )
                        }
                    }
                    is AuditState.Failed -> {
                        Spacer(Modifier.height(12.dp))
                        VysledekRamecek(ok = false, text = a.message)
                    }
                    is AuditState.Done -> {
                        Spacer(Modifier.height(12.dp))
                        val r = a.report
                        // Zpráva se skládá na jednom místě (ServerAudit.zprava),
                        // ať je v rámečku přesně to, co si uživatel zkopíruje
                        // na počítač — včetně balíků, složek a odkazů.
                        val zprava = remember(r) { cz.promptlab.h3video.comfy.ServerAudit.zprava(r) }
                        VysledekRamecek(ok = r.ok, text = zprava)
                        if (!r.ok) {
                            Spacer(Modifier.height(10.dp))
                            val ctx = LocalContext.current
                            OutlineButton(
                                "Zkopírovat seznam",
                                modifier = Modifier.fillMaxWidth(),
                                color = Cyan,
                            ) {
                                val cm = ctx.getSystemService(android.content.ClipboardManager::class.java)
                                cm.setPrimaryClip(
                                    android.content.ClipData.newPlainText("PocketComfy", zprava)
                                )
                                // Android 13+ ukazuje vlastní bublinu o zkopírování sám.
                                if (android.os.Build.VERSION.SDK_INT < 33) {
                                    Toast.makeText(ctx, "Zkopírováno", Toast.LENGTH_SHORT).show()
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Pošli si seznam do počítače (e-mailem, chatem) a stahuj " +
                                    "podle odkazů — nemusíš nic přepisovat.",
                                style = MaterialTheme.typography.bodySmall, color = TextLow
                            )
                        }
                    }
                }
            }
        }

        // Výpis posledního pádu. Ukáže se jen tehdy, když appka opravdu spadla –
        // jinak by tu trvale strašila sekce, která nikoho nezajímá.
        val crash by vm.crash.collectAsStateWithLifecycle()
        crash?.let { text ->
            SectionCard(
                title = "Appka naposledy spadla",
                subtitle = "Tohle pošli vývojáři, je v tom příčina"
            ) {
                Column {
                    Text(
                        text.take(1500),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMid,
                        modifier = Modifier
                            .heightIn(max = 260.dp)
                            .verticalScroll(rememberScrollState())
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlineButton("Zahodit výpis", modifier = Modifier.fillMaxWidth()) {
                        vm.clearCrash()
                    }
                }
            }
        }

        SectionCard(
            title = "Higgs Audio",
            subtitle = "Namlouvání replik pro kartu Mluvící scéna"
        ) {
            val higgsServer by vm.higgsServer.collectAsStateWithLifecycle()
            val higgsCode by vm.higgsCode.collectAsStateWithLifecycle()
            Column {
                Text(
                    "Prázdné pole = stejný počítač jako ComfyUI, port 7860. " +
                        "Higgs se zapíná sám, když necháš namluvit repliku, a před " +
                        "generováním videa se zase vypne – na grafiku se oba modely nevejdou.",
                    style = MaterialTheme.typography.bodySmall, color = TextMid
                )
                Spacer(Modifier.height(12.dp))
                DarkTextField(
                    value = higgsServer,
                    onValueChange = { vm.setHiggsServer(it) },
                    placeholder = "http://192.168.1.23:7860",
                    minHeight = 58.dp,
                    singleLine = true,
                )
                Spacer(Modifier.height(10.dp))
                DarkTextField(
                    value = higgsCode,
                    onValueChange = { vm.setHiggsCode(it) },
                    placeholder = "Přístupový kód (jen když si ho Higgs vyžádá)",
                    minHeight = 58.dp,
                    singleLine = true,
                )
                Spacer(Modifier.height(12.dp))
                GradientButton("Uložit", onClick = { vm.saveHiggs() })
            }
        }

        // Model patří k nastavení serveru, ne mezi pokročilé volby generování –
        // uživatel ho hledal právě tady. Na obrazovce generování zůstává taky,
        // aby se dal přehodit bez odcházení z rozdělané práce.
        SectionCard(
            title = "Model",
            subtitle = "Který MiniMax H3 se použije pro text a snímky"
        ) {
            val params by vm.params.collectAsStateWithLifecycle()
            val modely by vm.availableUnets.collectAsStateWithLifecycle()
            var otevreno by remember { mutableStateOf(false) }

            Column {
                Text(
                    "Výchozí je model z workflow. Vlastní model (třeba komunitní " +
                        "přetrénování) se týká jen karet Text, Obrázek a Osa – " +
                        "Reference, Mluvící scéna a Režisér s referencemi jedou dál " +
                        "na modelu z workflow, protože pro ně taková varianta neexistuje.",
                    style = MaterialTheme.typography.bodySmall, color = TextMid
                )
                Spacer(Modifier.height(12.dp))
                OutlineButton(
                    params.unetFl2va.ifBlank { "Z workflow (výchozí)" },
                    modifier = Modifier.fillMaxWidth(),
                ) { otevreno = !otevreno; if (otevreno) vm.loadUnets() }

                if (otevreno) {
                    Spacer(Modifier.height(8.dp))
                    if (modely.isEmpty()) {
                        Text(
                            "Seznam se načítá ze serveru… když se neobjeví, " +
                                "ComfyUI neodpovídá.",
                            style = MaterialTheme.typography.bodySmall, color = TextLow
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        val nabidka = listOf("") + modely.filter {
                            it.contains("h3", ignoreCase = true)
                        }
                        nabidka.forEach { jmeno ->
                            val vybrano = params.unetFl2va == jmeno
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (vybrano) Violet.copy(alpha = .16f) else Surface2)
                                    .clickable { vm.setUnet(jmeno); otevreno = false }
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                Text(
                                    jmeno.ifBlank { "Z workflow (výchozí)" },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (vybrano) Cyan else TextMid,
                                )
                            }
                        }
                    }
                }
            }
        }

        SectionCard(
            title = "Grafická karta",
            subtitle = "ComfyUI se zapíná samo při generování"
        ) {
            val ctx = LocalContext.current
            Column {
                Text(
                    "Na počítači nic neběží, dokud nedáš Generovat – karta zůstává " +
                        "volná na hry. Když ti generování doběhlo a chceš kartu zpátky " +
                        "hned, vypni ComfyUI tady.",
                    style = MaterialTheme.typography.bodySmall, color = TextMid
                )
                Spacer(Modifier.height(12.dp))
                OutlineButton(
                    "Vypnout ComfyUI a uvolnit grafiku",
                    modifier = Modifier.fillMaxWidth(),
                    color = Amber,
                ) {
                    vm.stopServer { msg ->
                        Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
                    }
                }
                Spacer(Modifier.height(10.dp))
                OutlineButton(
                    "Vypnout Higgs Audio",
                    modifier = Modifier.fillMaxWidth(),
                    color = Amber,
                ) {
                    vm.stopHiggs { msg ->
                        Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

        SectionCard(
            title = "Aby to fungovalo z mobilu",
            subtitle = "Krátký seznam, když se appka nemůže spojit"
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Step(
                    "1",
                    "Počítač musí být zapnutý a přihlášený. ComfyUI se pak spouští samo " +
                        "(úloha „H3 ComfyUI autostart\") – náběh po zapnutí trvá asi 3 minuty."
                )
                Step(
                    "2",
                    "Telefon musí mít zapnutý Tailscale na stejném účtu, nebo být " +
                        "ve stejné Wi-Fi jako počítač."
                )
                Step(
                    "3",
                    "Přes Tailscale se používá port 8189, po domácí síti 8188 – " +
                        "rychlá volba níž nastaví obojí správně."
                )
                Step(
                    "4",
                    "Modely MiniMax H3 (ref2va, qwen3vl enkodér a oba VAE) musí být " +
                        "v ComfyUI stažené – appka je nedoinstaluje."
                )
            }
        }

        val autoSave by vm.autoSave.collectAsStateWithLifecycle()
        SectionCard(
            title = "Ukládat vše do telefonu",
            subtitle = "Normálně vypnuté – stahuješ si jen to, co chceš",
            trailing = {
                Switch(
                    checked = autoSave,
                    onCheckedChange = { vm.autoSaveToGallery = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Violet,
                        uncheckedTrackColor = Surface2,
                        uncheckedBorderColor = Outline1,
                    )
                )
            }
        ) {
            Text(
                if (autoSave)
                    "Zapnuto: každé hotové video se rovnou uloží do Filmy/H3 Video. " +
                        "Hodí se, když chceš mít úplně všechno v telefonu."
                else "Videa zůstanou v Galerii aplikace a do telefonu se uloží až tehdy, " +
                    "když u konkrétního videa klepneš na „Uložit do galerie\". " +
                    "Jen pozor, že odinstalace aplikace neuložená videa smaže.",
                style = MaterialTheme.typography.bodySmall,
                color = TextMid
            )
            Spacer(Modifier.height(10.dp))
            // Jednorázová záchrana: dohraje do telefonu všechno, co tam chybí –
            // třeba videa vygenerovaná před zapnutím přepínače.
            var dohrano by remember { mutableStateOf<Int?>(null) }
            OutlineButton("Doplnit chybějící videa do galerie telefonu") {
                vm.saveAllToGallery { dohrano = it }
            }
            dohrano?.let {
                Spacer(Modifier.height(6.dp))
                Text(
                    if (it == 0) "Nic nechybělo – všechna videa už v telefonu jsou."
                    else "Uloženo $it videí do Filmy/H3 Video.",
                    style = MaterialTheme.typography.bodySmall, color = Ok
                )
            }
        }

        UpdateCard(vm)

        SectionCard(title = "O aplikaci") {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "Pouští workflow MiniMax H3 Reference-to-Video přesně tak, jak je " +
                        "uložené v ComfyUI. Aplikace mění jen prompt, referenční obrázky, " +
                        "délku, rozlišení a pokročilé volby – zbytek grafu zůstává nedotčený.",
                    style = MaterialTheme.typography.bodySmall, color = TextMid
                )
                Text(
                    "Obraz i zvuk vznikají v jednom průchodu, 24 snímků za sekundu.",
                    style = MaterialTheme.typography.bodySmall, color = TextLow
                )
            }
        }

        Spacer(Modifier.height(12.dp))
    }
}

/** Barevný rámeček s výsledkem (zelený = v pořádku, červený = problém). */
@Composable
fun VysledekRamecek(ok: Boolean, text: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background((if (ok) Ok else Danger).copy(alpha = .08f))
            .border(1.dp, (if (ok) Ok else Danger).copy(alpha = .25f), RoundedCornerShape(14.dp))
            .padding(13.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            if (ok) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
            null, Modifier.size(18.dp), if (ok) Ok else Danger
        )
        Text(text, style = MaterialTheme.typography.bodySmall, color = TextMid)
    }
}

@Composable
private fun UpdateCard(vm: MainViewModel) {
    val ctx = LocalContext.current
    val state by vm.update.collectAsStateWithLifecycle()

    val token by vm.token.collectAsStateWithLifecycle()

    SectionCard(
        title = "Aktualizace",
        subtitle = "Verze ${vm.versionName} (sestavení ${vm.versionCode})"
    ) {
        Column {
            // Od 2.20 je token součástí sestavení, takže tu není co vyplňovat.
            // Políčko se ukáže jen tehdy, když appka token nemá – nebo když si
            // uživatel sám uložil vlastní, který ten zapečený přebíjí.
            if (token.isBlank() && vm.hasBuiltInToken) {
                Text(
                    "Aktualizace jsou nastavené — token je součástí aplikace, " +
                        "nemusíš nic vyplňovat.",
                    style = MaterialTheme.typography.bodySmall, color = TextMid
                )
                Spacer(Modifier.height(12.dp))
            } else {
                Text(
                    "Vlož GitHub token — ten samý, který máš v PromptLab Relay. " +
                        "Zůstane jen v telefonu.",
                    style = MaterialTheme.typography.bodySmall, color = TextMid
                )
                Spacer(Modifier.height(10.dp))
                DarkTextField(
                    value = token,
                    onValueChange = { vm.setToken(it) },
                    placeholder = "github_pat_… / ghp_…",
                    minHeight = 58.dp,
                    singleLine = true,
                )
                Spacer(Modifier.height(10.dp))
                GradientButton("Uložit token a zkontrolovat") { vm.saveToken() }
                Spacer(Modifier.height(14.dp))
            }

            when (val s = state) {
                is UpdateState.Available -> Column {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Cyan.copy(alpha = .08f))
                            .border(1.dp, Cyan.copy(alpha = .25f), RoundedCornerShape(14.dp))
                            .padding(13.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.NewReleases, null, Modifier.size(18.dp), Cyan)
                        Column {
                            Text(
                                "Je k dispozici ${s.info.versionName}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            if (s.info.notes.isNotBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    s.info.notes,
                                    style = MaterialTheme.typography.bodySmall, color = TextMid
                                )
                            }
                            if (s.info.sizeBytes > 0) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "%.1f MB".format(s.info.sizeBytes / 1_048_576f),
                                    style = MaterialTheme.typography.bodySmall, color = TextLow
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    GradientButton("Stáhnout a nainstalovat") { vm.downloadUpdate(s.info) }
                }

                is UpdateState.Downloading -> Column {
                    Text(
                        "Stahuji… ${(s.progress * 100).toInt()} %",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { s.progress },
                        modifier = Modifier.fillMaxWidth(),
                        color = Cyan,
                        trackColor = Outline1,
                    )
                }

                is UpdateState.Ready -> Column {
                    Text(
                        "Staženo. Android se teď zeptá na potvrzení instalace.",
                        style = MaterialTheme.typography.bodySmall, color = TextMid
                    )
                    Spacer(Modifier.height(12.dp))
                    GradientButton("Nainstalovat ${s.info.versionName}") {
                        if (UpdateChecker.canInstall(ctx)) {
                            ctx.startActivity(UpdateChecker.installIntent(ctx, s.apk))
                        } else {
                            // bez tohoto povolení Android instalaci odmítne bez vysvětlení
                            ctx.startActivity(UpdateChecker.unknownSourcesIntent(ctx))
                        }
                    }
                }

                is UpdateState.Failed -> Column {
                    Text(s.message, style = MaterialTheme.typography.bodySmall, color = Danger)
                    Spacer(Modifier.height(12.dp))
                    OutlineButton("Zkusit znovu", modifier = Modifier.fillMaxWidth()) {
                        vm.checkUpdate()
                    }
                }

                UpdateState.Checking -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = Cyan, strokeWidth = 2.dp)
                    Text(
                        "  Hledám novou verzi…",
                        style = MaterialTheme.typography.bodySmall, color = TextMid
                    )
                }

                UpdateState.UpToDate -> Column {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CheckCircle, null, Modifier.size(18.dp), Ok)
                        Text(
                            "Máš nejnovější verzi.",
                            style = MaterialTheme.typography.bodySmall, color = TextMid
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlineButton("Zkontrolovat znovu", modifier = Modifier.fillMaxWidth()) {
                        vm.checkUpdate()
                    }
                }

                UpdateState.Idle -> OutlineButton(
                    "Zkontrolovat aktualizace",
                    modifier = Modifier.fillMaxWidth()
                ) { vm.checkUpdate() }
            }

            if (token.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "Změnit GitHub token",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextLow,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .clickable { vm.setToken(""); vm.saveToken(); vm.dismissUpdate() }
                        .padding(6.dp)
                )
            }
        }
    }
}

@Composable
private fun Step(number: String, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        androidx.compose.foundation.layout.Box(
            Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(50))
                .background(Surface1)
                .border(1.dp, Outline1, RoundedCornerShape(50)),
            contentAlignment = Alignment.Center
        ) {
            Text(number, style = MaterialTheme.typography.labelMedium, color = Cyan)
        }
        Text(text, style = MaterialTheme.typography.bodySmall, color = TextMid)
    }
}
