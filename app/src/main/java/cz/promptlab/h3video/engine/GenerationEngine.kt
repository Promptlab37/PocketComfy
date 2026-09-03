package cz.promptlab.h3video.engine

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.util.Log
import cz.promptlab.h3video.comfy.AioBuilder
import cz.promptlab.h3video.comfy.Krea2Builder
import cz.promptlab.h3video.comfy.AceMusicBuilder
import cz.promptlab.h3video.comfy.FaceSwapBuilder
import cz.promptlab.h3video.comfy.InpaintBuilder
import cz.promptlab.h3video.comfy.RestoreBuilder
import cz.promptlab.h3video.comfy.SeedVr2Builder
import cz.promptlab.h3video.comfy.ZImageBuilder
import cz.promptlab.h3video.comfy.ComfyClient
import cz.promptlab.h3video.comfy.ComfyException
import cz.promptlab.h3video.comfy.Stage
import cz.promptlab.h3video.comfy.WorkflowBuilder
import cz.promptlab.h3video.data.AppSettings
import cz.promptlab.h3video.data.GenParams
import cz.promptlab.h3video.data.HistoryStore
import cz.promptlab.h3video.data.VideoItem
import cz.promptlab.h3video.higgs.HiggsLauncher
import cz.promptlab.h3video.util.MediaSaver
import cz.promptlab.h3video.util.Preview
import cz.promptlab.h3video.util.PreviewDecoder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONObject
import java.io.File
import java.util.UUID
import kotlin.math.max
import kotlin.random.Random

sealed interface GenState {
    data object Idle : GenState

    /**
     * @Immutable je tu schválně: stav nese Bitmap, který Compose sám o sobě
     * považuje za nestabilní, a bez tohoto označení by se obrazovka průběhu
     * překreslovala celá při každé změně, i když se nic viditelného nezměnilo.
     * Bitmapy uvnitř nikdy nepřepisujeme, vždy se nahradí novou instancí.
     */
    @androidx.compose.runtime.Immutable
    data class Running(
        val promptId: String?,
        val stage: Stage,
        val overall: Float,
        val step: Int = 0,
        val totalSteps: Int = 0,
        val queuePosition: Int = -1,
        val startedAt: Long,
        val etaSeconds: Int? = null,
        /** Naměřené tempo vzorkování v sekundách na krok, 0 = ještě neznámé. */
        val secondsPerStep: Double = 0.0,
        /** Vzorkovací uzel už běží, ale model se teprve nahrává do karty. */
        val preparing: Boolean = false,
        /** Přenos hotového videa: přeneseno / celkem v bajtech (0 = neprobíhá). */
        val transferDone: Long = 0L,
        val transferTotal: Long = 0L,
        val preview: Preview? = null,
        /** Klidná poznámka (výpadek sítě, běh na pozadí) – nikdy to není chyba. */
        val note: String? = null,
        val offline: Boolean = false,
        val label: String = "",
        /** Beh vyrabi obrazek, ne video - texty prubehu se podle toho meni. */
        val isImage: Boolean = false,
        /** Beh je zvetsovani (SeedVR2) - jeste jine texty nez uprava. */
        val isUpscale: Boolean = false,
        /** Beh je novy obrazek z textu (Z-Image) - "Generuji", ne "Upravuji". */
        val isT2i: Boolean = false,
        /** Beh sklada hudbu (ACE-Step) - vysledkem je MP3, texty "Skladam". */
        val isMusic: Boolean = false,
        /** Beh opravuje starou fotku (Qwen 2511) - texty "Opravuji". */
        val isRestore: Boolean = false,
        /** Beh meni tvar (ACE++) - texty "Menim tvar". */
        val isSwap: Boolean = false,
        /** Beh domalovava do masky (inpaint) - texty "Domalovavam". */
        val isInpaint: Boolean = false,
    ) : GenState

    data class Done(
        val item: VideoItem,
        /** Co k běhu řekly samotné uzly – jinak to skončí jen v logu na počítači. */
        val warnings: List<String> = emptyList(),
    ) : GenState

    data class Failed(val message: String, val canRetryDownload: Boolean = false) : GenState
}

/**
 * Řídí celý běh: nahrání referencí -> zařazení do fronty -> sledování -> stažení.
 *
 * Pravidlo, které se tu drží: chyba sítě NIKDY neznamená selhání úlohy. Generuje
 * se na počítači, takže když telefon usne nebo vypadne signál, běh pokračuje dál.
 * Selháním je jen skutečná chyba ohlášená serverem.
 */
object GenerationEngine {

    private const val TAG = "H3Engine"
    private const val MAX_SUBMIT_ATTEMPTS = 10
    private const val MAX_DOWNLOAD_ATTEMPTS = 25
    /** Po této době se v průběhu objeví klidné „trvá to dlouho" – hlídá se dál. */
    private const val WATCH_TIMEOUT_MS = 90L * 60_000L
    /** Úplný strop; víc než 6 hodin žádný běh netrvá a smyčka nemá viset navěky. */
    private const val WATCH_HARD_LIMIT_MS = 6L * 3_600_000L
    /** Kolikrát po sobě musí server POTVRDIT, že úlohu nezná, než to vzdáme. */
    private const val LOST_POLLS_LIMIT = 5
    /** Jak dlouho se čeká, než server po restartu počítače naskočí (6 minut). */
    private const val SERVER_WAIT_SECONDS = 360
    private const val TICK_MS = 1000L
    /** Jak často se ptáme /history a fronty – jednou za tři sekundy. */
    private const val POLL_EVERY_TICKS = 3

    private lateinit var app: Context
    private lateinit var settings: AppSettings
    private lateinit var history: HistoryStore

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    // Zapisují ho OkHttp callback vlákna (onFailure/onClosed) i koroutiny —
    // bez @Volatile by watch po výpadku mohl trvale vidět starou hodnotu
    // a spojení už nikdy neobnovit.
    @Volatile private var socket: WebSocket? = null

    private val _state = MutableStateFlow<GenState>(GenState.Idle)
    val state: StateFlow<GenState> = _state.asStateFlow()

    private val clientId = UUID.randomUUID().toString()

    // živý stav běhu
    @Volatile private var currentNode: String? = null
    @Volatile private var nodeStartedAt: Long = 0L

    /**
     * Kroky hlásí ComfyUI po WebSocketu jinak, než kolik jich uživatel zadal:
     * u MiniMax H3 je jich dvojnásobek (obraz a zvuk se počítají zvlášť) a jiné
     * uzly hlásí úplně jiné jednotky (skládání videa posílá počet snímků).
     * Proto se drží zvlášť: co si přál uživatel a co posílá server.
     */
    @Volatile private var plannedSteps: Int = 0
    @Volatile private var srvStep: Int = 0
    @Volatile private var srvMax: Int = 0
    @Volatile private var queuePos: Int = -1
    @Volatile private var startedAt: Long = 0L
    @Volatile private var preview: Preview? = null
    @Volatile private var serverFinished: Boolean = false
    @Volatile private var serverError: String? = null
    @Volatile private var interrupted: Boolean = false
    @Volatile private var lastContactAt: Long = 0L
    @Volatile private var label: String = ""
    @Volatile private var transferDone: Long = 0L
    @Volatile private var transferTotal: Long = 0L
    @Volatile private var transferStartedAt: Long = 0L
    /** Kde v logu ComfyUI náš běh začal – od té značky se čtou varování uzlů. */
    @Volatile private var logSince: String? = null

    /**
     * Běží úloha z karty All in One? Její graf má vlastní čísla uzlů (šablona
     * balíku), takže se podle toho vybírá tabulka fází i uzel vzorkování.
     */
    @Volatile private var aioRun: Boolean = false

    /**
     * Běží úprava obrázku (Krea 2)? Má vlastní workflow i vlastní tabulku fází
     * a na rozdíl od ostatních karet nevrací video, ale PNG.
     */
    @Volatile private var editRun: Boolean = false

    /** Běží zvětšování (SeedVR2 gigapixel)? Vlastní workflow z APK, výsledek PNG. */
    @Volatile private var upscaleRun: Boolean = false

    /** Běží nový obrázek z textu (Z-Image Turbo)? Vlastní workflow z APK, výsledek PNG. */
    @Volatile private var t2iRun: Boolean = false

    /** Běží hudba (ACE-Step 1.5)? Vlastní workflow z APK, výsledek MP3. */
    @Volatile private var musicRun: Boolean = false

    /** Běží oprava fotky (Qwen 2511)? Vlastní workflow z APK, výsledek PNG. */
    @Volatile private var restoreRun: Boolean = false

    /** Běží výměna tváře (ACE++)? Vlastní workflow z APK, výsledek PNG. */
    @Volatile private var swapRun: Boolean = false

    /** Běží domalování do masky (Klein / Flux Fill)? Vlastní workflow z APK, výsledek PNG. */
    @Volatile private var inpaintRun: Boolean = false

    /**
     * Mapa „číslo uzlu → třída" z odeslaného grafu. U karty All in One se podle
     * ní poznávají fáze: čísla uzlů se mezi šablonami liší (uzel 3 je u SeedVR2
     * načtení modelu, u RTX rovnou celé zvětšení), třída je jednoznačná.
     */
    @Volatile private var nodeClasses: Map<String, String> = emptyMap()

