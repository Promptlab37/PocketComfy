package cz.promptlab.h3video.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cz.promptlab.h3video.MainViewModel
import cz.promptlab.h3video.data.Line
import cz.promptlab.h3video.data.MAX_LINES
import cz.promptlab.h3video.data.Speaker
import cz.promptlab.h3video.data.TalkScene
import cz.promptlab.h3video.data.VoiceSource
import cz.promptlab.h3video.data.VoiceStatus
import cz.promptlab.h3video.ui.theme.Amber
import cz.promptlab.h3video.ui.theme.Cyan
import cz.promptlab.h3video.ui.theme.Ok
import cz.promptlab.h3video.ui.theme.Outline1
import cz.promptlab.h3video.ui.theme.Surface2
import cz.promptlab.h3video.ui.theme.TextHi
import cz.promptlab.h3video.ui.theme.TextLow
import cz.promptlab.h3video.ui.theme.TextMid
import cz.promptlab.h3video.ui.theme.Violet
import cz.promptlab.h3video.util.LinePlayer
import cz.promptlab.h3video.util.VoiceRecorder

/**
 * Karta „Mluvící scéna": postavy z fotek a dialog, který spolu vedou.
 *
 * Postavy a repliky jsou oddělené, takže jedna postava může mluvit vícekrát —
 * běžné „ona – on – ona" je pak jen trojice replik u dvou postav. Model je bere
 * jako pořád stejné osoby, protože každá má jednu fotku a jedno ID mluvčího.
 */
