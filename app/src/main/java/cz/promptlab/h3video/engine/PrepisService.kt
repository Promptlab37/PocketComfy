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
        // Po startForegroundService MUSÍ přijít startForeground, i když akce mezitím
        // skončila — jinak Android appku shodí. Proto nejdřív popředí, pak případně konec.
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
        naPopredi = ok
        if (!ok || !bezi) stopSelf()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        naPopredi = false
        super.onDestroy()
    }

    // Denní kvóta dataSync (Android 15): jen zavřít, proces nezabít.
    override fun onTimeout(startId: Int, fgsType: Int) {
        stopSelf()
    }

    companion object {
        private const val KANAL = "priprava"
        private const val NOTIF = 1003

        /** Běžící dlouhé akce (klíče). Služba běží, dokud tu něco je. */
        private val drzi = java.util.Collections.synchronizedSet(mutableSetOf<String>())
        @Volatile private var bezi = false
        /** Služba už je na popředí — teprve pak ji smí zastavit stopService (jinak by Android appku shodil). */
        @Volatile private var naPopredi = false

        private fun kanal(ctx: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(KANAL, t("Příprava na počítači"), NotificationManager.IMPORTANCE_LOW)
            )
        }

        /** Příprava začala / skončila. Volá se ze změn stavu přepisu. */
        fun nastav(ctx: Context, prave: Boolean) = drz(ctx, "prepis", prave)

        /**
         * Dlouhá akce [klic] začala / skončila (5.37, uživatel: „appka musí
         * fungovat, i když z ní odejdu, vždy“). Služba běží, dokud drží aspoň jedna.
         */
        fun drz(ctx: Context, klic: String, zapnuto: Boolean) = synchronized(drzi) {
            // Volá se z hlavního vlákna i z IO — kontrola a změna stavu musí být naráz (audit 30. 9. 2026).
            if (zapnuto) drzi.add(klic) else drzi.remove(klic)
            val prave = drzi.isNotEmpty()
            if (prave == bezi) return@synchronized
            bezi = prave
            val i = Intent(ctx, PrepisService::class.java)
            val ok = runCatching {
                if (prave) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(i) else ctx.startService(i)
                } else if (naPopredi) ctx.stopService(i)
                // Jinak se služba teprve spouští: v onStartCommand uvidí bezi = false a skončí sama.
            }.isSuccess
            // Start odmítnut (appka na pozadí) → příště to zkusit znovu.
            if (!ok && prave) bezi = false
        }
    }
}
