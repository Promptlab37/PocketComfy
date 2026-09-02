package cz.promptlab.h3video.engine

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
        if (!runCatching { startForegroundCompat(buildProgress("Připravuji…", 0, false)) }.isSuccess) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (watcher == null) {
            watcher = scope.launch {
                GenerationEngine.state.collectLatest { s ->
                    when (s) {
                        is GenState.Running -> {
                            val percent = (s.overall * 100).toInt().coerceIn(0, 100)
                            val text = buildString {
                                // Krea 2 vyrábí obrázek – "Generuji video" by lhalo.
                                // Texty všech druhů běhů drží matice v RunTexts.kt.
                                append(stageText(s.stage, s.kind))
                                if (s.stage == Stage.SAMPLING && s.totalSteps > 0)
                                    append(" · krok ${s.step}/${s.totalSteps}")
                                s.etaSeconds?.let { append(" · zbývá ~${formatEta(it)}") }
                            }
                            notify(
                                NOTIF_PROGRESS,
                                buildProgress(text, percent, true, s.kind)
                            )
                        }
                        else -> stopSelf()
                    }
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
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
        runCatching { NotificationManagerCompat.from(this).notify(id, n) }
    }

    companion object {
        private const val CH_PROGRESS = "generation"
        private const val CH_DONE = "generation_done"
        private const val NOTIF_PROGRESS = 1001
        private const val NOTIF_DONE = 1002

        fun start(ctx: Context) {
            val i = Intent(ctx, GenerationService::class.java)
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(i)
                else ctx.startService(i)
            }
        }

        fun stop(ctx: Context) {
            runCatching { ctx.stopService(Intent(ctx, GenerationService::class.java)) }
        }

        fun ensureChannels(ctx: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val nm = ctx.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CH_PROGRESS, "Průběh generování", NotificationManager.IMPORTANCE_LOW)
                    .apply { description = "Živý průběh běžícího generování" }
            )
            nm.createNotificationChannel(
                NotificationChannel(CH_DONE, "Hotová videa", NotificationManager.IMPORTANCE_HIGH)
                    .apply { description = "Upozornění, když je video hotové" }
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
                        item.isAudio -> "Skladba je hotová"
                        item.isImage -> "Obrázek je hotový"
                        else -> "Video je hotové"
                    }
                )
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(item.prompt.take(180)))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(openApp(ctx))
                .build()
            runCatching { NotificationManagerCompat.from(ctx).notify(NOTIF_DONE, n) }
        }

        fun notifyFailed(ctx: Context, message: String) {
            ensureChannels(ctx)
            val n = NotificationCompat.Builder(ctx, CH_DONE)
                .setSmallIcon(R.drawable.ic_stat_h3)
                .setContentTitle("Generování se nepovedlo")
                .setContentText(message.lineSequence().first())
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(openApp(ctx))
                .build()
            runCatching { NotificationManagerCompat.from(ctx).notify(NOTIF_DONE, n) }
        }

        fun formatEta(seconds: Int): String {
            val m = seconds / 60
            val s = seconds % 60
            return if (m > 0) "$m min ${s}s" else "${s}s"
        }
    }
}
