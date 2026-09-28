package cz.promptlab.h3video.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import cz.promptlab.h3video.R
import cz.promptlab.h3video.data.t

/**
 * Vzhled appky, volitelný v Nastavení (od 4.73).
 *
 * Šest směrů vychází z vizuálního jazyka PromptLab (promptlab.cz): nadpisové
 * písmo Unbounded, text Manrope, technické popisky JetBrains Mono, barvy
 * značky (#6C5CE7, #A29BFE, #00CEC9). Rešerše 28. 9. 2026: v tmavém režimu
 * tmavě šedá místo čisté černé, tlumenější akcenty, kontrast textu ≥ 4,5 : 1;
 * na zelené a limetkové proto tmavý text ([naAkcentu]), ne bílý.
 * [PUVODNI] je vzhled do 4.72 beze změny.
 */
enum class Motiv(
    private val nazevCs: String,
    val bg: Color,
    val surface1: Color,
    val surface2: Color,
    val outline: Color,
    /** Hlavní akcent — výběr, rámečky, tónování (dřív pevná fialová). */
    val primary: Color,
    /** Zvýrazněný text a odkazy (dřív pevná azurová). */
    val accent: Color,
    val textHi: Color,
    val textMid: Color,
    val textLow: Color,
    /** Přechod hlavního tlačítka a vybrané záložky. */
    val cta: List<Color>,
    /** Barva textu na [cta]. */
    val naAkcentu: Color,
    /** Záře v pozadí: (x, y v poměru plochy, barva). Prázdné = bez záře. */
    val zare: List<Triple<Float, Float, Color>> = emptyList(),
    /** Nadpisy sekcí jako technický popisek (mono, verzálky) — jako web PromptLab. */
    val popiskyMono: Boolean = false,
    val popisekBarva: Color = textHi,
    /** Písma PromptLab (Unbounded + Manrope); [PUVODNI] má systémové. */
    val pismaPromptLab: Boolean = true,
) {
    SALVEJ(
        "Šalvěj",
        bg = Color(0xFF121614), surface1 = Color(0xFF1A201D), surface2 = Color(0xFF151A17), outline = Color(0xFF2B332F),
        primary = Color(0xFF9BD1B0), accent = Color(0xFFB7E3C8),
        textHi = Color(0xFFECF1EE), textMid = Color(0xFFB1BDB6), textLow = Color(0xFF8A968F),
        cta = listOf(Color(0xFF9BD1B0), Color(0xFF9BD1B0)), naAkcentu = Color(0xFF0F1D15),
        popisekBarva = Color(0xFFECF1EE),
    ),
    AURORA(
        "Aurora",
        bg = Color(0xFF070814), surface1 = Color(0xFF13142A), surface2 = Color(0xFF0E0F20), outline = Color(0xFF2A2B45),
        primary = Color(0xFFFD79A8), accent = Color(0xFF7EF4F0),
        textHi = Color(0xFFF4F2FF), textMid = Color(0xFFC4C0DD), textLow = Color(0xFF9A97B8),
        cta = listOf(Color(0xFF6C5CE7), Color(0xFFD13F77), Color(0xFF0B8783)), naAkcentu = Color.White,
        zare = listOf(
            Triple(0.5f, -0.12f, Color(0x8C6C5CE7)),
            Triple(1.1f, 0.4f, Color(0x38FD79A8)),
            Triple(-0.2f, 0.9f, Color(0x4700CEC9)),
        ),
        popiskyMono = true, popisekBarva = Color(0xFF7EF4F0),
    ),
    NEON(
        "Neonový kód",
        bg = Color(0xFF09090B), surface1 = Color(0xFF111113), surface2 = Color(0xFF0D0D0F), outline = Color(0xFF232326),
        primary = Color(0xFFA3E635), accent = Color(0xFFA3E635),
        textHi = Color(0xFFF4F5F2), textMid = Color(0xFFB9BCB3), textLow = Color(0xFF8C9086),
        cta = listOf(Color(0xFFA3E635), Color(0xFFA3E635)), naAkcentu = Color(0xFF0B1204),
        zare = listOf(Triple(1.0f, 0.0f, Color(0x1FA3E635))),
        popiskyMono = true, popisekBarva = Color(0xFFA3E635),
    ),
    SMARAGD(
        "Smaragdová",
        bg = Color(0xFF0A110E), surface1 = Color(0xFF111C17), surface2 = Color(0xFF0C1511), outline = Color(0xFF1E3329),
        primary = Color(0xFF10B981), accent = Color(0xFF6EE7B7),
        textHi = Color(0xFFEEF6F1), textMid = Color(0xFFB3C7BC), textLow = Color(0xFF87A194),
        cta = listOf(Color(0xFF34D399), Color(0xFF2DD4BF)), naAkcentu = Color(0xFF04130D),
        zare = listOf(Triple(0.85f, -0.1f, Color(0x4210B981)), Triple(-0.1f, 1.05f, Color(0x2400CEC9))),
        popiskyMono = true, popisekBarva = Color(0xFF6EE7B7),
    ),
    KLIDNA(
        "Klidná laboratoř",
        bg = Color(0xFF121218), surface1 = Color(0xFF1A1A22), surface2 = Color(0xFF15151C), outline = Color(0xFF2C2B38),
        primary = Color(0xFFA29BFE), accent = Color(0xFFC9C2FF),
        textHi = Color(0xFFEDECF3), textMid = Color(0xFFB3B1C4), textLow = Color(0xFF8F8DA3),
        cta = listOf(Color(0xFFA29BFE), Color(0xFFA29BFE)), naAkcentu = Color(0xFF16132E),
        popisekBarva = Color(0xFFEDECF3),
    ),
    NEBULA(
        "Nebula",
        bg = Color(0xFF0B0B14), surface1 = Color(0xFF15152A), surface2 = Color(0xFF10101F), outline = Color(0xFF2B2A4A),
        primary = Color(0xFF6C5CE7), accent = Color(0xFF00CEC9),
        textHi = Color(0xFFF2F1F8), textMid = Color(0xFFB8B6CB), textLow = Color(0xFF8E8CA6),
        cta = listOf(Color(0xFF6C5CE7), Color(0xFF5B6CF0), Color(0xFF0B8783)), naAkcentu = Color.White,
        zare = listOf(Triple(0.85f, -0.1f, Color(0x476C5CE7)), Triple(-0.1f, 1.05f, Color(0x1F00CEC9))),
        popiskyMono = true, popisekBarva = Color(0xFFA29BFE),
    ),
    PUVODNI(
        "Původní",
        bg = Color(0xFF07070C), surface1 = Color(0xFF12121B), surface2 = Color(0xFF1B1B27), outline = Color(0xFF2A2A3A),
        primary = Color(0xFF7C5CFF), accent = Color(0xFF22D3EE),
        textHi = Color(0xFFECECF5), textMid = Color(0xFF9E9EB4), textLow = Color(0xFF8B8BA3),
        cta = listOf(Color(0xFF7C5CFF), Color(0xFF22D3EE)), naAkcentu = Color.White,
        pismaPromptLab = false,
    );

    val nazev: String get() = t(nazevCs)

    companion object {
        /** Výchozí pro nové instalace — první z nabídky (přání uživatele 28. 9. 2026). */
        val VYCHOZI = SALVEJ

        fun zUlozeneho(jmeno: String?): Motiv = entries.firstOrNull { it.name == jmeno } ?: VYCHOZI
    }
}

