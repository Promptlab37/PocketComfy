package cz.promptlab.h3video.data

import android.content.Context
import android.content.SharedPreferences

/** Nastavení a poslední použité hodnoty. Držené v SharedPreferences, ať appka nic neztratí. */
class AppSettings(ctx: Context) {

    private val sp: SharedPreferences =
        ctx.getSharedPreferences("h3video", Context.MODE_PRIVATE)

    /**
     * Tajnosti zvlášť: soubor h3secrets je vyřazený ze zálohy do Googlu
     * (res/xml/backup_rules.xml), běžné nastavení se zálohuje dál.
     */
    private val secrets: SharedPreferences =
        ctx.getSharedPreferences("h3secrets", Context.MODE_PRIVATE)

    init {
        // jednorázový přesun tokenu ze zálohovaného souboru do tajností
        sp.getString("githubToken", null)?.let { old ->
            if (old.isNotBlank() && secrets.getString("githubToken", "").isNullOrBlank()) {
                secrets.edit().putString("githubToken", old).apply()
            }
            sp.edit().remove("githubToken").apply()
        }
    }

    /**
     * Prázdná hodnota znamená „server ještě nikdo nenastavil" – veřejné
     * sestavení žádnou výchozí adresu nemá a appka se při prvním spuštění
     * zeptá. Osobní sestavení si výchozí adresu nese z `local.properties`.
     */
    var serverUrl: String
        get() = sp.getString("server", "")!!.ifBlank { DEFAULT_SERVER }
        set(v) = sp.edit().putString("server", normalizeUrl(v)).apply()

    /** Je kam posílat úlohy? Bez adresy se místo appky ukáže úvodní obrazovka. */
    val serverConfigured: Boolean get() = serverUrl.isNotBlank()

    /**
     * Token pro čtení vydání z privátního repozitáře na GitHubu.
     *
     * Od verze 2.20 je zapečený v sestavení (`BuildConfig.GITHUB_TOKEN`, plněný
     * z `local.properties` mimo repozitář), aby aktualizace chodily rovnou po
     * instalaci a nebylo co vyplňovat. Token vepsaný v Nastavení má přednost –
     * kdyby ten zapečený vypršel, dá se přebít bez nového sestavení.
     */
    var githubToken: String
        get() = secrets.getString("githubToken", "")!!
            .ifBlank { cz.promptlab.h3video.BuildConfig.GITHUB_TOKEN }
        set(v) = secrets.edit().putString("githubToken", v.trim()).apply()

    /** Jen ručně vepsaný token – do políčka v Nastavení nepatří ten zapečený. */
    val githubTokenRaw: String get() = secrets.getString("githubToken", "")!!

    /** Umí appka kontrolovat aktualizace i bez toho, aby uživatel něco vyplnil? */
    val hasBuiltInToken: Boolean get() = cz.promptlab.h3video.BuildConfig.GITHUB_TOKEN.isNotBlank()

    /**
     * Higgs Audio Studio – samostatný server na počítači, ne ComfyUI. Adresa se
     * odvodí od ComfyUI (stejný počítač, port 7860), dokud ji uživatel nezmění;
     * díky tomu není co nastavovat, když je všechno na jednom stroji.
     */
    var higgsUrl: String
        get() = sp.getString("higgs", "")!!.ifBlank { defaultHiggsUrl() }
        // Pozor: normalizeUrl() doplňuje porty ComfyUI (8188/8189), tady by dala
        // adresu, na které Higgs neposlouchá. Proto vlastní doplnění portu.
        set(v) = sp.edit().putString("higgs", normalizeHiggsUrl(v)).apply()

    /** Co si uživatel skutečně uložil (prázdné = odvodit od ComfyUI). */
    val higgsUrlRaw: String get() = sp.getString("higgs", "")!!

    private fun defaultHiggsUrl(): String {
        val host = serverUrl.substringAfter("://").substringBefore(':')
        return if (host.isBlank()) "" else "http://$host:$HIGGS_PORT"
    }

    /**
     * Přístupový kód Higgse (leží na počítači v `outputs\.higgs_token`). Zvenčí
     * ho server vyžaduje; přes Tailscale dorazí požadavek jako místní a projde
     * i bez něj, proto je políčko nepovinné. Patří mezi tajnosti, ne do zálohy.
     */
    var higgsToken: String
        get() = secrets.getString("higgsToken", "")!!
        set(v) = secrets.edit().putString("higgsToken", v.trim()).apply()

