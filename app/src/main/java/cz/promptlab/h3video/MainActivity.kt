package cz.promptlab.h3video

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cz.promptlab.h3video.data.VideoItem
import cz.promptlab.h3video.engine.GenState
import cz.promptlab.h3video.engine.GenerationEngine
import cz.promptlab.h3video.engine.kind
import cz.promptlab.h3video.engine.stageText
import cz.promptlab.h3video.ui.FailureScreen
import cz.promptlab.h3video.ui.GenerateScreen
import cz.promptlab.h3video.ui.TimelineLandscapeScreen
import cz.promptlab.h3video.ui.HistoryScreen
import cz.promptlab.h3video.ui.ProgressScreen
import cz.promptlab.h3video.ui.ResultScreen
import cz.promptlab.h3video.ui.SettingsScreen
import cz.promptlab.h3video.ui.UpdateDialog
import cz.promptlab.h3video.comfy.Stage
import cz.promptlab.h3video.engine.GenerationService
import cz.promptlab.h3video.ui.theme.AccentBrush
import cz.promptlab.h3video.ui.theme.Cyan
import cz.promptlab.h3video.ui.theme.H3Theme
import cz.promptlab.h3video.ui.theme.Ink
import cz.promptlab.h3video.ui.theme.Outline1
import cz.promptlab.h3video.ui.theme.Surface1
import cz.promptlab.h3video.ui.theme.TextHi
import cz.promptlab.h3video.ui.theme.TextLow
import cz.promptlab.h3video.ui.theme.TextMid
import cz.promptlab.h3video.ui.theme.Violet

class MainActivity : ComponentActivity() {

    private val askNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Nic z přípravy okna nesmí shodit start – obrazovka je důležitější než
        // hezké okraje i než dotaz na oprávnění.
        runCatching { enableEdgeToEdge() }
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        setContent { H3Theme { Root() } }
    }
}

