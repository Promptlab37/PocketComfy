package cz.promptlab.h3video.ui

import cz.promptlab.h3video.data.t

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import cz.promptlab.h3video.UpdateState
import cz.promptlab.h3video.update.UpdateChecker
import cz.promptlab.h3video.ui.theme.Cyan
import cz.promptlab.h3video.ui.theme.Danger
import cz.promptlab.h3video.ui.theme.Outline1
import cz.promptlab.h3video.ui.theme.Surface1
import cz.promptlab.h3video.ui.theme.TextHi
import cz.promptlab.h3video.ui.theme.TextLow
import cz.promptlab.h3video.ui.theme.TextMid
import cz.promptlab.h3video.ui.theme.Violet

/**
 * Nabídka aktualizace hned po spuštění aplikace.
 *
 * Kontrola běží při startu sama; jakmile najde novější vydání, vyskočí tohle
 * okno. Dřív se nová verze hlásila jen proužkem nad obsahem, který se dal
 * přehlédnout, a instalace stála za třemi klepnutími přes Nastavení.
 *
 * Okno drží celý zbytek cesty – stažení s postupem i spuštění instalace –
 * takže se z něj neodchází jinam.
 */
@Composable
fun UpdateDialog(
    state: UpdateState,
    onDownload: () -> Unit,
    onLater: () -> Unit,
) {
    val ctx = LocalContext.current

    // Jakmile je APK stažené, Android má hned nabídnout instalaci. Čekat na další
    // klepnutí nemá smysl – uživatel právě řekl, že aktualizovat chce.
    LaunchedEffect(state) {
        val ready = state as? UpdateState.Ready ?: return@LaunchedEffect
        if (UpdateChecker.canInstall(ctx)) {
            ctx.startActivity(UpdateChecker.installIntent(ctx, ready.apk))
        }
    }

    Dialog(onDismissRequest = onLater) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Surface1)
                .border(1.dp, Outline1, RoundedCornerShape(20.dp))
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.NewReleases, null, Modifier.size(22.dp), Cyan)
                Spacer(Modifier.size(10.dp))
                Text(
                    when (state) {
                        is UpdateState.Available -> "Je tu ${state.info.versionName}"
                        is UpdateState.Downloading -> "Stahuji ${state.info.versionName}"
                        is UpdateState.Ready -> t("Staženo")
                        else -> t("Aktualizace")
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = TextHi,
                    fontWeight = FontWeight.SemiBold
                )
            }

            when (state) {
                is UpdateState.Available -> {
                    if (state.info.notes.isNotBlank()) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            state.info.notes,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMid,
                            // Dlouhé poznámky k vydání nesmí okno vytáhnout přes
                            // celou obrazovku – zbytek se doroluje.
                            modifier = Modifier
                                .heightIn(max = 260.dp)
                                .verticalScroll(rememberScrollState())
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "%.1f MB".format(state.info.sizeBytes / 1_048_576f),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextLow
                    )
                    Spacer(Modifier.height(18.dp))
                    GradientButton(t("Stáhnout a nainstalovat"), onClick = onDownload)
                    Spacer(Modifier.height(8.dp))
                    OutlineButton(t("Později"), modifier = Modifier.fillMaxWidth(), onClick = onLater)
                }

                is UpdateState.Downloading -> {
                    Spacer(Modifier.height(16.dp))
                    LinearProgressIndicator(
                        progress = { state.progress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                        color = Violet,
                        trackColor = Outline1,
                        gapSize = 0.dp,
                        drawStopIndicator = {},
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "${(state.progress * 100).toInt()} %",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMid
                    )
                }

                is UpdateState.Ready -> {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        if (UpdateChecker.canInstall(ctx))
                            t("Android se teď zeptá na potvrzení instalace.")
                        else t("Android potřebuje povolit instalaci z této aplikace."),
                        style = MaterialTheme.typography.bodySmall, color = TextMid
                    )
                    Spacer(Modifier.height(18.dp))
                    GradientButton("Nainstalovat ${state.info.versionName}") {
                        if (UpdateChecker.canInstall(ctx)) {
                            ctx.startActivity(UpdateChecker.installIntent(ctx, state.apk))
                        } else {
                            // bez tohoto povolení Android instalaci odmítne bez vysvětlení
                            ctx.startActivity(UpdateChecker.unknownSourcesIntent(ctx))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlineButton(t("Zavřít"), modifier = Modifier.fillMaxWidth(), onClick = onLater)
                }

                is UpdateState.Failed -> {
                    Spacer(Modifier.height(12.dp))
                    Text(state.message, style = MaterialTheme.typography.bodySmall, color = Danger)
                    Spacer(Modifier.height(18.dp))
                    OutlineButton(t("Zavřít"), modifier = Modifier.fillMaxWidth(), onClick = onLater)
                }

                else -> Unit
            }
        }
    }
}
