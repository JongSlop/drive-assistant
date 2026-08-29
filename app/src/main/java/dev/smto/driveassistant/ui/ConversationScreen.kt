package dev.smto.driveassistant.ui

import android.content.Context
import android.view.inputmethod.InputMethodManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.smto.driveassistant.assistant.AssistantOrchestrator.Phase
import kotlinx.coroutines.delay
import dev.smto.driveassistant.data.SettingsRepository.SttMode
import dev.smto.driveassistant.voice.RecognizerIntents

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ConversationScreen(
    vm: AssistantViewModel,
    micGranted: Boolean,
    onRequestMic: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val sttMode by vm.sttMode.collectAsStateWithLifecycle()
    val language by vm.language.collectAsStateWithLifecycle()
    val recognizerPackage by vm.recognizerPackage.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val listState = rememberLazyListState()

    val popupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        RecognizerIntents.extractText(result.data)?.let { vm.send(it) }
    }

    LaunchedEffect(state.transcript.size) {
        if (state.transcript.isNotEmpty()) listState.animateScrollToItem(state.transcript.size - 1)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("DriveAssistant") },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
    ) { inner ->
        Column(Modifier.fillMaxSize().padding(inner)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.transcript) { line -> Bubble(line.role, line.text) }
                if (state.partial.isNotBlank()) {
                    item { Bubble("user", state.partial, dim = true) }
                }
            }

            state.error?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            StatusText(state.phase, sttMode)

            when (sttMode) {
                SttMode.SYSTEM -> {
                    MicButton(
                        phase = state.phase,
                        onClick = { if (!micGranted) onRequestMic() else vm.onMicTap() },
                    )
                    Spacer(Modifier.height(24.dp))
                }

                SttMode.EXTERNAL_POPUP -> {
                    MicButton(
                        phase = state.phase,
                        onClick = {
                            if (state.phase != Phase.IDLE) {
                                vm.onMicTap()
                            } else {
                                val intent = RecognizerIntents.build(context, language, recognizerPackage)
                                if (intent.resolveActivity(context.packageManager) == null) {
                                    vm.announceRecognizerUnavailable()
                                } else {
                                    popupLauncher.launch(intent)
                                }
                            }
                        },
                    )
                    Spacer(Modifier.height(24.dp))
                }

                SttMode.IME -> DictationBar(
                    phase = state.phase,
                    onSend = { text ->
                        vm.stopSpeaking()
                        vm.send(text)
                    },
                    onStop = vm::stopSpeaking,
                    onCancel = vm::cancel,
                )
            }
        }
    }
}

private fun Phase.isWorking() = this == Phase.TRANSCRIBING || this == Phase.THINKING

@Composable
private fun StatusText(phase: Phase, mode: SttMode) {
    // Seconds spent in the current working phase; shown once it drags on so a slow
    // recognizer or model call never looks frozen.
    var secs by remember(phase) { mutableStateOf(0) }
    LaunchedEffect(phase) {
        while (phase.isWorking()) {
            delay(1000)
            secs++
        }
    }
    val elapsed = if (phase.isWorking() && secs >= 3) " ${secs}s" else ""

    val label = when (phase) {
        Phase.IDLE -> if (mode == SttMode.IME) "Dictate or type your request" else "Tap to talk"
        Phase.LISTENING -> "Listening…"
        Phase.TRANSCRIBING -> "Transcribing, tap to cancel$elapsed"
        Phase.THINKING -> "Thinking, tap to cancel$elapsed"
        Phase.SPEAKING -> "Speaking, tap to stop"
    }
    Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(8.dp),
    )
}

@Composable
private fun MicButton(phase: Phase, onClick: () -> Unit) {
    val target = when (phase) {
        Phase.LISTENING -> MaterialTheme.colorScheme.error
        Phase.TRANSCRIBING, Phase.THINKING -> MaterialTheme.colorScheme.secondary
        Phase.SPEAKING -> MaterialTheme.colorScheme.tertiary
        Phase.IDLE -> MaterialTheme.colorScheme.primary
    }
    val color by animateColorAsState(target, label = "mic")

    // Slow pulse while the mic is open; collapses to no motion in every other phase.
    val pulse = rememberInfiniteTransition(label = "pulse")
    val scale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = if (phase == Phase.LISTENING) 1.08f else 1f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
        label = "scale",
    )

    val icon = when (phase) {
        Phase.SPEAKING -> Icons.Default.Stop
        Phase.TRANSCRIBING, Phase.THINKING -> Icons.Default.Close
        else -> Icons.Default.Mic
    }

    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(contentAlignment = Alignment.Center) {
            if (phase.isWorking()) {
                CircularProgressIndicator(
                    modifier = Modifier.size(126.dp),
                    strokeWidth = 3.dp,
                    color = color,
                )
            }
            IconButton(
                onClick = onClick,
                modifier = Modifier
                    .size(112.dp)
                    .scale(scale)
                    .clip(CircleShape)
                    .background(color),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = when (phase) {
                        Phase.SPEAKING -> "Stop speaking"
                        Phase.TRANSCRIBING, Phase.THINKING -> "Cancel"
                        else -> "Talk"
                    },
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(48.dp),
                )
            }
        }
    }
}

/**
 * Text-ingestion path for voice keyboards / IMEs (e.g. FUTO Voice Input): a focused
 * field the user dictates into, then submits. Also usable as a plain type-in.
 */
@Composable
private fun DictationBar(
    phase: Phase,
    onSend: (String) -> Unit,
    onStop: () -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = remember { FocusRequester() }
    var text by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(Unit) {
        runCatching { focus.requestFocus() }
        keyboard?.show()
    }

    fun submit() {
        val t = text.trim()
        if (t.isEmpty()) return
        onSend(t)
        text = ""
        runCatching { focus.requestFocus() }
        keyboard?.show()
    }

    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        IconButton(onClick = { context.imm()?.showInputMethodPicker() }) {
            Icon(Icons.Default.Keyboard, contentDescription = "Switch keyboard")
        }

        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.weight(1f).focusRequester(focus),
            placeholder = { Text("Dictate or type…") },
            maxLines = 4,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { submit() }),
        )

        when {
            phase == Phase.SPEAKING -> IconButton(onClick = onStop) {
                Icon(Icons.Default.Stop, contentDescription = "Stop speaking")
            }
            phase.isWorking() -> IconButton(onClick = onCancel) {
                Icon(Icons.Default.Close, contentDescription = "Cancel")
            }
            else -> IconButton(onClick = { submit() }, enabled = text.isNotBlank()) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
            }
        }
    }
}

private fun Context.imm(): InputMethodManager? =
    getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager

@Composable
private fun Bubble(role: String, text: String, dim: Boolean = false) {
    val mine = role == "user"
    val bg = if (mine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            color = bg.copy(alpha = if (dim) 0.5f else 1f),
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 2.dp,
        ) {
            Text(
                text,
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = if (mine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
