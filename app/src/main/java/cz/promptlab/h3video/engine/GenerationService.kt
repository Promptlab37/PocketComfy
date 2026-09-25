package cz.promptlab.h3video.engine

import cz.promptlab.h3video.data.t

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import cz.promptlab.h3video.R
import cz.promptlab.h3video.comfy.Stage
import cz.promptlab.h3video.data.VideoItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Drží proces naživu, dokud se generuje, a ukazuje průběh v notifikaci.
 * Bez toho by Android při zamčeném telefonu appku uspal a sledování by se rozpadlo.
 */
class GenerationService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var watcher: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Prázdný intent = službu znovu spustil systém (START_STICKY) po tom, co
        // byl proces ukončen. Když se mezitím nic negeneruje, není co hlídat a
        // pokus o popředí by jen znovu spadl.
        if (intent == null && !GenerationEngine.isRunning) {
            stopSelf()
            return START_NOT_STICKY
        }
        // Tohle je to místo, kde appka padala. `startForeground` umí vyhodit
        // ForegroundServiceStartNotAllowedException (start mimo popředí od
        // Androidu 12) nebo narazit na denní kvótu typu dataSync (Android 15).
        // Neodchycená výjimka ve službě zabije celý proces – tedy i appku při
        // pouhém spuštění. Sledování běhu se bez služby obejde; jen hůř přežije
        // zamčený telefon, což je nesrovnatelně menší škoda než pád.
        if (!runCatching { startForegroundCompat(buildProgress(t("Připravuji…"), 0, false)) }.isSuccess) {
            stopSelf()
            return START_NOT_STICKY
        }
        naPopredi = true
        if (watcher == null) {
            watcher = scope.launch {
                // Hlídá se stav běhu I fronta: služba se smí vypnout až ve
                // chvíli, kdy nic neběží A nic nečeká. Dřív se vypnula hned
                // po doběhnutí úlohy — a další z fronty ji pak musela znovu
                // zapnout z pozadí (zamčený telefon), což Android od verze 12
                // nedovolí. Úloha se rozjela bez služby, v úsporném režimu
                // přišla o síť a spadla. Po odemčení další zase prošla.
                kotlinx.coroutines.flow.combine(
                    GenerationEngine.state, RunQueue.queue,
                ) { st, q -> st to q.size }.collectLatest { (s, cekaVeFronte) ->
                    when (s) {
                        is GenState.Running -> {
                            val percent = (s.overall * 100).toInt().coerceIn(0, 100)
                            val text = buildString {
                                // Krea 2 vyrábí obrázek – "Generuji video" by lhalo.
                                // Texty všech druhů běhů drží matice v RunTexts.kt.
                                append(stageText(s.stage, s.kind, s.model))
                                if (s.stage == Stage.SAMPLING && s.totalSteps > 0)
                                    append(" · krok ${s.step}/${s.totalSteps}")
                                s.etaSeconds?.let { append(" · zbývá ~${formatEta(it)}") }
                            }
                            notify(
                                NOTIF_PROGRESS,
                                buildProgress(text, percent, true, s.kind)
                            )
                        }
                        else -> if (cekaVeFronte > 0) {
                            notify(
                                NOTIF_PROGRESS,
                                buildProgress(
                                    t("Ve frontě čeká: %d").format(cekaVeFronte), 0, false,
                                ),
                            )
                        } else {
                            // Poslední úloha z fronty: fronta je prázdná dřív,
                            // než se nový běh stihne přihlásit. Chvíli počkat —
                            // přijde-li mezitím Running, collectLatest tohle
                            // zruší a služba jede dál.
                            kotlinx.coroutines.delay(3000)
                            if (!GenerationEngine.isRunning) stopSelf()
                        }
                    }
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        naPopredi = false
        watcher?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    // Android 15 dává dataSync službě denní kvótu ~6 h a po jejím vyčerpání
    // zavolá tohle. Bez override by systém proces zabil i s appkou; takhle se
    // služba jen tiše zavře a sledování běhu pokračuje bez notifikace.
    override fun onTimeout(startId: Int, fgsType: Int) {
        stopSelf()
    }

    private fun startForegroundCompat(n: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_PROGRESS, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIF_PROGRESS, n)
        }
    }

    private fun buildProgress(
        text: String, percent: Int, determinate: Boolean, kind: RunKind = RunKind.VIDEO,
    ): Notification =
        NotificationCompat.Builder(this, CH_PROGRESS)
            .setSmallIcon(R.drawable.ic_stat_h3)
            .setContentTitle(notificationTitle(kind))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setProgress(100, percent, !determinate)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openApp(this))
            .build()

    private fun notify(id: Int, n: Notification) {
        notifyIfAllowed(this, id, n)
    }

    companion object {
        private fun notifyIfAllowed(ctx: Context, id: Int, notification: Notification) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.POST_NOTIFICATIONS) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED
            ) return
            try {
                NotificationManagerCompat.from(ctx).notify(id, notification)
            } catch (_: SecurityException) {
                // Uživatel mohl oprávnění odvolat mezi kontrolou a odesláním.
            }
        }

        private const val CH_PROGRESS = "generation"
        private const val CH_DONE = "generation_done"
        private const val NOTIF_PROGRESS = 1001
        private const val NOTIF_DONE = 1002

        /**
         * Běží služba na popředí? Když ano, nový běh ji znovu nestartuje —
         * její hlídač si ho převezme sám. Znovu volat `startForegroundService`
         * z pozadí (zamčený telefon) Android 12+ odmítne.
         */
        @Volatile var naPopredi: Boolean = false
            private set

        fun start(ctx: Context) {
            if (naPopredi) return
            val i = Intent(ctx, GenerationService::class.java)
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(i)
                else ctx.startService(i)
            }
        }

        /**
         * Zastaví službu — **ale ne, dokud něco čeká ve frontě.** Engine volá
         * stop po každém dokončeném i spadlém běhu; kdyby služba opravdu
         * skončila, další úloha z fronty by ji musela zapnout z pozadí
         * a to Android 12+ se zamčeným telefonem nedovolí. Až je fronta
         * prázdná, vypne se služba sama (hlídač v [onStartCommand]).
         */
        fun stop(ctx: Context) {
            if (RunQueue.queue.value.isNotEmpty()) return
            runCatching { ctx.stopService(Intent(ctx, GenerationService::class.java)) }
        }

        fun ensureChannels(ctx: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val nm = ctx.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CH_PROGRESS, t("Průběh generování"), NotificationManager.IMPORTANCE_LOW)
                    .apply { description = t("Živý průběh běžícího generování") }
            )
            nm.createNotificationChannel(
                NotificationChannel(CH_DONE, t("Hotová videa"), NotificationManager.IMPORTANCE_HIGH)
                    .apply { description = t("Upozornění, když je video hotové") }
            )
        }

        private fun openApp(ctx: Context): PendingIntent = PendingIntent.getActivity(
            ctx, 0, GenerationEngine.openIntent(ctx),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        fun notifyDone(ctx: Context, item: VideoItem) {
            ensureChannels(ctx)
            val text =
                (if (item.isImage) item.resolution
                else "%.1f s · %s".format(item.seconds, item.resolution)) +
                    if (item.tookSeconds > 0)
                        " · hotovo za ${formatEta(item.tookSeconds)}" else ""
            val n = NotificationCompat.Builder(ctx, CH_DONE)
                .setSmallIcon(R.drawable.ic_stat_h3)
                .setContentTitle(
                    when {
                        item.isAudio -> t("Skladba je hotová")
                        item.isImage -> t("Obrázek je hotový")
                        else -> t("Video je hotové")
                    }
                )
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(item.prompt.take(180)))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(openApp(ctx))
                .build()
            notifyIfAllowed(ctx, NOTIF_DONE, n)
        }

        fun notifyFailed(ctx: Context, message: String) {
            ensureChannels(ctx)
            val n = NotificationCompat.Builder(ctx, CH_DONE)
                .setSmallIcon(R.drawable.ic_stat_h3)
                .setContentTitle(t("Generování se nepovedlo"))
                .setContentText(message.lineSequence().first())
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(openApp(ctx))
                .build()
            notifyIfAllowed(ctx, NOTIF_DONE, n)
        }

        fun formatEta(seconds: Int): String {
            val m = seconds / 60
            val s = seconds % 60
            return if (m > 0) "$m min ${s}s" else "${s}s"
        }
    }
}
