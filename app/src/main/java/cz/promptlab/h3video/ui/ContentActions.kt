package cz.promptlab.h3video.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.unit.dp
import cz.promptlab.h3video.data.VideoItem
import cz.promptlab.h3video.data.t

internal fun copyResultText(ctx: Context, text: String) {
    ctx.getSystemService(ClipboardManager::class.java)
        .setPrimaryClip(ClipData.newPlainText("PocketComfy", text))
    if (Build.VERSION.SDK_INT < 33) Toast.makeText(ctx, t("Zkopírováno"), Toast.LENGTH_SHORT).show()
}

@Composable
internal fun RenameResultDialog(item: VideoItem, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var title by rememberSaveable(item.id) { mutableStateOf(item.title) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(t("Pojmenovat výstup")) },
        text = {
            Column {
                DarkTextField(value = title, onValueChange = { title = it.take(120) },
                    placeholder = t("Název výstupu"), minHeight = 48.dp, singleLine = true, onClear = { title = "" })
                Text(t("Prázdný název použije původní zadání."))
            }
        },
        confirmButton = { TextButton(onClick = { onSave(title.trim()) }) { Text(t("Uložit")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("Zrušit")) } })
}
