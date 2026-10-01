package cz.promptlab.h3video.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Dialogy přes Higgs ve Filmu ze storyboardu (5.64).
 *
 * Každou repliku namluví Higgs hlasem, který uživatel vybral postavě (knihovna,
 * nebo vlastní vzorek). Nahrávky jdou do H3 jako `<Audio N>` s `partially_copy`:
 * film má pak přesně ten hlas a slova, pohyb pusy podle nahrávky.
 *
 * Postup ověřený testem 29. 9. 2026 (natočený film s dialogy, 1. 10. 2026):
 *  - jedna stopa = jeden mluvčí v jednom úseku: ticho délky úseku a do něj repliky
 *    v časech jejich panelů; stopy se skládají až na serveru (EmptyAudio +
 *    AudioConcat + AudioMerge), telefon zvuk nezpracovává,
 *  - k převzaté replice se píše jen „exactly the voice, pitch, timing and intonation
 *    of <Audio N>“ — vlastní popis hlasu v promptu hlas od nahrávky odtáhne,
 *  - média jdou přímými vstupy `media_N` uzlu úseků (obrázky první, pak stopy),
 *    MediaBridge s nimi nejde (bere nejvýš 3 zvuky).
 */
object SbDialogy {

    /** Replika začne tolik po začátku svého panelu. */
    const val NASTUP_S = 0.15
    /** Mezera mezi dvěma replikami téhož panelu. */
    const val MEZERA_S = 0.3
    /** Rezerva za poslední replikou panelu, než přijde střih. */
    const val DOBEH_S = 1.0
    /**
     * Rezerva za replikou v posledním panelu filmu: s 0,1 s rezervy se konec repliky
     * uřízl (Příšera 1. 10. 2026) — mluvený záběr vždy s velkou rezervou (~2 s).
     */
    const val DOBEH_KONEC_S = 2.0
    const val MAX_NA_USEK = 3
    const val MAX_NA_FILM = 9
    const val VZORKOVANI = 44100

    /** Hlasový pokyn k převzaté replice. */
    fun hlasZ(cislo: Int) = "in exactly the voice, pitch, timing and intonation of <Audio $cislo>, "

    // ---------- hlas postavy a nahrávky

    /** Hlas postavy uložený ve scéně: knihovna Higgse, nebo vlastní vzorek. */
    fun zakoduj(v: VoiceSource): String = when (v) {
        is VoiceSource.Library -> JSONObject().put("typ", "lib").put("id", v.voiceId).put("nazev", v.voiceName)
        is VoiceSource.Sample -> JSONObject().put("typ", "vzorek").put("soubor", v.file.absolutePath).put("nazev", v.label)
    }.toString()

    fun dekoduj(s: String?): VoiceSource? = runCatching {
        val j = JSONObject(s ?: return null)
        when (j.optString("typ")) {
            "lib" -> VoiceSource.Library(j.getString("id"), j.optString("nazev", j.getString("id")))
            "vzorek" -> VoiceSource.Sample(File(j.getString("soubor")), j.optString("nazev"))
            else -> null
        }
    }.getOrNull()

    /** Nahrávka repliky: platí jen pro stejný text a stejný hlas. */
    data class Nahravka(val soubor: File, val text: String, val hlas: String, val delkaS: Double)

    fun klic(cislo: Int, index: Int) = "$cislo:$index"

    fun nahravkaZ(s: String?): Nahravka? = runCatching {
        val j = JSONObject(s ?: return null)
        Nahravka(File(j.getString("soubor")), j.getString("text"), j.getString("hlas"), j.getDouble("delka"))
    }.getOrNull()

    fun zakoduj(n: Nahravka): String = JSONObject().put("soubor", n.soubor.absolutePath)
        .put("text", n.text).put("hlas", n.hlas).put("delka", n.delkaS).toString()

    private fun norm(s: String) = s.lowercase().replace(Regex("""[^\p{L}\p{N}]"""), "")

