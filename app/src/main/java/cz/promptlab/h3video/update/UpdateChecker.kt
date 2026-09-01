package cz.promptlab.h3video.update

import android.content.Context
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
    val versionCode: Int,
    val versionName: String,
    val notes: String,
    /** Adresa assetu v GitHub API, ne veřejný odkaz – privátní repo jinak nepustí. */
    val assetUrl: String,
    val sizeBytes: Long,
)

/**
 * Aktualizace přes GitHub Releases. S tokenem funguje i privátní repozitář
 * (osobní sestavení); bez tokenu jde totéž anonymně proti veřejnému repozitáři
 * – limit GitHubu (60 dotazů za hodinu na IP adresu) jedna kontrola denně ani
 * nenačne a APK se stahuje přes `browser_download_url`, který limit nemá.
 *
 * Číslo verze se bere ze značky vydání ve tvaru `v<versionCode>` (např. `v3`).
 */
object UpdateChecker {

    const val OWNER = "Promptlab37"
    const val REPO = "H3Video"
    private val API_LATEST = "https://api.github.com/repos/$OWNER/$REPO/releases/latest"

    /** Přesměrování si obsluhujeme sami, viz [downloadAsset]. */
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    fun currentVersionCode(ctx: Context): Int = runCatching {
        val info = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode.toInt()
        else @Suppress("DEPRECATION") info.versionCode
    }.getOrDefault(0)

    fun currentVersionName(ctx: Context): String = runCatching {
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "?"
    }.getOrDefault("?")

    /** Vrátí popis novější verze, nebo null když je nainstalovaná ta nejnovější. */
    fun check(ctx: Context, token: String): UpdateInfo? {
        val req = Request.Builder()
            .url(API_LATEST)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "H3Video")
            .apply { if (token.isNotBlank()) header("Authorization", "Bearer $token") }
            .build()

        http.newCall(req).execute().use { r ->
            if (r.code == 401 || r.code == 403) throw IllegalStateException(
                if (token.isBlank())
                    "GitHub odmítl anonymní dotaz (${r.code}). Zkus to za chvíli znovu."
                else
                    "GitHub token odmítl přístup (${r.code}). Zkontroluj, že je platný a má právo na repozitář."
            )
            if (r.code == 404) throw IllegalStateException(
                if (token.isBlank())
                    "Vydání se nenašlo. Privátní repozitář jde číst jen s tokenem – vlož ho níž."
                else
                    "Repozitář $OWNER/$REPO nebo jeho vydání se nenašlo."
            )
            if (!r.isSuccessful) throw IllegalStateException("GitHub odpověděl ${r.code}")

            val j = JSONObject(r.body!!.string())
            val tag = j.optString("tag_name")
            val code = tag.trimStart('v', 'V').substringBefore('.').toIntOrNull()
                ?: throw IllegalStateException("Vydání „$tag\" nemá čitelné číslo verze")

            val assets = j.optJSONArray("assets")
                ?: throw IllegalStateException("Vydání neobsahuje soubor APK")
            var url: String? = null
            var size = 0L
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.optString("name").endsWith(".apk", ignoreCase = true)) {
                    url = a.optString("url")          // API adresa, ne browser_download_url
                    size = a.optLong("size")
                    break
                }
            }
            if (url.isNullOrBlank()) throw IllegalStateException("Vydání neobsahuje soubor APK")

            if (code <= currentVersionCode(ctx)) return null
            return UpdateInfo(
                versionCode = code,
                versionName = j.optString("name").ifBlank { tag },
                notes = j.optString("body").trim(),
                assetUrl = url,
                sizeBytes = size,
            )
        }
    }

    /**
     * Stáhne APK. GitHub na asset odpoví přesměrováním na podepsanou adresu úložiště,
     * kam se autorizační hlavička posílat NESMÍ – jinak ji úložiště odmítne. Proto se
     * přesměrování obsluhuje ručně a druhý požadavek jde bez tokenu.
     */
    fun download(ctx: Context, info: UpdateInfo, token: String, onProgress: (Float) -> Unit): File {
        val first = Request.Builder()
            .url(info.assetUrl)
            .header("Accept", "application/octet-stream")
            .header("User-Agent", "H3Video")
            .apply { if (token.isNotBlank()) header("Authorization", "Bearer $token") }
            .build()

        var response = http.newCall(first).execute()
        if (response.code in 300..399) {
            val location = response.header("Location")
            response.close()
            if (location.isNullOrBlank()) throw IllegalStateException("GitHub nevrátil adresu souboru")
            response = http.newCall(
                Request.Builder().url(location).header("User-Agent", "H3Video").build()
            ).execute()
        }

        response.use { r ->
            if (!r.isSuccessful) throw IllegalStateException("Stažení selhalo (${r.code})")
            val target = File(ctx.cacheDir, "update-${info.versionCode}.apk")
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
            if (target.exists()) target.delete()
            if (!tmp.renameTo(target)) {
                tmp.copyTo(target, overwrite = true)
                tmp.delete()
            }
            return target
        }
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
