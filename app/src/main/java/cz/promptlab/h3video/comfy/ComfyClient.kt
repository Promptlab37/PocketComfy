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

    // Klienty OkHttp jsou sdílené všemi instancemi (viz companion). Každý
    // klient má vlastní fond spojení a vláken a ComfyClient se zakládá při
    // každém dotazu (ping každých 5 s) – nový klient pokaždé znamenal nový
    // fond. Na adrese serveru nezávisí, ta je až v požadavku.
    private val http: OkHttpClient get() = SDILENY_HTTP
    private val pingClient: OkHttpClient get() = SDILENY_PING
    private val socketClient: OkHttpClient get() = SDILENY_SOCKET

    // ---------------------------------------------------------------- základní

    fun systemStats(): JSONObject = get("/system_stats").use { r ->
        if (!r.isSuccessful) throw ComfyException("system_stats ${r.code}")
        JSONObject(r.body!!.string())
    }

    /** Volná a celková paměť grafiky v bajtech, jak ji vidí ComfyUI. */
    fun vram(): Pair<Long, Long>? = runCatching { vramZe(systemStats()) }.getOrNull()

    /**
     * Jede server s **comfy-aimdo** (dynamická VRAM, ComfyUI ≥ 0.34)?
     *
     * Ten si paměť grafiky řídí sám: modely „staguje" do zamčené (pinned)
     * RAM — na tomhle stroji 39 GB — a nechává je tam mezi běhy. Vyhodit mu
     * je přes `/free` nic neušetří, jen ho donutí při dalším běhu desítky GB
     * zamknout znovu; zamykání stránek je operace jádra, která na tu dobu
     * zastaví celý počítač. Přesně to uživatel viděl 8. 9. 2026 jako „všechno
     * zamrzne, paměť prázdná, grafika stojí" — a jen z appky, protože
     * ComfyUI UI `/free` nikdy nevolá.
     */
    fun dynamickaVram(): Boolean = runCatching { maAimdo(systemStats()) }.getOrDefault(false)


    /**
     * Řekne ComfyUI, ať pustí modely z paměti grafiky.
     *
     * Sahá VÝHRADNĚ na to, co drží ComfyUI samo — cizí programy (hra, prohlížeč,
     * jiný nástroj) tím nikdo nevypíná ani neomezuje. Používá se před během,
     * když je na kartě málo místa: bez toho se model dohrává po částech z RAM
     * a generování se natáhne i několikanásobně.
     */
    fun freeMemory(): Boolean = runCatching {
        val payload = JSONObject()
            .put("unload_models", true)
            .put("free_memory", true)
        http.newCall(
            Request.Builder().url("$base/free")
                .post(payload.toString().toRequestBody(JSON)).build()
        ).execute().use { it.isSuccessful }
    }.getOrDefault(false)

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
     * Vynutí na serveru existenci složky `temp`.
     *
     * Uzly, které streamují dekódování po kusech, do ní zakládají svůj
     * pracovní adresář přes `tempfile.mkdtemp(dir=…)` — a to spadne na
     * „[WinError 3] Systém nemůže nalézt uvedenou cestu", když složka
     * neexistuje. ComfyUI ji sice při startu založí, ale během sezení
     * zmizet může (23. 9. 2026 takhle spadlo navázání na kartě Long MiniMax).
     *
     * Nahrání souboru s `type=temp` složku vytvoří, protože ji server
     * před zápisem založí sám. Chyba se schválně polyká: tohle je pojistka,
     * ne důvod, proč by měl běh skončit.
     */
    fun zajistiTempSlozku() {
        runCatching {
            // Nejmenší platný PNG, 1×1 průhledný bod.
            val png = byteArrayOf(
                0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
                0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
                0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
                0x08, 0x06, 0x00, 0x00, 0x00, 0x1F, 0x15.toByte(), 0xC4.toByte(),
                0x89.toByte(), 0x00, 0x00, 0x00, 0x0A, 0x49, 0x44, 0x41,
                0x54, 0x78, 0x9C.toByte(), 0x63, 0x00, 0x01, 0x00, 0x00,
                0x05, 0x00, 0x01, 0x0D, 0x0A, 0x2D, 0xB4.toByte(), 0x00,
                0x00, 0x00, 0x00, 0x49, 0x45, 0x4E, 0x44, 0xAE.toByte(),
                0x42, 0x60, 0x82.toByte(),
            )
            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "image", "h3app_temp.png",
                    png.toRequestBody("image/png".toMediaType()),
                )
                .addFormDataPart("type", "temp")
                .addFormDataPart("overwrite", "true")
                .build()
            http.newCall(Request.Builder().url("$base/upload/image").post(body).build())
                .execute().close()
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

    /** Volitelná metadata safetensors; starší servery mohou vrátit 404. */
    fun loraMetadata(name: String): JSONObject? = runCatching {
        val url = "$base/view_metadata/loras".toHttpUrlOrNull()?.newBuilder()
            ?.addQueryParameter("filename", name)?.build() ?: return null
        pingClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) null else response.body?.string()?.let { JSONObject(it) }
        }
    }.getOrNull()

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

    /**
     * Seznam modelů ve formátu GGUF. Jde o **jiný seznam než [unetNames]**:
     * `UNETLoader` nabízí jen přípony, které umí jádro ComfyUI, a `.gguf`
     * mezi nimi není — ty drží uzel z balíku ComfyUI-GGUF pod vlastní složkou
     * `unet_gguf`. Bez tohohle volání by ve výběru chyběly zrovna komunitní
     * finetuny, které se v GGUF šíří nejčastěji.
     *
     * Když balík na serveru není, vrací prázdný seznam — to není chyba,
     * jen na tom serveru žádné GGUF modely nejsou k mání.
     */
    fun unetGgufNames(): List<String> = runCatching {
        val spec = objectInfo("UnetLoaderGGUF") ?: return emptyList()
        val arr = spec.getJSONObject("input").getJSONObject("required")
            .getJSONArray("unet_name").getJSONArray(0)
        (0 until arr.length()).map { arr.getString(it) }
    }.getOrDefault(emptyList())

    /**
     * Uložené latenty karty Long MiniMax, **nejnovější první**.
     *
     * Seznam si drží sám uzel, který je načítá — čte složku `output/h3_latents`
     * a řadí ji podle času změny. Appka ho proto nesmí sestavovat po svém:
     * kdyby se pořadí nebo filtr někdy změnily, nabídka v kartě by ukazovala
     * něco jiného než to, co uzel skutečně přijme.
     *
     * Když balík na serveru není, vrací prázdný seznam — to není chyba,
     * jen na tom serveru ještě žádný řetěz nezačal.
     */
    fun latentNames(): List<String> = runCatching {
        val spec = objectInfo("MiniMaxH3EasyLoadLatent_SatoDive") ?: return emptyList()
        val arr = spec.getJSONObject("input").getJSONObject("required")
            .getJSONArray("latent_file").getJSONArray(0)
        // Dokud nic uloženého není, vrací uzel jedinou položku v závorce
        // („(none saved yet)"). Není to název souboru a do nabídky nepatří.
        (0 until arr.length()).map { arr.getString(it) }.filterNot { it.startsWith("(") }
    }.getOrDefault(emptyList())

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
        private val SDILENY_HTTP: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(120, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build()
        }

        /** Krátké čekání jen pro dotaz „žiješ?" – viz [isAlive]. */
        private val SDILENY_PING: OkHttpClient by lazy {
            SDILENY_HTTP.newBuilder()
                .connectTimeout(4, TimeUnit.SECONDS)
                .readTimeout(6, TimeUnit.SECONDS)
                .retryOnConnectionFailure(false)
                .build()
        }

        private val SDILENY_SOCKET: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .pingInterval(20, TimeUnit.SECONDS)
                .build()
        }

        /** Volná a celková VRAM první grafiky z odpovědi `/system_stats`. */
        fun vramZe(stats: JSONObject): Pair<Long, Long>? {
            val d = stats.optJSONArray("devices")?.optJSONObject(0) ?: return null
            val volno = d.optLong("vram_free", 0L)
            val celkem = d.optLong("vram_total", 0L)
            return if (celkem <= 0L) null else volno to celkem
        }

        /** Je mezi balíčky serveru `comfy-aimdo`? Čistá funkce, ať jde testovat. */
        fun maAimdo(stats: JSONObject): Boolean {
            val bal = stats.optJSONObject("system")?.optJSONArray("comfy_package_versions")
                ?: return false
            return (0 until bal.length()).any {
                bal.optJSONObject(it)?.optString("name") == "comfy-aimdo"
            }
        }

        /**
         * Běží server bez připnuté paměti (`--disable-pinned-memory`)? Čte se
         * z `argv` v `/system_stats`. S aimdo a připnutou pamětí je `/free`
         * operace jádra nad desítkami GB a zastavuje počítač; bez připnutí
         * trvá 0,07 s a vrátí grafiku ploše (změřeno 9. 9. 2026: 10,5 → 1,7 GB).
         */
        fun bezPinnedMemory(stats: JSONObject): Boolean {
            val argv = stats.optJSONObject("system")?.optJSONArray("argv") ?: return false
            return (0 until argv.length()).any { argv.optString(it) == "--disable-pinned-memory" }
        }

        /** Smí appka volat `/free`? Bez aimdo vždy; s aimdo jen bez připnuté paměti. */
        fun smiUvolnit(stats: JSONObject): Boolean = !maAimdo(stats) || bezPinnedMemory(stats)

        /** Port spouštěče na počítači (viz comfyui_launcher_v1.py). */
        const val LAUNCHER_PORT = 8190
        const val UPLOAD_SUBFOLDER = "h3app"
        private val JSON = "application/json; charset=utf-8".toMediaType()

        private fun List<String>.toJsonArray() =
            org.json.JSONArray().also { a -> forEach { a.put(it) } }
    }
}