    fun hlasMluvciho(s: SbFilmScene, kdo: String): VoiceSource? =
        s.hlasyMluvcich.entries.firstOrNull { it.key.equals(kdo.trim(), ignoreCase = true) }?.value?.let(::dekoduj)

    /** Platná nahrávka repliky — sedí text i hlas a soubor existuje. */
    fun platna(s: SbFilmScene, p: SbPanel, index: Int, kdo: String, text: String, existuje: (File) -> Boolean = { it.exists() }): Nahravka? {
        val n = nahravkaZ(s.nahravky[klic(p.cislo, index)]) ?: return null
        if (norm(n.text) != norm(text) || !existuje(n.soubor)) return null
        // Vlastní zvukový soubor platí bez ohledu na vybraný hlas.
        if (n.hlas == VLASTNI_ZVUK) return n
        return n.takeIf { it.hlas == hlasMluvciho(s, kdo)?.klic }
    }

    /** Repliky, které ještě nemají platnou nahrávku: (panel, index, mluvčí, text). */
    fun chybejici(s: SbFilmScene, existuje: (File) -> Boolean = { it.exists() }): List<Replika> =
        s.panely.flatMap { p ->
            SbFilmPrepis.repliky(p.repliky).mapIndexedNotNull { i, (kdo, text) ->
                if (platna(s, p, i, kdo, text, existuje) == null) Replika(p.cislo, i, kdo.trim(), text) else null
            }
        }

    data class Replika(val panel: Int, val index: Int, val kdo: String, val text: String)

    /** Mluvčí bez vybraného hlasu, kteří ještě mají nenamluvenou repliku. */
    fun bezHlasu(s: SbFilmScene, existuje: (File) -> Boolean = { it.exists() }): List<String> =
        chybejici(s, existuje).map { it.kdo }.distinctBy { it.lowercase() }.filter { hlasMluvciho(s, it) == null }

    /**
     * Panely prodloužené tak, aby se jejich nahrané repliky vešly (nástup + repliky
     * + mezery + doběh). Jen prodlužuje — kratší nahrávka panel nezkrátí.
     */
    fun prodluzPanely(s: SbFilmScene, existuje: (File) -> Boolean = { it.exists() }): List<SbPanel> = s.panely.mapIndexed { k, p ->
        val repl = SbFilmPrepis.repliky(p.repliky)
        if (repl.isEmpty()) return@mapIndexed p
        val delky = repl.mapIndexed { i, (kdo, text) -> platna(s, p, i, kdo, text, existuje)?.delkaS }
        if (delky.any { it == null }) return@mapIndexed p
        val dobeh = if (k == s.panely.lastIndex) DOBEH_KONEC_S else DOBEH_S
        val potreba = NASTUP_S + delky.sumOf { it!! } + MEZERA_S * (repl.size - 1) + dobeh
        if (potreba > p.sekundy) p.copy(sekundy = Math.ceil(potreba * 2) / 2) else p
    }

    // ---------- plán stop

    data class Umisteni(val soubor: File, val startS: Double, val delkaS: Double, val text: String)

    /** Stopa jednoho mluvčího v jednom úseku; [cislo] = `<Audio N>` v celém filmu. */
    data class Stopa(val usek: Int, val mluvci: String, val cislo: Int, val delkaUsekuS: Double, val umisteni: List<Umisteni>)

    fun planuj(s: SbFilmScene, useky: List<SbUsek> = s.useky, existuje: (File) -> Boolean = { it.exists() }): List<Stopa> {
        if (!s.dialogyHiggs) return emptyList()
        val stopy = mutableListOf<Stopa>()
        var cislo = 0
        useky.forEachIndexed { k, u ->
            val poMluvcich = linkedMapOf<String, MutableList<Umisteni>>()
            var zacatekPanelu = 0.0
            u.panely.forEach { p ->
                var t = zacatekPanelu + NASTUP_S
                SbFilmPrepis.repliky(p.repliky).forEachIndexed { i, (kdo, text) ->
                    val n = platna(s, p, i, kdo, text, existuje) ?: return@forEachIndexed
                    poMluvcich.getOrPut(kdo.trim()) { mutableListOf() } += Umisteni(n.soubor, t, n.delkaS, text)
                    t += n.delkaS + MEZERA_S
                }
                zacatekPanelu += p.sekundy
            }
            poMluvcich.forEach { (kdo, um) -> stopy += Stopa(k, kdo, ++cislo, u.sekundy, um) }
        }
        return stopy
    }

