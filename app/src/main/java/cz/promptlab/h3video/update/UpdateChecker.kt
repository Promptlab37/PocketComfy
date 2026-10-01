package cz.promptlab.h3video.update

import android.content.Context
import cz.promptlab.h3video.data.t
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

data class UpdateInfo(
    /** Značka vydání, jak je na GitHubu. Slouží i k pojmenování staženého souboru. */
    val znacka: String,
    val versionName: String,
    val notes: String,
    /** Veřejný odkaz, nebo adresa assetu v API pro soukromý repozitář. */
    val assetUrl: String,
    val sizeBytes: Long,
    /** SHA-256 APK z poznámek vydání (řádek `SHA-256: …`), null = vydání ho neuvádí. */
    val sha256: String? = null,
)

/**
 * Aktualizace přes GitHub Releases. S tokenem funguje i privátní repozitář
 * (osobní sestavení); bez tokenu jde totéž anonymně proti veřejnému repozitáři
 * – limit GitHubu (60 dotazů za hodinu na IP adresu) jedna kontrola denně ani
 * nenačne a APK se stahuje přes `browser_download_url`, který limit nemá.
 *
 * Verze se bere ze značky vydání, viz [jeNovejsiVydani].
 */
object UpdateChecker {

    const val OWNER = "Promptlab37"
    const val REPO = "PocketComfy"
    private const val PRIVATE_REPO = "H3Video"

    private fun repository(token: String): String = if (token.isBlank()) REPO else PRIVATE_REPO

    internal fun latestRequest(token: String): Request = Request.Builder()
        .url("https://api.github.com/repos/$OWNER/${repository(token)}/releases/latest")
        .header("Accept", "application/vnd.github+json")
        .header("User-Agent", "PocketComfy")
        .header("Cache-Control", "no-cache")
        .apply { if (token.isNotBlank()) header("Authorization", "Bearer $token") }
        .build()

    internal fun assetDownloadUrl(asset: JSONObject, token: String): String =
        asset.optString(if (token.isBlank()) "browser_download_url" else "url")

    internal fun assetDownloadRequest(assetUrl: String, token: String): Request {
        val builder = Request.Builder().url(assetUrl)
            .header("Accept", "application/octet-stream")
            .header("User-Agent", "PocketComfy")
        val url = builder.build().url
        // Token patří výhradně do API našeho soukromého repozitáře.
        if (token.isNotBlank() && url.isHttps && url.host == "api.github.com" &&
            url.encodedPath.startsWith("/repos/$OWNER/$PRIVATE_REPO/releases/assets/")) {
            builder.header("Authorization", "Bearer $token")
        }
        return builder.build()
    }

    /** Přesměrování si obsluhujeme sami, viz [download]. */
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    private val checkHttp = http.newBuilder().callTimeout(25, TimeUnit.SECONDS).build()

    fun currentVersionCode(ctx: Context): Int = runCatching {
        val info = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode.toInt()
        else @Suppress("DEPRECATION") info.versionCode
    }.getOrDefault(0)

