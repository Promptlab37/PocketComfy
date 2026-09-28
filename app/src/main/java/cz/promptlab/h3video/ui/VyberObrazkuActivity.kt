package cz.promptlab.h3video.ui

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import cz.promptlab.h3video.comfy.BingHledani
import cz.promptlab.h3video.comfy.ComfyClient
import cz.promptlab.h3video.comfy.ComfyException
import cz.promptlab.h3video.data.AppSettings
import cz.promptlab.h3video.data.t
import cz.promptlab.h3video.ui.theme.Cyan
import cz.promptlab.h3video.ui.theme.Danger
import cz.promptlab.h3video.ui.theme.H3Theme
import cz.promptlab.h3video.ui.theme.Ink
import cz.promptlab.h3video.ui.theme.Outline1
import cz.promptlab.h3video.ui.theme.Surface1
import cz.promptlab.h3video.ui.theme.Surface2
import cz.promptlab.h3video.ui.theme.TextHi
import cz.promptlab.h3video.ui.theme.TextLow
import cz.promptlab.h3video.ui.theme.TextMid
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Výběr obrázku: z telefonu, nebo hledáním na internetu (Bing přes server).
 *
 * Spouští ho [VyberMedii] / [VyberViceMedii] místo systémového výběru, takže
 * platí pro všechny karty. Volba „Z telefonu" jen předá dál původní výběr
 * (Galerie nebo Soubory) a vrátí jeho výsledek beze změny. Z Bingu se vybrané
 * obrázky stáhnou do cache a vrátí jako `content://` adresy — karta s nimi
 * pak zachází stejně jako s fotkou z galerie.
 */
class VyberObrazkuActivity : ComponentActivity() {

    companion object {
        private const val EXTRA_ZDROJ = "zdroj"
        private const val EXTRA_MAX = "max"
        private const val DIR = "bing"
        private const val KEEP_MS = 24L * 60 * 60 * 1000

        /** [zdroj] = původní výběr z telefonu, [max] = kolik obrázků smí vybrat. */
        fun intent(ctx: Context, zdroj: Intent, max: Int): Intent =
            Intent(ctx, VyberObrazkuActivity::class.java)
                .putExtra(EXTRA_ZDROJ, zdroj)
                .putExtra(EXTRA_MAX, max)
    }

    private val zTelefonu = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { r ->
        setResult(r.resultCode, r.data?.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val max = intent.getIntExtra(EXTRA_MAX, 1).coerceIn(1, BingHledani.POCET)
        setContent {
            var bing by rememberSaveable { mutableStateOf(false) }
            if (!bing) {
                Volba(
                    onTelefon = { otevriTelefon() },
                    onBing = { bing = true },
                    onZavrit = { zrusit() },
                )
            } else {
                cz.promptlab.h3video.ui.theme.Vzhled.motiv =
                    cz.promptlab.h3video.ui.theme.Motiv.zUlozeneho(cz.promptlab.h3video.data.AppSettings(this).vzhled)
                H3Theme {
                    BingObrazovka(
                        max = max,
                        onHotovo = { vrat(it) },
                        onZavrit = { zrusit() },
                    )
                }
            }
        }
    }

    private fun otevriTelefon() {
        val zdroj = if (Build.VERSION.SDK_INT >= 33)
            intent.getParcelableExtra(EXTRA_ZDROJ, Intent::class.java)
        else @Suppress("DEPRECATION") intent.getParcelableExtra(EXTRA_ZDROJ)
        if (zdroj == null) { zrusit(); return }
        runCatching { zTelefonu.launch(zdroj) }.onFailure { zrusit() }
    }

    private fun zrusit() {
        setResult(Activity.RESULT_CANCELED)
        finish()
    }

    /** Vrátí adresy stejně jako systémový výběr: první v `data`, všechny v `clipData`. */
    private fun vrat(uris: List<Uri>) {
        if (uris.isEmpty()) { zrusit(); return }
        val data = Intent().setData(uris.first()).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        data.clipData = ClipData.newRawUri(null, uris.first()).apply {
            uris.drop(1).forEach { addItem(ClipData.Item(it)) }
        }
        setResult(Activity.RESULT_OK, data)
        finish()
    }

    /** Stáhne vybrané obrázky do cache a vrátí jejich adresy přes FileProvider. */
    internal fun stahni(base: String, session: String, ids: List<String>, onKus: (Int) -> Unit): List<Uri> {
        val dir = File(cacheDir, DIR).apply { mkdirs() }
        val limit = System.currentTimeMillis() - KEEP_MS
        dir.listFiles()?.forEach { if (it.lastModified() < limit) it.delete() }
        val client = ComfyClient(base)
        return ids.mapIndexed { i, id ->
            onKus(i)
            val soubor = File(dir, "bing_$id.png")
            client.download(BingHledani.plnyUrl(base, session, id), soubor) { _, _ -> }
            FileProvider.getUriForFile(this, "$packageName.fileprovider", soubor)
        }
    }
}

/** Úvodní volba — přes průhledné okno, pod ní je vidět karta, ze které přišla. */
@Composable
private fun Volba(onTelefon: () -> Unit, onBing: () -> Unit, onZavrit: () -> Unit) {
    BackHandler { onZavrit() }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = .55f))
            .clickable(onClick = onZavrit),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .background(Surface1)
                .clickable(enabled = false) {}
                .navigationBarsPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            VolbaRadek(Icons.Default.PhotoLibrary, t("Z telefonu"), onTelefon)
            VolbaRadek(Icons.Default.Search, t("Hledat na internetu"), onBing)
        }
    }
}

