package cz.promptlab.h3video.ui

import cz.promptlab.h3video.data.t

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import kotlin.math.roundToInt
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cz.promptlab.h3video.MainViewModel
import cz.promptlab.h3video.ServerState
import cz.promptlab.h3video.ServerStatus
import cz.promptlab.h3video.data.AioMode
import cz.promptlab.h3video.data.Aspect
import cz.promptlab.h3video.data.MAX_SECONDS
import cz.promptlab.h3video.data.sizeStepsFor
import cz.promptlab.h3video.data.MIN_SECONDS
import cz.promptlab.h3video.data.Mode
import cz.promptlab.h3video.data.Profile
import cz.promptlab.h3video.data.Resolution
import cz.promptlab.h3video.data.SAMPLERS
import cz.promptlab.h3video.data.SCHEDULERS
import cz.promptlab.h3video.data.TURBO
import cz.promptlab.h3video.util.CameraCapture
import cz.promptlab.h3video.ui.theme.Amber
import cz.promptlab.h3video.ui.theme.Danger
import cz.promptlab.h3video.ui.theme.Cyan
import cz.promptlab.h3video.ui.theme.Ok
import cz.promptlab.h3video.ui.theme.Outline1
import cz.promptlab.h3video.ui.theme.Surface1
import cz.promptlab.h3video.ui.theme.Surface2
import cz.promptlab.h3video.ui.theme.TextHi
import cz.promptlab.h3video.ui.theme.TextLow
import cz.promptlab.h3video.ui.theme.TextMid
import cz.promptlab.h3video.ui.theme.Violet

/**
 * Jednořádkový stav počítače s ComfyUI. Když běží, je to jen tichá zelená tečka;
 * když neodpovídá, řekne to rovnou i s tím, že se na počítači spouští sám a že
 * stačí počkat – uživatel tak ví, na čem je, ještě než zmáčkne Generovat.
 */
@Composable
private fun ServerBanner(status: ServerStatus, onRetry: () -> Unit) {
    val offline = status.state == ServerState.OFFLINE
    val color = when (status.state) {
        ServerState.ONLINE -> Ok
        ServerState.OFFLINE -> Amber
        ServerState.UNKNOWN -> TextLow
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (offline) Amber.copy(alpha = 0.10f) else Color.Transparent)
            .clickable(enabled = offline) { onRetry() }
            .padding(horizontal = if (offline) 12.dp else 2.dp, vertical = if (offline) 10.dp else 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(color)
        )
        Spacer(Modifier.size(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                when {
                    status.state == ServerState.ONLINE -> t("Počítač je připravený")
                    status.state == ServerState.OFFLINE && status.duringRun ->
                        t("Telefon je bez spojení s počítačem")
                    // Vypnuté ComfyUI je normální stav, ne porucha – zapne se samo,
                    // až dáš Generovat. Nesmí to tedy vypadat jako chyba.
                    status.state == ServerState.OFFLINE -> t("ComfyUI je vypnuté")
                    else -> t("Zjišťuji stav počítače…")
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (offline) TextHi else TextLow,
                fontWeight = if (offline) FontWeight.SemiBold else FontWeight.Normal
            )
            if (offline) {
                Text(
                    if (status.duringRun)
                        t("Generování na počítači běží dál – appka se připojí sama, ") +
                            t("jakmile bude spojení zpátky.")
                    else
                        t("Grafika je volná. Až dáš Generovat, appka ho zapne sama – ") +
                            t("náběh trvá asi tři minuty."),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMid
                )
            }
        }
    }
}

/**
 * Volba mezi rychlostí a kvalitou. Platí pro všechny tři karty – je to kolmé
 * na to, čím se zadává (text / obrázek / reference), proto to není čtvrtá karta.
 */
