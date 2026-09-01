package cz.promptlab.h3video.higgs

import cz.promptlab.h3video.comfy.ComfyException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/** Hlas z knihovny Higgse. */
data class Voice(
    val id: String,
    val name: String,
    val language: String,
    val isPreset: Boolean,
    val seconds: Float,
)

/** Stav namlouvání jedné repliky. */
data class VoiceJob(
    val id: String,
    val status: String,
    val progress: Float,
    val message: String,
    val error: String,
    /** Přepis referenční nahrávky, když se klonovalo s automatickým přepisem. */
    val transcript: String,
) {
    val done: Boolean get() = status == "done"
    val failed: Boolean get() = status == "error" || error.isNotBlank()
    val running: Boolean get() = !done && !failed
}

/**
 * Klient k Higgs Audio Studiu — samostatnému serveru na počítači
 * (`C:\HIGGS_AUDIO\app\server.py`, port 7860), ne k ComfyUI.
 *
 * Namlouvání běží jako úloha: požadavek vrátí `job_id`, průběh se čte
 * z `/api/jobs/{id}` a hotové WAV se stáhne z `/api/jobs/{id}/audio`.
 *
 * Přístupový kód je potřeba jen zvenčí. Přes Tailscale požadavek na server
 * dorazí jako místní, takže bez kódu projde; posílá se, jen když je vyplněný.
 */
class HiggsClient(baseUrl: String, private val token: String = "") {

    val base: String = baseUrl.trimEnd('/')

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val pingClient = http.newBuilder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build()

    // ---------------------------------------------------------------- stav

    /**
     * Odpovídá server a je model načtený? Model se po startu nahrává ~45 s a do
     * té doby každý požadavek na namluvení skončí chybou 503, takže se na tohle
     * musí počkat, ne to jen zkusit.
     */
    fun modelReady(): Boolean = runCatching {
        pingClient.newCall(build("$base/api/status").build()).execute().use { r ->
            if (!r.isSuccessful) return false
            JSONObject(r.body!!.string()).optBoolean("engine_loaded", false)
        }
    }.getOrDefault(false)

    fun isAlive(): Boolean = runCatching {
        pingClient.newCall(build("$base/api/status").build()).execute().use { it.isSuccessful }
    }.getOrDefault(false)

    // ---------------------------------------------------------------- hlasy

    fun voices(): List<Voice> {
        get("/api/voices").use { r ->
            if (!r.isSuccessful) throw ComfyException(
                "voices ${r.code}",
                "Nepodařilo se načíst seznam hlasů (HTTP ${r.code})."
            )
            val arr = JSONObject(r.body!!.string()).getJSONArray("voices")
            return (0 until arr.length()).map { i ->
                val v = arr.getJSONObject(i)
                Voice(
                    id = v.getString("id"),
                    name = v.optString("name", v.getString("id")),
                    language = v.optString("language", ""),
                    isPreset = v.optBoolean("is_preset", false),
                    seconds = v.optDouble("duration_s", 0.0).toFloat(),
                )
            }
        }
    }

    // ---------------------------------------------------------------- namluvení

    /** Namluví text hlasem z knihovny. Vrací id úlohy. */
    fun speak(text: String, voiceId: String): String {
        val payload = JSONObject()
            .put("text", text)
            .put("voice_id", voiceId)
        val req = build("$base/api/generate")
            .post(payload.toString().toRequestBody(JSON))
            .build()
        return jobIdFrom(req, "namluvení")
    }

    /**
     * Naklonuje hlas z nahrávky a rovnou jím namluví text.
     *
     * `auto_transcribe` nechává Higgs přepsat ukázku Whisperem — podle jeho
     * dokumentace to výrazně zvedá věrnost klonu, proto je zapnuté.
     */
    fun clone(text: String, sample: File): String {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("text", text)
            .addFormDataPart(
                "audio", sample.name,
                sample.asRequestBody(mimeFor(sample.name).toMediaType())
            )
            .addFormDataPart("auto_transcribe", "true")
            .addFormDataPart("language", "czech")
            .build()
        val req = build("$base/api/clone").post(body).build()
        return jobIdFrom(req, "klonování hlasu")
    }

