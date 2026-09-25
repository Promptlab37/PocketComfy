package cz.promptlab.h3video.comfy

import org.json.JSONArray
import org.json.JSONObject

/**
 * Nabídka výběrového vstupu uzlu z `/object_info`, v obou tvarech:
 *
 *  - starý: `"model": [["a", "b"], {…}]`
 *  - nový (API v3): `"model": ["COMBO", {"options": ["a", "b"], …}]`
 *
 * Balíky přecházejí na nový tvar postupně a bez ohlášení. 25. 9. 2026 se
 * to stalo přepisovači pro reference: appka četla jen starý tvar, dostala
 * prázdný seznam a hlásila „nemá čím fotky přečíst", přestože model na
 * serveru ležel. Proto se nabídka čte všude jen tudy.
 *
 * Chybí-li vstup nebo nabídka, vrací prázdné pole — nikdy nepadá.
 */
fun JSONObject.nabidkaArr(klic: String): JSONArray {
    val spec = optJSONArray(klic) ?: return JSONArray()
    spec.optJSONArray(0)?.let { return it }
    if (spec.optString(0) == "COMBO") {
        spec.optJSONObject(1)?.optJSONArray("options")?.let { return it }
    }
    return JSONArray()
}

/** Totéž jako seznam řetězců. */
fun JSONObject.nabidka(klic: String): List<String> {
    val a = nabidkaArr(klic)
    return (0 until a.length()).map { a.optString(it) }
}