@Composable
fun TalkSceneSection(vm: MainViewModel) {
    val scene by vm.scene.collectAsStateWithLifecycle()
    val voices by vm.voices.collectAsStateWithLifecycle()
    val higgs by vm.higgs.collectAsStateWithLifecycle()
    val higgsNote by vm.higgsNote.collectAsStateWithLifecycle()

    var pickAudioFor by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf<Int?>(null)
    }
    val pickLineAudio = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) {
        pickAudioFor?.let { key -> vm.pickLineAudio(key, it) }
        pickAudioFor = null
    }
    val player = remember { LinePlayer() }
    var playingKey by remember { mutableStateOf<Int?>(null) }
    DisposableEffect(Unit) { onDispose { player.stop() } }

    // ---------------------------------------------------------------- postavy
    SectionCard(
        title = "Postavy",
        subtitle = "Fotka drží podobu, hlas namluví repliky. Každá postava může mluvit vícekrát."
    ) {
        Column {
            scene.speakers.forEachIndexed { index, speaker ->
                SpeakerRow(
                    speaker = speaker,
                    number = index + 1,
                    voices = voices.map { it.id to it.name },
                    canRemove = scene.speakers.size > 1,
                    vm = vm,
                )
                Spacer(Modifier.height(12.dp))
            }
            if (scene.canAddSpeaker) {
                OutlineButton(
                    text = "Přidat postavu",
                    icon = { Icon(Icons.Default.PersonAdd, null, Modifier.size(18.dp), TextMid) },
                    onClick = { vm.addSpeaker() }
                )
            }
        }
    }

    // ---------------------------------------------------------------- dialog
    SectionCard(
        title = "Dialog",
        subtitle = "Repliky jdou po sobě v tomto pořadí. U každé vyber, kdo ji říká."
    ) {
        Column {
            scene.lines.forEachIndexed { index, line ->
                LineCard(
                    line = line,
                    number = index + 1,
                    scene = scene,
                    canRemove = scene.lines.size > 1,
                    vm = vm,
                    playing = playingKey == line.key,
                    onTogglePlay = {
                        if (playingKey == line.key) {
                            player.stop(); playingKey = null
                        } else line.audio?.let { f ->
                            player.play(line.key, f) { playingKey = null }
                            playingKey = line.key
                        }
                    },
                    onPickAudio = {
                        pickAudioFor = line.key
                        pickLineAudio.launch("audio/*")
                    },
                )
                Spacer(Modifier.height(12.dp))
            }

            if (scene.canAddLine) {
                OutlineButton(
                    text = "Přidat repliku",
                    icon = { Icon(Icons.Default.RecordVoiceOver, null, Modifier.size(18.dp), TextMid) },
                    onClick = { vm.addLine() }
                )
            } else {
                Text(
                    "Víc než $MAX_LINES repliky se do jednoho videa nevejdou – model bere " +
                        "jen tři zvukové reference.",
                    style = MaterialTheme.typography.bodySmall, color = TextLow
                )
            }

            scene.neededSeconds?.let { needed ->
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Cyan.copy(alpha = .10f))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.GraphicEq, null, Modifier.size(16.dp), Cyan)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Dialog trvá $needed s – délka videa se podle něj nastavila sama.",
                        style = MaterialTheme.typography.bodySmall, color = TextMid
                    )
                }
            }

            val pending = scene.lines.count {
                it.text.isNotBlank() && !it.voiceCurrent && scene.speakerOf(it)?.voice != null
            }
            if (pending > 0) {
                Spacer(Modifier.height(12.dp))
                OutlineButton(
                    text = if (pending == 1) "Namluvit repliku" else "Namluvit všechny ($pending)",
                    icon = { Icon(Icons.Default.RecordVoiceOver, null, Modifier.size(18.dp), Cyan) },
                    onClick = { vm.speakAll() }
                )
            }

            if (higgs == "starting" || higgsNote.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (higgs == "starting") {
                        CircularProgressIndicator(Modifier.size(14.dp), color = Cyan, strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        higgsNote.ifBlank { "Zapínám Higgs Audio na počítači…" },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (higgs == null) Amber else TextMid
                    )
                }
            }
        }
    }

    SectionCard(title = "Scéna", subtitle = "Kde se to odehrává a jak se chová kamera – nepovinné") {
        DarkTextField(
            value = scene.sceneNote,
            onValueChange = { vm.setSceneNote(it) },
            placeholder = "Kavárna, měkké odpolední světlo, kamera pomalu najíždí…",
            minHeight = 90.dp,
            onClear = { vm.setSceneNote("") },
        )
    }

    SectionCard(title = "Prompt pro model", subtitle = "Skládá se sám z postav a replik. Můžeš do něj sáhnout.") {
        Column {
            DarkTextField(
                value = scene.prompt,
                onValueChange = { vm.setTalkPrompt(it) },
                placeholder = "Doplní se, jakmile přidáš fotku a repliku",
                minHeight = 170.dp,
            )
            if (scene.promptEdited) {
                Spacer(Modifier.height(8.dp))
                OutlineButton(
                    text = "Složit prompt znovu podle dialogu",
                    icon = { Icon(Icons.Default.Refresh, null, Modifier.size(18.dp), TextMid) },
                    onClick = { vm.recomposeTalkPrompt() }
                )
            }
        }
    }
}