    private fun stageOf(node: String?): Stage = when {
        restoreRun -> RestoreBuilder.stageForClass(nodeClasses[node])
        swapRun -> FaceSwapBuilder.stageForClass(nodeClasses[node])
        inpaintRun -> InpaintBuilder.stageForClass(nodeClasses[node])
        musicRun -> AceMusicBuilder.stageForClass(nodeClasses[node])
        t2iRun -> ZImageBuilder.stageForClass(nodeClasses[node])
        upscaleRun -> SeedVr2Builder.stageForClass(nodeClasses[node])
        editRun -> Krea2Builder.stageForClass(nodeClasses[node])
        aioRun -> AioBuilder.stageForClass(nodeClasses[node])
        else -> WorkflowBuilder.stageFor(node)
    }

    private fun rangeOf(node: String?): Pair<Float, Float> = when {
        restoreRun -> RestoreBuilder.rangeForClass(nodeClasses[node])
        swapRun -> FaceSwapBuilder.rangeForClass(nodeClasses[node])
        inpaintRun -> InpaintBuilder.rangeForClass(nodeClasses[node])
        musicRun -> AceMusicBuilder.rangeForClass(nodeClasses[node])
        t2iRun -> ZImageBuilder.rangeForClass(nodeClasses[node])
        upscaleRun -> SeedVr2Builder.rangeForClass(nodeClasses[node])
        editRun -> Krea2Builder.rangeForClass(nodeClasses[node])
        aioRun -> AioBuilder.rangeForClass(nodeClasses[node])
        else -> WorkflowBuilder.rangeFor(node)
    }

    /**
     * Hlásí tenhle uzel kroky? Jen u něj má „progress" smysl počítat na kroky.
     * Jediný zdroj pravdy i pro odhad času a „autoritativní" postup v publish() –
     * dřív se tam porovnávalo přímo s N_SAMPLING, které u All in One ani u
     * Časové osy neběží, takže se odhad nikdy neukázal.
     */
    private fun reportsSteps(node: String?): Boolean = when {
        restoreRun -> RestoreBuilder.reportsSteps(nodeClasses[node])
        swapRun -> FaceSwapBuilder.reportsSteps(nodeClasses[node])
        inpaintRun -> InpaintBuilder.reportsSteps(nodeClasses[node])
        musicRun -> AceMusicBuilder.reportsSteps(nodeClasses[node])
        t2iRun -> ZImageBuilder.reportsSteps(nodeClasses[node])
        upscaleRun -> SeedVr2Builder.reportsSteps(nodeClasses[node])
        editRun -> Krea2Builder.reportsSteps(nodeClasses[node])
        aioRun -> AioBuilder.reportsSteps(nodeClasses[node])
        else -> WorkflowBuilder.reportsSteps(node)
    }

    // měření tempa vzorkování – jediný spolehlivý podklad pro odhad zbývajícího času
    @Volatile private var lastSrvStep: Int = 0
    @Volatile private var lastStepAt: Long = 0L
    /** Sekundy na JEDEN krok tak, jak je počítá server. */
    @Volatile private var secPerServerStep: Double = 0.0

    /**
     * Kolik kroků se uživateli ukazuje.
     *
     * `plannedSteps` je počet z předlohy dané karty. Když ho appka nezná
     * (SeedVR2 si dlaždice i kroky řídí sám), je nula a ukazuje se rovnou to,
     * co hlásí server. Dřív se v takovém případě propsal počet kroků
     * z nastavení VIDEA, takže obrazovka průběhu tvrdila „10 kroků" i u karet,
     * které s tím nastavením nemají nic společného.
     */
    private val zobrazenyPocetKroku: Int
        get() = if (plannedSteps > 0) plannedSteps else srvMax

    /** Krok přepočtený na to, co uživatel vidí (1..[zobrazenyPocetKroku]). */
    private val displayedStep: Int
        get() = when {
            srvMax <= 0 -> 0
            plannedSteps > 0 -> Math.ceil(srvStep.toDouble() / srvMax * plannedSteps)
                .toInt().coerceIn(0, plannedSteps)
            else -> srvStep
        }

    /** Tempo přepočtené na zobrazený krok, ať sedí s tím, co vidí. */
    private val displayedSecondsPerStep: Double
        get() = when {
            secPerServerStep <= 0 || srvMax <= 0 -> 0.0
            plannedSteps > 0 -> secPerServerStep * srvMax / plannedSteps
            else -> secPerServerStep
        }

    fun init(context: Context) {
        if (::app.isInitialized) return
        app = context.applicationContext
        settings = AppSettings(app)
        history = HistoryStore(app)
    }

    val isRunning: Boolean get() = _state.value is GenState.Running

    // ------------------------------------------------------------------ start

    fun start(
        params: GenParams,
        images: List<File>,
        /** Dialogy: namluvené repliky v pořadí, v jakém dostanou `<Audio N>`. */
        talkAudios: List<File> = emptyList(),
        /** Časová osa: segmenty pro LSI nody, JSON se skládá až po nahrání snímků. */
        timelineScene: cz.promptlab.h3video.data.TimelineScene? = null,
        /** All in One: šablona se stahuje ze serveru až při běhu. */
        aioScene: cz.promptlab.h3video.data.AioScene? = null,
        /** Úprava obrázku: Krea 2, vlastní workflow z APK, výsledkem je PNG. */
        editScene: cz.promptlab.h3video.data.ImageEditScene? = null,
        /** Zvětšit: SeedVR2 gigapixel, vlastní workflow z APK, výsledkem je PNG. */
        upscaleScene: cz.promptlab.h3video.data.UpscaleScene? = null,
        /** Obrázek: Z-Image Turbo, vlastní workflow z APK, výsledkem je PNG. */
        t2i: Boolean = false,
        /** Hudba: ACE-Step 1.5, vlastní workflow z APK, výsledkem je MP3. */
        musicScene: cz.promptlab.h3video.data.MusicScene? = null,
        /** Oprava fotky: Qwen 2511, vlastní workflow z APK, výsledkem je PNG. */
        restoreScene: cz.promptlab.h3video.data.RestoreScene? = null,
        /** Výměna tváře: ACE++, vlastní workflow z APK, výsledkem je PNG. */
        swapScene: cz.promptlab.h3video.data.FaceSwapScene? = null,
        /** Domalovat: Klein / Flux Fill, vlastní workflow z APK, výsledkem je PNG. */
        inpaintScene: cz.promptlab.h3video.data.InpaintScene? = null,
    ) {
        if (isRunning) return
        job?.cancel()
        resetRun()
        editRun = editScene != null
        upscaleRun = upscaleScene != null
        t2iRun = t2i
        musicRun = musicScene != null
        restoreRun = restoreScene != null
        swapRun = swapScene != null
        inpaintRun = inpaintScene != null
        aioRun = !editRun && !upscaleRun && !t2iRun && !musicRun && !restoreRun && !swapRun &&
            !inpaintRun &&
            (aioScene != null || params.mode == cz.promptlab.h3video.data.Mode.TALK)
        settings.activeAio = aioRun
        settings.activeEdit = editRun
        settings.activeUpscale = upscaleRun
        settings.activeT2i = t2iRun
        settings.activeMusic = musicRun
        settings.activeRestore = restoreRun
        settings.activeSwap = swapRun
        settings.activeInpaint = inpaintRun
        startedAt = System.currentTimeMillis()
        label = if (restoreScene != null) {
            "Oprava fotky"
        } else if (swapScene != null) {
            "Výměna tváře"
        } else if (inpaintScene != null) {
            "Domalovat · " + inpaintScene.model.title
        } else if (musicScene != null) {
            "Hudba · " + musicScene.seconds + " s"
        } else if (t2i) {
            "Obrázek · " + params.aspect.label
        } else if (upscaleScene != null) {
            "Zvětšit · " + upscaleScene.grid
        } else if (editScene != null) {
            params.mode.short + " · " + editScene.resolution.label
        } else {
            (aioScene?.mode?.nazev ?: params.mode.short) + " · " + params.resolution.label +
                " · " + "%.1f".format(aioScene?.let { it.frames / 24f } ?: params.realSeconds) + " s"
        }
        // Službu na popředí nesmí appka odnést pádem, když ji systém odmítne
        // (kvóta dataSync na Androidu 15, start mimo popředí). Bez ní se jen
        // hůř přežívá zamčený telefon; sledování běhu funguje dál.
        runCatching { GenerationService.start(app) }
            .onFailure { Log.w(TAG, "sluzbu na popredi se nepodarilo spustit", it) }

        job = scope.launch {
            runCatching {
                runGeneration(
                    params, images, talkAudios, timelineScene, aioScene, editScene,
                    upscaleScene, t2i, musicScene, restoreScene, swapScene, inpaintScene,
                )
            }
                .onFailure { e ->
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    Log.e(TAG, "generation failed", e)
                    fail((e as? ComfyException)?.userMessage ?: (e.message ?: "Neznámá chyba"))
                }
        }
        // Ať job skončí jakkoli (hotovo, chyba, zrušení uprostřed blokujícího
        // volání), socket nesmí zůstat viset — jinak by jeho guard zablokoval
        // připojení příštího běhu a starý listener by mu sahal do stavu.
        job?.invokeOnCompletion { closeSocket() }
        // Až PO vzniku jobu – publish() zahazuje stavy bez aktivního jobu
        // (ochrana proti vzkříšení po Zrušit) a před launch by úvodní stav
        // nepustil. Stav Running tak naskočí okamžitě, ne až s prvním hlášením
        // zevnitř běhu.
        publish(Stage.UPLOADING, 0.01f)
    }

