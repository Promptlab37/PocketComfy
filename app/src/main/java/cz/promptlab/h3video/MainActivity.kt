package cz.promptlab.h3video

import cz.promptlab.h3video.data.t

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import cz.promptlab.h3video.data.AppSettings
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.border
import cz.promptlab.h3video.ui.theme.Ok
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
import cz.promptlab.h3video.ui.theme.pozadiAplikace
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
        // Odkud se vybírají fotky — čte se hned, ať platí i pro první výběr.
        runCatching {
            val s = cz.promptlab.h3video.data.AppSettings(this)
            cz.promptlab.h3video.ui.VyberFotek.zeSouboru = s.vyberZeSouboru
            cz.promptlab.h3video.ui.VyberFotek.hledatNaInternetu = s.hledatNaInternetu
        }
        // Nic z přípravy okna nesmí shodit start – obrazovka je důležitější než
        // hezké okraje i než dotaz na oprávnění.
        runCatching { enableEdgeToEdge() }
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        cz.promptlab.h3video.ui.theme.Vzhled.motiv =
            cz.promptlab.h3video.ui.theme.Motiv.zUlozeneho(cz.promptlab.h3video.data.AppSettings(this).vzhled)
        setContent { H3Theme { Root() } }
    }

    /**
     * Schová spodní navigační tlačítka, dokud je appka vepředu — na kartách je
     * pod nimi tlačítko Generovat a lišta ukusuje kus obrazovky. Vytáhnou se
     * přejetím od spodního okraje a samy zase zmizí
     * (`BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`), takže se z telefonu nikam
     * neztratí. Stavový řádek s hodinami a baterií zůstává vidět.
     *
     * Volá se i při návratu do popředí: systém lištu po přepnutí aplikace,
     * odemčení nebo skrytí klávesnice vrací zpátky.
     */
    private fun schovejNavigaci() {
        runCatching {
            val app = AppSettings(this)
            val rizeni = WindowCompat.getInsetsController(window, window.decorView)
            rizeni.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (app.skryvatNavigaci) {
                rizeni.hide(WindowInsetsCompat.Type.navigationBars())
            } else {
                rizeni.show(WindowInsetsCompat.Type.navigationBars())
            }
        }
    }

    override fun onResume() {
        super.onResume()
        schovejNavigaci()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) schovejNavigaci()
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
    val savingResults by vm.savingResults.collectAsStateWithLifecycle()
    val rucniStahovani by vm.rucniStahovani.collectAsStateWithLifecycle()
    val kontext = androidx.compose.ui.platform.LocalContext.current
    // Akce „Pokračuj s…" do karty, kterou si uživatel skryl, se nenabízí.
    val skryteKarty by vm.skryteKartyUcinne.collectAsStateWithLifecycle()
    val skryteVolby by vm.skryteVolby.collectAsStateWithLifecycle()
    // Po startu, až je ViewModel celý: skrytá volba nesmí zůstat vybraná.
    LaunchedEffect(Unit) { vm.opravVybraneVolby() }
    val galleryState = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
    val updateState by vm.update.collectAsStateWithLifecycle()
    var opened by remember { mutableStateOf<VideoItem?>(null) }

    // Průběh se dá sbalit, aby šlo mezitím listovat galerií nebo měnit nastavení.
    val running = state as? GenState.Running
    var progressExpanded by remember { mutableStateOf(true) }

    /**
     * Odložený výsledek: hotový běh, který si NEMÁ vzít celou obrazovku.
     *
     * Dokončení úlohy na pozadí je podle pravidel rozhraní věc pro nenápadné
     * oznámení s akcí, ne pro okno přes celou obrazovku — to patří jen tam, kde
     * musí uživatel rozhodnout. Dřív se výsledek otevřel vždycky a překryl
     * i prohlížený obrázek nebo rozepsané zadání.
     *
     * Na celou obrazovku se otevře jen tehdy, když uživatel na běh opravdu
     * čekal (má otevřený průběh) a zrovna si nic neprohlíží.
     */
    var vysledekOdlozen by remember { mutableStateOf(false) }
    LaunchedEffect(state) {
        if (state is GenState.Done) {
            vysledekOdlozen = !(progressExpanded && opened == null)
        }
    }
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
    // Na hlavní obrazovce Zpět appku jen schová, neukončí: na Androidu 8–11 by konec
    // aktivity zrušil rozdělanou přípravu, namlouvání i stahování (audit 30. 9. 2026).
    val aktivita = androidx.compose.ui.platform.LocalContext.current as? android.app.Activity
    BackHandler(enabled = !backUsable) { aktivita?.moveTaskToBack(true) }

    // Nabídka aktualizace vyskočí sama po spuštění, jakmile kontrola najde novější
    // vydání. „Později" zavře jen okno – proužek nad obsahem zůstane, aby se dalo
    // vrátit k tomu kdykoli potom.
    // Zavření se pamatuje na konkrétní verzi – když mezitím vyjde novější,
    // okno se ozve znovu, jen „Později" u stejné verze se neopakuje.
    var updateDialogClosedFor by rememberSaveable { mutableStateOf("") }
    val updateOffer = updateState.takeIf {
        it is UpdateState.Available || (it is UpdateState.Downloading && !it.tiche) ||
            it is UpdateState.Ready
    }
    val offerVersion = (updateOffer as? UpdateState.Available)?.info?.versionName
        ?: (updateOffer as? UpdateState.Ready)?.takeIf { it.tiche }?.info?.versionName?.let { "ready $it" } ?: "~"
    if (updateOffer != null && updateDialogClosedFor != offerVersion && running == null) {
        UpdateDialog(
            state = updateOffer,
            onDownload = { (updateOffer as? UpdateState.Available)?.let { vm.downloadUpdate(it.info) } },
            onLater = { updateDialogClosedFor = offerVersion },
        )
    }

    // Telefon na šířku = editor časové osy přes celou obrazovku. Na střihovou
    // práci je potřeba šířka a všechno po ruce naráz (pás, ovládání, Generovat),
    // takže se tu neukazuje běžná rolovací obrazovka. Otočením zpět se appka
    // vrátí přesně tam, kde byla – osa se ukládá průběžně.
    // Editor se ale otevírá JEN když je karta Timeline aktivní – otočení
    // telefonu na jiné kartě (čtení výsledku, psaní promptu) dřív násilně
    // přepnulo režim a uživatel přišel o rozdělanou kartu.
    val naSirku = LocalConfiguration.current.orientation ==
        android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val aktParams by vm.params.collectAsStateWithLifecycle()
    val vTimeline = aktParams.mode == cz.promptlab.h3video.data.Mode.TIMELINE
    if (naSirku && vTimeline && !overlay) {
        LaunchedEffect(Unit) { vm.selectTab(Tab.CREATE) }
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
            .then(Modifier.pozadiAplikace())
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
            Header(tab, vm.versionName, vm.serverStatus.collectAsStateWithLifecycle().value.state)
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

                    Tab.GALLERY -> galleryState.SaveableStateProvider("gallery") {
                        val smazane by vm.smazane.collectAsStateWithLifecycle()
                        HistoryScreen(
                            items = history,
                            totalBytes = historyBytes,
                            onOpen = { opened = it },
                            onDelete = { vm.delete(it) },
                            onDeleteMany = { vm.deleteMany(it) },
                            onFavorite = { vm.toggleFavorite(it) },
                            onRename = { item, title -> vm.renameResult(item, title) },
                            onCreate = { vm.selectTab(Tab.CREATE) },
                            smazane = smazane,
                            onUndo = { vm.undoDelete() },
                        )
                    }

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
            // Hotový výsledek, který si nevzal obrazovku: nabídne se proužkem,
            // dokud ho uživatel neotevře nebo neodklidí. Nic neblokuje.
            (state as? GenState.Done)?.takeIf { vysledekOdlozen && !imeOtevrena }?.let { s ->
                HotovoPruh(
                    item = s.item,
                    onOpen = { vysledekOdlozen = false },
                    onDismiss = { GenerationEngine.dismissResult() },
                )
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
                    val fronta by vm.queue.collectAsStateWithLifecycle()
                    ProgressScreen(
                        state = s,
                        onMinimize = { progressExpanded = false },
                        onCancel = { GenerationEngine.cancel() },
                        queueCount = fronta.size,
                        fronta = fronta,
                        onRemoveFromQueue = { vm.removeFromQueue(it) },
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = state is GenState.Done && !vysledekOdlozen,
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
                    val vysledek = history.firstOrNull { it.id == s.item.id } ?: s.item
                    val hudbaUmi by vm.sbHudbaDostupna.collectAsStateWithLifecycle()
                    androidx.compose.runtime.LaunchedEffect(vysledek.id) {
                        if (vysledek.filmNaServeru.isNotBlank()) vm.overSbHudbu()
                    }
                    val hudbaFronta by vm.hudbaVeFronte.collectAsStateWithLifecycle()
                    ResultScreen(
                        item = vysledek,
                        // Po zařazení hudby se výsledek zavře, stejně jako v galerii (5.18).
                        onAddMusic = if (cz.promptlab.h3video.data.jdePridatHudbu(vysledek, hudbaUmi) && vysledek.id !in hudbaFronta) {
                            { styl, db -> vm.pridatHudbu(vysledek, styl, db); GenerationEngine.dismissResult() }
                        } else null,
                        hlasitostHudby = vm.hlasitostHudby(),
                        warnings = s.warnings,
                        naServeru = vysledek.naServeru(kontext),
                        stahovani = rucniStahovani[vysledek.id],
                        onStahnout = { vm.stahnoutVysledek(vysledek) },
                        onClose = { GenerationEngine.dismissResult() },
                        onAgain = {
                            GenerationEngine.dismissResult()
                            vm.selectTab(Tab.CREATE)
                        },
                        // Jen u řetězu Long MiniMax — jinde nemá co zahazovat.
                        onDiscard = if (s.item.retez.isNotBlank()) {
                            {
                                vm.zahodZaber(s.item)
                                GenerationEngine.dismissResult()
                                vm.selectTab(Tab.CREATE)
                            }
                        } else null,
                        onSave = { vm.saveResult(s.item) },
                        saving = s.item.id in savingResults,
                        onFavorite = { vm.toggleFavorite(s.item) },
                        onRename = { vm.renameResult(s.item, it) },
                        onUpscale = {
                            GenerationEngine.dismissResult()
                            vm.posliDoZvetseni(s.item)
                        }.takeUnless { cz.promptlab.h3video.data.Mode.UPSCALE in skryteKarty },
                        onEdit = {
                            GenerationEngine.dismissResult()
                            vm.posliDoUpravy(s.item)
                        }.takeUnless { cz.promptlab.h3video.data.Mode.EDIT in skryteKarty },
                        onExtend = {
                            GenerationEngine.dismissResult()
                            vm.posliDoRozsireni(s.item)
                        }.takeUnless { cz.promptlab.h3video.data.Mode.INPAINT in skryteKarty },
                        onInpaint = {
                            GenerationEngine.dismissResult()
                            vm.posliDoDomalovani(s.item)
                        }.takeUnless { cz.promptlab.h3video.data.Mode.INPAINT in skryteKarty },
                        onAnimate = {
                            GenerationEngine.dismissResult()
                            vm.posliDoRozhybani(s.item)
                        }.takeUnless { cz.promptlab.h3video.data.Mode.ALLINONE in skryteKarty },
                        onUpscaleVideo = {
                            GenerationEngine.dismissResult()
                            vm.posliVideoDoZvetseni(s.item)
                        }.takeUnless { cz.promptlab.h3video.data.Mode.VYLEPSENI_VIDEA in skryteKarty || "VYLEPSENI_VIDEA.ZVETSIT" in skryteVolby },
                        onSmoothVideo = {
                            GenerationEngine.dismissResult()
                            vm.posliVideoDoVylepseni(s.item, cz.promptlab.h3video.data.VylepseniRezim.ZPLYNULIT)
                        }.takeUnless { cz.promptlab.h3video.data.Mode.VYLEPSENI_VIDEA in skryteKarty || "VYLEPSENI_VIDEA.ZPLYNULIT" in skryteVolby },
                        onEditVideo = {
                            GenerationEngine.dismissResult()
                            vm.posliVideoDoUpravy(s.item)
                        }.takeUnless { cz.promptlab.h3video.data.Mode.UPRAVA_VIDEA in skryteKarty },
                        onExtendVideo = {
                            GenerationEngine.dismissResult()
                            vm.posliVideoDoProdlouzeni(s.item)
                        }.takeUnless { cz.promptlab.h3video.data.Mode.ALLINONE in skryteKarty },
                        onMusicToVideo = {
                            GenerationEngine.dismissResult()
                            vm.posliHudbuDoVidea(s.item)
                        }.takeUnless { cz.promptlab.h3video.data.Mode.LTXAUDIO in skryteKarty },
                        onMusicToDance = {
                            GenerationEngine.dismissResult()
                            vm.posliHudbuDoTance(s.item)
                        }.takeUnless { cz.promptlab.h3video.data.Mode.POHYB in skryteKarty || "POHYB.HUDBA" in skryteVolby },
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

        val open = opened?.let { selected -> history.firstOrNull { it.id == selected.id } ?: selected }
        if (open != null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Ink)
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                val hudbaUmi by vm.sbHudbaDostupna.collectAsStateWithLifecycle()
                androidx.compose.runtime.LaunchedEffect(open.id) {
                    if (open.filmNaServeru.isNotBlank()) vm.overSbHudbu()
                }
                val hudbaFronta by vm.hudbaVeFronte.collectAsStateWithLifecycle()
                ResultScreen(
                    item = open,
                    hlasitostHudby = vm.hlasitostHudby(),
                    onAddMusic = if (cz.promptlab.h3video.data.jdePridatHudbu(open, hudbaUmi) && open.id !in hudbaFronta) {
                        { styl, db -> vm.pridatHudbu(open, styl, db); opened = null }
                    } else null,
                    naServeru = open.naServeru(kontext),
                    stahovani = rucniStahovani[open.id],
                    onStahnout = { vm.stahnoutVysledek(open) },
                    onClose = { opened = null },
                    onAgain = { opened = null; vm.selectTab(Tab.CREATE) },
                    onDiscard = if (open.retez.isNotBlank()) {
                        { vm.zahodZaber(open); opened = null; vm.selectTab(Tab.CREATE) }
                    } else null,
                    onSave = { vm.saveResult(open) },
                    saving = open.id in savingResults,
                    onFavorite = { vm.toggleFavorite(open) },
                    onRename = { vm.renameResult(open, it) },
                    onUpscale = {
                        opened = null
                        vm.posliDoZvetseni(open)
                    }.takeUnless { cz.promptlab.h3video.data.Mode.UPSCALE in skryteKarty },
                    onEdit = {
                        opened = null
                        vm.posliDoUpravy(open)
                    }.takeUnless { cz.promptlab.h3video.data.Mode.EDIT in skryteKarty },
                    onExtend = {
                        opened = null
                        vm.posliDoRozsireni(open)
                    }.takeUnless { cz.promptlab.h3video.data.Mode.INPAINT in skryteKarty },
                    onInpaint = {
                        opened = null
                        vm.posliDoDomalovani(open)
                    }.takeUnless { cz.promptlab.h3video.data.Mode.INPAINT in skryteKarty },
                    onAnimate = {
                        opened = null
                        vm.posliDoRozhybani(open)
                    }.takeUnless { cz.promptlab.h3video.data.Mode.ALLINONE in skryteKarty },
                    onUpscaleVideo = {
                        opened = null
                        vm.posliVideoDoZvetseni(open)
                    }.takeUnless { cz.promptlab.h3video.data.Mode.VYLEPSENI_VIDEA in skryteKarty || "VYLEPSENI_VIDEA.ZVETSIT" in skryteVolby },
                    onSmoothVideo = {
                        opened = null
                        vm.posliVideoDoVylepseni(open, cz.promptlab.h3video.data.VylepseniRezim.ZPLYNULIT)
                    }.takeUnless { cz.promptlab.h3video.data.Mode.VYLEPSENI_VIDEA in skryteKarty || "VYLEPSENI_VIDEA.ZPLYNULIT" in skryteVolby },
                    onEditVideo = {
                        opened = null
                        vm.posliVideoDoUpravy(open)
                    }.takeUnless { cz.promptlab.h3video.data.Mode.UPRAVA_VIDEA in skryteKarty },
                    onExtendVideo = {
                        opened = null
                        vm.posliVideoDoProdlouzeni(open)
                    }.takeUnless { cz.promptlab.h3video.data.Mode.ALLINONE in skryteKarty },
                    onMusicToVideo = {
                        opened = null
                        vm.posliHudbuDoVidea(open)
                    }.takeUnless { cz.promptlab.h3video.data.Mode.LTXAUDIO in skryteKarty },
                    onMusicToDance = {
                        opened = null
                        vm.posliHudbuDoTance(open)
                    }.takeUnless { cz.promptlab.h3video.data.Mode.POHYB in skryteKarty || "POHYB.HUDBA" in skryteVolby },
                )
            }
        }
    }
}

