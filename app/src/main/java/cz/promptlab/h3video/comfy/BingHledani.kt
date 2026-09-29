package cz.promptlab.h3video.comfy

import cz.promptlab.h3video.data.t
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Hledání obrázků na Bingu přes balík ComfyUI-BingImageSelector.
 *
 * Balík hledá a stahuje na serveru ještě před spuštěním workflow:
 * `POST /bing_image_selector/search` s `{query, count}` vrátí manifest
 * `{session, query, count, items: [{id, url, title, source, width, height}]}`.
 * Náhledy (240 × 180) dává `GET /bing_image_selector/thumbnail/<session>/<id>`,
 * plné obrázky (PNG, delší strana nejvýš 4096) leží ve vstupní složce ComfyUI
 * jako `bing_image_selector/<session>/<id>.png` — stáhnou se přes `/view`.
 */
object BingHledani {

    /** Kolik obrázků se chce po serveru. Balík dovolí 1–64. */
    const val POCET = 24

    /** Třída uzlu — podle ní „Zkontrolovat server“ i hláška poznají chybějící balík. */
    const val UZEL = "BingImageSelector"

    data class Obrazek(val id: String, val sirka: Int, val vyska: Int, val zdroj: String)

    data class Vysledek(val session: String, val obrazky: List<Obrazek>)

    /** Manifest z `/search` → výsledek. Neplatné položky se vynechají. */
    fun parsuj(json: JSONObject): Vysledek {
        val session = json.optString("session")
        require(SESSION.matches(session)) { t("neplatná session") }
        val items = json.optJSONArray("items")
        val obrazky = buildList {
            for (i in 0 until (items?.length() ?: 0)) {
                val o = items!!.optJSONObject(i) ?: continue
                val id = o.optString("id")
                if (!ID.matches(id)) continue
                add(Obrazek(id, o.optInt("width"), o.optInt("height"), o.optString("source")))
            }
        }
        return Vysledek(session, obrazky)
    }

    /** Text chyby z odpovědi balíku (`{"error": …}`), jinak null. */
    fun chyba(telo: String): String? =
        runCatching { JSONObject(telo).optString("error").ifBlank { null } }.getOrNull()

    fun nahledUrl(base: String, session: String, id: String): String =
        "${base.trimEnd('/')}/bing_image_selector/thumbnail/$session/$id"

    fun plnyUrl(base: String, session: String, id: String): String =
        "${base.trimEnd('/')}/view".toHttpUrlOrNull()!!.newBuilder()
            .addQueryParameter("filename", "$id.png")
            .addQueryParameter("subfolder", "bing_image_selector/$session")
            .addQueryParameter("type", "input")
            .build().toString()

    /**
     * Klepnutí na náhled. Při výběru jednoho obrázku klepnutí vybraný nahradí,
     * při výběru více se přidá na konec (pořadí = pořadí výběru) nebo odebere.
     * Nad [max] se nepřidává.
     */
    fun prepni(vybrane: List<String>, id: String, max: Int): List<String> = when {
        id in vybrane -> vybrane - id
        max <= 1 -> listOf(id)
        vybrane.size >= max -> vybrane
        else -> vybrane + id
    }

    /**
     * Hledání trvá desítky sekund (stahuje až [POCET] plných obrázků), proto
     * vlastní klient s delším čtením než sdílený v [ComfyClient].
     */
    fun hledej(base: String, dotaz: String): Vysledek {
        val telo = JSONObject().put("query", dotaz.trim()).put("count", POCET).toString()
        val req = Request.Builder()
            .url("${base.trimEnd('/')}/bing_image_selector/search")
            .post(telo.toRequestBody("application/json".toMediaType()))
            .build()
        HTTP.newCall(req).execute().use { r ->
            val text = r.body?.string().orEmpty()
            if (r.code == 404) throw ComfyException("bing 404", t(CHYBI_BALIK))
            if (!r.isSuccessful) throw ComfyException("bing ${r.code}", chyba(text) ?: "HTTP ${r.code}")
            return parsuj(JSONObject(text))
        }
    }

    /** Hláška, když server adresu hledání nezná — balík chybí nebo se nenačetl. */
    const val CHYBI_BALIK = "Na serveru chybí balík ComfyUI-BingImageSelector, nebo ComfyUI od jeho instalace neproběhl restart."

    private val SESSION = Regex("[0-9a-f]{32}")
    private val ID = Regex("[0-9a-f]{64}")

    private val HTTP: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(4, TimeUnit.MINUTES)
            .build()
    }
}
