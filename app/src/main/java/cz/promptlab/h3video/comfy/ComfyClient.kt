package cz.promptlab.h3video.comfy

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

class ComfyException(message: String, val userMessage: String = message) : Exception(message)

/** Tenký klient nad ComfyUI HTTP+WebSocket API. */
class ComfyClient(baseUrl: String) {

    val base: String = baseUrl.trimEnd('/')

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    /** Krátké čekání jen pro dotaz „žiješ?" – viz [isAlive]. */
    private val pingClient = http.newBuilder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build()

    private val socketClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    // ---------------------------------------------------------------- základní

    fun systemStats(): JSONObject = get("/system_stats").use { r ->
        if (!r.isSuccessful) throw ComfyException("system_stats ${r.code}")
        JSONObject(r.body!!.string())
    }

    /**
     * Rychlá otázka „odpovídáš?". Používá se v čekací smyčce před generováním a
     * pro ukazatel stavu na hlavní obrazovce, proto má vlastní krátké čekání –
     * s běžnými 15 s by se odpověď „server neběží" táhla zbytečně dlouho.
     */
    fun isAlive(): Boolean = runCatching {
        val req = Request.Builder().url("$base/system_stats").build()
        pingClient.newCall(req).execute().use { it.isSuccessful }
    }.getOrDefault(false)

    /**
     * Požádá spouštěče na počítači, ať ComfyUI nastartuje.
     *
     * Appka sama proces na Windows spustit nedokáže – umí jen poslat požadavek.
     * Na počítači proto sedí `comfyui_launcher_v1.py` (port 8190, na GPU nesahá
     * a sám od sebe nic nespouští). Když neběží ani ten, nedá se dělat nic a
     * vrátí se false – uživateli se pak řekne, ať počítač zapne.
     */
    fun requestServerStart(): Boolean = runCatching {
        val host = base.substringAfter("://").substringBefore(':')
        val req = Request.Builder()
            .url("http://$host:$LAUNCHER_PORT/start")
            .post(ByteArray(0).toRequestBody(null))
            .build()
        pingClient.newCall(req).execute().use { it.isSuccessful }
    }.getOrDefault(false)

    /**
     * Požádá spouštěče, ať ComfyUI ukončí a uvolní grafiku – na počítači se pak
     * dá hrát. Ukončí se výhradně proces, který spouštěč sám nastartoval.
     */
    fun requestServerStop(): Boolean = runCatching {
        val host = base.substringAfter("://").substringBefore(':')
        val req = Request.Builder()
            .url("http://$host:$LAUNCHER_PORT/stop")
            .post(ByteArray(0).toRequestBody(null))
            .build()
        pingClient.newCall(req).execute().use { it.isSuccessful }
    }.getOrDefault(false)

    /** Běží na počítači aspoň spouštěč? Rozliší „počítač spí" od „ComfyUI stojí". */
    fun launcherAlive(): Boolean = runCatching {
        val host = base.substringAfter("://").substringBefore(':')
        val req = Request.Builder().url("http://$host:$LAUNCHER_PORT/status").build()
        pingClient.newCall(req).execute().use { it.isSuccessful }
    }.getOrDefault(false)

    /** Vrátí popis serveru pro obrazovku nastavení, nebo vyhodí ComfyException. */
    fun probe(): String {
        val j = systemStats()
        val sys = j.optJSONObject("system")
        val ver = sys?.optString("comfyui_version") ?: "?"
        val devices = j.optJSONArray("devices")
        val gpu = if (devices != null && devices.length() > 0)
            devices.getJSONObject(0).optString("name") else "neznámé GPU"
        return "ComfyUI $ver\n$gpu"
    }

    // ---------------------------------------------------------------- upload