/** Právě zvolený vzhled. Čte se v kompozici, takže změna appku hned překreslí. */
object Vzhled {
    var motiv by mutableStateOf(Motiv.VYCHOZI)
}

// Barvy, jak je čte zbytek appky — dřív pevné hodnoty, teď podle vzhledu.
val Ink: Color get() = Vzhled.motiv.bg
val Surface1: Color get() = Vzhled.motiv.surface1
val Surface2: Color get() = Vzhled.motiv.surface2
val Outline1: Color get() = Vzhled.motiv.outline
val Violet: Color get() = Vzhled.motiv.primary
val Cyan: Color get() = Vzhled.motiv.accent
val TextHi: Color get() = Vzhled.motiv.textHi
val TextMid: Color get() = Vzhled.motiv.textMid
val TextLow: Color get() = Vzhled.motiv.textLow
val NaAkcentu: Color get() = Vzhled.motiv.naAkcentu
val Rose = Color(0xFFFF6B9D)
val Amber = Color(0xFFFFB86B)
val Danger = Color(0xFFFF6B6B)
val Ok = Color(0xFF4ADE80)

val AccentBrush: Brush get() = Brush.linearGradient(Vzhled.motiv.cta)
val AccentSweep: Brush get() = Brush.sweepGradient(Vzhled.motiv.cta + Rose + Vzhled.motiv.cta.first())