    fun job(id: String): VoiceJob {
        get("/api/jobs/$id").use { r ->
            if (!r.isSuccessful) throw ComfyException(
                "job ${r.code}",
                "Higgs o úloze neví (HTTP ${r.code})."
            )
            val j = JSONObject(r.body!!.string())
            return VoiceJob(
                id = j.optString("id", id),
                status = j.optString("status", ""),
                progress = j.optDouble("progress", 0.0).toFloat(),
                message = j.optString("message", ""),
                error = j.optString("error", "").takeIf { it != "null" } ?: "",
                transcript = j.optString("transcript", ""),
            )
        }
    }

    fun cancel(id: String) {
        runCatching {
            http.newCall(
                build("$base/api/jobs/$id/cancel").post(ByteArray(0).toRequestBody(null)).build()
            ).execute().close()
        }
    }

    /** Stáhne hotové WAV do souboru v aplikaci. */
    fun downloadAudio(id: String, target: File) {
        http.newCall(build("$base/api/jobs/$id/audio?format=wav").build()).execute().use { r ->
            if (!r.isSuccessful) throw ComfyException(
                "job audio ${r.code}",
                "Hotový hlas se nepodařilo stáhnout (HTTP ${r.code})."
            )
            target.parentFile?.mkdirs()
            val tmp = File(target.parentFile, target.name + ".part")
            tmp.outputStream().use { out -> r.body!!.byteStream().copyTo(out) }
            if (target.exists()) target.delete()
            tmp.renameTo(target)
        }
    }

    // ---------------------------------------------------------------- pomocné

    private fun jobIdFrom(req: Request, what: String): String {
        http.newCall(req).execute().use { r ->
            val text = r.body?.string().orEmpty()
            if (r.code == 503) throw ComfyException(
                "higgs 503: $text",
                "Higgs se ještě rozjíždí, model se načítá. Zkus to za chvíli."
            )
            if (!r.isSuccessful) throw ComfyException(
                "higgs $what ${r.code}: $text",
                describeError(text, r.code, what)
            )
            return JSONObject(text).optString("job_id").ifBlank {
                throw ComfyException("higgs $what bez job_id", "Higgs nevrátil úlohu k $what.")
            }
        }
    }

    private fun describeError(text: String, code: Int, what: String): String = runCatching {
        val detail = JSONObject(text).optString("detail")
        if (detail.isNotBlank()) detail else "Higgs odmítl $what (HTTP $code)."
    }.getOrDefault("Higgs odmítl $what (HTTP $code).")

    private fun build(url: String): Request.Builder {
        val b = Request.Builder().url(url).header("User-Agent", "H3Video")
        if (token.isNotBlank()) b.header("x-higgs-token", token)
        return b
    }

    private fun get(path: String): Response =
        http.newCall(build("$base$path").build()).execute()

    private fun mimeFor(name: String) = when (name.substringAfterLast('.', "").lowercase()) {
        "wav" -> "audio/wav"
        "mp3" -> "audio/mpeg"
        "m4a", "aac" -> "audio/mp4"
        "ogg", "opus" -> "audio/ogg"
        "flac" -> "audio/flac"
        else -> "application/octet-stream"
    }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()

        /** Port spouštěče Higgse na počítači (viz higgs_launcher_v1.py). */
        const val LAUNCHER_PORT = 8191
    }
}

/**
 * Spouštěč Higgse na počítači. Higgs a MiniMax se na jednu grafiku nevejdou,
 * takže appka Higgs nahodí, když je potřeba hlas, a před generováním videa ho
 * zase složí — jinak by MiniMaxu chyběla paměť.
 *
 * Ukončuje se výhradně proces, který spouštěč sám nastartoval; Higgs spuštěný
 * ručně na počítači zůstane běžet.
 */
class HiggsLauncher(higgsUrl: String) {

    private val host = higgsUrl.trimEnd('/').substringAfter("://").substringBefore(':')
    private val base = "http://$host:${HiggsClient.LAUNCHER_PORT}"

    private val http = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(40, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build()

    /** "running" (model načtený), "starting", "stopped", nebo null když spouštěč neodpovídá. */
    fun state(): String? = runCatching {
        http.newCall(Request.Builder().url("$base/status").build()).execute().use { r ->
            if (!r.isSuccessful) return null
            JSONObject(r.body!!.string()).optString("higgs").ifBlank { null }
        }
    }.getOrNull()

    fun start(): Boolean = post("/start")

    fun stop(): Boolean = post("/stop")

    private fun post(path: String): Boolean = runCatching {
        http.newCall(
            Request.Builder().url("$base$path").post(ByteArray(0).toRequestBody(null)).build()
        ).execute().use { it.isSuccessful }
    }.getOrDefault(false)
}
