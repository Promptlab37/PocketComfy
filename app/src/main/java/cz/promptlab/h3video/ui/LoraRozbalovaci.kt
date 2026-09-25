package cz.promptlab.h3video.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cz.promptlab.h3video.data.t
import cz.promptlab.h3video.ui.theme.Amber
import cz.promptlab.h3video.ui.theme.Cyan
import cz.promptlab.h3video.ui.theme.Outline1
import cz.promptlab.h3video.ui.theme.Surface2
import cz.promptlab.h3video.ui.theme.TextHi
import cz.promptlab.h3video.ui.theme.TextLow
import cz.promptlab.h3video.ui.theme.TextMid

/**
 * Jedna položka nabídky LoRA.
 *
 * @param hodnota to, co se předá dál (obvykle jméno souboru)
 * @param poznamka šedý řádek pod názvem; [varovani] ho obarví
 * @param oddelit nad položkou se nakreslí čára (začátek další skupiny)
 */
data class LoraVolba(
    val hodnota: String,
    val nazev: String = hodnota.removeSuffix(".safetensors"),
    val poznamka: String = "",
    val varovani: Boolean = false,
    val povoleno: Boolean = true,
    val oddelit: Boolean = false,
)

/**
 * Standardní rozbalovací nabídka pro výběr LoRA — jedna a táž v celé appce
 * (přání uživatele 25. 9. 2026; do té doby měla každá karta jiný výběr:
 * dialog se seznamem, řádek dlaždic, rozbalovaný text).
 *
 * @param prazdna text volby „bez LoRA" (hodnota ""); null = volba chybí
 * @param otevreno volá se při rozbalení — karta si tím může načíst seznam
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoraRozbalovaci(
    popisek: String,
    volby: List<LoraVolba>,
    vybrana: String,
    onVybrat: (String) -> Unit,
    modifier: Modifier = Modifier,
    prazdna: String? = t("Bez LoRA"),
    zastupny: String = t("Vyber LoRA"),
    otevreno: () -> Unit = {},
) {
    var rozbaleno by remember { mutableStateOf(false) }
    val text = when {
        vybrana.isNotEmpty() -> volby.firstOrNull { it.hodnota == vybrana }?.nazev
            ?: vybrana.removeSuffix(".safetensors")
        prazdna != null -> prazdna
        else -> ""
    }
    ExposedDropdownMenuBox(
        expanded = rozbaleno,
        onExpandedChange = {
            rozbaleno = it
            if (it) otevreno()
        },
        modifier = modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(popisek) },
            placeholder = { Text(zastupny, color = TextLow) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = rozbaleno) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = TextHi,
                unfocusedTextColor = TextHi,
                focusedBorderColor = Cyan,
                unfocusedBorderColor = Outline1,
                focusedLabelColor = Cyan,
                unfocusedLabelColor = TextMid,
                focusedTrailingIconColor = Cyan,
                unfocusedTrailingIconColor = TextMid,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(
            expanded = rozbaleno,
            onDismissRequest = { rozbaleno = false },
            containerColor = Surface2,
            modifier = Modifier.heightIn(max = 380.dp),
        ) {
            if (prazdna != null) DropdownMenuItem(
                text = { Text(prazdna, color = if (vybrana.isEmpty()) Cyan else TextMid) },
                onClick = { onVybrat(""); rozbaleno = false },
            )
            if (volby.isEmpty()) DropdownMenuItem(
                text = {
                    Text(
                        t("Na serveru není žádná LoRA pro tenhle model"),
                        color = TextLow, style = MaterialTheme.typography.bodySmall,
                    )
                },
                onClick = {},
                enabled = false,
            )
            volby.forEach { v ->
                if (v.oddelit) HorizontalDivider(color = Outline1)
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(
                                v.nazev,
                                color = when {
                                    !v.povoleno -> TextLow
                                    v.hodnota == vybrana -> Cyan
                                    else -> TextHi
                                },
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            if (v.poznamka.isNotBlank()) Text(
                                v.poznamka,
                                color = if (v.varovani) Amber else TextLow,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    },
                    onClick = { onVybrat(v.hodnota); rozbaleno = false },
                    enabled = v.povoleno,
                )
            }
        }
    }
}
