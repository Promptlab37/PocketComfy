package cz.promptlab.h3video.data

import android.graphics.Bitmap
import androidx.compose.runtime.Immutable
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Časová osa pro kolegovy LSI nody (`LSIMinimaxTimeline` + `…TimelineRender`).
 *
 * Proti kartě Režisér je tohle ta cesta na DELŠÍ video. MiniMax H3 zvládne
 * jeden záběr do 15 s; osa se proto renderuje po segmentech za sebou a každý
 * další může začít **posledním snímkem toho předchozího**
 * (`use_previous_last_frame`), takže na sebe navazují a výsledek se slepí
 * dohromady. Hotové segmenty si uzel ukládá do cache pod `cache_project`, takže
 * úprava jednoho záběru neznamená počítat celé video znovu.
 *
 * Schéma JSONu je autorovo: `{"version":1,"segments":[…]}`, média jako
 * `{"path":"podsložka/soubor.jpg","name":"soubor.jpg"}` relativně ke složce
 * `ComfyUI/input`.
 */
enum class SegmentMode(val kod: String, val nazev: String, val popis: String) {
    /** Jen z textu – nic se nevkládá. */
    TEXT("T2V", "Text", "Jen z popisu"),

    /** Z obrázku: buď z vlastního, nebo navázáním na předchozí segment. */
    IMAGE("I2V", "Obrázek", "Z prvního snímku"),
}

@Immutable
data class TimelineSegment(
    val key: Int,
    val mode: SegmentMode = SegmentMode.TEXT,
    val prompt: String = "",
    val seconds: Float = 8f,
    val image: File? = null,
    val thumb: Bitmap? = null,
    /** Navázat na poslední snímek předchozího segmentu (jen [SegmentMode.IMAGE]). */
    val inheritPrevious: Boolean = false,
) {
    /**
     * Uzel má tři možnosti: dva snímky (první i poslední), jen první
     * (`first_frame_only`), nebo navázání na předchozí. Appka nabízí jen první
     * snímek a navázání – dva snímky patří kartě Obrázek → video.
     */
    val firstFrameOnly: Boolean get() = mode == SegmentMode.IMAGE && !inheritPrevious
}

@Immutable
data class TimelineScene(
    val segments: List<TimelineSegment> = listOf(TimelineSegment(key = 1)),
    /** Styl a pravidla pro celé video – uzel je přidá ke každému segmentu. */
    val globalPrompt: String = DEFAULT_GLOBAL,
    /**
     * Jméno projektu v cache uzlu. Drží hotové segmenty mezi běhy, takže se
     * přegeneruje jen to, co se změnilo.
     */
    val project: String = "h3app",
    /** Přegenerovat jen tenhle segment (1 = první), 0 = celou osu. */
    val onlySegment: Int = 0,
) {
    val totalSeconds: Float get() = segments.sumOf { it.seconds.toDouble() }.toFloat()

    val withImage: List<TimelineSegment> get() = segments.filter { it.image != null }

    val canAdd: Boolean get() = segments.size < MAX_SEGMENTS

    companion object {
        const val MAX_SEGMENTS = 12

        /** Jeden segment nesmí přes 15 s, to je strop modelu. */
        const val MAX_SEGMENT_SECONDS = 15f

        val DEFAULT_GLOBAL =
            "Live-action, cinematic, consistent lighting and color across the whole film."
    }
}

/**
 * JSON pro `LSIMinimaxTimeline`.
 *
 * @param imageNames jména nahraných snímků ve složce `input`, v pořadí
 *   odpovídajícím [TimelineScene.withImage].
 */
fun buildLsiTimeline(scene: TimelineScene, imageNames: List<String>): String {
    val segments = JSONArray()
    scene.segments.forEachIndexed { i, seg ->
        val o = JSONObject()
            .put("mode", seg.mode.kod)
            .put("prompt", seg.prompt.trim())
            .put("duration_seconds", seg.seconds.toDouble())

        if (seg.mode == SegmentMode.IMAGE) {
            // První segment nemá na co navázat – uzel to odmítne, tak to sem
            // ani nepustíme.
            val inherit = seg.inheritPrevious && i > 0
            o.put("use_previous_last_frame", inherit)
            o.put("first_frame_only", !inherit)
            if (!inherit) {
                val name = imageNames.getOrNull(scene.withImage.indexOf(seg))
                if (name != null) {
                    o.put(
                        "images", JSONArray().put(
                            JSONObject()
                                .put("path", name)
                                .put("name", seg.image?.name ?: name)
                        )
                    )
                }
            }
        }
        segments.put(o)
    }

    return JSONObject()
        .put("version", 1)
        .put("segments", segments)
        .toString()
}

/** Co ose chybí, než se dá odeslat. Hláška pro uživatele, nebo null. */
fun timelineProblem(scene: TimelineScene): String? {
    if (scene.segments.all { it.prompt.isBlank() }) {
        return "Napiš, co se má v prvním segmentu dít."
    }
    scene.segments.firstOrNull { it.prompt.isBlank() }?.let {
        return "Segment bez popisu model neumí natočit – doplň ho, nebo ho odeber."
    }
    scene.segments.forEachIndexed { i, seg ->
        if (seg.seconds > TimelineScene.MAX_SEGMENT_SECONDS) {
            return "Segment ${i + 1} má ${seg.seconds.toInt()} s, model zvládne nejvýš " +
                "${TimelineScene.MAX_SEGMENT_SECONDS.toInt()} s na jeden. Rozděl ho."
        }
        if (seg.mode == SegmentMode.IMAGE) {
            if (i == 0 && seg.inheritPrevious) {
                return "První segment nemá na co navázat – vyber mu snímek."
            }
            if (!seg.inheritPrevious && seg.image == null) {
                return "Segment ${i + 1} čeká na snímek, ze kterého má vyjít."
            }
        }
    }
    return null
}
