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
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ExpandLess
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
import cz.promptlab.h3video.data.t
import cz.promptlab.h3video.update.UpdateChecker
import cz.promptlab.h3video.ui.theme.Amber
import cz.promptlab.h3video.ui.theme.Cyan
import cz.promptlab.h3video.ui.theme.TextHi
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
        // Vždy vidět jen to, co se řeší nejčastěji; zbytek je ve sbalených
        // skupinách (dřív šest obrazovek sekcí pod sebou).
        SectionCard(
            title = t("Server ComfyUI"),
            subtitle = t("Adresa počítače, na kterém běží generování")
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
                // Rychlé volby jsou jen v osobním sestavení (local.properties);
                // veřejné žádné cizí adresy nenabízí.
                if (AppSettings.SUGGESTED.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text(t("Rychlá volba"), style = MaterialTheme.typography.labelMedium, color = TextLow)
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
                    if (check.checking) t("Zkouším spojení…") else t("Uložit a otestovat"),
                    enabled = !check.checking,
                    onClick = { vm.testServer() }
                )

                if (check.checking) {
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), color = Cyan, strokeWidth = 2.dp)
                        Spacer(Modifier.height(0.dp))
                        Text(
                            t("  Připojuji se…"),
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

        // Výpis posledního pádu. Ukáže se jen tehdy, když appka opravdu spadla –
        // jinak by tu trvale strašila sekce, která nikoho nezajímá.
        val crash by vm.crash.collectAsStateWithLifecycle()
        crash?.let { text ->
            SectionCard(
                title = t("Appka naposledy spadla"),
                subtitle = t("Tohle pošli vývojáři, je v tom příčina")
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
                    OutlineButton(t("Zahodit výpis"), modifier = Modifier.fillMaxWidth()) {
                        vm.clearCrash()
                    }
                }
            }
        }

        UpdateCard(vm)


        SkladaciSekce(
            title = t("Server a modely"),
            souhrn = t("Kontrola serveru, model, Higgs, grafická karta"),
            klic = "nastaveni-server",
        ) {
            SectionCard(
                title = t("Co serveru chybí"),
                subtitle = t("Nody a modely, které karty appky potřebují")
            ) {
                val audit by vm.audit.collectAsStateWithLifecycle()
                Column {
                    OutlineButton(
                        if (audit is AuditState.Running) t("Porovnávám…") else t("Zkontrolovat server"),
                        modifier = Modifier.fillMaxWidth(),
                        color = Cyan,
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
                                    t("  Čtu definice uzlů ze serveru…"),
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
                                    t("Zkopírovat seznam"),
                                    modifier = Modifier.fillMaxWidth(),
                                    color = Cyan,
                                ) {
                                    val cm = ctx.getSystemService(android.content.ClipboardManager::class.java)
                                    cm.setPrimaryClip(
                                        android.content.ClipData.newPlainText("PocketComfy", zprava)
                                    )
                                    // Android 13+ ukazuje vlastní bublinu o zkopírování sám.
                                    if (android.os.Build.VERSION.SDK_INT < 33) {
                                        Toast.makeText(ctx, t("Zkopírováno"), Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Model patří k nastavení serveru, ne mezi pokročilé volby generování –
            // uživatel ho hledal právě tady. Na obrazovce generování zůstává taky,
            // aby se dal přehodit bez odcházení z rozdělané práce.
            SectionCard(
                title = t("Model"),
                subtitle = t("Který MiniMax H3 se použije pro text a snímky")
            ) {
                val params by vm.params.collectAsStateWithLifecycle()
                val modely by vm.availableUnets.collectAsStateWithLifecycle()
                var otevreno by remember { mutableStateOf(false) }

                Column {
                    OutlineButton(
                        params.unetFl2va.ifBlank { t("Z workflow (výchozí)") },
                        modifier = Modifier.fillMaxWidth(),
                    ) { otevreno = !otevreno; if (otevreno) vm.loadUnets() }

                    if (otevreno) {
                        Spacer(Modifier.height(8.dp))
                        if (modely.isEmpty()) {
                            Text(
                                "Seznam se načítá ze serveru… když se neobjeví, " +
                                    t("ComfyUI neodpovídá."),
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
                                        jmeno.ifBlank { t("Z workflow (výchozí)") },
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
                title = "Higgs Audio",
                subtitle = t("Namlouvání replik pro kartu Mluvící scéna")
            ) {
                val higgsServer by vm.higgsServer.collectAsStateWithLifecycle()
                val higgsCode by vm.higgsCode.collectAsStateWithLifecycle()
                Column {
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
                        placeholder = t("Přístupový kód (jen když si ho Higgs vyžádá)"),
                        minHeight = 58.dp,
                        singleLine = true,
                        secret = true,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlineButton(t("Uložit"), modifier = Modifier.fillMaxWidth(), color = Cyan, onClick = { vm.saveHiggs() })
                }
            }

            SectionCard(
                title = t("Grafická karta"),
                subtitle = "ComfyUI se zapíná samo při generování"
            ) {
                val ctx = LocalContext.current
                Column {
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

            // Když je grafika plná, model se dohrává po částech z RAM a stejné
            // generování trvá i několikrát déle. Appka umí říct ComfyUI, ať pustí
            // svoje modely; na cizí programy nesahá.
            val vramStav by vm.vramStav.collectAsStateWithLifecycle()
            val vramPracuje by vm.vramPracuje.collectAsStateWithLifecycle()
            SectionCard(
                title = t("Paměť grafiky"),
                subtitle = t("Když je plná, generování se táhne")
            ) {
                Column {
                    Text(
                        if (vramStav.isBlank())
                            ""
                        else vramStav,
                        style = MaterialTheme.typography.bodySmall, color = TextMid
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlineButton(
                            if (vramPracuje) t("Zjišťuji…") else t("Zjistit stav"),
                            modifier = Modifier.weight(1f),
                        ) { if (!vramPracuje) vm.zjistiVram(uvolnit = false) }
                        OutlineButton(
                            t("Uvolnit paměť"),
                            modifier = Modifier.weight(1f),
                        ) { if (!vramPracuje) vm.zjistiVram(uvolnit = true) }
                    }
                }
            }
        }

        KartyVAplikaci(vm)

        SkladaciSekce(
            title = t("Aplikace"),
            souhrn = t("Jazyk, výběr fotek, ukládání, navigace"),
            klic = "nastaveni-aplikace",
        ) {
            val autoSave by vm.autoSave.collectAsStateWithLifecycle()
            // Jazyk rozhraní. Výchozí „Podle telefonu" = čeština na českém
            // telefonu, jinak angličtina — cizí uživatel tak nic hledat nemusí.
            SectionCard(
                title = t("Jazyk"),
                subtitle = t("Jazyk rozhraní; nepřeložené části zůstanou česky.")
            ) {
                PillRow(
                    items = cz.promptlab.h3video.data.Jazyk.Volba.entries.toList(),
                    selected = cz.promptlab.h3video.data.Jazyk.volba,
                    label = {
                        when (it) {
                            cz.promptlab.h3video.data.Jazyk.Volba.SYSTEM -> t("Podle telefonu")
                            cz.promptlab.h3video.data.Jazyk.Volba.CS -> t("Čeština")
                            cz.promptlab.h3video.data.Jazyk.Volba.EN -> t("Angličtina")
                        }
                    },
                    onSelect = { vm.setJazyk(it) },
                )
            }

            val zeSouboru by vm.vyberZeSouboru.collectAsStateWithLifecycle()
            SectionCard(
                title = t("Výběr fotek"),
                subtitle = if (zeSouboru) t("Ze souborů — naposledy upravené jsou nahoře")
                else t("Systémový výběr — řazení podle data pořízení"),
            ) {
                PillRow(
                    items = listOf(false, true),
                    selected = zeSouboru,
                    label = { if (it) t("Soubory") else t("Galerie") },
                    onSelect = { vm.setVyberZeSouboru(it) },
                )
            }

            val hledat by vm.hledatNaInternetu.collectAsStateWithLifecycle()
            SectionCard(
                title = t("Hledat obrázky na internetu"),
                trailing = {
                    Switch(
                        checked = hledat,
                        onCheckedChange = { vm.setHledatNaInternetu(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Violet,
                            uncheckedTrackColor = Surface2,
                            uncheckedBorderColor = Outline1,
                        )
                    )
                }
            ) {}

            SectionCard(
                title = t("Ukládat vše do telefonu"),
                subtitle = t("Normálně vypnuté – stahuješ si jen to, co chceš"),
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
                Spacer(Modifier.height(10.dp))
                // Jednorázová záchrana: dohraje do telefonu všechno, co tam chybí –
                // třeba videa vygenerovaná před zapnutím přepínače.
                var dohrano by remember { mutableStateOf<Int?>(null) }
                OutlineButton(t("Doplnit chybějící videa do galerie telefonu")) {
                    vm.saveAllToGallery { dohrano = it }
                }
                dohrano?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (it == 0) t("Nic nechybělo – všechna videa už v telefonu jsou.")
                        else t("Uloženo %d videí do Filmy/H3 Video.").format(it),
                        style = MaterialTheme.typography.bodySmall, color = Ok
                    )
                }
            }

            // Spodní systémová tlačítka ukusují kus obrazovky přímo pod tlačítkem
            // Generovat. Schovaná se vytáhnou přejetím od spodního okraje, takže
            // se z telefonu nikam neztratí — proto je to zapnuté ve výchozím stavu.
            val kontext = androidx.compose.ui.platform.LocalContext.current
            var skryvat by remember {
                mutableStateOf(cz.promptlab.h3video.data.AppSettings(kontext).skryvatNavigaci)
            }
            SectionCard(
                title = t("Schovat navigační tlačítka"),
                subtitle = t("Víc místa na obrazovce; vytáhneš je přejetím zespodu"),
                trailing = {
                    Switch(
                        checked = skryvat,
                        onCheckedChange = { zapnuto ->
                            skryvat = zapnuto
                            cz.promptlab.h3video.data.AppSettings(kontext).skryvatNavigaci = zapnuto
                            // Projeví se hned, ne až po přepnutí aplikace.
                            (kontext as? android.app.Activity)?.let { a ->
                                val rizeni = androidx.core.view.WindowCompat
                                    .getInsetsController(a.window, a.window.decorView)
                                rizeni.systemBarsBehavior = androidx.core.view
                                    .WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                                val lista = androidx.core.view.WindowInsetsCompat.Type.navigationBars()
                                if (zapnuto) rizeni.hide(lista) else rizeni.show(lista)
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Violet,
                            uncheckedTrackColor = Surface2,
                            uncheckedBorderColor = Outline1,
                        )
                    )
                }
            ) {
            }
        }

        SkladaciSekce(
            title = t("Připojení z mobilu"),
            souhrn = t("Když se appka nemůže spojit"),
            klic = "nastaveni-pripojeni",
        ) {
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
    var showToken by remember { mutableStateOf(false) }

    SectionCard(
        title = t("Aktualizace"),
        stav = t("Verze %s (sestavení %d)").format(vm.versionName, vm.versionCode)
    ) {
        Column {
            Text(
                t("Veřejné aktualizace fungují bez tokenu."),
                style = MaterialTheme.typography.bodySmall, color = TextMid
            )
            Spacer(Modifier.height(12.dp))
            if (showToken || token.isNotBlank()) {
                Text(
                    t("Token je potřeba jen pro soukromá vydání. Zůstane v telefonu."),
                    style = MaterialTheme.typography.bodySmall, color = TextMid
                )
                Spacer(Modifier.height(10.dp))
                DarkTextField(
                    value = token,
                    onValueChange = { vm.setToken(it) },
                    placeholder = "GitHub token",
                    minHeight = 58.dp,
                    singleLine = true,
                    secret = true,
                )
                Spacer(Modifier.height(10.dp))
                GradientButton(t("Uložit a zkontrolovat")) { vm.saveToken(); showToken = false }
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
                    GradientButton(t("Stáhnout a nainstalovat")) { vm.downloadUpdate(s.info) }
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
                        t("Staženo. Android se teď zeptá na potvrzení instalace."),
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
                        t("  Hledám novou verzi…"),
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
                            t("Máš nejnovější verzi."),
                            style = MaterialTheme.typography.bodySmall, color = TextMid
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlineButton("Zkontrolovat znovu", modifier = Modifier.fillMaxWidth()) {
                        vm.checkUpdate()
                    }
                }

                UpdateState.Idle -> OutlineButton(
                    t("Zkontrolovat aktualizace"),
                    modifier = Modifier.fillMaxWidth()
                ) { vm.checkUpdate() }
            }

            if (token.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    t("Přejít na veřejná vydání"),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextLow,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .clickable { vm.setToken(""); vm.saveToken(); showToken = false }
                        .padding(6.dp)
                )
            } else if (!showToken) {
                Spacer(Modifier.height(12.dp))
                OutlineButton(t("Soukromá vydání"), modifier = Modifier.fillMaxWidth()) {
                    showToken = true
                }
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

/**
 * Které karty a které volby uvnitř nich se v appce ukazují.
 *
 * Dvě úrovně (karta → její motory, modely a režimy), víc ne. Karta má
 * zaškrtávátko se třemi stavy: zapnutá, vypnutá, částečně (pomlčka, když
 * jsou zapnuté jen některé volby). Klepnutí na kartu zapne/vypne všechno
 * uvnitř, klepnutí na volbu přepočítá kartu. Poslední viditelnou kartu
 * vypnout nejde. Kontrola modelů ukáže u každé volby, jestli ji server
 * umí a kolik GB by chybělo — a jedním tlačítkem jde nechat jen to, co umí.
 */
@Composable
private fun KartyVAplikaci(vm: MainViewModel) {
    val skryteKarty by vm.skryteKarty.collectAsStateWithLifecycle()
    val skryteVolby by vm.skryteVolby.collectAsStateWithLifecycle()
    val ucinne by vm.skryteKartyUcinne.collectAsStateWithLifecycle()
    val stav by vm.stavVoleb.collectAsStateWithLifecycle()
    val kontroluje by vm.kontrolaVoleb.collectAsStateWithLifecycle()
    val chyba by vm.kontrolaVolebChyba.collectAsStateWithLifecycle()
    val vsechny = cz.promptlab.h3video.data.NABIZENE_KARTY
    SkladaciSekce(
        title = t("Karty v aplikaci"),
        souhrn = t("Zobrazeno %d z %d").format(vsechny.count { it !in ucinne }, vsechny.size),
        klic = "nastaveni-karty",
    ) {
        cz.promptlab.h3video.data.Skupina.entries.forEach { sk ->
            SectionCard(title = sk.title) {
                Column {
                    sk.karty.forEach { karta ->
                        RadekKarty(vm, karta, skryteKarty, skryteVolby, ucinne, stav)
                    }
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlineButton(
                if (kontroluje) t("Zjišťuji modely na serveru…") else t("Zkontrolovat modely na serveru"),
                modifier = Modifier.fillMaxWidth(),
                color = Cyan,
                enabled = !kontroluje,
            ) { vm.zkontrolujVolby() }
            chyba?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Amber) }
            val chybiViditelne = stav?.filter { (k, v) -> !v.naServeru && k !in skryteVolby }.orEmpty()
            if (chybiViditelne.isNotEmpty()) {
                OutlineButton(
                    t("Nechat jen to, co server má"),
                    modifier = Modifier.fillMaxWidth(),
                ) { vm.nechatJenCoServerMa() }
            }
            if (skryteKarty.isNotEmpty() || skryteVolby.isNotEmpty()) {
                OutlineButton(t("Zobrazit všechny"), modifier = Modifier.fillMaxWidth()) {
                    vm.zobrazitVsechnyKarty()
                }
            }
        }
    }
}

@Composable
private fun RadekKarty(
    vm: MainViewModel,
    karta: cz.promptlab.h3video.data.Mode,
    skryteKarty: Set<cz.promptlab.h3video.data.Mode>,
    skryteVolby: Set<String>,
    ucinne: Set<cz.promptlab.h3video.data.Mode>,
    stav: Map<String, MainViewModel.StavVolby>?,
) {
    val volby = cz.promptlab.h3video.data.VolbyKaret.proKartu(karta)
    var rozbaleno by androidx.compose.runtime.saveable.rememberSaveable(karta.name) {
        androidx.compose.runtime.mutableStateOf(false)
    }
    val zapnute = volby.count { it.klic !in skryteVolby }
    val stavKarty = when {
        karta in ucinne -> androidx.compose.ui.state.ToggleableState.Off
        volby.isNotEmpty() && zapnute < volby.size -> androidx.compose.ui.state.ToggleableState.Indeterminate
        else -> androidx.compose.ui.state.ToggleableState.On
    }
    // Klepnutí na částečnou kartu ji zapne celou (tak to má Material).
    val zapnout = stavKarty != androidx.compose.ui.state.ToggleableState.On
    val jdeZmenit = zapnout || cz.promptlab.h3video.data.lzeSkryt(ucinne, karta)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable {
                if (volby.isNotEmpty()) rozbaleno = !rozbaleno
                else if (jdeZmenit) vm.nastavViditelnostKarty(karta, zapnout)
            }
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.material3.TriStateCheckbox(
            state = stavKarty,
            onClick = { if (jdeZmenit) vm.nastavViditelnostKarty(karta, zapnout) },
            enabled = jdeZmenit,
        )
        Column(Modifier.weight(1f)) {
            Text(
                karta.title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (karta in ucinne) TextMid else TextHi,
            )
            if (volby.isNotEmpty()) Text(
                t("%d z %d").format(if (karta in skryteKarty) 0 else zapnute, volby.size),
                style = MaterialTheme.typography.bodySmall,
                color = TextLow,
            )
        }
        if (volby.isNotEmpty()) Icon(
            if (rozbaleno) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = if (rozbaleno) t("Sbalit") else t("Rozbalit"),
            tint = TextMid,
            modifier = Modifier.padding(end = 8.dp),
        )
    }
    if (rozbaleno) volby.forEach { v ->
        val zapnuta = v.klic !in skryteVolby && karta !in skryteKarty
        val jdeVypnout = !zapnuta || run {
            val nove = cz.promptlab.h3video.data.VolbyKaret.ucinneSkryte(skryteKarty, skryteVolby + v.klic)
            !cz.promptlab.h3video.data.NABIZENE_KARTY.all { it in nove }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 32.dp)
                .clip(RoundedCornerShape(10.dp))
                .clickable(enabled = jdeVypnout) { vm.nastavVolbu(v, !zapnuta) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.material3.Checkbox(
                checked = zapnuta,
                onCheckedChange = { vm.nastavVolbu(v, it) },
                enabled = jdeVypnout,
            )
            Column(Modifier.weight(1f)) {
                Text(
                    v.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (zapnuta) TextHi else TextMid,
                )
                stav?.get(v.klic)?.let { st ->
                    val (text, barva) = when {
                        st.naServeru -> t("na serveru") to Ok
                        st.chybiSoubory.isEmpty() -> t("chybí doplněk ComfyUI") to Amber
                        st.gb > 0.0 -> t("chybí %s GB").format(
                            (if (st.gb >= 10) "%.0f" else "%.1f").format(st.gb) + if (st.neznamaVelikost) "+" else ""
                        ) to Amber
                        else -> t("chybí %d souborů").format(st.chybiSoubory.size) to Amber
                    }
                    Text(text, style = MaterialTheme.typography.bodySmall, color = barva)
                }
            }
        }
    }
}
