package cz.promptlab.h3video.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.foundation.layout.ColumnScope
import cz.promptlab.h3video.ui.theme.TextHi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.runtime.saveable.rememberSaveable
import cz.promptlab.h3video.ui.theme.Cyan
import androidx.compose.foundation.layout.Spacer
import cz.promptlab.h3video.data.t
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cz.promptlab.h3video.ui.theme.AccentBrush
import cz.promptlab.h3video.ui.theme.Outline1
import cz.promptlab.h3video.ui.theme.Surface1
import cz.promptlab.h3video.ui.theme.TextLow
import cz.promptlab.h3video.ui.theme.TextMid
import cz.promptlab.h3video.ui.theme.Violet

/** Karta jedné sekce zadání. */
/**
 * Obdélník právě zaostřeného textového pole (souřadnice okna).
 *
 * Hlídač v MainActivity podle něj při KAŽDÉM dotyku pozná, jestli prst mířil
 * do pole, nebo mimo — a mimo pole klávesnici schová. Ošetření po jednom
 * prvku (tlačítka, sekce, pilulky) nefungovalo: klepnutí si spotřebují a
 * klávesnice zůstávala viset. Není to Compose stav, čte se jen při dotyku.
 */
object ZaostrenePole {
    @Volatile
    var bounds: androidx.compose.ui.geometry.Rect? = null
}

@Composable
fun SectionCard(
    title: String,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Surface1)
            .border(1.dp, Outline1, RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextLow,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
            trailing?.invoke()
        }
        // Sloupec, ne Box: obsah sekce je svislý seznam. V Boxu se prvky
        // kreslily PŘES SEBE — sekce, která nemá vlastní Column, měla text
        // přes tlačítko (viditelné u „Ukládat vše do telefonu", 2. 9. 2026).
        Column(Modifier.padding(top = 14.dp)) { content() }
    }
}


/**
 * Sbalitelná skupina karet. Hlavička ukazuje souhrn aktuálních hodnot, takže
 * uživatel vidí, co je nastavené, aniž by rozbaloval — a hlavní obrazovka
 * zůstane krátká: vstupy, zadání, Generovat.
 */
@Composable
fun SkladaciSekce(
    title: String,
    souhrn: String,
    klic: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    var rozbaleno by androidx.compose.runtime.saveable.rememberSaveable(klic) {
        androidx.compose.runtime.mutableStateOf(false)
    }
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Surface1)
                .border(1.dp, Outline1, RoundedCornerShape(14.dp))
                .clickable { rozbaleno = !rozbaleno }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyMedium, color = TextHi)
                Text(
                    souhrn,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextLow,
                    modifier = Modifier.padding(top = 1.dp)
                )
            }
            Icon(
                if (rozbaleno) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                if (rozbaleno) "Sbalit" else "Rozbalit",
                Modifier.size(22.dp),
                TextMid
            )
        }
        androidx.compose.animation.AnimatedVisibility(
            visible = rozbaleno,
            enter = androidx.compose.animation.fadeIn() +
                androidx.compose.animation.expandVertically(),
            exit = androidx.compose.animation.fadeOut() +
                androidx.compose.animation.shrinkVertically(),
        ) {
            Column(
                Modifier.padding(top = 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                content = content
            )
        }
    }
}

/**
 * Řada přepínacích „pilulek" s náznakem, že pokračuje za okrajem.
 *
 * Vodorovné rolování samo o sobě nikdo nepozná: na kartě Obrázek kvůli tomu
 * tři z pěti modelů vypadaly, že v appce vůbec nejsou. Proto se u okraje,
 * kde je ještě něco schované, vykreslí stín a šipka — a jakmile se doroluje
 * na konec, zmizí. Platí to pro VŠECHNY řady v appce naráz.
 *
 * @param pozadi barva, do které stín přechází. Výchozí je pozadí karty;
 *   na jiném podkladu se předá jeho barva, aby stín nebyl vidět jako obdélník.
 */
@Composable
fun <T> PillRow(
    items: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    pozadi: Color = Surface1,
) {
    val stav = rememberScrollState()
    Box(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(stav),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items.forEach { item ->
                Pill(label(item), item == selected) { onSelect(item) }
            }
        }
        OkrajSNaznakem(stav.canScrollBackward, pozadi, doleva = true, Modifier.align(Alignment.CenterStart))
        OkrajSNaznakem(stav.canScrollForward, pozadi, doleva = false, Modifier.align(Alignment.CenterEnd))
    }
}

