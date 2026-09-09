package cz.promptlab.h3video.data

import java.io.File

/** App files and cache share a filesystem. Failed moves keep the source intact. */
internal object HistoryTrash {
    fun move(source: File, target: File): Boolean = runCatching {
        source.isFile && !target.exists() && source.renameTo(target)
    }.getOrDefault(false)
}
