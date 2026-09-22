package cz.promptlab.h3video

import cz.promptlab.h3video.data.VideoItem
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Oprava uložených délek v galerii.
 *
 * Do 3.87 se do historie zapisovala délka z hlavního posuvníku aplikace,
 * o kterém karty s vlastní délkou (Long MiniMax, Dance, LTX 2.5) nevědí —
 * u pětisekundového videa proto v galerii svítilo „12,0 s". Oprava měří
 * soubory; testuje se tu jen to pravidlo, podle kterého se rozhoduje,
 * protože samo měření je věc Androidu.
 */
class HistoryDelkaOpravaTest {

    private fun polozka(id: String, sekund: Float) = VideoItem(
        id = id, fileName = "$id.mp4", prompt = "", createdAt = 0L, seconds = sekund,
        resolution = "", seed = 0L, twoImages = false,
    )

    /** Kopie pravidla z `HistoryStore.opravDelky`. */
    private fun opravit(item: VideoItem, zmerena: Float?): VideoItem {
        val skutecna = zmerena ?: return item
        return if (kotlin.math.abs(item.seconds - skutecna) < 0.15f) item
        else item.copy(seconds = skutecna)
    }

    @Test fun `zjevne spatna delka se prepise`() {
        assertEquals(5.17f, opravit(polozka("a", 12f), 5.17f).seconds, 0.001f)
        assertEquals(9.42f, opravit(polozka("b", 5f), 9.42f).seconds, 0.001f)
    }

    /** Desetina sekundy je zaokrouhlení, ne chyba — zapisovat se kvůli ní nemá. */
    @Test fun `drobny rozdil se nechava byt`() {
        assertEquals(5.0f, opravit(polozka("c", 5.0f), 5.08f).seconds, 0.001f)
        assertEquals(5.0f, opravit(polozka("c", 5.0f), 4.93f).seconds, 0.001f)
    }

    /** Nezměřitelný soubor nesmí uloženou hodnotu vynulovat. */
    @Test fun `bez zmereni zustava puvodni`() {
        assertEquals(12f, opravit(polozka("d", 12f), null).seconds, 0.001f)
    }
}