@Composable
private fun Header(tab: Tab, version: String, server: ServerState) {
    Column(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(10.dp)
                    .clip(RoundedCornerShape(50))
                    // Tečka je zároveň stav počítače: zelená připravený,
                    // oranžová vypnutý, šedá zjišťuje se.
                    .background(
                        when (server) {
                            ServerState.ONLINE -> cz.promptlab.h3video.ui.theme.Ok
                            ServerState.OFFLINE -> cz.promptlab.h3video.ui.theme.Amber
                            else -> TextLow
                        }
                    )
            )
            Spacer(Modifier.size(9.dp))
            // Jméno vlastní, ne jméno cizího modelu — „MiniMax" je ochranná
            // známka a patří jen do popisů karet (nominativní užití).
            // Jméno appky a pod ním podpis autora — PocketComfy dělá PromptLab.
            Column {
                Text(
                    "PocketComfy",
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 19.sp),
                    color = TextHi,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "by PromptLab",
                    fontFamily = cz.promptlab.h3video.ui.theme.JetBrainsMono,
                    fontSize = 10.sp,
                    letterSpacing = 0.6.sp,
                    color = cz.promptlab.h3video.ui.theme.Vzhled.motiv.popisekBarva,
                )
            }
            Spacer(Modifier.size(8.dp))
            Spacer(Modifier.weight(1f))
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
/**
 * Proužek „hotovo" nad spodní lištou. Nenápadné oznámení s akcí — přesně to,
 * co patří k dokončení práce na pozadí. Klepnutím se výsledek otevře, křížkem
 * se odklidí; do ničeho, co uživatel dělá, nezasahuje.
 */
