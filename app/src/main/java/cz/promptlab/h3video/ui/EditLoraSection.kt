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
            // Turbo i Base jsou jedna rodina — soubory se načtou na obojím.
            // Nahoru proto patří ty pro zvolenou větev, zbytek se jen popíše.
            serad = { ImageLoras.seradPodleVetve(model, it) },
            poznamka = { ImageLoras.poznamkaVetve(model, it) },
            refresh = { vm.refreshEditLoras(force = true) },
            select = { name, confirmed -> vm.setImageLora(slot, name, confirmed) },
            strength = { vm.setImageLoraStrength(slot, it) },
            title = if (slot == 0) t("LoRA pro %s").format(model.stitek) else t("Druhá LoRA (nepovinná)"))
    }
}

/**
 * LoRA na kartě **LTX 2.5**.
 *
 * Rodina LTX sedí celá — soubory pro LTX-2, 2.3 i 2.5 mají stejný tvar
 * (48 bloků, šířka 4096), ověřeno proti souborům na serveru. Nahoru se proto
 * jen řadí ty s bližší verzí, nic se nezakazuje.
 */
@Composable
fun LtxLoraSection(vm: MainViewModel, scene: LtxScene) {
    val catalog by vm.editLoras.collectAsStateWithLifecycle()
    val server by vm.server.collectAsStateWithLifecycle()
    LaunchedEffect(server) { vm.refreshEditLoras() }
    ModelLoraSection(
        "LTX 2.5", scene.lora, catalog,
        compatibility = { LtxLoras.compatibility(it) },
        serad = { LtxLoras.serad(it) },
        poznamka = { LtxLoras.poznamka(it) },
        refresh = { vm.refreshEditLoras(force = true) },
        select = { name, confirmed -> vm.setLtxLora(name, confirmed) },
        strength = { vm.setLtxLoraStrength(it) },
    )
}

@Composable
private fun ModelLoraSection(
    modelName: String, selected: EditLora, catalog: MainViewModel.EditLoraCatalog,
    compatibility: (EditLoraFile) -> LoraCompatibility, refresh: () -> Unit,
    select: (String, Boolean) -> Unit, strength: (Float) -> Unit,
    title: String = t("LoRA pro %s").format(modelName),
    serad: (List<EditLoraFile>) -> List<EditLoraFile> = { it },
    poznamka: (EditLoraFile) -> String = { "" },
) {
    var confirm by remember(modelName) { mutableStateOf<String?>(null) }
    val matching = serad(catalog.files.filter { compatibility(it) == LoraCompatibility.MATCH })
    val unclassified = catalog.files.filter { compatibility(it) == LoraCompatibility.UNKNOWN }
    // Pro model nahoře; soubory bez označení modelu pod čarou — ty se
    // potvrzují, protože na jiném modelu by graf spadl.
    val volby = matching.map { f ->
        val note = poznamka(f)
        LoraVolba(f.name, poznamka = note, varovani = note.isNotBlank())
    } + unclassified.mapIndexed { i, f ->
        LoraVolba(f.name, poznamka = t("neoznačený model"), varovani = true, oddelit = i == 0)
    }

    SectionCard(title = title,
        subtitle = t("Volba a síla se pamatují pro každý model zvlášť"),
        trailing = {
            IconButton(onClick = refresh, enabled = !catalog.loading) {
                Icon(Icons.Default.Refresh, t("Obnovit seznam LoRA"), tint = Cyan)
            }
        },
    ) {
        LoraRozbalovaci(
            popisek = t("LoRA"),
            volby = volby,
            vybrana = selected.name,
            onVybrat = { name ->
                when {
                    name.isEmpty() -> select("", false)
                    unclassified.any { it.name == name } -> confirm = name
                    else -> select(name, false)
                }
            },
        )
        if (selected.name.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            LabeledSlider(t("Síla LoRA"), "%.2f".format(selected.strength), selected.strength,
                0f..2f, onChange = strength)
        }
        if (catalog.loading) {
            LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 8.dp), color = Cyan)
            Text(t("Načítám LoRA a údaje o modelech…"), color = TextMid, style = MaterialTheme.typography.bodySmall)
        } else if (catalog.error) {
            Text(t("Seznam LoRA se nepodařilo načíst. Zkontrolujte server a obnovte seznam."),
                color = Amber, style = MaterialTheme.typography.bodySmall)
        }
        if (!catalog.loading && !catalog.error && selected.name.isNotBlank() && catalog.files.none { it.name == selected.name }) {
            Text(t("Vybraná LoRA na tomto serveru chybí. Obnovte seznam nebo vyberte jinou."),
                color = Amber, style = MaterialTheme.typography.bodySmall)
        }
    }

    confirm?.let { name ->
        AlertDialog(onDismissRequest = { confirm = null }, title = { Text(t("Přiřadit LoRA k modelu?")) },
            text = { Text(t("U souboru %s nelze ověřit základní model. Použijte ho jen pokud je určený pro %s.")
                .format(name, modelName)) },
            confirmButton = { TextButton(onClick = {
                select(name, true); confirm = null
            }) { Text(t("Použít pro tento model")) } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text(t("Zrušit")) } })
    }
}