@Composable
private fun ProfilePicker(
    selected: Profile,
    referencniCesta: Boolean,
    onSelect: (Profile) -> Unit,
) {
    // Čtyři profily vedle sebe by měly sloupce široké jako slovo a popisek by se
    // lámal po slabikách – proto dva řádky po dvou.
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Surface1)
            .padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Profile.entries.chunked(2).forEach { dvojice ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                dvojice.forEach { p ->
                    val active = p == selected
                    // FastH3 destiloval jen text→video; na referenční váhy
                    // nesedí, takže se u referenčních karet nedá vybrat.
                    val nejde = referencniCesta && p.bezReferenci
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(11.dp))
                            .background(if (active) Violet.copy(alpha = .22f) else Color.Transparent)
                            .clickable(enabled = !nejde) { onSelect(p) }
                            .alpha(if (nejde) 0.4f else 1f)
                            .padding(vertical = 9.dp, horizontal = 10.dp)
                    ) {
                        Text(
                            p.title,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (active) TextHi else TextMid,
                            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal
                        )
                        Text(
                            if (nejde) "Nejde s referencemi" else p.detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (active) TextMid else TextLow
                        )
                    }
                }
                // lichý počet profilů by jinak roztáhl poslední dlaždici na dvojnásobek
                if (dvojice.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun GenerateScreen(vm: MainViewModel, busy: Boolean = false, modifier: Modifier = Modifier) {
    val params by vm.params.collectAsStateWithLifecycle()
    val advanced by vm.advancedOpen.collectAsStateWithLifecycle()

    // Každá karta se ověřuje ze SVÉ scény, ne z polí nahoře – scény proto musí
    // být mezi klíči, jinak by tlačítko Generovat zůstalo šedivé, dokud se
    // nezmění něco z parametrů (psaní do repliky samo o sobě nic nemění).
    val talkScene by vm.scene.collectAsStateWithLifecycle()
    val timelineScene by vm.timeline.collectAsStateWithLifecycle()
    val aioScene by vm.aio.collectAsStateWithLifecycle()
    // Chybějící scéna mezi klíči = zamrzlé tlačítko. Přesně to se stalo kartě
    // Úprava obrázku: výběr fotky validaci neprobudil a Generovat zůstalo šedé.
    val editScene by vm.edit.collectAsStateWithLifecycle()
    val upscaleScene by vm.upscale.collectAsStateWithLifecycle()
    val musicScene by vm.music.collectAsStateWithLifecycle()
    val restoreScene by vm.restore.collectAsStateWithLifecycle()
    val swapScene by vm.swap.collectAsStateWithLifecycle()
    // Dostupnost AIO balíku doráží asynchronně – bez ní v klíčích by hláška
    // „server nemá balík" zůstala viset i po úspěšné kontrole (a naopak).
    val aioAvailable by vm.aioAvailable.collectAsStateWithLifecycle()
    val problem = remember(
        params, talkScene, timelineScene, aioScene, editScene, upscaleScene, musicScene,
        restoreScene, swapScene, aioAvailable,
    ) {
        vm.validation(params)
    }

    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val hints = remember(
        params, talkScene, aioScene, editScene, upscaleScene, musicScene, restoreScene, swapScene,
        aioAvailable,
    ) {
        vm.hints(params)
    }
    val mode = params.mode

    // imePadding: appka jede edge-to-edge, takže adjustResize z manifestu nic
    // nedělá a klávesnice by připnuté tlačítko překryla. Takhle se tlačítko
    // při psaní zvedne těsně nad ni – zadání a Generovat jsou vidět naráz.
    Column(modifier.fillMaxSize().imePadding()) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
        // ------------------------------------------------------- stav počítače
        ServerBanner(
            status = vm.serverStatus.collectAsStateWithLifecycle().value,
            onRetry = { vm.refreshServerStatus() }
        )

        // ---------------------------------------------------------- karty
        ModeTabs(mode) { vm.setMode(it) }

        val referencniCesta = mode == Mode.TALK ||
            (mode == Mode.ALLINONE && aioScene.mode.usesRefWeights)

        // ------------------------------------------------- mluvící scéna
        // Vlastní svět: postavy, repliky a hlasy si drží stav zvlášť a nic
        // z toho se nemíchá s obrázky ostatních karet.
        if (mode == Mode.TALK) {
            TalkSceneSection(vm)
        }

        // ------------------------------------------------------- Časová osa
        if (mode == Mode.TIMELINE) {
            TimelineSceneSection(vm)
        }

        // ---------------------------------------------------- úprava obrázku
        if (mode == Mode.EDIT) {
            ImageEditSection(vm)
        }

        // ---------------------------------------------------------- zvětšit
        if (mode == Mode.UPSCALE) {
            UpscaleSection(vm)
        }

        // ---------------------------------------------------------- obrázek
        if (mode == Mode.IMAGE) {
            TxtImageSection(vm, params)
        }

        // ------------------------------------------------------------ hudba
        if (mode == Mode.MUSIC) {
            MusicSection(vm)
        }

        // ----------------------------------------------------- oprava fotky
        if (mode == Mode.RESTORE) {
            RestoreSection(vm)
        }

        // ---------------------------------------------------- výměna tváře
        if (mode == Mode.FACESWAP) {
            FaceSwapSection(vm)
        }

        // -------------------------------------------------------- All in One
        // Vlastní svět: režim, vstupy i délka patří scéně karty, protože se
        // řídí šablonou staženou ze serveru, ne hlavním workflow.
        if (mode == Mode.ALLINONE) {
            AllInOneSection(vm)
        }

        // ---------------------------------------------------------- délka
        // Posuvník má jen karta Dialogy. All in One má délku ve své kartě
        // (u prodloužení se počítá jinak) a u Časové osy délku určuje součet
        // segmentů – uzel s délkou se z grafu odstraňuje, posuvník by lhal.
        if (mode == Mode.TALK) SectionCard(
            title = t("Délka"),
            subtitle = t("Model počítá po blocích 17 snímků, proto se délka zaokrouhlí"),
            trailing = {
                Text(
                    "%.1f s".format(params.realSeconds),
                    style = MaterialTheme.typography.headlineSmall, color = Cyan
                )
            }
        ) {
            Column {
                Slider(
                    value = params.seconds.toFloat(),
                    onValueChange = { v -> vm.update { it.copy(seconds = v.toInt()) } },
                    valueRange = MIN_SECONDS.toFloat()..MAX_SECONDS.toFloat(),
                    steps = MAX_SECONDS - MIN_SECONDS - 1,
                    colors = sliderColors()
                )
                Row {
                    Text(
                        "${params.frames} snímků · 24 fps",
                        style = MaterialTheme.typography.bodySmall, color = TextLow,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        t("trénováno na 5–15 s"),
                        style = MaterialTheme.typography.bodySmall, color = TextLow
                    )
                }
            }
        }

        // Vsechno, co se nemeni pri kazdem behu, je sbalene do jedne sekce.
        // Hlavni obrazovka tak zustava: vstupy, zadani, Generovat.
        if (mode.isVideo) SkladaciSekce(
            title = t("Nastavení"),
            souhrn = params.profile.title + " · " + params.resolution.label +
                (if (mode == Mode.TIMELINE && params.spectrum) " · Spectrum" else ""),
            klic = "nastaveni-" + mode.name,
        ) {
            // ------------------------------------------------------- Turbo / Kvalita
            ProfilePicker(params.profile, referencniCesta) { vm.setProfile(it) }

            // Spectrum má vliv na zvuk, takže nepatří schované v pokročilém nastavení.
            // Karta All in One jede na cizí šabloně, ve které uzel Spectrum vůbec
            // není – přepínač by tam nedělal nic.
            if (mode == Mode.TIMELINE) Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Surface1)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Spectrum", style = MaterialTheme.typography.bodyMedium, color = TextHi)
                    Text(
                        if (params.spectrum)
                            t("Zrychluje generování, ale zvuk je jen přibližný")
                        else t("Vypnuté – věrnější zvuk, o něco pomalejší"),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (params.spectrum) Amber else TextLow
                    )
                }
                Switch(
                    checked = params.spectrum,
                    onCheckedChange = { v -> vm.update { it.copy(spectrum = v) } },
                    colors = switchColors()
                )
            }
            // ---------------------------------------------------------- rozlišení
            SectionCard(
                title = t("Rozlišení"),
                subtitle = t("Megapixely × poměr stran, zaokrouhleno na násobek 32"),
                trailing = {
                    Text(
                        params.resolution.label,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (params.aboveNative) Amber else Cyan
                    )
                }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column {
                        Text(t("Poměr stran"), style = MaterialTheme.typography.labelMedium, color = TextLow)
                        Spacer(Modifier.height(8.dp))
                        PillRow(
                            items = Aspect.entries.toList(),
                            selected = params.aspect,
                            label = { it.label },
                            onSelect = { v -> vm.update { it.copy(aspect = v) } }
                        )
                    }
                    Column {
                        Text("Velikost", style = MaterialTheme.typography.labelMedium, color = TextLow)
                        Spacer(Modifier.height(8.dp))
                        // Pilulky ukazují rovnou výsledná rozlišení pro zvolený poměr stran –
                        // megapixely z tabulky se přepočítávají automaticky, uživatel čísla
                        // z tabulky nemusí znát. Nativní plátno je mezi nimi vždycky,
                        // i když v pevné řadě nevychází (3:4, čtverec).
                        val nativni = params.nativeResolution
                        PillRow(
                            items = sizeStepsFor(params.aspect),
                            selected = params.megapixels,
                            label = { mp ->
                                val r = Resolution.of(params.aspect, mp)
                                if (r == nativni) "${r.label} • nativní" else r.label
                            },
                            onSelect = { v -> vm.update { it.copy(megapixels = v) } }
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            // Věta se skládá z jednoho kusu i s hodnotou (%s),
                            // aby v angličtině nevznikla půl česká věta.
                            when {
                                params.isNativeResolution ->
                                    t("Přesně plátno, na kterém model vznikl (%s). Odsud je výsledek nejjistější.")
                                        .format(nativni.label)
                                params.aboveNative ->
                                    t("O %d %% víc bodů než plátno modelu (%s). Jde to, ale bude to déle trvat a detaily bývají měkčí. Ostřejší HD spíš vyjde z nativu a karty All in One → Zvětšit.")
                                        .format(params.nativeOverhead, nativni.label)
                                params.resolution.pixels < nativni.pixels ->
                                    t("Pod plátnem modelu (%s) – rychlejší, ale měkčí obraz a méně přesné tváře.")
                                        .format(nativni.label)
                                else ->
                                    t("Prakticky plátno modelu (%s) – tenhle rozdíl na výsledku nepoznáš.")
                                        .format(nativni.label)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (params.aboveNative) Amber else TextLow
                        )
                    }
                }
            }

            // ------------------------------------------------------ model a LoRA
            // Model je nad LoRA schválně: nejdřív se vybírá, na čem se generuje,
            // teprve pak čím se to dolaďuje.
            ModelCard(vm, params)

            LoraCard(vm, params)

            val onWorkflowDefaults = remember(params) { vm.matchesWorkflow(params) }
            SectionCard(
                title = t("Pokročilé"),
                subtitle = if (advanced) null else
                    if (onWorkflowDefaults) "Nastaveno podle workflow"
                    else t("Změněno oproti workflow"),
                trailing = {
                    Icon(
                        Icons.Default.Tune, null,
                        Modifier
                            .size(22.dp)
                            .clickable { vm.toggleAdvanced() },
                        tint = if (advanced) Violet else TextMid
                    )
                }
            ) {
                Column {
                    if (!advanced) OutlineButton(t("Zobrazit pokročilé volby")) { vm.toggleAdvanced() }
                    AnimatedVisibility(
                        visible = advanced,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            if (!onWorkflowDefaults) {
                                OutlineButton(
                                    t("Vrátit hodnoty z workflow"),
                                    modifier = Modifier.fillMaxWidth(),
                                    color = Amber,
                                    onClick = { vm.resetToWorkflowDefaults() }
                                )
                            }
                            // Výběr modelu bývala schovaný právě tady a nikdo ho nenašel;
                            // od verze 2.61 je to samostatná karta nad LoRA.

                            // Věrnost referencí patří ke KAŽDÉ kartě, která jede
                            // referenční cestou – tedy i k All in One v režimu
                            // Reference, kde o podobu postav jde ze všech nejvíc.
                            if (mode == Mode.TALK ||
                                (mode == Mode.ALLINONE && aioScene.mode == AioMode.REFERENCE)
                            ) {
                                Column {
                                    Text(
                                        t("Věrnost referencí"),
                                        style = MaterialTheme.typography.labelMedium, color = TextLow
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    PillRow(
                                        items = listOf("match", "max"),
                                        selected = params.refImageSize,
                                        label = { if (it == "match") t("Vyvážené") else t("Maximální detail") },
                                        onSelect = { v -> vm.update { it.copy(refImageSize = v) } }
                                    )
                                }
                            }

                            LabeledSlider(
                                label = t("Počet kroků"), value = "${params.steps}",
                                position = params.steps.toFloat(), range = 4f..40f,
                                onChange = { v -> vm.update { it.copy(steps = v.toInt()) } },
                                note = t("Workflow používá 8 s Turbo LoRA.")
                            )

                            Dropdown("Sampler", SAMPLERS, params.sampler, { it }) { v ->
                                vm.update { it.copy(sampler = v) }
                            }
                            Dropdown(t("Plánovač (scheduler)"), SCHEDULERS, params.scheduler, { it }) { v ->
                                vm.update { it.copy(scheduler = v) }
                            }

                            LabeledSlider(
                                label = t("Sigma shift – obraz"),
                                value = "%.2f".format(params.shiftVideo),
                                position = params.shiftVideo, range = 1f..20f,
                                onChange = { v -> vm.update { it.copy(shiftVideo = v) } },
                                note = t("Hodnota z workflow je 12,19.")
                            )
                            LabeledSlider(
                                label = t("Sigma shift – zvuk"),
                                value = "%.1f".format(params.shiftAudio),
                                position = params.shiftAudio, range = 1f..10f,
                                onChange = { v -> vm.update { it.copy(shiftAudio = v) } },
                                note = "Hodnota z workflow je 3."
                            )
                            LabeledSlider(
                                label = t("Komprese videa (CRF)"), value = "${params.crf}",
                                position = params.crf.toFloat(), range = 10f..30f,
                                onChange = { v -> vm.update { it.copy(crf = v.toInt()) } },
                                note = t("Nižší číslo = lepší obraz a větší soubor. Workflow má 19.")
                            )

                            // Spectrum je uzel jen v lokálním ULTRA workflow Časové osy.
                            // All in One a Dialogy jedou na šablonách balíku, kde uzel
                            // není a hodnota se do grafu nedosazuje – vypínač by lhal.
                            if (mode == Mode.TIMELINE) ToggleRow(
                                "Spectrum", t("Totéž co vypínač nahoře – přibližné zrychlení"),
                                params.spectrum
                            ) { v -> vm.update { it.copy(spectrum = v) } }
                            ToggleRow(
                                "Sage Attention", t("Rychlejší pozornost, ve workflow zapnutá"),
                                params.sageAttention
                            ) { v -> vm.update { it.copy(sageAttention = v) } }
                            ToggleRow(
                                "TeaCache",
                                t("Přeskočí podobné kroky — až 3× rychlejší, drobně méně věrné"),
                                params.teaCache
                            ) { v -> vm.update { it.copy(teaCache = v) } }
                            ToggleRow(
                                t("Živý náhled"),
                                t("Rozpracované snímky během generování; vypnutí šetří grafiku"),
                                params.livePreview
                            ) { v -> vm.update { it.copy(livePreview = v) } }

                            Column {
                                ToggleRow(
                                    t("Náhodný seed"),
                                    t("Vypni, když chceš stejné zadání zopakovat beze změny"),
                                    params.randomSeed
                                ) { v -> vm.update { it.copy(randomSeed = v) } }
                                AnimatedVisibility(!params.randomSeed) {
                                    Column(Modifier.padding(top = 10.dp)) {
                                        DarkTextField(
                                            value = params.seed.toString(),
                                            onValueChange = { v ->
                                                val n = v.filter { c -> c.isDigit() }.take(18)
                                                vm.update { it.copy(seed = n.toLongOrNull() ?: 0L) }
                                            },
                                            placeholder = "seed",
                                            minHeight = 56.dp,
                                            singleLine = true,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // ---------------------------------------------------------- upozornění
        if (hints.isNotEmpty()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Amber.copy(alpha = .08f))
                    .border(1.dp, Amber.copy(alpha = .25f), RoundedCornerShape(16.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                hints.forEach { h ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Default.Info, null, Modifier.size(18.dp), Amber)
                        Text(h, style = MaterialTheme.typography.bodySmall, color = TextMid)
                    }
                }
            }
        }

            Spacer(Modifier.height(6.dp))
        }

        // Tlačítko je připnuté dole a nikdy neodjede z obrazovky – kvůli němu
        // se dřív muselo rolovat na konec celé stránky.
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
        // Za běhu tlačítko nezhasíná – další zadání se zařadí do fronty a
        // spustí se samo, jakmile aktuální běh skončí.
        val fronta by vm.queue.collectAsStateWithLifecycle()
        val blocked = problem != null
        GradientButton(
            text = when {
                problem != null -> problem
                busy -> t("Přidat do fronty") +
                    (if (fronta.isNotEmpty()) t(" (čeká %d)").format(fronta.size) else "")
                mode == Mode.EDIT -> t("Upravit obrázek")
                mode == Mode.UPSCALE -> t("Zvětšit obrázek")
                mode == Mode.IMAGE -> t("Vygenerovat obrázek")
                mode == Mode.MUSIC -> t("Vygenerovat skladbu")
                mode == Mode.RESTORE -> t("Opravit fotku")
                mode == Mode.FACESWAP -> t("Vyměnit tvář")
                else -> t("Vygenerovat video")
            },
            enabled = !blocked,
            icon = {
                if (!blocked) Icon(
                    if (busy) Icons.Default.PlaylistAdd else Icons.Default.AutoAwesome,
                    null, Modifier.size(20.dp), Color.White
                )
            },
            onClick = {
                // Klávesnice pryč hned při zmáčknutí – průběh se otevírá přes celou
                // obrazovku a schovaný pod klávesnicí by nebyl k ničemu.
                focus.clearFocus(force = true)
                keyboard?.hide()
                vm.start()
            }
        )
        if (busy) {
            Text(
                t("Generování běží. Klidně uprav zadání a přidej další běh do fronty."),
                style = MaterialTheme.typography.bodySmall, color = TextLow,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
        if (fronta.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Surface1)
                    .border(1.dp, Outline1, RoundedCornerShape(14.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    t("Ve frontě"),
                    style = MaterialTheme.typography.labelMedium, color = TextLow
                )
                fronta.forEachIndexed { i, run ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${i + 1}. ${run.title}" +
                                (if (run.prompt.isNotBlank()) " · ${run.prompt.take(38)}" else ""),
                            style = MaterialTheme.typography.bodySmall, color = TextMid,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            Icons.Default.Close, t("Odebrat z fronty"),
                            Modifier
                                .size(26.dp)
                                .clip(RoundedCornerShape(50))
                                .clickable { vm.removeFromQueue(run.id) }
                                .padding(5.dp),
                            TextLow
                        )
                    }
                }
            }
        }
        }
    }
}

/**
 * Karta Obrázek: zadání a poměr stran, nic dalšího. Z-Image Turbo jede
 * z předlohy 1:1 (8 kroků, cfg 1), takže tu není co ladit — o to je karta
 * předvídatelnější. Hotový obrázek jde z výsledku rovnou do Úpravy či Zvětšit.
 */
@Composable
private fun TxtImageSection(vm: MainViewModel, params: cz.promptlab.h3video.data.GenParams) {
    SectionCard(
        title = t("Nový obrázek"),
        subtitle = t("Z-Image Turbo — hotovo za pár sekund")
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            DarkTextField(
                value = params.prompt,
                onValueChange = { v -> vm.update { it.copy(prompt = v) } },
                placeholder = t("Popiš, co má na obrázku být — jednoduše a bez záporů"),
                minHeight = 120.dp,
            )
            Column {
                Text(t("Poměr stran"), style = MaterialTheme.typography.labelMedium, color = TextLow)
                Spacer(Modifier.height(8.dp))
                PillRow(
                    items = Aspect.entries.toList(),
                    selected = params.aspect,
                    label = { it.label },
                    onSelect = { v -> vm.update { it.copy(aspect = v) } }
                )
                // ✨ Vylepšovač: pár slov (klidně česky) → plný prompt psaný
                // podle pravidel Z-Image (souvislé věty, světlo, styl).
                val stavPrepisu by vm.rewriteState.collectAsStateWithLifecycle()
                val puvodni by vm.rewriteOriginal.collectAsStateWithLifecycle()
                val bezi = stavPrepisu is MainViewModel.RewriteState.Busy
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlineButton(
                        if (bezi) t("Přepisuji…") else t("✨ Vylepšit prompt"),
                        color = Cyan,
                    ) { if (!bezi) vm.vylepsiObrazovyPrompt() }
                    if (bezi) {
                        Spacer(Modifier.width(10.dp))
                        androidx.compose.material3.CircularProgressIndicator(
                            Modifier.size(18.dp), color = Cyan, strokeWidth = 2.dp
                        )
                    }
                    if (!bezi && puvodni != null) {
                        Spacer(Modifier.width(12.dp))
                        Text(
                            t("Vrátit původní"),
                            style = MaterialTheme.typography.bodySmall, color = TextMid,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { vm.vratPuvodniPromptObrazku() }
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                        )
                    }
                }
                (stavPrepisu as? MainViewModel.RewriteState.Fail)?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        it.message,
                        style = MaterialTheme.typography.bodySmall, color = Danger
                    )
                }

                Spacer(Modifier.height(6.dp))
                val (w, h) = cz.promptlab.h3video.comfy.ZImageBuilder.sizeFor(params.aspect)
                Text(
                    t("Vyjde %d×%d px. Z výsledku se dá rovnou pokračovat do Úpravy obrázku nebo do Zvětšit.")
                        .format(w, h),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextLow
                )
            }
            Column {
                Text(t("Model"), style = MaterialTheme.typography.labelMedium, color = TextLow)
                Spacer(Modifier.height(8.dp))
                val jeOdvazany =
                    params.zimageModel == cz.promptlab.h3video.comfy.ZImageBuilder.NSFW_MODEL_FILE
                PillRow(
                    items = listOf(t("Turbo (základ)"), t("Photoreal (odvázaný)")),
                    selected = if (jeOdvazany) t("Photoreal (odvázaný)") else t("Turbo (základ)"),
                    label = { it },
                    onSelect = { v ->
                        vm.update {
                            it.copy(
                                zimageModel = if (v.startsWith("Photoreal"))
                                    cz.promptlab.h3video.comfy.ZImageBuilder.NSFW_MODEL_FILE
                                else ""
                            )
                        }
                    }
                )
                if (jeOdvazany) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        t("NSFW Photorealistic v6.1 — nic neodmítá, jede na 12 kroků. ") +
                            t("LoRA níž s ním není potřeba."),
                        style = MaterialTheme.typography.bodySmall, color = TextLow
                    )
                }
            }
            // LoRA patří jen k základnímu Turbo — odvázaný finetune to má
            // „v sobě" a míchání by výsledek jen kazilo, tak se neukazuje.
            if (params.zimageModel.isBlank()) Column {
                ToggleRow(
                    t("Bez cenzury"),
                    t("Přimíchá odvázanou LoRA — model pak nic neodmítá"),
                    params.zimageNsfw
                ) { v ->
                    vm.update { it.copy(zimageNsfw = v) }
                    if (v) vm.refreshZimageLoras()
                }
                AnimatedVisibility(params.zimageNsfw) {
                    val loras by vm.zimageLoras.collectAsStateWithLifecycle()
                    LaunchedEffect(Unit) { vm.refreshZimageLoras() }
                    Column(Modifier.padding(top = 8.dp)) {
                        // Výběr LoRA — vše se „zimage/zit" v názvu na serveru.
                        // Nová stažená LoRA se tu objeví sama.
                        if (loras.size > 1) {
                            Text(
                                t("Která LoRA"),
                                style = MaterialTheme.typography.labelMedium, color = TextLow
                            )
                            Spacer(Modifier.height(6.dp))
                            loras.forEach { lora ->
                                val vybrana = params.zimageNsfwLora == lora
                                Text(
                                    lora.removeSuffix(".safetensors").removePrefix("zimage_"),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (vybrana) Cyan else TextMid,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            if (vybrana) Cyan.copy(alpha = .12f)
                                            else Color.Transparent
                                        )
                                        .clickable {
                                            vm.update { it.copy(zimageNsfwLora = lora) }
                                        }
                                        .padding(horizontal = 10.dp, vertical = 7.dp)
                                )
                            }
                            zimageTriggerHint(params.zimageNsfwLora)?.let { hint ->
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    hint,
                                    style = MaterialTheme.typography.bodySmall, color = Amber
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                t("Síla"),
                                style = MaterialTheme.typography.labelMedium, color = TextLow
                            )
                            Slider(
                                value = params.zimageNsfwSila,
                                onValueChange = { v ->
                                    vm.update { it.copy(zimageNsfwSila = (v * 20).roundToInt() / 20f) }
                                },
                                valueRange = 0.5f..1.2f,
                                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                                colors = sliderColors()
                            )
                            Text(
                                "%.2f".format(params.zimageNsfwSila),
                                style = MaterialTheme.typography.labelMedium, color = TextMid
                            )
                        }
                        Text(
                            t("1.00 = jak byla trénovaná; kolem 0.75 jemnější výsledky."),
                            style = MaterialTheme.typography.bodySmall, color = TextLow
                        )
                    }
                }
            }
        }
    }
}

