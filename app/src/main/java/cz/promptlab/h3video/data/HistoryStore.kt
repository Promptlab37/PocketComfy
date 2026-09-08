package cz.promptlab.h3video.data

import android.content.Context
import java.io.File

data class VideoItem(
    val id: String,
    val fileName: String,
    val prompt: String,
    val createdAt: Long,
    val seconds: Float,
    val resolution: String,
    val seed: Long,
    val twoImages: Boolean,
    /** Je už kopie i v galerii telefonu (Filmy/H3 Video)? */
    val inGallery: Boolean = false,
    /** Název režimu (Mode.name), u starých záznamů prázdné. */
    val mode: String = "",
    /** Jak dlouho se generovalo (vteřiny). 0 = neznáme (staré záznamy, navázání). */
    val tookSeconds: Int = 0,
    /** Uživatelský název výstupu; původní zadání se nemění. */
    val title: String = "",
    val favorite: Boolean = false,
) {
    val displayTitle: String
        get() = title.ifBlank { prompt.lineSequence().firstOrNull { it.isNotBlank() }?.trim()?.take(96) ?: fileName }

    fun file(ctx: Context): File = File(videosDir(ctx), fileName)

    /**
     * Je výsledek obrázek, ne video? Karta Úprava obrázku vrací PNG, takže
     * galerie i obrazovka výsledku musí umět obojí. Pozná se to z přípony —
     * staré záznamy tím pádem zůstávají videem bez migrace.
     */
    val isImage: Boolean
        get() = fileName.substringAfterLast('.', "").lowercase() in
            listOf("png", "jpg", "jpeg", "webp")

    /**
     * Je výsledek video? Tedy nic z toho ostatního — obrázek, skladba ani
     * model. Odsud se pozná, že jde nabídnout zvětšení hotového videa.
     */
    val isVideoFile: Boolean get() = !isImage && !isAudio && !isModel3d

    /** Je výsledek skladba (karta Hudba)? Poznává se stejně — z přípony. */
    val isAudio: Boolean
        get() = fileName.substringAfterLast('.', "").lowercase() in
            listOf("mp3", "flac", "wav", "opus", "ogg", "m4a")

    /**
     * Je výsledek 3D model (karta 3D model)? Přípona `.glb` — obrázkový
     * náhled k němu neexistuje, takže galerie i obrazovka výsledku musí
     * počítat s tím, že se nedá vykreslit ani přehrát.
     */
    val isModel3d: Boolean
        get() = fileName.substringAfterLast('.', "").lowercase() in
            listOf("glb", "gltf")

    companion object {
        fun videosDir(ctx: Context): File =
            File(ctx.filesDir, "videos").also { it.mkdirs() }
    }
}

/** Seznam vygenerovaných videí. Soubory leží v filesDir/videos, metadata v prefs. */
class HistoryStore(private val ctx: Context) {

    private val sp = ctx.getSharedPreferences("h3history", Context.MODE_PRIVATE)

    // Čtení-úprava-zápis nad jedním klíčem v prefs: engine přidává výsledek
    // z IO vlákna, UI zároveň maže/označuje. Bez zámku by se změny přepsaly.
    // Engine i ViewModel mají vlastní instanci, zámek proto patří procesu.
    companion object { private val lock = Any() }

    fun all(): List<VideoItem> = synchronized(lock) { allLocked() }

    private fun allLocked(): List<VideoItem> {
        val raw = sp.getString("items", "[]")!!
        val decoded = HistoryCodec.decode(raw)
        // Před opravou uchovat původní data, ať poškozený záznam nezmizí beze stopy.
        if (decoded.damaged && !sp.contains("items_recovery")) {
            sp.edit().putString("items_recovery", raw).apply()
        }
        val list = decoded.items.filter { it.file(ctx).isFile }
        val sorted = list.sortedByDescending { it.createdAt }
        // Záznam bez souboru se rovnou i smaže – jinak by mrtvá metadata rostla donekonečna.
        if (list.size != decoded.items.size) persist(sorted)
        return sorted
    }

    fun add(item: VideoItem) = synchronized(lock) {
        val list = allLocked().toMutableList()
        list.removeAll { it.id == item.id }
        list.add(0, item)
        persist(list)
    }

    fun markInGallery(id: String) = synchronized(lock) {
        val list = allLocked().map { if (it.id == id) it.copy(inGallery = true) else it }
        persist(list)
    }

    fun rename(id: String, title: String) = synchronized(lock) {
        persist(allLocked().map { if (it.id == id) it.copy(title = title.trim().take(120)) else it })
    }

    fun toggleFavorite(id: String) = synchronized(lock) {
        persist(allLocked().map { if (it.id == id) it.copy(favorite = !it.favorite) else it })
    }

    /** Kolik místa zabírají kopie videí uvnitř aplikace. */
    fun totalBytes(): Long = all().sumOf { runCatching { it.file(ctx).length() }.getOrDefault(0L) }

    fun remove(item: VideoItem) = synchronized(lock) {
        runCatching { item.file(ctx).delete() }
        persist(allLocked().filterNot { it.id == item.id })
    }

    /** Vyřadí jen záznam a soubor nechá být – pro mazání s možností Vrátit. */
    fun removeEntry(item: VideoItem) = synchronized(lock) {
        persist(allLocked().filterNot { it.id == item.id })
    }

    private fun persist(list: List<VideoItem>) {
        sp.edit().putString("items", HistoryCodec.encode(list)).apply()
    }
}