/** Stín a šipka u okraje řady, když je za ním ještě něco schované. */
@Composable
private fun OkrajSNaznakem(
    videt: Boolean,
    pozadi: Color,
    doleva: Boolean,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(visible = videt, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        Row(
            Modifier
                .height(38.dp)
                .width(34.dp)
                .background(
                    Brush.horizontalGradient(
                        if (doleva) listOf(pozadi, pozadi.copy(alpha = 0f))
                        else listOf(pozadi.copy(alpha = 0f), pozadi)
                    )
                ),
            horizontalArrangement = if (doleva) Arrangement.Start else Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (doleva) Icons.Default.ChevronLeft else Icons.Default.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = TextMid,
            )
        }
    }
}

/**
 * Pruh s frontou běhů — vidět je na hlavní obrazovce i během generování.
 *
 * Sbalený ukazuje jen počet, klepnutím se rozbalí celý seznam i s křížky na
 * odebrání. Dřív byla fronta až úplně dole pod tlačítkem a během generování ji
 * překryla obrazovka průběhu — tedy zrovna ve chvíli, kdy člověka zajímá.
 *
 * @param prvni popis běhu, který zrovna běží. Když je, ukáže se nad frontou,
 *   aby bylo vidět celé pořadí, ne jen to, co čeká.
 */
@Composable
fun FrontaPruh(
    fronta: List<cz.promptlab.h3video.engine.QueuedRun>,
    onRemove: (Long) -> Unit,
    modifier: Modifier = Modifier,
    prvni: String? = null,
) {
    if (fronta.isEmpty() && prvni == null) return
    var rozbaleno by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Surface1)
            .border(1.dp, Outline1, RoundedCornerShape(14.dp))
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { rozbaleno = !rozbaleno }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.PlaylistPlay, null, Modifier.size(20.dp), Cyan)
            Spacer(Modifier.width(10.dp))
            Text(
                if (fronta.isEmpty()) t("Právě běží")
                else t("Ve frontě čeká %d").format(fronta.size),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Icon(
                if (rozbaleno) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                if (rozbaleno) t("Sbalit") else t("Zobrazit frontu"),
                Modifier.size(22.dp), TextMid,
            )
        }
        AnimatedVisibility(rozbaleno) {
            Column(Modifier.padding(start = 12.dp, end = 6.dp, bottom = 10.dp)) {
                prvni?.let {
                    Text(
                        t("Běží: %s").format(it),
                        style = MaterialTheme.typography.bodySmall, color = Cyan,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.padding(bottom = 4.dp, end = 6.dp),
                    )
                }
                fronta.forEachIndexed { i, run ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${i + 1}. ${run.title}" +
                                (if (run.prompt.isNotBlank()) " · ${run.prompt.take(38)}" else ""),
                            style = MaterialTheme.typography.bodySmall, color = TextMid,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            Icons.Default.Close, t("Odebrat z fronty"),
                            Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(50))
                                .clickable { onRemove(run.id) }
                                .padding(6.dp),
                            TextLow
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun Pill(text: String, selected: Boolean, onClick: () -> Unit) {
    val border by animateColorAsState(if (selected) Violet else Outline1, label = "pill")
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .then(
                if (selected) Modifier.background(
                    Brush.linearGradient(listOf(Violet.copy(alpha = .30f), Color.Transparent))
                ) else Modifier
            )
            .border(1.dp, border, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.onBackground else TextMid
        )
    }
}

/** Jednoduchý rozbalovací výběr. */
@Composable
fun <T> Dropdown(
    label: String,
    items: List<T>,
    selected: T,
    render: (T) -> String,
    onSelect: (T) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = TextLow)
        Box(
            Modifier
                .padding(top = 6.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .border(1.dp, Outline1, RoundedCornerShape(14.dp))
                .clickable { open = true }
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    render(selected),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                Icon(Icons.Default.ExpandMore, null, tint = TextMid, modifier = Modifier.size(20.dp))
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                items.forEach { item ->
                    DropdownMenuItem(
                        text = { Text(render(item)) },
                        onClick = { onSelect(item); open = false }
                    )
                }
            }
        }
    }
}

/** Tlačítko s přechodovou výplní. */
@Composable
fun GradientButton(
    text: String,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(if (enabled) AccentBrush else Brush.linearGradient(listOf(Outline1, Outline1)))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            icon?.invoke()
            Text(
                text,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = if (enabled) Color.White else TextLow
            )
        }
    }
}

@Composable
fun OutlineButton(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = TextMid,
    icon: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .border(BorderStroke(1.dp, Outline1), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 13.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            icon?.invoke()
            Text(text, style = MaterialTheme.typography.labelLarge, color = color)
        }
    }
}