    /**
     * Je vydání se značkou [znacka] novější než nainstalovaná aplikace?
     *
     * Snese oba tvary, které se v repozitáři objevily:
     *  - `v131` — přímo číslo sestavení, porovná se s [kodTed];
     *  - `v3.19` — jméno verze, porovná se po částech s [jmenoTed].
     *
     * Dřív se ze značky brávalo jen to, co je před tečkou. Ze značky `v3.19`
     * tak vyšlo „3", což je proti sestavení 129 méně — a aplikace mlčky
     * hlásila, že je aktuální, i když vydání bylo nové. Neznámý tvar proto
     * teď raději spadne s hláškou, než aby se tvářil, že není co stahovat.
     */
    fun jeNovejsiVydani(znacka: String, kodTed: Int, jmenoTed: String): Boolean {
        val cislo = znacka.trimStart('v', 'V').trim()
        val casti = cislo.split('.')
        if (casti.any { it.toIntOrNull() == null }) throw IllegalStateException(
            t("Vydání „%s\" nemá čitelné číslo verze").format(znacka)
        )
        if (casti.size == 1) return casti[0].toInt() > kodTed

        val ted = jmenoTed.trim().split('.').map { it.toIntOrNull() ?: 0 }
        val nove = casti.map { it.toInt() }
        for (i in 0 until maxOf(ted.size, nove.size)) {
            val a = nove.getOrElse(i) { 0 }
            val b = ted.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    fun currentVersionName(ctx: Context): String = runCatching {
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "?"
    }.getOrDefault("?")

    /** Vrátí popis novější verze, nebo null když je nainstalovaná ta nejnovější. */
    fun check(ctx: Context, token: String): UpdateInfo? {
        val req = latestRequest(token)

        checkHttp.newCall(req).execute().use { r ->
            if (r.code == 401 || r.code == 403) throw IllegalStateException(
                if (token.isBlank())
                    t("GitHub odmítl anonymní dotaz (%d). Zkus to za chvíli znovu.").format(r.code)
                else
                    t("GitHub token odmítl přístup (%d). Zkontroluj, že je platný a má právo na repozitář.").format(r.code)
            )
            if (r.code == 404) throw IllegalStateException(
                if (token.isBlank())
                    t("Veřejné vydání se zatím nenašlo. Zkus to později.")
                else
                    t("Repozitář %s nebo jeho vydání se nenašlo.").format("$OWNER/${repository(token)}")
            )
            if (!r.isSuccessful) throw IllegalStateException(t("GitHub odpověděl %d").format(r.code))

            val j = JSONObject(r.body!!.string())
            val tag = j.optString("tag_name")
            val novejsi = jeNovejsiVydani(tag, currentVersionCode(ctx), currentVersionName(ctx))

            val assets = j.optJSONArray("assets")
                ?: throw IllegalStateException(t("Vydání neobsahuje soubor APK"))
            var url: String? = null
            var size = 0L
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.optString("name").endsWith(".apk", ignoreCase = true)) {
                    url = assetDownloadUrl(a, token)
                    size = a.optLong("size")
                    break
                }
            }
            if (url.isNullOrBlank()) throw IllegalStateException(t("Vydání neobsahuje soubor APK"))

            if (!novejsi) return null
            return UpdateInfo(
                znacka = tag,
                versionName = j.optString("name").ifBlank { tag },
                notes = j.optString("body").trim(),
                assetUrl = url,
                sizeBytes = size,
                sha256 = sha256ZPoznamek(j.optString("body")),
            )
        }
    }