    /** Znovu se přilepí na rozdělanou úlohu po restartu aplikace. */
    fun resumeIfPending() {
        if (isRunning) return
        val pid = settings.activePromptId ?: return
        job?.cancel()
        job = scope.launch {
            // Napřed se zeptat, jestli server tu úlohu vůbec zná. Zapsané „rozdělané
            // generování" totiž přežije i to, že úloha mezitím zanikla (přerušení,
            // restart ComfyUI) – a pak by se appka při KAŽDÉM startu marně navazovala
            // na něco, co neexistuje, a pokaždé kvůli tomu rozjížděla službu na popředí.
            val client = ComfyClient(settings.serverUrl)
            val known = withContext(Dispatchers.IO) { runCatching { client.promptKnown(pid) }.getOrNull() }
            if (known == false) {
                // Server odpověděl a úlohu nezná: zahodit ji a chovat se jako po startu.
                settings.activePromptId = null
                return@launch
            }
            // null = server neodpovídá (typicky vypnuté ComfyUI). Úloha teoreticky
            // může běžet dál, ale navazovat naslepo při každém startu se nevyplácí:
            // appka se tvářila, že generuje, a budila kvůli tomu službu na popředí.
            // Zapsaná úloha zůstane, takže se na ni dá navázat, až server odpoví.
            if (known == null) return@launch
            resetRun()
            // Fáze se čtou podle toho, jaký graf běží – po restartu appky to jde
            // zjistit jen z uloženého příznaku. Uzly rozdělané úlohy se dají
            // dotáhnout z fronty; když už tam není, ukazatel jede po odhadu.
            editRun = settings.activeEdit
            upscaleRun = settings.activeUpscale
            t2iRun = settings.activeT2i
            musicRun = settings.activeMusic
            restoreRun = settings.activeRestore
            swapRun = settings.activeSwap
            inpaintRun = settings.activeInpaint
            aioRun = !editRun && !upscaleRun && !t2iRun && !musicRun && !restoreRun &&
                !swapRun && !inpaintRun && settings.activeAio
            nodeClasses = if (aioRun || editRun || upscaleRun || t2iRun || musicRun ||
                restoreRun || swapRun || inpaintRun
            ) {
                withContext(Dispatchers.IO) {
                    runCatching {
                        client.queuedGraph(pid)?.let {
                            when {
                                restoreRun -> RestoreBuilder.nodeClasses(it)
                                swapRun -> FaceSwapBuilder.nodeClasses(it)
                                inpaintRun -> InpaintBuilder.nodeClasses(it)
                                musicRun -> AceMusicBuilder.nodeClasses(it)
                                t2iRun -> ZImageBuilder.nodeClasses(it)
                                upscaleRun -> SeedVr2Builder.nodeClasses(it)
                                editRun -> Krea2Builder.nodeClasses(it)
                                else -> AioBuilder.nodeClasses(it)
                            }
                        }
                    }.getOrNull().orEmpty()
                }
            } else emptyMap()
            startedAt = System.currentTimeMillis()
            label = settings.activeLabel
            // Po restartu aplikace ještě nevíme, jak daleko úloha je – nezačínat na
            // vymyšlené třetině, skutečný postup dorazí ze serveru za okamžik.
            publish(Stage.QUEUED, 0.05f, note = "Navazuji na rozdělané generování")
            // Odmítnutou službu na popředí (kvóta Androidu 15, běh mimo popředí…)
            // nesmí appka odnést pádem při startu – bez ní se jen hůř přežívá
            // zamčený telefon, ale sledování běží dál.
            runCatching { GenerationService.start(app) }
                .onFailure { Log.w(TAG, "sluzbu na popredi se nepodarilo spustit", it) }
            runCatching {
                connectSocket(client, pid)
                watch(client, pid)
                finishFromHistory(client, pid, null)
            }.onFailure { e ->
                if (e is kotlinx.coroutines.CancellationException) throw e
                fail((e as? ComfyException)?.userMessage ?: e.message ?: "Nepodařilo se navázat na generování")
            }
        }
    }

    fun cancel() {
        val pid = (_state.value as? GenState.Running)?.promptId
        job?.cancel()
        scope.launch {
            val client = ComfyClient(settings.serverUrl)
            if (pid != null) {
                client.deleteFromQueue(pid)
                client.interrupt(pid)
            }
        }
        closeSocket()
        settings.activePromptId = null
        _state.value = GenState.Idle
        GenerationService.stop(app)
    }

    fun dismissResult() {
        if (_state.value is GenState.Done || _state.value is GenState.Failed) {
            _state.value = GenState.Idle
        }
    }

    /** Video se dogenerovalo, ale nešlo stáhnout – zkusit stažení znovu. */
    fun retryDownload() {
        val pid = settings.activePromptId ?: return
        if (isRunning) return
        resetRun()
        // Po restartu procesu by flagy druhu běhu byly na false a texty by
        // mluvily o videu i u obrázku/hudby — obnovit je ze settings stejně
        // jako při navazování.
        editRun = settings.activeEdit
        upscaleRun = settings.activeUpscale
        t2iRun = settings.activeT2i
        musicRun = settings.activeMusic
        restoreRun = settings.activeRestore
        swapRun = settings.activeSwap
        aioRun = !editRun && !upscaleRun && !t2iRun && !musicRun && !restoreRun &&
            !swapRun && settings.activeAio
        label = settings.activeLabel
        startedAt = System.currentTimeMillis()
        // Službu na popředí nesmí appka odnést pádem, když ji systém odmítne
        // (kvóta dataSync na Androidu 15, start mimo popředí). Bez ní se jen
        // hůř přežívá zamčený telefon; sledování běhu funguje dál.
        runCatching { GenerationService.start(app) }
            .onFailure { Log.w(TAG, "sluzbu na popredi se nepodarilo spustit", it) }
        job = scope.launch {
            runCatching {
                val client = ComfyClient(settings.serverUrl)
                finishFromHistory(client, pid, null)
            }.onFailure {
                fail(
                    (it as? ComfyException)?.userMessage ?: it.message ?: "Stažení se nepovedlo",
                    canRetryDownload = true,
                )
            }
        }
        // Až po vzniku jobu – publish() bez aktivního jobu stav zahazuje.
        publish(Stage.DOWNLOADING, 0.90f)
    }

    // ------------------------------------------------------------------ běh

