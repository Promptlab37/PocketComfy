package cz.promptlab.h3video.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cz.promptlab.h3video.MainViewModel
import cz.promptlab.h3video.data.*
import cz.promptlab.h3video.ui.theme.*

/** Výběr zůstává přístupný i při prázdném seznamu nebo odpojeném serveru. */
@Composable
fun EditLoraSection(vm: MainViewModel, scene: ImageEditScene) {
    val catalog by vm.editLoras.collectAsStateWithLifecycle()
    val server by vm.server.collectAsStateWithLifecycle()
    LaunchedEffect(server) { vm.refreshEditLoras() }
    ModelLoraSection(scene.motor.nazev, scene.selectedLora, catalog,
        compatibility = { it.compatibility(scene.motor) },
        refresh = { vm.refreshEditLoras(force = true) },
        select = { name, confirmed -> vm.setEditUserLora(name, confirmed) },
        strength = { vm.setEditUserLoraStrength(it) })
}

@Composable
fun ImageLoraSection(vm: MainViewModel, params: GenParams) {
    val catalog by vm.editLoras.collectAsStateWithLifecycle()
    val server by vm.server.collectAsStateWithLifecycle()
    LaunchedEffect(server) { vm.refreshEditLoras() }
    val model = cz.promptlab.h3video.comfy.T2iModel.zId(params.zimageModel)
    val selected = ImageLoras.selected(params)
    for (slot in 0..1) key(model.id, slot) {
        ModelLoraSection(model.stitek, selected.getOrElse(slot) { EditLora() }, catalog,
            compatibility = { ImageLoras.compatibility(model, it) },
            refresh = { vm.refreshEditLoras(force = true) },
            select = { name, confirmed -> vm.setImageLora(slot, name, confirmed) },
            strength = { vm.setImageLoraStrength(slot, it) },
            title = if (slot == 0) t("LoRA pro %s").format(model.stitek) else t("Druhá LoRA (nepovinná)"))
    }
}

@Composable
private fun ModelLoraSection(
    modelName: String, selected: EditLora, catalog: MainViewModel.EditLoraCatalog,
    compatibility: (EditLoraFile) -> LoraCompatibility, refresh: () -> Unit,
    select: (String, Boolean) -> Unit, strength: (Float) -> Unit,
    title: String = t("LoRA pro %s").format(modelName),
) {
    var picker by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var unknown by rememberSaveable(modelName) { mutableStateOf(false) }
    var confirm by remember(modelName) { mutableStateOf<String?>(null) }
    val matching = catalog.files.filter { compatibility(it) == LoraCompatibility.MATCH }
    val unclassified = catalog.files.filter { compatibility(it) == LoraCompatibility.UNKNOWN }

    SectionCard(title = title,
        subtitle = t("Volba a síla se pamatují pro každý model zvlášť"),
        trailing = {
            IconButton(onClick = refresh, enabled = !catalog.loading) {
                Icon(Icons.Default.Refresh, t("Obnovit seznam LoRA"), tint = Cyan)
            }
        },
    ) {
        OutlineButton(selected.name.ifBlank { t("Vybrat LoRA (nepovinné)") },
            modifier = Modifier.fillMaxWidth(), color = Cyan,
            icon = { Icon(Icons.Default.ExpandMore, null, Modifier.size(18.dp)) },
            onClick = { picker = true })
        if (selected.name.isNotBlank()) {
            LabeledSlider(t("Síla LoRA"), "%.2f".format(selected.strength), selected.strength,
                0f..2f, onChange = strength)
            TextButton(onClick = { select("", false) }) { Text(t("Bez doplňkové LoRA"), color = TextMid) }
        }
        if (catalog.loading) {
            LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 8.dp), color = Cyan)
            Text(t("Načítám LoRA a údaje o modelech…"), color = TextMid, style = MaterialTheme.typography.bodySmall)
        } else if (catalog.error) {
            Text(t("Seznam LoRA se nepodařilo načíst. Zkontrolujte server a obnovte seznam."),
                color = Amber, style = MaterialTheme.typography.bodySmall)
        } else if (matching.isEmpty()) {
            Text(t("Žádná LoRA s rozpoznaným základním modelem. Ve výběru lze přiřadit neoznačený soubor."),
                color = TextMid, style = MaterialTheme.typography.bodySmall)
        }
        if (!catalog.loading && !catalog.error && selected.name.isNotBlank() && catalog.files.none { it.name == selected.name }) {
            Text(t("Vybraná LoRA na tomto serveru chybí. Obnovte seznam nebo vyberte jinou."),
                color = Amber, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(6.dp))
        Text(t("LoRA se přidá k vybranému modelu. Síla 0 ji vypne. Nabídka zahrnuje i necenzurované LoRA pro tento model."),
            color = TextLow, style = MaterialTheme.typography.bodySmall)
    }

    if (picker) Dialog(onDismissRequest = { picker = false }) {
        Surface(shape = RoundedCornerShape(24.dp), color = Surface1) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                DarkTextField(query, { query = it }, placeholder = t("Hledat LoRA…"), minHeight = 48.dp, singleLine = true, onClear = { query = "" })
                PillRow(listOf(false, true), unknown,
                    label = { if (it) t("Neurčené") + " (${unclassified.size})" else t("Pro model") + " (${matching.size})" },
                    onSelect = { unknown = it })
                Text(if (unknown) t("U těchto souborů chybí označení modelu. Vyberte jen LoRA určenou pro aktuální model.")
                    else t("Výběr podle základního modelu v metadatech nebo názvu souboru."),
                    style = MaterialTheme.typography.bodySmall, color = TextMid)
                val files = (if (unknown) unclassified else matching).filter { it.name.contains(query.trim(), true) }
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
                    item {
                        ListItem(headlineContent = { Text(t("Bez doplňkové LoRA")) },
                            modifier = Modifier.clickable { select("", false); picker = false })
                    }
                    items(files, key = { it.name }) { file ->
                        ListItem(headlineContent = { Text(file.name, style = MaterialTheme.typography.bodyMedium) },
                            trailingContent = { if (selected.name == file.name) Icon(Icons.Default.Check, null, tint = Cyan) },
                            modifier = Modifier.clickable {
                                if (unknown) confirm = file.name
                                else { select(file.name, false); picker = false }
                            })
                    }
                    if (files.isEmpty()) item {
                        Text(if (catalog.loading) t("Načítám LoRA a údaje o modelech…")
                            else t("Seznam je prázdný. LoRA musí být uložená na serveru ve složce models/loras."),
                            Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall, color = TextMid)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = refresh, enabled = !catalog.loading) { Text(t("Obnovit")) }
                    TextButton(onClick = { picker = false }) { Text(t("Zavřít")) }
                }
            }
        }
    }
    confirm?.let { name ->
        AlertDialog(onDismissRequest = { confirm = null }, title = { Text(t("Přiřadit LoRA k modelu?")) },
            text = { Text(t("U souboru %s nelze ověřit základní model. Použijte ho jen pokud je určený pro %s.")
                .format(name, modelName)) },
            confirmButton = { TextButton(onClick = {
                select(name, true); confirm = null; picker = false
            }) { Text(t("Použít pro tento model")) } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text(t("Zrušit")) } })
    }
}
