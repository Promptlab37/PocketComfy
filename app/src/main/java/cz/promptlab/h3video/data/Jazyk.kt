package cz.promptlab.h3video.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/**
 * Jazyk rozhraní.
 *
 * Appka vznikla česky a české texty jsou přímo v kódu. Místo přepisu všech
 * stovek řetězců na `R.string` (a plumbingu Contextu do enginu i ViewModelu)
 * se překládá **podle českého originálu jako klíče**: [t] najde větu ve
 * [Slovnik] a vrátí anglickou verzi, nebo — když překlad chybí — původní
 * češtinu. Nepřeložený kus rozhraní tedy nikdy nezůstane prázdný.
 *
 * [volba] je Compose stav, takže přepnutí jazyka překreslí obrazovky samo,
 * a `t()` jde volat i mimo composable (engine, notifikace, ViewModel).
 */
object Jazyk {

    enum class Volba(val kod: String) {
        /** Podle jazyka telefonu: čeština → česky, cokoli jiného → anglicky. */
        SYSTEM("system"),
        CS("cs"),
        EN("en"),
    }

    private var systemAnglicky = false

    var volba by mutableStateOf(Volba.SYSTEM)
        private set

    /** Má se rozhraní kreslit anglicky? */
    val anglicky: Boolean
        get() = when (volba) {
            Volba.CS -> false
            Volba.EN -> true
            Volba.SYSTEM -> systemAnglicky
        }

    /** Zavolat jednou při startu — z uložené volby a jazyka telefonu. */
    fun init(ulozena: String?) {
        systemAnglicky = !Locale.getDefault().language.equals("cs", ignoreCase = true)
        volba = Volba.entries.firstOrNull { it.kod == ulozena } ?: Volba.SYSTEM
    }

    fun nastav(v: Volba) {
        volba = v
    }

    fun t(cesky: String): String =
        if (anglicky) Slovnik.EN[cesky] ?: cesky else cesky
}

/** Zkratka, ať se v kódu píše `t("Hotovo")`. */
fun t(cesky: String): String = Jazyk.t(cesky)
