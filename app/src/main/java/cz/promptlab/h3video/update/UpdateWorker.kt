package cz.promptlab.h3video.update

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import cz.promptlab.h3video.data.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Aktualizace se stáhne sama na pozadí, i když appka není otevřená (5.68). Do 5.67 se nová verze
 * hledala jen při spuštění appky — uživatel ji pak viděl stahovat se „naživo“ (2. 10. 2026: „to už
 * mělo být dávno stažené a čekat v appce“). Teď Android jednou za pár hodin na Wi-Fi zkontroluje
 * GitHub, verzi stáhne appka sama (bez systémového oznámení stahování) a ohlásí až hotovou
 * „Aktualizace připravena“. Instalace dál jen po klepnutí uživatele.
 */
class UpdateWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val ctx = applicationContext
        runCatching {
            val info = UpdateChecker.check(ctx, AppSettings(ctx).githubToken) ?: return@runCatching
            val prefs = ctx.getSharedPreferences("aktualizace_stahovani", Context.MODE_PRIVATE)
            val apk = UpdateChecker.stazeno(ctx, info) ?: UpdateChecker.download(ctx, info, AppSettings(ctx).githubToken) { }
            // Stejnou verzi ohlásit jen jednou, ne při každé kontrole.
            if (prefs.getString(OZNAMENO, null) != info.znacka) {
                UpdateChecker.oznamPripraveno(ctx, info, apk)
                prefs.edit().putString(OZNAMENO, info.znacka).apply()
            }
        }.fold(onSuccess = { Result.success() }, onFailure = { Result.retry() })
    }

    companion object {
        private const val PRACE = "aktualizace_na_pozadi"
        private const val OZNAMENO = "oznameno_pripraveno"

        /** Naplánuje kontrolu jednou za 6 h — jen na Wi-Fi (neměřená síť) a při nevybité baterii. */
        fun naplanuj(ctx: Context) {
            val podminky = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.UNMETERED)
                .setRequiresBatteryNotLow(true)
                .build()
            val prace = PeriodicWorkRequestBuilder<UpdateWorker>(6, TimeUnit.HOURS, 1, TimeUnit.HOURS)
                .setConstraints(podminky)
                .build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(PRACE, ExistingPeriodicWorkPolicy.KEEP, prace)
        }
    }
}