@Composable
private fun VolbaRadek(ikona: androidx.compose.ui.graphics.vector.ImageVector, text: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Surface2)
            .border(1.dp, Outline1, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(ikona, null, Modifier.size(24.dp), Cyan)
        Spacer(Modifier.width(14.dp))
        Text(text, style = MaterialTheme.typography.titleMedium, color = TextHi)
    }
}

/** Fáze čekání — ukazuje se text, uběhlý čas a odhad podle minulého hledání. */
private enum class Faze(val textCs: String) {
    START("Zapínám ComfyUI na počítači"),
    HLEDANI("Hledám a stahuji obrázky"),
    PRENOS("Přenáším do telefonu %d / %d"),
}

@Composable
private fun VyberObrazkuActivity.BingObrazovka(max: Int, onHotovo: (List<Uri>) -> Unit, onZavrit: () -> Unit) {
    val nastaveni = remember { AppSettings(this) }
    val base = remember { nastaveni.serverUrl.trim() }
    val prefs = remember { getSharedPreferences("bing_hledani", Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()

    var dotaz by rememberSaveable { mutableStateOf("") }
    var session by rememberSaveable { mutableStateOf("") }
    var ids by rememberSaveable { mutableStateOf(arrayListOf<String>()) }
    var vybrane by rememberSaveable { mutableStateOf(arrayListOf<String>()) }
    var chyba by rememberSaveable { mutableStateOf<String?>(null) }
    var faze by remember { mutableStateOf<Faze?>(null) }
    var kus by remember { mutableStateOf(0) }
    var od by remember { mutableLongStateOf(0L) }
    var prace by remember { mutableStateOf<Job?>(null) }

    BackHandler { prace?.cancel(); onZavrit() }

    fun hledej() {
        val q = dotaz.trim()
        if (q.isEmpty() || prace?.isActive == true) return
        if (base.isEmpty()) { chyba = t("Nejdřív v Nastavení vyplň adresu serveru."); return }
        chyba = null
        prace = scope.launch {
            od = System.currentTimeMillis()
            try {
                val client = ComfyClient(base)
                if (!withContext(Dispatchers.IO) { client.isAlive() }) {
                    faze = Faze.START
                    zapniComfy(client)
                }
                faze = Faze.HLEDANI
                val zacatek = System.currentTimeMillis()
                val v = withContext(Dispatchers.IO) { BingHledani.hledej(base, q) }
                prefs.edit().putLong("trvani_ms", System.currentTimeMillis() - zacatek).apply()
                session = v.session
                ids = ArrayList(v.obrazky.map { it.id })
                vybrane = arrayListOf()
                if (ids.isEmpty()) chyba = t("Nic se nenašlo.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: ComfyException) {
                chyba = t(e.userMessage)
            } catch (e: Exception) {
                chyba = t("Server neodpovídá. Zkontroluj, že je počítač zapnutý a v telefonu běží Tailscale.")
            } finally {
                faze = null
            }
        }
    }

    fun pouzij() {
        if (vybrane.isEmpty() || prace?.isActive == true) return
        chyba = null
        val sel = vybrane.toList()
        prace = scope.launch {
            od = System.currentTimeMillis()
            faze = Faze.PRENOS
            try {
                val uris = withContext(Dispatchers.IO) { stahni(base, session, sel) { kus = it } }
                onHotovo(uris)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                chyba = t("Obrázek se nepodařilo přenést. Zkus hledat znovu.")
            } finally {
                faze = null
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Ink)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 16.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                t("Hledat na internetu"),
                style = MaterialTheme.typography.titleLarge,
                color = TextHi,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { prace?.cancel(); onZavrit() }) {
                Icon(Icons.Default.Close, t("Zavřít"), tint = TextMid)
            }
        }

        DarkTextField(
            value = dotaz,
            onValueChange = { dotaz = it },
            placeholder = t("Co hledáš"),
            minHeight = 56.dp,
            singleLine = true,
            onClear = { dotaz = "" },
        )
        Spacer(Modifier.height(10.dp))
        GradientButton(
            text = t("Hledat"),
            enabled = dotaz.isNotBlank() && faze == null,
            onClick = { hledej() },
        )

        faze?.let { f ->
            Spacer(Modifier.height(12.dp))
            Prubeh(f, od, kus, vybrane.size, prefs.getLong("trvani_ms", 0L))
        }
        chyba?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, style = MaterialTheme.typography.bodyMedium, color = Danger)
        }

        Spacer(Modifier.height(12.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(ids, key = { it }) { id ->
                Nahled(
                    url = BingHledani.nahledUrl(base, session, id),
                    poradi = vybrane.indexOf(id).takeIf { it >= 0 }?.plus(1),
                    ukazPoradi = max > 1,
                    onClick = { if (faze == null) vybrane = ArrayList(BingHledani.prepni(vybrane, id, max)) },
                )
            }
        }

        if (ids.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            GradientButton(
                text = if (max > 1 && vybrane.isNotEmpty()) t("Použít (%d)").format(vybrane.size) else t("Použít"),
                enabled = vybrane.isNotEmpty() && faze == null,
                onClick = { pouzij() },
            )
            Spacer(Modifier.height(10.dp))
        }
    }
}

/** Čeká, až spouštěč na počítači nahodí ComfyUI (stejně jako generování). */
private suspend fun zapniComfy(client: ComfyClient) = withContext(Dispatchers.IO) {
    if (client.launcherStav() == "running") return@withContext
    if (!client.requestServerStart() && !client.launcherAlive()) throw ComfyException(
        "launcher offline",
        "Počítač neodpovídá. Zkontroluj, že je zapnutý a přihlášený a že máš v telefonu zapnutý Tailscale.",
    )
    val od = System.currentTimeMillis()
    while (System.currentTimeMillis() - od < 6 * 60_000L) {
        delay(3000)
        if (client.isAlive()) return@withContext
    }
    throw ComfyException("server offline", "ComfyUI se na počítači nerozjelo ani po šesti minutách.")
}

@Composable
private fun Prubeh(faze: Faze, od: Long, kus: Int, celkem: Int, minule: Long) {
    var ted by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(od) {
        while (true) { ted = System.currentTimeMillis(); delay(1000) }
    }
    val ubehlo = ((ted - od) / 1000).coerceAtLeast(0)
    val text = if (faze == Faze.PRENOS) t(faze.textCs).format(kus + 1, celkem) else t(faze.textCs)
    Column {
        LinearProgressIndicator(Modifier.fillMaxWidth(), color = Cyan, trackColor = Surface2)
        Spacer(Modifier.height(6.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = TextHi)
        val zbyva = if (faze == Faze.HLEDANI && minule > 0) minule / 1000 - ubehlo else -1
        Text(
            if (zbyva > 0) t("Uběhlo %s · zbývá asi %s").format(minSek(ubehlo), minSek(zbyva))
            else t("Uběhlo %s").format(minSek(ubehlo)),
            style = MaterialTheme.typography.bodySmall,
            color = TextLow,
        )
    }
}

/** Náhled z `/bing_image_selector/thumbnail`. Číslo = pořadí výběru. */
@Composable
private fun Nahled(url: String, poradi: Int?, ukazPoradi: Boolean, onClick: () -> Unit) {
    val bitmapa by produceState<Bitmap?>(null, url) {
        value = withContext(Dispatchers.IO) { nactiNahled(url) }
    }
    val vybrany = poradi != null
    Box(
        Modifier
            .aspectRatio(4f / 3f)
            .clip(RoundedCornerShape(10.dp))
            .background(Surface2)
            .border(if (vybrany) 3.dp else 1.dp, if (vybrany) Cyan else Outline1, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
    ) {
        bitmapa?.let {
            Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        if (vybrany) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(5.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Cyan),
                contentAlignment = Alignment.Center,
            ) {
                Text(if (ukazPoradi) "$poradi" else "✓", style = MaterialTheme.typography.labelMedium, color = Ink)
            }
        }
    }
}

private val NAHLEDY: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
}

private fun nactiNahled(url: String): Bitmap? = runCatching {
    NAHLEDY.newCall(Request.Builder().url(url).build()).execute().use { r ->
        if (!r.isSuccessful) null else r.body?.bytes()?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
    }
}.getOrNull()
