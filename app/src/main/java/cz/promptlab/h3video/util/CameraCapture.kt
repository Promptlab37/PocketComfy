package cz.promptlab.h3video.util

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * Focení rovnou z aplikace. Systémový fotoaparát ukládá snímek do našeho souboru
 * v cache, odkud se pak zpracuje úplně stejně jako obrázek z galerie – narovná se
 * podle EXIF, zmenší a uloží mezi reference (viz [ImageUtils.importToApp]).
 *
 * Proč se nikde nežádá o oprávnění `CAMERA`: focení obstarává cizí aplikace, ne my.
 * Kdyby appka oprávnění `CAMERA` v manifestu deklarovala, Android by ho začal
 * u `ACTION_IMAGE_CAPTURE` vyžadovat – proto ho tam schválně nemáme a uživatel
 * žádný dialog navíc neuvidí.
 */
object CameraCapture {

    private const val DIR = "fotky"

    /** Jak dlouho se drží odfocené originály, než je úklid smaže (24 h). */
    private const val KEEP_MS = 24L * 60 * 60 * 1000

    /**
     * Připraví prázdný soubor a vrátí adresu, kam do něj smí fotoaparát zapsat.
     * Musí to být `content://` adresa přes FileProvider – od Androidu 7 systém
     * předání `file://` do cizí aplikace odmítá.
     */
    fun newPhotoUri(ctx: Context): Uri? = runCatching {
        val dir = File(ctx.cacheDir, DIR).apply { mkdirs() }
        cleanUp(dir)
        val file = File(dir, "foto_${System.currentTimeMillis()}.jpg")
        FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
    }.getOrNull()

    /**
     * Originály z fotoaparátu jsou velké a po zpracování už nejsou k ničemu –
     * referenci si appka ukládá zvlášť. Bez úklidu by cache rostla donekonečna.
     */
    private fun cleanUp(dir: File) {
        val limit = System.currentTimeMillis() - KEEP_MS
        dir.listFiles()?.forEach { if (it.lastModified() < limit) it.delete() }
    }
}