@Composable
private fun Root(vm: MainViewModel = viewModel()) {
    // Veřejné sestavení nemá zapečenou adresu serveru – dokud ji uživatel
    // nezadá, nemá zbytek appky co dělat (je to jen klient ComfyUI).
    val serverConfigured by vm.serverConfigured.collectAsStateWithLifecycle()
    if (!serverConfigured) {
        cz.promptlab.h3video.ui.OnboardingScreen(vm)
        return
    }

    val tab by vm.tab.collectAsStateWithLifecycle()
    val state by GenerationEngine.state.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val historyBytes by vm.historyBytes.collectAsStateWithLifecycle()
    val updateState by vm.update.collectAsStateWithLifecycle()
    var opened by remember { mutableStateOf<VideoItem?>(null) }

    // Průběh se dá sbalit, aby šlo mezitím listovat galerií nebo měnit nastavení.
    val running = state as? GenState.Running
    var progressExpanded by remember { mutableStateOf(true) }
    LaunchedEffect(running?.startedAt) { if (running != null) progressExpanded = true }

    // po dokončení se obnoví seznam v galerii
    LaunchedEffect(state) {
        if (state is GenState.Done) vm.refreshHistory()
    }

    // Klávesnice nesmí zůstat viset přes průběh ani přes hotové video. Vrstvy se
    // otevírají samy (i z notifikace), takže se zavírá tady, ne u tlačítka –
    // jinak by ji uživatel musel po každém generování odklikávat ručně.
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val overlay = running != null || state is GenState.Done ||
        state is GenState.Failed || opened != null
    LaunchedEffect(overlay, tab) {
        if (overlay || tab != Tab.CREATE) {
            focus.clearFocus(force = true)
            keyboard?.hide()
        }
    }

    // Zpět má zavírat vrstvy a vracet na Tvořit, ne rovnou ukončit aplikaci.
    val backUsable = opened != null || state is GenState.Done || state is GenState.Failed ||
        (running != null && progressExpanded) || tab != Tab.CREATE
    BackHandler(enabled = backUsable) {
        when {
            opened != null -> opened = null
            state is GenState.Done || state is GenState.Failed -> GenerationEngine.dismissResult()
            running != null && progressExpanded -> progressExpanded = false
            else -> vm.selectTab(Tab.CREATE)
        }
    }

    // Nabídka aktualizace vyskočí sama po spuštění, jakmile kontrola najde novější
    // vydání. „Později" zavře jen okno – proužek nad obsahem zůstane, aby se dalo
    // vrátit k tomu kdykoli potom.
    var updateDialogClosed by rememberSaveable { mutableStateOf(false) }
    val updateOffer = updateState.takeIf {
        it is UpdateState.Available || it is UpdateState.Downloading ||
            it is UpdateState.Ready
    }
    if (updateOffer != null && !updateDialogClosed && running == null) {
        UpdateDialog(
            state = updateOffer,
            onDownload = { (updateOffer as? UpdateState.Available)?.let { vm.downloadUpdate(it.info) } },
            onLater = { updateDialogClosed = true },
        )
    }

    // Telefon na šířku = editor časové osy přes celou obrazovku. Na střihovou
    // práci je potřeba šířka a všechno po ruce naráz (pás, ovládání, Generovat),
    // takže se tu neukazuje běžná rolovací obrazovka. Otočením zpět se appka
    // vrátí přesně tam, kde byla – osa se ukládá průběžně.
    val naSirku = LocalConfiguration.current.orientation ==
        android.content.res.Configuration.ORIENTATION_LANDSCAPE
    LaunchedEffect(naSirku) {
        if (naSirku) {
            vm.setMode(cz.promptlab.h3video.data.Mode.TIMELINE)
            vm.selectTab(Tab.CREATE)
        }
    }
    if (naSirku && !overlay) {
        TimelineLandscapeScreen(vm, busy = running != null)
        return
    }

    // Hlídač klávesnice: KAŽDÝ stisk mimo zaostřené textové pole ji schová.
    // Běží v Initial fázi, takže funguje i na tlačítkách a rozbalovacích
    // sekcích, které by si klepnutí jinak spotřebovaly (a klávesnice visela).
    var korenoveSouradnice by remember {
        mutableStateOf<androidx.compose.ui.layout.LayoutCoordinates?>(null)
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Ink)
            .onGloballyPositioned { korenoveSouradnice = it }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val udalost = awaitPointerEvent(PointerEventPass.Initial)
                        if (udalost.type == PointerEventType.Press) {
                            val mistni = udalost.changes.firstOrNull()?.position
                            val vOkne = mistni?.let { p ->
                                korenoveSouradnice?.localToWindow(p)
                            }
                            val pole = cz.promptlab.h3video.ui.ZaostrenePole.bounds
                            if (vOkne == null || pole == null || !pole.contains(vOkne)) {
                                focus.clearFocus(force = true)
                                keyboard?.hide()
                            }
                        }
                    }
                }
            }
    ) {
        Column(Modifier.fillMaxSize()) {
            Header(tab, vm.versionName)
            // Nabídka aktualizace musí být vidět rovnou, ne až když někdo zabloudí
            // do nastavení – jinak by to žádná automatická aktualizace nebyla.
            (updateState as? UpdateState.Available)?.let { av ->
                if (tab != Tab.SETTINGS) {
                    UpdateBanner(av.info.versionName) { vm.selectTab(Tab.SETTINGS) }
                }
            }
            Box(Modifier.weight(1f)) {
                when (tab) {
                    // Klepnutí mimo textové pole klávesnici zavře – běžné chování,
                    // které se od appky čeká. Posouvání zůstává beze změny, tap se
                    // pozná až podle toho, že prst nikam neujel.
                    // Rolování si řídí GenerateScreen sám – tlačítko Generovat je
                    // připnuté pod scrollovanou částí a nesmí s ní odjíždět.
                    Tab.CREATE -> Column(
                        Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTapGestures(onTap = { focus.clearFocus(force = true) })
                            }
                    ) { GenerateScreen(vm, busy = running != null) }

                    Tab.GALLERY -> HistoryScreen(
                        items = history,
                        totalBytes = historyBytes,
                        onOpen = { opened = it },
                        onDelete = { vm.delete(it) }
                    )

                    Tab.SETTINGS -> SettingsScreen(vm)
                }
            }
            // Při otevřené klávesnici se spodní lišta schová – jinak tlačítko
            // Generovat (imePadding počítá od spodku OBRAZOVKY) vyskočí o výšku
            // lišty nad klávesnici a mezi nimi zůstane prázdný pruh.
            val imeOtevrena = WindowInsets.ime.getBottom(LocalDensity.current) > 0
            if (running != null && !progressExpanded && !imeOtevrena) {
                MiniProgress(running) { progressExpanded = true }
            }
            if (!imeOtevrena) BottomBar(
                tab = tab,
                updateWaiting = updateState is UpdateState.Available,
                onSelect = { vm.selectTab(it) }
            )
        }

        // ---- vrstvy přes celou obrazovku
        AnimatedVisibility(
            visible = running != null && progressExpanded,
            enter = fadeIn() + slideInVertically { it / 6 },
            exit = fadeOut() + slideOutVertically { it / 6 },
        ) {
            running?.let { s ->
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Ink)
                        .statusBarsPadding()
                        .navigationBarsPadding()
                ) {
                    ProgressScreen(
                        state = s,
                        onMinimize = { progressExpanded = false },
                        onCancel = { GenerationEngine.cancel() }
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = state is GenState.Done,
            enter = fadeIn(), exit = fadeOut()
        ) {
            (state as? GenState.Done)?.let { s ->
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Ink)
                        .statusBarsPadding()
                        .navigationBarsPadding()
                ) {
                    ResultScreen(
                        item = s.item,
                        warnings = s.warnings,
                        onClose = { GenerationEngine.dismissResult() },
                        onAgain = {
                            GenerationEngine.dismissResult()
                            vm.selectTab(Tab.CREATE)
                        },
                        onSaved = { vm.markSaved(s.item) },
                        onUpscale = {
                            GenerationEngine.dismissResult()
                            vm.posliDoZvetseni(s.item)
                        },
                        onEdit = {
                            GenerationEngine.dismissResult()
                            vm.posliDoUpravy(s.item)
                        },
                        onAnimate = {
                            GenerationEngine.dismissResult()
                            vm.posliDoRozhybani(s.item)
                        },
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = state is GenState.Failed,
            enter = fadeIn(), exit = fadeOut()
        ) {
            (state as? GenState.Failed)?.let { s ->
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Ink)
                        .statusBarsPadding()
                        .navigationBarsPadding()
                ) {
                    FailureScreen(
                        message = s.message,
                        canRetryDownload = s.canRetryDownload,
                        onRetryDownload = { GenerationEngine.retryDownload() },
                        onClose = { GenerationEngine.dismissResult() }
                    )
                }
            }
        }

        val open = opened
        if (open != null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Ink)
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                ResultScreen(
                    item = open,
                    onClose = { opened = null },
                    onAgain = { opened = null; vm.selectTab(Tab.CREATE) },
                    onSaved = { vm.markSaved(open) },
                    onUpscale = {
                        opened = null
                        vm.posliDoZvetseni(open)
                    },
                    onEdit = {
                        opened = null
                        vm.posliDoUpravy(open)
                    },
                    onAnimate = {
                        opened = null
                        vm.posliDoRozhybani(open)
                    },
                )
            }
        }
    }
}

