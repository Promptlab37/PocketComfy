package cz.promptlab.h3video.ui

import cz.promptlab.h3video.data.t

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cz.promptlab.h3video.MainViewModel
import cz.promptlab.h3video.data.Mode
import cz.promptlab.h3video.ui.theme.Amber
import cz.promptlab.h3video.ui.theme.Ink
import cz.promptlab.h3video.ui.theme.TextHi
import cz.promptlab.h3video.ui.theme.TextLow

/**
 * Editor časové osy na celou obrazovku, který naskočí po otočení telefonu na šířku.
 *
 * Proč je to samostatná obrazovka a ne karta uvnitř rolovací obrazovky: na osu
 * je potřeba šířka a **všechno po ruce najednou** – pás s klipy, ovládání
 * vybraného klipu i tlačítko Generovat. Rolovat kvůli generování o obrazovku níž
 * je u střihové práce k ničemu.
 *
 * Otočením zpátky na výšku se appka vrátí tam, kde byla; osa se ukládá, takže se
 * nic neztratí. Přepnutí režimu na [Mode.TIMELINE] je schválně: co se tady vidí,
 * to se po stisku Generovat taky vyrobí.
 */
@Composable
fun TimelineLandscapeScreen(
    vm: MainViewModel,
    busy: Boolean,
) {
    val params by vm.params.collectAsStateWithLifecycle()
    val scene by vm.timeline.collectAsStateWithLifecycle()
    val problem: String? = remember(params, scene) {
        vm.validation(params)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Ink)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        // ---- záhlaví: co to je, jak je to dlouhé, a hlavně Generovat
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    t("ČASOVÁ OSA"),
                    style = MaterialTheme.typography.labelMedium,
                    color = TextLow, letterSpacing = 3.sp,
                )
                Text(
                    "%.1f s · %s".format(scene.totalSeconds, segmentyPocet(scene.segments.size)),
                    style = MaterialTheme.typography.titleMedium,
                    color = TextHi, fontWeight = FontWeight.SemiBold,
                )
            }
            Box(Modifier.width(240.dp)) {
                GradientButton(
                    text = when {
                        busy -> t("Generování už běží")
                        else -> problem ?: t("Vygenerovat video")
                    },
                    enabled = !busy && problem == null,
                    onClick = { vm.start() },
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        // ---- pás a pod ním ovládání vybraného klipu; obojí na jedné obrazovce
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            TimelineSceneSection(vm)
        }

        val hlaska = problem
        if (hlaska != null) {
            Text(
                hlaska,
                style = MaterialTheme.typography.bodySmall,
                color = Amber,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}