    private suspend fun runGeneration(
        params: GenParams,
        images: List<File>,
        talkAudios: List<File> = emptyList(),
        timelineScene: cz.promptlab.h3video.data.TimelineScene? = null,
        aioScene: cz.promptlab.h3video.data.AioScene? = null,
        editScene: cz.promptlab.h3video.data.ImageEditScene? = null,
        upscaleScene: cz.promptlab.h3video.data.UpscaleScene? = null,
        t2i: Boolean = false,
        musicScene: cz.promptlab.h3video.data.MusicScene? = null,
        restoreScene: cz.promptlab.h3video.data.RestoreScene? = null,
        swapScene: cz.promptlab.h3video.data.FaceSwapScene? = null,
        inpaintScene: cz.promptlab.h3video.data.InpaintScene? = null,
    ) {
        val client = ComfyClient(settings.serverUrl)

        // --- 0a. Higgs a MiniMax se na jednu grafiku nevejdou. Higgs si drží
        // několik GB VRAM a MiniMax potřebuje zbytek, takže se Higgs před
        // generováním videa složí. Ukončuje se výhradně proces, který spouštěč
        // sám nastartoval – Higgs spuštěný ručně na počítači zůstane běžet.
        if (settings.higgsUrl.isNotBlank()) {
            publish(Stage.UPLOADING, 0.015f, note = "Uvolňuji grafiku po Higgsi")
            withContext(Dispatchers.IO) {
                runCatching { HiggsLauncher(settings.higgsUrl).stop() }
            }
        }

        // --- 0b. počkat, až server odpoví. Po restartu počítače ho hlídač na PC
        // spouští sám, ale nahrání uzlů a modelů trvá minuty. Není to chyba,
        // takže se nic neohlašuje jako selhání – jen se čeká a je vidět, na co.
        awaitServer(client)

        // --- 0b2. místo na grafice. Když je málo volné VRAM, model se dohrává
        // po částech z RAM a běh se natáhne i několikanásobně.
        uvolniPametKdyzTreba(client)

        // --- 0c. šablona balíku (jen All in One a Dialogy). Stahuje se DŘÍV,
        // než se nahraje jediný obrázek: když balík na serveru chybí, spadne to
        // hned, a ne až po zbytečném nahrání šesti fotek.
        val templateName = when {
            editScene != null -> null      // vlastní workflow z APK, nic se nestahuje
            upscaleScene != null -> null   // dtto
            t2i -> null                    // dtto
            musicScene != null -> null     // dtto
            restoreScene != null -> null   // dtto
            swapScene != null -> null      // dtto
            inpaintScene != null -> null   // dtto
            aioScene != null -> aioScene.sablona
            params.mode == cz.promptlab.h3video.data.Mode.TALK -> "r2v.json"
            else -> null
        }
        val templateText = templateName?.let { fetchTemplate(client, it) }

        // --- 1. načtení obrázků (jediné místo, kde smí spadnout kvůli souboru)
        publish(Stage.UPLOADING, 0.02f)
        // U mluvící scény určuje počet obrázků scéna sama (až devět postav),
        // proto se seznam nezkracuje podle přepínačů jako u ostatních karet.
        // Karta All in One si počet obrázků řídí sama (reference i klíčové snímky
        // jich mají víc), takže se seznam nezkracuje – stejně jako u mluvící scény.
        val needed = if (params.mode == cz.promptlab.h3video.data.Mode.TALK || aioScene != null) images
        else images.take(params.imageSlots)
        val payloads = needed.map { f ->
            readImage(f) ?: throw ComfyException(
                "image unreadable", "Obrázek se nepodařilo načíst. Zkus vybrat jiný."
            )
        }

        // --- 2. nahrání – síťová chyba se opakuje, protože upload je idempotentní.
        // Jméno je odvozené z obsahu: stejný obrázek = stejný soubor (žádné hromadění),
        // jiný obrázek = jiný soubor (nepřepíše podklad úlohy, která ještě čeká ve frontě).
        val names = payloads.mapIndexed { i, bytes ->
            uploadWithRetry(client, bytes, refName(bytes), 0.03f + 0.02f * i)
        }

        // Referenční video a zvuk (jen reference → video). Streamem, do kořene input
        // složky – VHS_LoadVideo i LoadAudio čtou právě odtud.
        val videoName = aioScene?.uploadVideo?.let { uploadMediaWithRetry(client, it, 0.05f) }
        // Namluvené repliky (dialogy). Pořadí je závazné – podle něj se
        // v promptu číslují značky <Audio N>.
        val talkNames = talkAudios.mapIndexed { i, f ->
            uploadMediaWithRetry(client, f, 0.05f + 0.005f * i)
        }

        // --- 3. zařazení do fronty pod vlastním prompt_id (kvůli bezpečnému opakování)
        val seed = if (params.randomSeed) Random.nextLong(1, 999_999_999_999_999L) else params.seed
        val effective = params.copy(seed = seed)
        // Časová osa (LSI nody) – snímky segmentů jdou ve stejném pořadí, v jakém
        // se předávaly k nahrání.
        val lsiTimeline = timelineScene?.let { scene ->
            cz.promptlab.h3video.data.buildLsiTimeline(scene, names)
        } ?: ""

        val jedeNaAio = aioScene != null || params.mode == cz.promptlab.h3video.data.Mode.TALK
        val workflow = when {
            // Úprava obrázku jede na Krea 2 z vlastní předlohy v APK; MiniMax
            // H3 se tu vůbec nespouští.
            editScene != null ->
                Krea2Builder.build(app, editScene, seed, names)

            // Zvětšit jede na uživatelově SeedVR2 workflow z APK.
            upscaleScene != null ->
                SeedVr2Builder.build(app, upscaleScene, seed, names)

            // Obrázek z textu jede na uživatelově Z-Image Turbo workflow z APK.
            t2i ->
                ZImageBuilder.build(
                    app, effective.prompt, effective.aspect, seed,
                    // PerfecZion je odvázaný sám o sobě – LoRA se s ním nemíchá
                    // (je trénovaná na základní Turbo a jen by kazila výsledek).
                    nsfwLora = effective.zimageNsfw && effective.zimageModel.isBlank(),
                    nsfwSila = effective.zimageNsfwSila,
                    model = effective.zimageModel,
                    loraFile = effective.zimageNsfwLora,
                )

            // Hudba jede na uživatelově ACE-Step 1.5 workflow z APK.
            musicScene != null ->
                AceMusicBuilder.build(app, musicScene, seed)

            // Oprava fotky jede na uživatelově Qwen 2511 workflow z APK.
            restoreScene != null ->
                RestoreBuilder.build(app, seed, names)

            // Výměna tváře jede na uživatelově ACE++ workflow z APK.
            swapScene != null ->
                FaceSwapBuilder.build(app, seed, names)

            // Domalování do masky: Klein 9B, nebo Flux Fill podle volby karty.
            inpaintScene != null ->
                InpaintBuilder.build(
                    app, inpaintScene.model, inpaintScene.prompt, seed, names,
                    lora = inpaintScene.lora,
                    loraSila = inpaintScene.loraSila,
                    sila = inpaintScene.sila,
                )

            // Karta All in One nestaví graf z předlohy zabalené v APK, ale
            // z hotové šablony balíku ComfyUI-ALLinONE-MinimaxH3 stažené přímo
            // ze serveru (viz fetchTemplate výš). Po aktualizaci balíku tak
            // appka generuje podle nové verze a nemůže se rozejít s počítačem.
            aioScene != null ->
                AioBuilder.build(templateText!!, effective, aioScene, names, videoName)

            // Dialogy jedou od verze 2.62 na téže šabloně (reference-to-video):
            // fotky postav jako reference obrazu, namluvené repliky jako
            // reference zvuku. Vlastní větev v ULTRA workflow už nemají.
            params.mode == cz.promptlab.h3video.data.Mode.TALK ->
                AioBuilder.buildTalk(
                    templateText!!, effective,
                    prompt = effective.prompt,
                    frames = effective.frames,
                    images = names,
                    audios = talkNames,
                )

            // Časová osa zůstává na uživatelově vyladěném ULTRA workflow –
            // LSI uzly si sampling řídí samy a na šablonách balíku neběží.
            else -> WorkflowBuilder.build(app, effective, lsiTimeline)
        }
        plannedSteps = effective.steps
        // Podle tříd uzlů se u šablon balíku poznávají fáze běhu.
        if (jedeNaAio) nodeClasses = AioBuilder.nodeClasses(workflow)
        if (editScene != null) nodeClasses = Krea2Builder.nodeClasses(workflow)
        if (upscaleScene != null) nodeClasses = SeedVr2Builder.nodeClasses(workflow)
        if (t2i) nodeClasses = ZImageBuilder.nodeClasses(workflow)
        if (musicScene != null) nodeClasses = AceMusicBuilder.nodeClasses(workflow)
        if (restoreScene != null) nodeClasses = RestoreBuilder.nodeClasses(workflow)
        if (swapScene != null) nodeClasses = FaceSwapBuilder.nodeClasses(workflow)
        if (inpaintScene != null) nodeClasses = InpaintBuilder.nodeClasses(workflow)

        val promptId = UUID.randomUUID().toString().lowercase()
        // Značka do logu: od téhle chvíle patří hlášky uzlů našemu běhu.
        logSince = runCatching { client.nodeWarnings().lastOrNull()?.first }.getOrNull()
        publish(Stage.QUEUED, 0.06f)
        submitWithRetry(client, workflow, promptId)

        // Zrušení mohlo přijít uprostřed blokujícího submitu — pak se úloha
        // sice zařadila, ale NESMÍ se zapsat jako aktivní (appka by se po
        // restartu chytala běhu, který uživatel zrušil) ani otevírat socket.
        // Místo toho se zrušená úloha rovnou uklidí i na serveru.
        if (!kotlin.coroutines.coroutineContext.isActive) {
            withContext(kotlinx.coroutines.NonCancellable) {
                runCatching {
                    client.deleteFromQueue(promptId)
                    client.interrupt(promptId)
                }
            }
            throw kotlinx.coroutines.CancellationException("zruseno behem odeslani")
        }

        settings.activePromptId = promptId
        settings.activeLabel = label

        // --- 4. sledování (nesmí selhat kvůli síti)
        connectSocket(client, promptId)
        watch(client, promptId)

        // --- 5. výsledek
        finishFromHistory(client, promptId, effective)
    }

    /**
     * Uklidí paměť grafiky, než se pošle úloha — a když nepomůže, řekne proč.
     *
     * Modely se do VRAM vejdou jen tehdy, když tam je místo. Když ho není,
     * ComfyUI je dohrává po částech z RAM a stejné generování trvá klidně
     * několikrát déle. Nejčastější příčiny jsou dvě:
     *
     * 1. **ComfyUI si drží model z minulé úlohy.** To se dá vyřešit z appky:
     *    `/free` mu řekne, ať vlastní modely pustí. Sahá se VÝHRADNĚ na to,
     *    co drží ComfyUI — cizí programy se tím nevypínají.
     * 2. **Paměť drží něco jiného na počítači** (hra, prohlížeč, jiný nástroj).
     *    S tím appka nic udělat nesmí ani nemůže, tak to aspoň napíše do
     *    průběhu, ať uživatel neřeší, „proč je to najednou pomalé".
     *
     * Uvolňuje se jen při skutečném nedostatku (pod 60 % karty volných), aby
     * dvě stejné úlohy po sobě nemusely model načítat zbytečně dvakrát.
     */
    private suspend fun uvolniPametKdyzTreba(client: ComfyClient) {
        val pred = withContext(Dispatchers.IO) { runCatching { client.vram() }.getOrNull() }
            ?: return
        val (volnoPred, celkem) = pred
        if (volnoPred.toDouble() / celkem >= 0.60) return

        publish(Stage.UPLOADING, 0.018f, note = "Uvolňuji paměť grafiky")
        val po = withContext(Dispatchers.IO) {
            runCatching {
                client.freeMemory()
                // ComfyUI uvolňuje na dalším průchodu smyčkou fronty.
                kotlinx.coroutines.delay(1200)
                client.vram()
            }.getOrNull()
        } ?: return
        val (volnoPo, _) = po
        val gb = { b: Long -> b.toDouble() / (1024 * 1024 * 1024) }
        Log.i(
            TAG,
            "VRAM pred %.1f GB, po uvolneni %.1f GB z %.1f GB".format(
                gb(volnoPred), gb(volnoPo), gb(celkem)
            )
        )
        // Pořád málo → drží to někdo jiný než ComfyUI. Uživatel je jediný,
        // kdo s tím může něco udělat, tak ať to ví.
        if (volnoPo.toDouble() / celkem < 0.55) {
            publish(
                Stage.UPLOADING, 0.02f,
                note = "Na grafice je volných jen %.1f z %.1f GB — něco jiného na počítači ji drží. Generování bude pomalejší.".format(
                    gb(volnoPo), gb(celkem)
                ),
            )
        }
    }