    /** Co je potřeba opravit, než se zadání napíše (prázdné = v pořádku). */
    fun problemy(stopy: List<Stopa>): List<String> {
        val p = mutableListOf<String>()
        if (stopy.size > MAX_NA_FILM) p += t("Film má %d zvukových stop, H3 unese nejvýš %d.").format(stopy.size, MAX_NA_FILM)
        stopy.groupBy { it.usek }.forEach { (k, s) ->
            if (s.size > MAX_NA_USEK) p += t("Úsek %d: mluví v něm %d postav, nejvýš %d.").format(k + 1, s.size, MAX_NA_USEK)
        }
        stopy.forEach { s ->
            s.umisteni.filter { it.startS + it.delkaS > s.delkaUsekuS + 0.01 }.forEach {
                p += t("Úsek %d: replika „%s“ přesahuje konec úseku.").format(s.usek + 1, it.text.take(30))
            }
        }
        return p
    }

    // ---------- prompt úseku

    private val D_BLOK = Regex("""<d>\s*\[[^\]]*]\s*(.*?)</d>""", RegexOption.DOT_MATCHES_ALL)
    // „, in a deep, grumbling voice,“ i „in the same soft voice“ — přívlastky smí mít čárky.
    private val POPIS_HLASU = Regex(
        """,?\s*\b(?:in|with)\s+(?:a|an|the\s+same|his|her|their)\b[^<.;:]{0,80}?\bvoice\b(?:\s*,)?""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Doplní do hotového zadání úseku převzaté hlasy: definice `<Audio N>`, `audio
     * reuse` ve shrnutí, `partially_copy` v retenci a u každé repliky „exactly the
     * voice … of <Audio N>“ místo popisu hlasu.
     */
    fun doplnPrompt(prompt: String, stopyUseku: List<Stopa>, idMluvcich: Map<String, String>): String {
        if (stopyUseku.isEmpty()) return prompt
        var text = prompt
        stopyUseku.forEach { s ->
            s.umisteni.forEach { u ->
                val zacatekPopisu = text.indexOf("detailed_description:").coerceAtLeast(0)
                // Replika v popisu záběrů, která ještě nemá svůj pokyn (stejná replika dvakrát = dvě nahrávky).
                val m = D_BLOK.findAll(text).firstOrNull {
                    it.range.first >= zacatekPopisu && norm(it.groupValues[1]) == norm(u.text) &&
                        !text.substring(0, it.range.first).endsWith(hlasZ(s.cislo))
                } ?: return@forEach
                val zacatekVety = maxOf(
                    text.lastIndexOf('.', m.range.first - 1), text.lastIndexOf('\n', m.range.first - 1),
                    text.lastIndexOf("</d>", m.range.first - 1).let { if (it < 0) -1 else it + 3 },
                ) + 1
                val predtim = POPIS_HLASU.replace(text.substring(zacatekVety, m.range.first), " ")
                    .replace(Regex(""" {2,}"""), " ").replace(" ,", ",").trimEnd()
                val oddelovac = if (predtim.endsWith(",") || predtim.endsWith(":") || predtim.isEmpty()) " " else ", "
                text = text.substring(0, zacatekVety) + predtim + oddelovac + hlasZ(s.cislo) + text.substring(m.range.first)
            }
        }
        val definice = stopyUseku.joinToString("\n") { s ->
            val id = idMluvcich.entries.firstOrNull { it.key.equals(s.mluvci, ignoreCase = true) }?.value
            "<Audio ${s.cislo}> is the recorded voice of the speaker ${s.mluvci}" + (id?.let { " ($it)" } ?: "") + "."
        }
        text = vlozPred(text, "summary:", definice)
        text = text.replaceFirst("[reference generation]", "[reference generation + audio reuse]")
        val retence = stopyUseku.joinToString("\n") {
            "<Audio ${it.cislo}>: partially_copy - the spoken voice layer is copied exactly, with its own timing; " +
                "all other sounds are generated around it."
        }
        return vlozPred(text, "detailed_description:", retence)
    }

    /** Vloží řádky na konec sekce před [pole]; když pole chybí, přidá je na konec. */
    private fun vlozPred(text: String, pole: String, radky: String): String {
        val i = Regex("""(?m)^\s*${Regex.escape(pole)}""").find(text)?.range?.first
            ?: return text.trimEnd() + "\n" + radky + "\n"
        val pred = text.substring(0, i).trimEnd()
        return pred + "\n" + radky + "\n\n" + text.substring(i).trimStart('\n')
    }

    // ---------- graf

    /**
     * Doplní do grafu filmu zvukové stopy: média místo MediaBridge přímými vstupy
     * `media_N` uzlu úseků (obrázky první, pak stopy v pořadí `<Audio N>`).
     */
    fun doplnGraf(wf: JSONObject, stopy: List<Stopa>, nazvy: Map<File, String>, nKontext: String, nMedia: String): JSONObject {
        if (stopy.isEmpty()) return wf
        val kontext = wf.getJSONObject(nKontext).getJSONObject("inputs")
        val bridge = wf.getJSONObject(nMedia).getJSONObject("inputs")
        kontext.remove("media")
        var idx = 1
        for (i in 1..bridge.optInt("image_count")) {
            kontext.put("media_$idx", bridge.get("image_$i"))
            kontext.put("media_type_$idx", "image")
            idx++
        }
        wf.remove(nMedia)
        // Zvuková reference + navazování „latent_guide“ v ComfyUI 0.37.4 / balíku 1.2.0 spadne
        // od 2. úseku („shape mismatch … [680, 32] vs [754, 32]“): model počítá i se zvukem
        // navazovacího kontextu, který mu nepřijde (ověřeno pokusem 1. 10. 2026: chybí vždy
        // přesně 74 řádků, ať je stopa jakkoli dlouhá). „guide“ zvuk kontextu nebere a prošel —
        // 147 snímků na 6 s, žádné přehrané snímky na švu, replika ve 2. úseku zazní.
        if (stopy.isNotEmpty() && kontext.optString("segment_seconds").contains(",")) kontext.put("continuity_mode", "guide")

        var id = 700
        fun uzel(trida: String, nazev: String, vstupy: JSONObject): String {
            val k = (id++).toString()
            wf.put(k, JSONObject().put("class_type", trida).put("inputs", vstupy)
                .put("_meta", JSONObject().put("title", nazev)))
            return k
        }
        fun odkaz(k: String) = JSONArray().put(k).put(0)
        fun ticho(s: Double) = JSONObject().put("duration", s).put("sample_rate", VZORKOVANI).put("channels", 2)

        val nacteno = HashMap<File, String>()
        stopy.sortedBy { it.cislo }.forEach { s ->
            var stopa = uzel("EmptyAudio", "Stopa ${s.cislo} – ${s.mluvci}", ticho(s.delkaUsekuS))
            s.umisteni.forEach { u ->
                val nahravka = nacteno.getOrPut(u.soubor) {
                    uzel("LoadAudio", "Replika", JSONObject().put("audio", nazvy.getValue(u.soubor)))
                }
                val predsazka = uzel("EmptyAudio", "Ticho před replikou", ticho(u.startS))
                val posunuta = uzel("AudioConcat", "Replika v čase", JSONObject()
                    .put("audio1", odkaz(predsazka)).put("audio2", odkaz(nahravka)).put("direction", "after"))
                stopa = uzel("AudioMerge", "Do stopy", JSONObject()
                    .put("audio1", odkaz(stopa)).put("audio2", odkaz(posunuta)).put("merge_method", "add"))
            }
            kontext.put("media_$idx", odkaz(stopa))
            kontext.put("media_type_$idx", "audio")
            idx++
        }
        return wf
    }
}
