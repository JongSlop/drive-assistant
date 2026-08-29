package dev.smto.driveassistant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import dev.smto.driveassistant.assistant.AssistantOrchestrator.Phase
import dev.smto.driveassistant.data.SettingsRepository.SttMode
import dev.smto.driveassistant.ui.theme.DriveAssistantTheme
import dev.smto.driveassistant.voice.RecognizerIntents
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Entry point for the system ASSIST gesture. Runs one voice turn — via the system
 * recognizer or the external recognizer popup — then dismisses itself. Hands off to
 * the main app for IME dictation or when a needed permission is missing.
 */
class AssistActivity : ComponentActivity() {

    private var started = false

    private val popup = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result: ActivityResult ->
        val text = RecognizerIntents.extractText(result.data)
        if (text.isNullOrBlank()) {
            finish()
        } else {
            lifecycleScope.launch {
                App.get().assistant.respondTo(text)
                awaitIdleThenFinish()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            DriveAssistantTheme {
                val state by App.get().assistant.state.collectAsState()
                Box(
                    Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(16.dp).clip(RoundedCornerShape(24.dp)),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 6.dp,
                    ) {
                        Column(Modifier.padding(20.dp)) {
                            Text(
                                when (state.phase) {
                                    Phase.LISTENING -> "Listening…"
                                    Phase.THINKING -> "Thinking…"
                                    Phase.SPEAKING -> "…"
                                    Phase.IDLE -> "Done"
                                },
                                style = MaterialTheme.typography.titleMedium,
                            )
                            val last = state.transcript.lastOrNull()
                            val shown = state.partial.ifBlank { last?.text.orEmpty() }
                            if (shown.isNotBlank()) {
                                Text(shown, style = MaterialTheme.typography.bodyLarge)
                            }
                            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (started) return
        started = true

        lifecycleScope.launch {
            val cfg = App.get().settings.current()
            when (cfg.sttMode) {
                SttMode.IME -> {
                    startActivity(Intent(this@AssistActivity, MainActivity::class.java))
                    finish()
                }

                SttMode.EXTERNAL_POPUP -> {
                    val intent = RecognizerIntents.build(
                        this@AssistActivity, cfg.language, cfg.recognizerPackage,
                    )
                    if (intent.resolveActivity(packageManager) == null) {
                        finish()
                    } else {
                        popup.launch(intent)
                    }
                }

                SttMode.SYSTEM -> {
                    val micGranted = ContextCompat.checkSelfPermission(
                        this@AssistActivity, Manifest.permission.RECORD_AUDIO,
                    ) == PackageManager.PERMISSION_GRANTED
                    if (!micGranted) {
                        startActivity(Intent(this@AssistActivity, MainActivity::class.java))
                        finish()
                    } else {
                        App.get().assistant.listenAndRespond()
                        awaitIdleThenFinish()
                    }
                }
            }
        }
    }

    private suspend fun awaitIdleThenFinish() {
        App.get().assistant.state.filter { it.phase == Phase.IDLE }.first()
        finish()
    }
}