    /**
     * Šablona balíku ze serveru, se stejnou filozofií jako všechno ostatní:
     * síťová chyba se opakuje (5×, s rostoucím rozestupem), protože jeden
     * zakolísaný paket nesmí shodit celý běh. Úspěšně stažená kopie se ukládá
     * do `filesDir/templates` a slouží jen jako poslední záchrana, když server
     * po všech pokusech neodpoví – s viditelnou poznámkou v průběhu.
     *
     * Jediná výjimka z opakování je 404: balík na serveru chybí, opakování ani
     * stará kopie by problém jen zamaskovaly (graf by stejně neprošel validací).
     */
    private suspend fun fetchTemplate(client: ComfyClient, jmeno: String): String {
        publish(Stage.UPLOADING, 0.018f, note = "Stahuji šablonu ze serveru")
        val cacheFile = File(File(app.filesDir, "templates"), jmeno)
        var lastError: Throwable? = null
        repeat(5) { attempt ->
            val result = withContext(Dispatchers.IO) {
                runCatching { client.workflowTemplate(jmeno) }
            }
            result.onSuccess { text ->
                withContext(Dispatchers.IO) {
                    runCatching {
                        cacheFile.parentFile?.mkdirs()
                        cacheFile.writeText(text)
                    }
                }
                return text
            }.onFailure { e ->
                if (e is ComfyException && e.message?.endsWith(" 404") == true) throw e
                lastError = e
            }
            delay((2000L * (attempt + 1)).coerceAtMost(10_000L))
        }
        val zaloha = withContext(Dispatchers.IO) {
            runCatching { cacheFile.takeIf { it.exists() }?.readText() }.getOrNull()
        }
        if (zaloha != null) {
            publish(
                Stage.UPLOADING, 0.02f,
                note = "Server šablonu nevydal – použila se poslední známá kopie."
            )
            return zaloha
        }
        throw ComfyException(
            "template", (lastError as? ComfyException)?.userMessage
                ?: ("Server nevydal šablonu $jmeno. Zkontroluj, že je " +
                    "v ComfyUI nainstalovaný balík ComfyUI-ALLinONE-MinimaxH3.")
        )
    }

    /**
     * Čeká, až ComfyUI začne odpovídat. Na počítači běží hlídač
     * (`comfyui_watchdog_v1.ps1`), který server po přihlášení spustí a po pádu
     * nastartuje znovu – appka tedy nemusí nic spouštět, jen dát serveru čas.
     *
     * Čekání je štědré schválně: tahle instalace má hodně custom uzlů a od
     * spuštění do prvního „odpovídám" to bývá i pár minut. Teprve když se
     * nedočkáme vůbec, ohlásí se to jako chyba – a rovnou s tím, co ověřit.
     */
    private suspend fun awaitServer(client: ComfyClient) {
        if (withContext(Dispatchers.IO) { client.isAlive() }) return

        // ComfyUI nestartuje samo – na počítači nic neběží, dokud si to appka
        // neřekne. Požádáme spouštěče; když neodpoví ani ten, je vypnutý celý
        // počítač a čekat nemá smysl.
        val launched = withContext(Dispatchers.IO) { client.requestServerStart() }
        if (!launched) {
            val launcherUp = withContext(Dispatchers.IO) { client.launcherAlive() }
            if (!launcherUp) throw ComfyException(
                "launcher offline",
                "Počítač neodpovídá.\n\n" +
                    "Zkontroluj, že je zapnutý a přihlášený a že máš v telefonu " +
                    "zapnutý Tailscale.\n\nAdresa: ${settings.serverUrl}"
            )
        }
        publish(
            Stage.STARTING, 0.005f,
            note = "Zapínám ComfyUI na počítači. Načtení modelů trvá asi tři minuty."
        )

        val startedWaiting = System.currentTimeMillis()
        while (true) {
            val waited = ((System.currentTimeMillis() - startedWaiting) / 1000).toInt()
            if (waited > SERVER_WAIT_SECONDS) throw ComfyException(
                "server offline",
                "ComfyUI se nerozjelo ani po ${SERVER_WAIT_SECONDS / 60} minutách.\n\n" +
                    "Na počítači se podívej do\n" +
                    "D:\\COMFYUI_SAGE_DVOJKA_TEST\\logs_launcher\\\n\n" +
                    "Adresa serveru: ${settings.serverUrl}"
            )
            publish(
                Stage.STARTING,
                // Kolečko se během čekání jen lehce nadechne – skutečná práce
                // ještě nezačala a nemá co ukazovat.
                (0.005f + 0.015f * (waited / SERVER_WAIT_SECONDS.toFloat())).coerceAtMost(0.02f),
                note = "ComfyUI se na počítači spouští – načítá uzly a modely " +
                    "(${waited} s). Jakmile bude hotové, generování se rozjede samo."
            )
            delay(3000)
            if (withContext(Dispatchers.IO) { client.isAlive() }) {
                publish(Stage.UPLOADING, 0.02f, note = "Server je připravený, pokračuji.")
                return
            }
        }
    }

    private fun refName(bytes: ByteArray): String {
        val digest = java.security.MessageDigest.getInstance("SHA-1").digest(bytes)
        val hex = digest.take(8).joinToString("") { "%02x".format(it) }
        // PNG si musí nechat svou příponu — u výměny tváře nese masku
        // v alfa kanálu a ta by se pod cizí příponou snadno ztratila z dohledu.
        val ext = if (bytes.size > 4 && bytes[0] == 0x89.toByte() &&
            bytes[1] == 0x50.toByte()
        ) "png" else "jpg"
        return "ref_$hex.$ext"
    }