@Composable
private fun Header(tab: Tab, version: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(10.dp)
                    .clip(RoundedCornerShape(50))
                    .background(AccentBrush)
            )
            Spacer(Modifier.size(9.dp))
            // Jméno vlastní, ne jméno cizího modelu — „MiniMax" je ochranná
            // známka a patří jen do popisů karet (nominativní užití).
            Text(
                "PocketComfy",
                style = MaterialTheme.typography.titleMedium,
                color = TextHi,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.size(8.dp))
            Text(
                when (tab) {
                    Tab.CREATE -> "ULTRA workflow"
                    Tab.GALLERY -> "galerie"
                    Tab.SETTINGS -> "nastavení"
                },
                style = MaterialTheme.typography.bodySmall,
                color = TextLow,
                fontSize = 12.sp,
                modifier = Modifier.weight(1f)
            )
            // číslo verze má být vidět na první pohled, ne až v nastavení
            Text(
                "v$version",
                style = MaterialTheme.typography.bodySmall,
                color = TextLow,
                fontSize = 12.sp
            )
        }
    }
}

/**
 * Sbalený průběh – vzor „mini přehrávač". Podle Material se sbalený stav dělá jako
 * zvýšená plocha nad obsahem, ne jako tenká čára; musí být na první pohled vidět,
 * že se tím dá vrátit zpátky k úloze.
 */