    /**
     * Stáhne APK. GitHub na asset odpoví přesměrováním na podepsanou adresu úložiště,
     * kam se autorizační hlavička posílat NESMÍ – jinak ji úložiště odmítne. Proto se
     * přesměrování obsluhuje ručně a druhý požadavek jde bez tokenu.
     */
    fun download(ctx: Context, info: UpdateInfo, token: String, onProgress: (Float) -> Unit): File {
        val first = assetDownloadRequest(info.assetUrl, token)

        var response = http.newCall(first).execute()
        if (response.code in 300..399) {
            val location = response.header("Location")
            response.close()
            if (location.isNullOrBlank()) throw IllegalStateException(t("GitHub nevrátil adresu souboru"))
            // Přesměrování musí zůstat na HTTPS – přes holé http by šlo APK podstrčit.
            if (!location.startsWith("https://")) throw IllegalStateException(
                t("Přesměrování nevede na zabezpečenou adresu – stažení zrušeno.")
            )
            response = http.newCall(
                Request.Builder().url(location).header("User-Agent", "H3Video").build()
            ).execute()
        }

        response.use { r ->
            if (!r.isSuccessful) throw IllegalStateException(t("Stažení selhalo (%d)").format(r.code))
            val nazev = info.znacka.filter { it.isLetterOrDigit() || it == '.' }
            val target = File(ctx.cacheDir, "update-$nazev.apk")
            val tmp = File(ctx.cacheDir, target.name + ".part")
            val total = if (info.sizeBytes > 0) info.sizeBytes else r.body!!.contentLength()
            r.body!!.byteStream().use { input ->
                tmp.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var read: Int
                    var done = 0L
                    while (input.read(buf).also { read = it } != -1) {
                        out.write(buf, 0, read)
                        done += read
                        if (total > 0) onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
            // Když vydání uvádí kontrolní součet, soubor se s ním porovná.
            // Integritu jinak hlídá jen Android (stejný podpis) – tohle
            // odchytí poškozený nebo zaměněný soubor dřív než instalátor.
            info.sha256?.let { ocekavany ->
                val skutecny = sha256Souboru(tmp)
                if (!skutecny.equals(ocekavany, ignoreCase = true)) {
                    tmp.delete()
                    throw IllegalStateException(
                        t("Stažený soubor neodpovídá kontrolnímu součtu z vydání – instalace zrušena.")
                    )
                }
            }
            if (target.exists()) target.delete()
            if (!tmp.renameTo(target)) {
                tmp.copyTo(target, overwrite = true)
                tmp.delete()
            }
            return target
        }
    }

    /**
     * Konečná adresa souboru: přesměrování GitHubu obslouží ručně (token jen
     * do API, cíl musí být HTTPS). Neotevírá tělo souboru.
     */
    private fun konecnaAdresa(info: UpdateInfo, token: String): String {
        var req = assetDownloadRequest(info.assetUrl, token)
        repeat(5) {
            val r = http.newCall(req).execute()
            val kod = r.code
            val location = r.header("Location")
            r.close()
            if (kod !in 300..399) return req.url.toString()
            if (location.isNullOrBlank()) throw IllegalStateException(t("GitHub nevrátil adresu souboru"))
            if (!location.startsWith("https://")) throw IllegalStateException(
                t("Přesměrování nevede na zabezpečenou adresu – stažení zrušeno.")
            )
            req = Request.Builder().url(location).header("User-Agent", "PocketComfy").build()
        }
        return req.url.toString()
    }

    /**
     * Stažení přes systémového správce stahování (5.39, uživatel: „když z appky
     * odejdu, musí to fungovat dál“). Stahuje Android sám, mimo appku — doběhne
     * i po odchodu, zamčení nebo ukončení appky; rozdělané stažení stejné verze
     * se při dalším ťuknutí převezme. Kontrolní součet se ověří stejně jako dřív.
     */
    fun downloadSystemem(ctx: Context, info: UpdateInfo, token: String, onProgress: (Float) -> Unit): File {
        val dm = ctx.getSystemService(android.app.DownloadManager::class.java)
            ?: throw IllegalStateException("DownloadManager")
        val prefs = ctx.getSharedPreferences("aktualizace_stahovani", Context.MODE_PRIVATE)
        val nazev = "update-" + info.znacka.filter { it.isLetterOrDigit() || it == '.' } + ".apk"
        val slozka = ctx.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)
            ?: throw IllegalStateException("úložiště")
        val stazeny = File(slozka, nazev)
        val klic = "id_" + info.znacka
        fun stav(id: Long): Pair<Int, Float>? {
            dm.query(android.app.DownloadManager.Query().setFilterById(id))?.use { c ->
                if (!c.moveToFirst()) return null
                val st = c.getInt(c.getColumnIndexOrThrow(android.app.DownloadManager.COLUMN_STATUS))
                val hotovo = c.getLong(c.getColumnIndexOrThrow(android.app.DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                val celkem = c.getLong(c.getColumnIndexOrThrow(android.app.DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                    .takeIf { it > 0 } ?: info.sizeBytes
                return st to (if (celkem > 0) (hotovo.toFloat() / celkem).coerceIn(0f, 1f) else 0f)
            }
            return null
        }
        var id = prefs.getLong(klic, -1L)
        // Rozdělané nebo hotové stažení téže verze převzít; spadlé zahodit.
        if (id >= 0) {
            val s = stav(id)
            if (s == null || s.first == android.app.DownloadManager.STATUS_FAILED) {
                runCatching { dm.remove(id) }
                id = -1L
            }
        }
        if (id < 0) {
            slozka.listFiles()?.filter { it.name.startsWith(nazev.removeSuffix(".apk")) }?.forEach { it.delete() }
            val req = android.app.DownloadManager.Request(android.net.Uri.parse(konecnaAdresa(info, token)))
                .setTitle("PocketComfy " + info.versionName)
                .setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE)
                .setDestinationInExternalFilesDir(ctx, android.os.Environment.DIRECTORY_DOWNLOADS, nazev)
                .addRequestHeader("User-Agent", "PocketComfy")
            id = dm.enqueue(req)
            prefs.edit().putLong(klic, id).apply()
        }
        // Když se stahování 3 minuty nepohne (čeká na síť / Wi-Fi), vzdát to — appka pak
        // stáhne sama. Dřív „Stahuji“ viselo navždy (audit 30. 9. 2026).
        var posledni = -1f
        var odZmeny = System.currentTimeMillis()
        while (true) {
            val s = stav(id) ?: run {
                prefs.edit().remove(klic).apply()
                throw IllegalStateException(t("Stahování bylo zrušeno."))
            }
            onProgress(s.second)
            if (s.second != posledni) { posledni = s.second; odZmeny = System.currentTimeMillis() }
            else if (System.currentTimeMillis() - odZmeny > 180_000 &&
                s.first != android.app.DownloadManager.STATUS_SUCCESSFUL
            ) {
                runCatching { dm.remove(id) }
                prefs.edit().remove(klic).apply()
                throw IllegalStateException(t("Stahování se zaseklo, zkouším jinak."))
            }
            when (s.first) {
                android.app.DownloadManager.STATUS_SUCCESSFUL -> break
                android.app.DownloadManager.STATUS_FAILED -> {
                    runCatching { dm.remove(id) }
                    prefs.edit().remove(klic).apply()
                    throw IllegalStateException(t("Stažení selhalo, zkus to znovu."))
                }
            }
            Thread.sleep(700)
        }
        prefs.edit().remove(klic).apply()
        // Skutečné umístění od správce stahování — při shodě jmen ukládá jako „…-1.apk“.
        val soubor = dm.query(android.app.DownloadManager.Query().setFilterById(id))?.use { c ->
            if (!c.moveToFirst()) null
            else c.getString(c.getColumnIndexOrThrow(android.app.DownloadManager.COLUMN_LOCAL_URI))
                ?.let { android.net.Uri.parse(it).path }?.let { File(it) }
        }?.takeIf { it.exists() } ?: stazeny
        info.sha256?.let { ocekavany ->
            if (!sha256Souboru(soubor).equals(ocekavany, ignoreCase = true)) {
                runCatching { dm.remove(id) }
                throw IllegalStateException(
                    t("Stažený soubor neodpovídá kontrolnímu součtu z vydání – instalace zrušena.")
                )
            }
        }
        // Instalátor čte z cache (FileProvider) — přesunout tam.
        val target = File(ctx.cacheDir, nazev)
        if (target.exists()) target.delete()
        soubor.copyTo(target, overwrite = true)
        runCatching { dm.remove(id) }
        soubor.delete()
        return target
    }

    /** Placená / omezená síť (mobilní data) — tam se aktualizace sama nestahuje. */
    fun meritkovaSit(ctx: Context): Boolean = runCatching {
        ctx.getSystemService(android.net.ConnectivityManager::class.java)?.isActiveNetworkMetered ?: true
    }.getOrDefault(true)

    /** Už stažená verze v cache s platným kontrolním součtem, jinak null. */
    fun stazeno(ctx: Context, info: UpdateInfo): File? {
        val f = File(ctx.cacheDir, "update-" + info.znacka.filter { it.isLetterOrDigit() || it == '.' } + ".apk")
        if (!f.exists() || f.length() == 0L) return null
        if (info.sizeBytes > 0 && f.length() != info.sizeBytes) return null
        val ok = info.sha256?.let { runCatching { sha256Souboru(f).equals(it, ignoreCase = true) }.getOrDefault(false) } ?: true
        return if (ok) f else null
    }

    /** Starší stažené verze z cache pryč — každá má desítky MB. */
    fun uklidStare(ctx: Context, nechat: File) {
        ctx.cacheDir.listFiles()?.filter { it.name.startsWith("update-") && it.name.endsWith(".apk") && it.name != nechat.name }
            ?.forEach { runCatching { it.delete() } }
    }

    private const val KANAL = "aktualizace"
    private const val ID_OZNAMENI = 4207

    /**
     * Oznámení „Aktualizace připravena“ — klepnutí rovnou otevře instalaci (5.58).
     * Bez povolení instalovat z appky otevře appku, kde okno vede k povolení.
     */
    fun oznamPripraveno(ctx: Context, info: UpdateInfo, apk: File) {
        uklidStare(ctx, apk)
        runCatching {
            val nm = ctx.getSystemService(android.app.NotificationManager::class.java) ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) nm.createNotificationChannel(
                android.app.NotificationChannel(KANAL, t("Aktualizace"), android.app.NotificationManager.IMPORTANCE_DEFAULT)
            )
            val cil = if (canInstall(ctx)) installIntent(ctx, apk)
            else (ctx.packageManager.getLaunchIntentForPackage(ctx.packageName) ?: return)
            val pi = android.app.PendingIntent.getActivity(
                ctx, ID_OZNAMENI, cil,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
            )
            val n = androidx.core.app.NotificationCompat.Builder(ctx, KANAL)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle(t("Aktualizace připravena"))
                .setContentText(t("Nainstalovat %s").format(info.versionName))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build()
            nm.notify(ID_OZNAMENI, n)
        }
    }

    /** SHA-256 z poznámek vydání: `SHA-256: <64 hex>`, `sha256=…` i v backticku. */
    internal fun sha256ZPoznamek(notes: String): String? =
        Regex("""(?i)sha-?256[^0-9a-f]{0,8}([0-9a-f]{64})""")
            .find(notes)?.groupValues?.get(1)?.lowercase()

    internal fun sha256Souboru(f: File): String {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        f.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            var read: Int
            while (input.read(buf).also { read = it } != -1) md.update(buf, 0, read)
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    /** Smí aplikace vůbec spustit instalaci? Od Androidu 8 je to zvlášť povolení. */
    fun canInstall(ctx: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            ctx.packageManager.canRequestPackageInstalls() else true

    fun unknownSourcesIntent(ctx: Context): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun installIntent(ctx: Context, apk: File): Intent {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", apk)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