    /** Jméno pro video/zvuk z obsahu souboru (streamem, velké soubory se nečtou do paměti). */
    private fun mediaName(file: File): String {
        val md = java.security.MessageDigest.getInstance("SHA-1")
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            var r: Int
            while (input.read(buf).also { r = it } != -1) md.update(buf, 0, r)
        }
        val hex = md.digest().take(8).joinToString("") { "%02x".format(it) }
        val ext = file.extension.ifEmpty { "bin" }
        return "ref_$hex.$ext"
    }

    private suspend fun uploadMediaWithRetry(client: ComfyClient, file: File, progress: Float): String {
        if (!file.exists() || file.length() == 0L) throw ComfyException(
            "media unreadable", "Soubor se nepodařilo načíst. Zkus ho vybrat znovu."
        )
        val name = withContext(Dispatchers.IO) { mediaName(file) }
        var attempt = 0
        while (true) {
            publish(Stage.UPLOADING, progress, note = if (attempt > 0) NOTE_WAITING else null)
            try {
                return withContext(Dispatchers.IO) { client.uploadMedia(file, name) }
            } catch (e: ComfyException) {
                throw e
            } catch (e: Exception) {
                if (++attempt >= MAX_SUBMIT_ATTEMPTS) throw ComfyException(
                    "media net", "Nedaří se nahrát soubor na server ComfyUI."
                )
                delay(2000L * attempt.coerceAtMost(5))
            }
        }
    }

    // Soubor je už narovnaný a zmenšený při výběru, takže stačí přečíst bajty.
    private suspend fun readImage(file: File): ByteArray? = withContext(Dispatchers.IO) {
        runCatching { file.readBytes() }.getOrNull()?.takeIf { it.isNotEmpty() }
    }

    private suspend fun uploadWithRetry(
        client: ComfyClient, bytes: ByteArray, name: String, progress: Float
    ): String {
        var attempt = 0
        while (true) {
            publish(Stage.UPLOADING, progress, note = if (attempt > 0) NOTE_WAITING else null)
            try {
                return client.uploadImage(bytes, name)
            } catch (e: ComfyException) {
                throw e                       // server odmítl -> skutečná chyba
            } catch (e: Exception) {
                if (++attempt >= MAX_SUBMIT_ATTEMPTS) throw ComfyException(
                    "upload net", "Nedaří se spojit se serverem ComfyUI.\n\n" +
                        "Zkontroluj, že počítač běží, ComfyUI je spuštěné s parametrem " +
                        "--listen 0.0.0.0 a že jsi ve stejné síti / na Tailscale."
                )
                delay(2000L * attempt.coerceAtMost(5))
            }
        }
    }

    private suspend fun submitWithRetry(client: ComfyClient, wf: JSONObject, promptId: String) {
        var attempt = 0
        while (true) {
            try {
                client.queuePrompt(wf, clientId, promptId)
                return
            } catch (e: ComfyException) {
                throw e                       // neplatné workflow -> skutečná chyba
            } catch (e: Exception) {
                // Možná to prošlo a jen se ztratila odpověď – ověřit, ať to nepošleme dvakrát.
                if (client.history(promptId) != null || client.queuePosition(promptId) >= 0) return
                if (++attempt >= MAX_SUBMIT_ATTEMPTS) throw ComfyException(
                    "submit net", "Nedaří se spojit se serverem ComfyUI."
                )
                publish(Stage.QUEUED, 0.06f, note = NOTE_WAITING)
                delay(2000L * attempt.coerceAtMost(5))
            }
        }
    }

    /**
     * Hlavní smyčka sledování. WebSocket dodává jemný průběh, HTTP dotazy na /history
     * jsou pojistka pro případ, že se spojení rozpadne (uzamčený telefon, Doze).
     *
     * Smyčka nekončí uplynutím času – po [WATCH_TIMEOUT_MS] se jen ukáže klidná
     * poznámka a hlídá se dál (dřívější verze tady skončila a o pár desítek sekund
     * později ohlásila selhání, i když server pořád počítal). Skončit smí jen třemi
     * způsoby: úloha je hotová, server ohlásil chybu, nebo server POTVRDIL, že
     * úlohu vůbec nezná – to se stane po jeho restartu, kdy fronta nepřežije.
     */
    private suspend fun watch(client: ComfyClient, promptId: String) {
        val softDeadline = System.currentTimeMillis() + WATCH_TIMEOUT_MS
        val hardDeadline = System.currentTimeMillis() + WATCH_HARD_LIMIT_MS
        var tick = 0
        var lostPolls = 0
        lastContactAt = System.currentTimeMillis()

        // POZOR: scope má SupervisorJob, který se nikdy neruší — podmínka na
        // scope.isActive by po zrušení JOBU byla pořád true a smyčku by
        // zachraňoval jen delay(). Kontroluje se proto kontext samotného jobu.
        while (kotlin.coroutines.coroutineContext.isActive) {
            if (serverError != null) throw ComfyException("server", serverError!!)
            if (interrupted) {
                settings.activePromptId = null
                _state.value = GenState.Idle
                GenerationService.stop(app)
                throw kotlinx.coroutines.CancellationException("interrupted")
            }
            if (serverFinished) return
            if (System.currentTimeMillis() > hardDeadline) throw ComfyException(
                "watch hard limit",
                "Generování běží už přes ${WATCH_HARD_LIMIT_MS / 3_600_000} hodin – " +
                    "to není normální. Podívej se na počítači, co ComfyUI dělá."
            )

            // dotaz na historii + frontu (pojistka nezávislá na WebSocketu)
            if (tick % POLL_EVERY_TICKS == 0) {
                val h = runCatching { client.history(promptId) }.getOrNull()
                if (h != null) {
                    lostPolls = 0
                    lastContactAt = System.currentTimeMillis()
                    val status = h.optJSONObject("status")
                    val str = status?.optString("status_str")
                    if (str == "error") {
                        serverError = extractError(status) ?: "ComfyUI ohlásilo chybu při generování."
                        continue
                    }
                    val outputs = h.optJSONObject("outputs")
                    if (outputs != null && outputs.length() > 0) return
                } else {
                    val pos = runCatching { client.queuePosition(promptId) }.getOrDefault(-1)
                    if (pos >= 0) {
                        lostPolls = 0
                        lastContactAt = System.currentTimeMillis()
                        queuePos = pos
                    } else {
                        // Úloha není v historii ani ve frontě. Buď je to jen výpadek
                        // sítě (pak se nic neděje a čeká se dál), nebo se server
                        // mezitím restartoval a fronta je pryč. Rozliší to promptKnown:
                        // false vrací JEN když server odpověděl a úlohu nezná.
                        when (client.promptKnown(promptId)) {
                            true -> { lostPolls = 0; lastContactAt = System.currentTimeMillis() }
                            false -> {
                                lastContactAt = System.currentTimeMillis()
                                if (++lostPolls >= LOST_POLLS_LIMIT) {
                                    settings.activePromptId = null
                                    throw ComfyException(
                                        "prompt lost",
                                        "Server ComfyUI se mezitím restartoval a rozdělaná " +
                                            "úloha se ztratila.\n\nSpusť generování znovu."
                                    )
                                }
                            }
                            null -> { /* síť – výpadek nikdy neznamená selhání úlohy */ }
                        }
                    }
                }
                // WebSocket se po probuzení telefonu musí obnovit
                if (socket == null) connectSocket(client, promptId)
            }

            emitProgress(longRun = System.currentTimeMillis() > softDeadline)
            tick++
            // Jednou za sekundu stačí: jemný postup chodí po WebSocketu, tohle jen
            // překresluje UI a notifikaci. Čtyřikrát za sekundu po dobu desítek
            // minut je zbytečná zátěž baterie i překreslování.
            delay(TICK_MS)
        }
    }

    private fun extractError(status: JSONObject?): String? {
        val messages = status?.optJSONArray("messages") ?: return null
        for (i in 0 until messages.length()) {
            val m = messages.optJSONArray(i) ?: continue
            if (m.optString(0) == "execution_error") {
                val d = m.optJSONObject(1) ?: continue
                val node = d.optString("node_type")
                val msg = d.optString("exception_message")
                return "Chyba v uzlu $node:\n$msg"
            }
        }
        return null
    }

    private suspend fun finishFromHistory(
        client: ComfyClient, promptId: String, params: GenParams?
    ) {
        publish(Stage.DOWNLOADING, 0.90f)

        // dotáhnout záznam historie (server ho má, i kdyby telefon zrovna neměl
        // signál). Záznam se navíc může objevit dřív než jeho výstupy, takže se
        // čeká i na neprázdné "outputs" – a když se to nestihne, stav zůstane
        // opakovatelný (Stáhnout znovu), protože na serveru výsledek je.
        var record: JSONObject?
        var attempt = 0
        while (true) {
            record = runCatching { client.history(promptId) }.getOrNull()
            val status = record?.optJSONObject("status")
            if (status?.optString("status_str") == "error") {
                throw ComfyException("server", extractError(status) ?: "ComfyUI ohlásilo chybu.")
            }
            val zatim = record?.optJSONObject("outputs")
            if (zatim != null && zatim.length() > 0) break
            if (++attempt >= MAX_DOWNLOAD_ATTEMPTS) {
                fail(
                    "Výsledek se nepodařilo najít na serveru. Zkus to znovu, až bude spojení stabilní.",
                    canRetryDownload = true,
                )
                return
            }
            publish(Stage.DOWNLOADING, 0.90f, note = NOTE_WAITING)
            delay(3000L)
        }

        val outputs = record!!.optJSONObject("outputs")
            ?: throw ComfyException("no outputs", "ComfyUI nevrátilo žádný výstup.")

        // Výstupů může být víc druhů najednou: list postavy vrací otočné video
        // (SaveVideo) i slepený list jako PNG (SaveImage). Videem se plní
        // galerie aplikace; obrázky se stáhnou zvlášť a uloží do galerie
        // telefonu, protože právě ony bývají to, kvůli čemu se generovalo.
        // Pozor: SaveVideo hlásí soubor taky pod klíčem "images", takže se
        // obrázek pozná podle přípony, ne podle názvu pole.
        data class OutFile(val filename: String, val subfolder: String, val type: String)

        fun isPicture(name: String) = name.substringAfterLast('.', "").lowercase() in
            listOf("png", "jpg", "jpeg", "webp")
        fun isSound(name: String) = name.substringAfterLast('.', "").lowercase() in
            listOf("mp3", "flac", "wav", "opus", "ogg", "m4a")

        // Priorita hlavního výstupu: video > zvuk > obrázek. Kdyby graf vracel
        // video i samostatnou stopu zvuku, výsledkem karty je video.
        var mainVideo: OutFile? = null
        var mainAudio: OutFile? = null
        val pictures = mutableListOf<OutFile>()
        for (key in outputs.keys()) {
            val o = outputs.optJSONObject(key) ?: continue
            // Zvukové uzly (SaveAudioMP3…) hlásí soubory pod klíčem "audio".
            for (arrayKey in listOf("images", "videos", "gifs", "audio")) {
                val arr = o.optJSONArray(arrayKey) ?: continue
                for (i in 0 until arr.length()) {
                    val f = arr.getJSONObject(i)
                    val out = OutFile(
                        f.optString("filename"),
                        f.optString("subfolder", ""),
                        f.optString("type", "output"),
                    )
                    if (out.filename.isBlank()) continue
                    when {
                        isPicture(out.filename) -> pictures += out
                        isSound(out.filename) -> if (mainAudio == null) mainAudio = out
                        else -> if (mainVideo == null) mainVideo = out
                    }
                }
            }
        }
        val main: OutFile? = mainVideo ?: mainAudio
        // Karta Úprava obrázku žádné video nevyrábí – jejím výsledkem je PNG.
        // Když tedy video není, bere se jako hlavní výstup první obrázek a
        // ostatní zůstanou jako doplňkové (list postavy má obojí).
        val jenObrazek = main == null && pictures.isNotEmpty()
        val mainOut = main ?: pictures.firstOrNull()
            ?: throw ComfyException("no file", "ComfyUI nevrátilo žádný soubor.")
        if (jenObrazek) pictures.removeAt(0)
        val filename = mainOut.filename
        val subfolder = mainOut.subfolder
        val type = mainOut.type

        // Přípona podle skutečného obsahu: obrázek a zvuk si nesou svou,
        // MP3 uložené jako .mp4 by hudební přehrávače nepřehrály.
        val pripona = when {
            jenObrazek -> filename.substringAfterLast('.', "png").lowercase()
            isSound(filename) -> filename.substringAfterLast('.', "mp3").lowercase()
            // I video si nese svou skutečnou příponu – webm přejmenované
            // na .mp4 by některé přehrávače odmítly.
            else -> filename.substringAfterLast('.', "mp4").lowercase()
        }
        val target = File(VideoItem.videosDir(app), "$promptId.$pripona")
        val url = client.viewUrl(filename, subfolder, type)

        var dl = 0
        while (true) {
            try {
                transferStartedAt = System.currentTimeMillis()
                client.download(url, target) { done, total ->
                    transferDone = done
                    transferTotal = total
                    val p = if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else 0f
                    publish(Stage.DOWNLOADING, 0.90f + 0.10f * p)
                }
                transferDone = 0L; transferTotal = 0L
                break
            } catch (e: Exception) {
                Log.w(TAG, "prenos videa selhal (pokus $dl)", e)
                transferDone = 0L; transferTotal = 0L
                if (++dl >= MAX_DOWNLOAD_ATTEMPTS) {
                    fail(
                        "Výsledek je hotový na počítači, ale nejde ho přenést do aplikace.\n" +
                            "Zkus to znovu, až bude spojení v pořádku.",
                        canRetryDownload = true
                    )
                    return
                }
                publish(Stage.DOWNLOADING, 0.90f, note = NOTE_WAITING)
                delay(2500L * dl.coerceAtMost(5))
            }
        }

        val createdAt = System.currentTimeMillis()

        // Obrázkové výstupy (list postavy) se ukládají do galerie telefonu
        // vždycky – jsou to hotové obrázky, tlačítko „Uložit" v přehrávači
        // videí se jich netýká a jinak by se k nim uživatel vůbec nedostal.
        var savedPictures = 0
        pictures.forEachIndexed { i, pic ->
            val ok = runCatching {
                val ext = pic.filename.substringAfterLast('.', "png")
                val tmp = File(app.cacheDir, "out_${promptId}_$i.$ext")
                client.download(client.viewUrl(pic.filename, pic.subfolder, pic.type), tmp) { _, _ -> }
                val saved = MediaSaver.saveImageToGallery(app, tmp, "H3_list_${createdAt}_${i + 1}.$ext")
                tmp.delete()
                saved
            }.getOrDefault(false)
            if (ok) savedPictures++
        }

        // Do galerie telefonu se nic neukládá samo – jen když si to uživatel
        // v nastavení zapne. Jinak výsledek žije v Galerii aplikace a stáhne se
        // tlačítkem, až se bude líbit. Upravený obrázek jde do Obrázků, video
        // do Filmů.
        val zvuk = isSound(target.name)
        val saved = settings.autoSaveToGallery && (
            when {
                jenObrazek -> MediaSaver.saveImageToGallery(app, target, "H3_$createdAt.$pripona")
                zvuk -> MediaSaver.saveAudioToGallery(app, target, "H3_$createdAt.$pripona")
                else -> MediaSaver.saveToGallery(app, target, "H3_$createdAt.$pripona")
            }
            )

        val item = VideoItem(
            id = promptId,
            fileName = target.name,
            prompt = params?.prompt.orEmpty(),
            createdAt = createdAt,
            // U hudby je délka rovnou v zadání (seconds); u videa ji počítá
            // model po blocích, u obrázku je nula.
            seconds = when {
                jenObrazek -> 0f
                zvuk -> params?.seconds?.toFloat() ?: 0f
                else -> params?.realSeconds ?: 0f
            },
            resolution = params?.resolution?.label ?: label,
            seed = params?.seed ?: 0L,
            twoImages = false,
            inGallery = saved,
            mode = params?.mode?.name.orEmpty(),
            // Jen u čerstvého běhu — po navázání či opakovaném stažení by
            // startedAt měřil jen to čekání, ne skutečné generování.
            tookSeconds = if (params != null && startedAt > 0)
                ((createdAt - startedAt) / 1000L).toInt().coerceAtLeast(0) else 0,
        )
        history.add(item)
        settings.activePromptId = null
        closeSocket()
        // Hlášky uzlů z tohohle běhu. Director takhle upozorňuje na věci, které
        // z hotového videa poznat nejde – repliku, kterou neudělal jako dialog,
        // nebo prázdné povinné pole.
        val warnings = withContext(Dispatchers.IO) {
            NodeWarnings.filtruj(
                client.nodeWarnings(logSince)
                    .map { it.second }
                    .filter { it.contains("[WARNING]") || it.contains("[ERROR]") }
                    .map { it.replace("[WARNING]", "").replace("[ERROR]", "").trim() }
                    .distinct()
            ).takeLast(6)
        }
        val poznamky = if (savedPictures > 0)
            listOf("List postavy je uložený v telefonu ve složce Obrázky/H3 Video.")
        else emptyList()
        _state.value = GenState.Done(item, poznamky + warnings)
        GenerationService.notifyDone(app, item)
        GenerationService.stop(app)
    }

    private fun fail(message: String, canRetryDownload: Boolean = false) {
        closeSocket()
        if (!canRetryDownload) settings.activePromptId = null
        _state.value = GenState.Failed(message, canRetryDownload)
        GenerationService.notifyFailed(app, message)
        GenerationService.stop(app)
    }

    // ------------------------------------------------------------------ websocket

    private fun connectSocket(client: ComfyClient, promptId: String) {
        if (socket != null) return
        socket = runCatching {
            client.openWebSocket(clientId, object : WebSocketListener() {
                override fun onMessage(webSocket: WebSocket, text: String) {
                    lastContactAt = System.currentTimeMillis()
                    runCatching { handleMessage(JSONObject(text), promptId) }
                }

                override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                    lastContactAt = System.currentTimeMillis()
                    runCatching { handlePreview(bytes) }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    // Spojení spadlo (uspaný telefon). To není chyba úlohy – jen se
                    // přestane chodit jemný průběh, /history to pohlídá.
                    socket = null
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    socket = null
                }
            })
        }.getOrNull()
    }

    private fun closeSocket() {
        runCatching { socket?.close(1000, null) }
        socket = null
    }

    private fun handleMessage(msg: JSONObject, promptId: String) {
        val type = msg.optString("type")
        val data = msg.optJSONObject("data") ?: JSONObject()
        val pid = data.optString("prompt_id", "")
        val mine = pid.isEmpty() || pid == promptId

        when (type) {
            "status" -> {
                val remaining = data.optJSONObject("status")
                    ?.optJSONObject("exec_info")?.optInt("queue_remaining", -1) ?: -1
                if (remaining >= 0 && currentNode == null) queuePos = max(0, remaining - 1)
            }

            "execution_start" -> if (mine) {
                queuePos = 0
                currentNode = null
                nodeStartedAt = System.currentTimeMillis()
            }

            // Živý náhled z uzlu ModelPreviewOverrideKJ. Nechodí binárním kanálem
            // jako standardní náhledy ComfyUI (ty jsou vypnuté, server běží bez
            // --preview-method), ale jako obyčejná zpráva s obrázkem v base64.
            // Prompt_id u ní není, proto se nedá filtrovat podle úlohy – během
            // našeho běhu ale žádná cizí sampling úloha běžet nemůže, fronta je
            // jednovláknová.
            "kj_preview_override" -> {
                val b64 = data.optString("image")
                if (b64.isNotEmpty()) runCatching {
                    val raw = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
                    // Podle `mime` – uzel posílá i animaci (MP4 nebo animovaný
                    // WebP), na kterou samotný BitmapFactory nestačí.
                    val mime = data.optString("mime").ifBlank { null }
                    val fps = data.optInt("fps", 8).let { if (it > 0) it else 8 }
                    PreviewDecoder.decode(raw, mime, fps)?.let { preview = it }
                }
            }

            "executing" -> if (mine) {
                val node = if (data.isNull("node")) null else data.optString("node")
                if (node == null) {
                    if (pid == promptId) serverFinished = true
                } else {
                    currentNode = node
                    nodeStartedAt = System.currentTimeMillis()
                    queuePos = 0
                }
            }

            "progress" -> if (mine) {
                val node = data.optString("node")
                node.takeIf { it.isNotEmpty() }?.let {
                    if (it != currentNode) { currentNode = it; nodeStartedAt = System.currentTimeMillis() }
                }
                // Kroky bere jen z uzlu vzorkování – ostatní uzly hlásí jiné jednotky.
                if (reportsSteps(node)) {
                    val m = data.optInt("max", 0)
                    if (m > 0) srvMax = m
                    noteStep(data.optInt("value", srvStep))
                }
            }

            "progress_state" -> if (mine) {
                val nodes = data.optJSONObject("nodes") ?: return
                for (key in nodes.keys()) {
                    val n = nodes.optJSONObject(key) ?: continue
                    if (n.optString("state") == "running") {
                        if (key != currentNode) {
                            currentNode = key
                            nodeStartedAt = System.currentTimeMillis()
                        }
                        queuePos = 0
                        if (reportsSteps(key)) {
                            val v = n.optDouble("value", 0.0)
                            val m = n.optDouble("max", 0.0)
                            if (m > 1.5) { srvMax = m.toInt(); noteStep(v.toInt()) }
                        }
                    }
                }
            }

            // POZOR: "executed" chodí po KAŽDÉM výstupním uzlu, ne po dokončení
            // úlohy — u grafů s více výstupy (list postavy: video + slepený
            // obrázek) by sledování skončilo po prvním z nich a /history by
            // ještě neexistovala. Konec poznáme z execution_success a z
            // "executing" s node == null (obojí výše).
            "execution_success" -> if (pid == promptId) serverFinished = true
            "execution_interrupted" -> if (pid == promptId) interrupted = true
            "execution_error" -> if (pid == promptId) {
                val node = data.optString("node_type")
                val message = data.optString("exception_message")
                serverError = "Chyba v uzlu $node:\n$message"
            }
        }
    }

    /**
     * Zaznamená nový krok vzorkování a udržuje klouzavý průměr tempa.
     * První krok se do průměru nezapočítává – je v něm ještě příprava modelu,
     * která u H3 zabere klidně minutu a odhad by kvůli ní byl mimo.
     */
    private fun noteStep(value: Int) {
        if (value <= srvStep) { srvStep = value; return }
        val now = System.currentTimeMillis()
        if (lastStepAt > 0L && lastSrvStep > 0) {
            val delta = (now - lastStepAt) / 1000.0 / (value - lastSrvStep)
            if (delta in 0.02..1800.0) {
                secPerServerStep =
                    if (secPerServerStep <= 0.0) delta else secPerServerStep * 0.7 + delta * 0.3
            }
        }
        lastSrvStep = value
        lastStepAt = now
        srvStep = value
    }

    private fun handlePreview(bytes: ByteString) {
        if (bytes.size < 8) return
        val arr = bytes.toByteArray()
        val event = ((arr[0].toInt() and 0xFF) shl 24) or ((arr[1].toInt() and 0xFF) shl 16) or
            ((arr[2].toInt() and 0xFF) shl 8) or (arr[3].toInt() and 0xFF)
        if (event != 1) return
        val bmp = android.graphics.BitmapFactory
            .decodeByteArray(arr, 8, arr.size - 8) ?: return
        preview = Preview.Frames(listOf(bmp), 1)
    }

    // ------------------------------------------------------------------ průběh

    private fun stageForNode(): Stage {
        val s = _state.value
        if (s is GenState.Running && s.stage == Stage.DOWNLOADING) return Stage.DOWNLOADING
        if (queuePos > 0) return Stage.QUEUED
        return stageOf(currentNode ?: return Stage.QUEUED)
    }

    /**
     * Celkový postup. Uvnitř uzlů, které nehlásí dílčí průběh, se hodnota plynule
     * plazí k hornímu okraji rozsahu, aby kolečko nevypadalo zaseknuté.
     */
    private fun progressNow(): Float {
        val node = currentNode ?: return 0.06f
        val (from, to) = rangeOf(node)
        val elapsed = (System.currentTimeMillis() - nodeStartedAt) / 1000f
        val inner = when {
            reportsSteps(node) && srvMax > 0 ->
                (srvStep.toFloat() / srvMax).coerceIn(0f, 1f)

            // Vzorkovací uzel běží, ale kroky ještě nechodí – ComfyUI zatím nahrává
            // model do karty (u H3 klidně minutu dvě). Kolečko tu smí popolézt jen
            // v prvních procentech rozsahu, jinak by ukazovalo desítky procent
            // hotové práce, přestože nezačal ani první krok.
            reportsSteps(node) ->
                (1f - Math.exp((-elapsed / 30f).toDouble()).toFloat()) * 0.06f

            else -> (1f - Math.exp((-elapsed / 8f).toDouble()).toFloat()) * 0.85f
        }
        return (from + (to - from) * inner).coerceIn(0f, 0.99f)
    }

    /** Model se nahrává do karty – vzorkování běží, ale kroky ještě nezačaly. */
    private val isPreparing: Boolean
        get() = reportsSteps(currentNode) && srvMax <= 0

    /**
     * Odhad zbývajícího času. Počítá se z NAMĚŘENÉHO tempa vzorkování, protože to
     * je zhruba 95 % celé práce; dokud tempo neznáme, radši se nic neukazuje, než
     * aby appka slibovala číslo, které se pak jen zvětšuje.
     */
    private fun etaNow(): Int? {
        // Během přenosu videa se zbývající čas počítá z rychlosti přenosu –
        // odhad ze vzorkování už neplatí a "počítám" by u velkého videa svítilo
        // klidně několik minut.
        if (transferTotal > 0 && transferDone > 0) {
            val elapsed = (System.currentTimeMillis() - transferStartedAt) / 1000.0
            if (elapsed >= 1.0) {
                val rate = transferDone / elapsed
                if (rate > 0) return ((transferTotal - transferDone) / rate)
                    .toInt().coerceIn(0, 6 * 3600)
            }
            return null
        }
        if (!reportsSteps(currentNode)) return null
        if (secPerServerStep <= 0.0 || srvMax <= 0) return null
        val remaining = (srvMax - srvStep).coerceAtLeast(0)
        // dekódování obrazu i zvuku a složení videa po vzorkování; u změřeného
        // ostrého běhu to bylo kolem minuty, proto strop 90 s
        val tail = (0.05 * srvMax * secPerServerStep).coerceAtMost(90.0)
        return (remaining * secPerServerStep + tail).toInt().coerceIn(0, 6 * 3600)
    }

    private fun emitProgress(longRun: Boolean = false) {
        val overall = progressNow()
        val quiet = System.currentTimeMillis() - lastContactAt > 25_000
        publish(
            stageForNode(), overall,
            note = when {
                quiet -> "Telefon je bez spojení se serverem – generování na počítači běží dál."
                longRun -> "Trvá to dlouho, ale hlídám to dál – video se objeví, jakmile bude hotové."
                else -> null
            },
            offline = quiet
        )
    }

    private fun publish(
        stage: Stage, overall: Float, note: String? = null, offline: Boolean = false
    ) {
        // Po Zrušit doběhne blokující síťové volání (OkHttp se přerušit nedá)
        // a jeho průběžné callbacky by stav „vzkřísily" zpátky na Running —
        // obrazovka průběhu by po zrušení zůstala viset. Zrušený job už stav
        // publikovat nesmí.
        if (job?.isActive != true) return
        val prev = _state.value as? GenState.Running
        // Dokud jen odhadujeme, kolečko necouvá. Jakmile ale server hlásí skutečné
        // kroky, má přednost i za cenu skoku dolů – jinak by se v kolečku zamklo
        // číslo z odhadu a zůstalo tam, i když je hotová teprve třetina.
        val authoritative = reportsSteps(currentNode) && srvMax > 0
        _state.value = GenState.Running(
            promptId = settings.activePromptId,
            stage = stage,
            overall = if (authoritative) overall else max(overall, prev?.overall ?: 0f),
            step = displayedStep,
            totalSteps = zobrazenyPocetKroku,
            queuePosition = queuePos,
            startedAt = startedAt,
            etaSeconds = etaNow(),
            secondsPerStep = displayedSecondsPerStep,
            preparing = isPreparing,
            transferDone = transferDone,
            transferTotal = transferTotal,
            preview = preview,
            note = note,
            offline = offline,
            label = label,
            isImage = editRun || upscaleRun || t2iRun || restoreRun || swapRun,
            isUpscale = upscaleRun,
            isT2i = t2iRun,
            isMusic = musicRun,
            isRestore = restoreRun,
            isSwap = swapRun,
            isInpaint = inpaintRun,
        )
    }

    private fun resetRun() {
        currentNode = null
        nodeStartedAt = System.currentTimeMillis()
        srvStep = 0
        srvMax = 0
        lastSrvStep = 0
        lastStepAt = 0L
        secPerServerStep = 0.0
        queuePos = -1
        preview = null
        serverFinished = false
        serverError = null
        interrupted = false
        transferDone = 0L
        transferTotal = 0L
        transferStartedAt = 0L
        lastContactAt = System.currentTimeMillis()
    }

    private const val NOTE_WAITING = "Čekám na spojení – zkouším to dál."

    /**
     * Otevře appku na výsledku (z notifikace). Třída se odkazuje přímo, ne přes
     * Class.forName – to by po zapnutí R8 přestalo fungovat, protože se název
     * třídy při zmenšování kódu přejmenuje.
     */
    fun openIntent(ctx: Context): Intent =
        Intent(ctx, cz.promptlab.h3video.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
}
