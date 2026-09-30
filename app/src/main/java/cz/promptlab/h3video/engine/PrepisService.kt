package cz.promptlab.h3video.engine

import cz.promptlab.h3video.data.t

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
import cz.promptlab.h3video.R

/**
 * Drží appku naživu, dokud se na počítači připravuje (čtení storyboardu,
 * přepis promptů). 30. 9. 2026: příprava Otevřených dveří trvala 15 minut,
 * ačkoli server pracoval necelé 3 — mezi kroky stál 10 a 3 minuty prázdný.
 * Přepis běží ve ViewModelu a bez služby na popředí ho Android po odchodu
 * z appky nebo zamčení telefonu zmrazí; generování videa tu službu mělo,
 * příprava ne.
 *
 * Start jde jen z popředí (Android 12+) — spouští se při ťuknutí na akci,
 * kdy je appka vidět. Když start selže, příprava jede dál jako dřív.
 */
class PrepisService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!bezi) {
            stopSelf()
            return START_NOT_STICKY
        }
        kanal(this)
        val n = NotificationCompat.Builder(this, KANAL)
            .setSmallIcon(R.drawable.ic_stat_h3)
            .setContentTitle(t("Připravuji…"))
            .setProgress(0, 0, true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 1, GenerationEngine.openIntent(this),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            )
            .build()
        val ok = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) startForeground(NOTIF, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            else startForeground(NOTIF, n)
        }.isSuccess
        if (!ok) stopSelf()
        return START_NOT_STICKY
    }

    // Denní kvóta dataSync (Android 15): jen zavřít, proces nezabít.
    override fun onTimeout(startId: Int, fgsType: Int) {
        stopSelf()
    }

    companion object {
        private const val KANAL = "priprava"
        private const val NOTIF = 1003

        @Volatile private var bezi = false

        private fun kanal(ctx: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(KANAL, t("Příprava na počítači"), NotificationManager.IMPORTANCE_LOW)
            )
        }

        /** Příprava začala / skončila. Volá se ze změn stavu přepisu. */
        fun nastav(ctx: Context, prave: Boolean) {
            if (prave == bezi) return
            bezi = prave
            val i = Intent(ctx, PrepisService::class.java)
            runCatching {
                if (prave) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(i) else ctx.startService(i)
                } else ctx.stopService(i)
            }
        }
    }
}
