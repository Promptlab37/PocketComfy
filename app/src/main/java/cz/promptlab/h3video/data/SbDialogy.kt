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

    // ---------- časování (5.66, rešerše dramaturg + scenárista + střihač)
    // Přirozená mezera mezi mluvčími je ~0,2 s (Stivers 2009, Heldner & Edlund 2010); od ~0,7 s
    // ji divák čte jako váhání, kolem 1 s je ticho „událost“ (Jefferson 1989). Střih krátce po
    // konci myšlenky (Murch). Dřív se rezervy sčítaly (dozvuk 1 s + zaokrouhlení na 0,5 s +
    // rezerva úseku) a za replikou bylo 1,3–2,5 s ticha (uživatel 1. 10. 2026: „nepřirozené“).

    /** Vzduch před replikou: běžný záběr / rychlé podání / první panel úseku / celek nebo úvod filmu. */
    const val NASTUP_S = 0.25
    const val NASTUP_RYCHLE_S = 0.15
    const val NASTUP_USEK_S = 0.40
    const val NASTUP_CELEK_S = 0.75
    const val NASTUP_VAHAVE_NAVIC_S = 0.3
    const val NASTUP_MAX_S = 1.0
    /** Mezera mezi replikami v panelu: jiný mluvčí / stejný mluvčí / pokračování souvětí / váhání. */
    const val MEZERA_STRIDANI_S = 0.25
    const val MEZERA_STEJNY_S = 0.40
    const val MEZERA_SOUVETI_S = 0.25
    const val MEZERA_VAHANI_S = 0.8
    /** Vzduch za replikou: před další replikou / před tichým záběrem / po pointě. */
    const val DOZVUK_NA_RECI_S = 0.25
    const val DOZVUK_NA_TICHO_S = 0.5
    const val DOZVUK_POINTA_S = 1.0
    /**
     * Konec úseku a filmu: [SbFilmPlan.rozdel] přidá k poslednímu mluvenému panelu úseku ještě
     * [SbFilmPlan.REZERVA_KONCE_S] (0,5 s) → za replikou je celkem 1,0 s na konci úseku a 2,0 s
     * na konci filmu (H3 umí repliku posunout; s 0,1 s rezervy se konec uřízl).
     */
    const val DOZVUK_USEK_S = 0.5
    const val DOZVUK_FILM_S = 1.5
    const val MIN_ZABER_S = 1.5
    /** Mluvený panel delší o víc než tohle se zkrátí na řeč a vzduch kolem ní. */
    const val TOLERANCE_ZKRACENI_S = 0.4
    const val SNIMEK_S = 1.0 / 24
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

    /**
     * Nahrávka repliky: platí jen pro stejný text a stejný hlas. [delkaS] = délka řeči,
     * [odS] = kde v souboru řeč začíná (ořez nechává 30 ms před řečí, 5.66).
     */
    data class Nahravka(val soubor: File, val text: String, val hlas: String, val delkaS: Double, val odS: Double = 0.0)

    fun klic(cislo: Int, index: Int) = "$cislo:$index"

    fun nahravkaZ(s: String?): Nahravka? = runCatching {
        val j = JSONObject(s ?: return null)
        Nahravka(File(j.getString("soubor")), j.getString("text"), j.getString("hlas"), j.getDouble("delka"), j.optDouble("od", 0.0))
    }.getOrNull()

    fun zakoduj(n: Nahravka): String = JSONObject().put("soubor", n.soubor.absolutePath)
        .put("text", n.text).put("hlas", n.hlas).put("delka", n.delkaS).put("od", n.odS).toString()

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

    private fun obsahuje(p: SbPanel, vzor: Regex) = vzor.containsMatchIn((p.popis + " " + p.podani).lowercase())
    private val RYCHLE = Regex("""rychl|naštvan|křič|zařv|skočí do řeči|angry|shout|fast|snap""")
    private val VAHAVE = Regex("""váhav|smut|zaražen|nejist|hesitant|sad|unsure""")
    private val CELEK = Regex("""wide|establish|long shot|celek|úvodní""")
    // Celá slova — „points his finger“ (ukazuje) není pointa (test na emulátoru 1. 10. 2026).
    private val POINTA = Regex("""(?<![\p{L}])(pointa|pointou|pointě|vtip|reakce|reakcí|punchline|reaction)(?![\p{L}])""")
    private val DRZ_DELKU = Regex("""(?<![\p{L}])(pauz\p{L}*|ticho|mlčí|mlčky|zírá|reakce|reakcí|pointa|pointou|dlouze|váhá|silence|silent|stares?|staring|reaction|pause|punchline)(?![\p{L}])""")

    fun puvodniDelka(s: SbFilmScene, p: SbPanel): Double = s.delkyStoryboardu[p.cislo] ?: p.sekundy

    private data class Pozice(val prvniVeFilmu: Boolean, val prvniVUseku: Boolean, val posledniVUseku: Boolean, val posledniVeFilmu: Boolean)

    private fun pozice(panely: List<SbPanel>): Map<Int, Pozice> {
        val useky = SbFilmPlan.rozdel(panely)
        val prvni = useky.mapNotNull { it.panely.firstOrNull()?.cislo }.toSet()
        val posledni = useky.mapNotNull { it.panely.lastOrNull()?.cislo }.toSet()
        return panely.mapIndexed { i, p ->
            p.cislo to Pozice(i == 0, p.cislo in prvni, p.cislo in posledni, i == panely.lastIndex)
        }.toMap()
    }

    private fun nastup(p: SbPanel, poz: Pozice): Double = when {
        poz.prvniVeFilmu || obsahuje(p, CELEK) || CELEK.containsMatchIn(p.typ.lowercase()) -> NASTUP_CELEK_S
        poz.prvniVUseku -> NASTUP_USEK_S
        obsahuje(p, RYCHLE) -> NASTUP_RYCHLE_S
        else -> NASTUP_S
    } + if (obsahuje(p, VAHAVE)) NASTUP_VAHAVE_NAVIC_S else 0.0

    private fun mezera(a: Pair<String, String>, b: Pair<String, String>, p: SbPanel): Double = when {
        obsahuje(p, VAHAVE) || a.second.trimEnd().endsWith("…") || a.second.trimEnd().endsWith("...") -> MEZERA_VAHANI_S
        !a.first.trim().equals(b.first.trim(), ignoreCase = true) -> MEZERA_STRIDANI_S
        a.second.trimEnd().endsWith(",") -> MEZERA_SOUVETI_S
        else -> MEZERA_STEJNY_S
    }

    private fun dozvuk(p: SbPanel, dalsi: SbPanel?, poz: Pozice): Double = when {
        poz.posledniVeFilmu -> DOZVUK_FILM_S
        poz.posledniVUseku -> DOZVUK_USEK_S
        obsahuje(p, POINTA) -> DOZVUK_POINTA_S
        dalsi != null && SbFilmPrepis.repliky(dalsi.repliky).isNotEmpty() -> DOZVUK_NA_RECI_S
        else -> DOZVUK_NA_TICHO_S
    }

    private fun snap(t: Double) = Math.ceil(t / SNIMEK_S - 1e-6) * SNIMEK_S

    /** Řeč panelu (repliky + mezery mezi nimi); null = panel bez replik, nebo nějaká nenamluvená. */
    private fun recPanelu(s: SbFilmScene, p: SbPanel, existuje: (File) -> Boolean): Double? {
        val repl = SbFilmPrepis.repliky(p.repliky)
        if (repl.isEmpty()) return null
        val n = repl.mapIndexed { i, (kdo, text) -> platna(s, p, i, kdo, text, existuje) ?: return null }
        return n.sumOf { it.delkaS } + repl.zipWithNext { a, b -> mezera(a, b, p) }.sum()
    }

    /**
     * Délky panelů podle nahrávek (5.66): mluvený panel = nástup + řeč + dozvuk (prodlouží se,
     * nebo zkrátí, když je delší o víc než [TOLERANCE_ZKRACENI_S] a nemá v popisu pauzu/reakci);
     * panely bez řeči zůstanou přesně podle storyboardu. Počítá se vždy od délek ze storyboardu.
     */
    fun upravPanely(s: SbFilmScene, existuje: (File) -> Boolean = { it.exists() }): List<SbPanel> {
        var panely = s.panely.map { it.copy(sekundy = puvodniDelka(s, it)) }
        repeat(2) {   // poloha v úseku závisí na délkách — dva průchody stačí
            val poz = pozice(panely)
            panely = panely.mapIndexed { i, p ->
                val sb = puvodniDelka(s, p)
                val rec = recPanelu(s, p, existuje) ?: return@mapIndexed p.copy(sekundy = sb)
                val po = poz.getValue(p.cislo)
                val potreba = maxOf(MIN_ZABER_S, nastup(p, po) + rec + dozvuk(p, panely.getOrNull(i + 1), po))
                val nova = when {
                    potreba > sb -> snap(potreba)
                    obsahuje(p, DRZ_DELKU) || sb - potreba <= TOLERANCE_ZKRACENI_S -> sb
                    else -> snap(potreba)
                }
                p.copy(sekundy = nova)
            }
        }
        return panely
    }

    // ---------- plán stop

    data class Umisteni(val soubor: File, val startS: Double, val delkaS: Double, val text: String)

    /** Stopa jednoho mluvčího v jednom úseku; [cislo] = `<Audio N>` v celém filmu. */
    data class Stopa(val usek: Int, val mluvci: String, val cislo: Int, val delkaUsekuS: Double, val umisteni: List<Umisteni>)

    /**
     * Stopy filmu: řeč každé repliky začne po nástupu svého panelu (přebytek délky panelu jde
     * z poloviny před repliku — pohled, pak řeč — nejvýš na [NASTUP_MAX_S]), další repliky
     * po přirozené mezeře. [Umisteni.startS] je začátek souboru, ne řeči.
     */
    fun planuj(s: SbFilmScene, useky: List<SbUsek> = s.useky, existuje: (File) -> Boolean = { it.exists() }): List<Stopa> {
        if (!s.dialogyHiggs) return emptyList()
        val poz = pozice(s.panely)
        val stopy = mutableListOf<Stopa>()
        var cislo = 0
        useky.forEachIndexed { k, u ->
            val poMluvcich = linkedMapOf<String, MutableList<Umisteni>>()
            var zacatekPanelu = 0.0
            u.panely.forEach { p ->
                val vScene = s.panely.firstOrNull { it.cislo == p.cislo } ?: p
                val i = s.panely.indexOf(vScene)
                val po = poz[p.cislo] ?: Pozice(i == 0, true, true, i == s.panely.lastIndex)
                val repl = SbFilmPrepis.repliky(p.repliky)
                val rec = recPanelu(s, p, existuje)
                val zaklad = nastup(p, po)
                val navic = if (rec == null) 0.0
                else vScene.sekundy - (zaklad + rec + dozvuk(p, s.panely.getOrNull(i + 1), po))
                var t = zacatekPanelu + minOf(NASTUP_MAX_S, zaklad + maxOf(0.0, navic) * 0.5)
                repl.forEachIndexed { r, (kdo, text) ->
                    val n = platna(s, p, r, kdo, text, existuje) ?: return@forEachIndexed
                    if (r > 0) t += mezera(repl[r - 1], repl[r], p)
                    poMluvcich.getOrPut(kdo.trim()) { mutableListOf() } += Umisteni(n.soubor, maxOf(0.0, t - n.odS), n.delkaS + n.odS, text)
                    t += n.delkaS
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
        // Navazování zůstává latent_guide. Pád od 2. úseku („shape mismatch … 74 řádků“) dělal
        // balík ComfyUI-H3-Multishot (h3_avbank_probe.py zahazoval zvuk kontextu); opraveno na
        // serveru 1. 10. 2026, nahlášeno autorovi (issue #23). Ověřeno během 2 × 3 s.

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