@Composable
private fun HotovoPruh(item: VideoItem, onOpen: () -> Unit, onDismiss: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Surface1)
            .border(1.dp, Cyan.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
            .clickable { onOpen() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.CheckCircle, null, Modifier.size(20.dp), Ok)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                when {
                    item.isModel3d -> t("3D model je hotový")
                    item.isImage -> t("Obrázek je hotový")
                    item.isAudio -> t("Skladba je hotová")
                    else -> t("Video je hotové")
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                t("Klepni pro zobrazení"),
                style = MaterialTheme.typography.bodySmall, color = TextLow,
            )
        }
        Icon(
            Icons.Default.Close, t("Odklidit"),
            Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(50))
                .clickable { onDismiss() }
                .padding(6.dp),
            TextMid,
        )
    }
}

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
                    stageText(state.stage, state.kind, state.model),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextHi,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                Text(
                    buildString {
                        if (state.stage == Stage.SAMPLING && state.totalSteps > 0)
                            append(t("krok %d/%d").format(state.step, state.totalSteps))
                        state.etaSeconds?.let {
                            if (isNotEmpty()) append(" · ")
                            append(t("zbývá ~%s").format(GenerationService.formatEta(it)))
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
                    t("Zobrazit"),
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
                t("Je k dispozici %s").format(versionName),
                style = MaterialTheme.typography.bodyMedium,
                color = TextHi,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                t("Klepni pro stažení a instalaci"),
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
            // Mezera navíc pod vlastní lištou: `navigationBarsPadding` zajistí,
            // že se tlačítka nepřekrývají se systémovými, ale nalepená na sebe
            // se pletou prstu — Galerie se trefovala do Zpět.
            .padding(top = 6.dp, bottom = 14.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        BarItem(t("Tvořit"), Icons.Default.AutoAwesome, tab == Tab.CREATE) { onSelect(Tab.CREATE) }
        BarItem(t("Galerie"), Icons.Default.VideoLibrary, tab == Tab.GALLERY) { onSelect(Tab.GALLERY) }
        BarItem(t("Nastavení"), Icons.Default.Settings, tab == Tab.SETTINGS, updateWaiting) {
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
        cz.promptlab.h3video.ui.TextVesel(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = if (active) TextHi else TextLow,
        )
    }
}