    /**
     * Nahraje obrázek do input/h3app. Používá pevné jméno, takže opakované
     * odeslání téhož souboru je idempotentní (důležité při výpadku sítě).
     */
    fun uploadImage(bytes: ByteArray, fileName: String): String {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("image", fileName, bytes.toRequestBody("image/jpeg".toMediaType()))
            .addFormDataPart("type", "input")
            .addFormDataPart("subfolder", UPLOAD_SUBFOLDER)
            .addFormDataPart("overwrite", "true")
            .build()
        val req = Request.Builder().url("$base/upload/image").post(body).build()
        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw ComfyException(
                "upload ${r.code}",
                "Server odmítl obrázek (HTTP ${r.code})."
            )
            val j = JSONObject(r.body!!.string())
            val name = j.getString("name")
            val sub = j.optString("subfolder", "")
            return if (sub.isEmpty()) name else "$sub/$name"
        }
    }

    /**
     * Nahraje větší soubor (video/zvuk) streamem, bez načítání do paměti.
     * Ukládá se do kořene input složky – uzly VHS_LoadVideo a LoadAudio nabízejí
     * soubory právě odtud, u podsložek to není zaručené.
     */
    fun uploadMedia(file: java.io.File, name: String): String {
        val mime = when (name.substringAfterLast('.', "").lowercase()) {
            "mp4", "m4v" -> "video/mp4"
            "mov" -> "video/quicktime"
            "webm" -> "video/webm"
            "mkv" -> "video/x-matroska"
            "mp3" -> "audio/mpeg"
            "wav" -> "audio/wav"
            "m4a", "aac" -> "audio/mp4"
            "ogg", "opus" -> "audio/ogg"
            "flac" -> "audio/flac"
            else -> "application/octet-stream"
        }
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("image", name, file.asRequestBody(mime.toMediaType()))
            .addFormDataPart("type", "input")
            .addFormDataPart("overwrite", "true")
            .build()
        val req = Request.Builder().url("$base/upload/image").post(body).build()
        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw ComfyException(
                "upload media ${r.code}",
                "Server odmítl soubor (HTTP ${r.code})."
            )
            val j = JSONObject(r.body!!.string())
            val n = j.getString("name")
            val sub = j.optString("subfolder", "")
            return if (sub.isEmpty()) n else "$sub/$n"
        }
    }

    /**
     * Definice jednoho uzlu, nebo null když server třídu nezná. Síťová chyba
     * se vyhazuje – „server neodpovídá" nesmí vypadat jako „uzel chybí".
     */
    fun objectInfo(cls: String): JSONObject? {
        val enc = java.net.URLEncoder.encode(cls, "UTF-8").replace("+", "%20")
        get("/object_info/$enc").use { r ->
            if (!r.isSuccessful) throw ComfyException("object_info $cls ${r.code}")
            val j = JSONObject(r.body!!.string())
            return if (j.has(cls)) j.getJSONObject(cls) else null
        }
    }

    /** Seznam LoRA, které server skutečně nabízí (čte se z definice uzlu). */
    fun loraNames(): List<String> {
        get("/object_info/LoraLoaderModelOnly").use { r ->
            if (!r.isSuccessful) throw ComfyException("lora list ${r.code}")
            val arr = JSONObject(r.body!!.string())
                .getJSONObject("LoraLoaderModelOnly")
                .getJSONObject("input").getJSONObject("required")
                .getJSONArray("lora_name").getJSONArray(0)
            return (0 until arr.length()).map { arr.getString(it) }
        }
    }

    /**
     * Seznam modelů, které server nabízí (`models/diffusion_models`). Čte se
     * ze stejného místa jako seznam LoRA – z definice uzlu, takže appka vidí
     * přesně to, co ComfyUI.
     */
    fun unetNames(): List<String> {
        get("/object_info/UNETLoader").use { r ->
            if (!r.isSuccessful) throw ComfyException("unet list ${r.code}")
            val arr = JSONObject(r.body!!.string())
                .getJSONObject("UNETLoader")
                .getJSONObject("input").getJSONObject("required")
                .getJSONArray("unet_name").getJSONArray(0)
            return (0 until arr.length()).map { arr.getString(it) }
        }
    }

    // ------------------------------------------------------------- All in One

    /**
     * Šablona workflow z balíku ComfyUI-ALLinONE-MinimaxH3.
     *
     * Appka si tyhle grafy schválně nekopíruje do sebe: po aktualizaci balíku
     * generuje podle nové verze, nemůže se rozejít s tím, co má uživatel na
     * počítači, a cizí kód pod GPL se nebalí do APK.
     */
    fun workflowTemplate(name: String): String {
        get("/h3one/workflow/$name").use { r ->
            if (!r.isSuccessful) throw ComfyException(
                "template $name ${r.code}",
                if (r.code == 404)
                    "Server šablonu „$name\" nezná. Zkontroluj, že je v ComfyUI nainstalovaný " +
                        "a načtený balík ComfyUI-ALLinONE-MinimaxH3."
                else "Server nevydal šablonu „$name\" (HTTP ${r.code})."
            )
            return r.body!!.string()
        }
    }

    /**
     * Graf čekající nebo běžící ve frontě. Po restartu aplikace se z něj dá
     * zjistit, z jakých uzlů se rozdělaná úloha skládá – bez toho by ukazatel
     * průběhu u karty All in One hádal fáze naslepo.
     */
    fun queuedGraph(promptId: String): JSONObject? = runCatching {
        get("/queue").use { r ->
            if (!r.isSuccessful) return null
            val j = JSONObject(r.body!!.string())
            for (key in listOf("queue_running", "queue_pending")) {
                val arr = j.optJSONArray(key) ?: continue
                for (i in 0 until arr.length()) {
                    val item = arr.getJSONArray(i)
                    if (item.optString(1) == promptId) return item.optJSONObject(2)
                }
            }
            null
        }
    }.getOrNull()

    /** Je balík All in One na serveru vůbec k dispozici? */
    fun hasAllInOne(): Boolean = runCatching {
        pingClient.newCall(Request.Builder().url("$base/h3one/workflow/t2v.json").build())
            .execute().use { it.isSuccessful }
    }.getOrDefault(false)

    // ---------------------------------------------------------------- fronta

    /**
     * Zařadí workflow pod předem známým [promptId]. ComfyUI vlastní prompt_id přijímá
     * (musí to být UUID v malých písmenech), takže se dá po výpadku sítě bezpečně
     * ověřit, jestli se úloha nezaložila, a neposlat ji podruhé.
     */
    fun queuePrompt(workflow: JSONObject, clientId: String, promptId: String): String {
        val payload = JSONObject()
            .put("prompt", workflow)
            .put("client_id", clientId)
            .put("prompt_id", promptId)
        val req = Request.Builder()
            .url("$base/prompt")
            .post(payload.toString().toRequestBody(JSON))
            .build()
        http.newCall(req).execute().use { r ->
            val text = r.body?.string().orEmpty()
            if (!r.isSuccessful) throw ComfyException(
                "prompt ${r.code}: $text",
                describeValidationError(text, r.code)
            )
            return JSONObject(text).optString("prompt_id", promptId)
        }
    }

    /** Je úloha ve frontě (běžící nebo čekající)? Vrací pozici, -1 když tam není. */
    fun queuePosition(promptId: String): Int = runCatching {
        get("/queue").use { r ->
            if (!r.isSuccessful) return -1
            val j = JSONObject(r.body!!.string())
            j.optJSONArray("queue_running")?.let { arr ->
                for (i in 0 until arr.length())
                    if (arr.getJSONArray(i).optString(1) == promptId) return 0
            }
            j.optJSONArray("queue_pending")?.let { arr ->
                var pos = 1
                for (i in 0 until arr.length()) {
                    if (arr.getJSONArray(i).optString(1) == promptId) return pos
                    pos++
                }
            }
            -1
        }
    }.getOrDefault(-1)

    /**
     * Ví server o úloze? true = ve frontě nebo v historii, false = SERVER ODPOVĚDĚL
     * a úlohu nezná (typicky se restartoval a fronta je pryč), null = nedostupný.
     * Rozdíl mezi false a null je zásadní: výpadek sítě nikdy neznamená selhání
     * úlohy, ztracená fronta ano.
     */
    fun promptKnown(promptId: String): Boolean? = runCatching {
        get("/history/$promptId").use { r ->
            if (!r.isSuccessful) return@runCatching null
            if (JSONObject(r.body!!.string()).has(promptId)) return@runCatching true
        }
        get("/queue").use { r ->
            if (!r.isSuccessful) return@runCatching null
            val j = JSONObject(r.body!!.string())
            for (key in listOf("queue_running", "queue_pending")) {
                val arr = j.optJSONArray(key) ?: continue
                for (i in 0 until arr.length())
                    if (arr.getJSONArray(i).optString(1) == promptId) return@runCatching true
            }
            false
        }
    }.getOrNull()

    private fun describeValidationError(text: String, code: Int): String = runCatching {
        val j = JSONObject(text)
        val err = j.optJSONObject("error")
        val head = err?.optString("message") ?: "Server workflow nepřijal (HTTP $code)"
        val details = StringBuilder()
        val nodeErrors = j.optJSONObject("node_errors")
        nodeErrors?.keys()?.forEach { k ->
            val errs = nodeErrors.getJSONObject(k).optJSONArray("errors")
            if (errs != null) for (i in 0 until errs.length()) {
                details.append("\n• uzel $k: ").append(errs.getJSONObject(i).optString("message"))
            }
        }
        head + details.toString()
    }.getOrDefault("Server workflow nepřijal (HTTP $code)")

    fun history(promptId: String): JSONObject? = runCatching {
        get("/history/$promptId").use { r ->
            if (!r.isSuccessful) return null
            val j = JSONObject(r.body!!.string())
            if (j.has(promptId)) j.getJSONObject(promptId) else null
        }
    }.getOrNull()

    /** Cílené přerušení – přeruší jen naši úlohu, cizí běžící práci nechá být. */
    fun interrupt(promptId: String?) {
        runCatching {
            val payload = JSONObject()
            if (promptId != null) payload.put("prompt_id", promptId)
            http.newCall(
                Request.Builder().url("$base/interrupt")
                    .post(payload.toString().toRequestBody(JSON)).build()
            ).execute().close()
        }
    }

    fun deleteFromQueue(promptId: String) {
        runCatching {
            val payload = JSONObject().put("delete", listOf(promptId).toJsonArray())
            http.newCall(
                Request.Builder().url("$base/queue")
                    .post(payload.toString().toRequestBody(JSON)).build()
            ).execute().close()
        }
    }

    /**
     * Varování uzlů z běhu, která jinak skončí jen v logu na počítači.
     *
     * MiniMax H3 Director takhle hlásí věci, které se z výsledku poznají těžko –
     * prázdnou zvukovou atmosféru, repliku bez dvojtečky, kterou proto neudělal
     * jako dialog. Z telefonu se do logu nedostaneš, tak si ho appka přečte za
     * tebe: `/internal/logs/raw` vrací `{"entries":[{"t":…,"m":"…"}]}`.
     *
     * @param since čas posledního přečteného zápisu (ISO), nebo null pro celý log
     */
    fun nodeWarnings(since: String? = null): List<Pair<String, String>> = runCatching {
        get("/internal/logs/raw").use { r ->
            val body = r.body?.string() ?: return emptyList()
            val entries = JSONObject(body).optJSONArray("entries") ?: return emptyList()
            (0 until entries.length()).mapNotNull { i ->
                val o = entries.getJSONObject(i)
                val t = o.optString("t")
                if (since != null && t <= since) return@mapNotNull null
                // Pryč s barvami terminálu, jinak by se v telefonu ukázaly
                // jako změť „ESC[32m".
                val m = o.optString("m").replace(Regex("\u001B\\[[0-9;]*m"), "").trim()
                if (m.isEmpty()) null else t to m
            }
        }
    }.getOrDefault(emptyList())

    // ---------------------------------------------------------------- výstup

    fun viewUrl(filename: String, subfolder: String, type: String): String {
        val url = "$base/view".toHttpUrlOrNull()!!.newBuilder()
            .addQueryParameter("filename", filename)
            .addQueryParameter("subfolder", subfolder)
            .addQueryParameter("type", type)
            .build()
        return url.toString()
    }

    /** Stáhne soubor do [target] a hlásí přenesené i celkové bajty. */
    fun download(url: String, target: File, onProgress: (done: Long, total: Long) -> Unit) {
        val req = Request.Builder().url(url).build()
        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw ComfyException("download ${r.code}")
            val bodyStream = r.body!!.byteStream()
            val total = r.body!!.contentLength()
            target.parentFile?.mkdirs()
            val tmp = File(target.parentFile, target.name + ".part")
            tmp.outputStream().use { out ->
                val buf = ByteArray(64 * 1024)
                var read: Int
                var done = 0L
                var lastReport = 0L
                while (bodyStream.read(buf).also { read = it } != -1) {
                    out.write(buf, 0, read)
                    done += read
                    // hlásit nejvýš ~20× za sekundu, ať se UI zbytečně nepřekresluje
                    val now = System.currentTimeMillis()
                    if (now - lastReport > 50) { onProgress(done, total); lastReport = now }
                }
                onProgress(done, total)
            }
            if (target.exists()) target.delete()
            if (!tmp.renameTo(target)) {
                tmp.copyTo(target, overwrite = true)
                tmp.delete()
            }
        }
    }

    // ---------------------------------------------------------------- websocket

    fun openWebSocket(clientId: String, listener: WebSocketListener): WebSocket {
        val wsBase = base.replaceFirst("https://", "wss://").replaceFirst("http://", "ws://")
        val req = Request.Builder().url("$wsBase/ws?clientId=$clientId").build()
        return socketClient.newWebSocket(req, listener)
    }

    // ---------------------------------------------------------------- pomocné

    private fun get(path: String): Response =
        http.newCall(Request.Builder().url("$base$path").build()).execute()

    companion object {
        /** Port spouštěče na počítači (viz comfyui_launcher_v1.py). */
        const val LAUNCHER_PORT = 8190
        const val UPLOAD_SUBFOLDER = "h3app"
        private val JSON = "application/json; charset=utf-8".toMediaType()

        private fun List<String>.toJsonArray() =
            org.json.JSONArray().also { a -> forEach { a.put(it) } }
    }
}