// ---------------------------------------------------------------- písma PromptLab

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun promenne(res: Int, vaha: Int) = Font(
    res, FontWeight(vaha),
    variationSettings = FontVariation.Settings(FontVariation.weight(vaha)),
)

val Unbounded = FontFamily(
    promenne(R.font.unbounded_var, 500), promenne(R.font.unbounded_var, 600), promenne(R.font.unbounded_var, 700),
)
val Manrope = FontFamily(
    promenne(R.font.manrope_var, 400), promenne(R.font.manrope_var, 500),
    promenne(R.font.manrope_var, 600), promenne(R.font.manrope_var, 700),
)
val JetBrainsMono = FontFamily(promenne(R.font.jetbrainsmono_var, 500))

private fun typografie(m: Motiv): Typography {
    val text = if (m.pismaPromptLab) Manrope else FontFamily.Default
    val nadpis = if (m.pismaPromptLab) Unbounded else FontFamily.Default
    return Typography(
        displaySmall = TextStyle(fontFamily = nadpis, fontSize = 34.sp, fontWeight = FontWeight.Light, letterSpacing = (-0.5).sp),
        headlineSmall = TextStyle(fontFamily = nadpis, fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
        titleLarge = TextStyle(fontFamily = nadpis, fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
        titleMedium = TextStyle(fontFamily = text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.1.sp),
        bodyLarge = TextStyle(fontFamily = text, fontSize = 16.sp, fontWeight = FontWeight.Normal, lineHeight = 22.sp),
        bodyMedium = TextStyle(fontFamily = text, fontSize = 14.sp, fontWeight = FontWeight.Normal, lineHeight = 20.sp),
        bodySmall = TextStyle(fontFamily = text, fontSize = 12.5.sp, fontWeight = FontWeight.Normal, lineHeight = 17.sp),
        labelLarge = TextStyle(fontFamily = text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.2.sp),
        labelMedium = TextStyle(fontFamily = text, fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp),
        labelSmall = TextStyle(fontFamily = text, fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp),
    )
}

/** Styl nadpisu sekce: u vzhledů PromptLab technický popisek (mono, verzálky). */
val nadpisSekceStyl: TextStyle
    get() = if (Vzhled.motiv.popiskyMono)
        TextStyle(fontFamily = JetBrainsMono, fontSize = 11.5.sp, fontWeight = FontWeight.Medium, letterSpacing = 2.sp)
    else TextStyle(
        fontFamily = if (Vzhled.motiv.pismaPromptLab) Manrope else FontFamily.Default,
        fontSize = 16.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.1.sp,
    )

/** Text nadpisu sekce — u mono popisků verzálkami, jako na webu PromptLab. */
fun nadpisSekce(text: String): String = if (Vzhled.motiv.popiskyMono) text.uppercase() else text

/** Pozadí hlavní obrazovky: barva vzhledu a jeho záře (mlhovina). */
fun Modifier.pozadiAplikace(): Modifier = this
    .background(Vzhled.motiv.bg)
    .drawBehind {
        Vzhled.motiv.zare.forEach { (x, y, barva) ->
            drawRect(
                Brush.radialGradient(
                    listOf(barva, Color.Transparent),
                    center = Offset(size.width * x, size.height * y),
                    radius = size.width * 1.1f,
                )
            )
        }
    }

@Composable
fun H3Theme(content: @Composable () -> Unit) {
    val m = Vzhled.motiv
    val schema = darkColorScheme(
        primary = m.primary,
        onPrimary = m.naAkcentu,
        primaryContainer = m.surface2,
        onPrimaryContainer = m.textHi,
        secondary = m.accent,
        onSecondary = m.bg,
        background = m.bg,
        onBackground = m.textHi,
        surface = m.surface1,
        onSurface = m.textHi,
        surfaceVariant = m.surface2,
        onSurfaceVariant = m.textMid,
        outline = m.outline,
        error = Danger,
    )
    MaterialTheme(colorScheme = schema, typography = typografie(m)) {
        // Bez Surface by texty bez explicitní barvy dědily černou a byly nečitelné.
        Surface(modifier = Modifier.fillMaxSize(), color = m.bg, contentColor = m.textHi) {
            content()
        }
    }
}