@Composable
private fun MiniProgress(state: GenState.Running, onExpand: () -> Unit) {
    val progress by animateFloatAsState(state.overall, label = "mini")
    Column(
        Modifier
            .fillMaxWidth()
            .shadow(14.dp, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .background(
                Brush.linearGradient(listOf(Violet.copy(alpha = .34f), Cyan.copy(alpha = .20f)))
            )
            .clickable(onClick = onExpand)
    ) {
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(),
            color = Cyan,
            trackColor = Outline1,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
        Row(
            Modifier.padding(start = 14.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxSize(),
                    color = Cyan,
                    trackColor = Outline1,
                    strokeWidth = 3.dp,
                    gapSize = 0.dp,
                    strokeCap = StrokeCap.Round,
                )
                Text(
                    "${(progress * 100).toInt()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextHi,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stageText(state.stage, state.kind),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextHi,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                Text(
                    buildString {
                        if (state.stage == Stage.SAMPLING && state.totalSteps > 0)
                            append("krok ${state.step}/${state.totalSteps}")
                        state.etaSeconds?.let {
                            if (isNotEmpty()) append(" · ")
                            append("zbývá ~${GenerationService.formatEta(it)}")
                        }
                        if (isEmpty()) append(state.label)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMid,
                    maxLines = 1
                )
            }
            Spacer(Modifier.size(8.dp))
            Row(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Cyan.copy(alpha = .16f))
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Zobrazit",
                    style = MaterialTheme.typography.labelMedium,
                    color = Cyan
                )
                Icon(Icons.Default.KeyboardArrowUp, null, Modifier.size(17.dp), Cyan)
            }
        }
    }
}

@Composable
private fun UpdateBanner(versionName: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.linearGradient(listOf(Violet.copy(alpha = .30f), Cyan.copy(alpha = .18f))))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.NewReleases, null, Modifier.size(19.dp), Cyan)
        Spacer(Modifier.size(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "Je k dispozici $versionName",
                style = MaterialTheme.typography.bodyMedium,
                color = TextHi,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "Klepni pro stažení a instalaci",
                style = MaterialTheme.typography.bodySmall,
                color = TextLow
            )
        }
        Icon(Icons.Default.ChevronRight, null, Modifier.size(20.dp), Cyan)
    }
}

@Composable
private fun BottomBar(tab: Tab, updateWaiting: Boolean, onSelect: (Tab) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Surface1)
            .navigationBarsPadding()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        BarItem("Tvořit", Icons.Default.AutoAwesome, tab == Tab.CREATE) { onSelect(Tab.CREATE) }
        BarItem("Galerie", Icons.Default.VideoLibrary, tab == Tab.GALLERY) { onSelect(Tab.GALLERY) }
        BarItem("Nastavení", Icons.Default.Settings, tab == Tab.SETTINGS, updateWaiting) {
            onSelect(Tab.SETTINGS)
        }
    }
}

@Composable
private fun BarItem(
    label: String,
    icon: ImageVector,
    active: Boolean,
    badge: Boolean = false,
    onClick: () -> Unit,
) {
    Column(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box {
            Icon(icon, null, Modifier.size(22.dp), if (active) Cyan else TextLow)
            if (badge) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .size(8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Cyan)
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = if (active) TextHi else TextLow
        )
    }
}