/** Postava: fotka, popis, hlas. Text repliky tu schválně není. */
@Composable
private fun SpeakerRow(
    speaker: Speaker,
    number: Int,
    voices: List<Pair<String, String>>,
    canRemove: Boolean,
    vm: MainViewModel,
) {
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) {
        vm.pickSpeakerImage(speaker.key, it)
    }
    val pickSample = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) {
        vm.pickSpeakerSample(speaker.key, it)
    }
    val shoot = rememberCameraShot { uri: Uri -> vm.pickSpeakerImage(speaker.key, uri) }
    var voicesOpen by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxWidth()
            .border(1.dp, Outline1, RoundedCornerShape(16.dp))
            .padding(12.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier
                    .width(96.dp)
                    .aspectRatio(0.82f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Surface2)
                    .border(
                        1.dp,
                        if (speaker.image != null) Violet.copy(alpha = .6f) else Outline1,
                        RoundedCornerShape(14.dp)
                    )
                    .clickable {
                        pickImage.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
            ) {
                val thumb = speaker.thumb
                if (thumb != null) {
                    Image(
                        bitmap = thumb.asImageBitmap(), contentDescription = null,
                        modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop
                    )
                } else {
                    Column(
                        Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.AddPhotoAlternate, null, Modifier.size(22.dp), TextMid)
                        Spacer(Modifier.height(4.dp))
                        Text("Fotka", style = MaterialTheme.typography.bodySmall, color = TextMid)
                    }
                }
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .size(26.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.Black.copy(alpha = .62f))
                        .clickable(onClick = shoot),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.PhotoCamera, "Vyfotit", Modifier.size(15.dp), Color.White)
                }
            }

            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Postava $number",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextHi, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    if (canRemove) {
                        Box(
                            Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(50))
                                .background(Surface2)
                                .clickable { vm.removeSpeaker(speaker.key) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Close, "Odebrat postavu", Modifier.size(14.dp), TextMid)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                DarkTextField(
                    value = speaker.look,
                    onValueChange = { vm.setSpeakerLook(speaker.key, it) },
                    placeholder = "muž v obleku (nepovinné)",
                    minHeight = 56.dp,
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Surface2)
                        .border(
                            1.dp,
                            if (speaker.voice != null) Violet.copy(alpha = .5f) else Outline1,
                            RoundedCornerShape(12.dp)
                        )
                        .clickable {
                            voicesOpen = !voicesOpen
                            if (voicesOpen && voices.isEmpty()) vm.loadVoices()
                        }
                        .padding(horizontal = 12.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.GraphicEq, null, Modifier.size(16.dp), Cyan)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        when (val v = speaker.voice) {
                            is VoiceSource.Library -> v.voiceName
                            is VoiceSource.Sample -> v.label
                            null -> "Vybrat hlas"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (speaker.voice != null) TextHi else TextMid,
                        maxLines = 1
                    )
                }
            }
        }

        AnimatedVisibility(voicesOpen) {
            Column(Modifier.padding(top = 10.dp)) {
                if (voices.isEmpty()) {
                    Text(
                        "Načítám hlasy z počítače…",
                        style = MaterialTheme.typography.bodySmall, color = TextMid
                    )
                } else {
                    voices.forEach { (id, name) ->
                        Text(
                            name,
                            style = MaterialTheme.typography.bodyMedium, color = TextHi,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    vm.setSpeakerVoice(speaker.key, VoiceSource.Library(id, name))
                                    voicesOpen = false
                                }
                                .padding(horizontal = 10.dp, vertical = 9.dp)
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                RecordVoiceButton { file ->
                    vm.setSpeakerSample(speaker.key, file, "vlastní nahrávka")
                    voicesOpen = false
                }
                Spacer(Modifier.height(8.dp))
                OutlineButton(
                    text = "Naklonovat ze zvukového souboru",
                    onClick = { pickSample.launch("audio/*"); voicesOpen = false }
                )
            }
        }
    }
}

