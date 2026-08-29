package dev.smto.driveassistant.ui

import android.content.Context
import android.view.inputmethod.InputMethodManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.smto.driveassistant.assistant.AssistantOrchestrator.Phase
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
                        onClick = {
                            if (!micGranted) onRequestMic()
                            else if (state.phase == Phase.SPEAKING) vm.stopSpeaking()
                            else vm.toggleListen()
                        },
                    )
                    Spacer(Modifier.height(24.dp))
                }

                SttMode.EXTERNAL_POPUP -> {
                    MicButton(
                        phase = state.phase,
                        onClick = {
                            if (state.phase == Phase.SPEAKING) {
                                vm.stopSpeaking()
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
                )
            }
        }
    }
}

@Composable
private fun StatusText(phase: Phase, mode: SttMode) {
    val label = when (phase) {
        Phase.IDLE -> if (mode == SttMode.IME) "Dictate or type your request" else "Tap to talk"
        Phase.LISTENING -> "Listening…"
        Phase.THINKING -> "Thinking…"
        Phase.SPEAKING -> "Speaking — tap stop to interrupt"
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
        Phase.THINKING -> MaterialTheme.colorScheme.secondary
        Phase.SPEAKING -> MaterialTheme.colorScheme.tertiary
        Phase.IDLE -> MaterialTheme.colorScheme.primary
    }
    val color by animateColorAsState(target, label = "mic")

    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        IconButton(
            onClick = onClick,
            modifier = Modifier.size(112.dp).clip(CircleShape).background(color),
        ) {
            Icon(
                imageVector = if (phase == Phase.SPEAKING) Icons.Default.Stop else Icons.Default.Mic,
                contentDescription = "Talk",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(48.dp),
            )
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

        if (phase == Phase.SPEAKING) {
            IconButton(onClick = onStop) {
                Icon(Icons.Default.Stop, contentDescription = "Stop speaking")
            }
        } else {
            IconButton(onClick = { submit() }, enabled = text.isNotBlank()) {
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
