package cz.promptlab.h3video.ui

import android.content.Context
import android.content.Intent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia

/**
 * Odkud se vybírají fotky a videa — platí pro všechny karty.
 *
 * Systémový výběr fotek (výchozí) řadí podle data pořízení a appka to změnit
 * nemůže. Galerie Samsungu nechává oříznuté nebo upravené kopii původní datum,
 * takže se nová fotka ztratí mezi starými (uživatel 26. 9. 2026: „nemůžu ji
 * tam nikde dohledat"). Výběr ze souborů má sekci „Poslední" řazenou podle
 * data změny — čerstvě upravená fotka je v ní první.
 */
object VyberFotek {
    @Volatile var zeSouboru: Boolean = false
}

/** Typy souborů podle žádosti — stejné, jaké pustí systémový výběr. */
private fun typy(input: PickVisualMediaRequest): Array<String> = when (val t = input.mediaType) {
    is PickVisualMedia.ImageOnly -> arrayOf("image/*")
    is PickVisualMedia.VideoOnly -> arrayOf("video/*")
    is PickVisualMedia.SingleMimeType -> arrayOf(t.mimeType)
    else -> arrayOf("image/*", "video/*")
}

private fun dokument(input: PickVisualMediaRequest, vice: Boolean): Intent {
    val t = typy(input)
    return Intent(Intent.ACTION_OPEN_DOCUMENT)
        .addCategory(Intent.CATEGORY_OPENABLE)
        .setType(if (t.size == 1) t[0] else "*/*")
        .apply {
            if (t.size > 1) putExtra(Intent.EXTRA_MIME_TYPES, t)
            if (vice) putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
}

/** Náhrada `PickVisualMedia` — podle [VyberFotek] Galerie, nebo Soubory. */
class VyberMedii : PickVisualMedia() {
    override fun createIntent(context: Context, input: PickVisualMediaRequest): Intent =
        if (VyberFotek.zeSouboru) dokument(input, vice = false)
        else super.createIntent(context, input)
}

/** Náhrada `PickMultipleVisualMedia` — podle [VyberFotek] Galerie, nebo Soubory. */
class VyberViceMedii(max: Int) : PickMultipleVisualMedia(max) {
    override fun createIntent(context: Context, input: PickVisualMediaRequest): Intent =
        if (VyberFotek.zeSouboru) dokument(input, vice = true)
        else super.createIntent(context, input)
}
