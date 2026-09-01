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
        Box(Modifier.padding(top = 14.dp)) { content() }
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

/** Vodorovná řada přepínacích „pilulek". */
@Composable
fun <T> PillRow(
    items: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items.forEach { item ->
            Pill(label(item), item == selected) { onSelect(item) }
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
