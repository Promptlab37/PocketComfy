package cz.promptlab.h3video

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cz.promptlab.h3video.comfy.ComfyClient
import cz.promptlab.h3video.comfy.ComfyException
import cz.promptlab.h3video.data.t
import cz.promptlab.h3video.comfy.ImagePromptBuilder
import cz.promptlab.h3video.comfy.PromptRewriteBuilder
import cz.promptlab.h3video.data.AioMode
import cz.promptlab.h3video.data.AioScene
import cz.promptlab.h3video.data.AioSlot
import cz.promptlab.h3video.data.AioStore
import cz.promptlab.h3video.data.Upscaler
import cz.promptlab.h3video.data.aioHints
import cz.promptlab.h3video.data.aioProblem
import cz.promptlab.h3video.data.AppSettings
import cz.promptlab.h3video.data.GenParams
import cz.promptlab.h3video.data.HistoryStore
import cz.promptlab.h3video.data.ImageEditScene
import cz.promptlab.h3video.data.ImageEditStore
import cz.promptlab.h3video.data.imageEditHints
import cz.promptlab.h3video.data.imageEditProblem
import cz.promptlab.h3video.data.FaceSwapScene
import cz.promptlab.h3video.data.FaceSwapStore
import cz.promptlab.h3video.data.faceSwapHints
import cz.promptlab.h3video.data.faceSwapProblem
import cz.promptlab.h3video.data.InpaintModel
import cz.promptlab.h3video.data.InpaintScene
import cz.promptlab.h3video.data.InpaintStore
import cz.promptlab.h3video.data.inpaintHints
import cz.promptlab.h3video.data.inpaintProblem
import cz.promptlab.h3video.data.MusicScene
import cz.promptlab.h3video.data.MusicStore
import cz.promptlab.h3video.data.musicHints
import cz.promptlab.h3video.data.musicProblem
import cz.promptlab.h3video.data.RestoreScene
import cz.promptlab.h3video.data.RestoreStore
import cz.promptlab.h3video.data.restoreProblem
import cz.promptlab.h3video.data.UpscaleScene
import cz.promptlab.h3video.data.UpscaleStore
import cz.promptlab.h3video.data.upscaleHints
import cz.promptlab.h3video.data.upscaleProblem
import cz.promptlab.h3video.data.LoraEntry
import cz.promptlab.h3video.data.Mode
import cz.promptlab.h3video.data.NATIVE_SHORT_EDGE
import cz.promptlab.h3video.data.Profile
import cz.promptlab.h3video.data.Line
import cz.promptlab.h3video.data.Speaker
import cz.promptlab.h3video.data.TURBO
import cz.promptlab.h3video.data.TalkScene
import cz.promptlab.h3video.data.TalkStore
import cz.promptlab.h3video.data.VideoItem
import cz.promptlab.h3video.data.VoiceSource
import cz.promptlab.h3video.data.VoiceStatus
import cz.promptlab.h3video.data.SegmentMode
import cz.promptlab.h3video.data.TimelineScene
import cz.promptlab.h3video.data.TimelineSegment
import cz.promptlab.h3video.data.TimelineStore
import cz.promptlab.h3video.data.timelineProblem
import cz.promptlab.h3video.data.MAX_SECONDS
import cz.promptlab.h3video.data.MIN_SECONDS
import cz.promptlab.h3video.data.composePrompt
import cz.promptlab.h3video.engine.GenState
import cz.promptlab.h3video.engine.GenerationEngine
import cz.promptlab.h3video.engine.QueuedRun
import cz.promptlab.h3video.engine.RunQueue
import cz.promptlab.h3video.higgs.HiggsClient
import cz.promptlab.h3video.higgs.HiggsLauncher
import cz.promptlab.h3video.higgs.Voice
import cz.promptlab.h3video.update.UpdateChecker
import cz.promptlab.h3video.update.UpdateInfo
import cz.promptlab.h3video.util.ImageUtils
import cz.promptlab.h3video.util.MediaSaver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class Tab { CREATE, GALLERY, SETTINGS }

data class ServerCheck(
    val checking: Boolean = false,
    val ok: Boolean? = null,
    val message: String = "",
)

/** Stav počítače s ComfyUI tak, jak ho appka průběžně vidí. */
enum class ServerState { UNKNOWN, ONLINE, OFFLINE }

data class ServerStatus(
    val state: ServerState = ServerState.UNKNOWN,
    /** Jak dlouho už je server nedostupný, v sekundách (0 = běží nebo se neví). */
    val offlineSeconds: Int = 0,
    /** Výpadek nastal uprostřed generování – úloha na počítači běží dál. */
    val duringRun: Boolean = false,
)

/** Průběh kontroly „co serveru chybí" (nody a modely podle předloh v APK). */
sealed interface AuditState {
    data object Idle : AuditState
    data object Running : AuditState
    data class Done(val report: cz.promptlab.h3video.comfy.AuditReport) : AuditState
    data class Failed(val message: String) : AuditState
}

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data class Downloading(val info: UpdateInfo, val progress: Float) : UpdateState
    data class Ready(val info: UpdateInfo, val apk: File) : UpdateState
    data class Failed(val message: String) : UpdateState
}

class MainViewModel(app: Application) : AndroidViewModel(app) {

    val settings = AppSettings(app)
    private val historyStore = HistoryStore(app)

    private val _tab = MutableStateFlow(Tab.CREATE)
    val tab: StateFlow<Tab> = _tab.asStateFlow()

    private val _params = MutableStateFlow(settings.load())
    val params: StateFlow<GenParams> = _params.asStateFlow()

    private val _history = MutableStateFlow(historyStore.all())
    val history: StateFlow<List<VideoItem>> = _history.asStateFlow()

    private val _server = MutableStateFlow(settings.serverUrl)
    val server: StateFlow<String> = _server.asStateFlow()

    /**
     * Bez adresy serveru se místo appky ukáže úvodní obrazovka. Osobní
     * sestavení má výchozí adresu zapečenou, takže ji nikdy neuvidí;
     * veřejné sestavení se tu při prvním spuštění zeptá.
     */
    private val _serverConfigured = MutableStateFlow(settings.serverConfigured)
    val serverConfigured: StateFlow<Boolean> = _serverConfigured.asStateFlow()

    private val _check = MutableStateFlow(ServerCheck())
    val check: StateFlow<ServerCheck> = _check.asStateFlow()

    private val _serverStatus = MutableStateFlow(ServerStatus())
    val serverStatus: StateFlow<ServerStatus> = _serverStatus.asStateFlow()

    /**
     * Je na serveru balík ComfyUI-ALLinONE-MinimaxH3? `null` = ještě nevíme
     * (server zatím neodpověděl) a nikoho neomezujeme; `false` je potvrzené
     * „chybí" a karty All in One a Dialogy to řeknou PŘED nahráváním fotek.
     */
    private val _aioAvailable = MutableStateFlow<Boolean?>(null)
    val aioAvailable: StateFlow<Boolean?> = _aioAvailable.asStateFlow()

    // Odvázané LoRA pro kartu Obrázek — čtou se ze serveru (vše, co má
    // v názvu zimage/zit), takže nová stažená LoRA se objeví sama.
    private val _zimageLoras = MutableStateFlow<List<String>>(emptyList())
    val zimageLoras: StateFlow<List<String>> = _zimageLoras.asStateFlow()

    fun refreshZimageLoras() {
        if (_zimageLoras.value.isNotEmpty()) return
        viewModelScope.launch {
            val nalezene = withContext(Dispatchers.IO) {
                runCatching {
                    ComfyClient(settings.serverUrl).loraNames().filter {
                        it.contains("zimage", ignoreCase = true) ||
                            it.contains("zit", ignoreCase = true) ||
                            it.contains("z-image", ignoreCase = true)
                    }
                }.getOrDefault(emptyList())
            }
            if (nalezene.isNotEmpty()) _zimageLoras.value = nalezene.sorted()
        }
    }

    private val _advancedOpen = MutableStateFlow(false)
    val advancedOpen: StateFlow<Boolean> = _advancedOpen.asStateFlow()

    private val _update = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val update: StateFlow<UpdateState> = _update.asStateFlow()

    val versionName: String get() = UpdateChecker.currentVersionName(getApplication())
    val versionCode: Int get() = UpdateChecker.currentVersionCode(getApplication())

    /**
     * Start aplikace nesmí spadnout. Tečka.
     *
     * Appka se opakovaně vypínala hned po zapnutí a příčinou byla práce, kterou
     * si tady pouštěla: navazování na starou úlohu, služba na popředí, obnova
     * náhledů. Cokoli z toho vyhodí výjimku a je po appce. Proto je každý krok
     * zvlášť ošetřený — když jeden selže, ostatní běží dál a uživatel se aspoň
     * dostane dovnitř.
     *
     * Navazování na rozdělané generování se ze startu odstranilo úplně; spouští
     * se až z obrazovky, když server potvrdí, že úloha existuje.
     */
    init {
        safely("engine") { GenerationEngine.init(app) }
        safely("update") { checkUpdate(silent = true) }
        safely("server") { watchServer() }
        safely("foreground") { watchForegroundUpdates() }
        safely("resume") { resumeIfServerConfirms() }
    }

    private inline fun safely(what: String, block: () -> Unit) {
        runCatching(block).onFailure {
            android.util.Log.w("H3App", "start: krok \"$what\" selhal", it)
        }
    }

    /**
     * Navázání na rozdělané generování – ale až potom, co server potvrdí, že o
     * úloze ví. Dřív se navazovalo hned při startu a naslepo, takže zápis po
     * dávno zrušené úloze rozjížděl službu na popředí při každém spuštění.
     */
    private fun resumeIfServerConfirms() {
        if (settings.activePromptId == null) return
        viewModelScope.launch {
            runCatching {
                delay(1_500)                     // ať je UI napřed venku
                GenerationEngine.resumeIfPending()
            }.onFailure { android.util.Log.w("H3App", "navazani selhalo", it) }
        }
    }

    /**
     * Kontrola aktualizací při každém návratu do popředí, ne jen při studeném
     * startu. Android proces běžně nechává žít, takže „spustit aplikaci" často
     * znamená jen přepnutí zpátky – a kontrola z init by se pak už nikdy nespustila.
     *
     * Nejvýš jednou za čtvrt hodiny: víc by bylo jen zbytečné buzení sítě.
     */
    private fun watchForegroundUpdates() {
        viewModelScope.launch {
            runCatching {
            val lifecycle = androidx.lifecycle.ProcessLifecycleOwner.get().lifecycle
            val state = androidx.lifecycle.Lifecycle.State.STARTED
            var lastCheck = System.currentTimeMillis()
            while (true) {
                lifecycle.currentStateFlow.first { !it.isAtLeast(state) }
                lifecycle.currentStateFlow.first { it.isAtLeast(state) }
                if (System.currentTimeMillis() - lastCheck > 15 * 60_000L) {
                    lastCheck = System.currentTimeMillis()
                    checkUpdate(silent = true)
                }
            }
            }.onFailure { android.util.Log.w("H3App", "hlidani aktualizaci skoncilo", it) }
        }
    }