/** Jedna replika: kdo ji říká, co říká, a její hlas. */
@Composable
private fun LineCard(
    line: Line,
    number: Int,
    scene: TalkScene,
    canRemove: Boolean,
    vm: MainViewModel,
    playing: Boolean,
    onTogglePlay: () -> Unit,
    /** Vybrat hotový zvuk místo namluvení Higgsem. */
    onPickAudio: () -> Unit,
) {
    val speaker = scene.speakerOf(line)

    Column(
        Modifier
            .fillMaxWidth()
            .border(1.dp, Outline1, RoundedCornerShape(16.dp))
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "$number. replika",
                style = MaterialTheme.typography.bodyMedium,
                color = TextHi, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            if (canRemove) {
                Box(
                    Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Surface2)
                        .clickable { vm.removeLine(line.key) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Close, "Odebrat repliku", Modifier.size(14.dp), TextMid)
                }
            }
        }

        // Kdo mluví. U dvou postav je to jedno ťuknutí, ne rozbalovací seznam.
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            scene.speakers.forEachIndexed { i, s ->
                val active = s.key == line.speakerKey
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (active) Violet.copy(alpha = .28f) else Surface2)
                        .clickable { vm.setLineSpeaker(line.key, s.key) }
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                ) {
                    Text(
                        s.look.trim().ifBlank { "Postava ${i + 1}" },
                        style = MaterialTheme.typography.labelMedium,
                        color = if (active) TextHi else TextMid,
                        maxLines = 1
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        DarkTextField(
            value = line.text,
            onValueChange = { vm.setLineText(line.key, it) },
            placeholder = "Co má říct…",
            minHeight = 92.dp,
            onClear = { vm.setLineText(line.key, "") },
        )

        if (line.text.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (line.voiceCurrent) Ok.copy(alpha = .18f) else Cyan.copy(alpha = .16f))
                        .clickable(enabled = speaker?.voice != null && line.status != VoiceStatus.RUNNING) {
                            if (line.voiceCurrent) onTogglePlay() else vm.speakLine(line.key)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        line.status == VoiceStatus.RUNNING ->
                            CircularProgressIndicator(Modifier.size(18.dp), color = Cyan, strokeWidth = 2.dp)
                        playing -> Icon(Icons.Default.Stop, "Zastavit", Modifier.size(18.dp), Ok)
                        line.voiceCurrent ->
                            Icon(Icons.Default.PlayArrow, "Přehrát repliku", Modifier.size(18.dp), Ok)
                        else -> Icon(Icons.Default.RecordVoiceOver, "Namluvit", Modifier.size(18.dp), Cyan)
                    }
                }
                if (line.voiceCurrent && line.status != VoiceStatus.RUNNING) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Surface2)
                            .clickable { vm.speakLine(line.key) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Refresh, "Namluvit znovu", Modifier.size(18.dp), TextMid)
                    }
                }
                Spacer(Modifier.width(10.dp))
                val note = when {
                    line.status == VoiceStatus.FAILED && line.error.isNotBlank() -> line.error to Amber
                    line.voiceCurrent -> "Hlas je hotový (%.1f s)".format(line.audioSeconds) to Ok
                    line.audio != null -> "Text se změnil – namluv znovu" to Amber
                    speaker?.voice == null -> "Postava nemá vybraný hlas" to Amber
                    else -> "Ťukni pro namluvení" to TextLow
                }
                Text(note.first, style = MaterialTheme.typography.bodySmall, color = note.second)
            }
            // Vlastní zvuk místo namluvení: když už hotovou nahrávku máš, není
            // důvod ji nechat vyrábět znovu. Délka se změří ze souboru, takže
            // sedí i délka videa a časy replik v promptu.
            Spacer(Modifier.height(6.dp))
            TagChip(
                text = if (line.voiceCurrent) "Vyměnit za vlastní zvuk" else "Vložit vlastní zvuk",
                active = false,
            ) { onPickAudio() }
            if (line.status == VoiceStatus.RUNNING) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { line.progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                    color = Cyan, trackColor = Outline1, gapSize = 0.dp, drawStopIndicator = {},
                )
            }
        }
    }
}

/**
 * Nahrání vzorku hlasu mikrofonem. Higgs doporučuje 5–30 s čisté řeči; kratší
 * ukázka dá znatelně horší klon, proto se to píše rovnou pod tlačítko.
 */
@Composable
private fun RecordVoiceButton(onRecorded: (java.io.File) -> Unit) {
    val ctx = LocalContext.current
    val recorder = remember { VoiceRecorder(ctx) }
    var recording by remember { mutableStateOf(false) }
    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) { recorder.start(); recording = true }
    }

    Column {
        OutlineButton(
            text = if (recording) "Zastavit nahrávání" else "Nahrát vlastní hlas mikrofonem",
            icon = {
                Icon(
                    if (recording) Icons.Default.Stop else Icons.Default.Mic,
                    null, Modifier.size(18.dp), if (recording) Amber else Cyan
                )
            },
            onClick = {
                if (recording) {
                    recording = false
                    recorder.stop()?.let(onRecorded)
                } else {
                    val granted = ContextCompat.checkSelfPermission(
                        ctx, Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED
                    if (granted) { recorder.start(); recording = true }
                    else askMic.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
        )
        Text(
            if (recording) "Mluv souvisle, ideálně 5–30 sekund."
            else "5–30 s čisté řeči; podle ní Higgs hlas naklonuje.",
            style = MaterialTheme.typography.bodySmall, color = TextLow,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}
