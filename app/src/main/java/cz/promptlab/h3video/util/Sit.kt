package cz.promptlab.h3video.util

import android.content.Context
import android.net.ConnectivityManager
import cz.promptlab.h3video.data.AppSettings

/**
 * Rozhodnutí, jestli velký výsledek stáhnout hned, nebo ho nechat na serveru
 * (5.03, tester: „velké video vyčerpá data“).
 *
 * Výchozí chování se nemění: velké výsledky se stahují všude. Odkládají se jen
 * na měřené síti (mobilní data, hotspot), a to když má uživatel zapnutý
 * Spořič dat Androidu, nebo v Nastavení vypnul „Velké výsledky přes mobilní
 * data“. Na Wi-Fi se nic neodkládá.
 */
object Sit {

    /** Nad tímhle se výsledek na měřené síti odloží (obrázky a krátká videa pod ním projdou). */
    const val PRAH_VELKYCH: Long = 20L * 1024 * 1024

    fun odlozitVelke(ctx: Context, settings: AppSettings): Boolean = runCatching {
        val cm = ctx.getSystemService(ConnectivityManager::class.java) ?: return false
        // U VPN (Tailscale) Android odvozuje měřenost od podkladové sítě.
        if (!cm.isActiveNetworkMetered) return false
        val sporic = cm.restrictBackgroundStatus == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED
        sporic || !settings.velkeNaDatech
    }.getOrDefault(false)
}