/**
 * LoRA pro model. Turbo se dá vypnout i vyměnit (od 2.12), další se přidávají
 * a vypínají – stejně jako v Power Lora Loaderu v ComfyUI.
 */
@Composable
private fun LoraCard(vm: MainViewModel, params: cz.promptlab.h3video.data.GenParams) {
    val available by vm.availableLoras.collectAsStateWithLifecycle()
    val loraError by vm.loraError.collectAsStateWithLifecycle()
    var picking by remember { mutableStateOf(false) }
    var showAll by remember { mutableStateOf(false) }
    var swapping by remember { mutableStateOf(false) }

    val active = params.extraLoras.count { it.enabled }
    SectionCard(
        title = "LoRA",
        subtitle = when {
            !params.turboLoraOn && active == 0 -> t("Žádná – model jede na plno")
            !params.turboLoraOn -> "$active bez Turba"
            active == 0 -> "Turbo"
            else -> "Turbo + $active další"
        },
    ) {
        Column {
            // Turbo – vypínatelná i vyměnitelná
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Turbo LoRA", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        if (params.turboLoraOn)
                            TURBO.profileFor(params.turboLora)?.label
                                ?: params.turboLora.removeSuffix(".safetensors")
                        else t("Vypnutá – plný model, lepší hlas, ale pomalejší"),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (params.turboLoraOn) TextLow else Amber,
                        maxLines = 2
                    )
                }
                Switch(
                    checked = params.turboLoraOn,
                    onCheckedChange = { vm.setTurboLoraOn(it) },
                    colors = switchColors()
                )
            }
            AnimatedVisibility(params.turboLoraOn) {
                Column {
                    Slider(
                        value = params.turboLoraStrength,
                        onValueChange = { vm.setTurboLoraStrength(it) },
                        valueRange = 0f..2f,
                        colors = sliderColors()
                    )
                    Text(
                        "Síla %.2f".format(java.util.Locale.US, params.turboLoraStrength),
                        style = MaterialTheme.typography.bodySmall, color = TextLow
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (swapping) t("Zrušit výměnu") else t("Vyměnit Turbo LoRA"),
                        style = MaterialTheme.typography.bodySmall,
                        color = Cyan,
                        modifier = Modifier
                            .clickable { swapping = !swapping; if (swapping) vm.loadLoras() }
                            .padding(vertical = 6.dp)
                    )
                    AnimatedVisibility(swapping) {
                        Column {
                            // Nejdřív ty, o kterých víme, na jaký shift jsou trénované –
                            // u nich se shift i kroky dosadí samy.
                            TURBO.KNOWN.forEach { known ->
                                val onServer = available.isEmpty() || available.contains(known.file)
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable(enabled = onServer) {
                                            vm.setTurboLora(known.file); swapping = false
                                        }
                                        .padding(vertical = 9.dp, horizontal = 8.dp)
                                ) {
                                    Column {
                                        Text(
                                            known.label,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (onServer) TextHi else TextLow
                                        )
                                        Text(
                                            if (onServer)
                                                "${known.steps} kroků · shift %.2f".format(
                                                    java.util.Locale.US, known.shiftVideo
                                                )
                                            else t("Na serveru není – nejdřív ji stáhni do models/loras"),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (onServer) TextLow else Amber
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            params.extraLoras.forEach { l ->
                Spacer(Modifier.height(14.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                l.name.removeSuffix(".safetensors"),
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 2
                            )
                            Text(
                                if (l.enabled) "Síla %.2f".format(java.util.Locale.US, l.strength)
                                else "Vypnuto",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (l.enabled) TextLow else Amber
                            )
                        }
                        Switch(
                            checked = l.enabled,
                            onCheckedChange = { vm.setLoraEnabled(l.name, it) },
                            colors = switchColors()
                        )
                        Spacer(Modifier.size(4.dp))
                        Box(
                            Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(50))
                                .clickable { vm.removeLora(l.name) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Close, "Odebrat", Modifier.size(16.dp), TextLow)
                        }
                    }
                    AnimatedVisibility(l.enabled) {
                        Slider(
                            value = l.strength,
                            onValueChange = { vm.setLoraStrength(l.name, it) },
                            valueRange = 0f..2f,
                            colors = sliderColors()
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            OutlineButton(
                t("Přidat LoRA"),
                modifier = Modifier.fillMaxWidth(),
                color = Cyan,
            ) { picking = true; vm.loadLoras() }

            if (picking) {
                Spacer(Modifier.height(10.dp))
                when {
                    loraError != null -> Text(
                        loraError!!, style = MaterialTheme.typography.bodySmall, color = Amber
                    )
                    available.isEmpty() -> Text(
                        t("Načítám seznam ze serveru…"),
                        style = MaterialTheme.typography.bodySmall, color = TextLow
                    )
                    else -> {
                        // Ve složce jsou i LoRA pro jiné modely; ty by na H3 nesedly,
                        // proto se ve výchozím stavu nabízejí jen minimax/h3. „h3" se
                        // hledá jako samostatný kus názvu – jinak by prošlo i „epoch35".
                        val h3 = Regex("(^|[^a-z0-9])h3([^a-z0-9]|$)", RegexOption.IGNORE_CASE)
                        val filtered = available.filter {
                            showAll || it.contains("minimax", true) || h3.containsMatchIn(it)
                        }.filterNot { it == params.turboLora }
                            .filterNot { n -> params.extraLoras.any { it.name == n } }
                        Column {
                            filtered.take(30).forEach { n ->
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { vm.addLora(n); picking = false }
                                        .padding(vertical = 10.dp, horizontal = 8.dp)
                                ) {
                                    Text(
                                        n.removeSuffix(".safetensors"),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextHi, maxLines = 2
                                    )
                                }
                            }
                            if (filtered.isEmpty()) {
                                Text(
                                    t("Nic dalšího pro H3 na serveru není."),
                                    style = MaterialTheme.typography.bodySmall, color = TextLow
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Row {
                                Text(
                                    if (showAll) "Jen pro H3" else t("Zobrazit všechny LoRA"),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Cyan,
                                    modifier = Modifier
                                        .clickable { showAll = !showAll }
                                        .padding(6.dp)
                                )
                                Spacer(Modifier.weight(1f))
                                Text(
                                    t("Zrušit"),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextLow,
                                    modifier = Modifier
                                        .clickable { picking = false }
                                        .padding(6.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModeTabs(selected: Mode, onSelect: (Mode) -> Unit) {
    // Karty se NEDĚLÍ o šířku obrazovky. Při pěti režimech vycházelo na jednu
    // ~66 dp, do kterých se „Reference" ani „Mluvení" nevejde – text se lámal
    // a karty vypadaly slepené. Pás se proto roluje a každá karta je široká
    // podle svého názvu, jak je to v mobilních aplikacích zvykem; vybraná se
    // sama posune do zorného pole, aby po přepnutí nezůstala za okrajem.
    val stav = rememberLazyListState()
    val vybranyIndex = Mode.entries.indexOf(selected)
    LaunchedEffect(vybranyIndex) {
        stav.animateScrollToItem(vybranyIndex.coerceAtLeast(0))
    }

    Column {
        // Že se pás dá posouvat, musí být vidět na první pohled: na kraji, kde
        // ještě něco je, se obsah vytrácí do pozadí a svítí tam šipka. Jakmile
        // uživatel dojede na konec, náznak zmizí — je to tedy i ukazatel polohy.
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Surface1)
                .border(1.dp, Outline1, RoundedCornerShape(16.dp))
        ) {
        LazyRow(
            state = stav,
            modifier = Modifier.fillMaxWidth(),
            // Postranní mezera je přesně tak široká jako náznak s šipkou —
            // karta v klidu nikdy neleží pod ní, jen jí při rolování projede.
            contentPadding = PaddingValues(horizontal = 30.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(Mode.entries.size) { i ->
                val m = Mode.entries[i]
                val active = m == selected
                Box(
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .then(
                            if (active) Modifier.background(
                                Brush.linearGradient(listOf(Violet, Cyan))
                            ) else Modifier
                        )
                        .clickable { onSelect(m) }
                        .padding(horizontal = 16.dp, vertical = 11.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        m.short,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (active) Color.White else TextMid,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1,
                    )
                }
            }
        }

            // Levý a pravý náznak. Zobrazují se jen tím směrem, kam se dá jet.
            androidx.compose.animation.AnimatedVisibility(
                visible = stav.canScrollBackward,
                enter = androidx.compose.animation.fadeIn(),
                exit = androidx.compose.animation.fadeOut(),
                modifier = Modifier.align(Alignment.CenterStart),
            ) {
                OkrajPasu(doleva = true)
            }
            androidx.compose.animation.AnimatedVisibility(
                visible = stav.canScrollForward,
                enter = androidx.compose.animation.fadeIn(),
                exit = androidx.compose.animation.fadeOut(),
                modifier = Modifier.align(Alignment.CenterEnd),
            ) {
                OkrajPasu(doleva = false)
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                selected.title + " — " + selected.detail,
                style = MaterialTheme.typography.bodySmall, color = TextLow,
                modifier = Modifier.weight(1f),
            )
            // Kolikátá karta z kolika — druhý (a nepřehlédnutelný) signál, že
            // jich je víc, než je zrovna vidět.
            Text(
                "${vybranyIndex + 1}/${Mode.entries.size}",
                style = MaterialTheme.typography.labelMedium, color = TextMid,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

/** Vytrácející se okraj se šipkou — „tímhle směrem jsou další karty". */
@Composable
private fun OkrajPasu(doleva: Boolean) {
    // Plná barva na kraji, aby projíždějící karta opravdu zmizela, a delší
    // přechod do průhledna, ať to nevypadá jako useknuté.
    val barvy = listOf(Surface1, Surface1, Surface1.copy(alpha = 0f))
    Box(
        Modifier
            .width(30.dp)
            .height(48.dp)
            .background(
                if (doleva) Brush.horizontalGradient(barvy)
                else Brush.horizontalGradient(barvy.reversed())
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (doleva) Icons.Default.ChevronLeft else Icons.Default.ChevronRight,
            contentDescription = if (doleva) "Další karty vlevo" else "Další karty vpravo",
            modifier = Modifier.size(20.dp),
            tint = Cyan,
        )
    }
}

@Composable
fun LabeledSlider(
    label: String,
    value: String,
    position: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
    note: String? = null,
) {
    Column {
        Row {
            Text(
                label, style = MaterialTheme.typography.labelMedium,
                color = TextLow, modifier = Modifier.weight(1f)
            )
            Text(value, style = MaterialTheme.typography.labelMedium, color = Cyan)
        }
        Slider(
            value = position.coerceIn(range.start, range.endInclusive),
            onValueChange = onChange,
            valueRange = range,
            colors = sliderColors()
        )
        if (note != null) {
            Text(note, style = MaterialTheme.typography.bodySmall, color = TextLow)
        }
    }
}

@Composable
private fun ToggleRow(title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = TextLow)
        }
        Switch(checked = checked, onCheckedChange = onChange, colors = switchColors())
    }
}

@Composable
fun switchColors() = SwitchDefaults.colors(
    checkedThumbColor = Color.White,
    checkedTrackColor = Violet,
    uncheckedTrackColor = Surface2,
    uncheckedBorderColor = Outline1,
)

@Composable
fun sliderColors() = SliderDefaults.colors(
    thumbColor = Cyan,
    activeTrackColor = Violet,
    inactiveTrackColor = Surface2,
    activeTickColor = Color.Transparent,
    inactiveTickColor = Color.Transparent,
)

/**
 * Spouštěcí slova stažených LoRA — bez nich některé skoro nic nedělají.
 * Zdroj: trainedWords z CivitAI při stažení (2. 9. 2026).
 */
private fun zimageTriggerHint(lora: String): String? = when (lora) {
    "zimage_acts_pack.safetensors" ->
        t("Chce v promptu polohu/akt (missionary, cowgirl…) — je na ně trénovaná.")
    "zimage_lenovo_ultrareal.safetensors" ->
        t("Přidej do promptu spouštěcí slovo: l3n0v0")
    "zimage_skin_texture.safetensors" ->
        t("Přidej do promptu: photorealistic, detailed skin, fine texture")
    else -> null
}

/**
 * Značka do promptu. `active` = už je v textu, takže je na první pohled vidět,
 * na kterou postavu záběr odkazuje a na kterou ne.
 */
@Composable
fun TagChip(text: String, active: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (active) Ok.copy(alpha = .18f) else Violet.copy(alpha = .16f))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = if (active) Ok else Cyan
        )
    }
}

/**
 * Vyfotí snímek systémovým fotoaparátem a vrátí adresu hotové fotky.
 *
 * Adresa se pamatuje přes [rememberSaveable], protože fotoaparát je cizí aplikace –
 * Android smí naši mezitím zabít a po návratu ji postavit znovu. Bez toho by se
 * fotka po zabití appky ztratila, i když by se povedla.
 */
@Composable
fun rememberCameraShot(onPhoto: (Uri) -> Unit): () -> Unit {
    val ctx = LocalContext.current
    var pending by rememberSaveable { mutableStateOf<String?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        val uri = pending?.let(Uri::parse)
        pending = null
        // false = uživatel focení zrušil; pak se nic nemění a zůstane původní snímek
        if (taken && uri != null) onPhoto(uri)
    }
    return {
        val uri = CameraCapture.newPhotoUri(ctx)
        if (uri == null) {
            Toast.makeText(ctx, t("Nepodařilo se připravit soubor pro fotku"), Toast.LENGTH_SHORT).show()
        } else {
            pending = uri.toString()
            // Na telefonu bez fotoaparátu (nebo s vypnutou appkou fotoaparátu)
            // by launch spadl na ActivityNotFoundException.
            runCatching { camera.launch(uri) }.onFailure {
                pending = null
                Toast.makeText(ctx, t("V telefonu není žádná aplikace fotoaparátu"), Toast.LENGTH_SHORT).show()
            }
        }
    }
}

@Composable
fun DarkTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    minHeight: androidx.compose.ui.unit.Dp,
    singleLine: Boolean = false,
    /** Když je předané, ukáže se vpravo křížek na vymazání – ale jen když je co mazat. */
    onClear: (() -> Unit)? = null,
) {
    val focus = LocalFocusManager.current
    // Poloha pole pro hlídač klávesnice (viz ZaostrenePole v Components.kt).
    var maFokus by remember { mutableStateOf(false) }
    var souradnice by remember {
        mutableStateOf<androidx.compose.ui.layout.LayoutCoordinates?>(null)
    }
    CompositionLocalProvider(
        LocalTextSelectionColors provides TextSelectionColors(Cyan, Cyan.copy(alpha = .35f))
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .height(minHeight)
                .onGloballyPositioned {
                    souradnice = it
                    if (maFokus) ZaostrenePole.bounds = it.boundsInWindow()
                }
                .onFocusChanged { stav ->
                    maFokus = stav.isFocused
                    ZaostrenePole.bounds =
                        if (stav.isFocused) souradnice?.boundsInWindow() else null
                },
            placeholder = {
                Text(placeholder, style = MaterialTheme.typography.bodyMedium, color = TextLow)
            },
            trailingIcon = if (onClear != null && value.isNotEmpty()) {
                {
                    IconButton(onClick = onClear, modifier = Modifier.size(44.dp)) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Vymazat text",
                            modifier = Modifier.size(20.dp),
                            tint = TextMid
                        )
                    }
                }
            } else null,
            singleLine = singleLine,
            // Jednořádkové pole (adresa serveru, token, název LoRA) se potvrzuje
            // klávesou „hotovo"; víceřádkový prompt si Enter nechává na nový řádek.
            keyboardOptions = if (singleLine) {
                KeyboardOptions(imeAction = ImeAction.Done)
            } else {
                KeyboardOptions.Default
            },
            keyboardActions = KeyboardActions(onDone = { focus.clearFocus(force = true) }),
            textStyle = MaterialTheme.typography.bodyMedium,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = TextHi,
                unfocusedTextColor = TextHi,
                focusedBorderColor = Violet,
                unfocusedBorderColor = Outline1,
                focusedContainerColor = Surface2,
                unfocusedContainerColor = Surface2,
                cursorColor = Cyan,
            )
        )
    }
}

/**
 * Karta výběru modelu. Stojí nad LoRA, protože se nejdřív vybírá, na čem se
 * generuje, a teprve pak čím se to dolaďuje.
 */
@Composable
private fun ModelCard(vm: MainViewModel, params: cz.promptlab.h3video.data.GenParams) {
    // Referenční cesta má vlastní váhy (ref2va) a ty se z workflow neberou pryč –
    // je poctivější to říct rovnou, než nabízet výběr, který na téhle kartě nic neudělá.
    val referencni = params.mode.usesRefModel
    SectionCard(
        title = t("Model"),
        subtitle = when {
            referencni -> t("Tahle karta jede na referenčním modelu z workflow")
            params.unetFl2va.isBlank() -> t("Z workflow (výchozí)")
            else -> params.unetFl2va
        }
    ) {
        UnetPicker(vm, params, referencni)
    }
}

/**
 * Výběr modelu (FL2VA). Prázdná volba = model z workflow, tedy stav, na kterém
 * je všechno vyladěné; vlastní model je vědomý krok stranou.
 */
@Composable
private fun UnetPicker(
    vm: MainViewModel,
    params: cz.promptlab.h3video.data.GenParams,
    referencni: Boolean = false,
) {
    val modely by vm.availableUnets.collectAsStateWithLifecycle()
    val unetError by vm.unetError.collectAsStateWithLifecycle()
    var otevreno by remember { mutableStateOf(false) }
    var vsechny by remember { mutableStateOf(false) }

    // Seznam ze serveru je celá složka diffusion_models – bývá v ní i to, co
    // s MiniMaxem nesouvisí. Napřed tedy jen H3, zbytek na vyžádání.
    val h3 = modely.filter { it.contains("h3", ignoreCase = true) }
    val ostatni = modely - h3.toSet()
    val nabidka = if (vsechny) h3 + ostatni else h3

    // Seznam se natáhne hned, ať je na tlačítku vidět, co je vybrané.
    LaunchedEffect(Unit) { vm.loadUnets() }

    Column {
        OutlineButton(
            text = params.unetFl2va.ifBlank { t("Z workflow (výchozí)") },
            modifier = Modifier.fillMaxWidth(),
            onClick = { otevreno = !otevreno; if (otevreno) vm.loadUnets() }
        )
        if (otevreno) {
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                PickRow(t("Z workflow (výchozí)"), params.unetFl2va.isBlank()) {
                    vm.setUnet(""); otevreno = false
                }
                nabidka.forEach { jmeno ->
                    PickRow(jmeno, params.unetFl2va == jmeno) {
                        vm.setUnet(jmeno); otevreno = false
                    }
                }
                when {
                    // Chyba se ukazuje stejně jako u LoRA – jinak by prázdný
                    // seznam vypadal jako věčné „načítám" a nikdo by nepoznal,
                    // že server vůbec neodpověděl.
                    unetError != null -> Text(
                        unetError!!, style = MaterialTheme.typography.bodySmall, color = Amber
                    )
                    modely.isEmpty() -> Text(
                        t("Seznam se načítá ze serveru…"),
                        style = MaterialTheme.typography.bodySmall, color = TextLow
                    )
                    !vsechny && ostatni.isNotEmpty() -> OutlineButton(
                        "Zobrazit i ostatní modely (${ostatni.size})",
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { vsechny = true }
                    )
                }
            }
        }
        if (referencni) {
            Spacer(Modifier.height(8.dp))
            Text(
                t("Na téhle kartě se generuje referenčními vahami z workflow. Výběr ") +
                    t("výš se projeví na kartách Text → video, Obrázek → video a v All in One."),
                style = MaterialTheme.typography.bodySmall, color = TextLow
            )
        } else if (params.unetFl2va.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                t("Vlastní model se týká textu, snímků a All in One. Reference, Mluvící ") +
                    t("scéna a Režisér s referencemi jedou dál na modelu z workflow."),
                style = MaterialTheme.typography.bodySmall, color = Amber
            )
        }
    }
}

@Composable
private fun PickRow(text: String, vybrano: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (vybrano) Violet.copy(alpha = .16f) else Surface2)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = if (vybrano) Cyan else TextMid,
            maxLines = 2,
        )
    }
}