    /**
     * Ukládat hotová videa rovnou do galerie telefonu. Vypnuté schválně –
     * uživatel si stahuje jen to, co se mu povedlo.
     */
    var autoSaveToGallery: Boolean
        get() = sp.getBoolean("autoSave", false)
        set(v) = sp.edit().putBoolean("autoSave", v).apply()

    /** Jazyk rozhraní: `system` / `cs` / `en` (viz [Jazyk]). */
    var jazyk: String
        get() = sp.getString("jazyk", Jazyk.Volba.SYSTEM.kod)!!
        set(v) = sp.edit().putString("jazyk", v).apply()

    /**
     * Další LoRA nad rámec Turbo. Ukládají se společné pro všechny karty –
     * jsou to vlastnosti modelu, ne způsobu zadání.
     */
    var extraLoras: List<LoraEntry>
        get() = runCatching {
            val arr = org.json.JSONArray(sp.getString("extraLoras", "[]"))
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                LoraEntry(o.getString("name"), o.optBoolean("on", true), o.optDouble("s", 1.0).toFloat())
            }
        }.getOrDefault(emptyList())
        set(v) {
            val arr = org.json.JSONArray()
            v.forEach {
                arr.put(
                    org.json.JSONObject()
                        .put("name", it.name).put("on", it.enabled).put("s", it.strength.toDouble())
                )
            }
            sp.edit().putString("extraLoras", arr.toString()).apply()
        }

    /** Rozdělaná úloha – aby se sledování dalo obnovit i po restartu appky. */
    var activePromptId: String?
        get() = sp.getString("activePromptId", null)
        set(v) = sp.edit().putString("activePromptId", v).apply()

    var activeLabel: String
        get() = sp.getString("activeLabel", "")!!
        set(v) = sp.edit().putString("activeLabel", v).apply()

    /**
     * Běží rozdělaná úloha z karty All in One? Ta má vlastní čísla uzlů, takže
     * bez téhle informace by ukazatel průběhu po restartu appky hlásil fáze
     * podle hlavního workflow – a tam osmička znamená model, tady šum.
     */
    var activeAio: Boolean
        get() = sp.getBoolean("activeAio", false)
        set(v) = sp.edit().putBoolean("activeAio", v).apply()

    /** Běží rozdělaná úprava obrázku (Krea 2)? Kvůli textům a fázím po restartu. */
    var activeEdit: Boolean
        get() = sp.getBoolean("activeEdit", false)
        set(v) = sp.edit().putBoolean("activeEdit", v).apply()

    /** Běží rozdělané zvětšování (SeedVR2)? Kvůli textům a fázím po restartu. */
    var activeUpscale: Boolean
        get() = sp.getBoolean("activeUpscale", false)
        set(v) = sp.edit().putBoolean("activeUpscale", v).apply()

    /** Běží rozdělaný obrázek z textu (Z-Image)? Kvůli textům a fázím po restartu. */
    var activeT2i: Boolean
        get() = sp.getBoolean("activeT2i", false)
        set(v) = sp.edit().putBoolean("activeT2i", v).apply()

    /** Běží rozdělaná hudba (ACE-Step)? Kvůli textům a fázím po restartu. */
    var activeMusic: Boolean
        get() = sp.getBoolean("activeMusic", false)
        set(v) = sp.edit().putBoolean("activeMusic", v).apply()

    /** Běží rozdělaná oprava fotky (Qwen 2511)? Kvůli textům a fázím po restartu. */
    var activeRestore: Boolean
        get() = sp.getBoolean("activeRestore", false)
        set(v) = sp.edit().putBoolean("activeRestore", v).apply()

    /** Běží rozdělaná výměna tváře (ACE++)? Kvůli textům a fázím po restartu. */
    var activeSwap: Boolean
        get() = sp.getBoolean("activeSwap", false)
        set(v) = sp.edit().putBoolean("activeSwap", v).apply()

    // ------------------------------------------------------------ parametry

    /** Prompt se pamatuje zvlášť pro každou kartu – jsou to jiné druhy zadání. */
    fun promptFor(mode: Mode): String = sp.getString("prompt_${mode.name}", "")!!

    /**
     * Jednorázový přechod na ULTRA workflow. Starší verze aplikace jely na jiném
     * workflow a měly uložené jeho hodnoty (20 kroků, res_multistep, simple).
     * Ty by se po aktualizaci přenesly dál a generovalo by se úplně jiným
     * nastavením, než jaké má vyladěné ULTRA workflow.
     */
    private fun migrateToUltra() {
        if (sp.getBoolean(MIGRATED, false)) return
        val d = GenParams()
        sp.edit()
            .putInt("steps", d.steps)
            .putString("sampler", d.sampler)
            .putString("scheduler", d.scheduler)
            .putFloat("shiftVideo", d.shiftVideo)
            .putFloat("shiftAudio", d.shiftAudio)
            .putInt("crf", d.crf)
            .putBoolean("spectrum", d.spectrum)
            .putBoolean("sage", d.sageAttention)
            .putBoolean("turbo", true)
            .putBoolean(MIGRATED, true)
            .apply()
    }

    /**
     * Profil Kvalita měl v 2.12 a 2.13 `res_multistep + simple` a vypnutý Spectrum
     * podle oficiální šablony ComfyUI. Na 16GB kartě to při větším rozlišení
     * přeteklo a běh se zastavil. Opravené hodnoty se samy neprojeví – uložené
     * nastavení má přednost – takže se jednou přepíšou tady.
     */
    private fun migrateFullProfile() {
        if (sp.getBoolean(MIGRATED_FULL, false)) return
        if (sp.getString("profile", "") == Profile.FULL.name) {
            val d = Profile.FULL
            sp.edit()
                .putString("sampler", d.sampler)
                .putString("scheduler", d.scheduler)
                .putFloat("shiftVideo", d.shiftVideo)
                .putBoolean("spectrum", d.spectrum)
                .apply()
        }
        sp.edit().putBoolean(MIGRATED_FULL, true).apply()
    }

    fun load(): GenParams {
        migrateToUltra()
        migrateFullProfile()
        // Uložený režim může být z verze, kde karet bylo sedm – ty zrušené
        // spadnou na All in One, ať se appka po aktualizaci nepokouší otevřít
        // obrazovku, která už neexistuje.
        val mode = runCatching { Mode.valueOf(sp.getString("mode", Mode.ALLINONE.name)!!) }
            .getOrDefault(Mode.ALLINONE)
        val defaults = GenParams()
        val profile = runCatching { Profile.valueOf(sp.getString("profile", defaults.profile.name)!!) }
            .getOrDefault(defaults.profile)
        return GenParams(
            mode = mode,
            profile = profile,
            turboLora = sp.getString("turboLora", defaults.turboLora)!!,
            // Vypnutá LoRA je stav profilu Kvalita; u Turba se drží uložená volba.
            turboLoraOn = sp.getBoolean("turboLoraOn", defaults.turboLoraOn),
            turboLoraStrength = sp.getFloat("turboLoraStrength", defaults.turboLoraStrength),
            prompt = promptFor(mode),
            seconds = sp.getInt("seconds", defaults.seconds),
            aspect = runCatching { Aspect.valueOf(sp.getString("aspect", defaults.aspect.name)!!) }
                .getOrDefault(defaults.aspect),
            megapixels = sp.getFloat("megapixels", defaults.megapixels),
            refImageSize = sp.getString("refSize", defaults.refImageSize)!!,
            timelineProject = sp.getString("tlProject", defaults.timelineProject)!!,
            unetFl2va = sp.getString("unetFl2va", defaults.unetFl2va)!!,
            steps = sp.getInt("steps", defaults.steps),
            sampler = sp.getString("sampler", defaults.sampler)!!,
            scheduler = sp.getString("scheduler", defaults.scheduler)!!,
            seed = sp.getLong("seed", 604429284680317L),
            randomSeed = sp.getBoolean("randomSeed", defaults.randomSeed),
            shiftVideo = sp.getFloat("shiftVideo", defaults.shiftVideo),
            shiftAudio = sp.getFloat("shiftAudio", defaults.shiftAudio),
            spectrum = sp.getBoolean("spectrum", defaults.spectrum),
            sageAttention = sp.getBoolean("sage", defaults.sageAttention),
            crf = sp.getInt("crf", defaults.crf),
            livePreview = sp.getBoolean("livePreview", defaults.livePreview),
            teaCache = sp.getBoolean("teaCache", defaults.teaCache),
            zimageNsfw = sp.getBoolean("zimageNsfw", defaults.zimageNsfw),
            zimageNsfwSila = sp.getFloat("zimageNsfwSila", defaults.zimageNsfwSila),
            zimageModel = sp.getString("zimageModel", defaults.zimageModel)!!,
            zimageNsfwLora = sp.getString("zimageNsfwLora", defaults.zimageNsfwLora)!!,
            rewriteHudba = sp.getBoolean("rewriteHudba", defaults.rewriteHudba),
            extraLoras = extraLoras,
            // Enkodér patří k profilu, ne k uloženému nastavení – jinak by po
            // restartu jel profil V2 se starým textovým enkodérem verze 1.
            clipName = profile.clip,
        )
    }

    fun save(p: GenParams) {
        sp.edit().apply {
            putString("mode", p.mode.name)
            putString("profile", p.profile.name)
            putString("turboLora", p.turboLora)
            putBoolean("turboLoraOn", p.turboLoraOn)
            putFloat("turboLoraStrength", p.turboLoraStrength)
            putString("prompt_${p.mode.name}", p.prompt)
            putInt("seconds", p.seconds)
            putString("aspect", p.aspect.name)
            putFloat("megapixels", p.megapixels)
            putString("refSize", p.refImageSize)
            putString("tlProject", p.timelineProject)
            putString("unetFl2va", p.unetFl2va)
            putInt("steps", p.steps)
            putString("sampler", p.sampler)
            putString("scheduler", p.scheduler)
            putLong("seed", p.seed)
            putBoolean("randomSeed", p.randomSeed)
            putFloat("shiftVideo", p.shiftVideo)
            putFloat("shiftAudio", p.shiftAudio)
            putBoolean("spectrum", p.spectrum)
            putBoolean("sage", p.sageAttention)
            putInt("crf", p.crf)
            putBoolean("livePreview", p.livePreview)
            putBoolean("teaCache", p.teaCache)
            putBoolean("zimageNsfw", p.zimageNsfw)
            putFloat("zimageNsfwSila", p.zimageNsfwSila)
            putString("zimageModel", p.zimageModel)
            putString("zimageNsfwLora", p.zimageNsfwLora)
            putBoolean("rewriteHudba", p.rewriteHudba)
        }.apply()
        extraLoras = p.extraLoras
    }

    companion object {
        /** Značka, že už proběhl přechod na hodnoty z ULTRA workflow. */
        private const val MIGRATED = "migratedUltra"

        /** Značka opravy profilu Kvalita z verzí 2.12–2.13 (viz migrateFullProfile). */
        // Číslo na konci se zvedá, když se hodnoty profilu opraví znovu – jinak
        // by se u toho, kdo má starou značku, oprava neprojevila.
        private const val MIGRATED_FULL = "migratedFullProfile2"

        /**
         * Výchozí adresa serveru ze sestavení (`local.properties` →
         * `BuildConfig`). Veřejné sestavení ji nemá – osobní IP adresy do
         * repozitáře ani do cizích telefonů nepatří.
         */
        val DEFAULT_SERVER: String
            get() = cz.promptlab.h3video.BuildConfig.DEFAULT_SERVER

        /** Higgs Audio Studio na počítači; standardně na portu 7860. */
        const val HIGGS_PORT = 7860

        /** Rychlé volby serveru ze sestavení; formát `url|popisek;url|popisek`. */
        val SUGGESTED: List<Pair<String, String>>
            get() = parsePresets(cz.promptlab.h3video.BuildConfig.SERVER_PRESETS)

        fun parsePresets(raw: String): List<Pair<String, String>> = raw
            .split(';')
            .mapNotNull { part ->
                val p = part.split('|')
                val url = p.getOrNull(0)?.trim().orEmpty()
                if (url.isBlank()) null
                else url to (p.getOrNull(1)?.trim().orEmpty().ifBlank { url })
            }

        /** Prázdné = odvodit od ComfyUI. Bez portu se doplní 7860, kde Higgs běží. */
        fun normalizeHiggsUrl(raw: String): String {
            var s = raw.trim().trimEnd('/')
            if (s.isEmpty()) return ""
            if (!s.startsWith("http://") && !s.startsWith("https://")) s = "http://$s"
            if (!s.substringAfter("://").contains(':')) s = "$s:$HIGGS_PORT"
            return s
        }

        /**
         * Prázdný vstup zůstává prázdný (= server nenastaven). Adresa bez portu
         * dostane 8188 (výchozí ComfyUI); když ale host odpovídá výchozímu
         * serveru ze sestavení, převezme se jeho port – typicky most
         * `tailscale serve`, kde ComfyUI na 8188 zvenčí neposlouchá.
         */
        fun normalizeUrl(raw: String, default: String = DEFAULT_SERVER): String {
            var s = raw.trim().trimEnd('/')
            if (s.isEmpty()) return ""
            if (!s.startsWith("http://") && !s.startsWith("https://")) s = "http://$s"
            if (!s.substringAfter("://").contains(':')) {
                val host = s.substringAfter("://")
                val defHost = default.substringAfter("://").substringBefore(':')
                val defPort = default.substringAfterLast(':').toIntOrNull()
                s = if (defPort != null && host == defHost) "$s:$defPort" else "$s:8188"
            }
            return s
        }
    }
}