    /**
     * Průběžně sleduje, jestli počítač s ComfyUI odpovídá, aby to uživatel viděl
     * dřív, než zmáčkne Generovat.
     *
     * Dvě pravidla, obě kvůli baterii a poctivosti:
     *  – ptá se JEN když je appka na obrazovce; na pozadí by pingy přes Tailscale
     *    držely rádio vzhůru a nikdo by výsledek stejně neviděl;
     *  – během generování se neptá vlastním dotazem (engine se serverem mluví sám),
     *    ale přebírá jeho skutečný stav spojení místo slepého „online".
     */
    private fun watchServer() {
        viewModelScope.launch {
            runCatching {
            val lifecycle = androidx.lifecycle.ProcessLifecycleOwner.get().lifecycle
            var offlineSince = 0L
            while (true) {
                // spí, dokud appka není v popředí (STARTED = viditelná)
                lifecycle.currentStateFlow.first { it.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED) }

                val running = GenerationEngine.state.value as? GenState.Running
                if (running != null) {
                    _serverStatus.value =
                        if (running.offline) ServerStatus(ServerState.OFFLINE, duringRun = true)
                        else ServerStatus(ServerState.ONLINE)
                    offlineSince = 0L
                    delay(5_000)
                    continue
                }
                val alive = withContext(Dispatchers.IO) {
                    runCatching { ComfyClient(settings.serverUrl).isAlive() }.getOrDefault(false)
                }
                if (alive) {
                    offlineSince = 0L
                    _serverStatus.value = ServerStatus(ServerState.ONLINE)
                    // Balík se ověřuje jen když o něm nic nevíme (start appky,
                    // nebo server mezitím spadl) – žádné zbytečné dotazy navíc.
                    if (_aioAvailable.value == null) {
                        _aioAvailable.value = withContext(Dispatchers.IO) {
                            runCatching { ComfyClient(settings.serverUrl).hasAllInOne() }
                                .getOrNull()
                        }
                    }
                } else {
                    _aioAvailable.value = null
                    if (offlineSince == 0L) offlineSince = System.currentTimeMillis()
                    _serverStatus.value = ServerStatus(
                        ServerState.OFFLINE,
                        ((System.currentTimeMillis() - offlineSince) / 1000).toInt()
                    )
                }
                // Když server neběží, ptát se častěji – ať je naskočení hned vidět.
                delay(if (alive) 30_000L else 8_000L)
            }
            }.onFailure { android.util.Log.w("H3App", "hlidani serveru skoncilo", it) }
        }
    }

    /**
     * Vypne ComfyUI na počítači a uvolní grafickou kartu – aby se dalo hrát.
     * Nespouští se nic jiného; spouštěč ukončí jen ten proces, který sám nastartoval.
     */
    fun stopServer(onDone: (String) -> Unit) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching { ComfyClient(settings.serverUrl).requestServerStop() }
                    .getOrDefault(false)
            }
            _serverStatus.value = ServerStatus(if (ok) ServerState.OFFLINE else ServerState.UNKNOWN)
            onDone(
                if (ok) "ComfyUI vypnuto, grafika je volná"
                else "Nepovedlo se – běží na počítači spouštěč?"
            )
        }
    }

    /** Ruční „zkus to hned" z hlavní obrazovky. */
    fun refreshServerStatus() {
        viewModelScope.launch {
            val alive = withContext(Dispatchers.IO) {
                runCatching { ComfyClient(settings.serverUrl).isAlive() }.getOrDefault(false)
            }
            if (alive) _serverStatus.value = ServerStatus(ServerState.ONLINE)
        }
    }

    // ------------------------------------------------------------- aktualizace

    /** [silent] = tichá kontrola po startu; neukáže „jsi aktuální" ani chybu sítě. */
    fun checkUpdate(silent: Boolean = false) {
        if (_update.value is UpdateState.Downloading) return
        if (!silent) _update.value = UpdateState.Checking
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { UpdateChecker.check(getApplication(), settings.githubToken) }
            }
            result.fold(
                onSuccess = { info ->
                    _update.value = when {
                        info != null -> UpdateState.Available(info)
                        silent -> UpdateState.Idle
                        else -> UpdateState.UpToDate
                    }
                },
                // I tichá kontrola musí chybu někam odložit. Dřív skončila jako
                // Idle a v Nastavení to pak vypadalo úplně stejně jako „nic
                // nového" – nešlo poznat, že se appka na GitHub vůbec nedostala.
                // Na hlavní obrazovku se stejně dostane jen stav Available,
                // takže tím nikoho neotravuje.
                onFailure = {
                    _update.value =
                        UpdateState.Failed(it.message ?: "Aktualizaci se nepodařilo ověřit")
                }
            )
        }
    }

    fun downloadUpdate(info: UpdateInfo) {
        _update.value = UpdateState.Downloading(info, 0f)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    UpdateChecker.download(getApplication(), info, settings.githubToken) { p ->
                        _update.value = UpdateState.Downloading(info, p)
                    }
                }
            }
            _update.value = result.fold(
                onSuccess = { UpdateState.Ready(info, it) },
                onFailure = { UpdateState.Failed("Stažení se nepovedlo: ${it.message}") }
            )
        }
    }

    fun dismissUpdate() { _update.value = UpdateState.Idle }

    // ------------------------------------------------------------- poslední pád

    private val _crash = MutableStateFlow(H3App.lastCrash(app))
    val crash: StateFlow<String?> = _crash.asStateFlow()

    fun clearCrash() {
        H3App.clearLastCrash(getApplication())
        _crash.value = null
    }

    private val _token = MutableStateFlow(settings.githubTokenRaw)
    val token: StateFlow<String> = _token.asStateFlow()

    /** Appka má token zapečený v sestavení – uživatel nemusí nic vyplňovat. */
    val hasBuiltInToken: Boolean get() = settings.hasBuiltInToken

    fun setToken(v: String) { _token.value = v }

    fun saveToken() {
        settings.githubToken = _token.value
        _token.value = settings.githubTokenRaw
        checkUpdate()
    }


    fun selectTab(t: Tab) { _tab.value = t }

    fun toggleAdvanced() { _advancedOpen.value = !_advancedOpen.value }

    // ------------------------------------------------------------------ LoRA

    private val _availableLoras = MutableStateFlow<List<String>>(emptyList())
    val availableLoras: StateFlow<List<String>> = _availableLoras.asStateFlow()

    private val _loraError = MutableStateFlow<String?>(null)
    val loraError: StateFlow<String?> = _loraError.asStateFlow()

    /**
     * Načte seznam LoRA ze serveru – appka nenabízí nic, co tam ve skutečnosti není.
     * Čte se při každém otevření výběru: nově přidaná LoRA na počítači se tak
     * objeví hned, bez restartu aplikace. Při chybě zůstane poslední známý seznam.
     */
    fun loadLoras() {
        viewModelScope.launch {
            val res = withContext(Dispatchers.IO) {
                runCatching { ComfyClient(settings.serverUrl).loraNames() }
            }
            res.fold(
                onSuccess = { _availableLoras.value = it; _loraError.value = null },
                onFailure = { _loraError.value = "Seznam LoRA se nepodařilo načíst — server neodpovídá." }
            )
        }
    }

    private val _availableUnets = MutableStateFlow<List<String>>(emptyList())
    val availableUnets: StateFlow<List<String>> = _availableUnets.asStateFlow()

    private val _unetError = MutableStateFlow<String?>(null)
    val unetError: StateFlow<String?> = _unetError.asStateFlow()

    /**
     * Modely, které server nabízí. Stejný vzor jako u LoRA – čte se při otevření
     * výběru, takže nově stažený model je vidět bez restartu aplikace. Chyba se
     * nesmí spolknout: bez ní by výběr věčně ukazoval „načítám".
     */
    fun loadUnets() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                runCatching { ComfyClient(settings.serverUrl).unetNames() }
            }.fold(
                onSuccess = { _availableUnets.value = it; _unetError.value = null },
                onFailure = { _unetError.value = "Seznam modelů se nepodařilo načíst — server neodpovídá." }
            )
        }
    }

    fun setUnet(name: String) = update { it.copy(unetFl2va = name) }

    fun addLora(name: String) = update { p ->
        if (p.extraLoras.any { it.name == name }) p
        else p.copy(extraLoras = p.extraLoras + LoraEntry(name))
    }

    fun removeLora(name: String) = update { p ->
        p.copy(extraLoras = p.extraLoras.filterNot { it.name == name })
    }

    fun setLoraEnabled(name: String, on: Boolean) = update { p ->
        p.copy(extraLoras = p.extraLoras.map { if (it.name == name) it.copy(enabled = on) else it })
    }

    fun setLoraStrength(name: String, strength: Float) = update { p ->
        p.copy(extraLoras = p.extraLoras.map { if (it.name == name) it.copy(strength = strength) else it })
    }

    /** Vrátí pokročilá nastavení na hodnoty zvoleného profilu. */
    fun resetToWorkflowDefaults() {
        val d = GenParams()
        update {
            it.profile.applyTo(it).copy(
                shiftAudio = d.shiftAudio,
                sageAttention = d.sageAttention,
                crf = d.crf,
                teaCache = d.teaCache,
            )
        }
    }

    /** Sedí pokročilá nastavení s profilem, nebo je uživatel někam posunul? */
    fun matchesWorkflow(p: GenParams): Boolean {
        val d = GenParams()
        val prof = p.profile
        return p.steps == prof.steps && p.sampler == prof.sampler &&
            p.scheduler == prof.scheduler && p.shiftVideo == prof.shiftVideo &&
            p.spectrum == prof.spectrum && p.turboLoraOn == prof.useLora &&
            p.shiftAudio == d.shiftAudio && p.sageAttention == d.sageAttention &&
            p.crf == d.crf && p.teaCache == d.teaCache
    }

    // ------------------------------------------------------------------ profil

    /**
     * Přepnutí Turbo / Kvalita. Dosadí sadu hodnot, které k profilu patří –
     * jednotlivosti si pak uživatel může doladit v pokročilém nastavení.
     */
    fun setProfile(profile: Profile) {
        if (profile == _params.value.profile) return
        update { profile.applyTo(it) }
    }

    /**
     * Referenční cesta nesnese profil, který je jen pro fl2va váhy (Fast).
     * Přepnutí karty nebo podrežimu proto takový profil vymění za V2 Turbo —
     * jinak by uživateli zůstaly čtyři kroky bez odpovídající LoRA a výsledek
     * by byl šum.
     */
    private fun hlidejProfilKCeste() {
        val p = _params.value
        if (!p.profile.bezReferenci) return
        val referencni = p.mode == Mode.TALK ||
            (p.mode == Mode.ALLINONE && _aio.value.mode.usesRefWeights)
        if (referencni) update { Profile.V2_TURBO.applyTo(it) }
    }

    fun setTurboLoraOn(on: Boolean) = update { it.copy(turboLoraOn = on) }

    fun setTurboLoraStrength(s: Float) = update { it.copy(turboLoraStrength = s) }

    /**
     * Výměna Turbo LoRA. U známých LoRA se rovnou dosadí i jejich trénovací shift
     * a počet kroků – 768p verze je trénovaná na shift 6, a kdyby zůstal 12,
     * vypadalo by to jako vada LoRA (viz ModelTC/Minimax-H3-Turbo).
     */
    fun setTurboLora(file: String) = update { p ->
        val known = TURBO.profileFor(file)
        p.copy(
            turboLora = file,
            turboLoraOn = true,
            steps = known?.steps ?: p.steps,
            shiftVideo = known?.shiftVideo ?: p.shiftVideo,
        )
    }

    /** Přepnutí karty. Prompt se pamatuje zvlášť pro každý režim. */
    /** Přepnutí jazyka rozhraní — projeví se hned, obrazovky se překreslí. */
    fun setJazyk(v: cz.promptlab.h3video.data.Jazyk.Volba) {
        cz.promptlab.h3video.data.Jazyk.nastav(v)
        settings.jazyk = v.kod
    }

    fun setMode(mode: Mode) {
        if (mode == _params.value.mode) return
        val next = _params.value.copy(mode = mode, prompt = settings.promptFor(mode))
        _params.value = next
        settings.save(next)
        hlidejProfilKCeste()
    }

    fun update(block: (GenParams) -> GenParams) {
        val next = block(_params.value)
        _params.value = next
        settings.save(next)
    }

    // ----------------------------------------------------------- soubory karet

    private fun mediaDir() = File(getApplication<Application>().filesDir, "refs").apply { mkdirs() }

    /** Zkopíruje vybraný soubor do aplikace, přípona zůstává – ComfyUI podle ní čte formát. */
    private suspend fun importMedia(uri: Uri, baseName: String): File? = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = getApplication<Application>().contentResolver
            val display = resolver.query(uri, null, null, null, null)?.use { c ->
                val i = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (i >= 0 && c.moveToFirst()) c.getString(i) else null
            }
            val ext = display?.substringAfterLast('.', "")?.takeIf { it.isNotEmpty() }
                ?: android.webkit.MimeTypeMap.getSingleton()
                    .getExtensionFromMimeType(resolver.getType(uri))
                ?: "bin"
            // starou verzi s jinou příponou smazat, ať se soubory nehromadí
            mediaDir().listFiles { f -> f.nameWithoutExtension == baseName }?.forEach { it.delete() }
            val target = File(mediaDir(), "$baseName.$ext")
            resolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { input.copyTo(it) }
            } ?: return@runCatching null
            target.takeIf { it.length() > 0 }
        }.getOrNull()
    }

    /**
     * Nechybí něco k odeslání? Vrací hlášku pro uživatele, nebo null když je vše v pořádku.
     * Hodnoty se předávají zvenčí schválně: v Compose se tak čtení stavu odehraje
     * v té části obrazovky, která se má při změně překreslit.
     */
    fun validation(p: GenParams): String? {
        // All in One i Dialogy jedou na šablonách balíku ze serveru – když tam
        // balík prokazatelně chybí, ať to uživatel ví hned, ne až po nahrání fotek.
        // Balík ALLinONE potřebují jen karty All in One a Dialogy.
        if ((p.mode == Mode.ALLINONE || p.mode == Mode.TALK) && _aioAvailable.value == false) {
            return "Na serveru chybí balík ComfyUI-ALLinONE-MinimaxH3 – nainstaluj ho " +
                "v ComfyUI a restartuj server."
        }
        // Poslední pojistka u profilu jen pro fl2va cestu. UI ho u referenčních
        // karet nenabízí a přepnutí karty ho vymění, ale uložené nastavení
        // z dřívějška by se sem jinak protlačilo.
        val referencni = p.mode == Mode.TALK ||
            (p.mode == Mode.ALLINONE && _aio.value.mode.usesRefWeights)
        if (referencni && p.profile.bezReferenci) {
            return "Profil ${p.profile.title} nejde použít s referencemi – přepni profil."
        }
        return when (p.mode) {
            Mode.TALK -> validateScene(_scene.value)
            Mode.TIMELINE -> timelineProblem(_timeline.value)
            Mode.ALLINONE -> aioProblem(_aio.value)
            Mode.EDIT -> imageEditProblem(_edit.value)
            Mode.UPSCALE -> upscaleProblem(_upscale.value)
            Mode.IMAGE ->
                if (p.prompt.isBlank()) t("Napiš, co má na obrázku být.") else null
            Mode.MUSIC -> musicProblem(_music.value)
            Mode.RESTORE -> restoreProblem(_restore.value)
            Mode.FACESWAP -> faceSwapProblem(_swap.value)
            Mode.INPAINT -> inpaintProblem(_inpaint.value)
        }
    }

    /**
     * Ověření mluvící scény. Prompt se skládá sám, takže se na rozdíl od ostatních
     * karet nekontroluje jako první – uživatel by nechápal, co má psát.
     */
    fun validateScene(s: TalkScene): String? {
        if (s.withImage.isEmpty()) return t("Přidej první postavě fotku.")
        val written = s.written
        if (written.isEmpty()) return t("Napiš aspoň jednu repliku.")
        written.firstOrNull { s.speakerOf(it)?.image == null }?.let {
            return t("Repliku říká postava bez fotky – doplň jí fotku.")
        }
        written.firstOrNull { s.speakerOf(it)?.voice == null }?.let {
            return t("Postava, která mluví, potřebuje vybraný hlas.")
        }
        written.firstOrNull { !it.voiceCurrent }?.let {
            return if (it.audio == null) t("Nech repliky namluvit.")
            else t("Replika se změnila – nech ji namluvit znovu.")
        }
        if (s.prompt.isBlank()) return t("Prompt je prázdný.")
        return null
    }

    /** Upozornění, které nebrání spuštění – jen se hodí vědět. */
    fun hints(p: GenParams): List<String> {
        val out = mutableListOf<String>()
        // Karta All in One jede na cizí šabloně a má vlastní pravidla; upozornění
        // od ostatních karet (sigma shift z profilu, značky <Picture N>) se jí
        // netýkají, tak se ani nemíchají dohromady.
        if (p.mode == Mode.ALLINONE) return aioHints(_aio.value, p)
        // Úprava obrázku má vlastní upozornění – s videem nemá nic společného.
        if (p.mode == Mode.EDIT) return imageEditHints(_edit.value)
        if (p.mode == Mode.UPSCALE) return upscaleHints(_upscale.value)
        // Obrázek z textu jede na vlastní předloze; upozornění k videu se ho netýkají.
        if (p.mode == Mode.IMAGE) return emptyList()
        if (p.mode == Mode.MUSIC) return musicHints(_music.value)
        if (p.mode == Mode.RESTORE) return emptyList()
        if (p.mode == Mode.FACESWAP) return faceSwapHints(_swap.value)
        if (p.mode == Mode.INPAINT) return inpaintHints(_inpaint.value)
        // Dialogy jedou referenční cestou (ref2va). Turbo LoRA je trénovaná
        // jen na text a snímky, takže se tam vyplatí profil Kvalita.
        val refCesta = p.mode.usesRefModel
        if (refCesta && p.turboLoraOn) {
            out += "Turbo LoRA je trénovaná jen na text a snímky videa, ne na reference. " +
                "Tady se vyplatí přepnout na Kvalitu – podoba postav i poslušnost zadání " +
                "bývají výrazně věrnější."
        }
        if (refCesta && p.refImageSize != "max") {
            out += "Reference se posílají zmenšené na velikost výstupu („Vyvážené\"). " +
                "Pro věrnou podobu lidí přepni v pokročilém nastavení na „Maximální detail\"."
        }
        if (p.shiftVideo != p.profile.shiftVideo) {
            out += "Sigma shift je %.2f, workflow má u tohoto profilu %.2f. Odchylka mění celý průběh vzorkování."
                .format(p.shiftVideo, p.profile.shiftVideo)
        }
        // Porovnává se s plátnem daného poměru stran, ne s plochým číslem:
        // u 21:9 má model nativně 672 px na kratší hraně, u ostatních 768.
        if (refCesta && p.resolution.pixels < p.nativeResolution.pixels) {
            out += "Rozlišení ${p.resolution.label} je pod plátnem modelu " +
                "(${p.nativeResolution.label}) – obraz bývá měkčí a tváře méně přesné."
        }
        if (p.mode == Mode.TALK) {
            val s = _scene.value
            val needed = s.neededSeconds
            if (needed != null && !s.fitsInto(p.seconds)) {
                out += "Namluvené repliky trvají ${needed} s, ale video má ${p.seconds} s – " +
                    "model konec ustřihne. Přidej délku, nebo repliky zkrať."
            } else if (needed != null) {
                out += "Délka je nastavená na ${p.seconds} s podle namluvených replik (potřeba ${needed} s)."
            }
            if (s.speakers.size > s.withImage.size) {
                out += "Postava bez fotky se do videa nedostane – model nemá podle čeho ji vytvořit."
            }
            if (!s.canAddLine) {
                out += "Tři repliky jsou strop jednoho videa (model bere jen tři zvukové reference)."
            }
        }
        if (p.frames < cz.promptlab.h3video.data.TRAINED_MIN_FRAMES) {
            out += "Délka pod 5 s je mimo trénovaný rozsah modelu – výsledek bývá horší."
        }
        if (p.aboveNative) {
            out += "Rozlišení ${p.resolution.label} je o ${p.nativeOverhead} % víc bodů než " +
                "plátno modelu (${p.nativeResolution.label}). Generovat se to dá, jen to trvá " +
                "déle a detaily bývají měkčí – ostřejší HD spíš vyjde z nativu a zvětšení " +
                "v kartě All in One."
        }
        return out
    }

    // ------------------------------------------------------------ fronta úloh

    /**
     * Fronta žije v [RunQueue] na úrovni procesu, ne tady. Do 3.00 byla ve
     * ViewModelu a při zahození obrazovky na pozadí (dlouhé video, zamčený
     * telefon) zmizela i s druhým zařazeným během. Tady je jen průchod.
     */
    val queue: StateFlow<List<QueuedRun>> get() = RunQueue.queue

    fun removeFromQueue(id: Long) = RunQueue.remove(id)

    fun start() {
        val p = _params.value
        if (validation(p) != null) return
        settings.save(p)
        // Vše jde přes frontu: když je volno, spustí se hned; když se generuje,
        // běh počká se zadáním zmrazeným teď (prompt, volby; náhodný seed se
        // losuje až při startu běhu, takže série dá pokaždé jiný výsledek).
        RunQueue.add(makeRunner(p))
    }

    /** Zmrazí aktuální zadání karty do spustitelného běhu pro frontu. */
    private fun makeRunner(p: GenParams): QueuedRun {
        val id = System.nanoTime()
        return when (p.mode) {
            Mode.TALK -> {
                // Pořadí obrázků a zvuků je závazné – podle něj se v promptu
                // číslují <Picture N> a <Audio N>, takže se posílá přesně tak,
                // jak se prompt skládal.
                val s = _scene.value
                QueuedRun(id, p.mode.title, s.prompt) {
                    GenerationEngine.start(
                        p.copy(prompt = s.prompt),
                        s.withImage.mapNotNull { it.image },
                        talkAudios = s.voiced.mapNotNull { it.audio },
                    )
                }
            }

            Mode.TIMELINE -> {
                val s = _timeline.value
                QueuedRun(id, p.mode.title, s.globalPrompt) {
                    resetOnlySegmentAfterRun = s.onlySegment > 0
                    GenerationEngine.start(
                        p.copy(
                            prompt = s.globalPrompt,
                            timelineProject = s.project,
                            timelineOnlySegment = s.onlySegment,
                        ),
                        s.withImage.mapNotNull { it.image },
                        timelineScene = s,
                    )
                }
            }

            Mode.ALLINONE -> {
                val s = _aio.value
                QueuedRun(id, p.mode.title, s.prompt) {
                    GenerationEngine.start(p.copy(prompt = s.prompt), s.uploadImages, aioScene = s)
                }
            }

            Mode.EDIT -> {
                val s = _edit.value
                // Poměr a velikost se přebírají ze scény úpravy, aby popisek běhu
                // i položka v galerii ukazovaly skutečné rozlišení obrázku.
                QueuedRun(id, p.mode.title, s.prompt) {
                    GenerationEngine.start(
                        p.copy(
                            prompt = s.prompt, aspect = s.aspect, megapixels = s.megapixels,
                            // Krea 2 jede na krocích z předlohy, ne na těch
                            // z nastavení videa — ukazatel to musí vědět.
                            steps = cz.promptlab.h3video.comfy.Krea2Builder.STEPS,
                        ),
                        s.uploadImages,
                        editScene = s,
                    )
                }
            }

            Mode.UPSCALE -> {
                val s = _upscale.value
                QueuedRun(id, p.mode.title, "") {
                    // Kroky SeedVR2 si řídí uzel sám (dlaždice × kroky), appka
                    // je nezná — nula říká ukazateli „ptej se serveru".
                    GenerationEngine.start(
                        p.copy(prompt = "", steps = 0), s.uploadImages, upscaleScene = s
                    )
                }
            }

            // Kroky se přepisují podle zvoleného modelu (Turbo 8, PerfecZion 12),
            // aby ukazatel průběhu počítal krok X/N a ne podle nastavení videa.
            Mode.IMAGE -> QueuedRun(id, p.mode.title, p.prompt) {
                GenerationEngine.start(
                    p.copy(steps = cz.promptlab.h3video.comfy.ZImageBuilder.stepsFor(p.zimageModel)),
                    emptyList(),
                    t2i = true,
                )
            }

            // Délka jde do parametrů kvůli popisku v galerii; prompt je styl,
            // aby položka historie ukazovala, o jakou skladbu šlo.
            Mode.MUSIC -> {
                val s = _music.value
                QueuedRun(id, p.mode.title, s.styl) {
                    GenerationEngine.start(
                        p.copy(
                            prompt = s.styl,
                            seconds = s.seconds,
                            steps = cz.promptlab.h3video.comfy.AceMusicBuilder.STEPS,
                        ),
                        emptyList(),
                        musicScene = s,
                    )
                }
            }

            Mode.RESTORE -> {
                val s = _restore.value
                QueuedRun(id, p.mode.title, "") {
                    GenerationEngine.start(
                        p.copy(
                            prompt = "Oprava staré fotky",
                            steps = cz.promptlab.h3video.comfy.RestoreBuilder.STEPS,
                        ),
                        s.uploadImages,
                        restoreScene = s,
                    )
                }
            }

            Mode.FACESWAP -> {
                val s = _swap.value
                QueuedRun(id, p.mode.title, "") {
                    GenerationEngine.start(
                        p.copy(
                            prompt = "Výměna tváře",
                            steps = cz.promptlab.h3video.comfy.FaceSwapBuilder.STEPS,
                        ),
                        s.uploadImages,
                        swapScene = s,
                    )
                }
            }

            Mode.INPAINT -> {
                val s = _inpaint.value
                QueuedRun(id, p.mode.title, s.prompt) {
                    GenerationEngine.start(
                        p.copy(
                            prompt = s.prompt,
                            steps = cz.promptlab.h3video.comfy.InpaintBuilder.stepsFor(s.model),
                        ),
                        s.uploadImages,
                        inpaintScene = s,
                    )
                }
            }
        }
    }

    // ------------------------------------------------------------- mluvící scéna

    private val talkStore = TalkStore(app)

    private val _scene = MutableStateFlow(TalkScene())
    val scene: StateFlow<TalkScene> = _scene.asStateFlow()

    // Rozdělaná scéna se vrací až tady a mimo hlavní vlákno – dekódují se u toho
    // náhledy fotek a to by při startu appky bylo znát.
    init {
        viewModelScope.launch {
            val restored = withContext(Dispatchers.IO) { talkStore.load() }
            if (restored.speakers.any { it.image != null } || restored.lines.any { it.text.isNotBlank() }) {
                _scene.value = restored
            }
        }
    }

    private val _voices = MutableStateFlow<List<Voice>>(emptyList())
    val voices: StateFlow<List<Voice>> = _voices.asStateFlow()

    /** Stav Higgse: "running" (připravený), "starting", "stopped", null = neodpovídá spouštěč. */
    private val _higgs = MutableStateFlow<String?>(null)
    val higgs: StateFlow<String?> = _higgs.asStateFlow()

    private val _higgsNote = MutableStateFlow("")
    val higgsNote: StateFlow<String> = _higgsNote.asStateFlow()

    private fun higgsClient() = HiggsClient(settings.higgsUrl, settings.higgsToken)
    private fun higgsLauncher() = HiggsLauncher(settings.higgsUrl)

    // Pole v nastavení. Prázdná adresa znamená „stejný počítač jako ComfyUI",
    // proto se do pole nepředvyplňuje odvozená hodnota – uživatel by pak nevěděl,
    // jestli si ji zadal sám, nebo mu ji appka jen ukazuje.
    private val _higgsServer = MutableStateFlow(settings.higgsUrlRaw)
    val higgsServer: StateFlow<String> = _higgsServer.asStateFlow()

    private val _higgsCode = MutableStateFlow(settings.higgsToken)
    val higgsCode: StateFlow<String> = _higgsCode.asStateFlow()

    fun setHiggsServer(v: String) { _higgsServer.value = v }
    fun setHiggsCode(v: String) { _higgsCode.value = v }

    fun saveHiggs() {
        settings.higgsUrl = _higgsServer.value
        settings.higgsToken = _higgsCode.value
        _higgsServer.value = settings.higgsUrlRaw
        _higgsCode.value = settings.higgsToken
        _voices.value = emptyList()   // jiný server = jiná knihovna hlasů
    }

    /**
     * Zapíše novou podobu scény. Prompt se přeskládá sám – ale jen do chvíle, než
     * do něj uživatel sáhne; od té chvíle je jeho a nikdo mu ho nepřepisuje.
     */
    private fun updateScene(block: (TalkScene) -> TalkScene) {
        val next = block(_scene.value).let { s ->
            if (s.promptEdited) s else s.copy(prompt = composePrompt(s))
        }
        _scene.value = next
        talkStore.save(next)
    }

    private fun updateSpeaker(key: Int, block: (Speaker) -> Speaker) =
        updateScene { s -> s.copy(speakers = s.speakers.map { if (it.key == key) block(it) else it }) }

    private fun updateLine(key: Int, block: (Line) -> Line) =
        updateScene { s -> s.copy(lines = s.lines.map { if (it.key == key) block(it) else it }) }

    fun addSpeaker() = updateScene { s ->
        if (!s.canAddSpeaker) s
        else s.copy(speakers = s.speakers + Speaker(key = (s.speakers.maxOfOrNull { it.key } ?: 0) + 1))
    }

    fun removeSpeaker(key: Int) {
        talkStore.forgetSpeaker(key)
        updateScene { s ->
            val left = s.speakers.filterNot { it.key == key }
            if (left.isEmpty()) return@updateScene s
            // Repliky osiřelé postavy se přepíšou na první zbylou, ať se dialog
            // nerozpadne a uživatel nepřišel o napsaný text.
            s.copy(
                speakers = left,
                lines = s.lines.map {
                    if (it.speakerKey == key) it.copy(speakerKey = left.first().key) else it
                },
            )
        }
    }

    fun setSpeakerLook(key: Int, look: String) = updateSpeaker(key) { it.copy(look = look) }

    fun setSpeakerVoice(key: Int, voice: VoiceSource?) = updateSpeaker(key) { it.copy(voice = voice) }

    /** Přidá repliku; ve výchozím stavu ji říká ten, kdo nemluvil naposledy. */
    fun addLine() = updateScene { s ->
        if (!s.canAddLine) s
        else {
            val last = s.lines.lastOrNull()?.speakerKey
            val next = s.speakers.firstOrNull { it.key != last }?.key ?: s.speakers.first().key
            s.copy(lines = s.lines + Line(key = (s.lines.maxOfOrNull { it.key } ?: 0) + 1, speakerKey = next))
        }
    }

    fun removeLine(key: Int) {
        talkStore.forgetLine(key)
        updateScene { s ->
            val left = s.lines.filterNot { it.key == key }
            s.copy(lines = left.ifEmpty { listOf(Line(key = 1, speakerKey = s.speakers.first().key)) })
        }
    }

    /** Změna textu zneplatní hotový hlas – ten říká stará slova. */
    fun setLineText(key: Int, text: String) = updateLine(key) { it.copy(text = text) }

    fun setLineSpeaker(key: Int, speakerKey: Int) = updateLine(key) { it.copy(speakerKey = speakerKey) }

    fun setSceneNote(note: String) = updateScene { it.copy(sceneNote = note) }

    fun setTalkPrompt(prompt: String) {
        val next = _scene.value.copy(prompt = prompt, promptEdited = true)
        _scene.value = next
        talkStore.save(next)
    }

    /** Vrátí prompt k automatickému skládání. */
    fun recomposeTalkPrompt() {
        val base = _scene.value.copy(promptEdited = false)
        val next = base.copy(prompt = composePrompt(base))
        _scene.value = next
        talkStore.save(next)
    }

    fun pickSpeakerImage(key: Int, uri: Uri?) {
        if (uri == null) return
        viewModelScope.launch {
            val target = talkStore.imageFile(key)
            val thumb = withContext(Dispatchers.IO) {
                ImageUtils.importToApp(getApplication(), uri, target)
            } ?: return@launch
            updateSpeaker(key) { it.copy(image = target, thumb = thumb) }
        }
    }

    fun setSpeakerSample(key: Int, file: File, label: String) =
        setSpeakerVoice(key, VoiceSource.Sample(file, label))

    fun pickSpeakerSample(key: Int, uri: Uri?) {
        if (uri == null) return
        viewModelScope.launch {
            val f = importMedia(uri, "sample_$key") ?: return@launch
            setSpeakerSample(key, f, f.name)
        }
    }


    /**
     * Načte hlasy z knihovny Higgse. Když neběží, zkusí ho napřed nahodit – ale
     * čeká jen na odpověď serveru, ne na načtený model: seznam hlasů je čtení
     * ze složky, na modelu nezávisí, a čekat kvůli němu 45 s by bylo zbytečné.
     */
    fun loadVoices(startIfNeeded: Boolean = true) {
        viewModelScope.launch {
            val ok = if (startIfNeeded) ensureHiggs(needModel = false) else higgsClient().isAlive()
            if (!ok) return@launch
            val list = withContext(Dispatchers.IO) {
                runCatching { higgsClient().voices() }.getOrDefault(emptyList())
            }
            if (list.isNotEmpty()) _voices.value = list
        }
    }

    /**
     * Postará se o to, aby Higgs běžel a měl načtený model. Vrací true, když je
     * připravený namlouvat.
     *
     * Higgs si drží grafiku, takže se pouští jen když je opravdu potřeba, a před
     * generováním videa ho engine zase složí.
     */
    private suspend fun ensureHiggs(needModel: Boolean = true): Boolean = withContext(Dispatchers.IO) {
        val launcher = higgsLauncher()
        val client = higgsClient()
        fun ready() = if (needModel) client.modelReady() else client.isAlive()
        if (ready()) {
            if (needModel) _higgs.value = "running"
            return@withContext true
        }
        _higgsNote.value = "Zapínám Higgs Audio na počítači…"
        _higgs.value = "starting"
        if (launcher.state() == null) {
            _higgsNote.value = "Na počítači neodpovídá spouštěč Higgse (port 8191)."
            _higgs.value = null
            return@withContext false
        }
        launcher.start()
        // Model se nahrává kolem 45 s; necháváme mu 3 minuty, ať to vydrží i po
        // studeném startu počítače.
        repeat(90) {
            delay(2_000)
            if (ready()) {
                if (needModel) _higgs.value = "running"
                _higgsNote.value = ""
                return@withContext true
            }
        }
        _higgsNote.value = "Higgs se nerozjel ani za tři minuty. Zkus to znovu."
        _higgs.value = "stopped"
        false
    }

    /** Ručně složí Higgs a uvolní grafiku (stejně to udělá i start generování videa). */
    fun stopHiggs(onDone: (String) -> Unit = {}) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { higgsLauncher().stop() }
            _higgs.value = if (ok) "stopped" else _higgs.value
            onDone(if (ok) "Higgs vypnutý, grafika je volná" else "Higgs jsem nespouštěl, není co vypínat")
        }
    }

    /**
     * Namluví repliku jedné postavy: hlas z knihovny přes `/api/generate`,
     * vlastní vzorek přes `/api/clone`. Hotové WAV se stáhne do aplikace a
     * odtud putuje do MiniMaxu jako `<Audio N>`.
     */
    /**
     * Hotový zvuk k replice místo namluvení Higgsem.
     *
     * Soubor se zkopíruje k sobě (odkaz do galerie může vypršet) a změří se jeho
     * délka – podle ní se počítá potřebná délka videa i časové značky replik
     * v promptu. Text repliky se nechává: model podle něj drží artikulaci a
     * v promptu z něj vzniká `<d>[Czech] …</d>`.
     */
    fun pickLineAudio(key: Int, uri: Uri?) {
        if (uri == null) return
        viewModelScope.launch {
            val hotovo = withContext(Dispatchers.IO) {
                runCatching {
                    val target = talkStore.audioFile(key)
                    getApplication<Application>().contentResolver.openInputStream(uri)!!
                        .use { vstup -> target.outputStream().use { vstup.copyTo(it) } }
                    val delka = android.media.MediaMetadataRetriever().let { r ->
                        try {
                            r.setDataSource(target.absolutePath)
                            (r.extractMetadata(
                                android.media.MediaMetadataRetriever.METADATA_KEY_DURATION
                            )?.toLongOrNull() ?: 0L) / 1000f
                        } finally {
                            runCatching { r.release() }
                        }
                    }
                    target to delka
                }.getOrNull()
            } ?: return@launch

            val (soubor, delka) = hotovo
            updateScene { s ->
                s.copy(lines = s.lines.map { l ->
                    if (l.key != key) l
                    else l.copy(
                        audio = soubor,
                        // Aby zvuk platil za aktuální, musí sedět s textem repliky.
                        spokenText = l.text.trim(),
                        status = VoiceStatus.READY,
                        audioSeconds = delka,
                        error = "",
                    )
                })
            }
        }
    }

    fun speakLine(key: Int) {
        val scene = _scene.value
        val line = scene.lines.firstOrNull { it.key == key } ?: return
        val text = line.text.trim()
        if (text.isEmpty()) return
        val voice = scene.speakerOf(line)?.voice
        if (voice == null) {
            updateLine(key) { it.copy(status = VoiceStatus.FAILED, error = "Postava nemá vybraný hlas.") }
            return
        }
        viewModelScope.launch {
            updateLine(key) { it.copy(status = VoiceStatus.RUNNING, progress = 0f, error = "") }
            if (!ensureHiggs()) {
                updateLine(key) {
                    it.copy(
                        status = VoiceStatus.FAILED,
                        error = _higgsNote.value.ifBlank { "Higgs se nepodařilo spustit." }
                    )
                }
                return@launch
            }
            val client = higgsClient()
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val jobId = when (voice) {
                        is VoiceSource.Library -> client.speak(text, voice.voiceId)
                        is VoiceSource.Sample -> client.clone(text, voice.file)
                    }
                    // Namlouvání běží na počítači jako úloha; ptáme se po dvou
                    // sekundách, ať je průběh vidět, ale server se nezahltí.
                    repeat(450) {
                        val job = client.job(jobId)
                        updateLine(key) { l -> l.copy(progress = job.progress) }
                        if (job.failed) throw IllegalStateException(
                            job.error.ifBlank { "Namluvení se nepovedlo." }
                        )
                        if (job.done) {
                            val target = talkStore.audioFile(key)
                            client.downloadAudio(jobId, target)
                            return@runCatching target
                        }
                        Thread.sleep(2_000)
                    }
                    throw IllegalStateException("Namlouvání trvá neúměrně dlouho, zkus kratší text.")
                }
            }
            result.fold(
                onSuccess = { file ->
                    val seconds = withContext(Dispatchers.IO) { audioSeconds(file) }
                    updateLine(key) {
                        it.copy(
                            audio = file, status = VoiceStatus.READY,
                            progress = 1f, error = "", spokenText = text,
                            audioSeconds = seconds,
                        )
                    }
                    fitLengthToLines()
                },
                onFailure = { e ->
                    updateLine(key) {
                        it.copy(
                            status = VoiceStatus.FAILED,
                            error = (e as? cz.promptlab.h3video.comfy.ComfyException)?.userMessage
                                ?: (e.message ?: "Namluvení se nepovedlo.")
                        )
                    }
                }
            )
        }
    }

    /** Skutečná délka namluveného souboru v sekundách (0 = nepodařilo se změřit). */
    private fun audioSeconds(file: File): Float = runCatching {
        val mmr = android.media.MediaMetadataRetriever()
        mmr.use {
            it.setDataSource(file.absolutePath)
            it.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()?.let { ms -> ms / 1000f } ?: 0f
        }
    }.getOrDefault(0f)

    /**
     * Nastaví délku videa tak, aby se repliky vešly.
     *
     * Dřív si uživatel musel délku odhadnout sám a když ji podstřelil, model
     * konec ustřihl uprostřed věty. Appka přitom hotové WAV má, takže délku zná
     * přesně – tohle je jediná správná odpověď, ne volba k nabídnutí.
     * Ruční posuvník zůstává: zvětšit délku (pauzy, dohra) dává smysl, zmenšit
     * pod délku řeči ne, a na to appka upozorní v poznámkách.
     */
    private fun fitLengthToLines() {
        val needed = _scene.value.neededSeconds ?: return
        if (_params.value.seconds == needed) return
        // Délka se nastavuje PŘESNĚ, tedy i dolů. Dřív se jen zvyšovala a to bylo
        // špatně: obraz i zvuk vznikají v jednom průchodu na celou délku videa,
        // takže volný čas po poslední replice si model vyplní vymyšlenou řečí.
        // Delší video než dialog není rezerva, je to prostor na blábolení.
        update { it.copy(seconds = needed) }
    }

    /** Namluví všechny repliky, které ještě hotový hlas nemají. */
    fun speakAll() {
        val scene = _scene.value
        scene.lines
            .filter { it.text.isNotBlank() && !it.voiceCurrent && scene.speakerOf(it)?.voice != null }
            .forEach { speakLine(it.key) }
    }

    // -------------------------------------------------------------- časová osa

    private val timelineStore = TimelineStore(app)

    private val _timeline = MutableStateFlow(TimelineScene())
    val timeline: StateFlow<TimelineScene> = _timeline.asStateFlow()

    init {
        viewModelScope.launch {
            val restored = withContext(Dispatchers.IO) { timelineStore.load() }
            if (restored.segments.any { it.prompt.isNotBlank() || it.image != null }) {
                _timeline.value = restored
            }
        }
    }

    private fun updateTimeline(block: (TimelineScene) -> TimelineScene) {
        val next = block(_timeline.value)
        _timeline.value = next
        timelineStore.save(next)
    }

    /**
     * „Přegenerovat jen segment N" je jednorázová volba: po úspěšně dokončeném
     * běhu osy se sama vypne, jinak by příští Generovat potichu přepočítal
     * zase jen ten jeden segment.
     */
    private var resetOnlySegmentAfterRun = false

    init {
        viewModelScope.launch {
            GenerationEngine.state.collect { st ->
                if (st is GenState.Done && resetOnlySegmentAfterRun) {
                    resetOnlySegmentAfterRun = false
                    if (_timeline.value.onlySegment > 0) {
                        updateTimeline { it.copy(onlySegment = 0) }
                    }
                }
            }
        }
    }

    fun addSegment() = updateTimeline { s ->
        if (!s.canAdd) s
        else s.copy(segments = s.segments + TimelineSegment(
            key = (s.segments.maxOfOrNull { it.key } ?: 0) + 1,
            // Nový segment rovnou navazuje na předchozí – to je celý smysl osy.
            mode = SegmentMode.IMAGE,
            inheritPrevious = true,
        ))
    }

    fun removeSegment(key: Int) = updateTimeline { s ->
        timelineStore.forgetSegment(key)
        val left = s.segments.filterNot { it.key == key }
        s.copy(segments = left.ifEmpty { listOf(TimelineSegment(key = 1)) })
    }

    /** Posun segmentu v ose o jedno místo (−1 doleva, +1 doprava). */
    fun moveSegment(key: Int, smer: Int) = updateTimeline { s ->
        val i = s.segments.indexOfFirst { it.key == key }
        val j = i + smer
        if (i < 0 || j !in s.segments.indices) s
        else {
            val list = s.segments.toMutableList()
            list.add(j, list.removeAt(i))
            // První segment nemá na co navázat – uzel by to odmítl.
            s.copy(segments = list.mapIndexed { index, seg ->
                if (index == 0) seg.copy(inheritPrevious = false) else seg
            })
        }
    }

    fun duplicateSegment(key: Int) = updateTimeline { s ->
        val i = s.segments.indexOfFirst { it.key == key }
        if (i < 0 || !s.canAdd) s
        else {
            val zdroj = s.segments[i]
            val list = s.segments.toMutableList()
            // Kopie nese text i délku, ale ne snímek: ten je uložený pod klíčem
            // segmentu a dva segmenty by si na něj sahaly navzájem.
            list.add(
                i + 1,
                zdroj.copy(
                    key = (s.segments.maxOfOrNull { it.key } ?: 0) + 1,
                    image = null, thumb = null,
                    mode = if (zdroj.image != null) SegmentMode.TEXT else zdroj.mode,
                )
            )
            s.copy(segments = list)
        }
    }

    fun setSegmentPrompt(key: Int, text: String) = updateTimeline { s ->
        s.copy(segments = s.segments.map { if (it.key == key) it.copy(prompt = text) else it })
    }

    fun setSegmentSeconds(key: Int, seconds: Float) = updateTimeline { s ->
        s.copy(segments = s.segments.map { if (it.key == key) it.copy(seconds = seconds) else it })
    }

    fun setSegmentMode(key: Int, mode: SegmentMode) = updateTimeline { s ->
        s.copy(segments = s.segments.map { seg ->
            if (seg.key != key) seg
            else seg.copy(
                mode = mode,
                // Navázání dává smysl jen u obrázkového segmentu a ne u prvního.
                inheritPrevious = mode == SegmentMode.IMAGE &&
                    seg.inheritPrevious && s.segments.firstOrNull()?.key != key,
            )
        })
    }

    fun setSegmentInherit(key: Int, inherit: Boolean) = updateTimeline { s ->
        s.copy(segments = s.segments.map {
            if (it.key == key) it.copy(inheritPrevious = inherit) else it
        })
    }

    fun setTimelineOnlySegment(index: Int) = updateTimeline { it.copy(onlySegment = index) }

    fun setTimelineGlobal(text: String) = updateTimeline { it.copy(globalPrompt = text) }

    fun pickSegmentImage(key: Int, uri: Uri?) {
        if (uri == null) return
        viewModelScope.launch {
            val target = timelineStore.imageFile(key)
            val thumb = withContext(Dispatchers.IO) {
                ImageUtils.importToApp(getApplication(), uri, target)
            } ?: return@launch
            updateTimeline { s ->
                s.copy(segments = s.segments.map {
                    if (it.key == key) it.copy(
                        image = target, thumb = thumb,
                        mode = SegmentMode.IMAGE, inheritPrevious = false,
                    ) else it
                })
            }
        }
    }

    // ------------------------------------------------------------- galerie

    fun refreshHistory() {
        _history.value = historyStore.all()
        _historyBytes.value = historyStore.totalBytes()
    }

    // Hned po startu, ať v galerii nesvítí "0,0 MB" u videí, která tam jsou.
    private val _historyBytes = MutableStateFlow(historyStore.totalBytes())
    val historyBytes: StateFlow<Long> = _historyBytes.asStateFlow()

    // Mazání s možností Vrátit: soubor se jen odsune do koše v cache a záznam
    // se schová. Definitivně zmizí až po pár vteřinách, nebo dalším mazáním.
    private val _smazane = MutableStateFlow<VideoItem?>(null)
    val smazane: StateFlow<VideoItem?> = _smazane.asStateFlow()
    private var smazaniJob: kotlinx.coroutines.Job? = null

    private fun kosFile(item: VideoItem) =
        File(getApplication<android.app.Application>().cacheDir, "kos_${item.fileName}")

    fun delete(item: VideoItem) {
        smazaniJob?.cancel()
        val predchozi = _smazane.value
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                predchozi?.let { runCatching { kosFile(it).delete() } }
                val src = item.file(getApplication())
                val kos = kosFile(item)
                runCatching {
                    if (!src.renameTo(kos)) { src.copyTo(kos, overwrite = true); src.delete() }
                }
                historyStore.removeEntry(item)
            }
            _smazane.value = item
            refreshHistory()
            smazaniJob = viewModelScope.launch {
                delay(6000)
                _smazane.value = null
                withContext(Dispatchers.IO) { runCatching { kosFile(item).delete() } }
            }
        }
    }

    fun undoDelete() {
        val item = _smazane.value ?: return
        smazaniJob?.cancel()
        _smazane.value = null
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val kos = kosFile(item)
                val cil = item.file(getApplication())
                runCatching {
                    if (!kos.renameTo(cil)) { kos.copyTo(cil, overwrite = true); kos.delete() }
                }
                historyStore.add(item)
            }
            refreshHistory()
        }
    }

    /** Doplní do galerie telefonu videa, která tam z nějakého důvodu chybí. */
    fun saveAllToGallery(onDone: (Int) -> Unit = {}) {
        viewModelScope.launch {
            val missing = _history.value.filterNot { it.inGallery }
            var ok = 0
            withContext(Dispatchers.IO) {
                missing.forEach { item ->
                    // Každý druh do své složky a se svou příponou — obrázek
                    // do Obrázků, skladba do Hudby; MP3 uložené jako .mp4
                    // do Filmů by hudební přehrávače nenašly.
                    val ext = item.fileName.substringAfterLast('.', "")
                    val saved = when {
                        item.isImage -> MediaSaver.saveImageToGallery(
                            getApplication(), item.file(getApplication()),
                            "H3_${item.createdAt}.${ext.ifBlank { "png" }}"
                        )
                        item.isAudio -> MediaSaver.saveAudioToGallery(
                            getApplication(), item.file(getApplication()),
                            "H3_${item.createdAt}.${ext.ifBlank { "mp3" }}"
                        )
                        else -> MediaSaver.saveToGallery(
                            getApplication(), item.file(getApplication()),
                            "H3_${item.createdAt}.mp4"
                        )
                    }
                    if (saved) {
                        historyStore.markInGallery(item.id); ok++
                    }
                }
            }
            refreshHistory()
            onDone(ok)
        }
    }

    fun markSaved(item: VideoItem) {
        historyStore.markInGallery(item.id)
        refreshHistory()
    }

    var autoSaveToGallery: Boolean
        get() = settings.autoSaveToGallery
        set(v) { settings.autoSaveToGallery = v; _autoSave.value = v }

    private val _autoSave = MutableStateFlow(settings.autoSaveToGallery)
    val autoSave: StateFlow<Boolean> = _autoSave.asStateFlow()


    // ------------------------------------------------------------- All in One

    private val aioStore = AioStore(app)

    private val _aio = MutableStateFlow(AioScene())
    val aio: StateFlow<AioScene> = _aio.asStateFlow()

    init {
        viewModelScope.launch {
            _aio.value = withContext(Dispatchers.IO) { aioStore.load() }
        }
    }

    private fun updateAio(block: (AioScene) -> AioScene) {
        val next = block(_aio.value)
        _aio.value = next
        aioStore.save(next)
    }

    // ------------------------------------------------ ✨ vylepšení promptu (AI)

    sealed interface RewriteState {
        data object Idle : RewriteState
        data object Busy : RewriteState
        data class Fail(val message: String) : RewriteState
    }

    private val _rewriteState = MutableStateFlow<RewriteState>(RewriteState.Idle)
    val rewriteState: StateFlow<RewriteState> = _rewriteState.asStateFlow()

    /** Původní zadání před přepsáním — na jedno ťuknutí se dá vrátit. */
    private val _rewriteOriginal = MutableStateFlow<String?>(null)
    val rewriteOriginal: StateFlow<String?> = _rewriteOriginal.asStateFlow()

    fun vratPuvodniPrompt() {
        _rewriteOriginal.value?.let { setAioPrompt(it) }
        _rewriteOriginal.value = null
    }

    /**
     * Odešle graf přepisovače a počká na text z náhledového uzlu.
     * Společné pro kartu All in One i Obrázek — liší se jen graf.
     */
    private suspend fun spustPrepisAPockej(
        client: ComfyClient,
        wf: org.json.JSONObject,
        uzelNahledu: String,
    ): String {
        val promptId = java.util.UUID.randomUUID().toString()
        client.queuePrompt(wf, java.util.UUID.randomUUID().toString(), promptId)
        // LLM se načítá z disku, první přepis klidně přes minutu.
        val limit = System.currentTimeMillis() + 240_000
        while (System.currentTimeMillis() < limit) {
            val h = client.history(promptId)
            if (h != null) {
                val status = h.optJSONObject("status")
                if (status?.optString("status_str") == "error") throw ComfyException(
                    "rewrite error",
                    "Přepis na serveru selhal — mrkni do logu ComfyUI.",
                )
                val text = h.optJSONObject("outputs")
                    ?.optJSONObject(uzelNahledu)
                    ?.optJSONArray("text")
                if (text != null && text.length() > 0) return text.getString(0)
                if (status?.optBoolean("completed") == true) throw ComfyException(
                    "bez textu",
                    "Server přepis dokončil, ale nevrátil text.",
                )
            }
            delay(1500)
        }
        throw ComfyException("timeout", "Přepis trvá moc dlouho — zkus to znovu.")
    }

    // ------------------------------------------------ 🌐 překlad promptu (AI)

    /** Pole se zadáním, které umí překladač obsloužit. */
    enum class PromptPole { OBRAZEK, AIO, UPRAVA, DOMALOVAT }

    private fun textPole(pole: PromptPole): String = when (pole) {
        PromptPole.OBRAZEK -> _params.value.prompt
        PromptPole.AIO -> _aio.value.prompt
        PromptPole.UPRAVA -> _edit.value.prompt
        PromptPole.DOMALOVAT -> _inpaint.value.prompt
    }

    private fun zapisPole(pole: PromptPole, text: String) = when (pole) {
        PromptPole.OBRAZEK -> update { it.copy(prompt = text) }
        PromptPole.AIO -> setAioPrompt(text)
        PromptPole.UPRAVA -> setEditPrompt(text)
        PromptPole.DOMALOVAT -> setInpaintPrompt(text)
    }

    /** Vrátí zadání, jak vypadalo před přepisem nebo překladem. */
    fun vratPuvodni(pole: PromptPole) {
        _rewriteOriginal.value?.let { zapisPole(pole, it) }
        _rewriteOriginal.value = null
    }

    /**
     * 🌐 **Přeložit do angličtiny.** Modely rozumí anglicky nejlíp, ale psát
     * anglicky je otrava — tohle vezme českou větu a vrátí ji anglicky, beze
     * změny obsahu (na rozdíl od ✨, které zadání rozepisuje). Jede na stejném
     * llama.cpp uzlu a stejném modelu v `models/LLM` jako vylepšovač.
     */
    fun prelozPrompt(pole: PromptPole) {
        if (_rewriteState.value is RewriteState.Busy) return
        val zadani = textPole(pole).trim()
        if (zadani.isBlank()) {
            _rewriteState.value = RewriteState.Fail(t("Nejdřív něco napiš, ať je co překládat."))
            return
        }
        _rewriteState.value = RewriteState.Busy
        viewModelScope.launch {
            val vysledek = withContext(Dispatchers.IO) {
                runCatching {
                    val client = ComfyClient(settings.serverUrl)
                    val spec = client.objectInfo(ImagePromptBuilder.LOADER_CLASS)
                        ?: throw ComfyException(
                            "chybi uzel",
                            "Na serveru chybí balík ComfyUI-llama-cpp_vlm — bez něj " +
                                "překladač nepojede.",
                        )
                    val nabidka = spec.optJSONObject("input")?.optJSONObject("required")
                        ?.optJSONArray("model")?.optJSONArray(0)
                        ?: throw ComfyException("chybi model", "Uzel nenabízí žádný model.")
                    val model = ImagePromptBuilder.vyberModel(
                        (0 until nabidka.length()).map { nabidka.getString(it) }
                    ) ?: throw ComfyException(
                        "zadny model",
                        "V models/LLM není žádný GGUF model, ze kterého by šlo překládat.",
                    )
                    val wf = ImagePromptBuilder.buildPreklad(
                        text = zadani,
                        model = model,
                        seed = kotlin.random.Random.nextLong(1, 0xFFFFFFFFL),
                    )
                    spustPrepisAPockej(client, wf, ImagePromptBuilder.N_PREVIEW)
                }
            }
            vysledek.onSuccess { text ->
                _rewriteOriginal.value = zadani
                zapisPole(pole, ImagePromptBuilder.ocisti(text))
                _rewriteState.value = RewriteState.Idle
            }.onFailure { e ->
                _rewriteState.value = RewriteState.Fail(
                    (e as? ComfyException)?.userMessage ?: e.message ?: t("Překlad se nepovedl.")
                )
            }
        }
    }

    /** Vrátí původní zadání na kartě Obrázek. */
    fun vratPuvodniPromptObrazku() {
        _rewriteOriginal.value?.let { puvodni -> update { it.copy(prompt = puvodni) } }
        _rewriteOriginal.value = null
    }

    /**
     * ✨ Vylepšit prompt na kartě **Obrázek**. Jede na obecném llama.cpp uzlu
     * s pravidly pro Z-Image (souvislé věty, 80–200 slov, bez negativního
     * promptu) — přepisovač od MiniMaxu píše scénář videa, sem by nesedl.
     */
    fun vylepsiObrazovyPrompt() {
        if (_rewriteState.value is RewriteState.Busy) return
        val zadani = _params.value.prompt.trim()
        if (zadani.isBlank()) {
            _rewriteState.value = RewriteState.Fail(
                t("Nejdřív napiš aspoň pár slov o tom, co chceš.")
            )
            return
        }
        _rewriteState.value = RewriteState.Busy
        viewModelScope.launch {
            val vysledek = withContext(Dispatchers.IO) {
                runCatching {
                    val client = ComfyClient(settings.serverUrl)
                    val spec = client.objectInfo(ImagePromptBuilder.LOADER_CLASS)
                        ?: throw ComfyException(
                            "llama uzel chybi",
                            "Server nemá uzly llama.cpp — bez nich prompt vylepšit nejde.",
                        )
                    val nabidka = spec.getJSONObject("input").getJSONObject("required")
                        .getJSONArray("model").getJSONArray(0)
                    val model = ImagePromptBuilder.vyberModel(
                        (0 until nabidka.length()).map { nabidka.getString(it) }
                    ) ?: throw ComfyException(
                        "zadny model",
                        "V models/LLM není žádný GGUF model, ze kterého by šlo psát.",
                    )
                    val wf = ImagePromptBuilder.build(
                        zadani = zadani,
                        model = model,
                        seed = kotlin.random.Random.nextLong(1, 0xFFFFFFFFL),
                    )
                    spustPrepisAPockej(client, wf, ImagePromptBuilder.N_PREVIEW)
                }
            }
            vysledek.onSuccess { text ->
                _rewriteOriginal.value = zadani
                update { it.copy(prompt = ImagePromptBuilder.ocisti(text)) }
                _rewriteState.value = RewriteState.Idle
            }.onFailure { e ->
                _rewriteState.value = RewriteState.Fail(
                    (e as? ComfyException)?.userMessage ?: e.message ?: "Přepis se nepovedl."
                )
            }
        }
    }

    /**
     * Pošle zadání karty All in One přepisovači na serveru (MiniMax-H3 Prompt
     * Rewriter 8B nad odblokovaným Qwen3-VL) a výsledný plný H3 prompt dosadí
     * zpět do pole. Česky napsané zadání přeloží a rozepíše sám; u režimu
     * „Z obrázku" dostane i snímek a popíše, co na něm vidí. LLM se po
     * přepisu z VRAM uklidí, video pak jede jako obvykle.
     */
    fun vylepsiAioPrompt() {
        if (_rewriteState.value is RewriteState.Busy) return
        val s = _aio.value
        val p = _params.value
        val zadani = s.prompt.trim()
        if (zadani.isBlank()) {
            _rewriteState.value = RewriteState.Fail("Nejdřív napiš aspoň pár slov o tom, co chceš.")
            return
        }
        _rewriteState.value = RewriteState.Busy
        viewModelScope.launch {
            val vysledek = withContext(Dispatchers.IO) {
                runCatching {
                    val client = ComfyClient(settings.serverUrl)
                    val spec = client.objectInfo(PromptRewriteBuilder.NODE_CLASS)
                        ?: throw ComfyException(
                            "rewriter chybi",
                            "Server nemá balík Prompt Rewriter — nainstaluj " +
                                "MiniMax-H3-Prompt-Rewriter-ComfyUI a restartuj ComfyUI.",
                        )
                    val req = spec.getJSONObject("input").getJSONObject("required")
                    val nabidka = req.getJSONArray("model").getJSONArray(0)
                    val model = PromptRewriteBuilder.vyberModel(
                        (0 until nabidka.length()).map { nabidka.getString(it) }
                    ) ?: throw ComfyException(
                        "zadny model",
                        "Přepisovač nenabízí žádný model — nahraj GGUF do models/LLM.",
                    )
                    // Snímky podle režimu karty: Z obrázku pošle první (a při
                    // zapnutém posledním snímku i ten); ostatní jedou z textu.
                    var first: String? = null
                    var last: String? = null
                    if (s.mode == AioMode.IMAGE) {
                        s.first.image?.let {
                            first = client.uploadImage(it.readBytes(), "rw_first.png")
                        }
                        if (s.useLastFrame) s.last.image?.let {
                            last = client.uploadImage(it.readBytes(), "rw_last.png")
                        }
                    } else if (s.mode == AioMode.KEYFRAMES) {
                        s.keys.firstOrNull { it.image != null }?.image?.let {
                            first = client.uploadImage(it.readBytes(), "rw_first.png")
                        }
                    }
                    val task = when {
                        first != null && last != null -> "FL2VA"
                        first != null -> "I2VA"
                        else -> "T2VA"
                    }
                    // Poměr stran a délka z toho, co má uživatel na kartě.
                    val rozliseniEnum = req.getJSONArray("resolution").getJSONArray(0)
                    val rozliseni = (0 until rozliseniEnum.length())
                        .map { rozliseniEnum.getString(it) }
                        .let { en ->
                            en.firstOrNull { it == p.aspect.label }
                                ?: en.firstOrNull { it == "16:9" } ?: en.first()
                        }
                    val durCfg = req.getJSONArray("duration").optJSONObject(1)
                    val delka = Math.round(s.frames / 24f)
                        .coerceIn(durCfg?.optInt("min", 2) ?: 2, durCfg?.optInt("max", 60) ?: 60)
                    // České zadání model občas vyloží i jako nápis do obrazu —
                    // tichý dovětek tomu předejde (do H3 promptu se nedostane,
                    // je to instrukce pro přepisovač, ne pro video model).
                    val zadaniProModel =
                        "$zadani. Do not add any on-screen text or captions unless explicitly requested."
                    val wf = PromptRewriteBuilder.build(
                        prompt = zadaniProModel,
                        model = model,
                        task = task,
                        resolution = rozliseni,
                        durationSec = delka,
                        seed = kotlin.random.Random.nextLong(1, 0xFFFFFFFFL),
                        firstImage = first,
                        lastImage = last,
                    )
                    spustPrepisAPockej(client, wf, PromptRewriteBuilder.N_PREVIEW)
                }
            }
            vysledek.onSuccess { text ->
                _rewriteOriginal.value = zadani
                // Přepisovač do promptu vždy dopíše podkresovou hudbu, i když
                // o ni nikdo nestál — bez zapnuté volby se vyhazuje.
                val hotovy = if (_params.value.rewriteHudba) text.trim()
                else PromptRewriteBuilder.bezPodkresoveHudby(text.trim())
                setAioPrompt(hotovy)
                _rewriteState.value = RewriteState.Idle
            }.onFailure { e ->
                _rewriteState.value = RewriteState.Fail(
                    (e as? ComfyException)?.userMessage ?: e.message ?: "Přepis se nepovedl."
                )
            }
        }
    }

    fun setAioMode(mode: AioMode) {
        updateAio { it.copy(mode = mode) }
        hlidejProfilKCeste()
        doplnReferencniZnacky()
    }

    /**
     * Reference (r2v): model potřebuje v popisu značky `<Picture N>`, jinak
     * fotky ignoruje. Doplní chybějící značky na začátek popisu — uživatel
     * pak jen dopíše, co se má dít. Co už v textu je, se nechává být (klidně
     * přesunuté doprostřed věty).
     */
    private fun doplnReferencniZnacky() {
        val s = _aio.value
        if (s.mode != AioMode.REFERENCE) return
        val pocet = s.refs.count { it.image != null }
        if (pocet == 0) return
        val chybejici = (1..pocet).map { "<Picture $it>" }.filterNot { s.prompt.contains(it) }
        if (chybejici.isEmpty()) return
        val prefix = chybejici.joinToString(" ")
        val novy = if (s.prompt.isBlank()) "$prefix " else "$prefix ${s.prompt}"
        updateAio { it.copy(prompt = novy) }
    }

    fun setAioPrompt(text: String) = updateAio { it.copy(prompt = text) }

    fun setAioSeconds(seconds: Float) = updateAio { it.copy(seconds = seconds) }

    fun setAioUseLastFrame(on: Boolean) = updateAio { it.copy(useLastFrame = on) }

    fun setAioRefVideoAudio(on: Boolean) = updateAio { it.copy(refVideoAudio = on) }

    fun setAioUpscaler(u: Upscaler) = updateAio { it.copy(upscaler = u) }

    fun setAioUpscaleResolution(px: Int) = updateAio { it.copy(upscaleResolution = px) }

    fun setAioUpscaleMultiplier(x: Int) = updateAio { it.copy(upscaleMultiplier = x) }

    fun setAioSheetPanels(panels: Int) = updateAio { it.copy(sheetPanels = panels) }

    fun setAioSheetPhotoreal(on: Boolean) = updateAio { it.copy(sheetPhotoreal = on) }

    fun addAioRef() = updateAio { s ->
        if (!s.canAddRef) s
        else s.copy(refs = s.refs + AioSlot(key = (s.refs.maxOfOrNull { it.key } ?: 0) + 1))
    }

    fun removeAioRef(key: Int) = updateAio { s ->
        runCatching { aioStore.imageFile("ref", key).delete() }
        val left = s.refs.filterNot { it.key == key }
        s.copy(refs = left.ifEmpty { listOf(AioSlot(key = 1)) })
    }

    fun addAioKey() = updateAio { s ->
        if (!s.canAddKey) s
        else {
            // Nový klíčový snímek sedne na konec videa – tam ho lidé chtějí
            // nejčastěji a nepřekryje se s tím, co už je nastavené.
            val posice = (s.keys.maxOfOrNull { it.position } ?: 1)
            s.copy(
                keys = s.keys + AioSlot(
                    key = (s.keys.maxOfOrNull { it.key } ?: 0) + 1,
                    position = minOf(s.frames, posice + s.frames / 2),
                )
            )
        }
    }

    fun removeAioKey(key: Int) = updateAio { s ->
        runCatching { aioStore.imageFile("key", key).delete() }
        val left = s.keys.filterNot { it.key == key }
        s.copy(keys = left.ifEmpty { listOf(AioSlot(key = 1)) })
    }

    fun setAioKeyPosition(key: Int, position: Int) = updateAio { s ->
        s.copy(keys = s.keys.map {
            if (it.key == key) it.copy(position = position.coerceIn(1, s.frames)) else it
        })
    }

    /**
     * Pošle hotový obrázek rovnou do All in One → „Z obrázku" a přepne kartu —
     * třetí rameno rozcestníku na výsledku vedle Úpravy a Zvětšení: obrázek
     * se dá jedním klepnutím rozhýbat do videa.
     */
    fun posliDoRozhybani(item: VideoItem) {
        viewModelScope.launch {
            val zdroj = item.file(getApplication())
            val target = aioStore.imageFile("first", 1)
            val thumb = withContext(Dispatchers.IO) {
                ImageUtils.importToApp(getApplication(), Uri.fromFile(zdroj), target)
            } ?: return@launch
            updateAio {
                it.copy(
                    mode = AioMode.IMAGE,
                    first = it.first.copy(image = target, thumb = thumb),
                )
            }
            hlidejProfilKCeste()
            setMode(Mode.ALLINONE)
            selectTab(Tab.CREATE)
        }
    }

    /**
     * Obrázek do slotu. `druh` je „first", „last", „ref" nebo „key" – podle něj
     * se pozná soubor na disku, takže se sloty navzájem nepřepisují.
     */
    fun pickAioImage(druh: String, key: Int, uri: Uri?) {
        if (uri == null) return
        viewModelScope.launch {
            val target = aioStore.imageFile(druh, key)
            val thumb = withContext(Dispatchers.IO) {
                ImageUtils.importToApp(getApplication(), uri, target)
            } ?: return@launch
            updateAio { s ->
                when (druh) {
                    "first" -> s.copy(first = s.first.copy(image = target, thumb = thumb))
                    "last" -> s.copy(
                        last = s.last.copy(image = target, thumb = thumb),
                        useLastFrame = true,
                    )
                    "ref" -> s.copy(refs = s.refs.map {
                        if (it.key == key) it.copy(image = target, thumb = thumb) else it
                    })
                    else -> s.copy(keys = s.keys.map {
                        if (it.key == key) it.copy(image = target, thumb = thumb) else it
                    })
                }
            }
            // Nová reference = rovnou i její značka v popisu.
            if (druh == "ref") doplnReferencniZnacky()
        }
    }

    fun clearAioImage(druh: String, key: Int) {
        runCatching { aioStore.imageFile(druh, key).delete() }
        updateAio { s ->
            when (druh) {
                "first" -> s.copy(first = s.first.copy(image = null, thumb = null))
                "last" -> s.copy(last = s.last.copy(image = null, thumb = null))
                "ref" -> s.copy(refs = s.refs.map {
                    if (it.key == key) it.copy(image = null, thumb = null) else it
                })
                else -> s.copy(keys = s.keys.map {
                    if (it.key == key) it.copy(image = null, thumb = null) else it
                })
            }
        }
    }

    /** Video do karty: „source" (prodloužit / zvětšit) nebo „refvideo" (reference). */
    fun pickAioVideo(druh: String, uri: Uri?) {
        if (uri == null) return
        viewModelScope.launch {
            val file = importMedia(uri, "aio_$druh") ?: return@launch
            updateAio { s ->
                if (druh == "refvideo") s.copy(refVideo = file) else s.copy(sourceVideo = file)
            }
        }
    }

    fun clearAioVideo(druh: String) = updateAio { s ->
        if (druh == "refvideo") {
            s.refVideo?.delete(); s.copy(refVideo = null)
        } else {
            s.sourceVideo?.delete(); s.copy(sourceVideo = null)
        }
    }

    // ------------------------------------------------------------- nastavení

    fun setServer(url: String) { _server.value = url }

    fun saveServer() {
        val normalized = AppSettings.normalizeUrl(_server.value)
        settings.serverUrl = normalized
        _server.value = settings.serverUrl
        _serverConfigured.value = settings.serverConfigured
    }

    /**
     * Odchod z úvodní obrazovky — adresa se uloží a appka se otevře. Test
     * spojení je zvlášť ([testServer]); pustit dál se dá i bez něj, třeba
     * když počítač zrovna neběží.
     */
    fun finishOnboarding() {
        saveServer()
    }

    fun testServer() {
        saveServer()
        _check.value = ServerCheck(checking = true)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { ComfyClient(settings.serverUrl).probe() }
            }
            _check.value = result.fold(
                onSuccess = { ServerCheck(false, true, it) },
                onFailure = {
                    ServerCheck(
                        false, false,
                        "Server neodpovídá.\n\nZkontroluj, že počítač běží, ComfyUI je " +
                            "spuštěné s parametrem --listen 0.0.0.0 a že jsi na stejné síti " +
                            "nebo VPN (např. Tailscale). Náběh ComfyUI po zapnutí počítače " +
                            "trvá i pár minut – chvíli počkej a zkus to znovu."
                    )
                }
            )
        }
    }

    // ------------------------------------------------------- kontrola serveru

    private val _audit = MutableStateFlow<AuditState>(AuditState.Idle)
    val audit: StateFlow<AuditState> = _audit.asStateFlow()

    /**
     * Porovná předlohy workflow z APK s tím, co server skutečně nabízí:
     * neznámé třídy uzlů = chybějící custom nody, hodnoty výběrových vstupů
     * mimo nabídku = chybějící modely/LoRA. Nic se negeneruje, jen se čte
     * `/object_info` po jednotlivých třídách.
     */
    fun runServerAudit() {
        if (_audit.value is AuditState.Running) return
        _audit.value = AuditState.Running
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val res = getApplication<android.app.Application>().resources
                    val templates = listOf(
                        R.raw.workflow_h3_ultra,
                        R.raw.workflow_krea2_edit,
                        R.raw.workflow_seedvr2_upscale,
                        R.raw.workflow_zimage_t2i,
                        R.raw.workflow_ace_music,
                        R.raw.workflow_qwen_restore,
                        R.raw.workflow_ace_faceswap,
                    ).map { id ->
                        res.openRawResource(id).bufferedReader().use { it.readText() }
                    }
                    cz.promptlab.h3video.comfy.ServerAudit.run(
                        ComfyClient(settings.serverUrl), templates
                    )
                }
            }
            _audit.value = result.fold(
                onSuccess = { AuditState.Done(it) },
                onFailure = {
                    AuditState.Failed(
                        "Kontrola se nedokončila — server neodpovídá. " +
                            "Zkontroluj spojení tlačítkem výš a zkus to znovu."
                    )
                }
            )
        }
    }

    // ------------------------------------------------------- úprava obrázku

    private val editStore = ImageEditStore(app)

    private val _edit = MutableStateFlow(ImageEditScene())
    val edit: StateFlow<ImageEditScene> = _edit.asStateFlow()

    init {
        viewModelScope.launch {
            val restored = withContext(Dispatchers.IO) { editStore.load() }
            if (restored.source != null || restored.prompt.isNotBlank()) _edit.value = restored
        }
    }

    private fun updateEdit(block: (ImageEditScene) -> ImageEditScene) {
        val next = block(_edit.value)
        _edit.value = next
        editStore.save(next)
    }

    fun setEditPrompt(text: String) = updateEdit { it.copy(prompt = text) }

    fun setEditRefBoost(v: Float) = updateEdit { it.copy(refBoost = v) }

    fun setEditGrounding(px: Int) = updateEdit { it.copy(groundingPx = px) }

    fun setEditMegapixels(mp: Float) = updateEdit { it.copy(megapixels = mp) }

    fun setEditAspect(a: cz.promptlab.h3video.data.Aspect) = updateEdit { it.copy(aspect = a) }

    /** `druh` je "source" (upravovaná fotka) nebo "person" (vkládaná osoba). */
    fun pickEditImage(druh: String, uri: Uri?) {
        if (uri == null) return
        viewModelScope.launch {
            val target = editStore.imageFile(druh)
            val thumb = withContext(Dispatchers.IO) {
                ImageUtils.importToApp(getApplication(), uri, target)
            } ?: return@launch
            updateEdit {
                if (druh == "person") it.copy(person = target, personThumb = thumb)
                else it.copy(source = target, thumb = thumb)
            }
        }
    }

    /**
     * Pošle hotový obrázek (typicky z karty Obrázek) rovnou do karty Úprava —
     * naimportuje soubor jako upravovanou fotku a přepne kartu. Spolu s
     * [posliDoZvetseni] drží celý řetěz Obrázek → Úprava → Zvětšit v appce.
     */
    fun posliDoUpravy(item: VideoItem) {
        viewModelScope.launch {
            val zdroj = item.file(getApplication())
            val target = editStore.imageFile("source")
            val thumb = withContext(Dispatchers.IO) {
                ImageUtils.importToApp(getApplication(), Uri.fromFile(zdroj), target)
            } ?: return@launch
            updateEdit { it.copy(source = target, thumb = thumb) }
            setMode(Mode.EDIT)
            selectTab(Tab.CREATE)
        }
    }

    fun clearEditImage(druh: String) {
        runCatching { editStore.imageFile(druh).delete() }
        updateEdit {
            if (druh == "person") it.copy(person = null, personThumb = null)
            else it.copy(source = null, thumb = null)
        }
    }

    // ---------------------------------------------------------------- hudba

    private val musicStore = MusicStore(app)

    private val _music = MutableStateFlow(MusicScene())
    val music: StateFlow<MusicScene> = _music.asStateFlow()

    init {
        viewModelScope.launch {
            val restored = withContext(Dispatchers.IO) { musicStore.load() }
            if (restored != MusicScene()) _music.value = restored
        }
    }

    private fun updateMusic(block: (MusicScene) -> MusicScene) {
        val next = block(_music.value)
        _music.value = next
        musicStore.save(next)
    }

    fun setMusicStyl(v: String) = updateMusic { it.copy(styl = v) }
    fun setMusicText(v: String) = updateMusic { it.copy(text = v) }
    fun setMusicSeconds(v: Int) = updateMusic {
        it.copy(seconds = v.coerceIn(MusicScene.MIN_SECONDS, MusicScene.MAX_SECONDS))
    }
    fun setMusicLanguage(v: String) = updateMusic { it.copy(language = v) }
    fun setMusicBpm(v: Int) = updateMusic { it.copy(bpm = v.coerceIn(10, 300)) }
    fun setMusicKeyscale(v: String) = updateMusic { it.copy(keyscale = v) }

    // ---------------------------------------------------------- oprava fotky

    private val restoreStore = RestoreStore(app)

    private val _restore = MutableStateFlow(RestoreScene())
    val restore: StateFlow<RestoreScene> = _restore.asStateFlow()

    init {
        viewModelScope.launch {
            val restored = withContext(Dispatchers.IO) {
                val s = restoreStore.load()
                s.source?.let { s.copy(thumb = ImageUtils.loadFileThumb(it)) } ?: s
            }
            if (restored.source != null) _restore.value = restored
        }
    }

    /** Fotka k opravě se kopíruje bajt po bajtu — kvalita je tu všechno. */
    fun pickRestoreImage(uri: Uri?) {
        if (uri == null) return
        viewModelScope.launch {
            val vysledek = withContext(Dispatchers.IO) {
                runCatching {
                    val resolver = getApplication<android.app.Application>().contentResolver
                    val ext = (android.webkit.MimeTypeMap.getSingleton()
                        .getExtensionFromMimeType(resolver.getType(uri)) ?: "png").lowercase()
                    restoreStore.dir().listFiles()?.forEach { it.delete() }
                    if (ext in setOf("png", "jpg", "jpeg", "webp")) {
                        val target = File(restoreStore.dir(), "zdroj.$ext")
                        resolver.openInputStream(uri)?.use { input ->
                            target.outputStream().use { input.copyTo(it) }
                        } ?: return@runCatching null
                        target.takeIf { it.length() > 0 }
                    } else {
                        // HEIC ze Samsungu (a jiné exotické formáty) server
                        // nepřečte – překóduje se na JPEG včetně EXIF otočení.
                        val bmp = ImageUtils.loadUpright(getApplication(), uri, 4096)
                            ?: return@runCatching null
                        val target = File(restoreStore.dir(), "zdroj.jpg")
                        target.outputStream().use {
                            bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, it)
                        }
                        target
                    }
                }.getOrNull()
            } ?: return@launch
            val thumb = withContext(Dispatchers.IO) { ImageUtils.loadFileThumb(vysledek) }
            _restore.value = RestoreScene(source = vysledek, thumb = thumb)
            restoreStore.save(_restore.value)
        }
    }

    fun clearRestoreImage() {
        runCatching { restoreStore.dir().listFiles()?.forEach { it.delete() } }
        _restore.value = RestoreScene()
        restoreStore.save(_restore.value)
    }

    // --------------------------------------------------------- výměna tváře

    private val swapStore = FaceSwapStore(app)

    private val _swap = MutableStateFlow(FaceSwapScene())
    val swap: StateFlow<FaceSwapScene> = _swap.asStateFlow()

    init {
        viewModelScope.launch {
            // Masky z verzí ≤2.88 žily v alfa kanálu fotky — store je neuznává
            // (nový soubor maska.png neexistuje) a appka si řekne o novou.
            val restored = withContext(Dispatchers.IO) {
                val s = swapStore.load()
                s.copy(
                    targetThumb = s.target?.let { ImageUtils.loadFileThumb(it) },
                    faceThumb = s.face?.let { ImageUtils.loadFileThumb(it) },
                )
            }
            if (restored.target != null || restored.face != null) _swap.value = restored
        }
    }

    private fun updateSwap(block: (FaceSwapScene) -> FaceSwapScene) {
        val next = block(_swap.value)
        _swap.value = next
        swapStore.save(next)
    }

    /**
     * Cílová fotka se ukládá jako PNG (kvůli masce v alfa kanálu) a delší
     * hrana se omezuje, aby se dala v telefonu malovat maska bez došlé paměti.
     * ACE++ si stejně vyřezává oblast tváře na 1024 px, o kvalitu se nepřijde.
     */
    fun pickSwapTarget(uri: Uri?) {
        if (uri == null) return
        viewModelScope.launch {
            val target = withContext(Dispatchers.IO) {
                runCatching {
                    // loadUpright dekóduje rovnou podvzorkované (108Mpx fotka by
                    // v plném rozlišení spolkla stovky MB) a srovná EXIF otočení –
                    // fotka z foťáku by jinak na server odešla naležato.
                    val bmp = ImageUtils.loadUpright(getApplication(), uri, 2560)
                        ?: return@runCatching null
                    val f = swapStore.targetFile()
                    f.outputStream().use {
                        bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                    }
                    f
                }.getOrNull()
            } ?: return@launch
            val thumb = withContext(Dispatchers.IO) { ImageUtils.loadFileThumb(target) }
            // Nová fotka = stará maska už nesedí, maže se.
            withContext(Dispatchers.IO) { runCatching { swapStore.maskFile().delete() } }
            updateSwap { it.copy(target = target, targetThumb = thumb, mask = null) }
        }
    }

    fun pickSwapFace(uri: Uri?) {
        if (uri == null) return
        viewModelScope.launch {
            val target = swapStore.faceFile()
            val thumb = withContext(Dispatchers.IO) {
                ImageUtils.importToApp(getApplication(), uri, target)
            } ?: return@launch
            updateSwap { it.copy(face = target, faceThumb = thumb) }
        }
    }

    fun clearSwap(druh: String) {
        if (druh == "face") {
            runCatching { swapStore.faceFile().delete() }
            updateSwap { it.copy(face = null, faceThumb = null) }
        } else {
            runCatching { swapStore.targetFile().delete() }
            runCatching { swapStore.maskFile().delete() }
            updateSwap { it.copy(target = null, targetThumb = null, mask = null) }
        }
    }

    /**
     * Uloží masku štětce jako samostatný černobílý PNG (bílá = vyměnit).
     * Fotka zůstává netknutá — gumování do alfy dřív černilo její pixely
     * a černé okraje dělaly tmavý šev kolem vyměněné tváře.
     */
    fun ulozSwapMasku(maska: android.graphics.Bitmap) {
        viewModelScope.launch {
            val f = withContext(Dispatchers.IO) {
                runCatching {
                    val soubor = swapStore.maskFile()
                    soubor.outputStream().use {
                        maska.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                    }
                    soubor.takeIf { it.length() > 0 }
                }.getOrNull()
            } ?: return@launch
            updateSwap { it.copy(mask = f) }
        }
    }

    // ------------------------------------------------------------ domalovat

    private val inpaintStore = InpaintStore(app)

    private val _inpaint = MutableStateFlow(InpaintScene())
    val inpaint: StateFlow<InpaintScene> = _inpaint.asStateFlow()

    init {
        viewModelScope.launch {
            val restored = withContext(Dispatchers.IO) {
                val s = inpaintStore.load()
                s.copy(thumb = s.source?.let { ImageUtils.loadFileThumb(it) })
            }
            if (restored.source != null || restored.prompt.isNotBlank()) _inpaint.value = restored
        }
    }

    private fun updateInpaint(block: (InpaintScene) -> InpaintScene) {
        val next = block(_inpaint.value)
        _inpaint.value = next
        inpaintStore.save(next)
    }

    fun setInpaintPrompt(text: String) = updateInpaint { it.copy(prompt = text) }
    fun setInpaintModel(model: InpaintModel) = updateInpaint {
        // LoRA patří vždycky k jedné rodině modelů — po přepnutí modelu už
        // vybraná sedět nemusí, tak se zahodí (jinak by běh spadl na
        // nesedící tvary vah).
        it.copy(model = model, lora = "")
    }
    fun setInpaintLora(lora: String) = updateInpaint { it.copy(lora = lora) }
    fun setInpaintLoraSila(v: Float) = updateInpaint { it.copy(loraSila = v) }
    fun setInpaintSila(v: Float) = updateInpaint { it.copy(sila = v) }

    /**
     * Nabídka LoRA pro domalování, čtená ze serveru. Filtruje se podle rodiny
     * modelu: adaptér pro FLUX.1 se na FLUX.2 Klein nenasadí (jiné tvary vah)
     * a naopak, takže míchat je nemá smysl ani nabízet.
     */
    private val _inpaintLoras = MutableStateFlow<List<String>>(emptyList())

    /**
     * Musí být vidět jako stav: seznam dorazí ze serveru až za chvíli po
     * otevření karty a obrazovka se na něj musí překreslit. (Čtení
     * `_inpaintLoras.value` přímo v composable by nechalo nabídku navždy
     * prázdnou — stejná past jako u validace tlačítka Generovat.)
     */
    val inpaintLoras: StateFlow<List<String>> = _inpaintLoras.asStateFlow()

    /** Filtr rodiny modelu žije v [loryProModel], ať jde ověřit testem. */
    fun inpaintLoraNabidka(model: InpaintModel, vse: List<String>): List<String> =
        cz.promptlab.h3video.data.loryProModel(model, vse)

    // ------------------------------------------------------- paměť grafiky

    /** Hláška o paměti grafiky pro Nastavení (prázdné = ještě se neptalo). */
    private val _vramStav = MutableStateFlow("")
    val vramStav: StateFlow<String> = _vramStav.asStateFlow()

    private val _vramPracuje = MutableStateFlow(false)
    val vramPracuje: StateFlow<Boolean> = _vramPracuje.asStateFlow()

    /**
     * Zjistí volnou paměť grafiky a nabídne její uvolnění. Uvolňuje se jen to,
     * co drží ComfyUI — cizí programy appka nevypíná (a vypínat nesmí).
     */
    fun zjistiVram(uvolnit: Boolean = false) {
        if (_vramPracuje.value) return
        _vramPracuje.value = true
        viewModelScope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    val client = ComfyClient(settings.serverUrl)
                    if (uvolnit) {
                        client.freeMemory()
                        kotlinx.coroutines.delay(1200)
                    }
                    val (volno, celkem) = client.vram()
                        ?: return@runCatching t("Server o paměti grafiky nic neřekl.")
                    val gb = { b: Long -> b.toDouble() / (1024 * 1024 * 1024) }
                    val podil = volno.toDouble() / celkem
                    val zaklad = t("Volných %.1f z %.1f GB.")
                        .format(gb(volno), gb(celkem))
                    zaklad + " " + when {
                        podil >= 0.75 -> t("Grafika je volná, generování poběží naplno.")
                        podil >= 0.55 -> t("Na obrázky to stačí; u videa se může model dohrávat z RAM.")
                        else -> t("Málo místa — něco jiného na počítači grafiku drží. " +
                            "Zavři hru nebo prohlížeč a zkus uvolnit znovu.")
                    }
                }.getOrElse { t("Nepodařilo se zeptat serveru na paměť grafiky.") }
            }
            _vramStav.value = text
            _vramPracuje.value = false
        }
    }

    fun refreshInpaintLoras() {
        if (_inpaintLoras.value.isNotEmpty()) return
        viewModelScope.launch {
            val nalezene = withContext(Dispatchers.IO) {
                runCatching { ComfyClient(settings.serverUrl).loraNames() }
                    .getOrDefault(emptyList())
            }
            if (nalezene.isNotEmpty()) _inpaintLoras.value = nalezene
        }
    }

    /**
     * Fotka se ukládá jako PNG a delší hrana se omezuje, aby se dala v telefonu
     * malovat maska bez došlé paměti. Model stejně pracuje na výřezu kolem
     * masky, takže o detail se tím nepřijde.
     */
    fun pickInpaintImage(uri: Uri?) {
        if (uri == null) return
        viewModelScope.launch {
            val source = withContext(Dispatchers.IO) {
                runCatching {
                    val bmp = ImageUtils.loadUpright(getApplication(), uri, 2560)
                        ?: return@runCatching null
                    val f = inpaintStore.sourceFile()
                    f.outputStream().use {
                        bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                    }
                    f
                }.getOrNull()
            } ?: return@launch
            val thumb = withContext(Dispatchers.IO) { ImageUtils.loadFileThumb(source) }
            // Nová fotka = stará maska už nesedí, maže se.
            withContext(Dispatchers.IO) { runCatching { inpaintStore.maskFile().delete() } }
            updateInpaint { it.copy(source = source, thumb = thumb, mask = null) }
        }
    }

    fun clearInpaintImage() {
        runCatching { inpaintStore.sourceFile().delete() }
        runCatching { inpaintStore.maskFile().delete() }
        updateInpaint { it.copy(source = null, thumb = null, mask = null) }
    }

    /** Maska štětce jako samostatný černobílý PNG (bílá = přemalovat). */
    fun ulozInpaintMasku(maska: android.graphics.Bitmap) {
        viewModelScope.launch {
            val f = withContext(Dispatchers.IO) {
                runCatching {
                    val soubor = inpaintStore.maskFile()
                    soubor.outputStream().use {
                        maska.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                    }
                    soubor.takeIf { it.length() > 0 }
                }.getOrNull()
            } ?: return@launch
            updateInpaint { it.copy(mask = f) }
        }
    }

    // -------------------------------------------------------------- zvětšit

    private val upscaleStore = UpscaleStore(app)

    private val _upscale = MutableStateFlow(UpscaleScene())
    val upscale: StateFlow<UpscaleScene> = _upscale.asStateFlow()

    init {
        viewModelScope.launch {
            val restored = withContext(Dispatchers.IO) { upscaleStore.load() }
            if (restored.source != null) _upscale.value = restored
        }
    }

    private fun updateUpscale(block: (UpscaleScene) -> UpscaleScene) {
        val next = block(_upscale.value)
        _upscale.value = next
        upscaleStore.save(next)
    }

    fun setUpscaleGrid(grid: String) = updateUpscale { it.copy(grid = grid) }

    /**
     * Fotka ke zvětšení se kopíruje BAJT PO BAJTU — žádné zmenšení na 2048 px
     * ani překódování do JPEG jako u referencí. Zmenšovat vstup upscaleru by
     * byl protimluv.
     */
    fun pickUpscaleImage(uri: Uri?) {
        if (uri == null) return
        viewModelScope.launch {
            val vysledek = withContext(Dispatchers.IO) {
                runCatching {
                    val resolver = getApplication<android.app.Application>().contentResolver
                    val ext = (android.webkit.MimeTypeMap.getSingleton()
                        .getExtensionFromMimeType(resolver.getType(uri)) ?: "png").lowercase()
                    upscaleStore.dir().listFiles()?.forEach { it.delete() }
                    if (ext in setOf("png", "jpg", "jpeg", "webp")) {
                        val target = File(upscaleStore.dir(), "zdroj.$ext")
                        resolver.openInputStream(uri)?.use { input ->
                            target.outputStream().use { input.copyTo(it) }
                        } ?: return@runCatching null
                        target.takeIf { it.length() > 0 }
                    } else {
                        // HEIC a spol. – server by je nepřečetl, překóduje se
                        // na JPEG včetně EXIF otočení.
                        val bmp = ImageUtils.loadUpright(getApplication(), uri, 4096)
                            ?: return@runCatching null
                        val target = File(upscaleStore.dir(), "zdroj.jpg")
                        target.outputStream().use {
                            bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, it)
                        }
                        target
                    }
                }.getOrNull()
            } ?: return@launch
            val thumb = withContext(Dispatchers.IO) { ImageUtils.loadFileThumb(vysledek) }
            updateUpscale { it.copy(source = vysledek, thumb = thumb) }
        }
    }

    fun clearUpscaleImage() {
        runCatching { upscaleStore.dir().listFiles()?.forEach { it.delete() } }
        updateUpscale { it.copy(source = null, thumb = null) }
    }

    /**
     * Pošle hotový obrázek (typicky z karty Úprava) rovnou do karty Zvětšit —
     * zkopíruje soubor beze změny a přepne kartu. Odsud vede tlačítko
     * „Zvětšit" na obrazovce výsledku.
     */
    fun posliDoZvetseni(item: VideoItem) {
        viewModelScope.launch {
            val vysledek = withContext(Dispatchers.IO) {
                runCatching {
                    val zdroj = item.file(getApplication())
                    val ext = item.fileName.substringAfterLast('.', "png")
                    upscaleStore.dir().listFiles()?.forEach { it.delete() }
                    val target = File(upscaleStore.dir(), "zdroj.$ext")
                    zdroj.copyTo(target, overwrite = true)
                    target
                }.getOrNull()
            } ?: return@launch
            val thumb = withContext(Dispatchers.IO) { ImageUtils.loadFileThumb(vysledek) }
            updateUpscale { it.copy(source = vysledek, thumb = thumb) }
            setMode(Mode.UPSCALE)
            selectTab(Tab.CREATE)
        }
    }
}
